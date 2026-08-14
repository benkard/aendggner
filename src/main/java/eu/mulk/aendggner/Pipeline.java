package eu.mulk.aendggner;

import eu.mulk.aendggner.aenderung.DokumentArt;
import eu.mulk.aendggner.aenderung.DokumentKopf;
import eu.mulk.aendggner.aenderung.parse.AenderungsantragParser;
import eu.mulk.aendggner.aenderung.parse.AenderungsgesetzParser;
import eu.mulk.aendggner.aenderung.parse.DokumentErkenner;
import eu.mulk.aendggner.aenderung.parse.EntwurfsPatcher;
import eu.mulk.aendggner.aenderung.parse.PatchTextExtraktor;
import eu.mulk.aendggner.aenderung.parse.SuperskriptModus;
import eu.mulk.aendggner.aenderung.parse.TextBereiniger;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Superskript;
import eu.mulk.aendggner.gesetz.gii.GiiXmlLoader;
import eu.mulk.aendggner.gesetz.land.LandesRechtLoader;
import eu.mulk.aendggner.synopse.HtmlRenderer;
import eu.mulk.aendggner.synopse.SynopseBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Kernpipeline: Stammgesetz laden → Änderungsgesetze parsen und anwenden → Synopse rendern.
 *
 * <p>Wird sowohl von der CLI ({@link AendGgner}) als auch vom Webserver ( {@code
 * eu.mulk.aendggner.web.UploadHandler}) verwendet, damit die Anwendungslogik nur an einer Stelle
 * existiert.
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
   */
  record Quelldokument(Path datei, DokumentKopf kopf, String text, List<String> eingearbeitet) {

    Quelldokument(Path datei, DokumentKopf kopf, String text) {
      this(datei, kopf, text, List.of());
    }

    String quellenAngabe(List<String> artikel) {
      var sb = new StringBuilder(datei.getFileName().toString());
      sb.append(" [").append(kopf.anzeigeName()).append("]");
      for (var zusatz : eingearbeitet) {
        sb.append(" + ").append(zusatz);
      }
      return sb.append(" (Artikel ").append(String.join(", ", artikel)).append(")").toString();
    }
  }

  public static Ergebnis erzeugeSynopse(
      Path baseFile, List<Path> patches, String artikel, boolean vollstaendig) throws Exception {
    var altesGesetz = ladeStammgesetz(baseFile);
    var extraktor = new PatchTextExtraktor(superskriptModus(altesGesetz));
    var parser = new AenderungsgesetzParser();

    var warnungen = new ArrayList<String>();
    var dokumente = wendeAntraegeAn(leseDokumente(patches, extraktor, warnungen), warnungen);

    var gesetz = altesGesetz;
    var protokoll = new ArrayList<BefehlAnwender.AngewandteAenderung>();
    var quellen = new ArrayList<String>();
    boolean entwurfsfassung = false;

    for (var dokument : dokumente) {
      var parseErgebnis = parser.parse(dokument.text(), gesetz, artikel, entwurfsGrenzen(dokument));
      if (parseErgebnis.befehle().isEmpty()) {
        warnungen.add(
            "In %s (%s) wurde kein auf %s anwendbarer Artikel gefunden."
                .formatted(
                    dokument.datei().getFileName(),
                    dokument.kopf().anzeigeName(),
                    gesetz.jurabk()));
      }
      var anwendung = BefehlAnwender.anwenden(gesetz, parseErgebnis.befehle());
      gesetz = anwendung.neu();
      protokoll.addAll(anwendung.protokoll());
      warnungen.addAll(parseErgebnis.warnungen());
      entwurfsfassung |= dokument.kopf().art().istEntwurfsfassung();
      quellen.add(dokument.quellenAngabe(parseErgebnis.artikel()));
    }

    var gesamtErgebnis = new BefehlAnwender.AnwendungsErgebnis(gesetz, protokoll);
    var synopse = SynopseBuilder.baue(altesGesetz, gesamtErgebnis, warnungen, vollstaendig);
    var quelle = baseFile.getFileName() + " + " + String.join(" + ", quellen);
    var html = HtmlRenderer.rendere(synopse, quelle, entwurfsfassung);

    return new Ergebnis(
        html,
        gesamtErgebnis.anzahlAngewandt(),
        gesamtErgebnis.anzahlManuell(),
        synopse.eintraege().size(),
        protokoll.size());
  }

  /**
   * Liest die Änderungsdokumente ein, bestimmt ihre Art und sortiert die aus, aus denen keine
   * Synopse zu gewinnen ist. Verworfen wird nichts stillschweigend: Jedes ausgesonderte Dokument
   * hinterlässt eine Warnung, die in der Synopse erscheint.
   */
  private static List<Quelldokument> leseDokumente(
      List<Path> patches, PatchTextExtraktor extraktor, List<String> warnungen) throws Exception {
    var dokumente = new ArrayList<Quelldokument>();
    for (var datei : patches) {
      var rohText = extraktor.extrahiere(datei);
      // Die Erkennung arbeitet auf dem Rohtext: Der Bereiniger entfernt genau die
      // Drucksachenköpfe, aus denen Art und Nummer hervorgehen.
      var kopf = DokumentErkenner.erkenne(rohText);
      log.infof("Datei %s erkannt als %s.", datei, kopf.anzeigeName());
      if (kopf.art() == DokumentArt.OHNE_BEFEHLE) {
        warnungen.add(
            "%s ist ein %s und enthält keine Änderungsbefehle; die Datei wurde übergangen."
                .formatted(datei.getFileName(), kopf.art().anzeigeName()));
        continue;
      }
      if (kopf.art() == DokumentArt.BESCHLUSSEMPFEHLUNG) {
        // Die beschlossene Fassung steht in der zweispaltigen Zusammenstellung, deren rechte
        // Spalte für sich unvollständig ist („unverändert“ statt des Wortlauts, Zitate, die nicht
        // aufgehen). Sie aufzulösen verlangt die zeilenweise Zuordnung beider Spalten und ist noch
        // nicht umgesetzt; eine halb aufgelöste Fassung auszugeben wäre schlimmer als keine.
        warnungen.add(
            ("%s ist eine Beschlussempfehlung. Ihre maßgebliche Fassung steht in der zweispaltigen"
                    + " Zusammenstellung, deren Auflösung noch nicht umgesetzt ist; die Datei wurde"
                    + " übergangen. Für eine Synopse eignet sich der zugrunde liegende"
                    + " Gesetzentwurf%s.")
                .formatted(
                    datei.getFileName(),
                    kopf.bezugsDrucksachen().isEmpty()
                        ? ""
                        : " (Drs. " + kopf.bezugsDrucksachen().get(0) + ")"));
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
                    antrag.datei().getFileName(),
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
          antrag.datei().getFileName(),
          patch.angewandt(),
          parseErgebnis.befehle().size(),
          ziel.datei().getFileName());
      var eingearbeitet = new ArrayList<>(ziel.eingearbeitet());
      eingearbeitet.add(antrag.datei().getFileName() + " [" + antrag.kopf().anzeigeName() + "]");
      ergebnis.set(
          zielIndex,
          new Quelldokument(ziel.datei(), ziel.kopf(), patch.text(), List.copyOf(eingearbeitet)));
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

  /** Gii-XML → {@link GiiXmlLoader}; PDF/Klartext (Landesrecht) → {@link LandesRechtLoader}. */
  static Gesetz ladeStammgesetz(Path baseFile) throws Exception {
    return istGiiXml(baseFile)
        ? new GiiXmlLoader().load(baseFile)
        : new LandesRechtLoader().load(baseFile);
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

  static boolean istGiiXml(Path baseFile) throws IOException {
    var mimeType = new org.apache.tika.Tika().detect(baseFile);
    return mimeType.equals("application/xml") || mimeType.equals("text/xml");
  }
}
