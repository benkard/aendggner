package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Sammelbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturErsetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuAbsatz;
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
  void erkenntNeufassungMitErhaeltFolgendeFassung() {
    // „… erhält folgende Fassung:“ ist die Neufassungsform in Schleswig-Holstein und Niedersachsen.
    var befehl =
        erkenne(
            "§ 34a Absatz 1 erhält folgende Fassung: „(1) Durch Hauptsatzung kann bestimmt"
                + " werden, dass Gemeindevertreterinnen und Gemeindevertreter teilnehmen.“",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Neufassung.class);
    var neufassung = (Neufassung) befehl.orElseThrow();
    assertThat(neufassung.stelle().anzeigeText()).isEqualTo("§ 34a Absatz 1");
    assertThat(neufassung.neuerText()).startsWith("(1) Durch Hauptsatzung");
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
  void erkenntGliederungsbezogeneParagraphEinfuegung() {
    // Echter Befehl aus Art. 1 Nr. 5 des Änderungsgesetzes zum NEFG (Nds. GVBl. 2026 Nr. 10). Die
    // Kapitelangabe nennt nur den Abschnitt, in dem der neue Paragraph landet; maßgeblich für die
    // Position ist der Anker „nach § 12“.
    var befehl =
        erkenne(
            "In Kapitel 4 wird nach § 12 der folgende neue § 13 angefügt: „§ 13 Entbehrlichkeit"
                + " von Vergabeverfahren im Unterschwellenbereich Das Gesetz ist nicht"
                + " anzuwenden.“",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.stelle().anzeigeText()).isEqualTo("§ 12");
    assertThat(einfuegung.vorher()).isFalse();
    assertThat(einfuegung.ebene()).isEqualTo(Ebene.PARAGRAPH);
    assertThat(einfuegung.bezeichnung()).isEqualTo("13");
    assertThat(einfuegung.text()).startsWith("§ 13 Entbehrlichkeit");
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

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.stelle().betrifftInhaltsuebersicht()).isTrue();
    assertThat(einfuegung.stelle().anzeigeText()).isEqualTo("Inhaltsübersicht § 28");
    assertThat(einfuegung.vorher()).isFalse();
    assertThat(einfuegung.text()).isEqualTo("§ 28a Besondere Schutzmaßnahmen");
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

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.stelle().betrifftInhaltsuebersicht()).isTrue();
    assertThat(einfuegung.stelle().anzeigeText()).isEqualTo("Inhaltsübersicht § 9");
    assertThat(einfuegung.text()).isEqualTo("§ 9a Länderregelung");
  }

  @Test
  void erkenntMehrfachzielStreichung() {
    var befehl =
        erkenne(
            "In § 3 Absatz 1 Satz 2 und Absatz 4 wird jeweils die Angabe „in Bezug auf § 2 Abs."
                + " 1 Nr. 1 bis 4“ gestrichen.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof Streichung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("§ 3 Absatz 1 Satz 2", "§ 3 Absatz 4");
    assertThat(((Streichung) teile.get(0)).woerter()).isEqualTo("in Bezug auf § 2 Abs. 1 Nr. 1 bis 4");
  }

  @Test
  void erkenntMehrfachzielErsetzung() {
    var befehl =
        erkenne(
            "In § 20 Absatz 1 Satz 1 und Absatz 2 Satz 2 wird jeweils die Angabe „Alters“ durch"
                + " die Angabe „Lebensalters“ ersetzt.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof Ersetzung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("§ 20 Absatz 1 Satz 1", "§ 20 Absatz 2 Satz 2");
    assertThat(((Ersetzung) teile.get(0)).neu()).isEqualTo("Lebensalters");
  }

  @Test
  void erkenntMehrfachzielEinfuegung() {
    var befehl =
        erkenne(
            "In § 30 Absatz 2 Satz 1 und Absatz 3 wird jeweils vor der Angabe „Familie“ die"
                + " Angabe „Bildung,“ eingefügt.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof WoerterEinfuegung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("§ 30 Absatz 2 Satz 1", "§ 30 Absatz 3");
  }

  @Test
  void erkenntBereichsUmnummerierungAbsteigend() {
    var befehl =
        erkenne(
            "Die bisherigen Absätze 2 bis 4 werden zu den Absätzen 3 bis 5.",
            new Stelle(List.of(new Stelle.Paragraph("5"))));

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(3).allMatch(t -> t instanceof Umnummerierung);
    // Absteigend, damit die Anwendung keine Labels kollidieren lässt: 4→5, 3→4, 2→3.
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("§ 5 Absatz 4", "§ 5 Absatz 3", "§ 5 Absatz 2");
    assertThat(teile).extracting(t -> ((Umnummerierung) t).neu().anzeigeText())
        .containsExactly("§ 5 Absatz 5", "§ 5 Absatz 4", "§ 5 Absatz 3");
  }

  @Test
  void verbundZweierBefehleWirdSammelbefehl() {
    // „… wird zu Absatz 2 und nach Satz 2 werden … eingefügt“ verbindet zwei verschiedene
    // Befehle per „und“ → Sammelbefehl aus Umnummerierung und Struktureinfügung.
    var befehl =
        erkenne(
            "Der bisherige Absatz 1 wird zu Absatz 2 und nach Satz 2 werden die folgenden"
                + " Sätze eingefügt: „Ein Satz.“",
            Stelle.LEER);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2);
    assertThat(teile.get(0)).isInstanceOf(Umnummerierung.class);
    assertThat(teile.get(1)).isInstanceOf(StrukturEinfuegung.class);
  }

  @Test
  void koordinierteStelleErsetzungWirdSammelbefehl() {
    // „In Absatz 1 Satz 1 und 2 …“ — koordinierte Stelle (das „2“ erbt „Satz“).
    var befehl =
        erkenne(
            "In Absatz 1 Satz 1 und 2 werden die Wörter „alt“ durch die Wörter „neu“ ersetzt.",
            Stelle.LEER);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof Ersetzung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("Absatz 1 Satz 1", "Absatz 1 Satz 2");
  }

  @Test
  void bereichsErsetzungAbsaetzeWirdStrukturErsetzung() {
    // „bis“-Bereich über Absätze: das erste und letzte Ziel spannen den zu ersetzenden Bereich auf.
    var befehl =
        erkenne(
            "Die Absätze 2 bis 4 werden durch die folgenden Absätze 2 bis 6 ersetzt:"
                + " „(2) Text.“",
            new Stelle(List.of(new Stelle.Paragraph("5"))));
    assertThat(befehl).containsInstanceOf(Aenderungsbefehl.StrukturErsetzung.class);
    var e = (Aenderungsbefehl.StrukturErsetzung) befehl.orElseThrow();
    assertThat(e.ebene()).isEqualTo(Ebene.ABSATZ);
    assertThat(e.stelle().anzeigeText()).isEqualTo("§ 5 Absatz 2");
    assertThat(e.bisStelle().anzeigeText()).isEqualTo("§ 5 Absatz 4");
  }

  @Test
  void koordinierteAbsatzErsetzungWirdBereich() {
    // IfSG: „Die Absätze 8 und 9 werden durch die folgenden Absätze 8 bis 10 ersetzt: „…““
    var befehl =
        erkenne(
            "Die Absätze 8 und 9 werden durch die folgenden Absätze 8 bis 10 ersetzt:"
                + " „(8) Erstes. (9) Zweites. (10) Drittes.“",
            new Stelle(List.of(new Stelle.Paragraph("14"))));
    assertThat(befehl).containsInstanceOf(Aenderungsbefehl.StrukturErsetzung.class);
    var e = (Aenderungsbefehl.StrukturErsetzung) befehl.orElseThrow();
    assertThat(e.stelle().anzeigeText()).isEqualTo("§ 14 Absatz 8");
    assertThat(e.bisStelle().anzeigeText()).isEqualTo("§ 14 Absatz 9");
  }

  @Test
  void mehrSatzNeufassungWirdStrukturErsetzung() {
    // IfSG: „Die bisherigen Sätze 4 und 5 werden wie folgt gefasst: „…““
    var befehl =
        erkenne(
            "Die bisherigen Sätze 4 und 5 werden wie folgt gefasst: „Erster neuer Satz."
                + " Zweiter neuer Satz.“",
            new Stelle(List.of(new Stelle.Paragraph("14"), new Stelle.AbsatzNr("2"))));
    assertThat(befehl).containsInstanceOf(Aenderungsbefehl.StrukturErsetzung.class);
    var e = (Aenderungsbefehl.StrukturErsetzung) befehl.orElseThrow();
    assertThat(e.ebene()).isEqualTo(Ebene.SATZ);
    assertThat(e.stelle().anzeigeText()).isEqualTo("§ 14 Absatz 2 Satz 4");
    assertThat(e.bisStelle().anzeigeText()).isEqualTo("§ 14 Absatz 2 Satz 5");
  }

  @Test
  void bereichsAufhebungWirdSammelbefehl() {
    var teile =
        ((Sammelbefehl) erkenne("Die Nummern 1 bis 3 werden aufgehoben.", Stelle.LEER).orElseThrow())
            .teilbefehle();
    assertThat(teile).hasSize(3).allMatch(t -> t instanceof Aufhebung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("Nummer 1", "Nummer 2", "Nummer 3");
  }

  @Test
  void koordinierteAufhebungWirdSammelbefehl() {
    var teile =
        ((Sammelbefehl) erkenne("Die Absätze 4 und 5 werden aufgehoben.", Stelle.LEER).orElseThrow())
            .teilbefehle();
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("Absatz 4", "Absatz 5");
  }

  @Test
  void bereichsUmnummerierungNummernOhneZuDen() {
    var teile =
        ((Sammelbefehl)
                erkenne("Die bisherigen Nummern 4 bis 6 werden die Nummern 8 bis 10.", Stelle.LEER)
                    .orElseThrow())
            .teilbefehle();
    assertThat(teile).hasSize(3).allMatch(t -> t instanceof Umnummerierung);
    // Absteigend: 6→10, 5→9, 4→8.
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("Nummer 6", "Nummer 5", "Nummer 4");
    assertThat(teile).extracting(t -> ((Umnummerierung) t).neu().anzeigeText())
        .containsExactly("Nummer 10", "Nummer 9", "Nummer 8");
  }

  @Test
  void paragraphBereichNeufassungWirdSammelbefehl() {
    var teile =
        ((Sammelbefehl)
                erkenne(
                        "Die §§ 52 bis 56 werden wie folgt gefasst: „§ 52 (weggefallen) § 53"
                            + " (weggefallen) § 54 (weggefallen)“.",
                        Stelle.LEER)
                    .orElseThrow())
            .teilbefehle();
    assertThat(teile).hasSize(3).allMatch(t -> t instanceof Neufassung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("§ 52", "§ 53", "§ 54");
  }

  @Test
  void wortlautWirdAbsatz() {
    var befehl =
        erkenne("Der Wortlaut wird Absatz 1.", new Stelle(List.of(new Stelle.Paragraph("5"))));
    assertThat(befehl).get().isInstanceOf(WortlautZuAbsatz.class);
    assertThat(((WortlautZuAbsatz) befehl.orElseThrow()).nummer()).isEqualTo("1");
  }

  @Test
  void wortDurchSatzzeichen() {
    var befehl =
        erkenne("In Nummer 7 wird das Wort „oder“ am Ende durch ein Komma ersetzt.", Stelle.LEER);
    var e = (Ersetzung) befehl.orElseThrow();
    assertThat(e.alt()).isEqualTo("oder");
    assertThat(e.neu()).isEqualTo(",");
    assertThat(e.amEnde()).isTrue();
  }

  @Test
  void gliederungsUeberschriftNeufassung() {
    var befehl =
        erkenne(
            "Die Überschrift von Teil 3 wird wie folgt gefasst: „Teil 3 Anforderungen an"
                + " bestehende Gebäude“.",
            Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftGliederung()).isTrue();
  }

  @Test
  void gliederungsUeberschriftStreichung() {
    var befehl = erkenne("Die Überschrift von Teil 2 Abschnitt 4 wird gestrichen.", Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Aufhebung.class);
    assertThat(befehl.orElseThrow().stelle().gliederungsPfad()).hasSize(2);
  }

  @Test
  void absatzbezeichnungStreichung() {
    var befehl =
        erkenne(
            "Die Absatzbezeichnung „(2)“ wird gestrichen.",
            new Stelle(List.of(new Stelle.Paragraph("64"))));
    var b = befehl.orElseThrow();
    assertThat(b).isInstanceOf(Aufhebung.class);
    assertThat(b.stelle().absatzbezeichnung()).get().extracting(Stelle.Absatzbezeichnung::nummer)
        .isEqualTo("2");
  }

  @Test
  void inhaltsuebersichtAngabeWirdTypisiert() {
    var befehl = erkenne("Die Angabe zu Teil 3 wird wie folgt gefasst: „Teil 3 Neu“.", Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftInhaltsuebersicht()).isTrue();
  }

  @Test
  void strukturStreichungGanzerEinheit() {
    // „§ 9 wird gestrichen.“ — Streichung einer ganzen Einheit ist semantisch eine Aufhebung.
    var befehl = erkenne("§ 9 wird gestrichen.", Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Aufhebung.class);
    assertThat(befehl.orElseThrow().stelle().anzeigeText()).isEqualTo("§ 9");
  }

  @Test
  void strukturStreichungMitKontext() {
    var befehl =
        erkenne("Absatz 3 wird gestrichen.", new Stelle(List.of(new Stelle.Paragraph("102"))));
    assertThat(befehl).get().isInstanceOf(Aufhebung.class);
    assertThat(befehl.orElseThrow().stelle().anzeigeText()).isEqualTo("§ 102 Absatz 3");
  }

  @Test
  void strukturStreichungBereichWirdSammelbefehl() {
    var teile =
        ((Sammelbefehl) erkenne("Die §§ 34 bis 39 werden gestrichen.", Stelle.LEER).orElseThrow())
            .teilbefehle();
    assertThat(teile).hasSize(6).allMatch(t -> t instanceof Aufhebung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("§ 34", "§ 35", "§ 36", "§ 37", "§ 38", "§ 39");
  }

  @Test
  void strukturStreichungGliederung() {
    var befehl = erkenne("Der bisherige Teil 3 wird gestrichen.", Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Aufhebung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftGliederung()).isTrue();
  }

  @Test
  void chapeauLokatorBeziehtSichAufKontext() {
    // „In der Angabe vor Nummer 1 …“ / „Im Satzteil vor Nummer 1 …“ tragen keine eigene Stelle;
    // die Operation bezieht sich auf die Kontextstelle.
    var kontext = new Stelle(List.of(new Stelle.Paragraph("48"), new Stelle.AbsatzNr("1")));
    var befehl =
        erkenne(
            "In der Angabe vor Nummer 1 wird die Angabe „2025“ durch die Angabe „2030“ ersetzt.",
            kontext);
    assertThat(befehl).get().isInstanceOf(Ersetzung.class);
    var e = (Ersetzung) befehl.orElseThrow();
    assertThat(e.stelle().anzeigeText()).isEqualTo("§ 48 Absatz 1");
    assertThat(e.alt()).isEqualTo("2025");
    assertThat(e.neu()).isEqualTo("2030");
  }

  @Test
  void bereichsUmnummerierungOhneBisherigen() {
    var teile =
        ((Sammelbefehl)
                erkenne(
                        "Die Absätze 4 bis 7 werden zu den Absätzen 3 bis 6.",
                        new Stelle(List.of(new Stelle.Paragraph("108"))))
                    .orElseThrow())
            .teilbefehle();
    assertThat(teile).hasSize(4).allMatch(t -> t instanceof Umnummerierung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText())
        .containsExactly("§ 108 Absatz 7", "§ 108 Absatz 6", "§ 108 Absatz 5", "§ 108 Absatz 4");
  }

  @Test
  void strukturErsetzungMitEnumeratorPraefix() {
    // Entwurfs-/Drucksachenform: das Aufzählungslabel steht außerhalb des Zitats.
    var befehl =
        erkenne(
            "Nummer 3 wird durch die folgende Nummer 3 ersetzt: 3. „ die Maßgaben der §§ 42 bis 45"
                + " entsprechend eingehalten werden.“",
            new Stelle(List.of(new Stelle.Paragraph("10"), new Stelle.AbsatzNr("2"))));
    assertThat(befehl).containsInstanceOf(Aenderungsbefehl.StrukturErsetzung.class);
    var e = (Aenderungsbefehl.StrukturErsetzung) befehl.orElseThrow();
    assertThat(e.ebene()).isEqualTo(Ebene.NUMMER);
    assertThat(e.stelle().anzeigeText()).isEqualTo("§ 10 Absatz 2 Nummer 3");
    // Das Label „3.“ wird dem Ersatztext wieder vorangestellt.
    assertThat(e.text()).startsWith("3. die Maßgaben");
  }

  @Test
  void paragraphUmnummerierung() {
    var befehl = erkenne("§ 9a wird zu § 9.", Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Umnummerierung.class);
    var u = (Umnummerierung) befehl.orElseThrow();
    assertThat(u.stelle().anzeigeText()).isEqualTo("§ 9a");
    assertThat(u.neu().anzeigeText()).isEqualTo("§ 9");
  }

  @Test
  void koordinierteParagraphUmnummerierung() {
    var teile =
        ((Sammelbefehl) erkenne("Die §§ 46 und 47 werden zu den §§ 34 und 35.", Stelle.LEER).orElseThrow())
            .teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof Umnummerierung);
    assertThat(teile).extracting(t -> t.stelle().anzeigeText()).containsExactly("§ 46", "§ 47");
    assertThat(teile).extracting(t -> ((Umnummerierung) t).neu().anzeigeText())
        .containsExactly("§ 34", "§ 35");
  }

  @Test
  void gliederungsUmnummerierung() {
    var befehl = erkenne("Der bisherige Abschnitt 2 wird zu Abschnitt 3.", Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Umnummerierung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftGliederung()).isTrue();
  }

  @Test
  void paragraphenBlockEinfuegung() {
    var befehl =
        erkenne(
            "Nach § 60a werden die folgenden §§ 60b und 60c eingefügt: „§ 60b Prüfung (1) Text."
                + " § 60c Optimierung (1) Mehr.“",
            Stelle.LEER);
    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var e = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(e.ebene()).isEqualTo(Ebene.PARAGRAPH);
    assertThat(e.bezeichnung()).isNull();
    assertThat(e.stelle().anzeigeText()).isEqualTo("§ 60a");
  }

  @Test
  void paragraphBlockErsetzung() {
    var befehl =
        erkenne(
            "Die §§ 42 bis 45 werden durch die folgenden §§ 42 bis 45 ersetzt: „§ 42 Grundsatz"
                + " (1) Text.“",
            Stelle.LEER);
    assertThat(befehl).containsInstanceOf(Aenderungsbefehl.StrukturErsetzung.class);
    var e = (Aenderungsbefehl.StrukturErsetzung) befehl.orElseThrow();
    assertThat(e.ebene()).isEqualTo(Ebene.PARAGRAPH);
    assertThat(e.stelle().anzeigeText()).isEqualTo("§ 42");
    assertThat(e.bisStelle().anzeigeText()).isEqualTo("§ 45");
  }

  @Test
  void ueberschriftErsetzungMitStelle() {
    var befehl =
        erkenne(
            "In Anlage 7 wird die Überschrift durch die folgende Überschrift ersetzt: „Anlage 7"
                + " (zu § 36) Höchstwerte“.",
            Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftGliederung()).isTrue();
  }

  @Test
  void inhaltsuebersichtErsetzung() {
    var befehl =
        erkenne(
            "Die Inhaltsübersicht wird durch die folgende Inhaltsübersicht ersetzt: „Inhaltsübersicht"
                + " § 1 Zweck § 2 Begriffe“.",
            Stelle.LEER);
    assertThat(befehl).get().isInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().betrifftInhaltsuebersicht()).isTrue();
  }

  @Test
  void mehrfachErsetzungWirdSammelbefehl() {
    // Mehrere Ersetzungspaare unter einem gemeinsamen „ersetzt“.
    var befehl =
        erkenne(
            "In Satz 1 werden die Wörter „a“ durch die Wörter „b“ und die Angabe „c“ durch die"
                + " Wörter „d“ ersetzt.",
            Stelle.LEER);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof Ersetzung);
    assertThat(teile).extracting(t -> ((Ersetzung) t).alt()).containsExactly("a", "c");
    assertThat(teile).extracting(t -> ((Ersetzung) t).neu()).containsExactly("b", "d");
  }
  // --- Welle-4-Formen --------------------------------------------------------------------------

  @Test
  void erkenntAngabenBereichsErsetzungInDerInhaltsuebersicht() {
    var kontext = new Stelle(List.of(new Stelle.Inhaltsuebersicht()));
    var befehl =
        erkenne(
            "Die Angaben zu den §§ 34 bis § 45 werden durch die folgenden Angaben ersetzt:"
                + " „§ 34 (weggefallen) § 35 (weggefallen)“.",
            kontext);

    assertThat(befehl).containsInstanceOf(StrukturErsetzung.class);
    var ersetzung = (StrukturErsetzung) befehl.orElseThrow();
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("Inhaltsübersicht § 34");
    assertThat(ersetzung.bisStelle().anzeigeText()).isEqualTo("Inhaltsübersicht § 45");
  }

  @Test
  void erkenntAngabeStreichungInDerInhaltsuebersicht() {
    var befehl =
        erkenne("In der Inhaltsübersicht wird die Angabe zu § 5a gestrichen.", Stelle.LEER);

    assertThat(befehl).containsInstanceOf(Aufhebung.class);
    assertThat(befehl.orElseThrow().stelle().anzeigeText()).isEqualTo("Inhaltsübersicht § 5a");
  }

  @Test
  void erkenntAngabeMitOrdinalerGliederung() {
    var kontext = new Stelle(List.of(new Stelle.Inhaltsuebersicht()));
    var befehl =
        erkenne(
            "Die Angabe zum zweiten Abschnitt wird wie folgt gefasst: „2. Abschnitt"
                + " Koordinierung und epidemische Lage von nationaler Tragweite“.",
            kontext);

    assertThat(befehl).containsInstanceOf(Neufassung.class);
    assertThat(befehl.orElseThrow().stelle().anzeigeText()).isEqualTo("Inhaltsübersicht Abschnitt 2");
  }

  @Test
  void erkenntVoranstellung() {
    var befehl =
        erkenne(
            "Der Nummer 1 wird folgende Nummer 1 vorangestellt: „1. eine Umwälzpumpe nach § 64"
                + " Absatz 2 auszutauschen ist,“.",
            new Stelle(List.of(new Stelle.Paragraph("64"))));

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.vorher()).isTrue();
    assertThat(einfuegung.ebene()).isEqualTo(Ebene.NUMMER);
    assertThat(einfuegung.stelle().anzeigeText()).isEqualTo("§ 64 Nummer 1");
  }

  @Test
  void erkenntVoranstellungOhneAnker() {
    var befehl =
        erkenne(
            "Folgende Nummer 1 wird vorangestellt: „1. einer vollziehbaren Anordnung nach § 5"
                + " Absatz 2 Nummer 1 oder 2 zuwiderhandelt,“.",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    assertThat(((StrukturEinfuegung) befehl.orElseThrow()).vorher()).isTrue();
  }

  @Test
  void erkenntNummernBereichsErsetzungMitKardinalitaetswechsel() {
    var befehl =
        erkenne(
            "Satz 1 Nummer 3 bis 6 wird durch die folgenden Nummern 3 und 4 ersetzt: 3. „ bei"
                + " Wärmeverteilungs- und Warmwasserleitungen die Wärmeabgabe begrenzt ist und"
                + " 4. die Anforderungen eingehalten werden.“.",
            new Stelle(List.of(new Stelle.Paragraph("61"))));

    assertThat(befehl).containsInstanceOf(StrukturErsetzung.class);
    var ersetzung = (StrukturErsetzung) befehl.orElseThrow();
    assertThat(ersetzung.ebene()).isEqualTo(Ebene.NUMMER);
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("§ 61 Satz 1 Nummer 3");
    assertThat(ersetzung.bisStelle().anzeigeText()).isEqualTo("§ 61 Satz 1 Nummer 6");
    assertThat(ersetzung.text()).startsWith("3. ");
  }

  @Test
  void erkenntKommaMehrfachErsetzung() {
    var befehl =
        erkenne(
            "In Satz 1 wird die Angabe „2025“ durch die Angabe „2030“, die Angabe „§ 50 Absatz 1"
                + " in Verbindung mit § 48“ durch die Angabe „§ 38 Absatz 1 in Verbindung mit"
                + " § 36“ und die Angabe „§ 50 Absatz 1“ durch die Angabe „§ 38 Absatz 1“"
                + " ersetzt.",
            new Stelle(List.of(new Stelle.Paragraph("109"))));

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(3).allMatch(t -> t instanceof Ersetzung);
    assertThat(((Ersetzung) teile.get(0)).alt()).isEqualTo("2025");
    assertThat(((Ersetzung) teile.get(0)).neu()).isEqualTo("2030");
  }

  @Test
  void erkenntMehrfachEinfuegepaare() {
    var befehl =
        erkenne(
            "In Nummer 24 werden nach den Wörtern „einer Rechtsverordnung nach“ die Wörter"
                + " „§ 5 Absatz 2 Nummer 4,“ und nach der Angabe „§ 23 Absatz 8 Satz 1“ ein"
                + " Komma und die Angabe „§ 32 Satz 1“ eingefügt.",
            new Stelle(List.of(new Stelle.Paragraph("73"))));

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof WoerterEinfuegung);
    assertThat(((WoerterEinfuegung) teile.get(1)).woerter()).isEqualTo(", § 32 Satz 1");
  }

  @Test
  void erkenntKommaUndWoerterVorDemPunktAmEnde() {
    var befehl =
        erkenne(
            "In Absatz 3 Satz 1 wird vor dem Punkt am Ende ein Komma und werden die Wörter"
                + " „oder wenn der Nachweis erfolgt ist“ eingefügt.",
            new Stelle(List.of(new Stelle.Paragraph("8"))));

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    var ersetzung = (Ersetzung) befehl.orElseThrow();
    assertThat(ersetzung.alt()).isEqualTo(".");
    assertThat(ersetzung.neu()).isEqualTo(", oder wenn der Nachweis erfolgt ist.");
    assertThat(ersetzung.amEnde()).isTrue();
  }

  @Test
  void erkenntKoordinierteUmnummerierung() {
    var befehl =
        erkenne(
            "Die bisherigen Absätze 6 und 7 werden die Absätze 1 und 2.",
            new Stelle(List.of(new Stelle.Paragraph("5"))));

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2).allMatch(t -> t instanceof Umnummerierung);
    // Absteigend: 7 → 2 zuerst, damit die Labels nicht kollidieren.
    assertThat(teile.get(0).stelle().anzeigeText()).isEqualTo("§ 5 Absatz 7");
  }

  @Test
  void erkenntVerbundMitUmnummerierungUndRueckbezug() {
    var befehl =
        erkenne(
            "Die bisherige Nummer 1 wird Nummer 2 und in ihr werden die Wörter „§ 72 Absatz 1"
                + " bis 3,“ durch die Wörter „Ablauf der Übergangsfristen,“ ersetzt.",
            new Stelle(List.of(new Stelle.Paragraph("96"))));

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(2);
    assertThat(teile.get(0)).isInstanceOf(Umnummerierung.class);
    assertThat(teile.get(1)).isInstanceOf(Ersetzung.class);
    assertThat(teile.get(1).stelle().anzeigeText()).isEqualTo("§ 96 Nummer 2");
  }

  @Test
  void erkenntVerbundMitUmnummerierungUndNeufassung() {
    var befehl =
        erkenne(
            "Die bisherige Nummer 3 wird Nummer 4 und wird wie folgt gefasst: 4. „ die"
                + " Abrechnungen und Bestätigungen nach § 96 Absatz 5 vorliegen.“",
            new Stelle(List.of(new Stelle.Paragraph("96"))));

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile.get(0)).isInstanceOf(Umnummerierung.class);
    assertThat(teile.get(1)).isInstanceOf(Neufassung.class);
    assertThat(((Neufassung) teile.get(1)).neuerText()).startsWith("4. ");
  }

  @Test
  void erkenntParagraphAnfuegungNachAnker() {
    var befehl =
        erkenne(
            "Nach § 114 wird folgender § 115 angefügt: „§ 115 Übergangsvorschriften für"
                + " Geldbußen Text.“",
            Stelle.LEER);

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.ebene()).isEqualTo(Ebene.PARAGRAPH);
    assertThat(einfuegung.bezeichnung()).isEqualTo("115");
  }

  @Test
  void erkenntAnkerloseAbsatzEinfuegung() {
    var befehl =
        erkenne(
            "Folgender Absatz 2 wird eingefügt: „(2) In einem Wohngebäude gilt dies nicht.“",
            new Stelle(List.of(new Stelle.Paragraph("72"))));

    assertThat(befehl).containsInstanceOf(StrukturEinfuegung.class);
    var einfuegung = (StrukturEinfuegung) befehl.orElseThrow();
    assertThat(einfuegung.vorher()).isFalse();
    assertThat(einfuegung.stelle().anzeigeText()).isEqualTo("§ 72 Absatz 1");
  }

  @Test
  void erkenntNummernBlockAnfuegung() {
    var befehl =
        erkenne(
            "Die folgenden Nummern 9 bis 11 werden angefügt: 9. „ Durchführung hydraulischer"
                + " Abgleiche, 10. Einbau von Messausstattungen, 11. Sonstiges.“",
            new Stelle(List.of(new Stelle.Paragraph("60"))));

    assertThat(befehl).containsInstanceOf(Anfuegung.class);
    var anfuegung = (Anfuegung) befehl.orElseThrow();
    assertThat(anfuegung.ebene()).isEqualTo(Ebene.NUMMER);
    assertThat(anfuegung.text()).startsWith("9. ");
  }

  @Test
  void erkenntPunktErsetzungDurchFolgendeWoerter() {
    var befehl =
        erkenne(
            "In Absatz 1 Satz 2 wird der Punkt am Ende durch folgende Wörter ersetzt: „, das"
                + " heißt, wenn die Investitionen unangemessen sind.“",
            new Stelle(List.of(new Stelle.Paragraph("102"))));

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    var ersetzung = (Ersetzung) befehl.orElseThrow();
    assertThat(ersetzung.alt()).isEqualTo(".");
    assertThat(ersetzung.amEnde()).isTrue();
  }

  @Test
  void erkenntWortVoranstellungImVerbund() {
    var befehl =
        erkenne(
            "In Buchstabe a werden die Wörter „des § 10“ durch die Wörter „der §§ 71 bis 71h“"
                + " ersetzt, wird dem Wort „Anforderungen“ das Wort „dortigen“ vorangestellt und"
                + " werden die Wörter „nach den §§ 35 bis 41“ gestrichen.",
            new Stelle(List.of(new Stelle.Paragraph("105"))));

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile).hasSize(3);
    assertThat(teile.get(1)).isInstanceOf(WoerterEinfuegung.class);
    assertThat(((WoerterEinfuegung) teile.get(1)).woerter()).isEqualTo("dortigen");
    assertThat(teile.get(2)).isInstanceOf(Streichung.class);
  }

  @Test
  void erkenntErsetzungMitPositionsanker() {
    var befehl =
        erkenne(
            "In Nummer 2 werden nach den Wörtern „jeweils auch in Verbindung mit“ die Wörter"
                + " „einer Rechtsverordnung nach § 14,“ durch die Wörter „§ 14 Absatz 8,“"
                + " ersetzt.",
            new Stelle(List.of(new Stelle.Paragraph("73"))));

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    var ersetzung = (Ersetzung) befehl.orElseThrow();
    assertThat(ersetzung.alt()).isEqualTo("einer Rechtsverordnung nach § 14,");
    assertThat(ersetzung.neu()).isEqualTo("§ 14 Absatz 8,");
  }

  // --- Bayerisches Landesrecht ---------------------------------------------------------------

  private static final Stelle ART_22 = new Stelle(List.of(new Stelle.Paragraph("22", "Art.")));

  @Test
  void erkenntBayerischeErsetzungMitAbkuerzungen() {
    var befehl =
        erkenne(
            "In Abs. 2 Satz 1 Nr. 1 wird die Angabe „des Bundesbaugesetzes“ durch die Angabe"
                + " „des Baugesetzbuchs (BauGB)“ ersetzt.",
            new Stelle(List.of(new Stelle.Paragraph("6", "Art."))));

    assertThat(befehl).containsInstanceOf(Ersetzung.class);
    var ersetzung = (Ersetzung) befehl.orElseThrow();
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("Art. 6 Absatz 2 Satz 1 Nummer 1");
  }

  @Test
  void erkenntFussnotenAufhebung() {
    var einzeln = erkenne("Fußnote 1 wird aufgehoben.", ART_22);
    assertThat(einzeln).containsInstanceOf(Aenderungsbefehl.FussnotenAufhebung.class);
    assertThat(((Aenderungsbefehl.FussnotenAufhebung) einzeln.orElseThrow()).nummern())
        .containsExactly("1");

    var mehrere = erkenne("Die Fußnoten 9 und 10 werden aufgehoben.", ART_22);
    assertThat(mehrere).containsInstanceOf(Aenderungsbefehl.FussnotenAufhebung.class);
    assertThat(((Aenderungsbefehl.FussnotenAufhebung) mehrere.orElseThrow()).nummern())
        .containsExactly("9", "10");
  }

  @Test
  void erkenntSatznummerierungsStreichungImVerbund() {
    // GVBl 2026 S. 113, Nr. 15 c) aa) — exakt die Verbundform des Belegdokuments.
    var befehl =
        erkenne(
            "In Satz 1 wird die Satznummerierung „1“ gestrichen und die Angabe „Absätzen 1"
                + " und 2“ wird durch die Angabe „Abs. 1 und 2“ ersetzt.",
            ART_22);

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile.get(0)).isInstanceOf(Aenderungsbefehl.SatznummerierungStreichung.class);
    var streichung = (Aenderungsbefehl.SatznummerierungStreichung) teile.get(0);
    assertThat(streichung.nummer()).isEqualTo("1");
    assertThat(streichung.stelle().anzeigeText()).isEqualTo("Art. 22 Satz 1");
    assertThat(teile.get(1)).isInstanceOf(Ersetzung.class);

    var einfach = erkenne("In Satz 1 wird die Satznummerierung „1“ gestrichen.", ART_22);
    assertThat(einfach).containsInstanceOf(Aenderungsbefehl.SatznummerierungStreichung.class);
  }

  @Test
  void erkenntBayerischeWortlautFormen() {
    var zuSatz = erkenne("Der Wortlaut wird Satz 1.", ART_22);
    assertThat(zuSatz).containsInstanceOf(Aenderungsbefehl.WortlautZuSatz.class);

    var zuAbsatz = erkenne("Der bisherige Wortlaut wird Abs. 5.", ART_22);
    assertThat(zuAbsatz).containsInstanceOf(WortlautZuAbsatz.class);
    assertThat(((WortlautZuAbsatz) zuAbsatz.orElseThrow()).nummer()).isEqualTo("5");

    var voranstellung =
        erkenne(
            "Dem Wortlaut werden die folgenden Abs. 1 bis 4 vorangestellt: „(1) Erster."
                + " (2) Zweiter. (3) Dritter. (4) Vierter.“",
            ART_22);
    assertThat(voranstellung).containsInstanceOf(Aenderungsbefehl.WortlautVoranstellung.class);
    assertThat(((Aenderungsbefehl.WortlautVoranstellung) voranstellung.orElseThrow()).text())
        .contains("(3) Dritter.");
  }

  @Test
  void erkenntWortlautZuAbsatzMitFolgeklausel() {
    // „Der bisherige Wortlaut wird Abs. 5 und in Halbsatz 1 wird …“ — die lokative Folgeklausel
    // bezieht sich auf den soeben nummerierten Absatz.
    var befehl =
        erkenne(
            "Der bisherige Wortlaut wird Abs. 5 und in Halbsatz 1 wird die Angabe „Das"
                + " Staatsministerium“ durch die Angabe „Die oberste Jagdbehörde“ ersetzt.",
            ART_22);

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile.get(0)).isInstanceOf(WortlautZuAbsatz.class);
    assertThat(teile.get(1)).isInstanceOf(Ersetzung.class);
    assertThat(((Ersetzung) teile.get(1)).stelle().anzeigeText())
        .isEqualTo("Art. 22 Absatz 5 Halbsatz 1");
  }

  @Test
  void erkenntSatzUmnummerierungMitErsetzungsKlausel() {
    // GVBl 2026 S. 113, Nr. 23 c) cc).
    var befehl =
        erkenne(
            "Der bisherige Satz 2 wird Satz 3 und die Angabe „der Durchführung der Lehrgänge"
                + " (Art. 28 Abs. 1 Satz 4),“ wird durch die Angabe „einer Durchführung von"
                + " Lehrgängen für die Fallenjagd“ ersetzt.",
            ART_22);

    assertThat(befehl).containsInstanceOf(Sammelbefehl.class);
    var teile = ((Sammelbefehl) befehl.orElseThrow()).teilbefehle();
    assertThat(teile.get(0)).isInstanceOf(Umnummerierung.class);
    assertThat(teile.get(1)).isInstanceOf(Ersetzung.class);
  }

  @Test
  void erkenntBayerischeUmnummerierungsVerbuende() {
    // „Abs. 3 wird Abs. 2 und wird wie folgt geändert:“ läuft über rahmenMitBefehl;
    // „Satz 5 wird Satz 4.“ ist eine gewöhnliche Umnummerierung.
    var satz = erkenne("Satz 5 wird Satz 4.", ART_22);
    assertThat(satz).containsInstanceOf(Umnummerierung.class);
    assertThat(((Umnummerierung) satz.orElseThrow()).neu().anzeigeText())
        .isEqualTo("Art. 22 Satz 4");

    var abs = erkenne("Abs. 3 wird Abs. 2.", ART_22);
    assertThat(abs).containsInstanceOf(Umnummerierung.class);
  }
}
