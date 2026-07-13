package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Sammelbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturErsetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuAbsatz;
import eu.mulk.aendggner.aenderung.Provenienz;
import eu.mulk.aendggner.aenderung.Stelle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

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

  // „§ 2 Absatz 2 wird durch die folgenden Absätze 2 und 3 ersetzt: „…““ (neues BGBl-Format);
  // auch „Die Überschrift wird durch die folgende Überschrift ersetzt: „…““ (Entwürfe).
  private static final Pattern STRUKTUR_ERSETZUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) durch (?:den |die |das )?folgende[nrs]? (.+?) ersetzt: "
              + Z
              + "\\.?$");

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

  // Auch die Verbundform „wird der Punkt am Ende durch ein Komma und die Wörter „…“ ersetzt“.
  private static final Pattern SATZZEICHEN_ERSETZUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (der Punkt|das Komma|das Semikolon) am Ende durch "
              + "(ein Komma und "
              + WOERTER
              + " "
              + Z
              + "|ein Komma|einen Punkt|ein Semikolon|"
              + WOERTER
              + " "
              + Z
              + ") ersetzt\\.$");

  private static final Pattern WOERTER_EINFUEGUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?(nach|vor) "
              + "(?:dem Wort|den Wörtern|der Angabe|der Zahl) "
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
          "^(Nach|Vor) (.+?) (?:wird|werden) (?:der |die |das )?folgende[nrs]? (.+?) eingefügt: "
              + Z
              + "\\.?$");

  private static final Pattern STRUKTUR_ANFUEGUNG_MIT_STELLE =
      Pattern.compile(
          "^(?:Dem|Der) (.+?) (?:wird|werden) (?:der |die |das )?folgende[nrs]? (.+?) angefügt: "
              + Z
              + "\\.?$");

  private static final Pattern STRUKTUR_ANFUEGUNG =
      Pattern.compile(
          "^(?:Der |Die |Das )?[Ff]olgende[nrs]? (.+?) (?:wird|werden) angefügt: " + Z + "\\.?$");

  private static final Pattern AUFHEBUNG = Pattern.compile("^(.+?) (?:wird|werden) aufgehoben\\.$");

  // „Die Überschrift von Teil 3 Abschnitt 2 wird gestrichen.“ — Streichung einer Gliederungs-
  // Überschrift (die Wörter-Streichung STREICHUNG erfordert dagegen ein Zitat).
  private static final Pattern UEBERSCHRIFT_STREICHUNG =
      Pattern.compile("^(Die Überschrift (?:von|des|der|zu) .+?) wird gestrichen\\.$");

  // „Die Absatzbezeichnung „(2)“ wird gestrichen.“
  private static final Pattern ABSATZBEZEICHNUNG_STREICHUNG =
      Pattern.compile("^Die Absatzbezeichnung " + Z + " wird gestrichen\\.$");

  // Inhaltsübersicht: „Die Angabe(n) zu <…> wird/werden wie folgt gefasst / durch … ersetzt /
  // gestrichen.“ Wird als Änderung der Inhaltsübersicht typisiert (Anwendung erfolgt gesondert).
  private static final Pattern INHALTSUEBERSICHT_ANGABE =
      Pattern.compile(
          "^Die Angaben? (?:zu|zur) .+? (?:wird|werden) "
              + "(?:wie folgt gefasst: "
              + Z
              + "|durch (?:die )?folgende[nrs]? Angaben? ersetzt: "
              + Z
              + "|gestrichen)\\.?$");

  private static final Pattern STREICHUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?" + WOERTER + " " + Z + " gestrichen\\.$");

  private static final Pattern UMNUMMERIERUNG =
      Pattern.compile(
          "^(?:Der bisherige |Die bisherige |Das bisherige )?(.+?) wird (?:zu )?"
              + "(Absatz|Satz|Nummer|Buchstabe) (\\d+[a-z]?)\\.$");

  // „Die bisherigen Absätze 2 bis 4 werden zu den Absätzen 3 bis 5.“ bzw. „Die bisherigen Nummern 4
  // bis 6 werden die Nummern 8 bis 10.“ — Bereichs-Umnummerierung, in Einzelbefehle aufgelöst.
  private static final Pattern UMNUMMERIERUNG_BEREICH =
      Pattern.compile(
          "^Die bisherigen (?:Absätze|Sätze|Nummern|Buchstaben) (\\d+) bis (\\d+) "
              + "werden (?:zu den |die )?(Absätzen|Sätzen|Nummern|Buchstaben|Absätze|Sätze) "
              + "(\\d+) bis (\\d+)\\.$");

  // „Die §§ 52 bis 56 werden wie folgt gefasst: „§ 52 (weggefallen) …““ — Neufassung eines §-Bereichs;
  // der Zitatblock wird an „§ N“-Grenzen in Einzel-Neufassungen zerlegt.
  private static final Pattern PARAGRAPH_BEREICH_NEUFASSUNG =
      Pattern.compile(
          "^Die §§ (\\d+[a-z]?) bis (\\d+[a-z]?) (?:wird|werden) wie folgt gefasst: " + Z + "\\.?$");

  // „Der Wortlaut wird Absatz 1.“
  private static final Pattern WORTLAUT_ZU_ABSATZ =
      Pattern.compile("^Der Wortlaut wird Absatz (\\d+[a-z]?)\\.$");

  // „In Nummer 7 wird das Wort «1» am Ende durch ein Komma ersetzt.“
  private static final Pattern WORT_ZU_SATZZEICHEN =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) "
              + WOERTER
              + " "
              + Z
              + "(?: am Ende)? durch (ein Komma|ein Semikolon|einen Punkt) ersetzt\\.$");

  // „In Satz 2 wird nach dem Wort «1» ein Komma und werden die Wörter «2» eingefügt.“
  private static final Pattern KOMMA_UND_WOERTER_EINFUEGUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?(nach|vor) "
              + "(?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " ein Komma und (?:wird|werden) "
              + WOERTER
              + " "
              + Z
              + " eingefügt\\.$");

  // „In § 74 werden die Wörter «1» durch ein Komma und die Wörter «2» ersetzt.“
  private static final Pattern ERSETZUNG_DURCH_KOMMA_UND_WOERTER =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?"
              + WOERTER
              + " "
              + Z
              + " durch ein Komma und "
              + WOERTER
              + " "
              + Z
              + " ersetzt\\.$");

  private static final Pattern INHALTSUEBERSICHT_EINFUEGUNG =
      Pattern.compile(
          "^In der Inhaltsübersicht (?:wird|werden) (nach|vor) der Angabe zu (§ \\S+?) "
              + "(?:die |der |das )?folgende Angabe(?:n)? eingefügt: "
              + Z
              + "\\.?$");

  // Variante innerhalb eines Kontextrahmens „Die Inhaltsübersicht wird wie folgt geändert:“.
  private static final Pattern ANGABE_EINFUEGUNG =
      Pattern.compile(
          "^(Nach|Vor) der Angabe zu (§ \\S+?) (?:wird|werden) "
              + "(?:die |der |das )?folgenden? Angabe(?:n)? eingefügt: "
              + Z
              + "\\.?$");

  private static final Pattern EBENE_BEZEICHNUNG =
      Pattern.compile(
          "^(?:§ (\\d+[a-z]?)|Absatz (\\d+[a-z]?)|Satz(?: (\\d+[a-z]?))?|Sätze"
              + "|Nummer (\\d+[a-z]?)|Buchstabe ([a-z]{1,3})"
              + "|(Absätze .+|Nummern .+|Buchstaben .+))$");

  // „In <Stelle> werden die Wörter «1» durch die Wörter «2» und die Angabe «3» durch die Wörter «4»
  // ersetzt.“ — mehrere Ersetzungspaare unter einem gemeinsamen „ersetzt“. Die Mitte (Gruppe 2)
  // wird an „ und “ in Einzelpaare zerlegt und je gegen EIN_ERSETZUNGS_PAAR validiert.
  private static final Pattern PAAR_ERSETZUNG =
      Pattern.compile("^(?:In )?(.+?) (?:wird|werden) (.+ und .+) ersetzt\\.$");
  private static final Pattern EIN_ERSETZUNGS_PAAR =
      Pattern.compile(
          "^(?:jeweils )?" + WOERTER + " " + Z + " (?:jeweils )?durch " + WOERTER + " " + Z + "$");

  // „… ein Komma eingefügt und werden …“: Trennstellen eines Verbundbefehls sind „ und “ (ggf. mit
  // Komma) bzw. „, “ direkt vor „wird/werden“. Innerhalb von Zitaten steht „ und “ als «n» maskiert.
  private static final Pattern VERBUND_SEP =
      Pattern.compile(",? und |, (?=wird\\b|werden\\b)");
  private static final Pattern WIRD_WERDEN = Pattern.compile(" (?:wird|werden) ");

  // „In <Stelle> wird nach den Wörtern «1» ein Komma eingefügt.“ (Satzzeichen statt Wörter einfügen)
  private static final Pattern KOMMA_EINFUEGUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?(nach|vor) "
              + "(?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " (ein Komma|ein Semikolon|einen Punkt) eingefügt\\.$");

  // „Nach der Angabe «1» wird die Angabe «2» eingefügt.“ — Anker zuerst, ohne eigene Stelle (nutzt
  // den Kontext). Tritt vor allem als rechte Klausel eines Verbundbefehls auf.
  private static final Pattern EINFUEGUNG_ANKER_ZUERST =
      Pattern.compile(
          "^(Nach|Vor) (?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " (?:wird|werden) (?:"
              + WOERTER
              + " "
              + Z
              + "|(ein Komma|ein Semikolon)) eingefügt\\.$");

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
   * Versucht, den Text als Änderungsbefehl zu erkennen. Zuerst als Einzelbefehl ({@link
   * #erkenneEinzeln}); schlägt das fehl (kein Muster passt oder die Stelle ist unparsbar), wird der
   * Text als Mehrfach-Ersetzung bzw. Verbundbefehl gedeutet.
   *
   * @param text platzhalter-substituierter, whitespace-normalisierter Befehlstext.
   * @param kontext die aus umgebenden Kontextrahmen geerbte Stelle.
   * @param zitate die extrahierten Zitate zur Auflösung der Platzhalter.
   * @param provenienz Herkunftsangabe für den Befehl.
   */
  static Optional<Aenderungsbefehl> erkenne(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {
    var einzeln = erkenneEinzeln(text, kontext, zitate, provenienz);
    if (einzeln.isPresent()) {
      return einzeln;
    }
    var paare = erkennePaarErsetzung(text, kontext, zitate, provenienz);
    if (paare.isPresent()) {
      return paare;
    }
    return erkenneVerbund(text, kontext, zitate, provenienz);
  }

  private static Optional<Aenderungsbefehl> erkenneEinzeln(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {

    Matcher m;

    // Inhaltsübersichts-Angaben zuerst prüfen, bevor NEUFASSUNG/STRUKTUR_ERSETZUNG die Phrase
    // strukturell (aber mit unparsbarer Stelle) an sich ziehen.
    if ((m = INHALTSUEBERSICHT_ANGABE.matcher(text)).matches()) {
      var stelle = kontext.plus(new Stelle(List.of(new Stelle.Inhaltsuebersicht())));
      if (m.group(1) != null) {
        return Optional.of(new Neufassung(stelle, zitat(zitate, m.group(1)), provenienz));
      }
      if (m.group(2) != null) {
        return Optional.of(new Neufassung(stelle, zitat(zitate, m.group(2)), provenienz));
      }
      return Optional.of(new Aufhebung(stelle, provenienz));
    }

    if ((m = PARAGRAPH_BEREICH_NEUFASSUNG.matcher(text)).matches()) {
      return paragraphBereichNeufassung(zitat(zitate, m.group(3)), kontext, provenienz);
    }

    if ((m = NEUFASSUNG.matcher(text)).matches()) {
      var neuerText = zitat(zitate, m.group(2));
      return StellenParser.parse(m.group(1))
          .map(s -> new Neufassung(kontext.plus(s), neuerText, provenienz));
    }

    if ((m = WORTLAUT_ZU_ABSATZ.matcher(text)).matches()) {
      return Optional.of(new WortlautZuAbsatz(kontext, m.group(1), provenienz));
    }

    if ((m = STRUKTUR_ERSETZUNG.matcher(text)).matches()) {
      var neuerText = zitat(zitate, m.group(3));
      var ziel = m.group(2).strip();
      var stelle = StellenParser.parse(m.group(1));
      if (stelle.isEmpty()) {
        return Optional.empty();
      }
      // „durch die folgende Überschrift ersetzt“ ist eine Neufassung der Überschrift,
      // „§ 19 wird durch den folgenden § 19 ersetzt“ eine Neufassung des Paragraphen.
      if (ziel.equals("Überschrift") || ziel.matches("§\\s*\\d+[a-z]?")) {
        return Optional.of(new Neufassung(kontext.plus(stelle.get()), neuerText, provenienz));
      }
      var ebene = strukturEbene(ziel);
      if (ebene == null) {
        return Optional.empty();
      }
      return Optional.of(
          new StrukturErsetzung(kontext.plus(stelle.get()), ebene, neuerText, provenienz));
    }

    if ((m = WORT_ZU_SATZZEICHEN.matcher(text)).matches()) {
      var alt = wortZitat(zitate, m.group(2));
      var neu = satzzeichen(m.group(3));
      var amEnde = text.contains(" am Ende ");
      return ausStellen(
          m.group(1), s -> new Ersetzung(kontext.plus(s), alt, neu, false, amEnde, provenienz));
    }

    if ((m = ERSETZUNG_DURCH_KOMMA_UND_WOERTER.matcher(text)).matches()) {
      var alt = wortZitat(zitate, m.group(2));
      var neu = ", " + wortZitat(zitate, m.group(3));
      return ausStellen(
          m.group(1), s -> new Ersetzung(kontext.plus(s), alt, neu, false, false, provenienz));
    }

    if ((m = ERSETZUNG.matcher(text)).matches()) {
      var jeweils = m.group(2) != null || text.contains(" jeweils durch ");
      var alt = wortZitat(zitate, m.group(3));
      var neu = wortZitat(zitate, m.group(4));
      return ausStellen(
          m.group(1), s -> new Ersetzung(kontext.plus(s), alt, neu, jeweils, false, provenienz));
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
      String neu;
      if (m.group(4) != null) {
        // „durch ein Komma und die Wörter „…“ ersetzt“
        neu = ", " + wortZitat(zitate, m.group(4));
      } else if (m.group(5) != null) {
        neu = wortZitat(zitate, m.group(5));
      } else {
        neu = satzzeichen(m.group(3));
      }
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
      return ausStellen(
          m.group(1), s -> new WoerterEinfuegung(kontext.plus(s), anker, woerter, provenienz));
    }

    if ((m = WOERTER_EINFUEGUNG_VOR_KOMMA.matcher(text)).matches()) {
      var woerter = wortZitat(zitate, m.group(2));
      return StellenParser.parse(m.group(1))
          .map(
              s ->
                  new WoerterEinfuegung(
                      kontext.plus(s), new WortAnker.VorKommaAmEnde(), woerter, provenienz));
    }

    if ((m = KOMMA_EINFUEGUNG.matcher(text)).matches()) {
      var ankerWoerter = wortZitat(zitate, m.group(3));
      var anker =
          m.group(2).equals("nach")
              ? new WortAnker.NachWoertern(ankerWoerter)
              : new WortAnker.VorWoertern(ankerWoerter);
      var woerter = satzzeichen(m.group(4));
      return ausStellen(
          m.group(1), s -> new WoerterEinfuegung(kontext.plus(s), anker, woerter, provenienz));
    }

    if ((m = KOMMA_UND_WOERTER_EINFUEGUNG.matcher(text)).matches()) {
      var ankerWoerter = wortZitat(zitate, m.group(3));
      var anker =
          m.group(2).equals("nach")
              ? new WortAnker.NachWoertern(ankerWoerter)
              : new WortAnker.VorWoertern(ankerWoerter);
      var woerter = ", " + wortZitat(zitate, m.group(4));
      return ausStellen(
          m.group(1), s -> new WoerterEinfuegung(kontext.plus(s), anker, woerter, provenienz));
    }

    if ((m = EINFUEGUNG_ANKER_ZUERST.matcher(text)).matches()) {
      var ankerWoerter = wortZitat(zitate, m.group(2));
      var anker =
          m.group(1).equalsIgnoreCase("nach")
              ? new WortAnker.NachWoertern(ankerWoerter)
              : new WortAnker.VorWoertern(ankerWoerter);
      var woerter = m.group(3) != null ? wortZitat(zitate, m.group(3)) : satzzeichen(m.group(4));
      return Optional.of(new WoerterEinfuegung(kontext, anker, woerter, provenienz));
    }

    if ((m = INHALTSUEBERSICHT_EINFUEGUNG.matcher(text)).matches()
        || (m = ANGABE_EINFUEGUNG.matcher(text)).matches()) {
      var anker =
          m.group(1).equalsIgnoreCase("nach")
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
      return ausStellen(m.group(1), s -> new Aufhebung(kontext.plus(s), provenienz));
    }

    if ((m = UEBERSCHRIFT_STREICHUNG.matcher(text)).matches()) {
      return StellenParser.parse(m.group(1))
          .map(s -> new Aufhebung(kontext.plus(s), provenienz));
    }

    if ((m = ABSATZBEZEICHNUNG_STREICHUNG.matcher(text)).matches()) {
      var nummer = zitat(zitate, m.group(1)).replaceAll("[^0-9a-z]", "");
      var stelle = new Stelle(List.of(new Stelle.Absatzbezeichnung(nummer)));
      return Optional.of(new Aufhebung(kontext.plus(stelle), provenienz));
    }

    if ((m = STREICHUNG.matcher(text)).matches()) {
      var woerter = wortZitat(zitate, m.group(2));
      return ausStellen(m.group(1), s -> new Streichung(kontext.plus(s), woerter, provenienz));
    }

    if ((m = UMNUMMERIERUNG_BEREICH.matcher(text)).matches()) {
      return bereichsUmnummerierung(
          m.group(3), m.group(1), m.group(2), m.group(4), m.group(5), kontext, provenienz);
    }

    if ((m = UMNUMMERIERUNG.matcher(text)).matches()) {
      var neu = komponenteFuer(m.group(2), m.group(3));
      return StellenParser.parse(m.group(1))
          .map(
              alt ->
                  new Umnummerierung(
                      kontext.plus(alt), kontext.plus(new Stelle(List.of(neu))), provenienz));
    }

    return Optional.empty();
  }

  /**
   * „In <Stelle> werden die Wörter «1» durch «2» und die Angabe «3» durch «4» ersetzt.“ — mehrere
   * Ersetzungspaare unter einem gemeinsamen „ersetzt“. Bei koordinierter Stelle („Absatz 1 und 5“)
   * wird das Kreuzprodukt aus Stellen und Paaren gebildet. Ergebnis ist ein {@link Sammelbefehl}
   * (bzw. ein einzelner Befehl, falls nur eine Kombination entsteht).
   */
  private static Optional<Aenderungsbefehl> erkennePaarErsetzung(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {
    var m = PAAR_ERSETZUNG.matcher(text);
    if (!m.matches()) {
      return Optional.empty();
    }
    var stellen = StellenParser.parseMehrfach(m.group(1));
    if (stellen.isEmpty()) {
      return Optional.empty();
    }
    var segmente = m.group(2).split(" und ");
    if (segmente.length < 2) {
      return Optional.empty();
    }
    var jeweils = text.contains("jeweils");
    record Paar(String alt, String neu) {}
    var paareListe = new ArrayList<Paar>();
    for (var segment : segmente) {
      var pm = EIN_ERSETZUNGS_PAAR.matcher(segment.strip());
      if (!pm.matches()) {
        return Optional.empty();
      }
      paareListe.add(new Paar(wortZitat(zitate, pm.group(1)), wortZitat(zitate, pm.group(2))));
    }
    var teile = new ArrayList<Aenderungsbefehl>();
    for (var stelle : stellen) {
      for (var paar : paareListe) {
        teile.add(
            new Ersetzung(
                kontext.plus(stelle), paar.alt(), paar.neu(), jeweils, false, provenienz));
      }
    }
    return Optional.of(teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile));
  }

  /**
   * Verbundbefehl: mehrere per „und“ (bzw. „, wird/werden“) verkettete Einzelbefehle. Der Text wird
   * an jeder Trennstelle probeweise gespalten; sobald beide Hälften als eigenständige Befehle
   * erkannt werden, entsteht ein {@link Sammelbefehl}. Nur wenn <em>alle</em> Klauseln erkannt
   * werden, greift die Zerlegung — sonst bleibt der Befehl unbekannt (konservativ).
   */
  private static Optional<Aenderungsbefehl> erkenneVerbund(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {
    var sep = VERBUND_SEP.matcher(text);
    while (sep.find()) {
      var links = text.substring(0, sep.start()).strip();
      var rechts = text.substring(sep.end()).strip();
      if (links.isEmpty() || rechts.isEmpty()) {
        continue;
      }
      var linksBefehl = erkenneAlsSatz(links, kontext, zitate, provenienz);
      if (linksBefehl.isEmpty()) {
        continue;
      }
      var rechtsBefehl = erkenneRechteKlausel(links, rechts, kontext, zitate, provenienz);
      if (rechtsBefehl.isEmpty()) {
        continue;
      }
      var teile = new ArrayList<Aenderungsbefehl>();
      flatten(linksBefehl.get(), teile);
      flatten(rechtsBefehl.get(), teile);
      return Optional.of(new Sammelbefehl(teile));
    }
    return Optional.empty();
  }

  /**
   * Versucht die rechte Klausel eines Verbunds zu erkennen: (1) unverändert, (2) mit großem
   * Anfangsbuchstaben (eigenständiger Befehl wie „nach …“ → „Nach …“), (3) mit vorangestelltem
   * lokativem Präfix der linken Klausel („In <Stelle> “).
   */
  private static Optional<Aenderungsbefehl> erkenneRechteKlausel(
      String links,
      String rechts,
      Stelle kontext,
      ZitatExtraktor.Ergebnis zitate,
      Provenienz provenienz) {
    var direkt = erkenneAlsSatz(rechts, kontext, zitate, provenienz);
    if (direkt.isPresent()) {
      return direkt;
    }
    var gross = Character.toUpperCase(rechts.charAt(0)) + rechts.substring(1);
    if (!gross.equals(rechts)) {
      var alsBefehl = erkenneAlsSatz(gross, kontext, zitate, provenienz);
      if (alsBefehl.isPresent()) {
        return alsBefehl;
      }
    }
    var praefix = lokativerPraefix(links);
    if (praefix != null) {
      return erkenneAlsSatz(praefix + " " + rechts, kontext, zitate, provenienz);
    }
    return Optional.empty();
  }

  private static Optional<Aenderungsbefehl> erkenneAlsSatz(
      String klausel, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {
    var satz = klausel.endsWith(".") ? klausel : klausel + ".";
    return erkenne(satz, kontext, zitate, provenienz);
  }

  /** Der Teil einer Klausel vor dem ersten „wird“/„werden“ („In § 3 Absatz 1“). */
  private static @Nullable String lokativerPraefix(String klausel) {
    var m = WIRD_WERDEN.matcher(klausel);
    return m.find() ? klausel.substring(0, m.start()) : null;
  }

  private static void flatten(Aenderungsbefehl befehl, List<Aenderungsbefehl> ziel) {
    if (befehl instanceof Sammelbefehl s) {
      ziel.addAll(s.teilbefehle());
    } else {
      ziel.add(befehl);
    }
  }

  /**
   * Löst „Die bisherigen Absätze X bis Y werden zu den Absätzen X′ bis Y′.“ in einzelne
   * Umnummerierungen auf, angewandt in absteigender Reihenfolge (Y→Y′ zuerst), damit die
   * sequenzielle Anwendung keine Labels kollidieren lässt.
   */
  private static Optional<Aenderungsbefehl> bereichsUmnummerierung(
      String ebeneWort,
      String altVon,
      String altBis,
      String neuVon,
      String neuBis,
      Stelle kontext,
      Provenienz provenienz) {
    var ebene = ebeneAusWort(ebeneWort);
    if (ebene == null) {
      return Optional.empty();
    }
    int av = Integer.parseInt(altVon);
    int ab = Integer.parseInt(altBis);
    int nv = Integer.parseInt(neuVon);
    int nb = Integer.parseInt(neuBis);
    if (ab - av != nb - nv || ab < av) {
      return Optional.empty();
    }
    var teile = new ArrayList<Aenderungsbefehl>();
    for (int k = ab - av; k >= 0; k--) {
      var alt = komponenteFuer(ebene, String.valueOf(av + k));
      var neu = komponenteFuer(ebene, String.valueOf(nv + k));
      teile.add(
          new Umnummerierung(
              kontext.plus(new Stelle(List.of(alt))),
              kontext.plus(new Stelle(List.of(neu))),
              provenienz));
    }
    return Optional.of(new Sammelbefehl(teile));
  }

  /**
   * Wendet einen Stellen-basierten Befehlsbauer auf eine (ggf. koordinierte) Stellenangabe an: bei
   * einer einzelnen Stelle das gewohnte Verhalten, bei mehreren per „und“ verbundenen Stellen ein
   * {@link Sammelbefehl}, der die Operation auf jede Stelle anwendet.
   */
  private static Optional<Aenderungsbefehl> ausStellen(
      String phrase, Function<Stelle, Aenderungsbefehl> bauer) {
    var stellen = StellenParser.parseMehrfach(phrase);
    if (stellen.isEmpty()) {
      return Optional.empty();
    }
    if (stellen.size() == 1) {
      return Optional.of(bauer.apply(stellen.get(0)));
    }
    return Optional.of(new Sammelbefehl(stellen.stream().map(bauer).toList()));
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
    if (m.group(6) != null) {
      // Pluralformen („Absätze 6 und 7“, „Nummern 4 bis 7“): Die Bezeichnungen der neuen
      // Einheiten stehen ohnehin im zitierten Block.
      var ebene = strukturEbene(m.group(6));
      return ebene == null ? Optional.empty() : Optional.of(new EbeneBezeichnung(ebene, null));
    }
    // „Satz“, „Satz 3“ oder „Sätze“.
    return Optional.of(new EbeneBezeichnung(Ebene.SATZ, m.group(3)));
  }

  /** Zielangabe einer Struktur-Ersetzung („Absätze 2 und 3“, „Sätze“, „Nummer 4a“) → Ebene. */
  private static @Nullable Ebene strukturEbene(String ziel) {
    var erstesWort = ziel.split("\\s+", 2)[0];
    return switch (erstesWort) {
      case "Absatz", "Absätze" -> Ebene.ABSATZ;
      case "Satz", "Sätze" -> Ebene.SATZ;
      case "Nummer", "Nummern" -> Ebene.NUMMER;
      case "Buchstabe", "Buchstaben" -> Ebene.BUCHSTABE;
      default -> null;
    };
  }

  private static Stelle.Komponente komponenteFuer(String ebene, String nummer) {
    return switch (ebene) {
      case "Absatz" -> new Stelle.AbsatzNr(nummer);
      case "Satz" -> new Stelle.SatzNr(nummer);
      case "Nummer" -> new Stelle.NummerNr(nummer);
      case "Buchstabe" -> new Stelle.BuchstabeNr(nummer);
      default -> throw new IllegalArgumentException("Unbekannte Ebene: " + ebene);
    };
  }

  /** Normalisiert die Ebenenwörter (auch Dativ-/Pluralformen) auf den Basisnamen. */
  private static @Nullable String ebeneAusWort(String wort) {
    return switch (wort) {
      case "Absatz", "Absätze", "Absätzen" -> "Absatz";
      case "Satz", "Sätze", "Sätzen" -> "Satz";
      case "Nummer", "Nummern" -> "Nummer";
      case "Buchstabe", "Buchstaben" -> "Buchstabe";
      default -> null;
    };
  }

  /**
   * Zerlegt den Zitatblock einer §-Bereichs-Neufassung („§ 52 (weggefallen) § 53 (weggefallen) …“)
   * an den „§ N“-Grenzen und erzeugt je eine {@link Neufassung} für den betroffenen Paragraphen.
   */
  private static Optional<Aenderungsbefehl> paragraphBereichNeufassung(
      String block, Stelle kontext, Provenienz provenienz) {
    var teile = new ArrayList<Aenderungsbefehl>();
    var stuecke = block.strip().split("(?=§\\s*\\d)");
    for (var stueck : stuecke) {
      var s = stueck.strip();
      if (s.isEmpty()) {
        continue;
      }
      var pm = Pattern.compile("^§\\s*(\\d+[a-z]?)\\b").matcher(s);
      if (!pm.find()) {
        return Optional.empty();
      }
      var stelle = kontext.plus(new Stelle(List.of(new Stelle.Paragraph(pm.group(1)))));
      teile.add(new Neufassung(stelle, s, provenienz));
    }
    if (teile.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile));
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
