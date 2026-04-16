package local.rag.ingest;

import java.util.ArrayList;
import java.util.List;

public class Chunker {
  public static List<String> chunk(String text, int maxChars, int overlapChars) {
    if (text == null) return List.of();
    String t = normalize(text);
    if (t.isBlank()) return List.of();

    List<String> paras = splitParas(t);
    List<String> out = new ArrayList<>();

    StringBuilder cur = new StringBuilder();
    for (String p : paras) {
      if (p.isBlank()) continue;
      if (cur.length() + p.length() + 1 <= maxChars) {
        if (!cur.isEmpty()) cur.append('\n');
        cur.append(p);
        continue;
      }

      if (!cur.isEmpty()) {
        out.add(cur.toString().strip());
        cur.setLength(0);
      }

      // If a single paragraph is huge, hard-split
      if (p.length() > maxChars) {
        int start = 0;
        while (start < p.length()) {
          int end = Math.min(p.length(), start + maxChars);
          out.add(p.substring(start, end).strip());
          start = Math.max(end - overlapChars, end);
        }
      } else {
        cur.append(p);
      }
    }

    if (!cur.isEmpty()) out.add(cur.toString().strip());
    return out.stream().filter(s -> !s.isBlank()).toList();
  }

  private static List<String> splitParas(String t) {
    // Split by blank lines or strong separators
    String[] parts = t.split("(\\r?\\n\\s*\\r?\\n)+|[\\u3000]{4,}");
    List<String> out = new ArrayList<>();
    for (String p : parts) {
      String s = p.strip();
      if (!s.isBlank()) out.add(s);
    }
    return out.isEmpty() ? List.of(t) : out;
  }

  private static String normalize(String t) {
    return t
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .replace("\r", "\n");
  }
}

