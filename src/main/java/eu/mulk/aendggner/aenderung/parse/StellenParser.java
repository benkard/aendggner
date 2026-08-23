// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.Stelle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

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
          "von",
          "zu",
          "zur",
          "zum",
          "im",
          "neue",
          "neuen",
          "bisherige",
          "bisherigen");

  private static final Pattern PARAGRAPH = Pattern.compile("§");
  // Gestufte Nummern („Nr. 1.29“) kommen in Listen vor, die ihre Glieder dezimal durchzählen —
  // etwa der Artenkatalog des BayJG. Der Punkt muss von einer Ziffer gefolgt sein, damit „Nummer
  // 1.“ mit bloßem Aufzählungspunkt weiterhin nicht als Wert durchgeht.
  private static final Pattern NUMMER_WERT = Pattern.compile("\\d+(?:\\.\\d+)*[a-z]?");
  private static final Pattern BUCHSTABE_WERT = Pattern.compile("[a-z]{1,3}");

  private StellenParser() {}

  // „in dem Satzteil vor Nummer 1“, „in der Angabe vor Nummer 1“, bayerisch auch „Satzteil nach
  // Nr. 3“ — verfeinernde Chapeau-Angaben ohne eigene Stelle-Komponente; die Ersetzung sucht
  // ohnehin im Text der umgebenden Stelle.
  private static final Pattern CHAPEAU_QUALIFIER =
      Pattern.compile(
          "(?i)(?:im |in dem |in der |dem |der )?(?:Satzteil|Angabe) (?:vor|nach) "
              + "(?:Nummer|Nr\\.|Buchstabe|Buchst\\.|Satz|Absatz|Abs\\.) \\S+");

  /**
   * Wahr, wenn die Phrase ausschließlich aus einem Chapeau-Qualifier besteht (z.B. „im Satzteil vor
   * Nummer 1“, „in der Angabe vor Nummer 1“) und daher keine eigene Stelle-Komponente trägt. Die
   * Operation bezieht sich dann auf die Kontextstelle (den umgebenden Änderungsrahmen).
   */
  public static boolean istNurChapeau(String phrase) {
    var rest = CHAPEAU_QUALIFIER.matcher(phrase.strip()).replaceAll(" ").strip();
    return rest.isEmpty() && !phrase.isBlank();
  }

  public static Optional<Stelle> parse(String phrase) {
    phrase = CHAPEAU_QUALIFIER.matcher(phrase).replaceAll(" ").strip();
    var woerter = phrase.strip().split("\\s+");
    var komponenten = new ArrayList<Stelle.Komponente>();

    for (int i = 0; i < woerter.length; i++) {
      var wort = entfernePunktuation(woerter[i]);
      if (wort.isEmpty() || FUELLWOERTER.contains(wort.toLowerCase())) {
        continue;
      }
      switch (wort) {
        case "§", "§§" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.Paragraph(wert));
          i++;
        }
        // Bayerisches Landesrecht: Gesetze gliedern sich in Artikel, zitiert stets als „Art. N“.
        case "Art.", "Artt." -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.Paragraph(wert, "Art."));
          i++;
        }
        case "Absatz", "Absatzes", "Abs.", "Absätze", "Absätzen" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.AbsatzNr(wert));
          i++;
        }
        case "Satz", "Satzes", "Sätze", "Sätzen" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.SatzNr(wert));
          i++;
        }
        case "Halbsatz", "Halbsatzes", "Halbsätze", "Halbs." -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.HalbsatzNr(wert));
          i++;
        }
        case "Nummer", "Nr.", "Nummern", "Nrn." -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.NummerNr(wert));
          i++;
        }
        case "Buchstabe", "Buchstabens", "Buchst.", "Doppelbuchstabe", "Buchstaben" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !BUCHSTABE_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.BuchstabeNr(wert));
          i++;
        }
        case "Teil",
            "Teils",
            "Buch",
            "Buches",
            "Kapitel",
            "Kapitels",
            "Abschnitt",
            "Abschnitts",
            "Unterabschnitt",
            "Unterabschnitts" -> {
          var wert = naechstesWort(woerter, i);
          if (wert == null || !NUMMER_WERT.matcher(wert).matches()) {
            return Optional.empty();
          }
          komponenten.add(new Stelle.Gliederungseinheit(gliederungsArt(wort), wert));
          i++;
        }
        case "Anlage", "Anlagen" -> {
          var wert = naechstesWort(woerter, i);
          if (wert != null && NUMMER_WERT.matcher(wert).matches()) {
            komponenten.add(new Stelle.Gliederungseinheit("Anlage", wert));
            i++;
            // „In Anlage 3b (Muster des Merkblatts zu den Stimmzetteln …) wird …“ — der
            // Klammerzusatz beschreibt die Anlage und ist nicht selbst Änderungsziel; er wird
            // übersprungen. Ohne das bräche die Stellenangabe hier ab und der ganze Befehl bliebe
            // unerkannt.
            var folgt = naechstesWort(woerter, i);
            if (folgt != null && folgt.startsWith("(")) {
              while (i + 1 < woerter.length && !woerter[i + 1].endsWith(")")) {
                i++;
              }
              i++;
            }
            continue;
          }
          // „die Anlage zu § 2 Absatz 4 Satz 1“ — ein Gesetz mit einer einzigen Anlage benennt sie
          // nach der Vorschrift, zu der sie gehört. Wie beim „Anhang“ trägt sie dann die enbez
          // „Anlage“; der Zusatz identifiziert sie nur und ist nicht selbst Änderungsziel. Er
          // reicht bis zum Ende der Stellenangabe und wird deshalb übersprungen — sonst läse der
          // Parser das darin genannte „§ 2“ als Ziel und änderte die falsche Norm.
          komponenten.add(new Stelle.Gliederungseinheit("Anlage", ""));
          if ("zu".equals(wert)) {
            i = woerter.length;
          }
        }
        case "Anhang" -> komponenten.add(new Stelle.Gliederungseinheit("Anhang", ""));
        case "Inhaltsübersicht" -> komponenten.add(new Stelle.Inhaltsuebersicht());
        case "Überschrift" -> komponenten.add(new Stelle.Ueberschrift());
        default -> {
          // Ordinal vor der Gliederungsart: „zum zweiten Abschnitt“, „des 2. Abschnitts“.
          var ordinal = ordinalZahl(wort);
          var art = ordinal != null ? naechstesWort(woerter, i) : null;
          if (art != null && istGliederungsArt(art)) {
            komponenten.add(new Stelle.Gliederungseinheit(gliederungsArt(art), ordinal));
            i++;
            continue;
          }
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
    var ergebnis = new ArrayList<Stelle>();
    Stelle vorige = null;
    for (var segment : segmente) {
      if (segment.isBlank()) {
        return List.of();
      }
      var bereich = entfalteBereich(segment, vorige);
      if (bereich != null) {
        if (bereich.isEmpty()) {
          return List.of();
        }
        ergebnis.addAll(bereich);
        vorige = bereich.get(bereich.size() - 1);
        continue;
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
      } else if (vorige != null) {
        // Bloßes Label mit nachgestellten Komponenten („Art. 18 Satz 2, Art. 19 und 20 Satz 1“ →
        // das „20 Satz 1“): Das Label erbt die gröbste passende Komponentenart der vorigen Stelle.
        var kombiniert = mitGeerbtemLabelUndRest(vorige, segment);
        if (kombiniert.isEmpty()) {
          return List.of();
        }
        voll = kombiniert.get();
      } else {
        return List.of();
      }
      ergebnis.add(voll);
      vorige = voll;
    }
    return ergebnis;
  }

  private static final Pattern BLOSSES_LABEL = Pattern.compile("\\d+[a-z]?|[a-z]{1,3}");

  /**
   * Ersetzt die letzte Komponente von {@code vorige} durch dieselbe Komponentenart mit neuem Label.
   */
  private static Optional<Stelle> mitGeerbtemLabel(Stelle vorige, String label) {
    var komponenten = new ArrayList<>(vorige.komponenten());
    var neu = mitLabel(komponenten.get(komponenten.size() - 1), label);
    if (neu == null) {
      return Optional.empty();
    }
    komponenten.set(komponenten.size() - 1, neu);
    return Optional.of(new Stelle(komponenten));
  }

  private static final Pattern LABEL_MIT_REST =
      Pattern.compile("(\\d+[a-z]?|[a-z]{1,3})\\s+(\\S.*)");

  /**
   * „Art. 19 und 20 Satz 1“: Das führende bloße Label des Segments erbt aus {@code vorige} die
   * letzte Komponentenart, die gröber ist als die erste Komponente des Rests; die feineren
   * Komponenten der vorigen Stelle entfallen, der geparste Rest wird angehängt.
   */
  private static Optional<Stelle> mitGeerbtemLabelUndRest(Stelle vorige, String segment) {
    var m = LABEL_MIT_REST.matcher(segment.strip());
    if (!m.matches()) {
      return Optional.empty();
    }
    var rest = parse(m.group(2));
    if (rest.isEmpty()) {
      return Optional.empty();
    }
    int restRang = rang(rest.get().komponenten().get(0));
    var vorKomp = vorige.komponenten();
    for (int i = vorKomp.size() - 1; i >= 0; i--) {
      if (rang(vorKomp.get(i)) < restRang) {
        var neu = mitLabel(vorKomp.get(i), m.group(1));
        if (neu == null) {
          return Optional.empty();
        }
        var komponenten = new ArrayList<Stelle.Komponente>(vorKomp.subList(0, i));
        komponenten.add(neu);
        komponenten.addAll(rest.get().komponenten());
        return Optional.of(new Stelle(komponenten));
      }
    }
    return Optional.empty();
  }

  /** Hierarchie-Rang einer Komponente (kleiner = gröber). */
  private static int rang(Stelle.Komponente komponente) {
    return switch (komponente) {
      case Stelle.Gliederungseinheit g -> 0;
      case Stelle.Paragraph p -> 1;
      case Stelle.AbsatzNr a -> 2;
      case Stelle.SatzNr s -> 3;
      case Stelle.HalbsatzNr h -> 4;
      case Stelle.NummerNr n -> 5;
      case Stelle.BuchstabeNr b -> 6;
      default -> 9;
    };
  }

  private static final Pattern BEREICH =
      Pattern.compile(
          "(?:(§§?|Artt?\\.|Absätze|Absatz|Abs\\.|Sätze|Satz|Nummern|Nummer|Nrn?\\.|Buchstaben"
              + "|Buchstabe|Buchst\\.)\\s+)?"
              + "(\\d+|[a-z])\\s+bis\\s+(\\d+|[a-z])");

  /**
   * Entfaltet einen {@code X bis Y}-Bereich (ganzzahlig oder Einzelbuchstabe) in Einzelstellen. Die
   * Komponentenart stammt aus der Bereichsangabe („Nummern 1 bis 3“) oder — bei bloßem „1 bis 3“ —
   * aus der letzten Komponente der vorigen Stelle. Liefert {@code null}, wenn {@code segment} kein
   * Bereich ist, und eine leere Liste, wenn er unauflösbar ist (z.B. absteigend, gemischt).
   */
  private static @Nullable List<Stelle> entfalteBereich(String segment, @Nullable Stelle vorige) {
    var ohneArtikel = segment.strip().replaceFirst("^(?:[Dd]ie|[Dd]er|[Dd]as|[Dd]en) ", "");
    var m = BEREICH.matcher(ohneArtikel);
    var praefixKomponenten = new ArrayList<Stelle.Komponente>();
    if (!m.matches()) {
      // Bereich mit vorangestellter Stelle („Satz 1 Nummer 3 bis 6“): Präfix separat parsen.
      var amEnde = BEREICH.matcher(ohneArtikel);
      if (!amEnde.find() || amEnde.end() != ohneArtikel.length() || amEnde.start() == 0) {
        return null;
      }
      var praefix = parse(ohneArtikel.substring(0, amEnde.start()).strip());
      if (praefix.isEmpty()) {
        return List.of();
      }
      praefixKomponenten.addAll(praefix.get().komponenten());
      m = amEnde;
    }
    var art = m.group(1);
    var von = m.group(2);
    var bis = m.group(3);
    boolean numerisch = von.matches("\\d+") && bis.matches("\\d+");
    boolean alpha = von.matches("[a-z]") && bis.matches("[a-z]");
    if (!numerisch && !alpha) {
      return List.of();
    }
    var praefix = new ArrayList<Stelle.Komponente>(praefixKomponenten);
    Stelle.Komponente muster;
    if (art != null) {
      muster = komponenteFuerArt(art, von);
      if (muster == null) {
        return List.of();
      }
    } else {
      if (!praefixKomponenten.isEmpty()) {
        // Präfix ohne Bereichsart („Satz 1 3 bis 6“) ist nicht deutbar.
        return List.of();
      }
      // Bloßer Bereich „1 bis 3“: Art und Präfix von der vorigen Stelle erben.
      if (vorige == null || vorige.komponenten().isEmpty()) {
        return List.of();
      }
      praefix.addAll(vorige.komponenten().subList(0, vorige.komponenten().size() - 1));
      muster = vorige.komponenten().get(vorige.komponenten().size() - 1);
    }
    var labels = numerisch ? zahlenBereich(von, bis) : buchstabenBereich(von, bis);
    if (labels.isEmpty()) {
      return List.of();
    }
    var stellen = new ArrayList<Stelle>();
    for (var label : labels) {
      var komponenten = new ArrayList<>(praefix);
      var neu = mitLabel(muster, label);
      if (neu == null) {
        return List.of();
      }
      komponenten.add(neu);
      stellen.add(new Stelle(komponenten));
    }
    return stellen;
  }

  private static Stelle.@Nullable Komponente komponenteFuerArt(String art, String label) {
    return switch (art) {
      case "§", "§§" -> new Stelle.Paragraph(label);
      case "Art.", "Artt." -> new Stelle.Paragraph(label, "Art.");
      case "Absatz", "Absätze", "Abs." -> new Stelle.AbsatzNr(label);
      case "Satz", "Sätze" -> new Stelle.SatzNr(label);
      case "Nummer", "Nummern", "Nr.", "Nrn." -> new Stelle.NummerNr(label);
      case "Buchstabe", "Buchstaben", "Buchst." -> new Stelle.BuchstabeNr(label);
      default -> null;
    };
  }

  private static Stelle.@Nullable Komponente mitLabel(Stelle.Komponente muster, String label) {
    return switch (muster) {
      case Stelle.Paragraph p -> new Stelle.Paragraph(label, p.sigel());
      case Stelle.AbsatzNr a -> new Stelle.AbsatzNr(label);
      case Stelle.SatzNr s -> new Stelle.SatzNr(label);
      case Stelle.HalbsatzNr h -> new Stelle.HalbsatzNr(label);
      case Stelle.NummerNr n -> new Stelle.NummerNr(label);
      case Stelle.BuchstabeNr b -> new Stelle.BuchstabeNr(label);
      case Stelle.Gliederungseinheit g -> new Stelle.Gliederungseinheit(g.art(), label);
      case Stelle.Ueberschrift u -> null;
      case Stelle.Inhaltsuebersicht i -> null;
      case Stelle.Absatzbezeichnung a -> null;
    };
  }

  private static List<String> zahlenBereich(String von, String bis) {
    int a = Integer.parseInt(von);
    int b = Integer.parseInt(bis);
    if (b < a) {
      return List.of();
    }
    var labels = new ArrayList<String>();
    for (int k = a; k <= b; k++) {
      labels.add(String.valueOf(k));
    }
    return labels;
  }

  private static List<String> buchstabenBereich(String von, String bis) {
    char a = von.charAt(0);
    char b = bis.charAt(0);
    if (b < a) {
      return List.of();
    }
    var labels = new ArrayList<String>();
    for (char c = a; c <= b; c++) {
      labels.add(String.valueOf(c));
    }
    return labels;
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

  private static final java.util.Map<String, String> ORDINALE =
      java.util.Map.ofEntries(
          java.util.Map.entry("erste", "1"),
          java.util.Map.entry("zweite", "2"),
          java.util.Map.entry("dritte", "3"),
          java.util.Map.entry("vierte", "4"),
          java.util.Map.entry("fünfte", "5"),
          java.util.Map.entry("sechste", "6"),
          java.util.Map.entry("siebte", "7"),
          java.util.Map.entry("siebente", "7"),
          java.util.Map.entry("achte", "8"),
          java.util.Map.entry("neunte", "9"),
          java.util.Map.entry("zehnte", "10"),
          java.util.Map.entry("elfte", "11"),
          java.util.Map.entry("zwölfte", "12"));

  /**
   * Liest ein Ordinal („zweiten“, „2.“) als Nummer („2“); {@code null}, wenn das Wort keines ist.
   */
  private static @Nullable String ordinalZahl(String wort) {
    if (wort.matches("\\d+\\.")) {
      return wort.substring(0, wort.length() - 1);
    }
    var klein = wort.toLowerCase().replaceFirst("[nm]$", "");
    return ORDINALE.get(klein);
  }

  private static boolean istGliederungsArt(String wort) {
    return switch (wort) {
      case "Teil",
          "Teils",
          "Buch",
          "Buches",
          "Kapitel",
          "Kapitels",
          "Abschnitt",
          "Abschnitts",
          "Unterabschnitt",
          "Unterabschnitts",
          "Anlage",
          "Anlagen" ->
          true;
      default -> false;
    };
  }

  /** Normalisiert Genitiv-/Pluralformen der Gliederungsart auf den Nominativ Singular. */
  private static String gliederungsArt(String wort) {
    return switch (wort) {
      case "Teils" -> "Teil";
      case "Buches" -> "Buch";
      case "Kapitels" -> "Kapitel";
      case "Abschnitts" -> "Abschnitt";
      case "Unterabschnitts" -> "Unterabschnitt";
      case "Anlagen" -> "Anlage";
      default -> wort;
    };
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
