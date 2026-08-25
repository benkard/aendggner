// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz.land;

import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Gliederung;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Schreibt ein {@link Gesetz} als kanonischen Klartext — die Umkehrung des {@link
 * LandesRechtTextParser}. Damit gibt das Erzeugnis die Fassung heraus, die es errechnet hat: Sie
 * lässt sich gegen die amtliche Nachfassung halten und als Stammgesetz eines weiteren
 * Änderungsheftes wieder einspeisen.
 *
 * <p>Maßgeblich ist, dass hier nichts erfunden wird. Ausgegeben wird allein, was das Modell trägt;
 * wo es nichts trägt, steht nichts. Der Beleg dafür ist der Rundlauf: {@code
 * parse(ausgeben(parse(t)))} muss dasselbe Gesetz ergeben wie {@code parse(t)}.
 *
 * <p>Zwei Stellen sind dem Format selbst nicht abzugewinnen und deshalb hier benannt:
 *
 * <ol>
 *   <li>Der Normkopf einer <em>Anlage</em> nimmt keinen Titel auf ({@code ANLAGEN_KOPF} verlangt
 *       das Zeilenende hinter der Nummer). Trägt eine Anlage gleichwohl einen Titel, so tritt er
 *       auf die Folgezeile und wird beim Wiedereinlesen Teil ihres Wortlauts.
 *   <li>Eine Gliederungseinheit, die keiner Norm vorangeht und hinter der letzten Norm des
 *       Textteils steht, hat im Klartext keinen Ort mehr als eben diesen; sie wird dort ausgegeben.
 * </ol>
 */
public final class LandesRechtTextAusgeber {

  /** Der Normkopf einer Anlage bzw. eines Anhangs, mit oder ohne Nummer. */
  private static final Pattern ANLAGE = Pattern.compile("^(?:Anlage|Anhang)(?:\\s+\\d+[a-z]?)?$");

  /**
   * Die Nummer einer Anlage als eigene Norm („Anlage Nummer 6“, siehe {@link
   * LandesRechtTextParser}). Ausgegeben wird nur der hintere Teil, denn die vorangehende
   * Anlagen-Norm stellt den Bezug schon her.
   */
  private static final Pattern ANLAGEN_NUMMER =
      Pattern.compile("^(?:Anlage|Anhang)(?:\\s+\\d+[a-z]?)?\\s+((?:Nummer|Nr\\.)\\s+\\d+[a-z]?)$");

  private LandesRechtTextAusgeber() {}

  public static String ausgeben(Gesetz gesetz) {
    var sb = new StringBuilder();

    sb.append(gesetz.langue()).append('\n');
    // Die Abkürzungszeile nur, wenn die Quelle eine geführt hat: Fehlt sie, so hält das Modell den
    // Langtitel als Abkürzung, und eine Zeile „(<Langtitel>)“ wäre eine Erfindung.
    if (!gesetz.jurabk().equals(gesetz.langue())) {
      sb.append('(')
          .append(
              gesetz.kurzue() != null ? gesetz.kurzue() + " – " + gesetz.jurabk() : gesetz.jurabk())
          .append(")\n");
    }
    if (gesetz.stand() != null) {
      sb.append("Stand: ").append(gesetz.stand().kommentar()).append('\n');
    }

    var gliederungen = gesetz.gliederungen();
    int gliederungsZeiger = 0;
    Gliederung letzte = null;
    boolean imAnlagenteil = false;

    for (var norm : gesetz.normen()) {
      if (!imAnlagenteil && istAnlagenNorm(norm)) {
        // Vor dem Anlagenteil ist der letzte Ort, an dem eine Gliederungs-Überschrift noch als
        // solche gelesen wird: innerhalb der Anlagen gliedert keine Zeile mehr das Gesetz.
        gliederungsZeiger = schreibeGliederungen(sb, gliederungen, gliederungsZeiger, null);
        imAnlagenteil = true;
      }
      if (!imAnlagenteil && norm.gliederung() != null && !norm.gliederung().equals(letzte)) {
        gliederungsZeiger =
            schreibeGliederungen(sb, gliederungen, gliederungsZeiger, norm.gliederung());
        letzte = norm.gliederung();
      }

      sb.append('\n');
      schreibeNorm(sb, norm);
    }
    if (!imAnlagenteil) {
      schreibeGliederungen(sb, gliederungen, gliederungsZeiger, null);
    }
    return sb.toString();
  }

  /**
   * Schreibt die Gliederungs-Überschriften ab {@code zeiger} bis einschließlich {@code bis} (bei
   * {@code null}: bis zum Ende) und gibt den neuen Zeiger zurück. Steht {@code bis} nicht mehr vor
   * dem Zeiger, so ist die Einheit bereits geschrieben; alsdann wird nichts geschrieben.
   */
  private static int schreibeGliederungen(
      StringBuilder sb, List<Gliederung> gliederungen, int zeiger, @Nullable Gliederung bis) {
    if (bis != null && gliederungen.subList(zeiger, gliederungen.size()).indexOf(bis) < 0) {
      return zeiger;
    }
    int i = zeiger;
    while (i < gliederungen.size()) {
      var g = gliederungen.get(i++);
      sb.append('\n').append(g.bezeichnung());
      if (g.titel() != null) {
        sb.append("  ").append(g.titel());
      }
      sb.append('\n');
      if (g.equals(bis)) {
        break;
      }
    }
    return i;
  }

  private static void schreibeNorm(StringBuilder sb, Norm norm) {
    var anlagenNummer = ANLAGEN_NUMMER.matcher(norm.enbez());
    if (anlagenNummer.matches()) {
      sb.append(anlagenNummer.group(1)).append('\n');
    } else if (ANLAGE.matcher(norm.enbez()).matches()) {
      sb.append(norm.enbez()).append('\n');
      if (norm.titel() != null) {
        sb.append(norm.titel()).append('\n');
      }
    } else {
      sb.append(norm.enbez());
      if (norm.titel() != null) {
        // Das doppelte Leerzeichen ist kanonisch: An ihm unterscheidet der Lader einen Normkopf
        // von einem Querverweis, der am Satzende klebt.
        sb.append("  ").append(norm.titel());
      }
      sb.append('\n');
    }

    for (var absatz : norm.absaetze()) {
      sb.append(absatz.anzeigeText()).append('\n');
    }
  }

  private static boolean istAnlagenNorm(Norm norm) {
    return ANLAGE.matcher(norm.enbez()).matches() || ANLAGEN_NUMMER.matcher(norm.enbez()).matches();
  }
}
