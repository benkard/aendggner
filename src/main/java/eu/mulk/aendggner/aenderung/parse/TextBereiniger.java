package eu.mulk.aendggner.aenderung.parse;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Bereinigt aus PDFs extrahierten Rohtext eines Änderungsgesetzes: entfernt Kolumnentitel und
 * Seitenzahlen des Bundesgesetzblatts, zieht Silbentrennungen am Zeilenende zusammen und
 * normalisiert Anführungszeichen-Glyphen.
 */
public final class TextBereiniger {

  private static final Pattern KOPFZEILE =
      Pattern.compile("^\\s*(\\d{1,5}\\s+)?Bundesgesetzblatt Jahrgang \\d{4}.*$");
  private static final Pattern SEITENZAHL = Pattern.compile("^\\s*\\d{1,5}\\s*$");
  private static final Pattern BUNDESANZEIGER =
      Pattern.compile(
          "^\\s*(Das Bundesgesetzblatt im Internet:|Ein Service des Bundesanzeiger).*$");

  private TextBereiniger() {}

  public static String bereinige(String rohText) {
    var text = normalisiereAnfuehrungszeichen(rohText);
    var zeilen = entferneKolumnentitel(text);
    return verbindeSilbentrennung(zeilen);
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

  private static ArrayList<String> entferneKolumnentitel(String text) {
    var ergebnis = new ArrayList<String>();
    for (var zeile : text.split("\n", -1)) {
      if (KOPFZEILE.matcher(zeile).matches()
          || SEITENZAHL.matcher(zeile).matches()
          || BUNDESANZEIGER.matcher(zeile).matches()) {
        continue;
      }
      ergebnis.add(zeile.stripTrailing());
    }
    return ergebnis;
  }

  /** Konjunktionen, die typischerweise auf einen Suspensivstrich folgen („Wirk- und …“). */
  private static final Pattern KONJUNKTION =
      Pattern.compile("^(und|oder|sowie|bzw\\.|beziehungsweise)\\b.*");

  /**
   * Zieht Silbentrennung am Zeilenende zusammen. Beginnt die Folgezeile mit einem Kleinbuchstaben,
   * wird der Trennstrich entfernt („Bundes-“ + „regierung“ → „Bundesregierung“) — außer vor
   * Konjunktionen, die auf einen Suspensivstrich hindeuten („Ausgangs- und Hilfsstoffe“). Beginnt
   * sie mit Großbuchstabe oder Ziffer, handelt es sich um ein umbrochenes Kompositum; der
   * Bindestrich bleibt erhalten („Coronavirus-“ + „Krankheit-2019“ → „Coronavirus-Krankheit-2019“).
   */
  private static String verbindeSilbentrennung(ArrayList<String> zeilen) {
    var sb = new StringBuilder();
    for (int i = 0; i < zeilen.size(); i++) {
      var zeile = zeilen.get(i);
      if (sb.length() > 0) {
        sb.append('\n');
      }
      while (endetMitSilbentrennung(zeile)) {
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
        if (Character.isLowerCase(erstesZeichen) && !KONJUNKTION.matcher(naechste).matches()) {
          zeile = zeile.substring(0, zeile.length() - 1) + naechste;
        } else if (Character.isUpperCase(erstesZeichen) || Character.isDigit(erstesZeichen)) {
          zeile = zeile + naechste;
        } else {
          break;
        }
        i = j;
      }
      sb.append(zeile);
    }
    return sb.toString();
  }

  private static boolean endetMitSilbentrennung(String zeile) {
    if (!zeile.endsWith("-") || zeile.length() < 2) {
      return false;
    }
    // Vor dem Bindestrich muss ein Buchstabe stehen („und -gestaltung“ nicht zusammenziehen).
    return Character.isLetter(zeile.charAt(zeile.length() - 2));
  }
}
