// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung;

import eu.mulk.aendggner.aenderung.parse.DeutschesDatum;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Was der Schlussartikel eines Änderungsgesetzes über sein Inkrafttreten sagt.
 *
 * <p>Ein Änderungsgesetz tritt nicht notwendig auf einen Schlag in Kraft. Das UWG-Änderungsgesetz
 * von 2026 etwa lässt seinen Artikel 1 Nummer 2 Buchstabe c am 19. Juni 2026 wirksam werden und
 * alles Übrige erst am 27. September 2026 — wer alle Befehle zugleich anwendet, erhält eine
 * Fassung, die an keinem einzigen Tag gegolten hat. Deshalb trägt jeder Befehl seinen Stichtag.
 *
 * <p>Erfunden wird nichts: Nennt der Wortlaut kein bestimmtes Datum („am Tag nach der Verkündung“ —
 * der Verkündungstag steht nicht im Gesetzestext), so bleibt {@link Regel#datum()} leer und es
 * bleibt beim Wortlaut.
 */
public record Inkrafttreten(List<Regel> regeln) {

  /**
   * Eine Inkrafttretens-Anordnung.
   *
   * @param datum der Tag, an dem sie wirkt; {@code null}, wenn der Wortlaut keinen nennt.
   * @param wortlaut der Satz, aus dem sie stammt — er bleibt maßgeblich.
   * @param giltFuer die Punkte, die sie erfasst; leer heißt: das ganze Gesetz (Grundregel).
   */
  public record Regel(@Nullable LocalDate datum, String wortlaut, List<Punktbezug> giltFuer) {

    public boolean istGrundregel() {
      return giltFuer.isEmpty();
    }

    /** „am 1. Oktober 2024“ bzw. der Wortlaut, wo kein Datum bestimmbar ist. */
    public String anzeige() {
      return datum == null ? wortlaut : DeutschesDatum.schreibe(datum);
    }
  }

  /**
   * Ein Verweis des Schlussartikels auf einen Punkt des eigenen Gesetzes: „Artikel 1 Nummer 11
   * Buchstabe a Doppelbuchstabe bb“.
   *
   * @param artikel die Artikel- bzw. Paragraphennummer, wie {@link Provenienz#artikel()} sie führt.
   * @param pfadPraefix der Anfang des Gliederungspfades („11. a) bb)“); leer heißt: der ganze
   *     Artikel.
   */
  public record Punktbezug(String artikel, String pfadPraefix) {

    boolean trifft(Provenienz provenienz) {
      if (!artikel.equals(provenienz.artikel())) {
        return false;
      }
      var pfad = provenienz.gliederungsPfad();
      return pfadPraefix.isEmpty()
          || pfad.equals(pfadPraefix)
          || pfad.startsWith(pfadPraefix + " ");
    }

    /** Je länger der Pfad, desto besonderer der Bezug. */
    int besonderheit() {
      return pfadPraefix.length();
    }
  }

  /**
   * Die Regel, die für diesen Befehl gilt: die besonderste, die ihn erfasst, sonst die Grundregel.
   */
  public Optional<Regel> fuer(Provenienz provenienz) {
    Regel beste = null;
    int besteBesonderheit = -1;
    for (var regel : regeln) {
      for (var bezug : regel.giltFuer()) {
        if (bezug.trifft(provenienz) && bezug.besonderheit() > besteBesonderheit) {
          beste = regel;
          besteBesonderheit = bezug.besonderheit();
        }
      }
    }
    return beste != null ? Optional.of(beste) : grundregel();
  }

  public Optional<Regel> grundregel() {
    return regeln.stream().filter(Regel::istGrundregel).findFirst();
  }

  /** Die Sonderregeln, in der Reihenfolge des Schlussartikels. */
  public List<Regel> sonderregeln() {
    var sonder = new ArrayList<Regel>();
    for (var regel : regeln) {
      if (!regel.istGrundregel()) {
        sonder.add(regel);
      }
    }
    return List.copyOf(sonder);
  }

  /**
   * Wahr, wenn das Gesetz an mehr als einem Tag wirksam wird — nur dann ist die auf einen Schlag
   * gerechnete Fassung eine, die es so nie gab.
   */
  public boolean gestaffelt() {
    return !sonderregeln().isEmpty();
  }
}
