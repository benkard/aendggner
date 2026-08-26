// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz.land;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.mulk.aendggner.gesetz.Fortschreibung;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Stand;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Der Beleg des Textausgebers ist der Rundlauf, nicht der Augenschein: Was er schreibt, muss der
 * Lader wieder zu demselben Gesetz lesen. Geprüft wird das an sämtlichen Klartext-Stammfassungen
 * des Beispielkorpus — dreizehn Länder, jede Eigenheit des Formats mindestens einmal (Superskripte
 * und „Art.“-Sigel in Bayern, titellose Paragraphen und ausgeschriebene Ordinale in Hessen, die
 * Nummern einer Anlage als eigene Normen in Berlin, Zwischentitel, Fußnoten, Inhaltsübersicht).
 */
class LandesRechtTextAusgeberTest {

  private static final Path SAMPLEDATA = Path.of("src/test/resources/sampledata");

  static Stream<Path> klartextFassungen() throws IOException {
    if (!Files.isDirectory(SAMPLEDATA)) {
      return Stream.of();
    }
    try (var pfade = Files.walk(SAMPLEDATA)) {
      return pfade
          .filter(p -> p.getFileName().toString().endsWith(".txt"))
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("klartextFassungen")
  void rundlaufErhaeltDasGesetz(Path datei) throws IOException {
    assumeTrue(Files.exists(datei), "Beispieldaten fehlen");
    var gelesen = new LandesRechtLoader().load(datei);

    var geschrieben = LandesRechtTextAusgeber.ausgeben(gelesen);
    var wiederGelesen = LandesRechtTextParser.parse(geschrieben);

    assertThat(wiederGelesen.langue()).isEqualTo(gelesen.langue());
    assertThat(wiederGelesen.jurabk()).isEqualTo(gelesen.jurabk());
    assertThat(wiederGelesen.kurzue()).isEqualTo(gelesen.kurzue());
    assertThat(bezeichnungen(wiederGelesen)).isEqualTo(bezeichnungen(gelesen));
    assertThat(wiederGelesen.gliederungen()).isEqualTo(gelesen.gliederungen());
    assertThat(wiederGelesen.stand()).isEqualTo(gelesen.stand());
    assertThat(wiederGelesen.fortschreibungen()).isEqualTo(gelesen.fortschreibungen());

    for (var soll : gelesen.normen()) {
      var ist = wiederGelesen.norm(soll.enbez()).orElseThrow();
      assertThat(ist.titel()).as("Titel von %s", soll.enbez()).isEqualTo(soll.titel());
      assertThat(ist.weggefallen())
          .as("Wegfall von %s", soll.enbez())
          .isEqualTo(soll.weggefallen());
      assertThat(ist.absaetze()).as("Absätze von %s", soll.enbez()).isEqualTo(soll.absaetze());
    }
  }

  /**
   * Der Kopf trägt das Gedächtnis der Kette: Standangabe und angewandte Hefte müssen den Rundlauf
   * überstehen, sonst wüsste die nächste Stufe nicht, was der Wortlaut schon trägt — und dasselbe
   * Heft ließe sich unbemerkt ein zweites Mal anwenden.
   */
  @Test
  void rundlaufErhaeltStandUndHefte() throws IOException {
    var datei = SAMPLEDATA.resolve("Brandenburg/FraktG-alt.txt");
    assumeTrue(Files.exists(datei), "Beispieldaten fehlen");
    var gelesen =
        new LandesRechtLoader()
            .load(datei)
            .mitFortschreibung(
                new Fortschreibung("Änderungsgesetz vom 22. April 2026", LocalDate.of(2026, 4, 22)))
            .mitFortschreibung(new Fortschreibung("Gesetzentwurf Drs. 7/4711", null));
    var mitStand =
        new Gesetz(
            gelesen.jurabk(),
            gelesen.langue(),
            gelesen.kurzue(),
            gelesen.normen(),
            gelesen.gliederungen(),
            Stand.aus("Zuletzt geändert durch Art. 2 G v. 15.12.2022 I Nr. 26"),
            gelesen.fortschreibungen());

    var wiederGelesen = LandesRechtTextParser.parse(LandesRechtTextAusgeber.ausgeben(mitStand));

    assertThat(wiederGelesen.stand()).isEqualTo(mitStand.stand());
    assertThat(wiederGelesen.stand().juengsteAenderung()).isEqualTo(LocalDate.of(2022, 12, 15));
    assertThat(wiederGelesen.fortschreibungen()).isEqualTo(mitStand.fortschreibungen());
    // Der Wortlautstand folgt dem jüngsten Heft, nicht der Standzeile: Wer fortschreibt, macht den
    // Wortlaut jünger, als die Quelle ihn ausweist.
    assertThat(wiederGelesen.wortlautStand()).isEqualTo(LocalDate.of(2026, 4, 22));
    assertThat(wiederGelesen.traegt(new Fortschreibung("Änderungsgesetz vom 22. April 2026", null)))
        .isTrue();
  }

  private static List<String> bezeichnungen(Gesetz gesetz) {
    return gesetz.normen().stream().map(n -> n.enbez()).toList();
  }
}
