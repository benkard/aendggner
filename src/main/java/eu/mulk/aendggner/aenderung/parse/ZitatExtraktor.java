// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Ersetzt in deutschem Gesetzestext alle auf oberster Ebene mit „…“ zitierten Passagen durch
 * indizierte Platzhalter («0», «1», …).
 *
 * <p>Das ist der zentrale Trick des Befehlsparsers: Zitierter Inhalt (der seitenlang sein und
 * eigene Gliederungsmarker, Sätze und geschachtelte Zitate enthalten kann) kann die Befehls-Regexes
 * nicht verwirren. Geschachtelte „…“ werden per Tiefenzählung dem äußersten Zitat zugeschlagen;
 * einfache Zitate ‚…‘ bleiben unangetastet.
 *
 * <p>Unbalancierte Anführungszeichen kommen in echten BGBl-Texten vor (Satzfehler oder
 * Extraktionsartefakte). Der Extraktor bricht deshalb nicht ab: Ein schließendes Anführungszeichen
 * ohne offenes Zitat wird als Literal übernommen, am Textende offene Zitate werden dort geschlossen
 * — beides wird als Warnung gemeldet und darf nicht stillschweigend untergehen.
 *
 * <p>Damit ein solcher Satzfehler nicht den halben Text verschlingt, gibt es zwei Strukturgrenzen,
 * an denen ein offenes Zitat endet: die Artikel-Überschrift (immer) und der nächste
 * Aufzählungspunkt des Änderungsgesetzes (nur in Abschnitten, deren Anführungszeichen nachweislich
 * nicht aufgehen — siehe {@link #aufzaehlungsGrenze}).
 */
public final class ZitatExtraktor {

  static final char OEFFNEND = '„'; // „
  static final char SCHLIESSEND = '“'; // “
  private static final Pattern PLATZHALTER = Pattern.compile("«(\\d+)»");

  /**
   * @param text der Text mit Platzhaltern.
   * @param zitate die extrahierten Zitatinhalte (ohne die äußeren Anführungszeichen), indiziert
   *     durch die Platzhalternummern.
   * @param warnungen Auffälligkeiten (unbalancierte Anführungszeichen), die der Nutzer prüfen
   *     sollte.
   */
  public record Ergebnis(String text, List<String> zitate, List<String> warnungen) {

    /** Liefert den Zitatinhalt zum Platzhalter «index». */
    public String zitat(int index) {
      return zitate.get(index);
    }

    /** Setzt in {@code textMitPlatzhaltern} alle Platzhalter wieder in Zitatform ein. */
    public String stelleZitateWiederHer(String textMitPlatzhaltern) {
      var matcher = PLATZHALTER.matcher(textMitPlatzhaltern);
      var sb = new StringBuilder();
      while (matcher.find()) {
        var zitatText = zitate.get(Integer.parseInt(matcher.group(1)));
        matcher.appendReplacement(sb, Matcher.quoteReplacement(OEFFNEND + zitatText + SCHLIESSEND));
      }
      matcher.appendTail(sb);
      return sb.toString();
    }
  }

  public static Ergebnis extrahiere(String text) {
    var segmente = artikelSegmente(text);
    var ausgabe = new StringBuilder();
    var zitate = new ArrayList<String>();
    var warnungen = new ArrayList<String>();
    var aktuellesZitat = new StringBuilder();
    int tiefe = 0;
    int segment = 0;
    // Letzter Aufzählungsmarker, der außerhalb eines Zitats am Zeilenanfang stand, und sein Stand
    // beim Aufgehen des laufenden Zitats — die Bezugsgröße der Aufzählungs-Grenze.
    var letzterMarker = marker(text, 0);
    String markerVorZitat = null;

    for (int i = 0; i < text.length(); i++) {
      while (segment + 1 < segmente.size() && i >= segmente.get(segment + 1).von()) {
        segment++;
      }
      char c = text.charAt(i);
      if (c == OEFFNEND) {
        if (tiefe > 0 && istFortfuehrungszeichen(text, i)) {
          // GVBl-Zitierweise: Jedes neugefasste Aufzählungsglied öffnet am Zeilenanfang erneut
          // mit „, geschlossen wird nur einmal am Blockende. Das Fortführungszeichen ist
          // Typografie, kein geschachteltes Zitat — es würde die Tiefenzählung entgleisen lassen.
          continue;
        }
        if (tiefe == 0) {
          aktuellesZitat.setLength(0);
          markerVorZitat = letzterMarker;
        } else {
          aktuellesZitat.append(c);
        }
        tiefe++;
      } else if (c == SCHLIESSEND) {
        if (tiefe == 0) {
          warnungen.add(
              "Schließendes Anführungszeichen ohne öffnendes: …"
                  + kontextAuszug(text, i)
                  + "… — als Literal übernommen.");
          ausgabe.append(c);
          continue;
        }
        tiefe--;
        if (tiefe == 0) {
          ausgabe.append('«').append(zitate.size()).append('»');
          zitate.add(aktuellesZitat.toString());
        } else {
          aktuellesZitat.append(c);
        }
      } else if (tiefe > 0) {
        var grenzMarker =
            c == '\n'
                ? aufzaehlungsGrenze(text, i + 1, markerVorZitat, segmente.get(segment))
                : null;
        if (c == '\n' && beginntArtikelUeberschrift(text, i + 1)) {
          // Ein Änderungsgesetz zitiert nie über eine Artikel-Überschrift hinweg: Hier fehlt im
          // amtlichen Satz ein schließendes Anführungszeichen (kommt vor, z.B. GV. NRW. 2026
          // S. 202 Artikel 2 Nr. 11). Ohne diese Grenze verschlänge das offene Zitat alle
          // folgenden Artikel und der Parser fände überhaupt keine Änderungsbefehle mehr.
          warnungen.add(
              "Zitat vor einer Artikel-Überschrift nicht geschlossen: …"
                  + kontextAuszug(text, i)
                  + "… — dort geschlossen.");
          ausgabe.append('«').append(zitate.size()).append('»');
          zitate.add(aktuellesZitat.toString());
          aktuellesZitat.setLength(0);
          tiefe = 0;
          ausgabe.append(c);
        } else if (grenzMarker != null) {
          warnungen.add(
              "Zitat vor dem Aufzählungspunkt „"
                  + grenzMarker
                  + "“ nicht geschlossen: …"
                  + kontextAuszug(text, i)
                  + "… — dort geschlossen.");
          ausgabe.append('«').append(zitate.size()).append('»');
          zitate.add(aktuellesZitat.toString());
          aktuellesZitat.setLength(0);
          tiefe = 0;
          ausgabe.append(c);
          letzterMarker = grenzMarker;
        } else {
          aktuellesZitat.append(c);
        }
      } else {
        if (c == '\n') {
          var neuerMarker = marker(text, i + 1);
          if (neuerMarker != null) {
            letzterMarker = neuerMarker;
          }
        }
        ausgabe.append(c);
      }
    }

    if (tiefe > 0) {
      warnungen.add(
          "Am Textende sind noch "
              + tiefe
              + " Zitat(e) offen: …"
              + kontextAuszug(text, text.length() - 1)
              + " — am Textende geschlossen.");
      ausgabe.append('«').append(zitate.size()).append('»');
      zitate.add(aktuellesZitat.toString());
    }
    return new Ergebnis(ausgabe.toString(), zitate, warnungen);
  }

  // Aufzählungsmarker unmittelbar nach dem öffnenden Zeichen („2.“, „b)“, „aa)“).
  private static final Pattern FORTFUEHRUNGS_MARKER =
      Pattern.compile("^(?:\\d+[a-z]?\\.|[a-z]{1,3}\\))[ \\t]");

  /**
   * Wahr, wenn das öffnende Anführungszeichen an Position {@code i} ein Fortführungszeichen ist: Es
   * steht am Zeilenanfang innerhalb eines offenen Zitats und ihm folgt unmittelbar ein
   * Aufzählungsmarker.
   */
  private static boolean istFortfuehrungszeichen(String text, int i) {
    int z = i;
    while (z > 0 && (text.charAt(z - 1) == ' ' || text.charAt(z - 1) == '\t')) {
      z--;
    }
    if (z != 0 && text.charAt(z - 1) != '\n') {
      return false;
    }
    var fenster = text.substring(i + 1, Math.min(text.length(), i + 10));
    return FORTFUEHRUNGS_MARKER.matcher(fenster).find();
  }

  /**
   * Strukturgrenze eines Änderungsgesetzes: eine Zeile, die nur aus „Artikel N“ besteht. Bayerische
   * Stammgesetze zitieren ihre Normköpfe als „Art. N“, nicht als „Artikel N“ — zitierter Inhalt
   * läuft hier also nicht versehentlich auf.
   */
  private static final Pattern ARTIKEL_GRENZE =
      Pattern.compile("^Artikel[ \\t]+\\d+[a-z]?[ \\t]*$");

  /** Wahr, wenn die bei {@code von} beginnende Zeile eine Artikel-Überschrift ist. */
  private static boolean beginntArtikelUeberschrift(String text, int von) {
    if (von >= text.length()) {
      return false;
    }
    return ARTIKEL_GRENZE.matcher(zeileAb(text, von)).matches();
  }

  // --- Grenze am Aufzählungspunkt ------------------------------------------------------------

  /** Ein Artikel-Abschnitt des Änderungsgesetzes samt Befund, ob darin ein Zitat offen bleibt. */
  private record Segment(int von, int bis, boolean defekt) {}

  /** Zeile am Zeilenanfang: Aufzählungsmarker („1.“, „2a.“, „b)“, „aa)“) und Zeilenrest. */
  private static final Pattern AUFZAEHLUNGSZEILE =
      Pattern.compile("^[ \\t]*(\\d+[a-z]?\\.|[a-z]{1,3}\\))[ \\t]+(.*)$");

  /**
   * Befehlssprache am Zeilenende — die Verbformen des Handbuchs der Rechtsförmlichkeit samt der
   * Umnummerierungsform („werden die Absätze 6 bis 10.“). Zitierter Gesetzestext endet so gut wie
   * nie auf eine dieser Wendungen; eine zitierte *Änderungs*vorschrift dagegen sehr wohl, weshalb
   * dieses Merkmal allein nicht ausreicht (siehe {@link #aufzaehlungsGrenze}).
   */
  private static final Pattern BEFEHLSSPRACHE =
      Pattern.compile(
          "(?:(?:wird|werden) wie folgt (?:geändert|gefasst|neu gefasst)"
              + "|(?:erhält|erhalten) folgende Fassung"
              + "|(?:wird|werden)(?: \\S+){0,12} "
              + "(?:eingefügt|angefügt|ersetzt|vorangestellt|aufgehoben|gestrichen)"
              + "|(?:wird|werden) (?:zu )?(?:die |der |das |den )?"
              + "(?:§§?|Artt?\\.|Absatz|Absätze|Absätzen|Nummer|Nummern|Satz|Sätze|Sätzen"
              + "|Buchstabe|Buchstaben) \\d+[a-z]?(?: bis \\d+[a-z]?)?"
              + ")[ \\t]*[:.]?[ \\t]*$");

  /**
   * Prüft, ob das offene Zitat vor der bei {@code von} beginnenden Zeile zu schließen ist, weil
   * dort die Aufzählung des Änderungsgesetzes weitergeht, und liefert dann deren Marker.
   *
   * <p>Ein Zitat darf hier nur unter allen vier Bedingungen zusammen enden, denn zitierter
   * Gesetzestext enthält selbst Aufzählungen — und eine zitierte Änderungsvorschrift (bayerische
   * GVBl-Hefte, Meta-Änderungen) sogar Befehlssprache:
   *
   * <ol>
   *   <li>Im Artikel-Abschnitt bleibt am Ende nachweislich ein Zitat offen, der Satz ist dort also
   *       defekt. In fehlerfreien Abschnitten wird nie geraten.
   *   <li>Der Marker der Folgezeile liegt auf derselben oder einer flacheren Ebene als der Marker,
   *       auf dessen Punkt das Zitat aufging („bb)“ → „cc)“, „c)“, „3.“).
   *   <li>Der Zeilenrest trägt Befehlssprache.
   *   <li>Ein Schluss an dieser Stelle lässt den Rest des Abschnitts ausbalanciert zurück — die
   *       Stelle behebt den Defekt also wirklich.
   * </ol>
   */
  private static @Nullable String aufzaehlungsGrenze(
      String text, int von, @Nullable String markerVorZitat, Segment segment) {
    if (markerVorZitat == null || !segment.defekt() || von >= text.length()) {
      return null;
    }
    var zeile = AUFZAEHLUNGSZEILE.matcher(zeileAb(text, von));
    if (!zeile.matches()) {
      return null;
    }
    var marker = zeile.group(1);
    if (markerEbene(marker) > markerEbene(markerVorZitat)
        || !BEFEHLSSPRACHE.matcher(zeile.group(2)).find()
        || offeneZitate(text, von, segment.bis()) != 0) {
      return null;
    }
    return marker;
  }

  /** Gliederungsebene eines Aufzählungsmarkers: „1.“ = 1, „b)“ = 2, „aa)“ = 3, „aaa)“ = 4. */
  private static int markerEbene(String marker) {
    return marker.endsWith(")") ? marker.length() : 1;
  }

  /** Der Aufzählungsmarker der bei {@code von} beginnenden Zeile, sonst {@code null}. */
  private static @Nullable String marker(String text, int von) {
    if (von >= text.length()) {
      return null;
    }
    var zeile = AUFZAEHLUNGSZEILE.matcher(zeileAb(text, von));
    return zeile.matches() ? zeile.group(1) : null;
  }

  /** Zerlegt den Text an den Artikel-Überschriften und hält je Abschnitt fest, ob er defekt ist. */
  private static List<Segment> artikelSegmente(String text) {
    var anfaenge = new ArrayList<Integer>();
    anfaenge.add(0);
    for (int i = 0; i < text.length(); ) {
      int ende = text.indexOf('\n', i);
      if (i > 0 && ARTIKEL_GRENZE.matcher(zeileAb(text, i)).matches()) {
        anfaenge.add(i);
      }
      if (ende < 0) {
        break;
      }
      i = ende + 1;
    }
    var segmente = new ArrayList<Segment>(anfaenge.size());
    for (int k = 0; k < anfaenge.size(); k++) {
      int von = anfaenge.get(k);
      int bis = k + 1 < anfaenge.size() ? anfaenge.get(k + 1) : text.length();
      segmente.add(new Segment(von, bis, offeneZitate(text, von, bis) > 0));
    }
    return segmente;
  }

  /**
   * Zahl der am Ende von {@code [von, bis)} noch offenen Zitate — dieselbe Tiefenzählung wie {@link
   * #extrahiere} (samt Fortführungszeichen), aber ohne Ausgabe. Ein schließendes Anführungszeichen
   * ohne offenes Zitat gilt wie dort als Literal.
   */
  private static int offeneZitate(String text, int von, int bis) {
    int tiefe = 0;
    for (int i = von; i < bis; i++) {
      char c = text.charAt(i);
      if (c == OEFFNEND) {
        if (tiefe > 0 && istFortfuehrungszeichen(text, i)) {
          continue;
        }
        tiefe++;
      } else if (c == SCHLIESSEND && tiefe > 0) {
        tiefe--;
      }
    }
    return tiefe;
  }

  /** Die bei {@code von} beginnende Zeile (ohne Zeilenumbruch). */
  private static String zeileAb(String text, int von) {
    int ende = text.indexOf('\n', von);
    return text.substring(von, ende < 0 ? text.length() : ende);
  }

  private static String kontextAuszug(String text, int position) {
    var von = Math.max(0, position - 60);
    var bis = Math.min(text.length(), position + 20);
    return text.substring(von, bis).replaceAll("\\s+", " ");
  }

  private ZitatExtraktor() {}
}
