// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz;

import java.time.LocalDate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Der Stand einer konsolidierten Fassung, wie ihn die Quelle selbst angibt.
 *
 * <p>Die Standzeile allein trägt nicht weit genug. Das gii-XML führt neben ihr Hinweise der Form
 * „Änderung durch Art. 1 G v. 18.11.2020 I 2397 … textlich nachgewiesen, dokumentarisch noch nicht
 * abschließend bearbeitet“: Der Wortlaut ist dann bereits fortgeschrieben, die Standzeile nennt
 * aber noch die vorige Änderung. Wer nur sie liest, hält eine Fassung für älter, als sie ist —
 * genau der Irrtum, an dem im Infektionsschutzgesetz siebenundzwanzig Befehle scheiterten, ohne
 * dass jemand den Grund benennen konnte.
 *
 * @param kommentar die Standzeile im Wortlaut der Quelle, für die Anzeige.
 * @param juengsteAenderung das späteste Datum, das irgendeine Standangabe nennt — der wirkliche
 *     Stand des Wortlauts; {@code null}, wenn keine Angabe ein Datum trägt.
 */
public record Stand(String kommentar, @Nullable LocalDate juengsteAenderung) {

  /** „… v. 19.6.2020 I 1385“ — das Datum, das eine Standangabe nennt. */
  private static final Pattern STANDDATUM =
      Pattern.compile("v\\. (\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");

  /** Eine Standzeile mitsamt dem spätesten Datum, das in ihr steht. */
  public static Stand aus(String kommentar) {
    return new Stand(kommentar, juengstesDatum(kommentar));
  }

  /**
   * Das späteste Datum, das {@code text} nennt; {@code null}, wenn keines darin steht. Eine
   * Standangabe nennt mitunter mehrere, und ein verschriebenes Datum („19.5..2020“) kommt vor — es
   * bleibt außer Betracht, statt den ganzen Stand zu verwerfen.
   */
  public static @Nullable LocalDate juengstesDatum(String text) {
    LocalDate juengste = null;
    var m = STANDDATUM.matcher(text);
    while (m.find()) {
      try {
        var datum =
            LocalDate.of(
                Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(1)));
        if (juengste == null || datum.isAfter(juengste)) {
          juengste = datum;
        }
      } catch (RuntimeException verschrieben) {
        // Ein unmögliches Datum bleibt außer Betracht.
      }
    }
    return juengste;
  }
}
