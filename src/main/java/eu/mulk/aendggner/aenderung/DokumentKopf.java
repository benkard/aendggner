package eu.mulk.aendggner.aenderung;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Was sich über ein Änderungsdokument sagen lässt, ohne seine Befehle zu lesen: seine Art, seine
 * eigene Drucksachennummer und die Nummern der Drucksachen, auf die es sich bezieht.
 *
 * <p>Die Drucksachennummern stiften die Verbindung zwischen den Dokumenten eines Verfahrens: Ein
 * Änderungsantrag nennt in {@link #bezugsDrucksachen()} den Entwurf, den er ändern will, und findet
 * ihn darüber unter den übrigen eingespeisten Dateien wieder — unabhängig davon, in welcher
 * Reihenfolge sie auf der Kommandozeile stehen.
 *
 * @param art die erkannte Dokumentart.
 * @param eigeneDrucksache die Drucksachennummer des Dokuments selbst („19/10365“), oder {@code
 *     null} bei Dokumenten ohne Drucksachenkopf (Gesetzblätter, Referentenentwürfe).
 * @param bezugsDrucksachen die Nummern der Drucksachen, auf die sich das Dokument bezieht.
 * @param titel eine kurze Bezeichnung für Quellen- und Warnzeilen.
 */
public record DokumentKopf(
    DokumentArt art,
    @Nullable String eigeneDrucksache,
    List<String> bezugsDrucksachen,
    String titel) {

  public DokumentKopf {
    bezugsDrucksachen = List.copyOf(bezugsDrucksachen);
  }

  /** Die Bezeichnung für die Quellenzeile der Synopse, z.B. „Änderungsantrag Drs. 19/10365“. */
  public String anzeigeName() {
    return eigeneDrucksache == null
        ? art.anzeigeName()
        : art.anzeigeName() + " Drs. " + eigeneDrucksache;
  }
}
