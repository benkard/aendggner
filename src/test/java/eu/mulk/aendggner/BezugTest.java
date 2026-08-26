// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Geprüft wird, was ohne Netz zu prüfen ist: die Ableitung der Kennung, die Ableitung des Namens
 * und der Vorrang der vorhandenen Datei. Der Abruf selbst gehört nicht in eine Prüfung, die auf
 * jedem Rechner laufen soll.
 */
class BezugTest {

  /**
   * Die Kennung des Portals: klein geschrieben, und was kein Buchstabe, keine Ziffer und kein
   * Bindestrich ist, wird zum Unterstrich. So schreibt gesetze-im-internet.de seine Anschriften.
   */
  @ParameterizedTest
  @CsvSource({
    "UWG 2004,        uwg_2004",
    "uwg_2004,        uwg_2004",
    "GEG,             geg",
    "1-DM-GoldmünzG,  1-dm-goldm_nzg",
    "'  IfSG  ',      ifsg",
  })
  void leitetDieKennungAb(String angabe, String erwartet) {
    assertThat(Bezug.kennung(angabe)).isEqualTo(erwartet);
  }

  @ParameterizedTest
  @CsvSource({
    "https://dserver.bundestag.de/btd/21/018/2101855.pdf, 2101855.pdf",
    "https://www.gesetze-im-internet.de/uwg_2004/xml.zip, xml.zip",
    "https://example.org/heft.pdf?fassung=2#seite3,      heft.pdf",
  })
  void leitetDenNamenAusDerAnschriftAb(String anschrift, String erwartet) {
    assertThat(Bezug.dateiname(anschrift)).isEqualTo(erwartet);
  }

  /** Die vorhandene Datei hat den Vorrang: Wer eine Datei „gii:etwas“ nennt, meint sie. */
  @Test
  void nimmtDieVorhandeneDateiVorJederDeutung() throws IOException, InterruptedException {
    var datei = Path.of("src/test/resources/sampledata/Brandenburg/FraktG-alt.txt");
    assumeTrue(Files.exists(datei), "Beispieldaten fehlen");

    var quelle = Bezug.hole(datei.toString());

    assertThat(quelle.name()).isEqualTo("FraktG-alt.txt");
    assertThat(quelle.inhalt()).isEqualTo(Files.readAllBytes(datei));
  }
}
