package eu.mulk.aendggner.aenderung.parse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
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

  private final Tika tika = new Tika();

  public String extrahiere(Path datei) throws IOException {
    var mimeType = tika.detect(datei);
    log.infof("Datei %s hat Typ %s.", datei, mimeType);

    return switch (mimeType) {
      case "application/pdf" -> extrahierePdf(datei);
      case "text/plain" -> Files.readString(datei, StandardCharsets.UTF_8);
      default ->
          throw new IOException(
              "Nicht unterstützter Dateityp %s für %s (unterstützt: PDF, Klartext)"
                  .formatted(mimeType, datei));
    };
  }

  private static String extrahierePdf(Path datei) throws IOException {
    try (var dokument = Loader.loadPDF(datei.toFile())) {
      var stripper = new PDFTextStripper();
      stripper.setSortByPosition(false);
      stripper.setLineSeparator("\n");
      stripper.setParagraphEnd("\n");
      return stripper.getText(dokument);
    }
  }
}
