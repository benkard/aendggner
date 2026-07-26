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
  void entferntGvblKolumnentitel() {
    // Gerade Seiten kleben die Seitenzahl ohne Zwischenraum an die Jahreszahl („…/202676“);
    // eine am Seitenwechsel unterbrochene Silbentrennung heilt nach der Kopfentfernung.
    var roh =
        "Einheiten über Online-Plattformen für die kurz-\n"
            + "Bayerisches Gesetz- und Verordnungsblatt Nr. 6/202676\n"
            + "fristige Vermietung von Unterkünften anbietet, \n"
            + "Bayerisches Gesetz- und Verordnungsblatt Nr. 6/2026 77\n"
            + "Zweiter Satz. ";

    assertThat(TextBereiniger.bereinige(roh))
        .isEqualTo(
            "Einheiten über Online-Plattformen für die kurzfristige Vermietung von Unterkünften"
                + " anbietet,\nZweiter Satz.");
  }

  @Test
  void entferntLandtagsKolumnentitel() {
    var roh =
        "Bayerischer Landtag \n"
            + "19. Wahlperiode 28.01.2026  Drucksache 19/9707 \n"
            + "Erster Satz. \n"
            + "Drucksache 19/9707 Bayerischer Landtag 19. Wahlperiode Seite 2 \n"
            + "Zweiter Satz. \n"
            + "19. Wahlperiode Drucksache 19/9707 ";

    assertThat(TextBereiniger.bereinige(roh)).isEqualTo("Erster Satz.\nZweiter Satz.");
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
  void ziehtMarkerlosNichtVorAufzaehlungsmarkerZusammen() {
    // Ein Marker eröffnet eine bewusste Strukturzeile — auch wenn die Vorzeile markerlos mit
    // einem Buchstaben endet und die Folgezeile klein beginnt („…und“ + „d) die Überwachung“).
    var roh =
        "es ist der Entzug der Verwendung im Fall von Verstößen gegen die Anforderungen"
            + " vorgesehen und\n"
            + "d) die Überwachung der Einhaltung der Anforderungen durch einen Dritten erfolgt. \n"
            + "Nächste Zeile endet mit Leerzeichen. ";

    assertThat(TextBereiniger.bereinige(roh)).contains("und\nd) die Überwachung");
  }

  @Test
  void ziehtMarkerlosNichtBeiGeometrischHartemZeilenendeZusammen() {
    // Volle Spaltenbreite in Zeichen, aber laut Geometrie-Marker des FontgroessenFilters
    // deutlich vor dem Rand endend (lange Stichwort-Zeile wie Nummer 4c im UWG-Anhang):
    // der Umbruch ist bewusst und bleibt erhalten.
    var roh =
        "4c. Aussagen zu Umweltauswirkungen bei Kompensation von Treibhausgasemissionen\uE000\n"
            + "das Treffen einer Aussage, die sich auf die Kompensation von"
            + " Treibhausgasemissionen gründet. ";

    assertThat(TextBereiniger.bereinige(roh)).contains("Treibhausgasemissionen\ndas Treffen");
  }

  @Test
  void reflowtGeometrischWeicheUmbruecheUndErhaeltHarte() {
    var roh =
        "Erste Zeile des Absatzes wird \uE001\n"
            + "fortgesetzt und endet hier. \uE000\n"
            + "Nächste eigene Zeile. \uE000";

    assertThat(TextBereiniger.bereinige(roh))
        .isEqualTo("Erste Zeile des Absatzes wird fortgesetzt und endet hier.\nNächste eigene Zeile.");
  }

  @Test
  void strukturzeilenBleibenTrotzWeicherKlassifikationEigeneZeilen() {
    // Gleich breite, zentrierte Artikel-Überschriften in Serie bilden ein Schein-
    // Ausrichtungs-Cluster und werden fälschlich als weich klassifiziert — der Parser braucht
    // sie aber allein auf der Zeile (teileInArtikel).
    var roh =
        "Artikel 2\uE001\n"
            + "Inkrafttreten\uE000\n"
            + "Dieses Gesetz tritt am Tag nach der Verkündung in Kraft. \uE000";

    assertThat(TextBereiniger.bereinige(roh))
        .isEqualTo("Artikel 2\nInkrafttreten\nDieses Gesetz tritt am Tag nach der Verkündung in Kraft.");
  }

  @Test
  void ziehtSilbentrennungAuchBeiHartemZeilenendeZusammen() {
    // Flattersatz (GVOBl. Schl.-H.): Die Randerkennung findet keinen Ausrichtungs-Cluster und
    // meldet auch volle Zeilen als hartes Zeilenende. Klebt der Trennstrich ohne Trailing-
    // Whitespace am Zeilenende, ist er dennoch eine Silbentrennung.
    assertThat(TextBereiniger.bereinige("Teilnahme mittels Ton-Bild-Übertra-\ngung. "))
        .isEqualTo("Teilnahme mittels Ton-Bild-Übertragung.");
    // Mit Trailing-Whitespace bleibt das harte Zeilenende ein bewusster Umbruch.
    assertThat(TextBereiniger.bereinige("Wirk-, Ausgangs- \nund Hilfsstoffe "))
        .isEqualTo("Wirk-, Ausgangs-\nund Hilfsstoffe");
  }

  @Test
  void streiftFussnotenmarkerVonArtikelUeberschriften() {
    // Schleswig-Holstein verweist an der Artikel-Überschrift auf die Gl.-Nr.-Fußnote; steht die
    // Einleitung in derselben Zeile, wird sie dabei wieder abgetrennt.
    assertThat(TextBereiniger.bereinige("Artikel 1 ¹)\nDie Gemeindeordnung"))
        .isEqualTo("Artikel 1\nDie Gemeindeordnung");
    assertThat(TextBereiniger.bereinige("Artikel 2 ²) Die Kreisordnung"))
        .isEqualTo("Artikel 2\nDie Kreisordnung");
  }

  @Test
  void schneidetDenBerlinerSeitenkopfAusDemFliesstext() {
    // Im GVBl. für Berlin steht der laufende Seitenkopf nicht auf einer eigenen Zeile, sondern
    // klebt samt Seitenzahl mitten im Text der folgenden Spalte.
    var roh =
        "zur Sicherung des Betriebs\n"
            + "235Gesetz- und Verordnungsblatt für Berlin      82. Jahrgang      Nr. 17"
            + "     11. Juni 2026 von Unterkünften.";

    assertThat(TextBereiniger.bereinige(roh))
        .isEqualTo("zur Sicherung des Betriebs\nvon Unterkünften.");
  }

  @Test
  void ziehtSachnummernMitLeerzeichenZusammen() {
    // Niedersachsen zitiert eingeschobene Paragraphen als „§ 2 a“; kanonisch ist „§ 2a“.
    assertThat(TextBereiniger.bereinige("Nach § 2 wird der folgende § 2 a eingefügt:"))
        .isEqualTo("Nach § 2 wird der folgende § 2a eingefügt:");
    assertThat(TextBereiniger.bereinige("Art. 28 a Abs. 1")).isEqualTo("Art. 28a Abs. 1");
    // Aufzählungsmarker des Änderungsgesetzes bleiben unberührt, ebenso echte Folgewörter.
    assertThat(TextBereiniger.bereinige("Nach § 8 c) In Absatz 2"))
        .isEqualTo("Nach § 8 c) In Absatz 2");
    assertThat(TextBereiniger.bereinige("§ 5 des Gesetzes")).isEqualTo("§ 5 des Gesetzes");
  }

  @Test
  void entferntSeitenkopfDesGvoblSchleswigHolstein() {
    var roh =
        """
        Erster Satz.
        Gesetz- und Verordnungsblatt für Schleswig-Holstein
        2026/27 vom 30. März
        Zweiter Satz.""";

    assertThat(TextBereiniger.bereinige(roh)).isEqualTo("Erster Satz.\nZweiter Satz.");
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
