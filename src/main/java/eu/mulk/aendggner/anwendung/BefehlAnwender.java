package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Sammelbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturErsetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuAbsatz;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Wendet Änderungsbefehle nacheinander auf ein Stammgesetz an (die Reihenfolge im Änderungsgesetz
 * ist maßgeblich). Jeder Befehl erhält einen Protokolleintrag; was nicht sicher angewandt werden
 * kann, wird mit Begründung als „manuell prüfen“ markiert — niemals stillschweigend verworfen.
 */
public final class BefehlAnwender {

  public enum Status {
    ANGEWANDT,
    MANUELL_PRUEFEN
  }

  public record AngewandteAenderung(
      Aenderungsbefehl befehl, Status status, String begruendung, Set<String> betroffeneEnbez) {}

  public record AnwendungsErgebnis(Gesetz neu, List<AngewandteAenderung> protokoll) {

    public long anzahlAngewandt() {
      return protokoll.stream().filter(a -> a.status() == Status.ANGEWANDT).count();
    }

    public long anzahlManuell() {
      return protokoll.stream().filter(a -> a.status() == Status.MANUELL_PRUEFEN).count();
    }
  }

  // Absatzmarker in Zitaten: „(1) “ am Zeilenanfang oder nach Leerraum (PDF-Umbrüche verwischen
  // die Zeilenstruktur).
  private static final Pattern ABSATZ_MARKER =
      Pattern.compile("(?m)(?:^|(?<=\\s))\\((\\d+[a-z]?)\\)\\s+");

  private BefehlAnwender() {}

  public static AnwendungsErgebnis anwenden(Gesetz alt, List<Aenderungsbefehl> befehle) {
    var normen = new ArrayList<>(alt.normen());
    var protokoll = new ArrayList<AngewandteAenderung>();

    for (var befehl : befehle) {
      protokoll.add(wendeAn(normen, befehl));
    }

    return new AnwendungsErgebnis(alt.mitNormen(normen), protokoll);
  }

  private static AngewandteAenderung wendeAn(List<Norm> normen, Aenderungsbefehl befehl) {
    if (befehl instanceof UnbekannterBefehl) {
      return manuell(befehl, "Befehl nicht erkannt.");
    }
    if (befehl.stelle().betrifftInhaltsuebersicht()) {
      return manuell(
          befehl, "Änderungen an der Inhaltsübersicht werden nicht automatisch angewandt.");
    }
    try {
      return switch (befehl) {
        case Ersetzung e -> wendeErsetzungAn(normen, e);
        case Streichung s -> wendeStreichungAn(normen, s);
        case WoerterEinfuegung w -> wendeWoerterEinfuegungAn(normen, w);
        case Neufassung n -> wendeNeufassungAn(normen, n);
        case StrukturErsetzung s -> wendeStrukturErsetzungAn(normen, s);
        case StrukturEinfuegung s -> wendeStrukturEinfuegungAn(normen, s);
        case Anfuegung a -> wendeAnfuegungAn(normen, a);
        case Aufhebung a -> wendeAufhebungAn(normen, a);
        case Umnummerierung u -> wendeUmnummerierungAn(normen, u);
        case WortlautZuAbsatz w -> wendeWortlautZuAbsatzAn(normen, w);
        case Sammelbefehl s -> wendeSammelAn(normen, s);
        case UnbekannterBefehl u -> manuell(befehl, "Befehl nicht erkannt.");
      };
    } catch (RuntimeException e) {
      return manuell(befehl, "Anwendung fehlgeschlagen: " + e);
    }
  }

  // --- Wortweise Textoperationen -------------------------------------------------------------

  private static AngewandteAenderung wendeErsetzungAn(List<Norm> normen, Ersetzung befehl) {
    return bearbeiteText(
        normen,
        befehl,
        text -> {
          if (befehl.amEnde()) {
            var gestutzt = text.stripTrailing();
            if (!gestutzt.endsWith(befehl.alt())) {
              return TextErgebnis.fehler("Der Text endet nicht mit „" + befehl.alt() + "“.");
            }
            return TextErgebnis.ok(
                gestutzt.substring(0, gestutzt.length() - befehl.alt().length()) + befehl.neu());
          }
          int anzahl = zaehleVorkommen(text, befehl.alt());
          if (anzahl == 0) {
            return TextErgebnis.fehler("„" + befehl.alt() + "“ kommt im Zieltext nicht vor.");
          }
          if (anzahl > 1 && !befehl.jeweils()) {
            return TextErgebnis.fehler(
                "„" + befehl.alt() + "“ kommt " + anzahl + "-mal vor (ohne „jeweils“ mehrdeutig).");
          }
          return TextErgebnis.ok(text.replace(befehl.alt(), befehl.neu()));
        });
  }

  private static AngewandteAenderung wendeStreichungAn(List<Norm> normen, Streichung befehl) {
    return bearbeiteText(
        normen,
        befehl,
        text -> {
          int anzahl = zaehleVorkommen(text, befehl.woerter());
          if (anzahl == 0) {
            return TextErgebnis.fehler("„" + befehl.woerter() + "“ kommt im Zieltext nicht vor.");
          }
          if (anzahl > 1) {
            return TextErgebnis.fehler("„" + befehl.woerter() + "“ kommt " + anzahl + "-mal vor.");
          }
          return TextErgebnis.ok(
              text.replace(befehl.woerter(), "")
                  .replaceAll("  +", " ")
                  .replaceAll(" ([,;.])", "$1"));
        });
  }

  private static AngewandteAenderung wendeWoerterEinfuegungAn(
      List<Norm> normen, WoerterEinfuegung befehl) {
    return bearbeiteText(
        normen,
        befehl,
        text ->
            switch (befehl.anker()) {
              case WortAnker.NachWoertern nach -> {
                var pruefung = eindeutigeFundstelle(text, nach.woerter());
                if (pruefung.fehler() != null) {
                  yield TextErgebnis.fehler(pruefung.fehler());
                }
                int ende = pruefung.index() + nach.woerter().length();
                yield TextErgebnis.ok(
                    text.substring(0, ende) + " " + befehl.woerter() + text.substring(ende));
              }
              case WortAnker.VorWoertern vor -> {
                var pruefung = eindeutigeFundstelle(text, vor.woerter());
                if (pruefung.fehler() != null) {
                  yield TextErgebnis.fehler(pruefung.fehler());
                }
                yield TextErgebnis.ok(
                    text.substring(0, pruefung.index())
                        + befehl.woerter()
                        + " "
                        + text.substring(pruefung.index()));
              }
              case WortAnker.VorKommaAmEnde ignoriert -> {
                var gestutzt = text.stripTrailing();
                if (!gestutzt.endsWith(",")) {
                  yield TextErgebnis.fehler("Der Zieltext endet nicht mit einem Komma.");
                }
                yield TextErgebnis.ok(
                    gestutzt.substring(0, gestutzt.length() - 1) + " " + befehl.woerter() + ",");
              }
              case WortAnker.AmEnde ignoriert -> {
                var gestutzt = text.stripTrailing();
                if (gestutzt.endsWith(".") || gestutzt.endsWith(",") || gestutzt.endsWith(";")) {
                  var satzzeichen = gestutzt.charAt(gestutzt.length() - 1);
                  yield TextErgebnis.ok(
                      gestutzt.substring(0, gestutzt.length() - 1)
                          + " "
                          + befehl.woerter()
                          + satzzeichen);
                }
                yield TextErgebnis.ok(gestutzt + " " + befehl.woerter());
              }
            });
  }

  // --- Strukturoperationen -------------------------------------------------------------------

  private static AngewandteAenderung wendeNeufassungAn(List<Norm> normen, Neufassung befehl) {
    var stelle = befehl.stelle();

    if (stelle.betrifftUeberschrift()) {
      var aufloesung = loeseNormAuf(normen, stelle);
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.fehler());
      }
      var norm = normen.get(aufloesung.normIndex());
      var titel = befehl.neuerText().replaceFirst("^§\\s*\\S+\\s+", "").strip();
      normen.set(aufloesung.normIndex(), norm.mitTitel(titel));
      return angewandt(befehl, norm.enbez());
    }

    if (nurParagraph(stelle)) {
      var aufloesung = loeseNormAuf(normen, stelle);
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.fehler());
      }
      var alteNorm = normen.get(aufloesung.normIndex());
      var neueNorm = parseNorm(befehl.neuerText(), alteNorm.enbez(), alteNorm);
      normen.set(aufloesung.normIndex(), neueNorm);
      return angewandt(befehl, alteNorm.enbez());
    }

    if (stelle.absatz().isPresent() && feinsteIstAbsatz(stelle)) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.begruendung());
      }
      var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
      var norm = normen.get(fundstelle.normIndex());
      var absaetze = new ArrayList<>(norm.absaetze());
      var alterAbsatz = absaetze.get(fundstelle.absatzIndex());
      var neueAbsaetze = parseAbsaetze(befehl.neuerText());
      if (neueAbsaetze.size() == 1) {
        var neuer = neueAbsaetze.get(0);
        absaetze.set(
            fundstelle.absatzIndex(),
            new Absatz(
                neuer.nummer() != null ? neuer.nummer() : alterAbsatz.nummer(), neuer.text()));
      } else {
        absaetze.remove((int) fundstelle.absatzIndex());
        absaetze.addAll(fundstelle.absatzIndex(), neueAbsaetze);
      }
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }

    // Neufassung eines Satzes / einer Nummer / eines Buchstabens: Bereich ersetzen.
    return bearbeiteText(normen, befehl, text -> TextErgebnis.ok(befehl.neuerText().strip()));
  }

  /** Ein Ziel (Absatz, Satz, Nummer, Buchstabe) wird durch einen Block ersetzt (ggf. 1 → N). */
  private static AngewandteAenderung wendeStrukturErsetzungAn(
      List<Norm> normen, StrukturErsetzung befehl) {
    return switch (befehl.ebene()) {
      case ABSATZ -> {
        var stelle = befehl.stelle();
        if (stelle.absatz().isEmpty()) {
          yield manuell(befehl, "Ersetzungsziel nennt keinen Absatz: " + stelle.anzeigeText());
        }
        var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
        if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
          yield manuell(befehl, nicht.begruendung());
        }
        var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
        var norm = normen.get(fundstelle.normIndex());
        var absaetze = new ArrayList<>(norm.absaetze());
        absaetze.remove((int) fundstelle.absatzIndex());
        absaetze.addAll(fundstelle.absatzIndex(), parseAbsaetze(befehl.text()));
        normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
        yield angewandt(befehl, norm.enbez());
      }
      case SATZ ->
          bearbeiteBereich(
              normen,
              befehl,
              (text, bereich) ->
                  TextErgebnis.ok(
                      text.substring(0, bereich.von())
                          + befehl.text().strip().replaceAll("\\s+", " ")
                          + text.substring(bereich.bis())));
      case NUMMER, BUCHSTABE ->
          bearbeiteBereich(
              normen,
              befehl,
              (text, bereich) -> {
                var einrueckung = einrueckungVon(text, bereich.von());
                var ersatz =
                    normalisiereZitatText(befehl.text())
                        .lines()
                        .map(zeile -> einrueckung + zeile.strip())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                return TextErgebnis.ok(
                    text.substring(0, bereich.von()) + ersatz + text.substring(bereich.bis()));
              });
      case PARAGRAPH ->
          manuell(befehl, "Struktur-Ersetzung ganzer Paragraphen wird nicht unterstützt.");
    };
  }

  private static AngewandteAenderung wendeStrukturEinfuegungAn(
      List<Norm> normen, StrukturEinfuegung befehl) {
    return switch (befehl.ebene()) {
      case PARAGRAPH -> {
        var enbezNeu = "§ " + befehl.bezeichnung();
        if (StellenAufloeser.normIndex(gesetzAus(normen), enbezNeu) >= 0) {
          yield manuell(befehl, enbezNeu + " existiert bereits im Stammgesetz.");
        }
        var aufloesung = loeseNormAuf(normen, befehl.stelle());
        if (aufloesung.fehler() != null) {
          yield manuell(befehl, aufloesung.fehler());
        }
        var anker = normen.get(aufloesung.normIndex());
        var neueNorm =
            parseNorm(
                befehl.text(),
                enbezNeu,
                new Norm(enbezNeu, null, anker.gliederung(), List.of(), false));
        normen.add(aufloesung.normIndex() + (befehl.vorher() ? 0 : 1), neueNorm);
        yield angewandt(befehl, enbezNeu);
      }
      case ABSATZ -> {
        var stelle = befehl.stelle();
        if (stelle.absatz().isEmpty()) {
          yield manuell(befehl, "Einfügeanker nennt keinen Absatz: " + stelle.anzeigeText());
        }
        var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
        if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
          yield manuell(befehl, nicht.begruendung());
        }
        var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
        var norm = normen.get(fundstelle.normIndex());
        var absaetze = new ArrayList<>(norm.absaetze());
        int position = fundstelle.absatzIndex() + (befehl.vorher() ? 0 : 1);
        absaetze.addAll(position, parseAbsaetze(befehl.text()));
        normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
        yield angewandt(befehl, norm.enbez());
      }
      case SATZ ->
          bearbeiteBereich(
              normen,
              befehl,
              (text, bereich) -> {
                var einschub = " " + befehl.text().strip();
                int position = befehl.vorher() ? bereich.von() : bereich.bis();
                return TextErgebnis.ok(
                    befehl.vorher()
                        ? text.substring(0, position)
                            + befehl.text().strip()
                            + " "
                            + text.substring(position)
                        : text.substring(0, position) + einschub + text.substring(position));
              });
      case NUMMER, BUCHSTABE ->
          bearbeiteBereich(
              normen,
              befehl,
              (text, bereich) -> {
                var einrueckung = einrueckungVon(text, bereich.von());
                int position = befehl.vorher() ? bereich.von() : bereich.bis();
                var zeile = einrueckung + befehl.text().strip().replaceAll("\\s+", " ");
                return TextErgebnis.ok(
                    befehl.vorher()
                        ? text.substring(0, position) + zeile + "\n" + text.substring(position)
                        : text.substring(0, position) + "\n" + zeile + text.substring(position));
              });
    };
  }

  private static AngewandteAenderung wendeAnfuegungAn(List<Norm> normen, Anfuegung befehl) {
    return switch (befehl.ebene()) {
      case ABSATZ -> {
        var aufloesung = loeseNormAuf(normen, befehl.stelle());
        if (aufloesung.fehler() != null) {
          yield manuell(befehl, aufloesung.fehler());
        }
        var norm = normen.get(aufloesung.normIndex());
        var absaetze = new ArrayList<>(norm.absaetze());
        absaetze.addAll(parseAbsaetze(befehl.text()));
        normen.set(aufloesung.normIndex(), norm.mitAbsaetzen(absaetze));
        yield angewandt(befehl, norm.enbez());
      }
      case SATZ ->
          bearbeiteText(
              normen,
              befehl,
              text -> TextErgebnis.ok(text.stripTrailing() + " " + befehl.text().strip()));
      case NUMMER, BUCHSTABE ->
          bearbeiteText(
              normen,
              befehl,
              text ->
                  TextErgebnis.ok(
                      text.stripTrailing()
                          + "\n  "
                          + befehl.text().strip().replaceAll("\\s+", " ")));
      case PARAGRAPH -> manuell(befehl, "Anfügen ganzer Paragraphen wird nicht unterstützt.");
    };
  }

  private static AngewandteAenderung wendeAufhebungAn(List<Norm> normen, Aufhebung befehl) {
    var stelle = befehl.stelle();

    if (nurParagraph(stelle)) {
      var aufloesung = loeseNormAuf(normen, stelle);
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.fehler());
      }
      var norm = normen.get(aufloesung.normIndex());
      if (norm.weggefallen()) {
        return manuell(befehl, norm.enbez() + " ist bereits weggefallen.");
      }
      normen.set(aufloesung.normIndex(), norm.alsWeggefallen());
      return angewandt(befehl, norm.enbez());
    }

    if (stelle.absatz().isPresent() && feinsteIstAbsatz(stelle)) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.begruendung());
      }
      var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
      var norm = normen.get(fundstelle.normIndex());
      var absaetze = new ArrayList<>(norm.absaetze());
      var alter = absaetze.get(fundstelle.absatzIndex());
      absaetze.set(fundstelle.absatzIndex(), new Absatz(alter.nummer(), "(weggefallen)"));
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }

    // Satz / Nummer / Buchstabe aufheben: Bereich entfernen bzw. als weggefallen markieren.
    return bearbeiteBereich(
        normen,
        befehl,
        (text, bereich) -> {
          var label = labelVon(stelle);
          if (label != null) {
            var einrueckung = einrueckungVon(text, bereich.von());
            return TextErgebnis.ok(
                text.substring(0, bereich.von())
                    + einrueckung
                    + label
                    + " (weggefallen)"
                    + text.substring(bereich.bis()));
          }
          return TextErgebnis.ok(
              (text.substring(0, bereich.von()) + text.substring(bereich.bis()))
                  .replaceAll("  +", " ")
                  .strip());
        });
  }

  private static AngewandteAenderung wendeUmnummerierungAn(
      List<Norm> normen, Umnummerierung befehl) {
    var altAbsatz = befehl.stelle().absatz();
    var neuAbsatz = befehl.neu().absatz();
    if (altAbsatz.isPresent() && neuAbsatz.isPresent()) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.begruendung());
      }
      var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
      var norm = normen.get(fundstelle.normIndex());
      var absaetze = new ArrayList<>(norm.absaetze());
      var absatz = absaetze.get(fundstelle.absatzIndex());
      absaetze.set(fundstelle.absatzIndex(), new Absatz(neuAbsatz.get().nummer(), absatz.text()));
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }
    // Satz-Umnummerierungen ändern den Text nicht (Sätze sind unnummeriert).
    return angewandt(befehl, "(keine Textänderung nötig)");
  }

  private static AngewandteAenderung wendeWortlautZuAbsatzAn(
      List<Norm> normen, WortlautZuAbsatz befehl) {
    var aufloesung = loeseNormAuf(normen, befehl.stelle());
    if (aufloesung.fehler() != null) {
      return manuell(befehl, aufloesung.fehler());
    }
    var norm = normen.get(aufloesung.normIndex());
    var sb = new StringBuilder();
    for (var absatz : norm.absaetze()) {
      if (sb.length() > 0) {
        sb.append("\n\n");
      }
      sb.append(absatz.text());
    }
    normen.set(
        aufloesung.normIndex(),
        norm.mitAbsaetzen(List.of(new Absatz(befehl.nummer(), sb.toString()))));
    return angewandt(befehl, norm.enbez());
  }

  /**
   * Ein Mehrfachziel-Befehl („In A und B wird jeweils …“): wendet jeden Teilbefehl nacheinander an
   * (jeder mutiert den fortlaufenden Zwischenstand) und fasst sie zu einem Protokolleintrag
   * zusammen. Nur wenn alle Teile gelingen, gilt der Befehl als angewandt; sonst wird er zur
   * manuellen Prüfung markiert (bereits angewandte Teile bleiben wirksam).
   */
  private static AngewandteAenderung wendeSammelAn(List<Norm> normen, Sammelbefehl befehl) {
    var betroffene = new LinkedHashSet<String>();
    var fehler = new ArrayList<String>();
    int i = 1;
    for (var teil : befehl.teilbefehle()) {
      var ergebnis = wendeAn(normen, teil);
      betroffene.addAll(ergebnis.betroffeneEnbez());
      if (ergebnis.status() != Status.ANGEWANDT) {
        fehler.add("Teil " + i + " (" + teil.stelle().anzeigeText() + "): " + ergebnis.begruendung());
      }
      i++;
    }
    if (fehler.isEmpty()) {
      return new AngewandteAenderung(befehl, Status.ANGEWANDT, "", betroffene);
    }
    return new AngewandteAenderung(
        befehl, Status.MANUELL_PRUEFEN, String.join(" ", fehler), betroffene);
  }

  // --- Gemeinsame Helfer ---------------------------------------------------------------------

  private record TextErgebnis(@Nullable String text, @Nullable String fehler) {
    static TextErgebnis ok(String text) {
      return new TextErgebnis(text, null);
    }

    static TextErgebnis fehler(String begruendung) {
      return new TextErgebnis(null, begruendung);
    }
  }

  private interface TextOperation {
    TextErgebnis wende(String text);
  }

  private interface BereichsOperation {
    TextErgebnis wende(String text, SatzTeiler.SatzBereich bereich);
  }

  /**
   * Wendet eine Textoperation auf den durch die Stelle bestimmten Bereich an. Ohne
   * Bereichs-/Absatzangabe wird die Operation auf jeden Absatz der Norm versucht; sie muss dann in
   * genau einem Absatz anwendbar sein.
   */
  private static AngewandteAenderung bearbeiteText(
      List<Norm> normen, Aenderungsbefehl befehl, TextOperation operation) {
    var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
    if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.begruendung());
    }
    var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
    var norm = normen.get(fundstelle.normIndex());

    if (fundstelle.absatzIndex() != null) {
      var absaetze = new ArrayList<>(norm.absaetze());
      var absatz = absaetze.get(fundstelle.absatzIndex());
      var neuerText = wendeAufBereichAn(absatz.text(), fundstelle.bereich(), operation);
      if (neuerText.fehler() != null) {
        return manuell(befehl, neuerText.fehler());
      }
      absaetze.set(fundstelle.absatzIndex(), absatz.mitText(neuerText.text()));
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }

    // Ganze Norm als Wirkungsbereich: Operation muss in genau einem Absatz gelingen.
    var absaetze = new ArrayList<>(norm.absaetze());
    Integer trefferIndex = null;
    TextErgebnis treffer = null;
    String letzterFehler = "Norm hat keine Absätze.";
    for (int i = 0; i < absaetze.size(); i++) {
      var versuch = operation.wende(absaetze.get(i).text());
      if (versuch.fehler() == null) {
        if (trefferIndex != null) {
          return manuell(
              befehl, "Mehrere Absätze von " + norm.enbez() + " kommen infrage; mehrdeutig.");
        }
        trefferIndex = i;
        treffer = versuch;
      } else {
        letzterFehler = versuch.fehler();
      }
    }
    if (trefferIndex == null) {
      return manuell(befehl, letzterFehler);
    }
    absaetze.set(trefferIndex, absaetze.get(trefferIndex).mitText(treffer.text()));
    normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
    return angewandt(befehl, norm.enbez());
  }

  /** Wie {@link #bearbeiteText}, aber die Operation braucht den konkreten Zeichenbereich. */
  private static AngewandteAenderung bearbeiteBereich(
      List<Norm> normen, Aenderungsbefehl befehl, BereichsOperation operation) {
    var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
    if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.begruendung());
    }
    var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
    if (fundstelle.absatzIndex() == null || fundstelle.bereich() == null) {
      return manuell(
          befehl,
          "„" + befehl.stelle().anzeigeText() + "“ bezeichnet keinen konkreten Textbereich.");
    }
    var norm = normen.get(fundstelle.normIndex());
    var absaetze = new ArrayList<>(norm.absaetze());
    var absatz = absaetze.get(fundstelle.absatzIndex());
    var neuerText = operation.wende(absatz.text(), fundstelle.bereich());
    if (neuerText.fehler() != null) {
      return manuell(befehl, neuerText.fehler());
    }
    absaetze.set(fundstelle.absatzIndex(), absatz.mitText(neuerText.text()));
    normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
    return angewandt(befehl, norm.enbez());
  }

  private static TextErgebnis wendeAufBereichAn(
      String text, SatzTeiler.@Nullable SatzBereich bereich, TextOperation operation) {
    if (bereich == null) {
      return operation.wende(text);
    }
    var ausschnitt = text.substring(bereich.von(), bereich.bis());
    var ergebnis = operation.wende(ausschnitt);
    if (ergebnis.fehler() != null) {
      return ergebnis;
    }
    return TextErgebnis.ok(
        text.substring(0, bereich.von()) + ergebnis.text() + text.substring(bereich.bis()));
  }

  private record NormAufloesung(int normIndex, @Nullable String fehler) {}

  private static NormAufloesung loeseNormAuf(List<Norm> normen, Stelle stelle) {
    if (stelle.paragraph().isEmpty()) {
      return new NormAufloesung(-1, "Stelle nennt keinen Paragraphen: " + stelle.anzeigeText());
    }
    var enbez = "§ " + stelle.paragraph().get().nummer();
    int index = StellenAufloeser.normIndex(gesetzAus(normen), enbez);
    if (index < 0) {
      return new NormAufloesung(-1, enbez + " existiert nicht im Gesetz.");
    }
    return new NormAufloesung(index, null);
  }

  private static boolean nurParagraph(Stelle stelle) {
    return stelle.komponenten().size() == 1
        && stelle.komponenten().get(0) instanceof Stelle.Paragraph;
  }

  private static boolean feinsteIstAbsatz(Stelle stelle) {
    return stelle.komponenten().stream()
        .noneMatch(
            k ->
                k instanceof Stelle.SatzNr
                    || k instanceof Stelle.NummerNr
                    || k instanceof Stelle.BuchstabeNr);
  }

  private static @Nullable String labelVon(Stelle stelle) {
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

  private static String einrueckungVon(String text, int position) {
    int i = position;
    var sb = new StringBuilder();
    while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
      sb.append(text.charAt(i));
      i++;
    }
    return sb.toString();
  }

  private record Fundpruefung(int index, @Nullable String fehler) {}

  private static Fundpruefung eindeutigeFundstelle(String text, String woerter) {
    int anzahl = zaehleVorkommen(text, woerter);
    if (anzahl == 0) {
      return new Fundpruefung(-1, "„" + woerter + "“ kommt im Zieltext nicht vor.");
    }
    if (anzahl > 1) {
      return new Fundpruefung(-1, "„" + woerter + "“ kommt " + anzahl + "-mal vor; mehrdeutig.");
    }
    return new Fundpruefung(text.indexOf(woerter), null);
  }

  private static int zaehleVorkommen(String text, String suchtext) {
    if (suchtext.isEmpty()) {
      return 0;
    }
    int anzahl = 0;
    int index = 0;
    while ((index = text.indexOf(suchtext, index)) >= 0) {
      anzahl++;
      index += suchtext.length();
    }
    return anzahl;
  }

  /** Zerlegt einen zitierten Normtext („§ 28a Titel (1) … (2) …“) in Titel und Absätze. */
  private static Norm parseNorm(String zitat, String enbez, Norm vorlage) {
    var text = zitat.strip();
    String titel = vorlage.titel();

    var absatzStart = ABSATZ_MARKER.matcher(text);
    int erster = absatzStart.find() ? absatzStart.start() : -1;

    if (erster >= 0) {
      var kopf = text.substring(0, erster).strip();
      if (!kopf.isEmpty()) {
        titel = kopf.replaceFirst("^§\\s*\\S+\\s*", "").replaceAll("\\s+", " ").strip();
        if (titel.isEmpty()) {
          titel = vorlage.titel();
        }
      }
      return new Norm(
          enbez, titel, vorlage.gliederung(), parseAbsaetze(text.substring(erster)), false);
    }

    // Ohne Absatzmarker (Einzelabsatz-Normen wie „§ 19 Außerkrafttreten Dieses Gesetz tritt …“):
    // Steht „§ N“ allein auf der ersten Zeile, ist die zweite Zeile die Überschrift und der Rest
    // der Normtext.
    var zeilen = text.lines().map(String::strip).filter(z -> !z.isEmpty()).toList();
    if (zeilen.size() >= 3 && zeilen.get(0).matches("§\\s*\\d+[a-z]?")) {
      titel = zeilen.get(1);
      var rest = String.join("\n", zeilen.subList(2, zeilen.size()));
      return new Norm(
          enbez,
          titel,
          vorlage.gliederung(),
          List.of(new Absatz(null, normalisiereZitatText(rest))),
          false);
    }
    // Fallback: gesamter Text (ohne „§ N“-Präfix) als unnummerierter Absatz, Titel unverändert.
    var inhalt = text.replaceFirst("^§\\s*\\S+\\s*", "");
    return new Norm(
        enbez,
        titel,
        vorlage.gliederung(),
        List.of(new Absatz(null, normalisiereZitatText(inhalt))),
        false);
  }

  /** Zerlegt zitierten Text in Absätze anhand der „(n)“-Marker. */
  static List<Absatz> parseAbsaetze(String zitat) {
    var text = zitat.strip();
    var absaetze = new ArrayList<Absatz>();
    var matcher = ABSATZ_MARKER.matcher(text);

    int vorherigesEnde = 0;
    String vorherigeNummer = null;
    while (matcher.find()) {
      if (matcher.start() > vorherigesEnde || vorherigeNummer != null) {
        var inhalt = text.substring(vorherigesEnde, matcher.start()).strip();
        if (!inhalt.isEmpty() || vorherigeNummer != null) {
          absaetze.add(new Absatz(vorherigeNummer, normalisiereZitatText(inhalt)));
        }
      }
      vorherigeNummer = matcher.group(1);
      vorherigesEnde = matcher.end();
    }
    var rest = text.substring(vorherigesEnde).strip();
    if (!rest.isEmpty() || vorherigeNummer != null) {
      absaetze.add(new Absatz(vorherigeNummer, normalisiereZitatText(rest)));
    }
    return absaetze;
  }

  /** Fließtext-Whitespace glätten, Aufzählungszeilen des Zitats aber erhalten. */
  private static String normalisiereZitatText(String text) {
    var zeilen = text.split("\n");
    var sb = new StringBuilder();
    for (var zeile : zeilen) {
      var gestutzt = zeile.strip();
      if (gestutzt.isEmpty()) {
        continue;
      }
      if (sb.length() == 0) {
        sb.append(gestutzt);
      } else if (gestutzt.matches("^(\\d+[a-z]?\\.|[a-z]{1,3}\\))\\s.*")) {
        // Aufzählungspunkt: eigene Zeile.
        sb.append("\n  ").append(gestutzt);
      } else {
        sb.append(' ').append(gestutzt);
      }
    }
    return sb.toString();
  }

  private static AngewandteAenderung angewandt(Aenderungsbefehl befehl, String enbez) {
    return new AngewandteAenderung(
        befehl, Status.ANGEWANDT, "", new LinkedHashSet<>(List.of(enbez)));
  }

  private static AngewandteAenderung manuell(Aenderungsbefehl befehl, String begruendung) {
    return new AngewandteAenderung(befehl, Status.MANUELL_PRUEFEN, begruendung, Set.of());
  }

  private static Gesetz gesetzAus(List<Norm> normen) {
    return new Gesetz("", null, null, normen);
  }
}
