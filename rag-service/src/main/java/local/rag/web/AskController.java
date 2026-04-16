package local.rag.web;

import jakarta.validation.Valid;
import local.rag.model.AskRequest;
import local.rag.model.AskResponse;
import local.rag.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {
  private final RagService ragService;

  public AskController(RagService ragService) {
    this.ragService = ragService;
  }

  @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public AskResponse ask(@Valid @RequestBody AskRequest req) throws Exception {
    return ragService.ask(req.getQuestion(), req.getDomain(), req.getTopK(), req.getMode());
  }
}

