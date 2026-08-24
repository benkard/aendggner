// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

/**
 * Die Art eines Grundes, aus dem ein Befehl nicht angewandt wurde.
 *
 * <p>Der ausformulierte Grund bleibt daneben stehen und wird nicht ersetzt — der Grundsatz, nichts
 * stillschweigend zu verwerfen, verlangt die genaue Auskunft im Einzelfall. Die Art ordnet sie nur:
 * Eine Synopse mit fünfzig Resten ist ohne Ordnung eine Liste, aus der niemand einen Schluss zieht;
 * nach Arten gebündelt und ausgezählt zeigt sie auf einen Blick, ob eine Vorlage am Werkzeug
 * scheitert, am Wortlaut des Änderungsgesetzes oder am falschen Stand des Stammgesetzes.
 *
 * <p>Die Reihenfolge ist die der Darstellung: erst, was am Befehl liegt, dann, was am Stammgesetz
 * liegt, zuletzt die bewusst gezogenen Grenzen.
 */
public enum Grund {

  /** Der Befehl wurde nicht als Änderungsbefehl erkannt. */
  NICHT_ERKANNT("Befehl nicht erkannt"),

  /** Die benannte Stelle ist im Stammgesetz nicht auffindbar. */
  STELLE_NICHT_AUFLOESBAR("Stelle nicht auffindbar"),

  /** Der zu ersetzende oder zu streichende Wortlaut steht nicht im Zieltext. */
  ZIELTEXT_FEHLT("Zieltext nicht vorhanden"),

  /** Die Stelle oder der Zieltext kommt mehrfach vor; welche Fundstelle gemeint ist, ist offen. */
  MEHRDEUTIG("Fundstelle mehrdeutig"),

  /** Ein Bereich ist leer, absteigend oder überschreitet die Einheit, in der er liegen müsste. */
  BEREICH_UNGUELTIG("Bereich unbrauchbar"),

  /** Das Zitat trägt nicht, was der Befehl ihm entnehmen will (Normkopf, Absatz, Überschrift). */
  ZITAT_UNBRAUCHBAR("Zitat unbrauchbar"),

  /** Der Bestand des Stammgesetzes widerspricht dem Befehl (Bezeichnung belegt oder fehlt). */
  BESTAND_WIDERSPRICHT("Bestand widerspricht dem Befehl"),

  /** Die Anwendung dieser Befehlsart ist nicht umgesetzt; die Grenze ist bewusst gezogen. */
  NICHT_UNTERSTUETZT("Nicht unterstützt"),

  /** Die Anwendung ist mit einem Fehler abgebrochen. */
  FEHLGESCHLAGEN("Anwendung fehlgeschlagen"),

  /** Der Befehl war am gewählten Stichtag noch nicht in Kraft; angewandt wurde er deshalb nicht. */
  NOCH_NICHT_IN_KRAFT("Noch nicht in Kraft");

  private final String bezeichnung;

  Grund(String bezeichnung) {
    this.bezeichnung = bezeichnung;
  }

  /** Die Aufschrift der Gruppe in der Synopse. */
  public String bezeichnung() {
    return bezeichnung;
  }
}
