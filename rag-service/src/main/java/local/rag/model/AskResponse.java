package local.rag.model;

import java.util.List;

public class AskResponse {
  private String answer;
  private List<Citation> citations;
  private Trace trace;

  public AskResponse() {}

  public AskResponse(String answer, List<Citation> citations, Trace trace) {
    this.answer = answer;
    this.citations = citations;
    this.trace = trace;
  }

  public String getAnswer() {
    return answer;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }

  public List<Citation> getCitations() {
    return citations;
  }

  public void setCitations(List<Citation> citations) {
    this.citations = citations;
  }

  public Trace getTrace() {
    return trace;
  }

  public void setTrace(Trace trace) {
    this.trace = trace;
  }

  public static class Trace {
    private int bm25Hits;

    public Trace() {}

    public Trace(int bm25Hits) {
      this.bm25Hits = bm25Hits;
    }

    public int getBm25Hits() {
      return bm25Hits;
    }

    public void setBm25Hits(int bm25Hits) {
      this.bm25Hits = bm25Hits;
    }
  }
}

