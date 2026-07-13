package eu.mulk.aendggner.gesetz;

import java.util.List;
import java.util.Optional;

/** Ein Stammgesetz, bestehend aus einer geordneten Liste von Einzelnormen. */
public record Gesetz(String jurabk, String langue, String kurzue, List<Norm> normen) {

  public Gesetz {
    normen = List.copyOf(normen);
  }

  public Optional<Norm> norm(String enbez) {
    return normen.stream().filter(n -> n.enbez().equals(enbez)).findFirst();
  }

  public Gesetz mitNormen(List<Norm> neueNormen) {
    return new Gesetz(jurabk, langue, kurzue, neueNormen);
  }
}
