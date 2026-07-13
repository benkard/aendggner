package eu.mulk.aendggner.synopse;

import eu.mulk.aendggner.anwendung.BefehlAnwender.AngewandteAenderung;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Eine Gegenüberstellung alter und neuer Fassung, normweise. */
public record Synopse(
    Gesetz alt,
    Gesetz neu,
    List<Eintrag> eintraege,
    List<AngewandteAenderung> manuellZuPruefen,
    List<String> warnungen) {

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
