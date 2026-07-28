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

  // Bayerische Änderungsgesetze gliedern sich in Paragraphen statt Artikel („§ 1“ als
  // freistehende Überschriftzeile). Die §-Teilung greift nur, wenn das Dokument keinerlei
  // Artikel-Überschriften enthält — Bundesgesetze mit unzitiert abgedruckten Ablösegesetzen
  // (ProdHaftG) enthalten freistehende „§ N“-Zeilen innerhalb ihrer Artikel.
  private static final Pattern PARAGRAPH_UEBERSCHRIFT_AUSSEN =
      Pattern.compile("^§\\s*(\\d+[a-z]?)$");

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
    var artikelBloecke = teileInArtikel(zitate.text(), ARTIKEL_UEBERSCHRIFT);
    boolean paragraphenModus = false;
    if (artikelBloecke.isEmpty()) {
      artikelBloecke = teileInArtikel(zitate.text(), PARAGRAPH_UEBERSCHRIFT_AUSSEN);
      paragraphenModus = !artikelBloecke.isEmpty();
    }

    var befehle = new ArrayList<Aenderungsbefehl>();
    var betroffeneArtikel = new ArrayList<String>();
    var warnungen = new ArrayList<>(zitate.warnungen());

    for (var artikel : artikelBloecke) {
      var relevant =
          artikelFilter != null
              ? artikel.label.equals(artikelFilter)
                  && (!paragraphenModus || hatAenderungsformel(artikel))
              : betrifft(artikel, ziel, zitate);
      if (!relevant) {
        continue;
      }
      log.infof("Artikel %s betrifft %s.", artikel.label, ziel.jurabk());
      if (paragraphenModus && artikelFilter != null && betroffeneArtikel.contains(artikel.label)) {
        // Ein GVBl-Heft enthält mehrere Gesetze mit je eigener §-Zählung.
        warnungen.add(
            "Mehrere Änderungsgesetze im Dokument tragen einen § "
                + artikel.label
                + "; --artikel ist hier mehrdeutig — besser ohne Filter arbeiten (Auswahl über den"
                + " Namen des Stammgesetzes).");
      }
      betroffeneArtikel.add(artikel.label);

      var scan = GliederungsScanner.scanne(artikel.zeilen);
      if (scan.punkte().isEmpty()) {
        // Artikel ohne nummerierte Punkte: Der Text nach der Änderungsformel ist ein
        // einzelner Befehl (häufig bei kleinen Folgeänderungen, z.B. „§ 19 wird durch den
        // folgenden § 19 ersetzt: …“).
        befehle.add(vorspannBefehl(scan.vorspann(), artikel.label, zitate));
        continue;
      }
      // Der Vorspann kann nach der gesetzesweiten Änderungsformel einen Rahmenbefehl tragen, der
      // den Kontext aller Punkte setzt: „… wird wie folgt geändert: Art. 28 Abs. 1 wird wie folgt
      // geändert:“ (GVBl) bzw. mit eingebettetem Ziel „Art. 7 Abs. 2 des X-Gesetzes … wird wie
      // folgt geändert:“ (dann trägt die Formel das Ziel selbst).
      var kontext = vorspannKontext(scan.vorspann(), artikel.label, zitate, befehle);
      for (var punkt : scan.punkte()) {
        verarbeitePunkt(punkt, kontext, artikel.label, "", zitate, befehle);
      }
    }

    return new ParseErgebnis(befehle, betroffeneArtikel, warnungen);
  }

  private static final String AENDERUNGSFORMEL = "wird wie folgt geändert:";

  private static boolean hatAenderungsformel(ArtikelBlock artikel) {
    var scan = GliederungsScanner.scanne(artikel.zeilen);
    return scan.vorspann().replaceAll("\\s+", " ").contains("wird wie folgt geändert");
  }

  // Eingebettetes Rahmenziel in der Änderungsformel selbst: „Art. 7 Abs. 2 des Bayerischen
  // Umweltinformationsgesetzes … wird wie folgt geändert:“, „§ 2 Satz 1 des Gesetzes zur
  // Errichtung … wird wie folgt geändert:“. Der Lookbehind auf „durch “ schließt die Zitierkette
  // der Änderungshistorie aus („das zuletzt durch § 5 des Gesetzes vom … geändert worden ist“) —
  // dort ist die Norm nie das Subjekt der Formel. Die vordere Grenze darf nicht \b sein: „§“ ist
  // selbst kein Wortzeichen, ein \b davor verlangte also ein vorangehendes Wortzeichen und machte
  // den §-Zweig unerreichbar.
  private static final Pattern EINGEBETTETES_ZIEL =
      Pattern.compile(
          "(?<!durch )(?<![\\p{L}\\d])((?:§|Art\\.)\\s*\\d+[a-z]?"
              + "(?:\\s+(?:Absatz|Abs\\.|Satz|Nummer|Nr\\.|Buchstabe|Buchst\\.)\\s+\\d+[a-z]?)*)"
              + "\\s+(?:des|der)\\s+\\p{Lu}");

  /**
   * Bestimmt aus dem Vorspann eines Artikels mit Gliederungspunkten den gemeinsamen Kontext der
   * Punkte. Ohne erkennbaren Rahmen bleibt der Kontext leer (die Punkte müssen ihre Ziele dann
   * selbst vollständig nennen; Unerkanntes landet als „nicht erkannt“ im Protokoll — niemals
   * stillschweigend).
   */
  private static Stelle vorspannKontext(
      String vorspann,
      String artikelLabel,
      ZitatExtraktor.Ergebnis zitate,
      List<Aenderungsbefehl> befehle) {
    var normalisiert = vorspann.replaceAll("\\s+", " ").strip();
    // Die erste Formel ist die gesetzesweite Einleitung; ein dahinter stehender Rahmenbefehl
    // trägt ggf. seine eigene Formel („… wird wie folgt geändert: Art. 28 Abs. 1 wird wie folgt
    // geändert:“).
    int formel = normalisiert.indexOf(AENDERUNGSFORMEL);
    if (formel < 0) {
      return Stelle.LEER;
    }
    var rest = normalisiert.substring(formel + AENDERUNGSFORMEL.length()).strip();
    if (!rest.isEmpty()) {
      // Rahmenbefehl hinter der gesetzesweiten Formel („Art. 28 Abs. 1 wird wie folgt geändert:“,
      // auch als Umnummerierungs-Verbund).
      var provenienz = new Provenienz(artikelLabel, "", zitate.stelleZitateWiederHer(rest));
      var rahmen = BefehlErkenner.rahmenMitBefehl(rest, Stelle.LEER, provenienz);
      if (rahmen.isPresent()) {
        if (rahmen.get().begleitbefehl() != null) {
          befehle.add(rahmen.get().begleitbefehl());
        }
        return rahmen.get().stelle();
      }
      return Stelle.LEER;
    }
    // Die Formel endet den Vorspann: Trägt sie ihr Ziel eingebettet („Art. 7 Abs. 2 des
    // X-Gesetzes … wird wie folgt geändert:“), wird dieses zum Kontext.
    var eingebettet = EINGEBETTETES_ZIEL.matcher(normalisiert.substring(0, formel));
    if (eingebettet.find()) {
      return StellenParser.parse(eingebettet.group(1)).orElse(Stelle.LEER);
    }
    return Stelle.LEER;
  }

  /** Versucht, den Vorspann-Rest nach der Änderungsformel als einzelnen Befehl zu erkennen. */
  private static Aenderungsbefehl vorspannBefehl(
      String vorspann, String artikelLabel, ZitatExtraktor.Ergebnis zitate) {
    var normalisiert = vorspann.replaceAll("\\s+", " ").strip();
    var provenienz = new Provenienz(artikelLabel, "", zitate.stelleZitateWiederHer(normalisiert));

    int formel = normalisiert.indexOf("wird wie folgt geändert:");
    if (formel >= 0) {
      var befehlsText =
          normalisiert.substring(formel + "wird wie folgt geändert:".length()).strip();
      if (!befehlsText.isEmpty()) {
        var befehlsProvenienz =
            new Provenienz(artikelLabel, "", zitate.stelleZitateWiederHer(befehlsText));
        var befehl = BefehlErkenner.erkenne(befehlsText, Stelle.LEER, zitate, befehlsProvenienz);
        if (befehl.isPresent()) {
          return befehl.get();
        }
      }
    }
    return new UnbekannterBefehl(Stelle.LEER, provenienz.originalText(), provenienz);
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
      // Verb-Rahmen („Es werden ersetzt:“): die Unterpunkte tragen die Fundstelle, der Rahmen nur
      // das Verb; jeder Unterpunkt wird dafür zum vollständigen Befehlssatz ergänzt.
      var verb = BefehlErkenner.verbRahmen(text);
      if (verb.isPresent()) {
        for (var kind : punkt.kinder()) {
          verarbeiteVerbRahmenPunkt(
              kind, kontext, artikelLabel, eigenerPfad, verb.get(), zitate, befehle);
        }
        return;
      }
      // Ein Punkt mit Unterpunkten muss sonst ein Kontextrahmen sein („§ X wird wie folgt
      // geändert:“, auch als Verbund „§ 50 wird zu § 38 und wird wie folgt geändert:“). Steht dort
      // „gefasst“ statt „geändert“, ist das ein amtlicher Schreibfehler: eine Neufassung trüge ihren
      // Wortlaut als Zitat, keine Unterpunkte mit eigenen Änderungsbefehlen.
      var rahmen = BefehlErkenner.rahmenMitBefehl(text, kontext, provenienz);
      if (rahmen.isEmpty() && text.endsWith("wie folgt gefasst:")) {
        rahmen =
            BefehlErkenner.rahmenMitBefehl(
                text.substring(0, text.length() - "gefasst:".length()) + "geändert:",
                kontext,
                provenienz);
      }
      var neuerKontext = kontext;
      if (rahmen.isPresent()) {
        if (rahmen.get().begleitbefehl() != null) {
          befehle.add(rahmen.get().begleitbefehl());
        }
        neuerKontext = kontext.plus(rahmen.get().stelle());
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

  /** Unterpunkt eines Verb-Rahmens: erst zum vollständigen Satz ergänzen, dann erkennen. */
  private static void verarbeiteVerbRahmenPunkt(
      GliederungsScanner.GliederungsPunkt punkt,
      Stelle kontext,
      String artikelLabel,
      String pfad,
      String verb,
      ZitatExtraktor.Ergebnis zitate,
      List<Aenderungsbefehl> befehle) {

    var eigenerPfad = pfad + " " + markerText(punkt);
    var text = punkt.text().replaceAll("\\s+", " ").strip();
    var provenienz = new Provenienz(artikelLabel, eigenerPfad, zitate.stelleZitateWiederHer(text));
    var befehl =
        BefehlErkenner.vervollstaendigeVerbRahmenPunkt(text, verb)
            .flatMap(satz -> BefehlErkenner.erkenne(satz, kontext, zitate, provenienz));
    befehle.add(
        befehl.orElseGet(
            () -> new UnbekannterBefehl(kontext, provenienz.originalText(), provenienz)));
  }

  private static String markerText(GliederungsScanner.GliederungsPunkt punkt) {
    return punkt.label().matches("\\d+[a-z]?") ? punkt.label() + "." : punkt.label() + ")";
  }

  private record ArtikelBlock(String label, List<String> zeilen) {}

  private static List<ArtikelBlock> teileInArtikel(String platzhalterText, Pattern ueberschrift) {
    var bloecke = new ArrayList<ArtikelBlock>();
    String aktuellesLabel = null;
    var aktuelleZeilen = new ArrayList<String>();

    for (var zeile : platzhalterText.split("\n", -1)) {
      // Gesetzentwürfe (RefE/RegE/Drucksachen): Nach dem Gesetzestext folgt der Begründungsteil
      // — Freitext, der keine Befehle enthält und den letzten Artikel nicht verunreinigen darf.
      if (aktuellesLabel != null && zeile.strip().matches("Begründung:?")) {
        break;
      }
      var matcher = ueberschrift.matcher(zeile.strip());
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
   * nennt. Der Vergleich ist deklinationstolerant („Das Allgemeine Gleichbehandlungsgesetz“ matcht
   * die amtliche Bezeichnung „Allgemeines Gleichbehandlungsgesetz“).
   */
  private static boolean betrifft(
      ArtikelBlock artikel, Gesetz ziel, ZitatExtraktor.Ergebnis zitate) {
    var scan = GliederungsScanner.scanne(artikel.zeilen);
    var vorspann = scan.vorspann().replaceAll("\\s+", " ");
    if (!vorspann.contains("wird wie folgt geändert")) {
      return false;
    }
    // Ausführungs-/Durchführungstitel nennen das Stammgesetz nur als Genitiv-Attribut („Die
    // Verordnung zur Ausführung des Bayerischen Jagdgesetzes (AVBayJG) … wird wie folgt
    // geändert“) — solche Nennungen zählen nicht als Treffer.
    vorspann =
        vorspann.replaceAll("\\b(?:zur Ausführung|zur Durchführung|zum Vollzug) des [^,()]*", "");
    var vorspannStamm = stammForm(vorspann);
    return (ziel.kurzue() != null && vorspannStamm.contains(stammForm(ziel.kurzue())))
        || (ziel.langue() != null && vorspannStamm.contains(stammForm(ziel.langue())))
        || vorspann.matches(".*\\b" + Pattern.quote(ziel.jurabk()) + "\\b.*");
  }

  private static final List<String> STAMM_SUFFIXE = List.of("es", "er", "en", "em", "e", "s", "n");

  /** Reduziert jedes Wort grob auf seinen Stamm, um Deklinationsendungen zu neutralisieren. */
  private static String stammForm(String text) {
    var sb = new StringBuilder();
    for (var wort : text.split("\\s+")) {
      var stamm = wort;
      for (var suffix : STAMM_SUFFIXE) {
        if (stamm.length() - suffix.length() >= 4 && stamm.endsWith(suffix)) {
          stamm = stamm.substring(0, stamm.length() - suffix.length());
          break;
        }
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(stamm);
    }
    return sb.toString();
  }
}
