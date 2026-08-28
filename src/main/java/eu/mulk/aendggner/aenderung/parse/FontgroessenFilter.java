// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
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
 * TextBereiniger nutzt das, um weiche Umbrüche zu Fließtext zusammenzuziehen und bewusste Umbrüche
 * (etwa die Kurzüberschrift einer hängend eingerückten Definition im UWG-Anhang) zu erhalten — eine
 * Unterscheidung, die aus dem reinen Text nicht zuverlässig möglich ist.
 */
final class FontgroessenFilter {

  private static final Logger log = Logger.getLogger(FontgroessenFilter.class);

  /** Läufe, die um mehr als diese Punktzahl unter der Brotschrift liegen, sind Kleingedrucktes. */
  private static final float TOLERANZ_PT = 1.4f;

  /**
   * Anteil an den Zeichen einer Seite, ab dem eine Fontgröße unstrittig die Brotschrift ist.
   * Bewusst über 50 %: Auf halb/halb geteilten Seiten (Befehle oben, langer Fußnotenblock unten)
   * darf nicht das Kleingedruckte durch eine hauchdünne Mehrheit gewinnen.
   */
  private static final double DOMINANZ_SCHWELLE = 0.6;

  /**
   * Erreicht keine Größe die absolute Mehrheit (fußnotenlastige Seiten), gewinnt die
   * <em>größte</em> Größe mit diesem Mindestanteil: Kleingedrucktes kann die Zeichenmehrheit
   * stellen, ist aber nie größer gesetzt als die Brotschrift.
   */
  private static final double KANDIDATEN_SCHWELLE = 0.25;

  /**
   * Interne Zeilenmetadaten am Zeilenende („␂22,7315,527“ = Seite, Grundlinie ×10, End-X), von
   * {@link #zerlege} eingelesen; als Text verlassen sie diese Klasse nie.
   *
   * <p>Die Metadaten reisen im Textstrom mit, statt nebenher gesammelt zu werden: Nur so ist ihre
   * Zuordnung zu den Zeilen gesichert. PDFBox schreibt Zeilentrenner an mehreren Stellen
   * (Zeilenende, Seitenende), und eine parallel geführte Liste geriete dort aus dem Tritt.
   */
  private static final char ZEILEN_MARKE = '\uE002';

  /** Verirrte Zeilenmetadaten mitten in einer Zeile (siehe {@link #zerlege}). */
  private static final java.util.regex.Pattern MARKEN_REST =
      java.util.regex.Pattern.compile("\uE002[\\d,]*\uE002?");

  /**
   * Fensterhälfte (Zeilen davor/danach) für die lokale Suche nach Ausrichtungs-Clustern. Lokal
   * statt dokumentweit, weil ein Dokument Blöcke unterschiedlicher Spaltenbreite mischt (schmalerer
   * Regelungstext vs. breitere Begründung; zweispaltiges altes BGBl, dessen Spalten in
   * Content-Stream-Reihenfolge nacheinander kommen).
   */
  private static final int RAND_FENSTER = 20;

  /**
   * Streuung (pt), innerhalb derer Zeilenenden als „gleich ausgerichtet“ gelten. Blocksatz-Zeilen
   * enden auf wenige pt genau am Rand; ein evtl. mitgemessenes Trailing-Space verschiebt das Ende
   * um eine Leerzeichenbreite (~2–3 pt).
   */
  private static final float CLUSTER_TOLERANZ_PT = 4f;

  /**
   * Ab diesem Abstand (pt) unter einem Ausrichtungs-Cluster ist ein Zeilenende bewusst gesetzt →
   * harter Umbruch. Der Bereich dazwischen bleibt unklassifiziert (z.B. Zeilen, deren gefilterte
   * Fußnotenziffer das gemessene Ende leicht verkürzt).
   */
  private static final float HART_ABSTAND_PT = 10f;

  /**
   * Mindestzahl gleich ausgerichteter Fensterzeilen, damit ein Zeilenende als Satzspiegelrand
   * (Ausrichtungs-Cluster) gilt — Titelseiten, Unterschriftenblöcke u.ä. bilden keine Cluster und
   * bleiben unklassifiziert.
   */
  private static final int MIN_RANDZEILEN = 5;

  /**
   * Mindestbreite (pt) des Stegs zwischen zwei Spalten, an den Rändern der <em>sichtbaren</em>
   * Zeichen gemessen (siehe {@link GroessenFilterStripper#aufSpalte}).
   *
   * <p>Ausgezählt an den beiden Zusammenstellungen der Beispieldaten: Der schmalste echte Steg
   * misst dort 6,9 pt (BT-Drs. 20/7619 und 19/24334 übereinstimmend), der breiteste
   * Wortzwischenraum einer ganzseitenbreiten Zeile über der Blattmitte 5,4 pt. Dazwischen liegt die
   * Schwelle. Sie war früher 12 pt und trennte damit gerade die engsten Stege nicht mehr.
   */
  private static final float RINNE_MIN_PT = 6f;

  private FontgroessenFilter() {}

  static String extrahiere(PDDocument dokument) throws IOException {
    return extrahiere(dokument, SuperskriptModus.ENTFERNEN);
  }

  static String extrahiere(PDDocument dokument, SuperskriptModus superskriptModus)
      throws IOException {
    return extrahiere(dokument, superskriptModus, Spalte.GANZ);
  }

  /**
   * Welcher Teil der Seitenbreite extrahiert wird. Nötig für die Zusammenstellung einer
   * Beschlussempfehlung, deren zwei Spalten — anders als beim alten BGBl und beim Berliner GVBl —
   * <em>nicht</em> nacheinander im Inhaltsstrom stehen, sondern zeilenweise verschränkt; nur die
   * Koordinaten trennen sie.
   */
  enum Spalte {
    GANZ,
    LINKS,
    RECHTS
  }

  static String extrahiere(PDDocument dokument, SuperskriptModus superskriptModus, Spalte spalte)
      throws IOException {
    return klassifiziereZeilenenden(extrahiereZeilen(dokument, superskriptModus, spalte));
  }

  /**
   * Der Auszug samt der Zuordnung seines Wortbestandes zu den Seiten.
   *
   * @param text der Auszug, wie ihn {@link #extrahiere} liefert.
   * @param seiten die Konkordanz, aus denselben Zeilen erhoben — ein zweiter Lauf über das Dokument
   *     (PDFBox braucht dafür zwei Pässe) wäre reine Verschwendung.
   */
  record Auszug(String text, Seitenkonkordanz seiten) {}

  static Auszug extrahiereMitSeiten(PDDocument dokument, SuperskriptModus superskriptModus)
      throws IOException {
    var zeilen = extrahiereZeilen(dokument, superskriptModus, Spalte.GANZ);
    return new Auszug(klassifiziereZeilenenden(zeilen), Seitenkonkordanz.aus(zeilen));
  }

  /**
   * Eine Ausgabezeile samt ihrer Herkunft im Satzbild.
   *
   * <p>Sonst verlässt keine Koordinate diese Klasse. Die Zusammenstellung einer Beschlussempfehlung
   * braucht sie: Ihre beiden Spalten stehen im Inhaltsstrom verschränkt und lassen sich nur über
   * Seite und Grundlinie in eine gemeinsame Lesereihenfolge bringen.
   *
   * @param seite 1-basierte Seitennummer.
   * @param grundlinie Grundlinie (pt von oben); {@link Float#NaN}, wenn unbekannt.
   * @param startX linker Anfang der Zeile, an den sichtbaren Zeichen gemessen (pt); {@link
   *     Float#NaN}, wenn unbekannt.
   * @param endX rechtes Ende der Zeile (pt); {@link Float#NaN}, wenn unbekannt.
   */
  record Zeile(int seite, float grundlinie, float startX, float endX, String text) {}

  /**
   * Die Zeilen des Dokuments mit ihrer Geometrie. Hat keine Seite eine dominante Brotschrift,
   * bleibt das Dokument ungefiltert und die Zeilen tragen keine Geometrie.
   */
  static List<Zeile> extrahiereZeilen(
      PDDocument dokument, SuperskriptModus superskriptModus, Spalte spalte) throws IOException {
    var zaehler = new GroessenZaehler();
    zaehler.setLineSeparator("\n");
    var wegwerf = new StringWriter();
    zaehler.writeText(dokument, wegwerf);

    var schwellen = zaehler.schwellenProSeite();
    if (schwellen.isEmpty()) {
      log.debugf("Keine dominanten Fontgrößen; Kleingedrucktes wird nicht gefiltert.");
      return inLesereihenfolge(zerlege(wegwerf.toString()), spalte);
    }
    log.debugf("Brotschriftgrößen (je Seite): %s", schwellen);

    var filter =
        new GroessenFilterStripper(
            schwellen, zaehler.brotschriftUntergrenzen(schwellen), superskriptModus, spalte);
    filter.setLineSeparator("\n");
    var ausgabe = new StringWriter();
    filter.writeText(dokument, ausgabe);
    return inLesereihenfolge(zerlege(ausgabe.toString()), spalte);
  }

  /**
   * Der XY-Schnitt gilt nur dem ungeteilten Auszug. Wer ohnehin eine Spalte für sich anfordert (die
   * Zusammenstellung einer Beschlussempfehlung), hat die Lesereihenfolge schon hergestellt — und
   * zwar über die Grundlinien, die er hernach noch braucht, um die Spalten zeilensynchron
   * gegeneinanderzuhalten.
   */
  private static List<Zeile> inLesereihenfolge(List<Zeile> zeilen, Spalte spalte) {
    return spalte == Spalte.GANZ ? Lesereihenfolge.ordne(zeilen) : zeilen;
  }

  /** Trennt die {@link #ZEILEN_MARKE}-Metadaten wieder vom Text ab. */
  private static List<Zeile> zerlege(String text) {
    var roh = text.split("\n", -1);
    var zeilen = new ArrayList<Zeile>(roh.length);
    for (var zeile : roh) {
      int seite = 0;
      float grundlinie = Float.NaN;
      float startX = Float.NaN;
      float endX = Float.NaN;
      if (!zeile.isEmpty() && zeile.charAt(zeile.length() - 1) == ZEILEN_MARKE) {
        int start = zeile.lastIndexOf(ZEILEN_MARKE, zeile.length() - 2);
        if (start >= 0) {
          var felder = zeile.substring(start + 1, zeile.length() - 1).split(",");
          try {
            seite = Integer.parseInt(felder[0]);
            grundlinie = Integer.parseInt(felder[1]) / 10f;
            endX = Integer.parseInt(felder[2]);
            startX = Integer.parseInt(felder[3]);
            zeile = zeile.substring(0, start);
          } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            seite = 0;
            grundlinie = Float.NaN;
            startX = Float.NaN;
            endX = Float.NaN;
          }
        }
      }
      // Verirrte Metadaten mitten in der Zeile (Seitenwechsel ohne Zeilentrenner) sind wertlos.
      if (zeile.indexOf(ZEILEN_MARKE) >= 0) {
        zeile = MARKEN_REST.matcher(zeile).replaceAll("");
      }
      zeilen.add(new Zeile(seite, grundlinie, startX, endX, zeile));
    }
    return zeilen;
  }

  /**
   * Hängt an jede Zeile die Umbruch-Klassifikation: Zeilen, die an einem lokalen Satzspiegelrand
   * enden (Ausrichtungs-Cluster aus mindestens {@link #MIN_RANDZEILEN} gleich endenden
   * Fensterzeilen), erhalten {@link TextBereiniger#WEICHES_ZEILENENDE}; Zeilen, die deutlich vor
   * einem solchen Rand enden, {@link TextBereiniger#HARTES_ZEILENENDE}; alles andere bleibt
   * unmarkiert. Cluster statt Perzentil, weil ein Fenster am Spaltenwechsel des zweispaltigen alten
   * BGBl beide Spaltenränder enthält — maßgeblich ist der Rand, an dem die Zeile selbst
   * ausgerichtet ist bzw. der nächste oberhalb ihres Endes.
   */
  static String klassifiziereZeilenenden(List<Zeile> zeilen) {
    var markiert = markiereZeilenenden(zeilen);
    var sb = new StringBuilder();
    for (int i = 0; i < markiert.size(); i++) {
      if (i > 0) {
        sb.append('\n');
      }
      sb.append(markiert.get(i).text());
    }
    return sb.toString();
  }

  /**
   * Wie {@link #klassifiziereZeilenenden}, aber zeilenweise. Die Zusammenstellung braucht das: Ihre
   * beiden Spalten haben verschiedene Satzspiegelränder und müssen deshalb <em>vor</em> dem
   * Zusammenführen klassifiziert werden — in der gemischten Fassung fände keine Spalte mehr ihren
   * eigenen Rand wieder, und die Silbentrennung bliebe ungeheilt („An- gabe“).
   */
  static List<Zeile> markiereZeilenenden(List<Zeile> zeilen) {
    var text = new String[zeilen.size()];
    var endX = new float[zeilen.size()];
    for (int i = 0; i < zeilen.size(); i++) {
      text[i] = zeilen.get(i).text();
      endX[i] = zeilen.get(i).endX();
    }

    for (int i = 0; i < text.length; i++) {
      float x = endX[i];
      if (Float.isNaN(x)) {
        continue;
      }
      var fenster = new ArrayList<Float>();
      for (int j = Math.max(0, i - RAND_FENSTER);
          j < Math.min(text.length, i + RAND_FENSTER + 1);
          j++) {
        if (!Float.isNaN(endX[j])) {
          fenster.add(endX[j]);
        }
      }
      if (istCluster(fenster, x)) {
        // Die Zeile endet an einem Satzspiegelrand → automatischer Blocksatz-Umbruch.
        text[i] = text[i] + TextBereiniger.WEICHES_ZEILENENDE;
        continue;
      }
      // Gibt es deutlich oberhalb des Zeilenendes einen Satzspiegelrand, wäre dort noch Platz
      // gewesen → das Zeilenende ist bewusst gesetzt.
      for (float v : fenster) {
        if (v >= x + HART_ABSTAND_PT && istCluster(fenster, v)) {
          text[i] = text[i] + TextBereiniger.HARTES_ZEILENENDE;
          break;
        }
      }
    }
    var ergebnis = new ArrayList<Zeile>(zeilen.size());
    for (int i = 0; i < zeilen.size(); i++) {
      var zeile = zeilen.get(i);
      ergebnis.add(
          new Zeile(zeile.seite(), zeile.grundlinie(), zeile.startX(), zeile.endX(), text[i]));
    }
    return ergebnis;
  }

  /**
   * Enden mindestens {@link #MIN_RANDZEILEN} der Fensterzeilen gleich ausgerichtet bei {@code x}?
   */
  private static boolean istCluster(List<Float> fenster, float x) {
    int anzahl = 0;
    for (float v : fenster) {
      if (Math.abs(v - x) <= CLUSTER_TOLERANZ_PT) {
        anzahl++;
      }
    }
    return anzahl >= MIN_RANDZEILEN;
  }

  /**
   * Anteil der Seitenhöhe, unterhalb dessen Brotschrift-Text als Seitenfuß (Kolumnentitel) gilt und
   * die Fußnotengrenze nicht nach unten ziehen darf.
   */
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
          dokumentweit.merge(
              groessenEintrag.getKey(), (long) groessenEintrag.getValue(), Long::sum);
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
          zaehlungen.put(
              eintrag.getKey(), Math.toIntExact(Math.min(Integer.MAX_VALUE, eintrag.getValue())));
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
   * absichtlich etwas kleiner als die Brotschrift; solcher Text steht im Satzspiegel (oberhalb der
   * Grenze) und muss erhalten bleiben.
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
    private final Spalte spalte;

    /** End-X (pt) des breitesten behaltenen Laufs der laufenden Zeile; NaN vor dem ersten. */
    private float zeilenEndX = Float.NaN;

    /**
     * Anfangs-X (pt) der laufenden Zeile, am ersten <em>sichtbaren</em> Zeichen gemessen. Anders
     * als beim Zeilenende zählen Leerzeichen hier nicht mit: Der linke Rand einer Spalte bestimmt
     * mit über die Rinne, an der die {@link Lesereihenfolge} schneidet, und ein Lauf beginnt
     * regelmäßig mit dem Steg der Spalte davor.
     */
    private float zeilenStartX = Float.NaN;

    /** Grundlinie (pt von oben) der laufenden Zeile: die tiefste ihrer behaltenen Läufe. */
    private float zeilenGrundlinie = Float.NaN;

    /** Seite, auf der die laufende Zeile steht. */
    private int zeilenSeite = 0;

    GroessenFilterStripper(
        Map<Integer, Float> schwellen,
        Map<Integer, Float> untergrenzen,
        SuperskriptModus superskriptModus,
        Spalte spalte) {
      this.schwellen = schwellen;
      this.untergrenzen = untergrenzen;
      this.superskriptModus = superskriptModus;
      this.spalte = spalte;
    }

    /**
     * Beschränkt einen Lauf auf die gewünschte Spalte, Zeichen für Zeichen.
     *
     * <p>Nicht lauf-, sondern zeichenweise, weil PDFBox alles auf einer Grundlinie zu einem Lauf
     * zusammenfasst: Die einander gegenüberstehenden Überschriften beider Spalten („Artikel 1“ und
     * „Artikel 1“) kämen sonst gemeinsam in einer Spalte an.
     *
     * <p>Gemessen wird ausschließlich an den <em>sichtbaren</em> Zeichen. Der Zwischenraum zweier
     * Spalten trägt im Textstrom regelmäßig noch Leerzeichen, die fast bis an die nächste Spalte
     * reichen; nach ihren Rändern gemessen schrumpft ein 7-pt-Steg auf 4,7 pt und wurde für
     * durchlaufenden Text gehalten. In BT-Drs. 20/7619 riss das die gesperrte Marke des Punktes 14
     * mitten entzwei („… wird wie folgt gefasst: 14. u“ links, „n v e r ä n d e r t“ rechts).
     *
     * @return die Zeichen der Spalte, oder {@code null}, wenn der Lauf ganz außerhalb liegt.
     */
    private @org.jspecify.annotations.Nullable List<TextPosition> aufSpalte(
        List<TextPosition> positionen) {
      if (spalte == Spalte.GANZ || positionen.isEmpty()) {
        return positionen;
      }
      var seite = getCurrentPage();
      if (seite == null) {
        return positionen;
      }
      if (positionen.get(0).getDir() != 0) {
        // Gedrehter Text gehört keiner Spalte an — er steht quer am Blattrand. Der Randvermerk
        // „Vorabfassung – wird durch die lektorierte Fassung ersetzt“ der Bundestagsdrucksachen
        // steht so, mit Koordinaten in seinem eigenen, gedrehten Bezugssystem: Seine Grundlinie
        // liefe quer durch beide Spalten und zerschnitte deren Zeilenfolge. Beim ungeteilten
        // Auszug bleibt er erhalten und wird wie bisher vom TextBereiniger entfernt.
        return null;
      }
      float mitte = seite.getMediaBox().getWidth() / 2;

      // Das erste sichtbare Zeichen jenseits der Blattmitte und das letzte diesseits.
      int erstesRechts = -1;
      int letztesLinks = -1;
      for (int i = 0; i < positionen.size(); i++) {
        var position = positionen.get(i);
        if (position.getUnicode().isBlank()) {
          continue;
        }
        if (zeichenMitte(position) < mitte) {
          letztesLinks = i;
        } else {
          erstesRechts = i;
          break;
        }
      }

      if (erstesRechts < 0 || letztesLinks < 0) {
        // Der Lauf liegt ganz auf einer Seite der Mitte.
        return (erstesRechts < 0) == (spalte == Spalte.LINKS) ? positionen : null;
      }

      var davor = positionen.get(letztesLinks);
      float luecke =
          positionen.get(erstesRechts).getXDirAdj() - (davor.getXDirAdj() + davor.getWidthDirAdj());
      if (luecke < RINNE_MIN_PT) {
        // Kein Spaltensteg, sondern durchlaufender Text über die Blattmitte hinweg: eine
        // ganzseitenbreite Zeile (Vorblatt, Bericht, Seitenkopf). Sie gehört keiner Spalte an und
        // wird der linken zugeschlagen, damit sie genau einmal erscheint statt zerschnitten
        // zweimal.
        return spalte == Spalte.LINKS ? positionen : null;
      }
      // Die Leerzeichen des Stegs bleiben bei der linken Spalte; der Bereiniger stutzt sie.
      return spalte == Spalte.LINKS
          ? positionen.subList(0, erstesRechts)
          : positionen.subList(erstesRechts, positionen.size());
    }

    private static float zeichenMitte(TextPosition position) {
      return position.getXDirAdj() + position.getWidthDirAdj() / 2;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positionen) throws IOException {
      var inSpalte = aufSpalte(positionen);
      if (inSpalte == null) {
        return;
      }
      if (inSpalte.size() != positionen.size()) {
        positionen = inSpalte;
        var sb = new StringBuilder();
        for (var position : positionen) {
          sb.append(position.getUnicode());
        }
        text = sb.toString();
      }
      if (!behalte(positionen)) {
        // Fußnotenblock bzw. hochgestellte Ziffer. In BEHALTEN-Modus werden reine Ziffernläufe
        // im Satzspiegel (oberhalb des Fußnotenblocks) als Superskripte übernommen.
        var hochgestellt =
            superskriptModus == SuperskriptModus.BEHALTEN ? nurZiffern(positionen) : null;
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
        if (!position.getUnicode().isBlank()) {
          float startX = position.getXDirAdj();
          zeilenStartX = Float.isNaN(zeilenStartX) ? startX : Math.min(zeilenStartX, startX);
        }
        // Grundlinie = tiefstes Y des Laufs, wie schon in mitSuperskripten: Hochgestelltes sitzt
        // höher und darf die Zeile nicht nach oben ziehen.
        float y = position.getYDirAdj();
        zeilenGrundlinie = Float.isNaN(zeilenGrundlinie) ? y : Math.max(zeilenGrundlinie, y);
      }
      zeilenSeite = getCurrentPageNo();
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

    /** Schreibt vor jedem Zeilentrenner die Geometrie der Zeile als Metadaten. */
    @Override
    protected void writeLineSeparator() throws IOException {
      schreibeZeilenMarke();
      super.writeLineSeparator();
    }

    /**
     * Die letzte Zeile einer Seite endet ohne Zeilentrenner — ohne Flush würde ihre Geometrie erst
     * an der ersten Zeile der Folgeseite landen und diese falsch beschreiben.
     */
    @Override
    protected void writePageEnd() throws IOException {
      schreibeZeilenMarke();
      super.writePageEnd();
    }

    private void schreibeZeilenMarke() throws IOException {
      if (!Float.isNaN(zeilenEndX)) {
        writeString(
            "%c%d,%d,%d,%d%c"
                .formatted(
                    ZEILEN_MARKE,
                    zeilenSeite,
                    Math.round(zeilenGrundlinie * 10),
                    Math.round(zeilenEndX),
                    Math.round(Float.isNaN(zeilenStartX) ? zeilenEndX : zeilenStartX),
                    ZEILEN_MARKE));
        zeilenEndX = Float.NaN;
        zeilenStartX = Float.NaN;
        zeilenGrundlinie = Float.NaN;
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
