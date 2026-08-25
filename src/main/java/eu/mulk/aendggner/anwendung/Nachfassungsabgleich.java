// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.ArrayList;
import java.util.List;

/**
 * Stellt die errechnete Fassung normweise gegen die amtliche Nachfassung.
 *
 * <p>Das ist der Maßstab, an dem jeder Belegfall dieses Erzeugnisses hängt: Nicht daran, dass
 * sämtliche Befehle angewandt wurden, entscheidet sich seine Richtigkeit, sondern daran, dass der
 * Wortlaut hinterher derselbe ist, den das Gesetzblatt und das Landesportal führen. Bis zu dieser
 * Welle stand die Prüfung allein im Testcode; wer ein neues Land erschloss, musste sie ein zweites
 * Mal schreiben.
 *
 * <p>Verglichen wird nach Normalisierung des Leerraums. Wie ein Portal umbricht, ist kein
 * Rechtsinhalt — wohl aber jedes Wort und jedes Satzzeichen.
 */
public record Nachfassungsabgleich(
    List<String> fehlende,
    List<String> ueberzaehlige,
    List<Abweichung> abweichungen,
    int gleich,
    int geprueft) {

  /**
   * Eine Norm, die es beiderseits gibt und deren Wortlaut auseinanderfällt.
   *
   * @param soll der Wortlaut der amtlichen Nachfassung, normalisiert.
   * @param ist der errechnete Wortlaut, normalisiert.
   */
  public record Abweichung(String enbez, String soll, String ist) {}

  public Nachfassungsabgleich {
    fehlende = List.copyOf(fehlende);
    ueberzaehlige = List.copyOf(ueberzaehlige);
    abweichungen = List.copyOf(abweichungen);
  }

  /**
   * @param soll die amtliche Nachfassung.
   * @param ist die vom Erzeugnis fortgeschriebene Fassung.
   */
  public static Nachfassungsabgleich vergleiche(Gesetz soll, Gesetz ist) {
    var fehlende = new ArrayList<String>();
    var abweichungen = new ArrayList<Abweichung>();
    int gleich = 0;

    for (var normSoll : soll.normen()) {
      var normIst = ist.norm(normSoll.enbez());
      if (normIst.isEmpty()) {
        fehlende.add(normSoll.enbez());
        continue;
      }
      var wortlautSoll = wortlaut(normSoll);
      var wortlautIst = wortlaut(normIst.orElseThrow());
      if (wortlautSoll.equals(wortlautIst)) {
        gleich++;
      } else {
        abweichungen.add(new Abweichung(normSoll.enbez(), wortlautSoll, wortlautIst));
      }
    }

    var ueberzaehlige =
        ist.normen().stream().map(Norm::enbez).filter(enbez -> soll.norm(enbez).isEmpty()).toList();

    return new Nachfassungsabgleich(
        fehlende, ueberzaehlige, abweichungen, gleich, soll.normen().size());
  }

  /** Geht der Abgleich auf, so ist die errechnete Fassung die amtliche. */
  public boolean gehtAuf() {
    return fehlende.isEmpty() && ueberzaehlige.isEmpty() && abweichungen.isEmpty();
  }

  /** „171 von 171 Normen gleich“ — die Zahl, die jeder Belegfall führt. */
  public String kurzbericht() {
    var sb = new StringBuilder("%d von %d Normen gleich".formatted(gleich, geprueft));
    if (!fehlende.isEmpty()) {
      sb.append("; %d fehlen".formatted(fehlende.size()));
    }
    if (!ueberzaehlige.isEmpty()) {
      sb.append("; %d überzählig".formatted(ueberzaehlige.size()));
    }
    return sb.toString();
  }

  /**
   * Die Überschrift gehört zum Wortlaut: Ein Befehl, der sie neu fasst, ändert die Norm ebenso wie
   * einer, der ihren Text ändert, und eine Prüfung, die sie überginge, ließe gerade die Befehle
   * ungeprüft, die auf Überschriften zielen.
   */
  private static String wortlaut(Norm norm) {
    var titel = norm.titel() == null ? "" : norm.titel() + "\n";
    return (titel + norm.gesamtText()).replaceAll("\\s+", " ").strip();
  }
}
