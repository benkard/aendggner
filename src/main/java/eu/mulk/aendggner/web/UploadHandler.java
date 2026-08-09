package eu.mulk.aendggner.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import eu.mulk.aendggner.Pipeline;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@code POST /synopse} — nimmt Stammgesetz- und Änderungsgesetz-Datei(en) per
 * {@code multipart/form-data} entgegen, ruft {@link Pipeline#erzeugeSynopse} auf und liefert das
 * erzeugte HTML zurück.
 *
 * <p>Hochgeladene Dateien landen ausschließlich als temporäre Dateien für die Dauer der Anfrage
 * und werden danach in jedem Fall gelöscht — es wird nichts dauerhaft gespeichert. Die eigentliche
 * Verarbeitung läuft auf einem auf die Kernzahl begrenzten Thread-Pool mit fester Warteschlange;
 * bei Überlast wird sofort mit {@code 503} abgelehnt, statt unbegrenzt Arbeit anzunehmen.
 */
final class UploadHandler implements HttpHandler {

  private static final long MAX_PART_BYTES = 15L * 1024 * 1024; // 15 MB je Datei
  private static final long MAX_BODY_BYTES = 40L * 1024 * 1024; // Sicherheitsnetz für die gesamte Anfrage
  private static final long TIMEOUT_SECONDS = 30;

  private final ExecutorService pipelinePool;

  UploadHandler() {
    var poolSize = Math.max(1, Runtime.getRuntime().availableProcessors());
    this.pipelinePool =
        new ThreadPoolExecutor(
            poolSize,
            poolSize,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(poolSize),
            new ThreadPoolExecutor.AbortPolicy());
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    var tempDirs = new ArrayList<Path>();
    try {
      handleInternal(exchange, tempDirs);
    } catch (Throwable e) {
      // Ohne diesen Fang schließt com.sun.net.httpserver die Verbindung bei einer
      // unerwarteten Exception kommentarlos ("Empty reply from server" beim Client).
      e.printStackTrace();
      sendText(exchange, 500, "Unerwarteter Fehler bei der Verarbeitung.");
    } finally {
      for (var tempDir : tempDirs) {
        deleteRecursively(tempDir);
      }
      exchange.close();
    }
  }

  private static void deleteRecursively(Path dir) {
    try (var files = Files.walk(dir)) {
      files
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // Aufräumen ist best effort; ein verwaistes Temp-File blockiert die Antwort nicht.
                }
              });
    } catch (IOException ignored) {
      // Verzeichnis existiert eventuell schon nicht mehr (z. B. bei frühem Abbruch) — egal.
    }
  }

  private void handleInternal(HttpExchange exchange, ArrayList<Path> tempDirs) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Methode nicht erlaubt.");
      return;
    }

    var boundary = Multipart.extraktBoundary(exchange.getRequestHeaders().getFirst("Content-Type"));
    if (boundary == null) {
      sendText(exchange, 400, "Ungültige Anfrage: multipart/form-data mit boundary erwartet.");
      return;
    }

    byte[] body;
    try {
      body = readLimited(exchange.getRequestBody(), MAX_BODY_BYTES);
    } catch (PayloadTooLargeException e) {
      sendText(
          exchange,
          413,
          "Die Anfrage ist zu groß (Limit: " + (MAX_BODY_BYTES / 1024 / 1024) + " MB insgesamt).");
      return;
    }

    var parts = Multipart.parse(body, boundary);

    Path stammFile = null;
    var aenderungFiles = new ArrayList<Path>();
    var vollstaendig = false;

    for (var part : parts) {
      if ("vollstaendig".equals(part.name())) {
        var value = new String(part.data(), StandardCharsets.UTF_8).trim();
        vollstaendig = !value.isEmpty() && !value.equals("off") && !value.equals("false");
        continue;
      }
      if (part.filename() == null || part.filename().isBlank() || part.data().length == 0) {
        continue;
      }
      if (part.data().length > MAX_PART_BYTES) {
        sendText(
            exchange,
            413,
            "Die Datei „"
                + part.filename()
                + "“ ist zu groß (Limit: "
                + (MAX_PART_BYTES / 1024 / 1024)
                + " MB).");
        return;
      }

      // Jede Datei bekommt ein eigenes Temp-Verzeichnis, damit sie unter ihrem ursprünglichen
      // Namen abgelegt werden kann (erscheint so in der "Quelle"-Zeile der Synopse) und
      // gleichnamige Uploads sich nicht überschreiben.
      var tempDir = Files.createTempDirectory("aendggner-");
      tempDirs.add(tempDir);
      var tempFile = tempDir.resolve(sanitize(part.filename()));
      Files.write(tempFile, part.data());

      if ("stamm".equals(part.name())) {
        stammFile = tempFile;
      } else if ("aenderung".equals(part.name())) {
        aenderungFiles.add(tempFile);
      }
    }

    if (stammFile == null) {
      sendText(exchange, 400, "Bitte ein Stammgesetz hochladen.");
      return;
    }
    if (aenderungFiles.isEmpty()) {
      sendText(exchange, 400, "Bitte mindestens ein Änderungsgesetz hochladen.");
      return;
    }

    var finalStammFile = stammFile;
    var finalVollstaendig = vollstaendig;
    Callable<Pipeline.Ergebnis> job =
        () -> Pipeline.erzeugeSynopse(finalStammFile, aenderungFiles, null, finalVollstaendig);

    Pipeline.Ergebnis ergebnis;
    try {
      var future = pipelinePool.submit(job);
      ergebnis = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (RejectedExecutionException e) {
      sendText(exchange, 503, "Der Dienst ist gerade ausgelastet. Bitte in Kürze erneut versuchen.");
      return;
    } catch (TimeoutException e) {
      sendText(exchange, 504, "Die Verarbeitung hat zu lange gedauert und wurde abgebrochen.");
      return;
    } catch (ExecutionException e) {
      var cause = e.getCause();
      var message = cause != null ? cause.getMessage() : e.getMessage();
      sendText(
          exchange,
          422,
          "Verarbeitung fehlgeschlagen: " + (message == null ? "unbekannter Fehler." : message));
      return;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      sendText(exchange, 500, "Anfrage wurde unterbrochen.");
      return;
    }

    var responseBody = ergebnis.html().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(200, responseBody.length);
    try (var os = exchange.getResponseBody()) {
      os.write(responseBody);
    }
  }

  private static byte[] readLimited(InputStream in, long limit) throws IOException {
    var buffer = new ByteArrayOutputStream();
    var chunk = new byte[8192];
    long total = 0;
    int read;
    while ((read = in.read(chunk)) != -1) {
      total += read;
      if (total > limit) {
        throw new PayloadTooLargeException();
      }
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }

  private static String sanitize(String filename) {
    return filename.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static void sendText(HttpExchange exchange, int status, String message) throws IOException {
    var body = message.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(status, body.length);
    try (var os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  private static final class PayloadTooLargeException extends IOException {}
}
