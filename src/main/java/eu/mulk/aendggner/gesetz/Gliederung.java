package eu.mulk.aendggner.gesetz;

import org.jspecify.annotations.Nullable;

/**
 * Eine Gliederungseinheit (z.B. „Teil 2 — Anforderungen an zu errichtende Gebäude“ oder „2.
 * Abschnitt — Koordinierung und Früherkennung“).
 *
 * @param kennzahl die hierarchie-kodierende Gliederungskennzahl aus dem gii-XML (z.B. {@code
 *     020020} für Abschnitt 2 in Teil 2); {@code null} bei anderweitig konstruierten Einheiten.
 * @param bezeichnung die relative Bezeichnung („Teil 2“, „Abschnitt 2“).
 * @param titel die Überschrift der Einheit ({@code null} bei titellosen Einheiten).
 */
public record Gliederung(@Nullable String kennzahl, String bezeichnung, @Nullable String titel) {

  public Gliederung(String bezeichnung, @Nullable String titel) {
    this(null, bezeichnung, titel);
  }

  public String anzeigeText() {
    return titel == null ? bezeichnung : bezeichnung + " — " + titel;
  }

  public Gliederung mitTitel(@Nullable String neuerTitel) {
    return new Gliederung(kennzahl, bezeichnung, neuerTitel);
  }

  public Gliederung mitBezeichnung(String neueBezeichnung) {
    return new Gliederung(kennzahl, neueBezeichnung, titel);
  }
}
