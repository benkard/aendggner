package eu.mulk.aendggner.aenderung.parse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Gewinnt aus der Zusammenstellung einer Beschlussempfehlung die vom Ausschuss beschlossene
 * Fassung.
 *
 * <p>Die Zusammenstellung steht zweispaltig: links der Entwurf, rechts die Beschlüsse des
 * Ausschusses. Die rechte Spalte schreibt aber nur aus, was der Ausschuss <em>geändert</em> hat;
 * überall sonst steht die gesperrte Marke „u n v e r ä n d e r t“ — und zwar nicht nur je
 * Gliederungspunkt, sondern auch zeilenweise innerhalb zitierter Blöcke:
 *
 * <pre>
 * links                                  rechts
 * 13. Die Angaben zu den §§ 34 bis 45 …   13. Die §§ 34 bis 45 werden wie folgt gefasst:
 * „§ 34 (weggefallen)                     „§ 34 u n v e r ä n d e r t
 * § 35 (weggefallen)                      § 35 u n v e r ä n d e r t
 * § 45 (weggefallen)“.                    § 45 u n v e r ä n d e r t
 * </pre>
 *
 * <p>Für sich gelesen ist die rechte Spalte deshalb unbrauchbar: Ihre Anführungszeichen gehen nicht
 * auf. Maßgeblich ist die <em>Grundlinie</em>: Beide Spalten sind zeilensynchron gesetzt, jeder
 * Vermerk steht auf der Höhe der Entwurfszeile, die er meint. Über Seite und Grundlinie (aus {@link
 * FontgroessenFilter.Zeile}) lässt sich die Fassung deshalb Zeile für Zeile zusammensetzen: Wo die
 * rechte Spalte „unverändert“ vermerkt, gilt die linke, sonst die rechte.
 *
 * <p>Ein früherer Versuch, das über die Gliederungspfade statt über die Geometrie zu lösen, ist
 * gescheitert (45 statt 117 Befehlen), weil er die zeilenweisen Vermerke innerhalb der Zitate nicht
 * auflösen kann.
 */
final class ZusammenstellungsLeser {

  private static final Logger log = Logger.getLogger(ZusammenstellungsLeser.class);

  /**
   * Die Umbruch-Klassifikation, die {@link FontgroessenFilter#markiereZeilenenden} ans Zeilenende
   * hängt. Sie gehört in die ausgegebene Fassung, aber in keine Textprüfung.
   */
  private static final Pattern ZEILENENDE =
      Pattern.compile(
          "[" + TextBereiniger.HARTES_ZEILENENDE + TextBereiniger.WEICHES_ZEILENENDE + "]");

  /** Der Zeileninhalt ohne die Umbruch-Klassifikation. */
  private static String nurText(String zeile) {
    return ZEILENENDE.matcher(zeile).replaceAll("");
  }

  /**
   * Die Zusammenstellung beginnt unter dieser Überschrift. Davor stehen Beschlussformel und
   * Ausschussbesetzung, danach der Bericht der Berichterstatter — beides Fließtext ohne Befehle,
   * beides einspaltig und deshalb ohnehin nicht auflösbar.
   */
  private static final Pattern BEGINN = Pattern.compile("\\s*Zusammenstellung\\s*");

  private static final Pattern ENDE = Pattern.compile("\\s*Bericht (?:des|der) Abgeordneten.*");

  /**
   * Die Marke eines Gliederungspunkts, wie sie beide Spalten an den Zeilenanfang setzen: „13.“,
   * „b)“, „aa)“, „(2)“, „§ 34“, „Artikel 2“, „Teil 3“ — in Zitaten samt öffnendem
   * Anführungszeichen.
   */
  private static final String MARKE =
      "„?(?:\\d+[a-z]?\\.|[a-z]{1,3}\\)|\\(\\d+[a-z]?\\)|§+\\s*\\d+[a-z]?"
          + "|Artikel\\s+\\d+[a-z]?|Teil\\s+\\d+)";

  /**
   * Der Vermerk, dass der Ausschuss der Entwurfsfassung folgt — für sich allein oder hinter der
   * Marke des gemeinten Punktes.
   */
  private static final Pattern UNVERAENDERT =
      Pattern.compile("\\s*(" + MARKE + "\\s+)?unverändert[\\s.]*");

  /**
   * Der Vermerk, dass der Ausschuss den Punkt streicht. Er hat kein Gegenstück in der beschlossenen
   * Fassung: Sie schweigt an dieser Stelle, und der Entwurfstext darunter entfällt mit ihm.
   */
  private static final Pattern ENTFAELLT =
      Pattern.compile("\\s*(?:" + MARKE + "\\s+)?entfällt[\\s.]*");

  /** Die führende Marke einer Zeile, um sie gegen die der anderen Spalte auszutauschen. */
  private static final Pattern FUEHRENDE_MARKE = Pattern.compile("^(\\s*)" + MARKE + "\\s+");

  /** Höhenunterschied (pt), bis zu dem zwei Zeilen als auf derselben Grundlinie gelten. */
  private static final float GRUNDLINIEN_TOLERANZ_PT = 1f;

  private ZusammenstellungsLeser() {}

  /**
   * @param text die beschlossene Fassung als linearer Text, oder {@code null}, wenn sie sich nicht
   *     gewinnen ließ — dann nennt {@code warnungen} den Grund.
   */
  record Ergebnis(@Nullable String text, List<String> warnungen) {}

  static Ergebnis lies(PDDocument dokument, SuperskriptModus superskriptModus) throws IOException {
    // Jede Spalte für sich klassifiziert: Ihre Satzspiegelränder liegen verschieden, in der
    // gemischten Fassung fände keine ihren eigenen wieder.
    var links =
        FontgroessenFilter.markiereZeilenenden(
            FontgroessenFilter.extrahiereZeilen(
                dokument, superskriptModus, FontgroessenFilter.Spalte.LINKS));
    var rechts =
        FontgroessenFilter.markiereZeilenenden(
            FontgroessenFilter.extrahiereZeilen(
                dokument, superskriptModus, FontgroessenFilter.Spalte.RECHTS));
    var warnungen = new ArrayList<String>();

    var hoehen = fasseNachGrundlinieZusammen(links, rechts);
    int beginn = findeZeile(hoehen, BEGINN);
    if (beginn < 0) {
      warnungen.add("Die Beschlussempfehlung enthält keine Zusammenstellung.");
      return new Ergebnis(null, warnungen);
    }
    int ende = findeZeile(hoehen, ENDE);
    if (ende < 0) {
      ende = hoehen.size();
    }

    var fassung = loeseAuf(hoehen.subList(beginn, ende), warnungen);
    return new Ergebnis(String.join("\n", fassung), warnungen);
  }

  /** Eine Höhe im Satzbild mit dem, was auf ihr in den beiden Spalten steht. */
  private record Hoehe(
      int seite,
      float grundlinie,
      FontgroessenFilter.@Nullable Zeile links,
      FontgroessenFilter.@Nullable Zeile rechts) {}

  /**
   * Führt beide Spalten über Seite und Grundlinie in eine gemeinsame Lesereihenfolge zusammen.
   * Seitenmöbel fällt dabei heraus: Der laufende Spaltenkopf („Entwurf“ / „Beschlüsse des 25.
   * Ausschusses“) stünde sonst mitten in einem unveränderten Block und täuschte {@link #loeseAuf}
   * einen Wechsel der maßgeblichen Spalte vor.
   */
  private static List<Hoehe> fasseNachGrundlinieZusammen(
      List<FontgroessenFilter.Zeile> links, List<FontgroessenFilter.Zeile> rechts) {
    record Kandidat(FontgroessenFilter.Zeile zeile, boolean istLinks) {}
    var kandidaten = new ArrayList<Kandidat>(links.size() + rechts.size());
    for (var zeile : links) {
      if (traegtInhalt(zeile)) {
        kandidaten.add(new Kandidat(zeile, true));
      }
    }
    for (var zeile : rechts) {
      if (traegtInhalt(zeile)) {
        kandidaten.add(new Kandidat(zeile, false));
      }
    }
    kandidaten.sort(
        Comparator.<Kandidat>comparingInt(k -> k.zeile().seite())
            .thenComparing(k -> k.zeile().grundlinie())
            // Die linke Spalte zuerst, damit eine Höhe ihre beiden Seiten in fester Ordnung erhält.
            .thenComparing(k -> !k.istLinks()));

    var hoehen = new ArrayList<Hoehe>();
    for (var kandidat : kandidaten) {
      var zeile = kandidat.zeile();
      var letzte = hoehen.isEmpty() ? null : hoehen.get(hoehen.size() - 1);
      boolean gleicheHoehe =
          letzte != null
              && letzte.seite() == zeile.seite()
              && Math.abs(letzte.grundlinie() - zeile.grundlinie()) <= GRUNDLINIEN_TOLERANZ_PT
              && (kandidat.istLinks() ? letzte.links() == null : letzte.rechts() == null);
      if (gleicheHoehe) {
        hoehen.set(
            hoehen.size() - 1,
            kandidat.istLinks()
                ? new Hoehe(letzte.seite(), letzte.grundlinie(), zeile, letzte.rechts())
                : new Hoehe(letzte.seite(), letzte.grundlinie(), letzte.links(), zeile));
      } else {
        hoehen.add(
            new Hoehe(
                zeile.seite(),
                zeile.grundlinie(),
                kandidat.istLinks() ? zeile : null,
                kandidat.istLinks() ? null : zeile));
      }
    }
    return hoehen;
  }

  private static boolean traegtInhalt(FontgroessenFilter.Zeile zeile) {
    var text = nurText(zeile.text());
    return !text.isBlank() && !TextBereiniger.istKolumnentitel(text);
  }

  private static int findeZeile(List<Hoehe> hoehen, Pattern muster) {
    for (int i = 0; i < hoehen.size(); i++) {
      var links = hoehen.get(i).links();
      if (links != null && muster.matcher(nurText(links.text())).matches()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Setzt die beschlossene Fassung Zeile für Zeile zusammen.
   *
   * <p>Maßgeblich ist stets die zuletzt in der rechten Spalte vermerkte Entscheidung: Nach einem
   * „unverändert“ gilt die linke Spalte, nach „entfällt“ oder ausgeschriebenem Text die rechte.
   * Höhen, auf denen nur die linke Spalte steht, sind daher entweder die Fortsetzung eines
   * unveränderten Blocks (sie gelten) oder der vom Ausschuss ersetzte Entwurfstext (er entfällt).
   */
  private static List<String> loeseAuf(List<Hoehe> hoehen, List<String> warnungen) {
    var fassung = new ArrayList<String>();
    boolean linkeSpalteGilt = true;
    int unveraendert = 0;
    int gestrichen = 0;
    int uebernommen = 0;
    for (var hoehe : hoehen) {
      var rechts = hoehe.rechts();
      var links = hoehe.links();
      if (rechts == null) {
        if (linkeSpalteGilt && links != null) {
          fassung.add(links.text());
        }
        continue;
      }
      var vermerk = TextBereiniger.entsperreUnveraendert(nurText(rechts.text()));
      var unveraendertMarke = UNVERAENDERT.matcher(vermerk);
      if (unveraendertMarke.matches()) {
        linkeSpalteGilt = true;
        unveraendert++;
        if (links == null) {
          warnungen.add(
              ("Die Zusammenstellung vermerkt auf Seite %d „unverändert“, ohne dass dort eine"
                      + " Entwurfszeile steht; die Stelle fehlt in der beschlossenen Fassung.")
                  .formatted(rechts.seite()));
          continue;
        }
        fassung.add(mitMarkeDerAusschussspalte(links.text(), unveraendertMarke.group(1)));
        continue;
      }
      linkeSpalteGilt = false;
      if (ENTFAELLT.matcher(vermerk).matches()) {
        // Der Ausschuss streicht den Punkt: Die beschlossene Fassung schweigt hier.
        gestrichen++;
        continue;
      }
      uebernommen++;
      fassung.add(rechts.text());
    }
    log.debugf(
        "Zusammenstellung aufgelöst: %d Zeilen aus der Ausschussspalte, %d unverändert übernommen,"
            + " %d gestrichen.",
        uebernommen, unveraendert, gestrichen);
    return fassung;
  }

  /**
   * Die Entwurfszeile unter der Marke, die der Ausschuss ihr gibt.
   *
   * <p>„Unverändert“ meint den <em>Wortlaut</em>, nicht die Zählung: Streicht der Ausschuss einen
   * Punkt, rücken die folgenden auf, und seine Marke 5. steht der Nummer 6 des Entwurfs gegenüber.
   * Maßgeblich ist deshalb die Marke der rechten, der Wortlaut der linken Spalte.
   */
  private static String mitMarkeDerAusschussspalte(
      String entwurfsZeile, @Nullable String markeMitLeerraum) {
    if (markeMitLeerraum == null) {
      return entwurfsZeile;
    }
    var marke = FUEHRENDE_MARKE.matcher(entwurfsZeile);
    if (!marke.find()) {
      return markeMitLeerraum + entwurfsZeile;
    }
    // Der Einzug ist der der Entwurfszeile: Er trägt die Ebene, die Marke nur die Zählung.
    return marke.group(1) + markeMitLeerraum + entwurfsZeile.substring(marke.end());
  }
}
