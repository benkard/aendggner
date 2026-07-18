package eu.mulk.aendggner.gesetz.bayern;

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
 * Parst den kanonischen Lineartext eines bayerischen Stammgesetzes (gesetze-bayern.de) zu einem
 * {@link Gesetz}. Das Layout entspricht der {@code --extract-only}-Ausgabe der konsolidierten
 * PDF-Fassung (siehe {@link BayRechtLoader}):
 *
 * <pre>
 * Bayerisches Jagdgesetz          ← Langtitel
 * (BayJG)                         ← Juris-Abkürzung
 * Vom 13. Oktober 1978            ← Datumszeile (übersprungen)
 * (BayRS V S. 595)                ← Fundstellen (übersprungen)
 * BayRS 792-1-W
 * Vollzitat nach RedR: …          ← übersprungen (ggf. mehrzeilig)
 * I. Abschnitt Grundsätze         ← Gliederungs-Überschrift
 * 1. Allgemeine Vorschriften      ← nummerierte Unter-Überschrift (vor einem Art.-Kopf)
 * Art. 1  Gesetzeszweck           ← Normkopf; „Art. 60  (aufgehoben)“ → weggefallen
 * (1) ¹Die freilebende Tierwelt … ← Absätze mit amtlichen Satznummern als Superskript
 * ⁶) [Amtl. Anm.:] …              ← Fußnotenzeile, verbleibt im Text des tragenden Absatzes
 * </pre>
 */
final class BayRechtTextParser {

  private static final Pattern ABSATZ_MARKER = Pattern.compile("^\\((\\d+[a-z]?)\\)\\s+");

  private static final Pattern JURABK_ZEILE = Pattern.compile("^\\((\\S+)\\)$");

  // Kopf der Druckfassung („BayJG: Bayerisches Jagdgesetz … (Art. 1–64)“), ggf. mit
  // umbrochenem Rest („1–64)“) auf der Folgezeile.
  private static final Pattern DRUCKKOPF = Pattern.compile("^\\S{1,20}: .+$");
  private static final Pattern DRUCKKOPF_REST = Pattern.compile("^\\d+[–-]\\d+[a-z]?\\)$");

  private static final Pattern GLIEDERUNG =
      Pattern.compile("^([IVXLCDM]+)\\.\\s+(Abschnitt|Teil|Kapitel)\\b\\s*(.*)$");

  private static final Pattern UNTER_GLIEDERUNG = Pattern.compile("^(\\d+[a-z]?)\\.\\s+(\\S.*)$");

  // Normkopf: „Art. N“ plus Titel auf derselben Zeile. Die Negativliste schließt
  // Querverweise am Zeilenanfang aus („Art. 4 Abs. 3 …“).
  private static final Pattern NORM_KOPF =
      Pattern.compile(
          "^Art\\.\\s+(\\d+[a-z]?)\\s+"
              + "(?!Absatz|Abs\\.|Satz|Sätze|Nummer|Nr\\.|Buchstabe|Buchst\\."
              + "|und|bis|oder|sowie|des|der|dieses)"
              + "((?:\\p{Lu}|\\().*)$");

  private static final Pattern WEGGEFALLEN_TITEL =
      Pattern.compile("^\\((?:aufgehoben|weggefallen)\\)$");

  private static final Pattern FUSSNOTE =
      Pattern.compile("^([⁰¹²³⁴⁵⁶⁷⁸⁹]+|\\d{1,3})\\)\\s*(\\[Amtl\\. Anm\\.:\\].*)$");

  private BayRechtTextParser() {}

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
    if (i < zeilen.size()) {
      var m = JURABK_ZEILE.matcher(zeilen.get(i).strip());
      if (m.matches()) {
        jurabk = m.group(1);
        i++;
      }
    }
    // Rest des Titelblocks (Datum, Fundstellen, Vollzitat) bis zur ersten Struktur überspringen.
    while (i < zeilen.size()
        && !GLIEDERUNG.matcher(zeilen.get(i).strip()).matches()
        && !NORM_KOPF.matcher(zeilen.get(i).strip()).matches()) {
      i++;
    }

    var normen = new ArrayList<Norm>();
    var gliederungen = new ArrayList<Gliederung>();
    Gliederung aktuelleGliederung = null;
    String elternKennzahl = null;
    int gliederungsZaehler = 0;

    String normNummer = null;
    String normTitel = null;
    var normZeilen = new ArrayList<String>();
    int letzteNormNummer = 0;

    for (; i <= zeilen.size(); i++) {
      var zeile = i < zeilen.size() ? zeilen.get(i).strip() : null;

      var gliederung = zeile != null ? GLIEDERUNG.matcher(zeile) : null;
      var unterGliederung = zeile != null ? UNTER_GLIEDERUNG.matcher(zeile) : null;
      var normKopf = zeile != null ? NORM_KOPF.matcher(zeile) : null;

      if (zeile == null
          || gliederung.matches()
          || (normKopf.matches() && istNeuerNormKopf(normKopf.group(1), letzteNormNummer))
          || (unterGliederung.matches() && istUnterGliederung(unterGliederung, zeilen, i))) {
        // Laufende Norm abschließen.
        if (normNummer != null) {
          normen.add(baueNorm(normNummer, normTitel, aktuelleGliederung, normZeilen));
        }
        normNummer = null;
        normZeilen.clear();

        if (zeile == null) {
          break;
        }
        if (gliederung.matches()) {
          gliederungsZaehler++;
          elternKennzahl = String.format("%03d", gliederungsZaehler);
          var titel = gliederung.group(3).strip();
          aktuelleGliederung =
              new Gliederung(
                  elternKennzahl,
                  gliederung.group(1) + ". " + gliederung.group(2),
                  titel.isEmpty() ? null : titel);
          gliederungen.add(aktuelleGliederung);
        } else if (normKopf.matches() && istNeuerNormKopf(normKopf.group(1), letzteNormNummer)) {
          normNummer = normKopf.group(1);
          normTitel = normKopf.group(2).strip();
          letzteNormNummer = numerisch(normNummer);
        } else {
          var kennzahl =
              (elternKennzahl != null ? elternKennzahl : "000") + "." + unterGliederung.group(1);
          aktuelleGliederung =
              new Gliederung(kennzahl, unterGliederung.group(1) + ".", unterGliederung.group(2));
          gliederungen.add(aktuelleGliederung);
        }
        continue;
      }

      if (normNummer != null) {
        normZeilen.add(zeilen.get(i));
      }
    }

    if (normen.isEmpty()) {
      throw new IllegalArgumentException(
          "Kein „Art. N“-Normkopf gefunden — ist das eine konsolidierte Fassung von"
              + " gesetze-bayern.de?");
    }
    return new Gesetz(jurabk != null ? jurabk : langue, langue, null, normen, gliederungen);
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
      String nummer, @Nullable String titel, @Nullable Gliederung gliederung, List<String> zeilen) {
    var enbez = "Art. " + nummer;
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

    return new Norm(enbez, titel == null || titel.isEmpty() ? null : titel, gliederung, absaetze, weggefallen);
  }
}
