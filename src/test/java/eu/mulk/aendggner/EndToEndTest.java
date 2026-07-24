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

    // 4. Anwenden auf die alte Fassung. Von 154 Befehlen bleiben genau fünf als „manuell prüfen“
    //    stehen — allesamt Folgeänderungen innerhalb zweier mehrschrittiger Umnummerierungs-
    //    sequenzen, die ÄndGgner bewusst nicht automatisch auflöst (statt fehlerhaft zu raten):
    //      * Art. 29a (§ 1 Nr. 23): ein vorangestellter Absatz und die Bereichs-Umnummerierung
    //        „Die bisherigen Abs. 1 bis 3 werden die Abs. 2 bis 4“ verschieben die Absatzzählung;
    //        die auf den neuen Abs. 5 zielenden Wort-/Satzbefehle finden ihren Alttext nicht mehr.
    //      * Art. 56 (§ 1 Nr. 48): eine umfangreiche Neunummerierung des Bußgeldkatalogs
    //        (Einfügen der Nrn. 5–7 und 13, Verschieben von Nr. 6→9, 11→12 …) verschiebt die
    //        Zielnummern der begleitenden Buchstaben-/Wortänderungen.
    //    Diese Residuen sind exakt gepinnt; sie landen mit Begründung im Abschnitt „Manuell prüfen“
    //    der Synopse und werden nie stillschweigend verworfen.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.protokoll()).hasSameSizeAs(parseErgebnis.befehle());
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    assertThat(manuellPfade)
        .containsExactlyInAnyOrder(
            "23. c) aa)", "23. c) cc)", "48. a) ee)", "48. a) gg)", "48. b) cc)");

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

    // Fünf der sieben Befehle werden angewandt. Zwei bleiben als „manuell prüfen“ stehen — zwei
    // Einfügeformen, die ÄndGgner noch nicht beherrscht (bewusst dokumentierte Grenze, nicht
    // stillschweigend verworfen):
    //   * Nr. 3 „Nach § 2 wird der folgende § 2 a eingefügt“ — Einfügung eines §-Blocks mit einer
    //     durch Leerzeichen getrennten Sachnummer („§ 2 a“).
    //   * Nr. 5 „In Kapitel 4 wird nach § 12 der folgende neue § 13 angefügt“ — kapitelbezogene
    //     §-Block-Einfügung.
    var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
    assertThat(anwendung.anzahlAngewandt()).isEqualTo(5);
    var manuellPfade =
        anwendung.protokoll().stream()
            .filter(a -> a.status() == BefehlAnwender.Status.MANUELL_PRUEFEN)
            .map(a -> a.befehl().provenienz().gliederungsPfad())
            .toList();
    assertThat(manuellPfade).containsExactlyInAnyOrder("3.", "5.");

    // Stichproben: „erhält folgende Fassung“ (§ 1 Abs. 1, § 2), Angaben-Ersetzung (§ 1 Abs. 5),
    // Wörter-Einfügung (§ 6 Abs. 1) und §-Umnummerierung (§ 13 → § 14).
    var neu = anwendung.neu();
    assertThat(neu.norm("§ 1").orElseThrow().absaetze().get(0).text())
        .contains("Verordnung (EU) 2021/2116");
    assertThat(neu.norm("§ 1").orElseThrow().absaetze().get(4).text()).contains("§ 14 Abs. 3");
    assertThat(neu.norm("§ 2").orElseThrow().titel()).isEqualTo("Registriernummer");
    assertThat(neu.norm("§ 6").orElseThrow().gesamtText()).contains("8 bis 10");
    assertThat(neu.norm("§ 14")).isPresent();

    var synopse = SynopseBuilder.baue(gesetz, anwendung, parseErgebnis.warnungen(), false);
    assertThat(HtmlRenderer.rendere(synopse, "E2E-Test NEFG")).contains("§ 1");
  }
}
