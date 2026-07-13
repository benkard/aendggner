package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BefehlErkennerTest {

  private static final Provenienz PROV = new Provenienz("1", "1.", "(Test)");

  /** Erkennt einen Befehl aus echtem Gesetzestext (mit „…“-Zitaten). */
  private static Optional<Aenderungsbefehl> erkenne(String befehlsSatz, Stelle kontext) {
    var zitate = ZitatExtraktor.extrahiere(befehlsSatz);
    var text = zitate.text().replaceAll("\\s+", " ").strip();
    return BefehlErkenner.erkenne(text, kontext, zitate, PROV);
  }

  @Test
  void erkenntErsetzung() {
    var befehl =
        erkenne(
            "In § 20 Absatz 6 Satz 1 werden die Wörter „der Bevölkerung“ durch die Wörter"
                + " „der Bevölkerung oder von Bevölkerungsteilen“ ersetzt.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    var ersetzung = (Ersetzung) befehl.orElseThrow();
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("§ 20 Absatz 6 Satz 1");
    assertThat(ersetzung.alt()).isEqualTo("der Bevölkerung");
    assertThat(ersetzung.neu()).isEqualTo("der Bevölkerung oder von Bevölkerungsteilen");
    assertThat(ersetzung.jeweils()).isFalse();
  }

  @Test
  void erkenntJeweilsErsetzung() {
    var befehl =
        erkenne(
            "In § 5 werden jeweils die Wörter „epidemischen Lage“ durch die Wörter"
                + " „epidemischen Lage von nationaler Tragweite“ ersetzt.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    assertThat(((Ersetzung) befehl.orElseThrow()).jeweils()).isTrue();
  }

  @Test
  void erkenntSatzzeichenErsetzung() {
    var befehl =
        erkenne(
            "In Nummer 16 wird der Punkt am Ende durch ein Komma ersetzt.",
            new Stelle(List.of(new Stelle.Paragraph("2"))));

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    var ersetzung = (Ersetzung) befehl.orElseThrow();
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("§ 2 Nummer 16");
    assertThat(ersetzung.alt()).isEqualTo(".");
    assertThat(ersetzung.neu()).isEqualTo(",");
    assertThat(ersetzung.amEnde()).isTrue();
  }

  @Test
  void erkenntNeufassung() {
    var befehl =
        erkenne(
            "§ 5 Absatz 1 wird wie folgt gefasst: „(1) Der Deutsche Bundestag kann eine"
                + " epidemische Lage von nationaler Tragweite feststellen.“",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Neufassung.class);
    var neufassung = (Neufassung) befehl.orElseThrow();
    assertThat(neufassung.stelle().anzeigeText()).isEqualTo("§ 5 Absatz 1");
    assertThat(neufassung.neuerText()).startsWith("(1) Der Deutsche Bundestag");
  }

  @Test
  void erkenntParagraphEinfuegung() {
    // Echter Befehl aus Art. 1 Nr. 17 des Dritten Bevölkerungsschutzgesetzes.
    var befehl =
        erkenne(
            "Nach § 28 wird folgender § 28a eingefügt: „§ 28a Besondere Schutzmaßnahmen zur"
                + " Verhinderung der Verbreitung der Coronavirus-Krankheit-2019 (COVID-19)"
                + " (1) Notwendige Schutzmaßnahmen im Sinne des § 28 Absatz 1 Satz 1 und 2"
                + " können … sein.“",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.stelle().anzeigeText()).isEqualTo("§ 28");
    assertThat(einfuegung.vorher()).isFalse();
    assertThat(einfuegung.ebene()).isEqualTo(Ebene.PARAGRAPH);
    assertThat(einfuegung.bezeichnung()).isEqualTo("28a");
    assertThat(einfuegung.text()).startsWith("§ 28a Besondere Schutzmaßnahmen");
  }

  @Test
  void erkenntSatzEinfuegung() {
    var befehl =
        erkenne(
            "Nach Satz 1 wird folgender Satz eingefügt: „Das Robert Koch-Institut ist der"
                + " Verantwortliche im Sinne des Datenschutzrechts.“",
            new Stelle(List.of(new Stelle.Paragraph("14"), new Stelle.AbsatzNr("1"))));

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.stelle().anzeigeText()).isEqualTo("§ 14 Absatz 1 Satz 1");
    assertThat(einfuegung.ebene()).isEqualTo(Ebene.SATZ);
    assertThat(einfuegung.bezeichnung()).isNull();
  }

  @Test
  void erkenntWoerterEinfuegung() {
    var befehl =
        erkenne(
            "In Nummer 3 werden nach dem Wort „Kontaktdaten“ die Wörter „sowie die lebenslange"
                + " Arztnummer (LANR)“ eingefügt.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(WoerterEinfuegung.class);
    var einfuegung = (WoerterEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.anker()).isEqualTo(new WortAnker.NachWoertern("Kontaktdaten"));
    assertThat(einfuegung.woerter()).startsWith("sowie die lebenslange");
  }

  @Test
  void erkenntWoerterEinfuegungVorKommaAmEnde() {
    var befehl =
        erkenne(
            "In Nummer 2 werden vor dem Komma am Ende die Wörter „sowie Zahnärzte und"
                + " Tierärzte“ eingefügt.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(WoerterEinfuegung.class);
    assertThat(((WoerterEinfuegung) befehl.orElseThrow()).anker())
        .isEqualTo(new WortAnker.VorKommaAmEnde());
  }

  @Test
  void erkenntAnfuegung() {
    var befehl =
        erkenne(
            "Folgender Absatz 8 wird angefügt: „(8) Aufgrund einer epidemischen"
                + " Lage von nationaler Tragweite kann das Bundesministerium für Gesundheit Hilfe"
                + " leisten.“",
            new Stelle(List.of(new Stelle.Paragraph("5"))));

    assertThat(befehl).containsInstanceOf(Anfuegung.class);
    var anfuegung = (Anfuegung) befehl.orElseThrow();
    assertThat(anfuegung.stelle().anzeigeText()).isEqualTo("§ 5");
    assertThat(anfuegung.ebene()).isEqualTo(Ebene.ABSATZ);
    assertThat(anfuegung.bezeichnung()).isEqualTo("8");
  }

  @Test
  void erkenntSatzAnfuegungMitDativStelle() {
    var befehl =
        erkenne(
            "Dem Absatz 6 wird folgender Satz angefügt: „Die Kontrolle obliegt dem"
                + " Bundesbeauftragten.“",
            new Stelle(List.of(new Stelle.Paragraph("14"))));

    assertThat(befehl).containsInstanceOf(Anfuegung.class);
    var anfuegung = (Anfuegung) befehl.orElseThrow();
    assertThat(anfuegung.stelle().anzeigeText()).isEqualTo("§ 14 Absatz 6");
    assertThat(anfuegung.ebene()).isEqualTo(Ebene.SATZ);
  }

  @Test
  void erkenntAufhebungMitKontext() {
    var kontext = new Stelle(List.of(new Stelle.Paragraph("56")));
    var befehl = erkenne("Absatz 3 wird aufgehoben.", kontext);

    assertThat(befehl).containsInstanceOf(Aufhebung.class);
    assertThat(befehl.orElseThrow().stelle().anzeigeText()).isEqualTo("§ 56 Absatz 3");
  }

  @Test
  void erkenntStreichung() {
    var befehl =
        erkenne(
            "In Satz 2 werden die Wörter „bis zum Ende“ gestrichen.",
            new Stelle(List.of(new Stelle.Paragraph("5"), new Stelle.AbsatzNr("1"))));

    assertThat(befehl).containsInstanceOf(Streichung.class);
    var streichung = (Streichung) befehl.orElseThrow();
    assertThat(streichung.stelle().anzeigeText()).isEqualTo("§ 5 Absatz 1 Satz 2");
    assertThat(streichung.woerter()).isEqualTo("bis zum Ende");
  }

  @Test
  void erkenntUmnummerierung() {
    var befehl =
        erkenne("Absatz 4 wird Absatz 3.", new Stelle(List.of(new Stelle.Paragraph("10"))));

    assertThat(befehl).containsInstanceOf(Umnummerierung.class);
    var umnummerierung = (Umnummerierung) befehl.orElseThrow();
    assertThat(umnummerierung.stelle().anzeigeText()).isEqualTo("§ 10 Absatz 4");
    assertThat(umnummerierung.neu().anzeigeText()).isEqualTo("§ 10 Absatz 3");
  }

  @Test
  void erkenntInhaltsuebersichtEinfuegung() {
    var befehl =
        erkenne(
            "In der Inhaltsübersicht wird nach der Angabe zu § 28 folgende Angabe eingefügt:"
                + " „§ 28a Besondere Schutzmaßnahmen“.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(WoerterEinfuegung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftInhaltsuebersicht()).isTrue();
  }

  @Test
  void erkenntUeberschriftNeufassung() {
    var befehl =
        erkenne(
            "Die Überschrift wird wie folgt gefasst: „§ 5 Epidemische Lage“.",
            new Stelle(List.of(new Stelle.Paragraph("5"))));

    assertThat(befehl).containsInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftUeberschrift()).isTrue();
  }

  @Test
  void erkenntKontextRahmen() {
    assertThat(BefehlErkenner.kontextRahmen("§ 2 wird wie folgt geändert:"))
        .contains(new Stelle(List.of(new Stelle.Paragraph("2"))));
    assertThat(BefehlErkenner.kontextRahmen("Absatz 1 wird wie folgt geändert:"))
        .contains(new Stelle(List.of(new Stelle.AbsatzNr("1"))));
    assertThat(BefehlErkenner.kontextRahmen("§ 8 Absatz 1 wird wie folgt geändert:"))
        .contains(new Stelle(List.of(new Stelle.Paragraph("8"), new Stelle.AbsatzNr("1"))));
    assertThat(BefehlErkenner.kontextRahmen("Absatz 3 wird aufgehoben.")).isEmpty();
  }

  @Test
  void erkenntStrukturErsetzungEinesAbsatzes() {
    // Neues BGBl-Format (3. UWGÄndG 2026, Artikel 1 Nummer 1).
    var befehl =
        erkenne(
            "§ 2 Absatz 2 wird durch die folgenden Absätze 2 und 3 ersetzt: „(2) Im Sinne"
                + " dieses Gesetzes ist … (3) Weiteres.“",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Aenderungsbefehl.StrukturErsetzung.class);
    var ersetzung = (Aenderungsbefehl.StrukturErsetzung) befehl.orElseThrow();
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("§ 2 Absatz 2");
    assertThat(ersetzung.ebene()).isEqualTo(Ebene.ABSATZ);
    assertThat(ersetzung.text()).startsWith("(2) Im Sinne");
  }

  @Test
  void erkenntStrukturErsetzungEinesSatzes() {
    var befehl =
        erkenne(
            "Satz 3 wird durch die folgenden Sätze ersetzt: „Erster neuer Satz. Zweiter"
                + " neuer Satz.“",
            new Stelle(List.of(new Stelle.Paragraph("13"), new Stelle.AbsatzNr("3"))));

    assertThat(befehl).containsInstanceOf(Aenderungsbefehl.StrukturErsetzung.class);
    var ersetzung = (Aenderungsbefehl.StrukturErsetzung) befehl.orElseThrow();
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("§ 13 Absatz 3 Satz 3");
    assertThat(ersetzung.ebene()).isEqualTo(Ebene.SATZ);
  }

  @Test
  void erkenntUeberschriftErsetzungAlsNeufassung() {
    // AGG-Regierungsentwurf 2026, Artikel 1 Nummer 4 Buchstabe a.
    var befehl =
        erkenne(
            "Die Überschrift wird durch die folgende Überschrift ersetzt: „§ 10 Zulässige"
                + " unterschiedliche Behandlung wegen des Lebensalters“.",
            new Stelle(List.of(new Stelle.Paragraph("10"))));

    assertThat(befehl).containsInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftUeberschrift()).isTrue();
  }

  @Test
  void erkenntParagraphErsetzungAlsNeufassung() {
    // ProdHaftG-Regierungsentwurf 2025, Artikel 2.
    var befehl =
        erkenne(
            "§ 19 wird durch den folgenden § 19 ersetzt: „§ 19 Außerkrafttreten Dieses"
                + " Gesetz tritt am 9. Dezember 2026 außer Kraft.“",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().anzeigeText()).isEqualTo("§ 19");
  }

  @Test
  void erkenntPluralEinfuegung() {
    // AGG-Regierungsentwurf 2026: „die folgenden Absätze 6 und 7“.
    var befehl =
        erkenne(
            "Nach Absatz 5 werden die folgenden Absätze 6 und 7 eingefügt: „(6) Neu."
                + " (7) Auch neu.“",
            new Stelle(List.of(new Stelle.Paragraph("27"))));

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.ebene()).isEqualTo(Ebene.ABSATZ);
    assertThat(einfuegung.bezeichnung()).isNull();
  }

  @Test
  void erkenntSatzzeichenErsetzungMitWoertern() {
    // GEG-Novelle 2023: „durch ein Komma und die Wörter … ersetzt“.
    var befehl =
        erkenne(
            "In Satz 2 wird der Punkt am Ende durch ein Komma und die Wörter „sowie neue"
                + " Anforderungen“ ersetzt.",
            new Stelle(List.of(new Stelle.Paragraph("47"), new Stelle.AbsatzNr("1"))));

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    var ersetzung = (Ersetzung) befehl.orElseThrow();
    assertThat(ersetzung.alt()).isEqualTo(".");
    assertThat(ersetzung.neu()).isEqualTo(", sowie neue Anforderungen");
    assertThat(ersetzung.amEnde()).isTrue();
  }

  @Test
  void erkenntAngabeEinfuegungImInhaltsuebersichtsKontext() {
    // GEG-Novelle 2023: verschachtelt unter „Die Inhaltsübersicht wird wie folgt geändert:“.
    var kontext = new Stelle(List.of(new Stelle.Inhaltsuebersicht()));
    var befehl =
        erkenne(
            "Nach der Angabe zu § 9 wird folgende Angabe eingefügt: „§ 9a" + " Länderregelung“.",
            kontext);

    assertThat(befehl).containsInstanceOf(WoerterEinfuegung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftInhaltsuebersicht()).isTrue();
  }

  @Test
  void faelltBeiBereichsbefehlenAufUnbekanntZurueck() {
    // Bereichs- und Mehrfachbefehle sind in v1 bewusst nicht unterstützt.
    assertThat(
            erkenne(
                "Die Absätze 2 bis 4 werden durch die folgenden Absätze 2 bis 6 ersetzt:"
                    + " „(2) Text.“",
                Stelle.LEER))
        .isEmpty();
    assertThat(erkenne("Die Nummern 1 bis 3 werden aufgehoben.", Stelle.LEER)).isEmpty();
    assertThat(
            erkenne(
                "In Absatz 1 Satz 1 und 2 werden die Wörter „alt“ durch die Wörter „neu“"
                    + " ersetzt.",
                Stelle.LEER))
        .isEmpty();
  }
}
