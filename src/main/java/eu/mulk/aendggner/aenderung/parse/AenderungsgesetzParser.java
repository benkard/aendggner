package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.gesetz.Gesetz;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Parst den bereinigten Lineartext eines Änderungsgesetzes zu einer Liste von Änderungsbefehlen.
 *
 * <p>Es werden nur Artikel berücksichtigt, deren Einleitung das Zielgesetz nennt (per {@code
 * artikelFilter} übersteuerbar). Nicht erkannte Befehle erscheinen als {@link UnbekannterBefehl} im
 * Ergebnis.
 */
public final class AenderungsgesetzParser {

  private static final Logger log = Logger.getLogger(AenderungsgesetzParser.class);

  private static final Pattern ARTIKEL_UEBERSCHRIFT = Pattern.compile("^Artikel\\s+(\\d+[a-z]?)$");

  public record ParseErgebnis(
      List<Aenderungsbefehl> befehle, List<String> artikel, List<String> warnungen) {}

  /**
   * @param text der bereinigte Lineartext des Änderungsgesetzes.
   * @param ziel das Stammgesetz, dessen Änderungsartikel angewandt werden sollen.
   * @param artikelFilter nur diesen Artikel berücksichtigen (z.B. „1“); {@code null} = alle
   *     Artikel, deren Einleitung das Zielgesetz nennt.
   */
  public ParseErgebnis parse(String text, Gesetz ziel, @Nullable String artikelFilter) {
    var zitate = ZitatExtraktor.extrahiere(text);
    var artikelBloecke = teileInArtikel(zitate.text());

    var befehle = new ArrayList<Aenderungsbefehl>();
    var betroffeneArtikel = new ArrayList<String>();

    for (var artikel : artikelBloecke) {
      var relevant =
          artikelFilter != null
              ? artikel.label.equals(artikelFilter)
              : betrifft(artikel, ziel, zitate);
      if (!relevant) {
        continue;
      }
      log.infof("Artikel %s betrifft %s.", artikel.label, ziel.jurabk());
      betroffeneArtikel.add(artikel.label);

      var scan = GliederungsScanner.scanne(artikel.zeilen);
      if (scan.punkte().isEmpty()) {
        befehle.add(
            new UnbekannterBefehl(
                Stelle.LEER,
                zitate.stelleZitateWiederHer(scan.vorspann()),
                new Provenienz(artikel.label, "", zitate.stelleZitateWiederHer(scan.vorspann()))));
        continue;
      }
      for (var punkt : scan.punkte()) {
        verarbeitePunkt(punkt, Stelle.LEER, artikel.label, "", zitate, befehle);
      }
    }

    return new ParseErgebnis(befehle, betroffeneArtikel, zitate.warnungen());
  }

  private static void verarbeitePunkt(
      GliederungsScanner.GliederungsPunkt punkt,
      Stelle kontext,
      String artikelLabel,
      String pfad,
      ZitatExtraktor.Ergebnis zitate,
      List<Aenderungsbefehl> befehle) {

    var eigenerPfad = pfad.isEmpty() ? markerText(punkt) : pfad + " " + markerText(punkt);
    var text = punkt.text().replaceAll("\\s+", " ").strip();
    var provenienz = new Provenienz(artikelLabel, eigenerPfad, zitate.stelleZitateWiederHer(text));

    if (!punkt.kinder().isEmpty()) {
      // Ein Punkt mit Unterpunkten muss ein Kontextrahmen sein („§ X wird wie folgt geändert:“).
      var rahmen = BefehlErkenner.kontextRahmen(text);
      var neuerKontext = kontext;
      if (rahmen.isPresent()) {
        neuerKontext = kontext.plus(rahmen.get());
      } else {
        befehle.add(new UnbekannterBefehl(kontext, provenienz.originalText(), provenienz));
      }
      for (var kind : punkt.kinder()) {
        verarbeitePunkt(kind, neuerKontext, artikelLabel, eigenerPfad, zitate, befehle);
      }
      return;
    }

    // Blattpunkt: einzelner Befehl.
    var befehl = BefehlErkenner.erkenne(text, kontext, zitate, provenienz);
    befehle.add(
        befehl.orElseGet(
            () -> new UnbekannterBefehl(kontext, provenienz.originalText(), provenienz)));
  }

  private static String markerText(GliederungsScanner.GliederungsPunkt punkt) {
    return punkt.label().matches("\\d+[a-z]?") ? punkt.label() + "." : punkt.label() + ")";
  }

  private record ArtikelBlock(String label, List<String> zeilen) {}

  private static List<ArtikelBlock> teileInArtikel(String platzhalterText) {
    var bloecke = new ArrayList<ArtikelBlock>();
    String aktuellesLabel = null;
    var aktuelleZeilen = new ArrayList<String>();

    for (var zeile : platzhalterText.split("\n", -1)) {
      var matcher = ARTIKEL_UEBERSCHRIFT.matcher(zeile.strip());
      if (matcher.matches()) {
        if (aktuellesLabel != null) {
          bloecke.add(new ArtikelBlock(aktuellesLabel, List.copyOf(aktuelleZeilen)));
        }
        aktuellesLabel = matcher.group(1);
        aktuelleZeilen.clear();
      } else if (aktuellesLabel != null) {
        aktuelleZeilen.add(zeile);
      }
    }
    if (aktuellesLabel != null) {
      bloecke.add(new ArtikelBlock(aktuellesLabel, List.copyOf(aktuelleZeilen)));
    }
    return bloecke;
  }

  /**
   * Ein Artikel betrifft das Zielgesetz, wenn seine Einleitung (Text vor dem ersten
   * Gliederungspunkt) den Namen oder die Abkürzung des Gesetzes zusammen mit der Änderungsformel
   * nennt.
   */
  private static boolean betrifft(
      ArtikelBlock artikel, Gesetz ziel, ZitatExtraktor.Ergebnis zitate) {
    var scan = GliederungsScanner.scanne(artikel.zeilen);
    var vorspann = scan.vorspann().replaceAll("\\s+", " ");
    if (!vorspann.contains("wird wie folgt geändert")) {
      return false;
    }
    return (ziel.kurzue() != null && vorspann.contains(ziel.kurzue()))
        || (ziel.langue() != null && vorspann.contains(ziel.langue()))
        || vorspann.matches(".*\\b" + Pattern.quote(ziel.jurabk()) + "\\b.*");
  }
}
