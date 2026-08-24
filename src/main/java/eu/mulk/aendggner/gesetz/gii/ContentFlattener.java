// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz.gii;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Flattet den strukturierten Inhalt eines gii-norm-{@code <P>}-Elements zu Klartext.
 *
 * <p>Aufzählungen ({@code <DL>/<DT>/<DD>/<LA>}) werden zu eingerückten Zeilen („1. …“, „a) …“),
 * {@code <BR/>} zu Zeilenumbrüchen, {@code <pre>} bleibt wörtlich erhalten, Tabellenzellen werden
 * mit „ | “ verbunden.
 */
final class ContentFlattener {

  private ContentFlattener() {}

  static String flatten(Element p) {
    var sb = new StringBuilder();
    flattenKnoten(p, sb, 0);
    return normalisiere(sb.toString());
  }

  private static void flattenKinder(Node parent, StringBuilder sb, int einrueckung) {
    for (var kind = parent.getFirstChild(); kind != null; kind = kind.getNextSibling()) {
      flattenKnoten(kind, sb, einrueckung);
    }
  }

  private static void flattenKnoten(Node knoten, StringBuilder sb, int einrueckung) {
    switch (knoten.getNodeType()) {
      case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> sb.append(knoten.getNodeValue());
      case Node.ELEMENT_NODE -> flattenElement((Element) knoten, sb, einrueckung);
      default -> {}
    }
  }

  private static void flattenElement(Element element, StringBuilder sb, int einrueckung) {
    switch (element.getNodeName()) {
      case "BR" -> sb.append('\n');
      case "DL" -> flattenListe(element, sb, einrueckung);
      // Mehrere <LA>-Geschwister in einem <DD> sind eigene Zeilen (z.B. Kurzüberschrift +
      // Definitionstext im UWG-Anhang) — ohne Trenner würden sie nahtlos verkleben. Die
      // Folgezeile wird tiefer eingerückt als die Aufzählungszeile, damit sie als Kindzeile
      // der Einheit erkennbar bleibt (Stellenauflösung „Nummer 31 Buchstabe b“).
      case "LA" -> {
        flattenKinder(element, sb, einrueckung);
        neueZeile(sb);
        sb.append("  ".repeat(einrueckung + 1));
      }
      case "pre" -> sb.append(element.getTextContent());
      case "table" -> flattenTabelle(element, sb);
      case "TOC" -> flattenToc(element, sb);
      default -> flattenKinder(element, sb, einrueckung);
    }
  }

  private static void flattenListe(Element dl, StringBuilder sb, int einrueckung) {
    String aktuellesLabel = null;
    for (var kind = dl.getFirstChild(); kind != null; kind = kind.getNextSibling()) {
      if (kind.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      var kindElement = (Element) kind;
      switch (kindElement.getNodeName()) {
        case "DT" -> aktuellesLabel = kindElement.getTextContent().strip();
        case "DD" -> {
          var eintrag = new StringBuilder();
          flattenKinder(kindElement, eintrag, einrueckung + 1);
          neueZeile(sb);
          sb.append("  ".repeat(einrueckung + 1));
          if (aktuellesLabel != null && !aktuellesLabel.isEmpty()) {
            sb.append(aktuellesLabel).append(' ');
          }
          sb.append(eintrag.toString().strip());
          sb.append('\n');
          aktuellesLabel = null;
        }
        default -> {}
      }
    }
  }

  /**
   * Flattet ein {@code <TOC>}-Element (Inhaltsübersicht): {@code <Ident>}/{@code <Title>}-Paare
   * werden zu Überschriftszeilen („Teil 1 | Allgemeiner Teil“), Tabellen zu Angabe-Zeilen.
   */
  private static void flattenToc(Element toc, StringBuilder sb) {
    String ident = null;
    for (var kind = toc.getFirstChild(); kind != null; kind = kind.getNextSibling()) {
      if (kind.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      var kindElement = (Element) kind;
      switch (kindElement.getNodeName()) {
        case "Ident" -> {
          if (ident != null && !ident.isEmpty()) {
            tocZeile(sb, ident);
          }
          ident = kindElement.getTextContent().strip();
        }
        case "Title" -> {
          var titel = kindElement.getTextContent().strip();
          tocZeile(sb, ident != null && !ident.isEmpty() ? ident + " | " + titel : titel);
          ident = null;
        }
        case "table" -> flattenTabelle(kindElement, sb);
        case "TOC" -> flattenToc(kindElement, sb);
        default -> {}
      }
    }
    if (ident != null && !ident.isEmpty()) {
      tocZeile(sb, ident);
    }
  }

  private static void tocZeile(StringBuilder sb, String zeile) {
    neueZeile(sb);
    sb.append(zeile);
    sb.append('\n');
  }

  private static void flattenTabelle(Element table, StringBuilder sb) {
    for (var row : alleNachkommen(table, "row")) {
      var zeile = new StringBuilder();
      for (var entry : alleNachkommen(row, "entry")) {
        var zellText = entry.getTextContent().strip();
        if (zellText.isEmpty()) {
          continue;
        }
        if (zeile.length() > 0) {
          zeile.append(" | ");
        }
        zeile.append(zellText);
      }
      if (zeile.length() > 0) {
        neueZeile(sb);
        sb.append(zeile);
        sb.append('\n');
      }
    }
  }

  private static java.util.List<Element> alleNachkommen(Element parent, String name) {
    var ergebnis = new java.util.ArrayList<Element>();
    var elemente = parent.getElementsByTagName(name);
    for (int i = 0; i < elemente.getLength(); i++) {
      ergebnis.add((Element) elemente.item(i));
    }
    return ergebnis;
  }

  private static void neueZeile(StringBuilder sb) {
    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
      sb.append('\n');
    }
  }

  /** Whitespace in Fließtextzeilen zusammenziehen, Zeilenstruktur aber erhalten. */
  private static String normalisiere(String text) {
    var zeilen = text.split("\n", -1);
    var sb = new StringBuilder();
    for (var zeile : zeilen) {
      var fuehrend = zeile.length() - zeile.stripLeading().length();
      var normalisiert =
          zeile.substring(0, fuehrend)
              + zeile.substring(fuehrend).replaceAll("[ \\t]+", " ").stripTrailing();
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(normalisiert);
    }
    // Mehrfache Leerzeilen zusammenfassen.
    return sb.toString().replaceAll("\n{3,}", "\n\n");
  }
}
