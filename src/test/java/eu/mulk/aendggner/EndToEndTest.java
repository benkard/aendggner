package eu.mulk.aendggner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.gesetz.gii.GiiXmlLoader;
import eu.mulk.aendggner.synopse.HtmlRenderer;
import eu.mulk.aendggner.synopse.SynopseBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Smoke-Test der Gesamtpipeline auf den IfSG-Beispieldaten (nicht eingecheckt; der Test wird
 * übersprungen, wenn sie fehlen).
 *
 * <p>Achtung: Das Beispiel-XML ist bereits konsolidiert (Stand 18.11.2020, enthält die
 * Art.-1-Änderungen des Beispiel-Änderungsgesetzes schon). Die Assertions prüfen deshalb
 * Parse-Zahlen und sauberes Reporting, nicht die Diff-Korrektheit.
 */
class EndToEndTest {

  private static final Path SAMPLEDATA = Path.of("src/main/resources/sampledata");
  private static final Path STAMMGESETZ = SAMPLEDATA.resolve("IfSG/BJNR104510000.xml");
  private static final Path AENDERUNGSGESETZ = SAMPLEDATA.resolve("IfSG/bgbl120s2397_78991.pdf");

  @Test
  void gesamtePipelineAufIfSgBeispiel() throws Exception {
    assumeTrue(Files.exists(STAMMGESETZ), "IfSG-Beispieldaten fehlen");
    assumeTrue(Files.exists(AENDERUNGSGESETZ), "BGBl-Beispiel-PDF fehlt");

    // 1. Stammgesetz laden.
    var gesetz = new GiiXmlLoader().load(STAMMGESETZ);
    assertThat(gesetz.jurabk()).isEqualTo("IfSG");
    assertThat(gesetz.normen()).hasSize(86);
    assertThat(gesetz.norm("§ 5").orElseThrow().absaetze()).hasSize(8);

    // 2. Änderungsgesetz-Text extrahieren.
    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(AENDERUNGSGESETZ));
    assertThat(text).contains("wird wie folgt geändert");
    assertThat(text).contains("Nach § 28 wird folgender § 28a eingefügt");

    // 3. Befehle parsen: Artikel 1 und 2 betreffen das IfSG.
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1", "2");
    assertThat(parseErgebnis.befehle().size()).isGreaterThanOrEqualTo(50);
    var typisiert =
        parseErgebnis.befehle().stream().filter(b -> !(b instanceof UnbekannterBefehl)).count();
    assertThat(typisiert).isGreaterThanOrEqualTo(40);
    // Das Original-BGBl enthält ein überzähliges Anführungszeichen (Artikel 3) — muss als
    // Warnung gemeldet werden, nicht als Abbruch.
    assertThat(parseErgebnis.warnungen()).isNotEmpty();

    // 4. Anwenden: wirft nicht; jeder Befehl erhält einen Protokolleintrag.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isGreaterThan(0);

    // 5. Synopse rendern.
    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    var html = HtmlRenderer.rendere(synopse, "E2E-Test");
    assertThat(html).contains("Alte Fassung").contains("Neue Fassung");
    assertThat(html).contains("Manuell prüfen");
    assertThat(html).containsAnyOf("<del>", "<ins>");
  }

  /** Neues digitales BGBl-Format (recht.bund.de, ab 2023): 3. UWGÄndG 2026. */
  @Test
  void uwgNeuesBgblFormat() throws Exception {
    var xml = SAMPLEDATA.resolve("UWG/BJNR141400004.xml");
    var pdf = SAMPLEDATA.resolve("UWG/bgbl126s0043_regelungstext.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "UWG-Beispieldaten fehlen");

    var gesetz = new GiiXmlLoader().load(xml);
    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));

    // Der Fontgrößen-Filter muss die Fußnotenblöcke entfernt haben, die im neuen Format sonst
    // mitten im Gesetzestext landen.
    assertThat(text).doesNotContain("Dieses Gesetz dient der Umsetzung der Richtlinie (EU)");
    assertThat(text).contains("wird wie folgt geändert");

    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle().size()).isGreaterThanOrEqualTo(10);
    var typisiert =
        parseErgebnis.befehle().stream().filter(b -> !(b instanceof UnbekannterBefehl)).count();
    assertThat(typisiert).isGreaterThan(parseErgebnis.befehle().size() / 2);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    assertThat(HtmlRenderer.rendere(synopse, "E2E-Test")).contains("Neue Fassung");
  }

  /** Stresstest: GEG-Novelle 2023 („Heizungsgesetz“, 26 Seiten, >100 Befehle). */
  @Test
  void gegGrossesAenderungsgesetz() throws Exception {
    var xml = SAMPLEDATA.resolve("GEG/BJNR172810020.xml");
    var pdf = SAMPLEDATA.resolve("GEG/bgbl123s0280_regelungstext.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "GEG-Beispieldaten fehlen");

    var gesetz = new GiiXmlLoader().load(xml);
    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);

    assertThat(parseErgebnis.artikel()).contains("1");
    assertThat(parseErgebnis.befehle().size()).isGreaterThanOrEqualTo(100);

    // Das XML ist bereits konsolidiert; entscheidend ist, dass die Anwendung sauber terminiert
    // und jeden Befehl protokolliert.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    assertThat(HtmlRenderer.rendere(synopse, "E2E-Test")).contains("Manuell prüfen");
  }

  /**
   * Gesetzentwurf (Bundestags-Drucksache mit markerloser Silbentrennung). Das AGG-XML ist NICHT
   * konsolidiert (Stand 2023, Entwurf von 2026) — hier entstehen echte Diffs.
   */
  @Test
  void aggRegierungsentwurfMitEchtenDiffs() throws Exception {
    var xml = SAMPLEDATA.resolve("AGG/BJNR189710006.xml");
    var pdf = SAMPLEDATA.resolve("AGG/BT-Drs-21-6178_Regierungsentwurf.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "AGG-Beispieldaten fehlen");

    var gesetz = new GiiXmlLoader().load(xml);
    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));

    // Markerlose Silbentrennung („Schwel|lenwertes“) muss zusammengezogen sein.
    assertThat(text).contains("Schwellenwertes");

    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    // Der Begründungsteil darf keine Befehle erzeugen.
    assertThat(parseErgebnis.befehle().size()).isBetween(15, 40);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isGreaterThan(10);

    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    var html = HtmlRenderer.rendere(synopse, "E2E-Test");
    assertThat(html).contains("<del>").contains("<ins>");
    // Stichprobe aus Artikel 1 Nummer 1: „Alters“ → „Lebensalters“ in § 1.
    assertThat(html).contains("<ins>Lebensalters</ins>");
  }

  /**
   * Regierungsentwurf, dessen Artikel 1 ein Ablösegesetz ist; nur Artikel 2 ändert das ProdHaftG.
   */
  @Test
  void prodHaftGRegierungsentwurf() throws Exception {
    var xml = SAMPLEDATA.resolve("ProdHaftG/BJNR021980989.xml");
    var pdf = SAMPLEDATA.resolve("ProdHaftG/RegE_Produkthaftungsrecht_2025-12-17.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "ProdHaftG-Beispieldaten fehlen");

    var gesetz = new GiiXmlLoader().load(xml);
    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);

    // Artikel 1 (neues Produkthaftungsgesetz) betrifft das Stammgesetz nicht; Artikel 2 schon.
    assertThat(parseErgebnis.artikel()).containsExactly("2");
    assertThat(parseErgebnis.befehle()).hasSize(1);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(1);
    var neueNorm = anwendung.neu().norm("§ 19").orElseThrow();
    assertThat(neueNorm.titel()).isEqualTo("Außerkrafttreten");
    assertThat(neueNorm.gesamtText()).contains("9. Dezember 2026 außer Kraft");
  }
}
