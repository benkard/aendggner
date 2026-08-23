// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.gesetz.gii;

import eu.mulk.aendggner.Quelle;
import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Gliederung;
import eu.mulk.aendggner.gesetz.Norm;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Liest ein Stammgesetz im gii-norm-Format von gesetze-im-internet.de.
 *
 * <p>Die DTD-Referenz ({@code gii-norm.dtd} per HTTP) wird bewusst nicht aufgelöst; der Loader
 * arbeitet vollständig offline.
 */
public final class GiiXmlLoader {

  private static final Pattern ABSATZ_MARKER =
      Pattern.compile("^\\((\\d+[a-z]?)\\)\\s+", Pattern.UNICODE_CASE);

  /** Bequemlichkeit für Befehlszeile und Tests; im Browser gibt es keine {@link Path}e. */
  public Gesetz load(Path datei) throws IOException, SAXException {
    return load(Quelle.lies(datei));
  }

  public Gesetz load(Quelle quelle) throws IOException, SAXException {
    var builder = neuerDocumentBuilder();
    var dokument = builder.parse(new ByteArrayInputStream(quelle.inhalt()));
    var wurzel = dokument.getDocumentElement();

    String jurabk = null;
    String langue = null;
    String kurzue = null;
    var normen = new ArrayList<Norm>();
    var gliederungen = new ArrayList<Gliederung>();
    Gliederung aktuelleGliederung = null;

    for (var normElement : kindElemente(wurzel, "norm")) {
      var metadaten = erstesKind(normElement, "metadaten");
      if (metadaten == null) {
        continue;
      }

      if (jurabk == null) {
        jurabk = kindText(metadaten, "jurabk");
        langue = kindText(metadaten, "langue");
        kurzue = kindText(metadaten, "kurzue");
      }

      var gliederungselement = erstesKind(metadaten, "gliederungseinheit");
      if (gliederungselement != null) {
        var kennzahl = kindText(gliederungselement, "gliederungskennzahl");
        var bez = kindText(gliederungselement, "gliederungsbez");
        var titel = kindText(gliederungselement, "gliederungstitel");
        if (bez != null) {
          aktuelleGliederung = new Gliederung(kennzahl, bez, titel);
          gliederungen.add(aktuelleGliederung);
        }
      }

      var enbez = kindText(metadaten, "enbez");
      if (enbez == null) {
        // Rahmen-Norm (Metadaten des Gesamtgesetzes, Fußnoten) — keine Einzelnorm.
        continue;
      }

      var titel = kindText(metadaten, "titel");
      var absaetze = leseAbsaetze(normElement);
      var weggefallen =
          (titel != null && titel.strip().equals("(weggefallen)"))
              || (absaetze.size() == 1 && absaetze.get(0).text().strip().equals("(weggefallen)"));
      normen.add(new Norm(enbez, titel, aktuelleGliederung, absaetze, weggefallen));
    }

    if (jurabk == null) {
      throw new SAXException("Keine <norm>-Elemente mit Metadaten gefunden: " + quelle.name());
    }
    return new Gesetz(jurabk, langue, kurzue, normen, gliederungen);
  }

  private static ArrayList<Absatz> leseAbsaetze(Element normElement) {
    var absaetze = new ArrayList<Absatz>();
    var textdaten = erstesKind(normElement, "textdaten");
    if (textdaten == null) {
      return absaetze;
    }
    var text = erstesKind(textdaten, "text");
    if (text == null) {
      return absaetze;
    }
    var content = erstesKind(text, "Content");
    if (content == null) {
      return absaetze;
    }

    for (var p : kindElemente(content, "P", "TOC")) {
      var geflattet = ContentFlattener.flatten(p).strip();
      if (geflattet.isEmpty()) {
        continue;
      }
      var matcher = ABSATZ_MARKER.matcher(geflattet);
      if (matcher.find()) {
        absaetze.add(new Absatz(matcher.group(1), geflattet.substring(matcher.end())));
      } else {
        absaetze.add(new Absatz(null, geflattet));
      }
    }
    return absaetze;
  }

  private static DocumentBuilder neuerDocumentBuilder() {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var builder = factory.newDocumentBuilder();
      builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
      return builder;
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("XML-Parser kann nicht konfiguriert werden", e);
    }
  }

  private static Iterable<Element> kindElemente(Element parent, String... namen) {
    var ergebnis = new ArrayList<Element>();
    for (var kind = parent.getFirstChild(); kind != null; kind = kind.getNextSibling()) {
      if (kind.getNodeType() == Node.ELEMENT_NODE
          && java.util.Arrays.asList(namen).contains(kind.getNodeName())) {
        ergebnis.add((Element) kind);
      }
    }
    return ergebnis;
  }

  private static @Nullable Element erstesKind(Element parent, String name) {
    for (var kind = parent.getFirstChild(); kind != null; kind = kind.getNextSibling()) {
      if (kind.getNodeType() == Node.ELEMENT_NODE && kind.getNodeName().equals(name)) {
        return (Element) kind;
      }
    }
    return null;
  }

  private static @Nullable String kindText(Element parent, String name) {
    var element = erstesKind(parent, name);
    if (element == null) {
      return null;
    }
    var text = element.getTextContent().strip();
    return text.isEmpty() ? null : text;
  }
}
