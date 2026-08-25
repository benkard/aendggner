// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz.land;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import eu.mulk.aendggner.gesetz.Gesetz;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
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

    for (var soll : gelesen.normen()) {
      var ist = wiederGelesen.norm(soll.enbez()).orElseThrow();
      assertThat(ist.titel()).as("Titel von %s", soll.enbez()).isEqualTo(soll.titel());
      assertThat(ist.weggefallen())
          .as("Wegfall von %s", soll.enbez())
          .isEqualTo(soll.weggefallen());
      assertThat(ist.absaetze()).as("Absätze von %s", soll.enbez()).isEqualTo(soll.absaetze());
    }
  }

  private static List<String> bezeichnungen(Gesetz gesetz) {
    return gesetz.normen().stream().map(n -> n.enbez()).toList();
  }
}
