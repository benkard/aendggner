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
    assertThat(stellen).extracting(Stelle::anzeigeText)
        .containsExactly("§ 3 Absatz 1 Satz 2", "§ 3 Absatz 4");
  }

  @Test
  void parstMehrfachMitSatzTiefe() {
    var stellen = StellenParser.parseMehrfach("§ 20 Absatz 1 Satz 1 und Absatz 2 Satz 2");
    assertThat(stellen).extracting(Stelle::anzeigeText)
        .containsExactly("§ 20 Absatz 1 Satz 1", "§ 20 Absatz 2 Satz 2");
  }

  @Test
  void parstMehrfachMitKomma() {
    var stellen = StellenParser.parseMehrfach("§ 1, § 2 und § 3");
    assertThat(stellen).extracting(Stelle::anzeigeText)
        .containsExactly("§ 1", "§ 2", "§ 3");
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
    assertThat(stellen).extracting(Stelle::anzeigeText)
        .containsExactly("§ 7 Absatz 1", "§ 7 Absatz 5");
  }

  @Test
  void bloßeNummerNachSatz() {
    var stellen = StellenParser.parseMehrfach("Absatz 1 Satz 1 und 2");
    assertThat(stellen).extracting(Stelle::anzeigeText)
        .containsExactly("Absatz 1 Satz 1", "Absatz 1 Satz 2");
  }
}
