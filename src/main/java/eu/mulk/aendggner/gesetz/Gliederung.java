package eu.mulk.aendggner.gesetz;

import org.jspecify.annotations.Nullable;

/** Eine Gliederungseinheit (z.B. „2. Abschnitt — Koordinierung und Früherkennung“). */
public record Gliederung(String bezeichnung, @Nullable String titel) {

  public String anzeigeText() {
    return titel == null ? bezeichnung : bezeichnung + " — " + titel;
  }
}
