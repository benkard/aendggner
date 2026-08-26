// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Zerlegt den Rumpf eines Artikels in einen Baum von Gliederungspunkten (1. → a) → aa)).
 *
 * <p>Arbeitet auf platzhalter-substituiertem Text (siehe {@link ZitatExtraktor}), sodass
 * Gliederungsmarker innerhalb von Zitaten nicht stören können. Ein Marker am Zeilenanfang wird nur
 * akzeptiert, wenn er entweder eine neue Ebene eröffnet („1.“, „a)“, „aa)“) oder der Nachfolger des
 * letzten Markers seiner Ebene ist — sonst gilt die Zeile als Fortsetzungstext (das fängt z.B.
 * Datumsangaben wie „18. November“ am Zeilenanfang ab).
 */
final class GliederungsScanner {

  /** Ein Gliederungspunkt mit Marker-Label, Text und Unterpunkten. */
  record GliederungsPunkt(String label, String text, List<GliederungsPunkt> kinder) {}

  /** Ergebnis: Text vor dem ersten Gliederungspunkt (Einleitungssatz) und die Punkte selbst. */
  record ScanErgebnis(String vorspann, List<GliederungsPunkt> punkte) {}

  /**
   * Dezimalgliederung: „6.1“, „7.1.1“ — die Ebene steht in der Zahl selbst. Das hamburgische
   * Gesetzblatt gliedert seine Änderungsbefehle so, wo andere Blätter a)/aa) setzen. Der Punkt
   * hinter dem letzten Glied ist wahlfrei; ein Leerzeichen muss folgen, sonst wäre „6.1“ aus
   * „Nummer 6.1“ ein Marker.
   */
  private static final Pattern DEZIMAL_MARKER =
      Pattern.compile("^(\\d+(?:\\.\\d+[a-z]?)+)\\.?\\s+(\\S.*)$");

  // Eingeschobene Punkte tragen Suffixe: „2a.“, „a1)“, „aa1)“.
  private static final Pattern NUMMER_MARKER = Pattern.compile("^(\\d+[a-z]?)\\.\\s+(.*)$");
  private static final Pattern BUCHSTABE_MARKER = Pattern.compile("^([a-z]\\d*)\\)\\s+(.*)$");
  private static final Pattern DOPPELBUCHSTABE_MARKER =
      Pattern.compile("^(([a-z])\\2\\d*)\\)\\s+(.*)$");
  private static final Pattern DREIFACHBUCHSTABE_MARKER =
      Pattern.compile("^(([a-z])\\2\\2\\d*)\\)\\s+(.*)$");

  private GliederungsScanner() {}

  static ScanErgebnis scanne(List<String> zeilen) {
    var vorspann = new StringBuilder();
    var wurzeln = new ArrayList<MutablerPunkt>();
    var stapel = new ArrayDeque<MutablerPunkt>(); // offene Punkte, äußerster zuerst

    for (var zeile : zeilen) {
      var gestutzt = zeile.strip();
      var marker = erkenneMarker(gestutzt);

      if (marker != null && istAkzeptabel(marker, stapel)) {
        // Tiefere offene Ebenen schließen.
        while (!stapel.isEmpty() && stapel.peekLast().ebene >= marker.ebene) {
          stapel.removeLast();
        }
        var punkt = new MutablerPunkt(marker.label, marker.ebene);
        punkt.text.append(marker.rest);
        if (stapel.isEmpty()) {
          wurzeln.add(punkt);
        } else {
          stapel.peekLast().kinder.add(punkt);
        }
        stapel.addLast(punkt);
      } else if (stapel.isEmpty()) {
        vorspann.append(gestutzt).append('\n');
      } else {
        stapel.peekLast().text.append('\n').append(gestutzt);
      }
    }

    return new ScanErgebnis(
        vorspann.toString().strip(), wurzeln.stream().map(MutablerPunkt::zuRecord).toList());
  }

  private record Marker(String label, int ebene, String rest) {}

  private static Marker erkenneMarker(String zeile) {
    var dezimal = DEZIMAL_MARKER.matcher(zeile);
    if (dezimal.matches()) {
      var label = dezimal.group(1);
      return new Marker(label, label.split("\\.").length, dezimal.group(2));
    }
    var dreifach = DREIFACHBUCHSTABE_MARKER.matcher(zeile);
    if (dreifach.matches()) {
      return new Marker(dreifach.group(1), 4, dreifach.group(3));
    }
    var doppel = DOPPELBUCHSTABE_MARKER.matcher(zeile);
    if (doppel.matches()) {
      return new Marker(doppel.group(1), 3, doppel.group(3));
    }
    var buchstabe = BUCHSTABE_MARKER.matcher(zeile);
    if (buchstabe.matches()) {
      return new Marker(buchstabe.group(1), 2, buchstabe.group(2));
    }
    var nummer = NUMMER_MARKER.matcher(zeile);
    if (nummer.matches()) {
      return new Marker(nummer.group(1), 1, nummer.group(2));
    }
    return null;
  }

  private static boolean istAkzeptabel(Marker marker, ArrayDeque<MutablerPunkt> stapel) {
    // Nachfolger eines offenen Punkts derselben Ebene?
    for (var offen : stapel) {
      if (offen.ebene == marker.ebene) {
        return istNachfolger(offen.label, marker.label);
      }
    }
    // Sonst: nur ein Eröffnungslabel einer tieferen Ebene ist zulässig.
    int aktuelleTiefe = stapel.isEmpty() ? 0 : stapel.peekLast().ebene;
    var eltern = stapel.isEmpty() ? null : stapel.peekLast().label;
    return marker.ebene == aktuelleTiefe + 1 && istEroeffnung(marker, eltern);
  }

  /**
   * Ob das Label eine Ebene eröffnet. Die Dezimalgliederung trägt ihre Herkunft im Label: „6.1“
   * eröffnet die Unterebene von „6.“, aber nur dort — unter „7.“ hätte sie nichts zu suchen.
   */
  private static boolean istEroeffnung(Marker marker, @Nullable String eltern) {
    if (marker.label.contains(".")) {
      return eltern != null
          && marker.label.startsWith(eltern.endsWith(".") ? eltern : eltern + ".")
          && marker.label.endsWith(".1");
    }
    return switch (marker.ebene) {
      case 1 -> marker.label.equals("1");
      case 2 -> marker.label.equals("a");
      case 3 -> marker.label.equals("aa");
      case 4 -> marker.label.equals("aaa");
      default -> false;
    };
  }

  private static boolean istNachfolger(String vorher, String nachher) {
    // Dezimalgliederung: Nachfolger ist, wer denselben Vorspann trägt und im letzten Glied
    // fortzählt („7.1.1“ → „7.1.2“).
    if (vorher.contains(".") || nachher.contains(".")) {
      int vorherTrenner = vorher.lastIndexOf('.');
      int nachherTrenner = nachher.lastIndexOf('.');
      if (vorherTrenner < 0 || nachherTrenner < 0) {
        return false;
      }
      return vorher.substring(0, vorherTrenner).equals(nachher.substring(0, nachherTrenner))
          && istNachfolger(
              vorher.substring(vorherTrenner + 1), nachher.substring(nachherTrenner + 1));
    }
    if (vorher.matches("\\d+[a-z]?") && nachher.matches("\\d+[a-z]?")) {
      var vorherZahl = Integer.parseInt(vorher.replaceAll("[a-z]$", ""));
      var vorherSuffix = vorher.replaceAll("^\\d+", "");
      var nachherZahl = Integer.parseInt(nachher.replaceAll("[a-z]$", ""));
      var nachherSuffix = nachher.replaceAll("^\\d+", "");
      // „2.“ → „3.“, „2.“ → „2a.“, „2a.“ → „2b.“, „2a.“ → „3.“
      return (nachherZahl == vorherZahl + 1 && nachherSuffix.isEmpty())
          || (nachherZahl == vorherZahl
              && !nachherSuffix.isEmpty()
              && (vorherSuffix.isEmpty()
                  ? nachherSuffix.equals("a")
                  : nachherSuffix.charAt(0) == vorherSuffix.charAt(0) + 1));
    }
    if (vorher.matches("[a-z]+\\d*") && nachher.matches("[a-z]+\\d*")) {
      var vorherBasis = vorher.replaceAll("\\d+$", "");
      var vorherSuffix = vorher.substring(vorherBasis.length());
      var nachherBasis = nachher.replaceAll("\\d+$", "");
      var nachherSuffix = nachher.substring(nachherBasis.length());
      // „a)“ → „b)“, „a)“ → „a1)“, „a1)“ → „a2)“, „a1)“ → „b)“; analog „aa)“ → „bb)“ etc.
      if (nachherBasis.equals(basisNachfolger(vorherBasis)) && nachherSuffix.isEmpty()) {
        return true;
      }
      return nachherBasis.equals(vorherBasis)
          && !nachherSuffix.isEmpty()
          && (vorherSuffix.isEmpty()
              ? nachherSuffix.equals("1")
              : Integer.parseInt(nachherSuffix) == Integer.parseInt(vorherSuffix) + 1);
    }
    return false;
  }

  /** „a“ → „b“, „aa“ → „bb“. */
  private static String basisNachfolger(String basis) {
    var naechster = (char) (basis.charAt(0) + 1);
    return String.valueOf(naechster).repeat(basis.length());
  }

  private static final class MutablerPunkt {
    final String label;
    final int ebene;
    final StringBuilder text = new StringBuilder();
    final List<MutablerPunkt> kinder = new ArrayList<>();

    MutablerPunkt(String label, int ebene) {
      this.label = label;
      this.ebene = ebene;
    }

    GliederungsPunkt zuRecord() {
      return new GliederungsPunkt(
          label, text.toString().strip(), kinder.stream().map(MutablerPunkt::zuRecord).toList());
    }
  }
}
