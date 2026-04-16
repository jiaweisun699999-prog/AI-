package local.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import local.rag.config.RagProperties;
import local.rag.repo.ChunkRepository;
import local.rag.search.OpenSearchClient;
import org.springframework.stereotype.Service;

@Service
public class IngestService {
  private final ChunkRepository chunkRepository;
  private final OpenSearchClient openSearchClient;
  private final RagProperties props;
  private final ObjectMapper objectMapper;

  public IngestService(ChunkRepository chunkRepository, OpenSearchClient openSearchClient, RagProperties props, ObjectMapper objectMapper) {
    this.chunkRepository = chunkRepository;
    this.openSearchClient = openSearchClient;
    this.props = props;
    this.objectMapper = objectMapper;
  }

  public IngestResult ingestFolder(String domain, String source, String folder, boolean recursive) throws Exception {
    chunkRepository.ensureIngestTables();
    openSearchClient.ensureIndex(props.getOpensearch().getUrl(), props.getOpensearch().getIndex());

    Path root = Path.of(folder).normalize();
    if (!Files.exists(root) || !Files.isDirectory(root)) {
      throw new IllegalArgumentException("Folder not found or not a directory: " + root);
    }

    int filesSeen = 0;
    int filesIngested = 0;
    int chunksInserted = 0;

    var stream = recursive ? Files.walk(root) : Files.list(root);
    try (stream) {
      for (Path p : stream.filter(Files::isRegularFile).toList()) {
        String name = p.getFileName().toString();
        if (!isSupported(name)) continue;
        filesSeen++;

        String hash = sha256Hex(p);
        if (chunkRepository.hasFileHash(domain, source, hash)) continue;

        String text;
        try {
          text = TextExtractor.extract(p);
        } catch (Exception e) {
          continue;
        }
        List<String> chunks = Chunker.chunk(text, 900, 120);
        if (chunks.isEmpty()) continue;

        UUID fileId = UUID.randomUUID();
        chunkRepository.insertIngestedFile(fileId, domain, source, p.toString(), name, hash);
        filesIngested++;

        for (int i = 0; i < chunks.size(); i++) {
          UUID chunkId = UUID.randomUUID();
          String content = chunks.get(i);
          Map<String, Object> md = new LinkedHashMap<>();
          md.put("file_id", fileId.toString());
          md.put("file_name", name);
          md.put("file_path", p.toString());
          md.put("file_hash", hash);
          md.put("chunk_index", i);

          String mdJson = objectMapper.writeValueAsString(md);
          chunkRepository.insertChunk(chunkId, domain, source, name, null, (Instant) null, content, mdJson);
          chunksInserted++;

          Map<String, Object> doc = new LinkedHashMap<>();
          doc.put("id", chunkId.toString());
          doc.put("domain", domain);
          doc.put("source", source);
          doc.put("title", name);
          doc.put("url", null);
          doc.put("publish_time", null);
          doc.put("content", content);
          doc.put("metadata", md);
          openSearchClient.indexDoc(props.getOpensearch().getUrl(), props.getOpensearch().getIndex(), chunkId, objectMapper.writeValueAsString(doc));
        }
      }
    }

    openSearchClient.refresh(props.getOpensearch().getUrl(), props.getOpensearch().getIndex());
    return new IngestResult(filesSeen, filesIngested, chunksInserted);
  }

  private static boolean isSupported(String name) {
    String n = name.toLowerCase();
    return n.endsWith(".pdf") || n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".html") || n.endsWith(".htm");
  }

  private static String sha256Hex(Path p) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(p)) {
      byte[] buf = new byte[8192];
      int r;
      while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
    }
    byte[] dig = md.digest();
    StringBuilder sb = new StringBuilder(dig.length * 2);
    for (byte b : dig) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  public record IngestResult(int filesSeen, int filesIngested, int chunksInserted) {}
}

