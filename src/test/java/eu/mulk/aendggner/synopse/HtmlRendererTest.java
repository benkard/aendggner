// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.synopse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Gliederung;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.List;
import org.junit.jupiter.api.Test;

class HtmlRendererTest {

  private static final Provenienz PROV = new Provenienz("1", "1.", "In § 1 wird … ersetzt.");

  private static Gesetz gesetz(String absatzText) {
    return new Gesetz(
        "TestG",
        "Testgesetz",
        "TestG",
        List.of(new Norm("§ 1", "Zweck", null, List.of(new Absatz("1", absatzText)), false)));
  }

  private static Synopse synopse() {
    var alt = gesetz("Der alte Wortlaut gilt.");
    var befehl =
        new Ersetzung(
            new Stelle(List.of(new Stelle.Paragraph("1"))), "alte", "neue", false, false, PROV);
    var anwendung = BefehlAnwender.anwenden(alt, List.of(befehl));
    return SynopseBuilder.baue(alt, anwendung, List.of("Eine Testwarnung."), false);
  }

  @Test
  void rendertBeideSpaltenMitDiffTags() {
    var html = HtmlRenderer.rendere(synopse(), "test.xml + test.pdf");

    assertThat(html).contains("Alte Fassung").contains("Neue Fassung");
    assertThat(html).contains("<del>alte</del>").contains("<ins>neue</ins>");
    assertThat(html).contains("§ 1 — Zweck");
  }

  @Test
  void escapetHtmlInGesetzestexten() {
    var alt = gesetz("Böser <script>alert('x')</script> Text & mehr.");
    var anwendung = BefehlAnwender.anwenden(alt, List.of());
    var synopse = SynopseBuilder.baue(alt, anwendung, List.of(), true);

    var html = HtmlRenderer.rendere(synopse, "quelle");

    assertThat(html).doesNotContain("<script>");
    assertThat(html).contains("&lt;script&gt;");
    assertThat(html).contains("&amp; mehr.");
  }

  @Test
  void listetManuellZuPruefendeBefehleAuf() {
    var alt = gesetz("Text.");
    var prov = new Provenienz("1", "2.", "Die Nummern 1 bis 3 werden aufgehoben.");
    var befehl = new UnbekannterBefehl(Stelle.LEER, "Die Nummern 1 bis 3 werden aufgehoben.", prov);
    var anwendung = BefehlAnwender.anwenden(alt, List.of(befehl));
    var synopse = SynopseBuilder.baue(alt, anwendung, List.of(), false);

    var html = HtmlRenderer.rendere(synopse, "quelle");

    assertThat(html).contains("Manuell prüfen");
    assertThat(html).contains("Die Nummern 1 bis 3 werden aufgehoben.");
  }

  @Test
  void wendetGliederungsUeberschriftAnUndRendertDiff() {
    var alt =
        new Gesetz(
            "TestG",
            "Testgesetz",
            "TestG",
            List.of(new Norm("§ 1", "Zweck", null, List.of(new Absatz("1", "Text.")), false)),
            List.of(new Gliederung("030", "Teil 3", "Alte Überschrift")));
    var befehl =
        new Neufassung(
            new Stelle(
                List.of(new Stelle.Ueberschrift(), new Stelle.Gliederungseinheit("Teil", "3"))),
            "Teil 3 Neue Überschrift",
            PROV);

    var anwendung = BefehlAnwender.anwenden(alt, List.of(befehl));
    assertThat(anwendung.protokoll().get(0).status()).isEqualTo(BefehlAnwender.Status.ANGEWANDT);
    assertThat(anwendung.neu().gliederungen().get(0).titel()).isEqualTo("Neue Überschrift");

    var html =
        HtmlRenderer.rendere(SynopseBuilder.baue(alt, anwendung, List.of(), false), "quelle");
    assertThat(html).contains("Geänderte Gliederungs-Überschriften");
    assertThat(html).contains("<del>Alte</del>").contains("<ins>Neue</ins>");
  }

  @Test
  void zeigtWarnungenAn() {
    var html = HtmlRenderer.rendere(synopse(), "quelle");

    assertThat(html).contains("Eine Testwarnung.");
  }

  @Test
  void traegtVorspannUndFusssteg() {
    var html = HtmlRenderer.rendere(synopse(), "test.xml + test.pdf");

    assertThat(html).contains("<dt>Stammgesetz</dt><dd>TestG</dd>");
    assertThat(html).contains("<dt>Grundlage</dt><dd>test.xml + test.pdf</dd>");
    assertThat(html).contains("<dt>Geltung</dt><dd>nichtamtlich</dd>");
    assertThat(html).contains("class=\"vordrucknummer\">ÄG&#8209;2<");
    assertThat(html).contains("maßgeblich ist allein die amtliche Verkündung");
    assertThat(html).doesNotContain("Entwurfsstand");
  }

  @Test
  void weistDenEntwurfsstandImVorspannAus() {
    var html = HtmlRenderer.rendere(synopse(), "quelle", true);

    assertThat(html).contains("<dt>Geltung</dt><dd>nichtamtlich, Entwurfsstand</dd>");
    assertThat(html).contains("nicht geltendes Recht");
  }

  @Test
  void markiertNeueNormenAlsEingefuegt() {
    var alt = gesetz("Text.");
    var neuGesetz =
        alt.mitNormen(
            List.of(
                alt.normen().get(0),
                new Norm("§ 1a", "Neu", null, List.of(new Absatz(null, "Neuer Inhalt.")), false)));
    var anwendung = new BefehlAnwender.AnwendungsErgebnis(neuGesetz, List.of());
    var synopse = SynopseBuilder.baue(alt, anwendung, List.of(), false);

    var html = HtmlRenderer.rendere(synopse, "quelle");

    assertThat(html).contains("§ 1a");
    assertThat(html).contains("neu</span>");
    assertThat(html).contains("(nicht vorhanden)");
    assertThat(html).contains("<ins>Neuer Inhalt.</ins>");
  }
}
