package eu.mulk.aendggner.aenderung.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bereinigt aus PDFs extrahierten Rohtext eines Änderungsgesetzes: entfernt Kolumnentitel,
 * Seitenzahlen und Drucksachen-Seitenköpfe, zieht Silbentrennungen am Zeilenende zusammen und
 * normalisiert Anführungszeichen-Glyphen.
 *
 * <p>Wichtig für die Silbentrennung: Die Verarbeitung erhält den Trailing-Whitespace der Zeilen bis
 * zum Schluss, denn er ist das Unterscheidungssignal für markerlose Trennungen (siehe {@link
 * #verbindeUmbrueche}).
 *
 * <p>PDF-extrahierter Text trägt außerdem die geometrische Umbruch-Klassifikation des {@link
 * FontgroessenFilter}s ({@link #HARTES_ZEILENENDE}/{@link #WEICHES_ZEILENENDE} am Zeilenende):
 * Weiche Umbrüche (Zeile endet am rechten Blocksatzrand) werden zu Fließtext zusammengezogen,
 * harte (deutlich davor) bleiben als Zeilenumbruch erhalten. Nach {@link #bereinige} ist damit
 * jeder verbleibende Zeilenumbruch nach bester Einschätzung beabsichtigt; die Marker selbst
 * verlassen diese Klasse nie.
 */
public final class TextBereiniger {

  /** Vom {@link FontgroessenFilter} an eine Zeile angehängt, deren Ende deutlich vor dem lokalen
   * rechten Satzspiegelrand liegt: ein bewusstes Zeilenende. */
  static final char HARTES_ZEILENENDE = '\uE000';

  /** Vom {@link FontgroessenFilter} an eine Zeile angehängt, die am lokalen rechten
   * Satzspiegelrand endet: ein automatischer (weicher) Blocksatz-Umbruch. */
  static final char WEICHES_ZEILENENDE = '\uE001';

  /** Geometrische Einordnung eines Zeilenendes (siehe {@link FontgroessenFilter}). */
  private enum Umbruch {
    HART,
    WEICH,
    UNBEKANNT
  }

  /** Eine Rohtextzeile samt der Einordnung ihres Zeilenendes. */
  private record Zeile(String text, Umbruch umbruch) {}

  // BGBl alt (zweispaltig, bis 2022) und neu (recht.bund.de, ab 2023).
  private static final Pattern KOPFZEILE =
      Pattern.compile(
          "^\\s*(Seite \\d+ von \\d+\\s+)?(\\d{1,5}\\s+)?Bundesgesetzblatt Jahrgang \\d{4}.*$");
  private static final Pattern SEITENZAHL = Pattern.compile("^\\s*\\d{1,5}\\s*$");
  private static final Pattern BUNDESANZEIGER =
      Pattern.compile(
          "^\\s*(Das Bundesgesetzblatt im Internet:|Ein Service des Bundesanzeiger).*$");
  // Referenten-/Regierungsentwürfe: „ - 10 - “ bzw. „ - 5 - Bearbeitungsstand: 05.05.2026 16:18“.
  private static final Pattern SEITENMARKER =
      Pattern.compile("^\\s*[-–]\\s*\\d+\\s*[-–]\\s*(Bearbeitungsstand: .*)?$");
  // Bundesrats-Drucksachen: „- 2 -Drucksache 170/23“, „Drucksache 170/23 - 3 -“ oder die
  // Drucksachennummer allein als Kolumnentitel.
  private static final Pattern BUNDESRAT_KOPF =
      Pattern.compile(
          "^\\s*(?:[-–]\\s*\\d+\\s*[-–]\\s*)?Drucksache \\d+/\\d+(?:\\s*[-–]\\s*\\d+\\s*[-–])?\\s*$");
  // Wasserzeichen der Bundestags-Vorabfassungen: senkrecht gesetzt, extrahiert deshalb mit
  // Zeilenumbrüchen an beliebigen Stellen („V\norabfassung - w\nird durch …“). Das Muster
  // erlaubt Whitespace zwischen allen Zeichen der festen Phrase.
  private static final Pattern VORABFASSUNG =
      Pattern.compile(
          gesperrt("Vorabfassung - wird durch die lektorierte ")
              + "(?:"
              + gesperrt("Fassung")
              + "|"
              + gesperrt("Version")
              + ")"
              + gesperrt(" ersetzt."));

  /** Whitespace einschließlich der Umbruch-Marker des FontgroessenFilters — die kurzen Zeilen
   * des senkrechten Wasserzeichens tragen sie an jedem Zeilenende. */
  private static final String FUELLER = "[\\s\\uE000\\uE001]*";

  /** Regex für eine Phrase, deren Zeichen durch beliebigen Whitespace getrennt sein dürfen. */
  private static String gesperrt(String phrase) {
    var sb = new StringBuilder();
    for (char c : phrase.toCharArray()) {
      if (c == ' ') {
        sb.append(FUELLER).append("[-–]?").append(FUELLER);
      } else if (c == '-') {
        sb.append("[-–]").append(FUELLER);
      } else {
        sb.append(Pattern.quote(String.valueOf(c))).append(FUELLER);
      }
    }
    return sb.toString();
  }

  // Verirrte „Anlage N“-Marke unmittelbar vor einem Seitenkopf (Lesereihenfolge-Artefakt).
  private static final Pattern ANLAGE_MARKE = Pattern.compile("^\\s*Anlage \\d+\\s*$");
  // Bundestags-Drucksachen: „Drucksache 21/6178 – 2 – Deutscher Bundestag – 21. Wahlperiode“
  // bzw. gespiegelt auf geraden Seiten.
  private static final Pattern DRUCKSACHE_KOPF =
      Pattern.compile("^\\s*Drucksache \\d+/\\d+ [–-] \\d+ [–-] Deutscher Bundestag.*$");
  private static final Pattern BUNDESTAG_KOPF =
      Pattern.compile(
          "^\\s*Deutscher Bundestag [–-] \\d+\\. Wahlperiode [–-] \\d+ [–-] Drucksache.*$");

  // Bayerisches Gesetz- und Verordnungsblatt: Kolumnentitel mit Seitenzahl links oder rechts
  // („… Nr. 6/2026 77“) bzw. auf geraden Seiten ohne Zwischenraum an die Jahreszahl geklebt
  // („… Nr. 6/202676“).
  private static final Pattern GVBL_KOPF =
      Pattern.compile(
          "^\\s*(?:\\d{1,4}\\s+)?Bayerisches Gesetz- und Verordnungsblatt Nr\\. \\d+/\\d{4}"
              + "(?:\\s*\\d{1,4})?\\s*$");
  // Bayerischer Landtag: laufender Seitenkopf („Drucksache 19/9707 Bayerischer Landtag
  // 19. Wahlperiode Seite 2“) und Titelköpfe („19. Wahlperiode 28.01.2026  Drucksache 19/9707“,
  // „Bayerischer Landtag“ + „19. Wahlperiode Drucksache 19/9707“).
  private static final Pattern LANDTAG_SEITENKOPF =
      Pattern.compile(
          "^\\s*Drucksache \\d+/\\d+\\s+Bayerischer Landtag\\s+\\d+\\. Wahlperiode"
              + "\\s+Seite \\d+\\s*$");
  private static final Pattern LANDTAG_TITELKOPF =
      Pattern.compile(
          "^\\s*\\d+\\. Wahlperiode\\s+(?:\\d{2}\\.\\d{2}\\.\\d{4}\\s+)?Drucksache \\d+/\\d+\\s*$");
  private static final Pattern LANDTAG_MARKE = Pattern.compile("^\\s*Bayerischer Landtag\\s*$");

  // Fußzeile der revosax-Volltextausgabe (Sachsen), z.B. „https://www.revosax.sachsen.de
  // Fassung vom 13.04.2026 Seite 1 von 1“ — Herausgeber-URL, Fassungsdatum und Seitenangabe.
  private static final Pattern REVOSAX_FUSS =
      Pattern.compile(
          "^\\s*https?://\\S+\\s+Fassung vom \\d{1,2}\\.\\d{1,2}\\.\\d{4}\\s+Seite \\d+ von \\d+\\s*$");

  // Niedersächsisches GVBl: laufende Fußzeile („Nds. GVBl. 2026 Nr. 10 vom 4. Februar 2026  Seite 2“)
  // und Herausgeberzeile. Die Fußzeile steht zwischen zwei Aufzählungsgliedern und hängte sich sonst
  // an den vorangehenden Befehl (Neufassungs-Zitat), sodass dessen Satzende-Anker nicht mehr greift.
  private static final Pattern NDS_GVBL_FUSS =
      Pattern.compile("^\\s*Nds\\. GVBl\\. \\d{4} Nr\\. \\d+ vom .+? Seite \\d+\\s*$");
  private static final Pattern NDS_HERAUSGEBER =
      Pattern.compile("^\\s*Herausgeber: Niedersächsische Staatskanzlei\\s*$");

  /** Konjunktionen, die typischerweise auf einen Suspensivstrich folgen („Wirk- und …“). */
  private static final Pattern KONJUNKTION =
      Pattern.compile("^(und|oder|sowie|bzw\\.|beziehungsweise)\\b.*");

  /** Aufzählungsmarker am Zeilenanfang („3. “, „d) “, „aa) “) — eröffnet eine bewusste
   * Strukturzeile, in die nie hineingejoint werden darf. */
  private static final Pattern AUFZAEHLUNGSMARKER =
      Pattern.compile("(\\d+[a-z]?\\.|[a-z]{1,3}\\))\\s");

  /** Zeilen, die eigenständige Struktur-Anker des Parsers sind („Artikel 2“, „§ 19“, allein
   * stehende Gliederungs-Bezeichnungen) und nie mit Nachbarzeilen zusammengezogen werden dürfen —
   * auch dann nicht, wenn die Geometrie sie für einen Blocksatz-Umbruch hält: gleich breite,
   * zentrierte Überschriften in Serie (etwa die Artikel-Überschriften der Folgeänderungen eines
   * Entwurfs) bilden ein Schein-Ausrichtungs-Cluster. */
  private static final Pattern STRUKTURZEILE =
      Pattern.compile(
          "^(?:(?:Artikel|Teil|Abschnitt|Unterabschnitt|Kapitel|Titel|Buch)\\s+\\d+[a-z]?"
              + "|§\\s*\\d+[a-z]?"
              + "|Art\\.\\s*\\d+[a-z]?"
              + "|(?:Anlage|Anhang)(?:\\s+\\d+[a-z]?)?)$");

  /** Perzentil der Zeilenlängen, das als „volle Spaltenbreite“ gilt (siehe {@link #verbindeUmbrueche}). */
  private static final double VOLLZEILE_PERZENTIL = 0.9;

  /** Mindestanteil der vollen Spaltenbreite, ab dem ein markerloser Umbruch als Silbentrennung
   * statt als bewusster Wortgrenzen-Umbruch gilt. */
  private static final double VOLLZEILE_MINDESTANTEIL = 0.7;

  /** Anzahl Zeilen vor/nach einer Kandidatenzeile, die für die lokale Spaltenbreiten-Schätzung
   * herangezogen werden (siehe {@link #typischeZeilenlaenge}). */
  private static final int VOLLZEILE_FENSTER = 20;

  // BMJV-Entwurfsvorlagen zeichnen das hängende öffnende Anführungszeichen im Content-Stream
  // NACH dem ersten Element der zitierten Passage: „(1) „ Ungeachtet…“ statt „„(1) Ungeachtet…“,
  // „§ 19„“ statt „„§ 19“.
  private static final Pattern INVERTIERTES_ZITAT =
      Pattern.compile("(?m)^(\\s*)\\((\\d+[a-z]?)\\) „\\s*");
  private static final Pattern INVERTIERTES_PARAGRAPH_ZITAT =
      Pattern.compile("(?m)^(\\s*)(§\\s*\\d+[a-z]?)„[ \\t]*");
  // Dieselbe Vertauschung bei Aufzählungslabeln: „3. „ mit Vorteilen …“ statt „„3. mit Vorteilen“.
  // Das Leerzeichen NACH dem „ ist das Artefakt-Signal — echte Binnenzitate („13a. „größere
  // Renovierung““) kleben direkt am Inhalt und bleiben unangetastet.
  private static final Pattern INVERTIERTES_LISTEN_ZITAT =
      Pattern.compile("(?m)^(\\s*)(\\d+[a-z]?\\.|[a-z]{1,3}\\))[ \\t]+„[ \\t]+");

  private TextBereiniger() {}

  public static String bereinige(String rohText) {
    // Geschützte Leerzeichen (GVBl-Satz: „§  1“, „Abs.  2“) sind für Javas \s und
    // String.strip unsichtbar — früh auf gewöhnliche Leerzeichen normalisieren.
    var text = rohText.replace(' ', ' ').replace(' ', ' ');
    text = normalisiereAnfuehrungszeichen(text);
    text = INVERTIERTES_ZITAT.matcher(text).replaceAll("$1„($2) ");
    text = INVERTIERTES_PARAGRAPH_ZITAT.matcher(text).replaceAll("$1„$2");
    text = INVERTIERTES_LISTEN_ZITAT.matcher(text).replaceAll("$1„$2 ");
    text = trenneVerklebteZitatgrenzen(text);
    text = VORABFASSUNG.matcher(text).replaceAll("\n");
    var zeilen = entferneKolumnentitel(zerlegeInZeilen(text));
    var verbunden = verbindeUmbrueche(zeilen);
    // Falsch-positive markerlose Zusammenzüge („durch“ + „die“ → „durchdie“) reparieren — die
    // Befehlsvokabeln sind nie Kompositum-Bestandteile.
    return trenneVerklebteZitatgrenzen(reflowUndStrippe(verbunden));
  }

  /**
   * Zerlegt den Text in Zeilen und streift dabei die Umbruch-Marker des {@link
   * FontgroessenFilter}s in die Klassifikation ab. Verirrte Marker mitten in der Zeile (z.B.
   * Reste der Wasserzeichen-Entfernung) sind bedeutungslos und werden entfernt.
   */
  private static ArrayList<Zeile> zerlegeInZeilen(String text) {
    var roh = text.split("\n", -1);
    var ergebnis = new ArrayList<Zeile>(roh.length);
    for (var zeile : roh) {
      var umbruch = Umbruch.UNBEKANNT;
      if (!zeile.isEmpty()) {
        char letztes = zeile.charAt(zeile.length() - 1);
        if (letztes == HARTES_ZEILENENDE) {
          umbruch = Umbruch.HART;
        } else if (letztes == WEICHES_ZEILENENDE) {
          umbruch = Umbruch.WEICH;
        }
        if (umbruch != Umbruch.UNBEKANNT) {
          zeile = zeile.substring(0, zeile.length() - 1);
        }
      }
      ergebnis.add(new Zeile(ohneMarker(zeile), umbruch));
    }
    return ergebnis;
  }

  private static String ohneMarker(String text) {
    if (text.indexOf(HARTES_ZEILENENDE) < 0 && text.indexOf(WEICHES_ZEILENENDE) < 0) {
      return text;
    }
    return text.replace(String.valueOf(HARTES_ZEILENENDE), "")
        .replace(String.valueOf(WEICHES_ZEILENENDE), "");
  }

  /**
   * PDF-Extraktoren liefern je nach Schriftart unterschiedliche Glyphen für die deutschen
   * Anführungszeichen; der Parser verlässt sich auf {@code „}/{@code “}.
   */
  private static String normalisiereAnfuehrungszeichen(String text) {
    return text
        // Doppelte Low-9- und gerade Anführungszeichen am Wortanfang → „
        .replace('‚', '‘') // ‚ bleibt einfaches öffnendes Zitat
        .replace("‟", "“") // ‟ → “
        .replace("«", "„") // « → „ (selten, aus Fremdsatz)
        .replace("»", "“") // » → “
        // Gerade und englische schließende Anführungszeichen: in BGBl-/Drucksachentexten öffnet
        // stets „, also sind diese Glyphen (fast immer Satz-/OCR-Fehler) schließend zu lesen.
        .replace("”", "“")
        .replace("\"", "“");
  }

  /** Verklebte Zitatgrenzen wieder trennen („§ 9“ersetzt → „§ 9“ ersetzt) — erst nach den
   * Invertiertes-Zitat-Fixes, die auf die verklebte Form angewiesen sind. */
  private static String trenneVerklebteZitatgrenzen(String text) {
    return text
        .replaceAll("“(\\p{L})", "“ $1")
        .replaceAll("(\\p{L})„", "$1 „")
        // Verklebte Befehlsvokabeln (Zusammenzug über Zeilengrenzen ohne Leerzeichen).
        .replace("durchdie ", "durch die ")
        .replace("undwerden ", "und werden ")
        .replace("undwird ", "und wird ")
        .replace("Kommaeingefügt", "Komma eingefügt")
        .replace("Kommaersetzt", "Komma ersetzt")
        // Kontextrahmen, an den der folgende Unterpunkt geklebt wurde („geändertaa) In …“).
        .replaceAll("(wie folgt geändert:?)(?=[a-z]{1,3}\\)|\\d+[a-z]?\\.)", "$1\n");
  }

  /** Entfernt Seitenkopf-/Fußzeilen. Trailing-Whitespace der übrigen Zeilen bleibt erhalten! */
  private static ArrayList<Zeile> entferneKolumnentitel(List<Zeile> roh) {
    var kolumnentitel = new boolean[roh.size()];
    for (int i = 0; i < roh.size(); i++) {
      kolumnentitel[i] = istKolumnentitel(roh.get(i).text());
    }
    var ergebnis = new ArrayList<Zeile>();
    for (int i = 0; i < roh.size(); i++) {
      if (kolumnentitel[i]) {
        continue;
      }
      // Eine verirrte „Anlage N“-Marke direkt vor einem Seitenkopf gehört zum Seitenmöbel.
      if (ANLAGE_MARKE.matcher(roh.get(i).text()).matches()) {
        int j = i + 1;
        while (j < roh.size() && roh.get(j).text().isBlank()) {
          j++;
        }
        if (j < roh.size() && kolumnentitel[j]) {
          continue;
        }
      }
      ergebnis.add(roh.get(i));
    }
    return ergebnis;
  }

  private static boolean istKolumnentitel(String zeile) {
    return KOPFZEILE.matcher(zeile).matches()
        || SEITENZAHL.matcher(zeile).matches()
        || BUNDESANZEIGER.matcher(zeile).matches()
        || SEITENMARKER.matcher(zeile).matches()
        || DRUCKSACHE_KOPF.matcher(zeile).matches()
        || BUNDESTAG_KOPF.matcher(zeile).matches()
        || BUNDESRAT_KOPF.matcher(zeile).matches()
        || GVBL_KOPF.matcher(zeile).matches()
        || LANDTAG_SEITENKOPF.matcher(zeile).matches()
        || LANDTAG_TITELKOPF.matcher(zeile).matches()
        || LANDTAG_MARKE.matcher(zeile).matches()
        || REVOSAX_FUSS.matcher(zeile).matches()
        || NDS_GVBL_FUSS.matcher(zeile).matches()
        || NDS_HERAUSGEBER.matcher(zeile).matches();
  }

  /**
   * Zieht am Zeilenende umbrochene Wörter zusammen. Zwei Formen:
   *
   * <ul>
   *   <li><b>Mit Trennstrich</b> („Bundes-“ + „regierung“): Bei kleingeschriebener Folgezeile wird
   *       der Strich entfernt — außer vor Konjunktionen („Ausgangs- und Hilfsstoffe“). Bei
   *       Großbuchstabe/Ziffer ist es ein umbrochenes Kompositum, der Bindestrich bleibt
   *       („Coronavirus-“ + „Krankheit-2019“).
   *   <li><b>Markerlos</b> (Bundestags-Drucksachen: „Schwel“ + „lenwertes“): Reguläre Umbrüche
   *       enden dort mit Leerzeichen vor dem Zeilenumbruch; endet eine Zeile direkt mit einem
   *       Buchstaben und beginnt die Folgezeile klein, ist es eine Trennung → ohne Leerzeichen
   *       zusammenziehen. Das trifft aber nur zu, wenn die Zeile den rechten Rand tatsächlich
   *       erreicht — sonst wäre der Umbruch dort nicht nötig gewesen. Bewusst abgebrochene
   *       Zeilen (z.B. ein Stichwort vor einer hängend eingerückten Definition:
   *       „…Nachhaltigkeitssiegels“ + „das Anbringen …“) werden deshalb ausgenommen — sie sind
   *       ein Wortgrenzen-Umbruch, keine Silbentrennung, auch wenn das Trailing-Space-Signal
   *       fehlt. Maßgeblich ist die geometrische Klassifikation des FontgroessenFilters; nur wo
   *       sie fehlt, springt die Zeichenzahl-Näherung ({@link #typischeZeilenlaenge}) ein.
   * </ul>
   *
   * <p>Beginnt die Folgezeile mit einem Aufzählungsmarker („d)“, „3.“), unterbleibt jeder
   * Zusammenzug — ein Marker eröffnet eine bewusste Strukturzeile, auch wenn er klein
   * geschrieben ist („…vorgesehen und“ + „d) die Überwachung …“).
   */
  private static ArrayList<Zeile> verbindeUmbrueche(List<Zeile> zeilen) {
    // Markerlose Trennungen sind nur erkennbar, wenn die Quelle die Trailing-Space-Konvention
    // verwendet (PDF-Extraktion). Handgeschriebene Klartextdateien haben keine Trailing-Spaces —
    // dort würde die Heuristik reguläre Umbrüche verschmelzen, also bleibt sie aus.
    var markerlosAktiv = verwendetTrailingSpaces(zeilen);

    var ergebnis = new ArrayList<Zeile>();
    for (int i = 0; i < zeilen.size(); i++) {
      var zeile = zeilen.get(i).text();
      var umbruch = zeilen.get(i).umbruch();
      while (true) {
        var gestutzt = zeile.stripTrailing();
        // Ein geometrisch hartes Zeilenende ist ein bewusster Umbruch — nie zusammenziehen.
        var mitTrennstrich = umbruch != Umbruch.HART && endetMitSilbentrennung(gestutzt);
        var markerlos =
            markerlosAktiv
                && endetMarkerlos(zeile)
                && switch (umbruch) {
                  case HART -> false;
                  case WEICH -> true;
                  case UNBEKANNT ->
                      gestutzt.length()
                          >= typischeZeilenlaenge(zeilen, i) * VOLLZEILE_MINDESTANTEIL;
                };
        if (!mitTrennstrich && !markerlos) {
          break;
        }
        // Leerzeilen (z.B. an Spalten-/Seitenumbrüchen) überspringen.
        int j = i + 1;
        while (j < zeilen.size() && zeilen.get(j).text().isBlank()) {
          j++;
        }
        if (j >= zeilen.size()) {
          break;
        }
        var naechste = zeilen.get(j).text().stripLeading();
        if (AUFZAEHLUNGSMARKER.matcher(naechste).lookingAt()) {
          break;
        }
        int erstesZeichen = naechste.codePointAt(0);
        if (mitTrennstrich) {
          if (Character.isLowerCase(erstesZeichen) && !KONJUNKTION.matcher(naechste).matches()) {
            zeile = gestutzt.substring(0, gestutzt.length() - 1) + naechste;
          } else if (Character.isUpperCase(erstesZeichen) || Character.isDigit(erstesZeichen)) {
            zeile = gestutzt + naechste;
          } else {
            break;
          }
        } else {
          if (Character.isLowerCase(erstesZeichen) && !KONJUNKTION.matcher(naechste).matches()) {
            zeile = zeile + naechste;
          } else {
            break;
          }
        }
        umbruch = zeilen.get(j).umbruch();
        i = j;
      }
      ergebnis.add(new Zeile(zeile, umbruch));
    }
    return ergebnis;
  }

  private static boolean endetMitSilbentrennung(String gestutzteZeile) {
    if (!gestutzteZeile.endsWith("-") || gestutzteZeile.length() < 2) {
      return false;
    }
    // Vor dem Bindestrich muss ein Buchstabe stehen („und -gestaltung“ nicht zusammenziehen).
    return Character.isLetter(gestutzteZeile.charAt(gestutzteZeile.length() - 2));
  }

  /** Zeile endet ohne Trailing-Whitespace direkt mit einem Buchstaben. */
  private static boolean endetMarkerlos(String zeile) {
    if (zeile.isEmpty()) {
      return false;
    }
    return Character.isLetter(zeile.charAt(zeile.length() - 1));
  }

  /** Endet ein nennenswerter Teil der nichtleeren Zeilen mit Whitespace? */
  private static boolean verwendetTrailingSpaces(List<Zeile> zeilen) {
    int nichtLeer = 0;
    int mitTrailingSpace = 0;
    for (var eintrag : zeilen) {
      var zeile = eintrag.text();
      if (zeile.isBlank()) {
        continue;
      }
      nichtLeer++;
      if (Character.isWhitespace(zeile.charAt(zeile.length() - 1))) {
        mitTrailingSpace++;
      }
    }
    return mitTrailingSpace > 0 && mitTrailingSpace * 4 >= nichtLeer;
  }

  /**
   * Typische „volle“ Zeilenlänge im Umfeld von {@code zentrum} ({@link #VOLLZEILE_PERZENTIL}-
   * Perzentil der gestutzten Längen nichtleerer Zeilen in einem Fenster von {@link
   * #VOLLZEILE_FENSTER} Zeilen davor/danach) — ein grober Näherungswert für die lokale
   * Spaltenbreite, ohne auf PDF-Positionsdaten zugreifen zu müssen. Lokal statt dokumentweit, weil
   * ein einziges Dokument Abschnitte mit unterschiedlicher Spaltenbreite mischen kann (z.B.
   * schmalerer Regelungstext vs. breitere Begründung in Regierungsentwürfen) — eine dokumentweite
   * Kennzahl würde dort die kürzere Spalte systematisch benachteiligen. Vereinzelte überlange
   * Zeilen (z.B. selbst fälschlich verklebte Umbrüche) dürfen den Wert nicht verzerren, daher ein
   * hohes Perzentil statt des reinen Maximums.
   */
  private static int typischeZeilenlaenge(List<Zeile> zeilen, int zentrum) {
    var laengen = new ArrayList<Integer>();
    int von = Math.max(0, zentrum - VOLLZEILE_FENSTER);
    int bis = Math.min(zeilen.size(), zentrum + VOLLZEILE_FENSTER + 1);
    for (int i = von; i < bis; i++) {
      var zeile = zeilen.get(i).text();
      if (zeile.isBlank()) {
        continue;
      }
      laengen.add(zeile.stripTrailing().length());
    }
    if (laengen.isEmpty()) {
      return 0;
    }
    laengen.sort(null);
    int index = (int) (laengen.size() * VOLLZEILE_PERZENTIL);
    index = Math.min(index, laengen.size() - 1);
    return laengen.get(index);
  }

  /**
   * Zieht geometrisch weiche Umbrüche (Blocksatz-Zeilenfall) mit einem Leerzeichen zu Fließtext
   * zusammen und stutzt die Zeilenenden. Harte und unklassifizierte Umbrüche bleiben erhalten —
   * nach diesem Schritt ist jeder verbleibende Zeilenumbruch nach bester Einschätzung
   * beabsichtigt. Leerzeilen unmittelbar nach einem weichen Umbruch sind Spalten-/Seitenwechsel
   * mitten im Absatz und entfallen.
   *
   * <p>Beginnt die Folgezeile mit einem Aufzählungsmarker, bleibt der Umbruch auch nach einer
   * weichen Zeile stehen: Der Zeilenfall kann zufällig genau vor einem Aufzählungspunkt am Rand
   * enden, und ein in die Zeile gezogener Marker wäre für die nachgelagerte Strukturerkennung
   * unsichtbar.
   */
  private static String reflowUndStrippe(List<Zeile> zeilen) {
    var sb = new StringBuilder();
    boolean erste = true;
    boolean vorherWeich = false;
    for (var zeile : zeilen) {
      var text = zeile.text().stripTrailing();
      if (vorherWeich && text.isBlank()) {
        continue;
      }
      var gestrippt = text.stripLeading();
      if (erste) {
        sb.append(text);
        erste = false;
      } else if (vorherWeich
          && !AUFZAEHLUNGSMARKER.matcher(gestrippt).lookingAt()
          && !STRUKTURZEILE.matcher(gestrippt).matches()) {
        sb.append(' ').append(gestrippt);
      } else {
        sb.append('\n').append(text);
      }
      vorherWeich =
          zeile.umbruch() == Umbruch.WEICH
              && !text.isBlank()
              && !STRUKTURZEILE.matcher(gestrippt).matches();
    }
    return sb.toString();
  }
}
