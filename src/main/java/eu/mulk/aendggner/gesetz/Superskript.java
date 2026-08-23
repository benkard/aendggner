// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz;

import java.util.regex.Pattern;

/**
 * Hochgestellte Ziffern (¹²³…), wie sie das bayerische Landesrecht als amtliche Satznummern und
 * Fußnotenmarker führt. Die kanonische Textform des ÄndGgner stellt solche Ziffern als
 * Unicode-Superskripte dar, damit Stammgesetz und zitierter Änderungstext dieselbe Schreibweise
 * tragen und Befehle wie „In Satz 1 wird die Satznummerierung „1“ gestrichen“ anwendbar sind.
 */
public final class Superskript {

  private static final String NORMAL = "0123456789";
  private static final String HOCH = "⁰¹²³⁴⁵⁶⁷⁸⁹";

  /** Ein Lauf hochgestellter Ziffern. */
  public static final Pattern LAUF = Pattern.compile("[" + HOCH + "]+");

  private Superskript() {}

  /** Wahr, wenn {@code c} eine hochgestellte Ziffer ist. */
  public static boolean istHochgestellt(char c) {
    return HOCH.indexOf(c) >= 0;
  }

  /** „12“ → „¹²“. Nicht-Ziffern bleiben unverändert. */
  public static String zuSuperskript(String ziffern) {
    var sb = new StringBuilder(ziffern.length());
    for (int i = 0; i < ziffern.length(); i++) {
      var c = ziffern.charAt(i);
      int idx = NORMAL.indexOf(c);
      sb.append(idx >= 0 ? HOCH.charAt(idx) : c);
    }
    return sb.toString();
  }

  /** „¹²“ → „12“. Nicht-Superskripte bleiben unverändert. */
  public static String zuZahl(String superskript) {
    var sb = new StringBuilder(superskript.length());
    for (int i = 0; i < superskript.length(); i++) {
      var c = superskript.charAt(i);
      int idx = HOCH.indexOf(c);
      sb.append(idx >= 0 ? NORMAL.charAt(idx) : c);
    }
    return sb.toString();
  }

  /**
   * Wahr, wenn {@code text} mindestens eine amtliche Satznummer am Satzanfang trägt. Dient dazu,
   * die Schreibweise eines Stammgesetzes zu erkennen (Landesrecht mit amtlicher Satzzählung behält
   * seine Superskripte, Bundesrecht ohne solche verwirft sie).
   */
  public static boolean traegtSatznummern(String text) {
    var m = LAUF.matcher(text);
    while (m.find()) {
      if (istSatzanfang(text, m.start(), m.end())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Wahr, wenn der Superskript-Lauf {@code [start, ende)} in {@code text} eine amtliche Satznummer
   * am Satzanfang ist — im Unterschied zum Fußnotenmarker, der einem Wort anhängt und auf den eine
   * schließende Klammer folgt („Enteignung⁶)“).
   */
  public static boolean istSatzanfang(String text, int start, int ende) {
    if (ende < text.length() && text.charAt(ende) == ')') {
      return false; // Fußnotenmarker „⁶)“
    }
    if (start > 0) {
      var davor = text.charAt(start - 1);
      if (!Character.isWhitespace(davor) && davor != '„' && davor != '(') {
        return false; // hängt einem Wort an → Fußnotenmarker ohne Klammer
      }
    }
    // Nach der Satznummer beginnt der Satz: Buchstabe, Paragraphzeichen, öffnendes Zitat oder
    // öffnende Klammer (z. B. „³§ 23 des Gesetzes …“).
    return ende < text.length()
        && (Character.isLetter(text.charAt(ende))
            || text.charAt(ende) == '§'
            || text.charAt(ende) == '„'
            || text.charAt(ende) == '»'
            || text.charAt(ende) == '"'
            || text.charAt(ende) == '(');
  }
}
