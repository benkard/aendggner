package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Stelle;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parst Stellenangaben wie „§ 5a Absatz 2 Satz 1 Nummer 4 Buchstabe c“, „der Inhaltsübersicht“ oder
 * „Die Überschrift“.
 *
 * <p>Die gesamte Phrase muss aus bekannten Komponenten und Füllwörtern bestehen; andernfalls wird
 * {@link Optional#empty()} geliefert (und der aufrufende Parser stuft den Befehl als unbekannt
 * ein). Insbesondere fallen Bereichs- und Mehrfachangaben („Sätze 2 bis 4“, „Absatz 1 und 2“)
 * absichtlich durch.
 */
public final class StellenParser {

  private static final Set<String> FUELLWOERTER =
      Set.of("in", "der", "die", "das", "dem", "den", "des");

  private static final Pattern PARAGRAPH = Pattern.compile("§");
  private static final Pattern NUMMER_WERT = Pattern.compile("\\d+[a-z]?");
  private static final Pattern BUCHSTABE_WERT = Pattern.compile("[a-z]{1,3}");

  private StellenParser() {}

  public static Optional<Stelle> parse(String phrase) {
    var woerter = phrase.strip().split("\\s+");
    var komponenten = new ArrayList<Stelle.Komponente>();

    for (int i = 0; i < woerter.length; i++) {
      var wort = entfernePunktuation(woerter[i]);
      if (wort.isEmpty() || FUELLWOERTER.contains(wort.toLowerCase())) {
        continue;
      }
      switch (wort) {
        case "§" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.Paragraph(wert));
          i++;
        }
        case "Absatz", "Abs." -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.AbsatzNr(wert));
          i++;
        }
        case "Satz" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.SatzNr(wert));
          i++;
        }
        case "Nummer", "Nr." -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.NummerNr(wert));
          i++;
        }
        case "Buchstabe", "Buchst.", "Doppelbuchstabe" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !BUCHSTABE_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.BuchstabeNr(wert));
          i++;
        }
        case "Inhaltsübersicht" -> komponenten.add(new Stelle.Inhaltsuebersicht());
        case "Überschrift" -> komponenten.add(new Stelle.Ueberschrift());
        default -> {
          return Optional.empty();
        }
      }
    }

    if (komponenten.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Stelle(komponenten));
  }

  private static String naechstesWort(String[] woerter, int i) {
    return i + 1 < woerter.length ? entfernePunktuation(woerter[i + 1]) : null;
  }

  /** Entfernt anhängende Satzzeichen („§ 28,“ → „§ 28“), nicht aber den Punkt in „Abs.“/„Nr.“. */
  private static String entfernePunktuation(String wort) {
    var ergebnis = wort.strip();
    while (!ergebnis.isEmpty() && ",;:".indexOf(ergebnis.charAt(ergebnis.length() - 1)) >= 0) {
      ergebnis = ergebnis.substring(0, ergebnis.length() - 1);
    }
    return ergebnis;
  }
}
