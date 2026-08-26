// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz;

import eu.mulk.aendggner.aenderung.parse.DeutschesDatum;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Ein Änderungsheft, das auf diese Fassung bereits angewandt worden ist.
 *
 * <p>Ein Befehl ist keine Zustandsbeschreibung, sondern eine Anordnung: Wer zweimal anfügt, fügt
 * zweimal an. Dasselbe Heft ein zweites Mal auf seine eigene Ausgabe anzuwenden ist deshalb keine
 * folgenlose Wiederholung — und dem Wortlaut allein ist das nicht anzusehen. Die Fassung führt
 * darum mit, was auf ihr schon geschehen ist; im kanonischen Klartext steht das als Zeile
 * „Fortgeschrieben durch: …“.
 *
 * <p>Die Bezeichnung ist zugleich der Schlüssel: Zwei Hefte gelten als dasselbe, wenn sie
 * gleichlautend bezeichnet sind. Das trägt, weil die Bezeichnung aus dem Dokument selbst stammt
 * (Art und Ausfertigungsdatum, bei Drucksachen deren Nummer) und nicht aus dem Dateinamen.
 *
 * @param bezeichnung wie das Heft sich nennt, z.B. „Änderungsgesetz vom 22. April 2026“.
 * @param datum sein Ausfertigungsdatum, soweit die Bezeichnung eines nennt. Daran hängt die
 *     Altersrüge: Nach der Fortschreibung ist der Wortlaut so jung wie das jüngste angewandte Heft.
 */
public record Fortschreibung(String bezeichnung, @Nullable LocalDate datum) {

  /** „… vom 22. April 2026“ — das Ausfertigungsdatum, wie die Bezeichnung es führt. */
  private static final Pattern VOM = Pattern.compile("\\bvom " + DeutschesDatum.MUSTER + "\\b");

  /**
   * Liest eine Bezeichnung, wie der Klartext sie führt, und gewinnt das Datum aus ihr zurück. Die
   * Bezeichnung bleibt dabei unangetastet — ausgegeben wird, was eingelesen wurde.
   */
  public static Fortschreibung aus(String bezeichnung) {
    var m = VOM.matcher(bezeichnung);
    if (!m.find()) {
      return new Fortschreibung(bezeichnung, null);
    }
    return new Fortschreibung(bezeichnung, DeutschesDatum.lies(m.group(1), m.group(2), m.group(3)));
  }
}
