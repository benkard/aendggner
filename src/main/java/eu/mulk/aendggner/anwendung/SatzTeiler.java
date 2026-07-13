package eu.mulk.aendggner.anwendung;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Zerlegt einen Absatztext in Sätze im Rechtssinne, mit einer Stoppliste für juristische
 * Abkürzungen und Datumsangaben („Abs.“, „31. März 2021“).
 */
public final class SatzTeiler {

  /** Ein Satz als Zeichenbereich {@code [von, bis)} im Absatztext. */
  public record SatzBereich(int von, int bis) {}

  private static final Set<String> ABKUERZUNGEN =
      Set.of(
          "Abs",
          "Nr",
          "Nrn",
          "Buchst",
          "S",
          "Art",
          "bzw",
          "vgl",
          "ggf",
          "gem",
          "insbes",
          "u",
          "z",
          "v",
          "d",
          "h",
          "B",
          "ff",
          "Halbs",
          "Doppelbuchst",
          "usw",
          "etc");

  private static final Set<String> MONATE =
      Set.of(
          "Januar",
          "Februar",
          "März",
          "April",
          "Mai",
          "Juni",
          "Juli",
          "August",
          "September",
          "Oktober",
          "November",
          "Dezember");

  /**
   * Kandidat für ein Satzende: Punkt (ggf. gefolgt von schließendem Zitat oder Klammer), dann
   * Leerraum, dann Großbuchstabe, Zitat oder Klammer.
   */
  private static final Pattern SATZENDE = Pattern.compile("(\\.[“)]*)\\s+(?=[A-ZÄÖÜ„(])");

  private SatzTeiler() {}

  public static List<SatzBereich> teile(String text) {
    var bereiche = new ArrayList<SatzBereich>();
    var matcher = SATZENDE.matcher(text);
    int start = 0;
    while (matcher.find()) {
      int punkt = matcher.start();
      if (istAbkuerzung(text, punkt) || istDatum(text, punkt, matcher.end())) {
        continue;
      }
      bereiche.add(new SatzBereich(start, matcher.end(1)));
      start = matcher.end();
    }
    if (start < text.length()) {
      bereiche.add(new SatzBereich(start, text.length()));
    }
    return bereiche;
  }

  /** Die Satztexte statt der Bereiche. */
  public static List<String> teileTexte(String text) {
    return teile(text).stream().map(b -> text.substring(b.von(), b.bis()).strip()).toList();
  }

  private static boolean istAbkuerzung(String text, int punktPosition) {
    var wort = wortVor(text, punktPosition);
    return ABKUERZUNGEN.contains(wort);
  }

  /** „31. März 2021“ — Ziffern vor dem Punkt, Monatsname dahinter. */
  private static boolean istDatum(String text, int punktPosition, int naechstesWortPosition) {
    var wort = wortVor(text, punktPosition);
    if (!wort.matches("\\d{1,2}")) {
      return false;
    }
    var rest = text.substring(naechstesWortPosition);
    var naechstesWort = rest.split("[\\s,.;]", 2)[0];
    return MONATE.contains(naechstesWort);
  }

  private static String wortVor(String text, int position) {
    int ende = position;
    int anfang = ende;
    while (anfang > 0 && Character.isLetterOrDigit(text.charAt(anfang - 1))) {
      anfang--;
    }
    return text.substring(anfang, ende);
  }
}
