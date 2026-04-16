package local.rag.repo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChunkRow(
    UUID id,
    String domain,
    String source,
    String title,
    String url,
    Instant publishTime,
    String content,
    Map<String, Object> metadata
) {}

