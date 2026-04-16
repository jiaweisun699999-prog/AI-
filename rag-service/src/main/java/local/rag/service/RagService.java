package local.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import local.rag.config.RagProperties;
import local.rag.llm.OllamaClient;
import local.rag.model.AskResponse;
import local.rag.model.Citation;
import local.rag.repo.ChunkRepository;
import local.rag.repo.ChunkRow;
import local.rag.search.OpenSearchClient;
import local.rag.search.SearchHit;
import local.rag.util.PgVector;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class RagService {
  private final RagProperties props;
  private final OpenSearchClient openSearchClient;
  private final ChunkRepository chunkRepository;
  private final ObjectMapper objectMapper;
  private final OllamaClient ollamaClient;

  public RagService(RagProperties props, OpenSearchClient openSearchClient, ChunkRepository chunkRepository, ObjectMapper objectMapper, OllamaClient ollamaClient) {
    this.props = props;
    this.openSearchClient = openSearchClient;
    this.chunkRepository = chunkRepository;
    this.objectMapper = objectMapper;
    this.ollamaClient = ollamaClient;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() throws Exception {
    openSearchClient.ensureIndex(props.getOpensearch().getUrl(), props.getOpensearch().getIndex());

    // Seed OpenSearch from Postgres demo rows if index is empty-ish. Safe: overwrites docs by id.
    List<ChunkRow> seeds = chunkRepository.latestByDomain("ashare", 50);
    for (ChunkRow r : seeds) {
      Map<String, Object> doc = new LinkedHashMap<>();
      doc.put("id", r.id().toString());
      doc.put("domain", r.domain());
      doc.put("source", r.source());
      doc.put("title", r.title());
      doc.put("url", r.url());
      doc.put("publish_time", r.publishTime() == null ? null : r.publishTime().toString());
      doc.put("content", r.content());
      doc.put("metadata", r.metadata());
      openSearchClient.indexDoc(props.getOpensearch().getUrl(), props.getOpensearch().getIndex(), r.id(), objectMapper.writeValueAsString(doc));
    }

    openSearchClient.refresh(props.getOpensearch().getUrl(), props.getOpensearch().getIndex());
  }

  public AskResponse ask(String question, String domain, Integer requestedTopK, String mode) throws Exception {
    int cfgTopK = props.getRetrieval().getBm25TopK();
    int topK = requestedTopK == null ? cfgTopK : Math.max(1, Math.min(20, requestedTopK));
    // BM25 hits
    List<SearchHit> bm25Hits = openSearchClient.search(props.getOpensearch().getUrl(), props.getOpensearch().getIndex(), question, domain, topK);

    // Vector hits (optional): only if we have an embedding model configured and query embedding works
    List<ChunkRow> vectorRows = List.of();
    try {
      String embModel = props.getOllama().getEmbeddingModel();
      if (embModel != null && !embModel.isBlank()) {
        Duration timeout = Duration.ofSeconds(Math.max(10, props.getOllama().getTimeoutSeconds()));
        float[] qEmb = ollamaClient.embed(props.getOllama().getUrl(), embModel, question, timeout);
        if (qEmb.length == props.getOllama().getEmbeddingDim()) {
          vectorRows = chunkRepository.vectorSearch(domain, PgVector.toVectorLiteral(qEmb), topK);
        }
      }
    } catch (Exception ignored) {
      // If embedding isn't available yet, just run BM25.
    }

    // Merge BM25 id hits with vector rows (dedupe by id, keep BM25 order first then vector).
    Map<UUID, ChunkRow> merged = new LinkedHashMap<>();
    List<UUID> bm25Ids = bm25Hits.stream()
        .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
        .map(SearchHit::id)
        .toList();
    for (ChunkRow r : chunkRepository.findByIds(bm25Ids)) merged.put(r.id(), r);
    for (ChunkRow r : vectorRows) merged.putIfAbsent(r.id(), r);

    List<Citation> citations = new ArrayList<>();
    for (ChunkRow r : merged.values().stream().limit(topK).toList()) {
      citations.add(new Citation(
          r.id(),
          r.title(),
          r.source(),
          r.publishTime(),
          snippet(r.content()),
          r.url(),
          r.metadata()
      ));
    }

    String answer = generateAnswerWithOllamaOrFallback(question, citations, mode);
    return new AskResponse(answer, citations, new AskResponse.Trace(citations.size()));
  }

  private String generateAnswerWithOllamaOrFallback(String question, List<Citation> citations, String mode) {
    String ollamaUrl = props.getOllama().getUrl();
    String model = pickModel(mode);

    if (citations == null || citations.isEmpty()) {
      return buildMvpAnswer(question, List.of());
    }

    if (ollamaUrl == null || ollamaUrl.isBlank() || model == null || model.isBlank()) {
      return buildMvpAnswer(question, citations);
    }

    try {
      String system = """
你是“金融研报智能问答与分析助手”。你必须严格基于【证据片段】回答，不得编造研报中不存在的信息。

输出要求（必须用中文，尽量简洁）：
1) 结论：3-6条要点
2) 依据：逐条列出对应证据编号（如[1][2]）
3) 风险：与证据一致；如证据不足请注明“不确定”
4) 时间范围：如证据未提供时间范围，请注明“不确定”

硬约束：
- 任何关键结论都必须带至少一个证据编号
- 如果证据不足以回答，直接说明不足，并给出你需要的补充信息类型
""";

      String user = buildUserPrompt(question, citations);
      var messages = List.<Map<String, String>>of(
          Map.of("role", "system", "content", system),
          Map.of("role", "user", "content", user)
      );

      Duration timeout = Duration.ofSeconds(Math.max(5, props.getOllama().getTimeoutSeconds()));
      String out = ollamaClient.chat(ollamaUrl, model, messages, timeout);
      out = out == null ? "" : out.strip();
      if (out.isBlank()) return buildMvpAnswer(question, citations);
      return out;
    } catch (Exception e) {
      return buildMvpAnswer(question, citations) + "\n\n（提示：Ollama 调用失败，已回退到检索摘要。原因：" + e.getMessage() + "）";
    }
  }

  private String pickModel(String mode) {
    String m = mode == null ? "" : mode.strip().toLowerCase();
    if ("deep".equals(m)) return props.getOllama().getDeepModel();
    return props.getOllama().getDefaultModel();
  }

  private static String buildUserPrompt(String question, List<Citation> citations) {
    StringBuilder sb = new StringBuilder();
    sb.append("问题：").append(question).append("\n\n");
    sb.append("证据片段：\n");
    for (int i = 0; i < citations.size(); i++) {
      Citation c = citations.get(i);
      sb.append("[").append(i + 1).append("] ");
      sb.append(c.getTitle() == null ? "（无标题）" : c.getTitle());
      sb.append("：").append(c.getSnippet() == null ? "" : c.getSnippet());
      sb.append("\n");
    }
    if (citations.isEmpty()) {
      sb.append("（无）\n");
    }
    return sb.toString();
  }

  private static String snippet(String content) {
    if (content == null) return null;
    String s = content.strip();
    return s.length() <= 220 ? s : s.substring(0, 220) + "…";
  }

  private static String buildMvpAnswer(String question, List<Citation> citations) {
    StringBuilder sb = new StringBuilder();
    sb.append("这是基于检索到的研报片段做的“可溯源”摘要：\n\n");
    if (citations.isEmpty()) {
      sb.append("- 未检索到相关证据（citations 为空）。\n");
      sb.append("- 建议：导入更多研报数据，或把问题改得更具体（公司/行业/时间范围/指标）。\n");
      return sb.toString();
    }
    sb.append("- 你的问题：").append(question).append("\n");
    sb.append("- 证据要点：\n");
    for (int i = 0; i < citations.size(); i++) {
      Citation c = citations.get(i);
      sb.append("  ").append(i + 1).append(") ");
      sb.append(c.getTitle() == null ? "（无标题）" : c.getTitle());
      sb.append("：").append(c.getSnippet()).append("\n");
    }
    sb.append("\n你可以先用这些引用验证方向是否正确；接入本地模型后，会把“结论/依据/风险/时间范围”结构化生成出来。");
    return sb.toString();
  }
}

