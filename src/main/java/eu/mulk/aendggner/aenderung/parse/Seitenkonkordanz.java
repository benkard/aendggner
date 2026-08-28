// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Zuordnung des Befehlstextes zur Seite des Änderungsdokuments, auf der er steht.
 *
 * <p>Wozu: Bleibt ein Befehl liegen, so nennt die Synopse bislang Artikel und Gliederungspunkt —
 * wer nachsehen will, was im Heft wirklich steht, muss die Seite selbst suchen. Bei einem Heft von
 * achtzig Seiten ist das die eigentliche Arbeit.
 *
 * <p>Wie: nicht durch eine Marke im Textstrom. Der Bereiniger zieht weiche Umbrüche zusammen,
 * entfernt Kolumnentitel und schneidet Seitenfüße heraus; eine mitreisende Marke geriete dabei
 * entweder vor einen Zeilenanfangs-Anker (und nähme jedem Normkopf-Muster seine Grundlage) oder
 * verschwände mit der Zeile, die sie trug. Stattdessen wird der <em>Wortbestand</em> des Auszugs
 * seitenweise festgehalten und der Befehlstext darin wiedergefunden: Beide werden auf Buchstaben
 * und Ziffern heruntergebrochen, sodass Silbentrennung, Anführungszeichen, Leerraum und Satzzeichen
 * — also gerade das, woran der Bereiniger arbeitet — den Vergleich nicht stören.
 *
 * <p>Gefunden wird von einem fortschreitenden Leser aus (siehe {@link Leser}): Die Befehle werden
 * in der Reihenfolge des Dokuments erschlossen, und derselbe kurze Wortlaut („Absatz 3 wird
 * aufgehoben“) steht in einem Heft dutzendfach. Was sich nicht zweifelsfrei wiederfindet, bleibt
 * ohne Seitenangabe; eine falsche Seite wäre schlimmer als keine.
 */
public final class Seitenkonkordanz {

  /** Für Klartext-Eingaben und synthetische Texte: kein Satzbild, keine Seiten. */
  public static final Seitenkonkordanz LEER = new Seitenkonkordanz("", new int[0]);

  /**
   * Mindestlänge des Suchstücks. Kürzeres ist kein Wortlaut, sondern eine Marke („a)“) und stünde
   * auf jeder zweiten Seite.
   */
  private static final int MIND_LAENGE = 8;

  /** Länge des Suchstücks. Lang genug, um zu unterscheiden, kurz genug, um heil zu bleiben. */
  private static final int SUCHSTUECK = 60;

  /** Zweiter Versuch mit kürzerem Stück, wenn ein Seitenfuß den Wortlaut zerschnitten hat. */
  private static final int SUCHSTUECK_KURZ = 24;

  /** Der Wortbestand des Auszugs: nur Buchstaben und Ziffern, kleingeschrieben. */
  private final String wortbestand;

  /** Je Zeichen des Wortbestandes die Seite, auf der es steht. */
  private final int[] seiten;

  private Seitenkonkordanz(String wortbestand, int[] seiten) {
    this.wortbestand = wortbestand;
    this.seiten = seiten;
  }

  /** Erhebt den Wortbestand aus den Zeilen des Auszugs, die ihre Seite noch mitführen. */
  static Seitenkonkordanz aus(List<FontgroessenFilter.Zeile> zeilen) {
    var sb = new StringBuilder();
    var seiten = new int[zeilenLaenge(zeilen)];
    for (var zeile : zeilen) {
      if (zeile.seite() <= 0) {
        continue;
      }
      for (int i = 0; i < zeile.text().length(); i++) {
        char c = zeile.text().charAt(i);
        if (Character.isLetterOrDigit(c)) {
          seiten[sb.length()] = zeile.seite();
          sb.append(Character.toLowerCase(c));
        }
      }
    }
    if (sb.isEmpty()) {
      return LEER;
    }
    var gekuerzt = new int[sb.length()];
    System.arraycopy(seiten, 0, gekuerzt, 0, sb.length());
    return new Seitenkonkordanz(sb.toString(), gekuerzt);
  }

  private static int zeilenLaenge(List<FontgroessenFilter.Zeile> zeilen) {
    int summe = 0;
    for (var zeile : zeilen) {
      summe += zeile.text().length();
    }
    return summe;
  }

  /** Ein Leser, der das Dokument von vorn nach hinten durchschreitet. */
  public Leser leser() {
    return new Leser();
  }

  /**
   * Der fortschreitende Leser. Er merkt sich, wie weit die Erschließung im Dokument gediehen ist,
   * und sucht von dort aus vorwärts; nur so trifft ein mehrfach vorkommender Wortlaut die richtige
   * Seite. Zurück geht er nie: Ein Fund vor der Marke bleibt ein Fund, verschiebt sie aber nicht.
   */
  public final class Leser {

    private int marke;

    private Leser() {}

    /**
     * Die Seite, auf der {@code befehlsText} steht, oder {@code null}, wenn er sich nicht
     * wiederfinden lässt.
     */
    public @Nullable Integer seiteVon(String befehlsText) {
      if (wortbestand.isEmpty()) {
        return null;
      }
      var gesucht = nurWortbestand(befehlsText);
      if (gesucht.length() < MIND_LAENGE) {
        return null;
      }
      int index = suche(gesucht, SUCHSTUECK);
      if (index < 0 && gesucht.length() > SUCHSTUECK_KURZ) {
        index = suche(gesucht, SUCHSTUECK_KURZ);
      }
      if (index < 0) {
        return null;
      }
      marke = Math.max(marke, index);
      return seiten[index];
    }

    private int suche(String gesucht, int laenge) {
      var stueck = gesucht.length() > laenge ? gesucht.substring(0, laenge) : gesucht;
      int index = wortbestand.indexOf(stueck, marke);
      return index >= 0 ? index : wortbestand.indexOf(stueck);
    }
  }

  private static String nurWortbestand(String text) {
    var sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }
}
