// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.aenderung.parse.AenderungsantragParser.DrucksachenStelle;
import eu.mulk.aendggner.aenderung.parse.AenderungsantragParser.MetaBefehl;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Wendet die Befehle eines Änderungsantrags auf den Text eines Gesetzentwurfs an.
 *
 * <p>Gearbeitet wird auf dem Lineartext, nicht auf dem {@code Gesetz}-Modell: Ein Antrag ändert
 * einen Entwurf, und ein Entwurf ist kein Gesetz, sondern eine Folge von Änderungsbefehlen, deren
 * Zitate erst künftiges Gesetz werden sollen. Der Antrag greift in genau diese Zitate ein — „In § 3
 * Nr. 22 wird § 18 Nr. 1 wie folgt geändert: … Nr. 1.30 aufgehoben“ streicht ein Glied aus der
 * Artenliste, die der 22. Befehl des § 3 als neuen § 18 zitiert. Der so geänderte Entwurfstext
 * durchläuft anschließend unverändert die gewöhnliche Pipeline.
 *
 * <p>Was nicht sicher zuzuordnen ist, bleibt liegen und wird gemeldet; stillschweigend verworfen
 * wird nichts.
 */
public final class EntwurfsPatcher {

  private static final Logger log = Logger.getLogger(EntwurfsPatcher.class);

  private EntwurfsPatcher() {}

  /**
   * @param text der geänderte Entwurfstext.
   * @param angewandt Zahl der angewandten Antragsbefehle.
   * @param warnungen die nicht angewandten Befehle, jeweils mit Begründung.
   */
  public record Ergebnis(String text, int angewandt, List<String> warnungen) {}

  // Überschriftzeilen, die einen Entwurfscontainer eröffnen: „§ 3“, „Artikel 1“.
  private static final Pattern CONTAINER_KOPF =
      Pattern.compile("^(?:§|Art\\.|Artikel)\\s*\\d+[a-z]?$");
  // Gliederungsmarker am Zeilenanfang, mit ihrer Ebene: „22.“ → 1, „a)“ → 2, „aa)“ → 3.
  private static final Pattern NUMMER_MARKER = Pattern.compile("^(\\d+[a-z]?)\\.\\s");
  private static final Pattern BUCHSTABE_MARKER = Pattern.compile("^([a-z]{1,3}\\d*)\\)\\s");
  // Gestufte Aufzählungsmarken innerhalb eines Zitats („1.“, „1.29.“) — anders als die
  // Gliederungsmarken des Änderungsgesetzes stehen sie auch mitten in der Zeile, weil der
  // Zeilenumbruch des Satzspiegels sie zusammenzieht.
  private static final Pattern ZITAT_MARKER =
      Pattern.compile("(?<![^\\s])(\\d+(?:\\.\\d+)*)\\.(?=\\s)");

  public static Ergebnis wendeAn(String entwurfsText, List<MetaBefehl> befehle) {
    var text = entwurfsText;
    var warnungen = new ArrayList<String>();
    int angewandt = 0;

    for (var befehl : befehle) {
      var neu = wendeEinzelnAn(text, befehl, warnungen);
      if (neu != null) {
        text = neu;
        angewandt++;
      }
    }
    return new Ergebnis(text, angewandt, warnungen);
  }

  /**
   * @return der geänderte Text, oder {@code null}, wenn der Befehl nicht angewandt werden konnte.
   */
  private static @Nullable String wendeEinzelnAn(
      String text, MetaBefehl befehl, List<String> warnungen) {

    var punkt = findePunkt(text, befehl.drucksachenStelle());
    if (punkt == null) {
      warnungen.add(
          "Antragsbefehl „%s“ nicht angewandt: die Stelle %s wurde im Entwurf nicht gefunden."
              .formatted(
                  kuerze(befehl.befehl().provenienz().originalText()),
                  befehl.drucksachenStelle().anzeigeText()));
      return null;
    }
    var zitat = findeZitat(text, punkt);
    if (zitat == null) {
      warnungen.add(
          ("Antragsbefehl „%s“ nicht angewandt: %s zitiert keinen Text, in den hineingeändert"
                  + " werden könnte.")
              .formatted(
                  kuerze(befehl.befehl().provenienz().originalText()),
                  befehl.drucksachenStelle().anzeigeText()));
      return null;
    }

    var bereich = engeEin(text, zitat, befehl.zitatStelle());
    bereich = engeEin(text, bereich, befehl.befehl().stelle());
    if (bereich == null) {
      warnungen.add(
          "Antragsbefehl „%s“ nicht angewandt: das Ziel %s wurde im zitierten Text nicht gefunden."
              .formatted(
                  kuerze(befehl.befehl().provenienz().originalText()),
                  befehl.befehl().stelle().anzeigeText()));
      return null;
    }

    var ergebnis = fuehreAus(text, bereich, befehl.befehl());
    if (ergebnis == null) {
      warnungen.add(
          ("Antragsbefehl „%s“ nicht angewandt: diese Befehlsform ist für Änderungen an einer"
                  + " Drucksache noch nicht umgesetzt.")
              .formatted(kuerze(befehl.befehl().provenienz().originalText())));
      return null;
    }
    log.infof(
        "Antragsbefehl auf %s angewandt: %s",
        befehl.drucksachenStelle().anzeigeText(),
        kuerze(befehl.befehl().provenienz().originalText()));
    return ergebnis;
  }

  /** Ein halboffener Zeichenbereich [von, bis) im Entwurfstext. */
  private record Bereich(int von, int bis) {}

  // ---------------------------------------------------------------- Drucksachenstelle finden

  /**
   * Sucht den Gliederungspunkt der Drucksache und liefert seinen Zeichenbereich.
   *
   * <p>Die Suche läuft über Zeilen mit Zeichenoffsets statt über den {@link GliederungsScanner}:
   * Der liefert einen Baum, aber keine Positionen, und hier wird der Originaltext an Ort und Stelle
   * geändert.
   */
  private static @Nullable Bereich findePunkt(String text, DrucksachenStelle stelle) {
    if (stelle.istLeer()) {
      return null;
    }
    // Gesucht wird auf einer Kopie, in der die Zitate ausgeblendet sind: Der zitierte Gesetzestext
    // führt seine eigenen Aufzählungen („1. Haarwild:“), die sonst als Gliederungspunkte des
    // Entwurfs gelesen würden und den Punkt viel zu früh enden ließen. Die Kopie ist zeichengleich
    // lang, sodass alle Offsets im Originaltext gelten.
    var zeilen = zeilenMitOffsets(maskiereZitate(text));
    var bereich = containerBereich(zeilen, text.length(), stelle.container());
    if (bereich == null) {
      return null;
    }
    for (var label : stelle.punktPfad()) {
      bereich = punktBereich(zeilen, bereich, label);
      if (bereich == null) {
        return null;
      }
    }
    return bereich;
  }

  /**
   * Ersetzt jedes Zeichen innerhalb eines Zitats durch ein Leerzeichen, Zeilenumbrüche ausgenommen.
   * Das Ergebnis ist zeichengleich lang wie die Eingabe, trägt aber keine zitatinternen
   * Gliederungsmarken mehr.
   *
   * <p>Fehlt einem Zitat das schließende Anführungszeichen — im amtlichen Satz nicht selten —, so
   * endet es an der nächsten Container-Überschrift; sonst verschlänge ein einziges offenes Zitat
   * den Rest des Dokuments.
   */
  private static String maskiereZitate(String text) {
    var maskiert = new StringBuilder(text);
    int tiefe = 0;
    int zeilenAnfang = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        if (tiefe > 0
            && CONTAINER_KOPF.matcher(text.substring(zeilenAnfang, i).strip()).matches()) {
          tiefe = 0;
        }
        zeilenAnfang = i + 1;
        continue;
      }
      if (c == '„') {
        tiefe++;
        continue;
      }
      if (c == '“' && tiefe > 0) {
        tiefe--;
        continue;
      }
      if (tiefe > 0) {
        maskiert.setCharAt(i, ' ');
      }
    }
    return maskiert.toString();
  }

  private record Zeile(int von, int bis, String inhalt) {}

  private static List<Zeile> zeilenMitOffsets(String text) {
    var zeilen = new ArrayList<Zeile>();
    int von = 0;
    while (von <= text.length()) {
      int umbruch = text.indexOf('\n', von);
      int bis = umbruch < 0 ? text.length() : umbruch;
      zeilen.add(new Zeile(von, bis, text.substring(von, bis)));
      if (umbruch < 0) {
        break;
      }
      von = umbruch + 1;
    }
    return zeilen;
  }

  private static @Nullable Bereich containerBereich(
      List<Zeile> zeilen, int textEnde, String container) {
    var gesucht = container.replaceAll("\\s+", " ").strip();
    int start = -1;
    for (var zeile : zeilen) {
      var gestutzt = zeile.inhalt().strip();
      if (start < 0) {
        if (gleicherContainer(gestutzt, gesucht)) {
          start = zeile.bis(); // hinter der Überschriftzeile
        }
      } else if (CONTAINER_KOPF.matcher(gestutzt).matches()) {
        return new Bereich(start, zeile.von());
      }
    }
    return start < 0 ? null : new Bereich(start, textEnde);
  }

  /** „Artikel 3“ und „Art. 3“ bezeichnen denselben Container; „§ 3“ ist ein anderer. */
  private static boolean gleicherContainer(String zeile, String gesucht) {
    return normiereContainer(zeile).equals(normiereContainer(gesucht));
  }

  private static String normiereContainer(String container) {
    return container.replaceAll("\\s+", "").replace("Artikel", "Art.");
  }

  /** Der Bereich des Gliederungspunkts {@code label} innerhalb von {@code rahmen}. */
  private static @Nullable Bereich punktBereich(List<Zeile> zeilen, Bereich rahmen, String label) {
    int ebene = ebene(label);
    int start = -1;
    for (var zeile : zeilen) {
      if (zeile.von() < rahmen.von() || zeile.von() >= rahmen.bis()) {
        continue;
      }
      var gestutzt = zeile.inhalt().strip();
      var marker = marker(gestutzt);
      if (start < 0) {
        if (marker != null && marker.equals(label)) {
          start = zeile.von();
        }
      } else if (marker != null && ebene(marker) <= ebene) {
        return new Bereich(start, zeile.von());
      }
    }
    return start < 0 ? null : new Bereich(start, rahmen.bis());
  }

  private static @Nullable String marker(String zeile) {
    var nummer = NUMMER_MARKER.matcher(zeile);
    if (nummer.find() && nummer.start() == 0) {
      return nummer.group(1);
    }
    var buchstabe = BUCHSTABE_MARKER.matcher(zeile);
    return buchstabe.find() && buchstabe.start() == 0 ? buchstabe.group(1) : null;
  }

  private static int ebene(String label) {
    if (label.matches("\\d+[a-z]?")) {
      return 1;
    }
    return label.replaceAll("\\d", "").length() + 1; // a) → 2, aa) → 3, aaa) → 4
  }

  // ---------------------------------------------------------------- Zitat und Glieder finden

  /**
   * Der Inhalt des ersten Zitats im Punkt — der Text, den der Entwurfsbefehl zu Gesetz erheben
   * will.
   */
  private static @Nullable Bereich findeZitat(String text, Bereich punkt) {
    int auf = text.indexOf('„', punkt.von());
    if (auf < 0 || auf >= punkt.bis()) {
      return null;
    }
    int tiefe = 0;
    for (int i = auf; i < punkt.bis(); i++) {
      char c = text.charAt(i);
      if (c == '„') {
        tiefe++;
      } else if (c == '“') {
        tiefe--;
        if (tiefe == 0) {
          return new Bereich(auf + 1, i);
        }
      }
    }
    return new Bereich(auf + 1, punkt.bis());
  }

  /**
   * Verengt einen Bereich auf das von {@code stelle} bezeichnete Aufzählungsglied.
   *
   * <p>Nur die Nummern- und Buchstabenkomponenten zählen: Der Paragraph einer Zitatstelle („§ 18
   * Nr. 1“) benennt das Zitat als ganzes, das hier schon der Rahmen ist.
   */
  private static @Nullable Bereich engeEin(String text, @Nullable Bereich rahmen, Stelle stelle) {
    if (rahmen == null) {
      return null;
    }
    var bereich = rahmen;
    for (var komponente : stelle.komponenten()) {
      var label =
          switch (komponente) {
            case Stelle.NummerNr n -> n.nummer();
            case Stelle.BuchstabeNr b -> b.kennung();
            default -> null;
          };
      if (label == null) {
        continue;
      }
      var enger = gliedBereich(text, bereich, label);
      if (enger == null) {
        log.debugf(
            "Glied %s nicht gefunden in: %s",
            label,
            kuerze(text.substring(bereich.von(), Math.min(bereich.bis(), bereich.von() + 200))));
        return null;
      }
      bereich = enger;
    }
    return bereich;
  }

  /**
   * Der Bereich des Aufzählungsglieds {@code label} innerhalb von {@code rahmen}. Das Glied endet
   * am nächsten Marker, der es ablöst: ein Geschwister mit höherer Nummer oder ein übergeordnetes
   * Glied, das den Zweig verlässt.
   */
  private static @Nullable Bereich gliedBereich(String text, Bereich rahmen, String label) {
    var marker = ZITAT_MARKER.matcher(text).region(rahmen.von(), rahmen.bis());
    int start = -1;
    var eigene = teile(label);
    while (marker.find()) {
      if (start < 0) {
        if (marker.group(1).equals(label)) {
          start = marker.start();
        }
      } else if (loestAb(teile(marker.group(1)), eigene)) {
        return new Bereich(start, marker.start());
      }
    }
    return start < 0 ? null : new Bereich(start, rahmen.bis());
  }

  private static int[] teile(String label) {
    var stuecke = label.split("\\.");
    var zahlen = new int[stuecke.length];
    for (int i = 0; i < stuecke.length; i++) {
      zahlen[i] = stuecke[i].matches("\\d+") ? Integer.parseInt(stuecke[i]) : 0;
    }
    return zahlen;
  }

  /** Ob {@code kandidat} das Glied {@code eigene} ablöst, also dessen Bereich beendet. */
  private static boolean loestAb(int[] kandidat, int[] eigene) {
    if (kandidat.length > eigene.length) {
      return false; // ein Unterglied bleibt drinnen
    }
    for (int i = 0; i < kandidat.length - 1; i++) {
      if (kandidat[i] != eigene[i]) {
        return kandidat[i] > eigene[i];
      }
    }
    return kandidat[kandidat.length - 1] > eigene[kandidat.length - 1];
  }

  // ---------------------------------------------------------------- Befehle ausführen

  /**
   * @return der geänderte Gesamttext, oder {@code null} bei einer hier nicht umgesetzten Form.
   */
  private static @Nullable String fuehreAus(String text, Bereich ziel, Aenderungsbefehl befehl) {
    var abschnitt = text.substring(ziel.von(), ziel.bis());
    return switch (befehl) {
      case Aenderungsbefehl.Ersetzung e -> ersetze(text, ziel, abschnitt, e);
      case Aenderungsbefehl.Streichung s ->
          abschnitt.contains(s.woerter())
              ? ersetzeAbschnitt(
                  text, ziel, abschnitt.replace(s.woerter(), "").replaceAll("  +", " "))
              : null;
      case Aenderungsbefehl.Aufhebung a -> ersetzeAbschnitt(text, ziel, "");
      case Aenderungsbefehl.Neufassung n -> ersetzeAbschnitt(text, ziel, n.neuerText());
      default -> null;
    };
  }

  private static @Nullable String ersetze(
      String text, Bereich ziel, String abschnitt, Aenderungsbefehl.Ersetzung e) {
    if (e.amEnde()) {
      // „die Angabe „,“ am Ende“: das letzte Vorkommen vor dem abschließenden Leerraum.
      var gestutzt = abschnitt.stripTrailing();
      if (!gestutzt.endsWith(e.alt())) {
        return null;
      }
      var neu =
          gestutzt.substring(0, gestutzt.length() - e.alt().length())
              + e.neu()
              + abschnitt.substring(gestutzt.length());
      return ersetzeAbschnitt(text, ziel, neu);
    }
    if (!abschnitt.contains(e.alt())) {
      return null;
    }
    var neu =
        e.jeweils()
            ? abschnitt.replace(e.alt(), e.neu())
            : abschnitt.replaceFirst(Pattern.quote(e.alt()), Matcher.quoteReplacement(e.neu()));
    return ersetzeAbschnitt(text, ziel, neu);
  }

  private static String ersetzeAbschnitt(String text, Bereich ziel, String neu) {
    return text.substring(0, ziel.von()) + neu + text.substring(ziel.bis());
  }

  private static String kuerze(String text) {
    var einzeilig = text.replaceAll("\\s+", " ").strip();
    return einzeilig.length() <= 90 ? einzeilig : einzeilig.substring(0, 87) + "…";
  }
}
