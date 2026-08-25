// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.synopse;

import eu.mulk.aendggner.aenderung.Inkrafttreten;
import eu.mulk.aendggner.anwendung.BefehlAnwender.AnwendungsErgebnis;
import eu.mulk.aendggner.anwendung.BefehlAnwender.Status;
import eu.mulk.aendggner.gesetz.Gesetz;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Paart die Normen alter und neuer Fassung zu Synopse-Einträgen. */
public final class SynopseBuilder {

  private SynopseBuilder() {}

  /**
   * @param alt das Stammgesetz vor Anwendung der Befehle.
   * @param anwendung das Ergebnis des {@link eu.mulk.aendggner.anwendung.BefehlAnwender}.
   * @param parseWarnungen Warnungen aus dem Parsen des Änderungsgesetzes.
   * @param vollstaendig auch unveränderte Normen aufnehmen.
   */
  public static Synopse baue(
      Gesetz alt, AnwendungsErgebnis anwendung, List<String> parseWarnungen, boolean vollstaendig) {
    return baue(alt, anwendung, parseWarnungen, vollstaendig, null, null);
  }

  /**
   * @param inkrafttreten was der Schlussartikel des Änderungsgesetzes anordnet, sofern gelesen.
   * @param stichtag der Tag, dessen Fassung gezeigt wird; {@code null} = ohne Rücksicht darauf.
   */
  public static Synopse baue(
      Gesetz alt,
      AnwendungsErgebnis anwendung,
      List<String> parseWarnungen,
      boolean vollstaendig,
      @Nullable Inkrafttreten inkrafttreten,
      @Nullable LocalDate stichtag) {
    var neu = anwendung.neu();
    var eintraege = new ArrayList<Synopse.Eintrag>();

    for (var neuNorm : neu.normen()) {
      var altNorm = alt.norm(neuNorm.enbez()).orElse(null);

      Synopse.Aenderungsart art;
      if (altNorm == null) {
        art = Synopse.Aenderungsart.NEU;
      } else if (neuNorm.weggefallen() && !altNorm.weggefallen()) {
        art = Synopse.Aenderungsart.AUFGEHOBEN;
      } else if (!gleicherInhalt(altNorm, neuNorm)) {
        art = Synopse.Aenderungsart.GEAENDERT;
      } else {
        art = Synopse.Aenderungsart.UNVERAENDERT;
      }

      if (art == Synopse.Aenderungsart.UNVERAENDERT && !vollstaendig) {
        continue;
      }

      var ursachen =
          anwendung.protokoll().stream()
              .filter(a -> a.betroffeneEnbez().contains(neuNorm.enbez()))
              .toList();
      eintraege.add(new Synopse.Eintrag(altNorm, neuNorm, art, ursachen));
    }

    var manuell =
        anwendung.protokoll().stream().filter(a -> a.status() == Status.MANUELL_PRUEFEN).toList();
    var nichtInKraft =
        anwendung.protokoll().stream().filter(a -> a.status() == Status.NICHT_IN_KRAFT).toList();

    return new Synopse(
        alt,
        neu,
        eintraege,
        gliederungsAenderungen(alt, neu),
        manuell,
        parseWarnungen,
        inkrafttreten,
        stichtag,
        nichtInKraft,
        null);
  }

  /**
   * Paart die Gliederungseinheiten nach Kennzahl und sammelt die mit geänderter Überschrift. Neu
   * eingefügte Einheiten (ohne Kennzahl und ohne Alt-Pendant) erscheinen mit {@code alt == null}.
   */
  private static List<Synopse.GliederungsAenderung> gliederungsAenderungen(Gesetz alt, Gesetz neu) {
    var aenderungen = new ArrayList<Synopse.GliederungsAenderung>();
    for (var neuG : neu.gliederungen()) {
      if (neuG.kennzahl() == null) {
        if (!alt.gliederungen().contains(neuG)) {
          aenderungen.add(new Synopse.GliederungsAenderung(null, neuG));
        }
        continue;
      }
      alt.gliederungen().stream()
          .filter(a -> java.util.Objects.equals(a.kennzahl(), neuG.kennzahl()))
          .findFirst()
          .filter(a -> !java.util.Objects.equals(a.titel(), neuG.titel()))
          .ifPresent(a -> aenderungen.add(new Synopse.GliederungsAenderung(a, neuG)));
    }
    return aenderungen;
  }

  private static boolean gleicherInhalt(
      eu.mulk.aendggner.gesetz.Norm a, eu.mulk.aendggner.gesetz.Norm b) {
    return a.gesamtText().equals(b.gesamtText()) && java.util.Objects.equals(a.titel(), b.titel());
  }
}
