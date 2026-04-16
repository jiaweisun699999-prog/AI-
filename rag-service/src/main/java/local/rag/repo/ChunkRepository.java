package local.rag.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ChunkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<ChunkRow> latestByDomain(String domain, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, domain, source, title, url, publish_time, content, metadata
        FROM chunks
        WHERE domain = ?
        ORDER BY publish_time DESC NULLS LAST, created_at DESC
        LIMIT ?
        """,
        rowMapper(),
        domain,
        limit
    );
  }

  public void ensureIngestTables() {
    jdbcTemplate.execute("""
      CREATE TABLE IF NOT EXISTS ingested_files (
        id UUID PRIMARY KEY,
        domain TEXT NOT NULL,
        source TEXT NOT NULL,
        file_path TEXT NOT NULL,
        file_name TEXT NOT NULL,
        file_hash TEXT NOT NULL,
        ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE(domain, source, file_hash)
      );
      """);
    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ingested_files_domain ON ingested_files(domain);");
  }

  public boolean hasFileHash(String domain, String source, String fileHash) {
    Integer n = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM ingested_files WHERE domain = ? AND source = ? AND file_hash = ?",
        Integer.class,
        domain,
        source,
        fileHash
    );
    return n != null && n > 0;
  }

  public void insertIngestedFile(UUID id, String domain, String source, String filePath, String fileName, String fileHash) {
    jdbcTemplate.update(
        """
        INSERT INTO ingested_files (id, domain, source, file_path, file_name, file_hash, ingested_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (domain, source, file_hash) DO NOTHING
        """,
        id, domain, source, filePath, fileName, fileHash, OffsetDateTime.now()
    );
  }

  public void insertChunk(UUID id, String domain, String source, String title, String url, Instant publishTime, String content, String metadataJson) {
    jdbcTemplate.update(
        """
        INSERT INTO chunks (id, domain, source, title, url, publish_time, content, metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """,
        id, domain, source, title, url, publishTime == null ? null : java.sql.Timestamp.from(publishTime), content, metadataJson == null ? "{}" : metadataJson
    );
  }

  public List<ChunkRow> findByIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    // Build simple IN list for MVP; ids originate from our own search results.
    String in = ids.stream().map(u -> "'" + u + "'").reduce((a, b) -> a + "," + b).orElse("''");
    return jdbcTemplate.query(
        "SELECT id, domain, source, title, url, publish_time, content, metadata FROM chunks WHERE id IN (" + in + ")",
        rowMapper()
    );
  }

  public List<ChunkRow> listForEmbeddingBackfill(String domain, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, domain, source, title, url, publish_time, content, metadata
        FROM chunks
        WHERE domain = ? AND embedding IS NULL
        ORDER BY created_at DESC
        LIMIT ?
        """,
        rowMapper(),
        domain,
        limit
    );
  }

  public void updateEmbedding(UUID id, String vectorLiteral) {
    jdbcTemplate.update("UPDATE chunks SET embedding = ?::vector WHERE id = ?", vectorLiteral, id);
  }

  public List<ChunkRow> vectorSearch(String domain, String queryVectorLiteral, int limit) {
    // cosine distance: smaller is better
    return jdbcTemplate.query(
        """
        SELECT id, domain, source, title, url, publish_time, content, metadata
        FROM chunks
        WHERE domain = ? AND embedding IS NOT NULL
        ORDER BY (embedding <=> ?::vector) ASC
        LIMIT ?
        """,
        rowMapper(),
        domain,
        queryVectorLiteral,
        limit
    );
  }

  private RowMapper<ChunkRow> rowMapper() {
    return new RowMapper<>() {
      @Override
      public ChunkRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String domain = rs.getString("domain");
        String source = rs.getString("source");
        String title = rs.getString("title");
        String url = rs.getString("url");
        Instant publishTime = rs.getTimestamp("publish_time") == null ? null : rs.getTimestamp("publish_time").toInstant();
        String content = rs.getString("content");
        Map<String, Object> metadata;
        try {
          String json = rs.getString("metadata");
          metadata = json == null ? Map.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
          metadata = Map.of("metadata_parse_error", e.getMessage());
        }
        return new ChunkRow(id, domain, source, title, url, publishTime, content, metadata);
      }
    };
  }
}

