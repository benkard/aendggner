package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt einzelne Änderungsbefehle in platzhalter-substituiertem Text (siehe {@link
 * ZitatExtraktor}; «n» steht für das n-te Zitat).
 *
 * <p>Die Muster folgen den Formulierungen des Handbuchs der Rechtsförmlichkeit. Was hier nicht
 * erkannt wird, stuft der Aufrufer als {@link Aenderungsbefehl.UnbekannterBefehl} ein.
 */
final class BefehlErkenner {

  // Wiederkehrende Bausteine.
  private static final String WOERTER = "(?:die Wörter|das Wort|die Angabe|die Zahl)";
  private static final String Z = "«(\\d+)»";

  private static final Pattern KONTEXT =
      Pattern.compile("^(?:In )?(.+?) (?:wird|werden) wie folgt geändert:$");

  private static final Pattern NEUFASSUNG =
      Pattern.compile("^(.+?) (?:wird|werden) wie folgt gefasst: " + Z + "\\.?$");

  private static final Pattern ERSETZUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (jeweils )?"
              + WOERTER
              + " "
              + Z
              + " (?:jeweils )?durch "
              + WOERTER
              + " "
              + Z
              + " ersetzt\\.$");

  private static final Pattern ERSETZUNG_OHNE_STELLE =
      Pattern.compile(
          "^(?:Die Wörter|Das Wort|Die Angabe|Die Zahl) "
              + Z
              + " (?:wird|werden) (jeweils )?durch "
              + WOERTER
              + " "
              + Z
              + " ersetzt\\.$");

  private static final Pattern SATZZEICHEN_ERSETZUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (der Punkt|das Komma|das Semikolon) am Ende durch "
              + "(ein Komma|einen Punkt|ein Semikolon|"
              + WOERTER
              + " "
              + Z
              + ") ersetzt\\.$");

  private static final Pattern WOERTER_EINFUEGUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (nach|vor) (?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " "
              + WOERTER
              + " "
              + Z
              + " eingefügt\\.$");

  private static final Pattern WOERTER_EINFUEGUNG_VOR_KOMMA =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) vor dem Komma am Ende "
              + WOERTER
              + " "
              + Z
              + " eingefügt\\.$");

  private static final Pattern WOERTER_ANFUEGUNG =
      Pattern.compile(
          "^(?:In |Dem |Der )?(.+?) (?:wird|werden) " + WOERTER + " " + Z + " angefügt\\.$");

  private static final Pattern STRUKTUR_EINFUEGUNG =
      Pattern.compile(
          "^(Nach|Vor) (.+?) (?:wird|werden) (?:der |die |das )?folgende[rs]? (.+?) eingefügt: "
              + Z
              + "\\.?$");

  private static final Pattern STRUKTUR_ANFUEGUNG_MIT_STELLE =
      Pattern.compile(
          "^(?:Dem|Der) (.+?) (?:wird|werden) (?:der |die |das )?folgende[rs]? (.+?) angefügt: "
              + Z
              + "\\.?$");

  private static final Pattern STRUKTUR_ANFUEGUNG =
      Pattern.compile(
          "^(?:Der |Die |Das )?[Ff]olgende[rs]? (.+?) (?:wird|werden) angefügt: " + Z + "\\.?$");

  private static final Pattern AUFHEBUNG = Pattern.compile("^(.+?) (?:wird|werden) aufgehoben\\.$");

  private static final Pattern STREICHUNG =
      Pattern.compile("^(?:In )?(.+?) (?:wird|werden) " + WOERTER + " " + Z + " gestrichen\\.$");

  private static final Pattern UMNUMMERIERUNG =
      Pattern.compile("^(?:Der bisherige )?(.+?) wird (Absatz|Satz) (\\d+[a-z]?)\\.$");

  private static final Pattern INHALTSUEBERSICHT_EINFUEGUNG =
      Pattern.compile(
          "^In der Inhaltsübersicht (?:wird|werden) (nach|vor) der Angabe zu (§ \\S+?) "
              + "(?:die |der |das )?folgende Angabe(?:n)? eingefügt: "
              + Z
              + "\\.?$");

  private static final Pattern EBENE_BEZEICHNUNG =
      Pattern.compile(
          "^(?:§ (\\d+[a-z]?)|Absatz (\\d+[a-z]?)|Satz(?: (\\d+[a-z]?))?|Sätze"
              + "|Nummer (\\d+[a-z]?)|Buchstabe ([a-z]{1,3}))$");

  private BefehlErkenner() {}

  /** Prüft, ob der Text ein Kontextrahmen („§ X wird wie folgt geändert:“) ist. */
  static Optional<Stelle> kontextRahmen(String text) {
    var matcher = KONTEXT.matcher(text);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return StellenParser.parse(matcher.group(1));
  }

  /**
   * Versucht, den Text als Änderungsbefehl zu erkennen.
   *
   * @param text platzhalter-substituierter, whitespace-normalisierter Befehlstext.
   * @param kontext die aus umgebenden Kontextrahmen geerbte Stelle.
   * @param zitate die extrahierten Zitate zur Auflösung der Platzhalter.
   * @param provenienz Herkunftsangabe für den Befehl.
   */
  static Optional<Aenderungsbefehl> erkenne(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {

    Matcher m;

    if ((m = NEUFASSUNG.matcher(text)).matches()) {
      var neuerText = zitat(zitate, m.group(2));
      return StellenParser.parse(m.group(1))
          .map(s -> new Neufassung(kontext.plus(s), neuerText, provenienz));
    }

    if ((m = ERSETZUNG.matcher(text)).matches()) {
      var jeweils = m.group(2) != null || text.contains(" jeweils durch ");
      var alt = wortZitat(zitate, m.group(3));
      var neu = wortZitat(zitate, m.group(4));
      var stelle = StellenParser.parse(m.group(1));
      var effektivesJeweils = jeweils;
      return stelle.map(
          s -> new Ersetzung(kontext.plus(s), alt, neu, effektivesJeweils, false, provenienz));
    }

    if ((m = ERSETZUNG_OHNE_STELLE.matcher(text)).matches()) {
      var jeweils = m.group(2) != null;
      return Optional.of(
          new Ersetzung(
              kontext,
              wortZitat(zitate, m.group(1)),
              wortZitat(zitate, m.group(3)),
              jeweils,
              false,
              provenienz));
    }

    if ((m = SATZZEICHEN_ERSETZUNG.matcher(text)).matches()) {
      var alt = satzzeichen(m.group(2));
      var neu = m.group(4) != null ? wortZitat(zitate, m.group(4)) : satzzeichen(m.group(3));
      var neuText = neu;
      return StellenParser.parse(m.group(1))
          .map(s -> new Ersetzung(kontext.plus(s), alt, neuText, false, true, provenienz));
    }

    if ((m = WOERTER_EINFUEGUNG.matcher(text)).matches()) {
      var ankerWoerter = wortZitat(zitate, m.group(3));
      var anker =
          m.group(2).equals("nach")
              ? new WortAnker.NachWoertern(ankerWoerter)
              : new WortAnker.VorWoertern(ankerWoerter);
      var woerter = wortZitat(zitate, m.group(4));
      return StellenParser.parse(m.group(1))
          .map(s -> new WoerterEinfuegung(kontext.plus(s), anker, woerter, provenienz));
    }

    if ((m = WOERTER_EINFUEGUNG_VOR_KOMMA.matcher(text)).matches()) {
      var woerter = wortZitat(zitate, m.group(2));
      return StellenParser.parse(m.group(1))
          .map(
              s ->
                  new WoerterEinfuegung(
                      kontext.plus(s), new WortAnker.VorKommaAmEnde(), woerter, provenienz));
    }

    if ((m = INHALTSUEBERSICHT_EINFUEGUNG.matcher(text)).matches()) {
      var anker =
          m.group(1).equals("nach")
              ? new WortAnker.NachWoertern("Angabe zu " + m.group(2))
              : new WortAnker.VorWoertern("Angabe zu " + m.group(2));
      return Optional.of(
          new WoerterEinfuegung(
              kontext.plus(new Stelle(java.util.List.of(new Stelle.Inhaltsuebersicht()))),
              anker,
              wortZitat(zitate, m.group(3)),
              provenienz));
    }

    if ((m = STRUKTUR_EINFUEGUNG.matcher(text)).matches()) {
      var vorher = m.group(1).equals("Vor");
      var stelle = StellenParser.parse(m.group(2));
      var ebeneBez = ebeneUndBezeichnung(m.group(3));
      var textInhalt = zitat(zitate, m.group(4));
      if (stelle.isEmpty() || ebeneBez.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new StrukturEinfuegung(
              kontext.plus(stelle.get()),
              vorher,
              ebeneBez.get().ebene(),
              ebeneBez.get().bezeichnung(),
              textInhalt,
              provenienz));
    }

    if ((m = STRUKTUR_ANFUEGUNG_MIT_STELLE.matcher(text)).matches()) {
      var stelle = StellenParser.parse(m.group(1));
      var ebeneBez = ebeneUndBezeichnung(m.group(2));
      var textInhalt = zitat(zitate, m.group(3));
      if (stelle.isEmpty() || ebeneBez.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new Anfuegung(
              kontext.plus(stelle.get()),
              ebeneBez.get().ebene(),
              ebeneBez.get().bezeichnung(),
              textInhalt,
              provenienz));
    }

    if ((m = STRUKTUR_ANFUEGUNG.matcher(text)).matches()) {
      var ebeneBez = ebeneUndBezeichnung(m.group(1));
      var textInhalt = zitat(zitate, m.group(2));
      if (ebeneBez.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new Anfuegung(
              kontext,
              ebeneBez.get().ebene(),
              ebeneBez.get().bezeichnung(),
              textInhalt,
              provenienz));
    }

    if ((m = WOERTER_ANFUEGUNG.matcher(text)).matches()) {
      var woerter = wortZitat(zitate, m.group(2));
      return StellenParser.parse(m.group(1))
          .map(
              s ->
                  new WoerterEinfuegung(
                      kontext.plus(s), new WortAnker.AmEnde(), woerter, provenienz));
    }

    if ((m = AUFHEBUNG.matcher(text)).matches()) {
      return StellenParser.parse(m.group(1)).map(s -> new Aufhebung(kontext.plus(s), provenienz));
    }

    if ((m = STREICHUNG.matcher(text)).matches()) {
      var woerter = wortZitat(zitate, m.group(2));
      return StellenParser.parse(m.group(1))
          .map(s -> new Streichung(kontext.plus(s), woerter, provenienz));
    }

    if ((m = UMNUMMERIERUNG.matcher(text)).matches()) {
      var neu = komponenteFuer(m.group(2), m.group(3));
      return StellenParser.parse(m.group(1))
          .map(
              alt ->
                  new Umnummerierung(
                      kontext.plus(alt),
                      kontext.plus(new Stelle(java.util.List.of(neu))),
                      provenienz));
    }

    return Optional.empty();
  }

  private record EbeneBezeichnung(Ebene ebene, String bezeichnung) {}

  private static Optional<EbeneBezeichnung> ebeneUndBezeichnung(String phrase) {
    var m = EBENE_BEZEICHNUNG.matcher(phrase.strip());
    if (!m.matches()) {
      return Optional.empty();
    }
    if (m.group(1) != null) {
      return Optional.of(new EbeneBezeichnung(Ebene.PARAGRAPH, m.group(1)));
    }
    if (m.group(2) != null) {
      return Optional.of(new EbeneBezeichnung(Ebene.ABSATZ, m.group(2)));
    }
    if (m.group(4) != null) {
      return Optional.of(new EbeneBezeichnung(Ebene.NUMMER, m.group(4)));
    }
    if (m.group(5) != null) {
      return Optional.of(new EbeneBezeichnung(Ebene.BUCHSTABE, m.group(5)));
    }
    // „Satz“, „Satz 3“ oder „Sätze“.
    return Optional.of(new EbeneBezeichnung(Ebene.SATZ, m.group(3)));
  }

  private static Stelle.Komponente komponenteFuer(String ebene, String nummer) {
    return switch (ebene) {
      case "Absatz" -> new Stelle.AbsatzNr(nummer);
      case "Satz" -> new Stelle.SatzNr(nummer);
      default -> throw new IllegalArgumentException("Unbekannte Ebene: " + ebene);
    };
  }

  /** Zitat für Textblöcke (Neufassung, Einfügung ganzer Einheiten): Zeilenstruktur erhalten. */
  private static String zitat(ZitatExtraktor.Ergebnis zitate, String index) {
    return zitate.zitat(Integer.parseInt(index));
  }

  /**
   * Zitat für wortweise Operationen (alt/neu-Wörter, Anker): Whitespace normalisieren, denn im PDF
   * umbrochene Zitate enthalten Zeilenumbrüche, der Stammgesetztext aber nicht.
   */
  private static String wortZitat(ZitatExtraktor.Ergebnis zitate, String index) {
    return zitat(zitate, index).replaceAll("\\s+", " ").strip();
  }

  private static String satzzeichen(String phrase) {
    return switch (phrase) {
      case "der Punkt", "einen Punkt" -> ".";
      case "das Komma", "ein Komma" -> ",";
      case "das Semikolon", "ein Semikolon" -> ";";
      default -> throw new IllegalArgumentException("Unbekanntes Satzzeichen: " + phrase);
    };
  }
}
