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
      enbez = "§ " + stelle.paragraph().get().nummer();
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

    // 3. Feinste Komponente (Buchstabe > Nummer > Satz) als Textbereich auflösen.
    var feinste = feinsteKomponente(stelle);
    if (feinste == null) {
      return new Ergebnis.Gefunden(new Fundstelle(normIndex, absatzIndex, null));
    }

    if (absatzIndex == null) {
      if (norm.absaetze().size() == 1) {
        absatzIndex = 0;
      } else {
        return new Ergebnis.NichtGefunden(
            enbez
                + " hat "
                + norm.absaetze().size()
                + " Absätze; „"
                + stelle.anzeigeText()
                + "“ ist ohne Absatzangabe nicht eindeutig.");
      }
    }

    var text = norm.absaetze().get(absatzIndex).text();
    var bereich = loeseKomponenteAuf(feinste, text);
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

  private static Stelle.@Nullable Komponente feinsteKomponente(Stelle stelle) {
    Stelle.Komponente feinste = null;
    for (var komponente : stelle.komponenten()) {
      switch (komponente) {
        case Stelle.SatzNr s -> feinste = besser(feinste, s, 1);
        case Stelle.NummerNr n -> feinste = besser(feinste, n, 2);
        case Stelle.BuchstabeNr b -> feinste = besser(feinste, b, 3);
        default -> {}
      }
    }
    return feinste;
  }

  private static Stelle.Komponente besser(
      Stelle.@Nullable Komponente bisher, Stelle.Komponente neu, int rang) {
    if (bisher == null) {
      return neu;
    }
    return rang(bisher) >= rang ? bisher : neu;
  }

  private static int rang(Stelle.Komponente komponente) {
    return switch (komponente) {
      case Stelle.SatzNr s -> 1;
      case Stelle.NummerNr n -> 2;
      case Stelle.BuchstabeNr b -> 3;
      default -> 0;
    };
  }

  private static SatzTeiler.@Nullable SatzBereich loeseKomponenteAuf(
      Stelle.Komponente komponente, String text) {
    return switch (komponente) {
      case Stelle.SatzNr satz -> {
        int index = Integer.parseInt(satz.nummer().replaceAll("[a-z]$", "")) - 1;
        var saetze = SatzTeiler.teile(text);
        yield index >= 0 && index < saetze.size() ? saetze.get(index) : null;
      }
      case Stelle.NummerNr nummer -> zeilenBereich(text, Pattern.quote(nummer.nummer()) + "\\.");
      case Stelle.BuchstabeNr buchstabe ->
          zeilenBereich(text, Pattern.quote(buchstabe.kennung()) + "\\)");
      default -> null;
    };
  }

  /** Findet die (eindeutige) Aufzählungszeile, die mit dem gegebenen Label beginnt. */
  private static SatzTeiler.@Nullable SatzBereich zeilenBereich(String text, String labelRegex) {
    var muster = Pattern.compile("(?m)^[ \\t]*" + labelRegex + "[ \\t].*$");
    var matcher = muster.matcher(text);
    SatzTeiler.SatzBereich gefunden = null;
    while (matcher.find()) {
      if (gefunden != null) {
        return null; // mehrdeutig (z.B. gleiche Buchstaben in mehreren Nummern)
      }
      gefunden = new SatzTeiler.SatzBereich(matcher.start(), matcher.end());
    }
    return gefunden;
  }

  static List<Stelle.Komponente> komponenten(Stelle stelle) {
    return stelle.komponenten();
  }
}
