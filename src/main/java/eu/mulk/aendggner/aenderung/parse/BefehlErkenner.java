package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.FussnotenAufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.GliederungsUeberschriften;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.SatznummerierungStreichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Sammelbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturErsetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautVoranstellung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuAbsatz;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuSatz;
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

  // Der Doppelpunkt fehlt gelegentlich (Seitenumbruch-Artefakt); für einen Punkt mit Unterpunkten
  // ist die Rahmenform trotzdem eindeutig.
  private static final Pattern KONTEXT =
      Pattern.compile("^(?:In )?(.+?) (?:wird|werden) wie folgt geändert:?$");

  // Aufzählungslabel, das in Entwürfen/Drucksachen vor dem Zitat steht („… gefasst: 3. „…““).
  private static final String ENUM = "((?:\\d+[a-z]?\\.|[a-z]{1,3}\\))\\s*)?";

  // Verb der Neufassung. Neben „wird/werden wie folgt gefasst“ (Bund/Bayern) auch „erhält/erhalten
  // folgende Fassung“ — die in mehreren Ländern (Schleswig-Holstein, Niedersachsen) übliche Form.
  // Rein zusätzliche Alternation ohne eigene Fanggruppe, damit die Gruppennummern gleich bleiben.
  private static final String NEUFASSUNG_VERB =
      "(?:(?:wird|werden) wie folgt gefasst|(?:erhält|erhalten) folgende Fassung)";

  private static final Pattern NEUFASSUNG =
      Pattern.compile("^(.+?) " + NEUFASSUNG_VERB + ": " + ENUM + Z + "\\.?$");

  // „§ 2 Absatz 2 wird durch die folgenden Absätze 2 und 3 ersetzt: „…““ (neues BGBl-Format);
  // auch „Die Überschrift wird durch die folgende Überschrift ersetzt: „…““ (Entwürfe).
  // Der optionale Enumerator-Präfix („3. “, „a) “) fängt die Entwurfs-/Drucksachenform ab, bei der
  // die Aufzählungsbezeichnung außerhalb des Zitats steht („… ersetzt: 3. „…““); er wird dem
  // Ersatztext wieder vorangestellt, da das Label sonst verloren ginge.
  private static final Pattern STRUKTUR_ERSETZUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) durch (?:den |die |das )?folgende[nrs]? (.+?) ersetzt: "
              + ENUM
              + Z
              + "\\.?$");

  // „Nach § 33 werden die folgenden Überschriften zu Teil 3 und zu Teil 3 Abschnitt 1 eingefügt:
  // „…““ — neue Gliederungs-Überschriften hinter einem Anker-§.
  private static final Pattern GLIEDERUNG_UEBERSCHRIFT_EINFUEGUNG =
      Pattern.compile(
          "^Nach ((?:§|Art\\.) \\S+) (?:wird|werden) (?:die |der |das )?folgenden? Überschrift(?:en)? "
              + "zu (.+?) (?:ein|an)gefügt: "
              + Z
              + "\\.?$");

  // „Die bisherigen Überschriften zu Teil 4 und Teil 4 Abschnitt 1 werden durch die folgende
  // Überschrift zu Abschnitt 2 ersetzt: „…““.
  private static final Pattern GLIEDERUNG_UEBERSCHRIFT_ERSETZUNG =
      Pattern.compile(
          "^Die bisherigen? Überschrift(?:en)? zu (.+?) (?:wird|werden) durch "
              + "(?:die |der |das )?folgenden? Überschrift(?:en)? zu (.+?) ersetzt: "
              + Z
              + "\\.?$");

  // „In Anlage 7 wird die Überschrift durch die folgende Überschrift ersetzt: „…““ — Neufassung der
  // Überschrift einer benannten Einheit (Anlage/Gliederung/Paragraph); das „die Überschrift“-Objekt
  // steht hier zwischen Stelle und „durch“, weshalb STRUKTUR_ERSETZUNG nicht greift.
  private static final Pattern UEBERSCHRIFT_ERSETZUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) die Überschrift durch "
              + "(?:die |den |das )?folgende[nrs]? Überschrift ersetzt: "
              + Z
              + "\\.?$");

  // Das Objekt nach „durch“ darf verkürzt sein („… durch „Y“ ersetzt“, BR-Drucksachen).
  private static final Pattern ERSETZUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (jeweils )?"
              + WOERTER
              + " "
              + Z
              + " (?:jeweils )?durch (?:"
              + WOERTER
              + " )?"
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

  // „In Nummer 2 werden nach den Wörtern «1» die Wörter «2» durch die Wörter «3» ersetzt.“ —
  // Ersetzung mit Positionsanker; der Anker präzisiert nur die Fundstelle, die Eindeutigkeits-
  // prüfung des Anwenders schützt vor Fehlgriffen.
  private static final Pattern ERSETZUNG_MIT_ANKER =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?nach "
              + "(?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " "
              + WOERTER
              + " "
              + Z
              + " durch (?:"
              + WOERTER
              + " )?"
              + Z
              + " ersetzt\\.$");

  // „… wird dem Wort „Anforderungen“ das Wort „dortigen“ vorangestellt.“
  private static final Pattern WORT_VORANSTELLUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?"
              + "(?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " "
              + WOERTER
              + " "
              + Z
              + " vorangestellt\\.$");

  // „In Absatz 1 Satz 2 wird der Punkt am Ende durch folgende Wörter / den folgenden Wortlaut
  // ersetzt: „…““ — der Ersatz steht als Zitatblock hinter dem Doppelpunkt.
  private static final Pattern PUNKT_DURCH_WORTLAUT =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (der Punkt|das Komma|das Semikolon) am Ende durch "
              + "(?:die |den )?folgende[n]? (?:Wörter|Wortlaut) ersetzt: "
              + Z
              + "\\.?$");

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
              + " (?:ein|an)gefügt\\.$");

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
          "^(Nach|Vor) (.+?) (?:wird|werden) (?:der |die |das )?folgende[nrs]? (?:neue[nrs]? )?(.+?) "
              + "(?:ein|an)gefügt: "
              + ENUM
              + Z
              + "\\.?$");

  // „Vor den Wörtern „Aus dem Bereich Verkehr:“ wird folgender Absatz 5 eingefügt: „…““ (GVBl. für
  // Berlin 17/2026, Artikel 1 Nr. 2 b) bb)) — die Position der neuen Einheit bestimmt ein Wortanker
  // statt einer Stellenangabe; das Ziel selbst erbt der Befehl aus dem Kontextrahmen.
  private static final Pattern STRUKTUR_EINFUEGUNG_WORTANKER =
      Pattern.compile(
          "^(Nach|Vor) (?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " (?:wird|werden) (?:der |die |das )?folgende[nrs]? (?:neue[nrs]? )?(.+?) "
              + "(?:ein|an)gefügt: "
              + ENUM
              + Z
              + "\\.?$");

  // „In Kapitel 4 wird nach § 12 der folgende neue § 13 angefügt: „…““ — gliederungsbezogene
  // Einfügung. Die Gliederungsangabe nennt nur den Abschnitt, in dem die neue Einheit landet;
  // maßgeblich für die Position ist der Anker („nach § 12“), der ohnehin eindeutig ist.
  private static final Pattern STRUKTUR_EINFUEGUNG_IN_GLIEDERUNG =
      Pattern.compile(
          "^In (?:Buch|Teil|Kapitel|Abschnitt|Unterabschnitt|Titel) \\S+ (?:wird|werden) "
              + "(?i:(nach|vor)) (.+?) (?:der |die |das )?folgende[nrs]? (?:neue[nrs]? )?(.+?) "
              + "(?:ein|an)gefügt: "
              + ENUM
              + Z
              + "\\.?$");

  // Auch mit Artefakt-Toleranz: verdoppeltes „wird“ und Leerzeichen vor dem Doppelpunkt
  // („In § 51 Absatz 1 wird folgender Satz wird angefügt : „…““, BR-Drs).
  private static final Pattern STRUKTUR_ANFUEGUNG_MIT_STELLE =
      Pattern.compile(
          "^(?:Dem|Der|In) (.+?) (?:wird|werden) (?:der |die |das )?folgende[nrs]? (.+?)"
              + "(?: wird| werden)? angefügt ?: "
              + ENUM
              + Z
              + "\\.?$");

  private static final Pattern STRUKTUR_ANFUEGUNG =
      Pattern.compile(
          "^(?:Der |Die |Das )?[Ff]olgende[nrs]? (.+?) (?:wird|werden) angefügt ?: "
              + ENUM
              + Z
              + "\\.?$");

  // „Der Nummer 1 wird folgende Nummer 1 vorangestellt: „…““ bzw. (im Kontextrahmen)
  // „Folgende Nummer 1 wird vorangestellt: „…““ — Einfügung vor der genannten Einheit.
  private static final Pattern VORANSTELLUNG_MIT_STELLE =
      Pattern.compile(
          "^(?:Dem|Der) (.+?) (?:wird|werden) (?:der |die |das )?folgende[nrs]? (.+?) "
              + "vorangestellt: "
              + ENUM
              + Z
              + "\\.?$");

  private static final Pattern VORANSTELLUNG =
      Pattern.compile(
          "^(?:Der |Die |Das )?[Ff]olgende[nrs]? (.+?) (?:wird|werden) vorangestellt: "
              + ENUM
              + Z
              + "\\.?$");

  // „Folgender Absatz 2 wird eingefügt: „…““ — ohne Anker; die Position ergibt sich aus der
  // Bezeichnung (nach dem Vorgänger, hier Absatz 1).
  private static final Pattern EINFUEGUNG_OHNE_ANKER =
      Pattern.compile(
          "^(?:Der |Die |Das )?[Ff]olgende[nrs]? "
              + "(Absatz \\d+[a-z]?|Nummer \\d+[a-z]?|Buchstabe [a-z]{1,3}) "
              + "(?:wird|werden) eingefügt: "
              + ENUM
              + Z
              + "\\.?$");

  private static final Pattern AUFHEBUNG = Pattern.compile("^(.+?) (?:wird|werden) aufgehoben\\.$");

  // „Fußnote 1 wird aufgehoben.“ / „Die Fußnoten 9 und 10 werden aufgehoben.“ (bayerisches
  // Landesrecht; Fußnoten stehen dort als „ⁿ) [Amtl. Anm.:] …“-Zeilen im Normtext).
  private static final Pattern FUSSNOTE_AUFHEBUNG =
      Pattern.compile(
          "^(?:Die )?Fußnoten? (\\d+(?:(?:, | und )\\d+)*) (?:wird|werden) aufgehoben\\.$");
  private static final Pattern FUSSNOTEN_TRENNER = Pattern.compile(", | und ");

  // „In Satz 1 wird die Satznummerierung „1“ gestrichen.“ (bayerisches Landesrecht) — etwa
  // nachdem der zweite und letzte Satz eines Absatzes aufgehoben wurde.
  private static final Pattern SATZNUMMERIERUNG_STREICHUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) die Satznummerierung " + Z + " gestrichen\\.$");

  // „Die Überschrift von Teil 3 Abschnitt 2 wird gestrichen.“ — Streichung einer Gliederungs-
  // Überschrift (die Wörter-Streichung STREICHUNG erfordert dagegen ein Zitat).
  private static final Pattern UEBERSCHRIFT_STREICHUNG =
      Pattern.compile("^(Die Überschrift (?:von|des|der|zu) .+?) wird gestrichen\\.$");

  // „Die Absatzbezeichnung „(2)“ wird gestrichen.“
  private static final Pattern ABSATZBEZEICHNUNG_STREICHUNG =
      Pattern.compile("^Die Absatzbezeichnung " + Z + " wird gestrichen\\.$");

  // Inhaltsübersicht: „Die Angabe(n) zu <…> wird/werden wie folgt gefasst / durch … ersetzt /
  // gestrichen.“ Das Ziel (Gruppe 1) benennt die Angabe-Zeile(n); der Lookahead (?!«) verhindert,
  // dass zitierte Wort-Angaben („Die Angabe „X“ wird gestrichen“) hier hängen bleiben.
  private static final Pattern INHALTSUEBERSICHT_ANGABE =
      Pattern.compile(
          "^Die Angaben? (?:zu den |zu der |zu |zur |zum |von )?(?!«)(.+?) (?:wird|werden) "
              + "(?:wie folgt gefasst: "
              + Z
              + "|durch (?:die )?folgende[nrs]? Angaben? ersetzt: "
              + Z
              + "|gestrichen)\\.?$");

  // „In der Inhaltsübersicht wird die Angabe zu § 5a gestrichen.“
  private static final Pattern INHALTSUEBERSICHT_STREICHUNG =
      Pattern.compile(
          "^In der Inhaltsübersicht (?:wird|werden) die Angaben? "
              + "(?:zu den |zu der |zu |zur |zum |von )?(?!«)(.+?) gestrichen\\.$");

  private static final Pattern STREICHUNG =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?" + WOERTER + " " + Z + " gestrichen\\.$");

  // „Die Angabe „X“ wird gestrichen.“ — ohne Stellenangabe (Kontext liefert das Ziel); tritt vor
  // allem als rechte Klausel eines Verbunds auf („… ersetzt und die Angabe „X“ wird gestrichen“).
  private static final Pattern STREICHUNG_OHNE_STELLE =
      Pattern.compile(
          "^(?:Die Wörter|Das Wort|Die Angabe|Die Zahl) " + Z + " (?:wird|werden) "
              + "(?:jeweils )?gestrichen\\.$");

  // „In Nr. 2 werden die Angabe „X“ und die Angabe „Y“ gestrichen.“ — mehrere Streichobjekte
  // unter einem gemeinsamen „gestrichen“.
  private static final Pattern STREICHUNG_MEHRFACH =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?("
              + WOERTER
              + " «\\d+»(?:(?:, | und | sowie )"
              + WOERTER
              + " «\\d+»)+) gestrichen\\.$");

  // „§ 9 wird gestrichen.“, „Absatz 3 wird gestrichen.“, „Die §§ 34 bis 39 werden gestrichen.“,
  // „Der bisherige Teil 3 wird gestrichen.“ — Streichung ganzer Struktureinheiten (semantisch eine
  // Aufhebung). Greift erst, wenn die Wörter-Streichung STREICHUNG (die ein Zitat verlangt) und die
  // Überschrift-/Absatzbezeichnungs-Streichungen nicht passen.
  private static final Pattern STRUKTUR_STREICHUNG =
      Pattern.compile("^(.+?) (?:wird|werden) gestrichen\\.$");

  private static final Pattern UMNUMMERIERUNG =
      Pattern.compile(
          "^(?:Der bisherige |Die bisherige |Das bisherige )?(.+?) wird (?:zu )?"
              + "(§|Art\\.|Absatz|Abs\\.|Satz|Nummer|Nr\\.|Buchstabe|Buchst\\."
              + "|Teil|Abschnitt|Unterabschnitt|Buch|Kapitel|Anlage) "
              + "(\\d+[a-z]?)\\.$");

  // „Die bisherigen Absätze 2 bis 4 werden zu den Absätzen 3 bis 5.“ bzw. „Die bisherigen Nummern 4
  // bis 6 werden die Nummern 8 bis 10.“ — Bereichs-Umnummerierung, in Einzelbefehle aufgelöst.
  private static final Pattern UMNUMMERIERUNG_BEREICH =
      Pattern.compile(
          "^Die (?:bisherigen )?(?:Absätze|Abs\\.|Sätze|Nummern|Nrn?\\.|Buchstaben|Buchst\\.) "
              + "(\\d+) bis (\\d+) "
              + "werden (?:zu den |die )?(Absätzen|Sätzen|Nummern|Buchstaben|Absätze|Sätze"
              + "|Abs\\.|Nrn?\\.|Buchst\\.) "
              + "(\\d+) bis (\\d+)\\.$");

  // „Die §§ 46 und 47 werden zu den §§ 34 und 35.“ — paarweise §-Umnummerierung (auch Bereiche).
  private static final Pattern UMNUMMERIERUNG_PARAGRAPHEN =
      Pattern.compile("^Die (§§|Artt?\\.) (.+?) werden (?:zu den \\1 |zu \\1 |die \\1 )(.+?)\\.$");

  // „Die §§ 52 bis 56 werden wie folgt gefasst: „§ 52 (weggefallen) …““ — Neufassung eines §-Bereichs;
  // der Zitatblock wird an „§ N“-Grenzen in Einzel-Neufassungen zerlegt.
  private static final Pattern PARAGRAPH_BEREICH_NEUFASSUNG =
      Pattern.compile(
          "^Die (?:§§|Artt?\\.) (\\d+[a-z]?) bis (\\d+[a-z]?) " + NEUFASSUNG_VERB + ": " + Z + "\\.?$");

  // „Der Wortlaut wird Absatz 1.“, bayerisch auch „Der bisherige Wortlaut wird Abs. 5.“ und
  // „Der Wortlaut wird Satz 1.“
  private static final Pattern WORTLAUT_ZU_ABSATZ =
      Pattern.compile("^Der (?:bisherige )?Wortlaut wird (Absatz|Abs\\.|Satz) (\\d+[a-z]?)\\.$");

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
          "^In der Inhaltsübersicht (?:wird|werden) (nach|vor) der Angabe "
              + "(?:zu |zur |zum |von )?(.+?) "
              + "(?:die |der |das )?folgende[nrs]? Angabe(?:n)?(?: zu .+?)? (?:ein|an)gefügt: "
              + Z
              + "\\.?$");

  // Variante innerhalb eines Kontextrahmens „Die Inhaltsübersicht wird wie folgt geändert:“.
  private static final Pattern ANGABE_EINFUEGUNG =
      Pattern.compile(
          "^(Nach|Vor) der Angabe (?:zu |zur |zum |von )?(.+?) (?:wird|werden) "
              + "(?:die |der |das )?folgende[nrs]? Angabe(?:n)?(?: zu .+?)? (?:ein|an)gefügt: "
              + Z
              + "\\.?$");

  private static final Pattern EBENE_BEZEICHNUNG =
      Pattern.compile(
          "^(?:(?:§|Art\\.) (\\d+[a-z]?)|(?:Absatz|Abs\\.) (\\d+[a-z]?)|Satz(?: (\\d+[a-z]?))?|Sätze"
              + "|(?:Nummer|Nr\\.) (\\d+[a-z]?)|(?:Buchstabe|Buchst\\.) ([a-z]{1,3})"
              + "|(Absätze .+|Abs\\. .+|Sätze .+|Nummern .+|Nrn?\\. .+|Buchstaben .+"
              + "|Buchst\\. .+))$");

  // „In <Stelle> werden die Wörter «1» durch die Wörter «2» und die Angabe «3» durch die Wörter «4»
  // ersetzt.“ — mehrere Ersetzungspaare unter einem gemeinsamen „ersetzt“. Die Mitte (Gruppe 2)
  // wird an „ und “ in Einzelpaare zerlegt und je gegen EIN_ERSETZUNGS_PAAR validiert.
  private static final Pattern PAAR_ERSETZUNG =
      Pattern.compile("^(?:In )?(.+?) (?:wird|werden) (.+(?: und | sowie |, ).+) ersetzt\\.$");
  private static final Pattern EIN_ERSETZUNGS_PAAR =
      Pattern.compile(
          "^(?:jeweils )?"
              + WOERTER
              + " "
              + Z
              + " (?:jeweils )?durch (?:"
              + WOERTER
              + " )?"
              + Z
              + "$");
  // Paare trennen sich an „ und “/„ sowie “ sowie an Kommata vor dem nächsten Wörter-Objekt.
  private static final Pattern PAAR_SEP =
      Pattern.compile(" und | sowie |,\\s+(?=(?:die Wörter|das Wort|die Angabe|die Zahl) )");

  // „Die bisherigen Absätze 6 und 7 werden die Absätze 1 und 2.“ — koordinierte Umnummerierung.
  private static final Pattern UMNUMMERIERUNG_KOORDINIERT =
      Pattern.compile(
          "^Die (?:bisherigen )?(Absätze|Abs\\.|Sätze|Nummern|Nrn?\\.|Buchstaben|Buchst\\.)"
              + " (\\d+[a-z]?) und (\\d+[a-z]?) "
              + "werden (?:zu den |die )?(?:Absätze[n]?|Abs\\.|Sätze[n]?|Nummern|Nrn?\\."
              + "|Buchstaben|Buchst\\.) "
              + "(\\d+[a-z]?) und (\\d+[a-z]?)\\.$");

  // „In Absatz 3 Satz 1 wird vor dem Punkt am Ende ein Komma und werden die Wörter „…“
  // eingefügt.“ — läuft auf eine Ersetzung des Schlusspunkts durch „, … .“ hinaus.
  private static final Pattern KOMMA_UND_WOERTER_VOR_PUNKT =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) vor dem Punkt am Ende ein Komma und "
              + "(?:wird|werden) "
              + WOERTER
              + " "
              + Z
              + " eingefügt\\.$");

  // „In Nummer 24 werden nach den Wörtern «1» die Wörter «2» und nach der Angabe «3» ein Komma
  // und die Angabe «4» eingefügt.“ — mehrere Einfügepaare unter einem gemeinsamen „eingefügt“.
  private static final Pattern EINFUEGUNGS_PAARE =
      Pattern.compile(
          "^(?:In )?(.+?) (?:wird|werden) (?:jeweils )?((?:nach|vor) .+) eingefügt\\.$");
  private static final Pattern EINFUEGUNGS_PAAR_SEP =
      Pattern.compile("(?: und |,\\s+)(?=nach |vor )");
  private static final Pattern EIN_EINFUEGUNGS_PAAR =
      Pattern.compile(
          "^(nach|vor) (?:dem Wort|den Wörtern|der Angabe|der Zahl) "
              + Z
              + " (?:(ein Komma und )?(?:wird |werden )?"
              + WOERTER
              + " "
              + Z
              + "|(ein Komma|ein Semikolon))$");

  // „… ein Komma eingefügt und werden …“: Trennstellen eines Verbundbefehls sind „ und “ (ggf. mit
  // Komma) bzw. „, “ direkt vor „wird/werden“. Innerhalb von Zitaten steht „ und “ als «n» maskiert.
  private static final Pattern VERBUND_SEP =
      Pattern.compile(
          ",? und |, (?=wird\\b|werden\\b)"
              // Komma-Kette gleichrangiger Klauseln („… ersetzt, die Angabe «n» wird gestrichen
              // und …“) — nur vor einer Wortgruppen-Klausel mit maskiertem Zitat, damit echte
              // Relativsätze nicht getrennt werden.
              + "|, (?=(?:die Wörter|das Wort|die Angabe|die Zahl) «)");
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

  // „§ 50 wird zu § 38 und wird wie folgt geändert:“ — Umnummerierung als Begleitbefehl eines
  // Kontextrahmens; die Folgebefehle beziehen sich auf die neue Bezeichnung.
  private static final Pattern UMNUMMERIERUNGS_RAHMEN =
      Pattern.compile(
          "^(?:Der bisherige |Die bisherige |Das bisherige )?(.+?) wird (?:zu )?"
              + "(§|Art\\.|Absatz|Abs\\.|Satz|Nummer|Nr\\.|Buchstabe|Buchst\\."
              + "|Teil|Abschnitt|Unterabschnitt|Buch|Kapitel|Anlage)"
              + " (\\d+[a-z]?) und wird wie folgt geändert:$");

  /** Ein Kontextrahmen samt optionalem Begleitbefehl (Umnummerierung des Rahmens selbst). */
  record Rahmen(Stelle stelle, @Nullable Aenderungsbefehl begleitbefehl) {}

  /**
   * Wie {@link #kontextRahmen}, erkennt zusätzlich den Verbund „<alt> wird zu <neu> und wird wie
   * folgt geändert:“ — die Umnummerierung wird als Begleitbefehl geliefert, der Rahmen zeigt auf
   * die neue Bezeichnung.
   */
  static Optional<Rahmen> rahmenMitBefehl(String text, Stelle kontext, Provenienz provenienz) {
    var einfach = kontextRahmen(text);
    if (einfach.isPresent()) {
      return Optional.of(new Rahmen(einfach.get(), null));
    }
    var m = UMNUMMERIERUNGS_RAHMEN.matcher(text);
    if (m.matches()) {
      var alt = StellenParser.parse(m.group(1));
      if (alt.isPresent()) {
        var neu = new Stelle(List.of(komponenteFuer(m.group(2), m.group(3))));
        var befehl =
            new Umnummerierung(kontext.plus(alt.get()), kontext.plus(neu), provenienz);
        return Optional.of(new Rahmen(neu, befehl));
      }
    }
    return Optional.empty();
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
  /**
   * Obergrenze für die Mustersuche: Zitate sind zu Platzhaltern maskiert, echte Befehlssätze
   * deshalb kurz. Ein Riesentext ist ein Extraktionsschaden (verschluckte Zitate) — er bliebe
   * ohnehin unbekannt, würde die Backtracking-Muster aber quadratisch teuer machen.
   */
  private static final int MAX_BEFEHLSLAENGE = 4000;

  static Optional<Aenderungsbefehl> erkenne(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {
    if (text.length() > MAX_BEFEHLSLAENGE) {
      return Optional.empty();
    }
    var einzeln = erkenneEinzeln(text, kontext, zitate, provenienz);
    if (einzeln.isPresent()) {
      return einzeln;
    }
    var paare = erkennePaarErsetzung(text, kontext, zitate, provenienz);
    if (paare.isPresent()) {
      return paare;
    }
    var einfuegungen = erkenneEinfuegungsPaare(text, kontext, zitate, provenienz);
    if (einfuegungen.isPresent()) {
      return einfuegungen;
    }
    return erkenneVerbund(text, kontext, zitate, provenienz);
  }

  private static Optional<Aenderungsbefehl> erkenneEinzeln(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {

    Matcher m;

    // Inhaltsübersichts-Angaben zuerst prüfen, bevor NEUFASSUNG/STRUKTUR_ERSETZUNG die Phrase
    // strukturell (aber mit unparsbarer Stelle) an sich ziehen.
    if ((m = INHALTSUEBERSICHT_ANGABE.matcher(text)).matches()) {
      var basis = mitInhaltsuebersicht(kontext);
      var stellen = StellenParser.parseMehrfach(angabenZiel(m.group(1)));
      var zitatIndex = m.group(2) != null ? m.group(2) : m.group(3);
      if (zitatIndex == null) {
        // „… gestrichen.“
        if (stellen.isEmpty()) {
          return Optional.empty();
        }
        var teile =
            stellen.stream()
                .map(s -> (Aenderungsbefehl) new Aufhebung(basis.plus(s), provenienz))
                .toList();
        return Optional.of(teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile));
      }
      var neuerText = zitat(zitate, zitatIndex);
      if (stellen.isEmpty()) {
        // Ziel unparsbar: als Neufassung der Inhaltsübersicht typisieren (Anwendung: manuell).
        return Optional.of(new Neufassung(basis, neuerText, provenienz));
      }
      if (stellen.size() == 1) {
        return Optional.of(new Neufassung(basis.plus(stellen.get(0)), neuerText, provenienz));
      }
      // Bereich („Die Angaben zu den §§ 34 bis 45 …“): erster/letzter bestimmen die Spanne.
      return Optional.of(
          new StrukturErsetzung(
              basis.plus(stellen.get(0)),
              basis.plus(stellen.get(stellen.size() - 1)),
              Ebene.PARAGRAPH,
              neuerText,
              provenienz));
    }

    if ((m = INHALTSUEBERSICHT_STREICHUNG.matcher(text)).matches()) {
      var basis = mitInhaltsuebersicht(kontext);
      var stellen = StellenParser.parseMehrfach(angabenZiel(m.group(1)));
      if (stellen.isEmpty()) {
        return Optional.empty();
      }
      var teile =
          stellen.stream()
              .map(s -> (Aenderungsbefehl) new Aufhebung(basis.plus(s), provenienz))
              .toList();
      return Optional.of(teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile));
    }

    if ((m = PARAGRAPH_BEREICH_NEUFASSUNG.matcher(text)).matches()) {
      return paragraphBereichNeufassung(zitat(zitate, m.group(3)), kontext, provenienz);
    }

    if ((m = NEUFASSUNG.matcher(text)).matches()) {
      var stellen = StellenParser.parseMehrfach(m.group(1));
      var neuerText = mitEnumerator(m.group(2), stellen, zitat(zitate, m.group(3)));
      if (stellen.size() == 1) {
        return Optional.of(new Neufassung(kontext.plus(stellen.get(0)), neuerText, provenienz));
      }
      if (stellen.size() > 1) {
        // „Die bisherigen Sätze 4 und 5 werden wie folgt gefasst: „…““ — ein zusammenhängender
        // Bereich wird durch einen Block ersetzt (Ebene aus dem Ziel abgeleitet).
        return koordinierteErsetzung(stellen, null, kontext, neuerText, provenienz);
      }
      return Optional.empty();
    }

    if ((m = WORTLAUT_ZU_ABSATZ.matcher(text)).matches()) {
      if (m.group(1).equals("Satz")) {
        return Optional.of(new WortlautZuSatz(kontext, m.group(2), provenienz));
      }
      return Optional.of(new WortlautZuAbsatz(kontext, m.group(2), provenienz));
    }

    if ((m = FUSSNOTE_AUFHEBUNG.matcher(text)).matches()) {
      var nummern = List.of(FUSSNOTEN_TRENNER.split(m.group(1)));
      return Optional.of(new FussnotenAufhebung(kontext, nummern, provenienz));
    }

    if ((m = SATZNUMMERIERUNG_STREICHUNG.matcher(text)).matches()) {
      var nummer = wortZitat(zitate, m.group(2));
      return ausStellen(
          m.group(1), s -> new SatznummerierungStreichung(kontext.plus(s), nummer, provenienz));
    }

    if ((m = UEBERSCHRIFT_ERSETZUNG.matcher(text)).matches()) {
      var neuerText = zitat(zitate, m.group(2));
      return StellenParser.parse(m.group(1))
          .map(
              s ->
                  new Neufassung(
                      kontext.plus(s).plus(new Stelle(List.of(new Stelle.Ueberschrift()))),
                      neuerText,
                      provenienz));
    }

    if ((m = GLIEDERUNG_UEBERSCHRIFT_EINFUEGUNG.matcher(text)).matches()) {
      var anker = StellenParser.parse(m.group(1));
      var neue = gliederungsPfade(m.group(2));
      if (anker.isEmpty() || neue.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new GliederungsUeberschriften(
              kontext.plus(anker.get()),
              neue.stream().map(pfad -> pfad.get(pfad.size() - 1)).toList(),
              List.of(),
              zitat(zitate, m.group(3)),
              provenienz));
    }

    if ((m = GLIEDERUNG_UEBERSCHRIFT_ERSETZUNG.matcher(text)).matches()) {
      var alte = gliederungsPfade(m.group(1));
      var neue = gliederungsPfade(m.group(2));
      if (alte.isEmpty() || neue.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new GliederungsUeberschriften(
              kontext,
              neue.stream().map(pfad -> pfad.get(pfad.size() - 1)).toList(),
              alte,
              zitat(zitate, m.group(3)),
              provenienz));
    }

    if ((m = STRUKTUR_ERSETZUNG.matcher(text)).matches()) {
      var ziel = m.group(2).strip();
      var stellen = StellenParser.parseMehrfach(m.group(1));
      if (stellen.isEmpty()) {
        return Optional.empty();
      }
      // Entwurfsform „… ersetzt: 3. „…““: das außerhalb des Zitats stehende Label wieder anfügen.
      var neuerText = mitEnumerator(m.group(3), stellen, zitat(zitate, m.group(4)));
      // „durch die folgende Überschrift ersetzt“ ist eine Neufassung der Überschrift,
      // „§ 19 wird durch den folgenden § 19 ersetzt“ eine Neufassung des Paragraphen,
      // „Die Inhaltsübersicht wird durch die folgende Inhaltsübersicht ersetzt“ eine Neufassung der
      // Inhaltsübersicht (die der Applier stets zur manuellen Prüfung markiert).
      if (stellen.size() == 1
          && (ziel.equals("Überschrift")
              || ziel.equals("Inhaltsübersicht")
              || ziel.matches("(?:§|Art\\.)\\s*\\d+[a-z]?"))) {
        return Optional.of(new Neufassung(kontext.plus(stellen.get(0)), neuerText, provenienz));
      }
      var ebene = strukturEbene(ziel);
      if (ebene == null) {
        return Optional.empty();
      }
      if (ebene == Ebene.PARAGRAPH) {
        // „§ 71 wird durch die folgenden §§ 71 bis 71p ersetzt: „…““ bzw. „Die §§ 42 bis 45 werden
        // durch die folgenden §§ 42 bis 45 ersetzt: „…““ — ein §-Bereich wird durch einen §-Block
        // ersetzt.
        var first = stellen.get(0);
        var last = stellen.get(stellen.size() - 1);
        return Optional.of(
            new StrukturErsetzung(
                kontext.plus(first),
                stellen.size() > 1 ? kontext.plus(last) : null,
                Ebene.PARAGRAPH,
                neuerText,
                provenienz));
      }
      if (stellen.size() == 1) {
        return Optional.of(
            new StrukturErsetzung(kontext.plus(stellen.get(0)), ebene, neuerText, provenienz));
      }
      // „Die Absätze 8 und 9 werden durch die folgenden Absätze 8 bis 10 ersetzt: „…““
      return koordinierteErsetzung(stellen, ebene, kontext, neuerText, provenienz);
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

    if ((m = ERSETZUNG_MIT_ANKER.matcher(text)).matches()) {
      var alt = wortZitat(zitate, m.group(3));
      var neu = wortZitat(zitate, m.group(4));
      return ausStellen(
          m.group(1), s -> new Ersetzung(kontext.plus(s), alt, neu, false, false, provenienz));
    }

    if ((m = WORT_VORANSTELLUNG.matcher(text)).matches()) {
      var anker = new WortAnker.VorWoertern(wortZitat(zitate, m.group(2)));
      var woerter = wortZitat(zitate, m.group(3));
      return ausStellen(
          m.group(1), s -> new WoerterEinfuegung(kontext.plus(s), anker, woerter, provenienz));
    }

    if ((m = PUNKT_DURCH_WORTLAUT.matcher(text)).matches()) {
      var alt = satzzeichen(m.group(2));
      var neu = wortZitat(zitate, m.group(3));
      return ausStellen(
          m.group(1), s -> new Ersetzung(kontext.plus(s), alt, neu, false, true, provenienz));
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
      var vorher = m.group(1).equalsIgnoreCase("vor");
      var anker = StellenParser.parse(angabenZiel(m.group(2)));
      if (anker.isEmpty()) {
        return Optional.empty();
      }
      var basis = mitInhaltsuebersicht(kontext);
      return Optional.of(
          new StrukturEinfuegung(
              basis.plus(anker.get()),
              vorher,
              Ebene.PARAGRAPH,
              null,
              zitat(zitate, m.group(3)),
              provenienz));
    }

    // Vor STRUKTUR_EINFUEGUNG: dort scheitert die Wortanker-Form am StellenParser („den Wörtern
    // «0»“) und verließe die Erkennung mit Optional.empty(), sodass kein späteres Muster mehr zum
    // Zuge käme.
    if ((m = STRUKTUR_EINFUEGUNG_WORTANKER.matcher(text)).matches()) {
      var ebeneBez = ebeneUndBezeichnung(m.group(3));
      if (ebeneBez.isEmpty()) {
        return Optional.empty();
      }
      var ankerWoerter = wortZitat(zitate, m.group(2));
      var anker =
          m.group(1).equalsIgnoreCase("nach")
              ? new WortAnker.NachWoertern(ankerWoerter)
              : new WortAnker.VorWoertern(ankerWoerter);
      var textInhalt =
          mitEnumerator(
              m.group(4),
              labelFuer(ebeneBez.get().ebene(), ebeneBez.get().bezeichnung()),
              zitat(zitate, m.group(5)));
      return Optional.of(
          new StrukturEinfuegung(
              kontext,
              m.group(1).equalsIgnoreCase("vor"),
              ebeneBez.get().ebene(),
              ebeneBez.get().bezeichnung(),
              textInhalt,
              anker,
              provenienz));
    }

    if ((m = STRUKTUR_EINFUEGUNG.matcher(text)).matches()
        || (m = STRUKTUR_EINFUEGUNG_IN_GLIEDERUNG.matcher(text)).matches()) {
      var vorher = m.group(1).equalsIgnoreCase("vor");
      var stelle = StellenParser.parse(m.group(2));
      if (stelle.isEmpty()) {
        return Optional.empty();
      }
      var ebeneBez = ebeneUndBezeichnung(m.group(3));
      var textInhalt =
          mitEnumerator(
              m.group(4),
              ebeneBez.map(e -> labelFuer(e.ebene(), e.bezeichnung())).orElse(null),
              zitat(zitate, m.group(5)));
      if (ebeneBez.isEmpty()) {
        // „Nach § 60a werden die folgenden §§ 60b und 60c eingefügt: „…““ — Block mehrerer
        // Paragraphen (Aufteilung an den §-Überschriften erfolgt beim Anwenden). Signal:
        // Ebene PARAGRAPH mit bezeichnung == null.
        if (m.group(3).strip().matches("(?:§§|Artt?\\.)\\s*\\d.*")) {
          return Optional.of(
              new StrukturEinfuegung(
                  kontext.plus(stelle.get()),
                  vorher,
                  Ebene.PARAGRAPH,
                  null,
                  textInhalt,
                  provenienz));
        }
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

    if ((m = VORANSTELLUNG_MIT_STELLE.matcher(text)).matches()
        || (m = VORANSTELLUNG.matcher(text)).matches()) {
      // Bei der stellenlosen Form ist die neue Einheit zugleich der Anker (sie tritt vor die
      // gleichnamige bestehende Einheit).
      boolean mitStelle = m.pattern() == VORANSTELLUNG_MIT_STELLE;
      // „Dem Wortlaut werden die folgenden Abs. 1 bis 4 vorangestellt: „…““ (bayerisch): die
      // neuen Absätze treten vor den gesamten bisherigen Normtext.
      if (mitStelle && m.group(1).equals("Wortlaut")) {
        return Optional.of(
            new WortlautVoranstellung(kontext, zitat(zitate, m.group(4)), provenienz));
      }
      var ankerPhrase = m.group(1);
      var ebeneBez = ebeneUndBezeichnung(mitStelle ? m.group(2) : m.group(1));
      var stelle = StellenParser.parse(ankerPhrase);
      if (stelle.isEmpty() || ebeneBez.isEmpty()) {
        return Optional.empty();
      }
      var textInhalt =
          mitEnumerator(
              m.group(mitStelle ? 3 : 2),
              labelFuer(ebeneBez.get().ebene(), ebeneBez.get().bezeichnung()),
              zitat(zitate, m.group(mitStelle ? 4 : 3)));
      return Optional.of(
          new StrukturEinfuegung(
              kontext.plus(stelle.get()),
              true,
              ebeneBez.get().ebene(),
              ebeneBez.get().bezeichnung(),
              textInhalt,
              provenienz));
    }

    if ((m = EINFUEGUNG_OHNE_ANKER.matcher(text)).matches()) {
      var ebeneBez = ebeneUndBezeichnung(m.group(1));
      if (ebeneBez.isEmpty() || ebeneBez.get().bezeichnung() == null) {
        return Optional.empty();
      }
      var vorgaenger = vorgaengerLabel(ebeneBez.get().bezeichnung());
      if (vorgaenger == null) {
        return Optional.empty();
      }
      var textInhalt =
          mitEnumerator(
              m.group(2),
              labelFuer(ebeneBez.get().ebene(), ebeneBez.get().bezeichnung()),
              zitat(zitate, m.group(3)));
      var anker = komponenteFuerEbene(ebeneBez.get().ebene(), vorgaenger);
      if (anker == null) {
        return Optional.empty();
      }
      return Optional.of(
          new StrukturEinfuegung(
              kontext.plus(new Stelle(List.of(anker))),
              false,
              ebeneBez.get().ebene(),
              ebeneBez.get().bezeichnung(),
              textInhalt,
              provenienz));
    }

    if ((m = STRUKTUR_ANFUEGUNG_MIT_STELLE.matcher(text)).matches()) {
      var stelle = StellenParser.parse(m.group(1));
      var ebeneBez = ebeneUndBezeichnung(m.group(2));
      if (stelle.isEmpty() || ebeneBez.isEmpty()) {
        return Optional.empty();
      }
      var textInhalt =
          mitEnumerator(
              m.group(3),
              labelFuer(ebeneBez.get().ebene(), ebeneBez.get().bezeichnung()),
              zitat(zitate, m.group(4)));
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
      if (ebeneBez.isEmpty()) {
        return Optional.empty();
      }
      var textInhalt =
          mitEnumerator(
              m.group(2),
              labelFuer(ebeneBez.get().ebene(), erstesLabel(m.group(1))),
              zitat(zitate, m.group(3)));
      return Optional.of(
          new Anfuegung(
              kontext,
              ebeneBez.get().ebene(),
              ebeneBez.get().bezeichnung(),
              textInhalt,
              provenienz));
    }

    if ((m = KOMMA_UND_WOERTER_VOR_PUNKT.matcher(text)).matches()) {
      // Der Schlusspunkt wird durch „, <Wörter>.“ ersetzt.
      var neu = ", " + wortZitat(zitate, m.group(2)) + ".";
      return ausStellen(
          m.group(1), s -> new Ersetzung(kontext.plus(s), ".", neu, false, true, provenienz));
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

    if ((m = STREICHUNG_OHNE_STELLE.matcher(text)).matches()) {
      var woerter = wortZitat(zitate, m.group(1));
      return Optional.of(new Streichung(kontext, woerter, provenienz));
    }

    if ((m = STREICHUNG_MEHRFACH.matcher(text)).matches()) {
      var stellen = StellenParser.parseMehrfach(m.group(1));
      if (stellen.isEmpty()) {
        if (!StellenParser.istNurChapeau(m.group(1))) {
          return Optional.empty();
        }
        stellen = List.of(Stelle.LEER);
      }
      var objekte = new ArrayList<String>();
      var zm = Pattern.compile("«(\\d+)»").matcher(m.group(2));
      while (zm.find()) {
        objekte.add(wortZitat(zitate, zm.group(1)));
      }
      var teile = new ArrayList<Aenderungsbefehl>();
      for (var s : stellen) {
        for (var woerter : objekte) {
          teile.add(new Streichung(kontext.plus(s), woerter, provenienz));
        }
      }
      return Optional.of(teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile));
    }

    if ((m = STRUKTUR_STREICHUNG.matcher(text)).matches()) {
      return ausStellen(m.group(1), s -> new Aufhebung(kontext.plus(s), provenienz));
    }

    if ((m = UMNUMMERIERUNG_PARAGRAPHEN.matcher(text)).matches()) {
      var sigel = m.group(1).startsWith("Art") ? "Art. " : "§ ";
      return paragraphenUmnummerierung(sigel, m.group(2), m.group(3), kontext, provenienz);
    }

    if ((m = UMNUMMERIERUNG_BEREICH.matcher(text)).matches()) {
      return bereichsUmnummerierung(
          m.group(3), m.group(1), m.group(2), m.group(4), m.group(5), kontext, provenienz);
    }

    if ((m = UMNUMMERIERUNG_KOORDINIERT.matcher(text)).matches()) {
      var ebene = ebeneAusWort(m.group(1));
      if (ebene == null) {
        return Optional.empty();
      }
      // Absteigend anwenden (zweites Paar zuerst), damit sequenziell keine Labels kollidieren.
      var teile = new ArrayList<Aenderungsbefehl>();
      teile.add(paarUmnummerierung(ebene, m.group(3), m.group(5), kontext, provenienz));
      teile.add(paarUmnummerierung(ebene, m.group(2), m.group(4), kontext, provenienz));
      return Optional.of(new Sammelbefehl(teile));
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
      // „In dem Satzteil nach Nr. 3 werden … ersetzt.“ — reine Chapeau-Angabe ohne eigene
      // Komponente: die Ersetzung sucht im Text der Kontextstelle.
      if (!StellenParser.istNurChapeau(m.group(1))) {
        return Optional.empty();
      }
      stellen = List.of(Stelle.LEER);
    }
    var segmente = PAAR_SEP.split(m.group(2));
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
   * „In <Stelle> werden nach X die Wörter «1» und nach Y ein Komma und die Angabe «2» eingefügt.“
   * — mehrere Einfügepaare unter einem gemeinsamen „eingefügt“, aufgelöst in einen {@link
   * Sammelbefehl} von {@link WoerterEinfuegung}en (Kreuzprodukt mit koordinierter Stelle).
   */
  private static Optional<Aenderungsbefehl> erkenneEinfuegungsPaare(
      String text, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {
    var m = EINFUEGUNGS_PAARE.matcher(text);
    if (!m.matches()) {
      return Optional.empty();
    }
    var segmente = EINFUEGUNGS_PAAR_SEP.split(m.group(2));
    if (segmente.length < 2) {
      return Optional.empty();
    }
    var stellen = StellenParser.parseMehrfach(m.group(1));
    if (stellen.isEmpty()) {
      if (!StellenParser.istNurChapeau(m.group(1))) {
        return Optional.empty();
      }
      stellen = List.of(Stelle.LEER);
    }
    record Einfuegung(WortAnker anker, String woerter) {}
    var einfuegungen = new ArrayList<Einfuegung>();
    for (var segment : segmente) {
      var pm = EIN_EINFUEGUNGS_PAAR.matcher(segment.strip());
      if (!pm.matches()) {
        return Optional.empty();
      }
      var ankerWoerter = wortZitat(zitate, pm.group(2));
      var anker =
          pm.group(1).equals("nach")
              ? (WortAnker) new WortAnker.NachWoertern(ankerWoerter)
              : new WortAnker.VorWoertern(ankerWoerter);
      String woerter;
      if (pm.group(5) != null) {
        woerter = satzzeichen(pm.group(5));
      } else if (pm.group(3) != null) {
        woerter = ", " + wortZitat(zitate, pm.group(4));
      } else {
        woerter = wortZitat(zitate, pm.group(4));
      }
      einfuegungen.add(new Einfuegung(anker, woerter));
    }
    var teile = new ArrayList<Aenderungsbefehl>();
    for (var stelle : stellen) {
      for (var einfuegung : einfuegungen) {
        teile.add(
            new WoerterEinfuegung(
                kontext.plus(stelle), einfuegung.anker(), einfuegung.woerter(), provenienz));
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
  // Reine Wortoperationen tragen nie einen eigenen strukturellen Lokativ, sondern wirken innerhalb
  // des zuvor gesetzten Skopus (Gapping): „… und die Angabe „X“ wird gestrichen/eingefügt“.
  private static final Pattern REINE_WORT_OPERATION =
      Pattern.compile(
          "(?i)(?:die Angabe|nach der Angabe|vor der Angabe|die Wörter|die Worte|"
              + "nach den Wörtern|vor den Wörtern|das Wort)\\b");

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
        // Gemeinsames Schlussverb (Gapping): „die Angabe «A» wird durch die Angabe «B» und die
        // Angabe «C» wird durch die Angabe «D» ersetzt.“ — das „ersetzt“ der rechten Klausel
        // gilt auch links.
        var schluss = gemeinsamerVerbSchluss(rechts);
        if (schluss != null) {
          linksBefehl = erkenneAlsSatz(links + " " + schluss, kontext, zitate, provenienz);
        }
      }
      if (linksBefehl.isEmpty()) {
        continue;
      }
      var rechtsBefehl =
          erkenneRechteKlausel(links, linksBefehl.get(), rechts, kontext, zitate, provenienz);
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
   * Anfangsbuchstaben (eigenständiger Befehl wie „nach …“ → „Nach …“), (3) nach einer
   * Umnummerierung mit aufgelöstem Rückbezug („… wird Nummer 2 und in ihr werden …“ / „… und wie
   * folgt gefasst: …“), (4) mit vorangestelltem lokativem Präfix der linken Klausel („In
   * <Stelle> “).
   */
  private static Optional<Aenderungsbefehl> erkenneRechteKlausel(
      String links,
      Aenderungsbefehl linksBefehl,
      String rechts,
      Stelle kontext,
      ZitatExtraktor.Ergebnis zitate,
      Provenienz provenienz) {
    var gross = Character.toUpperCase(rechts.charAt(0)) + rechts.substring(1);
    // Nach einer Umnummerierung beziehen sich explizit lokative Folgeklauseln („… und in Satz 3
    // wird …“) auf die umnummerierte Einheit — deren neue Stelle wird zum Kontext der rechten
    // Klausel (nur wenn die Klausel nicht ihrerseits einen § nennt; Zitate sind bereits maskiert).
    if (linksBefehl instanceof Umnummerierung um
        && um.neu().paragraph().isPresent()
        && rechts.startsWith("in ")
        && !rechts.matches(".*(?:§|Art\\.)\\s*\\d.*")) {
      var imNeuen = erkenneAlsSatz(gross, um.neu(), zitate, provenienz);
      if (imNeuen.isPresent()) {
        return imNeuen;
      }
    }
    // „Der bisherige Wortlaut wird Abs. 5 und in Halbsatz 1 wird … ersetzt“ (bayerisch): die
    // lokative Folgeklausel bezieht sich auf den soeben nummerierten Absatz.
    if (linksBefehl instanceof WortlautZuAbsatz wz
        && rechts.startsWith("in ")
        && !rechts.matches(".*(?:§|Art\\.)\\s*\\d.*")) {
      var neuKontext =
          wz.stelle().plus(new Stelle(List.of(new Stelle.AbsatzNr(wz.nummer()))));
      var imNeuen = erkenneAlsSatz(gross, neuKontext, zitate, provenienz);
      if (imNeuen.isPresent()) {
        return imNeuen;
      }
    }
    // Gapping mit gemeinsamem Lokativ: „In Abs. 2 Satz 1 wird A ersetzt und die Angabe „B“ wird
    // gestrichen“ — die rechte Klausel ist eine reine Wortoperation ohne eigenen Lokativ und erbt
    // daher den Skopus der linken (sonst löste sie fälschlich auf Artikelebene auf). Nur wenn die
    // linke Klausel überhaupt einen Lokativ trägt und der so entstandene Befehl eine engere Stelle
    // als der bloße Kontext trifft.
    // Nach einer Umnummerierung wird die Wortoperation nicht auf die neue Stelle festgenagelt: sie
    // löst norm-weit auf (findet ihren Zieltext im umbenannten Absatz, ohne eine womöglich schon
    // bestehende Zielnummer fälschlich zu treffen) — das übernehmen die Zweige weiter unten.
    if (REINE_WORT_OPERATION.matcher(rechts).lookingAt()
        && !(linksBefehl instanceof Umnummerierung)) {
      var praefix0 = lokativerPraefix(links);
      var vm = WIRD_WERDEN.matcher(rechts);
      if (praefix0 != null && !praefix0.isBlank() && vm.find()) {
        // „die Angabe „X“ wird gestrichen“ → „In <loc> wird die Angabe „X“ gestrichen“: den Lokativ
        // der linken Klausel voranstellen und das Verb nach vorn ziehen (grammatische Normalform).
        var subjekt = rechts.substring(0, vm.start()).strip();
        var praedikat = rechts.substring(vm.end()).strip();
        var umgestellt =
            praefix0.strip() + " " + vm.group().strip() + " " + subjekt + " " + praedikat;
        var geerbt = erkenneAlsSatz(umgestellt, kontext, zitate, provenienz);
        if (geerbt.isPresent()
            && geerbt.get().stelle().komponenten().size() > kontext.komponenten().size()) {
          return geerbt;
        }
      }
    }
    var direkt = erkenneAlsSatz(rechts, kontext, zitate, provenienz);
    if (direkt.isPresent()) {
      return direkt;
    }
    if (!gross.equals(rechts)) {
      var alsBefehl = erkenneAlsSatz(gross, kontext, zitate, provenienz);
      if (alsBefehl.isPresent()) {
        return alsBefehl;
      }
    }
    if (linksBefehl instanceof Umnummerierung u) {
      var relativ = relativeStelle(u.neu(), kontext);
      if (!relativ.istLeer()) {
        // „Die bisherige Nummer 1 wird Nummer 2 und in ihr werden … ersetzt.“
        for (var pronomen : List.of("in ihr ", "in ihm ", "darin ")) {
          if (rechts.startsWith(pronomen)) {
            return erkenneAlsSatz(
                "In " + relativ.anzeigeText() + " " + rechts.substring(pronomen.length()),
                kontext,
                zitate,
                provenienz);
          }
        }
        // „Die bisherige Nummer 3 wird Nummer 4 und (wird) wie folgt gefasst: „…““
        if (rechts.startsWith("wie folgt ")) {
          return erkenneAlsSatz(
              relativ.anzeigeText() + " wird " + rechts, kontext, zitate, provenienz);
        }
        if (rechts.startsWith("wird wie folgt ") || rechts.startsWith("werden wie folgt ")) {
          return erkenneAlsSatz(
              relativ.anzeigeText() + " " + rechts, kontext, zitate, provenienz);
        }
      }
    }
    var praefix = lokativerPraefix(links);
    if (praefix != null) {
      var mitPraefix = erkenneAlsSatz(praefix + " " + rechts, kontext, zitate, provenienz);
      if (mitPraefix.isPresent()) {
        return mitPraefix;
      }
      // Verb-Ellipse („… ersetzt und die Angabe „X“ gestrichen.“): das geteilte „wird/werden“
      // wieder einsetzen.
      if (!WIRD_WERDEN.matcher(rechts).find()) {
        for (var verb : List.of(" wird ", " werden ")) {
          var ergaenzt = erkenneAlsSatz(praefix + verb + rechts, kontext, zitate, provenienz);
          if (ergaenzt.isPresent()) {
            return ergaenzt;
          }
        }
      }
    }
    return Optional.empty();
  }

  /** Die Komponenten von {@code voll} hinter dem Kontext-Präfix (leer, wenn nichts übrig bleibt). */
  private static Stelle relativeStelle(Stelle voll, Stelle kontext) {
    int praefix = kontext.komponenten().size();
    if (voll.komponenten().size() <= praefix) {
      return Stelle.LEER;
    }
    return new Stelle(voll.komponenten().subList(praefix, voll.komponenten().size()));
  }

  private static Optional<Aenderungsbefehl> erkenneAlsSatz(
      String klausel, Stelle kontext, ZitatExtraktor.Ergebnis zitate, Provenienz provenienz) {
    var satz = klausel.endsWith(".") ? klausel : klausel + ".";
    return erkenne(satz, kontext, zitate, provenienz);
  }

  private static final Pattern VERB_SCHLUSS =
      Pattern.compile(".*\\b(ersetzt|gestrichen|eingefügt|angefügt|aufgehoben)\\.$");

  /** Das gemeinsame Schlussverb einer Klauselkette („… ersetzt.“), falls vorhanden. */
  private static @Nullable String gemeinsamerVerbSchluss(String rechts) {
    var m = VERB_SCHLUSS.matcher(rechts);
    return m.matches() ? m.group(1) + "." : null;
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
   * Löst „Die §§ 46 und 47 werden zu den §§ 34 und 35.“ (auch Bereiche) in paarweise
   * §-Umnummerierungen auf. Beide Seiten werden zu Paragraphenlisten expandiert und zusammengeführt.
   */
  private static Optional<Aenderungsbefehl> paragraphenUmnummerierung(
      String sigel, String altPhrase, String neuPhrase, Stelle kontext, Provenienz provenienz) {
    var alt = StellenParser.parseMehrfach(sigel + altPhrase);
    var neu = StellenParser.parseMehrfach(sigel + neuPhrase);
    if (alt.isEmpty() || alt.size() != neu.size()) {
      return Optional.empty();
    }
    var teile = new ArrayList<Aenderungsbefehl>();
    for (int i = 0; i < alt.size(); i++) {
      teile.add(new Umnummerierung(kontext.plus(alt.get(i)), kontext.plus(neu.get(i)), provenienz));
    }
    return Optional.of(teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile));
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

  private static Umnummerierung paarUmnummerierung(
      String ebene, String alt, String neu, Stelle kontext, Provenienz provenienz) {
    return new Umnummerierung(
        kontext.plus(new Stelle(List.of(komponenteFuer(ebene, alt)))),
        kontext.plus(new Stelle(List.of(komponenteFuer(ebene, neu)))),
        provenienz);
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
      // „Im Satzteil vor Nummer 1 …“, „In der Angabe vor Nummer 1 …“ — reiner Chapeau-Qualifier
      // ohne eigene Stelle: die Operation bezieht sich auf die Kontextstelle.
      if (StellenParser.istNurChapeau(phrase)) {
        return Optional.of(bauer.apply(Stelle.LEER));
      }
      return Optional.empty();
    }
    if (stellen.size() == 1) {
      return Optional.of(bauer.apply(stellen.get(0)));
    }
    return Optional.of(new Sammelbefehl(stellen.stream().map(bauer).toList()));
  }

  /**
   * Baut aus einem zusammenhängenden, koordinierten Ziel-Bereich („Die Absätze 8 und 9 …“, „Die
   * bisherigen Sätze 4 und 5 …“) eine bereichsbezogene {@link StrukturErsetzung}: erstes und letztes
   * Ziel spannen den zu ersetzenden Bereich auf; der zitierte Block ersetzt ihn (Absatz-, Satz-,
   * Nummer- und Buchstaben-Bereiche; §-Bereiche laufen über den PARAGRAPH-Zweig).
   */
  private static Optional<Aenderungsbefehl> koordinierteErsetzung(
      List<Stelle> stellen,
      @Nullable Ebene ebeneHint,
      Stelle kontext,
      String block,
      Provenienz provenienz) {
    var first = stellen.get(0);
    var last = stellen.get(stellen.size() - 1);
    var ebene = ebeneHint != null ? ebeneHint : ebeneAusStelle(first);
    if (ebene == null || ebene == Ebene.PARAGRAPH) {
      return Optional.empty();
    }
    return Optional.of(
        new StrukturErsetzung(
            kontext.plus(first), kontext.plus(last), ebene, block, provenienz));
  }

  /** Die Ebene der feinsten Komponente einer Stelle (Buchstabe < Nummer < Satz < Absatz < §). */
  private static @Nullable Ebene ebeneAusStelle(Stelle stelle) {
    Ebene ebene = null;
    for (var komponente : stelle.komponenten()) {
      ebene =
          switch (komponente) {
            case Stelle.Paragraph p -> Ebene.PARAGRAPH;
            case Stelle.AbsatzNr a -> Ebene.ABSATZ;
            case Stelle.SatzNr s -> Ebene.SATZ;
            case Stelle.NummerNr n -> Ebene.NUMMER;
            case Stelle.BuchstabeNr b -> Ebene.BUCHSTABE;
            default -> ebene;
          };
    }
    return ebene;
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
      case "§", "§§", "Art.", "Artt." -> Ebene.PARAGRAPH;
      case "Absatz", "Absätze", "Abs." -> Ebene.ABSATZ;
      case "Satz", "Sätze" -> Ebene.SATZ;
      case "Nummer", "Nummern", "Nr.", "Nrn." -> Ebene.NUMMER;
      case "Buchstabe", "Buchstaben", "Buchst." -> Ebene.BUCHSTABE;
      default -> null;
    };
  }

  private static Stelle.Komponente komponenteFuer(String ebene, String nummer) {
    return switch (ebene) {
      case "§" -> new Stelle.Paragraph(nummer);
      case "Art." -> new Stelle.Paragraph(nummer, "Art.");
      case "Absatz", "Abs." -> new Stelle.AbsatzNr(nummer);
      case "Satz" -> new Stelle.SatzNr(nummer);
      case "Nummer", "Nr." -> new Stelle.NummerNr(nummer);
      case "Buchstabe", "Buchst." -> new Stelle.BuchstabeNr(nummer);
      case "Teil", "Abschnitt", "Unterabschnitt", "Buch", "Kapitel", "Anlage" ->
          new Stelle.Gliederungseinheit(ebene, nummer);
      default -> throw new IllegalArgumentException("Unbekannte Ebene: " + ebene);
    };
  }

  /** Normalisiert die Ebenenwörter (auch Dativ-/Pluralformen) auf den Basisnamen. */
  private static @Nullable String ebeneAusWort(String wort) {
    return switch (wort) {
      case "Absatz", "Absätze", "Absätzen", "Abs." -> "Absatz";
      case "Satz", "Sätze", "Sätzen" -> "Satz";
      case "Nummer", "Nummern", "Nr.", "Nrn." -> "Nummer";
      case "Buchstabe", "Buchstaben", "Buchst." -> "Buchstabe";
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
    var stuecke = block.strip().split("(?=(?:§|Art\\.)\\s*\\d)");
    for (var stueck : stuecke) {
      var s = stueck.strip();
      if (s.isEmpty()) {
        continue;
      }
      var pm = Pattern.compile("^(§|Art\\.)\\s*(\\d+[a-z]?)\\b").matcher(s);
      if (!pm.find()) {
        return Optional.empty();
      }
      var stelle =
          kontext.plus(new Stelle(List.of(new Stelle.Paragraph(pm.group(2), pm.group(1)))));
      teile.add(new Neufassung(stelle, s, provenienz));
    }
    if (teile.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile));
  }

  /**
   * Stellt dem Zitat das außerhalb stehende Aufzählungslabel („3. “, „a) “) wieder voran — aber
   * nur, wenn das Zitat es nicht schon trägt und es zum Ziel passt. Ein fremdes Label (die Zählung
   * des Änderungsdokuments selbst, das der Extraktor fälschlich vor das Zitat gezogen hat) wird
   * verworfen.
   */
  private static String mitEnumerator(
      @Nullable String enumerator, List<Stelle> stellen, String zitat) {
    return mitEnumerator(
        enumerator, stellen.isEmpty() ? null : erwartetesLabel(stellen.get(0)), zitat);
  }

  private static String mitEnumerator(
      @Nullable String enumerator, @Nullable String erwartet, String zitat) {
    if (enumerator == null) {
      return zitat;
    }
    var label = enumerator.strip();
    if (zitat.strip().startsWith(label)) {
      return zitat;
    }
    if (erwartet == null || erwartet.equals(label)) {
      return label + " " + zitat.strip();
    }
    return zitat;
  }

  /** Das Aufzählungslabel einer neuen Einheit („3.“, „a)“); {@code null} für andere Ebenen. */
  private static @Nullable String labelFuer(Ebene ebene, @Nullable String bezeichnung) {
    if (bezeichnung == null) {
      return null;
    }
    return switch (ebene) {
      case NUMMER -> bezeichnung + ".";
      case BUCHSTABE -> bezeichnung + ")";
      default -> null;
    };
  }

  /** Die erste Bezeichnung einer Pluralphrase („Nummern 9 bis 11“ → „9“). */
  private static @Nullable String erstesLabel(String phrase) {
    var m = Pattern.compile("\\b(\\d+[a-z]?|[a-z]{1,3})\\b").matcher(phrase);
    return m.find() ? m.group(1) : null;
  }

  /**
   * Die Bezeichnung des Vorgängers einer Einheit („2“ → „1“, „2a“ → „2“, „5c“ → „5b“); {@code
   * null}, wenn es keinen gibt (erste Einheit).
   */
  private static @Nullable String vorgaengerLabel(String bezeichnung) {
    var m = Pattern.compile("^(\\d+)([a-z])?$").matcher(bezeichnung);
    if (m.matches()) {
      if (m.group(2) == null) {
        int n = Integer.parseInt(m.group(1));
        return n > 1 ? String.valueOf(n - 1) : null;
      }
      char buchstabe = m.group(2).charAt(0);
      return buchstabe == 'a' ? m.group(1) : m.group(1) + (char) (buchstabe - 1);
    }
    if (bezeichnung.matches("^[b-z]$")) {
      return String.valueOf((char) (bezeichnung.charAt(0) - 1));
    }
    return null;
  }

  private static Stelle.@Nullable Komponente komponenteFuerEbene(Ebene ebene, String nummer) {
    return switch (ebene) {
      case PARAGRAPH -> new Stelle.Paragraph(nummer);
      case ABSATZ -> new Stelle.AbsatzNr(nummer);
      case SATZ -> new Stelle.SatzNr(nummer);
      case NUMMER -> new Stelle.NummerNr(nummer);
      case BUCHSTABE -> new Stelle.BuchstabeNr(nummer);
    };
  }

  /** Das Aufzählungslabel der feinsten Nummer/Buchstabe-Komponente („3.“, „a)“). */
  private static @Nullable String erwartetesLabel(Stelle stelle) {
    for (var komponente : stelle.komponenten().reversed()) {
      if (komponente instanceof Stelle.NummerNr n) {
        return n.nummer() + ".";
      }
      if (komponente instanceof Stelle.BuchstabeNr b) {
        return b.kennung() + ")";
      }
    }
    return null;
  }

  /**
   * Zerlegt eine koordinierte Gliederungsphrase („Teil 3 und zu Teil 3 Abschnitt 1“) in Pfade
   * ([Teil 3], [Teil 3, Abschnitt 1]). Liefert die leere Liste, wenn ein Segment nicht
   * ausschließlich aus Gliederungseinheiten besteht.
   */
  private static List<List<Stelle.Gliederungseinheit>> gliederungsPfade(String phrase) {
    var pfade = new ArrayList<List<Stelle.Gliederungseinheit>>();
    for (var segment : phrase.split(" und |, ")) {
      var bereinigt = segment.strip().replaceFirst("^(?:zu|zur|zum) ", "");
      var stelle = StellenParser.parse(bereinigt);
      if (stelle.isEmpty()
          || stelle.get().komponenten().isEmpty()
          || !stelle.get().komponenten().stream()
              .allMatch(Stelle.Gliederungseinheit.class::isInstance)) {
        return List.of();
      }
      pfade.add(stelle.get().gliederungsPfad());
    }
    return pfade;
  }

  /** Markiert die Stelle als Inhaltsübersichts-Ziel (idempotent bei IU-Kontextrahmen). */
  private static Stelle mitInhaltsuebersicht(Stelle kontext) {
    return kontext.betrifftInhaltsuebersicht()
        ? kontext
        : kontext.plus(new Stelle(List.of(new Stelle.Inhaltsuebersicht())));
  }

  /**
   * Normalisiert die Zielphrase einer Angabe für den StellenParser: Artikel entfernen, redundante
   * §-Zeichen in Bereichen glätten („den §§ 34 bis § 45“ → „§§ 34 bis 45“).
   */
  private static String angabenZiel(String phrase) {
    return phrase
        .strip()
        .replaceFirst("^(?:[Dd]en|[Dd]ie|[Dd]er|[Dd]as) ", "")
        .replace(" bis § ", " bis ");
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
