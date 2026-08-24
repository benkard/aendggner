// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz.gii;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GiiXmlLoaderTest {

  private static Path fixture() {
    return Path.of("src/test/resources/eu/mulk/aendggner/mini-gii.xml");
  }

  @Test
  void liestMetadatenUndNormen() throws Exception {
    var gesetz = new GiiXmlLoader().load(fixture());

    assertThat(gesetz.jurabk()).isEqualTo("TestG");
    assertThat(gesetz.langue()).isEqualTo("Gesetz zur Erprobung des ÄndGgners");
    assertThat(gesetz.kurzue()).isEqualTo("Testgesetz");
    // Die Rahmen-Norm ohne enbez wird übersprungen.
    assertThat(gesetz.normen()).hasSize(4);
    assertThat(gesetz.normen().get(0).enbez()).isEqualTo("Inhaltsübersicht");
    assertThat(gesetz.norm("§ 1")).isPresent();
    assertThat(gesetz.norm("§ 3")).isEmpty();
  }

  @Test
  void liestAbsaetzeMitNummern() throws Exception {
    var gesetz = new GiiXmlLoader().load(fixture());
    var paragraph1 = gesetz.norm("§ 1").orElseThrow();

    assertThat(paragraph1.titel()).isEqualTo("Zweck des Gesetzes");
    assertThat(paragraph1.gliederung()).isNotNull();
    assertThat(paragraph1.gliederung().bezeichnung()).isEqualTo("1. Abschnitt");
    assertThat(paragraph1.absaetze()).hasSize(2);
    assertThat(paragraph1.absaetze().get(0).nummer()).isEqualTo("1");
    assertThat(paragraph1.absaetze().get(0).text())
        .isEqualTo("Zweck dieses Gesetzes ist die Erprobung. Die Erprobung erfolgt sorgfältig.");
  }

  @Test
  void flattetAufzaehlungenAlsEingerueckteZeilen() throws Exception {
    var gesetz = new GiiXmlLoader().load(fixture());
    var absatz2 = gesetz.norm("§ 1").orElseThrow().absaetze().get(1);

    assertThat(absatz2.nummer()).isEqualTo("2");
    var zeilen = absatz2.text().lines().toList();
    assertThat(zeilen.get(0)).isEqualTo("Die Erprobung umfasst");
    assertThat(zeilen).anyMatch(z -> z.strip().startsWith("1. das Einlesen"));
    assertThat(zeilen).anyMatch(z -> z.strip().startsWith("a) Ersetzungen"));
    // Fließtext nach der Aufzählung bleibt erhalten.
    assertThat(absatz2.text()).contains("Weitere Einzelheiten regelt die Rechtsverordnung.");
    // Geschachtelte Aufzählung ist tiefer eingerückt als die äußere.
    var aeussere =
        zeilen.stream().filter(z -> z.strip().startsWith("1.")).findFirst().orElseThrow();
    var innere = zeilen.stream().filter(z -> z.strip().startsWith("a)")).findFirst().orElseThrow();
    assertThat(einrueckung(innere)).isGreaterThan(einrueckung(aeussere));
  }

  @Test
  void trenntKurzueberschriftUndDefinitionInAufzaehlungen() throws Exception {
    var gesetz = new GiiXmlLoader().load(fixture());
    var anhang = gesetz.norm("Anhang").orElseThrow().absaetze().get(0).text();

    // Zwei <LA>-Geschwister in einem <DD> (Kurzüberschrift + Definitionstext, wie im UWG-Anhang
    // oder in § 2 IfSG) sind eigene Zeilen — nicht nahtlos verklebter Fließtext. Die Folgezeile
    // ist tiefer eingerückt als die Aufzählungszeile, damit die Stellenauflösung sie als
    // Kindzeile der Einheit erkennt.
    assertThat(anhang).doesNotContain("Erprobungdie");
    assertThat(anhang)
        .contains(
            "1. Irreführung über die Erprobung\n"
                + "    die unwahre Angabe, die Erprobung sei abgeschlossen;");
    // Auch mit geschachtelter Aufzählung im Definitionstext bleibt die Zeilenstruktur erhalten.
    assertThat(anhang)
        .contains("  2. Verheimlichung von Prüfschritten\n    das Verschweigen, dass")
        .contains("    a) Zwischenschritte oder");
  }

  @Test
  void flattetTabellenZuZeilen() throws Exception {
    var gesetz = new GiiXmlLoader().load(fixture());
    var inhaltsuebersicht = gesetz.norm("Inhaltsübersicht").orElseThrow();

    assertThat(inhaltsuebersicht.absaetze()).hasSize(1);
    assertThat(inhaltsuebersicht.absaetze().get(0).text())
        .contains("§ 1 | Zweck des Gesetzes")
        .contains("§ 2 | Begriffsbestimmungen");
  }

  @Test
  void erkenntWeggefalleneNormen() throws Exception {
    var gesetz = new GiiXmlLoader().load(fixture());

    assertThat(gesetz.norm("§ 2").orElseThrow().weggefallen()).isTrue();
    assertThat(gesetz.norm("§ 1").orElseThrow().weggefallen()).isFalse();
  }

  private static int einrueckung(String zeile) {
    return zeile.length() - zeile.stripLeading().length();
  }
}
