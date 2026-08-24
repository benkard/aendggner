// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Provenienz;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Der Schlussartikel, gelesen — die Wortlaute stammen sämtlich aus den Beispieldaten. */
class InkrafttretensLeserTest {

  private static Provenienz punkt(String artikel, String pfad) {
    return new Provenienz(artikel, pfad, "");
  }

  @Test
  void schlichtesInkrafttretenOhneStaffelung() {
    var inkrafttreten =
        InkrafttretensLeser.lies("Dieses Gesetz tritt am 12. Juni 2026 in Kraft.\nBerlin, den 3.");

    assertThat(inkrafttreten).isNotNull();
    assertThat(inkrafttreten.gestaffelt()).isFalse();
    assertThat(inkrafttreten.grundregel().orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 6, 12));
    // Ohne eigenen Bezug gilt für jeden Punkt die Grundregel.
    assertThat(inkrafttreten.fuer(punkt("1", "3. a)")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 6, 12));
  }

  /** UWG 2026: Ein einzelner Buchstabe tritt drei Monate vor dem übrigen Gesetz in Kraft. */
  @Test
  void gestaffeltMitBuchstabenBezug() {
    var inkrafttreten =
        InkrafttretensLeser.lies(
            """
            (1) Dieses Gesetz tritt vorbehaltlich des Absatzes 2 am 27. September 2026 in Kraft.
            (2) Artikel 1 Nummer 2 Buchstabe c tritt am 19. Juni 2026 in Kraft.
            Die verfassungsmäßigen Rechte des Bundesrates sind gewahrt.
            """);

    assertThat(inkrafttreten).isNotNull();
    assertThat(inkrafttreten.gestaffelt()).isTrue();
    // Der Vorbehalt („vorbehaltlich des Absatzes 2“) verweist auf den Schlussartikel selbst; er
    // ist kein Punktbezug und macht die Grundregel nicht zur Sonderregel.
    assertThat(inkrafttreten.grundregel().orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 9, 27));
    assertThat(inkrafttreten.fuer(punkt("1", "2. c)")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 6, 19));
    // Der Nachbarbuchstabe ist nicht gemeint, und die Nummer selbst auch nicht.
    assertThat(inkrafttreten.fuer(punkt("1", "2. d)")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 9, 27));
    assertThat(inkrafttreten.fuer(punkt("1", "2.")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 9, 27));
  }

  /**
   * GEG 2023: „Abweichend von Absatz 1 treten Artikel 1 Nummer 22 sowie Artikel 3 am … in Kraft.“ —
   * zwei Bezüge in einem Satz, davon einer auf einen ganzen Artikel. Der Schlusssatz über die
   * Rechte des Bundesrates klebt im Gesetzblatt unmittelbar am Anordnungssatz.
   */
  @Test
  void zweiBezuegeInEinemSatzUndAngeklebterSchlusssatz() {
    var inkrafttreten =
        InkrafttretensLeser.lies(
            """
            (1) Dieses Gesetz tritt vorbehaltlich des Absatzes 2 am 1. Januar 2024 in Kraft.
            (2) Abweichend von Absatz 1 treten Artikel 1 Nummer 22 sowie Artikel 3 am 1. Oktober \
            2024 in Kraft. Die verfassungsmäßigen Rechte des Bundesrates sind gewahrt.
            """);

    assertThat(inkrafttreten).isNotNull();
    var sonder = inkrafttreten.sonderregeln();
    assertThat(sonder).hasSize(1);
    assertThat(sonder.get(0).wortlaut()).endsWith("am 1. Oktober 2024 in Kraft.");
    assertThat(inkrafttreten.fuer(punkt("1", "22.")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2024, 10, 1));
    // Ein ganzer Artikel als Bezug erfasst jeden seiner Punkte.
    assertThat(inkrafttreten.fuer(punkt("3", "5. b)")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2024, 10, 1));
    assertThat(inkrafttreten.fuer(punkt("1", "21.")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2024, 1, 1));
  }

  /**
   * IfSG 2020: Rückwirkendes Inkrafttreten („mit Wirkung vom“), ein Doppelbuchstabe als Bezug und
   * eine Grundregel, die kein Datum nennt — der Verkündungstag steht nicht im Gesetzestext, also
   * wird auch keiner erfunden.
   */
  @Test
  void rueckwirkungDoppelbuchstabeUndUnbestimmteGrundregel() {
    var inkrafttreten =
        InkrafttretensLeser.lies(
            """
            (1) Dieses Gesetz tritt vorbehaltlich der Absätze 2 und 3 am Tag nach der Verkündung \
            in Kraft.
            (2) Artikel 4a Nummer 1 tritt mit Wirkung vom
            18. November 2020 in Kraft.
            (3) Artikel 1 Nummer 11 Buchstabe a Doppelbuchstabe bb und Artikel 2 treten am \
            1. April 2021 in Kraft.
            """);

    assertThat(inkrafttreten).isNotNull();
    var grundregel = inkrafttreten.grundregel().orElseThrow();
    assertThat(grundregel.datum()).isNull();
    assertThat(grundregel.anzeige()).isEqualTo(grundregel.wortlaut());
    // Der Zeilenumbruch mitten im Datum („mit Wirkung vom\n18. November 2020“) stört nicht.
    assertThat(inkrafttreten.fuer(punkt("4a", "1.")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2020, 11, 18));
    assertThat(inkrafttreten.fuer(punkt("1", "11. a) bb)")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2021, 4, 1));
    assertThat(inkrafttreten.fuer(punkt("1", "11. a) aa)")).orElseThrow().datum()).isNull();
    assertThat(inkrafttreten.fuer(punkt("2", "6. b)")).orElseThrow().datum())
        .isEqualTo(LocalDate.of(2021, 4, 1));
  }

  /** Das Außerkrafttreten sagt nichts darüber, wann eine Änderung wirkt — es bleibt außer Acht. */
  @Test
  void ausserkrafttretenIstKeineAnordnungDesInkrafttretens() {
    assertThat(InkrafttretensLeser.lies("Diese Verordnung tritt am 31. Dezember 2027 außer Kraft."))
        .isNull();
  }

  /**
   * Ein Gesetzblatt trägt mehrere Gesetze mit je eigener Schlussvorschrift. Maßgeblich ist die, die
   * auf den eigenen ändernden Artikel folgt — nicht die eines fremden Gesetzes davor.
   */
  @Test
  void waehltDenSchlussartikelHinterDemEigenenGesetz() {
    var bloecke =
        java.util.List.of(
            "Das Erste Gesetz wird wie folgt geändert: …",
            "Dieses Gesetz tritt am 1. März 2026 in Kraft.",
            "Das Zweite Gesetz wird wie folgt geändert: …",
            "Dieses Gesetz tritt am 1. Juli 2026 in Kraft.");

    assertThat(InkrafttretensLeser.waehle(bloecke, 1).grundregel().orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(InkrafttretensLeser.waehle(bloecke, 3).grundregel().orElseThrow().datum())
        .isEqualTo(LocalDate.of(2026, 7, 1));
  }
}
