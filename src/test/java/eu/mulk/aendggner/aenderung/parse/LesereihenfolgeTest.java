// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.parse.FontgroessenFilter.Zeile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Die Lesereihenfolge am nachgestellten Satzbild eines Gesetzblatts. */
class LesereihenfolgeTest {

  /** Der Satzspiegel des Berliner Blattes: zwei Spalten von 45 bis 289 und von 332 bis 547. */
  private static Zeile links(int seite, float y, String text) {
    return new Zeile(seite, y, 45f, 289f, text);
  }

  private static Zeile rechts(int seite, float y, String text) {
    return new Zeile(seite, y, 332f, 547f, text);
  }

  private static Zeile breit(int seite, float y, String text) {
    return new Zeile(seite, y, 127f, 465f, text);
  }

  private static List<String> texte(List<Zeile> zeilen) {
    var namen = new ArrayList<String>();
    for (var zeile : zeilen) {
      namen.add(zeile.text());
    }
    return namen;
  }

  /**
   * Der Fall, der den Anstoß gab: Das Blatt zeichnet den ganzseitenbreiten Titelblock zuletzt,
   * obgleich er über beiden Spalten steht. Er gehört vor sie — und die Spalten bleiben ganz.
   */
  @Test
  void derZuletztGezeichneteTitelblockGehoertNachOben() {
    var seite = new ArrayList<Zeile>();
    seite.add(breit(1, 51, "Kolumnentitel"));
    for (int i = 0; i < 5; i++) {
      seite.add(links(1, 345 + 10 * i, "links " + i));
    }
    for (int i = 0; i < 5; i++) {
      seite.add(rechts(1, 345 + 10 * i, "rechts " + i));
    }
    seite.add(breit(1, 280, "Gesetz zur Änderung"));
    seite.add(breit(1, 293, "Vom 3. Juni 2026"));

    assertThat(texte(Lesereihenfolge.ordne(seite)))
        .containsExactly(
            "Kolumnentitel",
            "Gesetz zur Änderung",
            "Vom 3. Juni 2026",
            "links 0",
            "links 1",
            "links 2",
            "links 3",
            "links 4",
            "rechts 0",
            "rechts 1",
            "rechts 2",
            "rechts 3",
            "rechts 4");
  }

  /**
   * Die Falle des einfachen XY-Schnitts: Läge zwischen den Zeilen 4 und 5 ein weites Band, so
   * zerfiele die Seite erst in oben und unten — gelesen würde links oben, rechts oben, links unten,
   * rechts unten. Die Spalte hat Vorrang vor dem Band.
   */
  @Test
  void eineLueckeAufGleicherHoeheZerreisstDieSpaltenNicht() {
    var seite = new ArrayList<Zeile>();
    seite.add(breit(1, 51, "Kolumnentitel"));
    float[] hoehen = {100, 110, 120, 130, 300, 310, 320, 330};
    for (float y : hoehen) {
      seite.add(links(1, y, "links " + (int) y));
    }
    for (float y : hoehen) {
      seite.add(rechts(1, y, "rechts " + (int) y));
    }

    var geordnet = texte(Lesereihenfolge.ordne(seite));
    assertThat(geordnet).startsWith("Kolumnentitel", "links 100");
    assertThat(geordnet.subList(1, 9)).allMatch(t -> t.startsWith("links"));
    assertThat(geordnet.subList(9, 17)).allMatch(t -> t.startsWith("rechts"));
  }

  /** Einspaltiger Satz hat keine Rinne; da bleibt es beim Inhaltsstrom. */
  @Test
  void einspaltigerSatzBleibtUnangetastet() {
    var seite = new ArrayList<Zeile>();
    for (int i = 0; i < 10; i++) {
      seite.add(breit(1, 100 + 10 * i, "zeile " + i));
    }
    assertThat(Lesereihenfolge.ordne(seite)).isEqualTo(seite);
  }

  /** Fehlt einer Zeile die Geometrie, so ist die ganze Seite nicht zu vermessen. */
  @Test
  void ohneGeometrieBleibtDieSeiteWieSieIst() {
    var seite = new ArrayList<Zeile>();
    seite.add(new Zeile(0, Float.NaN, Float.NaN, Float.NaN, "unbekannt"));
    for (int i = 0; i < 5; i++) {
      seite.add(links(0, 100 + 10 * i, "links " + i));
      seite.add(rechts(0, 100 + 10 * i, "rechts " + i));
    }
    assertThat(Lesereihenfolge.ordne(seite)).isEqualTo(seite);
  }
}
