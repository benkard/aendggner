// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Stelle;
import org.junit.jupiter.api.Test;

class StellenParserTest {

  @Test
  void parstEinfacheStelle() {
    assertThat(StellenParser.parse("§ 5a Absatz 2 Satz 1").orElseThrow().anzeigeText())
        .isEqualTo("§ 5a Absatz 2 Satz 1");
  }

  @Test
  void lehntKoordinationInParseAb() {
    // parse (einfach) bleibt bewusst streng: Koordination fällt durch.
    assertThat(StellenParser.parse("§ 3 Absatz 1 und Absatz 4")).isEmpty();
  }

  @Test
  void parstMehrfachMitGemeinsamemPraefix() {
    // Zweites Segment erbt „§ 3“, ersetzt aber ab „Absatz“.
    var stellen = StellenParser.parseMehrfach("§ 3 Absatz 1 Satz 2 und Absatz 4");
    assertThat(stellen)
        .extracting(Stelle::anzeigeText)
        .containsExactly("§ 3 Absatz 1 Satz 2", "§ 3 Absatz 4");
  }

  @Test
  void parstMehrfachMitSatzTiefe() {
    var stellen = StellenParser.parseMehrfach("§ 20 Absatz 1 Satz 1 und Absatz 2 Satz 2");
    assertThat(stellen)
        .extracting(Stelle::anzeigeText)
        .containsExactly("§ 20 Absatz 1 Satz 1", "§ 20 Absatz 2 Satz 2");
  }

  @Test
  void parstMehrfachMitKomma() {
    var stellen = StellenParser.parseMehrfach("§ 1, § 2 und § 3");
    assertThat(stellen).extracting(Stelle::anzeigeText).containsExactly("§ 1", "§ 2", "§ 3");
  }

  @Test
  void einfacheStelleBleibtEinElementig() {
    assertThat(StellenParser.parseMehrfach("§ 5 Absatz 2"))
        .extracting(Stelle::anzeigeText)
        .containsExactly("§ 5 Absatz 2");
  }

  @Test
  void unparsbaresSegmentLiefertLeereListe() {
    assertThat(StellenParser.parseMehrfach("§ 5 Absatz 2 und Kauderwelsch")).isEmpty();
  }

  @Test
  void bloßeNummerErbtKomponentenart() {
    // „Absatz 1 und 5“: das „5“ erbt die Komponentenart „Absatz“ der letzten Komponente.
    var stellen = StellenParser.parseMehrfach("§ 7 Absatz 1 und 5");
    assertThat(stellen)
        .extracting(Stelle::anzeigeText)
        .containsExactly("§ 7 Absatz 1", "§ 7 Absatz 5");
  }

  @Test
  void bloßeNummerNachSatz() {
    var stellen = StellenParser.parseMehrfach("Absatz 1 Satz 1 und 2");
    assertThat(stellen)
        .extracting(Stelle::anzeigeText)
        .containsExactly("Absatz 1 Satz 1", "Absatz 1 Satz 2");
  }

  @Test
  void parstGliederungspfad() {
    assertThat(StellenParser.parse("Teil 3 Abschnitt 2").orElseThrow().anzeigeText())
        .isEqualTo("Teil 3 Abschnitt 2");
  }

  @Test
  void parstUeberschriftVonGliederung() {
    var stelle = StellenParser.parse("Die Überschrift von Teil 3 Abschnitt 2").orElseThrow();
    assertThat(stelle.betrifftUeberschrift()).isTrue();
    assertThat(stelle.gliederungsPfad()).hasSize(2);
    assertThat(stelle.anzeigeText()).isEqualTo("Überschrift Teil 3 Abschnitt 2");
  }

  @Test
  void parstBayerischeArtikelStelle() {
    // Bayerisches Landesrecht: Normen heißen „Art. N“, Unterkomponenten sind abgekürzt.
    var stelle = StellenParser.parse("Art. 6 Abs. 2 Satz 1 Nr. 2").orElseThrow();
    assertThat(stelle.anzeigeText()).isEqualTo("Art. 6 Absatz 2 Satz 1 Nummer 2");
    assertThat(stelle.paragraph().orElseThrow().enbez()).isEqualTo("Art. 6");
  }

  @Test
  void parstBayerischenBereichMitAbkuerzung() {
    // „In den Abs. 4 und 5“ und Bereiche wie „Art. 4 bis 6“ mit erhaltenem Sigel.
    var stellen = StellenParser.parseMehrfach("Art. 4 Abs. 3, Art. 5 Abs. 2 und Art. 11 Abs. 6");
    assertThat(stellen)
        .extracting(Stelle::anzeigeText)
        .containsExactly("Art. 4 Absatz 3", "Art. 5 Absatz 2", "Art. 11 Absatz 6");
    var bereich = StellenParser.parseMehrfach("Abs. 1 bis 3");
    assertThat(bereich)
        .extracting(Stelle::anzeigeText)
        .containsExactly("Absatz 1", "Absatz 2", "Absatz 3");
  }

  @Test
  void ignoriertBayerischenChapeauZusatz() {
    assertThat(StellenParser.parse("Abs. 2 Satzteil vor Nr. 1").orElseThrow().anzeigeText())
        .isEqualTo("Absatz 2");
    assertThat(StellenParser.parse("in dem Satzteil nach Nr. 3").isEmpty()).isTrue();
    assertThat(StellenParser.istNurChapeau("in dem Satzteil nach Nr. 3")).isTrue();
  }

  @Test
  void parstUnnummerierteBenannteAnlage() {
    // Ein Gesetz mit einer einzigen Anlage benennt sie nach der Vorschrift, zu der sie gehört.
    // Der Zusatz identifiziert die Anlage und ist nicht selbst Ziel — sonst würde das darin
    // genannte „§ 2“ als Ziel gelesen und die falsche Norm geändert.
    var anlage = StellenParser.parse("Die Anlage zu § 2 Absatz 4 Satz 1").orElseThrow();
    assertThat(anlage.anzeigeText()).isEqualTo("Anlage");
    assertThat(anlage.anlagenEnbez()).contains("Anlage");
    assertThat(anlage.paragraph()).isEmpty();

    // Nummerierte Anlagen bleiben unverändert.
    assertThat(StellenParser.parse("Anlage 8 Nummer 1 Buchstabe b").orElseThrow().anzeigeText())
        .isEqualTo("Anlage 8 Nummer 1 Buchstabe b");
  }

  @Test
  void ignoriertChapeauZusatz() {
    // „in der Angabe vor Nummer 1“ ist ein verfeinernder Zusatz ohne eigene Komponente.
    assertThat(
            StellenParser.parse("Absatz 1 in der Angabe vor Nummer 1").orElseThrow().anzeigeText())
        .isEqualTo("Absatz 1");
  }

  /**
   * „In der Einleitung“ (Rheinland-Pfalz) ist ein Chapeau-Lokator wie „im Satzteil vor Nummer 1“:
   * Er nennt keinen Bezugspunkt und trägt deshalb keine eigene Komponente — die Operation läuft auf
   * der Stelle, die der Rahmen bezeichnet.
   */
  @Test
  void einleitungIstEinChapeauLokator() {
    assertThat(StellenParser.istNurChapeau("In der Einleitung")).isTrue();
    assertThat(StellenParser.istNurChapeau("Im Eingangssatz")).isTrue();
    assertThat(StellenParser.istNurChapeau("in dem Einleitungssatz")).isTrue();
    // Ein Wort, das bloß so anfängt, ist keiner.
    assertThat(StellenParser.istNurChapeau("In der Einleitungsformel")).isFalse();
  }
}
