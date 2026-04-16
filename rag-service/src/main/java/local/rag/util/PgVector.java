package local.rag.util;

public class PgVector {
  public static String toVectorLiteral(float[] v) {
    if (v == null) return "[]";
    StringBuilder sb = new StringBuilder(v.length * 8);
    sb.append('[');
    for (int i = 0; i < v.length; i++) {
      if (i > 0) sb.append(',');
      // Keep a reasonable precision
      sb.append(String.format(java.util.Locale.ROOT, "%.6f", v[i]));
    }
    sb.append(']');
    return sb.toString();
  }
}

