// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.DokumentArt;
import eu.mulk.aendggner.aenderung.DokumentKopf;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Bestimmt die Art eines Änderungsdokuments aus seinem Kopf.
 *
 * <p>Gearbeitet wird auf dem <em>rohen</em> Extraktionstext, nicht auf dem von {@link
 * TextBereiniger} bereinigten: Gerade die Drucksachenköpfe, aus denen die Nummern stammen, entfernt
 * der Bereiniger als Kolumnentitel. Erkannt wird ausschließlich am Text, nie an Dateinamen — die
 * Beispieldaten zeigen, warum: {@code GEG/BT-Drs-21-7071_Beschlussempfehlung.pdf} ist in Wahrheit
 * ein Entschließungsantrag.
 */
public final class DokumentErkenner {

  private DokumentErkenner() {}

  /** So viele nichtleere Zeilen gelten als Kopf des Dokuments. */
  private static final int KOPFZEILEN = 120;

  /** Was im Rohtext wie Leerraum wirkt, ohne für {@code \s} welcher zu sein. */
  private static final Pattern ZU_LEERRAUM = Pattern.compile("[\\uE000-\\uE002\\u00A0\\uFEFF]");

  // Dokumentart-Zeilen: In Drucksachen steht die Art als eigene Zeile über dem Titel („Gesetzent-
  // wurf“ / „der Staatsregierung“). Der Zeilenanfang ist wesentlich — „zum Gesetzentwurf der
  // Staatsregierung …“ im Änderungsantrag darf nicht als Entwurf durchgehen.
  private static final Pattern AENDERUNGSANTRAG_ZEILE = Pattern.compile("^Änderungsantr[aä]g\\b.*");
  private static final Pattern ENTSCHLIESSUNGSANTRAG_ZEILE =
      Pattern.compile("^Entschließungsantr[aä]g\\b.*");
  private static final Pattern BESCHLUSSEMPFEHLUNG_ZEILE =
      Pattern.compile("^Beschlussempfehlung\\b.*");
  private static final Pattern ANTRAG_ZEILE = Pattern.compile("^Antr[aä]g\\b.*");
  private static final Pattern ENTWURF_ZEILE =
      Pattern.compile("^(?:Gesetzentwurf|Verordnungsentwurf|Referentenentwurf|Entwurf eines)\\b.*");
  private static final Pattern PROTOKOLL_ZEILE =
      Pattern.compile(
          "^(?:Plenarprotokoll|Stenografischer Bericht)\\b.*|^\\d+\\. Wahlperiode\\s+Protokoll\\b.*");

  private static final Pattern BESCHLUSSFORMEL =
      Pattern.compile("\\bwolle\\s+beschließen\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern ZUSAMMENSTELLUNG = Pattern.compile("\\bZusammenstellung\\b");
  private static final Pattern BEARBEITUNGSSTAND = Pattern.compile("^Bearbeitungsstand:.*");
  private static final Pattern AENDERUNGSFORMEL = Pattern.compile("wird wie folgt geändert");
  private static final Pattern ARTIKEL_UEBERSCHRIFT = Pattern.compile("^Artikel\\s+\\d+[a-z]?$");

  // „Deutscher Bundestag Drucksache 20/7619“, „19. Wahlperiode 02.03.2026 Drucksache 19/10365“.
  private static final Pattern EIGENE_DRUCKSACHE = Pattern.compile("\\bDrucksache\\s+(\\d+/\\d+)");

  /** So viele nichtleere Zeilen weit reicht der Drucksachenkopf. */
  private static final int DRUCKSACHENKOPF_ZEILEN = 10;

  // Bezugsangaben: „– Drucksache 20/6875 –“, „– Drucksachen 21/6278, 21/6565, 21/7009 –“,
  // „(Drs. 19/9707)“.
  private static final Pattern BEZUG_DRUCKSACHEN =
      Pattern.compile("[–—-]\\s*Drucksachen?\\s+((?:\\d+/\\d+)(?:\\s*,\\s*\\d+/\\d+)*)\\s*[–—-]");
  private static final Pattern BEZUG_DRS = Pattern.compile("\\(\\s*Drs\\.?\\s*(\\d+/\\d+)\\s*\\)");
  private static final Pattern NUMMER = Pattern.compile("\\d+/\\d+");

  private static final Pattern HIER_ZEILE = Pattern.compile("^hier:\\s*(.+)$");
  private static final Pattern ENTWURF_EINES = Pattern.compile("^Entwurf eines\\b.*");
  private static final Pattern TITELFORTSETZUNG = Pattern.compile("^(?:der|des|zur|zum|über)\\b.*");
  // Wo der Titel endet: Gliederungsmarken des Vorblatts, die Aufzählung weiterer Vorlagen in einer
  // Beschlussempfehlung („b) zu dem Antrag der Fraktion …“), die Verkündungsformel, der
  // Gesetzestext.
  private static final Pattern TITELENDE =
      Pattern.compile(
          "^(?:[A-Za-z][.)]\\s.*|Vom\\s.*|Der\\s+(?:Bundestag|Landtag)\\b.*|Artikel\\s+\\d.*"
              + "|§\\s*\\d.*|Problem\\b.*|Drucksache\\s.*)");

  /** So viele Zeilen darf ein Titel überspannen. */
  private static final int TITELZEILEN = 6;

  /** Die Ausfertigungszeile eines verkündeten Werkes: „Vom 22. April 2026“. */
  private static final Pattern VOM_ZEILE = Pattern.compile("^Vom " + DeutschesDatum.MUSTER + "$");

  /**
   * @param rohText der unbereinigte Extraktionstext des Dokuments.
   */
  public static DokumentKopf erkenne(String rohText) {
    var zeilen = kopfZeilen(rohText);
    var art = bestimmeArt(zeilen, rohText);
    return new DokumentKopf(
        art,
        eigeneDrucksache(zeilen),
        bezugsDrucksachen(zeilen),
        titel(zeilen),
        ausfertigung(rohText));
  }

  /**
   * Eine Rohtextzeile, wie die Erkennung sie liest: ohne die Zeilenend- und Schriftgrößenmarken aus
   * dem privaten Unicode-Bereich, die erst der {@link TextBereiniger} auswertet, und ohne
   * geschützte Leerzeichen, die für {@code \s} nicht als Leerraum zählen. Beides hinge sonst
   * unsichtbar an Titeln, Nummern und Daten.
   */
  private static String normiere(String zeile) {
    return ZU_LEERRAUM.matcher(zeile).replaceAll(" ").replaceAll("\\s+", " ").strip();
  }

  /**
   * Das Ausfertigungsdatum, das die Zeile „Vom …“ nennt.
   *
   * <p>Nur wenn das <em>ganze</em> Dokument genau eine solche Zeile führt. Ein Sammelheft stellt
   * mehrere Verkündungen nebeneinander (so das thüringische GVBl. Nr. 2/2026 mit vieren), und
   * welche von ihnen das Dokument ausmacht, sagt es nicht; dann bleibt das Datum lieber leer, als
   * falsch zu sein. Denn an ihm hängt die Wiedererkennung des Heftes, und ein falsch bezeichnetes
   * Heft wäre schlimmer als ein unbezeichnetes. Gesucht wird deshalb über den vollen Text und nicht
   * bloß über die {@link #KOPFZEILEN} des Kopfes: Die zweite Verkündung stünde sonst außerhalb des
   * Blickfeldes, und das Heft trüge den Namen der ersten.
   */
  private static @Nullable LocalDate ausfertigung(String rohText) {
    LocalDate gefunden = null;
    for (var zeile : rohText.split("\n", -1)) {
      var m = VOM_ZEILE.matcher(normiere(zeile));
      if (!m.matches()) {
        continue;
      }
      if (gefunden != null) {
        return null;
      }
      gefunden = DeutschesDatum.lies(m.group(1), m.group(2), m.group(3));
    }
    return gefunden;
  }

  /**
   * Die ersten {@link #KOPFZEILEN} nichtleeren Zeilen, jeweils auf einfache Leerzeichen normiert.
   */
  private static List<String> kopfZeilen(String rohText) {
    var zeilen = new ArrayList<String>();
    for (var zeile : rohText.split("\n", -1)) {
      var normiert = normiere(zeile);
      if (normiert.isEmpty()) {
        continue;
      }
      zeilen.add(normiert);
      if (zeilen.size() >= KOPFZEILEN) {
        break;
      }
    }
    return zeilen;
  }

  private static DokumentArt bestimmeArt(List<String> zeilen, String rohText) {
    if (trifftZu(zeilen, PROTOKOLL_ZEILE)) {
      return DokumentArt.OHNE_BEFEHLE;
    }
    // Ein Änderungsantrag trägt seine Befehle hinter der Beschlussformel; ohne sie ist die
    // Kopfzeile allein kein Beleg (sie kann in einer Begründung zitiert sein).
    if (trifftZu(zeilen, AENDERUNGSANTRAG_ZEILE) && BESCHLUSSFORMEL.matcher(rohText).find()) {
      return DokumentArt.AENDERUNGSANTRAG;
    }
    if (trifftZu(zeilen, ENTSCHLIESSUNGSANTRAG_ZEILE)) {
      return DokumentArt.OHNE_BEFEHLE;
    }
    if (trifftZu(zeilen, BESCHLUSSEMPFEHLUNG_ZEILE)) {
      // Nur mit Zusammenstellung trägt die Empfehlung eine Gesetzesfassung; eine reine
      // Annahme-/Ablehnungsempfehlung ist ein Dokument ohne Befehle.
      return ZUSAMMENSTELLUNG.matcher(rohText).find()
          ? DokumentArt.BESCHLUSSEMPFEHLUNG
          : DokumentArt.OHNE_BEFEHLE;
    }
    if (trifftZu(zeilen, ENTWURF_ZEILE) || trifftZu(zeilen, BEARBEITUNGSSTAND)) {
      return DokumentArt.GESETZENTWURF;
    }
    if (trifftZu(zeilen, ANTRAG_ZEILE)) {
      return DokumentArt.OHNE_BEFEHLE;
    }
    return trifftZu(zeilen, ARTIKEL_UEBERSCHRIFT) || AENDERUNGSFORMEL.matcher(rohText).find()
        ? DokumentArt.ARTIKELGESETZ
        : DokumentArt.UNBEKANNT;
  }

  private static boolean trifftZu(List<String> zeilen, Pattern muster) {
    return zeilen.stream().anyMatch(zeile -> muster.matcher(zeile).matches());
  }

  private static @Nullable String eigeneDrucksache(List<String> zeilen) {
    for (var zeile : zeilen.subList(0, Math.min(DRUCKSACHENKOPF_ZEILEN, zeilen.size()))) {
      var treffer = EIGENE_DRUCKSACHE.matcher(zeile);
      if (treffer.find()) {
        return treffer.group(1);
      }
    }
    return null;
  }

  private static List<String> bezugsDrucksachen(List<String> zeilen) {
    var eigene = eigeneDrucksache(zeilen);
    var nummern = new LinkedHashSet<String>();
    for (var zeile : zeilen) {
      var liste = BEZUG_DRUCKSACHEN.matcher(zeile);
      while (liste.find()) {
        var einzeln = NUMMER.matcher(liste.group(1));
        while (einzeln.find()) {
          nummern.add(einzeln.group());
        }
      }
      var drs = BEZUG_DRS.matcher(zeile);
      while (drs.find()) {
        nummern.add(drs.group(1));
      }
    }
    nummern.remove(eigene);
    return List.copyOf(nummern);
  }

  /**
   * Eine kurze Bezeichnung fürs Protokoll: der {@code hier:}-Zusatz eines Antrags, sonst der
   * Entwurfstitel, sonst der Titel unter der Dokumentart-Zeile. Findet sich nichts davon (etwa im
   * Gesetzblatt), bleibt sie leer — die Quellenzeile trägt dann Dateiname und Dokumentart.
   */
  private static String titel(List<String> zeilen) {
    for (var zeile : zeilen) {
      var hier = HIER_ZEILE.matcher(zeile);
      if (hier.matches()) {
        return hier.group(1).strip();
      }
    }
    for (int i = 0; i < zeilen.size(); i++) {
      if (ENTWURF_EINES.matcher(zeilen.get(i)).matches()) {
        return sammleTitel(zeilen, i);
      }
    }
    for (int i = 0; i < zeilen.size(); i++) {
      if (ENTWURF_ZEILE.matcher(zeilen.get(i)).matches()
          && i + 1 < zeilen.size()
          && TITELFORTSETZUNG.matcher(zeilen.get(i + 1)).matches()) {
        return sammleTitel(zeilen, i);
      }
    }
    return "";
  }

  /** Sammelt ab {@code start} die zusammengehörigen Titelzeilen bis zur nächsten Strukturmarke. */
  private static String sammleTitel(List<String> zeilen, int start) {
    var titel = new StringBuilder(zeilen.get(start));
    for (int i = start + 1; i < Math.min(start + TITELZEILEN, zeilen.size()); i++) {
      var zeile = zeilen.get(i);
      if (TITELENDE.matcher(zeile).matches()) {
        break;
      }
      titel.append(' ').append(zeile);
      if (zeile.endsWith(".")) {
        break;
      }
    }
    return titel.toString().replaceAll("\\s+", " ").strip();
  }
}
