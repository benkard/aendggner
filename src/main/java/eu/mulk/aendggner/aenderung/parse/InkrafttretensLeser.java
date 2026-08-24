// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Inkrafttreten;
import eu.mulk.aendggner.aenderung.Inkrafttreten.Punktbezug;
import eu.mulk.aendggner.aenderung.Inkrafttreten.Regel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Liest den Schlussartikel eines Änderungsgesetzes — den, der das Inkrafttreten regelt.
 *
 * <p>Er wird von der Artikelwahl des {@link AenderungsgesetzParser} nicht erfasst, denn er trägt
 * keine Änderungsformel und nennt kein Stammgesetz. Gelesen werden muss er trotzdem: Ohne ihn weiß
 * niemand, ab wann die errechnete Fassung gilt, und bei gestaffeltem Inkrafttreten gilt sie
 * womöglich nie im ganzen.
 */
public final class InkrafttretensLeser {

  // Absatzmarke „(1) “ am Anfang oder nach Leerraum.
  private static final Pattern ABSATZ = Pattern.compile("(?:^|(?<=\\s))\\((\\d+[a-z]?)\\)\\s+");

  // „… tritt/treten … in Kraft.“ — das Außerkrafttreten bleibt außer Betracht (es sagt nichts
  // darüber, wann die Änderung wirkt), deshalb der Ausschluss von „außer“.
  private static final Pattern IN_KRAFT =
      Pattern.compile("\\b(?:tritt|treten)\\b.{0,400}?(?<!außer )\\bin Kraft\\b", Pattern.DOTALL);

  private static final Pattern SATZ_ENDE = Pattern.compile("(?<!außer )\\bin Kraft\\b\\s*\\.?");

  /** „am 1. Oktober 2024“, „mit Wirkung vom 18. November 2020“. */
  private static final Pattern DATUM = Pattern.compile("\\b(?:am|vom)\\s+" + DeutschesDatum.MUSTER);

  /**
   * „Artikel 1 Nummer 11 Buchstabe a Doppelbuchstabe bb“ — ein Verweis des Schlussartikels auf
   * einen Punkt des eigenen Gesetzes. Bayerische Änderungsgesetze gliedern sich in Paragraphen;
   * deren Schlussvorschrift verweist entsprechend („§ 3 Nr. 2“).
   */
  private static final Pattern PUNKTBEZUG =
      Pattern.compile(
          "(?:Artikel|Art\\.|§)\\s*(\\d+[a-z]?)"
              + "(?:\\s+(?:Nummer|Nr\\.)\\s+(\\d+[a-z]?))?"
              + "(?:\\s+Buchstabe\\s+(\\p{Ll})\\b)?"
              + "(?:\\s+Doppelbuchstabe\\s+(\\p{Ll}{2})\\b)?");

  private static final Pattern VERB = Pattern.compile("\\b(?:tritt|treten)\\b");

  private static final Pattern AENDERUNGSFORMEL = Pattern.compile("wird wie folgt geändert");

  private InkrafttretensLeser() {}

  /**
   * Sucht unter den Artikelblöcken den Schlussartikel und liest ihn.
   *
   * <p>Maßgeblich ist der <em>erste</em> Block ab {@code abIndex}, der vom Inkrafttreten handelt
   * und selbst nichts ändert. Der Index ist der des letzten Artikels, der das Stammgesetz betrifft:
   * Ein Gesetzblatt enthält mehrere Gesetze mit je eigener Schlussvorschrift, und maßgeblich ist
   * die, die auf das eigene Gesetz folgt — nicht die eines fremden weiter vorn.
   *
   * @return {@code null}, wenn kein solcher Block da ist oder er keine lesbare Anordnung enthält.
   */
  public static @Nullable Inkrafttreten waehle(List<String> blockTexte, int abIndex) {
    for (int i = Math.max(abIndex, 0); i < blockTexte.size(); i++) {
      var text = blockTexte.get(i);
      if (AENDERUNGSFORMEL.matcher(text).find()) {
        continue;
      }
      var gelesen = lies(text);
      if (gelesen != null) {
        return gelesen;
      }
    }
    return null;
  }

  /** Liest die Anordnungen eines einzelnen Blocks; {@code null}, wenn er keine trägt. */
  public static @Nullable Inkrafttreten lies(String blockText) {
    var text = ohneUeberschrift(blockText).replaceAll("\\s+", " ").strip();
    var regeln = new ArrayList<Regel>();
    for (var absatz : teileInAbsaetze(text)) {
      var regel = liesAbsatz(absatz);
      if (regel != null) {
        regeln.add(regel);
      }
    }
    return regeln.isEmpty() ? null : new Inkrafttreten(List.copyOf(regeln));
  }

  /**
   * Streicht die Überschrift des Artikels („Inkrafttreten“), die als eigene Zeile vor der Anordnung
   * steht. Ohne das begänne der Wortlaut der Regel mit ihr, denn ein Artikel ohne Absatzmarken ist
   * im ganzen ein Absatz.
   */
  private static String ohneUeberschrift(String blockText) {
    var zeilen = blockText.lines().toList();
    for (int i = 0; i < zeilen.size(); i++) {
      var zeile = zeilen.get(i).strip();
      if (zeile.isEmpty()) {
        continue;
      }
      // Auf das bloße Vorkommen von „tritt“ ist kein Verlass: Die Überschrift „Inkrafttreten“
      // trägt es selbst. Maßgeblich ist das Wort.
      if (ABSATZ.matcher(zeile).lookingAt() || VERB.matcher(zeile).find()) {
        return String.join("\n", zeilen.subList(i, zeilen.size()));
      }
      // Eine Überschrift steht für sich und endet nicht wie ein Satz.
      if (zeile.endsWith(".") || zeile.length() > 60) {
        return String.join("\n", zeilen.subList(i, zeilen.size()));
      }
    }
    return blockText;
  }

  /**
   * Zerlegt den Schlussartikel an seinen Absatzmarken. Ohne Marken ist der ganze Text ein Absatz
   * („Dieses Gesetz tritt am Tage nach seiner Verkündung in Kraft.“).
   */
  private static List<String> teileInAbsaetze(String text) {
    var marken = ABSATZ.matcher(text);
    var grenzen = new ArrayList<Integer>();
    while (marken.find()) {
      grenzen.add(marken.start());
    }
    if (grenzen.isEmpty()) {
      return List.of(text);
    }
    var absaetze = new ArrayList<String>(grenzen.size());
    for (int i = 0; i < grenzen.size(); i++) {
      int ende = i + 1 < grenzen.size() ? grenzen.get(i + 1) : text.length();
      absaetze.add(text.substring(grenzen.get(i), ende));
    }
    return absaetze;
  }

  private static @Nullable Regel liesAbsatz(String absatz) {
    if (!IN_KRAFT.matcher(absatz).find()) {
      return null;
    }
    // Der Anordnungssatz endet bei „in Kraft“; was danach steht, gehört nicht mehr dazu (im GEG
    // klebt der Schlusssatz über die Rechte des Bundesrates unmittelbar an ihm).
    var ende = SATZ_ENDE.matcher(absatz);
    var satz = ende.find() ? absatz.substring(0, ende.end()).strip() : absatz.strip();
    // Die Absatzmarke selbst gehört nicht zum Wortlaut der Anordnung.
    var wortlaut = ABSATZ.matcher(satz).replaceFirst("").strip();

    var datumTreffer = DATUM.matcher(satz);
    var datum =
        datumTreffer.find()
            ? DeutschesDatum.lies(
                datumTreffer.group(1), datumTreffer.group(2), datumTreffer.group(3))
            : null;

    return new Regel(datum, wortlaut, bezuege(satz));
  }

  /**
   * Die Punkte, die der Satz benennt. Nennt er keine, so ist es die Grundregel („Dieses Gesetz
   * tritt … in Kraft“) — der Vorbehalt zugunsten späterer Absätze („vorbehaltlich des Absatzes 2“)
   * verweist auf den Schlussartikel selbst und ist kein Punktbezug.
   */
  private static List<Punktbezug> bezuege(String satz) {
    var bezuege = new ArrayList<Punktbezug>();
    var treffer = PUNKTBEZUG.matcher(satz);
    while (treffer.find()) {
      var pfad = new StringBuilder();
      if (treffer.group(2) != null) {
        pfad.append(treffer.group(2)).append('.');
        if (treffer.group(3) != null) {
          pfad.append(' ').append(treffer.group(3)).append(')');
          if (treffer.group(4) != null) {
            pfad.append(' ').append(treffer.group(4)).append(')');
          }
        }
      }
      bezuege.add(new Punktbezug(treffer.group(1), pfad.toString()));
    }
    return List.copyOf(bezuege);
  }
}
