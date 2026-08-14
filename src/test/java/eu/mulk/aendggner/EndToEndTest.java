package eu.mulk.aendggner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.parse.AenderungsantragParser;
import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.EntwurfsPatcher;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.SuperskriptModus;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.aenderung.parse.ZitatExtraktor;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.gii.GiiXmlLoader;
import eu.mulk.aendggner.synopse.HtmlRenderer;
import eu.mulk.aendggner.synopse.SynopseBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // Das XML ist bereits konsolidiert; entscheidend ist, dass die Anwendung sauber terminiert
    // und jeden Befehl protokolliert.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
    // Gliederungs-Überschriften (Teil/Abschnitt) werden als Befehle erkannt und angewandt.
    assertThat(anwendung.anzahlAngewandt()).isGreaterThanOrEqualTo(50);
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

    // 4. Anwenden auf die alte Fassung. Von 154 Befehlen bleiben genau drei als „manuell prüfen“
    //    stehen — Folgeänderungen innerhalb einer mehrschrittigen Umnummerierungssequenz, die
    //    ÄndGgner bewusst nicht automatisch auflöst (statt fehlerhaft zu raten):
    //      * Art. 56 (§ 1 Nr. 48): eine umfangreiche Neunummerierung des Bußgeldkatalogs
    //        (Einfügen der Nrn. 5–7 und 13, Verschieben von Nr. 6→9, 11→12 …) verschiebt die
    //        Zielnummern der begleitenden Buchstaben-/Wortänderungen.
    //    Die Sequenz in Art. 29a (§ 1 Nr. 23) löst sich dagegen seit der Kaskaden-Ordnung des
    //    BefehlAnwenders auf: „Der bisherige Abs. 4 wird Abs. 5“ läuft vor der Bereichs-
    //    Umnummerierung „Die bisherigen Abs. 1 bis 3 werden die Abs. 2 bis 4“, sodass die auf den
    //    neuen Abs. 5 zielenden Wort- und Satzbefehle ihren Alttext finden.
    //    Diese Residuen sind exakt gepinnt; sie landen mit Begründung im Abschnitt „Manuell prüfen“
    //    der Synopse und werden nie stillschweigend verworfen.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    assertThat(manuellPfade).containsExactlyInAnyOrder("48. a) ee)", "48. a) gg)", "48. b) cc)");

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
   * Schleswig-Holstein: Extraktion und Befehlserkennung auf dem Kommunalrecht-Änderungsgesetz
   * (GVOBl. Schl.-H. 2026/27). Kein voller Akzeptanztest — die konsolidierte Gemeindeordnung ist
   * aus freien Quellen nicht in der maßgeblichen Fassung zu beschaffen (das juris-Landesportal
   * liefert nur eine anmeldepflichtige API; die letzten frei archivierten Volltexte stammen aus
   * 2022 und kennen den hier geänderten § 34a noch nicht). Geprüft wird deshalb alles bis
   * einschließlich der Befehlserkennung, gegen ein Stammgesetz ohne Normen.
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
    var gemeindeordnung =
        new Gesetz("GO", "Gemeindeordnung für Schleswig-Holstein", "Gemeindeordnung", List.of());
    var kreisordnung =
        new Gesetz("KrO", "Kreisordnung für Schleswig-Holstein", "Kreisordnung", List.of());

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
    // Einziges Residuum: „In Satz 2 Nummer 1 wird … der Punkt am Ende des Satzes durch ein
    // Semikolon ersetzt …“ — die adressierte Nummer 1 trägt zwei Sätze und endet selbst auf ein
    // Komma; welchen Punkt der Befehl meint, ist dem Wortlaut nicht sicher zu entnehmen.
    assertThat(manuellPfade).containsExactly("13. e) aa)");
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(parseErgebnis.befehle().size() - 1);

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
   * <p>Kein voller Akzeptanztest: gesetze.berlin.de ist wie das schleswig-holsteinische Portal eine
   * anmeldepflichtige juris-Anwendung, die Stammfassungen sind daraus nicht zu beschaffen. Artikel
   * 1 ändert zudem eine *Anlage*; anlagenbezogene Befehle wendet ÄndGgner an (siehe GEG), doch der
   * {@link eu.mulk.aendggner.gesetz.land.LandesRechtTextParser} kennt nur „§“- und „Art.“-Normköpfe
   * — eine handgepflegte Stammfassung könnte diese Anlage nicht tragen (dieselbe Grenze, an der
   * Baden-Württemberg zurückgestellt wurde).
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
    var laf =
        new Gesetz(
            "LAF-ErrichtungsG",
            "Gesetz zur Errichtung eines Landesamtes für Flüchtlingsangelegenheiten und"
                + " Unterbringung",
            null,
            List.of());
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
    var asog =
        new Gesetz("ASOG Bln", "Allgemeines Sicherheits- und Ordnungsgesetz", null, List.of());
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
   * Zuständigkeiten (GVBl. 2026 Nr. 5). Der Test hält Extraktion und Befehlserkennung fest — alle
   * 21 Befehle des Artikels 1 werden erkannt.
   *
   * <p>Kein voller Akzeptanztest: {@code hessenrecht.hessen.de} ist dieselbe anmeldepflichtige
   * juris-Anwendung wie die Portale Schleswig-Holsteins und Berlins, die Stammfassung ist daraus
   * nicht zu beschaffen.
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

    var verordnung =
        new Gesetz(
            "VerkZustV",
            "Verordnung zur Bestimmung verkehrsrechtlicher Zuständigkeiten",
            null,
            List.of());
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

    var ohne = Pipeline.erzeugeSynopse(alt, List.of(entwurfPdf), null, false);
    var mit = Pipeline.erzeugeSynopse(alt, List.of(entwurfPdf, antragPdf), null, false);

    assertThat(mit.anzahlAngewandt()).isEqualTo(ohne.anzahlAngewandt()).isEqualTo(151);
    assertThat(mit.anzahlManuell()).isEqualTo(ohne.anzahlManuell()).isEqualTo(3);
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
   * Eine Beschlussempfehlung wird als solche erkannt und mit Begründung übergangen, statt eine halb
   * aufgelöste Fassung auszugeben. Die Meldung nennt den Entwurf, der stattdessen taugt.
   */
  @Test
  void beschlussempfehlungWirdMitBegruendungUebergangen() throws Exception {
    var xml = SAMPLEDATA.resolve("GEG/BJNR172810020.xml");
    var pdf = SAMPLEDATA.resolve("GEG/BT-Drs-20-7619_Beschlussempfehlung.pdf");
    assumeTrue(Files.exists(xml) && Files.exists(pdf), "GEG-Beispieldaten fehlen");

    var ergebnis = Pipeline.erzeugeSynopse(xml, List.of(pdf), null, false);

    assertThat(ergebnis.anzahlAngewandt()).isZero();
    assertThat(ergebnis.html()).contains("Beschlussempfehlung");
    assertThat(ergebnis.html()).contains("Drs. 20/6875");
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

    var ergebnis = Pipeline.erzeugeSynopse(xml, List.of(pdf), null, false);

    assertThat(ergebnis.anzahlAngewandt()).isZero();
    assertThat(ergebnis.html()).contains("keine Änderungsbefehle");
  }

  private static Aenderungsbefehl befehlZu(
      AenderungsgesetzParser.ParseErgebnis ergebnis, String gliederungsPfad) {
    return ergebnis.befehle().stream()
        .filter(b -> b.provenienz().gliederungsPfad().equals(gliederungsPfad))
        .findFirst()
        .orElseThrow();
  }
}
