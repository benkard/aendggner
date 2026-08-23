// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Ein Stammgesetz, bestehend aus einer geordneten Liste von Einzelnormen und den (ebenfalls
 * geordneten) Gliederungseinheiten (Teil/Abschnitt/Unterabschnitt/…), die die Normen strukturieren.
 *
 * @param stand der Stand der Quelle, soweit sie einen angibt; {@code null} bei handgepflegtem
 *     Klartext. An ihm hängt die Auskunft, ob das Stammgesetz jünger ist als das Änderungsgesetz,
 *     das darauf angewandt wird.
 */
public record Gesetz(
    String jurabk,
    String langue,
    String kurzue,
    List<Norm> normen,
    List<Gliederung> gliederungen,
    @Nullable Stand stand) {

  public Gesetz {
    normen = List.copyOf(normen);
    gliederungen = List.copyOf(gliederungen);
  }

  public Gesetz(
      String jurabk,
      String langue,
      String kurzue,
      List<Norm> normen,
      List<Gliederung> gliederungen) {
    this(jurabk, langue, kurzue, normen, gliederungen, null);
  }

  public Gesetz(String jurabk, String langue, String kurzue, List<Norm> normen) {
    this(jurabk, langue, kurzue, normen, List.of(), null);
  }

  public Optional<Norm> norm(String enbez) {
    return normen.stream().filter(n -> n.enbez().equals(enbez)).findFirst();
  }

  public Gesetz mitNormen(List<Norm> neueNormen) {
    return new Gesetz(jurabk, langue, kurzue, neueNormen, gliederungen, stand);
  }

  public Gesetz mitGliederungen(List<Gliederung> neueGliederungen) {
    return new Gesetz(jurabk, langue, kurzue, normen, neueGliederungen, stand);
  }

  public Gesetz mitLangue(String neuerLangtitel) {
    return new Gesetz(jurabk, neuerLangtitel, kurzue, normen, gliederungen, stand);
  }
}
