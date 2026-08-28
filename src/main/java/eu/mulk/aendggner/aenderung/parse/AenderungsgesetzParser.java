// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Inkrafttreten;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.gesetz.Gesetz;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

  /**
   * @param inkrafttreten was der Schlussartikel über das Inkrafttreten sagt; {@code null}, wenn das
   *     Dokument keinen lesbaren trägt (Entwürfe ohne Schlussartikel, Auszüge).
   */
  public record ParseErgebnis(
      List<Aenderungsbefehl> befehle,
      List<String> artikel,
      List<String> warnungen,
      @Nullable Inkrafttreten inkrafttreten) {

    public ParseErgebnis(
        List<Aenderungsbefehl> befehle, List<String> artikel, List<String> warnungen) {
      this(befehle, artikel, warnungen, null);
    }
  }

  /**
   * @param text der bereinigte Lineartext des Änderungsgesetzes.
   * @param ziel das Stammgesetz, dessen Änderungsartikel angewandt werden sollen.
   * @param artikelFilter nur diesen Artikel berücksichtigen (z.B. „1“); {@code null} = alle
   *     Artikel, deren Einleitung das Zielgesetz nennt.
   */
  public ParseErgebnis parse(String text, Gesetz ziel, @Nullable String artikelFilter) {
    return parse(text, ziel, artikelFilter, false);
  }

  /**
   * @param entwurfsGrenzen für Entwürfe und Anträge zusätzlich an den dort üblichen
   *     Begründungsmarken abbrechen (Referentenentwürfe überschreiben „Begründung“ gern mit „A.
   *     Allgemeiner Teil“). Für verkündete Gesetze bleibt es bei der schlichten Marke, damit ein
   *     Gesetzblatt nicht an einem gleichlautenden Wort abbricht.
   */
  public ParseErgebnis parse(
      String text, Gesetz ziel, @Nullable String artikelFilter, boolean entwurfsGrenzen) {
    return parse(text, ziel, artikelFilter, entwurfsGrenzen, Seitenkonkordanz.LEER);
  }

  /**
   * @param konkordanz die Zuordnung des Wortbestandes zu den Seiten des Änderungsdokuments; jeder
   *     Befehl erhält daraus seine Fundstelle. {@link Seitenkonkordanz#LEER} für Eingaben ohne
   *     Satzbild — dann bleiben die Befehle ohne Seitenangabe.
   */
  public ParseErgebnis parse(
      String text,
      Gesetz ziel,
      @Nullable String artikelFilter,
      boolean entwurfsGrenzen,
      Seitenkonkordanz konkordanz) {
    var zitate = ZitatExtraktor.extrahiere(text);
    // Ein Leser je Lauf: Er schreitet mit der Erschließung durch das Dokument und darf deshalb
    // nicht zwischen zwei Heften geteilt werden.
    var seiten = konkordanz.leser();
    var artikelBloecke = teileInArtikel(zitate.text(), ARTIKEL_UEBERSCHRIFT, entwurfsGrenzen);
    boolean paragraphenModus = false;
    // Ein Sammelheft führt beide Gliederungen nebeneinander: Die eine Verkündung teilt sich in
    // Artikel, die nächste in Paragraphen (so das hamburgische GVBl. Nr. 17/2026). Es genügt
    // deshalb nicht, die §-Teilung erst dann zu versuchen, wenn das Heft überhaupt keinen Artikel
    // führt — sie ist auch dann zu versuchen, wenn kein Artikel das Stammgesetz betrifft.
    if (artikelBloecke.isEmpty()
        || artikelBloecke.stream()
            .noneMatch(b -> istRelevant(b, ziel, zitate, artikelFilter, false))) {
      var nachParagraphen =
          teileInArtikel(zitate.text(), PARAGRAPH_UEBERSCHRIFT_AUSSEN, entwurfsGrenzen);
      if (nachParagraphen.stream()
          .anyMatch(b -> istRelevant(b, ziel, zitate, artikelFilter, true))) {
        artikelBloecke = nachParagraphen;
        paragraphenModus = true;
      }
    }

    var befehle = new ArrayList<Aenderungsbefehl>();
    var betroffeneArtikel = new ArrayList<String>();
    var warnungen = new ArrayList<>(zitate.warnungen());
    int letzterBetroffen = -1;

    for (int artikelIndex = 0; artikelIndex < artikelBloecke.size(); artikelIndex++) {
      var artikel = artikelBloecke.get(artikelIndex);
      if (!istRelevant(artikel, ziel, zitate, artikelFilter, paragraphenModus)) {
        continue;
      }
      log.infof("Artikel %s betrifft %s.", artikel.label, ziel.jurabk());
      // Nur einmal je Dokument: Ein Gesetzblatt kann mehrere Artikel auf dasselbe Stammgesetz
      // richten, die Auskunft über dessen Stand gilt aber für alle zusammen.
      standWarnung(GliederungsScanner.scanne(artikel.zeilen).vorspann(), ziel)
          .filter(w -> !warnungen.contains(w))
          .ifPresent(warnungen::add);
      if (paragraphenModus && artikelFilter != null && betroffeneArtikel.contains(artikel.label)) {
        // Ein GVBl-Heft enthält mehrere Gesetze mit je eigener §-Zählung.
        warnungen.add(
            "Mehrere Änderungsgesetze im Dokument tragen einen § "
                + artikel.label
                + "; --artikel ist hier mehrdeutig — besser ohne Filter arbeiten (Auswahl über den"
                + " Namen des Stammgesetzes).");
      }
      betroffeneArtikel.add(artikel.label);
      letzterBetroffen = artikelIndex;

      var scan = GliederungsScanner.scanne(artikel.zeilen);
      if (scan.punkte().isEmpty()) {
        // Artikel ohne nummerierte Punkte: Der Text nach der Änderungsformel ist ein
        // einzelner Befehl (häufig bei kleinen Folgeänderungen, z.B. „§ 19 wird durch den
        // folgenden § 19 ersetzt: …“).
        befehle.add(vorspannBefehl(scan.vorspann(), artikel.label, zitate, seiten));
        continue;
      }
      // Der Vorspann kann nach der gesetzesweiten Änderungsformel einen Rahmenbefehl tragen, der
      // den Kontext aller Punkte setzt: „… wird wie folgt geändert: Art. 28 Abs. 1 wird wie folgt
      // geändert:“ (GVBl) bzw. mit eingebettetem Ziel „Art. 7 Abs. 2 des X-Gesetzes … wird wie
      // folgt geändert:“ (dann trägt die Formel das Ziel selbst).
      var kontext = vorspannKontext(scan.vorspann(), artikel.label, zitate, seiten, befehle);
      for (var punkt : scan.punkte()) {
        verarbeitePunkt(punkt, kontext, artikel.label, "", zitate, seiten, befehle);
      }
    }

    // Der Schlussartikel steht hinter dem letzten ändernden Artikel desselben Gesetzes.
    var inkrafttreten =
        letzterBetroffen < 0
            ? null
            : InkrafttretensLeser.waehle(
                artikelBloecke.stream().map(b -> String.join("\n", b.zeilen())).toList(),
                letzterBetroffen + 1);
    return new ParseErgebnis(befehle, betroffeneArtikel, warnungen, inkrafttreten);
  }

  /**
   * Ob dieser Block anzuwenden ist: der benannte, wenn ein Filter gesetzt ist, sonst jeder, dessen
   * Einleitung das Stammgesetz nennt. Im §-Modus muss der benannte Block überdies eine
   * Änderungsformel tragen — ein Heft führt mehrere Verkündungen mit je eigener §-Zählung.
   */
  private static boolean istRelevant(
      ArtikelBlock artikel,
      Gesetz ziel,
      ZitatExtraktor.Ergebnis zitate,
      @Nullable String artikelFilter,
      boolean paragraphenModus) {
    return artikelFilter != null
        ? artikel.label.equals(artikelFilter) && (!paragraphenModus || hatAenderungsformel(artikel))
        : betrifft(artikel, ziel, zitate);
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
      Seitenkonkordanz.Leser seiten,
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
      var provenienz = provenienz(artikelLabel, "", zitate.stelleZitateWiederHer(rest), seiten);
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
      String vorspann,
      String artikelLabel,
      ZitatExtraktor.Ergebnis zitate,
      Seitenkonkordanz.Leser seiten) {
    var normalisiert = vorspann.replaceAll("\\s+", " ").strip();
    var provenienz =
        provenienz(artikelLabel, "", zitate.stelleZitateWiederHer(normalisiert), seiten);

    int formel = normalisiert.indexOf("wird wie folgt geändert:");
    if (formel >= 0) {
      var befehlsText =
          normalisiert.substring(formel + "wird wie folgt geändert:".length()).strip();
      if (!befehlsText.isEmpty()) {
        var befehlsProvenienz =
            provenienz(artikelLabel, "", zitate.stelleZitateWiederHer(befehlsText), seiten);
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
      Seitenkonkordanz.Leser seiten,
      List<Aenderungsbefehl> befehle) {

    // Die Dezimalgliederung trägt ihre Herkunft im Label selbst („6.“ → „6.1“); ihn dem Pfad des
    // Elternpunktes noch einmal anzuhängen ergäbe „6. 6.1“.
    var eigenerPfad =
        pfad.isEmpty() || punkt.label().startsWith(pfad.replaceAll("\\.$", "") + ".")
            ? markerText(punkt)
            : pfad + " " + markerText(punkt);
    var text = punkt.text().replaceAll("\\s+", " ").strip();
    var provenienz =
        provenienz(artikelLabel, eigenerPfad, zitate.stelleZitateWiederHer(text), seiten);

    if (!punkt.kinder().isEmpty()) {
      // Verb-Rahmen („Es werden ersetzt:“): die Unterpunkte tragen die Fundstelle, der Rahmen nur
      // das Verb; jeder Unterpunkt wird dafür zum vollständigen Befehlssatz ergänzt.
      var verb = BefehlErkenner.verbRahmen(text);
      if (verb.isPresent()) {
        for (var kind : punkt.kinder()) {
          verarbeiteVerbRahmenPunkt(
              kind, kontext, artikelLabel, eigenerPfad, verb.get(), zitate, seiten, befehle);
        }
        return;
      }
      // Ein Punkt mit Unterpunkten muss sonst ein Kontextrahmen sein („§ X wird wie folgt
      // geändert:“, auch als Verbund „§ 50 wird zu § 38 und wird wie folgt geändert:“). Steht dort
      // „gefasst“ statt „geändert“, ist das ein amtlicher Schreibfehler: eine Neufassung trüge
      // ihren
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
        verarbeitePunkt(kind, neuerKontext, artikelLabel, eigenerPfad, zitate, seiten, befehle);
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
      Seitenkonkordanz.Leser seiten,
      List<Aenderungsbefehl> befehle) {

    var eigenerPfad = pfad + " " + markerText(punkt);
    var text = punkt.text().replaceAll("\\s+", " ").strip();
    var provenienz =
        provenienz(artikelLabel, eigenerPfad, zitate.stelleZitateWiederHer(text), seiten);
    var befehl =
        BefehlErkenner.vervollstaendigeVerbRahmenPunkt(text, verb)
            .flatMap(satz -> BefehlErkenner.erkenne(satz, kontext, zitate, provenienz));
    befehle.add(
        befehl.orElseGet(
            () -> new UnbekannterBefehl(kontext, provenienz.originalText(), provenienz)));
  }

  private static String markerText(GliederungsScanner.GliederungsPunkt punkt) {
    if (punkt.label().contains(".")) {
      // Dezimalgliederung: Das Label ist schon vollständig („7.1.1“); ein Klammerzeichen
      // dahinter wäre eine Erfindung.
      return punkt.label();
    }
    return punkt.label().matches("\\d+[a-z]?") ? punkt.label() + "." : punkt.label() + ")";
  }

  /**
   * Die Herkunft eines Befehls samt seiner Seite im Heft. Die Seite wird am Wortlaut gesucht, nicht
   * mitgezählt — siehe {@link Seitenkonkordanz}.
   */
  private static Provenienz provenienz(
      String artikelLabel, String pfad, String originalText, Seitenkonkordanz.Leser seiten) {
    return new Provenienz(artikelLabel, pfad, originalText, seiten.seiteVon(originalText));
  }

  private record ArtikelBlock(String label, List<String> zeilen) {}

  // Gesetzentwürfe (RefE/RegE/Drucksachen): Nach dem Gesetzestext folgt der Begründungsteil —
  // Freitext, der keine Befehle enthält und den letzten Artikel nicht verunreinigen darf.
  private static final Pattern BEGRUENDUNG = Pattern.compile("Begründung:?");
  // Weitere Überschriften, mit denen Entwürfe und Anträge ihren Begründungsteil eröffnen.
  private static final Pattern BEGRUENDUNG_ENTWURF =
      Pattern.compile(
          "Begründung:?|Begründung\\s*:?\\s*[–-]?\\s*Allgemeiner Teil|[AB]\\.\\s*(?:Allgemeiner|Besonderer) Teil"
              + "|Zu Artikel \\d+[a-z]?\\b.*");

  private static List<ArtikelBlock> teileInArtikel(
      String platzhalterText, Pattern ueberschrift, boolean entwurfsGrenzen) {
    var bloecke = new ArrayList<ArtikelBlock>();
    var begruendung = entwurfsGrenzen ? BEGRUENDUNG_ENTWURF : BEGRUENDUNG;
    String aktuellesLabel = null;
    var aktuelleZeilen = new ArrayList<String>();

    for (var zeile : platzhalterText.split("\n", -1)) {
      if (aktuellesLabel != null && begruendung.matcher(zeile.strip()).matches()) {
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

  /**
   * Der Einleitungssatz eines Änderungsgesetzes nennt die Fassung, die es fortschreibt („das
   * zuletzt durch Artikel 5 des Gesetzes vom 19. Juni 2020 (BGBl. I S. 1385) geändert worden ist“),
   * und die Quelle des Stammgesetzes nennt ihren eigenen Stand („Zuletzt geändert durch Art. 5 G v.
   * 19.6.2020 I 1385“). Weichen beide voneinander ab, so rechnet das Werkzeug auf der falschen
   * Fassung — und zwar meist auf einer <em>jüngeren</em>, weil die Portale die heute geltende
   * ausliefern.
   *
   * <p>Das ist die häufigste Ursache liegengebliebener Befehle überhaupt und aus der Meldung „kommt
   * im Zieltext nicht vor“ allein nicht zu erkennen. Verglichen werden die beiden Daten; ist das
   * des Stammes das spätere, ergeht eine Warnung. Bei gleichem oder früherem Datum, bei fehlender
   * Standangabe (handgepflegter Klartext) und bei unlesbarem Einleitungssatz schweigt die Prüfung —
   * geraten wird nicht.
   */
  private static Optional<String> standWarnung(String vorspann, Gesetz ziel) {
    var wortlautStand = ziel.wortlautStand();
    if (wortlautStand == null) {
      return Optional.empty();
    }
    var satzDatum = EINLEITUNGS_DATUM.matcher(vorspann.replaceAll("\\s+", " "));
    if (!satzDatum.find()) {
      return Optional.empty();
    }
    var genannt = DeutschesDatum.lies(satzDatum.group(1), satzDatum.group(2), satzDatum.group(3));
    if (genannt == null || !wortlautStand.isAfter(genannt)) {
      return Optional.empty();
    }
    return Optional.of(
        "Das Stammgesetz ist jünger als das Änderungsgesetz: Sein Wortlaut ist bis zum "
            + DeutschesDatum.schreibe(wortlautStand)
            + " fortgeschrieben („"
            + standHerkunft(ziel, wortlautStand)
            + "“), während der Einleitungssatz die Fassung vom "
            + satzDatum.group(1)
            + ". "
            + satzDatum.group(2)
            + " "
            + satzDatum.group(3)
            + " fortschreibt. Befehle, deren Zieltext „im Zieltext nicht vorkommt“, beruhen"
            + " wahrscheinlich hierauf und nicht auf einem Mangel des Werkzeugs; abzuhelfen ist"
            + " ihnen nur mit der zeitrichtigen Fassung des Stammgesetzes.");
  }

  /**
   * Woher der Wortlautstand rührt: aus einem Heft, das das Erzeugnis selbst angewandt hat, sonst
   * aus der Standangabe der Quelle. In der Kette ist die Fassung nämlich jünger als ihre eigene
   * Standzeile — sie trägt die Hefte, die seither auf sie angewandt worden sind.
   */
  private static String standHerkunft(Gesetz ziel, LocalDate wortlautStand) {
    for (var heft : ziel.fortschreibungen()) {
      if (wortlautStand.equals(heft.datum())) {
        return heft.bezeichnung();
      }
    }
    return ziel.stand() != null ? ziel.stand().kommentar() : "";
  }

  /**
   * „… zuletzt durch Artikel 5 des Gesetzes vom 19. Juni 2020 (BGBl. I S. 1385) geändert worden
   * ist“ — das Datum der <em>letzten Änderung</em>, nicht das der Ausfertigung. Deshalb muss ihm
   * die Änderungsklausel vorausgehen: Der Einleitungssatz nennt zuerst das Ausfertigungsdatum („Das
   * Infektionsschutzgesetz vom 20. Juli 2000“), und dieses zu vergleichen ergäbe bei jedem je
   * geänderten Gesetz eine Warnung.
   */
  private static final Pattern EINLEITUNGS_DATUM =
      Pattern.compile("durch (?:Artikel|Art\\.)[^.]{0,80}?vom " + DeutschesDatum.MUSTER);

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
