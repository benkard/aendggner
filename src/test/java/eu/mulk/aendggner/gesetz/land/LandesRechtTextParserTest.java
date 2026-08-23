// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz.land;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gliederung;
import org.junit.jupiter.api.Test;

class LandesRechtTextParserTest {

  // Bayern: Gliederung in „Art.“, amtliche Satznummern als Superskript.
  private static final String BAYERN =
      """
      BayJG: Bayerisches Jagdgesetz (BayJG) Vom 13. Oktober 1978 (BayRS V S. 595) BayRS 792-1-W (Art.
      1–64)
      Bayerisches Jagdgesetz
      (BayJG)
      Vom 13. Oktober 1978
      (BayRS V S. 595)
      BayRS 792-1-W
      Vollzitat nach RedR: Bayerisches Jagdgesetz (BayJG) in der in der Bayerischen Rechtssammlung (BayRS
      792-1-L) veröffentlichten bereinigten Fassung
      I. Abschnitt Grundsätze
      Art. 1  Gesetzeszweck

      (1) ¹Die freilebende Tierwelt ist wesentlicher Bestandteil der heimischen Natur. ²Sie ist zu bewahren.
      (2) Dieses Gesetz soll dazu dienen:
      1.  einen artenreichen Wildbestand zu erhalten,

      2.  die Lebensgrundlagen des Wildes zu sichern.

      II. Abschnitt Jagdreviere, Hegegemeinschaften
      1. Allgemeine Vorschriften
      Art. 3  Feststellung der Jagdreviere

      Bestand, Umfang und Grenzen eines Jagdreviers werden durch die
      Jagdbehörde festgestellt.
      2. Jagdreviere
      Art. 8  Eigenjagdreviere

      (1) ¹Die Mindestgröße beträgt 81,755 ha. ²Die Art. 4 Abs. 3, Art. 5 Abs. 2 und
      Art. 11 Abs. 6 sind entsprechend anzuwenden.
      Art. 59  Enteignende Maßnahmen

      (1) Es ist nach den Vorschriften des Gesetzes über die entschädigungspflichtige Enteignung⁶) Entschädigung zu leisten.
      6) [Amtl. Anm.:] BayRS 2141-1-I
      Art. 60  (aufgehoben)

      Art. 63 (Änderungsbestimmung)

      Art. 64  Inkrafttreten; Aufhebung von Vorschriften

      (1) Dieses Gesetz tritt am 1. Januar 1979 in Kraft.
      """;

  // Übriges Landesrecht: Gliederung in „§“ (wie der Bund), hier mit amtlichen Satznummern.
  private static final String PARAGRAPHEN =
      """
      Gemeindeordnung für das Land Nordrhein-Westfalen
      (GO NRW)
      Vom 14. Juli 1994
      I. Teil Grundlagen der Gemeindeverfassung
      § 1  Wesen der Gemeinde

      (1) ¹Die Gemeinden sind die Grundlage des demokratischen Staatsaufbaus. ²Sie fördern das Wohl der Einwohner.
      (2) Die Gemeinden verwalten ihre Angelegenheiten selbst.
      § 2  Aufgaben

      Die Gemeinden erfüllen die Aufgaben der örtlichen Gemeinschaft.
      § 3  (weggefallen)

      § 4  Satzungen

      (1) Die Gemeinden können ihre Angelegenheiten durch Satzung regeln.
      """;

  @Test
  void parstBayerischenTitelblock() {
    var gesetz = LandesRechtTextParser.parse(BAYERN);
    assertThat(gesetz.jurabk()).isEqualTo("BayJG");
    assertThat(gesetz.langue()).isEqualTo("Bayerisches Jagdgesetz");
    assertThat(gesetz.kurzue()).isNull();
  }

  @Test
  void parstBayerischeNormenMitTitelnUndAbsaetzen() {
    var gesetz = LandesRechtTextParser.parse(BAYERN);
    assertThat(gesetz.normen())
        .extracting(n -> n.enbez())
        .containsExactly("Art. 1", "Art. 3", "Art. 8", "Art. 59", "Art. 60", "Art. 63", "Art. 64");

    var art1 = gesetz.norm("Art. 1").orElseThrow();
    assertThat(art1.titel()).isEqualTo("Gesetzeszweck");
    assertThat(art1.absaetze()).hasSize(2);
    assertThat(art1.absaetze().get(0).nummer()).isEqualTo("1");
    assertThat(art1.absaetze().get(0).text()).startsWith("¹Die freilebende Tierwelt");
    assertThat(art1.absaetze().get(1).text()).contains("1.  einen artenreichen");

    // Unnummerierter Einzelabsatz.
    var art3 = gesetz.norm("Art. 3").orElseThrow();
    assertThat(art3.absaetze()).hasSize(1);
    assertThat(art3.absaetze().get(0).nummer()).isNull();
  }

  @Test
  void querverweiseAmZeilenanfangEroeffnenKeineNorm() {
    // „Art. 11 Abs. 6 …“ am Zeilenanfang in Art. 8 ist ein Querverweis, kein Normkopf.
    var gesetz = LandesRechtTextParser.parse(BAYERN);
    var art8 = gesetz.norm("Art. 8").orElseThrow();
    assertThat(art8.absaetze().get(0).text()).contains("Art. 11 Abs. 6 sind entsprechend");
    assertThat(gesetz.norm("Art. 11")).isEmpty();
  }

  @Test
  void parstGliederungenMitUnterUeberschriften() {
    var gesetz = LandesRechtTextParser.parse(BAYERN);
    assertThat(gesetz.gliederungen())
        .extracting(Gliederung::anzeigeText)
        .containsExactly(
            "I. Abschnitt — Grundsätze",
            "II. Abschnitt — Jagdreviere, Hegegemeinschaften",
            "1. — Allgemeine Vorschriften",
            "2. — Jagdreviere");
    // Kennzahlen sind eindeutig und hängen an der Eltern-Gliederung.
    assertThat(gesetz.gliederungen())
        .extracting(Gliederung::kennzahl)
        .containsExactly("001", "002", "002.1", "002.2");
    assertThat(gesetz.norm("Art. 8").orElseThrow().gliederung().kennzahl()).isEqualTo("002.2");
  }

  @Test
  void erkenntWeggefalleneNormenUndFussnoten() {
    var gesetz = LandesRechtTextParser.parse(BAYERN);
    assertThat(gesetz.norm("Art. 60").orElseThrow().weggefallen()).isTrue();
    assertThat(gesetz.norm("Art. 63").orElseThrow().weggefallen()).isFalse();

    // Fußnotenzeile bleibt superskript-normalisiert im tragenden Absatz.
    var art59 = gesetz.norm("Art. 59").orElseThrow();
    assertThat(art59.absaetze().get(0).text())
        .contains("Enteignung⁶)")
        .contains("⁶) [Amtl. Anm.:] BayRS 2141-1-I");
  }

  @Test
  void parstParagraphengegliedertesLandesrecht() {
    // Die übrigen Länder zitieren in „§“; das Sigel folgt aus dem Normkopf, ein Land-Merkmal ist
    // nicht nötig. Amtliche Satznummern werden ebenso wie bei Bayern erhalten.
    var gesetz = LandesRechtTextParser.parse(PARAGRAPHEN);
    assertThat(gesetz.jurabk()).isEqualTo("GO NRW");
    assertThat(gesetz.langue()).isEqualTo("Gemeindeordnung für das Land Nordrhein-Westfalen");
    assertThat(gesetz.normen())
        .extracting(n -> n.enbez())
        .containsExactly("§ 1", "§ 2", "§ 3", "§ 4");

    var p1 = gesetz.norm("§ 1").orElseThrow();
    assertThat(p1.titel()).isEqualTo("Wesen der Gemeinde");
    assertThat(p1.absaetze()).hasSize(2);
    assertThat(p1.absaetze().get(0).text()).startsWith("¹Die Gemeinden sind die Grundlage");

    // Unnummerierter Einzelabsatz und weggefallene Norm wie im bayerischen Fall.
    assertThat(gesetz.norm("§ 2").orElseThrow().absaetze().get(0).nummer()).isNull();
    assertThat(gesetz.norm("§ 3").orElseThrow().weggefallen()).isTrue();
    assertThat(gesetz.gliederungen())
        .extracting(Gliederung::anzeigeText)
        .containsExactly("I. Teil — Grundlagen der Gemeindeverfassung");
  }

  @Test
  void trenntKurztitelUndAbkuerzungImKlammerzusatz() {
    // Landesgesetze führen im Klammerzusatz oft beides („Telemedienzuständigkeitsgesetz –
    // TMZ-Gesetz“); das Änderungsgesetz zitiert den Kurztitel, nicht die Abkürzung.
    var gesetz =
        LandesRechtTextParser.parse(
            """
            Gesetz zur Regelung der Zuständigkeit für die Überwachung von Telemedien
            (Telemedienzuständigkeitsgesetz – TMZ-Gesetz)
            Vom 29. März 2007
            § 1  Aufsicht bei Telemedien
            (1) Die Landesanstalt für Medien ist zuständig.
            """);

    assertThat(gesetz.jurabk()).isEqualTo("TMZ-Gesetz");
    assertThat(gesetz.kurzue()).isEqualTo("Telemedienzuständigkeitsgesetz");

    // Ohne Trenner bleibt der Klammerzusatz die Abkürzung; ein nachgestellter Gedankenstrich
    // („Gemeindeordnung – GO –“) gehört nicht zur Abkürzung.
    var ohneKurztitel =
        LandesRechtTextParser.parse(
            "Gemeindeordnung für Schleswig-Holstein\n(GO)\n§ 1  Wesen\n(1) Text.\n");
    assertThat(ohneKurztitel.jurabk()).isEqualTo("GO");
    assertThat(ohneKurztitel.kurzue()).isNull();

    var mitGedankenstrich =
        LandesRechtTextParser.parse(
            "Gemeindeordnung für Schleswig-Holstein\n(Gemeindeordnung – GO –)\n§ 1  Wesen\n(1) Text.\n");
    assertThat(mitGedankenstrich.jurabk()).isEqualTo("GO");
    assertThat(mitGedankenstrich.kurzue()).isEqualTo("Gemeindeordnung");
  }

  /**
   * Die Inhaltsübersicht ist eine eigene Norm — nur unter diesem Namen finden sie die
   * Angabe-Befehle. Sie führt auch die Gliederungs-Überschriften des Gesetzes mit; daran darf sie
   * nicht zerfallen.
   */
  @Test
  void liestInhaltsuebersichtAlsNorm() {
    var gesetz =
        LandesRechtTextParser.parse(
            """
            Landesmediengesetz Nordrhein-Westfalen
            (LMG NRW)
            Vom 2. Juli 2002
            Inhaltsübersicht
            Abschnitt 1 Allgemeine Vorschriften
            § 1 | Geltungsbereich
            Abschnitt 2 Zulassung
            § 4 | Grundsätze
            Abschnitt 1 Allgemeine Vorschriften
            § 1  Geltungsbereich
            (1) Dieses Gesetz gilt für die Veranstaltung von Rundfunk.
            """);

    var uebersicht = gesetz.norm("Inhaltsübersicht").orElseThrow();
    assertThat(uebersicht.gesamtText())
        .contains("Abschnitt 1 Allgemeine Vorschriften")
        .contains("§ 1 | Geltungsbereich")
        .contains("Abschnitt 2 Zulassung")
        .contains("§ 4 | Grundsätze");
    // Nur die Gliederung des Textteils zählt, nicht die zitierte in der Übersicht.
    assertThat(gesetz.gliederungen()).hasSize(1);
    assertThat(gesetz.norm("§ 1").orElseThrow().titel()).isEqualTo("Geltungsbereich");
  }

  /** Römische Gliederung ohne Schlüsselwort — sie ist nur an ihrer Stellung erkennbar. */
  @Test
  void erkenntRoemischeGliederungOhneSchluesselwort() {
    var gesetz =
        LandesRechtTextParser.parse(
            """
            Gesetz über den Westdeutschen Rundfunk Köln
            (WDR-Gesetz)
            Vom 25. April 1998
            I. Rechtsform und Aufgaben
            § 1  Name, Rechtsform
            (1) Der WDR ist eine Anstalt des öffentlichen Rechts.
            II. Organisation
            1. Der Rundfunkrat
            § 15  Zusammensetzung
            (1) Der Rundfunkrat besteht aus Mitgliedern.
            """);

    assertThat(gesetz.gliederungen())
        .extracting(Gliederung::bezeichnung, Gliederung::titel)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("I.", "Rechtsform und Aufgaben"),
            org.assertj.core.groups.Tuple.tuple("II.", "Organisation"),
            org.assertj.core.groups.Tuple.tuple("1.", "Der Rundfunkrat"));
    assertThat(gesetz.norm("§ 15").orElseThrow().gliederung().titel()).isEqualTo("Der Rundfunkrat");
  }

  /**
   * Eine Anlage ist eine eigene Norm; ihre enbez lautet genau wie im gii-XML („Anlage“, „Anlage 2“,
   * „Anhang“), damit {@code Stelle.anlagenEnbez()} sie unverändert trifft. Alles hinter ihrem Kopf
   * gehört zu ihr: Ihre inneren Überschriften gliedern nicht das Gesetz, und ein Paragraph, den sie
   * zitiert, eröffnet keine Norm. Nur eine weitere Anlage beendet sie.
   */
  @Test
  void erkenntAnlagenAlsEigeneNormen() {
    var gesetz =
        LandesRechtTextParser.parse(
            """
            Beispielgesetz
            (BeispG)
            Vom 1. Januar 2020
            Erster Abschnitt  Allgemeines
            § 1  Zweck
            (1) Dieses Gesetz dient der Erprobung.
            Anlage
            Zuständigkeitskatalog
            (zu § 1 Absatz 1)
            Erster Abschnitt  Aufgaben der Behörden
            (1) Die erste Aufgabe.
            (2) Die zweite Aufgabe; § 1 bleibt unberührt.
            Anlage 2
            Muster
            (1) Das Muster.
            """);

    assertThat(gesetz.normen())
        .extracting(eu.mulk.aendggner.gesetz.Norm::enbez)
        .containsExactly("§ 1", "Anlage", "Anlage 2");
    // Die Überschrift innerhalb der Anlage ist Inhalt, keine Gliederung des Gesetzes.
    assertThat(gesetz.gliederungen())
        .extracting(Gliederung::bezeichnung)
        .containsExactly("Erster Abschnitt");
    assertThat(gesetz.norm("Anlage").orElseThrow().gesamtText())
        .contains("Erster Abschnitt  Aufgaben der Behörden")
        .contains("§ 1 bleibt unberührt");
    assertThat(gesetz.norm("Anlage 2").orElseThrow().gesamtText()).contains("Das Muster");
  }

  /**
   * Trägt eine Anlage Nummern als eigene Einheiten — so der Zuständigkeitskatalog des Berliner ASOG
   * —, so ist jede eine eigene Norm mit eigener Absatzzählung. Ihre Bezeichnung stellt die Anlage
   * voran, damit sie im Gesetz eindeutig bleibt: „Anlage Nummer 6“.
   */
  @Test
  void nummernEinerAnlageSindEigeneNormen() {
    var gesetz =
        LandesRechtTextParser.parse(
            """
            Beispielgesetz
            (BeispG)
            Vom 1. Januar 2020
            § 1  Zweck
            (1) Dieses Gesetz dient der Erprobung.
            Anlage
            (zu § 1 Absatz 1)
            Nummer 6
            Bezirksamt
            (1) Die erste Aufgabe.
            (2) Die zweite Aufgabe.
            Nummer 23
            Polizeipräsident
            (1) Die dritte Aufgabe.
            """);

    assertThat(gesetz.normen())
        .extracting(eu.mulk.aendggner.gesetz.Norm::enbez)
        .containsExactly("§ 1", "Anlage", "Anlage Nummer 6", "Anlage Nummer 23");
    var nummer6 = gesetz.norm("Anlage Nummer 6").orElseThrow();
    assertThat(nummer6.absaetze()).extracting(Absatz::nummer).containsExactly(null, "1", "2");
    // Der Vorspann der Anlage bleibt bei ihr; die Nummern nehmen ihn nicht mit.
    assertThat(gesetz.norm("Anlage").orElseThrow().gesamtText()).contains("(zu § 1 Absatz 1)");
    assertThat(nummer6.gesamtText()).contains("Bezirksamt").doesNotContain("zu § 1 Absatz 1");
  }
}
