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

  @Test
  void entferntKopfzeilenDesNeuenBgblFormats() {
    var roh =
        "Erster Satz. \n"
            + "Seite 2 von 5 Bundesgesetzblatt Jahrgang 2026 Teil I Nr. 43, ausgegeben zu"
            + " Bonn am 19. Februar 2026 \n"
            + "Zweiter Satz. ";

    assertThat(TextBereiniger.bereinige(roh)).isEqualTo("Erster Satz.\nZweiter Satz.");
  }

  @Test
  void entferntSeitenmarkerVonEntwuerfen() {
    assertThat(TextBereiniger.bereinige("Erster Satz. \n - 10 -   \nZweiter Satz. "))
        .isEqualTo("Erster Satz.\nZweiter Satz.");
  }

  @Test
  void entferntDrucksachenKopfzeilen() {
    var roh =
        "Erster Satz. \n"
            + "Drucksache 21/6178 – 2 – Deutscher Bundestag – 21. Wahlperiode \n"
            + "Deutscher Bundestag – 21. Wahlperiode – 3 – Drucksache 21/6178 \n"
            + "Zweiter Satz. ";

    assertThat(TextBereiniger.bereinige(roh)).isEqualTo("Erster Satz.\nZweiter Satz.");
  }

  @Test
  void ziehtMarkerloseSilbentrennungZusammen() {
    // Bundestags-Drucksachen: reguläre Umbrüche enden mit Leerzeichen, Trennungen nicht.
    var roh = "unterhalb eines Schwel\nlenwertes von 50 \nWohnungen. ";

    assertThat(TextBereiniger.bereinige(roh))
        .isEqualTo("unterhalb eines Schwellenwertes von 50\nWohnungen.");
  }

  @Test
  void ziehtOhneTrailingSpaceKonventionKeineMarkerlosenTrennungenZusammen() {
    // Handgeschriebene Klartextdateien: kein Trailing-Space-Signal → kein Join.
    var roh = "das zuletzt geändert worden\nist, wird wie folgt geändert:";

    assertThat(TextBereiniger.bereinige(roh)).isEqualTo(roh);
  }

  @Test
  void ziehtMarkerlosNichtVorKonjunktionenZusammen() {
    var roh = "die Wirk\nund Hilfsstoffe sind wichtig. \nZweite Zeile endet mit Leerzeichen. ";

    assertThat(TextBereiniger.bereinige(roh)).startsWith("die Wirk\nund Hilfsstoffe");
  }

  @Test
  void repariertInvertierteZitatzeichenAnAbsatzmarkern() {
    // BMJV-Vorlagen zeichnen das hängende „ nach dem Absatzmarker bzw. der Paragraphenangabe.
    assertThat(TextBereiniger.bereinige("(1) „ Ungeachtet des § 8 gilt.“"))
        .isEqualTo("„(1) Ungeachtet des § 8 gilt.“");
    assertThat(TextBereiniger.bereinige("§ 19„\nAußerkrafttreten"))
        .isEqualTo("„§ 19\nAußerkrafttreten");
  }
}
