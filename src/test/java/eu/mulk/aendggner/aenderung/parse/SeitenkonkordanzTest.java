// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SeitenkonkordanzTest {

  private static FontgroessenFilter.Zeile zeile(int seite, String text) {
    return new FontgroessenFilter.Zeile(seite, Float.NaN, Float.NaN, Float.NaN, text);
  }

  private static Seitenkonkordanz konkordanz(FontgroessenFilter.Zeile... zeilen) {
    return Seitenkonkordanz.aus(List.of(zeilen));
  }

  @Test
  void findetDenBefehlAufSeinerSeite() {
    var leser =
        konkordanz(
                zeile(1, "1. § 2 Absatz 2 wird durch die folgenden Absätze 2 und 3 ersetzt:"),
                zeile(2, "2. In § 5 Absatz 3 Nummer 1 wird die Angabe „a“ durch „b“ ersetzt."))
            .leser();

    assertThat(leser.seiteVon("§ 2 Absatz 2 wird durch die folgenden Absätze 2 und 3 ersetzt:"))
        .isEqualTo(1);
    assertThat(leser.seiteVon("In § 5 Absatz 3 Nummer 1 wird die Angabe „a“ durch „b“ ersetzt."))
        .isEqualTo(2);
  }

  @Test
  void satzzeichenUndSilbentrennungStoerenNicht() {
    // Der Bereiniger zieht weiche Umbrüche zusammen und heilt Trennstriche; der Wortbestand ist
    // hernach derselbe, das Satzbild nicht. Genau darauf beruht die Suche.
    var leser =
        konkordanz(
                zeile(4, "3. Nach Absatz 5 wird der folgende Ab-"),
                zeile(5, "satz 6 eingefügt: „(6) Eine geschäftliche Handlung ist irreführend.“"))
            .leser();

    assertThat(leser.seiteVon("Nach Absatz 5 wird der folgende Absatz 6 eingefügt:")).isEqualTo(4);
  }

  @Test
  void mehrfacherWortlautTrifftDieFortgeschritteneSeite() {
    // „Absatz 3 wird aufgehoben“ steht in einem Heft dutzendfach. Maßgeblich ist, wie weit die
    // Erschließung gediehen ist — deshalb der fortschreitende Leser.
    var konkordanz =
        konkordanz(
            zeile(1, "a) Der Absatz 3 wird aufgehoben."),
            zeile(7, "b) In § 40 wird ein Wort ersetzt."),
            zeile(9, "c) Der Absatz 3 wird aufgehoben."));

    var leser = konkordanz.leser();
    assertThat(leser.seiteVon("Der Absatz 3 wird aufgehoben.")).isEqualTo(1);
    assertThat(leser.seiteVon("In § 40 wird ein Wort ersetzt.")).isEqualTo(7);
    assertThat(leser.seiteVon("Der Absatz 3 wird aufgehoben.")).isEqualTo(9);
  }

  @Test
  void wasSichNichtWiederfindetBleibtOhneSeite() {
    var leser =
        konkordanz(zeile(1, "Ein Wortlaut, der nichts mit dem Gesuchten zu tun hat.")).leser();

    assertThat(leser.seiteVon("Dieser Befehl steht in keinem Heft.")).isNull();
  }

  @Test
  void zuKurzesIstKeinWortlaut() {
    // „a)“ steht auf jeder zweiten Seite; daraus eine Fundstelle zu machen wäre geraten.
    var leser = konkordanz(zeile(3, "a) und b) und c)")).leser();

    assertThat(leser.seiteVon("a)")).isNull();
  }

  @Test
  void ohneSatzbildKeineSeite() {
    assertThat(Seitenkonkordanz.LEER.leser().seiteVon("Ein beliebiger Befehlstext von Länge."))
        .isNull();
  }
}
