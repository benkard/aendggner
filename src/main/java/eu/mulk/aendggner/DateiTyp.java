// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import java.nio.charset.StandardCharsets;

/**
 * Die Eingabeformate, die ÄndGgner unterscheidet, erkannt an den ersten Bytes.
 *
 * <p>Ersetzt die frühere Tika-Erkennung: Unterschieden werden muss nur zwischen ZIP, PDF, gii-XML
 * und Klartext, und dafür genügen die Signaturbytes. Das spart eine schwergewichtige Abhängigkeit
 * samt ServiceLoader- und XML-Konfiguration — was der Wasm-Übersetzung zugutekommt, für die jede
 * dynamisch aufgelöste Abhängigkeit Handarbeit bedeutet.
 */
public enum DateiTyp {
  PDF,
  XML,
  ZIP,
  KLARTEXT;

  /** Wie weit hinein nach der Signatur gesucht wird (PDFs tragen gelegentlich Vorspann). */
  private static final int VORSCHAU_BYTES = 1024;

  public static DateiTyp erkenne(byte[] inhalt) {
    // Die ZIP-Signatur steht an Byte 0 und wird deshalb zuerst und ohne Vorschaufenster geprüft:
    // Ein Archiv, das zufällig „%PDF-“ in seinen komprimierten Daten führt, wäre sonst ein PDF.
    if (inhalt.length >= 4
        && inhalt[0] == 'P'
        && inhalt[1] == 'K'
        && inhalt[2] == 3
        && inhalt[3] == 4) {
      return ZIP;
    }

    // ISO-8859-1 bildet jedes Byte auf genau ein Zeichen ab — hier geht es um Signaturen, nicht
    // um lesbaren Text, und die Zeichenzählung soll der Byteposition entsprechen.
    var vorschau =
        new String(inhalt, 0, Math.min(inhalt.length, VORSCHAU_BYTES), StandardCharsets.ISO_8859_1);

    if (vorschau.contains("%PDF-")) {
      return PDF;
    }

    // BOM und führenden Leerraum überspringen: Das erste bedeutungstragende Zeichen einer
    // XML-Datei ist die Deklaration oder das Wurzelelement.
    int i = vorschau.startsWith("ï»¿") ? 3 : 0;
    while (i < vorschau.length() && Character.isWhitespace(vorschau.charAt(i))) {
      i++;
    }
    if (i < vorschau.length() && vorschau.charAt(i) == '<') {
      return XML;
    }

    return KLARTEXT;
  }

  /** Für Fehlermeldungen: „PDF“, „XML“, „ZIP“, „Klartext“. */
  public String anzeigeName() {
    return this == KLARTEXT ? "Klartext" : name();
  }
}
