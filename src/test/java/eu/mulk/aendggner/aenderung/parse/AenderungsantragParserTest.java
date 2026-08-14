package eu.mulk.aendggner.aenderung.parse;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import org.junit.jupiter.api.Test;

class AenderungsantragParserTest {

  /** Der bayerische Beleg (Ltg-Drs. 19/10365), auf den Beschlussteil verkürzt. */
  private static final String ANTRAG =
      """
      Änderungsantrag
      der Abgeordneten Katharina Schulze und Fraktion (BÜNDNIS 90/DIE GRÜNEN)
      hier: Goldschakal nicht ins Jagdrecht aufnehmen
      (Drs. 19/9707)
      Der Landtag wolle beschließen:
      In § 3 Nr. 22 wird § 18 Nr. 1 wie folgt geändert:
      1. In Nr. 1.29 die Angabe „ ,“ am Ende durch die Angabe „ ;“ ersetzt.
      2. Nr. 1.30 aufgehoben.
      Begründung:
      Die Aufnahme des Goldschakals ins Jagdrecht ist nicht zielführend.
      """;

  @Test
  void liestBeideBefehleAusDemBeschlussteil() {
    var ergebnis = AenderungsantragParser.parse(ANTRAG);

    assertThat(ergebnis.warnungen()).isEmpty();
    assertThat(ergebnis.befehle()).hasSize(2);

    var erster = ergebnis.befehle().get(0);
    assertThat(erster.drucksachenStelle().container()).isEqualTo("§ 3");
    assertThat(erster.drucksachenStelle().punktPfad()).containsExactly("22");
    assertThat(erster.zitatStelle().anzeigeText()).isEqualTo("§ 18 Nummer 1");
    // Die elliptische Antragsform („… ersetzt.“ ohne „wird“) wird zum vollständigen Satz ergänzt.
    assertThat(erster.befehl()).isInstanceOf(Aenderungsbefehl.Ersetzung.class);
    var ersetzung = (Aenderungsbefehl.Ersetzung) erster.befehl();
    assertThat(ersetzung.alt()).isEqualTo(",");
    assertThat(ersetzung.neu()).isEqualTo(";");
    assertThat(ersetzung.amEnde()).isTrue();
    assertThat(ersetzung.stelle().anzeigeText()).isEqualTo("Nummer 1.29");

    var zweiter = ergebnis.befehle().get(1);
    assertThat(zweiter.befehl()).isInstanceOf(Aenderungsbefehl.Aufhebung.class);
    assertThat(zweiter.befehl().stelle().anzeigeText()).isEqualTo("Nummer 1.30");
  }

  /** Der Begründungsteil steht hinter den Befehlen und darf keine erzeugen. */
  @Test
  void begruendungErzeugtKeineBefehle() {
    var mitLangerBegruendung =
        ANTRAG + "1. Der Goldschakal ist in Anhang V aufgeführt.\n2. Er ist kein jagdbares Wild.\n";
    assertThat(AenderungsantragParser.parse(mitLangerBegruendung).befehle()).hasSize(2);
  }

  @Test
  void ohneBeschlussformelWirdGewarntStattStillGeschwiegen() {
    var ergebnis = AenderungsantragParser.parse("Änderungsantrag\nIrgendein Fließtext.\n");

    assertThat(ergebnis.befehle()).isEmpty();
    assertThat(ergebnis.warnungen()).singleElement().asString().contains("wolle beschließen");
  }

  /** Die Bundestagsform des Rahmens: „In Artikel 1 Nummer 3 …“. */
  @Test
  void erkenntArtikelAlsDrucksachenContainer() {
    var antrag =
        """
        Änderungsantrag
        Der Bundestag wolle beschließen:
        In Artikel 1 Nummer 3 wird § 9a Nr. 2 wie folgt geändert:
        1. Nr. 2.1 aufgehoben.
        """;
    var ergebnis = AenderungsantragParser.parse(antrag);

    assertThat(ergebnis.befehle()).hasSize(1);
    assertThat(ergebnis.befehle().get(0).drucksachenStelle().container()).isEqualTo("Artikel 1");
    assertThat(ergebnis.befehle().get(0).drucksachenStelle().punktPfad()).containsExactly("3");
  }
}
