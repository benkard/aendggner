// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.synopse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.parse.DeutschesDatum;
import eu.mulk.aendggner.anwendung.BefehlAnwender;
import eu.mulk.aendggner.anwendung.Grund;
import eu.mulk.aendggner.gesetz.Norm;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
        .append("</style>\n</head>\n<body>\n<div class=\"bogen\">\n");

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

    rendereAbgleich(sb, synopse);

    rendereManuellZuPruefen(sb, synopse);

    rendereFuss(sb);

    sb.append("</div>\n</body>\n</html>\n");
    return sb.toString();
  }

  /**
   * Der Kopf des Vordrucks ÄG-2: Titel, ausstellende Stelle, darunter der Vorspann als
   * Beschriftungsstreifen — grau, was die Stelle gedruckt hat, weiß die Eintragung daneben.
   */
  private static void rendereKopf(
      StringBuilder sb, Synopse synopse, String quelle, boolean entwurfsfassung) {
    sb.append("<header>\n<h1>Synopse zum Stammgesetz ")
        .append(esc(synopse.alt().jurabk()))
        .append("</h1>\n<p class=\"stelle\">")
        .append(LANGNAME)
        .append("</p>\n</header>\n");

    if (entwurfsfassung) {
      sb.append(
          "<p class=\"entwurfshinweis\"><strong>Entwurfsfassung — nicht geltendes Recht.</strong>"
              + " Die neue Fassung gibt den Stand des Gesetzgebungsverfahrens wieder.</p>\n");
    }

    long geaendert =
        synopse.eintraege().stream()
            .filter(e -> e.art() != Synopse.Aenderungsart.UNVERAENDERT)
            .count();
    sb.append("<dl class=\"vorspann\">\n");
    vorspannZeile(sb, "Stammgesetz", synopse.alt().jurabk());
    if (synopse.alt().langue() != null) {
      vorspannZeile(sb, "Langbezeichnung", synopse.alt().langue());
    }
    vorspannZeile(sb, "Grundlage", quelle);
    vorspannZeile(sb, "Erstellt am", LocalDate.now().toString());
    vorspannZeile(
        sb,
        "Umfang",
        geaendert
            + " geänderte Normen, "
            + synopse.manuellZuPruefen().size()
            + " manuell zu prüfende Befehle");
    if (synopse.inkrafttreten() != null) {
      var grundregel = synopse.inkrafttreten().grundregel();
      if (grundregel.isPresent()) {
        vorspannZeile(
            sb,
            "Inkrafttreten",
            grundregel.get().anzeige()
                + (synopse.inkrafttreten().gestaffelt() ? " (gestaffelt, siehe unten)" : ""));
      }
    }
    if (synopse.stichtag() != null) {
      vorspannZeile(sb, "Stichtag", DeutschesDatum.schreibe(synopse.stichtag()));
    }
    vorspannZeile(sb, "Geltung", entwurfsfassung ? "nichtamtlich, Entwurfsstand" : "nichtamtlich");
    sb.append("</dl>\n");
  }

  private static void vorspannZeile(StringBuilder sb, String bezeichnung, String wert) {
    sb.append("<dt>")
        .append(esc(bezeichnung))
        .append("</dt><dd>")
        .append(esc(wert))
        .append("</dd>\n");
  }

  /**
   * Der Fußsteg trägt wie im Muster die Vordrucknummer links und den Ausgabestand rechts. Der
   * Vorbehalt gehört gerade hierher und nicht bloß auf die Aufgabeseite: Die Synopse wird
   * ausgedruckt und weitergereicht, das Formular nicht.
   */
  private static void rendereFuss(StringBuilder sb) {
    sb.append("<footer>\n<span class=\"vordrucknummer\">ÄG&#8209;2</span>\n")
        .append(
            "<span>Erstellt mit ÄndGgner. Ohne Gewähr; maßgeblich ist allein die amtliche"
                + " Verkündung im jeweiligen Gesetz- oder Verordnungsblatt.</span>\n")
        .append("<span class=\"ausgabe\">")
        .append(LocalDate.now())
        .append("</span>\n</footer>\n");
  }

  private static final String LANGNAME =
      "ÄndGgner — nichtamtliche Zentralstelle für die maschinelle Fortschreibung von Stammgesetzen"
          + " anhand von Änderungsvorschriften des Bundes und der Länder";

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

  /**
   * Der Abgleich mit der amtlichen Nachfassung. Er steht vor dem Abschnitt „Manuell prüfen“, denn
   * er beantwortet die vorrangige Frage: Nicht, ob jeder Befehl angewandt wurde, entscheidet über
   * die Richtigkeit, sondern ob der Wortlaut hinterher derselbe ist. Die Abweichungen werden
   * wortweise gezeigt — links, was amtlich steht, rechts, was errechnet wurde.
   */
  private static void rendereAbgleich(StringBuilder sb, Synopse synopse) {
    var abgleich = synopse.abgleich();
    if (abgleich == null) {
      return;
    }
    sb.append("<section class=\"abgleich\">\n<h2>Abgleich mit der amtlichen Nachfassung</h2>\n");
    sb.append("<p class=\"bilanz")
        .append(abgleich.gehtAuf() ? " geht-auf" : "")
        .append("\">")
        .append(esc(abgleich.kurzbericht()))
        .append("</p>\n");
    liste(sb, "Im Erzeugnis fehlende Normen", abgleich.fehlende());
    liste(sb, "Im Erzeugnis überzählige Normen", abgleich.ueberzaehlige());
    if (!abgleich.abweichungen().isEmpty()) {
      sb.append("<h3>Abweichender Wortlaut <span class=\"anzahl\">")
          .append(abgleich.abweichungen().size())
          .append("</span></h3>\n<div class=\"gegenueberstellung\">\n")
          .append("<div class=\"spaltenkopf\"><div>Amtliche Nachfassung</div>")
          .append("<div>Errechnete Fassung</div></div>\n");
      for (var abweichung : abgleich.abweichungen()) {
        var spalten = WortDiff.vergleiche(abweichung.soll(), abweichung.ist());
        sb.append("<section class=\"norm geaendert\">\n<h2>")
            .append(esc(abweichung.enbez()))
            .append("</h2>\n<div class=\"vergleich\">\n<div class=\"alt\">")
            .append(spalten.altHtml())
            .append("</div>\n<div class=\"neu\">")
            .append(spalten.neuHtml())
            .append("</div>\n</div>\n</section>\n");
      }
      sb.append("</div>\n");
    }
    sb.append("</section>\n");
  }

  private static void liste(StringBuilder sb, String ueberschrift, List<String> posten) {
    if (posten.isEmpty()) {
      return;
    }
    sb.append("<h3>")
        .append(esc(ueberschrift))
        .append(" <span class=\"anzahl\">")
        .append(posten.size())
        .append("</span></h3>\n<ul>\n");
    for (var posten1 : posten) {
      sb.append("<li>").append(esc(posten1)).append("</li>\n");
    }
    sb.append("</ul>\n");
  }

  private static void rendereManuellZuPruefen(StringBuilder sb, Synopse synopse) {
    if (synopse.manuellZuPruefen().isEmpty()
        && synopse.warnungen().isEmpty()
        && synopse.nichtInKraft().isEmpty()) {
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
      sb.append("<h3>Nicht automatisch angewandte Befehle</h3>\n");
      // Nach Art des Grundes gebündelt und ausgezählt. Der ausformulierte Grund bleibt bei jedem
      // Befehl stehen — gebündelt ist nur, was ohne Ordnung eine bloße Liste wäre. Innerhalb der
      // Gruppe bleibt die Reihenfolge des Dokuments.
      var gruppen = new LinkedHashMap<Grund, List<BefehlAnwender.AngewandteAenderung>>();
      for (var grund : Grund.values()) {
        var eintraege =
            synopse.manuellZuPruefen().stream().filter(e -> e.grund() == grund).toList();
        if (!eintraege.isEmpty()) {
          gruppen.put(grund, eintraege);
        }
      }
      for (var gruppe : gruppen.entrySet()) {
        sb.append("<h4>")
            .append(esc(gruppe.getKey().bezeichnung()))
            .append(" <span class=\"anzahl\">")
            .append(gruppe.getValue().size())
            .append("</span></h4>\n<ol>\n");
        for (var eintrag : gruppe.getValue()) {
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
    }
    if (!synopse.nichtInKraft().isEmpty()) {
      // Nicht angewandt, aber auch nicht zu beanstanden: Diese Befehle galten am Stichtag noch
      // nicht. Sie stehen deshalb für sich und nicht unter den Gründen des Scheiterns.
      sb.append("<h3>Am Stichtag noch nicht in Kraft <span class=\"anzahl\">")
          .append(synopse.nichtInKraft().size())
          .append("</span></h3>\n<ol>\n");
      for (var eintrag : synopse.nichtInKraft()) {
        sb.append("<li><strong>")
            .append(esc(eintrag.befehl().provenienz().anzeigeText()))
            .append("</strong> — ")
            .append(esc(eintrag.begruendung()))
            .append("<br><span class=\"originaltext\">")
            .append(esc(eintrag.befehl().provenienz().originalText()))
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
      /* Der Vordruck ÄG-2, gesetzt nach demselben Muster wie die Aufgabeseite (siehe
         src/main/resources/eu/mulk/aendggner/web/style.css): weißes Papier, schwarze
         Haarlinien, grauer Raster für alles, was die Stelle gedruckt hat. Das Farbgerüst
         wird hier ein zweites Mal geführt, weil diese Datei aus einem blob:-Verweis
         geöffnet und im Befehlszeilenbetrieb als einzelne Datei abgelegt wird; sie kann
         kein fremdes Stilblatt einbinden. Wer dort etwas ändert, ändere es hier mit.

         Die Schrift ist geteilt: Der Vordruck steht in der Groteske, der Wortlaut der
         Normen in der Antiqua. Amtskopf und Vorschrift sind zweierlei, im Gesetzblatt wie
         hier.

         Schmuckfarbe gibt es nicht. Farbe trägt allein die Änderung: rot heißt weg, blau
         heißt hinzu. Rot gegen Blau und nicht das naheliegendere Rot gegen Grün, weil
         die Hinterlegung für sich allein tragen soll: Rot und Grün sind gerade das Paar,
         das bei Protanopie und Deuteranopie zusammenfällt — beide erscheinen als
         dasselbe blasse Beige. Blau bleibt Blau, wo Rot zu Beige wird, und ist zugleich
         merklich dunkler; die Hinterlegungen sind damit nach Farbort wie nach Helligkeit
         geschieden und brauchen keine Unterstreichung als Krücke. Die Durchstreichung
         der Streichungen bleibt gleichwohl: Sie ist beim gestrichenen Wort die übliche
         Auszeichnung und trägt den Schwarzweiß-Ausdruck. */
      :root {
        color-scheme: light dark;
        --fg: #000;
        --papier: #fff;
        --feld: #fff;
        --grund: #d5d5d5;
        --tint: #e4e4e4;
        --linie: #000;
        --muted: #444;
        --tisch: #9a9a9a;
        --del-bg: #ffdad6;
        --del-fg: #8b0000;
        --ins-bg: #a8ccff;
        --ins-fg: #003a75;
        --grotesk: Arial, "Helvetica Neue", Helvetica, system-ui, sans-serif;
        --antiqua: Georgia, "Times New Roman", serif;
      }
      /* Im Dunkeln kehrt sich nicht die Farbe um, sondern das Verhältnis: Der Grund der
         Stelle bleibt heller als das Feld. */
      @media (prefers-color-scheme: dark) {
        :root {
          --fg: #e8e8e8;
          --papier: #1a1a1a;
          --feld: #101010;
          --grund: #333333;
          --tint: #242424;
          --linie: #7a7a7a;
          --muted: #a8a8a8;
          --tisch: #000;
          --del-bg: #5a2320;
          --del-fg: #ffb3b3;
          --ins-bg: #1b3f74;
          --ins-fg: #b3d4ff;
        }
      }
      * { box-sizing: border-box; }
      html { background: var(--tisch); }
      body {
        counter-reset: norm;
        margin: 0;
        padding: 1.5rem 1rem;
        font-family: var(--grotesk);
        font-size: 0.8125rem;
        line-height: 1.4;
        color: var(--fg);
      }
      /* Ein Blatt Papier hat keinen Rahmen; die Kante entsteht aus dem Farbunterschied zur
         Unterlage. Die Breite ist größer als beim Antrag, weil zwei Spalten sie brauchen. */
      .bogen {
        max-width: 88rem;
        margin: 0 auto;
        padding: 1.75rem 1.75rem 0.75rem;
        background: var(--papier);
      }
      @media (max-width: 40rem) {
        body { padding: 0; }
        .bogen { padding: 1rem 0.75rem 0.5rem; }
      }

      h1 { margin: 0 0 0.5rem; font-size: 1rem; line-height: 1.35; }
      .stelle { margin: 0 0 0.9rem; font-size: 0.6875rem; color: var(--muted); }

      .entwurfshinweis {
        margin: 0 0 0.9rem;
        padding: 0.45rem 0.6rem;
        border: 1px solid var(--linie);
        border-left: 4px solid var(--del-fg);
        background: var(--feld);
        font-size: 0.75rem;
      }

      /* Der Vorspann ist der Beschriftungsstreifen des Musters: links grau die Bezeichnung,
         rechts weiß die Eintragung, Zelle an Zelle ohne Abstand. */
      .vorspann {
        display: grid;
        grid-template-columns: 10rem 1fr;
        margin: 0;
        border-top: 1px solid var(--linie);
        border-left: 1px solid var(--linie);
        font-size: 0.6875rem;
      }
      .vorspann dt {
        padding: 0.2rem 0.5rem;
        border-right: 1px solid var(--linie);
        border-bottom: 1px solid var(--linie);
        background: var(--grund);
        font-weight: bold;
      }
      .vorspann dd {
        margin: 0;
        padding: 0.2rem 0.5rem;
        border-right: 1px solid var(--linie);
        border-bottom: 1px solid var(--linie);
        background: var(--feld);
        overflow-wrap: anywhere;
      }

      /* Der Spaltenkopf steht mit der Gegenüberstellung in einem Block: Sein Klebebereich
         reicht damit über alle Normen und endet vor dem Abschnitt „Manuell prüfen“. Seine
         beiden Felder sind Beschriftungsstreifen wie im Vordruck, also grau. */
      .spaltenkopf {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0.75rem;
        position: sticky;
        top: 0;
        z-index: 1;
        margin-top: 1.25rem;
        padding: 0.5rem 0 0.5rem 2.5rem;
        background: var(--papier);
      }
      .spaltenkopf div {
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--linie);
        background: var(--grund);
        font-size: 0.6875rem;
        font-weight: bold;
      }

      /* Der Steg links trägt die Randziffer in einem Kästchen, wie der Antrag das
         Ankreuzfeld; sie macht die Abschnitte überdies zitierbar. */
      section { position: relative; padding-left: 2.5rem; }
      section.norm {
        counter-increment: norm;
        padding-top: 0.9rem;
        padding-bottom: 0.9rem;
        border-bottom: 1px solid var(--linie);
      }
      section.norm::before {
        content: counter(norm);
        position: absolute;
        top: 1rem;
        left: 0;
        width: 1.5rem;
        height: 1.2rem;
        border: 1px solid var(--linie);
        background: var(--grund);
        font-size: 0.6875rem;
        font-weight: bold;
        line-height: 1.1rem;
        text-align: center;
      }
      section.norm h2 { font-size: 0.8125rem; margin: 0 0 0.15rem; }
      .gliederung, .ursachen { color: var(--muted); font-size: 0.6875rem; margin: 0.1rem 0; }

      .vergleich {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0.75rem;
        align-items: start;
        margin-top: 0.5rem;
      }
      /* Beide Fassungen stehen auf demselben weißen Feld. Welche Spalte welche ist, sagt
         der Spaltenkopf; ihn durch zweierlei Grund zu wiederholen, kostete nur den Abstand,
         den die Hinterlegungen der Änderungen zum Grund haben. */
      .vergleich > div {
        padding: 0.5rem 0.6rem;
        border: 1px solid var(--linie);
        white-space: pre-wrap;
        overflow-wrap: anywhere;
        font-family: var(--antiqua);
        font-size: 0.875rem;
        line-height: 1.45;
        background: var(--feld);
      }

      del, .alt del {
        background: var(--del-bg);
        color: var(--del-fg);
        text-decoration: line-through;
      }
      /* text-decoration: none ist keine Zierde, sondern nötig: Die Unterstreichung von <ins>
         steht im Stilblatt des Browsers und bliebe sonst stehen. */
      ins, .neu ins {
        background: var(--ins-bg);
        color: var(--ins-fg);
        text-decoration: none;
      }
      .leer { font-family: var(--grotesk); font-size: 0.75rem; color: var(--muted); }

      /* Kästchen wie die Ankreuzfelder des Antrags: eckig, haarfein umrandet. Beschriftet
         sind sie ohnehin, sodass die Farbe nur bestätigt, was dasteht. */
      .badge {
        font-family: var(--grotesk);
        font-size: 0.625rem;
        font-weight: normal;
        text-transform: uppercase;
        border: 1px solid var(--linie);
        padding: 0 0.4rem;
        vertical-align: middle;
        white-space: nowrap;
      }
      .neu-badge { background: var(--ins-bg); color: var(--ins-fg); }
      .aufgehoben-badge { background: var(--del-bg); color: var(--del-fg); }
      .geaendert-badge { background: var(--grund); color: var(--fg); }

      /* Die Abschnittsüberschriften sind Beschriftungsstreifen über die volle Breite, wie im
         Muster die Zeile „Verfügung des Finanzamts“. */
      section.gliederung-aenderungen > h2, section.manuell > h2, section.abgleich > h2 {
        margin: 0 0 0.6rem;
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--linie);
        background: var(--grund);
        font-size: 0.75rem;
        text-align: center;
      }
      section.gliederung-aenderungen { padding-top: 1.25rem; }
      section.manuell { margin-top: 1.5rem; padding-top: 1.25rem; }
      section.manuell h3 { margin: 1rem 0 0.4rem; font-size: 0.6875rem; }
      /* Die Gruppenaufschrift trägt ihre Häufigkeit in einem Kästchen wie die Randziffer der
         Normen — sie ist eine Zahl und keine Zierde. */
      section.manuell h4 {
        margin: 0.9rem 0 0.3rem;
        font-size: 0.6875rem;
        font-weight: bold;
        color: var(--muted);
      }
      section.manuell h4 .anzahl {
        display: inline-block;
        min-width: 1.5rem;
        margin-left: 0.35rem;
        padding: 0 0.25rem;
        border: 1px solid var(--linie);
        background: var(--grund);
        color: var(--fg);
        text-align: center;
      }
      section.manuell li { margin-bottom: 0.6rem; font-size: 0.75rem; }

      /* Der Abgleich mit der amtlichen Nachfassung. Seine Bilanz ist die Zahl, auf die es
         ankommt; sie steht deshalb im Kasten und nicht im Fließtext. Geht sie auf, so wird das
         nicht durch Farbe gefeiert — ein Formblatt jubelt nicht —, sondern durch Fettung. */
      section.abgleich { margin-top: 1.5rem; padding-top: 1.25rem; }
      section.abgleich h3 { margin: 1rem 0 0.4rem; font-size: 0.6875rem; }
      section.abgleich h3 .anzahl {
        display: inline-block;
        min-width: 1.5rem;
        margin-left: 0.35rem;
        padding: 0 0.25rem;
        border: 1px solid var(--linie);
        background: var(--grund);
        text-align: center;
      }
      section.abgleich li { font-size: 0.75rem; }
      .bilanz {
        margin: 0 0 0.6rem;
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--linie);
        font-size: 0.75rem;
        text-align: center;
      }
      .bilanz.geht-auf { font-weight: bold; }
      .originaltext { font-family: var(--antiqua); color: var(--muted); font-size: 0.8125rem; }

      /* Vordrucknummer links, Ausgabestand rechts, beide winzig und ohne Zierrat. */
      footer {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.4rem 1rem;
        margin-top: 1.5rem;
        padding: 0.4rem 0 0.6rem;
        font-size: 0.625rem;
        color: var(--muted);
      }
      .vordrucknummer, .ausgabe { white-space: nowrap; }

      /* Zwei Spalten Gesetzestext nebeneinander sind auf dem Telefon nicht zu lesen. Gestapelt
         trägt der Spaltenkopf nichts mehr; deshalb beschriftet sich dort jede Spalte selbst. */
      @media (max-width: 60rem) {
        .spaltenkopf { display: none; }
        .vergleich { grid-template-columns: 1fr; }
        .vorspann { grid-template-columns: 7rem 1fr; }
        section { padding-left: 2rem; }
        .vergleich > div::before {
          display: block;
          margin: -0.5rem -0.6rem 0.4rem;
          padding: 0.15rem 0.6rem;
          border-bottom: 1px solid var(--linie);
          background: var(--grund);
          font-family: var(--grotesk);
          font-size: 0.625rem;
          font-weight: bold;
        }
        .vergleich > .alt::before { content: "Alte Fassung"; }
        .vergleich > .neu::before { content: "Neue Fassung"; }
      }

      /* Gedruckt wird stets auf weißes Papier, auch aus einem dunkel eingestellten Browser
         heraus: prefers-color-scheme gilt im Druck fort. Deshalb ist das ganze Farbgerüst
         zurückzusetzen und nicht nur ein Teil davon — sonst käme die alte Spalte als
         schwarzer Block aus dem Drucker. */
      @media print {
        :root {
          --fg: #000;
          --papier: #fff;
          --feld: #fff;
          --grund: #d5d5d5;
          --tint: #e4e4e4;
          --linie: #000;
          --muted: #333;
          --tisch: #fff;
          --del-bg: #ffdad6;
          --del-fg: #8b0000;
          --ins-bg: #a8ccff;
          --ins-fg: #003a75;
        }
        body { padding: 0; font-size: 9pt; }
        .bogen { max-width: none; padding: 0; }
        .spaltenkopf { position: static; }
        .vergleich, section.norm { break-inside: avoid; }
        /* Ohne diese Festlegung wirft der Druck die Hinterlegung weg. Die Durchstreichung
           übersteht ihn ohnehin und trägt die Streichung auch dort, wo schwarzweiß gedruckt
           wird; die Einfügung aber hat keine solche zweite Gestalt mehr, und der Raster der
           Stelle hatte nie eine — er sagt, was gedruckt und was eingetragen ist. */
        del, ins, .badge, .entwurfshinweis, .spaltenkopf div,
        .vorspann dt, section.norm::before,
        section.gliederung-aenderungen > h2, section.manuell > h2, section.abgleich > h2 {
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        }
      }
      """;
}
