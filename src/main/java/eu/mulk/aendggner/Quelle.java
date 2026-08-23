// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ein Eingabedokument als Name und Inhalt — die Form, in der die Pipeline Dateien entgegennimmt.
 *
 * <p>Die Pipeline kennt bewusst kein Dateisystem: Auf der Befehlszeile kommen die Bytes aus einer
 * Datei ({@link #lies(Path)}), im Browser aus einem Datei-Upload, den JavaScript übergibt. Der
 * {@code name} dient allein der Anzeige (Quellenzeile der Synopse, Warnungen) und trägt deshalb
 * genau das, was auf der Befehlszeile {@code Path.getFileName()} liefern würde.
 *
 * <p>{@code equals}/{@code hashCode} sind für {@code byte[]} identitätsbasiert; auf Gleichheit von
 * {@code Quelle}n verlässt sich niemand.
 */
public record Quelle(String name, byte[] inhalt) {

  /** Liest eine Datei vollständig ein. Nur für JVM-Aufrufer (Befehlszeile, Tests). */
  public static Quelle lies(Path datei) throws IOException {
    return new Quelle(datei.getFileName().toString(), Files.readAllBytes(datei));
  }
}
