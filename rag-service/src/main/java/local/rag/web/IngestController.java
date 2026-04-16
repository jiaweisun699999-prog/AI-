package local.rag.web;

import java.util.Map;
import local.rag.ingest.IngestService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestController {
  private final IngestService ingestService;

  public IngestController(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  public record IngestRequest(String domain, String source, String folder, Boolean recursive) {}

  @PostMapping(value = "/ingest/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> run(@RequestBody IngestRequest req) throws Exception {
    String domain = req.domain() == null || req.domain().isBlank() ? "ashare" : req.domain();
    String source = req.source() == null || req.source().isBlank() ? "local" : req.source();
    String folder = req.folder() == null || req.folder().isBlank() ? "/inbox" : req.folder();
    boolean recursive = req.recursive() != null && req.recursive();

    var res = ingestService.ingestFolder(domain, source, folder, recursive);
    return Map.of(
        "ok", true,
        "domain", domain,
        "source", source,
        "folder", folder,
        "recursive", recursive,
        "filesSeen", res.filesSeen(),
        "filesIngested", res.filesIngested(),
        "chunksInserted", res.chunksInserted()
    );
  }
}

