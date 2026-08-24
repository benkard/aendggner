// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

/**
 * Die Auflösung der Zusammenstellung einer Beschlussempfehlung am Beispiel der GEG-Novelle (BT-Drs.
 * 20/7619). Geprüft wird nicht die Befehlszahl — das tut der {@code EndToEndTest} —, sondern jede
 * der vier Entscheidungen, die der Leser zeilenweise trifft.
 */
class ZusammenstellungsLeserTest {

  private static final Path EMPFEHLUNG =
      Path.of("src/test/resources/sampledata/GEG/BT-Drs-20-7619_Beschlussempfehlung.pdf");

  private static String fassung() throws Exception {
    try (var dokument = Loader.loadPDF(EMPFEHLUNG.toFile())) {
      var ergebnis = ZusammenstellungsLeser.lies(dokument, SuperskriptModus.ENTFERNEN);
      assertThat(ergebnis.warnungen()).isEmpty();
      assertThat(ergebnis.text()).isNotNull();
      return TextBereiniger.bereinige(ergebnis.text());
    }
  }

  /** Wo die Ausschussspalte ausgeschrieben ist, gilt sie — nicht der Entwurf daneben. */
  @Test
  void ausgeschriebeneAusschussfassungVerdraengtDenEntwurf() throws Exception {
    assumeTrue(Files.exists(EMPFEHLUNG), "GEG-Beispieldaten fehlen");
    var fassung = fassung();

    assertThat(fassung).contains("13. Die §§ 34 bis 45 werden wie folgt gefasst:");
    assertThat(fassung)
        .as("die Entwurfsfassung desselben Punktes darf nicht danebenstehen")
        .doesNotContain("Die Angaben zu den §§ 34 bis 45");
  }

  /**
   * „Unverändert“ holt den Wortlaut aus der Entwurfsspalte — auch zeilenweise innerhalb eines
   * Zitats, wo die Ausschussspalte für sich gelesen ihre Anführungszeichen nicht schlösse.
   */
  @Test
  void unveraendertHoltDenWortlautAusDerEntwurfsspalte() throws Exception {
    assumeTrue(Files.exists(EMPFEHLUNG), "GEG-Beispieldaten fehlen");
    var fassung = fassung();

    assertThat(fassung).contains("„§ 34 (weggefallen)");
    assertThat(fassung).contains("§ 45 (weggefallen)“");
    assertThat(fassung).doesNotContain("unverändert");
  }

  /**
   * „Unverändert“ meint den Wortlaut, nicht die Zählung: Der Ausschuss streicht die Entwurfsnummer
   * 5, seine eigene Nummer 5 ist die Nummer 6 des Entwurfs — mit dessen Wortlaut.
   */
  @Test
  void umnummerierterPunktBehaeltDenWortlautUndErhaeltDieNeueMarke() throws Exception {
    assumeTrue(Files.exists(EMPFEHLUNG), "GEG-Beispieldaten fehlen");
    var fassung = fassung();

    assertThat(fassung)
        .contains("„5. die Anforderungen an den Einbau von Heizungsanlagen bei Nutzung von fester");
    assertThat(fassung).doesNotContain("6. die Anforderungen an den Einbau von Heizungsanlagen");
  }

  /** Ein gestrichener Punkt hinterlässt nichts — weder den Vermerk noch den Entwurfstext. */
  @Test
  void gestrichenerPunktHinterlaesstNichts() throws Exception {
    assumeTrue(Files.exists(EMPFEHLUNG), "GEG-Beispieldaten fehlen");
    var fassung = fassung();

    assertThat(fassung).doesNotContain("bb) entfällt");
    assertThat(fassung)
        .as("der vom Ausschuss gestrichene Entwurfstext gehört nicht in die beschlossene Fassung")
        .doesNotContain("„1. eine Umwälzpumpe nach § 64");
  }

  /**
   * Der quer am Blattrand stehende Vorabfassungs-Vermerk hat keine Grundlinie im Satzspiegel; er
   * würde die Zeilenfolge zerschneiden und bleibt deshalb draußen.
   */
  @Test
  void gedrehterRandvermerkKommtNichtInDenText() throws Exception {
    assumeTrue(Files.exists(EMPFEHLUNG), "GEG-Beispieldaten fehlen");

    assertThat(fassung()).doesNotContain("lektorierte");
  }
}
