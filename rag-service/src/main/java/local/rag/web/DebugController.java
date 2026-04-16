package local.rag.web;

import java.util.List;
import java.util.Map;
import local.rag.repo.ChunkRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DebugController {
  private final ChunkRepository chunkRepository;

  public DebugController(ChunkRepository chunkRepository) {
    this.chunkRepository = chunkRepository;
  }

  @GetMapping("/debug/latest-chunks")
  public List<Map<String, Object>> latestChunks(@RequestParam(defaultValue = "ashare") String domain) {
    return chunkRepository.latestByDomain(domain, 20).stream()
        .map(r -> Map.<String, Object>of(
            "id", r.id().toString(),
            "domain", r.domain(),
            "title", r.title(),
            "content", r.content()
        ))
        .toList();
  }
}

