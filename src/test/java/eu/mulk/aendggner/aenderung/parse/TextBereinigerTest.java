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
  void ziehtMarkerlosNichtBeiKurzemStichwortVorEingerueckterDefinitionZusammen() {
    // Definitionslisten-Muster (neues BGBl-Format, „2a. …Siegel“ + hängend eingerückte
    // Definition): die Stichwort-Zeile endet ohne Trailing-Space, ist aber deutlich kürzer als
    // die umgebenden Volltextzeilen — kein Wort wird getrennt, der Umbruch ist bewusst.
    var roh =
        "1. „Betriebsstoff“ jeder Bestandteil einer Ware, der wiederholt verbraucht wird und ersetzt"
            + " oder aufgefüllt werden muss, damit die Ware ordnungsgemäß funktioniert. \n"
            + "2. „Haltbarkeit“ die Fähigkeit der Waren, ihre erforderlichen Funktionen und ihre"
            + " Leistung bei normaler Verwendung über einen längeren Zeitraum zu bewahren. \n"
            + "3. „Zertifizierungssystem“ ein System der Überprüfung durch Dritte, durch das"
            + " bestätigt wird, dass ein Produkt bestimmten Anforderungen entspricht. \n"
            + "2a. unzulässiges Anbringen eines Nachhaltigkeitssiegels\n"
            + "das Anbringen eines Nachhaltigkeitssiegels, das weder auf einem Zertifizierungssystem"
            + " beruht noch von \n"
            + "staatlichen Stellen festgesetzt wurde;";

    assertThat(TextBereiniger.bereinige(roh))
        .contains("Nachhaltigkeitssiegels\ndas Anbringen eines Nachhaltigkeitssiegels");
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
