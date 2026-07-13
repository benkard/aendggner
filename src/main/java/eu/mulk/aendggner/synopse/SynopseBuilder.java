package eu.mulk.aendggner.synopse;

import eu.mulk.aendggner.anwendung.BefehlAnwender.AnwendungsErgebnis;
import eu.mulk.aendggner.anwendung.BefehlAnwender.Status;
import eu.mulk.aendggner.gesetz.Gesetz;
import java.util.ArrayList;
import java.util.List;

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

    return new Synopse(alt, neu, eintraege, gliederungsAenderungen(alt, neu), manuell, parseWarnungen);
  }

  /** Paart die Gliederungseinheiten nach Kennzahl und sammelt die mit geänderter Überschrift. */
  private static List<Synopse.GliederungsAenderung> gliederungsAenderungen(Gesetz alt, Gesetz neu) {
    var aenderungen = new ArrayList<Synopse.GliederungsAenderung>();
    for (var neuG : neu.gliederungen()) {
      alt.gliederungen().stream()
          .filter(a -> java.util.Objects.equals(a.kennzahl(), neuG.kennzahl()))
          .filter(a -> a.kennzahl() != null)
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
