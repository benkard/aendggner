// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Die Probe schlägt an, wo ein Lauf den Bestand angerührt und die Inhaltsübersicht zurückgelassen
 * hat — und nur dort. Was die Quelle von sich aus ungenau führt, geht sie nichts an.
 */
class InhaltsuebersichtsProbeTest {

  private static Gesetz gesetz(String uebersicht, Norm... normen) {
    var alle = new ArrayList<Norm>();
    alle.add(
        new Norm("Inhaltsübersicht", null, null, List.of(new Absatz(null, uebersicht)), false));
    alle.addAll(List.of(normen));
    return new Gesetz("TestG", "Testgesetz", null, alle);
  }

  private static Norm norm(String enbez, String titel) {
    return new Norm(enbez, titel, null, List.of(new Absatz(null, "Text.")), false);
  }

  private static final String UEBERSICHT = "§ 1 | Zweck\n§ 2 | Begriffe";

  @Test
  void ruegtDieNichtNachgefuehrteAngabe() {
    var alt = gesetz(UEBERSICHT, norm("§ 1", "Zweck"), norm("§ 2", "Begriffe"));
    var neu = gesetz(UEBERSICHT, norm("§ 1", "Zweck und Ziel"), norm("§ 2", "Begriffe"));

    assertThat(InhaltsuebersichtsProbe.pruefe(alt, neu))
        .singleElement()
        .asString()
        .contains("§ 1")
        .contains("gehen auseinander");
  }

  @Test
  void schweigtWennDieAngabeMitgefuehrtWurde() {
    var alt = gesetz(UEBERSICHT, norm("§ 1", "Zweck"), norm("§ 2", "Begriffe"));
    var neu =
        gesetz(
            "§ 1 | Zweck und Ziel\n§ 2 | Begriffe",
            norm("§ 1", "Zweck und Ziel"),
            norm("§ 2", "Begriffe"));

    assertThat(InhaltsuebersichtsProbe.pruefe(alt, neu)).isEmpty();
  }

  @Test
  void ruegtDenEingefuegtenParagraphenOhneAngabe() {
    var alt = gesetz(UEBERSICHT, norm("§ 1", "Zweck"), norm("§ 2", "Begriffe"));
    var neu =
        gesetz(UEBERSICHT, norm("§ 1", "Zweck"), norm("§ 1a", "Neues"), norm("§ 2", "Begriffe"));

    assertThat(InhaltsuebersichtsProbe.pruefe(alt, neu))
        .singleElement()
        .asString()
        .contains("keine Angabe zu § 1a");
  }

  @Test
  void ruegtDieAngabeZurBeseitigtenNorm() {
    var alt = gesetz(UEBERSICHT, norm("§ 1", "Zweck"), norm("§ 2", "Begriffe"));
    var neu = gesetz(UEBERSICHT, norm("§ 1", "Zweck"));

    assertThat(InhaltsuebersichtsProbe.pruefe(alt, neu))
        .singleElement()
        .asString()
        .contains("weiterhin eine Angabe zu § 2");
  }

  /**
   * Eine Anlage steht nicht in der Inhaltsübersicht; sie dort zu vermissen hieße, ihr etwas
   * abzuverlangen, was sie nie leistet.
   */
  @Test
  void schweigtZurGeaendertenAnlage() {
    var alt = gesetz(UEBERSICHT, norm("§ 1", "Zweck"), norm("Anlage 8", "Muster"));
    var neu = gesetz(UEBERSICHT, norm("§ 1", "Zweck"), norm("Anlage 8", "Anderes Muster"));

    assertThat(InhaltsuebersichtsProbe.pruefe(alt, neu)).isEmpty();
  }

  /** Ohne Inhaltsübersicht gibt es nichts zu prüfen — und nichts zu rügen. */
  @Test
  void schweigtOhneInhaltsuebersicht() {
    var ohne = new Gesetz("TestG", "Testgesetz", null, List.of(norm("§ 1", "Zweck")));
    var geaendert = new Gesetz("TestG", "Testgesetz", null, List.of(norm("§ 1", "Zweck und Ziel")));

    assertThat(InhaltsuebersichtsProbe.pruefe(ohne, geaendert)).isEmpty();
  }
}
