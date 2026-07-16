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
 * <p>Die Brotschrift wird <b>je Seite</b> bestimmt: Entwürfe mischen Layouts (der Regelungstext der
 * Ministeriumsvorlagen ist kleiner gesetzt als der seitenstarke Begründungsteil); eine dokumentweit
 * dominante Größe würde dort den gesamten Gesetzestext als „Kleingedrucktes“ verwerfen. Zwei Pässe:
 * Der erste ermittelt die zeichenhäufigste Fontgröße jeder Seite, der zweite lässt nur Läufe durch,
 * deren mittlere Größe nicht deutlich darunter liegt (Überschriften sind größer und bleiben
 * erhalten). Seiten ohne klar dominante Brotschrift werden nicht gefiltert.
 */
final class FontgroessenFilter {

  private static final Logger log = Logger.getLogger(FontgroessenFilter.class);

  /** Läufe, die um mehr als diese Punktzahl unter der Brotschrift liegen, sind Kleingedrucktes. */
  private static final float TOLERANZ_PT = 1.4f;

  /** Anteil an den Zeichen einer Seite, ab dem eine Fontgröße unstrittig die Brotschrift ist.
   * Bewusst über 50 %: Auf halb/halb geteilten Seiten (Befehle oben, langer Fußnotenblock unten)
   * darf nicht das Kleingedruckte durch eine hauchdünne Mehrheit gewinnen. */
  private static final double DOMINANZ_SCHWELLE = 0.6;

  /**
   * Erreicht keine Größe die absolute Mehrheit (fußnotenlastige Seiten), gewinnt die <em>größte</em>
   * Größe mit diesem Mindestanteil: Kleingedrucktes kann die Zeichenmehrheit stellen, ist aber nie
   * größer gesetzt als die Brotschrift.
   */
  private static final double KANDIDATEN_SCHWELLE = 0.25;

  private FontgroessenFilter() {}

  static String extrahiere(PDDocument dokument) throws IOException {
    var zaehler = new GroessenZaehler();
    zaehler.setLineSeparator("\n");
    var wegwerf = new StringWriter();
    zaehler.writeText(dokument, wegwerf);

    var schwellen = zaehler.schwellenProSeite();
    if (schwellen.isEmpty()) {
      log.debugf("Keine dominanten Fontgrößen; Kleingedrucktes wird nicht gefiltert.");
      return wegwerf.toString();
    }
    log.debugf("Brotschriftgrößen (je Seite): %s", schwellen);

    var filter = new GroessenFilterStripper(schwellen, zaehler.brotschriftUntergrenzen(schwellen));
    filter.setLineSeparator("\n");
    var ausgabe = new StringWriter();
    filter.writeText(dokument, ausgabe);
    return ausgabe.toString();
  }

  /** Anteil der Seitenhöhe, unterhalb dessen Brotschrift-Text als Seitenfuß (Kolumnentitel)
   * gilt und die Fußnotengrenze nicht nach unten ziehen darf. */
  private static final float SEITENFUSS_BEREICH = 0.92f;

  /** Pass 1: zeichengewichtete Häufigkeit der Fontgrößen (auf halbe Punkte gerundet) je Seite. */
  private static final class GroessenZaehler extends PDFTextStripper {
    private final Map<Integer, Map<Float, Integer>> haeufigkeit = new HashMap<>();
    private final Map<Integer, Map<Float, Float>> maxY = new HashMap<>();

    @Override
    protected void writeString(String text, List<TextPosition> positionen) throws IOException {
      var seite = haeufigkeit.computeIfAbsent(getCurrentPageNo(), s -> new HashMap<>());
      var seitenMaxY = maxY.computeIfAbsent(getCurrentPageNo(), s -> new HashMap<>());
      var fussbereich = SEITENFUSS_BEREICH * getCurrentPage().getMediaBox().getHeight();
      for (var position : positionen) {
        var groesse = runde(position.getFontSizeInPt());
        seite.merge(groesse, 1, Integer::sum);
        // Seitenfüße (Drucksachennummer u.ä. am Blattrand) zählen nicht als unterste
        // Brotschriftzeile — sonst blieben Fußnotenblöcke oberhalb davon erhalten.
        if (position.getYDirAdj() <= fussbereich) {
          seitenMaxY.merge(groesse, position.getYDirAdj(), Math::max);
        }
      }
      super.writeString(text, positionen);
    }

    /**
     * Filter-Schwelle je Seite: die größte Fontgröße mit nennenswertem Zeichenanteil bestimmt die
     * Brotschrift der Seite. Seiten ohne eigene Brotschrift erben die dokumentweite; fehlt auch
     * die, bleibt die Seite ungefiltert.
     */
    Map<Integer, Float> schwellenProSeite() {
      var schwellen = new HashMap<Integer, Float>();
      var dokumentweit = new HashMap<Float, Long>();
      long dokumentGesamt = 0;
      for (var eintrag : haeufigkeit.entrySet()) {
        long gesamt = eintrag.getValue().values().stream().mapToLong(Integer::longValue).sum();
        if (gesamt == 0) {
          continue;
        }
        for (var groessenEintrag : eintrag.getValue().entrySet()) {
          dokumentweit.merge(groessenEintrag.getKey(), (long) groessenEintrag.getValue(), Long::sum);
        }
        dokumentGesamt += gesamt;
        var brotschrift = groessterKandidat(eintrag.getValue(), gesamt);
        if (brotschrift != null) {
          schwellen.put(eintrag.getKey(), brotschrift - TOLERANZ_PT);
        }
      }
      if (dokumentGesamt > 0) {
        var zaehlungen = new HashMap<Float, Integer>();
        for (var eintrag : dokumentweit.entrySet()) {
          zaehlungen.put(eintrag.getKey(), Math.toIntExact(Math.min(Integer.MAX_VALUE, eintrag.getValue())));
        }
        var global = groessterKandidat(zaehlungen, dokumentGesamt);
        if (global != null) {
          for (var seite : haeufigkeit.keySet()) {
            schwellen.putIfAbsent(seite, global - TOLERANZ_PT);
          }
        }
      }
      return schwellen;
    }

    /**
     * Die Brotschrift einer Zählung: die Größe mit absoluter Mehrheit; sonst die größte Größe mit
     * mindestens {@link #KANDIDATEN_SCHWELLE} Zeichenanteil; sonst {@code null}.
     */
    private static @org.jspecify.annotations.Nullable Float groessterKandidat(
        Map<Float, Integer> zaehlung, long gesamt) {
      Float brotschrift = null;
      for (var eintrag : zaehlung.entrySet()) {
        double anteil = (double) eintrag.getValue() / gesamt;
        if (anteil >= DOMINANZ_SCHWELLE) {
          return eintrag.getKey();
        }
        if (anteil >= KANDIDATEN_SCHWELLE
            && (brotschrift == null || eintrag.getKey() > brotschrift)) {
          brotschrift = eintrag.getKey();
        }
      }
      return brotschrift;
    }

    /**
     * Die tiefste Position (größtes Y) von Brotschrift-Text je Seite: Kleingedrucktes unterhalb
     * davon ist ein Fußnotenblock, Kleingedrucktes darüber Satzspiegel-Inhalt (z.B. kleiner
     * gesetzte Zitatkästen der Bundesrats-Drucksachen).
     */
    Map<Integer, Float> brotschriftUntergrenzen(Map<Integer, Float> schwellen) {
      var grenzen = new HashMap<Integer, Float>();
      for (var eintrag : maxY.entrySet()) {
        var schwelle = schwellen.get(eintrag.getKey());
        if (schwelle == null) {
          continue;
        }
        float grenze = Float.NEGATIVE_INFINITY;
        for (var groessenEintrag : eintrag.getValue().entrySet()) {
          if (groessenEintrag.getKey() >= schwelle) {
            grenze = Math.max(grenze, groessenEintrag.getValue());
          }
        }
        if (grenze > Float.NEGATIVE_INFINITY) {
          grenzen.put(eintrag.getKey(), grenze);
        }
      }
      return grenzen;
    }
  }

  /**
   * Pass 2: Kleingedrucktes verwerfen — aber nur, wenn es unterhalb der letzten Brotschrift-Zeile
   * der Seite steht (Fußnotenblock) oder sehr deutlich unter der Brotschriftgröße liegt
   * (hochgestellte Fußnotenziffern). Bundesrats-Drucksachen setzen zitierten Gesetzestext
   * absichtlich etwas kleiner als die Brotschrift; solcher Text steht im Satzspiegel (oberhalb
   * der Grenze) und muss erhalten bleiben.
   */
  private static final class GroessenFilterStripper extends PDFTextStripper {

    /** Läufe, die um mehr als diese Punktzahl unter der Brotschrift liegen, sind immer Beiwerk. */
    private static final float STARK_KLEINER_PT = 3f;

    private final Map<Integer, Float> schwellen;
    private final Map<Integer, Float> untergrenzen;

    GroessenFilterStripper(Map<Integer, Float> schwellen, Map<Integer, Float> untergrenzen) {
      this.schwellen = schwellen;
      this.untergrenzen = untergrenzen;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positionen) throws IOException {
      var schwelle = schwellen.get(getCurrentPageNo());
      if (schwelle == null || positionen.isEmpty()) {
        super.writeString(text, positionen);
        return;
      }
      float groessenSumme = 0;
      float ySumme = 0;
      for (var position : positionen) {
        groessenSumme += position.getFontSizeInPt();
        ySumme += position.getYDirAdj();
      }
      float groesse = groessenSumme / positionen.size();
      if (groesse >= schwelle) {
        super.writeString(text, positionen);
        return;
      }
      var brotschrift = schwelle + TOLERANZ_PT;
      var grenze = untergrenzen.get(getCurrentPageNo());
      boolean unterDerBrotschrift = grenze != null && ySumme / positionen.size() > grenze;
      if (unterDerBrotschrift || groesse < brotschrift - STARK_KLEINER_PT) {
        return; // Fußnotenblock bzw. hochgestellte Ziffer
      }
      super.writeString(text, positionen);
    }
  }

  private static float runde(float groesse) {
    return Math.round(groesse * 2f) / 2f;
  }
}
