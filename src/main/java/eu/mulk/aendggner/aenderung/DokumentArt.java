// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung;

/**
 * Die Art eines eingespeisten Änderungsdokuments, allein aus seinem Text erschlossen.
 *
 * <p>Die Unterscheidung trägt drei Entscheidungen: ob das Dokument überhaupt Änderungsbefehle
 * enthalten <em>kann</em> ({@link #OHNE_BEFEHLE} tut es nicht), ob es vor der gewöhnlichen
 * Verarbeitung noch aufbereitet werden muss ({@link #BESCHLUSSEMPFEHLUNG} trägt seine maßgebliche
 * Fassung in einer zweispaltigen Zusammenstellung, {@link #AENDERUNGSANTRAG} ändert nicht das
 * Stammgesetz, sondern einen Entwurf) und ob die entstehende Synopse geltendes Recht oder erst
 * einen Entwurfsstand zeigt.
 */
public enum DokumentArt {

  /** Verkündetes Artikelgesetz (BGBl, GVBl, GVOBl …). */
  ARTIKELGESETZ,

  /** Gesetzentwurf: Referenten-, Regierungs- oder Fraktionsentwurf, auch als Drucksache. */
  GESETZENTWURF,

  /** Änderungsantrag zu einem Entwurf — ändert eine Drucksache, nicht das Stammgesetz. */
  AENDERUNGSANTRAG,

  /**
   * Beschlussempfehlung eines Ausschusses; die maßgebliche Fassung steht in der zweispaltigen
   * Zusammenstellung (rechte Spalte).
   */
  BESCHLUSSEMPFEHLUNG,

  /**
   * Dokument des Verfahrens ohne Rechtsetzungsbefehle: Entschließungs- und schlichter Antrag,
   * Plenarprotokoll, Bericht. Aus ihm ist keine Synopse zu gewinnen.
   */
  OHNE_BEFEHLE,

  /** Nicht zuzuordnen; wird wie ein Artikelgesetz behandelt, aber gemeldet. */
  UNBEKANNT;

  /** Ob das Dokument einen Entwurfsstand und nicht geltendes Recht beschreibt. */
  public boolean istEntwurfsfassung() {
    return this == GESETZENTWURF || this == AENDERUNGSANTRAG || this == BESCHLUSSEMPFEHLUNG;
  }

  /** Die Bezeichnung für Quellen- und Warnzeilen. */
  public String anzeigeName() {
    return switch (this) {
      case ARTIKELGESETZ -> "Änderungsgesetz";
      case GESETZENTWURF -> "Gesetzentwurf";
      case AENDERUNGSANTRAG -> "Änderungsantrag";
      case BESCHLUSSEMPFEHLUNG -> "Beschlussempfehlung";
      case OHNE_BEFEHLE -> "Dokument ohne Änderungsbefehle";
      case UNBEKANNT -> "Dokument unbekannter Art";
    };
  }
}
