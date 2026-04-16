package local.rag.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchClient {
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OpenSearchClient(ObjectMapper objectMapper) {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    this.objectMapper = objectMapper;
  }

  public void ensureIndex(String baseUrl, String index) throws Exception {
    URI uri = URI.create(baseUrl + "/" + index);
    HttpRequest head = HttpRequest.newBuilder(uri)
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(10))
        .build();
    HttpResponse<Void> headResp = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
    if (headResp.statusCode() == 200) return;
    if (headResp.statusCode() != 404) {
      throw new IllegalStateException("OpenSearch index HEAD unexpected status: " + headResp.statusCode());
    }

    // Built-in analyzer for Chinese isn't available by default; use a lightweight ngram analyzer (works for CJK).
    String body = """
      {
        "settings": {
          "index": {
            "number_of_shards": 1,
            "number_of_replicas": 0
          },
          "analysis": {
            "tokenizer": {
              "cjk_ngram_tokenizer": {
                "type": "ngram",
                "min_gram": 2,
                "max_gram": 3
              }
            },
            "analyzer": {
              "cjk_ngram": {
                "type": "custom",
                "tokenizer": "cjk_ngram_tokenizer",
                "filter": ["lowercase"]
              }
            }
          }
        },
        "mappings": {
          "properties": {
            "id": { "type": "keyword" },
            "domain": { "type": "keyword" },
            "source": { "type": "keyword" },
            "title": { "type": "text", "analyzer": "cjk_ngram", "search_analyzer": "cjk_ngram" },
            "url": { "type": "keyword" },
            "publish_time": { "type": "date" },
            "content": { "type": "text", "analyzer": "cjk_ngram", "search_analyzer": "cjk_ngram" },
            "metadata": { "type": "object", "enabled": true }
          }
        }
      }
      """;

    HttpRequest put = HttpRequest.newBuilder(uri)
        .PUT(HttpRequest.BodyPublishers.ofString(body))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(15))
        .build();
    HttpResponse<String> putResp = httpClient.send(put, HttpResponse.BodyHandlers.ofString());
    if (putResp.statusCode() >= 300) {
      throw new IllegalStateException("OpenSearch index create failed: " + putResp.statusCode() + " body=" + putResp.body());
    }
  }

  public void refresh(String baseUrl, String index) throws Exception {
    URI uri = URI.create(baseUrl + "/" + index + "/_refresh");
    HttpRequest req = HttpRequest.newBuilder(uri)
        .POST(HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(15))
        .build();
    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 300) {
      throw new IllegalStateException("OpenSearch refresh failed: " + resp.statusCode() + " body=" + resp.body());
    }
  }

  public void indexDoc(String baseUrl, String index, UUID id, String jsonBody) throws Exception {
    URI uri = URI.create(baseUrl + "/" + index + "/_doc/" + id);
    HttpRequest req = HttpRequest.newBuilder(uri)
        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(15))
        .build();
    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 300) {
      throw new IllegalStateException("OpenSearch index doc failed: " + resp.statusCode() + " body=" + resp.body());
    }
  }

  public List<SearchHit> search(String baseUrl, String index, String query, String domain, int topK) throws Exception {
    URI uri = URI.create(baseUrl + "/" + index + "/_search");
    var reqRoot = objectMapper.createObjectNode();
    reqRoot.put("size", topK);

    var multiMatch = objectMapper.createObjectNode();
    multiMatch.put("query", query);
    multiMatch.putArray("fields").add("title^2").add("content");

    var must = objectMapper.createArrayNode();
    must.add(objectMapper.createObjectNode().set("multi_match", multiMatch));

    var filter = objectMapper.createArrayNode();
    filter.add(objectMapper.createObjectNode()
        .set("term", objectMapper.createObjectNode().put("domain", domain)));

    var bool = objectMapper.createObjectNode();
    bool.set("filter", filter);
    bool.set("must", must);

    reqRoot.set("query", objectMapper.createObjectNode().set("bool", bool));
    String body = objectMapper.writeValueAsString(reqRoot);

    HttpRequest req = HttpRequest.newBuilder(uri)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(15))
        .build();
    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 300) {
      throw new IllegalStateException("OpenSearch search failed: " + resp.statusCode() + " body=" + resp.body());
    }

    JsonNode root = objectMapper.readTree(resp.body());
    JsonNode hits = root.path("hits").path("hits");
    List<SearchHit> out = new ArrayList<>();
    for (JsonNode h : hits) {
      String id = h.path("_source").path("id").asText(null);
      double score = h.path("_score").asDouble(0.0);
      if (id == null || id.isBlank()) continue;
      out.add(new SearchHit(UUID.fromString(id), score));
    }
    return out;
  }
}

