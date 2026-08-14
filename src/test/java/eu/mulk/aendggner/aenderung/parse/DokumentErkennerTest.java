package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.mulk.aendggner.aenderung.DokumentArt;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DokumentErkennerTest {

  private static final Path SAMPLEDATA = Path.of("src/main/resources/sampledata");

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
