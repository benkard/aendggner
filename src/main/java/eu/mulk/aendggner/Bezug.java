// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Woher eine Eingabe kommt: aus dem Dateisystem, aus dem Netz oder aus dem Bundesanzeiger-Portal.
 *
 * <p>Das Handbuch verlangte bislang, das Norm-XML des Bundes von Hand herunterzuladen, obgleich
 * seine Anschrift aus der Abkürzung des Gesetzes folgt. Die Handarbeit ist damit keine Leistung des
 * Benutzers, sondern eine Zumutung; sie entfällt.
 *
 * <p>Diese Klasse gehört allein der Befehlszeile. Die Browserfassung darf sie nicht anrühren: Dort
 * gibt es kein Dateisystem, und der Wagen des Benutzers verweigert einer fremden Anschrift ohnehin
 * die Auskunft. Die Kernpipeline kennt deshalb nur {@link Quelle} — Name und Bytes.
 */
public final class Bezug {

  private static final org.jboss.logging.Logger log =
      org.jboss.logging.Logger.getLogger(Bezug.class);

  /** gesetze-im-internet.de gibt jedes Werk unter seiner Kennung als Archiv aus. */
  private static final String GII = "https://www.gesetze-im-internet.de/%s/xml.zip";

  /** Das Verzeichnis sämtlicher Werke des Portals; es nennt zu jedem Titel dessen Anschrift. */
  private static final String GII_VERZEICHNIS = "https://www.gesetze-im-internet.de/gii-toc.xml";

  private static final java.util.regex.Pattern GII_LINK =
      java.util.regex.Pattern.compile(
          "<link>https?://www\\.gesetze-im-internet\\.de/([^/]+)/xml\\.zip</link>");

  private static final Duration FRIST = Duration.ofSeconds(60);

  static {
    // Ohne dies verweigert die Laufzeit das einfache Kennwort gegenüber einem Vermittler, sobald
    // der Weg verschlüsselt ist. In einer Behörde führt jeder Weg über einen solchen Vermittler,
    // und das Kennwort steht ohnehin in der Umgebung; die Sperre schützt hier niemanden.
    if (System.getProperty("jdk.http.auth.tunneling.disabledSchemes") == null) {
      System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
    }
  }

  private Bezug() {}

  /**
   * Die Eingabe, wie sie angegeben ist: ein Dateipfad, eine Netzanschrift {@code http(s)://…} oder
   * die Kurzform {@code gii:<abkürzung>} für das Bundesrecht ({@code gii:uwg}).
   *
   * <p>Der Dateipfad hat den Vorrang: Wer eine Datei „gii:etwas“ nennt, meint sie. Geladenes wird
   * unter {@code ~/.cache/aendggner} abgelegt und beim nächsten Lauf von dort genommen — ein
   * zweiter Lauf mit denselben Eingaben soll das Netz nicht abermals behelligen.
   */
  public static Quelle hole(String angabe) throws IOException, InterruptedException {
    var alsPfad = Path.of(angabe);
    if (Files.exists(alsPfad)) {
      return Quelle.lies(alsPfad);
    }
    if (angabe.startsWith("gii:")) {
      var kurz = kennung(angabe.substring("gii:".length()));
      if (kurz.isEmpty()) {
        throw new IOException("„" + angabe + "“ nennt keine Kennung (etwa „gii:uwg_2004“).");
      }
      try {
        return lade(GII.formatted(kurz), kurz + "-xml.zip");
      } catch (NichtGefunden nichtGefunden) {
        var gefunden = sucheImVerzeichnis(kurz);
        return lade(GII.formatted(gefunden), gefunden + "-xml.zip");
      }
    }
    if (angabe.startsWith("http://") || angabe.startsWith("https://")) {
      return lade(angabe, dateiname(angabe));
    }
    // Kein Pfad, keine Anschrift: Quelle.lies meldet den fehlenden Pfad mit dem üblichen Wortlaut.
    return Quelle.lies(alsPfad);
  }

  private static Quelle lade(String anschrift, String name)
      throws IOException, InterruptedException {
    var abgelegt = zwischenspeicher(anschrift, name);
    if (abgelegt != null && Files.exists(abgelegt)) {
      return new Quelle(name, Files.readAllBytes(abgelegt));
    }

    try (var client = baueClient()) {
      var antwort =
          client.send(
              HttpRequest.newBuilder(URI.create(anschrift)).timeout(FRIST).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (antwort.statusCode() == 404) {
        throw new NichtGefunden("„" + anschrift + "“ gibt es nicht (Status 404).");
      }
      if (antwort.statusCode() != 200) {
        throw new IOException(
            "„" + anschrift + "“ antwortet mit dem Status " + antwort.statusCode() + ".");
      }
      if (abgelegt != null) {
        try {
          Files.createDirectories(abgelegt.getParent());
          Files.write(abgelegt, antwort.body());
        } catch (IOException nichtSchreibbar) {
          // Der Zwischenspeicher ist eine Bequemlichkeit, keine Bedingung: Ist das
          // Heimatverzeichnis
          // schreibgeschützt, so wird eben jedes Mal geladen.
          log.debugf("Zwischenspeicher %s nicht beschreibbar: %s", abgelegt, nichtSchreibbar);
        }
      }
      return new Quelle(name, antwort.body());
    }
  }

  /** Die Anschrift gibt es nicht — für die Kennung des Bundesrechts ein Anlass nachzuschlagen. */
  private static final class NichtGefunden extends IOException {
    NichtGefunden(String meldung) {
      super(meldung);
    }
  }

  /**
   * Die Kennung, wie das Portal sie führt: klein geschrieben, und was kein Buchstabe, keine Ziffer
   * und kein Bindestrich ist, wird zum Unterstrich. So wird aus der Abkürzung „UWG 2004“ die
   * Kennung „uwg_2004“ und aus „1-DM-GoldmünzG“ die Kennung „1-dm-goldm_nzg“.
   */
  static String kennung(String angabe) {
    return angabe.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9-]", "_");
  }

  /**
   * Sucht die Kennung im Verzeichnis des Portals, wenn die angegebene ins Leere führt.
   *
   * <p>Das Portal hängt an manche Kennung das Jahr der Fassung („uwg_2004“), und wer „uwg“ eingibt,
   * kann das nicht wissen. Eine Kennung, die mit der angegebenen beginnt, ist deshalb gemeint —
   * aber nur, wenn es genau eine gibt. Sonst wird nicht geraten, sondern aufgezählt.
   */
  private static String sucheImVerzeichnis(String kurz) throws IOException, InterruptedException {
    var verzeichnis =
        new String(
            lade(GII_VERZEICHNIS, "gii-toc.xml").inhalt(), java.nio.charset.StandardCharsets.UTF_8);
    var treffer = new java.util.LinkedHashSet<String>();
    var m = GII_LINK.matcher(verzeichnis);
    while (m.find()) {
      if (m.group(1).equals(kurz) || m.group(1).startsWith(kurz + "_")) {
        treffer.add(m.group(1));
      }
    }
    if (treffer.size() == 1) {
      return treffer.iterator().next();
    }
    if (treffer.isEmpty()) {
      throw new IOException(
          "Das Portal führt kein Werk unter der Kennung „"
              + kurz
              + "“. Die Kennung ist der letzte Teil seiner Anschrift auf gesetze-im-internet.de.");
    }
    throw new IOException(
        "Die Kennung „"
            + kurz
            + "“ ist mehrdeutig; das Portal führt "
            + String.join(", ", treffer)
            + ". Gemeint ist eine von ihnen.");
  }

  /**
   * Der Wagen samt Vermittler, wie ihn die Umgebung vorschreibt.
   *
   * <p>Die Laufzeit liest {@code HTTPS_PROXY} und Verwandte nicht von sich aus; auf allen übrigen
   * Werkzeugen der Kommandozeile ist das aber die gewohnte Angabe. Wer hinter einem Vermittler
   * sitzt — in der Verwaltung ist das die Regel —, soll nichts eigens einstellen müssen.
   */
  private static HttpClient baueClient() {
    var bauer = HttpClient.newBuilder().connectTimeout(FRIST);
    var vermittler = umgebung("HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy");
    if (vermittler != null) {
      try {
        var anschrift = URI.create(vermittler);
        if (anschrift.getHost() != null) {
          int hafen = anschrift.getPort() > 0 ? anschrift.getPort() : 8080;
          bauer.proxy(ProxySelector.of(new InetSocketAddress(anschrift.getHost(), hafen)));
          var kennung = anschrift.getUserInfo();
          if (kennung != null && kennung.contains(":")) {
            var teile = kennung.split(":", 2);
            bauer.authenticator(
                new Authenticator() {
                  @Override
                  protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(teile[0], teile[1].toCharArray());
                  }
                });
          }
        }
      } catch (IllegalArgumentException unbrauchbar) {
        // Eine unlesbare Angabe bleibt außer Betracht; der unmittelbare Weg wird versucht.
      }
    }
    return bauer.build();
  }

  private static @org.jspecify.annotations.Nullable String umgebung(String... namen) {
    for (var name : namen) {
      var wert = System.getenv(name);
      if (wert != null && !wert.isBlank()) {
        return wert;
      }
    }
    return null;
  }

  /**
   * Der Ort im Zwischenspeicher; {@code null}, wenn keiner zu bestimmen ist. Der Name trägt einen
   * Abdruck der Anschrift, damit zwei gleichnamige Werke verschiedener Herkunft sich nicht
   * verdrängen.
   */
  private static Path zwischenspeicher(String anschrift, String name) {
    var verzeichnis = System.getenv("XDG_CACHE_HOME");
    var heim = System.getProperty("user.home");
    if ((verzeichnis == null || verzeichnis.isBlank()) && (heim == null || heim.isBlank())) {
      return null;
    }
    var wurzel =
        verzeichnis != null && !verzeichnis.isBlank()
            ? Path.of(verzeichnis)
            : Path.of(heim, ".cache");
    return wurzel.resolve("aendggner").resolve(abdruck(anschrift) + "-" + name);
  }

  private static String abdruck(String anschrift) {
    try {
      var summe =
          MessageDigest.getInstance("SHA-256")
              .digest(anschrift.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(summe, 0, 8);
    } catch (java.security.NoSuchAlgorithmException unmoeglich) {
      throw new IllegalStateException(unmoeglich);
    }
  }

  /** Das letzte Glied der Anschrift, ohne Abfrageteil; sonst die Anschrift selbst. */
  static String dateiname(String anschrift) {
    var ohneAbfrage = anschrift.split("[?#]", 2)[0];
    int schrägstrich = ohneAbfrage.lastIndexOf('/');
    var letztes = schrägstrich >= 0 ? ohneAbfrage.substring(schrägstrich + 1) : ohneAbfrage;
    return letztes.isBlank() ? anschrift : letztes;
  }
}
