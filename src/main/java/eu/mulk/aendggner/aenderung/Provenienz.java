// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung;

import org.jspecify.annotations.Nullable;

/**
 * Herkunft eines Änderungsbefehls im Änderungsgesetz.
 *
 * @param artikel die Artikelbezeichnung, z.B. „1“ oder „2a“.
 * @param gliederungsPfad der Pfad der Gliederungspunkte, z.B. „7. a) aa)“.
 * @param originalText der ursprüngliche Befehlstext (mit Zitaten).
 * @param seite die Seite des Änderungsdokuments, auf der der Befehl steht; {@code null}, wenn die
 *     Eingabe kein Satzbild trägt (Klartext) oder der Wortlaut sich nicht zweifelsfrei wiederfinden
 *     ließ. Eine falsche Seite wäre schlimmer als keine.
 */
public record Provenienz(
    String artikel, String gliederungsPfad, String originalText, @Nullable Integer seite) {

  public Provenienz(String artikel, String gliederungsPfad, String originalText) {
    this(artikel, gliederungsPfad, originalText, null);
  }

  public String anzeigeText() {
    var sb = new StringBuilder();
    sb.append("Artikel ").append(artikel);
    if (!gliederungsPfad.isEmpty()) {
      sb.append(" ").append(gliederungsPfad);
    }
    if (seite != null) {
      sb.append(" (S. ").append(seite).append(")");
    }
    return sb.toString();
  }
}
