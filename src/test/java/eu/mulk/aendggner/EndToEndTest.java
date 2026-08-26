// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Sammelbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.VerweisenderBefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.parse.AenderungsantragParser;
import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.EntwurfsPatcher;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.SuperskriptModus;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.aenderung.parse.ZitatExtraktor;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.anwendung.Grund;
import eu.mulk.aendggner.anwendung.Nachfassungsabgleich;
import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.gii.GiiXmlLoader;
import eu.mulk.aendggner.synopse.HtmlRenderer;
import eu.mulk.aendggner.synopse.SynopseBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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

  private static final Path SAMPLEDATA = Path.of("src/test/resources/sampledata");
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
    // Und der Leser erfährt, woran die Reste liegen: Das XML trägt die Änderung dieses
    // Gesetzes bereits als „textlich nachgewiesen“ und ist damit jünger als das Gesetz selbst.
    assertThat(parseErgebnis.warnungen())
        .anyMatch(w -> w.contains("Das Stammgesetz ist jünger als das Änderungsgesetz"))
        // Die Rüge ergeht einmal, obwohl zwei Artikel dasselbe Stammgesetz betreffen.
        .filteredOn(w -> w.contains("ist jünger als"))
        .hasSize(1);

    // 4. Anwenden: wirft nicht; jeder Befehl erhält einen Protokolleintrag. Auch hier ist das XML
    //    jünger als das Änderungsgesetz — die Sollzahlen stehen in ifsgGegenZeitrichtigenStamm().
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

  /**
   * Dasselbe Änderungsgesetz gegen den <em>zeitrichtigen</em> Stamm: das IfSG in der Fassung, die
   * der Einleitungssatz nennt („zuletzt durch Artikel 5 des Gesetzes vom 19. Juni 2020 geändert“).
   *
   * <p>Der Fall daneben ({@link #gesamtePipelineAufIfSgBeispiel}) rechnet gegen die heutige
   * konsolidierte Fassung und kann deshalb keine Sollzahl pinnen — dort blieben 27 von 75 Befehlen
   * liegen, weil ihr Zieltext im Stamm längst anders lautet. Gegen den Stand vom 22. Juli 2020 geht
   * die Rechnung vollständig auf: <b>75 von 75</b>, kein Rest. Das ist zugleich der Beleg, dass die
   * 27 nie ein Mangel des Werkzeugs waren.
   */
  /**
   * Der Notausgang (§ 6 Absatz 2 des Handbuchs) gibt aus, was das Erzeugnis gelesen hat, und geht
   * dabei denselben Weg wie die Synopse — auf ihn verlässt sich, wer einem unerklärlichen Rest
   * nachgeht, und seit Welle 21 tut das auch die Browserfassung.
   */
  @Test
  void notausgangLiefertDenGelesenenText() throws Exception {
    assumeTrue(Files.exists(STAMMGESETZ), "IfSG-Beispieldaten fehlen");
    assumeTrue(Files.exists(AENDERUNGSGESETZ), "BGBl-Beispiel-PDF fehlt");

    var stamm = Quelle.lies(STAMMGESETZ);
    var patches = List.of(Quelle.lies(AENDERUNGSGESETZ));

    var bereinigt = Pipeline.extrahiereText(stamm, patches, false);
    var roh = Pipeline.extrahiereText(stamm, patches, true);

    // Wortgleich mit dem, was der Parser bekommt — sonst führte der Notausgang in die Irre.
    assertThat(bereinigt)
        .isEqualTo(
            TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(AENDERUNGSGESETZ)) + "\n");
    assertThat(bereinigt).contains("Nach § 28 wird folgender § 28a eingefügt");
    // Die Bereinigung nimmt fort, was der Satz beisteuert (Kolumnentitel, Trennstriche); der
    // Rohtext ist deshalb länger und trägt den Kopf des Gesetzblatts noch.
    assertThat(roh).hasSizeGreaterThan(bereinigt.length());
    assertThat(roh).contains("Bundesgesetzblatt");
    assertThat(bereinigt).doesNotContain("Bundesgesetzblatt Jahrgang 2020 Teil I Nr.");
  }

  @Test
  void ifsgGegenZeitrichtigenStamm() throws Exception {
    var xml = SAMPLEDATA.resolve("IfSG/BJNR104510000-2020.xml");
    assumeTrue(Files.exists(xml) && Files.exists(AENDERUNGSGESETZ), "IfSG-Beispieldaten fehlen");

    var gesetz = new GiiXmlLoader().load(xml);
    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(AENDERUNGSGESETZ));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);

    assertThat(parseErgebnis.artikel()).containsExactly("1", "2");
    assertThat(parseErgebnis.befehle()).hasSize(75);
    // Gegen den zeitrichtigen Stamm ergeht keine Altersrüge; gegen den heutigen schon (siehe
    // gesamtePipelineAufIfSgBeispiel).
    assertThat(parseErgebnis.warnungen()).noneMatch(w -> w.contains("ist jünger als"));

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    assertThat(manuellPfade).isEmpty();
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(75);

    // Der neu eingefügte § 28a steht an seiner Stelle und trägt seine Überschrift.
    var neu = anwendung.neu();
    assertThat(neu.norm("§ 28a")).isPresent();
    assertThat(neu.norm("§ 28a").orElseThrow().titel())
        .contains("Besondere Schutzmaßnahmen zur Verhinderung der Verbreitung");
    // „In Absatz 6 wird jeweils das Wort ‚schwerwiegende‘ durch ‚bedrohliche‘ … ersetzt“ — die
    // Ersetzung greift nur ganze Wörter, sonst verbrauchte das erste Wort das zweite mit.
    assertThat(neu.norm("§ 36").orElseThrow().gesamtText())
        .doesNotContain("schwerwiegend")
        .contains("bedrohlicher übertragbarer Krankheiten");
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

    // Regression: Stichwort/Definition-Umbruch im Anhang darf nicht ohne Leerzeichen verklebt
    // werden (kurze Stichwort-Zeile vor hängend eingerückter Definition, kein Wort wird getrennt).
    assertThat(text).contains("Nachhaltigkeitssiegels\ndas Anbringen");
    assertThat(text).doesNotContain("Nachhaltigkeitssiegelsdas");
    // Dank geometrischer Umbruch-Klassifikation gilt das auch für Stichwort-Zeilen, die fast die
    // volle Spaltenbreite erreichen (Nummer 4c) …
    assertThat(text).contains("Treibhausgasemissionen\ndas Treffen");
    assertThat(text).doesNotContain("Treibhausgasemissionendas");
    // … und für markerlose Zeilenenden vor einer Aufzählungs-Folgezeile („und“ + „d)“).
    assertThat(text).contains("vorgesehen und\nd) die Überwachung");
    assertThat(text).doesNotContain("undd)");

    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle().size()).isGreaterThanOrEqualTo(10);
    var typisiert =
        parseErgebnis.befehle().stream().filter(b -> !(b instanceof UnbekannterBefehl)).count();
    assertThat(typisiert).isEqualTo(parseErgebnis.befehle().size());

    // Referenzfall des Ausbaus: alle Befehle (auch die Anhang-Änderungen) werden angewandt.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlManuell()).isZero();
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(parseErgebnis.befehle().size());

    // Kurzüberschrift und Definitionstext stehen in der angewandten Fassung auf eigenen Zeilen —
    // sowohl bei XML-stämmigen (Nummer 22) als auch bei PDF-stämmigen (Nummer 2a) Anhang-Nummern.
    var anhangNeu = anwendung.neu().norm("Anhang").orElseThrow();
    assertThat(anhangNeu.gesamtText())
        .contains("Irreführung über Unternehmereigenschaft\n    die unwahre Angabe")
        .contains("unzulässiges Anbringen eines Nachhaltigkeitssiegels\n    das Anbringen");

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

    // Im BGBl-Format werden inzwischen alle Befehle typisiert (Bereichs-/Struktur-Ersetzungen,
    // §-Blöcke, Chapeau-Lokatoren, strukturelle Streichungen usw.).
    var unbekannt =
        parseErgebnis.befehle().stream().filter(b -> b instanceof UnbekannterBefehl).count();
    assertThat(unbekannt).isZero();

    // Das XML ist die heutige konsolidierte Fassung und damit jünger als das Änderungsgesetz;
    // deshalb wird hier keine Sollzahl gepinnt, sondern nur, dass die Anwendung sauber terminiert
    // und jeden Befehl protokolliert. Die Sollzahlen stehen in gegGegenZeitrichtigenStamm().
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
    // Gliederungs-Überschriften (Teil/Abschnitt) werden als Befehle erkannt und angewandt.
    assertThat(anwendung.anzahlAngewandt()).isGreaterThanOrEqualTo(50);
    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    assertThat(HtmlRenderer.rendere(synopse, "E2E-Test")).contains("Manuell prüfen");
  }

  /**
   * Dasselbe Änderungsgesetz gegen den <em>zeitrichtigen</em> Stamm: das GEG in der Fassung, die
   * der Einleitungssatz nennt („durch Artikel 18a des Gesetzes vom 20. Juli 2022 geändert“).
   *
   * <p>Gegen die heutige konsolidierte Fassung ({@link #gegGrossesAenderungsgesetz}) blieben 51 von
   * 119 Befehlen liegen, und der Test konnte deshalb keine Sollzahl pinnen. Gegen den Stand vom 24.
   * April 2023 sind es <b>117 angewandte und zwei Reste</b>, und diese zwei sind echte Befunde:
   *
   * <p>Der dritte Rest ist keiner mehr: {@code 40. a) ff)} („Die bisherige Nummer 9 wird
   * aufgehoben.“) scheiterte daran, dass Punkt bb) derselben Kaskade aus den bisherigen Nummern 4
   * bis 6 die Nummern 8 bis 10 macht — vorübergehend trugen zwei Einheiten die Bezeichnung 9. Seit
   * auch eine Aufhebung als Räumende zählt, läuft sie vor der Umnummerierung, und § 108 Absatz 1
   * trägt danach die lückenlose Folge der Nummern 1 bis 32.
   *
   * <p>Auch {@code 43. b) aa)} ist keiner mehr („In der Überschrift werden die Wörter … in den
   * Fällen des § 69 und § 71 Absatz 1 … gestrichen.“). Ziel ist die Überschrift der <em>Nummer 1
   * der Anlage 8</em>, und es brauchte dafür keinen eigenen Rang zwischen Norm und Absatz: Im
   * gii-XML ist die Überschrift einer Anlagen-Nummer die Kopfzeile ihres Aufzählungsblocks. Der
   * Fehler saß darin, dass der Überschrift-Zweig die feinere Angabe schweigend verwarf und stets
   * auf den Normtitel griff.
   *
   * <p>Ein Rest bleibt: {@code 40. a) hh)} „Die bisherige Nummer 18 wird Nummer 29 und nach der
   * Angabe ‚Absatz 1‘ werden die Wörter ‚oder Absatz 4‘ eingefügt.“ — Die Begleitklausel löst
   * norm-weit auf (bewusst so, siehe {@code BefehlErkenner}) und trifft dort 21 Vorkommen. Gemeint
   * ist die soeben umnummerierte Einheit.
   */
  @Test
  void gegGegenZeitrichtigenStamm() throws Exception {
    var xml = SAMPLEDATA.resolve("GEG/BJNR172810020-2023.xml");
    var pdf = SAMPLEDATA.resolve("GEG/bgbl123s0280_regelungstext.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "GEG-Beispieldaten fehlen");

    var gesetz = new GiiXmlLoader().load(xml);
    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, "1");

    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).hasSize(119);
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    assertThat(manuellPfade).isEmpty();
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(119);

    // Die Kaskade des § 108 Absatz 1 geht lückenlos auf: 32 Nummern, keine doppelt, keine fehlend.
    var nummern =
        anwendung
            .neu()
            .norm("§ 108")
            .orElseThrow()
            .absaetze()
            .get(0)
            .text()
            .lines()
            .map(z -> z.strip().split("\\.")[0])
            .filter(z -> z.matches("\\d+"))
            .toList();
    assertThat(nummern).hasSize(32).endsWith("32");

    var neu = anwendung.neu();
    // Die Begleitklausel der Nummer 18/29 hat allein dort gegriffen — der Wortlaut gleicht der
    // amtlichen Nachfassung (BJNR172810020.xml, § 108 Absatz 1 Nummer 29).
    var paragraph108 = neu.norm("§ 108").orElseThrow().absaetze().get(0).text();
    assertThat(paragraph108)
        .contains("29. entgegen § 96 Absatz 1 oder Absatz 4 eine Bestätigung nicht")
        .contains("30. entgegen § 96 Absatz 5 Satz 2 eine Abrechnung nicht");

    // Die Überschrift der Nummer 1 der Anlage 8 ist geändert — und nur sie: Der Titel der Anlage
    // trägt seinen eigenen Wortlaut unversehrt, den Punkt 43. a) ihm gegeben hat.
    var anlage8 = neu.norm("Anlage 8").orElseThrow();
    assertThat(anlage8.gesamtText())
        .contains("1. Wärmedämmung von Wärmeverteilungs- und Warmwasserleitungen sowie Armaturen")
        .doesNotContain("sowie Armaturen in den Fällen des § 69 und § 71 Absatz 1");
    assertThat(anlage8.titel())
        .contains("Anforderungen an die Wärmedämmung von Rohrleitungen und Armaturen");

    // Der neue § 9a steht im Gesetz und in der Inhaltsübersicht.
    assertThat(neu.norm("§ 9a")).isPresent();
    assertThat(neu.norm("Inhaltsübersicht").orElseThrow().gesamtText())
        .contains("§ 9a")
        // „Die Angabe zur Überschrift von Teil 2 Abschnitt 4 wird gestrichen“ — sie ist fort.
        .doesNotContain("Abschnitt 4 | Nutzung");
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
    // Inkl. der Mehrfachziel-„jeweils“-Befehle (§ 3, § 20, § 30) werden hier ~22 Befehle
    // angewandt; nur die zwei Sonderfälle (positionaler Lokator, Verbundbefehl) bleiben manuell.
    assertThat(anwendung.anzahlAngewandt()).isGreaterThanOrEqualTo(20);
    assertThat(anwendung.anzahlManuell()).isLessThanOrEqualTo(3);

    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    var html = HtmlRenderer.rendere(synopse, "E2E-Test");
    assertThat(html).contains("<del>").contains("<ins>");
    // Stichprobe aus Artikel 1 Nummer 1: „Alters“ → „Lebensalters“ in § 1.
    assertThat(html).contains("<ins>Lebensalters</ins>");
    // Mehrfachziel-Einfügung (§ 30 Absatz 2 Satz 1 und Absatz 3): „Bildung,“ vor „Familie“.
    assertThat(html).contains("<ins>Bildung,");
    // Mehrfachziel-Streichung (§ 3 Absatz 1 Satz 2 und Absatz 4): gezielt entfernt — der
    // untargetierte Absatz 5 behält die Angabe.
    var norm3 = anwendung.neu().norm("§ 3").orElseThrow();
    var absatz1 =
        norm3.absaetze().stream().filter(a -> "1".equals(a.nummer())).findFirst().orElseThrow();
    assertThat(absatz1.text()).doesNotContain("in Bezug auf § 2 Abs. 1 Nr. 1 bis 4");
    var absatz5 =
        norm3.absaetze().stream().filter(a -> "5".equals(a.nummer())).findFirst().orElseThrow();
    assertThat(absatz5.text()).contains("in Bezug auf § 2 Abs. 1 Nr. 1 bis 4");
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

  /**
   * Bayerisches Landesrecht (Akzeptanzfall der fünften Welle): altes BayJG (Klartext, aus der
   * archivierten Fassung von gesetze-bayern.de abgeleitet) + GVBl-Heft 6/2026. §§ 1 und 2 des
   * Änderungsgesetzes betreffen das BayJG; alle Befehle werden erkannt und angewandt.
   */
  @Test
  void bayJgGvblAcceptance() throws Exception {
    var alt = SAMPLEDATA.resolve("BayJG/BayJG-alt.txt");
    var pdf = SAMPLEDATA.resolve("BayJG/gvbl-2026-06.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "BayJG-Beispieldaten fehlen");

    // 1. Stammgesetz laden (Art.-gegliedert, amtliche Satznummern als Superskripte).
    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(gesetz.jurabk()).isEqualTo("BayJG");
    assertThat(gesetz.langue()).isEqualTo("Bayerisches Jagdgesetz");
    assertThat(gesetz.norm("Art. 1").orElseThrow().absaetze().get(0).text())
        .startsWith("¹Die freilebende Tierwelt");

    // 2. GVBl-Heft extrahieren (Superskript-Erhalt; Kolumnentitel entfernt).
    var text =
        TextBereiniger.bereinige(
            new PatchTextExtraktor(eu.mulk.aendggner.aenderung.parse.SuperskriptModus.BEHALTEN)
                .extrahiere(pdf));
    assertThat(text).doesNotContain("Gesetz- und Verordnungsblatt Nr. 6/2026 ");
    assertThat(text).contains("für die kurzfristige Vermietung");

    // 3. Parsen: §§ 1 und 2 betreffen das BayJG (nicht aber die AVBayJG-§§ 4 und 5,
    //    deren Titel das BayJG nur als Genitiv-Attribut nennt); alles wird erkannt.
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1", "2");
    assertThat(parseErgebnis.befehle()).hasSize(154);
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // 4. Anwenden auf die alte Fassung. Alle 154 Befehle greifen; kein Rest bleibt manuell.
    //    Das Heft trägt zwei mehrschrittige Umnummerierungssequenzen, an denen sich die
    //    Schritt-Ordnung des BefehlAnwenders bewährt:
    //      * Art. 29a (§ 1 Nr. 23): „Der bisherige Abs. 4 wird Abs. 5“ läuft vor der Bereichs-
    //        Umnummerierung „Die bisherigen Abs. 1 bis 3 werden die Abs. 2 bis 4“, sodass die auf
    //        den neuen Abs. 5 zielenden Wort- und Satzbefehle ihren Alttext finden.
    //      * Art. 56 (§ 1 Nr. 48): die Neunummerierung des Bußgeldkatalogs verschränkt Einfügungen
    //        und Umnummerierungen über zehn Unterpunkte. Sie geht auf, weil die Umnummerierungen
    //        vorrücken, ihre Begleitänderungen aber an ihrer Dokumentstelle bleiben — die Wortfolge
    //        „schriftliche“ etwa steht erst nach dem vorangehenden Punkt nur noch einmal im
    //        Artikel und ist damit eindeutig.
    //    „Kein Rest“ heißt zunächst nur: jeder Befehl hat gegriffen. Dass Art. 56 Abs. 1 darüber
    //    hinaus in Aufbau und Wortlaut der amtlichen Nachfassung gleicht, prüfen die
    //    Zusicherungen weiter unten.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    assertThat(manuellPfade).isEmpty();

    // Der Bußgeldkatalog des Art. 56 trägt jetzt an den drei Stellen, an denen die Kaskade zuvor
    // gescheitert war, den Wortlaut der amtlichen Nachfassung (BayJG.pdf):
    var art56 = anwendung.neu().norm("Art. 56").orElseThrow();
    var katalog = art56.absaetze().get(0).text();
    assertThat(katalog)
        // aus „Die bisherige Nr. 6 wird Nr. 9 und in Buchst. b …“ — die Begleitklausel findet ihre
        // Stelle erst, seit die lokative Kurzform („in Buchst. b“) erkannt wird.
        .contains("9. vorsätzlich oder fahrlässig entgegen Art. 32 Abs. 2 Satz 1, Abs. 4 oder 5")
        .contains("b) die Abschussmeldung oder die Streckenliste")
        // aus „Die bisherige Nr. 11 wird Nr. 12 und die Angabe „schriftliche“ wird gestrichen“ —
        // eindeutig nur, weil die Streichung an ihrer Dokumentstelle bleibt.
        .contains("12. ohne Begleitung oder Erlaubnis des Revierinhabers")
        .doesNotContain("schriftliche");
    // Abs. 2 Nr. 12 Buchst. b führt ihre Marke allein auf der Zeile — sie ist trotzdem auflösbar.
    assertThat(art56.absaetze().get(1).text()).contains("(§ 2 Abs. 3 BJagdG)");

    // Der Block aus „Nach Nr. 4 werden die folgenden Nrn. 5 bis 7 eingefügt“ steht an seinem Platz
    // — zwischen der Nr. 4 und der Nr. 8 — und nicht mehr am Ende des Absatzes. Dass er dort
    // landete, lag an der Neufassung der Nr. 4 einen Punkt zuvor: Sie setzte ihren Wortlaut ohne
    // Einrückung, worauf der Zeilenblock der Nr. 4 bis ans Absatzende reichte (er reicht so weit,
    // wie tiefer eingerückt wird).
    assertThat(katalog.indexOf("  5. den Verboten des Art. 29 Abs. 2"))
        .as("die eingefügten Nrn. 5 bis 7 stehen zwischen der Nr. 4 und der Nr. 8")
        .isGreaterThan(katalog.indexOf("  4. entgegen Art. 29 Abs. 1"))
        .isLessThan(katalog.indexOf("  8. entgegen Art. 31 Abs. 2"));
    // Der leere Platzhalter „7. (aufgehoben)“ der Altfassung weicht dem eingefügten Block, weil
    // dieser dieselbe Bezeichnung vergibt — dieselbe Regel wie bei der Umnummerierung auf eine
    // weggefallene Bezeichnung. Verdrängt wird dabei nur ein Platzhalter, nie ein Wortlaut: Der
    // Platzhalter „1. (aufgehoben)“ des Abs. 2, den kein Befehl anrührt, bleibt unberührt stehen.
    assertThat(katalog).doesNotContain("(aufgehoben)");
    assertThat(art56.absaetze().get(1).text()).contains("1. (aufgehoben)");
    // Die Einrückung der Aufzählungszeilen übersteht die Streichungen des Hefts: Sie wird nicht
    // mehr von der Leerzeichen-Heilung mitgenommen, die nur noch die Naht der Streichung glättet.
    assertThat(katalog).contains("\n  12. ohne Begleitung").doesNotContain("\n 12. ");

    // Art. 29a Abs. 5 trägt nach der Kaskade die neue Behördenbezeichnung und den eingefügten Satz.
    var art29a = anwendung.neu().norm("Art. 29a").orElseThrow();
    assertThat(art29a.absaetze()).hasSize(5);
    assertThat(art29a.absaetze().get(4).text())
        .startsWith("¹Die oberste Jagdbehörde wird ermächtigt")
        .contains("²Die oberste Jagdbehörde kann zudem durch Rechtsverordnung");

    // 5. Stichproben: § 2 (in Kraft 1.1.2027) hebt Art. 28 Abs. 1 Satz 4 auf und nummeriert
    //    Satz 5 um; § 1 Nr. 16 stellt Art. 22a die Abs. 1 bis 4 voran.
    var art28 = anwendung.neu().norm("Art. 28").orElseThrow();
    var abs1 = art28.absaetze().get(0);
    assertThat(abs1.text()).doesNotContain("⁵");
    var art22a = anwendung.neu().norm("Art. 22a").orElseThrow();
    assertThat(art22a.absaetze().size()).isGreaterThanOrEqualTo(5);
    assertThat(art22a.absaetze().get(0).nummer()).isEqualTo("1");

    // 6. Synopse rendern.
    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    var html = HtmlRenderer.rendere(synopse, "E2E-Test BayJG");
    assertThat(html).contains("Art. 29");
  }

  /** Landtags-Gesetzentwurf (Ltg-Drs. 19/9707) auf derselben alten Fassung. */
  @Test
  void bayJgLandtagsEntwurf() throws Exception {
    var alt = SAMPLEDATA.resolve("BayJG/BayJG-alt.txt");
    var pdf = SAMPLEDATA.resolve("BayJG/Ltg-Drs-19-9707_Gesetzentwurf.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "BayJG-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    var text =
        TextBereiniger.bereinige(
            new PatchTextExtraktor(eu.mulk.aendggner.aenderung.parse.SuperskriptModus.BEHALTEN)
                .extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);

    // Nur § 1 des Entwurfs ändert das BayJG; Vorblatt und Begründung erzeugen keine Befehle.
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).hasSize(154);
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
  }

  /**
   * Sächsisches Landesrecht (Akzeptanzfall der sechsten Welle): das paragraphengegliederte
   * SächsBeamtVG in der Fassung vom 1. Februar 2025 (Klartext, aus der archivierten revosax-
   * Volltextfassung abgeleitet) + das Gesetz zur Änderung des SächsBeamtVG vom 13. April 2026
   * (SächsGVBl. S. 134). Anders als Bayern gliedert Sachsen in §; alle fünf Befehle werden erkannt
   * und angewandt.
   */
  @Test
  void saechsBeamtVgAcceptance() throws Exception {
    var alt = SAMPLEDATA.resolve("Sachsen/SaechsBeamtVG-alt.txt");
    var pdf = SAMPLEDATA.resolve("Sachsen/SaechsGVBl-2026-S134_AendG-SaechsBeamtVG_revosax.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "SächsBeamtVG-Beispieldaten fehlen");

    // 1. Stammgesetz laden (§-gegliedert, amtliche Satznummern als Superskripte, arabische
    //    „Abschnitt N“-Gliederung).
    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(gesetz.jurabk()).isEqualTo("SächsBeamtVG");
    assertThat(gesetz.langue()).isEqualTo("Sächsisches Beamtenversorgungsgesetz");
    assertThat(gesetz.norm("§ 47").orElseThrow().absaetze()).hasSize(6);
    assertThat(gesetz.norm("§ 47").orElseThrow().absaetze().get(0).text()).contains("80 000 Euro");
    assertThat(gesetz.norm("§ 80").orElseThrow().absaetze()).hasSize(4);

    // 2. Änderungsgesetz extrahieren (Superskript-Erhalt; revosax-Fußzeile entfernt).
    var text =
        TextBereiniger.bereinige(
            new PatchTextExtraktor(eu.mulk.aendggner.aenderung.parse.SuperskriptModus.BEHALTEN)
                .extrahiere(pdf));
    assertThat(text).doesNotContain("Fassung vom 30.04.2026");

    // 3. Parsen: nur Artikel 1 ändert das SächsBeamtVG (Artikel 2 ist Inkrafttreten); fünf Befehle,
    //    keiner unbekannt.
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).hasSize(5);
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // 4. Anwenden: alle fünf Befehle greifen, kein Residuum.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll())
        .hasSameSizeAs(parseErgebnis.befehle())
        .allMatch(a -> a.status() == BefehlAnwender.Status.ANGEWANDT);

    // 5. Stichproben auf der neuen Fassung.
    var neu = anwendung.neu();
    var neuer47 = neu.norm("§ 47").orElseThrow();
    assertThat(neuer47.absaetze().get(0).text()).contains("150 000 Euro").doesNotContain("80 000");
    var abs2 = neuer47.absaetze().get(1).text();
    assertThat(abs2).contains("100 000 Euro").contains("40 000 Euro").contains("20 000 Euro");
    assertThat(neu.norm("§ 80").orElseThrow().absaetze().get(0).text())
        .contains("§ 47 Absatz 1 und 2");

    // 6. Synopse rendern.
    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    var html = HtmlRenderer.rendere(synopse, "E2E-Test SächsBeamtVG");
    assertThat(html).contains("§ 47");
  }

  /**
   * Niedersächsisches Landesrecht (sechste Welle): das Niedersächsische ELER-Fördergesetz (NEFG,
   * seit 2022 unverändert, Klartext aus dem zweispaltigen Nds. GVBl 2022 Nr. 33 abgeleitet) + das
   * Gesetz zur Änderung des NEFG vom 28. Januar 2026 (Nds. GVBl 2026 Nr. 10). Prüft den
   * §-gegliederten Superskript-Modus und die Neufassungsform „erhält folgende Fassung“ auf einem
   * zweispaltig gesetzten Ausgangsheft.
   */
  @Test
  void nefgAcceptance() throws Exception {
    var alt = SAMPLEDATA.resolve("Niedersachsen/NEFG-alt.txt");
    var pdf = SAMPLEDATA.resolve("Niedersachsen/Nds-GVBl-2026-10_ELER-Foerdergesetz-AendG.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "NEFG-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(gesetz.jurabk()).isEqualTo("NEFG");
    assertThat(gesetz.langue()).isEqualTo("Niedersächsisches ELER-Fördergesetz");
    assertThat(gesetz.normen()).hasSize(13);
    assertThat(gesetz.norm("§ 1").orElseThrow().absaetze()).hasSize(5);

    var text =
        TextBereiniger.bereinige(
            new PatchTextExtraktor(eu.mulk.aendggner.aenderung.parse.SuperskriptModus.BEHALTEN)
                .extrahiere(pdf));
    // Die laufende GVBl-Fußzeile ist entfernt — sonst hängte sie sich an das Neufassungs-Zitat des
    // § 1 Abs. 1 und der Befehl würde nicht erkannt.
    assertThat(text).doesNotContain("Nds. GVBl. 2026 Nr. 10");

    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).hasSize(7);

    // Alle sieben Befehle werden angewandt, darunter die beiden niedersächsischen Einfügeformen:
    //   * Nr. 3 „Nach § 2 wird der folgende § 2 a eingefügt“ — Sachnummer mit Leerzeichen.
    //   * Nr. 5 „In Kapitel 4 wird nach § 12 der folgende neue § 13 angefügt“ — gliederungs-
    //     bezogene Einfügung, deren Zielbezeichnung erst der nachfolgende Befehl Nr. 6 freimacht.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(7);
    assertThat(anwendung.protokoll())
        .noneMatch(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN);
    // Protokolliert wird trotz vorgezogener Umnummerierung in der Reihenfolge des Gesetzes.
    assertThat(anwendung.protokoll())
        .extracting(a -> a.befehl().provenienz().gliederungsPfad())
        .containsExactly("1. a)", "1. b)", "2.", "3.", "4.", "5.", "6.");

    // Stichproben: „erhält folgende Fassung“ (§ 1 Abs. 1, § 2), Angaben-Ersetzung (§ 1 Abs. 5),
    // Wörter-Einfügung (§ 6 Abs. 1) und §-Umnummerierung (§ 13 → § 14).
    var neu = anwendung.neu();
    assertThat(neu.norm("§ 1").orElseThrow().absaetze().get(0).text())
        .contains("Verordnung (EU) 2021/2116");
    assertThat(neu.norm("§ 1").orElseThrow().absaetze().get(4).text()).contains("§ 14 Abs. 3");
    assertThat(neu.norm("§ 2").orElseThrow().titel()).isEqualTo("Registriernummer");
    assertThat(neu.norm("§ 6").orElseThrow().gesamtText()).contains("8 bis 10");

    // Die beiden neuen Paragraphen stehen an der richtigen Stelle, der bisherige § 13 ist § 14.
    assertThat(neu.normen()).hasSize(15);
    assertThat(neu.normen())
        .extracting(n -> n.enbez())
        .containsSubsequence("§ 2", "§ 2a", "§ 3")
        .containsSubsequence("§ 12", "§ 13", "§ 14");
    assertThat(neu.norm("§ 2a").orElseThrow().titel())
        .isEqualTo("Anwendung bundesrechtlicher Vorschriften");
    assertThat(neu.norm("§ 13").orElseThrow().titel())
        .isEqualTo("Entbehrlichkeit von Vergabeverfahren im Unterschwellenbereich");
    assertThat(neu.norm("§ 14").orElseThrow().titel()).isEqualTo("Verordnungsermächtigungen");

    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    assertThat(HtmlRenderer.rendere(synopse, "E2E-Test NEFG")).contains("§ 1");
  }

  /**
   * Schleswig-Holstein: das Kommunalrecht-Änderungsgesetz (GVOBl. Schl.-H. 2026/27). Voller
   * Akzeptanzfall für beide geänderten Gesetze: Artikel 1 fasst § 34a Abs. 1 der Gemeindeordnung
   * neu, Artikel 2 den gleichlautenden § 29a Abs. 1 der Kreisordnung; Artikel 3 ändert ein anderes
   * Änderungsgesetz und wird zu Recht nicht gewählt.
   *
   * <p>Beide Stammfassungen sind über eine Browsersteuerung aus dem Landesrechtsportal beschafft,
   * das Skriptabfragen nur seine Anwendungshülle ausgibt; Herkunft und Aufbereitung stehen in
   * {@code SchleswigHolstein/SOURCES}. Der Fall trägt zwei Eigenheiten des Portalsatzes: die
   * abgetrennte Sachnummer („§ 57 c“) und den Bindestrich statt des Gedankenstrichs im
   * Klammerzusatz „(Gemeindeordnung - GO -)“.
   */
  @Test
  void kommunalrechtAendGSchleswigHolstein() throws Exception {
    var pdf = SAMPLEDATA.resolve("SchleswigHolstein/GVOBl-2026-27_Kommunalrecht-AendG.pdf");
    assumeTrue(Files.exists(pdf), "GVOBl-Schl.-H.-Beispiel-PDF fehlt");

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));

    // Laufender Seitenkopf entfernt — er stand zwischen Artikel 1 und Artikel 2 und klebte sonst
    // an der Artikel-2-Überschrift.
    assertThat(text).doesNotContain("Gesetz- und Verordnungsblatt für Schleswig-Holstein");
    assertThat(text).doesNotContain("2026/27 vom 30. März");
    // Hochgestellte Fußnotenmarker der Artikel-Überschriften („Artikel 1 ¹)“) sind abgestreift.
    assertThat(text).contains("\nArtikel 1\n").contains("\nArtikel 2\n").contains("\nArtikel 3\n");
    assertThat(text).doesNotContain("¹)").doesNotContain("²)").doesNotContain("³)");
    // Silbentrennung im Flattersatz zusammengezogen (dort findet die geometrische Randerkennung
    // keinen Ausrichtungs-Cluster und meldet auch volle Zeilen als hartes Zeilenende).
    assertThat(text).contains("Ton-Bild-Übertragung zu ermöglichen").doesNotContain("Übertra-");

    // Artikel 1 ändert die Gemeindeordnung, Artikel 2 die Kreisordnung, Artikel 3 ein anderes
    // Änderungsgesetz — nur der jeweils zutreffende Artikel wird gewählt.
    var altGo = SAMPLEDATA.resolve("SchleswigHolstein/GO-SH-alt.txt");
    var altKro = SAMPLEDATA.resolve("SchleswigHolstein/KrO-SH-alt.txt");
    assumeTrue(Files.exists(altGo) && Files.exists(altKro), "Stammfassungen Schl.-H. fehlen");
    var lader = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader();
    var gemeindeordnung = lader.load(altGo);
    var kreisordnung = lader.load(altKro);

    // Kurztitel und Abkürzung sind auch dann getrennt, wenn der Klammerzusatz statt des
    // Gedankenstrichs einen Bindestrich führt, wie ihn das Portal setzt.
    assertThat(gemeindeordnung.jurabk()).isEqualTo("GO");
    assertThat(gemeindeordnung.kurzue()).isEqualTo("Gemeindeordnung");
    // 167 Normen: Die abgetrennte Sachnummer des Portalsatzes („§ 57 c“) ist bei der Aufbereitung
    // zusammengezogen — sonst fehlten zehn Paragraphen. Ein Satz, der mit einem Querverweis
    // beginnt („§ 102 mit Ausnahme des Absatzes 2 …“), eröffnet dagegen keine Norm.
    assertThat(gemeindeordnung.normen()).hasSize(167);
    assertThat(gemeindeordnung.norm("§ 57c")).isPresent();
    assertThat(gemeindeordnung.norm("§ 135a")).isPresent();
    assertThat(kreisordnung.normen()).hasSize(86);

    var go = new AenderungsgesetzParser().parse(text, gemeindeordnung, null);
    assertThat(go.artikel()).containsExactly("1");
    assertThat(go.befehle()).hasSize(1);
    assertThat(go.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);
    var neufassung = go.befehle().get(0);
    assertThat(neufassung.stelle().anzeigeText()).isEqualTo("§ 34a Absatz 1");
    assertThat(neufassung).isInstanceOf(Aenderungsbefehl.Neufassung.class);
    // Das Zitat der neuen Fassung ist vollständig übernommen („erhält folgende Fassung“).
    assertThat(((Aenderungsbefehl.Neufassung) neufassung).neuerText())
        .startsWith("(1) Durch Hauptsatzung kann bestimmt werden")
        .endsWith("persönlich im Sitzungsraum anwesend sein.");

    var kro = new AenderungsgesetzParser().parse(text, kreisordnung, null);
    assertThat(kro.artikel()).containsExactly("2");
    assertThat(kro.befehle()).hasSize(1);
    assertThat(kro.befehle().get(0).stelle().anzeigeText()).isEqualTo("§ 29a Absatz 1");

    // --- Anwendung, je gegen die amtliche Nachfassung vom 31. März 2026 --------------------
    pruefeGegenNachfassung(gemeindeordnung, go, "SchleswigHolstein/GO-SH-neu.txt");
    pruefeGegenNachfassung(kreisordnung, kro, "SchleswigHolstein/KrO-SH-neu.txt");
  }

  /**
   * Wendet die erkannten Befehle an und hält das Ergebnis Norm für Norm gegen die amtliche
   * Nachfassung. Kein Befehl darf zur Prüfung von Hand bleiben, keine Norm abweichen.
   */
  private static void pruefeGegenNachfassung(
      Gesetz alt, AenderungsgesetzParser.ParseErgebnis erkannt, String nachfassung)
      throws Exception {
    var anwendung = BefehlAnwender.anwenden(alt, erkannt.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(erkannt.befehle().size());
    assertThat(anwendung.protokoll())
        .noneMatch(x -> x.status() == BefehlAnwender.Status.MANUELL_PRUEFEN);

    var pfad = SAMPLEDATA.resolve(nachfassung);
    assumeTrue(Files.exists(pfad), "Nachfassung " + nachfassung + " fehlt");
    var soll = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(pfad);
    assertThat(soll.normen()).hasSameSizeAs(alt.normen());

    // Geprüft wird mit demselben Abgleich, den auch --nachfassung fährt: Werkzeug und Test messen
    // an einem Maßstab, sonst geht der eine durch, wo der andere anschlüge.
    var abgleich = Nachfassungsabgleich.vergleiche(soll, anwendung.neu());
    assertThat(abgleich.fehlende()).isEmpty();
    assertThat(abgleich.abweichungen()).as("Abweichungen gegen die amtliche Nachfassung").isEmpty();
    assertThat(abgleich.gehtAuf()).as(abgleich.kurzbericht()).isTrue();
  }

  /**
   * Nordrhein-Westfalen: Artikel 3 des 22. Rundfunkänderungsgesetzes (GV. NRW. 2026 S. 202) ändert
   * das Telemedienzuständigkeitsgesetz. Voller Akzeptanztest — alle vier Befehle werden angewandt;
   * das Ergebnis stimmt mit der amtlichen Fassung vom 1. April 2026 (recht.nrw.de) überein.
   */
  @Test
  void tmzGesetzAcceptance() throws Exception {
    var alt = SAMPLEDATA.resolve("NRW/TMZ-Gesetz-alt.txt");
    var pdf = SAMPLEDATA.resolve("NRW/GV-NRW-2026-S202_22-Rundfunkaenderungsgesetz.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "TMZ-Gesetz-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    // Der Klammerzusatz führt Kurztitel und Abkürzung nebeneinander; das Änderungsgesetz zitiert
    // den Kurztitel („Das Telemedienzuständigkeitsgesetz vom 29. März 2007 …“).
    assertThat(gesetz.jurabk()).isEqualTo("TMZ-Gesetz");
    assertThat(gesetz.kurzue()).isEqualTo("Telemedienzuständigkeitsgesetz");
    assertThat(gesetz.normen()).hasSize(3);
    assertThat(gesetz.norm("§ 1").orElseThrow().absaetze()).hasSize(3);

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    // Das Heft ändert vier Gesetze; nur Artikel 3 trifft das TMZ-Gesetz.
    assertThat(parseErgebnis.artikel()).containsExactly("3");
    assertThat(parseErgebnis.befehle()).hasSize(4);
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // Im amtlichen Satz fehlt bei Artikel 2 Nr. 11 das schließende Anführungszeichen. Ohne eine
    // Strukturgrenze im ZitatExtraktor verschlänge dieses Zitat die Artikel 3 bis 5. Die Grenze
    // greift seit der Aufzählungs-Grenze schon am nächsten Punkt desselben Artikels („12.“) und
    // nicht mehr erst an der Überschrift „Artikel 3“ — Artikel 2 Nr. 12 bleibt damit erhalten.
    assertThat(parseErgebnis.warnungen())
        .anyMatch(w -> w.startsWith("Zitat vor dem Aufzählungspunkt „12.“ nicht geschlossen"));

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(4);
    assertThat(anwendung.protokoll())
        .noneMatch(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN);

    // Abgleich mit der amtlichen Fassung vom 01.04.2026:
    var neu = anwendung.neu();
    // 1. Die Gesetzesüberschrift ist neu gefasst.
    assertThat(neu.langue())
        .startsWith("Gesetz zur Regelung der Zuständigkeit für die Überwachung von Telemedien im")
        .contains("Anwendungsbereich des Digitale-Dienste-Gesetzes");
    // 2. § 1 Absatz 2 nennt nun die Verordnung (EU) 2016/679 statt der Datenschutz-Grundverordnung
    //    und das Telekommunikation-Digitale-Dienste-Datenschutz-Gesetz.
    var absatz2 = neu.norm("§ 1").orElseThrow().absaetze().get(1).text();
    assertThat(absatz2)
        .contains("Verordnung (EU) 2016/679")
        .contains(
            "Telekommunikation-Digitale-Dienste-Datenschutz-Gesetzes vom 23. Juni 2021 (BGBl."
                + " I S. 1982; 2022 I S. 1045)")
        .doesNotContain("WDR-Rundfunkdatenschutzbeauftragte");
    // 3./4. § 2: Fundstellen-Ersetzung im Satzteil vor Nummer 1 und Neufassung der Nummern 1 und 2.
    var paragraph2 = neu.norm("§ 2").orElseThrow().gesamtText();
    assertThat(paragraph2)
        .contains("Artikel 4 des Gesetzes vom 17. Juli 2025 (BGBl. 2025 I Nr. 163)")
        .contains("§ 33 Absatz 1 und 2 Nummer 1 und 2 des Digitale-Dienste-Gesetzes")
        .doesNotContain("§ 11 Absatz 1 und 2 des Telemediengesetzes");
    // § 3 bleibt unberührt.
    assertThat(neu.norm("§ 3").orElseThrow().gesamtText())
        .isEqualTo(gesetz.norm("§ 3").orElseThrow().gesamtText());

    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    assertThat(HtmlRenderer.rendere(synopse, "E2E-Test TMZ-Gesetz")).contains("§ 2");
  }

  /**
   * Nordrhein-Westfalen, Massentest: Artikel 1 desselben Heftes ändert das WDR-Gesetz — 101 Befehle
   * an 31 Normen, darunter die Inhaltsübersicht, mehrere Umnummerierungs-Kaskaden über ganze
   * Absatz- und Nummernfolgen und ein Sammelrahmen („Es werden ersetzt:“). Genau ein Befehl bleibt
   * manuell; das Ergebnis wurde gegen die amtliche Fassung vom 1. April 2026 (recht.nrw.de)
   * abgeglichen.
   */
  @Test
  void wdrGesetzAcceptance() throws Exception {
    var alt = SAMPLEDATA.resolve("NRW/WDR-Gesetz-alt.txt");
    var pdf = SAMPLEDATA.resolve("NRW/GV-NRW-2026-S202_22-Rundfunkaenderungsgesetz.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "WDR-Gesetz-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(gesetz.jurabk()).isEqualTo("WDR-Gesetz");
    // Die Inhaltsübersicht ist eine eigene Norm — nur so greifen die Angabe-Befehle des Artikels 1.
    assertThat(gesetz.norm("Inhaltsübersicht")).isPresent();

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    // Kein Rest mehr. Das frühere Residuum „13. e) aa)“ — „In Satz 2 Nummer 1 wird … der Punkt am
    // Ende des Satzes durch ein Semikolon ersetzt …“ — trifft seit dieser Welle seine Stelle: Die
    // benannte Nummer wird innerhalb des benannten Satzes gesucht (StellenAufloeser.satzRahmen),
    // und § 16 Abs. 6 Nr. 1 liest sich danach wie die amtliche Nachfassung („… überschreitet; der
    // WDR hat …“).
    assertThat(manuellPfade).isEmpty();
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(parseErgebnis.befehle().size());
    assertThat(anwendung.neu().norm("§ 16").orElseThrow().absaetze().get(5).text())
        .contains("insgesamt drei Millionen Euro überschreitet; der WDR hat");

    var neu = anwendung.neu();
    // 1. Umnummerierungs-Kaskade in § 3: Absatz 2 wird durch zwei Absätze ersetzt, die bisherigen
    //    Absätze 3 bis 11 rücken auf, ein neuer Absatz 13 kommt hinzu — 15 Absätze in der Reihe der
    //    amtlichen Nachfassung.
    var paragraph3 = neu.norm("§ 3").orElseThrow();
    assertThat(paragraph3.absaetze().stream().map(a -> a.nummer()).toList())
        .containsExactly(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
    assertThat(paragraph3.absaetze().get(2).text())
        .startsWith("Der WDR veranstaltet ein landesweites Fernsehprogramm");
    assertThat(paragraph3.absaetze().get(12).text()).startsWith("Der WDR strebt Partnerschaften");

    // 2. Nummern-Kaskade in § 15 Absatz 3: Nummer 22 entfällt, die folgenden rücken auf — die
    //    Aufzählungsmarken im Text sind mitgezogen.
    var absatz3 = neu.norm("§ 15").orElseThrow().absaetze().get(2).text();
    assertThat(absatz3)
        .contains("22. den Landesbehindertenrat NRW e.V.")
        .contains("25. die Allianz Deutscher Produzentinnen und Produzenten")
        .doesNotContain("22. den Sozialverband VdK");

    // 3. Sammelrahmen „Es werden ersetzt:“ — die Unterpunkte tragen die Fundstelle, nicht das Verb.
    assertThat(neu.norm("§ 17").orElseThrow().gesamtText())
        .contains("schriftlich oder elektronisch");
    assertThat(neu.norm("§ 40").orElseThrow().gesamtText())
        .contains("schriftliche oder elektronische");

    // 4. Die Angabe-Befehle auf die Inhaltsübersicht sind angewandt.
    assertThat(neu.norm("Inhaltsübersicht").orElseThrow().gesamtText())
        .contains("§ 11 | (weggefallen)")
        .contains("§ 57a | Übergangsregelung zu Amtszeiten, Entsendung und Hörfunkprogrammen");

    // 5. „Dem Wortlaut des Absatzes 3 werden die folgenden Sätze vorangestellt“ trifft den Absatz,
    //    nicht die Norm.
    assertThat(neu.norm("§ 24").orElseThrow().absaetze().get(0).text())
        .startsWith("Die Intendantin oder der Intendant wird auf sechs Jahre gewählt");
    assertThat(neu.norm("§ 24").orElseThrow().absaetze().get(2).text())
        .startsWith("Die inhaltlichen Anforderungen an das Amt");
  }

  /**
   * Nordrhein-Westfalen: Artikel 2 desselben Heftes ändert das Landesmediengesetz — 18 Befehle,
   * *alle* angewandt. Das Ergebnis stimmt mit der amtlichen Fassung vom 1. April 2026 überein; nur
   * § 93 Absatz 3 Nummer 11 weicht ab, weil die amtliche Nachfassung dort über den Befehlswortlaut
   * hinaus auch die Wörter „Film (“ gestrichen hat.
   */
  @Test
  void lmgNrwAcceptance() throws Exception {
    var alt = SAMPLEDATA.resolve("NRW/LMG-NRW-alt.txt");
    var pdf = SAMPLEDATA.resolve("NRW/GV-NRW-2026-S202_22-Rundfunkaenderungsgesetz.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "LMG-NRW-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(gesetz.jurabk()).isEqualTo("LMG NRW");
    // Die Inhaltsübersicht führt auch die Gliederungs-Überschriften („Abschnitt 1 …“); sie zerfällt
    // daran nicht, sondern bleibt eine Norm bis zum ersten Normkopf.
    var uebersicht = gesetz.norm("Inhaltsübersicht").orElseThrow();
    assertThat(uebersicht.gesamtText())
        .contains("Abschnitt 1 Allgemeine Vorschriften")
        .contains("§ 127 | Übergangsregelung zur Neukonstituierung der Medienkommission");

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("2");
    assertThat(parseErgebnis.befehle()).hasSize(18);
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlManuell()).isZero();

    var neu = anwendung.neu();
    // § 49 Absatz 2 Satz 1: Die Fundstellen-Abkürzung „BGBl.“ darf den Satz nicht teilen, sonst
    // fände der zweite Teil des Befehls seinen Alttext nicht.
    assertThat(neu.norm("§ 49").orElseThrow().absaetze().get(1).text())
        .contains("der der Verordnung (EU) 2016/679")
        .contains("Telekommunikation-Digitale-Dienste-Datenschutz-Gesetzes vom 23. Juni 2021")
        .doesNotContain("Telekommunikation-Telemedien-Datenschutz-Gesetzes");
    // § 127 ist neu gefasst — samt Überschrift in der Inhaltsübersicht.
    assertThat(neu.norm("§ 127").orElseThrow().titel())
        .isEqualTo("Übergangsregelung zu Amtszeiten und Entsendung");
    assertThat(neu.norm("Inhaltsübersicht").orElseThrow().gesamtText())
        .contains("§ 127 | Übergangsregelung zu Amtszeiten und Entsendung");
    // § 88 Absatz 6 Satz 4 ist aufgehoben.
    assertThat(neu.norm("§ 88").orElseThrow().absaetze().get(5).text())
        .doesNotContain("Absatz 5 Satz 2 gilt entsprechend");
  }

  /**
   * Berlin: Das GVBl. ist zweispaltig gesetzt (Wolters-Kluwer-Satz). Der Test hält das Ergebnis des
   * Spaltenspikes fest — die Spaltenreihenfolge des Inhaltsstroms ist bereits die Lesereihenfolge,
   * eine koordinatenbasierte Spaltenerkennung ist dafür nicht nötig.
   *
   * <p>Beide Artikel sind volle Akzeptanzfälle: Artikel 2 (LAF-Errichtungsgesetz) mit beiden
   * Befehlen und allen fünf Normen, Artikel 1 (ASOG) mit allen sechs Befehlen und allen 171 Normen
   * gleich der amtlichen Nachfassung. Fünf der sechs Befehle zielen auf die <em>Anlage</em>, deren
   * Einheiten dort nicht als Aufzählungsmarken, sondern als Überschriften gesetzt sind („Nummer 6“,
   * „Nummer 23“) — sie werden als eigene Normen geführt.
   */
  @Test
  void asogLafAendGBerlin() throws Exception {
    var pdf = SAMPLEDATA.resolve("Berlin/GVBl-2026-17_ASOG-LAF-AendG.pdf");
    assumeTrue(Files.exists(pdf), "GVBl-Berlin-Beispiel-PDF fehlt");

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));

    // Spaltenspike: Die Befehle beider Artikel stehen in Lesereihenfolge im Text — die linke Spalte
    // läuft vollständig vor der rechten, ohne Verschränkung.
    assertThat(text.indexOf("§ 67 Absatz 2 wird wie folgt gefasst"))
        .isLessThan(text.indexOf("Die Anlage zu § 2 Absatz 4 Satz 1 wird wie folgt geändert"));
    assertThat(text.indexOf("Die Anlage zu § 2 Absatz 4 Satz 1 wird wie folgt geändert"))
        .isLessThan(text.indexOf("In der Nummer 2 wird der Punkt am Ende"));
    // Der laufende Seitenkopf ist auch dort entfernt, wo er mitten im Fließtext klebt.
    assertThat(text).doesNotContain("Gesetz- und Verordnungsblatt für Berlin");

    // Artikel 2 trägt sein Ziel in der Änderungsformel („§ 2 Satz 1 des Gesetzes zur Errichtung
    // …“); beide Punkte erben es als Kontext.
    var altLaf = SAMPLEDATA.resolve("Berlin/LAF-ErrG-alt.txt");
    assumeTrue(Files.exists(altLaf), "Berliner Stammfassung fehlt");
    var laf = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(altLaf);
    // Dieses Gesetz führt kein Inhaltsverzeichnis; der Wortlaut beginnt beim ersten Normkopf.
    assertThat(laf.normen()).hasSize(5);
    var ergebnis = new AenderungsgesetzParser().parse(text, laf, null);
    assertThat(ergebnis.artikel()).containsExactly("2");
    assertThat(ergebnis.befehle()).hasSize(2);
    assertThat(ergebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);
    assertThat(ergebnis.befehle())
        .extracting(b -> b.stelle().anzeigeText())
        .containsExactly("§ 2 Satz 1 Nummer 2", "§ 2 Satz 1 Nummer 2");

    // Artikel 1 ändert die unnummerierte, nach ihrer Vorschrift benannte Anlage („Die Anlage zu
    // § 2 Absatz 4 Satz 1 wird wie folgt geändert“). Der Zusatz benennt die Anlage nur — er darf
    // nicht als Ziel „§ 2“ gelesen werden; die Punkte erben „Anlage“ als Kontext.
    var altAsog = SAMPLEDATA.resolve("Berlin/ASOG-Bln-alt.txt");
    assumeTrue(Files.exists(altAsog), "ASOG-Stammfassung fehlt");
    var asog = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(altAsog);
    // 129 Paragraphen, die Anlage und ihre 40 Nummern — letztere sind eigene Normen mit eigener
    // Absatzzählung, wie das Landesrechtsportal sie führt.
    assertThat(asog.normen()).hasSize(171);
    assertThat(asog.norm("Anlage Nummer 23")).isPresent();
    var artikel1 = new AenderungsgesetzParser().parse(text, asog, null);
    assertThat(artikel1.artikel()).containsExactly("1");
    assertThat(artikel1.befehle())
        .extracting(b -> b.stelle().anzeigeText())
        .containsExactly(
            "§ 67 Absatz 2",
            "Anlage Nummer 6 Absatz 2",
            "Anlage Nummer 23 Absatz 4a",
            "Anlage Nummer 23",
            "Anlage Nummer 23 Absatz 9",
            "Anlage Nummer 31");
    assertThat(artikel1.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // Nr. 2 b) bb) ist die Struktureinfügung mit Wortanker: „Vor den Wörtern „Aus dem Bereich
    // Verkehr:“ wird folgender Absatz 5 eingefügt“ — die Position bestimmt der Wortanker, das Ziel
    // erbt der Befehl aus dem Rahmen („Nummer 23“ der Anlage).
    var bb =
        artikel1.befehle().stream()
            .filter(b -> b.provenienz().gliederungsPfad().equals("2. b) bb)"))
            .findFirst()
            .orElseThrow();
    assertThat(bb).isInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) bb;
    assertThat(einfuegung.vorher()).isTrue();
    assertThat(einfuegung.bezeichnung()).isEqualTo("5");
    assertThat(einfuegung.anker()).isEqualTo(new WortAnker.VorWoertern("Aus dem Bereich Verkehr:"));

    // Bei diesem Punkt fehlt im amtlichen Satz das schließende Anführungszeichen. Ohne die Grenze
    // am nächsten Aufzählungspunkt verschlänge das offene Zitat die Punkte cc) und c).
    assertThat(ZitatExtraktor.extrahiere(text).warnungen())
        .anyMatch(w -> w.startsWith("Zitat vor dem Aufzählungspunkt „cc)“ nicht geschlossen"));

    // Artikel 2 wird vollständig angewandt und gleicht danach der amtlichen Nachfassung.
    pruefeGegenNachfassung(laf, ergebnis, "Berlin/LAF-ErrG-neu.txt");

    // Artikel 1: fünf der sechs Befehle werden angewandt.
    var anwendung = BefehlAnwender.anwenden(asog, artikel1.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(x -> x.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(x -> x.befehl().provenienz().gliederungsPfad())
            .toList();
    // Kein Rest mehr: Der Zwischentitel „Aus dem Bereich Verkehr:“ trägt keine Absatz-
    // bezeichnung und wurde bislang dem vorangehenden Absatz zugeschlagen; dessen Text endete
    // dann nicht auf den Punkt, den „In Absatz 4a wird der Punkt am Ende durch ein Semikolon
    // ersetzt“ meint. Er ist jetzt ein eigener, bezeichnungsloser Absatz.
    assertThat(manuellPfade).isEmpty();
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(6);

    // Die Nummer 23 der Anlage trägt danach die Absatzfolge der amtlichen Nachfassung: Der neue
    // Absatz 5 ist ein Absatz geworden (nicht eine Zeile im Absatz 4a), und die bisherigen
    // Absätze 5 bis 9 sind zu 6 bis 10 aufgerückt.
    var nummer23 = anwendung.neu().norm("Anlage Nummer 23").orElseThrow();
    // Die bezeichnungslosen Einträge sind der Vorspann und die Zwischentitel des Katalogs („Aus
    // dem Bereich Verkehr:“); sie tragen keine Absatzbezeichnung und stehen deshalb für sich.
    assertThat(nummer23.absaetze())
        .extracting(Absatz::nummer)
        .containsExactly(
            null, null, "1", "2", "3", "4", "4a", "5", null, "6", "7", null, "8", "9", null, "10");

    // Norm für Norm gegen die amtliche Nachfassung vom 12. Juni 2026. Es bleiben genau zwei
    // benannte Abweichungen — keine davon in der Anwendung begründet:
    var sollAsog = SAMPLEDATA.resolve("Berlin/ASOG-Bln-neu.txt");
    assumeTrue(Files.exists(sollAsog), "ASOG-Nachfassung fehlt");
    var soll = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(sollAsog);
    var abweichend =
        Nachfassungsabgleich.vergleiche(soll, anwendung.neu()).abweichungen().stream()
            .map(Nachfassungsabgleich.Abweichung::enbez)
            .toList();
    // Keine Abweichung mehr: Alle 171 Normen gleichen der amtlichen Nachfassung.
    //
    // § 67 trug die beiden zuvor benannten Abweichungen: Das Portal setzt in der neuen Fassung
    //   amtliche Satznummern („(2) ¹Gegen einen …“), das Gesetzblatt, aus dem der Wortlaut
    //   stammt, setzt keine — die Zählung wird jetzt beim Einsetzen fortgeschrieben. Und in der
    //   Anlage Nummer 23 steht das Semikolon, weil der Zwischentitel den Absatz nicht mehr
    //   verlängert.
    assertThat(abweichend).isEmpty();
    // Die Nummer 31 war die dritte: Ihr Zitat läuft über einen Seitenwechsel, und der
    // ganzseitenbreite Titelblock des Änderungsgesetzes steht im Inhaltsstrom mitten darin
    // („… zur Sicherung des Be-“ / Titelblock / „triebs von Unterkünften …“). Seit die
    // Lesereihenfolge dem Satzbild folgt, steht der Titel, wo er hingehört, und das Zitat liest
    // sich durch.
    var nummer31 =
        anwendung.neu().norm("Anlage Nummer 31").orElseThrow().gesamtText().replaceAll("\\s+", " ");
    assertThat(nummer31)
        .doesNotContain("Gesetz zur Änderung des Allgemeinen Sicherheits- und Ordnungsgesetzes")
        .contains("die Ordnungsaufgaben zur Sicherung des Betriebs von Unterkünften");
    assertThat(anwendung.neu().norm("§ 67").orElseThrow().gesamtText())
        .contains("Gegen einen straßenverkehrsrechtlichen Verwaltungsakt");
  }

  /**
   * Baden-Württemberg: Die Verordnung des Innenministeriums zur Änderung der Kommunalwahlordnung
   * (GBl. 2026 Nr. 26). 37 Befehle, 31 angewandt; 91 der 93 Normen gleichen danach der amtlichen
   * Fassung vom 1. Mai 2026.
   *
   * <p>Zwei Eigenheiten des GBl-Satzes waren zu beheben: Der Seitenfuß steht im Inhaltsstrom
   * <em>mitten im Befehlstext</em> und trennt dort den Zieltext eines Befehls von seinem Verb, und
   * die Blattzählung („Seite 2 von 7“) steht mitunter für sich zwischen zwei Gliederungspunkten.
   * Dazu zwei Befehlsidiome: der Dativ ohne Artikel („Absatz 2 wird folgender Satz angefügt“) und
   * der Klammerzusatz hinter einer Anlagenbezeichnung („In Anlage 3b (Muster des Merkblatts …)“).
   *
   * <p>Die sechs liegengebliebenen Befehle betreffen sämtlich die <em>Muster</em> der Anlagen
   * (Wahlschein, Merkblätter). Sie sind nicht anwendbar, weil das Landesrechtsportal die Muster
   * nicht als Text ausliefert: Die Anlagen 2 bis 14 bestehen dort nur aus Kopf und Fundstelle. Was
   * nicht vorliegt, lässt sich nicht ändern — das ist eine Grenze der Quelle, nicht des Werkzeugs.
   */
  @Test
  void kommunalwahlOAendVOBadenWuerttemberg() throws Exception {
    var alt = SAMPLEDATA.resolve("BadenWuerttemberg/KomWO-BW-alt.txt");
    var pdf = SAMPLEDATA.resolve("BadenWuerttemberg/GBl-2026-26_KommunalwahlO-AendVO.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "KomWO-BW-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(gesetz.jurabk()).isEqualTo("KomWO");
    // 92 Normen; der Befehl 15. fügt den § 57a hinzu, den die Nachfassung dann als 93. führt.
    assertThat(gesetz.normen()).hasSize(92);

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    // Der Seitenfuß ist heraus — samt der Blattzählung, die für sich allein stand.
    assertThat(text).doesNotContain("Gesetzblatt für Baden-Württemberg, Jahrgang");
    assertThat(text).doesNotContain("Seite 2 von 7");
    // Ohne ihn steht der Befehl wieder zusammenhängend da.
    assertThat(text.replaceAll("\\s+", " "))
        .contains("die Wörter „Telegramm, Fernschreiben,“ gestrichen");

    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).hasSize(37);

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    assertThat(manuellPfade)
        .containsExactly("16. a)", "16. b)", "16. b) aa)", "16. b) bb)", "17.", "18.");
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(31);

    // „Absatz 2 Satz 3 Halbsatz 2 wird gestrichen“ — der Halbsatz nimmt sein Semikolon mit, und
    // der Satz behält seinen Schlusspunkt; so setzt es auch die amtliche Nachfassung.
    // (Der gleichlautende Halbsatz eines späteren Satzes bleibt unberührt.)
    assertThat(anwendung.neu().norm("§ 24").orElseThrow().absaetze().get(1).text())
        .contains("Absatz 1 Satz 3 gilt entsprechend. Im übrigen")
        .doesNotContain("entsprechend; anstelle der Wohnung ist der Wohnort anzugeben");

    // Norm für Norm gegen die amtliche Fassung vom 1. Mai 2026.
    var soll =
        new eu.mulk.aendggner.gesetz.land.LandesRechtLoader()
            .load(SAMPLEDATA.resolve("BadenWuerttemberg/KomWO-BW-neu.txt"));
    var abweichend =
        Nachfassungsabgleich.vergleiche(soll, anwendung.neu()).abweichungen().stream()
            .map(Nachfassungsabgleich.Abweichung::enbez)
            .toList();
    // § 20: Der Befehl ersetzt das Wort „Name“ durch „der vollständige Familienname“; im Zieltext
    //   steht davor bereits „der“, sodass es doppelt erscheint. Die amtliche Nachfassung räumt das
    //   auf, der Befehlswortlaut tut es nicht — ÄndGgner wendet den Wortlaut an.
    // Anlage 1: die sechs Muster-Befehle, siehe oben.
    assertThat(abweichend).containsExactly("§ 20", "Anlage 1");
  }

  /**
   * Nordrhein-Westfalen: Artikel 4 desselben Heftes fasst § 1 des Ausführungsgesetzes zum
   * Siebzehnten Rundfunkänderungsstaatsvertrag neu. Damit sind alle vier ändernden Artikel des
   * Heftes GV. NRW. 7/2026 belegt.
   *
   * <p>Zwei Eigenheiten: Der Artikel führt seinen einzigen Befehl <em>ohne</em> Gliederungspunkt,
   * und das Zitat der neuen Fassung trägt verschachtelte Anführungszeichen (ein vollständiges
   * „durch Artikel 3 …“ innerhalb einer eckigen Klammer). Das Ergebnis stimmt zeichengenau mit der
   * amtlichen Fassung vom 1. April 2026 (recht.nrw.de) überein — einschließlich der amtlichen
   * Platzhalter „[einsetzen: …]“.
   */
  @Test
  void rundfunkAusfuehrungsgesetzAcceptance() throws Exception {
    var alt = SAMPLEDATA.resolve("NRW/17-RAEStV-AusfG-alt.txt");
    var pdf = SAMPLEDATA.resolve("NRW/GV-NRW-2026-S202_22-Rundfunkaenderungsgesetz.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "17.-RÄStV-AusfG-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    // Der Klammerzusatz führt hier nur eine (mehrteilige) Bezeichnung, keinen Kurztitel am
    // Gedankenstrich; genau sie nennt der Einleitungssatz des Artikels 4.
    assertThat(gesetz.jurabk())
        .isEqualTo("Siebzehnter Rundfunkänderungsstaatsvertrag" + " Ausführungsgesetz");
    assertThat(gesetz.kurzue()).isNull();
    assertThat(gesetz.normen()).hasSize(3);
    // Der alte § 1 besteht aus einem einzigen, unbezeichneten Absatz.
    assertThat(gesetz.norm("§ 1").orElseThrow().absaetze()).hasSize(1);
    assertThat(gesetz.norm("§ 1").orElseThrow().absaetze().get(0).nummer()).isNull();

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    // Nur Artikel 4 trifft dieses Gesetz — die Auswahl gelingt über die Bezeichnung allein.
    assertThat(parseErgebnis.artikel()).containsExactly("4");
    // Der Artikel trägt keine nummerierten Punkte: Der Text nach der Änderungsformel ist der
    // Befehl.
    assertThat(parseErgebnis.befehle()).hasSize(1);
    assertThat(parseErgebnis.befehle().get(0)).isInstanceOf(Aenderungsbefehl.Neufassung.class);
    assertThat(parseErgebnis.befehle().get(0).stelle().anzeigeText()).isEqualTo("§ 1");

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(1);
    assertThat(anwendung.protokoll())
        .noneMatch(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN);

    // Abgleich mit der amtlichen Fassung vom 01.04.2026: aus dem einen unbezeichneten Absatz sind
    // zwei nummerierte geworden, Überschrift und Wortlaut stimmen zeichengenau.
    var neu = anwendung.neu();
    var paragraph1 = neu.norm("§ 1").orElseThrow();
    assertThat(paragraph1.titel()).isEqualTo("Entsendungsbefugnis");
    assertThat(paragraph1.absaetze()).hasSize(2);
    assertThat(paragraph1.absaetze().get(0).text())
        .isEqualTo(
            "Die Vertreterin oder der Vertreter aus dem Bereich „Medienwirtschaft und Film“ nach"
                + " § 21 Absatz 1 Satz 1 Buchstabe q Doppelbuchstabe jj des ZDF-Staatsvertrags vom"
                + " 31. August 1991 (GV. NRW. S. 408), der zuletzt durch Artikel 2 des Vierten"
                + " Medienänderungsstaatsvertrages vom 9. bis 16. Mai 2023 geändert worden ist"
                + " (GV. NRW. S. 1252) [noch erforderliche Anpassung: „durch Artikel 3 des Siebten"
                + " Medienänderungsstaatsvertrages vom 14. bis 26. März 2025 geändert worden ist"
                + " (GV. NRW. [einsetzen: Datum der Bekanntmachung])“], wird gemeinsam durch die"
                + " Allianz Deutscher Produzentinnen und Produzenten – Film, Fernsehen und"
                + " Audiovisuelle Medien e.V., Produktionsallianz NRW, das Filmbüro NW e.V. und den"
                + " Kulturrat NRW e.V., Sektion Medien, in den Fernsehrat des ZDF entsandt.");
    assertThat(paragraph1.absaetze().get(1).text())
        .isEqualTo(
            "Bis zum Ende der zum Stichtag 31. Dezember 2025 laufenden Amtsperiode des Fernsehrates"
                + " des ZDF ist § 1 in der am [einsetzen: Datum der Verkündung dieses Gesetzes]"
                + " geltenden Fassung weiter anzuwenden.");
    // §§ 2 und 3 bleiben unberührt.
    assertThat(neu.norm("§ 2").orElseThrow().gesamtText())
        .isEqualTo(gesetz.norm("§ 2").orElseThrow().gesamtText());
    assertThat(neu.norm("§ 3").orElseThrow().gesamtText())
        .isEqualTo(gesetz.norm("§ 3").orElseThrow().gesamtText());
  }

  /**
   * Hessen: Die Elfte Verordnung zur Änderung der Verordnung zur Bestimmung verkehrsrechtlicher
   * Zuständigkeiten (GVBl. 2026 Nr. 5). Voller Akzeptanzfall: Alle 21 Befehle des Artikels 1 werden
   * erkannt und angewandt, und jede der 52 Normen gleicht danach zeichengenau der amtlichen
   * Nachfassung.
   *
   * <p>Die Stammfassung ist über eine Browsersteuerung aus dem Landesrechtsportal beschafft — es
   * ist eine Einseitenanwendung, die Skriptabfragen nur ihre Hülle zurückgibt. Herkunft und
   * Aufbereitung stehen in {@code Hessen/SOURCES}.
   *
   * <p>Der hessische Sperrsatz („Der Mi n is t er“) bleibt bewusst unbehandelt: Er trifft nur den
   * Unterschriftenblock am Dokumentende, keinen einzigen Befehl.
   */
  @Test
  void verkehrsZustVAendVOHessen() throws Exception {
    var pdf =
        SAMPLEDATA.resolve("Hessen/GVBl-2026-05_11-AendVO-verkehrsrechtl-Zustaendigkeiten.pdf");
    assumeTrue(Files.exists(pdf), "GVBl-Hessen-Beispiel-PDF fehlt");

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));

    // Die laufende Fußzeile stand zwischen den Punkten 11. und 12. und hing sonst an Punkt 12.
    assertThat(text).doesNotContain("Gesetz- und Verordnungsblatt für das Land Hessen");
    // Die amtliche Sternchen-Fußnote der Überschrift steht am Seitenfuß zwischen 4. b) und 5. und
    // hing sonst an Punkt 4. b).
    assertThat(text).doesNotContain("Ändert FFN");
    // Der Sperrsatz beschränkt sich auf den Unterschriftenblock — hier bewusst nicht geheilt.
    assertThat(text).contains("Min isterpräsident");

    var alt = SAMPLEDATA.resolve("Hessen/StVRZustV-alt.txt");
    assumeTrue(Files.exists(alt), "Hessische Stammfassung fehlt");
    var verordnung = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(verordnung.jurabk()).isEqualTo("StVRZustV HE 2007");
    // Diese Verordnung führt keine Paragraphenüberschriften und gliedert sich in ausgeschriebene
    // Ordinalzahlen („Erster Teil“); beides kannte der Klartext-Parser bis dahin nicht.
    assertThat(verordnung.normen()).hasSize(52);
    assertThat(verordnung.norm("§ 1").orElseThrow().titel()).isNull();
    assertThat(verordnung.gliederungen()).hasSize(28);
    assertThat(verordnung.gliederungen())
        .extracting(g -> g.bezeichnung())
        .allMatch(b -> b.endsWith(" Teil"));
    assertThat(verordnung.gliederungen().get(0).bezeichnung()).isEqualTo("Erster Teil");

    var ergebnis = new AenderungsgesetzParser().parse(text, verordnung, null);
    // Artikel 2 regelt nur das Inkrafttreten.
    assertThat(ergebnis.artikel()).containsExactly("1");
    assertThat(ergebnis.befehle()).hasSize(21);
    assertThat(ergebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // Hessen kürzt im §-Kontext durchweg ab („In Abs. 1 …“, „Nr. 1 wird aufgehoben“); der Rahmen
    // liefert den Paragraphen.
    assertThat(befehlZu(ergebnis, "1. a)").stelle().anzeigeText()).isEqualTo("§ 6 Absatz 1");
    assertThat(befehlZu(ergebnis, "5. a)").stelle().anzeigeText()).isEqualTo("§ 14 Nummer 1");
    // „Die Absatzbezeichnung „(1)“ wird gestrichen.“ — die Bezeichnung selbst ist das Ziel.
    assertThat(befehlZu(ergebnis, "4. a)").stelle().anzeigeText())
        .isEqualTo("§ 13 Absatzbezeichnung (1)");

    // 5. b) und 5. f) sind Verbünde aus Umnummerierung und einer Folgeoperation ohne eigenen
    // Lokativ. Die Wortoperation löst norm-weit auf (ihr Zieltext ist unterscheidend), die
    // Satzzeichen-Operation dagegen wird auf die umnummerierte Nummer festgelegt — „das Komma“
    // träfe sonst beliebig viele Vorkommen.
    var umnummerierungUndErsetzung = (Aenderungsbefehl.Sammelbefehl) befehlZu(ergebnis, "5. b)");
    assertThat(umnummerierungUndErsetzung.teilbefehle())
        .extracting(b -> b.stelle().anzeigeText())
        .containsExactly("§ 14 Nummer 2", "§ 14");
    var umnummerierungUndKomma = (Aenderungsbefehl.Sammelbefehl) befehlZu(ergebnis, "5. f)");
    assertThat(umnummerierungUndKomma.teilbefehle())
        .extracting(b -> b.stelle().anzeigeText())
        .containsExactly("§ 14 Nummer 7", "§ 14 Nummer 5");
    var komma = (Aenderungsbefehl.Ersetzung) umnummerierungUndKomma.teilbefehle().get(1);
    assertThat(komma.alt()).isEqualTo(",");
    assertThat(komma.neu()).isEqualTo("und");
    // Ohne den Zusatz „am Ende“ ist das Satzzeichen nicht ans Einheitsende geheftet; der Anwender
    // verlangt dann, dass die Einheit genau eines trägt.
    assertThat(komma.amEnde()).isFalse();

    // Bereichs-Umnummerierung, absteigend angewandt: „Die bisherigen Nr. 3 und 4 werden die
    // Nr. 2 und 3.“
    var bereich = (Aenderungsbefehl.Sammelbefehl) befehlZu(ergebnis, "5. c)");
    assertThat(bereich.teilbefehle())
        .extracting(b -> b.stelle().anzeigeText())
        .containsExactly("§ 14 Nummer 4", "§ 14 Nummer 3");

    // Auch die Bereichsaufhebung spannt absteigend auf („Nr. 9 bis 11 werden aufgehoben“): Ob eine
    // aufgehobene Einheit einen nummerierten Platzhalter hinterlässt, entscheidet sich am Bestand,
    // der ihr folgt — von hinten aufgehoben, sieht jede den endgültigen.
    var aufhebung = (Aenderungsbefehl.Sammelbefehl) befehlZu(ergebnis, "5. h)");
    assertThat(aufhebung.teilbefehle())
        .extracting(b -> b.stelle().anzeigeText())
        .containsExactly("§ 14 Nummer 11", "§ 14 Nummer 10", "§ 14 Nummer 9");

    // --- Anwendung ---------------------------------------------------------------------------
    var anwendung = BefehlAnwender.anwenden(verordnung, ergebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(21);
    assertThat(anwendung.protokoll())
        .noneMatch(x -> x.status() == BefehlAnwender.Status.MANUELL_PRUEFEN);

    // § 13: Die Streichung der Absatzbezeichnung „(1)“ nimmt dem Wortlaut seine Nummer und lässt
    // ihn im Übrigen stehen; die Aufhebung des Abs. 2 beseitigt ihn ganz, weil eine Norm ohne
    // Absatzzählung keinen nummerierten Platzhalter tragen kann.
    var dreizehn = anwendung.neu().norm("§ 13").orElseThrow();
    assertThat(dreizehn.absaetze()).hasSize(1);
    assertThat(dreizehn.absaetze().get(0).nummer()).isNull();

    // § 14: Das Wort, das an die Stelle des Kommas tritt, bekommt seinen Zwischenraum.
    assertThat(anwendung.neu().norm("§ 14").orElseThrow().gesamtText())
        .contains("Fachkräfte) und")
        .doesNotContain("(weggefallen)");

    // Der Abgleich gegen die amtliche Nachfassung, Norm für Norm.
    var soll = SAMPLEDATA.resolve("Hessen/StVRZustV-neu.txt");
    assumeTrue(Files.exists(soll), "Hessische Nachfassung fehlt");
    var amtlich = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(soll);
    assertThat(amtlich.normen()).hasSize(52);
    var abgleich = Nachfassungsabgleich.vergleiche(amtlich, anwendung.neu());
    assertThat(abgleich.fehlende()).isEmpty();
    assertThat(abgleich.abweichungen()).isEmpty();
    assertThat(abgleich.gleich()).isEqualTo(52);
  }

  /**
   * Thüringen: Vierte Verordnung zur Änderung der Thüringer Kindergartenfinanzierungsverordnung
   * (GVBl. für den Freistaat Thüringen Nr. 2 vom 13.02.2026, S. 77). Neun der zehn Befehle des
   * Artikels 1 werden erkannt.
   *
   * <p>Das Heft ist ein Sammelheft: Es trägt vier Verkündungen, von denen nur diese eine ein
   * Änderungsdokument ist. Die übrigen — ein Zuständigkeitsbeschluss mit eigener Nummernfolge, eine
   * Stammverordnung und eine Inkrafttretens-Bekanntmachung — bleiben folgenlos.
   *
   * <p>Der unterscheidende Satzbefund: Das Heft führt <em>kein einziges</em> {@code „}, sondern
   * setzt gerade Anführungszeichen beidseitig. Würden sie wie im BGBl durchweg schließend gelesen,
   * fände der Zitatextraktor kein Zitat und keinen einzigen Befehl.
   *
   * <p>Kein voller Akzeptanztest: {@code landesrecht.thueringen.de} ist dieselbe anmeldepflichtige
   * juris-Anwendung wie die Portale Schleswig-Holsteins, Berlins, Baden-Württembergs und Hessens.
   */
  @Test
  void thuerKiGaFinVOAendVO() throws Exception {
    var pdf = SAMPLEDATA.resolve("Thueringen/GVBl-TH-2026-02_KiGaFinanzVO-AendVO-ua.pdf");
    assumeTrue(Files.exists(pdf), "GVBl-Thüringen-Beispiel-PDF fehlt");

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));

    // Gerade Anführungszeichen paarweise gelesen: Das Heft führt beide Sorten in gleicher Zahl.
    assertThat(text).doesNotContain("\"");
    assertThat(text.chars().filter(c -> c == '„').count())
        .isEqualTo(text.chars().filter(c -> c == '“').count());

    // Laufender Kolumnentitel und Seitenfuß herausgeschnitten. Der Kolumnentitel stand mitten in
    // einem getrennten Wort — nach dem Schnitt ist die Silbentrennung geheilt.
    assertThat(text).doesNotContain("Tag der Ausgabe");
    assertThat(text).doesNotContain("Verordnungsblatt für den Freistaat");
    assertThat(text).contains("Frauenhäusern und Frauenschutzwohnungen");

    var alt = SAMPLEDATA.resolve("Thueringen/ThuerKigaFinVO-alt.txt");
    var verordnung = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(verordnung.jurabk()).isEqualTo("ThürKigaFinVO");
    // Stand vor der Vierten ÄndVO: §§ 1 bis 12, darunter die aufgehobenen Platzhalter § 7 und
    // § 10 und der 2023 eingefügte § 7a.
    assertThat(verordnung.normen()).hasSize(13);
    assertThat(verordnung.norm("§ 7").orElseThrow().weggefallen()).isTrue();
    assertThat(verordnung.norm("§ 10").orElseThrow().weggefallen()).isTrue();
    var ergebnis = new AenderungsgesetzParser().parse(text, verordnung, null);
    // Artikel 2 regelt nur das Inkrafttreten.
    assertThat(ergebnis.artikel()).containsExactly("1");
    assertThat(ergebnis.befehle()).hasSize(10);
    assertThat(ergebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // „Die §§ 1 bis 3 erhalten folgende Fassung:“ — ein Zitat, drei Normen.
    var bereichsNeufassung = (Aenderungsbefehl.Sammelbefehl) befehlZu(ergebnis, "1.");
    assertThat(bereichsNeufassung.teilbefehle())
        .extracting(b -> b.stelle().anzeigeText())
        .containsExactly("§ 1", "§ 2", "§ 3");

    // „die Verweisung „X“ durch die Verweisung „Y“ ersetzt“ — dasselbe Muster wie „die Angabe“.
    var verweisung = (Aenderungsbefehl.Ersetzung) befehlZu(ergebnis, "2. b)");
    assertThat(verweisung.stelle().anzeigeText()).isEqualTo("§ 4 Satz 2");
    assertThat(verweisung.alt()).isEqualTo("§ 27 Abs. 1, 3 und 5");
    assertThat(verweisung.neu()).isEqualTo("§ 27 Abs. 1");

    // „Der bisherige § 8 wird § 7 und Absatz 1 wird wie folgt geändert:“ — der Rahmen verengt sich
    // auf eine Untereinheit der umnummerierten Norm; die Unterpunkte meinen den NEUEN § 7.
    var rahmenUmnummerierung = befehlZu(ergebnis, "6.");
    assertThat(rahmenUmnummerierung).isInstanceOf(Aenderungsbefehl.Umnummerierung.class);
    assertThat(rahmenUmnummerierung.stelle().anzeigeText()).isEqualTo("§ 8");
    assertThat(befehlZu(ergebnis, "6. a)").stelle().anzeigeText()).isEqualTo("§ 7 Absatz 1 Satz 1");
    assertThat(befehlZu(ergebnis, "6. b)").stelle().anzeigeText()).isEqualTo("§ 7 Absatz 1 Satz 2");

    // „Die bisherigen §§ 9 bis 12 werden die §§ 8 bis 10.“ nennt vier Ausgangs-, aber drei
    // Zielbezeichnungen — kein Widerspruch, sondern eine Lücke: § 10 ist aufgehoben und zählt nicht
    // mit. Welche Einheiten der Bereich trägt, weiß erst das Gesetz; der Befehl bleibt deshalb bis
    // zur Anwendung ungeteilt.
    var bereich = befehlZu(ergebnis, "7.");
    assertThat(bereich).isInstanceOf(Aenderungsbefehl.BereichsUmnummerierung.class);

    // Anwendung auf die konsolidierte Fassung vom Stand der Dritten ÄndVO (siehe SOURCES).
    var anwendung = BefehlAnwender.anwenden(verordnung, ergebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(10);
    assertThat(anwendung.protokoll())
        .noneMatch(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN);

    var neu = anwendung.neu();
    // Die Verordnung zählt danach lückenlos §§ 1 bis 10: § 6 ist aufgehoben, § 7a wird § 6, § 8
    // wird § 7, und aus §§ 9, 11 und 12 werden §§ 8, 9 und 10 — die aufgehobenen Platzhalter § 7
    // und § 10 weichen den neuen Bezeichnungen.
    assertThat(neu.normen())
        .extracting(n -> n.enbez())
        .containsExactly("§ 1", "§ 2", "§ 3", "§ 4", "§ 5", "§ 6", "§ 7", "§ 8", "§ 9", "§ 10");
    assertThat(neu.norm("§ 6").orElseThrow().titel())
        .isEqualTo("Zuschuss für die praxisintegrierte Ausbildung");
    assertThat(neu.norm("§ 10").orElseThrow().titel()).isEqualTo("Inkrafttreten, Außerkrafttreten");

    // Der umnummerierte § 7 (bisher § 8) trägt die beiden Verweisungs-Ersetzungen aus Punkt 6.
    var paragraph7 = neu.norm("§ 7").orElseThrow().gesamtText();
    assertThat(paragraph7)
        .contains("Meldungen nach § 27 Abs. 1 ThürKigaG")
        .contains("Meldungen nach § 30 Abs. 4 und 6 ThürKigaG")
        .doesNotContain("§ 27 Abs. 1 und 3 ThürKigaG")
        .doesNotContain("§ 27 Abs. 5 sowie");
    // Die neu gefassten §§ 1 bis 3 stammen aus einem einzigen Zitat.
    assertThat(neu.norm("§ 1").orElseThrow().gesamtText())
        .contains("Die Zahlung der Landeszuschüsse nach § 25 Satz 1 Nr. 2 und 3 ThürKigaG");
    // Die Überschrift des zitierten § 3 läuft über zwei Zeilen; sie gehört ganz in den Titel …
    assertThat(neu.norm("§ 3").orElseThrow().titel())
        .isEqualTo("Zahlungen an den örtlichen Träger der öffentlichen Jugendhilfe");
    // … und die des § 1, die im Satz am Kopf klebt, gehört nicht zusätzlich in den Normtext.
    assertThat(neu.norm("§ 1").orElseThrow().gesamtText())
        .doesNotContain("Zahlungen an die Wohnsitzgemeinde");
    // Die Silbentrennung des Spaltenumbruchs ist geheilt: „Thür-“ + „KigaG“ ist ein Wort, obwohl
    // die Folgezeile großgeschrieben beginnt — der Wortbestand des Heftes bezeugt es.
    assertThat(neu.norm("§ 5").orElseThrow().gesamtText())
        .contains("nach den §§ 25 und 26 ThürKigaG")
        .doesNotContain("Thür-KigaG");
  }

  /**
   * Änderungsantrag der GRÜNEN (Ltg-Drs. 19/10365) zum Landtags-Gesetzentwurf 19/9707: Er ändert
   * nicht das Stammgesetz, sondern die Drucksache — genau den Befehl § 3 Nr. 22, der die Artenliste
   * des neuen § 18 AVBayJG zitiert.
   */
  @Test
  void bayJgAenderungsantragAendertDenEntwurf() throws Exception {
    var entwurfPdf = SAMPLEDATA.resolve("BayJG/Ltg-Drs-19-9707_Gesetzentwurf.pdf");
    var antragPdf = SAMPLEDATA.resolve("BayJG/Ltg-Drs-19-10365_Aenderungsantrag-Gruene.pdf");
    assumeTrue(Files.exists(entwurfPdf) && Files.exists(antragPdf), "BayJG-Beispieldaten fehlen");

    var extraktor = new PatchTextExtraktor(SuperskriptModus.BEHALTEN);
    var entwurf = TextBereiniger.bereinige(extraktor.extrahiere(entwurfPdf));
    var antrag = TextBereiniger.bereinige(extraktor.extrahiere(antragPdf));

    // Der Entwurf stellt Wolf und Goldschakal nebeneinander unter Jagdrecht.
    assertThat(entwurf).contains("1.29. Wolf (Canis lupus),");
    assertThat(entwurf).contains("1.30. Goldschakal (Canis aureus);");

    var parseErgebnis = AenderungsantragParser.parse(antrag);
    assertThat(parseErgebnis.warnungen()).isEmpty();
    assertThat(parseErgebnis.befehle()).hasSize(2);

    var patch = EntwurfsPatcher.wendeAn(entwurf, parseErgebnis.befehle());
    assertThat(patch.warnungen()).isEmpty();
    assertThat(patch.angewandt()).isEqualTo(2);

    // Der Goldschakal ist gestrichen, der Wolf schließt die Liste nun mit Semikolon ab.
    assertThat(patch.text()).contains("1.29. Wolf (Canis lupus);");
    assertThat(patch.text()).doesNotContain("Goldschakal (Canis aureus)");
    // Die übrigen 154 Befehle des Entwurfs bleiben unangetastet.
    assertThat(patch.text()).contains("1.28. Mink (Neovison vison),");
    assertThat(patch.text()).contains("2. Federwild:");
  }

  /**
   * Derselbe Antrag durch die volle Pipeline. Er zielt auf § 3 des Entwurfs, und der ändert die
   * AVBayJG, nicht das BayJG — die Synopse des Stammgesetzes bleibt deshalb zu Recht dieselbe. Das
   * ist die eigentliche Probe: Ein Antrag darf nicht auf das Stammgesetz durchschlagen, nur weil
   * seine Stellenangaben zufällig auch dort passen könnten.
   */
  @Test
  void bayJgAenderungsantragLaesstDasStammgesetzUnberuehrt() throws Exception {
    var alt = SAMPLEDATA.resolve("BayJG/BayJG-alt.txt");
    var entwurfPdf = SAMPLEDATA.resolve("BayJG/Ltg-Drs-19-9707_Gesetzentwurf.pdf");
    var antragPdf = SAMPLEDATA.resolve("BayJG/Ltg-Drs-19-10365_Aenderungsantrag-Gruene.pdf");
    assumeTrue(
        Files.exists(alt) && Files.exists(entwurfPdf) && Files.exists(antragPdf),
        "BayJG-Beispieldaten fehlen");

    var ohne = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(alt, List.of(entwurfPdf)));
    var mit = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(alt, List.of(entwurfPdf, antragPdf)));

    assertThat(mit.anzahlAngewandt()).isEqualTo(ohne.anzahlAngewandt()).isEqualTo(154);
    assertThat(mit.anzahlManuell()).isEqualTo(ohne.anzahlManuell()).isEqualTo(0);
    // Beide Läufe zeigen eine Entwurfsfassung, nicht geltendes Recht.
    assertThat(mit.html()).contains("Entwurfsfassung");
    assertThat(mit.html()).contains("[Änderungsantrag Drs. 19/10365]");
  }

  /**
   * Die Zusammenstellung einer Beschlussempfehlung (BT-Drs. 20/7619) steht zweispaltig: links der
   * Entwurf, rechts die Ausschussfassung. Anders als beim alten BGBl und beim Berliner GVBl folgen
   * die Spalten <em>nicht</em> nacheinander im Inhaltsstrom, sondern zeilenweise verschränkt; nur
   * die Koordinaten trennen sie.
   *
   * <p>Die Probe aufs Exempel für die Spaltentrennung: Die linke Spalte muss Wort für Wort den
   * Regierungsentwurf ergeben, aus dem die Zusammenstellung gebaut ist — dieselbe Befehlszahl wie
   * aus BT-Drs. 20/6875, das als eigener Testfall danebensteht.
   */
  @Test
  void zusammenstellungTrenntDieSpaltenSauber() throws Exception {
    var xml = SAMPLEDATA.resolve("GEG/BJNR172810020.xml");
    var empfehlung = SAMPLEDATA.resolve("GEG/BT-Drs-20-7619_Beschlussempfehlung.pdf");
    var entwurf = SAMPLEDATA.resolve("GEG/BT-Drs-20-6875_Regierungsentwurf.pdf");
    assumeTrue(
        Files.exists(xml) && Files.exists(empfehlung) && Files.exists(entwurf),
        "GEG-Beispieldaten fehlen");

    var gesetz = new GiiXmlLoader().load(xml);
    var extraktor = new PatchTextExtraktor();
    var spalten = extraktor.extrahiereSpalten(empfehlung);

    var ausEntwurfsspalte =
        new AenderungsgesetzParser()
            .parse(TextBereiniger.bereinige(spalten.links()), gesetz, null, true);
    var ausDrucksache =
        new AenderungsgesetzParser()
            .parse(TextBereiniger.bereinige(extraktor.extrahiere(entwurf)), gesetz, null, true);

    assertThat(ausEntwurfsspalte.artikel()).isEqualTo(ausDrucksache.artikel());
    assertThat(ausEntwurfsspalte.befehle())
        .as("die linke Spalte ist der Regierungsentwurf")
        .hasSameSizeAs(ausDrucksache.befehle());
    assertThat(ausDrucksache.befehle()).hasSize(117);

    // Die rechte Spalte trägt überwiegend den Vermerk „unverändert“ — gesperrt gesetzt im PDF,
    // vom TextBereiniger auf die Normalform gebracht.
    assertThat(TextBereiniger.bereinige(spalten.rechts())).contains("unverändert");
  }

  /**
   * Aus einer Beschlussempfehlung entsteht die Synopse der <em>beschlossenen</em> Fassung: Die
   * Pipeline löst die zweispaltige Zusammenstellung auf, statt die Datei zu übergehen.
   *
   * <p>Gepinnt ist die Zahl der angewandten Befehle. Der große Rest bleibt aus demselben Grund
   * manuell wie beim Regierungsentwurf daneben (BT-Drs. 20/6875, {@link
   * #gegGrossesAenderungsgesetz}): Das Beispiel-XML ist die Urfassung des GEG von 2020, geändert
   * wird eine Fassung von 2023. Maßgeblich ist deshalb der Vergleich mit dem Entwurf — die
   * Ausschussfassung muss <em>mehr</em> Befehle tragen, denn der Ausschuss hat zwei Artikel
   * hinzugefügt (BGB und Betriebskostenverordnung).
   *
   * <p>Die Zahl stand bei 67, bevor die Anwendungsreihenfolge auf Schritte umgestellt wurde. Der
   * hinzugekommene Befehl ist die Bereichs-Umnummerierung des Bußgeldkatalogs („Die bisherigen
   * Nummern 19 bis 21 werden die Nummern 30 bis 32“, § 108 Absatz 1): Sie muss vor die Einfügung
   * rücken, die ihre Ausgangsnummern neu vergibt, und dorthin trug die frühere einmalige
   * Verschiebung nur ihr erstes Glied. Was in § 108 dabei herauskommt, sagt über die Ordnung nichts
   * — die Norm liegt mitten im beschriebenen Stamm-Mismatch. Belegt ist die Ordnung an den
   * Kaskaden, deren Stammfassung stimmt: BayJG Art. 29a und Art. 56 sowie WDR-Gesetz §§ 3 und 15,
   * beide gegen die amtliche Nachfassung abgeglichen.
   */
  @Test
  void beschlussempfehlungLiefertDieAusschussfassung() throws Exception {
    var xml = SAMPLEDATA.resolve("GEG/BJNR172810020.xml");
    var empfehlung = SAMPLEDATA.resolve("GEG/BT-Drs-20-7619_Beschlussempfehlung.pdf");
    var entwurf = SAMPLEDATA.resolve("GEG/BT-Drs-20-6875_Regierungsentwurf.pdf");
    assumeTrue(
        Files.exists(xml) && Files.exists(empfehlung) && Files.exists(entwurf),
        "GEG-Beispieldaten fehlen");

    var ausEmpfehlung = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(empfehlung)));
    var ausEntwurf = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(entwurf)));

    // 69 statt der früheren 68: Nennt der Rahmen dieselbe Gliederungseinheit wie der Befehl („…
    // Teil 2 wird wie folgt geändert: … die Angabe zur Überschrift von Teil 2 Abschnitt 4 …“),
    // so gilt sie jetzt einmal statt zweimal (InhaltsuebersichtAnwender.zielKette).
    assertThat(ausEmpfehlung.anzahlAngewandt()).isEqualTo(70);
    assertThat(ausEmpfehlung.anzahlAngewandt() + ausEmpfehlung.anzahlManuell())
        .as("die Ausschussfassung trägt mehr Befehle als der Entwurf")
        .isGreaterThan(ausEntwurf.anzahlAngewandt() + ausEntwurf.anzahlManuell());
    // Die Quellenzeile muss sagen, welche der beiden Spalten gilt.
    assertThat(ausEmpfehlung.html()).contains(", Ausschussfassung]");
  }

  /**
   * Derselbe Weg auf einer zweiten Beschlussempfehlung — dem Dritten Bevölkerungsschutzgesetz
   * (BT-Drs. 19/24334) —, damit der Leser nicht am GEG-Heft hängt.
   *
   * <p>Zwei Befehle bleiben hier unerkannt, beide wegen Setzfehlern der amtlichen Drucksache: In
   * Nummer 16 b) fehlt der Schlusspunkt („… eingefügt“), und Artikel 3 Nummer 1 b) trägt ein
   * überzähliges schließendes Anführungszeichen. Beides ist zeichengenau so übernommen.
   */
  @Test
  void beschlussempfehlungBevoelkerungsschutzgesetz() throws Exception {
    var xml = SAMPLEDATA.resolve("IfSG/BJNR104510000.xml");
    var pdf = SAMPLEDATA.resolve("IfSG/1924334.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "IfSG-Beispieldaten fehlen");

    var ergebnis = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(pdf)));

    // 51 statt der früheren 47: Die Satzzählung folgt jetzt der amtlichen — eine eingerückte
    // Aufzählungsmarke beendet keinen Satz, und eine Ordnungszahl vor einem Gliederungswort („nach
    // dem 5. Abschnitt“) auch nicht (SatzTeiler); dazu wird eine benannte Nummer innerhalb des
    // benannten Satzes gesucht. Vier satzbezogene Befehle treffen dadurch ihre Stelle.
    assertThat(ergebnis.anzahlAngewandt()).isEqualTo(51);
    assertThat(ergebnis.html()).contains(", Ausschussfassung]");
  }

  /**
   * Ein Entschließungsantrag trägt keine Rechtsetzungsbefehle. Er wird als solcher erkannt,
   * übergangen und gemeldet — nicht stillschweigend zu null Befehlen verarbeitet.
   */
  @Test
  void entschliessungsantragWirdGemeldetStattStillUebergangen() throws Exception {
    var xml = SAMPLEDATA.resolve("GEG/BJNR172810020.xml");
    var pdf = SAMPLEDATA.resolve("GEG/BT-Drs-21-7071_Beschlussempfehlung.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "GEG-Beispieldaten fehlen");

    var ergebnis = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(pdf)));

    assertThat(ergebnis.anzahlAngewandt()).isZero();
    assertThat(ergebnis.html()).contains("keine Änderungsbefehle");
  }

  /**
   * Die Kette und ihr Gedächtnis. Die eigene Ausgabe ist wieder Eingabe; sie trägt im Kopf, welche
   * Hefte auf ihr schon vollzogen sind. Trifft dasselbe Heft ein zweites Mal auf sie, so greifen
   * seine Befehle abermals — ein Befehl ist eine Anordnung und keine Zustandsbeschreibung —, und
   * der angefügte Absatz stünde zweimal da. Am Wortlaut allein ist das nicht zu erkennen, wohl aber
   * an dem, was die Fassung über sich selbst mitführt.
   */
  @Test
  void ketteRuegtDasZweimalAngewandteHeft() throws Exception {
    var xml = SAMPLEDATA.resolve("UWG/BJNR141400004.xml");
    var pdf = SAMPLEDATA.resolve("UWG/bgbl126s0043_regelungstext.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "UWG-Beispieldaten fehlen");

    var erste = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(pdf)));
    assertThat(erste.anzahlAngewandt()).isEqualTo(19);
    // Das Heft nennt sich nach dem, was im Dokument steht (Ausfertigungsdatum), nicht nach seinem
    // Dateinamen — sonst hinge die Wiedererkennung an einer Zufälligkeit des Ablageortes.
    assertThat(erste.neufassung())
        .containsOnlyOnce("Fortgeschrieben durch: Änderungsgesetz vom 12. Februar 2026");

    var zwischenfassung =
        new Quelle("UWG-Zwischenfassung.txt", erste.neufassung().getBytes(StandardCharsets.UTF_8));
    var zweite =
        Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(zwischenfassung, List.of(Quelle.lies(pdf))));

    assertThat(zweite.html())
        .contains("Die Fassung trägt „Änderungsgesetz vom 12. Februar 2026“ bereits")
        .contains("ein zweites Mal angewandt");
    // Vermerkt bleibt das Heft einmal: Die Liste zählt Hefte, nicht Anwendungen.
    assertThat(zweite.neufassung())
        .containsOnlyOnce("Fortgeschrieben durch: Änderungsgesetz vom 12. Februar 2026");
  }

  private static Aenderungsbefehl befehlZu(
      AenderungsgesetzParser.ParseErgebnis ergebnis, String gliederungsPfad) {
    return ergebnis.befehle().stream()
        .filter(b -> b.provenienz().gliederungsPfad().equals(gliederungsPfad))
        .findFirst()
        .orElseThrow();
  }

  /**
   * Die Fassung eines bestimmten Tages. Das 3. UWGÄndG tritt gestaffelt in Kraft: Artikel 1 Nummer
   * 2 Buchstabe c am 19. Juni 2026, alles Übrige erst am 27. September 2026. Wer alle Befehle auf
   * einen Schlag anwendet, erhält eine Fassung, die an keinem einzigen Tag gegolten hat — deshalb
   * warnt die Synopse davor, und mit einem Stichtag ergibt sich die wirklich geltende Fassung.
   */
  @Test
  void uwgTrittGestaffeltInKraft() throws Exception {
    var xml = SAMPLEDATA.resolve("UWG/BJNR141400004.xml");
    var pdf = SAMPLEDATA.resolve("UWG/bgbl126s0043_regelungstext.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "UWG-Beispieldaten fehlen");

    // Ohne Stichtag bleibt es beim vollen Bestand — aber nicht stillschweigend.
    var ohne = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(pdf)));
    assertThat(ohne.anzahlAngewandt()).isEqualTo(19);
    assertThat(ohne.html())
        .contains("Das Änderungsgesetz tritt gestaffelt in Kraft")
        .contains("Artikel 1 Nummer 2 Buchstabe c tritt am 19. Juni 2026 in Kraft.")
        .contains("<dt>Inkrafttreten</dt><dd>27. September 2026 (gestaffelt, siehe unten)</dd>");

    // Am 19. Juni 2026 galt genau ein Befehl: der Buchstabe c der Nummer 2.
    var frueh =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf)).mitStichtag(LocalDate.of(2026, 6, 19)));
    assertThat(frueh.anzahlAngewandt()).isEqualTo(1);
    assertThat(frueh.anzahlManuell()).isZero();
    assertThat(frueh.html())
        .contains("<dt>Stichtag</dt><dd>19. Juni 2026</dd>")
        .contains("Am Stichtag noch nicht in Kraft")
        .contains("Tritt erst am 27. September 2026 in Kraft");
    // Der Tag davor ändert noch gar nichts.
    var davor =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf)).mitStichtag(LocalDate.of(2026, 6, 18)));
    assertThat(davor.anzahlAngewandt()).isZero();

    // Am 27. September 2026 ist das Gesetz vollständig in Kraft; dann deckt sich die Fassung mit
    // der ungefilterten, und der Abschnitt „noch nicht in Kraft“ entfällt.
    var spaet =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf)).mitStichtag(LocalDate.of(2026, 9, 27)));
    assertThat(spaet.anzahlAngewandt()).isEqualTo(19);
    assertThat(spaet.html()).doesNotContain("Am Stichtag noch nicht in Kraft");
  }

  /**
   * Dasselbe am großen Fall, und zugleich die Probe auf die Schritt-Ordnung: Im GEG bleibt am 1.
   * Januar 2024 allein der Befehl der Nummer 22 zurück. Er steht in keiner Umnummerierungs-Kaskade,
   * sodass die übrigen 116 unverändert durchlaufen — die liegengebliebenen Befehle sind dieselben
   * wie ohne Stichtag und werden von der Auswahl nicht vermehrt.
   */
  @Test
  void gegStichtagLaesstDieUebrigenBefehleUnberuehrt() throws Exception {
    var xml = SAMPLEDATA.resolve("GEG/BJNR172810020-2023.xml");
    var pdf = SAMPLEDATA.resolve("GEG/bgbl123s0280_regelungstext.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "GEG-Beispieldaten fehlen");

    var vollstaendig = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(pdf)));
    assertThat(vollstaendig.anzahlAngewandt()).isEqualTo(119);
    assertThat(vollstaendig.anzahlManuell()).isZero();

    var anfang2024 =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf)).mitStichtag(LocalDate.of(2024, 1, 1)));
    assertThat(anfang2024.anzahlAngewandt()).isEqualTo(118);
    assertThat(anfang2024.anzahlManuell()).isZero();
    assertThat(anfang2024.html())
        .contains("Am Stichtag noch nicht in Kraft")
        .contains("Tritt erst am 1. Oktober 2024 in Kraft");

    var oktober2024 =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf)).mitStichtag(LocalDate.of(2024, 10, 1)));
    assertThat(oktober2024.anzahlAngewandt()).isEqualTo(119);
  }

  /**
   * Das IfSG-Gesetz von 2020 ändert dasselbe Stammgesetz in zwei Artikeln zu zwei Zeitpunkten:
   * Artikel 1 sogleich, Artikel 2 erst am 1. April 2021. Genau dort ist die auf einen Schlag
   * gerechnete Fassung eine, die es nie gab.
   *
   * <p>Zugleich der Fall der unbestimmten Grundregel: „am Tag nach der Verkündung“ nennt kein
   * Datum, das im Gesetzestext stünde. Erfunden wird keines; die betroffenen Befehle gelten als am
   * Stichtag bereits wirksam, und das wird gesagt.
   */
  @Test
  void ifsgZweiArtikelZuZweiZeitpunkten() throws Exception {
    var xml = SAMPLEDATA.resolve("IfSG/BJNR104510000-2020.xml");
    var pdf = SAMPLEDATA.resolve("IfSG/bgbl120s2397_78991.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "IfSG-Beispieldaten fehlen");

    var novemberFassung =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf)).mitStichtag(LocalDate.of(2020, 11, 19)));
    assertThat(novemberFassung.anzahlAngewandt()).isEqualTo(65);
    assertThat(novemberFassung.html())
        .contains("Am Stichtag noch nicht in Kraft")
        .contains("Eine Inkrafttretens-Anordnung nennt kein bestimmtes Datum");

    // Am 1. April 2021 kommen die zehn Befehle des Artikels 2 und der Doppelbuchstabe hinzu.
    var aprilFassung =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf)).mitStichtag(LocalDate.of(2021, 4, 1)));
    assertThat(aprilFassung.anzahlAngewandt()).isEqualTo(75);
    assertThat(aprilFassung.html()).doesNotContain("Am Stichtag noch nicht in Kraft");
  }

  /**
   * Brandenburg: das Erste Gesetz zur Änderung des Fraktionsgesetzes (GVBl. I 2026 Nr. 12).
   *
   * <p>Der erste Belegfall aus einem Portal, das kein juris-Portal ist: BRAVORS gibt seine
   * Vorschriften als freies HTML aus und führt zu jedem Gesetz die einzelnen Fassungen, sodass Vor-
   * und Nachfassung derselben Quelle entstammen. Zwei Eigenheiten des Satzes waren beim Aufbereiten
   * zu beachten und sind in {@code Brandenburg/SOURCES} vermerkt: Die Abschnittsbezeichnung steht
   * im Kopfelement vor der Überschrift, und die Aufzählungen sind echte Listen, deren Marken erst
   * das Stilblatt erzeugt.
   *
   * <p>Zugleich die Gegenprobe zum Inkrafttreten: Artikel 2 ordnet es schlicht an („am Tag nach der
   * Verkündung“). Es ist nicht gestaffelt — also darf die Synopse nicht davor warnen —, und ein
   * Datum nennt der Wortlaut nicht, also wird keines gezeigt.
   */
  @Test
  void fraktionsgesetzBrandenburg() throws Exception {
    var alt = SAMPLEDATA.resolve("Brandenburg/FraktG-alt.txt");
    var pdf = SAMPLEDATA.resolve("Brandenburg/GVBl-I-2026-12_FraktG-AendG.pdf");
    assumeTrue(Files.exists(alt) && Files.exists(pdf), "Brandenburger Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(alt);
    assertThat(gesetz.jurabk()).isEqualTo("FraktG");
    assertThat(gesetz.normen()).hasSize(24);
    // Die Abschnitte des Gesetzes sind als Gliederung erkannt, nicht als Normtext.
    assertThat(gesetz.gliederungen())
        .extracting(g -> g.titel())
        .contains("Status und Organisation");

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).hasSize(4);
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // Vier Befehle, vier Normen der Nachfassung gleich — § 1, § 10 und § 21 geändert.
    pruefeGegenNachfassung(gesetz, parseErgebnis, "Brandenburg/FraktG-neu.txt");

    // Artikel 2 ordnet das Inkrafttreten ohne Staffelung und ohne bestimmtes Datum an.
    var inkrafttreten = parseErgebnis.inkrafttreten();
    assertThat(inkrafttreten).isNotNull();
    assertThat(inkrafttreten.gestaffelt()).isFalse();
    var grundregel = inkrafttreten.grundregel().orElseThrow();
    assertThat(grundregel.datum()).isNull();
    assertThat(grundregel.wortlaut())
        .isEqualTo("Dieses Gesetz tritt am Tag nach der Verkündung in Kraft.");

    var synopse = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(alt, List.of(pdf)));
    assertThat(synopse.anzahlAngewandt()).isEqualTo(4);
    assertThat(synopse.html()).doesNotContain("tritt gestaffelt in Kraft");
  }

  /**
   * Rheinland-Pfalz: die Erste Landesverordnung zur Änderung der Ausbildungs- und Prüfungsordnung
   * für den Zugang zum dritten Einstiegsamt bei der Unfallkasse (GVBl. 2026 Nr. 21).
   *
   * <p>Die Prüfung reicht bis zur Befehlserkennung, nicht bis zur Anwendung, und zwar aus einem
   * Grund, der in der Quelle liegt: Das rheinland-pfälzische Landesrechtsportal führt
   * <em>keine</em> früheren Gesamtausgaben — anders als Hessen, Schleswig-Holstein, Berlin und
   * Baden-Württemberg kennt es nur die aktuelle. Ebenso das saarländische und das
   * sachsen-anhaltische Portal. Der Stamm ist deshalb die <em>Nachfassung</em>; die neun Meldungen
   * „Zieltext nicht vorhanden“ sind gerade der Beleg dafür, dass die Befehle dort bereits vollzogen
   * sind. Die Verkündungsplattform verkuendung.rlp.de gibt die Hefte seit dem 1. Juli 2026 als
   * freie PDF aus, das Änderungsblatt ist also frei zugänglich — es fehlt allein die Vorfassung.
   *
   * <p>Zwei allgemeine Funde hat der Fall gleichwohl gebracht (beide behoben):
   *
   * <ol>
   *   <li>Rheinland-Pfalz schreibt „die <b>Worte</b>“, wo das Handbuch der Rechtsförmlichkeit „die
   *       Wörter“ setzt. Der Befehlserkenner nimmt die Nebenform nun überall an; ohne sie blieben
   *       sieben der dreiundzwanzig Befehle unerkannt.
   *   <li>Der Seitenfuß des GVBl. steht im Inhaltsstrom und zerschneidet das Zitat einer Neufassung
   *       („§ 18 erhält folgende Fassung:“ / Fußzeile / „„§ 18 …““).
   * </ol>
   */
  @Test
  void unfallkassenAusbildungsVORheinlandPfalz() throws Exception {
    var stamm = SAMPLEDATA.resolve("RheinlandPfalz/UKAPOGS-E3-neu.txt");
    var pdf = SAMPLEDATA.resolve("RheinlandPfalz/GVBl-2026-21_UKAPOGS-E3-AendVO.pdf");
    assumeTrue(Files.exists(stamm) && Files.exists(pdf), "RLP-Beispieldaten fehlen");

    var gesetz = new eu.mulk.aendggner.gesetz.land.LandesRechtLoader().load(stamm);
    assertThat(gesetz.jurabk()).isEqualTo("UKAPOGS-E3");
    assertThat(gesetz.normen()).hasSize(22);

    var text = TextBereiniger.bereinige(new PatchTextExtraktor().extrahiere(pdf));
    // Der Seitenfuß ist heraus; ohne ihn steht das Zitat der Neufassung wieder zusammenhängend da.
    assertThat(text).doesNotContain("Gesetz- und Verordnungsblatt für das Land Rheinland-Pfalz -");
    assertThat(text.replaceAll("\\s+", " "))
        .contains("§ 18 erhält folgende Fassung: „§ 18 Bestehen der Laufbahnprüfung");

    var parseErgebnis = new AenderungsgesetzParser().parse(text, gesetz, null);
    assertThat(parseErgebnis.artikel()).containsExactly("1");
    assertThat(parseErgebnis.befehle()).hasSize(23);

    // Die Nebenform „die Worte“ wird wie „die Wörter“ gelesen.
    var wortbefehle =
        parseErgebnis.befehle().stream()
            .filter(b -> b.provenienz().originalText().contains("die Worte"))
            .toList();
    assertThat(wortbefehle).hasSizeGreaterThanOrEqualTo(7);
    assertThat(wortbefehle).noneMatch(b -> b instanceof UnbekannterBefehl);

    // Der Strichpunkt-Verbund („wird der Punkt durch einen Strichpunkt ersetzt und folgender
    // Halbsatz angefügt“) und der bezugspunktlose Chapeau („In der Einleitung“) werden gelesen;
    // dass beide gleichwohl nicht greifen, liegt an der Quelle und nicht am Werkzeug: Der Stamm
    // ist hier die Nachfassung, ihr Wortlaut trägt die Änderung bereits.
    assertThat(befehlAn(parseErgebnis, "5. a) aa)")).isInstanceOf(Sammelbefehl.class);
    assertThat(befehlAn(parseErgebnis, "7. a) aa)")).isInstanceOf(Ersetzung.class);

    // Kein Befehl des Heftes bleibt mehr ungelesen.
    assertThat(parseErgebnis.befehle()).noneMatch(b -> b instanceof UnbekannterBefehl);

    // Die Verweisung auf einen anderen Punkt desselben Artikels wird gelesen und ausgeführt: Der
    // verwiesene Punkt ändert die Überschrift des § 13, und die Angabe der Inhaltsübersicht wird
    // ihr nachgeführt. Hier scheitert das nicht am Erzeugnis, sondern am Gegenstand — diese
    // Verordnung führt gar keine Inhaltsübersicht, und die Rüge sagt genau das.
    var verweisung = befehlAn(parseErgebnis, "12.");
    assertThat(verweisung).isInstanceOf(VerweisenderBefehl.class);
    assertThat(((VerweisenderBefehl) verweisung).verweis()).isEqualTo("Nummer 8 Buchst. a");
    assertThat(gesetz.norm("Inhaltsübersicht")).isEmpty();

    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    var protokoll =
        anwendung.protokoll().stream()
            .filter(a -> a.befehl() == verweisung)
            .findFirst()
            .orElseThrow();
    assertThat(protokoll.grund()).isEqualTo(Grund.BESTAND_WIDERSPRICHT);
    assertThat(protokoll.begruendung()).contains("keine Inhaltsübersicht");
  }

  private static Aenderungsbefehl befehlAn(
      AenderungsgesetzParser.ParseErgebnis ergebnis, String gliederungsPfad) {
    return ergebnis.befehle().stream()
        .filter(b -> gliederungsPfad.equals(b.provenienz().gliederungsPfad()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * Der Abgleich mit der amtlichen Nachfassung ist fortan eine Leistung des Erzeugnisses und nicht
   * bloß eine des Testcodes: {@code --nachfassung} nimmt dieselben Eingaben an wie das Stammgesetz
   * und stellt das Ergebnis normweise dagegen. Berlin, Artikel 1: 171 von 171.
   */
  @Test
  void nachfassungWirdNormweiseAbgeglichen() throws Exception {
    var alt = SAMPLEDATA.resolve("Berlin/ASOG-Bln-alt.txt");
    var neu = SAMPLEDATA.resolve("Berlin/ASOG-Bln-neu.txt");
    var pdf = SAMPLEDATA.resolve("Berlin/GVBl-2026-17_ASOG-LAF-AendG.pdf");
    assumeTrue(
        Files.exists(alt) && Files.exists(neu) && Files.exists(pdf), "ASOG-Beispieldaten fehlen");

    var ergebnis =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(alt, List.of(pdf))
                .mitArtikel("1")
                .mitNachfassung(Quelle.lies(neu)));

    var abgleich = ergebnis.abgleich();
    assertThat(abgleich).isNotNull();
    assertThat(abgleich.gehtAuf()).as(abgleich.kurzbericht()).isTrue();
    assertThat(abgleich.gleich()).isEqualTo(171);
    assertThat(abgleich.geprueft()).isEqualTo(171);
    // Und er steht in der Synopse, wo ihn auch findet, wer keine Befehlszeile liest.
    assertThat(ergebnis.html())
        .contains("Abgleich mit der amtlichen Nachfassung")
        .contains("171 von 171 Normen gleich");
  }

  /**
   * Die Kette steht und fällt damit, dass die ausgegebene Fassung wieder eingelesen dasselbe Gesetz
   * ergibt. Geprüft wird das hier am <em>Bundesrecht</em> — der Rundlauf-Test des Textausgebers
   * deckt nur die Klartext-Stammfassungen ab, und gerade der Weg vom gii-XML in den kanonischen
   * Klartext ist der, den die Kette braucht: Wer ein zweites Heft auf das Ergebnis des ersten
   * anwenden will, hat kein XML mehr, sondern nur diesen Text.
   */
  @Test
  void dieAusgegebeneFassungLaesstSichWiederEinlesen() throws Exception {
    var xml = SAMPLEDATA.resolve("UWG/BJNR141400004.xml");
    var pdf = SAMPLEDATA.resolve("UWG/bgbl126s0043_regelungstext.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "UWG-Beispieldaten fehlen");

    var erst = Pipeline.erzeugeSynopse(Pipeline.Auftrag.von(xml, List.of(pdf)));
    assertThat(erst.anzahlAngewandt()).isEqualTo(19);

    // Der zweite Lauf hält dasselbe Ergebnis gegen den Text, den der erste geschrieben hat. Geht
    // der Abgleich auf, so trägt der Text die Fassung vollständig — und damit die Kette.
    var zweit =
        Pipeline.erzeugeSynopse(
            Pipeline.Auftrag.von(xml, List.of(pdf))
                .mitNachfassung(
                    new Quelle("UWG-neu.txt", erst.neufassung().getBytes(StandardCharsets.UTF_8))));

    var abgleich = zweit.abgleich();
    assertThat(abgleich).isNotNull();
    assertThat(abgleich.fehlende()).isEmpty();
    assertThat(abgleich.ueberzaehlige()).isEmpty();
    assertThat(abgleich.abweichungen()).isEmpty();
    assertThat(abgleich.gehtAuf()).as(abgleich.kurzbericht()).isTrue();
  }
}
