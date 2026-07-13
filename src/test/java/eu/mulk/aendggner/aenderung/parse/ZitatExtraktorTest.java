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
  void stelltZitateWiederHer() {
    var original = "Das Wort „alt“ wird durch das Wort „neu“ ersetzt.";
    var ergebnis = ZitatExtraktor.extrahiere(original);

    assertThat(ergebnis.stelleZitateWiederHer(ergebnis.text())).isEqualTo(original);
  }
}
