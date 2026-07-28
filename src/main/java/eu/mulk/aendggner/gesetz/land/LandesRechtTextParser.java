package eu.mulk.aendggner.gesetz.land;

import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Gliederung;
import eu.mulk.aendggner.gesetz.Norm;
import eu.mulk.aendggner.gesetz.Superskript;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Parst den kanonischen Lineartext eines Landesrecht-Stammgesetzes zu einem {@link Gesetz}. Das
 * Layout entspricht der {@code --extract-only}-Ausgabe der konsolidierten PDF-Fassung (siehe {@link
 * LandesRechtLoader}). Die Norm-Gliederung folgt dem jeweiligen Land: der Bund und die meisten
 * Länder zitieren in {@code §}, Bayern in {@code Art.} — das Sigel wird je Norm aus dem Normkopf
 * abgeleitet und nach {@link Norm#enbez()} durchgereicht, ein zentrales „Land“-Merkmal ist nicht
 * nötig.
 *
 * <pre>
 * Bayerisches Jagdgesetz          ← Langtitel
 * (BayJG)                         ← Juris-Abkürzung
 * Vom 13. Oktober 1978            ← Datumszeile (übersprungen)
 * (BayRS V S. 595)                ← Fundstellen (übersprungen)
 * BayRS 792-1-W
 * Vollzitat nach RedR: …          ← übersprungen (ggf. mehrzeilig)
 * I. Abschnitt Grundsätze         ← Gliederungs-Überschrift
 * 1. Allgemeine Vorschriften      ← nummerierte Unter-Überschrift (vor einem Normkopf)
 * Art. 1  Gesetzeszweck           ← Normkopf (§/Art.); „Art. 60  (aufgehoben)“ → weggefallen
 * (1) ¹Die freilebende Tierwelt … ← Absätze mit amtlichen Satznummern als Superskript
 * ⁶) [Amtl. Anm.:] …              ← Fußnotenzeile, verbleibt im Text des tragenden Absatzes
 * </pre>
 */
final class LandesRechtTextParser {

  private static final Pattern ABSATZ_MARKER = Pattern.compile("^\\((\\d+[a-z]?)\\)\\s+");

  // Juris-Abkürzung direkt hinter dem Langtitel. Bayern führt einteilige Kürzel („(BayJG)“), die
  // übrigen Länder oft mehrteilige („(GO NRW)“); Nur die Zeile unmittelbar nach dem Titel wird
  // geprüft, sodass spätere Fundstellen-Klammern („(BayRS V S. 595)“) nicht getroffen werden.
  private static final Pattern JURABK_ZEILE = Pattern.compile("^\\(([^()]+)\\)$");

  /** Trennt im Klammerzusatz den Kurztitel von der Abkürzung („Kurztitel – Abk“). */
  private static final Pattern KURZTITEL_TRENNER = Pattern.compile("\\s+[–—]\\s+");

  // Kopf der Druckfassung („BayJG: Bayerisches Jagdgesetz … (Art. 1–64)“), ggf. mit
  // umbrochenem Rest („1–64)“) auf der Folgezeile.
  private static final Pattern DRUCKKOPF = Pattern.compile("^\\S{1,20}: .+$");
  private static final Pattern DRUCKKOPF_REST = Pattern.compile("^\\d+[–-]\\d+[a-z]?\\)$");

  private static final Pattern GLIEDERUNG =
      Pattern.compile("^([IVXLCDM]+)\\.\\s+(Abschnitt|Teil|Kapitel)\\b\\s*(.*)$");

  // Gliederungs-Überschrift in keyword-erster, arabischer Form („Abschnitt 1“, „Unterabschnitt 2“,
  // „Teil 3“). Die meisten Länder (z.B. Sachsen) gliedern so; Bayern dagegen römisch mit
  // nachgestelltem Schlüsselwort („I. Abschnitt“, Muster GLIEDERUNG). Der Titel steht — nach der
  // kanonischen Aufbereitung — mit doppeltem Leerzeichen auf derselben Zeile.
  private static final Pattern GLIEDERUNG_ARABISCH =
      Pattern.compile("^(Buch|Teil|Kapitel|Abschnitt|Unterabschnitt|Titel) (\\d+[a-z]?)\\s+(\\S.*)$");

  // Römische Gliederung ohne Schlüsselwort („I. Rechtsform und Aufgaben“, NRW). Sie ist nur an der
  // Stellung erkennbar, deshalb gilt dieselbe Absicherung wie für UNTER_GLIEDERUNG: kurzer,
  // großgeschriebener, satzzeichenfreier Titel und ein Normkopf oder eine weitere Überschrift als
  // nächste nicht-leere Zeile.
  private static final Pattern GLIEDERUNG_ROEMISCH = Pattern.compile("^([IVXLCDM]+)\\.\\s+(\\S.*)$");

  private static final Pattern UNTER_GLIEDERUNG = Pattern.compile("^(\\d+[a-z]?)\\.\\s+(\\S.*)$");

  /**
   * Die Inhaltsübersicht ist eine Norm ohne Sigel; Angabe-Befehle adressieren sie unter genau
   * diesem Namen (siehe {@code InhaltsuebersichtAnwender}). Ihre Zeilen tragen das
   * Übersichtsformat „§ N | Titel“.
   */
  private static final String INHALTSUEBERSICHT = "Inhaltsübersicht";

  // Normkopf: „§ N“ bzw. „Art. N“ plus Titel auf derselben Zeile. Das Sigel steht in Gruppe 1, die
  // Nummer in Gruppe 2, der Titel in Gruppe 3. Die Negativliste schließt Querverweise am
  // Zeilenanfang aus („Art. 4 Abs. 3 …“, „§ 5 Absatz 2 …“).
  private static final Pattern NORM_KOPF =
      Pattern.compile(
          "^(§|Art\\.)\\s+(\\d+[a-z]?)\\s+"
              // Querverweis-Schlüsselwörter nur als ganzes Wort ausschließen: „§ 4 Satz 2“ ist ein
              // Verweis, „§ 4 Satzungen“ dagegen ein Normtitel. Der Schutz „(?![a-zäöüß])“ verhindert,
              // dass „Satz“ auch „Satzungen“, „Nummer“ auch „Nummerierung“ trifft.
              + "(?!(?:Absatz|Abs\\.|Satz|Sätze|Nummer|Nr\\.|Buchstabe|Buchst\\."
              + "|und|bis|oder|sowie|des|der|dieses)(?![a-zäöüß]))"
              // Der Titel endet nicht auf einen Punkt: ein Normkopf trägt eine Überschrift, keinen
              // ganzen Satz. So wird ein am Zeilenanfang stehender Querverweis-Satz („§ 7 GAPInVeKoSG
              // findet entsprechend Anwendung.“) nicht fälschlich als Normkopf „§ 7“ gelesen.
              + "((?:\\p{Lu}|\\().*[^.])\\s*$");

  private static final Pattern WEGGEFALLEN_TITEL =
      Pattern.compile("^\\((?:aufgehoben|weggefallen)\\)$");

  private static final Pattern FUSSNOTE =
      Pattern.compile("^([⁰¹²³⁴⁵⁶⁷⁸⁹]+|\\d{1,3})\\)\\s*(\\[Amtl\\. Anm\\.:\\].*)$");

  private LandesRechtTextParser() {}

  static Gesetz parse(String text) {
    var zeilen = text.lines().toList();
    int i = ueberspringeDruckkopf(zeilen);

    // Titelblock.
    while (i < zeilen.size() && zeilen.get(i).isBlank()) {
      i++;
    }
    if (i >= zeilen.size()) {
      throw new IllegalArgumentException("Leere Eingabe: kein Titel gefunden.");
    }
    var langue = zeilen.get(i++).strip();
    String jurabk = null;
    String kurzue = null;
    if (i < zeilen.size()) {
      var m = JURABK_ZEILE.matcher(zeilen.get(i).strip());
      if (m.matches()) {
        // Landesgesetze führen im Klammerzusatz häufig Kurztitel und Abkürzung nebeneinander
        // („Telemedienzuständigkeitsgesetz – TMZ-Gesetz“, „Gemeindeordnung – GO –“). Das
        // Änderungsgesetz zitiert dann den Kurztitel, nicht die Abkürzung.
        var teile = KURZTITEL_TRENNER.split(m.group(1).strip(), 2);
        if (teile.length == 2) {
          kurzue = teile[0].strip();
          jurabk = teile[1].strip().replaceAll("\\s*[–—-]\\s*$", "");
        } else {
          jurabk = m.group(1).strip();
        }
        i++;
      }
    }
    // Rest des Titelblocks (Datum, Fundstellen, Vollzitat) bis zur ersten Struktur überspringen.
    while (i < zeilen.size() && !istStrukturZeile(zeilen, i)) {
      i++;
    }

    var normen = new ArrayList<Norm>();
    var gliederungen = new ArrayList<Gliederung>();
    Gliederung aktuelleGliederung = null;
    String elternKennzahl = null;
    int gliederungsZaehler = 0;

    String normEnbez = null;
    String normTitel = null;
    var normZeilen = new ArrayList<String>();
    int letzteNormNummer = 0;

    for (; i <= zeilen.size(); i++) {
      var zeile = i < zeilen.size() ? zeilen.get(i).strip() : null;

      var gliederung = zeile != null ? GLIEDERUNG.matcher(zeile) : null;
      var arabisch = zeile != null ? GLIEDERUNG_ARABISCH.matcher(zeile) : null;
      var roemisch = zeile != null ? GLIEDERUNG_ROEMISCH.matcher(zeile) : null;
      var unterGliederung = zeile != null ? UNTER_GLIEDERUNG.matcher(zeile) : null;
      var normKopf = zeile != null ? NORM_KOPF.matcher(zeile) : null;
      var uebersicht = INHALTSUEBERSICHT.equals(zeile);
      // Die Inhaltsübersicht führt die Gliederungs-Überschriften des Gesetzes als eigene Zeilen mit
      // („Abschnitt 1 Allgemeine Vorschriften“). Innerhalb ihrer beendet eine solche Zeile die Norm
      // nur, wenn ihr ein Normkopf folgt — sonst zerfiele die Übersicht und die Angaben gingen
      // verloren.
      var gliederungsZeile =
          zeile != null
              && (gliederung.matches()
                  || arabisch.matches()
                  || (roemisch.matches() && istUnterGliederung(roemisch, zeilen, i))
                  || (unterGliederung.matches() && istUnterGliederung(unterGliederung, zeilen, i)))
              && (!INHALTSUEBERSICHT.equals(normEnbez) || folgtNormkopf(zeilen, i));

      if (zeile == null
          || uebersicht
          || gliederungsZeile
          || (normKopf.matches() && istNeuerNormKopf(normKopf.group(2), letzteNormNummer))) {
        // Laufende Norm abschließen.
        if (normEnbez != null) {
          normen.add(baueNorm(normEnbez, normTitel, aktuelleGliederung, normZeilen));
        }
        normEnbez = null;
        normZeilen.clear();

        if (zeile == null) {
          break;
        }
        if (uebersicht) {
          normEnbez = INHALTSUEBERSICHT;
          normTitel = null;
        } else if (gliederung.matches()) {
          gliederungsZaehler++;
          elternKennzahl = String.format("%03d", gliederungsZaehler);
          var titel = gliederung.group(3).strip();
          aktuelleGliederung =
              new Gliederung(
                  elternKennzahl,
                  gliederung.group(1) + ". " + gliederung.group(2),
                  titel.isEmpty() ? null : titel);
          gliederungen.add(aktuelleGliederung);
        } else if (arabisch.matches()) {
          gliederungsZaehler++;
          elternKennzahl = String.format("%03d", gliederungsZaehler);
          var titel = arabisch.group(3).strip();
          aktuelleGliederung =
              new Gliederung(
                  elternKennzahl,
                  arabisch.group(1) + " " + arabisch.group(2),
                  titel.isEmpty() ? null : titel);
          gliederungen.add(aktuelleGliederung);
        } else if (normKopf.matches() && istNeuerNormKopf(normKopf.group(2), letzteNormNummer)) {
          normEnbez = normKopf.group(1) + " " + normKopf.group(2);
          normTitel = normKopf.group(3).strip();
          letzteNormNummer = numerisch(normKopf.group(2));
        } else if (roemisch.matches()) {
          gliederungsZaehler++;
          elternKennzahl = String.format("%03d", gliederungsZaehler);
          aktuelleGliederung =
              new Gliederung(
                  elternKennzahl, roemisch.group(1) + ".", roemisch.group(2).strip());
          gliederungen.add(aktuelleGliederung);
        } else {
          var kennzahl =
              (elternKennzahl != null ? elternKennzahl : "000") + "." + unterGliederung.group(1);
          aktuelleGliederung =
              new Gliederung(kennzahl, unterGliederung.group(1) + ".", unterGliederung.group(2));
          gliederungen.add(aktuelleGliederung);
        }
        continue;
      }

      if (normEnbez != null) {
        normZeilen.add(zeilen.get(i));
      }
    }

    if (normen.isEmpty()) {
      throw new IllegalArgumentException(
          "Kein „§ N“- oder „Art. N“-Normkopf gefunden — ist das eine konsolidierte Fassung im"
              + " kanonischen Klartextformat?");
    }
    return new Gesetz(jurabk != null ? jurabk : langue, langue, kurzue, normen, gliederungen);
  }

  /** Eröffnet die Zeile eine Struktureinheit (Gliederung, Inhaltsübersicht oder Normkopf)? */
  private static boolean istStrukturZeile(List<String> zeilen, int i) {
    var zeile = zeilen.get(i).strip();
    var roemisch = GLIEDERUNG_ROEMISCH.matcher(zeile);
    return INHALTSUEBERSICHT.equals(zeile)
        || GLIEDERUNG.matcher(zeile).matches()
        || GLIEDERUNG_ARABISCH.matcher(zeile).matches()
        || NORM_KOPF.matcher(zeile).matches()
        || (roemisch.matches() && istUnterGliederung(roemisch, zeilen, i));
  }

  /**
   * Die nächste nicht-leere Zeile ist ein Normkopf. Innerhalb der Inhaltsübersicht unterscheidet
   * das die zitierte Gliederungs-Überschrift („Abschnitt 1 …“, gefolgt von Angabezeilen) von der
   * gleichlautenden Überschrift des Textteils, die die Übersicht beendet.
   */
  private static boolean folgtNormkopf(List<String> zeilen, int index) {
    for (int j = index + 1; j < zeilen.size(); j++) {
      var naechste = zeilen.get(j).strip();
      if (!naechste.isEmpty()) {
        return NORM_KOPF.matcher(naechste).matches();
      }
    }
    return false;
  }

  /** Überspringt die Kopfzeile der Druckfassung samt umbrochenem Rest. */
  private static int ueberspringeDruckkopf(List<String> zeilen) {
    int i = 0;
    while (i < zeilen.size() && zeilen.get(i).isBlank()) {
      i++;
    }
    if (i < zeilen.size() && DRUCKKOPF.matcher(zeilen.get(i).strip()).matches()) {
      i++;
      if (i < zeilen.size() && DRUCKKOPF_REST.matcher(zeilen.get(i).strip()).matches()) {
        i++;
      }
    }
    return i;
  }

  /**
   * Ein Normkopf eröffnet nur dann eine neue Norm, wenn seine Nummer hinter der letzten liegt —
   * das fängt Querverweise ab, die die Negativliste nicht ausschließt.
   */
  private static boolean istNeuerNormKopf(String nummer, int letzteNormNummer) {
    return numerisch(nummer) >= letzteNormNummer;
  }

  private static int numerisch(String nummer) {
    return Integer.parseInt(nummer.replaceAll("[a-z]+$", ""));
  }

  /**
   * Eine nummerierte Zeile ist eine Unter-Überschrift (keine Aufzählung), wenn ihr Text kurz ist,
   * großgeschrieben beginnt, nicht mit Satzzeichen endet und die nächste nicht-leere Zeile ein
   * Normkopf oder eine weitere Überschrift ist.
   */
  private static boolean istUnterGliederung(
      java.util.regex.Matcher unterGliederung, List<String> zeilen, int index) {
    var titel = unterGliederung.group(2);
    if (titel.length() > 80
        || !Character.isUpperCase(titel.codePointAt(0))
        || titel.matches(".*[.,;:]$")) {
      return false;
    }
    for (int j = index + 1; j < zeilen.size(); j++) {
      var naechste = zeilen.get(j).strip();
      if (naechste.isEmpty()) {
        continue;
      }
      return NORM_KOPF.matcher(naechste).matches()
          || GLIEDERUNG.matcher(naechste).matches()
          || UNTER_GLIEDERUNG.matcher(naechste).matches();
    }
    return false;
  }

  private static Norm baueNorm(
      String enbez,
      @Nullable String titel,
      @Nullable Gliederung gliederung,
      List<String> zeilen) {
    boolean weggefallen = titel != null && WEGGEFALLEN_TITEL.matcher(titel).matches();

    var absaetze = new ArrayList<Absatz>();
    String absatzNummer = null;
    var absatzZeilen = new ArrayList<String>();
    for (var zeile : zeilen) {
      var gestutzt = zeile.strip();
      if (gestutzt.isEmpty()) {
        continue;
      }
      var fussnote = FUSSNOTE.matcher(gestutzt);
      if (fussnote.matches()) {
        // Fußnoten verbleiben superskript-normalisiert als eigene Zeile im tragenden Absatz.
        absatzZeilen.add(Superskript.zuSuperskript(fussnote.group(1)) + ") " + fussnote.group(2));
        continue;
      }
      var marker = ABSATZ_MARKER.matcher(gestutzt);
      if (marker.find()) {
        if (!absatzZeilen.isEmpty()) {
          absaetze.add(new Absatz(absatzNummer, String.join("\n", absatzZeilen)));
          absatzZeilen.clear();
        }
        absatzNummer = marker.group(1);
        absatzZeilen.add(gestutzt.substring(marker.end()));
      } else {
        absatzZeilen.add(zeile.stripTrailing());
      }
    }
    if (!absatzZeilen.isEmpty()) {
      absaetze.add(new Absatz(absatzNummer, String.join("\n", absatzZeilen)));
    }

    return new Norm(
        enbez, titel == null || titel.isEmpty() ? null : titel, gliederung, absaetze, weggefallen);
  }
}
