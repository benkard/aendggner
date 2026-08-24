// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Ausgeschriebene Daten, wie Gesetzestexte sie führen: „19. Juni 2020“.
 *
 * <p>Gebraucht an zwei Stellen, die nichts miteinander zu tun haben — die Altersrüge liest das
 * Datum aus dem Einleitungssatz des Änderungsgesetzes, der {@link InkrafttretensLeser} liest es aus
 * dessen Schlussartikel. Damit beide dieselbe Schreibweise verstehen, steht die Erkennung hier.
 */
public final class DeutschesDatum {

  /** Tag, Monatsname, Jahr — als Gruppen 1 bis 3. Ohne Anker; zum Einbetten gedacht. */
  public static final String MUSTER = "(\\d{1,2})\\. (\\p{Lu}\\p{L}+) (\\d{4})";

  private static final DateTimeFormatter AUSGABE =
      DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN);

  private static final List<String> MONATSNAMEN =
      List.of(
          "Januar",
          "Februar",
          "März",
          "April",
          "Mai",
          "Juni",
          "Juli",
          "August",
          "September",
          "Oktober",
          "November",
          "Dezember");

  private DeutschesDatum() {}

  /** „19“, „Juni“, „2020“ → 2020-06-19; {@code null} bei unbekanntem Monat oder unmöglichem Tag. */
  public static @Nullable LocalDate lies(String tag, String monatsname, String jahr) {
    int monat = MONATSNAMEN.indexOf(monatsname) + 1;
    if (monat < 1) {
      return null;
    }
    try {
      return LocalDate.of(Integer.parseInt(jahr), monat, Integer.parseInt(tag));
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** 2020-06-19 → „19. Juni 2020“. */
  public static String schreibe(LocalDate datum) {
    return datum.format(AUSGABE);
  }
}
