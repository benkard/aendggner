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

  private static final Path STAMMGESETZ =
      Path.of("src/main/resources/sampledata/IfSG/BJNR104510000.xml");
  private static final Path AENDERUNGSGESETZ =
      Path.of("src/main/resources/sampledata/IfSG/bgbl120s2397_78991.pdf");

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
}
