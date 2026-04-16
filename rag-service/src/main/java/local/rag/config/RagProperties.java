package local.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
  private Opensearch opensearch = new Opensearch();
  private Postgres postgres = new Postgres();
  private Retrieval retrieval = new Retrieval();
  private Ollama ollama = new Ollama();

  public Opensearch getOpensearch() {
    return opensearch;
  }

  public void setOpensearch(Opensearch opensearch) {
    this.opensearch = opensearch;
  }

  public Postgres getPostgres() {
    return postgres;
  }

  public void setPostgres(Postgres postgres) {
    this.postgres = postgres;
  }

  public Retrieval getRetrieval() {
    return retrieval;
  }

  public void setRetrieval(Retrieval retrieval) {
    this.retrieval = retrieval;
  }

  public Ollama getOllama() {
    return ollama;
  }

  public void setOllama(Ollama ollama) {
    this.ollama = ollama;
  }

  public static class Opensearch {
    private String url;
    private String index;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getIndex() {
      return index;
    }

    public void setIndex(String index) {
      this.index = index;
    }
  }

  public static class Postgres {
    private String jdbcUrl;
    private String user;
    private String password;

    public String getJdbcUrl() {
      return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
      this.jdbcUrl = jdbcUrl;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  public static class Retrieval {
    private int bm25TopK = 5;

    public int getBm25TopK() {
      return bm25TopK;
    }

    public void setBm25TopK(int bm25TopK) {
      this.bm25TopK = bm25TopK;
    }
  }

  public static class Ollama {
    private String url;
    private String defaultModel;
    private String deepModel;
    private String embeddingModel;
    private int embeddingDim = 1024;
    private int timeoutSeconds = 120;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getDefaultModel() {
      return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
      this.defaultModel = defaultModel;
    }

    public String getDeepModel() {
      return deepModel;
    }

    public void setDeepModel(String deepModel) {
      this.deepModel = deepModel;
    }

    public String getEmbeddingModel() {
      return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
      this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDim() {
      return embeddingDim;
    }

    public void setEmbeddingDim(int embeddingDim) {
      this.embeddingDim = embeddingDim;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }
}

