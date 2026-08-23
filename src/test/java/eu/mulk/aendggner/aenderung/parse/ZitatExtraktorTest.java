// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZitatExtraktorTest {

  @Test
  void ersetztZitateDurchPlatzhalter() {
    var ergebnis = ZitatExtraktor.extrahiere("Das Wort „alt“ wird durch das Wort „neu“ ersetzt.");

    assertThat(ergebnis.text()).isEqualTo("Das Wort «0» wird durch das Wort «1» ersetzt.");
    assertThat(ergebnis.zitat(0)).isEqualTo("alt");
    assertThat(ergebnis.zitat(1)).isEqualTo("neu");
    assertThat(ergebnis.warnungen()).isEmpty();
  }

  @Test
  void schlaegtGeschachtelteZitateDemAeusserenZu() {
    var ergebnis = ZitatExtraktor.extrahiere("Es wird gefasst: „Satz mit „innerem“ Zitat.“");

    assertThat(ergebnis.text()).isEqualTo("Es wird gefasst: «0»");
    assertThat(ergebnis.zitat(0)).isEqualTo("Satz mit „innerem“ Zitat.");
  }

  @Test
  void laesstEinfacheZitateUnangetastet() {
    var ergebnis = ZitatExtraktor.extrahiere("„Wort ‚einfach‘ zitiert“");

    assertThat(ergebnis.zitat(0)).isEqualTo("Wort ‚einfach‘ zitiert");
  }

  @Test
  void verkraftetMehrzeiligeZitate() {
    var ergebnis =
        ZitatExtraktor.extrahiere("Gefasst: „(1) Erster Absatz.\n\n(2) Zweiter Absatz.“ Ende.");

    assertThat(ergebnis.text()).isEqualTo("Gefasst: «0» Ende.");
    assertThat(ergebnis.zitat(0)).contains("(2) Zweiter Absatz.");
  }

  @Test
  void meldetUeberzaehligesSchliessendesAnfuehrungszeichen() {
    // Kommt in echten BGBl-Texten vor (z.B. BGBl. I 2020 S. 2397, Artikel 3).
    var ergebnis = ZitatExtraktor.extrahiere("wird „alt“ ersetzt.“ Danach normal.");

    assertThat(ergebnis.text()).isEqualTo("wird «0» ersetzt.“ Danach normal.");
    assertThat(ergebnis.warnungen()).hasSize(1);
    assertThat(ergebnis.warnungen().get(0)).contains("ohne öffnendes");
  }

  @Test
  void meldetAmTextendeOffeneZitate() {
    var ergebnis = ZitatExtraktor.extrahiere("Es wird gefasst: „nie geschlossen");

    assertThat(ergebnis.text()).isEqualTo("Es wird gefasst: «0»");
    assertThat(ergebnis.zitat(0)).isEqualTo("nie geschlossen");
    assertThat(ergebnis.warnungen()).hasSize(1);
    assertThat(ergebnis.warnungen().get(0)).contains("offen");
  }

  @Test
  void schliesstOffenesZitatVorArtikelUeberschrift() {
    // Fehlt im amtlichen Satz das schließende Anführungszeichen (GV. NRW. 2026 S. 202 Artikel 2
    // Nr. 11), verschlänge das Zitat sonst alle folgenden Artikel und der Parser fände keinen
    // einzigen Änderungsbefehl mehr.
    var ergebnis =
        ZitatExtraktor.extrahiere(
            "12. Dem § 128 wird folgender Absatz angefügt:\n"
                + "„(3) § 14 gilt nicht.\n"
                + "Artikel 3\n"
                + "§ 1 wird wie folgt gefasst: „(1) Neu.“");

    assertThat(ergebnis.text())
        .isEqualTo(
            "12. Dem § 128 wird folgender Absatz angefügt:\n«0»\nArtikel 3\n"
                + "§ 1 wird wie folgt gefasst: «1»");
    assertThat(ergebnis.zitat(0)).isEqualTo("(3) § 14 gilt nicht.");
    assertThat(ergebnis.zitat(1)).isEqualTo("(1) Neu.");
    assertThat(ergebnis.warnungen()).hasSize(1);
    assertThat(ergebnis.warnungen().get(0)).startsWith("Zitat vor einer Artikel-Überschrift");
  }

  @Test
  void zitiertUeberParagraphenUeberschriftenHinweg() {
    // Nur „Artikel N“ ist Strukturgrenze: Ein zitierter Paragraph („§ 19“ + Überschrift auf der
    // Folgezeile) gehört vollständig ins Zitat.
    var ergebnis = ZitatExtraktor.extrahiere("Es wird gefasst: „§ 19\nAußerkrafttreten\nText.“");

    assertThat(ergebnis.text()).isEqualTo("Es wird gefasst: «0»");
    assertThat(ergebnis.zitat(0)).isEqualTo("§ 19\nAußerkrafttreten\nText.");
    assertThat(ergebnis.warnungen()).isEmpty();
  }

  @Test
  void schliesstOffenesZitatVorDemNaechstenAufzaehlungspunkt() {
    // GVBl. für Berlin 17/2026 Artikel 1 Nr. 2 b) bb): Am Ende des neu gefassten Absatzes fehlt das
    // schließende Anführungszeichen, das Zitat verschlänge sonst die Punkte cc) und c).
    var ergebnis =
        ZitatExtraktor.extrahiere(
            "Artikel 1\n"
                + "bb) Vor den Wörtern „Aus dem Bereich Verkehr:“ wird folgender Absatz 5"
                + " eingefügt:\n"
                + "„(5) Die Sicherheitskontrolle.\n"
                + "cc) Die bisherigen Absätze 5 bis 9 werden die Absätze 6 bis 10.\n"
                + "c) Nummer 31 wird wie folgt gefasst:\n"
                + "„Nummer 31\nNeuer Text.“\n");

    // Zitat 0 ist der Wortanker, Zitat 1 der unvollständig zitierte neue Absatz, Zitat 2 die
    // Neufassung aus Punkt c) — dieser Punkt bleibt also erhalten.
    assertThat(ergebnis.zitate()).hasSize(3);
    assertThat(ergebnis.zitat(1)).isEqualTo("(5) Die Sicherheitskontrolle.");
    assertThat(ergebnis.zitat(2)).isEqualTo("Nummer 31\nNeuer Text.");
    assertThat(ergebnis.text())
        .contains("cc) Die bisherigen Absätze 5 bis 9 werden die Absätze 6 bis 10.")
        .contains("c) Nummer 31 wird wie folgt gefasst:");
    assertThat(ergebnis.warnungen()).hasSize(1);
    assertThat(ergebnis.warnungen().get(0))
        .startsWith("Zitat vor dem Aufzählungspunkt „cc)“ nicht geschlossen");
  }

  @Test
  void raetNichtImAusbalanciertenAbschnitt() {
    // Ein zitiertes Änderungsgesetz trägt selbst Befehlssprache in seinen Aufzählungspunkten (so in
    // den bayerischen GVBl-Heften). Solange die Anführungszeichen des Artikels aufgehen, wird
    // deshalb nicht geraten — das Zitat läuft über die Punkte hinweg.
    var ergebnis =
        ZitatExtraktor.extrahiere(
            "Artikel 1\n"
                + "2. § 5 wird wie folgt gefasst:\n"
                + "„Die Verordnung wird wie folgt geändert:\n"
                + "1. § 2 wird wie folgt geändert:\n"
                + "2. § 3 wird aufgehoben.“\n");

    assertThat(ergebnis.zitate()).hasSize(1);
    assertThat(ergebnis.zitat(0)).contains("1. § 2 wird wie folgt geändert:");
    assertThat(ergebnis.warnungen()).isEmpty();
  }

  @Test
  void schliesstNichtAnTieferemAufzaehlungspunkt() {
    // Der Marker der Folgezeile muss auf derselben oder einer flacheren Ebene liegen als der Punkt,
    // auf dem das Zitat aufging: eine zitierte Untergliederung setzt das Zitat nicht ab.
    var ergebnis =
        ZitatExtraktor.extrahiere(
            "Artikel 1\n"
                + "2. § 5 wird wie folgt gefasst:\n"
                + "„(1) Es gilt:\n"
                + "aa) § 7 wird aufgehoben.\n");

    assertThat(ergebnis.zitate()).hasSize(1);
    assertThat(ergebnis.zitat(0)).contains("aa) § 7 wird aufgehoben.");
    assertThat(ergebnis.warnungen()).hasSize(1);
    assertThat(ergebnis.warnungen().get(0)).contains("offen");
  }

  @Test
  void stelltZitateWiederHer() {
    var original = "Das Wort „alt“ wird durch das Wort „neu“ ersetzt.";
    var ergebnis = ZitatExtraktor.extrahiere(original);

    assertThat(ergebnis.stelleZitateWiederHer(ergebnis.text())).isEqualTo(original);
  }
}
