// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung;

/**
 * Herkunft eines Änderungsbefehls im Änderungsgesetz.
 *
 * @param artikel die Artikelbezeichnung, z.B. „1“ oder „2a“.
 * @param gliederungsPfad der Pfad der Gliederungspunkte, z.B. „7. a) aa)“.
 * @param originalText der ursprüngliche Befehlstext (mit Zitaten).
 */
public record Provenienz(String artikel, String gliederungsPfad, String originalText) {

  public String anzeigeText() {
    return gliederungsPfad.isEmpty()
        ? "Artikel " + artikel
        : "Artikel " + artikel + " " + gliederungsPfad;
  }
}
