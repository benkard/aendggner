// SPDX-FileCopyrightText: 2020-2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.DokumentErkenner;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.anwendung.Grund;
import eu.mulk.aendggner.bericht.Korpusbericht;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
      arity = "0..1",
      paramLabel = "<base law>",
      description =
          "The base law: gii-norm XML from gesetze-im-internet.de, or — for state law — the"
              + " consolidated version from the state portal (PDF or canonical plain text). May be"
              + " a file, an http(s) URL, or \"gii:<abbr>\" (e.g. gii:uwg) for federal law.")
  private String baseFile;

  @Parameters(
      index = "1..*",
      arity = "*",
      paramLabel = "<amendment>",
      description =
          "The amendment act(s) to apply (BGBl PDF or plain text). May be files or http(s) URLs.")
  private List<String> patches;

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

  @Option(
      names = "--stichtag",
      paramLabel = "<JJJJ-MM-TT>",
      description =
          "Produce the version in force on this date. Commands of the amendment act that had not "
              + "yet entered into force are listed separately instead of being applied.")
  private String stichtag;

  @Option(
      names = "--neufassung",
      paramLabel = "<file>",
      description =
          "Also write the amended base law as canonical plain text (\"-\" = stdout). The file can "
              + "be fed back in as the base law of a further amendment act.")
  private String neufassung;

  @Option(
      names = "--nachfassung",
      paramLabel = "<file>",
      description =
          "Compare the result against the official amended version, norm by norm. Accepts the same "
              + "inputs as the base law. The report is added to the synopsis; a mismatch yields "
              + "exit code 3.")
  private String nachfassung;

  @Option(
      names = "--korpus",
      paramLabel = "<file>",
      description =
          "Run every job of the given tab-separated job list and write a report of key figures "
              + "instead of a synopsis. Paths in the list are relative to the list's directory.")
  private String korpus;

  @Option(
      names = "--grundlinie",
      paramLabel = "<file>",
      description =
          "With --korpus: hold the run against this earlier report and complain about every figure "
              + "that has fallen. A regression yields exit code 3.")
  private String grundlinie;

  @Option(
      names = "--synopsen",
      paramLabel = "<dir>",
      description = "With --korpus: also write each job's synopsis into this directory.")
  private String synopsen;

  /** Die angegebenen Eingaben, jede über {@link Bezug} beschafft. */
  private static List<Quelle> hole(List<String> angaben) throws IOException, InterruptedException {
    var quellen = new ArrayList<Quelle>();
    for (var angabe : angaben) {
      quellen.add(Bezug.hole(angabe));
    }
    return quellen;
  }

  public static void main(String... args) {
    int exitCode = new CommandLine(new AendGgner()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public final Integer call() throws Exception {
    setupLogging();

    log.debugf("Logging configured.");

    if (korpus != null) {
      return fuehreKorpusAus();
    }

    if (baseFile == null) {
      System.err.println("Fehler: das Stammgesetz muss angegeben werden.");
      return 1;
    }

    if (dumpGesetz) {
      var gesetz = Pipeline.ladeStammgesetz(Bezug.hole(baseFile));
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
      System.out.print(Pipeline.extrahiereText(Bezug.hole(baseFile), hole(patches), raw));
      return 0;
    }

    if (dumpDokumentart) {
      var extraktor = new PatchTextExtraktor();
      // Ohne weitere Argumente wird die erste Datei selbst eingeordnet — zum Nachsehen, was
      // ÄndGgner in einem einzelnen Dokument erkennt, braucht es dann kein Stammgesetz.
      var zuPruefen = patches == null || patches.isEmpty() ? List.of(baseFile) : patches;
      for (var quelle : hole(zuPruefen)) {
        var kopf = DokumentErkenner.erkenne(extraktor.extrahiere(quelle));
        System.out.printf(
            "%s: %s [eigene Drs. %s, Bezug %s] %s%n",
            quelle.name(),
            kopf.art(),
            kopf.eigeneDrucksache() == null ? "—" : kopf.eigeneDrucksache(),
            kopf.bezugsDrucksachen().isEmpty() ? "—" : String.join(", ", kopf.bezugsDrucksachen()),
            kopf.titel());
      }
      return 0;
    }

    if (dumpBefehle) {
      var gesetz = Pipeline.ladeStammgesetz(Bezug.hole(baseFile));
      var extraktor = new PatchTextExtraktor(Pipeline.superskriptModus(gesetz));
      var parser = new AenderungsgesetzParser();
      for (var quelle : hole(patches)) {
        var auszug = extraktor.extrahiereMitSeiten(quelle);
        var text = TextBereiniger.bereinige(auszug.text());
        var ergebnis = parser.parse(text, gesetz, artikel, false, auszug.seiten());
        System.out.printf(
            "%s: %d Befehle aus Artikel %s%n",
            quelle.name(), ergebnis.befehle().size(), ergebnis.artikel());
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

    LocalDate tag = null;
    if (stichtag != null) {
      try {
        tag = LocalDate.parse(stichtag);
      } catch (DateTimeParseException e) {
        System.err.println(
            "Fehler: „" + stichtag + "“ ist kein Datum in der Form JJJJ-MM-TT (z.B. 2024-01-01).");
        return 1;
      }
    }

    var auftrag =
        Pipeline.Auftrag.von(Bezug.hole(baseFile), hole(patches))
            .mitArtikel(artikel)
            .mitVollstaendig(vollstaendig)
            .mitStichtag(tag)
            .mitNachfassung(nachfassung == null ? null : Bezug.hole(nachfassung));

    var ergebnis = Pipeline.erzeugeSynopse(auftrag);

    if (output.equals("-")) {
      System.out.println(ergebnis.html());
    } else {
      Files.writeString(Path.of(output), ergebnis.html(), StandardCharsets.UTF_8);
      log.infof("Synopse nach %s geschrieben.", output);
    }

    if (neufassung != null) {
      if (neufassung.equals("-")) {
        System.out.print(ergebnis.neufassung());
      } else {
        Files.writeString(Path.of(neufassung), ergebnis.neufassung(), StandardCharsets.UTF_8);
        log.infof("Neue Fassung nach %s geschrieben.", neufassung);
      }
    }

    System.err.printf(
        "%d Befehle angewandt, %d manuell zu prüfen, %d geänderte Normen.%n",
        ergebnis.anzahlAngewandt(), ergebnis.anzahlManuell(), ergebnis.anzahlGeaenderteNormen());

    var abgleich = ergebnis.abgleich();
    if (abgleich != null) {
      System.err.println("Abgleich mit der amtlichen Nachfassung: " + abgleich.kurzbericht());
      for (var abweichung : abgleich.abweichungen()) {
        System.err.println("  abweichend: " + abweichung.enbez());
      }
      for (var fehlend : abgleich.fehlende()) {
        System.err.println("  fehlt: " + fehlend);
      }
      for (var ueberzaehlig : abgleich.ueberzaehlige()) {
        System.err.println("  überzählig: " + ueberzaehlig);
      }
      // Ein eigener Ausgang, damit ein Massenlauf die Abweichung bemerkt, ohne die Ausgabe zu
      // lesen. Er geht dem Ausgang 2 vor: Dass die Fassung nicht stimmt, wiegt schwerer als
      // dass kein Befehl gegriffen hat — Letzteres wäre ohnehin dessen Ursache.
      if (!abgleich.gehtAuf()) {
        return 3;
      }
    }

    return ergebnis.anzahlAngewandt() == 0 && ergebnis.anzahlProtokollEintraege() > 0 ? 2 : 0;
  }

  /**
   * „Gründe nach Häufigkeit“ — die Auszählung der liegengebliebenen Befehle nach Art des Grundes.
   * Bei fünfzig Resten sagt sie mit einem Blick, woran es liegt; der ausformulierte Grund steht bei
   * jedem Befehl darüber.
   */
  private static void haeufigkeitDerGruende(BefehlAnwender.AnwendungsErgebnis anwendung) {
    var haeufigkeit = Pipeline.zaehleGruende(anwendung.protokoll());
    if (haeufigkeit.isEmpty()) {
      return;
    }
    System.out.println("Gründe nach Häufigkeit:");
    haeufigkeit.entrySet().stream()
        .sorted(java.util.Map.Entry.<Grund, Integer>comparingByValue().reversed())
        .forEach(e -> System.out.printf("  %4d  %s%n", e.getValue(), e.getKey().bezeichnung()));
  }

  /**
   * Der Massenlauf: die Aufträge der Liste nacheinander, eine Zeile Kennzahlen je Auftrag.
   *
   * <p>Der Bericht geht dahin, wohin sonst die Synopse ginge ({@code -o}). Ist eine Grundlinie
   * angegeben, so wird der Lauf gegen sie gehalten; jede gefallene Kennzahl wird gerügt und der
   * Lauf endet mit 3 — dieselbe Zahl, mit der ein nicht aufgehender Abgleich endet.
   */
  private Integer fuehreKorpusAus() throws IOException {
    var liste = Path.of(korpus);
    var wurzel = liste.toAbsolutePath().getParent();
    var auftraege = Korpusbericht.liesListe(liste);
    log.infof("Korpuslauf: %d Aufträge aus %s.", auftraege.size(), liste);

    var zeilen =
        Korpusbericht.fuehreAus(auftraege, wurzel, synopsen == null ? null : Path.of(synopsen));
    var tsv = Korpusbericht.alsTsv(zeilen);
    if (output == null || output.equals("-") || output.equals("synopse.html")) {
      // Die Voreinstellung des Schalters meint die Synopse; ein Bericht gehört dann auf die
      // Standardausgabe und nicht in eine Datei namens „synopse.html“.
      System.out.print(tsv);
    } else {
      Files.writeString(Path.of(output), tsv, StandardCharsets.UTF_8);
      System.out.println("Bericht geschrieben: " + output);
    }
    if (synopsen != null) {
      var uebersicht = Path.of(synopsen).resolveSibling("uebersicht.html");
      Files.writeString(
          uebersicht,
          Korpusbericht.alsHtml(zeilen, Path.of(synopsen).getFileName().toString()),
          StandardCharsets.UTF_8);
      System.out.println("Übersicht geschrieben: " + uebersicht);
    }
    System.out.println(Korpusbericht.summe(zeilen));

    if (grundlinie == null) {
      return zeilen.stream().anyMatch(z -> z.fehler() != null) ? 3 : 0;
    }
    var ruegen =
        Korpusbericht.gegenGrundlinie(
            zeilen, Files.readString(Path.of(grundlinie), StandardCharsets.UTF_8));
    if (ruegen.isEmpty()) {
      System.out.println("Die Grundlinie ist gehalten.");
      return 0;
    }
    System.out.println("Rückschritte gegenüber der Grundlinie:");
    ruegen.forEach(r -> System.out.println("  " + r));
    return 3;
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
