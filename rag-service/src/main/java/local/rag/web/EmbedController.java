package local.rag.web;

import java.time.Duration;
import java.util.Map;
import local.rag.config.RagProperties;
import local.rag.llm.OllamaClient;
import local.rag.repo.ChunkRepository;
import local.rag.util.PgVector;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmbedController {
  private final ChunkRepository chunkRepository;
  private final RagProperties props;
  private final OllamaClient ollamaClient;

  public EmbedController(ChunkRepository chunkRepository, RagProperties props, OllamaClient ollamaClient) {
    this.chunkRepository = chunkRepository;
    this.props = props;
    this.ollamaClient = ollamaClient;
  }

  public record BackfillRequest(String domain, Integer limit) {}

  @PostMapping(value = "/embed/backfill", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> backfill(@RequestBody BackfillRequest req) throws Exception {
    String domain = req.domain() == null || req.domain().isBlank() ? "ashare" : req.domain();
    int limit = req.limit() == null ? 200 : Math.max(1, Math.min(2000, req.limit()));

    String url = props.getOllama().getUrl();
    String model = props.getOllama().getEmbeddingModel();
    int expectedDim = props.getOllama().getEmbeddingDim();

    var rows = chunkRepository.listForEmbeddingBackfill(domain, limit);
    int updated = 0;
    int skipped = 0;

    Duration timeout = Duration.ofSeconds(Math.max(10, props.getOllama().getTimeoutSeconds()));
    for (var r : rows) {
      String input = (r.title() == null ? "" : r.title() + "\n") + (r.content() == null ? "" : r.content());
      if (input.isBlank()) { skipped++; continue; }
      float[] emb = ollamaClient.embed(url, model, input, timeout);
      if (emb.length != expectedDim) {
        throw new IllegalStateException("Embedding dim mismatch: expected=" + expectedDim + " got=" + emb.length + " (model=" + model + ")");
      }
      chunkRepository.updateEmbedding(r.id(), PgVector.toVectorLiteral(emb));
      updated++;
    }

    return Map.of(
        "ok", true,
        "domain", domain,
        "model", model,
        "expectedDim", expectedDim,
        "scanned", rows.size(),
        "updated", updated,
        "skipped", skipped
    );
  }
}

