package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextBereinigerTest {

  @Test
  void entferntKolumnentitelUndSeitenzahlen() {
    var roh =
        """
        Erster Satz.
        2397 Bundesgesetzblatt Jahrgang 2020 Teil I Nr. 52, ausgegeben zu Bonn am 18. November 2020
        Das Bundesgesetzblatt im Internet: www.bundesgesetzblatt.de | Ein Service des Bundesanzeiger Verlag
        42
        Zweiter Satz.""";

    assertThat(TextBereiniger.bereinige(roh)).isEqualTo("Erster Satz.\nZweiter Satz.");
  }

  @Test
  void ziehtSilbentrennungZusammen() {
    assertThat(TextBereiniger.bereinige("die Bundes-\nregierung")).isEqualTo("die Bundesregierung");
  }

  @Test
  void ziehtSilbentrennungUeberLeerzeilenZusammen() {
    assertThat(TextBereiniger.bereinige("verhält-\n\n\nnismäßig")).isEqualTo("verhältnismäßig");
  }

  @Test
  void verbindetUmbrocheneKompositaMitBindestrich() {
    assertThat(TextBereiniger.bereinige("der Coronavirus-\nKrankheit-2019"))
        .isEqualTo("der Coronavirus-Krankheit-2019");
  }

  @Test
  void erhaeltSuspensivstricheVorKonjunktionen() {
    assertThat(TextBereiniger.bereinige("Wirk-, Ausgangs-\nund Hilfsstoffe"))
        .isEqualTo("Wirk-, Ausgangs-\nund Hilfsstoffe");
  }

  @Test
  void erhaeltGedankenstricheOhneVorangehendenBuchstaben() {
    assertThat(TextBereiniger.bereinige("Preisbildung und -\ngestaltung"))
        .isEqualTo("Preisbildung und -\ngestaltung");
  }
}
