package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.DateiTyp;
import eu.mulk.aendggner.Quelle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.jboss.logging.Logger;

/**
 * Extrahiert den linearen Text eines Änderungsgesetzes aus einer Eingabedatei.
 *
 * <p>Klartextdateien werden wörtlich übernommen (der dokumentierte Ausweg, wenn die PDF-Extraktion
 * versagt). PDFs werden mit PDFBox in Content-Stream-Reihenfolge extrahiert — BGBl-PDFs zeichnen
 * ihren Text spaltenweise, sodass die Stream-Reihenfolge in der Regel der Lesereihenfolge
 * entspricht, während Positionssortierung die beiden Spalten verschränken würde.
 */
public final class PatchTextExtraktor {

  private static final Logger log = Logger.getLogger(PatchTextExtraktor.class);

  private final SuperskriptModus superskriptModus;

  public PatchTextExtraktor() {
    this(SuperskriptModus.ENTFERNEN);
  }

  public PatchTextExtraktor(SuperskriptModus superskriptModus) {
    this.superskriptModus = superskriptModus;
  }

  /** Bequemlichkeit für Befehlszeile und Tests; im Browser gibt es keine {@link Path}e. */
  public String extrahiere(Path datei) throws IOException {
    return extrahiere(Quelle.lies(datei));
  }

  public String extrahiere(Quelle quelle) throws IOException {
    var typ = DateiTyp.erkenne(quelle.inhalt());
    log.infof("Datei %s hat Typ %s.", quelle.name(), typ.anzeigeName());

    return switch (typ) {
      case PDF -> extrahierePdf(quelle.inhalt());
      case KLARTEXT -> new String(quelle.inhalt(), StandardCharsets.UTF_8);
      case XML ->
          throw new IOException(
              "Nicht unterstützter Dateityp %s für %s (unterstützt: PDF, Klartext)"
                  .formatted(typ.anzeigeName(), quelle.name()));
    };
  }

  private String extrahierePdf(byte[] inhalt) throws IOException {
    try (var dokument = Loader.loadPDF(inhalt)) {
      return FontgroessenFilter.extrahiere(dokument, superskriptModus);
    }
  }

  /**
   * Extrahiert die beiden Spalten einer zweispaltigen Seite getrennt und in Lesereihenfolge.
   *
   * <p>Nur für Layouts nötig, deren Spalten im Inhaltsstrom verschränkt stehen — die
   * Zusammenstellung einer Beschlussempfehlung. BGBl- und GVBl-Spalten kommen bereits nacheinander
   * und brauchen das nicht.
   *
   * @return links = Entwurfsspalte, rechts = Ausschussspalte.
   */
  public Spalten extrahiereSpalten(Path datei) throws IOException {
    return extrahiereSpalten(Quelle.lies(datei));
  }

  public Spalten extrahiereSpalten(Quelle quelle) throws IOException {
    try (var dokument = Loader.loadPDF(quelle.inhalt())) {
      return new Spalten(
          FontgroessenFilter.extrahiere(
              dokument, superskriptModus, FontgroessenFilter.Spalte.LINKS),
          FontgroessenFilter.extrahiere(
              dokument, superskriptModus, FontgroessenFilter.Spalte.RECHTS));
    }
  }

  public record Spalten(String links, String rechts) {}
}
