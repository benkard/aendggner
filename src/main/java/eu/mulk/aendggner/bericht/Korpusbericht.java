// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.bericht;

import eu.mulk.aendggner.Pipeline;
import eu.mulk.aendggner.Quelle;
import eu.mulk.aendggner.anwendung.Grund;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Der Massenlauf: viele Hefte in einem Durchgang, eine Zeile Kennzahlen je Heft.
 *
 * <p>Wozu: Ein Belegfall sagt, dass <em>dieses</em> Heft aufgeht. Er sagt nicht, woran die Reste im
 * Ganzen liegen — ob an der Befehlssprache eines Landes, an der Aufbereitung des Druckwerks oder am
 * Alter der Stammfassung. Die Auszählung der Gründe (§ 1 Absatz 5 des Handbuchs) beantwortet das
 * für ein Heft; über einen Korpus geführt beantwortet sie es für das Erzeugnis.
 *
 * <p>Der Bericht ist zugleich eine Grundlinie: Wird er eingecheckt, so schlägt jeder Lauf an, bei
 * dem eine Kennzahl <em>fällt</em> — weniger erschlossene Befehle, weniger angewandte, weniger
 * gleiche Normen. Steigt eine, ist die Grundlinie fortzuschreiben; das ist der Vermerk der Welle.
 *
 * <p>Verworfen wird auch hier nichts: Ein Auftrag, der mit einem Fehler abbricht, hinterlässt seine
 * Zeile samt Fehlertext und hält den Lauf nicht an.
 */
public final class Korpusbericht {

  private Korpusbericht() {}

  /** Kein Wert — in der Liste wie im Bericht. */
  private static final String LEER = "-";

  /**
   * Ein Auftrag der Liste.
   *
   * @param bezeichnung der Name, unter dem der Auftrag im Bericht und in der Grundlinie steht.
   * @param stamm die Stammfassung, relativ zum Verzeichnis der Liste.
   * @param hefte die Änderungsdokumente, in der Reihenfolge der Liste.
   * @param artikel nur diesen Artikel anwenden; {@code null} = alle betroffenen.
   * @param nachfassung die amtliche Nachfassung; {@code null} = kein Abgleich, dann bleiben dessen
   *     Spalten leer.
   * @param stichtag die Fassung dieses Tages; {@code null} = alle Befehle anwenden.
   */
  public record Auftrag(
      String bezeichnung,
      String stamm,
      List<String> hefte,
      @Nullable String artikel,
      @Nullable String nachfassung,
      @Nullable LocalDate stichtag) {

    public Auftrag {
      hefte = List.copyOf(hefte);
    }
  }

  /**
   * Die Kennzahlen eines Laufes.
   *
   * @param fehler der Grund des Abbruchs; {@code null}, wenn der Auftrag durchgelaufen ist.
   */
  public record Zeile(
      String bezeichnung,
      int befehle,
      long angewandt,
      long manuell,
      long zurueckgestellt,
      @Nullable Integer gleich,
      @Nullable Integer geprueft,
      @Nullable Integer fehlend,
      @Nullable Integer ueberzaehlig,
      @Nullable Integer abweichend,
      Map<Grund, Integer> gruende,
      @Nullable String fehler) {

    public Zeile {
      gruende = Map.copyOf(gruende);
    }
  }

  /** Die Spaltenüberschriften des Berichts; sie stehen als Kommentarzeile obenan. */
  private static final List<String> SPALTEN =
      List.of(
          "Bezeichnung",
          "Befehle",
          "angewandt",
          "manuell",
          "zurückgestellt",
          "gleich",
          "geprüft",
          "fehlend",
          "überzählig",
          "abweichend",
          "Gründe");

  // ---------------------------------------------------------------- Liste lesen

  /**
   * Liest die Auftragsliste. Sie ist eine Tabulatortabelle mit den Spalten Bezeichnung,
   * Stammfassung, Hefte (durch Komma getrennt), Artikel, Nachfassung, Stichtag; „-“ steht für
   * „nicht angegeben“. Zeilen, die mit „#“ beginnen, und Leerzeilen werden übergangen.
   */
  public static List<Auftrag> liesListe(Path liste) throws IOException {
    var auftraege = new ArrayList<Auftrag>();
    int nummer = 0;
    for (var zeile : Files.readAllLines(liste, StandardCharsets.UTF_8)) {
      nummer++;
      if (zeile.isBlank() || zeile.startsWith("#")) {
        continue;
      }
      var felder = zeile.split("\t");
      if (felder.length < 3) {
        throw new IOException(
            "%s, Zeile %d: Ein Auftrag braucht wenigstens Bezeichnung, Stammfassung und Heft."
                .formatted(liste, nummer));
      }
      auftraege.add(
          new Auftrag(
              felder[0].strip(),
              felder[1].strip(),
              List.of(felder[2].strip().split("\\s*,\\s*")),
              feld(felder, 3),
              feld(felder, 4),
              feld(felder, 5) == null ? null : LocalDate.parse(feld(felder, 5))));
    }
    return auftraege;
  }

  private static @Nullable String feld(String[] felder, int index) {
    if (index >= felder.length) {
      return null;
    }
    var wert = felder[index].strip();
    return wert.isEmpty() || wert.equals(LEER) ? null : wert;
  }

  // ---------------------------------------------------------------- Lauf

  /**
   * Führt alle Aufträge aus.
   *
   * @param wurzel das Verzeichnis, auf das sich die Pfade der Liste beziehen.
   * @param synopsen wohin die Einzelsynopsen zu legen sind; {@code null} = keine ablegen.
   */
  public static List<Zeile> fuehreAus(
      List<Auftrag> auftraege, Path wurzel, @Nullable Path synopsen) {
    var zeilen = new ArrayList<Zeile>(auftraege.size());
    for (var auftrag : auftraege) {
      zeilen.add(fuehreAus(auftrag, wurzel, synopsen));
    }
    return zeilen;
  }

  public static Zeile fuehreAus(Auftrag auftrag, Path wurzel, @Nullable Path synopsen) {
    try {
      var hefte = new ArrayList<Quelle>(auftrag.hefte().size());
      for (var heft : auftrag.hefte()) {
        hefte.add(Quelle.lies(wurzel.resolve(heft)));
      }
      var ergebnis =
          Pipeline.erzeugeSynopse(
              new Pipeline.Auftrag(
                  Quelle.lies(wurzel.resolve(auftrag.stamm())),
                  hefte,
                  auftrag.artikel(),
                  false,
                  auftrag.stichtag(),
                  auftrag.nachfassung() == null
                      ? null
                      : Quelle.lies(wurzel.resolve(auftrag.nachfassung()))));
      if (synopsen != null) {
        Files.createDirectories(synopsen);
        Files.writeString(
            synopsen.resolve(dateiname(auftrag.bezeichnung()) + ".html"),
            ergebnis.html(),
            StandardCharsets.UTF_8);
      }
      var abgleich = ergebnis.abgleich();
      return new Zeile(
          auftrag.bezeichnung(),
          ergebnis.anzahlProtokollEintraege(),
          ergebnis.anzahlAngewandt(),
          ergebnis.anzahlManuell(),
          // Was am Stichtag noch nicht galt, ist weder angewandt noch liegengeblieben.
          ergebnis.anzahlProtokollEintraege()
              - ergebnis.anzahlAngewandt()
              - ergebnis.anzahlManuell(),
          abgleich == null ? null : abgleich.gleich(),
          abgleich == null ? null : abgleich.geprueft(),
          abgleich == null ? null : abgleich.fehlende().size(),
          abgleich == null ? null : abgleich.ueberzaehlige().size(),
          abgleich == null ? null : abgleich.abweichungen().size(),
          ergebnis.gruende(),
          null);
    } catch (Exception e) {
      // Ein gescheiterter Auftrag hält den Lauf nicht an; sein Fehler steht in seiner Zeile.
      return new Zeile(
          auftrag.bezeichnung(),
          0,
          0,
          0,
          0,
          null,
          null,
          null,
          null,
          null,
          Map.of(),
          e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static String dateiname(String bezeichnung) {
    return bezeichnung.replaceAll("[^\\p{L}\\p{N}]+", "-");
  }

  // ---------------------------------------------------------------- Ausgabe

  /** Der Bericht als Tabulatortabelle — die Form, in der er auch als Grundlinie liegt. */
  public static String alsTsv(List<Zeile> zeilen) {
    var sb = new StringBuilder("# ").append(String.join("\t", SPALTEN)).append('\n');
    for (var zeile : zeilen) {
      sb.append(zeile.bezeichnung())
          .append('\t')
          .append(zeile.befehle())
          .append('\t')
          .append(zeile.angewandt())
          .append('\t')
          .append(zeile.manuell())
          .append('\t')
          .append(zeile.zurueckgestellt())
          .append('\t')
          .append(zahl(zeile.gleich()))
          .append('\t')
          .append(zahl(zeile.geprueft()))
          .append('\t')
          .append(zahl(zeile.fehlend()))
          .append('\t')
          .append(zahl(zeile.ueberzaehlig()))
          .append('\t')
          .append(zahl(zeile.abweichend()))
          .append('\t')
          .append(gruendeText(zeile.gruende()));
      if (zeile.fehler() != null) {
        sb.append('\t').append(zeile.fehler().replace('\t', ' ').replace('\n', ' '));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static String zahl(@Nullable Integer wert) {
    return wert == null ? LEER : String.valueOf(wert);
  }

  /** „Zieltext nicht vorhanden:3; Befehl nicht erkannt:1“ — in der Reihenfolge der Gründe. */
  static String gruendeText(Map<Grund, Integer> gruende) {
    if (gruende.isEmpty()) {
      return LEER;
    }
    var geordnet = new EnumMap<>(gruende);
    var sb = new StringBuilder();
    for (var eintrag : geordnet.entrySet()) {
      if (sb.length() > 0) {
        sb.append("; ");
      }
      sb.append(eintrag.getKey().bezeichnung()).append(':').append(eintrag.getValue());
    }
    return sb.toString();
  }

  /**
   * Der Bericht als Übersichtsseite. Sie ist kein Ersatz für die Tabulatortabelle, sondern deren
   * Lesefassung: Jede Zeile verweist auf die Synopse ihres Auftrags, sodass sich ein Rest an seinem
   * eigenen Dokument nachschlagen lässt.
   *
   * @param verzeichnis das Verzeichnis der Einzelsynopsen, relativ zur Übersicht; {@code null} =
   *     ohne Verweise.
   */
  public static String alsHtml(List<Zeile> zeilen, @Nullable String verzeichnis) {
    var sb = new StringBuilder();
    sb.append("<!DOCTYPE html>\n<html lang=\"de\"><head><meta charset=\"utf-8\">\n")
        .append("<title>Korpusbericht — ÄndGgner</title>\n")
        .append("<style>\n")
        .append("body { font-family: system-ui, sans-serif; margin: 2rem; max-width: 70rem; }\n")
        .append("table { border-collapse: collapse; width: 100%; font-size: 0.85rem; }\n")
        .append("th, td { border: 1px solid #999; padding: 0.25rem 0.5rem; text-align: right; }\n")
        .append("th:first-child, td:first-child { text-align: left; }\n")
        .append("td.gruende { text-align: left; font-size: 0.8rem; }\n")
        .append("tr.rest td { background: #fff6f2; }\n")
        .append("caption { text-align: left; padding-bottom: 0.5rem; }\n")
        .append("</style></head><body>\n")
        .append("<h1>Korpusbericht</h1>\n<p>")
        .append(esc(summe(zeilen)))
        .append("</p>\n<table><thead><tr>");
    for (var spalte : SPALTEN) {
      sb.append("<th>").append(esc(spalte)).append("</th>");
    }
    sb.append("</tr></thead><tbody>\n");
    for (var zeile : zeilen) {
      sb.append(zeile.manuell() > 0 || zeile.fehler() != null ? "<tr class=\"rest\">" : "<tr>");
      sb.append("<td>");
      if (verzeichnis == null) {
        sb.append(esc(zeile.bezeichnung()));
      } else {
        sb.append("<a href=\"")
            .append(esc(verzeichnis))
            .append('/')
            .append(esc(dateiname(zeile.bezeichnung())))
            .append(".html\">")
            .append(esc(zeile.bezeichnung()))
            .append("</a>");
      }
      sb.append("</td>");
      for (var wert :
          List.of(
              String.valueOf(zeile.befehle()),
              String.valueOf(zeile.angewandt()),
              String.valueOf(zeile.manuell()),
              String.valueOf(zeile.zurueckgestellt()),
              zahl(zeile.gleich()),
              zahl(zeile.geprueft()),
              zahl(zeile.fehlend()),
              zahl(zeile.ueberzaehlig()),
              zahl(zeile.abweichend()))) {
        sb.append("<td>").append(esc(wert)).append("</td>");
      }
      sb.append("<td class=\"gruende\">")
          .append(esc(zeile.fehler() != null ? zeile.fehler() : gruendeText(zeile.gruende())))
          .append("</td></tr>\n");
    }
    return sb.append("</tbody></table>\n</body></html>\n").toString();
  }

  private static String esc(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  /** Die Summe über den Korpus — die Zahl, die eine Welle vor und nach sich nennt. */
  public static String summe(List<Zeile> zeilen) {
    long befehle = zeilen.stream().mapToLong(Zeile::befehle).sum();
    long angewandt = zeilen.stream().mapToLong(Zeile::angewandt).sum();
    long manuell = zeilen.stream().mapToLong(Zeile::manuell).sum();
    long gleich = zeilen.stream().filter(z -> z.gleich() != null).mapToLong(Zeile::gleich).sum();
    long geprueft =
        zeilen.stream().filter(z -> z.geprueft() != null).mapToLong(Zeile::geprueft).sum();
    long gescheitert = zeilen.stream().filter(z -> z.fehler() != null).count();
    var sb =
        new StringBuilder(
            "%d Aufträge, %d Befehle, %d angewandt, %d manuell; %d von %d Normen gleich"
                .formatted(zeilen.size(), befehle, angewandt, manuell, gleich, geprueft));
    if (gescheitert > 0) {
      sb.append("; %d Aufträge gescheitert".formatted(gescheitert));
    }
    return sb.toString();
  }

  // ---------------------------------------------------------------- Grundlinie

  /**
   * Hält den Lauf gegen die Grundlinie und meldet jede Kennzahl, die <em>gefallen</em> ist.
   *
   * <p>Nur der Rückschritt wird gerügt. Ein Fortschritt ist kein Fehler, sondern der Zweck der
   * Arbeit; er verlangt allein, die Grundlinie fortzuschreiben.
   *
   * @return die Rügen; leer, wenn nichts gefallen ist.
   */
  public static List<String> gegenGrundlinie(List<Zeile> lauf, String grundlinie) {
    var alt = liesGrundlinie(grundlinie);
    var ruegen = new ArrayList<String>();
    for (var zeile : lauf) {
      if (zeile.fehler() != null) {
        ruegen.add("%s: gescheitert — %s".formatted(zeile.bezeichnung(), zeile.fehler()));
        continue;
      }
      var frueher = alt.get(zeile.bezeichnung());
      if (frueher == null) {
        // Ein neuer Auftrag ist kein Rückschritt; die Grundlinie ist bloß noch nicht
        // fortgeschrieben.
        continue;
      }
      pruefe(
          ruegen, zeile.bezeichnung(), "erschlossene Befehle", zeile.befehle(), frueher[1], true);
      pruefe(
          ruegen, zeile.bezeichnung(), "angewandte Befehle", zeile.angewandt(), frueher[2], true);
      pruefe(
          ruegen,
          zeile.bezeichnung(),
          "liegengebliebene Befehle",
          zeile.manuell(),
          frueher[3],
          false);
      pruefe(ruegen, zeile.bezeichnung(), "gleiche Normen", wert(zeile.gleich()), frueher[5], true);
      pruefe(
          ruegen, zeile.bezeichnung(), "fehlende Normen", wert(zeile.fehlend()), frueher[7], false);
      pruefe(
          ruegen,
          zeile.bezeichnung(),
          "überzählige Normen",
          wert(zeile.ueberzaehlig()),
          frueher[8],
          false);
      pruefe(
          ruegen,
          zeile.bezeichnung(),
          "abweichende Normen",
          wert(zeile.abweichend()),
          frueher[9],
          false);
    }
    for (var bezeichnung : alt.keySet()) {
      if (lauf.stream().noneMatch(z -> z.bezeichnung().equals(bezeichnung))) {
        ruegen.add(
            "%s: steht in der Grundlinie, ist im Lauf aber nicht vorgekommen."
                .formatted(bezeichnung));
      }
    }
    return ruegen;
  }

  private static long wert(@Nullable Integer zahl) {
    return zahl == null ? Long.MIN_VALUE : zahl;
  }

  /**
   * @param hoeherIstBesser ob ein Fallen der Zahl den Rückschritt bedeutet (angewandte Befehle,
   *     gleiche Normen) oder ihr Steigen (liegengebliebene, abweichende).
   */
  private static void pruefe(
      List<String> ruegen,
      String bezeichnung,
      String kennzahl,
      long jetzt,
      String frueherText,
      boolean hoeherIstBesser) {
    if (frueherText.equals(LEER) || jetzt == Long.MIN_VALUE) {
      return;
    }
    long frueher;
    try {
      frueher = Long.parseLong(frueherText);
    } catch (NumberFormatException e) {
      return;
    }
    boolean rueckschritt = hoeherIstBesser ? jetzt < frueher : jetzt > frueher;
    if (rueckschritt) {
      ruegen.add("%s: %s %d statt %d.".formatted(bezeichnung, kennzahl, jetzt, frueher));
    }
  }

  private static Map<String, String[]> liesGrundlinie(String tsv) {
    var zeilen = new LinkedHashMap<String, String[]>();
    for (var zeile : tsv.split("\n")) {
      if (zeile.isBlank() || zeile.startsWith("#")) {
        continue;
      }
      var felder = zeile.split("\t");
      if (felder.length >= 10) {
        zeilen.put(felder[0].strip(), felder);
      }
    }
    return zeilen;
  }
}
