package eu.mulk.aendggner.aenderung.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    var ausgabe = new StringBuilder();
    var zitate = new ArrayList<String>();
    var warnungen = new ArrayList<String>();
    var aktuellesZitat = new StringBuilder();
    int tiefe = 0;

    for (int i = 0; i < text.length(); i++) {
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
        aktuellesZitat.append(c);
      } else {
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
   * Wahr, wenn das öffnende Anführungszeichen an Position {@code i} ein Fortführungszeichen ist:
   * Es steht am Zeilenanfang innerhalb eines offenen Zitats und ihm folgt unmittelbar ein
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

  private static String kontextAuszug(String text, int position) {
    var von = Math.max(0, position - 60);
    var bis = Math.min(text.length(), position + 20);
    return text.substring(von, bis).replaceAll("\\s+", " ");
  }

  private ZitatExtraktor() {}
}
