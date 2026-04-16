package local.rag.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;

public class TextExtractor {
  public static String extract(Path path) throws IOException {
    String name = path.getFileName().toString().toLowerCase();
    if (name.endsWith(".pdf")) return extractPdf(path);
    if (name.endsWith(".html") || name.endsWith(".htm")) return extractHtml(path);
    // txt / md / json / others -> read as UTF-8 best-effort
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  private static String extractPdf(Path path) throws IOException {
    try (PDDocument doc = Loader.loadPDF(path.toFile())) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(doc);
    }
  }

  private static String extractHtml(Path path) throws IOException {
    String html = Files.readString(path, StandardCharsets.UTF_8);
    return Jsoup.parse(html).text();
  }
}

