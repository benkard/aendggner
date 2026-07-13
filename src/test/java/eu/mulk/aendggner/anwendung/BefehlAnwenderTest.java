package eu.mulk.aendggner.anwendung;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
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

  private static String absatzText(Gesetz gesetz, String enbez, int index) {
    return gesetz.norm(enbez).orElseThrow().absaetze().get(index).text();
  }
}
