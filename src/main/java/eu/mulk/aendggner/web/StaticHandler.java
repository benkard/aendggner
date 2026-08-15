package eu.mulk.aendggner.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Liefert eine einzelne, beim Start einmal geladene statische Ressource aus (Formular, Impressum,
 * CSS, …).
 */
final class StaticHandler implements HttpHandler {

  private final byte[] content;
  private final String contentType;

  StaticHandler(String resourcePath, String contentType) {
    try (InputStream in = StaticHandler.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Ressource nicht gefunden: " + resourcePath);
      }
      this.content = in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Ressource konnte nicht geladen werden: " + resourcePath, e);
    }
    this.contentType = contentType;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      if (!"GET".equals(exchange.getRequestMethod())) {
        sendText(exchange, 405, "Methode nicht erlaubt.");
        return;
      }
      // com.sun.net.httpserver routet nach dem längsten passenden Präfix; ohne diesen Vergleich
      // würde z. B. "/irgendwas" auf den Kontext "/" fallen und fälschlich die Startseite liefern.
      if (!exchange.getRequestURI().getPath().equals(exchange.getHttpContext().getPath())) {
        sendText(exchange, 404, "Nicht gefunden.");
        return;
      }
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.sendResponseHeaders(200, content.length);
      try (var os = exchange.getResponseBody()) {
        os.write(content);
      }
    } finally {
      exchange.close();
    }
  }

  private static void sendText(HttpExchange exchange, int status, String message)
      throws IOException {
    var body = message.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, body.length);
    try (var os = exchange.getResponseBody()) {
      os.write(body);
    }
  }
}
