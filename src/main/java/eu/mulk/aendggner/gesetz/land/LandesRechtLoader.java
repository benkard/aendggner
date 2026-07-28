package eu.mulk.aendggner.gesetz.land;

import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.SuperskriptModus;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.gesetz.Gesetz;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.Tika;
import org.jboss.logging.Logger;

/**
 * Liest ein Stammgesetz des Landesrechts aus der konsolidierten Fassung — als PDF oder als
 * kanonischer Klartext. Deckt bayerisches Landesrecht (Gliederung in {@code Art.}, amtliche
 * Satznummern) ebenso ab wie die {@code §}-gegliederten Gesetze der übrigen Länder; das Sigel
 * folgt je Norm aus dem Text (siehe {@link LandesRechtTextParser}).
 *
 * <p>PDFs durchlaufen dieselbe Aufbereitung wie Änderungsgesetze (Fontgrößen-Filter mit
 * Superskript-Erhalt, Textbereinigung); der bereinigte Lineartext ist zugleich das dokumentierte
 * Klartext-Format: Wo die PDF-Extraktion versagt oder nur eine andere Quelle verfügbar ist (etwa
 * eine archivierte HTML-Fassung), kann der Text von Hand aufbereitet und als {@code .txt}
 * eingespeist werden. Amtliche Satznummern und Fußnotenmarker stehen dabei als Unicode-Superskripte
 * im Text (¹Die freilebende Tierwelt …, Enteignung⁶)).
 */
public final class LandesRechtLoader {

  private static final Logger log = Logger.getLogger(LandesRechtLoader.class);

  private final Tika tika = new Tika();

  public Gesetz load(Path datei) throws IOException {
    var mimeType = tika.detect(datei);
    log.infof("Stammgesetz %s hat Typ %s.", datei, mimeType);

    var text =
        switch (mimeType) {
          case "application/pdf" ->
              nachSatzendeGetrennteNormkoepfe(
                  TextBereiniger.bereinige(
                      new PatchTextExtraktor(SuperskriptModus.BEHALTEN).extrahiere(datei)));
          // Auch der handgepflegte Klartext wird kanonisch zusammengesetzt (NFC), damit Stammtext
          // und Befehlstext gleich kodiert sind — der PDF-Zweig erledigt das über bereinige().
          case "text/plain" ->
              Normalizer.normalize(
                  Files.readString(datei, StandardCharsets.UTF_8), Normalizer.Form.NFC);
          default ->
              throw new IOException(
                  "Nicht unterstützter Dateityp %s für Stammgesetz %s (unterstützt: PDF, Klartext)"
                      .formatted(mimeType, datei));
        };
    return LandesRechtTextParser.parse(text);
  }

  /**
   * Stellt einen vom Zeilen-Reflow an das Satzende der Vornorm geklebten Normkopf („… verlangen.
   * Art. 17  Jagderlaubnis“) wieder auf eine eigene Zeile. Das doppelte Leerzeichen zwischen
   * Norm-Nummer und Titel unterscheidet den Normkopf von gewöhnlichen Querverweisen.
   */
  private static String nachSatzendeGetrennteNormkoepfe(String text) {
    return text.replaceAll("(?<=[.“?!])[ \\t]+(?=(?:§|Art\\.)\\s\\d+[a-z]?[ \\t]{2})", "\n");
  }
}
