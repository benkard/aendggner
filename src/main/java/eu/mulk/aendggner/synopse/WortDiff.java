// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.synopse;

import com.github.difflib.text.DiffRowGenerator;
import java.util.List;

/**
 * Erzeugt einen wortweisen HTML-Diff zweier Texte: Entferntes links als {@code <del>}, Neues rechts
 * als {@code <ins>}. Die Eingaben werden vor dem Taggen HTML-escapet.
 */
final class WortDiff {

  /** Beide Spalten einer Diff-Darstellung, als HTML. */
  record Spalten(String altHtml, String neuHtml) {}

  private static final DiffRowGenerator GENERATOR =
      DiffRowGenerator.create()
          .showInlineDiffs(true)
          .mergeOriginalRevised(false)
          .inlineDiffByWord(true)
          .oldTag(oeffnend -> oeffnend ? "<del>" : "</del>")
          .newTag(oeffnend -> oeffnend ? "<ins>" : "</ins>")
          .lineNormalizer(WortDiff::escapeHtml)
          .build();

  private WortDiff() {}

  static Spalten vergleiche(String altText, String neuText) {
    var zeilenAlt = altText.lines().toList();
    var zeilenNeu = neuText.lines().toList();
    var zeilen = GENERATOR.generateDiffRows(zeilenAlt, zeilenNeu);

    var alt = new StringBuilder();
    var neu = new StringBuilder();
    for (var zeile : zeilen) {
      if (!zeile.getOldLine().isEmpty()) {
        if (alt.length() > 0) {
          alt.append('\n');
        }
        alt.append(zeile.getOldLine());
      }
      if (!zeile.getNewLine().isEmpty()) {
        if (neu.length() > 0) {
          neu.append('\n');
        }
        neu.append(zeile.getNewLine());
      }
    }
    return new Spalten(alt.toString(), neu.toString());
  }

  static String escapeHtml(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  static List<String> nurEscapen(List<String> zeilen) {
    return zeilen.stream().map(WortDiff::escapeHtml).toList();
  }
}
