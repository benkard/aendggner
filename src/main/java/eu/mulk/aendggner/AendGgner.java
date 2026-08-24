// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.DokumentErkenner;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.anwendung.Grund;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.LogManager;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "ÄndGgner",
    mixinStandardHelpOptions = true,
    version = "ÄndGgner 0.1",
    description = "Displays German amendment acts in a user-friendly, consolidated way.")
public class AendGgner implements Callable<Integer> {

  private static final Logger log = Logger.getLogger(AendGgner.class);

  @Parameters(
      index = "0",
      description =
          "The base law: gii-norm XML from gesetze-im-internet.de, or — for Bavarian state law —"
              + " the consolidated version from gesetze-bayern.de (PDF or canonical plain text).")
  private Path baseFile;

  @Parameters(
      index = "1..*",
      arity = "*",
      description = "The amendment act(s) to apply (BGBl PDF or plain text).")
  private List<Path> patches;

  @Option(
      names = {"-o", "--output"},
      paramLabel = "<file>",
      description =
          "Output file for the HTML synopsis (default: ${DEFAULT-VALUE}; \"-\" = stdout).",
      defaultValue = "synopse.html")
  private String output;

  @Option(names = "--vollstaendig", description = "Include unchanged provisions in the synopsis.")
  private boolean vollstaendig;

  @Option(
      names = "--format",
      paramLabel = "<format>",
      description = "Output format (currently only: html).",
      defaultValue = "html")
  private String format;

  @Option(
      names = "--dump-gesetz",
      hidden = true,
      description = "Debug: dump the parsed base law structure and exit.")
  private boolean dumpGesetz;

  @Option(
      names = "--extract-only",
      description =
          "Print the cleaned linear text of the amendment act and exit. "
              + "Useful for inspecting and hand-correcting PDF extraction; "
              + "the corrected text can be fed back in as a plain-text patch file.")
  private boolean extractOnly;

  @Option(
      names = "--raw",
      hidden = true,
      description = "Debug: with --extract-only, skip text cleanup.")
  private boolean raw;

  @Option(
      names = "--dump-dokumentart",
      hidden = true,
      description = "Debug: print the recognised document kind of each amendment file and exit.")
  private boolean dumpDokumentart;

  @Option(
      names = "--dump-befehle",
      hidden = true,
      description = "Debug: dump the parsed amendment commands and exit.")
  private boolean dumpBefehle;

  @Option(
      names = "--artikel",
      paramLabel = "<n>",
      description =
          "Only apply this article of the amendment act "
              + "(default: all articles whose introduction names the base law).")
  private String artikel;

  public static void main(String... args) {
    int exitCode = new CommandLine(new AendGgner()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public final Integer call() throws Exception {
    setupLogging();

    log.debugf("Logging configured.");

    if (dumpGesetz) {
      var gesetz = Pipeline.ladeStammgesetz(baseFile);
      System.out.printf(
          "%s — %s (%d Normen)%n", gesetz.jurabk(), gesetz.langue(), gesetz.normen().size());
      for (var norm : gesetz.normen()) {
        System.out.printf(
            "  %s%s — %d Absätze%s%n",
            norm.enbez(),
            norm.titel() == null ? "" : " (" + norm.titel() + ")",
            norm.absaetze().size(),
            norm.weggefallen() ? " [weggefallen]" : "");
      }
      return 0;
    }

    if (extractOnly) {
      var quellen = new ArrayList<Quelle>();
      for (var file : patches) {
        quellen.add(Quelle.lies(file));
      }
      System.out.print(Pipeline.extrahiereText(Quelle.lies(baseFile), quellen, raw));
      return 0;
    }

    if (dumpDokumentart) {
      var extraktor = new PatchTextExtraktor();
      // Ohne weitere Argumente wird die erste Datei selbst eingeordnet — zum Nachsehen, was
      // ÄndGgner in einem einzelnen Dokument erkennt, braucht es dann kein Stammgesetz.
      var zuPruefen = patches == null || patches.isEmpty() ? List.of(baseFile) : patches;
      for (var file : zuPruefen) {
        var kopf = DokumentErkenner.erkenne(extraktor.extrahiere(file));
        System.out.printf(
            "%s: %s [eigene Drs. %s, Bezug %s] %s%n",
            file.getFileName(),
            kopf.art(),
            kopf.eigeneDrucksache() == null ? "—" : kopf.eigeneDrucksache(),
            kopf.bezugsDrucksachen().isEmpty() ? "—" : String.join(", ", kopf.bezugsDrucksachen()),
            kopf.titel());
      }
      return 0;
    }

    if (dumpBefehle) {
      var gesetz = Pipeline.ladeStammgesetz(baseFile);
      var extraktor = new PatchTextExtraktor(Pipeline.superskriptModus(gesetz));
      var parser = new AenderungsgesetzParser();
      for (var file : patches) {
        var text = TextBereiniger.bereinige(extraktor.extrahiere(file));
        var ergebnis = parser.parse(text, gesetz, artikel);
        System.out.printf(
            "%s: %d Befehle aus Artikel %s%n",
            file.getFileName(), ergebnis.befehle().size(), ergebnis.artikel());
        for (var warnung : ergebnis.warnungen()) {
          System.err.println("WARNUNG: " + warnung);
        }
        var anwendung = BefehlAnwender.anwenden(gesetz, ergebnis.befehle());
        for (var eintrag : anwendung.protokoll()) {
          var befehl = eintrag.befehl();
          System.out.printf(
              "  [%s] %s @ %s → %s%s%n      %s%n",
              befehl.getClass().getSimpleName(),
              befehl.provenienz().anzeigeText(),
              befehl.stelle().anzeigeText(),
              eintrag.status(),
              eintrag.begruendung().isEmpty() ? "" : " (" + eintrag.begruendung() + ")",
              kuerze(befehl.provenienz().originalText()));
        }
        System.out.printf(
            "Angewandt: %d, manuell prüfen: %d%n",
            anwendung.anzahlAngewandt(), anwendung.anzahlManuell());
        haeufigkeitDerGruende(anwendung);
        gesetz = anwendung.neu();
      }
      return 0;
    }

    if (patches == null || patches.isEmpty()) {
      System.err.println("Fehler: mindestens ein Änderungsgesetz muss angegeben werden.");
      return 1;
    }
    if (!format.equals("html")) {
      System.err.println("Fehler: unbekanntes Ausgabeformat „" + format + "“ (unterstützt: html).");
      return 1;
    }

    var ergebnis = Pipeline.erzeugeSynopse(baseFile, patches, artikel, vollstaendig);

    if (output.equals("-")) {
      System.out.println(ergebnis.html());
    } else {
      Files.writeString(Path.of(output), ergebnis.html(), StandardCharsets.UTF_8);
      log.infof("Synopse nach %s geschrieben.", output);
    }

    System.err.printf(
        "%d Befehle angewandt, %d manuell zu prüfen, %d geänderte Normen.%n",
        ergebnis.anzahlAngewandt(), ergebnis.anzahlManuell(), ergebnis.anzahlGeaenderteNormen());

    return ergebnis.anzahlAngewandt() == 0 && ergebnis.anzahlProtokollEintraege() > 0 ? 2 : 0;
  }

  /**
   * „Gründe nach Häufigkeit“ — die Auszählung der liegengebliebenen Befehle nach Art des Grundes.
   * Bei fünfzig Resten sagt sie mit einem Blick, woran es liegt; der ausformulierte Grund steht bei
   * jedem Befehl darüber.
   */
  private static void haeufigkeitDerGruende(BefehlAnwender.AnwendungsErgebnis anwendung) {
    var haeufigkeit = new EnumMap<Grund, Integer>(Grund.class);
    for (var eintrag : anwendung.protokoll()) {
      if (eintrag.status() == BefehlAnwender.Status.MANUELL_PRUEFEN && eintrag.grund() != null) {
        haeufigkeit.merge(eintrag.grund(), 1, Integer::sum);
      }
    }
    if (haeufigkeit.isEmpty()) {
      return;
    }
    System.out.println("Gründe nach Häufigkeit:");
    haeufigkeit.entrySet().stream()
        .sorted(java.util.Map.Entry.<Grund, Integer>comparingByValue().reversed())
        .forEach(e -> System.out.printf("  %4d  %s%n", e.getValue(), e.getKey().bezeichnung()));
  }

  private static String kuerze(String text) {
    var einzeilig = text.replaceAll("\\s+", " ");
    return einzeilig.length() <= 160 ? einzeilig : einzeilig.substring(0, 157) + "…";
  }

  private static void setupLogging() throws IOException {
    try (var loggingProperties =
        AendGgner.class.getResourceAsStream("/eu/mulk/aendggner/logging.properties")) {
      LogManager.getLogManager().readConfiguration(loggingProperties);
    }
  }
}
