// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.synopse;

import eu.mulk.aendggner.aenderung.Inkrafttreten;
import eu.mulk.aendggner.anwendung.BefehlAnwender.AngewandteAenderung;
import eu.mulk.aendggner.anwendung.Nachfassungsabgleich;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Gliederung;
import eu.mulk.aendggner.gesetz.Norm;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Eine Gegenüberstellung alter und neuer Fassung, normweise.
 *
 * @param inkrafttreten was der Schlussartikel des Änderungsgesetzes anordnet; {@code null}, wo
 *     keiner gelesen werden konnte.
 * @param stichtag der Tag, dessen Fassung gezeigt wird; {@code null} heißt: alle Befehle, ohne
 *     Rücksicht auf das Inkrafttreten.
 * @param nichtInKraft die Befehle, die am Stichtag noch nicht galten und deshalb unterblieben.
 * @param abgleich der normweise Vergleich mit einer amtlichen Nachfassung, soweit eine angegeben
 *     wurde; {@code null} sonst.
 */
public record Synopse(
    Gesetz alt,
    Gesetz neu,
    List<Eintrag> eintraege,
    List<GliederungsAenderung> gliederungsAenderungen,
    List<AngewandteAenderung> manuellZuPruefen,
    List<String> warnungen,
    @Nullable Inkrafttreten inkrafttreten,
    @Nullable LocalDate stichtag,
    List<AngewandteAenderung> nichtInKraft,
    @Nullable Nachfassungsabgleich abgleich) {

  public Synopse(
      Gesetz alt,
      Gesetz neu,
      List<Eintrag> eintraege,
      List<GliederungsAenderung> gliederungsAenderungen,
      List<AngewandteAenderung> manuellZuPruefen,
      List<String> warnungen) {
    this(
        alt,
        neu,
        eintraege,
        gliederungsAenderungen,
        manuellZuPruefen,
        warnungen,
        null,
        null,
        List.of(),
        null);
  }

  /**
   * Die Synopse mit dem Abgleich gegen eine amtliche Nachfassung. Er tritt erst nach dem Aufbau
   * hinzu, weil er die fertige neue Fassung voraussetzt.
   */
  public Synopse mitAbgleich(@Nullable Nachfassungsabgleich neuerAbgleich) {
    return new Synopse(
        alt,
        neu,
        eintraege,
        gliederungsAenderungen,
        manuellZuPruefen,
        warnungen,
        inkrafttreten,
        stichtag,
        nichtInKraft,
        neuerAbgleich);
  }

  /** Eine geänderte Gliederungs-Überschrift (Teil/Abschnitt/…); {@code alt == null} bei neuen. */
  public record GliederungsAenderung(@Nullable Gliederung alt, Gliederung neu) {}

  public enum Aenderungsart {
    UNVERAENDERT,
    GEAENDERT,
    NEU,
    AUFGEHOBEN
  }

  /**
   * @param altNorm die Norm in der alten Fassung; {@code null} bei neu eingefügten Normen.
   * @param neuNorm die Norm in der neuen Fassung.
   */
  public record Eintrag(
      @Nullable Norm altNorm, Norm neuNorm, Aenderungsart art, List<AngewandteAenderung> ursachen) {

    public String enbez() {
      return neuNorm.enbez();
    }
  }
}
