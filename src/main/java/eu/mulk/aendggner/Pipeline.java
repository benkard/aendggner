// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import eu.mulk.aendggner.aenderung.DokumentArt;
import eu.mulk.aendggner.aenderung.DokumentKopf;
import eu.mulk.aendggner.aenderung.Inkrafttreten;
import eu.mulk.aendggner.aenderung.parse.AenderungsantragParser;
import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.DeutschesDatum;
import eu.mulk.aendggner.aenderung.parse.DokumentErkenner;
import eu.mulk.aendggner.aenderung.parse.EntwurfsPatcher;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.SuperskriptModus;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.anwendung.Grund;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Superskript;
import eu.mulk.aendggner.gesetz.gii.GiiXmlLoader;
import eu.mulk.aendggner.gesetz.land.LandesRechtLoader;
import eu.mulk.aendggner.synopse.HtmlRenderer;
import eu.mulk.aendggner.synopse.SynopseBuilder;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Kernpipeline: Stammgesetz laden → Änderungsgesetze parsen und anwenden → Synopse rendern.
 *
 * <p>Wird sowohl von der Befehlszeile ({@link AendGgner}) als auch von der Browserfassung ({@code
 * eu.mulk.aendggner.wasm.BrowserMain}) verwendet, damit die Anwendungslogik nur an einer Stelle
 * existiert. Sie kennt kein Dateisystem: Eingaben kommen als {@link Quelle} (Name und Bytes).
 */
public final class Pipeline {

  private static final org.jboss.logging.Logger log =
      org.jboss.logging.Logger.getLogger(Pipeline.class);

  private Pipeline() {}

  public record Ergebnis(
      String html,
      long anzahlAngewandt,
      long anzahlManuell,
      int anzahlGeaenderteNormen,
      int anzahlProtokollEintraege) {}

  /**
   * Ein eingespeistes Änderungsdokument samt erkannter Art und aufbereitetem Text.
   *
   * @param eingearbeitet die Dokumente, die vor der Anwendung in {@code text} eingearbeitet wurden
   *     — bei einem Entwurf die Änderungsanträge, die ihn geändert haben. Sie gehen in die
   *     Quellenzeile ein, denn die gezeigte Fassung ist ohne sie nicht nachvollziehbar.
   * @param fassung welche von mehreren Fassungen des Dokuments gilt, sofern es mehrere trägt — die
   *     Beschlussempfehlung stellt Entwurf und Ausschussfassung nebeneinander. Sonst {@code null}.
   */
  record Quelldokument(
      Quelle quelle,
      DokumentKopf kopf,
      String text,
      List<String> eingearbeitet,
      @Nullable String fassung) {

    Quelldokument(Quelle quelle, DokumentKopf kopf, String text) {
      this(quelle, kopf, text, List.of(), null);
    }

    Quelldokument(Quelle quelle, DokumentKopf kopf, String text, List<String> eingearbeitet) {
      this(quelle, kopf, text, eingearbeitet, null);
    }

    String quellenAngabe(List<String> artikel) {
      var sb = new StringBuilder(quelle.name());
      sb.append(" [").append(kopf.anzeigeName());
      if (fassung != null) {
        sb.append(", ").append(fassung);
      }
      sb.append("]");
      for (var zusatz : eingearbeitet) {
        sb.append(" + ").append(zusatz);
      }
      return sb.append(" (Artikel ").append(String.join(", ", artikel)).append(")").toString();
    }
  }

  /** Bequemlichkeit für Befehlszeile und Tests; im Browser gibt es keine {@link Path}e. */
  public static Ergebnis erzeugeSynopse(
      Path baseFile, List<Path> patches, String artikel, boolean vollstaendig) throws Exception {
    return erzeugeSynopse(baseFile, patches, artikel, vollstaendig, null);
  }

  public static Ergebnis erzeugeSynopse(
      Path baseFile,
      List<Path> patches,
      String artikel,
      boolean vollstaendig,
      @Nullable LocalDate stichtag)
      throws Exception {
    var patchQuellen = new ArrayList<Quelle>();
    for (var patch : patches) {
      patchQuellen.add(Quelle.lies(patch));
    }
    return erzeugeSynopse(Quelle.lies(baseFile), patchQuellen, artikel, vollstaendig, stichtag);
  }

  public static Ergebnis erzeugeSynopse(
      Quelle baseFile, List<Quelle> patches, String artikel, boolean vollstaendig)
      throws Exception {
    return erzeugeSynopse(baseFile, patches, artikel, vollstaendig, null);
  }

  /**
   * @param stichtag die Fassung dieses Tages erzeugen: Befehle, die an ihm noch nicht in Kraft
   *     waren, bleiben unangewandt und werden gesondert ausgewiesen. {@code null} = alle Befehle
   *     anwenden (dann warnt die Synopse, wenn das Gesetz gestaffelt in Kraft tritt).
   */
  public static Ergebnis erzeugeSynopse(
      Quelle baseFile,
      List<Quelle> patches,
      String artikel,
      boolean vollstaendig,
      @Nullable LocalDate stichtag)
      throws Exception {
    var altesGesetz = ladeStammgesetz(baseFile);
    var extraktor = new PatchTextExtraktor(superskriptModus(altesGesetz));
    var parser = new AenderungsgesetzParser();

    var warnungen = new ArrayList<String>();
    var dokumente = wendeAntraegeAn(leseDokumente(patches, extraktor, warnungen), warnungen);

    var gesetz = altesGesetz;
    var protokoll = new ArrayList<BefehlAnwender.AngewandteAenderung>();
    var quellen = new ArrayList<String>();
    boolean entwurfsfassung = false;
    Inkrafttreten inkrafttreten = null;

    for (var dokument : dokumente) {
      var parseErgebnis = parser.parse(dokument.text(), gesetz, artikel, entwurfsGrenzen(dokument));
      if (parseErgebnis.befehle().isEmpty()) {
        warnungen.add(
            "In %s (%s) wurde kein auf %s anwendbarer Artikel gefunden."
                .formatted(
                    dokument.quelle().name(), dokument.kopf().anzeigeName(), gesetz.jurabk()));
      }
      if (parseErgebnis.inkrafttreten() != null) {
        inkrafttreten = parseErgebnis.inkrafttreten();
      }
      var geteilt = teileNachStichtag(parseErgebnis, stichtag, warnungen);
      var anwendung = BefehlAnwender.anwenden(gesetz, geteilt.anzuwenden());
      gesetz = anwendung.neu();
      protokoll.addAll(anwendung.protokoll());
      protokoll.addAll(geteilt.zurueckgestellt());
      warnungen.addAll(parseErgebnis.warnungen());
      entwurfsfassung |= dokument.kopf().art().istEntwurfsfassung();
      quellen.add(dokument.quellenAngabe(parseErgebnis.artikel()));
    }

    if (stichtag == null && inkrafttreten != null && inkrafttreten.gestaffelt()) {
      warnungen.add(staffelungsWarnung(inkrafttreten));
    }
    var gesamtErgebnis = new BefehlAnwender.AnwendungsErgebnis(gesetz, protokoll);
    var synopse =
        SynopseBuilder.baue(
            altesGesetz, gesamtErgebnis, warnungen, vollstaendig, inkrafttreten, stichtag);
    var quellenZeile = baseFile.name() + " + " + String.join(" + ", quellen);
    var html = HtmlRenderer.rendere(synopse, quellenZeile, entwurfsfassung);

    return new Ergebnis(
        html,
        gesamtErgebnis.anzahlAngewandt(),
        gesamtErgebnis.anzahlManuell(),
        synopse.eintraege().size(),
        protokoll.size());
  }

  /** Die Befehle eines Dokuments, geschieden nach dem, was am Stichtag schon galt. */
  private record Geteilt(
      List<eu.mulk.aendggner.aenderung.Aenderungsbefehl> anzuwenden,
      List<BefehlAnwender.AngewandteAenderung> zurueckgestellt) {}

  /**
   * Scheidet die Befehle nach dem Stichtag. Ohne Stichtag bleibt alles beisammen — dann ist die
   * Fassung die des vollständigen Inkrafttretens, worauf die Staffelungswarnung hinweist.
   *
   * <p>Eine Anordnung ohne bestimmbares Datum („am Tag nach der Verkündung“ — der Verkündungstag
   * steht nicht im Gesetzestext) gilt als am Stichtag bereits wirksam; geraten wird nicht, aber es
   * wird gesagt.
   */
  private static Geteilt teileNachStichtag(
      AenderungsgesetzParser.ParseErgebnis parseErgebnis,
      @Nullable LocalDate stichtag,
      List<String> warnungen) {
    var befehle = parseErgebnis.befehle();
    var inkrafttreten = parseErgebnis.inkrafttreten();
    if (stichtag == null || befehle.isEmpty()) {
      return new Geteilt(befehle, List.of());
    }
    if (inkrafttreten == null) {
      warnungen.add(
          "Es wurde ein Stichtag gewählt, das Änderungsdokument trägt aber keine lesbare"
              + " Inkrafttretens-Vorschrift; angewandt wurden deshalb alle Befehle.");
      return new Geteilt(befehle, List.of());
    }
    var anzuwenden = new ArrayList<eu.mulk.aendggner.aenderung.Aenderungsbefehl>();
    var zurueckgestellt = new ArrayList<BefehlAnwender.AngewandteAenderung>();
    boolean unbestimmt = false;
    for (var befehl : befehle) {
      var regel = inkrafttreten.fuer(befehl.provenienz()).orElse(null);
      if (regel == null || regel.datum() == null) {
        unbestimmt |= regel != null;
        anzuwenden.add(befehl);
        continue;
      }
      if (regel.datum().isAfter(stichtag)) {
        zurueckgestellt.add(
            new BefehlAnwender.AngewandteAenderung(
                befehl,
                BefehlAnwender.Status.NICHT_IN_KRAFT,
                "Tritt erst am "
                    + DeutschesDatum.schreibe(regel.datum())
                    + " in Kraft („"
                    + regel.wortlaut()
                    + "“).",
                Set.of(),
                Grund.NOCH_NICHT_IN_KRAFT));
        continue;
      }
      anzuwenden.add(befehl);
    }
    if (unbestimmt) {
      warnungen.add(
          "Eine Inkrafttretens-Anordnung nennt kein bestimmtes Datum, sondern knüpft an die"
              + " Verkündung an; der Verkündungstag steht nicht im Gesetzestext. Die betroffenen"
              + " Befehle wurden als am Stichtag bereits geltend behandelt.");
    }
    return new Geteilt(List.copyOf(anzuwenden), List.copyOf(zurueckgestellt));
  }

  /**
   * Wird ein Änderungsgesetz gestaffelt wirksam, so ist die auf einen Schlag gerechnete Fassung
   * eine, die an keinem Tag gegolten hat. Das darf nicht unausgesprochen bleiben.
   */
  private static String staffelungsWarnung(Inkrafttreten inkrafttreten) {
    var sb = new StringBuilder("Das Änderungsgesetz tritt gestaffelt in Kraft");
    inkrafttreten
        .grundregel()
        .ifPresent(r -> sb.append(" — im Grundsatz: „").append(r.wortlaut()).append("“"));
    for (var sonder : inkrafttreten.sonderregeln()) {
      sb.append("; abweichend: „").append(sonder.wortlaut()).append("“");
    }
    return sb.append(
            ". Die gezeigte Fassung wendet alle Befehle an und gilt daher erst, wenn das Gesetz"
                + " vollständig in Kraft getreten ist; die Fassung eines bestimmten Tages ergibt"
                + " sich nur mit einem Stichtag.")
        .toString();
  }

  /**
   * Liest die Änderungsdokumente ein, bestimmt ihre Art und sortiert die aus, aus denen keine
   * Synopse zu gewinnen ist. Verworfen wird nichts stillschweigend: Jedes ausgesonderte Dokument
   * hinterlässt eine Warnung, die in der Synopse erscheint.
   */
  private static List<Quelldokument> leseDokumente(
      List<Quelle> patches, PatchTextExtraktor extraktor, List<String> warnungen) throws Exception {
    var dokumente = new ArrayList<Quelldokument>();
    for (var datei : patches) {
      var rohText = extraktor.extrahiere(datei);
      // Die Erkennung arbeitet auf dem Rohtext: Der Bereiniger entfernt genau die
      // Drucksachenköpfe, aus denen Art und Nummer hervorgehen.
      var kopf = DokumentErkenner.erkenne(rohText);
      log.infof("Datei %s erkannt als %s.", datei.name(), kopf.anzeigeName());
      if (kopf.art() == DokumentArt.OHNE_BEFEHLE) {
        warnungen.add(
            "%s ist ein %s und enthält keine Änderungsbefehle; die Datei wurde übergangen."
                .formatted(datei.name(), kopf.art().anzeigeName()));
        continue;
      }
      if (kopf.art() == DokumentArt.BESCHLUSSEMPFEHLUNG) {
        // Die maßgebliche Fassung steht nicht im Fließtext, sondern in der zweispaltigen
        // Zusammenstellung: links der Entwurf, rechts die Beschlüsse des Ausschusses.
        var fassung = extraktor.leseZusammenstellung(datei);
        warnungen.addAll(fassung.warnungen());
        if (fassung.text() == null) {
          // Eine halb aufgelöste Fassung auszugeben wäre schlimmer als keine.
          warnungen.add(
              ("%s ist eine Beschlussempfehlung, deren Zusammenstellung sich nicht auflösen ließ;"
                      + " die Datei wurde übergangen. Für eine Synopse eignet sich der zugrunde"
                      + " liegende Gesetzentwurf%s.")
                  .formatted(
                      datei.name(),
                      kopf.bezugsDrucksachen().isEmpty()
                          ? ""
                          : " (Drs. " + kopf.bezugsDrucksachen().get(0) + ")"));
          continue;
        }
        dokumente.add(
            new Quelldokument(
                datei,
                kopf,
                TextBereiniger.bereinige(fassung.text()),
                List.of(),
                "Ausschussfassung"));
        continue;
      }
      dokumente.add(new Quelldokument(datei, kopf, TextBereiniger.bereinige(rohText)));
    }
    return dokumente;
  }

  /**
   * Wendet jeden Änderungsantrag auf den Entwurf an, den er ändern will, und nimmt ihn aus der
   * Liste: Was danach bleibt, sind lauter Dokumente, die unmittelbar das Stammgesetz ändern.
   *
   * <p>Zugeordnet wird über die Drucksachennummer, die der Antrag selbst nennt („(Drs. 19/9707)“).
   * Nur wo die fehlt, entscheidet die Reihenfolge der Argumente — der zuletzt genannte Entwurf
   * davor. Findet sich gar kein Entwurf, bleibt der Antrag unangewandt und wird gemeldet; ihn
   * ersatzweise auf das Stammgesetz loszulassen wäre falsch, denn seine Stellenangaben zielen auf
   * die Drucksache.
   */
  private static List<Quelldokument> wendeAntraegeAn(
      List<Quelldokument> dokumente, List<String> warnungen) {
    if (dokumente.stream().noneMatch(d -> d.kopf().art() == DokumentArt.AENDERUNGSANTRAG)) {
      return dokumente;
    }
    var ergebnis = new ArrayList<>(dokumente);
    for (var antrag : dokumente) {
      if (antrag.kopf().art() != DokumentArt.AENDERUNGSANTRAG) {
        continue;
      }
      ergebnis.remove(antrag);
      int zielIndex = findeEntwurf(ergebnis, antrag, dokumente.indexOf(antrag));
      if (zielIndex < 0) {
        warnungen.add(
            ("%s ist ein Änderungsantrag zu %s; der zugehörige Gesetzentwurf wurde nicht"
                    + " mitgegeben, der Antrag blieb daher unberücksichtigt.")
                .formatted(
                    antrag.quelle().name(),
                    antrag.kopf().bezugsDrucksachen().isEmpty()
                        ? "einer Drucksache"
                        : "Drs. " + String.join(", ", antrag.kopf().bezugsDrucksachen())));
        continue;
      }
      var ziel = ergebnis.get(zielIndex);
      var parseErgebnis = AenderungsantragParser.parse(antrag.text());
      warnungen.addAll(parseErgebnis.warnungen());
      var patch = EntwurfsPatcher.wendeAn(ziel.text(), parseErgebnis.befehle());
      warnungen.addAll(patch.warnungen());
      log.infof(
          "%s: %d von %d Antragsbefehlen auf %s angewandt.",
          antrag.quelle().name(),
          patch.angewandt(),
          parseErgebnis.befehle().size(),
          ziel.quelle().name());
      var eingearbeitet = new ArrayList<>(ziel.eingearbeitet());
      eingearbeitet.add(antrag.quelle().name() + " [" + antrag.kopf().anzeigeName() + "]");
      ergebnis.set(
          zielIndex,
          new Quelldokument(
              ziel.quelle(),
              ziel.kopf(),
              patch.text(),
              List.copyOf(eingearbeitet),
              ziel.fassung()));
    }
    return ergebnis;
  }

  /** Der Index des Entwurfs, den {@code antrag} ändert; {@code -1}, wenn keiner dabei ist. */
  private static int findeEntwurf(
      List<Quelldokument> dokumente, Quelldokument antrag, int antragsPosition) {
    for (int i = 0; i < dokumente.size(); i++) {
      var kopf = dokumente.get(i).kopf();
      if (kopf.eigeneDrucksache() != null
          && antrag.kopf().bezugsDrucksachen().contains(kopf.eigeneDrucksache())) {
        return i;
      }
    }
    int letzter = -1;
    for (int i = 0; i < dokumente.size() && i < antragsPosition; i++) {
      if (dokumente.get(i).kopf().art() == DokumentArt.GESETZENTWURF) {
        letzter = i;
      }
    }
    return letzter;
  }

  /**
   * Entwürfe tragen hinter dem Regelungstext einen Begründungsteil, dessen Freitext keine Befehle
   * enthält und den letzten Artikel nicht verunreinigen darf. Verkündete Gesetze haben ihn nicht —
   * dort bliebe die Suche nach Begründungsmarken folgenlos, aber sie unterbleibt trotzdem, damit
   * ein Gesetzblatt nicht an einem gleichlautenden Wort abbricht.
   */
  private static boolean entwurfsGrenzen(Quelldokument dokument) {
    return dokument.kopf().art().istEntwurfsfassung();
  }

  /**
   * Der Text der Änderungsdokumente, so wie das Erzeugnis ihn liest — der Notausgang des § 6 Absatz
   * 2 des Handbuchs.
   *
   * <p>Bleibt die PDF-Aufbereitung im Einzelfall fehlerhaft, so ist nicht das Ergebnis zu
   * beargwöhnen, sondern der Text nachzusehen, von Hand zu berichtigen und als Klartextdatei wieder
   * einzuspeisen. Dass dieser Weg beiden Fassungen offensteht, ist kein Zierat: Wer im Browser
   * arbeitet, hat sonst keine Möglichkeit, einem unerklärlichen Rest auf den Grund zu gehen.
   *
   * <p>Das Stammgesetz wird auch hier gebraucht, denn seine Schreibweise bestimmt den
   * Superskriptmodus der Textgewinnung ({@link #superskriptModus}).
   *
   * @param roh die Bereinigung übergehen; nur zur Fehlersuche an der Textgewinnung selbst.
   */
  public static String extrahiereText(Quelle baseFile, List<Quelle> patches, boolean roh)
      throws Exception {
    var extraktor = new PatchTextExtraktor(superskriptModus(ladeStammgesetz(baseFile)));
    var text = new StringBuilder();
    for (var patch : patches) {
      var roher = extraktor.extrahiere(patch);
      text.append(roh ? roher : TextBereiniger.bereinige(roher)).append('\n');
    }
    return text.toString();
  }

  /** Bequemlichkeit für Befehlszeile und Tests; im Browser gibt es keine {@link Path}e. */
  static Gesetz ladeStammgesetz(Path baseFile) throws Exception {
    return ladeStammgesetz(Quelle.lies(baseFile));
  }

  /**
   * Gii-XML → {@link GiiXmlLoader}; PDF/Klartext (Landesrecht) → {@link LandesRechtLoader}.
   *
   * <p>Vorweg wird ausgepackt: gesetze-im-internet.de gibt das Norm-XML nur als {@code xml.zip}
   * aus, und das soll unentpackt taugen (siehe {@link ZipAuspacker}). Die Änderungsdokumente
   * bleiben davon unberührt — Gesetzblätter und Drucksachen kommen nirgends als Archiv.
   */
  static Gesetz ladeStammgesetz(Quelle baseFile) throws Exception {
    var quelle = ZipAuspacker.auspacken(baseFile);
    return DateiTyp.erkenne(quelle.inhalt()) == DateiTyp.XML
        ? new GiiXmlLoader().load(quelle)
        : new LandesRechtLoader().load(quelle);
  }

  /**
   * Der Superskriptmodus folgt der Schreibweise des Stammgesetzes: Trägt es amtliche Satznummern
   * (bayerisches Landesrecht, Niedersachsen u.a.), werden auch die Änderungsgesetze mit
   * Superskript-Erhalt extrahiert, damit Zitate und Stammtext dieselbe Schreibweise tragen; sonst
   * (Bundesrecht, Länder ohne amtliche Satzzählung) sind hochgestellte Ziffern bloße Fußnotenmarker
   * und werden verworfen.
   */
  static SuperskriptModus superskriptModus(Gesetz gesetz) {
    for (var norm : gesetz.normen()) {
      for (var absatz : norm.absaetze()) {
        if (Superskript.traegtSatznummern(absatz.text())) {
          return SuperskriptModus.BEHALTEN;
        }
      }
    }
    return SuperskriptModus.ENTFERNEN;
  }
}
