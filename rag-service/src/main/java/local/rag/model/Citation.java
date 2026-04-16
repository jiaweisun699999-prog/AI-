package local.rag.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class Citation {
  private UUID id;
  private String title;
  private String source;
  private Instant publishTime;
  private String snippet;
  private String url;
  private Map<String, Object> metadata;

  public Citation() {}

  public Citation(UUID id, String title, String source, Instant publishTime, String snippet, String url, Map<String, Object> metadata) {
    this.id = id;
    this.title = title;
    this.source = source;
    this.publishTime = publishTime;
    this.snippet = snippet;
    this.url = url;
    this.metadata = metadata;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Instant getPublishTime() {
    return publishTime;
  }

  public void setPublishTime(Instant publishTime) {
    this.publishTime = publishTime;
  }

  public String getSnippet() {
    return snippet;
  }

  public void setSnippet(String snippet) {
    this.snippet = snippet;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }
}

