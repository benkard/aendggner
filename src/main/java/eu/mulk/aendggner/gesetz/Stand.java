// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz;

import java.time.LocalDate;
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
public record Stand(String kommentar, @Nullable LocalDate juengsteAenderung) {}
