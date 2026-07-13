package eu.mulk.aendggner.aenderung.parse;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.jboss.logging.Logger;

/**
 * Extrahiert PDF-Text unter Ausschluss von Kleingedrucktem: Textläufe, die deutlich kleiner gesetzt
 * sind als die dominante Brotschrift, werden verworfen. Das entfernt Fußnotenblöcke und
 * hochgestellte Fußnotenziffern („Wettbewerb¹“), die im neuen BGBl-Format sonst mitten im
 * Gesetzestext — auch mitten in Zitaten — landen würden.
 *
 * <p>Zwei Pässe: Der erste ermittelt die zeichenhäufigste Fontgröße, der zweite lässt nur Läufe
 * durch, deren mittlere Größe nicht deutlich darunter liegt (Überschriften sind größer und bleiben
 * erhalten). Ohne klar dominante Brotschrift wird nicht gefiltert.
 */
final class FontgroessenFilter {

  private static final Logger log = Logger.getLogger(FontgroessenFilter.class);

  /** Läufe, die um mehr als diese Punktzahl unter der Brotschrift liegen, sind Kleingedrucktes. */
  private static final float TOLERANZ_PT = 1.4f;

  /** Anteil an allen Zeichen, ab dem eine Fontgröße als dominant gilt. */
  private static final double DOMINANZ_SCHWELLE = 0.5;

  private FontgroessenFilter() {}

  static String extrahiere(PDDocument dokument) throws IOException {
    var zaehler = new GroessenZaehler();
    zaehler.setLineSeparator("\n");
    var wegwerf = new StringWriter();
    zaehler.writeText(dokument, wegwerf);

    var brotschrift = zaehler.dominanteGroesse();
    if (brotschrift == null) {
      log.debugf("Keine dominante Fontgröße; Kleingedrucktes wird nicht gefiltert.");
      return wegwerf.toString();
    }
    log.debugf("Brotschriftgröße: %.1f pt", brotschrift);

    var filter = new GroessenFilterStripper(brotschrift - TOLERANZ_PT);
    filter.setLineSeparator("\n");
    var ausgabe = new StringWriter();
    filter.writeText(dokument, ausgabe);
    return ausgabe.toString();
  }

  /** Pass 1: zeichengewichtete Häufigkeit der Fontgrößen (auf halbe Punkte gerundet). */
  private static final class GroessenZaehler extends PDFTextStripper {
    private final Map<Float, Integer> haeufigkeit = new HashMap<>();
    private long gesamt = 0;

    @Override
    protected void writeString(String text, List<TextPosition> positionen) throws IOException {
      for (var position : positionen) {
        var groesse = runde(position.getFontSizeInPt());
        haeufigkeit.merge(groesse, 1, Integer::sum);
        gesamt++;
      }
      super.writeString(text, positionen);
    }

    Float dominanteGroesse() {
      if (gesamt == 0) {
        return null;
      }
      var haeufigste =
          haeufigkeit.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
      if ((double) haeufigste.getValue() / gesamt < DOMINANZ_SCHWELLE) {
        return null;
      }
      return haeufigste.getKey();
    }
  }

  /** Pass 2: Läufe unterhalb der Schwelle verwerfen. */
  private static final class GroessenFilterStripper extends PDFTextStripper {
    private final float schwelle;

    GroessenFilterStripper(float schwelle) {
      this.schwelle = schwelle;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positionen) throws IOException {
      if (positionen.isEmpty()) {
        return;
      }
      float summe = 0;
      for (var position : positionen) {
        summe += position.getFontSizeInPt();
      }
      if (summe / positionen.size() < schwelle) {
        return; // Kleingedrucktes (Fußnote, hochgestellte Ziffer)
      }
      super.writeString(text, positionen);
    }
  }

  private static float runde(float groesse) {
    return Math.round(groesse * 2f) / 2f;
  }
}
