// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.BereichsUmnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.GliederungsUeberschriften;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Sammelbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturErsetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.anwendung.BefehlAnwender.Status;
import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Gliederung;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.List;
import org.junit.jupiter.api.Test;

class BefehlAnwenderTest {

  private static final Provenienz PROV = new Provenienz("1", "1.", "(Test)");

  private static Stelle stelle(Stelle.Komponente... komponenten) {
    return new Stelle(List.of(komponenten));
  }

  @Test
  void wendetErsetzungAn() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            "die Erprobung",
            "die gründliche Erprobung",
            false,
            false,
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Zweck dieses Gesetzes ist die gründliche Erprobung.");
  }

  @Test
  void meldetFehlendenAltTextAlsManuellPruefen() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            "gibt es nicht",
            "egal",
            false,
            false,
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    var eintrag = ergebnis.protokoll().get(0);
    assertThat(eintrag.status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(eintrag.begruendung()).contains("kommt im Zieltext nicht vor");
    // Das Gesetz bleibt unverändert.
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Zweck dieses Gesetzes ist die Erprobung.");
  }

  @Test
  void meldetMehrdeutigeErsetzungOhneJeweils() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("2")), "Prüfung", "Untersuchung", false, false, PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(ergebnis.protokoll().get(0).begruendung()).contains("mehrdeutig");
  }

  @Test
  void wendetJeweilsErsetzungAufAlleVorkommenAn() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("2")), "Prüfung", "Untersuchung", true, false, PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 2", 0))
        .contains("die Untersuchung der Tauglichkeit")
        .contains("Die Untersuchung erfolgt");
  }

  @Test
  void wendetErsetzungInSatzAn() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("2"), new Stelle.SatzNr("2")),
            "sorgfältig",
            "gewissenhaft",
            false,
            false,
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 2", 0)).contains("erfolgt gewissenhaft");
  }

  @Test
  void wendetErsetzungInNummerAn() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("2")),
            "Befehlen",
            "Änderungsbefehlen",
            false,
            false,
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1))
        .contains("2. die Anwendung von Änderungsbefehlen und");
  }

  @Test
  void wendetPunktZuKommaErsetzungAmEndeAn() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("3")),
            ".",
            ",",
            false,
            true,
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1)).contains("3. die Ausgabe von Synopsen,");
  }

  @Test
  void wendetNeufassungEinesAbsatzesAn() {
    var befehl =
        new Neufassung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            "(1) Zweck dieses Gesetzes ist die umfassende Erprobung.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Zweck dieses Gesetzes ist die umfassende Erprobung.");
  }

  @Test
  void wendetNeufassungEinesParagraphenAn() {
    var befehl =
        new Neufassung(
            stelle(new Stelle.Paragraph("2")),
            "§ 2 Begriffsbestimmungen (1) Erprobung ist die Untersuchung. (2) Bericht ist das"
                + " Ergebnis.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var norm = ergebnis.neu().norm("§ 2").orElseThrow();
    assertThat(norm.titel()).isEqualTo("Begriffsbestimmungen");
    assertThat(norm.absaetze()).hasSize(2);
    assertThat(norm.absaetze().get(0).nummer()).isEqualTo("1");
    assertThat(norm.absaetze().get(1).text()).isEqualTo("Bericht ist das Ergebnis.");
  }

  @Test
  void fuegtParagraphEin() {
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("2")),
            false,
            Ebene.PARAGRAPH,
            "2a",
            "§ 2a Verfahren (1) Das Verfahren ist einfach.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var enbezListe = ergebnis.neu().normen().stream().map(Norm::enbez).toList();
    assertThat(enbezListe).containsExactly("§ 1", "§ 2", "§ 2a", "§ 3");
    var neueNorm = ergebnis.neu().norm("§ 2a").orElseThrow();
    assertThat(neueNorm.titel()).isEqualTo("Verfahren");
    assertThat(neueNorm.absaetze().get(0).text()).isEqualTo("Das Verfahren ist einfach.");
  }

  @Test
  void meldetBereitsVorhandenenParagraphen() {
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1")), false, Ebene.PARAGRAPH, "2", "§ 2 Doppelt", PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(ergebnis.protokoll().get(0).begruendung()).contains("existiert bereits");
  }

  @Test
  void fuegtEinheitVorWortankerEin() {
    // „Vor den Wörtern „…“ wird folgende Nummer 1a eingefügt: „…““ — die Position bestimmt der
    // Wortanker, nicht die Struktur (GVBl. für Berlin 17/2026, Artikel 1 Nr. 2 b) bb)).
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            true,
            Ebene.NUMMER,
            "1a",
            "1a. die Prüfung von Zitaten,",
            new WortAnker.VorWoertern("die Anwendung von Befehlen"),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1))
        .isEqualTo(
            "Die Erprobung umfasst\n"
                + "  1. das Einlesen von Gesetzen,\n"
                + "  1a. die Prüfung von Zitaten,\n"
                + "  2. die Anwendung von Befehlen und\n"
                + "  3. die Ausgabe von Synopsen.");
  }

  @Test
  void fuegtEinheitNachWortankerEin() {
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            false,
            Ebene.NUMMER,
            "1a",
            "1a. die Prüfung von Zitaten,",
            new WortAnker.NachWoertern("das Einlesen von Gesetzen"),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1))
        .contains(
            "  1. das Einlesen von Gesetzen,\n  1a. die Prüfung von Zitaten,\n  2. die"
                + " Anwendung von Befehlen und");
  }

  @Test
  void meldetFehlendenWortankerEinerEinfuegung() {
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            true,
            Ebene.NUMMER,
            "1a",
            "1a. die Prüfung von Zitaten,",
            new WortAnker.VorWoertern("gibt es nicht"),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    var eintrag = ergebnis.protokoll().get(0);
    assertThat(eintrag.status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(eintrag.begruendung()).contains("kommt im Zieltext nicht vor");
  }

  @Test
  void meldetMehrdeutigenWortankerEinerEinfuegung() {
    // „Prüfung“ steht zweimal im Wortlaut des § 2 — die Position wäre nicht bestimmt.
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("2")),
            true,
            Ebene.SATZ,
            null,
            "Die Prüfung ist zu dokumentieren.",
            new WortAnker.VorWoertern("Prüfung"),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    var eintrag = ergebnis.protokoll().get(0);
    assertThat(eintrag.status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(eintrag.begruendung()).contains("mehrdeutig");
  }

  @Test
  void fuegtWoerterNachAnkerEin() {
    var befehl =
        new WoerterEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            new WortAnker.NachWoertern("Gesetzes"),
            "und dieser Verordnung",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Zweck dieses Gesetzes und dieser Verordnung ist die Erprobung.");
  }

  @Test
  void fuegtAbsatzAn() {
    var befehl =
        new Anfuegung(
            stelle(new Stelle.Paragraph("3")),
            Ebene.ABSATZ,
            "2",
            "(2) Es tritt nie außer Kraft.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var norm = ergebnis.neu().norm("§ 3").orElseThrow();
    assertThat(norm.absaetze()).hasSize(2);
    assertThat(norm.absaetze().get(1).nummer()).isEqualTo("2");
  }

  @Test
  void haengtGanzeParagraphenAnsGesetzAn() {
    var befehl =
        new Anfuegung(
            Stelle.LEER,
            Ebene.PARAGRAPH,
            null,
            "§ 4\nÜbergang\nWer zuvor erprobt hat, erprobt weiter.\n\n"
                + "§ 5\nSchluss\nDiese Vorschrift gilt zuletzt.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(ergebnis.neu().normen().stream().map(Norm::enbez))
        .containsExactly("§ 1", "§ 2", "§ 3", "§ 4", "§ 5");
    assertThat(ergebnis.neu().norm("§ 4").orElseThrow().titel()).isEqualTo("Übergang");
  }

  @Test
  void haengtNichtAnUndBegruendetEsWennDasZitatKeinenNormkopfTraegt() {
    var befehl =
        new Anfuegung(Stelle.LEER, Ebene.PARAGRAPH, null, "Wer zuvor erprobt hat, …", PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(ergebnis.protokoll().get(0).grund()).isEqualTo(Grund.ZITAT_UNBRAUCHBAR);
    assertThat(ergebnis.neu().normen()).hasSize(3);
  }

  @Test
  void hebtParagraphenAuf() {
    var befehl = new Aufhebung(stelle(new Stelle.Paragraph("2")), PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var norm = ergebnis.neu().norm("§ 2").orElseThrow();
    assertThat(norm.weggefallen()).isTrue();
    assertThat(norm.gesamtText()).isEqualTo("(weggefallen)");
  }

  @Test
  void hebtAbsatzAuf() {
    var befehl = new Aufhebung(stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")), PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1)).isEqualTo("(weggefallen)");
  }

  @Test
  void streichtWoerter() {
    var befehl =
        new Streichung(
            stelle(new Stelle.Paragraph("2"), new Stelle.SatzNr("2")), "sorgfältig", PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 2", 0)).contains("Die Prüfung erfolgt.");
  }

  @Test
  void nummeriertAbsatzUm() {
    var befehl =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("3")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var norm = ergebnis.neu().norm("§ 1").orElseThrow();
    assertThat(norm.absaetze().get(1).nummer()).isEqualTo("3");
  }

  @Test
  void meldetUnbekanntenBefehlAlsManuellPruefen() {
    var befehl = new UnbekannterBefehl(Stelle.LEER, "Die Nummern 1 bis 3 werden aufgehoben.", PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(ergebnis.anzahlManuell()).isEqualTo(1);
    assertThat(ergebnis.anzahlAngewandt()).isEqualTo(0);
  }

  @Test
  void ersetztAbsatzDurchMehrereAbsaetze() {
    var befehl =
        new StrukturErsetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            Ebene.ABSATZ,
            "(1) Zweck ist die Erprobung. (1a) Die Erprobung ist wichtig.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var norm = ergebnis.neu().norm("§ 1").orElseThrow();
    assertThat(norm.absaetze()).hasSize(3);
    assertThat(norm.absaetze().get(0).nummer()).isEqualTo("1");
    assertThat(norm.absaetze().get(1).nummer()).isEqualTo("1a");
    assertThat(norm.absaetze().get(1).text()).isEqualTo("Die Erprobung ist wichtig.");
    assertThat(norm.absaetze().get(2).nummer()).isEqualTo("2");
  }

  @Test
  void ersetztSatzDurchMehrereSaetze() {
    var befehl =
        new StrukturErsetzung(
            stelle(new Stelle.Paragraph("2"), new Stelle.SatzNr("2")),
            Ebene.SATZ,
            "Die Prüfung erfolgt gewissenhaft. Sie wird dokumentiert.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 2", 0))
        .contains(
            "Erprobung ist die Prüfung der Tauglichkeit. Die Prüfung erfolgt gewissenhaft."
                + " Sie wird dokumentiert. Sie endet mit einem Bericht.");
  }

  @Test
  void ersetztAbsatzBereichDurchBlock() {
    // „Die Absätze 1 und 2 werden durch die folgenden Absätze 1 bis 3 ersetzt: „…““
    var befehl =
        new StrukturErsetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            Ebene.ABSATZ,
            "(1) Neu eins. (2) Neu zwei. (3) Neu drei.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var norm = ergebnis.neu().norm("§ 1").orElseThrow();
    assertThat(norm.absaetze()).hasSize(3);
    assertThat(norm.absaetze()).extracting(Absatz::nummer).containsExactly("1", "2", "3");
    assertThat(norm.absaetze().get(0).text()).isEqualTo("Neu eins.");
  }

  @Test
  void ersetztSatzBereichDurchBlock() {
    // „Die Sätze 1 und 2 werden wie folgt gefasst: „…““ in § 2 (3 Sätze).
    var befehl =
        new StrukturErsetzung(
            stelle(new Stelle.Paragraph("2"), new Stelle.SatzNr("1")),
            stelle(new Stelle.Paragraph("2"), new Stelle.SatzNr("2")),
            Ebene.SATZ,
            "Neuer Satz eins. Neuer Satz zwei.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 2", 0))
        .isEqualTo("Neuer Satz eins. Neuer Satz zwei. Sie endet mit einem Bericht.");
  }

  @Test
  void fuegtSatzNachSatzEin() {
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("2"), new Stelle.SatzNr("1")),
            false,
            Ebene.SATZ,
            null,
            "Die Tauglichkeit ist zu dokumentieren.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 2", 0))
        .contains(
            "Erprobung ist die Prüfung der Tauglichkeit. Die Tauglichkeit ist zu dokumentieren."
                + " Die Prüfung erfolgt sorgfältig.");
  }

  // --- Helfer --------------------------------------------------------------------------------

  @Test
  void wendetSammelbefehlAlsEinenEintragAn() {
    var teil1 =
        new Ersetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            "Erprobung",
            "Prüfung",
            false,
            false,
            PROV);
    var teil2 =
        new Ersetzung(
            stelle(new Stelle.Paragraph("2")), "Erprobung", "Prüfung", false, false, PROV);
    var sammel = new Sammelbefehl(List.of(teil1, teil2));

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(sammel));

    // Ein Befehl → genau ein Protokolleintrag, aber beide Teiledits wirken.
    assertThat(ergebnis.protokoll()).hasSize(1);
    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(ergebnis.protokoll().get(0).betroffeneEnbez())
        .containsExactlyInAnyOrder("§ 1", "§ 2");
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Zweck dieses Gesetzes ist die Prüfung.");
    assertThat(absatzText(ergebnis.neu(), "§ 2", 0)).startsWith("Prüfung ist die Prüfung");
  }

  @Test
  void sammelbefehlMitFehlschlagendemTeilWirdManuell() {
    var teil1 =
        new Ersetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            "Erprobung",
            "Prüfung",
            false,
            false,
            PROV);
    var teil2 =
        new Ersetzung(
            stelle(new Stelle.Paragraph("2")), "gibt es nicht", "egal", false, false, PROV);
    var sammel = new Sammelbefehl(List.of(teil1, teil2));

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(sammel));

    assertThat(ergebnis.protokoll()).hasSize(1);
    var eintrag = ergebnis.protokoll().get(0);
    assertThat(eintrag.status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(eintrag.begruendung()).contains("Teil 2");
    // Der gelungene Teil bleibt trotzdem wirksam.
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Zweck dieses Gesetzes ist die Prüfung.");
  }

  @Test
  void nummeriertParagraphUm() {
    var befehl =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("3")), stelle(new Stelle.Paragraph("4")), PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var enbezListe = ergebnis.neu().normen().stream().map(Norm::enbez).toList();
    assertThat(enbezListe).containsExactly("§ 1", "§ 2", "§ 4");
    assertThat(ergebnis.neu().norm("§ 4").orElseThrow().titel()).isEqualTo("Schlussvorschriften");
  }

  @Test
  void paragraphUmnummerierungUeberschreibtWeggefalleneZielnorm() {
    // § 2 aufheben, dann § 3 → § 2: die weggefallene Zielnorm wird ersetzt.
    var befehle =
        List.<eu.mulk.aendggner.aenderung.Aenderungsbefehl>of(
            new Aufhebung(stelle(new Stelle.Paragraph("2")), PROV),
            new Umnummerierung(
                stelle(new Stelle.Paragraph("3")), stelle(new Stelle.Paragraph("2")), PROV));

    var ergebnis = BefehlAnwender.anwenden(gesetz(), befehle);

    assertThat(ergebnis.protokoll()).allMatch(a -> a.status() == Status.ANGEWANDT);
    var enbezListe = ergebnis.neu().normen().stream().map(Norm::enbez).toList();
    assertThat(enbezListe).containsExactly("§ 1", "§ 2");
    assertThat(ergebnis.neu().norm("§ 2").orElseThrow().titel()).isEqualTo("Schlussvorschriften");
  }

  @Test
  void meldetKonfliktBeiParagraphUmnummerierung() {
    var befehl =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("3")), stelle(new Stelle.Paragraph("1")), PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(ergebnis.protokoll().get(0).begruendung()).contains("existiert bereits");
  }

  @Test
  void fuegtParagraphenBlockEin() {
    // „Nach § 1 werden die folgenden §§ 1a und 1b eingefügt: „…““ (bezeichnung == null).
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1")),
            false,
            Ebene.PARAGRAPH,
            null,
            "§ 1a Erstes Neu (1) Inhalt eins. § 1b Zweites Neu (1) Inhalt zwei.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var enbezListe = ergebnis.neu().normen().stream().map(Norm::enbez).toList();
    assertThat(enbezListe).containsExactly("§ 1", "§ 1a", "§ 1b", "§ 2", "§ 3");
    assertThat(ergebnis.neu().norm("§ 1a").orElseThrow().titel()).isEqualTo("Erstes Neu");
    assertThat(ergebnis.neu().norm("§ 1b").orElseThrow().titel()).isEqualTo("Zweites Neu");
  }

  @Test
  void ersetztParagraphBlock() {
    // „§ 2 wird durch die folgenden §§ 2 und 2a ersetzt: „…““ — § 2 wird durch zwei §§ ersetzt.
    // Der Querverweis „§ 1 Absatz 1“ im Text darf NICHT als Grenze zerteilt werden.
    var befehl =
        new StrukturErsetzung(
            stelle(new Stelle.Paragraph("2")),
            null,
            Ebene.PARAGRAPH,
            "§ 2 Begriffe (1) Erprobung nach § 1 Absatz 1 ist die Prüfung. § 2a Weiteres (1) Mehr.",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var enbezListe = ergebnis.neu().normen().stream().map(Norm::enbez).toList();
    assertThat(enbezListe).containsExactly("§ 1", "§ 2", "§ 2a", "§ 3");
    assertThat(ergebnis.neu().norm("§ 2").orElseThrow().absaetze().get(0).text())
        .isEqualTo("Erprobung nach § 1 Absatz 1 ist die Prüfung.");
    assertThat(ergebnis.neu().norm("§ 2a").orElseThrow().titel()).isEqualTo("Weiteres");
  }

  @Test
  void nummeriertGliederungUm() {
    var gesetz =
        new Gesetz(
            "TestG",
            "Gesetz",
            "Test",
            List.of(new Norm("§ 1", "Zweck", null, List.of(new Absatz("1", "Text.")), false)),
            List.of(new Gliederung("010020", "Abschnitt 2", "Früherkennung")));
    var befehl =
        new Umnummerierung(
            stelle(new Stelle.Gliederungseinheit("Abschnitt", "2")),
            stelle(new Stelle.Gliederungseinheit("Abschnitt", "3")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz, List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(ergebnis.neu().gliederungen().get(0).bezeichnung()).isEqualTo("Abschnitt 3");
    assertThat(ergebnis.neu().gliederungen().get(0).titel()).isEqualTo("Früherkennung");
  }

  private static Gesetz gesetz() {
    return new Gesetz(
        "TestG",
        "Gesetz zur Erprobung",
        "Testgesetz",
        List.of(
            new Norm(
                "§ 1",
                "Zweck",
                null,
                List.of(
                    new Absatz("1", "Zweck dieses Gesetzes ist die Erprobung."),
                    new Absatz(
                        "2",
                        "Die Erprobung umfasst\n"
                            + "  1. das Einlesen von Gesetzen,\n"
                            + "  2. die Anwendung von Befehlen und\n"
                            + "  3. die Ausgabe von Synopsen.")),
                false),
            new Norm(
                "§ 2",
                "Begriffe",
                null,
                List.of(
                    new Absatz(
                        null,
                        "Erprobung ist die Prüfung der Tauglichkeit. Die Prüfung erfolgt"
                            + " sorgfältig. Sie endet mit einem Bericht.")),
                false),
            new Norm(
                "§ 3",
                "Schlussvorschriften",
                null,
                List.of(new Absatz("1", "Dieses Gesetz tritt am 1. Januar 2021 in Kraft.")),
                false)));
  }

  @Test
  void wendetWortlautZuAbsatzAn() {
    // § 2 hat einen unnummerierten Wortlaut, der zu Absatz 1 wird.
    var befehl =
        new eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuAbsatz(
            stelle(new Stelle.Paragraph("2")), "1", PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var norm = ergebnis.neu().norm("§ 2").orElseThrow();
    assertThat(norm.absaetze()).hasSize(1);
    assertThat(norm.absaetze().get(0).nummer()).isEqualTo("1");
    assertThat(norm.absaetze().get(0).anzeigeText()).startsWith("(1) Erprobung ist");
  }

  @Test
  void wendetBereichsAufhebungAn() {
    // „Die Nummern 1 bis 3 werden aufgehoben.“ innerhalb von § 1 Absatz 2.
    var teile =
        // Absteigend, wie der Erkenner eine Bereichsaufhebung aufspannt: Ob eine aufgehobene
        // Einheit einen Platzhalter hinterlässt, entscheidet sich am Bestand, der ihr folgt.
        List.<eu.mulk.aendggner.aenderung.Aenderungsbefehl>of(
            new Aufhebung(
                stelle(
                    new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("3")),
                PROV),
            new Aufhebung(
                stelle(
                    new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("2")),
                PROV),
            new Aufhebung(
                stelle(
                    new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("1")),
                PROV));

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(new Sammelbefehl(teile)));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var text = absatzText(ergebnis.neu(), "§ 1", 1);
    // Ein Platzhalter hält einen Platz nur dort, wo ihm eine weitere Einheit derselben Art folgt.
    // Hier ist die ganze Aufzählung aufgehoben — es bleibt nichts, dessen Bezeichnung zu schonen
    // wäre, und der Einleitungssatz steht allein. So hält es auch die amtliche Nachfassung des
    // § 14 der hessischen Verkehrsrechts-Zuständigkeitsverordnung.
    assertThat(text).isEqualTo("Die Erprobung umfasst");
  }

  // --- Anhang/Anlage als Norm-Ziel -----------------------------------------------------------

  /** Ein Gesetz mit Anhang-Norm nach dem Muster des UWG (mehrere Absätze, Nummern mit Kindern). */
  private static Gesetz gesetzMitAnhang() {
    return new Gesetz(
        "TestG",
        null,
        null,
        List.of(
            new Norm("§ 1", "Zweck", null, List.of(new Absatz("1", "Es gilt der Anhang.")), false),
            new Norm(
                "Anhang",
                "(zu § 1)",
                null,
                List.of(
                    new Absatz(null, "Folgende Handlungen sind stets unzulässig:"),
                    new Absatz(
                        null,
                        "  1. die erste Handlung;\n"
                            + "  2. die zweite Handlung,\n"
                            + "    a) wenn sie morgens geschieht, oder\n"
                            + "    b) wenn sie abends geschieht;\n"
                            + "  3. die dritte Handlung;"),
                    new Absatz(
                        null,
                        "  31. die aggressive Handlung,\n"
                            + "    a) wenn sie laut geschieht, oder\n"
                            + "    b) wenn sie leise gemacht wird.\n"
                            + "  32. die letzte Handlung;")),
                false)));
  }

  private static Stelle anhangStelle(Stelle.Komponente... feinere) {
    var komponenten = new java.util.ArrayList<Stelle.Komponente>();
    komponenten.add(new Stelle.Gliederungseinheit("Anhang", ""));
    komponenten.addAll(List.of(feinere));
    return new Stelle(komponenten);
  }

  @Test
  void fuegtNummerImAnhangNachNummerMitKindernEin() {
    // „Nach Nummer 2 wird die folgende Nummer 2a eingefügt“ — Nummer 2 hat Buchstaben a)/b);
    // die neue Nummer muss hinter deren Block landen, nicht zwischen Nummer und Buchstaben.
    var befehl =
        new StrukturEinfuegung(
            anhangStelle(new Stelle.NummerNr("2")),
            false,
            Ebene.NUMMER,
            "2a",
            "2a. die eingeschobene Handlung;",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitAnhang(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Anhang", 1))
        .isEqualTo(
            "  1. die erste Handlung;\n"
                + "  2. die zweite Handlung,\n"
                + "    a) wenn sie morgens geschieht, oder\n"
                + "    b) wenn sie abends geschieht;\n"
                + "  2a. die eingeschobene Handlung;\n"
                + "  3. die dritte Handlung;");
  }

  @Test
  void fuegtNummernBlockImAnhangEin() {
    // Mehrere Einheiten in einem Einfügeblock bleiben eigene Zeilen.
    var befehl =
        new StrukturEinfuegung(
            anhangStelle(new Stelle.NummerNr("3")),
            false,
            Ebene.NUMMER,
            null,
            "3a. die vierte Handlung;\n3b. die fünfte Handlung;",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitAnhang(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Anhang", 1))
        .endsWith(
            "  3. die dritte Handlung;\n"
                + "  3a. die vierte Handlung;\n"
                + "  3b. die fünfte Handlung;");
  }

  @Test
  void loestNummerBuchstabeKetteImAnhangAuf() {
    // „b)“ existiert in Nummer 2 und Nummer 31 — die Kette „Nummer 31 Buchstabe b“ ist trotzdem
    // eindeutig, weil der Buchstabe im Block der Nummer 31 gesucht wird.
    var befehl =
        new Ersetzung(
            anhangStelle(new Stelle.NummerNr("31"), new Stelle.BuchstabeNr("b")),
            "gemacht wird.",
            "gemacht wird;",
            false,
            false,
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitAnhang(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Anhang", 2))
        .contains("wenn sie leise gemacht wird;")
        .contains("  32. die letzte Handlung;");
    // Nummer 2 Buchstabe b bleibt unangetastet.
    assertThat(absatzText(ergebnis.neu(), "Anhang", 1)).contains("wenn sie abends geschieht;");
  }

  @Test
  void streichtWoerterInDerUeberschriftEinerNorm() {
    var befehl =
        new Streichung(
            stelle(new Stelle.Paragraph("3"), new Stelle.Ueberschrift()),
            "Schlussvorschriften",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(ergebnis.neu().norm("§ 3").orElseThrow().titel()).isEmpty();
  }

  @Test
  void meldetMehrdeutigeNummerOhneAbsatzangabeImAnhang() {
    // Gäbe es dieselbe Nummer in mehreren Absätzen, bliebe der Befehl manuell.
    var gesetz =
        new Gesetz(
            "TestG",
            null,
            null,
            List.of(
                new Norm(
                    "Anhang",
                    null,
                    null,
                    List.of(
                        new Absatz(null, "  1. erstens;"),
                        new Absatz(null, "  1. nochmal erstens;")),
                    false)));
    var befehl =
        new Ersetzung(
            anhangStelle(new Stelle.NummerNr("1")), "erstens", "zuerst", false, false, PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz, List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(ergebnis.protokoll().get(0).begruendung()).contains("nicht eindeutig");
  }

  // --- Inhaltsübersicht ------------------------------------------------------------------------

  private static Gesetz gesetzMitInhaltsuebersicht() {
    return new Gesetz(
        "TestG",
        null,
        null,
        List.of(
            new Norm(
                "Inhaltsübersicht",
                null,
                null,
                List.of(
                    new Absatz(
                        null,
                        "Teil 1 | Allgemeines\n"
                            + "§ 1 | Zweck\n"
                            + "§ 2 | Begriffe\n"
                            + "Teil 2 | Verfahren\n"
                            + "Abschnitt 1 | Grundsätze\n"
                            + "§ 3 | Ablauf\n"
                            + "§ 4 | Fristen\n"
                            + "§ 5 | Schluss")),
                false),
            new Norm("§ 1", "Zweck", null, List.of(new Absatz(null, "Text.")), false)));
  }

  private static Stelle iuStelle(Stelle.Komponente... feinere) {
    var komponenten = new java.util.ArrayList<Stelle.Komponente>();
    komponenten.add(new Stelle.Inhaltsuebersicht());
    komponenten.addAll(List.of(feinere));
    return new Stelle(komponenten);
  }

  @Test
  void fasstAngabeInDerInhaltsuebersichtNeu() {
    var befehl =
        new Neufassung(iuStelle(new Stelle.Paragraph("2")), "§ 2 Begriffsbestimmungen", PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitInhaltsuebersicht(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Inhaltsübersicht", 0))
        .contains("§ 2 | Begriffsbestimmungen")
        .doesNotContain("§ 2 | Begriffe\n");
  }

  @Test
  void fuegtAngabeInDerInhaltsuebersichtEin() {
    var befehl =
        new StrukturEinfuegung(
            iuStelle(new Stelle.Paragraph("2")),
            false,
            Ebene.PARAGRAPH,
            null,
            "§ 2a Anwendungsbereich",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitInhaltsuebersicht(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Inhaltsübersicht", 0))
        .contains("§ 2 | Begriffe\n§ 2a | Anwendungsbereich\nTeil 2 | Verfahren");
  }

  @Test
  void ersetztAngabenBereichInDerInhaltsuebersicht() {
    // „Die Angaben zu den §§ 3 bis 4 werden durch die folgenden Angaben ersetzt: …“
    var befehl =
        new StrukturErsetzung(
            iuStelle(new Stelle.Paragraph("3")),
            iuStelle(new Stelle.Paragraph("4")),
            Ebene.PARAGRAPH,
            "§ 3 (weggefallen) § 4 (weggefallen)",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitInhaltsuebersicht(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Inhaltsübersicht", 0))
        .contains("§ 3 | (weggefallen)\n§ 4 | (weggefallen)")
        .doesNotContain("Ablauf");
  }

  @Test
  void streichtAngabeUndLoestGliederungsKetteAuf() {
    // „Die Angabe zu Teil 2 Abschnitt 1 wird gestrichen.“ — Kette grenzt das Fenster ein.
    var befehl =
        new Aufhebung(
            iuStelle(
                new Stelle.Gliederungseinheit("Teil", "2"),
                new Stelle.Gliederungseinheit("Abschnitt", "1")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitInhaltsuebersicht(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Inhaltsübersicht", 0))
        .doesNotContain("Abschnitt 1 | Grundsätze")
        .contains("Teil 2 | Verfahren\n§ 3 | Ablauf");
  }

  // --- Gliederungs-Überschriften ---------------------------------------------------------------

  private static Gesetz gesetzMitGliederungen() {
    var teil1 = new Gliederung("010", "Teil 1", "Allgemeines");
    var teil2 = new Gliederung("020", "Teil 2", "Anforderungen");
    return new Gesetz(
        "TestG",
        null,
        null,
        List.of(
            new Norm("§ 1", "Zweck", teil1, List.of(new Absatz(null, "Eins.")), false),
            new Norm("§ 2", "Begriffe", teil1, List.of(new Absatz(null, "Zwei.")), false),
            new Norm("§ 3", "Pflichten", teil2, List.of(new Absatz(null, "Drei.")), false),
            new Norm("§ 4", "Nachweise", teil2, List.of(new Absatz(null, "Vier.")), false)),
        List.of(teil1, teil2));
  }

  @Test
  void fuegtGliederungsUeberschriftenEin() {
    // „Nach § 2 werden die folgenden Überschriften zu Teil 3 und zu Teil 3 Abschnitt 1
    // eingefügt: „Teil 3 Modernisierung Abschnitt 1 Grundpflichten“.“
    var befehl =
        new GliederungsUeberschriften(
            stelle(new Stelle.Paragraph("2")),
            List.of(
                new Stelle.Gliederungseinheit("Teil", "3"),
                new Stelle.Gliederungseinheit("Abschnitt", "1")),
            List.of(),
            "Teil 3 Modernisierung Abschnitt 1 Grundpflichten",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitGliederungen(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(ergebnis.neu().gliederungen())
        .extracting(Gliederung::bezeichnung)
        .containsExactly("Teil 1", "Teil 3", "Abschnitt 1", "Teil 2");
    var abschnitt1 = ergebnis.neu().gliederungen().get(2);
    assertThat(abschnitt1.titel()).isEqualTo("Grundpflichten");
    // §§ 3 und 4 (der zusammenhängende Block nach dem Anker) hängen jetzt unter Abschnitt 1.
    assertThat(ergebnis.neu().norm("§ 3").orElseThrow().gliederung()).isEqualTo(abschnitt1);
    assertThat(ergebnis.neu().norm("§ 4").orElseThrow().gliederung()).isEqualTo(abschnitt1);
    assertThat(ergebnis.neu().norm("§ 2").orElseThrow().gliederung().bezeichnung())
        .isEqualTo("Teil 1");
  }

  @Test
  void ersetztGliederungsUeberschriften() {
    // „Die bisherigen Überschriften zu Teil 2 werden durch die folgende Überschrift zu
    // Abschnitt 2 ersetzt: „Abschnitt 2 Neue Anforderungen“.“
    var befehl =
        new GliederungsUeberschriften(
            Stelle.LEER,
            List.of(new Stelle.Gliederungseinheit("Abschnitt", "2")),
            List.of(List.of(new Stelle.Gliederungseinheit("Teil", "2"))),
            "Abschnitt 2 Neue Anforderungen",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetzMitGliederungen(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(ergebnis.neu().gliederungen())
        .extracting(Gliederung::bezeichnung)
        .containsExactly("Teil 1", "Abschnitt 2");
    assertThat(ergebnis.neu().norm("§ 3").orElseThrow().gliederung().titel())
        .isEqualTo("Neue Anforderungen");
  }

  @Test
  void ersetztGesetzesUeberschrift() {
    var befehl =
        new Neufassung(stelle(new Stelle.Ueberschrift()), "Gesetz zur gründlichen Erprobung", PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(ergebnis.neu().langue()).isEqualTo("Gesetz zur gründlichen Erprobung");
  }

  /**
   * Aufsteigende Umnummerierungs-Kaskade: Beide Befehle meinen die ursprüngliche Zählung. In
   * Dokumentreihenfolge angewandt entstünde ein zweiter Absatz 2; der Anwender zieht deshalb den
   * räumenden Befehl vor.
   */
  @Test
  void loestAufsteigendeUmnummerierungsKaskadeAuf() {
    var eins =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            PROV);
    var zwei =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("3")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(eins, zwei));

    assertThat(ergebnis.anzahlManuell()).isZero();
    assertThat(ergebnis.neu().norm("§ 1").orElseThrow().absaetze().stream().map(Absatz::nummer))
        .containsExactly("2", "3");
    // Das Protokoll bleibt in Dokumentreihenfolge, unabhängig von der Anwendungsreihenfolge.
    assertThat(ergebnis.protokoll().get(0).befehl()).isSameAs(eins);
  }

  /** Eine Umnummerierung läuft vor der Einfügung, die ihre bisherige Bezeichnung neu vergibt. */
  @Test
  void raeumtBezeichnungVorEinfuegung() {
    var einfuegung =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            false,
            Ebene.ABSATZ,
            "2",
            "(2) Neuer Absatz.",
            PROV);
    var umnummerierung =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2")),
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("3")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(einfuegung, umnummerierung));

    assertThat(ergebnis.anzahlManuell()).isZero();
    assertThat(ergebnis.neu().norm("§ 1").orElseThrow().absaetze().stream().map(Absatz::nummer))
        .containsExactly("1", "2", "3");
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1)).isEqualTo("Neuer Absatz.");
  }

  /**
   * Anders als ein Absatz trägt eine Aufzählungsnummer ihre Bezeichnung als Marke im Text; die
   * Umnummerierung muss sie dort austauschen.
   */
  @Test
  void nummeriertAufzaehlungsNummerImTextUm() {
    var befehl =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("2")),
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("4")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1))
        .contains("  4. die Anwendung von Befehlen und")
        .doesNotContain("  2. die Anwendung");
  }

  /** Der weggefallene Platzhalter mit der Zielbezeichnung weicht der Umnummerierung. */
  @Test
  void ueberschreibtWeggefalleneNummer() {
    var mitLuecke =
        new Gesetz(
            "TestG",
            "Gesetz zur Erprobung",
            "Testgesetz",
            List.of(
                new Norm(
                    "§ 1",
                    "Zweck",
                    null,
                    List.of(
                        new Absatz(
                            "1",
                            "Die Erprobung umfasst\n"
                                + "  1. (weggefallen)\n"
                                + "  2. die Anwendung von Befehlen.")),
                    false)),
            List.of());
    var befehl =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1"), new Stelle.NummerNr("2")),
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1"), new Stelle.NummerNr("1")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(mitLuecke, List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Die Erprobung umfasst\n  1. die Anwendung von Befehlen.");
  }

  /** Beginnt der Einschub mit einem Satzzeichen, entfällt das Leerzeichen hinter dem Anker. */
  @Test
  void fuegtSatzzeichenOhneVorangehendesLeerzeichenEin() {
    var befehl =
        new WoerterEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            new WortAnker.NachWoertern("die Erprobung"),
            ", auch in Teilen,",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));

    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo("Zweck dieses Gesetzes ist die Erprobung, auch in Teilen,.");
  }

  /**
   * Ein Katalog, wie ihn das Stammgesetz führt: Aufzählungszeilen auf zwei Leerzeichen, darunter
   * ein weggefallener Platzhalter.
   */
  private static Gesetz katalog() {
    return new Gesetz(
        "TestG",
        "Gesetz zur Erprobung",
        "Testgesetz",
        List.of(
            new Norm(
                "§ 1",
                "Zweck",
                null,
                List.of(
                    new Absatz(
                        "1",
                        "Erprobt wird, wer\n"
                            + "  1. Befehle erkennt,\n"
                            + "  2. Befehle schriftlich anwendet,\n"
                            + "  3. (weggefallen)\n"
                            + "  4. Ergebnisse prüft.")),
                false)),
        List.of());
  }

  /**
   * Die Neufassung einer Nummer setzt ihren Wortlaut auf die Einrückung der alten Einheit. Ohne sie
   * stünde die Zeile in Spalte 0 und der Zeilenblock ihrer Marke reichte bis ans Absatzende — ein
   * nachfolgender Einfügebefehl träte dann an die falsche Stelle.
   */
  @Test
  void neufassungEinerNummerBehaeltDieEinrueckung() {
    var befehl =
        new Neufassung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1"), new Stelle.NummerNr("2")),
            "2. Befehle anwendet,",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(katalog(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .contains("\n  2. Befehle anwendet,\n")
        .doesNotContain("\n2. ");
  }

  /**
   * Die Heilung des Weißraums nach einer Streichung glättet die Naht und sonst nichts: Die
   * Einrückung der Aufzählungszeilen bleibt, wie sie war.
   */
  @Test
  void streichungLaesstDieEinrueckungUnberuehrt() {
    var befehl =
        new Streichung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")), "schriftlich ", PROV);

    var ergebnis = BefehlAnwender.anwenden(katalog(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo(
            "Erprobt wird, wer\n"
                + "  1. Befehle erkennt,\n"
                + "  2. Befehle anwendet,\n"
                + "  3. (weggefallen)\n"
                + "  4. Ergebnisse prüft.");
  }

  /**
   * Vergibt ein eingefügter Block eine Bezeichnung, die ein leerer Platzhalter noch hält, so weicht
   * dieser — dieselbe Regel wie bei der Umnummerierung auf eine weggefallene Bezeichnung.
   */
  @Test
  void eingefuegterBlockVerdraengtDenWeggefallenenPlatzhalter() {
    var befehl =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1"), new Stelle.NummerNr("2")),
            false,
            Ebene.NUMMER,
            "3",
            "3. Befehle ordnet,",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(katalog(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "§ 1", 0))
        .isEqualTo(
            "Erprobt wird, wer\n"
                + "  1. Befehle erkennt,\n"
                + "  2. Befehle schriftlich anwendet,\n"
                + "  3. Befehle ordnet,\n"
                + "  4. Ergebnisse prüft.");
  }

  private static String absatzText(Gesetz gesetz, String enbez, int index) {
    return gesetz.norm(enbez).orElseThrow().absaetze().get(index).text();
  }

  // --- Bayerisches Landesrecht ---------------------------------------------------------------

  /** Ein Gesetz nach bayerischem Muster: Art.-Normen, amtliche Satznummern, Fußnoten. */
  private static Gesetz bayGesetz() {
    return new Gesetz(
        "BayTestG",
        "Bayerisches Testgesetz",
        null,
        List.of(
            new Norm(
                "Art. 1",
                "Zweck",
                null,
                List.of(
                    new Absatz(
                        "1",
                        "¹Die Erprobung ist Zweck dieses Gesetzes⁶). ²Sie erfolgt sorgfältig.\n"
                            + "⁶) [Amtl. Anm.:] BayRS 0-0-T"),
                    new Absatz("2", "¹Erster Satz. ²Zweiter Satz.")),
                false),
            new Norm(
                "Art. 2",
                "Begriffe",
                null,
                List.of(
                    new Absatz(
                        null,
                        "Erprobung ist die Prüfung der Tauglichkeit; sie endet mit einem"
                            + " Bericht.")),
                false)),
        List.of());
  }

  @Test
  void wendetFussnotenAufhebungAn() {
    var befehl =
        new eu.mulk.aendggner.aenderung.Aenderungsbefehl.FussnotenAufhebung(
            stelle(new Stelle.Paragraph("1", "Art.")), List.of("6"), PROV);

    var ergebnis = BefehlAnwender.anwenden(bayGesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    var text = absatzText(ergebnis.neu(), "Art. 1", 0);
    assertThat(text).doesNotContain("⁶").doesNotContain("[Amtl. Anm.:]");
    assertThat(text).startsWith("¹Die Erprobung ist Zweck dieses Gesetzes.");
  }

  @Test
  void meldetFehlendeFussnoteAlsManuellPruefen() {
    var befehl =
        new eu.mulk.aendggner.aenderung.Aenderungsbefehl.FussnotenAufhebung(
            stelle(new Stelle.Paragraph("1", "Art.")), List.of("7"), PROV);

    var ergebnis = BefehlAnwender.anwenden(bayGesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.MANUELL_PRUEFEN);
    assertThat(ergebnis.protokoll().get(0).begruendung()).contains("Fußnote 7");
  }

  @Test
  void wendetSatznummerierungsStreichungAn() {
    var befehl =
        new eu.mulk.aendggner.aenderung.Aenderungsbefehl.SatznummerierungStreichung(
            stelle(
                new Stelle.Paragraph("1", "Art."),
                new Stelle.AbsatzNr("2"),
                new Stelle.SatzNr("1")),
            "1",
            PROV);

    var ergebnis = BefehlAnwender.anwenden(bayGesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Art. 1", 1)).isEqualTo("Erster Satz. ²Zweiter Satz.");
  }

  @Test
  void satzUmnummerierungSchreibtSuperskriptUm() {
    // „Satz 2 wird Satz 3.“ — die amtliche Satznummer im Text wird umgeschrieben.
    var befehl =
        new Umnummerierung(
            stelle(
                new Stelle.Paragraph("1", "Art."),
                new Stelle.AbsatzNr("2"),
                new Stelle.SatzNr("2")),
            stelle(
                new Stelle.Paragraph("1", "Art."),
                new Stelle.AbsatzNr("2"),
                new Stelle.SatzNr("3")),
            PROV);

    var ergebnis = BefehlAnwender.anwenden(bayGesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Art. 1", 1)).isEqualTo("¹Erster Satz. ³Zweiter Satz.");
  }

  @Test
  void wendetWortlautZuSatzAn() {
    var befehl =
        new eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuSatz(
            stelle(new Stelle.Paragraph("2", "Art.")), "1", PROV);

    var ergebnis = BefehlAnwender.anwenden(bayGesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Art. 2", 0)).startsWith("¹Erprobung ist die Prüfung");
  }

  @Test
  void wendetWortlautVoranstellungUndNummerierungAn() {
    // Bayerische Folge: erst „Dem Wortlaut werden die folgenden Abs. 1 und 2 vorangestellt“,
    // dann „Der bisherige Wortlaut wird Abs. 3“ — nur der unnummerierte Absatz erhält die Nummer.
    var voranstellung =
        new eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautVoranstellung(
            stelle(new Stelle.Paragraph("2", "Art.")), "(1) Erstens. (2) Zweitens.", PROV);
    var nummerierung =
        new eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuAbsatz(
            stelle(new Stelle.Paragraph("2", "Art.")), "3", PROV);

    var ergebnis = BefehlAnwender.anwenden(bayGesetz(), List.of(voranstellung, nummerierung));

    assertThat(ergebnis.protokoll())
        .allSatisfy(eintrag -> assertThat(eintrag.status()).isEqualTo(Status.ANGEWANDT));
    var norm = ergebnis.neu().norm("Art. 2").orElseThrow();
    assertThat(norm.absaetze()).hasSize(3);
    assertThat(norm.absaetze().get(0).nummer()).isEqualTo("1");
    assertThat(norm.absaetze().get(1).nummer()).isEqualTo("2");
    assertThat(norm.absaetze().get(2).nummer()).isEqualTo("3");
    assertThat(norm.absaetze().get(2).text()).startsWith("Erprobung ist");
  }

  @Test
  void wendetHalbsatzErsetzungAn() {
    var befehl =
        new Ersetzung(
            stelle(new Stelle.Paragraph("2", "Art."), new Stelle.HalbsatzNr("2")),
            "sie endet",
            "sie schließt",
            false,
            false,
            PROV);

    var ergebnis = BefehlAnwender.anwenden(bayGesetz(), List.of(befehl));

    assertThat(ergebnis.protokoll().get(0).status()).isEqualTo(Status.ANGEWANDT);
    assertThat(absatzText(ergebnis.neu(), "Art. 2", 0))
        .isEqualTo("Erprobung ist die Prüfung der Tauglichkeit; sie schließt mit einem Bericht.");
  }

  @Test
  void ziehtUmnummerierungVorDieNeubesetzendeEinfuegung() {
    // „Nach § 2 wird der folgende neue § 3 eingefügt“ + „Der bisherige § 3 wird § 4“: In der
    // Textreihenfolge angewandt kollidierten beide Befehle auf „§ 3“. Die Umnummerierung
    // beschreibt den Stand vor der Änderung und geht der Neubesetzung sachlich voraus.
    var einfuegung =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("2")),
            false,
            Ebene.PARAGRAPH,
            "3",
            "§ 3 Zwischennorm (1) Der neue Text.",
            PROV);
    var umnummerierung =
        new Umnummerierung(
            stelle(new Stelle.Paragraph("3")),
            stelle(new Stelle.Paragraph("4")),
            new Provenienz("1", "2.", "(Test)"));

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(einfuegung, umnummerierung));

    assertThat(ergebnis.anzahlAngewandt()).isEqualTo(2);
    // Protokolliert wird weiterhin in der Reihenfolge des Änderungsgesetzes.
    assertThat(ergebnis.protokoll())
        .extracting(a -> a.befehl().provenienz().gliederungsPfad())
        .containsExactly("1.", "2.");
    assertThat(ergebnis.neu().normen())
        .extracting(n -> n.enbez())
        .containsExactly("§ 1", "§ 2", "§ 3", "§ 4");
    assertThat(ergebnis.neu().norm("§ 3").orElseThrow().titel()).isEqualTo("Zwischennorm");
    assertThat(ergebnis.neu().norm("§ 4").orElseThrow().titel()).isEqualTo("Schlussvorschriften");
  }

  @Test
  void zieehtEineGanzeUmnummerierungsketteVorDieEinfuegung() {
    // Eine Einfügung besetzt die Nr. 2, die eine Kette von Umnummerierungen erst räumen muss:
    // „Nr. 3 wird Nr. 4“ hält „Nr. 2 wird Nr. 3“ auf, und diese hält die Einfügung auf. Vorgezogen
    // werden muss deshalb nicht nur das letzte Glied der Kette, sondern die ganze Kette — sonst
    // träfe „Nr. 2 wird Nr. 3“ auf eine noch besetzte Nr. 3 (BayJG Art. 56 Abs. 1 Buchst. hh/ii).
    // Solange der Verbund als Ganzes vorrückte, war das umsonst zu haben; seit die Schritte
    // einzeln geordnet werden, hängt es an der Mitnahme der Vorgänger.
    var einfuegung =
        new StrukturEinfuegung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("2"), new Stelle.NummerNr("1")),
            false,
            Ebene.NUMMER,
            "2",
            "2. die Zulassung von Anträgen,",
            PROV);
    // So liefert der Erkenner eine Bereichs-Umnummerierung: das höhere Paar zuerst.
    var kette =
        new Sammelbefehl(
            List.of(umnummerierungNummer("2", "3", "4"), umnummerierungNummer("2", "2", "3")));

    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(einfuegung, kette));

    assertThat(ergebnis.anzahlManuell()).isZero();
    assertThat(absatzText(ergebnis.neu(), "§ 1", 1))
        .isEqualTo(
            "Die Erprobung umfasst\n"
                + "  1. das Einlesen von Gesetzen,\n"
                + "  2. die Zulassung von Anträgen,\n"
                + "  3. die Anwendung von Befehlen und\n"
                + "  4. die Ausgabe von Synopsen.");
  }

  @Test
  void laesstDieBegleitaenderungAnIhrerDokumentstelle() {
    // „Die bisherige Nr. 3 wird Nr. 4 und die Angabe „schriftliche “ wird gestrichen“: Die
    // Umnummerierung muss vor den Befehl rücken, der die Nr. 3 neu besetzt — ihre Begleitänderung
    // aber nicht. Vorgezogen träfe die Streichung noch zwei Fundstellen und bliebe mehrdeutig; an
    // ihrem Platz trifft sie genau eine, weil der Punkt davor die andere längst getilgt hat
    // (BayJG Art. 56 Abs. 1 Buchst. gg).
    var neuBesetzung = umnummerierungNummer("1", "2", "3");
    var tilgtDieErsteFundstelle =
        new Ersetzung(
            stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr("1")),
            "die schriftliche Anmeldung",
            "die Anmeldung",
            false,
            false,
            PROV);
    var verbund =
        new Sammelbefehl(
            List.of(
                umnummerierungNummer("1", "3", "4"),
                new Streichung(stelle(new Stelle.Paragraph("1")), "schriftliche ", PROV)));

    var ergebnis =
        BefehlAnwender.anwenden(
            kaskadenGesetz(), List.of(neuBesetzung, tilgtDieErsteFundstelle, verbund));

    // Beide Teile des Verbunds greifen — die Streichung ist an ihrer Dokumentstelle eindeutig.
    assertThat(ergebnis.anzahlManuell()).isZero();
    var text = absatzText(ergebnis.neu(), "§ 1", 0);
    assertThat(text).contains("1. die Anmeldung,").contains("3. die Prüfung und");
    // Die Umnummerierung ist vorgerückt, ihre Begleitänderung nicht: Nr. 4 trägt den Text der
    // bisherigen Nr. 3, und zwar ohne „schriftliche“.
    assertThat(text).contains("4. die Bestätigung.").doesNotContain("schriftliche");
  }

  private static Umnummerierung umnummerierungNummer(String absatz, String alt, String neu) {
    return new Umnummerierung(
        stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr(absatz), new Stelle.NummerNr(alt)),
        stelle(new Stelle.Paragraph("1"), new Stelle.AbsatzNr(absatz), new Stelle.NummerNr(neu)),
        PROV);
  }

  /**
   * Ein Gesetz für die Kaskadenprobe: Das Wort „schriftliche“ steht zweimal in derselben Norm, in
   * der ersten und in der letzten Nummer. Die Nummer 2, die die Kette frei macht, bleibt hier
   * unbesetzt — die Einfügung, die sie im echten Fall füllt, gehört nicht zur Ordnungsfrage.
   */
  private static Gesetz kaskadenGesetz() {
    return new Gesetz(
        "TestG",
        "Gesetz zur Erprobung",
        "Testgesetz",
        List.of(
            new Norm(
                "§ 1",
                "Verfahren",
                null,
                List.of(
                    new Absatz(
                        "1",
                        "Die Erprobung umfasst\n"
                            + "  1. die schriftliche Anmeldung,\n"
                            + "  2. die Prüfung und\n"
                            + "  3. die schriftliche Bestätigung.")),
                false)));
  }

  /**
   * „Die bisherigen §§ 2 bis 4 werden die §§ 2 und 3.“ — der Bereich nennt drei Bezeichnungen, das
   * Gesetz trägt darin aber nur zwei Einheiten: § 3 ist aufgehoben und zählt nicht mit. Erst am
   * Gesetz steht die Zuordnung fest.
   */
  @Test
  void bereichsUmnummerierungUeberspringtAufgehobeneEinheiten() {
    var gesetz =
        new Gesetz(
            "TestG",
            "Gesetz zur Erprobung",
            "Testgesetz",
            List.of(
                new Norm("§ 1", "Zweck", null, List.of(new Absatz(null, "Erprobung.")), false),
                new Norm(
                    "§ 2", "Mittel", null, List.of(new Absatz(null, "Mittel sind frei.")), false),
                new Norm("§ 3", "(aufgehoben)", null, List.of(), true),
                new Norm(
                    "§ 4", "Schluss", null, List.of(new Absatz(null, "Tritt in Kraft.")), false)),
            List.of());

    var befehl =
        new BereichsUmnummerierung(
            stelle(new Stelle.Paragraph("2")),
            stelle(new Stelle.Paragraph("4")),
            stelle(new Stelle.Paragraph("2")),
            stelle(new Stelle.Paragraph("3")),
            PROV);
    var ergebnis = BefehlAnwender.anwenden(gesetz, List.of(befehl));

    assertThat(ergebnis.anzahlManuell()).isZero();
    assertThat(ergebnis.neu().normen())
        .extracting(Norm::enbez)
        .containsExactly("§ 1", "§ 2", "§ 3");
    assertThat(ergebnis.neu().norm("§ 3").orElseThrow().titel()).isEqualTo("Schluss");
  }

  /**
   * Trägt der Bereich mehr Einheiten, als der Befehl neue Bezeichnungen nennt, so ist er nicht
   * auflösbar — dann wird nicht geraten, sondern gemeldet.
   */
  @Test
  void unaufloesbarerBereichBleibtManuell() {
    var befehl =
        new BereichsUmnummerierung(
            stelle(new Stelle.Paragraph("1")),
            stelle(new Stelle.Paragraph("3")),
            stelle(new Stelle.Paragraph("1")),
            stelle(new Stelle.Paragraph("1")),
            PROV);
    var ergebnis = BefehlAnwender.anwenden(gesetz(), List.of(befehl));
    assertThat(ergebnis.anzahlManuell()).isEqualTo(1);
    assertThat(ergebnis.protokoll().get(0).begruendung()).contains("nicht so viele Einheiten");
  }
}
