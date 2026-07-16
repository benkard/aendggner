package eu.mulk.aendggner.gesetz;

import java.util.List;
import java.util.Optional;

/**
 * Ein Stammgesetz, bestehend aus einer geordneten Liste von Einzelnormen und den (ebenfalls
 * geordneten) Gliederungseinheiten (Teil/Abschnitt/Unterabschnitt/…), die die Normen strukturieren.
 */
public record Gesetz(
    String jurabk, String langue, String kurzue, List<Norm> normen, List<Gliederung> gliederungen) {

  public Gesetz {
    normen = List.copyOf(normen);
    gliederungen = List.copyOf(gliederungen);
  }

  public Gesetz(String jurabk, String langue, String kurzue, List<Norm> normen) {
    this(jurabk, langue, kurzue, normen, List.of());
  }

  public Optional<Norm> norm(String enbez) {
    return normen.stream().filter(n -> n.enbez().equals(enbez)).findFirst();
  }

  public Gesetz mitNormen(List<Norm> neueNormen) {
    return new Gesetz(jurabk, langue, kurzue, neueNormen, gliederungen);
  }

  public Gesetz mitGliederungen(List<Gliederung> neueGliederungen) {
    return new Gesetz(jurabk, langue, kurzue, normen, neueGliederungen);
  }

  public Gesetz mitLangue(String neuerLangtitel) {
    return new Gesetz(jurabk, neuerLangtitel, kurzue, normen, gliederungen);
  }
}
