package eu.mulk.aendggner.aenderung.parse;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * <p>Zusätzlich klassifiziert der Filter jedes Zeilenende geometrisch als <b>weich</b>
 * (automatischer Blocksatz-Umbruch: die Zeile endet am lokalen rechten Satzspiegelrand) oder
 * <b>hart</b> (bewusstes Zeilenende: deutlich davor) und markiert es mit {@link
 * TextBereiniger#WEICHES_ZEILENENDE} bzw. {@link TextBereiniger#HARTES_ZEILENENDE}. Der
 * TextBereiniger nutzt das, um weiche Umbrüche zu Fließtext zusammenzuziehen und bewusste
 * Umbrüche (etwa die Kurzüberschrift einer hängend eingerückten Definition im UWG-Anhang) zu
 * erhalten — eine Unterscheidung, die aus dem reinen Text nicht zuverlässig möglich ist.
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

  /** Interne End-X-Metadaten am Zeilenende („␂527␂“), von {@link #klassifiziereZeilenenden}
   * konsumiert; verlässt diese Klasse nie. */
  private static final char ENDX_MARKE = '\uE002';

  /** Verirrte End-X-Metadaten mitten in einer Zeile (siehe {@link #klassifiziereZeilenenden}). */
  private static final java.util.regex.Pattern ENDX_REST =
      java.util.regex.Pattern.compile("\uE002\\d*\uE002?");

  /** Fensterhälfte (Zeilen davor/danach) für die lokale Suche nach Ausrichtungs-Clustern. Lokal
   * statt dokumentweit, weil ein Dokument Blöcke unterschiedlicher Spaltenbreite mischt
   * (schmalerer Regelungstext vs. breitere Begründung; zweispaltiges altes BGBl, dessen Spalten
   * in Content-Stream-Reihenfolge nacheinander kommen). */
  private static final int RAND_FENSTER = 20;

  /** Streuung (pt), innerhalb derer Zeilenenden als „gleich ausgerichtet“ gelten. Blocksatz-Zeilen
   * enden auf wenige pt genau am Rand; ein evtl. mitgemessenes Trailing-Space verschiebt das Ende
   * um eine Leerzeichenbreite (~2–3 pt). */
  private static final float CLUSTER_TOLERANZ_PT = 4f;

  /** Ab diesem Abstand (pt) unter einem Ausrichtungs-Cluster ist ein Zeilenende bewusst gesetzt →
   * harter Umbruch. Der Bereich dazwischen bleibt unklassifiziert (z.B. Zeilen, deren gefilterte
   * Fußnotenziffer das gemessene Ende leicht verkürzt). */
  private static final float HART_ABSTAND_PT = 10f;

  /** Mindestzahl gleich ausgerichteter Fensterzeilen, damit ein Zeilenende als Satzspiegelrand
   * (Ausrichtungs-Cluster) gilt — Titelseiten, Unterschriftenblöcke u.ä. bilden keine Cluster
   * und bleiben unklassifiziert. */
  private static final int MIN_RANDZEILEN = 5;

  private FontgroessenFilter() {}

  static String extrahiere(PDDocument dokument) throws IOException {
    return extrahiere(dokument, SuperskriptModus.ENTFERNEN);
  }

  static String extrahiere(PDDocument dokument, SuperskriptModus superskriptModus)
      throws IOException {
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

    var filter =
        new GroessenFilterStripper(
            schwellen, zaehler.brotschriftUntergrenzen(schwellen), superskriptModus);
    filter.setLineSeparator("\n");
    var ausgabe = new StringWriter();
    filter.writeText(dokument, ausgabe);
    return klassifiziereZeilenenden(ausgabe.toString());
  }

  /**
   * Ersetzt die End-X-Metadaten der Zeilen durch die Umbruch-Klassifikation: Zeilen, die an einem
   * lokalen Satzspiegelrand enden (Ausrichtungs-Cluster aus mindestens {@link #MIN_RANDZEILEN}
   * gleich endenden Fensterzeilen), erhalten {@link TextBereiniger#WEICHES_ZEILENENDE}; Zeilen,
   * die deutlich vor einem solchen Rand enden, {@link TextBereiniger#HARTES_ZEILENENDE}; alles
   * andere bleibt unmarkiert. Cluster statt Perzentil, weil ein Fenster am Spaltenwechsel des
   * zweispaltigen alten BGBl beide Spaltenränder enthält — maßgeblich ist der Rand, an dem die
   * Zeile selbst ausgerichtet ist bzw. der nächste oberhalb ihres Endes.
   */
  private static String klassifiziereZeilenenden(String text) {
    var zeilen = text.split("\n", -1);
    var endX = new float[zeilen.length];
    Arrays.fill(endX, Float.NaN);
    for (int i = 0; i < zeilen.length; i++) {
      var zeile = zeilen[i];
      if (zeile.isEmpty() || zeile.charAt(zeile.length() - 1) != ENDX_MARKE) {
        continue;
      }
      int start = zeile.lastIndexOf(ENDX_MARKE, zeile.length() - 2);
      if (start < 0) {
        continue;
      }
      try {
        endX[i] = Integer.parseInt(zeile, start + 1, zeile.length() - 1, 10);
      } catch (NumberFormatException e) {
        continue;
      }
      zeilen[i] = zeile.substring(0, start);
    }

    // Verirrte Metadaten mitten in der Zeile (Seitenwechsel ohne Zeilentrenner) sind wertlos.
    for (int i = 0; i < zeilen.length; i++) {
      if (zeilen[i].indexOf(ENDX_MARKE) >= 0) {
        zeilen[i] = ENDX_REST.matcher(zeilen[i]).replaceAll("");
      }
    }

    for (int i = 0; i < zeilen.length; i++) {
      float x = endX[i];
      if (Float.isNaN(x)) {
        continue;
      }
      var fenster = new ArrayList<Float>();
      for (int j = Math.max(0, i - RAND_FENSTER);
          j < Math.min(zeilen.length, i + RAND_FENSTER + 1);
          j++) {
        if (!Float.isNaN(endX[j])) {
          fenster.add(endX[j]);
        }
      }
      if (istCluster(fenster, x)) {
        // Die Zeile endet an einem Satzspiegelrand → automatischer Blocksatz-Umbruch.
        zeilen[i] = zeilen[i] + TextBereiniger.WEICHES_ZEILENENDE;
        continue;
      }
      // Gibt es deutlich oberhalb des Zeilenendes einen Satzspiegelrand, wäre dort noch Platz
      // gewesen → das Zeilenende ist bewusst gesetzt.
      for (float v : fenster) {
        if (v >= x + HART_ABSTAND_PT && istCluster(fenster, v)) {
          zeilen[i] = zeilen[i] + TextBereiniger.HARTES_ZEILENENDE;
          break;
        }
      }
    }
    return String.join("\n", zeilen);
  }

  /** Enden mindestens {@link #MIN_RANDZEILEN} der Fensterzeilen gleich ausgerichtet bei {@code x}? */
  private static boolean istCluster(List<Float> fenster, float x) {
    int anzahl = 0;
    for (float v : fenster) {
      if (Math.abs(v - x) <= CLUSTER_TOLERANZ_PT) {
        anzahl++;
      }
    }
    return anzahl >= MIN_RANDZEILEN;
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

    /** Mindest-Hebung (pt) der Grundlinie, ab der eine kleinere Ziffer als Superskript gilt. */
    private static final float SUPERSKRIPT_HEBUNG_MIN_PT = 2f;

    /** Maximal-Hebung (pt): darüber liegt ein Zeilenwechsel vor, kein Superskript. */
    private static final float SUPERSKRIPT_HEBUNG_MAX_PT = 6f;

    private final Map<Integer, Float> schwellen;
    private final Map<Integer, Float> untergrenzen;
    private final SuperskriptModus superskriptModus;

    /** End-X (pt) des breitesten behaltenen Laufs der laufenden Zeile; NaN vor dem ersten. */
    private float zeilenEndX = Float.NaN;

    GroessenFilterStripper(
        Map<Integer, Float> schwellen,
        Map<Integer, Float> untergrenzen,
        SuperskriptModus superskriptModus) {
      this.schwellen = schwellen;
      this.untergrenzen = untergrenzen;
      this.superskriptModus = superskriptModus;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positionen) throws IOException {
      if (!behalte(positionen)) {
        // Fußnotenblock bzw. hochgestellte Ziffer. In BEHALTEN-Modus werden reine Ziffernläufe
        // im Satzspiegel (oberhalb des Fußnotenblocks) als Superskripte übernommen.
        var hochgestellt = superskriptModus == SuperskriptModus.BEHALTEN ? nurZiffern(positionen) : null;
        if (hochgestellt == null) {
          return;
        }
        text = hochgestellt;
      } else if (superskriptModus == SuperskriptModus.BEHALTEN) {
        // Gehobene, kleiner gesetzte Ziffern innerhalb eines Brotschrift-Laufs (bayerische
        // Satznummern und Fußnotenmarker kleben im selben Lauf wie der Fließtext).
        text = mitSuperskripten(text, positionen);
      }
      for (var position : positionen) {
        float endX = position.getXDirAdj() + position.getWidthDirAdj();
        zeilenEndX = Float.isNaN(zeilenEndX) ? endX : Math.max(zeilenEndX, endX);
      }
      super.writeString(text, positionen);
    }

    /**
     * Der Lauf als Superskript-Text, falls er ausschließlich aus Ziffern im Satzspiegel besteht
     * (hochgestellte Marker, die als eigener Lauf ankommen); sonst {@code null}.
     */
    private @org.jspecify.annotations.Nullable String nurZiffern(List<TextPosition> positionen) {
      if (positionen.isEmpty()) {
        return null;
      }
      var grenze = untergrenzen.get(getCurrentPageNo());
      var sb = new StringBuilder();
      for (var position : positionen) {
        if (grenze != null && position.getYDirAdj() > grenze) {
          return null; // Fußnotenblock
        }
        var unicode = position.getUnicode();
        for (int i = 0; i < unicode.length(); i++) {
          if (!Character.isDigit(unicode.charAt(i))) {
            return null;
          }
        }
        sb.append(eu.mulk.aendggner.gesetz.Superskript.zuSuperskript(unicode));
      }
      return sb.toString();
    }

    /**
     * Ersetzt innerhalb eines behaltenen Laufs Ziffern, die kleiner gesetzt und gegenüber der
     * Grundlinie des Laufs deutlich gehoben sind, durch Unicode-Superskripte. Die Grundlinie ist
     * das größte Y des Laufs (gehobene Zeichen haben kleinere Y-Werte); die Hebung ist nach oben
     * begrenzt, damit ein etwaiger Zeilenwechsel im Lauf keine Fehltreffer erzeugt.
     */
    private String mitSuperskripten(String text, List<TextPosition> positionen) {
      float grundlinie = Float.NEGATIVE_INFINITY;
      float brotschriftGroesse = 0;
      for (var position : positionen) {
        if (position.getYDirAdj() > grundlinie) {
          grundlinie = position.getYDirAdj();
          brotschriftGroesse = position.getFontSizeInPt();
        }
      }
      var sb = new StringBuilder(text.length());
      boolean geaendert = false;
      for (var position : positionen) {
        var unicode = position.getUnicode();
        float hebung = grundlinie - position.getYDirAdj();
        if (hebung >= SUPERSKRIPT_HEBUNG_MIN_PT
            && hebung <= SUPERSKRIPT_HEBUNG_MAX_PT
            && position.getFontSizeInPt() < brotschriftGroesse
            && istZiffernfolge(unicode)) {
          sb.append(eu.mulk.aendggner.gesetz.Superskript.zuSuperskript(unicode));
          geaendert = true;
        } else {
          sb.append(unicode);
        }
      }
      return geaendert ? sb.toString() : text;
    }

    private static boolean istZiffernfolge(String unicode) {
      if (unicode.isEmpty()) {
        return false;
      }
      for (int i = 0; i < unicode.length(); i++) {
        if (!Character.isDigit(unicode.charAt(i))) {
          return false;
        }
      }
      return true;
    }

    /** Schreibt vor jedem Zeilentrenner das End-X der Zeile als Metadaten für
     * {@link #klassifiziereZeilenenden}. */
    @Override
    protected void writeLineSeparator() throws IOException {
      schreibeEndXMarke();
      super.writeLineSeparator();
    }

    /** Die letzte Zeile einer Seite endet ohne Zeilentrenner — ohne Flush würde ihr End-X erst
     * an der ersten Zeile der Folgeseite landen und diese falsch klassifizieren. */
    @Override
    protected void writePageEnd() throws IOException {
      schreibeEndXMarke();
      super.writePageEnd();
    }

    private void schreibeEndXMarke() throws IOException {
      if (!Float.isNaN(zeilenEndX)) {
        writeString(ENDX_MARKE + Integer.toString(Math.round(zeilenEndX)) + ENDX_MARKE);
        zeilenEndX = Float.NaN;
      }
    }

    private boolean behalte(List<TextPosition> positionen) {
      var schwelle = schwellen.get(getCurrentPageNo());
      if (schwelle == null || positionen.isEmpty()) {
        return true;
      }
      float groessenSumme = 0;
      float ySumme = 0;
      for (var position : positionen) {
        groessenSumme += position.getFontSizeInPt();
        ySumme += position.getYDirAdj();
      }
      float groesse = groessenSumme / positionen.size();
      if (groesse >= schwelle) {
        return true;
      }
      var brotschrift = schwelle + TOLERANZ_PT;
      var grenze = untergrenzen.get(getCurrentPageNo());
      boolean unterDerBrotschrift = grenze != null && ySumme / positionen.size() > grenze;
      return !unterDerBrotschrift && groesse >= brotschrift - STARK_KLEINER_PT;
    }
  }

  private static float runde(float groesse) {
    return Math.round(groesse * 2f) / 2f;
  }
}
