// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.gesetz.Superskript;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Zerlegt einen Absatztext in Sätze im Rechtssinne, mit einer Stoppliste für juristische
 * Abkürzungen und Datumsangaben („Abs.“, „31. März 2021“).
 *
 * <p>Trägt der Text amtliche Satznummern als Unicode-Superskripte (bayerisches Landesrecht, „¹Die
 * freilebende Tierwelt …“), wird exakt an diesen geteilt statt heuristisch.
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
          "etc",
          // Fundstellen-Abkürzungen. Sie stehen mitten im Satz vor einem Großbuchstaben („… vom
          // 23. Juni 2021 (BGBl. I S. 1982) in der jeweils geltenden Fassung …“) und rissen den
          // Satz sonst genau dort auseinander.
          "BGBl",
          "RGBl",
          "GBl",
          "GVBl",
          "GVOBl",
          "GV",
          "ABl",
          "AmtsBl",
          "BAnz",
          "BayRS",
          "NRW",
          "NW",
          "Nds",
          "Bek");

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
   * Leerraum, dann Großbuchstabe, Zitat, Klammer oder Paragraphenzeichen — Rechtssätze beginnen
   * häufig mit einem Verweis („… zusammengefasst werden. § 27 Absatz 2 … bleibt unberührt.“).
   */
  private static final Pattern SATZENDE = Pattern.compile("(\\.[“)]*)\\s+(?=[A-ZÄÖÜ„(§])");

  private SatzTeiler() {}

  public static List<SatzBereich> teile(String text) {
    var nummeriert = teileAnSatznummern(text);
    if (nummeriert != null) {
      return nummeriert;
    }
    var bereiche = new ArrayList<SatzBereich>();
    var matcher = SATZENDE.matcher(text);
    int start = 0;
    while (matcher.find()) {
      int punkt = matcher.start();
      if (istAbkuerzung(text, punkt)
          || istDatum(text, punkt, matcher.end())
          || istAufzaehlungsMarke(text, punkt)) {
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

  /**
   * Teilt an amtlichen Satznummern (Superskript-Läufe an Satzanfängen); {@code null}, wenn der Text
   * keine trägt. Text vor der ersten Satznummer (und Fußnotenzeilen nach dem letzten Satz) bleibt
   * dem jeweils angrenzenden Satz zugeschlagen.
   */
  private static @Nullable List<SatzBereich> teileAnSatznummern(String text) {
    // Satzgrenzen: stets der Textanfang (Satz 1 samt etwaigem Vorspann vor „¹“) plus die Position
    // jeder Satznummer ab „²“. Eine allein stehende Nummer ≥ 2 (weil Satz 1 unnummeriert ist oder
    // seine Nummer zuvor gestrichen wurde, „Verboten … ²Art. 33 …“) bildet so eine eigene Grenze.
    var matcher = Superskript.LAUF.matcher(text);
    var grenzen = new ArrayList<Integer>();
    grenzen.add(0);
    boolean gefunden = false;
    while (matcher.find()) {
      if (Superskript.istSatzanfang(text, matcher.start(), matcher.end())) {
        gefunden = true;
        int nummer = Integer.parseInt(Superskript.zuZahl(matcher.group()));
        if (nummer >= 2 && matcher.start() > 0) {
          grenzen.add(matcher.start());
        }
      }
    }
    if (!gefunden) {
      return null;
    }
    var bereiche = new ArrayList<SatzBereich>();
    for (int k = 0; k < grenzen.size(); k++) {
      int von = grenzen.get(k);
      int bis = k + 1 < grenzen.size() ? grenzen.get(k + 1) : text.length();
      while (bis > von && Character.isWhitespace(text.charAt(bis - 1))) {
        bis--;
      }
      if (bis > von) {
        bereiche.add(new SatzBereich(von, bis));
      }
    }
    return bereiche;
  }

  /** Die amtliche Satznummer am Anfang des Satztexts; {@code null}, wenn er keine trägt. */
  public static @Nullable Integer nummerVonSatz(String satzText) {
    var s = satzText.stripLeading();
    var matcher = Superskript.LAUF.matcher(s);
    if (!matcher.lookingAt() || !Superskript.istSatzanfang(s, 0, matcher.end())) {
      return null;
    }
    return Integer.parseInt(Superskript.zuZahl(matcher.group()));
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

  /**
   * Punkt einer Aufzählungsmarke am Zeilenanfang („… folgende Aufgaben ⏎ 1. Erlaß von Satzungen
   * …“). Er beendet keinen Satz: die Glieder einer Aufzählung gehören zum tragenden Satz. Die
   * Beschränkung auf den Zeilenanfang unterscheidet die Marke von einer Zahl am Satzende („…
   * beträgt 30. Die Frist …“).
   */
  private static boolean istAufzaehlungsMarke(String text, int punktPosition) {
    var wort = wortVor(text, punktPosition);
    if (!wort.matches("\\d{1,2}[a-z]?")) {
      return false;
    }
    int anfang = punktPosition - wort.length();
    return anfang == 0 || text.charAt(anfang - 1) == '\n';
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
