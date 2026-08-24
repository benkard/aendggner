// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Parst einen Änderungsantrag — ein Dokument, das nicht das Stammgesetz ändert, sondern einen
 * Gesetzentwurf.
 *
 * <p>Der Antrag adressiert deshalb zwei Ebenen zugleich. Der Rahmensatz „In § 3 Nr. 22 wird § 18
 * Nr. 1 wie folgt geändert:“ nennt zuerst die Stelle <em>in der Drucksache</em> (§ 3 Nr. 22 = der
 * 22. Änderungsbefehl des dritten Entwurfsparagraphen) und dann die Stelle <em>in dem Text, den
 * dieser Befehl zitiert</em> (der neue § 18, dessen Nr. 1). Die Unterpunkte tragen die eigentliche
 * Operation und zielen auf Glieder innerhalb dieses Zitats.
 *
 * <p>Der Antrag wird daher nicht auf das Stammgesetz angewandt, sondern von {@link EntwurfsPatcher}
 * auf den Entwurfstext; erst der so geänderte Entwurf läuft anschließend durch die gewöhnliche
 * Pipeline.
 */
public final class AenderungsantragParser {

  private static final Logger log = Logger.getLogger(AenderungsantragParser.class);

  private AenderungsantragParser() {}

  /**
   * Eine Stelle in der Drucksache selbst: ihr Container („§ 3“, „Artikel 1“) und der Pfad des
   * Gliederungspunkts darin („22“, oder „3“ → „a“).
   *
   * <p>Bewusst nicht als {@link Stelle} modelliert: Eine {@code Stelle} bezeichnet eine Fundstelle
   * im <em>Gesetz</em>; „§ 3 Nr. 22“ meint hier aber den 22. Änderungsbefehl einer Drucksache, also
   * ein ganz anderes Bezugssystem. Die beiden zu vermengen brächte den Anwender durcheinander.
   */
  public record DrucksachenStelle(String container, List<String> punktPfad) {

    public DrucksachenStelle {
      punktPfad = List.copyOf(punktPfad);
    }

    public static final DrucksachenStelle LEER = new DrucksachenStelle("", List.of());

    public boolean istLeer() {
      return container.isEmpty() && punktPfad.isEmpty();
    }

    public String anzeigeText() {
      return istLeer() ? "(ohne Stelle)" : (container + " " + String.join(" ", punktPfad)).strip();
    }
  }

  /**
   * @param drucksachenStelle die Stelle im Entwurfstext (etwa „§ 3 Nr. 22“, „Artikel 1 Nummer 3“).
   * @param zitatStelle die Stelle innerhalb des von dort zitierten Textes; {@link Stelle#LEER},
   *     wenn der Rahmen keine nennt.
   * @param befehl die auszuführende Operation.
   */
  public record MetaBefehl(
      DrucksachenStelle drucksachenStelle, Stelle zitatStelle, Aenderungsbefehl befehl) {}

  public record ParseErgebnis(List<MetaBefehl> befehle, List<String> warnungen) {}

  // „Der Landtag wolle beschließen:“, „Der Bundestag wolle beschließen:“.
  private static final Pattern BESCHLUSSFORMEL =
      Pattern.compile("^.*\\bwolle\\s+beschließen\\s*:?\\s*$");
  private static final Pattern BEGRUENDUNG = Pattern.compile("^Begründung\\s*:?\\s*$");

  // „In § 3 Nr. 22 wird § 18 Nr. 1 wie folgt geändert:“ — Drucksachenstelle und Zitatstelle.
  private static final Pattern RAHMEN_ZWEISTUFIG =
      Pattern.compile("^In (.+?) wird (.+?) wie folgt geändert:$");
  // „In Artikel 1 Nummer 3 werden folgende Änderungen vorgenommen:“ — nur die Drucksachenstelle.
  private static final Pattern RAHMEN_EINSTUFIG =
      Pattern.compile(
          "^In (.+?) (?:wird|werden) (?:folgende Änderungen vorgenommen|die folgenden Änderungen"
              + " vorgenommen):$");
  // „§ 3 Nr. 22 wird wie folgt geändert:“ — ohne führendes „In“.
  private static final Pattern RAHMEN_SCHLICHT =
      Pattern.compile("^(.+?) wird wie folgt geändert:$");

  /**
   * @param text der bereinigte Lineartext des Änderungsantrags.
   */
  public static ParseErgebnis parse(String text) {
    var warnungen = new ArrayList<String>();
    var beschlussTeil = beschlussTeil(text);
    if (beschlussTeil.isEmpty()) {
      warnungen.add(
          "Im Änderungsantrag wurde keine Beschlussformel („… wolle beschließen:“) gefunden;"
              + " es wurden keine Befehle gelesen.");
      return new ParseErgebnis(List.of(), warnungen);
    }

    var zitate = ZitatExtraktor.extrahiere(String.join("\n", beschlussTeil));
    warnungen.addAll(zitate.warnungen());
    var scan = GliederungsScanner.scanne(List.of(zitate.text().split("\n", -1)));

    var befehle = new ArrayList<MetaBefehl>();
    var rahmen = rahmen(scan.vorspann(), warnungen);
    if (scan.punkte().isEmpty()) {
      // Antrag ohne Gliederungspunkte: der Rahmensatz trägt den Befehl selbst.
      erkenneBefehl(scan.vorspann(), rahmen, "", zitate, befehle, warnungen);
    }
    for (var punkt : scan.punkte()) {
      verarbeitePunkt(punkt, rahmen, "", zitate, befehle, warnungen);
    }
    return new ParseErgebnis(befehle, warnungen);
  }

  /** Der Rahmen eines Antragsabschnitts: wohin in der Drucksache und wohin in deren Zitat. */
  private record Rahmen(DrucksachenStelle drucksachenStelle, Stelle zitatStelle) {
    static final Rahmen LEER = new Rahmen(DrucksachenStelle.LEER, Stelle.LEER);
  }

  // „§ 3 Nr. 22“, „Artikel 1 Nummer 3 Buchstabe a“ — Container und Punktpfad einer Drucksache.
  private static final Pattern DRUCKSACHEN_STELLE =
      Pattern.compile(
          "^(?:(§|Art\\.|Artikel)\\s*(\\d+[a-z]?))?\\s*"
              + "((?:(?:Nr\\.|Nummer|Buchstabe|Buchst\\.)\\s*[\\w.]+\\s*)*)$");
  private static final Pattern PUNKT_GLIED =
      Pattern.compile("(?:Nr\\.|Nummer|Buchstabe|Buchst\\.)\\s*([\\w.]+)");

  /** Die Zeilen zwischen Beschlussformel und Begründung — nur dort stehen Befehle. */
  private static List<String> beschlussTeil(String text) {
    var zeilen = text.split("\n", -1);
    int start = -1;
    for (int i = 0; i < zeilen.length; i++) {
      if (BESCHLUSSFORMEL.matcher(zeilen[i].strip()).matches()) {
        start = i + 1;
        break;
      }
    }
    if (start < 0) {
      return List.of();
    }
    var teil = new ArrayList<String>();
    for (int i = start; i < zeilen.length; i++) {
      if (BEGRUENDUNG.matcher(zeilen[i].strip()).matches()) {
        break;
      }
      teil.add(zeilen[i]);
    }
    return teil;
  }

  private static Rahmen rahmen(String vorspann, List<String> warnungen) {
    var satz = vorspann.replaceAll("\\s+", " ").strip();
    if (satz.isEmpty()) {
      return Rahmen.LEER;
    }
    var zweistufig = RAHMEN_ZWEISTUFIG.matcher(satz);
    if (zweistufig.matches()) {
      return new Rahmen(drucksachenStelle(zweistufig.group(1)), stelle(zweistufig.group(2)));
    }
    var einstufig = RAHMEN_EINSTUFIG.matcher(satz);
    if (einstufig.matches()) {
      return new Rahmen(drucksachenStelle(einstufig.group(1)), Stelle.LEER);
    }
    var schlicht = RAHMEN_SCHLICHT.matcher(satz);
    if (schlicht.matches()) {
      return new Rahmen(drucksachenStelle(schlicht.group(1)), Stelle.LEER);
    }
    warnungen.add("Der Rahmensatz des Änderungsantrags wurde nicht verstanden: „" + satz + "“");
    return Rahmen.LEER;
  }

  private static Stelle stelle(String phrase) {
    return StellenParser.parse(phrase.strip()).orElse(Stelle.LEER);
  }

  private static DrucksachenStelle drucksachenStelle(String phrase) {
    var treffer = DRUCKSACHEN_STELLE.matcher(phrase.strip());
    if (!treffer.matches()) {
      return DrucksachenStelle.LEER;
    }
    var container =
        treffer.group(1) == null
            ? ""
            : (treffer.group(1).equals("Artikel") ? "Artikel" : treffer.group(1))
                + " "
                + treffer.group(2);
    var pfad = new ArrayList<String>();
    var glieder = PUNKT_GLIED.matcher(treffer.group(3));
    while (glieder.find()) {
      pfad.add(glieder.group(1));
    }
    return new DrucksachenStelle(container, pfad);
  }

  private static void verarbeitePunkt(
      GliederungsScanner.GliederungsPunkt punkt,
      Rahmen rahmen,
      String pfad,
      ZitatExtraktor.Ergebnis zitate,
      List<MetaBefehl> befehle,
      List<String> warnungen) {

    var eigenerPfad = pfad.isEmpty() ? markerText(punkt) : pfad + " " + markerText(punkt);
    var text = punkt.text().replaceAll("\\s+", " ").strip();

    if (!punkt.kinder().isEmpty()) {
      // Ein Punkt mit Unterpunkten verfeinert den Rahmen für seine Kinder.
      var verfeinert = rahmen(text, warnungen);
      var neuerRahmen =
          verfeinert.drucksachenStelle().istLeer()
              ? rahmen
              : new Rahmen(
                  verfeinert.drucksachenStelle(),
                  rahmen.zitatStelle().plus(verfeinert.zitatStelle()));
      for (var kind : punkt.kinder()) {
        verarbeitePunkt(kind, neuerRahmen, eigenerPfad, zitate, befehle, warnungen);
      }
      return;
    }

    erkenneBefehl(text, rahmen, eigenerPfad, zitate, befehle, warnungen);
  }

  private static void erkenneBefehl(
      String text,
      Rahmen rahmen,
      String pfad,
      ZitatExtraktor.Ergebnis zitate,
      List<MetaBefehl> befehle,
      List<String> warnungen) {

    var provenienz = new Provenienz("Antrag", pfad, zitate.stelleZitateWiederHer(text));
    // Anträge kürzen das Hilfsverb weg („… ersetzt.“ statt „… wird … ersetzt.“); erst die
    // ergänzte Form entspricht den Mustern des Befehlserkenners.
    var satz = BefehlErkenner.vervollstaendigeAntragsPunkt(text).orElse(text);
    var befehl = BefehlErkenner.erkenne(satz, Stelle.LEER, zitate, provenienz);
    if (befehl.isEmpty()) {
      befehle.add(
          new MetaBefehl(
              rahmen.drucksachenStelle(),
              rahmen.zitatStelle(),
              new Aenderungsbefehl.UnbekannterBefehl(
                  Stelle.LEER, provenienz.originalText(), provenienz)));
      return;
    }
    log.infof("Antragsbefehl erkannt: %s", satz);
    befehle.add(new MetaBefehl(rahmen.drucksachenStelle(), rahmen.zitatStelle(), befehl.get()));
  }

  private static String markerText(GliederungsScanner.GliederungsPunkt punkt) {
    return punkt.label().matches("\\d+[a-z]?") ? punkt.label() + "." : punkt.label() + ")";
  }
}
