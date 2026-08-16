package eu.mulk.aendggner.gesetz.land;

import eu.mulk.aendggner.DateiTyp;
import eu.mulk.aendggner.Quelle;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.SuperskriptModus;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.gesetz.Gesetz;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import org.jboss.logging.Logger;

/**
 * Liest ein Stammgesetz des Landesrechts aus der konsolidierten Fassung — als PDF oder als
 * kanonischer Klartext. Deckt bayerisches Landesrecht (Gliederung in {@code Art.}, amtliche
 * Satznummern) ebenso ab wie die {@code §}-gegliederten Gesetze der übrigen Länder; das Sigel folgt
 * je Norm aus dem Text (siehe {@link LandesRechtTextParser}).
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

  /** Bequemlichkeit für Befehlszeile und Tests; im Browser gibt es keine {@link Path}e. */
  public Gesetz load(Path datei) throws IOException {
    return load(Quelle.lies(datei));
  }

  public Gesetz load(Quelle quelle) throws IOException {
    var typ = DateiTyp.erkenne(quelle.inhalt());
    log.infof("Stammgesetz %s hat Typ %s.", quelle.name(), typ.anzeigeName());

    var text =
        switch (typ) {
          case PDF ->
              nachSatzendeGetrennteNormkoepfe(
                  TextBereiniger.bereinige(
                      new PatchTextExtraktor(SuperskriptModus.BEHALTEN).extrahiere(quelle)));
          // Auch der handgepflegte Klartext wird kanonisch zusammengesetzt (NFC), damit Stammtext
          // und Befehlstext gleich kodiert sind — der PDF-Zweig erledigt das über bereinige().
          case KLARTEXT ->
              Normalizer.normalize(
                  new String(quelle.inhalt(), StandardCharsets.UTF_8), Normalizer.Form.NFC);
          case XML, ZIP ->
              throw new IOException(
                  "Nicht unterstützter Dateityp %s für Stammgesetz %s (unterstützt: PDF, Klartext)"
                      .formatted(typ.anzeigeName(), quelle.name()));
        };
    return LandesRechtTextParser.parse(text);
  }

  /**
   * Stellt einen vom Zeilen-Reflow an das Satzende der Vornorm geklebten Normkopf („… verlangen.
   * Art. 17 Jagderlaubnis“) wieder auf eine eigene Zeile. Das doppelte Leerzeichen zwischen
   * Norm-Nummer und Titel unterscheidet den Normkopf von gewöhnlichen Querverweisen.
   */
  private static String nachSatzendeGetrennteNormkoepfe(String text) {
    return text.replaceAll("(?<=[.“?!])[ \\t]+(?=(?:§|Art\\.)\\s\\d+[a-z]?[ \\t]{2})", "\n");
  }
}
