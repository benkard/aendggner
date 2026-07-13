package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Stelle;
import java.util.ArrayList;
import java.util.List;
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

  // „neu“/„bisherig“ beziehen sich auf den jeweils aktuellen Zwischenstand — da die Befehle
  // sequenziell angewandt werden, ist „die neue Nummer 11“ schlicht Nummer 11.
  private static final Set<String> FUELLWOERTER =
      Set.of(
          "in",
          "der",
          "die",
          "das",
          "dem",
          "den",
          "des",
          "neue",
          "neuen",
          "bisherige",
          "bisherigen");

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

  private static final Pattern KOORDINATION = Pattern.compile(",\\s*|\\s+und\\s+|\\s+sowie\\s+");

  /**
   * Parst eine ggf. per „und“/„sowie“/Komma koordinierte Stellenangabe in eine Liste von Stellen.
   * Nachfolgende Segmente teilen sich den gemeinsamen Präfix des vorigen Segments: „§ 3 Absatz 1
   * Satz 2 und Absatz 4“ → [§ 3 Absatz 1 Satz 2, § 3 Absatz 4].
   *
   * <p>Für eine einfache (nicht koordinierte) Angabe liefert die Methode genau ein Element (bzw.
   * eine leere Liste, wenn {@link #parse} sie ablehnt) und ist damit ein Drop-in für {@code
   * parse(...).map(List::of)}. Kann ein Segment nicht geparst werden oder findet der Präfix-Merge
   * keine Anknüpfung, wird eine leere Liste geliefert — der Aufrufer stuft den Befehl dann als
   * unbekannt ein (konservativ: lieber manuell prüfen als falsch anwenden).
   */
  public static List<Stelle> parseMehrfach(String phrase) {
    var segmente = KOORDINATION.split(phrase.strip());
    if (segmente.length <= 1) {
      return parse(phrase).map(List::of).orElseGet(List::of);
    }

    var ergebnis = new ArrayList<Stelle>();
    Stelle vorige = null;
    for (var segment : segmente) {
      if (segment.isBlank()) {
        return List.of();
      }
      Stelle voll;
      var teil = parse(segment);
      if (teil.isPresent()) {
        if (vorige == null) {
          voll = teil.get();
        } else {
          var gemergt = mitGemeinsamemPraefix(vorige, teil.get());
          if (gemergt.isEmpty()) {
            return List.of();
          }
          voll = gemergt.get();
        }
      } else if (vorige != null && BLOSSES_LABEL.matcher(segment).matches()) {
        // Bloße Nummer/Buchstabe („Absatz 1 und 5“ → das „5“): Typ der letzten Komponente erben.
        var geerbt = mitGeerbtemLabel(vorige, segment);
        if (geerbt.isEmpty()) {
          return List.of();
        }
        voll = geerbt.get();
      } else {
        return List.of();
      }
      ergebnis.add(voll);
      vorige = voll;
    }
    return ergebnis;
  }

  private static final Pattern BLOSSES_LABEL = Pattern.compile("\\d+[a-z]?|[a-z]{1,3}");

  /** Ersetzt die letzte Komponente von {@code vorige} durch dieselbe Komponentenart mit neuem Label. */
  private static Optional<Stelle> mitGeerbtemLabel(Stelle vorige, String label) {
    var komponenten = new ArrayList<>(vorige.komponenten());
    var letzte = komponenten.get(komponenten.size() - 1);
    Stelle.Komponente neu =
        switch (letzte) {
          case Stelle.Paragraph p -> new Stelle.Paragraph(label);
          case Stelle.AbsatzNr a -> new Stelle.AbsatzNr(label);
          case Stelle.SatzNr s -> new Stelle.SatzNr(label);
          case Stelle.NummerNr n -> new Stelle.NummerNr(label);
          case Stelle.BuchstabeNr b -> new Stelle.BuchstabeNr(label);
          case Stelle.Ueberschrift u -> null;
          case Stelle.Inhaltsuebersicht i -> null;
        };
    if (neu == null) {
      return Optional.empty();
    }
    komponenten.set(komponenten.size() - 1, neu);
    return Optional.of(new Stelle(komponenten));
  }

  /**
   * Ergänzt {@code segment} um die Präfix-Komponenten von {@code vorige}, die feiner-granular als
   * die führende Komponente des Segments sind. Die führende Komponentenklasse des Segments wird in
   * {@code vorige} gesucht; alle davor stehenden Komponenten bilden den gemeinsamen Präfix.
   */
  private static Optional<Stelle> mitGemeinsamemPraefix(Stelle vorige, Stelle segment) {
    var fuehrende = segment.komponenten().get(0).getClass();
    var vorKomp = vorige.komponenten();
    int ankerIndex = -1;
    for (int i = 0; i < vorKomp.size(); i++) {
      if (vorKomp.get(i).getClass().equals(fuehrende)) {
        ankerIndex = i;
        break;
      }
    }
    if (ankerIndex < 0) {
      return Optional.empty();
    }
    var komponenten = new ArrayList<>(vorKomp.subList(0, ankerIndex));
    komponenten.addAll(segment.komponenten());
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
