// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.mulk.aendggner.aenderung.DokumentArt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DokumentErkennerTest {

  private static final Path SAMPLEDATA = Path.of("src/test/resources/sampledata");

  /**
   * Die Erkennung ist eine Heuristik auf Kopfzeilen; sie wird deshalb gegen wirkliche Dokumente
   * festgenagelt, je eines pro Art und Herausgeber. Der Dateiname zählt dabei ausdrücklich nicht —
   * {@code BT-Drs-21-7071_Beschlussempfehlung.pdf} heißt so, ist aber ein Entschließungsantrag.
   */
  @ParameterizedTest
  @CsvSource({
    "BayJG/Ltg-Drs-19-10365_Aenderungsantrag-Gruene.pdf, AENDERUNGSANTRAG",
    "BayJG/Ltg-Drs-19-9707_Gesetzentwurf.pdf,            GESETZENTWURF",
    "BayJG/Plenarprotokoll-19-72_2te-Lesung.pdf,         OHNE_BEFEHLE",
    "BayJG/gvbl-2026-06.pdf,                             ARTIKELGESETZ",
    "GEG/BT-Drs-20-7619_Beschlussempfehlung.pdf,         BESCHLUSSEMPFEHLUNG",
    "GEG/BT-Drs-21-7071_Beschlussempfehlung.pdf,         OHNE_BEFEHLE",
    "GEG/BT-Drs-20-6875_Regierungsentwurf.pdf,           GESETZENTWURF",
    "GEG/Referentenentwurf_GModG_2026-05-05.pdf,         GESETZENTWURF",
    "GEG/bgbl123s0280_regelungstext.pdf,                 ARTIKELGESETZ",
    "IfSG/1924334.pdf,                                   BESCHLUSSEMPFEHLUNG",
    "NRW/GV-NRW-2026-S202_22-Rundfunkaenderungsgesetz.pdf, ARTIKELGESETZ",
    "Sachsen/SaechsGVBl-2026-S134_AendG-SaechsBeamtVG_revosax.pdf, ARTIKELGESETZ",
  })
  void ordnetBeispieldokumenteRichtigEin(String datei, DokumentArt erwartet) throws Exception {
    var pfad = SAMPLEDATA.resolve(datei.strip());
    assumeTrue(Files.exists(pfad), "Beispieldatei fehlt: " + datei);

    var kopf = DokumentErkenner.erkenne(new PatchTextExtraktor().extrahiere(pfad));

    assertThat(kopf.art()).isEqualTo(erwartet);
  }

  /**
   * Das Ausfertigungsdatum aus der Zeile „Vom …“. An ihm erkennt die Fassung ein Heft wieder, das
   * auf sie schon angewandt worden ist; deshalb darf es nicht geraten sein. Ein Sammelheft stellt
   * mehrere Verkündungen nebeneinander (so das thüringische GVBl. Nr. 2/2026 mit vieren), und
   * welche von ihnen das Dokument ausmacht, sagt sein Kopf nicht — dann bleibt das Datum leer.
   */
  @ParameterizedTest
  @CsvSource({
    "UWG/bgbl126s0043_regelungstext.pdf,                          2026-02-12",
    "Brandenburg/GVBl-I-2026-12_FraktG-AendG.pdf,                 2026-04-22",
    "NRW/GV-NRW-2026-S202_22-Rundfunkaenderungsgesetz.pdf,        2026-03-24",
    "Hessen/GVBl-2026-05_11-AendVO-verkehrsrechtl-Zustaendigkeiten.pdf, 2026-01-28",
    "Thueringen/GVBl-TH-2026-02_KiGaFinanzVO-AendVO-ua.pdf,       ",
    "GEG/BT-Drs-20-6875_Regierungsentwurf.pdf,                    ",
  })
  void liestDasAusfertigungsdatum(String datei, String erwartet) throws Exception {
    var pfad = SAMPLEDATA.resolve(datei.strip());
    assumeTrue(Files.exists(pfad), "Beispieldatei fehlt: " + datei);

    var kopf = DokumentErkenner.erkenne(new PatchTextExtraktor().extrahiere(pfad));

    assertThat(kopf.ausfertigung())
        .isEqualTo(erwartet == null ? null : LocalDate.parse(erwartet.strip()));
  }

  /**
   * Woran ein Heft sich nennt: an seiner Drucksachennummer, sonst an seinem Ausfertigungsdatum. Der
   * Dateiname bleibt außen vor — er ist eine Zufälligkeit des Ablageortes, und an der Bezeichnung
   * hängt die Wiedererkennung in der Kette.
   */
  @Test
  void bezeichnetSichNachDemDokumentUndNichtNachDerDatei() throws Exception {
    var gesetzblatt = SAMPLEDATA.resolve("UWG/bgbl126s0043_regelungstext.pdf");
    var drucksache = SAMPLEDATA.resolve("UWG/BT-Drs-21-1855_Regierungsentwurf.pdf");
    assumeTrue(Files.exists(gesetzblatt) && Files.exists(drucksache), "UWG-Beispieldaten fehlen");

    assertThat(
            DokumentErkenner.erkenne(new PatchTextExtraktor().extrahiere(gesetzblatt))
                .anzeigeName())
        .isEqualTo("Änderungsgesetz vom 12. Februar 2026");
    assertThat(
            DokumentErkenner.erkenne(new PatchTextExtraktor().extrahiere(drucksache)).anzeigeName())
        .isEqualTo("Gesetzentwurf Drs. 21/1855");
  }

  /** Die Drucksachennummern stiften die Verbindung zwischen Antrag und Entwurf. */
  @Test
  void liestEigeneUndBezogeneDrucksachennummern() throws Exception {
    var antrag = SAMPLEDATA.resolve("BayJG/Ltg-Drs-19-10365_Aenderungsantrag-Gruene.pdf");
    assumeTrue(Files.exists(antrag), "BayJG-Beispieldaten fehlen");

    var kopf = DokumentErkenner.erkenne(new PatchTextExtraktor().extrahiere(antrag));

    assertThat(kopf.eigeneDrucksache()).isEqualTo("19/10365");
    assertThat(kopf.bezugsDrucksachen()).containsExactly("19/9707");
    assertThat(kopf.titel()).isEqualTo("Goldschakal nicht ins Jagdrecht aufnehmen");
  }

  @Test
  void kennzeichnetEntwurfsfassungen() {
    assertThat(DokumentArt.ARTIKELGESETZ.istEntwurfsfassung()).isFalse();
    assertThat(DokumentArt.GESETZENTWURF.istEntwurfsfassung()).isTrue();
    assertThat(DokumentArt.AENDERUNGSANTRAG.istEntwurfsfassung()).isTrue();
    assertThat(DokumentArt.BESCHLUSSEMPFEHLUNG.istEntwurfsfassung()).isTrue();
  }
}
