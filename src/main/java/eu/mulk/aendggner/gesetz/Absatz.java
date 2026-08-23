// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz;

import org.jspecify.annotations.Nullable;

/**
 * Ein Absatz einer Einzelnorm.
 *
 * @param nummer die Absatznummer (z.B. „1“, „2a“); {@code null} bei unnummerierten Absätzen.
 * @param text der geflattete Text ohne den führenden Nummernmarker; Aufzählungen als eingerückte
 *     Zeilen („1. …“, „a) …“).
 */
public record Absatz(@Nullable String nummer, String text) {

  /** Text mit vorangestelltem Nummernmarker, wie er angezeigt wird. */
  public String anzeigeText() {
    return nummer == null ? text : "(" + nummer + ") " + text;
  }

  public Absatz mitText(String neuerText) {
    return new Absatz(nummer, neuerText);
  }
}
