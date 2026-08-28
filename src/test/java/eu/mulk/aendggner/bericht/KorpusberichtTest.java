// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.bericht;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Der Massenlauf über den Beispielkorpus, gehalten gegen die eingecheckte Grundlinie.
 *
 * <p>Die Einzelprüfungen sagen, dass ein bestimmtes Heft aufgeht. Diese sagt, dass keines der
 * einunddreißig Hefte hinter das zurückfällt, was es einmal hergegeben hat — und zwar in einer
 * Zahl, die sich fortschreiben lässt, statt in Dutzenden verstreuter Behauptungen.
 */
class KorpusberichtTest {

  private static final Path SAMPLEDATA = Path.of("src/test/resources/sampledata");
  private static final Path LISTE = SAMPLEDATA.resolve("korpus.tsv");
  private static final Path GRUNDLINIE = SAMPLEDATA.resolve("korpus-grundlinie.tsv");

  @Test
  void derKorpusFaelltNichtHinterDieGrundlinieZurueck() throws Exception {
    assumeTrue(Files.exists(LISTE) && Files.exists(GRUNDLINIE), "Korpusliste fehlt");
    var auftraege = Korpusbericht.liesListe(LISTE);
    assumeTrue(
        auftraege.stream().allMatch(a -> Files.exists(SAMPLEDATA.resolve(a.stamm()))),
        "Beispieldaten des Korpus fehlen");

    var zeilen = Korpusbericht.fuehreAus(auftraege, SAMPLEDATA.toAbsolutePath(), null);

    // Ein gescheiterter Auftrag hält den Lauf nicht an — unbemerkt bleiben darf er aber nicht;
    // sonst meldete der Bericht lauter Nullen und niemand sähe den Grund.
    assertThat(zeilen).hasSameSizeAs(auftraege);
    assertThat(zeilen).allSatisfy(z -> assertThat(z.fehler()).isNull());

    var ruegen =
        Korpusbericht.gegenGrundlinie(zeilen, Files.readString(GRUNDLINIE, StandardCharsets.UTF_8));

    assertThat(ruegen)
        .as(
            "Rückschritte gegenüber der Grundlinie; steht dagegen ein Fortschritt darin, so ist"
                + " korpus-grundlinie.tsv fortzuschreiben:\n%s\n\nLauf:\n%s",
            String.join("\n", ruegen), Korpusbericht.alsTsv(zeilen))
        .isEmpty();
  }

  @Test
  void dieListeWirdVollstaendigGelesen() throws Exception {
    assumeTrue(Files.exists(LISTE), "Korpusliste fehlt");
    var auftraege = Korpusbericht.liesListe(LISTE);

    assertThat(auftraege).isNotEmpty();
    // Kommentar- und Leerzeilen sind übergangen, „-“ heißt „nicht angegeben“.
    assertThat(auftraege).noneMatch(a -> a.bezeichnung().startsWith("#"));
    assertThat(auftraege).allSatisfy(a -> assertThat(a.hefte()).isNotEmpty());
    var mitArtikel =
        auftraege.stream().filter(a -> "GEG-BGBl-zeitrichtig".equals(a.bezeichnung())).findFirst();
    assertThat(mitArtikel).isPresent();
    assertThat(mitArtikel.orElseThrow().artikel()).isEqualTo("1");
    assertThat(mitArtikel.orElseThrow().nachfassung()).isNull();
    var mehrereHefte =
        auftraege.stream()
            .filter(a -> "BayJG-mit-Aenderungsantrag".equals(a.bezeichnung()))
            .findFirst()
            .orElseThrow();
    assertThat(mehrereHefte.hefte()).hasSize(2);
    var mitStichtag =
        auftraege.stream()
            .filter(a -> "IfSG-Stichtag".equals(a.bezeichnung()))
            .findFirst()
            .orElseThrow();
    assertThat(mitStichtag.stichtag()).isNotNull();
  }

  @Test
  void nurDerRueckschrittWirdGeruegt() {
    var grundlinie =
        """
        # Bezeichnung\tBefehle\tangewandt\tmanuell\tzurückgestellt\tgleich\tgeprüft\tfehlend\tüberzählig\tabweichend\tGründe
        Fall\t10\t8\t2\t0\t20\t22\t1\t0\t1\t-
        """;

    var besser =
        List.of(new Korpusbericht.Zeile("Fall", 10, 9, 1, 0, 21, 22, 0, 0, 1, Map.of(), null));
    assertThat(Korpusbericht.gegenGrundlinie(besser, grundlinie)).isEmpty();

    var schlechter =
        List.of(new Korpusbericht.Zeile("Fall", 10, 7, 3, 0, 19, 22, 1, 0, 2, Map.of(), null));
    assertThat(Korpusbericht.gegenGrundlinie(schlechter, grundlinie))
        .anySatisfy(r -> assertThat(r).contains("angewandte Befehle 7 statt 8"))
        .anySatisfy(r -> assertThat(r).contains("liegengebliebene Befehle 3 statt 2"))
        .anySatisfy(r -> assertThat(r).contains("gleiche Normen 19 statt 20"))
        .anySatisfy(r -> assertThat(r).contains("abweichende Normen 2 statt 1"));
  }

  @Test
  void einGescheiterterAuftragWirdGeruegtStattUebergangen() {
    var grundlinie =
        """
        # Bezeichnung\tBefehle\tangewandt\tmanuell\tzurückgestellt\tgleich\tgeprüft\tfehlend\tüberzählig\tabweichend\tGründe
        Fall\t10\t8\t2\t0\t-\t-\t-\t-\t-\t-
        """;

    var gescheitert =
        List.of(
            new Korpusbericht.Zeile(
                "Fall", 0, 0, 0, 0, null, null, null, null, null, Map.of(), "IOException: weg"));
    assertThat(Korpusbericht.gegenGrundlinie(gescheitert, grundlinie))
        .singleElement()
        .asString()
        .contains("gescheitert");
  }

  @Test
  void einVerschwundenerAuftragFaelltAuf() {
    var grundlinie =
        """
        # Bezeichnung\tBefehle\tangewandt\tmanuell\tzurückgestellt\tgleich\tgeprüft\tfehlend\tüberzählig\tabweichend\tGründe
        Fall\t10\t8\t2\t0\t-\t-\t-\t-\t-\t-
        """;

    assertThat(Korpusbericht.gegenGrundlinie(List.of(), grundlinie))
        .singleElement()
        .asString()
        .contains("ist im Lauf aber nicht vorgekommen");
  }
}
