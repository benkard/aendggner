// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Gliederungspunkte, mit denen ein Änderungsgesetz auf sich selbst verweist: „Nummer 11
 * Buchstabe a Doppelbuchstabe bb“, abgekürzt auch „Nr. 8 Buchst. a“.
 *
 * <p>Zwei Stellen brauchen dieselbe Lesart, und beide meinen dasselbe Gebilde: der Schlussartikel,
 * der das Inkrafttreten eines einzelnen Punktes anordnet ({@link InkrafttretensLeser}), und der
 * verweisende Befehl, der einen anderen Punkt desselben Artikels sinngemäß übernimmt ({@code
 * Aenderungsbefehl.VerweisenderBefehl}). Geschrieben wird der Pfad so, wie {@link
 * eu.mulk.aendggner.aenderung.Provenienz#gliederungsPfad()} ihn führt — „8. a) bb)“ —, denn an ihm
 * wird verglichen.
 */
public final class PunktPfad {

  /**
   * Nummer, Buchstabe und Doppelbuchstabe als Gruppen 1 bis 3. Ohne Anker; zum Einbetten gedacht,
   * weshalb die Zählung der Gruppen beim Einbetten um den vorangehenden Teil zu versetzen ist.
   */
  public static final String MUSTER =
      "(?:\\s*(?:Nummer|Nr\\.)\\s+(\\d+[a-z]?))?"
          + "(?:\\s*(?:Buchstabe|Buchst\\.)\\s+(\\p{Ll})\\b)?"
          + "(?:\\s*(?:Doppelbuchstabe|Doppelbuchst\\.)\\s+(\\p{Ll}{2})\\b)?";

  private static final Pattern GANZ = Pattern.compile("^" + MUSTER + "\\s*$");

  private PunktPfad() {}

  /**
   * Der Pfad in der Schreibweise der Provenienz; leer, wenn der Text keinen Punkt nennt. Leer heißt
   * dabei: der ganze Artikel — nicht etwa „nichts gefunden“; wer das unterscheiden muss, prüfe den
   * Text zuvor.
   */
  public static String aus(String text) {
    var m = GANZ.matcher(text.strip());
    return m.matches() ? baue(m, 0) : "";
  }

  /**
   * Baut den Pfad aus einem Treffer, dessen Gruppen {@code versatz + 1} bis {@code versatz + 3} die
   * Nummer, den Buchstaben und den Doppelbuchstaben tragen.
   */
  public static String baue(Matcher treffer, int versatz) {
    var pfad = new StringBuilder();
    if (treffer.group(versatz + 1) != null) {
      pfad.append(treffer.group(versatz + 1)).append('.');
      if (treffer.group(versatz + 2) != null) {
        pfad.append(' ').append(treffer.group(versatz + 2)).append(')');
        if (treffer.group(versatz + 3) != null) {
          pfad.append(' ').append(treffer.group(versatz + 3)).append(')');
        }
      }
    }
    return pfad.toString();
  }
}
