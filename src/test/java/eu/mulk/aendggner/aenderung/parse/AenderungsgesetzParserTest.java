package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.gesetz.Gesetz;
import java.util.List;
import org.junit.jupiter.api.Test;

class AenderungsgesetzParserTest {

  private static final Gesetz BAYJG =
      new Gesetz("BayJG", "Bayerisches Jagdgesetz", null, List.of(), List.of());

  private static final Gesetz UWG =
      new Gesetz(
          "UWG", "Gesetz gegen den unlauteren Wettbewerb", "UWG-Langtitel", List.of(), List.of());

  @Test
  void teiltParagraphenGegliedertesAenderungsgesetz() {
    // Bayerischer Stil: das Änderungsgesetz gliedert sich in §§; § 1 und § 2 treffen dasselbe
    // Stammgesetz, § 3 ein anderes; Inkrafttretens-§ ohne Änderungsformel wird nicht gewählt.
    var text =
        """
        § 1
        Änderung des
        Bayerischen Jagdgesetzes
        Das Bayerische Jagdgesetz (BayJG) in der in der Bayerischen Rechtssammlung (BayRS 792-1-W) veröffentlichten bereinigten Fassung, das zuletzt durch § 5 des Gesetzes vom 23. Juli 2024 (GVBl. S. 247) geändert worden ist, wird wie folgt geändert:
        1. Art. 1 wird wie folgt geändert:
        a) In Abs. 2 Satzteil vor Nr. 1 wird die Angabe „Bundesjagdgesetz“ durch die Angabe „BJagdG“ ersetzt.
        b) Fußnote 1 wird aufgehoben.
        § 2
        Weitere Änderung des
        Bayerischen Jagdgesetzes
        Das Bayerische Jagdgesetz (BayJG) in der in der Bayerischen Rechtssammlung (BayRS 792-1-W) veröffentlichten bereinigten Fassung, das zuletzt durch § 1 dieses Gesetzes geändert worden ist, wird wie folgt geändert:
        Art. 28 Abs. 1 wird wie folgt geändert:
        1. Satz 4 wird aufgehoben.
        2. Satz 5 wird Satz 4.
        § 3
        Änderung des
        Bayerischen Umweltinformationsgesetzes
        Art. 7 Abs. 2 des Bayerischen Umweltinformationsgesetzes (BayUIG) vom 8. Dezember 2006 (GVBl. S. 933, BayRS 2129-1-4-U), das zuletzt durch § 10 des Gesetzes vom 23. Dezember 2024 (GVBl. S. 605) geändert worden ist, wird wie folgt geändert:
        1. Der Wortlaut wird Satz 1.
        § 4
        Inkrafttreten
        Dieses Gesetz tritt am 1. April 2026 in Kraft.
        """;

    var ergebnis = new AenderungsgesetzParser().parse(text, BAYJG, null);

    assertThat(ergebnis.artikel()).containsExactly("1", "2");
    // § 2: Vorspann-Rahmen „Art. 28 Abs. 1“ wird Kontext der Punkte.
    var aufhebung =
        ergebnis.befehle().stream()
            .filter(b -> b instanceof Aenderungsbefehl.Aufhebung)
            .map(Aenderungsbefehl::stelle)
            .map(s -> s.anzeigeText())
            .toList();
    assertThat(aufhebung).contains("Art. 28 Absatz 1 Satz 4");
  }

  @Test
  void eingebettetesRahmenzielSetztKontext() {
    var bayUig =
        new Gesetz(
            "BayUIG", "Bayerisches Umweltinformationsgesetz", null, List.of(), List.of());
    var text =
        """
        § 3
        Änderung des
        Bayerischen Umweltinformationsgesetzes
        Art. 7 Abs. 2 des Bayerischen Umweltinformationsgesetzes (BayUIG) vom 8. Dezember 2006 (GVBl. S. 933, BayRS 2129-1-4-U), das zuletzt durch § 10 des Gesetzes vom 23. Dezember 2024 (GVBl. S. 605) geändert worden ist, wird wie folgt geändert:
        1. Der Wortlaut wird Satz 1.
        2. Folgender Satz 2 wird angefügt:
        „²Die Voraussetzungen nach Satz 1 Nr. 2 sind insbesondere gegeben.“
        """;

    var ergebnis = new AenderungsgesetzParser().parse(text, bayUig, null);

    assertThat(ergebnis.artikel()).containsExactly("3");
    // Das eingebettete Ziel „Art. 7 Abs. 2“ ist Kontext aller Punkte — nicht etwa das „§ 10“
    // aus der Zitierkette der Änderungshistorie.
    assertThat(ergebnis.befehle())
        .allSatisfy(
            befehl ->
                assertThat(befehl.stelle().anzeigeText()).startsWith("Art. 7 Absatz 2"));
  }

  @Test
  void artikelUeberschriftenUnterdrueckenParagraphenModus() {
    // Ein Bundes-Änderungsgesetz mit unzitiert abgedrucktem Ablösegesetz enthält freistehende
    // „§ N“-Zeilen — sie dürfen nicht als äußere Gliederung gedeutet werden.
    var text =
        """
        Artikel 1
        Das Gesetz gegen den unlauteren Wettbewerb vom 3. Juli 2004, das zuletzt geändert worden ist, wird wie folgt geändert:
        1. In § 5 wird die Angabe „alt“ durch die Angabe „neu“ ersetzt.
        Artikel 2
        § 1
        Dieses Gesetz tritt am 1. Januar 2027 in Kraft.
        """;

    var ergebnis = new AenderungsgesetzParser().parse(text, UWG, null);

    assertThat(ergebnis.artikel()).containsExactly("1");
    assertThat(ergebnis.befehle()).hasSize(1);
    assertThat(ergebnis.befehle().get(0)).isInstanceOf(Aenderungsbefehl.Ersetzung.class);
  }

  @Test
  void artikelFilterImParagraphenModusVerlangtAenderungsformel() {
    var text =
        """
        § 1
        Das Bayerische Jagdgesetz (BayJG), das zuletzt geändert worden ist, wird wie folgt geändert:
        1. In Art. 1 wird die Angabe „alt“ durch die Angabe „neu“ ersetzt.
        § 2
        Dieses Gesetz tritt am 1. April 2026 in Kraft.
        """;

    // § 2 ist der Inkrafttretens-§ ohne Änderungsformel: der Filter wählt ihn nicht.
    var ergebnis = new AenderungsgesetzParser().parse(text, BAYJG, "2");
    assertThat(ergebnis.artikel()).isEmpty();

    var eins = new AenderungsgesetzParser().parse(text, BAYJG, "1");
    assertThat(eins.artikel()).containsExactly("1");
  }
}
