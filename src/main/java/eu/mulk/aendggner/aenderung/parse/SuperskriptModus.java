// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

/**
 * Behandlung hochgestellter Ziffern bei der PDF-Extraktion.
 *
 * <p>Bundesrecht ({@link #ENTFERNEN}): hochgestellte Fußnotenziffern sind Beiwerk und werden — wo
 * sie als eigene Läufe erkennbar sind — verworfen. Bayerisches Landesrecht ({@link #BEHALTEN}):
 * hochgestellte Ziffern sind amtliche Satznummern bzw. Fußnotenmarker und werden als
 * Unicode-Superskripte (¹²³) in den Text übernommen, damit Stammgesetz und zitierter Änderungstext
 * dieselbe kanonische Schreibweise tragen.
 */
public enum SuperskriptModus {
  ENTFERNEN,
  BEHALTEN
}
