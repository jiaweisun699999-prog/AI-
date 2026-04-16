package local.rag.model;

import jakarta.validation.constraints.NotBlank;

public class AskRequest {
  @NotBlank
  private String question;

  private String domain = "ashare";

  // Optional: allow UI to request a specific topK (bounded server-side later if needed).
  private Integer topK;

  // "fast" (default) or "deep"
  private String mode = "fast";

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public Integer getTopK() {
    return topK;
  }

  public void setTopK(Integer topK) {
    this.topK = topK;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }
}

