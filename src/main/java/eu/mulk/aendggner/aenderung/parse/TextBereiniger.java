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
  // Referenten-/Regierungsentwürfe: „ - 10 - “.
  private static final Pattern SEITENMARKER = Pattern.compile("^\\s*[-–]\\s*\\d+\\s*[-–]\\s*$");
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

  // BMJV-Entwurfsvorlagen zeichnen das hängende öffnende Anführungszeichen im Content-Stream
  // NACH dem ersten Element der zitierten Passage: „(1) „ Ungeachtet…“ statt „„(1) Ungeachtet…“,
  // „§ 19„“ statt „„§ 19“.
  private static final Pattern INVERTIERTES_ZITAT =
      Pattern.compile("(?m)^(\\s*)\\((\\d+[a-z]?)\\) „\\s*");
  private static final Pattern INVERTIERTES_PARAGRAPH_ZITAT =
      Pattern.compile("(?m)^(\\s*)(§\\s*\\d+[a-z]?)„[ \\t]*");

  private TextBereiniger() {}

  public static String bereinige(String rohText) {
    var text = normalisiereAnfuehrungszeichen(rohText);
    text = INVERTIERTES_ZITAT.matcher(text).replaceAll("$1„($2) ");
    text = INVERTIERTES_PARAGRAPH_ZITAT.matcher(text).replaceAll("$1„$2");
    var zeilen = entferneKolumnentitel(text);
    var verbunden = verbindeUmbrueche(zeilen);
    return strippeZeilenenden(verbunden);
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
        .replace("»", "“"); // » → “
  }

  /** Entfernt Seitenkopf-/Fußzeilen. Trailing-Whitespace der übrigen Zeilen bleibt erhalten! */
  private static ArrayList<String> entferneKolumnentitel(String text) {
    var ergebnis = new ArrayList<String>();
    for (var zeile : text.split("\n", -1)) {
      if (KOPFZEILE.matcher(zeile).matches()
          || SEITENZAHL.matcher(zeile).matches()
          || BUNDESANZEIGER.matcher(zeile).matches()
          || SEITENMARKER.matcher(zeile).matches()
          || DRUCKSACHE_KOPF.matcher(zeile).matches()
          || BUNDESTAG_KOPF.matcher(zeile).matches()) {
        continue;
      }
      ergebnis.add(zeile);
    }
    return ergebnis;
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
   *       zusammenziehen.
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
        var markerlos = markerlosAktiv && endetMarkerlos(zeile);
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
