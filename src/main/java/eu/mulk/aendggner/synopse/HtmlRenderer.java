package eu.mulk.aendggner.synopse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.gesetz.Norm;
import java.time.LocalDate;
import java.util.List;

/**
 * Rendert eine {@link Synopse} als selbständige HTML-Datei: alte Fassung links, neue rechts,
 * wortweise Änderungen hervorgehoben, gefolgt von einem Abschnitt „Manuell prüfen“.
 */
public final class HtmlRenderer {

  private HtmlRenderer() {}

  public static String rendere(Synopse synopse, String quelleBeschreibung) {
    var sb = new StringBuilder();
    sb.append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    sb.append("<title>Synopse: ")
        .append(esc(synopse.alt().jurabk()))
        .append("</title>\n<style>\n")
        .append(CSS)
        .append("</style>\n</head>\n<body>\n");

    rendereKopf(sb, synopse, quelleBeschreibung);

    for (var eintrag : synopse.eintraege()) {
      rendereEintrag(sb, eintrag);
    }

    rendereManuellZuPruefen(sb, synopse);

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  private static void rendereKopf(StringBuilder sb, Synopse synopse, String quelle) {
    sb.append("<header>\n<h1>Synopse: ").append(esc(synopse.alt().jurabk())).append("</h1>\n");
    if (synopse.alt().langue() != null) {
      sb.append("<p class=\"langue\">").append(esc(synopse.alt().langue())).append("</p>\n");
    }
    sb.append("<p class=\"quelle\">")
        .append(esc(quelle))
        .append(" — erstellt am ")
        .append(LocalDate.now())
        .append(" mit ÄndGgner</p>\n");
    long geaendert =
        synopse.eintraege().stream()
            .filter(e -> e.art() != Synopse.Aenderungsart.UNVERAENDERT)
            .count();
    sb.append("<p class=\"statistik\">")
        .append(geaendert)
        .append(" geänderte Normen, ")
        .append(synopse.manuellZuPruefen().size())
        .append(" manuell zu prüfende Befehle</p>\n");
    sb.append("<div class=\"spaltenkopf\"><div>Alte Fassung</div><div>Neue Fassung</div></div>\n");
    sb.append("</header>\n");
  }

  private static void rendereEintrag(StringBuilder sb, Synopse.Eintrag eintrag) {
    sb.append("<section class=\"norm ")
        .append(eintrag.art().name().toLowerCase())
        .append("\">\n<h2>")
        .append(esc(eintrag.enbez()));
    var titel =
        eintrag.neuNorm().titel() != null
            ? eintrag.neuNorm().titel()
            : eintrag.altNorm() != null ? eintrag.altNorm().titel() : null;
    if (titel != null) {
      sb.append(" — ").append(esc(titel));
    }
    sb.append(markierung(eintrag.art()));
    sb.append("</h2>\n");
    if (eintrag.neuNorm().gliederung() != null) {
      sb.append("<p class=\"gliederung\">")
          .append(esc(eintrag.neuNorm().gliederung().anzeigeText()))
          .append("</p>\n");
    }
    if (!eintrag.ursachen().isEmpty()) {
      sb.append("<p class=\"ursachen\">Geändert durch: ");
      var erste = true;
      for (var ursache : eintrag.ursachen()) {
        if (!erste) {
          sb.append("; ");
        }
        sb.append(esc(ursache.befehl().provenienz().anzeigeText()));
        erste = false;
      }
      sb.append("</p>\n");
    }

    var spalten = spaltenFuer(eintrag);
    sb.append("<div class=\"vergleich\">\n<div class=\"alt\">")
        .append(
            spalten.altHtml().isEmpty()
                ? "<span class=\"leer\">(nicht vorhanden)</span>"
                : spalten.altHtml())
        .append("</div>\n<div class=\"neu\">")
        .append(
            spalten.neuHtml().isEmpty()
                ? "<span class=\"leer\">(nicht vorhanden)</span>"
                : spalten.neuHtml())
        .append("</div>\n</div>\n</section>\n");
  }

  private static WortDiff.Spalten spaltenFuer(Synopse.Eintrag eintrag) {
    var altText = eintrag.altNorm() == null ? "" : textVon(eintrag.altNorm());
    var neuText = textVon(eintrag.neuNorm());
    if (eintrag.art() == Synopse.Aenderungsart.NEU) {
      return new WortDiff.Spalten("", "<ins>" + WortDiff.escapeHtml(neuText) + "</ins>");
    }
    return WortDiff.vergleiche(altText, neuText);
  }

  private static String textVon(Norm norm) {
    return norm.gesamtText();
  }

  private static String markierung(Synopse.Aenderungsart art) {
    return switch (art) {
      case NEU -> " <span class=\"badge neu-badge\">neu</span>";
      case AUFGEHOBEN -> " <span class=\"badge aufgehoben-badge\">aufgehoben</span>";
      case GEAENDERT -> " <span class=\"badge geaendert-badge\">geändert</span>";
      case UNVERAENDERT -> "";
    };
  }

  private static void rendereManuellZuPruefen(StringBuilder sb, Synopse synopse) {
    if (synopse.manuellZuPruefen().isEmpty() && synopse.warnungen().isEmpty()) {
      return;
    }
    sb.append("<section class=\"manuell\">\n<h2>Manuell prüfen</h2>\n");
    if (!synopse.warnungen().isEmpty()) {
      sb.append("<h3>Warnungen der Textverarbeitung</h3>\n<ul>\n");
      for (var warnung : synopse.warnungen()) {
        sb.append("<li>").append(esc(warnung)).append("</li>\n");
      }
      sb.append("</ul>\n");
    }
    if (!synopse.manuellZuPruefen().isEmpty()) {
      sb.append("<h3>Nicht automatisch angewandte Befehle</h3>\n<ol>\n");
      for (var eintrag : synopse.manuellZuPruefen()) {
        var befehl = eintrag.befehl();
        sb.append("<li><strong>")
            .append(esc(befehl.provenienz().anzeigeText()))
            .append("</strong>");
        if (!(befehl instanceof UnbekannterBefehl) || !eintrag.begruendung().isEmpty()) {
          sb.append(" — ").append(esc(eintrag.begruendung()));
        }
        sb.append("<br><span class=\"originaltext\">")
            .append(esc(befehl.provenienz().originalText()))
            .append("</span></li>\n");
      }
      sb.append("</ol>\n");
    }
    sb.append("</section>\n");
  }

  private static String esc(String text) {
    return WortDiff.escapeHtml(text);
  }

  static List<String> zeilenVon(String text) {
    return text.lines().toList();
  }

  private static final String CSS =
      """
      :root {
        color-scheme: light dark;
        --del-bg: #ffd7d7;
        --del-fg: #8b0000;
        --ins-bg: #d7f5d7;
        --ins-fg: #005f00;
        --rand: #ccc;
        --dezent: #666;
      }
      @media (prefers-color-scheme: dark) {
        :root {
          --del-bg: #5a1f1f;
          --del-fg: #ffb3b3;
          --ins-bg: #1f4a1f;
          --ins-fg: #b3ffb3;
          --rand: #555;
          --dezent: #aaa;
        }
      }
      body {
        font-family: Georgia, "Times New Roman", serif;
        line-height: 1.45;
        max-width: 90rem;
        margin: 0 auto;
        padding: 1rem 2rem;
      }
      header h1 { margin-bottom: 0.2rem; }
      .langue { font-style: italic; margin-top: 0; }
      .quelle, .statistik, .gliederung, .ursachen { color: var(--dezent); font-size: 0.9rem; }
      .spaltenkopf {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1rem;
        font-weight: bold;
        border-bottom: 2px solid var(--rand);
        padding: 0.5rem 0;
        position: sticky;
        top: 0;
        background: inherit;
      }
      section.norm { border-bottom: 1px solid var(--rand); padding: 0.7rem 0; }
      section.norm h2 { font-size: 1.15rem; margin: 0.3rem 0; }
      .vergleich {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1rem;
        align-items: start;
      }
      .vergleich > div {
        white-space: pre-wrap;
        overflow-wrap: anywhere;
        font-size: 0.95rem;
      }
      del, .alt del {
        background: var(--del-bg);
        color: var(--del-fg);
        text-decoration: line-through;
      }
      ins, .neu ins {
        background: var(--ins-bg);
        color: var(--ins-fg);
        text-decoration: none;
      }
      .leer { color: var(--dezent); font-style: italic; }
      .badge {
        font-family: system-ui, sans-serif;
        font-size: 0.7rem;
        font-weight: normal;
        border-radius: 0.6rem;
        padding: 0.1rem 0.55rem;
        vertical-align: middle;
      }
      .neu-badge { background: var(--ins-bg); color: var(--ins-fg); }
      .aufgehoben-badge { background: var(--del-bg); color: var(--del-fg); }
      .geaendert-badge { border: 1px solid var(--rand); color: var(--dezent); }
      section.manuell { margin-top: 2rem; }
      section.manuell li { margin-bottom: 0.7rem; }
      .originaltext { color: var(--dezent); font-size: 0.85rem; }
      @media print {
        .vergleich { break-inside: avoid; }
      }
      """;
}
