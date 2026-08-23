// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
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
    return rendere(synopse, quelleBeschreibung, false);
  }

  /**
   * @param entwurfsfassung mindestens eines der angewandten Dokumente war ein Entwurf, ein
   *     Änderungsantrag oder eine Beschlussempfehlung; die rechte Spalte zeigt dann keinen
   *     geltenden Rechtsstand, sondern einen Verfahrensstand.
   */
  public static String rendere(
      Synopse synopse, String quelleBeschreibung, boolean entwurfsfassung) {
    var sb = new StringBuilder();
    sb.append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    sb.append("<title>Synopse: ")
        .append(esc(synopse.alt().jurabk()))
        .append("</title>\n<style>\n")
        .append(CSS)
        .append("</style>\n</head>\n<body>\n");

    rendereKopf(sb, synopse, quelleBeschreibung, entwurfsfassung);

    // Der Spaltenkopf steht mit der Gegenüberstellung in einem Block: Sein Klebebereich reicht
    // damit über alle Normen, endet aber vor dem Abschnitt „Manuell prüfen“, der keine Spalten
    // hat und über dem er nur im Wege stünde.
    sb.append("<div class=\"gegenueberstellung\">\n");
    sb.append("<div class=\"spaltenkopf\"><div>Alte Fassung</div><div>Neue Fassung</div></div>\n");

    rendereGliederungsAenderungen(sb, synopse);

    for (var eintrag : synopse.eintraege()) {
      rendereEintrag(sb, eintrag);
    }

    sb.append("</div>\n");

    rendereManuellZuPruefen(sb, synopse);

    rendereFuss(sb);

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  /**
   * Der Briefkopf des Vordrucks ÄG-2, im Aufbau derselbe wie auf der Aufgabeseite: links die
   * ausstellende Stelle, rechts die Kennung; darunter der Vorspann mit Herkunft und Statistik.
   */
  private static void rendereKopf(
      StringBuilder sb, Synopse synopse, String quelle, boolean entwurfsfassung) {
    sb.append("<header class=\"briefkopf\">\n<div class=\"dienststelle\">\n");
    sb.append("<p class=\"langname\">").append(LANGNAME).append("</p>\n");
    sb.append("<h1>Synopse: ").append(esc(synopse.alt().jurabk())).append("</h1>\n");
    if (synopse.alt().langue() != null) {
      sb.append("<p class=\"langue\">").append(esc(synopse.alt().langue())).append("</p>\n");
    }
    sb.append("</div>\n<dl class=\"kennung\">\n");
    sb.append("<dt>Formblatt</dt><dd>ÄG&#8209;2</dd>\n");
    sb.append("<dt>Stammgesetz</dt><dd>")
        .append(esc(synopse.alt().jurabk()))
        .append("</dd>\n<dt>Erstellt</dt><dd>")
        .append(LocalDate.now())
        .append("</dd>\n");
    if (entwurfsfassung) {
      sb.append("<dt>Stand</dt><dd>Entwurfsfassung</dd>\n");
    }
    sb.append("<dt>Geltung</dt><dd>nichtamtlich</dd>\n");
    sb.append("</dl>\n</header>\n");

    sb.append("<div class=\"vorgang\">\n");
    if (entwurfsfassung) {
      sb.append(
          "<p class=\"entwurfshinweis\">Entwurfsfassung — nicht geltendes Recht. Die neue Fassung"
              + " gibt den Stand des Gesetzgebungsverfahrens wieder.</p>\n");
    }
    sb.append("<p class=\"quelle\">Grundlage: ").append(esc(quelle)).append("</p>\n");
    long geaendert =
        synopse.eintraege().stream()
            .filter(e -> e.art() != Synopse.Aenderungsart.UNVERAENDERT)
            .count();
    sb.append("<p class=\"statistik\">")
        .append(geaendert)
        .append(" geänderte Normen, ")
        .append(synopse.manuellZuPruefen().size())
        .append(" manuell zu prüfende Befehle</p>\n");
    sb.append("</div>\n");
  }

  /**
   * Der Fußsteg. Der Vorbehalt gehört gerade hierher und nicht bloß auf die Aufgabeseite: Die
   * Synopse wird ausgedruckt und weitergereicht, das Formular nicht.
   */
  private static void rendereFuss(StringBuilder sb) {
    sb.append("<footer>\n<span>Erstellt am ")
        .append(LocalDate.now())
        .append(" mit ÄndGgner</span>\n")
        .append(
            "<span class=\"nebenquelle\">Ohne Gewähr; maßgeblich ist allein die amtliche"
                + " Verkündung im jeweiligen Gesetz- oder Verordnungsblatt.</span>\n")
        .append("</footer>\n");
  }

  private static final String LANGNAME =
      "Nichtamtliche Zentralstelle für die maschinelle Fortschreibung von Stammgesetzen anhand"
          + " von Änderungsvorschriften des Bundes und der Länder";

  private static void rendereGliederungsAenderungen(StringBuilder sb, Synopse synopse) {
    if (synopse.gliederungsAenderungen().isEmpty()) {
      return;
    }
    sb.append(
        "<section class=\"gliederung-aenderungen\">\n<h2>Geänderte Gliederungs-Überschriften</h2>\n");
    for (var aenderung : synopse.gliederungsAenderungen()) {
      var altText = aenderung.alt() != null ? aenderung.alt().anzeigeText() : "";
      var spalten = WortDiff.vergleiche(altText, aenderung.neu().anzeigeText());
      sb.append("<div class=\"vergleich\">\n<div class=\"alt\">")
          .append(spalten.altHtml())
          .append("</div>\n<div class=\"neu\">")
          .append(spalten.neuHtml());
      if (aenderung.alt() == null) {
        sb.append(" <span class=\"badge neu-badge\">neu</span>");
      }
      sb.append("</div>\n</div>\n");
    }
    sb.append("</section>\n");
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
      /* Der Vordruck ÄG-2. Das Farbgerüst ist dasselbe wie in
         src/main/resources/eu/mulk/aendggner/web/style.css und wird hier ein zweites Mal
         geführt: Diese Datei wird aus einem blob:-Verweis geöffnet und im Befehlszeilen-
         betrieb als einzelne Datei abgelegt; sie kann kein fremdes Stilblatt einbinden.
         Wer dort etwas ändert, ändere es hier mit.

         Die Schrift ist geteilt: Der Vordruck — Briefkopf, Kennung, Überschriften,
         Spaltenköpfe, Kästchen, Fußsteg — steht in der Groteske, der Wortlaut der Normen
         in der Antiqua. Amtskopf und Vorschrift sind zweierlei, im Gesetzblatt wie hier.

         Farbe trägt allein die Änderung: rot heißt weg, grün heißt hinzu. Damit die
         Aussage auch im Schwarzweiß-Ausdruck und bei Farbfehlsichtigkeit erhalten bleibt,
         tritt zur Farbe stets die Form — durchgestrichen gegen unterstrichen — sowie die
         Spalte, in der sie steht. */
      :root {
        color-scheme: light dark;
        --fg: #111;
        --bg: #fff;
        --papier: #f3f1ea;
        --muted: #595959;
        --raster: #d6d2c6;
        --kasten: #f0ede4;
        --amt: #8b0f21;
        --del-bg: #ffd7d7;
        --del-fg: #8b0000;
        --ins-bg: #d7f5d7;
        --ins-fg: #005f00;
        --grotesk: system-ui, "Segoe UI", "Helvetica Neue", Arial, sans-serif;
        --antiqua: Georgia, "Times New Roman", serif;
      }
      @media (prefers-color-scheme: dark) {
        :root {
          --fg: #e6e6e6;
          --bg: #1c1c1c;
          --papier: #0f0f0f;
          --muted: #a0a0a0;
          --raster: #3c3c3c;
          --kasten: #242424;
          --amt: #e79aa4;
          --del-bg: #5a1f1f;
          --del-fg: #ffb3b3;
          --ins-bg: #1f4a1f;
          --ins-fg: #b3ffb3;
        }
      }
      * { box-sizing: border-box; }
      /* Die Unterlage endet nie, auch wenn der Bogen kürzer ist als das Fenster. */
      html { background: var(--papier); }
      body {
        counter-reset: norm;
        font-family: var(--grotesk);
        line-height: 1.5;
        max-width: 90rem;
        margin: 2rem auto 3rem;
        padding: 2rem 2.25rem 2.5rem;
        border: 1px solid var(--fg);
        color: var(--fg);
        background: var(--bg);
      }
      /* Auf schmalen Geräten wäre der Bogenrand nur ein Verlust an Satzbreite. */
      @media (max-width: 48rem) {
        body { margin: 0; padding: 1.25rem 1rem 2rem; border: none; }
      }

      /* Briefkopf und Doppellinie wie auf der Aufgabeseite: kräftig in Amtsrot, dünn in
         Rasterfarbe, wobei die zweite Linie der Oberrand des Vorspanns ist. */
      .briefkopf {
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 1rem 2rem;
        align-items: end;
        padding-bottom: 0.7rem;
        border-bottom: 3px solid var(--amt);
      }
      .langname {
        margin: 0 0 0.3rem;
        max-width: 30rem;
        font-size: 0.75rem;
        letter-spacing: 0.05em;
        text-transform: uppercase;
        line-height: 1.35;
        color: var(--muted);
      }
      .dienststelle h1 {
        margin: 0;
        font-size: 1.9rem;
        letter-spacing: 0.06em;
        line-height: 1.1;
      }
      .langue { margin: 0.35rem 0 0; font-style: italic; color: var(--muted); }
      .kennung {
        display: grid;
        grid-template-columns: auto auto;
        gap: 0 0.6rem;
        margin: 0;
        padding: 0.4rem 0.6rem;
        border: 1px solid var(--raster);
        font-size: 0.75rem;
        line-height: 1.5;
      }
      .kennung dt { letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }
      .kennung dd { margin: 0; font-weight: bold; color: var(--amt); white-space: nowrap; }
      /* Gestapelt zöge die Kennung sonst über die ganze Breite und sähe aus wie ein Kasten
         des Satzspiegels statt wie ein Stempel am Kopfrand. */
      @media (max-width: 34rem) {
        .briefkopf { grid-template-columns: 1fr; align-items: start; }
        .kennung { justify-self: start; }
      }

      .vorgang { margin-top: 3px; padding-top: 1rem; border-top: 1px solid var(--raster); }
      .quelle, .statistik, .gliederung, .ursachen {
        color: var(--muted);
        font-size: 0.85rem;
        margin: 0.2rem 0;
      }
      .entwurfshinweis {
        margin: 0 0 0.9rem;
        padding: 0.9rem 1.1rem;
        border: 1px solid var(--raster);
        border-left: 4px solid var(--del-fg);
        background: var(--kasten);
        font-size: 0.9rem;
      }

      /* Der Spaltenkopf steht mit der Gegenüberstellung in einem Block und nicht mehr im
         Kopfblock: Nur so reicht sein Klebebereich über alle Normen und nicht bloß über den
         Vorspann. Seine beiden Felder tragen die Form der Legende des Antragsvordrucks. */
      .spaltenkopf {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1rem;
        position: sticky;
        top: 0;
        z-index: 1;
        margin-top: 1.25rem;
        padding: 0.6rem 0 0.6rem 3rem;
        background: var(--bg);
      }
      .spaltenkopf div {
        padding: 0.3rem 0.8rem;
        background: var(--fg);
        color: var(--bg);
        font-size: 0.8rem;
        font-weight: bold;
        letter-spacing: 0.08em;
        text-transform: uppercase;
      }

      /* Der Steg links trägt die Randziffer, wie im Vordruck die Feldziffer; sie macht die
         Abschnitte überdies zitierbar. */
      section { position: relative; padding-left: 3rem; }
      section.norm {
        counter-increment: norm;
        padding-top: 1rem;
        padding-bottom: 1rem;
        border-bottom: 1px solid var(--raster);
      }
      section.norm::before {
        content: counter(norm);
        position: absolute;
        top: 1.15rem;
        left: 0;
        width: 1.5rem;
        height: 1.5rem;
        border: 1px solid var(--amt);
        color: var(--amt);
        font-size: 0.8rem;
        font-weight: bold;
        line-height: 1.4rem;
        text-align: center;
      }
      section.norm h2 { font-size: 1.05rem; margin: 0 0 0.5rem; letter-spacing: 0.02em; }

      .vergleich {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1rem;
        align-items: start;
      }
      /* Die alte Fassung liegt auf dem Kastenton wie ein Durchschlag, die neue auf
         Bogenweiß; beim Springen zwischen den Spalten ist damit ohne Hinsehen klar, wo man
         steht. Die Hinterlegungen der Änderungen heben sich von beiden Gründen ab. */
      .vergleich > div {
        padding: 0.75rem 0.9rem;
        border: 1px solid var(--raster);
        white-space: pre-wrap;
        overflow-wrap: anywhere;
        font-family: var(--antiqua);
        font-size: 0.95rem;
        line-height: 1.45;
      }
      .vergleich > .alt { background: var(--kasten); }
      .vergleich > .neu { background: var(--bg); }

      del, .alt del {
        background: var(--del-bg);
        color: var(--del-fg);
        text-decoration: line-through;
      }
      ins, .neu ins {
        background: var(--ins-bg);
        color: var(--ins-fg);
        text-decoration: underline;
      }
      .leer { color: var(--muted); font-style: italic; }

      /* Kästchen in Versalien; die Farbe folgt derselben Regel wie im Diff. Beschriftet
         sind sie ohnehin, sodass die Farbe hier nur bestätigt, was dasteht. Der Rahmen
         steht auch dort, wo die Hinterlegung schwach wirkt. */
      .badge {
        font-family: var(--grotesk);
        font-size: 0.7rem;
        font-weight: normal;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        border: 1px solid var(--raster);
        padding: 0.1rem 0.55rem;
        vertical-align: middle;
        white-space: nowrap;
      }
      .neu-badge { background: var(--ins-bg); color: var(--ins-fg); border-color: var(--ins-fg); }
      .aufgehoben-badge {
        background: var(--del-bg);
        color: var(--del-fg);
        border-color: var(--del-fg);
      }
      .geaendert-badge { border-color: var(--raster); color: var(--muted); }

      /* Die Abschnittsüberschriften treten als Balken auf wie die Legende des Feldblocks. */
      section.gliederung-aenderungen > h2, section.manuell > h2 {
        display: inline-block;
        margin: 0 0 0.9rem;
        padding: 0.3rem 0.8rem;
        background: var(--fg);
        color: var(--bg);
        font-size: 0.8rem;
        font-weight: bold;
        letter-spacing: 0.08em;
        text-transform: uppercase;
      }
      section.gliederung-aenderungen { padding-top: 1.25rem; }
      section.manuell { margin-top: 2rem; padding-top: 1.5rem; border-top: 3px solid var(--amt); }
      section.manuell h3 {
        margin: 1.25rem 0 0.5rem;
        font-size: 0.8rem;
        letter-spacing: 0.06em;
        text-transform: uppercase;
      }
      section.manuell li { margin-bottom: 0.7rem; }
      .originaltext { font-family: var(--antiqua); color: var(--muted); font-size: 0.85rem; }

      footer {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;
        gap: 0.5rem 1.5rem;
        margin-top: 2.5rem;
        padding-top: 0.9rem;
        border-top: 1px solid var(--fg);
        font-size: 0.8125rem;
        color: var(--muted);
      }

      /* Zwei Spalten Gesetzestext nebeneinander sind auf dem Telefon nicht zu lesen. Gestapelt
         trägt der Spaltenkopf nichts mehr; deshalb beschriftet sich dort jede Spalte selbst. */
      @media (max-width: 60rem) {
        .spaltenkopf { display: none; }
        .vergleich { grid-template-columns: 1fr; }
        section { padding-left: 2.25rem; }
        .vergleich > div::before {
          display: block;
          margin-bottom: 0.4rem;
          font-family: var(--grotesk);
          font-size: 0.7rem;
          font-weight: bold;
          letter-spacing: 0.08em;
          text-transform: uppercase;
          color: var(--muted);
        }
        .vergleich > .alt::before { content: "Alte Fassung"; }
        .vergleich > .neu::before { content: "Neue Fassung"; }
      }

      /* Gedruckt wird stets auf weißes Papier, auch aus einem dunkel eingestellten Browser
         heraus: prefers-color-scheme gilt im Druck fort. Deshalb ist hier das ganze
         Farbgerüst zurückzusetzen und nicht nur ein Teil davon — sonst käme die alte Spalte
         als schwarzer Block aus dem Drucker. */
      @media print {
        :root {
          --fg: #000;
          --bg: #fff;
          --papier: #fff;
          --muted: #333;
          --raster: #999;
          --kasten: #f2f0ea;
          --amt: #000;
          --del-bg: #ffd7d7;
          --del-fg: #8b0000;
          --ins-bg: #d7f5d7;
          --ins-fg: #005f00;
        }
        body { margin: 0; padding: 0; max-width: none; border: none; font-size: 10pt; }
        .spaltenkopf { position: static; }
        .vergleich, section.norm { break-inside: avoid; }
        /* Ohne diese Festlegung wirft der Druck die Hinterlegung weg. Durchstreichung
           und Unterstreichung überstehen ihn ohnehin und tragen die Aussage auch dort,
           wo schwarzweiß gedruckt wird; der Spaltenbalken und der Durchschlagston der
           alten Fassung aber haben keine solche zweite Gestalt. */
        del, ins, .badge, .entwurfshinweis, .spaltenkopf div, .vergleich > .alt,
        section.gliederung-aenderungen > h2, section.manuell > h2 {
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        }
      }
      """;
}
