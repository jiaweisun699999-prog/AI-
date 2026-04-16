package local.rag.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OllamaClient {
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OllamaClient(ObjectMapper objectMapper) {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    this.objectMapper = objectMapper;
  }

  public String chat(String baseUrl, String model, List<Map<String, String>> messages, Duration timeout) throws Exception {
    URI uri = URI.create(trimTrailingSlash(baseUrl) + "/api/chat");
    var root = objectMapper.createObjectNode();
    root.put("model", model);
    root.put("stream", false);

    var msgArr = root.putArray("messages");
    for (Map<String, String> m : messages) {
      msgArr.add(objectMapper.createObjectNode()
          .put("role", m.getOrDefault("role", "user"))
          .put("content", m.getOrDefault("content", "")));
    }

    HttpRequest req = HttpRequest.newBuilder(uri)
        .timeout(timeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
        .build();

    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 300) {
      throw new IllegalStateException("Ollama chat failed: " + resp.statusCode() + " body=" + resp.body());
    }
    JsonNode json = objectMapper.readTree(resp.body());
    return json.path("message").path("content").asText("");
  }

  public float[] embed(String baseUrl, String model, String input, Duration timeout) throws Exception {
    // Prefer /api/embed (newer); fall back to /api/embeddings (older).
    String url = trimTrailingSlash(baseUrl);
    try {
      return embedViaEmbed(url, model, input, timeout);
    } catch (Exception ignored) {
      return embedViaEmbeddings(url, model, input, timeout);
    }
  }

  private float[] embedViaEmbed(String baseUrl, String model, String input, Duration timeout) throws Exception {
    URI uri = URI.create(baseUrl + "/api/embed");
    var root = objectMapper.createObjectNode();
    root.put("model", model);
    root.putArray("input").add(input);

    HttpRequest req = HttpRequest.newBuilder(uri)
        .timeout(timeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
        .build();

    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 300) {
      throw new IllegalStateException("Ollama embed failed: " + resp.statusCode() + " body=" + resp.body());
    }
    JsonNode json = objectMapper.readTree(resp.body());
    JsonNode arr = json.path("embeddings");
    if (!arr.isArray() || arr.isEmpty()) throw new IllegalStateException("Ollama /api/embed: missing embeddings");
    return toFloatArray(arr.get(0));
  }

  private float[] embedViaEmbeddings(String baseUrl, String model, String input, Duration timeout) throws Exception {
    URI uri = URI.create(baseUrl + "/api/embeddings");
    var root = objectMapper.createObjectNode();
    root.put("model", model);
    root.put("prompt", input);

    HttpRequest req = HttpRequest.newBuilder(uri)
        .timeout(timeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
        .build();

    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() >= 300) {
      throw new IllegalStateException("Ollama embeddings failed: " + resp.statusCode() + " body=" + resp.body());
    }
    JsonNode json = objectMapper.readTree(resp.body());
    JsonNode emb = json.path("embedding");
    if (!emb.isArray() || emb.isEmpty()) throw new IllegalStateException("Ollama /api/embeddings: missing embedding");
    return toFloatArray(emb);
  }

  private static float[] toFloatArray(JsonNode arr) {
    int n = arr.size();
    float[] out = new float[n];
    for (int i = 0; i < n; i++) out[i] = (float) arr.get(i).asDouble();
    return out;
  }

  private static String trimTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }
}

