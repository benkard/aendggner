package eu.mulk.aendggner.anwendung;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SatzTeilerTest {

  @Test
  void teiltEinfacheSaetze() {
    assertThat(SatzTeiler.teileTexte("Erster Satz. Zweiter Satz. Dritter Satz."))
        .containsExactly("Erster Satz.", "Zweiter Satz.", "Dritter Satz.");
  }

  @Test
  void teiltNichtBeiAbkuerzungen() {
    assertThat(
            SatzTeiler.teileTexte(
                "Die Meldung erfolgt nach Abs. 2 und § 5 Nr. 3 des Gesetzes. Sie ist"
                    + " schriftlich abzugeben."))
        .containsExactly(
            "Die Meldung erfolgt nach Abs. 2 und § 5 Nr. 3 des Gesetzes.",
            "Sie ist schriftlich abzugeben.");
  }

  @Test
  void teiltNichtBeiDatumsangaben() {
    assertThat(
            SatzTeiler.teileTexte(
                "Die Regelung gilt bis zum 31. März 2021. Danach tritt sie außer Kraft."))
        .containsExactly(
            "Die Regelung gilt bis zum 31. März 2021.", "Danach tritt sie außer Kraft.");
  }

  @Test
  void teiltVorZitatUndKlammer() {
    assertThat(SatzTeiler.teileTexte("Es gilt Satz 1. „Zitat folgt.“ (Klammer folgt.)")).hasSize(3);
  }

  @Test
  void teiltAnAmtlichenSatznummern() {
    // Bayerisches Landesrecht: hochgestellte Satznummern sind exakte Satzgrenzen — auch dort,
    // wo die Heuristik versagen würde (Abkürzung am Satzende).
    var text = "¹Die Förderung erfolgt gem. Art. 26 und 27. ²Sie ist als Teil zu bewahren.";
    assertThat(SatzTeiler.teileTexte(text))
        .containsExactly(
            "¹Die Förderung erfolgt gem. Art. 26 und 27.", "²Sie ist als Teil zu bewahren.");
    assertThat(SatzTeiler.nummerVonSatz("²Sie ist als Teil zu bewahren.")).isEqualTo(2);
    assertThat(SatzTeiler.nummerVonSatz("Ohne Nummer.")).isNull();
  }

  @Test
  void fussnotenmarkerIstKeinSatzanfang() {
    // „Enteignung⁶)“ mitten im Satz und die Fußnotenzeile „⁶) …“ eröffnen keinen neuen Satz.
    var text =
        "¹Es gilt das Gesetz über die entschädigungspflichtige Enteignung⁶) entsprechend."
            + " ²Näheres regelt die Verordnung.\n⁶) [Amtl. Anm.:] BayRS 2141-1-I";
    var saetze = SatzTeiler.teileTexte(text);
    assertThat(saetze).hasSize(2);
    assertThat(saetze.get(0)).contains("Enteignung⁶)");
    assertThat(saetze.get(1)).startsWith("²Näheres").contains("⁶) [Amtl. Anm.:]");
  }

  @Test
  void behandeltAufzaehlungenAlsTeilDesSatzes() {
    var text =
        "Die Erprobung umfasst\n"
            + "  1. das Einlesen,\n"
            + "  2. die Anwendung und\n"
            + "  3. die Ausgabe. Weitere Einzelheiten regelt die Verordnung.";
    var saetze = SatzTeiler.teileTexte(text);

    assertThat(saetze).hasSize(2);
    assertThat(saetze.get(0)).contains("2. die Anwendung und");
    assertThat(saetze.get(1)).isEqualTo("Weitere Einzelheiten regelt die Verordnung.");
  }

  @Test
  void aufzaehlungsmarkeAmZeilenanfangBeendetKeinenSatz() {
    // Anders als im Test darüber beginnen die Glieder großgeschrieben — erst die Marke am
    // Zeilenanfang unterscheidet sie von einer Zahl am Satzende.
    var text =
        "Dem Rundfunkrat obliegen insbesondere folgende Aufgaben\n"
            + "1. Erlaß von Satzungen,\n"
            + "2. Wahl der Intendantin oder des Intendanten.\n"
            + "Vor Beschlüssen ist Gelegenheit zur Stellungnahme zu geben.";
    var saetze = SatzTeiler.teileTexte(text);

    assertThat(saetze).hasSize(2);
    assertThat(saetze.get(0)).contains("2. Wahl der Intendantin");
    assertThat(saetze.get(1)).startsWith("Vor Beschlüssen");
  }

  @Test
  void zahlAmSatzendeBeendetDenSatz() {
    var saetze = SatzTeiler.teileTexte("Die Frist beträgt 30. Der Lauf beginnt am Folgetag.");

    assertThat(saetze).containsExactly("Die Frist beträgt 30.", "Der Lauf beginnt am Folgetag.");
  }

  @Test
  void fundstellenAbkuerzungTeiltDenSatzNicht() {
    var saetze =
        SatzTeiler.teileTexte(
            "Es gilt das Gesetz vom 23. Juni 2021 (BGBl. I S. 1982) in der geltenden Fassung."
                + " Näheres regelt die Satzung.");

    assertThat(saetze).hasSize(2);
    assertThat(saetze.get(0)).endsWith("in der geltenden Fassung.");
  }

  @Test
  void paragraphenzeichenEroeffnetEinenSatz() {
    var saetze =
        SatzTeiler.teileTexte(
            "Die Angebote können zusammengefasst werden. § 27 Absatz 2 bleibt unberührt.");

    assertThat(saetze).containsExactly(
        "Die Angebote können zusammengefasst werden.", "§ 27 Absatz 2 bleibt unberührt.");
  }
}
