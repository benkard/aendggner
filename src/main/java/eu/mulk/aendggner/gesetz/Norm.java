package eu.mulk.aendggner.gesetz;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Eine Einzelnorm eines Gesetzes (z.B. „§ 5“ oder „Inhaltsübersicht“).
 *
 * @param enbez Einzelnormbezeichnung, z.B. „§ 5“, „§ 28a“, „Inhaltsübersicht“.
 * @param titel amtliche Überschrift (nullable).
 * @param gliederung übergeordnete Gliederungseinheit (nullable).
 * @param absaetze die Absätze; bei unstrukturierten Normen ein einzelner unnummerierter Absatz.
 * @param weggefallen ob die Norm aufgehoben ist.
 */
public record Norm(
    String enbez,
    @Nullable String titel,
    @Nullable Gliederung gliederung,
    List<Absatz> absaetze,
    boolean weggefallen) {

  public Norm {
    absaetze = List.copyOf(absaetze);
  }

  public Norm mitAbsaetzen(List<Absatz> neueAbsaetze) {
    return new Norm(enbez, titel, gliederung, neueAbsaetze, weggefallen);
  }

  public Norm mitTitel(@Nullable String neuerTitel) {
    return new Norm(enbez, neuerTitel, gliederung, absaetze, weggefallen);
  }

  public Norm alsWeggefallen() {
    return new Norm(enbez, titel, gliederung, List.of(new Absatz(null, "(weggefallen)")), true);
  }

  /** Gesamttext der Norm (alle Absätze, durch Leerzeilen getrennt). */
  public String gesamtText() {
    var sb = new StringBuilder();
    for (var absatz : absaetze) {
      if (sb.length() > 0) {
        sb.append("\n\n");
      }
      sb.append(absatz.anzeigeText());
    }
    return sb.toString();
  }
}
