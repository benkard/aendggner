// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EntwurfsPatcherTest {

  /**
   * Ein Entwurf im Kleinen: § 3 ändert eine Verordnung, sein 22. Befehl fasst § 18 neu und zitiert
   * dabei eine Artenliste. Genau in dieses Zitat greift der Antrag hinein.
   */
  private static final String ENTWURF =
      """
      § 2
      Änderung eines anderen Gesetzes
      1. § 4 wird aufgehoben.
      § 3
      Änderung der Verordnung zur Ausführung des Bayerischen Jagdgesetzes
      Die Verordnung wird wie folgt geändert:
      21. § 17 wird aufgehoben.
      22. § 18 wird wie folgt gefasst:
      „§ 18
      Tierarten
      Dem Jagdrecht unterliegen folgende Tierarten:
      1. Haarwild:
      1.28. Mink (Neovison vison), 1.29. Wolf (Canis lupus),
      1.30. Goldschakal (Canis aureus);
      2. Federwild:
      2.1. Rebhuhn (Perdix perdix).“
      23. § 19 wird aufgehoben.
      § 4
      Inkrafttreten
      """;

  private static final String ANTRAG =
      """
      Änderungsantrag
      Der Landtag wolle beschließen:
      In § 3 Nr. 22 wird § 18 Nr. 1 wie folgt geändert:
      1. In Nr. 1.29 die Angabe „ ,“ am Ende durch die Angabe „ ;“ ersetzt.
      2. Nr. 1.30 aufgehoben.
      """;

  @Test
  void aendertGenauDasZitierteAufzaehlungsglied() {
    var befehle = AenderungsantragParser.parse(ANTRAG).befehle();

    var ergebnis = EntwurfsPatcher.wendeAn(ENTWURF, befehle);

    assertThat(ergebnis.warnungen()).isEmpty();
    assertThat(ergebnis.angewandt()).isEqualTo(2);
    // Das Komma nach dem Wolf wird zum Semikolon, weil er nun das letzte Glied ist …
    assertThat(ergebnis.text()).contains("1.29. Wolf (Canis lupus);");
    // … und der Goldschakal verschwindet.
    assertThat(ergebnis.text()).doesNotContain("Goldschakal");
    // Alles andere bleibt unangetastet, insbesondere die Nachbarglieder und die Nachbarbefehle.
    assertThat(ergebnis.text()).contains("1.28. Mink (Neovison vison),");
    assertThat(ergebnis.text()).contains("2. Federwild:");
    assertThat(ergebnis.text()).contains("21. § 17 wird aufgehoben.");
    assertThat(ergebnis.text()).contains("23. § 19 wird aufgehoben.");
  }

  /**
   * Die Aufzählung des Zitats („1. Haarwild:“) sieht aus wie ein Gliederungspunkt des Entwurfs. Der
   * Patcher darf sie nicht dafür halten, sonst endete der Punkt 22 schon vor der Artenliste.
   */
  @Test
  void verwechseltZitatAufzaehlungNichtMitEntwurfsGliederung() {
    var befehle = AenderungsantragParser.parse(ANTRAG).befehle();
    assertThat(EntwurfsPatcher.wendeAn(ENTWURF, befehle).angewandt()).isEqualTo(2);
  }

  @Test
  void meldetEinenBefehlAufEineNichtVorhandeneStelle() {
    var antrag =
        """
        Änderungsantrag
        Der Landtag wolle beschließen:
        In § 3 Nr. 99 wird § 18 Nr. 1 wie folgt geändert:
        1. Nr. 1.30 aufgehoben.
        """;

    var ergebnis = EntwurfsPatcher.wendeAn(ENTWURF, AenderungsantragParser.parse(antrag).befehle());

    assertThat(ergebnis.angewandt()).isZero();
    assertThat(ergebnis.text()).isEqualTo(ENTWURF);
    assertThat(ergebnis.warnungen()).singleElement().asString().contains("§ 3 99");
  }

  /** Ein Befehl auf einen anderen Entwurfsparagraphen darf dessen Nachbarn nicht treffen. */
  @Test
  void bleibtImAngesprochenenParagraphen() {
    var antrag =
        """
        Änderungsantrag
        Der Landtag wolle beschließen:
        In § 2 Nr. 22 wird § 18 Nr. 1 wie folgt geändert:
        1. Nr. 1.30 aufgehoben.
        """;

    var ergebnis = EntwurfsPatcher.wendeAn(ENTWURF, AenderungsantragParser.parse(antrag).befehle());

    assertThat(ergebnis.angewandt()).isZero();
    assertThat(ergebnis.text()).contains("Goldschakal");
  }
}
