package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Löst eine {@link Stelle} gegen ein {@link Gesetz} zu einer konkreten Fundstelle auf: Norm, Absatz
 * und — bei Satz-/Nummer-/Buchstaben-Angaben — Zeichenbereich im Absatztext.
 */
final class StellenAufloeser {

  /**
   * @param normIndex Index der Norm in {@code gesetz.normen()}.
   * @param absatzIndex Index des Absatzes; {@code null}, wenn die Stelle die ganze Norm meint.
   * @param bereich Zeichenbereich im Absatztext; {@code null}, wenn der ganze Absatz gemeint ist.
   */
  record Fundstelle(
      int normIndex, @Nullable Integer absatzIndex, SatzTeiler.@Nullable SatzBereich bereich) {}

  sealed interface Ergebnis {
    record Gefunden(Fundstelle fundstelle) implements Ergebnis {}

    record NichtGefunden(String begruendung) implements Ergebnis {}
  }

  private StellenAufloeser() {}

  static Ergebnis aufloese(Gesetz gesetz, Stelle stelle) {
    if (stelle.istLeer()) {
      return new Ergebnis.NichtGefunden("Stelle nennt keine Fundstelle im Gesetz.");
    }

    // 1. Norm bestimmen.
    String enbez;
    if (stelle.betrifftInhaltsuebersicht()) {
      enbez = "Inhaltsübersicht";
    } else if (stelle.paragraph().isPresent()) {
      enbez = stelle.paragraph().get().enbez();
    } else if (stelle.anlagenEnbez().isPresent()) {
      enbez = stelle.anlagenEnbez().get();
    } else {
      return new Ergebnis.NichtGefunden("Stelle nennt keinen Paragraphen: " + stelle.anzeigeText());
    }
    int normIndex = normIndex(gesetz, enbez);
    if (normIndex < 0) {
      return new Ergebnis.NichtGefunden(enbez + " existiert nicht im Gesetz.");
    }
    var norm = gesetz.normen().get(normIndex);

    // 2. Absatz bestimmen.
    Integer absatzIndex = null;
    if (stelle.absatz().isPresent()) {
      var nummer = stelle.absatz().get().nummer();
      absatzIndex = absatzIndex(norm, nummer);
      if (absatzIndex < 0) {
        return new Ergebnis.NichtGefunden(enbez + " hat keinen Absatz " + nummer + ".");
      }
    }

    // 3. Feinere Komponenten (Satz bzw. Nummer/Buchstabe-Kette) als Textbereich auflösen.
    if (!hatFeinKomponente(stelle)) {
      return new Ergebnis.Gefunden(new Fundstelle(normIndex, absatzIndex, null));
    }

    if (absatzIndex == null) {
      if (norm.absaetze().size() == 1) {
        absatzIndex = 0;
      } else {
        // Ohne Absatzangabe (z.B. „Anhang Nummer 2“): die Komponente muss norm-weit in genau
        // einem Absatz auffindbar sein.
        Integer trefferAbsatz = null;
        SatzTeiler.SatzBereich trefferBereich = null;
        for (int i = 0; i < norm.absaetze().size(); i++) {
          var kandidat = loeseFeinKomponentenAuf(stelle, norm.absaetze().get(i).text());
          if (kandidat != null) {
            if (trefferAbsatz != null) {
              return new Ergebnis.NichtGefunden(
                  enbez
                      + " hat "
                      + norm.absaetze().size()
                      + " Absätze; „"
                      + stelle.anzeigeText()
                      + "“ ist ohne Absatzangabe nicht eindeutig.");
            }
            trefferAbsatz = i;
            trefferBereich = kandidat;
          }
        }
        if (trefferAbsatz == null) {
          return new Ergebnis.NichtGefunden(
              "„" + stelle.anzeigeText() + "“ ist im Text von " + enbez + " nicht auffindbar.");
        }
        return new Ergebnis.Gefunden(new Fundstelle(normIndex, trefferAbsatz, trefferBereich));
      }
    }

    var text = norm.absaetze().get(absatzIndex).text();
    var bereich = loeseFeinKomponentenAuf(stelle, text);
    if (bereich == null) {
      return new Ergebnis.NichtGefunden(
          "„" + stelle.anzeigeText() + "“ ist im Text von " + enbez + " nicht auffindbar.");
    }
    return new Ergebnis.Gefunden(new Fundstelle(normIndex, absatzIndex, bereich));
  }

  static int normIndex(Gesetz gesetz, String enbez) {
    var normen = gesetz.normen();
    for (int i = 0; i < normen.size(); i++) {
      if (normen.get(i).enbez().equals(enbez)) {
        return i;
      }
    }
    return -1;
  }

  static int absatzIndex(Norm norm, String nummer) {
    var absaetze = norm.absaetze();
    for (int i = 0; i < absaetze.size(); i++) {
      if (nummer.equals(absaetze.get(i).nummer())) {
        return i;
      }
    }
    // Unnummerierter Einzelabsatz gilt als „Absatz 1“.
    if (absaetze.size() == 1 && absaetze.get(0).nummer() == null && nummer.equals("1")) {
      return 0;
    }
    return -1;
  }

  private static boolean hatFeinKomponente(Stelle stelle) {
    return stelle.komponenten().stream()
        .anyMatch(
            k ->
                k instanceof Stelle.SatzNr
                    || k instanceof Stelle.HalbsatzNr
                    || k instanceof Stelle.NummerNr
                    || k instanceof Stelle.BuchstabeNr);
  }

  /**
   * Löst die feineren Komponenten der Stelle zu einem Textbereich auf. Nummern/Buchstaben werden
   * als Kette verschachtelt gesucht („Nummer 31 Buchstabe b“: erst der Block der Nummer 31, darin
   * der Buchstabe b) — der Bereich einer Einheit umfasst ihre Aufzählungszeile samt der tiefer
   * eingerückten Kindzeilen. Ohne Nummern/Buchstaben zählt eine Satzangabe. Liefert {@code null},
   * wenn ein Glied nicht oder nicht eindeutig auffindbar ist.
   */
  private static SatzTeiler.@Nullable SatzBereich loeseFeinKomponentenAuf(
      Stelle stelle, String text) {
    SatzTeiler.SatzBereich bereich = null;
    boolean zeilenKette = false;
    for (var komponente : stelle.komponenten()) {
      String labelRegex =
          switch (komponente) {
            case Stelle.NummerNr nummer -> Pattern.quote(nummer.nummer()) + "\\.";
            case Stelle.BuchstabeNr buchstabe -> Pattern.quote(buchstabe.kennung()) + "\\)";
            default -> null;
          };
      if (labelRegex == null) {
        continue;
      }
      bereich =
          zeilenBlock(
              text,
              labelRegex,
              bereich != null ? bereich : new SatzTeiler.SatzBereich(0, text.length()));
      if (bereich == null) {
        return null;
      }
      zeilenKette = true;
    }
    if (zeilenKette) {
      return bereich;
    }
    for (var komponente : stelle.komponenten()) {
      if (komponente instanceof Stelle.SatzNr satz) {
        int nummer = Integer.parseInt(satz.nummer().replaceAll("[a-z]$", ""));
        var saetze = SatzTeiler.teile(text);
        var satzBereich = satzBereich(text, nummer, saetze);
        return satzBereich == null ? null : halbsatzBereich(text, stelle, satzBereich);
      }
    }
    // Halbsatz ohne Satzangabe („in Halbsatz 1“ im Rahmen eines Satzes bzw. Absatzes).
    if (stelle.komponenten().stream().anyMatch(k -> k instanceof Stelle.HalbsatzNr)) {
      return halbsatzBereich(text, stelle, new SatzTeiler.SatzBereich(0, text.length()));
    }
    return null;
  }

  /**
   * Der Bereich des Satzes mit der gegebenen Nummer. Amtlich nummerierte Sätze (bayerisches
   * Landesrecht) werden nach Satznummer statt Position aufgelöst — nach Streichungen kann die
   * Zählung von der Position abweichen. Trägt mindestens ein Satz eine Nummer, entscheidet allein
   * das Label (kein Positions-Fallback, der einen falsch nummerierten Satz greifen könnte).
   */
  private static SatzTeiler.@Nullable SatzBereich satzBereich(
      String text, int nummer, List<SatzTeiler.SatzBereich> saetze) {
    boolean nummeriert = false;
    for (var kandidat : saetze) {
      var label = SatzTeiler.nummerVonSatz(text.substring(kandidat.von(), kandidat.bis()));
      if (label != null && label == nummer) {
        return kandidat;
      }
      nummeriert |= label != null;
    }
    if (nummeriert) {
      return null;
    }
    int index = nummer - 1;
    return index >= 0 && index < saetze.size() ? saetze.get(index) : null;
  }

  /**
   * Verfeinert den Bereich um eine etwaige Halbsatz-Angabe: Halbsatz 1 reicht bis zum (ersten)
   * Semikolon, Halbsatz 2 beginnt dahinter. Ohne Semikolon ist die Angabe nicht auflösbar
   * (konservativ: {@code null} → manuell prüfen).
   */
  private static SatzTeiler.@Nullable SatzBereich halbsatzBereich(
      String text, Stelle stelle, SatzTeiler.SatzBereich bereich) {
    Stelle.HalbsatzNr halbsatz = null;
    for (var komponente : stelle.komponenten()) {
      if (komponente instanceof Stelle.HalbsatzNr h) {
        halbsatz = h;
      }
    }
    if (halbsatz == null) {
      return bereich;
    }
    int semikolon = text.indexOf(';', bereich.von());
    if (semikolon < 0 || semikolon >= bereich.bis()) {
      return null;
    }
    return switch (halbsatz.nummer()) {
      case "1" -> new SatzTeiler.SatzBereich(bereich.von(), semikolon);
      case "2" -> {
        int start = semikolon + 1;
        while (start < bereich.bis() && Character.isWhitespace(text.charAt(start))) {
          start++;
        }
        yield new SatzTeiler.SatzBereich(start, bereich.bis());
      }
      default -> null;
    };
  }

  /**
   * Findet die (im Suchbereich eindeutige) Aufzählungszeile, die mit dem gegebenen Label beginnt,
   * und dehnt den Bereich auf die tiefer eingerückten Kindzeilen der Einheit aus.
   */
  private static SatzTeiler.@Nullable SatzBereich zeilenBlock(
      String text, String labelRegex, SatzTeiler.SatzBereich suchbereich) {
    var muster = Pattern.compile("(?m)^([ \\t]*)" + labelRegex + "[ \\t].*$");
    var matcher = muster.matcher(text).region(suchbereich.von(), suchbereich.bis());
    SatzTeiler.SatzBereich gefunden = null;
    int einrueckung = 0;
    while (matcher.find()) {
      if (gefunden != null) {
        return null; // mehrdeutig (z.B. gleiche Buchstaben in mehreren Nummern)
      }
      gefunden = new SatzTeiler.SatzBereich(matcher.start(), matcher.end());
      einrueckung = matcher.group(1).length();
    }
    if (gefunden == null) {
      return null;
    }
    int ende = gefunden.bis();
    while (ende < suchbereich.bis() && text.charAt(ende) == '\n') {
      int naechsteEnde = text.indexOf('\n', ende + 1);
      if (naechsteEnde < 0 || naechsteEnde > suchbereich.bis()) {
        naechsteEnde = suchbereich.bis();
      }
      var zeile = text.substring(ende + 1, naechsteEnde);
      if (zeile.isBlank() || fuehrendeBreite(zeile) <= einrueckung) {
        break;
      }
      ende = naechsteEnde;
    }
    return new SatzTeiler.SatzBereich(gefunden.von(), ende);
  }

  private static int fuehrendeBreite(String zeile) {
    int i = 0;
    while (i < zeile.length() && (zeile.charAt(i) == ' ' || zeile.charAt(i) == '\t')) {
      i++;
    }
    return i;
  }

  static List<Stelle.Komponente> komponenten(Stelle stelle) {
    return stelle.komponenten();
  }
}
