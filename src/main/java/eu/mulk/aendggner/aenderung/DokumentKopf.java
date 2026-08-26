// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung;

import eu.mulk.aendggner.aenderung.parse.DeutschesDatum;
import java.time.LocalDate;
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
 * @param ausfertigung das Datum, das die Zeile „Vom …“ nennt, oder {@code null}. Entwürfe tragen
 *     keines, und ein Sammelheft mit mehreren Verkündungen trägt mehrere — dann bleibt es
 *     gleichfalls leer, denn welche Verkündung gemeint ist, steht dort nicht fest. Geraten wird
 *     nicht.
 */
public record DokumentKopf(
    DokumentArt art,
    @Nullable String eigeneDrucksache,
    List<String> bezugsDrucksachen,
    String titel,
    @Nullable LocalDate ausfertigung) {

  public DokumentKopf {
    bezugsDrucksachen = List.copyOf(bezugsDrucksachen);
  }

  public DokumentKopf(
      DokumentArt art,
      @Nullable String eigeneDrucksache,
      List<String> bezugsDrucksachen,
      String titel) {
    this(art, eigeneDrucksache, bezugsDrucksachen, titel, null);
  }

  /**
   * Die Bezeichnung für die Quellenzeile der Synopse, z.B. „Änderungsantrag Drs. 19/10365“ oder
   * „Änderungsgesetz vom 22. April 2026“.
   *
   * <p>Sie ist zugleich der Schlüssel, an dem die Fassung wiedererkennt, welches Heft sie schon
   * trägt (siehe {@link eu.mulk.aendggner.gesetz.Fortschreibung}). Deshalb steht hier, was das
   * Dokument von sich selbst sagt — Drucksachennummer oder Ausfertigungsdatum —, und nicht der
   * Dateiname, den jeder anders schreibt.
   */
  public String anzeigeName() {
    if (eigeneDrucksache != null) {
      return art.anzeigeName() + " Drs. " + eigeneDrucksache;
    }
    return ausfertigung == null
        ? art.anzeigeName()
        : art.anzeigeName() + " vom " + DeutschesDatum.schreibe(ausfertigung);
  }
}
