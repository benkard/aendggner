package eu.mulk.aendggner;

import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.SuperskriptModus;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Superskript;
import eu.mulk.aendggner.gesetz.gii.GiiXmlLoader;
import eu.mulk.aendggner.gesetz.land.LandesRechtLoader;
import eu.mulk.aendggner.synopse.HtmlRenderer;
import eu.mulk.aendggner.synopse.SynopseBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Kernpipeline: Stammgesetz laden → Änderungsgesetze parsen und anwenden → Synopse rendern.
 *
 * <p>Wird sowohl von der CLI ({@link AendGgner}) als auch vom Webserver (
 * {@code eu.mulk.aendggner.web.UploadHandler}) verwendet, damit die Anwendungslogik nur an einer
 * Stelle existiert.
 */
public final class Pipeline {

  private Pipeline() {}

  public record Ergebnis(
      String html,
      long anzahlAngewandt,
      long anzahlManuell,
      int anzahlGeaenderteNormen,
      int anzahlProtokollEintraege) {}

  public static Ergebnis erzeugeSynopse(
      Path baseFile, List<Path> patches, String artikel, boolean vollstaendig) throws Exception {
    var altesGesetz = ladeStammgesetz(baseFile);
    var extraktor = new PatchTextExtraktor(superskriptModus(altesGesetz));
    var parser = new AenderungsgesetzParser();

    var gesetz = altesGesetz;
    var protokoll = new ArrayList<BefehlAnwender.AngewandteAenderung>();
    var warnungen = new ArrayList<String>();
    var quellen = new ArrayList<String>();

    for (var file : patches) {
      var text = TextBereiniger.bereinige(extraktor.extrahiere(file));
      var parseErgebnis = parser.parse(text, gesetz, artikel);
      if (parseErgebnis.befehle().isEmpty()) {
        System.err.printf(
            "Warnung: in %s wurde kein auf %s anwendbarer Artikel gefunden.%n",
            file, gesetz.jurabk());
      }
      var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
      gesetz = anwendung.neu();
      protokoll.addAll(anwendung.protokoll());
      warnungen.addAll(parseErgebnis.warnungen());
      quellen.add(
          file.getFileName() + " (Artikel " + String.join(", ", parseErgebnis.artikel()) + ")");
    }

    var gesamtErgebnis = new BefehlAnwender.AnwendungsErgebnis(gesetz, protokoll);
    var synopse = SynopseBuilder.baue(altesGesetz, gesamtErgebnis, warnungen, vollstaendig);
    var quelle = baseFile.getFileName() + " + " + String.join(" + ", quellen);
    var html = HtmlRenderer.rendere(synopse, quelle);

    return new Ergebnis(
        html,
        gesamtErgebnis.anzahlAngewandt(),
        gesamtErgebnis.anzahlManuell(),
        synopse.eintraege().size(),
        protokoll.size());
  }

  /** Gii-XML → {@link GiiXmlLoader}; PDF/Klartext (Landesrecht) → {@link LandesRechtLoader}. */
  static Gesetz ladeStammgesetz(Path baseFile) throws Exception {
    return istGiiXml(baseFile) ? new GiiXmlLoader().load(baseFile) : new LandesRechtLoader().load(baseFile);
  }

  /**
   * Der Superskriptmodus folgt der Schreibweise des Stammgesetzes: Trägt es amtliche Satznummern
   * (bayerisches Landesrecht, Niedersachsen u.a.), werden auch die Änderungsgesetze mit
   * Superskript-Erhalt extrahiert, damit Zitate und Stammtext dieselbe Schreibweise tragen; sonst
   * (Bundesrecht, Länder ohne amtliche Satzzählung) sind hochgestellte Ziffern bloße
   * Fußnotenmarker und werden verworfen.
   */
  static SuperskriptModus superskriptModus(Gesetz gesetz) {
    for (var norm : gesetz.normen()) {
      for (var absatz : norm.absaetze()) {
        if (Superskript.traegtSatznummern(absatz.text())) {
          return SuperskriptModus.BEHALTEN;
        }
      }
    }
    return SuperskriptModus.ENTFERNEN;
  }

  static boolean istGiiXml(Path baseFile) throws IOException {
    var mimeType = new org.apache.tika.Tika().detect(baseFile);
    return mimeType.equals("application/xml") || mimeType.equals("text/xml");
  }
}
