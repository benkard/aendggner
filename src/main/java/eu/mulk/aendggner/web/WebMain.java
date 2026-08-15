package eu.mulk.aendggner.web;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Eigenständiger Einstiegspunkt für den ÄndGgner-Webserver.
 *
 * <p>Nutzt ausschließlich JDK-Bordmittel ({@link HttpServer}) — keine zusätzliche
 * Web-Framework-Abhängigkeit. TLS-Terminierung und Rate-Limiting übernimmt ein vorgeschalteter
 * Reverse Proxy (siehe {@code deploy/nginx-aendggner.conf}); dieser Prozess bindet standardmäßig
 * nur an {@code localhost}.
 */
public final class WebMain {

  private WebMain() {}

  public static void main(String... args) throws IOException {
    var port = Integer.parseInt(envOr("AENDGGNER_WEB_PORT", "8080"));
    var bindAddress = envOr("AENDGGNER_WEB_BIND", "127.0.0.1");

    var server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
    server.setExecutor(
        Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors() * 2)));

    server.createContext(
        "/", new StaticHandler("/eu/mulk/aendggner/web/index.html", "text/html; charset=utf-8"));
    server.createContext(
        "/impressum",
        new StaticHandler("/eu/mulk/aendggner/web/impressum.html", "text/html; charset=utf-8"));
    server.createContext(
        "/datenschutz",
        new StaticHandler("/eu/mulk/aendggner/web/datenschutz.html", "text/html; charset=utf-8"));
    server.createContext(
        "/style.css",
        new StaticHandler("/eu/mulk/aendggner/web/style.css", "text/css; charset=utf-8"));
    server.createContext("/synopse", new UploadHandler());

    server.start();
    System.out.printf("ÄndGgner-Webserver läuft auf http://%s:%d/%n", bindAddress, port);
  }

  private static String envOr(String name, String fallback) {
    var value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
