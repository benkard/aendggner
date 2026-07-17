package eu.mulk.aendggner.aenderung.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bereinigt aus PDFs extrahierten Rohtext eines Änderungsgesetzes: entfernt Kolumnentitel,
 * Seitenzahlen und Drucksachen-Seitenköpfe, zieht Silbentrennungen am Zeilenende zusammen und
 * normalisiert Anführungszeichen-Glyphen.
 *
 * <p>Wichtig für die Silbentrennung: Die Verarbeitung erhält den Trailing-Whitespace der Zeilen bis
 * zum Schluss, denn er ist das Unterscheidungssignal für markerlose Trennungen (siehe {@link
 * #verbindeUmbrueche}).
 */
public final class TextBereiniger {

  // BGBl alt (zweispaltig, bis 2022) und neu (recht.bund.de, ab 2023).
  private static final Pattern KOPFZEILE =
      Pattern.compile(
          "^\\s*(Seite \\d+ von \\d+\\s+)?(\\d{1,5}\\s+)?Bundesgesetzblatt Jahrgang \\d{4}.*$");
  private static final Pattern SEITENZAHL = Pattern.compile("^\\s*\\d{1,5}\\s*$");
  private static final Pattern BUNDESANZEIGER =
      Pattern.compile(
          "^\\s*(Das Bundesgesetzblatt im Internet:|Ein Service des Bundesanzeiger).*$");
  // Referenten-/Regierungsentwürfe: „ - 10 - “ bzw. „ - 5 - Bearbeitungsstand: 05.05.2026 16:18“.
  private static final Pattern SEITENMARKER =
      Pattern.compile("^\\s*[-–]\\s*\\d+\\s*[-–]\\s*(Bearbeitungsstand: .*)?$");
  // Bundesrats-Drucksachen: „- 2 -Drucksache 170/23“, „Drucksache 170/23 - 3 -“ oder die
  // Drucksachennummer allein als Kolumnentitel.
  private static final Pattern BUNDESRAT_KOPF =
      Pattern.compile(
          "^\\s*(?:[-–]\\s*\\d+\\s*[-–]\\s*)?Drucksache \\d+/\\d+(?:\\s*[-–]\\s*\\d+\\s*[-–])?\\s*$");
  // Wasserzeichen der Bundestags-Vorabfassungen: senkrecht gesetzt, extrahiert deshalb mit
  // Zeilenumbrüchen an beliebigen Stellen („V\norabfassung - w\nird durch …“). Das Muster
  // erlaubt Whitespace zwischen allen Zeichen der festen Phrase.
  private static final Pattern VORABFASSUNG =
      Pattern.compile(
          gesperrt("Vorabfassung - wird durch die lektorierte ")
              + "(?:"
              + gesperrt("Fassung")
              + "|"
              + gesperrt("Version")
              + ")"
              + gesperrt(" ersetzt."));

  /** Regex für eine Phrase, deren Zeichen durch beliebigen Whitespace getrennt sein dürfen. */
  private static String gesperrt(String phrase) {
    var sb = new StringBuilder();
    for (char c : phrase.toCharArray()) {
      if (c == ' ') {
        sb.append("\\s*[-–]?\\s*");
      } else if (c == '-') {
        sb.append("[-–]\\s*");
      } else {
        sb.append(Pattern.quote(String.valueOf(c))).append("\\s*");
      }
    }
    return sb.toString();
  }

  // Verirrte „Anlage N“-Marke unmittelbar vor einem Seitenkopf (Lesereihenfolge-Artefakt).
  private static final Pattern ANLAGE_MARKE = Pattern.compile("^\\s*Anlage \\d+\\s*$");
  // Bundestags-Drucksachen: „Drucksache 21/6178 – 2 – Deutscher Bundestag – 21. Wahlperiode“
  // bzw. gespiegelt auf geraden Seiten.
  private static final Pattern DRUCKSACHE_KOPF =
      Pattern.compile("^\\s*Drucksache \\d+/\\d+ [–-] \\d+ [–-] Deutscher Bundestag.*$");
  private static final Pattern BUNDESTAG_KOPF =
      Pattern.compile(
          "^\\s*Deutscher Bundestag [–-] \\d+\\. Wahlperiode [–-] \\d+ [–-] Drucksache.*$");

  /** Konjunktionen, die typischerweise auf einen Suspensivstrich folgen („Wirk- und …“). */
  private static final Pattern KONJUNKTION =
      Pattern.compile("^(und|oder|sowie|bzw\\.|beziehungsweise)\\b.*");

  /** Perzentil der Zeilenlängen, das als „volle Spaltenbreite“ gilt (siehe {@link #verbindeUmbrueche}). */
  private static final double VOLLZEILE_PERZENTIL = 0.9;

  /** Mindestanteil der vollen Spaltenbreite, ab dem ein markerloser Umbruch als Silbentrennung
   * statt als bewusster Wortgrenzen-Umbruch gilt. */
  private static final double VOLLZEILE_MINDESTANTEIL = 0.7;

  /** Anzahl Zeilen vor/nach einer Kandidatenzeile, die für die lokale Spaltenbreiten-Schätzung
   * herangezogen werden (siehe {@link #typischeZeilenlaenge}). */
  private static final int VOLLZEILE_FENSTER = 20;

  // BMJV-Entwurfsvorlagen zeichnen das hängende öffnende Anführungszeichen im Content-Stream
  // NACH dem ersten Element der zitierten Passage: „(1) „ Ungeachtet…“ statt „„(1) Ungeachtet…“,
  // „§ 19„“ statt „„§ 19“.
  private static final Pattern INVERTIERTES_ZITAT =
      Pattern.compile("(?m)^(\\s*)\\((\\d+[a-z]?)\\) „\\s*");
  private static final Pattern INVERTIERTES_PARAGRAPH_ZITAT =
      Pattern.compile("(?m)^(\\s*)(§\\s*\\d+[a-z]?)„[ \\t]*");
  // Dieselbe Vertauschung bei Aufzählungslabeln: „3. „ mit Vorteilen …“ statt „„3. mit Vorteilen“.
  // Das Leerzeichen NACH dem „ ist das Artefakt-Signal — echte Binnenzitate („13a. „größere
  // Renovierung““) kleben direkt am Inhalt und bleiben unangetastet.
  private static final Pattern INVERTIERTES_LISTEN_ZITAT =
      Pattern.compile("(?m)^(\\s*)(\\d+[a-z]?\\.|[a-z]{1,3}\\))[ \\t]+„[ \\t]+");

  private TextBereiniger() {}

  public static String bereinige(String rohText) {
    var text = normalisiereAnfuehrungszeichen(rohText);
    text = INVERTIERTES_ZITAT.matcher(text).replaceAll("$1„($2) ");
    text = INVERTIERTES_PARAGRAPH_ZITAT.matcher(text).replaceAll("$1„$2");
    text = INVERTIERTES_LISTEN_ZITAT.matcher(text).replaceAll("$1„$2 ");
    text = trenneVerklebteZitatgrenzen(text);
    text = VORABFASSUNG.matcher(text).replaceAll("\n");
    var zeilen = entferneKolumnentitel(text);
    var verbunden = verbindeUmbrueche(zeilen);
    // Falsch-positive markerlose Zusammenzüge („durch“ + „die“ → „durchdie“) reparieren — die
    // Befehlsvokabeln sind nie Kompositum-Bestandteile.
    return trenneVerklebteZitatgrenzen(strippeZeilenenden(verbunden));
  }

  /**
   * PDF-Extraktoren liefern je nach Schriftart unterschiedliche Glyphen für die deutschen
   * Anführungszeichen; der Parser verlässt sich auf {@code „}/{@code “}.
   */
  private static String normalisiereAnfuehrungszeichen(String text) {
    return text
        // Doppelte Low-9- und gerade Anführungszeichen am Wortanfang → „
        .replace('‚', '‘') // ‚ bleibt einfaches öffnendes Zitat
        .replace("‟", "“") // ‟ → “
        .replace("«", "„") // « → „ (selten, aus Fremdsatz)
        .replace("»", "“") // » → “
        // Gerade und englische schließende Anführungszeichen: in BGBl-/Drucksachentexten öffnet
        // stets „, also sind diese Glyphen (fast immer Satz-/OCR-Fehler) schließend zu lesen.
        .replace("”", "“")
        .replace("\"", "“");
  }

  /** Verklebte Zitatgrenzen wieder trennen („§ 9“ersetzt → „§ 9“ ersetzt) — erst nach den
   * Invertiertes-Zitat-Fixes, die auf die verklebte Form angewiesen sind. */
  private static String trenneVerklebteZitatgrenzen(String text) {
    return text
        .replaceAll("“(\\p{L})", "“ $1")
        .replaceAll("(\\p{L})„", "$1 „")
        // Verklebte Befehlsvokabeln (Zusammenzug über Zeilengrenzen ohne Leerzeichen).
        .replace("durchdie ", "durch die ")
        .replace("undwerden ", "und werden ")
        .replace("undwird ", "und wird ")
        .replace("Kommaeingefügt", "Komma eingefügt")
        .replace("Kommaersetzt", "Komma ersetzt")
        // Kontextrahmen, an den der folgende Unterpunkt geklebt wurde („geändertaa) In …“).
        .replaceAll("(wie folgt geändert:?)(?=[a-z]{1,3}\\)|\\d+[a-z]?\\.)", "$1\n");
  }

  /** Entfernt Seitenkopf-/Fußzeilen. Trailing-Whitespace der übrigen Zeilen bleibt erhalten! */
  private static ArrayList<String> entferneKolumnentitel(String text) {
    var roh = text.split("\n", -1);
    var kolumnentitel = new boolean[roh.length];
    for (int i = 0; i < roh.length; i++) {
      kolumnentitel[i] = istKolumnentitel(roh[i]);
    }
    var ergebnis = new ArrayList<String>();
    for (int i = 0; i < roh.length; i++) {
      if (kolumnentitel[i]) {
        continue;
      }
      // Eine verirrte „Anlage N“-Marke direkt vor einem Seitenkopf gehört zum Seitenmöbel.
      if (ANLAGE_MARKE.matcher(roh[i]).matches()) {
        int j = i + 1;
        while (j < roh.length && roh[j].isBlank()) {
          j++;
        }
        if (j < roh.length && kolumnentitel[j]) {
          continue;
        }
      }
      ergebnis.add(roh[i]);
    }
    return ergebnis;
  }

  private static boolean istKolumnentitel(String zeile) {
    return KOPFZEILE.matcher(zeile).matches()
        || SEITENZAHL.matcher(zeile).matches()
        || BUNDESANZEIGER.matcher(zeile).matches()
        || SEITENMARKER.matcher(zeile).matches()
        || DRUCKSACHE_KOPF.matcher(zeile).matches()
        || BUNDESTAG_KOPF.matcher(zeile).matches()
        || BUNDESRAT_KOPF.matcher(zeile).matches();
  }

  /**
   * Zieht am Zeilenende umbrochene Wörter zusammen. Zwei Formen:
   *
   * <ul>
   *   <li><b>Mit Trennstrich</b> („Bundes-“ + „regierung“): Bei kleingeschriebener Folgezeile wird
   *       der Strich entfernt — außer vor Konjunktionen („Ausgangs- und Hilfsstoffe“). Bei
   *       Großbuchstabe/Ziffer ist es ein umbrochenes Kompositum, der Bindestrich bleibt
   *       („Coronavirus-“ + „Krankheit-2019“).
   *   <li><b>Markerlos</b> (Bundestags-Drucksachen: „Schwel“ + „lenwertes“): Reguläre Umbrüche
   *       enden dort mit Leerzeichen vor dem Zeilenumbruch; endet eine Zeile direkt mit einem
   *       Buchstaben und beginnt die Folgezeile klein, ist es eine Trennung → ohne Leerzeichen
   *       zusammenziehen. Das trifft aber nur zu, wenn die Zeile (fast) die volle Spaltenbreite
   *       ausnutzt — sonst wäre der Umbruch dort nicht nötig gewesen. Kurze, bewusst
   *       abgebrochene Zeilen (z.B. ein Stichwort vor einer hängend eingerückten Definition:
   *       „…Nachhaltigkeitssiegels“ + „das Anbringen …“) werden deshalb ausgenommen — sie sind
   *       ein Wortgrenzen-Umbruch, keine Silbentrennung, auch wenn das Trailing-Space-Signal fehlt.
   * </ul>
   */
  private static ArrayList<String> verbindeUmbrueche(List<String> zeilen) {
    // Markerlose Trennungen sind nur erkennbar, wenn die Quelle die Trailing-Space-Konvention
    // verwendet (PDF-Extraktion). Handgeschriebene Klartextdateien haben keine Trailing-Spaces —
    // dort würde die Heuristik reguläre Umbrüche verschmelzen, also bleibt sie aus.
    var markerlosAktiv = verwendetTrailingSpaces(zeilen);

    var ergebnis = new ArrayList<String>();
    for (int i = 0; i < zeilen.size(); i++) {
      var zeile = zeilen.get(i);
      while (true) {
        var gestutzt = zeile.stripTrailing();
        var mitTrennstrich = endetMitSilbentrennung(gestutzt);
        var markerlos =
            markerlosAktiv
                && endetMarkerlos(zeile)
                && gestutzt.length() >= typischeZeilenlaenge(zeilen, i) * VOLLZEILE_MINDESTANTEIL;
        if (!mitTrennstrich && !markerlos) {
          break;
        }
        // Leerzeilen (z.B. an Spalten-/Seitenumbrüchen) überspringen.
        int j = i + 1;
        while (j < zeilen.size() && zeilen.get(j).isBlank()) {
          j++;
        }
        if (j >= zeilen.size()) {
          break;
        }
        var naechste = zeilen.get(j).stripLeading();
        int erstesZeichen = naechste.codePointAt(0);
        if (mitTrennstrich) {
          if (Character.isLowerCase(erstesZeichen) && !KONJUNKTION.matcher(naechste).matches()) {
            zeile = gestutzt.substring(0, gestutzt.length() - 1) + naechste;
          } else if (Character.isUpperCase(erstesZeichen) || Character.isDigit(erstesZeichen)) {
            zeile = gestutzt + naechste;
          } else {
            break;
          }
        } else {
          if (Character.isLowerCase(erstesZeichen) && !KONJUNKTION.matcher(naechste).matches()) {
            zeile = zeile + naechste;
          } else {
            break;
          }
        }
        i = j;
      }
      ergebnis.add(zeile);
    }
    return ergebnis;
  }

  private static boolean endetMitSilbentrennung(String gestutzteZeile) {
    if (!gestutzteZeile.endsWith("-") || gestutzteZeile.length() < 2) {
      return false;
    }
    // Vor dem Bindestrich muss ein Buchstabe stehen („und -gestaltung“ nicht zusammenziehen).
    return Character.isLetter(gestutzteZeile.charAt(gestutzteZeile.length() - 2));
  }

  /** Zeile endet ohne Trailing-Whitespace direkt mit einem Buchstaben. */
  private static boolean endetMarkerlos(String zeile) {
    if (zeile.isEmpty()) {
      return false;
    }
    return Character.isLetter(zeile.charAt(zeile.length() - 1));
  }

  /** Endet ein nennenswerter Teil der nichtleeren Zeilen mit Whitespace? */
  private static boolean verwendetTrailingSpaces(List<String> zeilen) {
    int nichtLeer = 0;
    int mitTrailingSpace = 0;
    for (var zeile : zeilen) {
      if (zeile.isBlank()) {
        continue;
      }
      nichtLeer++;
      if (Character.isWhitespace(zeile.charAt(zeile.length() - 1))) {
        mitTrailingSpace++;
      }
    }
    return mitTrailingSpace > 0 && mitTrailingSpace * 4 >= nichtLeer;
  }

  /**
   * Typische „volle“ Zeilenlänge im Umfeld von {@code zentrum} ({@link #VOLLZEILE_PERZENTIL}-
   * Perzentil der gestutzten Längen nichtleerer Zeilen in einem Fenster von {@link
   * #VOLLZEILE_FENSTER} Zeilen davor/danach) — ein grober Näherungswert für die lokale
   * Spaltenbreite, ohne auf PDF-Positionsdaten zugreifen zu müssen. Lokal statt dokumentweit, weil
   * ein einziges Dokument Abschnitte mit unterschiedlicher Spaltenbreite mischen kann (z.B.
   * schmalerer Regelungstext vs. breitere Begründung in Regierungsentwürfen) — eine dokumentweite
   * Kennzahl würde dort die kürzere Spalte systematisch benachteiligen. Vereinzelte überlange
   * Zeilen (z.B. selbst fälschlich verklebte Umbrüche) dürfen den Wert nicht verzerren, daher ein
   * hohes Perzentil statt des reinen Maximums.
   */
  private static int typischeZeilenlaenge(List<String> zeilen, int zentrum) {
    var laengen = new ArrayList<Integer>();
    int von = Math.max(0, zentrum - VOLLZEILE_FENSTER);
    int bis = Math.min(zeilen.size(), zentrum + VOLLZEILE_FENSTER + 1);
    for (int i = von; i < bis; i++) {
      var zeile = zeilen.get(i);
      if (zeile.isBlank()) {
        continue;
      }
      laengen.add(zeile.stripTrailing().length());
    }
    if (laengen.isEmpty()) {
      return 0;
    }
    laengen.sort(null);
    int index = (int) (laengen.size() * VOLLZEILE_PERZENTIL);
    index = Math.min(index, laengen.size() - 1);
    return laengen.get(index);
  }

  private static String strippeZeilenenden(List<String> zeilen) {
    var sb = new StringBuilder();
    for (var zeile : zeilen) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(zeile.stripTrailing());
    }
    return sb.toString();
  }
}
