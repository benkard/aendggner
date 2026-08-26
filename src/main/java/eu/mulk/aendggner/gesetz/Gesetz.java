// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz;

import java.time.LocalDate;
import java.util.ArrayList;
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
 * @param fortschreibungen die Änderungshefte, die auf diesen Wortlaut bereits angewandt worden sind
 *     — leer bei jeder Fassung, die aus einer fremden Quelle stammt. Gefüllt wird die Liste erst,
 *     wenn das Erzeugnis selbst fortschreibt; sie überdauert die Kette, weil der kanonische
 *     Klartext sie mitführt (siehe {@link Fortschreibung}).
 */
public record Gesetz(
    String jurabk,
    String langue,
    String kurzue,
    List<Norm> normen,
    List<Gliederung> gliederungen,
    @Nullable Stand stand,
    List<Fortschreibung> fortschreibungen) {

  public Gesetz {
    normen = List.copyOf(normen);
    gliederungen = List.copyOf(gliederungen);
    fortschreibungen = List.copyOf(fortschreibungen);
  }

  public Gesetz(
      String jurabk,
      String langue,
      String kurzue,
      List<Norm> normen,
      List<Gliederung> gliederungen,
      @Nullable Stand stand) {
    this(jurabk, langue, kurzue, normen, gliederungen, stand, List.of());
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
    return new Gesetz(jurabk, langue, kurzue, neueNormen, gliederungen, stand, fortschreibungen);
  }

  public Gesetz mitGliederungen(List<Gliederung> neueGliederungen) {
    return new Gesetz(jurabk, langue, kurzue, normen, neueGliederungen, stand, fortschreibungen);
  }

  public Gesetz mitLangue(String neuerLangtitel) {
    return new Gesetz(
        jurabk, neuerLangtitel, kurzue, normen, gliederungen, stand, fortschreibungen);
  }

  /** Vermerkt ein angewandtes Heft; die Reihenfolge ist die der Anwendung. */
  public Gesetz mitFortschreibung(Fortschreibung heft) {
    var erweitert = new ArrayList<>(fortschreibungen);
    erweitert.add(heft);
    return new Gesetz(jurabk, langue, kurzue, normen, gliederungen, stand, erweitert);
  }

  /** Ob dieses Heft auf den Wortlaut bereits angewandt worden ist. */
  public boolean traegt(Fortschreibung heft) {
    return fortschreibungen.stream().anyMatch(f -> f.bezeichnung().equals(heft.bezeichnung()));
  }

  /**
   * Wie jung der Wortlaut wirklich ist: das späteste Datum, das entweder die Standangabe der Quelle
   * oder eines der angewandten Hefte nennt. An ihm hängt die Altersrüge — und zwar auch in der
   * Kette, denn wer ein Heft anwendet, macht den Wortlaut so jung wie dieses Heft.
   */
  public @Nullable LocalDate wortlautStand() {
    LocalDate juengste = stand != null ? stand.juengsteAenderung() : null;
    for (var heft : fortschreibungen) {
      if (heft.datum() != null && (juengste == null || heft.datum().isAfter(juengste))) {
        juengste = heft.datum();
      }
    }
    return juengste;
  }
}
