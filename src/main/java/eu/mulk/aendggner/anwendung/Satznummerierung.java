// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Norm;
import eu.mulk.aendggner.gesetz.Superskript;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Amtliche Satznummern für neu gesetzten Text.
 *
 * <p>Ein Teil der Länder zählt die Sätze eines Absatzes amtlich mit („(2) ¹Gegen einen … ²In diesem
 * Fall …“). Das Gesetzblatt, aus dem der neue Wortlaut zitiert wird, setzt diese Zählung nicht —
 * sie gehört zur Fassung, nicht zum Verkündungstext. Wer den Zitatwortlaut unverändert einsetzt,
 * erhält deshalb einen Absatz, der als einziger seines Gesetzes ohne Satzzählung dasteht; die
 * amtliche Nachfassung führt sie (Berlin, ASOG § 67 Absatz 2).
 *
 * <p>Angefasst wird nur, was angefasst werden muss: ein Absatz, der sich geändert hat, keine
 * Zählung trägt, aus mehr als einem Satz besteht und auf einer Zeile steht. Die Mehrzeiligkeit
 * schließt Aufzählungen aus — dort ist der Chapeau ein Satz und jedes Glied ein eigener Block, und
 * eine fortlaufende Satzzählung wäre falsch.
 */
final class Satznummerierung {

  private Satznummerierung() {}

  /** Wahr, wenn das Gesetz die Sätze seiner Absätze amtlich zählt. */
  static boolean fuehrtSatznummern(Gesetz gesetz) {
    for (var norm : gesetz.normen()) {
      for (var absatz : norm.absaetze()) {
        if (Superskript.traegtSatznummern(absatz.text())) {
          return true;
        }
      }
    }
    return false;
  }

  /** Schreibt die Satzzählung in den geänderten Absätzen fort. */
  static List<Norm> schreibeFort(Gesetz alt, List<Norm> normen) {
    var ergebnis = new ArrayList<Norm>(normen.size());
    for (var norm : normen) {
      var alteNorm = alt.norm(norm.enbez()).orElse(null);
      var absaetze = new ArrayList<Absatz>(norm.absaetze().size());
      boolean geaendert = false;
      for (var absatz : norm.absaetze()) {
        var neuerText = fortgeschrieben(absatz, alteNorm);
        geaendert |= !neuerText.equals(absatz.text());
        absaetze.add(neuerText.equals(absatz.text()) ? absatz : absatz.mitText(neuerText));
      }
      ergebnis.add(geaendert ? norm.mitAbsaetzen(absaetze) : norm);
    }
    return ergebnis;
  }

  private static String fortgeschrieben(Absatz absatz, @Nullable Norm alteNorm) {
    var text = absatz.text();
    if (text.contains("\n") || Superskript.traegtSatznummern(text)) {
      return text;
    }
    if (alteNorm != null && trugDenselbenText(alteNorm, absatz)) {
      return text;
    }
    var saetze = SatzTeiler.teileTexte(text);
    if (saetze.size() < 2) {
      return text;
    }
    var sb = new StringBuilder();
    for (int i = 0; i < saetze.size(); i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(Superskript.zuSuperskript(String.valueOf(i + 1))).append(saetze.get(i));
    }
    return sb.toString();
  }

  private static boolean trugDenselbenText(Norm alteNorm, Absatz absatz) {
    for (var alterAbsatz : alteNorm.absaetze()) {
      if (java.util.Objects.equals(alterAbsatz.nummer(), absatz.nummer())) {
        return alterAbsatz.text().equals(absatz.text());
      }
    }
    return false;
  }
}
