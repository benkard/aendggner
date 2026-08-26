// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Hält die Inhaltsübersicht gegen den Bestand der Normen — aber nur dort, wo dieser Lauf ihn
 * angerührt hat.
 *
 * <p>Ein Änderungsgesetz, das Paragraphen einfügt, aufhebt, umnummeriert oder ihre Überschriften
 * neu fasst, muss die Angaben der Inhaltsübersicht eigens mitändern; das Handbuch der
 * Rechtsförmlichkeit verlangt dafür eigene Befehle. Bleiben sie aus oder greifen sie nicht, so
 * bleibt die Übersicht hinter dem Text zurück, ohne dass ein einziger Befehl liegenbliebe — ein
 * Fehler, den keine Zahl des Protokolls anzeigt.
 *
 * <p>Geprüft wird deshalb <em>der Unterschied</em>: allein die Normen, deren Bezeichnung oder
 * Überschrift dieser Lauf verändert hat. Die Übersicht einer fremden Quelle mag im Übrigen ungenau
 * sein — das ist ein Befund über die Quelle und nicht über den Lauf, und ihn hier zu rügen hieße,
 * den Leser mit Fremdem zu behelligen.
 *
 * <p>Geheilt wird nichts. Die Übersicht ist eine Norm wie jede andere; sie ohne Befehl
 * fortzuschreiben hieße, Recht zu erfinden.
 */
public final class InhaltsuebersichtsProbe {

  private static final String ENBEZ = "Inhaltsübersicht";

  /** Eine Norm, die in der Übersicht ein Gegenstück haben kann: der Paragraph, der Artikel. */
  private static final Pattern EIGENE_NORM = Pattern.compile("^(?:§|Art\\.)\\s*\\d+[a-z]?$");

  /** Eine Angabezeile im Zeilenmodell der Übersicht: „§ 5 | Titel“. */
  private static final Pattern ANGABE =
      Pattern.compile("^\\s*((?:§|Art\\.)\\s*\\d+[a-z]?)\\s*\\|\\s*(.*)$");

  private InhaltsuebersichtsProbe() {}

  /**
   * Die Rügen, die der Vergleich ergibt; leer, wenn alles stimmt oder das Gesetz keine
   * Inhaltsübersicht führt.
   */
  public static List<String> pruefe(Gesetz alt, Gesetz neu) {
    var uebersicht = neu.norm(ENBEZ).orElse(null);
    if (uebersicht == null) {
      return List.of();
    }
    var angaben = angaben(uebersicht);
    var vorher = titelJeNorm(alt);
    var ruegen = new ArrayList<String>();
    for (var norm : neu.normen()) {
      if (norm.weggefallen() || !EIGENE_NORM.matcher(norm.enbez()).matches()) {
        // Die Übersicht führt Paragraphen; Anlagen, ihre Nummern und die Übersicht selbst stehen
        // nicht in ihr. Sie hier zu vermissen hieße, ihr etwas abzuverlangen, was sie nie leistet.
        continue;
      }
      var altTitel = vorher.get(norm.enbez());
      boolean neuHinzu = !vorher.containsKey(norm.enbez());
      boolean titelGeaendert = altTitel != null && !gleich(altTitel, norm.titel());
      if (!neuHinzu && !titelGeaendert) {
        continue;
      }
      if (!angaben.containsKey(norm.enbez())) {
        ruegen.add(
            "Die Inhaltsübersicht führt keine Angabe zu "
                + norm.enbez()
                + ", obgleich dieser Lauf die Norm "
                + (neuHinzu ? "eingefügt" : "geändert")
                + " hat. Ein Angabe-Befehl, der das nachholte, ist nicht ersichtlich.");
        continue;
      }
      if (!gleich(angaben.get(norm.enbez()), norm.titel())) {
        // Gesagt wird der Befund, nicht seine Ursache: Es kann am fehlenden Angabe-Befehl liegen —
        // es kommt aber auch vor, dass die amtliche Fassung selbst beide verschieden führt (so das
        // GEG bei § 71k: „Erdgas“ in der Übersicht, „Gas“ in der Überschrift).
        ruegen.add(
            "Die Angabe zu "
                + norm.enbez()
                + " und ihre Überschrift gehen auseinander: „"
                + angaben.get(norm.enbez())
                + "“ gegen „"
                + (norm.titel() == null ? "" : norm.titel())
                + "“. Dieser Lauf hat die Überschrift geändert, die Angabe nicht.");
      }
    }
    for (var enbez : vorher.keySet()) {
      if (EIGENE_NORM.matcher(enbez).matches()
          && neu.norm(enbez).isEmpty()
          && angaben.containsKey(enbez)) {
        ruegen.add(
            "Die Inhaltsübersicht führt weiterhin eine Angabe zu "
                + enbez
                + ", obgleich dieser Lauf die Norm beseitigt hat.");
      }
    }
    return List.copyOf(ruegen);
  }

  /** Die Angaben der Übersicht, Bezeichnung auf Titel. */
  private static Map<String, String> angaben(Norm uebersicht) {
    var angaben = new LinkedHashMap<String, String>();
    for (var absatz : uebersicht.absaetze()) {
      for (var zeile : absatz.text().lines().toList()) {
        var m = ANGABE.matcher(zeile);
        if (m.matches()) {
          angaben.putIfAbsent(m.group(1).replaceAll("\\s+", " ").strip(), m.group(2).strip());
        }
      }
    }
    return angaben;
  }

  private static Map<String, String> titelJeNorm(Gesetz gesetz) {
    var titel = new LinkedHashMap<String, String>();
    for (var norm : gesetz.normen()) {
      if (!norm.enbez().equals(ENBEZ)) {
        titel.put(norm.enbez(), norm.titel() == null ? "" : norm.titel());
      }
    }
    return titel;
  }

  private static boolean gleich(@Nullable String a, @Nullable String b) {
    return normiere(a).equals(normiere(b));
  }

  private static String normiere(@Nullable String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").strip();
  }
}
