// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturErsetzung;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.anwendung.BefehlAnwender.AngewandteAenderung;
import eu.mulk.aendggner.anwendung.BefehlAnwender.Status;
import eu.mulk.aendggner.gesetz.Norm;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Wendet Angabe-Befehle auf die Inhaltsübersichts-Norm an. Deren Text besteht aus Tabellenzeilen
 * („§ 5 | Titel“) und Zwischenüberschriften („2. Abschnitt - …“); die Befehle ersetzen, entfernen
 * oder ergänzen einzelne solcher Zeilen.
 */
final class InhaltsuebersichtAnwender {

  private static final String ENBEZ = "Inhaltsübersicht";

  private InhaltsuebersichtAnwender() {}

  /**
   * Behandelt die Struktur-Befehle auf der Inhaltsübersicht (Angabe gefasst/ersetzt/gestrichen/
   * eingefügt). Liefert {@code null} für wortweise Operationen — die laufen über die normalen
   * Textzweige des {@link BefehlAnwender}, weil die Inhaltsübersicht eine gewöhnliche Norm ist.
   */
  static @Nullable AngewandteAenderung wendeAn(List<Norm> normen, Aenderungsbefehl befehl) {
    return switch (befehl) {
      case Neufassung n -> {
        var ziel = zielKette(n.stelle());
        if (ziel.isEmpty()) {
          yield ersetzeGesamteUebersicht(normen, n);
        }
        yield ersetzeZeilen(normen, befehl, ziel, ziel, n.neuerText());
      }
      case StrukturErsetzung s -> {
        var von = zielKette(s.stelle());
        var bis = s.bisStelle() != null ? zielKette(s.bisStelle()) : von;
        if (von.isEmpty() || bis.isEmpty()) {
          yield manuell(
              befehl, Grund.STELLE_NICHT_AUFLOESBAR, "Angabe-Bereich nennt kein auflösbares Ziel.");
        }
        yield ersetzeZeilen(normen, befehl, von, bis, s.text());
      }
      case Aufhebung a -> {
        var ziel = zielKette(a.stelle());
        if (ziel.isEmpty()) {
          yield manuell(
              befehl,
              Grund.STELLE_NICHT_AUFLOESBAR,
              "Zu streichende Angabe nennt kein auflösbares Ziel.");
        }
        yield ersetzeZeilen(normen, befehl, ziel, ziel, null);
      }
      case StrukturEinfuegung e -> {
        var anker = zielKette(e.stelle());
        if (anker.isEmpty()) {
          yield manuell(
              befehl,
              Grund.STELLE_NICHT_AUFLOESBAR,
              "Einfügeanker in der Inhaltsübersicht nennt kein Ziel.");
        }
        yield fuegeZeilenEin(normen, befehl, anker, e.vorher(), e.text());
      }
      case Aenderungsbefehl.Ersetzung ignoriert -> null;
      case Aenderungsbefehl.Streichung ignoriert -> null;
      case Aenderungsbefehl.WoerterEinfuegung ignoriert -> null;
      default ->
          manuell(
              befehl,
              Grund.NICHT_UNTERSTUETZT,
              "Änderungen an der Inhaltsübersicht werden nicht automatisch angewandt.");
    };
  }

  /**
   * Die adressierte Angabe: die §-/Gliederungs-Komponenten hinter der Inhaltsübersichts-Marke.
   *
   * <p>Nennt der Rahmen dieselbe Einheit wie der Befehl selbst — „In der Inhaltsübersicht wird …
   * Teil 2 wie folgt geändert: … Die Angabe zur Überschrift von Teil 2 Abschnitt 4 wird gestrichen“
   * —, so steht sie zweimal hintereinander in der Stelle. Gemeint ist sie einmal: Das zweite Glied
   * suchte sich sonst innerhalb seiner selbst und bliebe unauffindbar.
   */
  private static List<Stelle.Komponente> zielKette(Stelle stelle) {
    var kette = new ArrayList<Stelle.Komponente>();
    for (var k : stelle.komponenten()) {
      if (!(k instanceof Stelle.Paragraph || k instanceof Stelle.Gliederungseinheit)) {
        continue;
      }
      if (kette.isEmpty() || !kette.get(kette.size() - 1).equals(k)) {
        kette.add(k);
      }
    }
    return List.copyOf(kette);
  }

  /**
   * Führt die Angabe einer Norm ihrer Überschrift nach: Die Zeile der Inhaltsübersicht, die den
   * Paragraphen führt, wird auf den jetzigen Titel gesetzt.
   *
   * <p>Das ist die Ausführung des verweisenden Befehls („Die Inhaltsübersicht wird entsprechend der
   * vorstehenden Nummer 8 Buchst. a geändert“). Übertragen wird nicht der Wortlaut jenes Punktes,
   * sondern sein Ergebnis: Was die Überschrift nach seiner Anwendung besagt, besagt fortan auch die
   * Angabe. Der Umweg über das Ergebnis erspart es, jede Befehlsform ein zweites Mal auf dem
   * Zeilenmodell nachzubilden — und er trifft genau das, was der Verweis meint.
   */
  static AngewandteAenderung fuehreTitelNach(
      List<Norm> normen, Aenderungsbefehl befehl, Stelle.Paragraph paragraph, String neuerTitel) {
    var ziel = List.<Stelle.Komponente>of(paragraph);
    return ersetzeZeilen(normen, befehl, ziel, ziel, paragraph.enbez() + " " + neuerTitel);
  }

  /**
   * Ersetzt die Zeilen von {@code von} bis {@code bis} durch die Angaben des Zitats (oder nichts).
   */
  private static AngewandteAenderung ersetzeZeilen(
      List<Norm> normen,
      Aenderungsbefehl befehl,
      List<Stelle.Komponente> von,
      List<Stelle.Komponente> bis,
      @Nullable String zitat) {
    int normIndex = StellenAufloeser.normIndex(gesetzAus(normen), ENBEZ);
    if (normIndex < 0) {
      return manuell(
          befehl, Grund.BESTAND_WIDERSPRICHT, "Das Gesetz enthält keine Inhaltsübersicht.");
    }
    var norm = normen.get(normIndex);
    var vonFund = findeZeile(norm, von);
    if (vonFund.fehler() != null) {
      return manuell(befehl, vonFund.grund(), vonFund.fehler());
    }
    var bisFund = von.equals(bis) ? vonFund : findeZeile(norm, bis);
    if (bisFund.fehler() != null) {
      return manuell(befehl, bisFund.grund(), bisFund.fehler());
    }
    if (vonFund.absatzIndex() != bisFund.absatzIndex() || bisFund.bisZeile() < vonFund.vonZeile()) {
      return manuell(
          befehl,
          Grund.BEREICH_UNGUELTIG,
          "Angabe-Bereich liegt nicht zusammenhängend in der Inhaltsübersicht.");
    }
    var zeilen = new ArrayList<>(zeilenVon(norm, vonFund.absatzIndex()));
    var einrueckung = einrueckungVon(zeilen.get(vonFund.vonZeile()));
    for (int i = bisFund.bisZeile(); i >= vonFund.vonZeile(); i--) {
      zeilen.remove(i);
    }
    if (zitat != null) {
      zeilen.addAll(vonFund.vonZeile(), angabenZeilen(zitat, einrueckung));
    }
    setzeZeilen(normen, normIndex, vonFund.absatzIndex(), zeilen);
    return angewandt(befehl);
  }

  private static AngewandteAenderung fuegeZeilenEin(
      List<Norm> normen,
      Aenderungsbefehl befehl,
      List<Stelle.Komponente> anker,
      boolean vorher,
      String zitat) {
    int normIndex = StellenAufloeser.normIndex(gesetzAus(normen), ENBEZ);
    if (normIndex < 0) {
      return manuell(
          befehl, Grund.BESTAND_WIDERSPRICHT, "Das Gesetz enthält keine Inhaltsübersicht.");
    }
    var norm = normen.get(normIndex);
    var fund = findeZeile(norm, anker);
    if (fund.fehler() != null) {
      return manuell(befehl, fund.grund(), fund.fehler());
    }
    var zeilen = new ArrayList<>(zeilenVon(norm, fund.absatzIndex()));
    var einrueckung = einrueckungVon(zeilen.get(fund.vonZeile()));
    int position = vorher ? fund.vonZeile() : fund.bisZeile() + 1;
    zeilen.addAll(position, angabenZeilen(zitat, einrueckung));
    setzeZeilen(normen, normIndex, fund.absatzIndex(), zeilen);
    return angewandt(befehl);
  }

  // --- Zeilenmodell ----------------------------------------------------------------------------

  private record Zeilenfund(
      int absatzIndex, int vonZeile, int bisZeile, @Nullable String fehler, @Nullable Grund grund) {
    static Zeilenfund fehlgeschlagen(String begruendung, Grund grund) {
      return new Zeilenfund(-1, -1, -1, begruendung, grund);
    }
  }

  /**
   * Findet die (norm-weit eindeutige) Zeile der adressierten Angabe. Die Kette wird verschachtelt
   * aufgelöst: „Teil 2 Abschnitt 4“ sucht die Abschnitt-Zeile erst hinter der Teil-2-Zeile (und vor
   * dem nächsten Teil), sodass gleichnamige Abschnitte anderer Teile nicht stören.
   */
  private static Zeilenfund findeZeile(Norm norm, List<Stelle.Komponente> kette) {
    var ziel = kette.get(kette.size() - 1);
    Zeilenfund gefunden = null;
    for (int a = 0; a < norm.absaetze().size(); a++) {
      var zeilen = zeilenVon(norm, a);
      int von = 0;
      int bis = zeilen.size();
      boolean fenstergueltig = true;
      for (int k = 0; k < kette.size() - 1 && fenstergueltig; k++) {
        int eltern = eindeutigeZeile(zeilen, zeilenMuster(kette.get(k)), von, bis);
        if (eltern < 0) {
          fenstergueltig = false;
          continue;
        }
        von = eltern + 1;
        bis = naechsteGleichrangige(zeilen, kette.get(k), von, zeilen.size());
      }
      if (!fenstergueltig) {
        continue;
      }
      int treffer = eindeutigeZeile(zeilen, zeilenMuster(ziel), von, bis);
      if (treffer == -2) {
        return Zeilenfund.fehlgeschlagen(
            "Die Angabe zu „" + anzeige(ziel) + "“ ist in der Inhaltsübersicht mehrdeutig.",
            Grund.MEHRDEUTIG);
      }
      if (treffer >= 0) {
        if (gefunden != null) {
          return Zeilenfund.fehlgeschlagen(
              "Die Angabe zu „" + anzeige(ziel) + "“ ist in der Inhaltsübersicht mehrdeutig.",
              Grund.MEHRDEUTIG);
        }
        gefunden = new Zeilenfund(a, treffer, treffer, null, null);
      }
    }
    if (gefunden == null) {
      return Zeilenfund.fehlgeschlagen(
          "Die Angabe zu „" + anzeige(ziel) + "“ ist in der Inhaltsübersicht nicht auffindbar.",
          Grund.STELLE_NICHT_AUFLOESBAR);
    }
    return gefunden;
  }

  /** Die eindeutige Trefferzeile in [von,bis): Index, -1 (nicht gefunden) oder -2 (mehrdeutig). */
  private static int eindeutigeZeile(List<String> zeilen, Pattern muster, int von, int bis) {
    int treffer = -1;
    for (int z = von; z < bis; z++) {
      if (muster.matcher(zeilen.get(z).strip()).find()) {
        if (treffer >= 0) {
          return -2;
        }
        treffer = z;
      }
    }
    return treffer;
  }

  /** Die nächste Zeile derselben Gliederungsart („Teil <n>“) als Fenstergrenze. */
  private static int naechsteGleichrangige(
      List<String> zeilen, Stelle.Komponente eltern, int von, int bis) {
    if (!(eltern instanceof Stelle.Gliederungseinheit g)) {
      return bis;
    }
    var muster =
        Pattern.compile(
            "^(?:"
                + Pattern.quote(g.art())
                + "\\s+\\d|\\d+[a-z]?\\.\\s*"
                + Pattern.quote(g.art())
                + "\\b)");
    for (int z = von; z < bis; z++) {
      if (muster.matcher(zeilen.get(z).strip()).find()) {
        return z;
      }
    }
    return bis;
  }

  private static Pattern zeilenMuster(Stelle.Komponente ziel) {
    return switch (ziel) {
      case Stelle.Paragraph p ->
          Pattern.compile(
              "^" + Pattern.quote(p.sigel()) + "\\s*" + Pattern.quote(p.nummer()) + "(?![0-9a-z])");
      case Stelle.Gliederungseinheit g -> {
        if (g.nummer().isEmpty()) {
          yield Pattern.compile("^" + Pattern.quote(g.art()) + "\\b");
        }
        // Beide Schreibweisen: „Abschnitt 2“ und „2. Abschnitt“.
        yield Pattern.compile(
            "^(?:"
                + Pattern.quote(g.art())
                + "\\s+"
                + Pattern.quote(g.nummer())
                + "(?![0-9a-z])|"
                + Pattern.quote(g.nummer())
                + "\\.\\s*"
                + Pattern.quote(g.art())
                + "\\b)");
      }
      default -> Pattern.compile("(?!)");
    };
  }

  /**
   * „Die Inhaltsübersicht wird durch die folgende Inhaltsübersicht ersetzt: „…““ — der komplette
   * Zeilenbestand der Inhaltsübersichts-Norm wird aus dem Zitat neu aufgebaut.
   */
  private static AngewandteAenderung ersetzeGesamteUebersicht(
      List<Norm> normen, Neufassung befehl) {
    int normIndex = StellenAufloeser.normIndex(gesetzAus(normen), ENBEZ);
    if (normIndex < 0) {
      return manuell(
          befehl, Grund.BESTAND_WIDERSPRICHT, "Das Gesetz enthält keine Inhaltsübersicht.");
    }
    var flach = befehl.neuerText().replaceAll("\\s+", " ").strip();
    flach = flach.replaceFirst("^Inhaltsübersicht\\s*", "");
    // Plausibilitätssperre: Enthält das Zitat Befehlssprache, hat vermutlich ein unbalanciertes
    // Anführungszeichen nachfolgende Befehle in das Zitat gezogen — dann keinesfalls anwenden.
    if (flach.contains("wie folgt geändert") || flach.contains(" wird wie folgt gefasst")) {
      return manuell(
          befehl,
          Grund.ZITAT_UNBRAUCHBAR,
          "Das Zitat der neuen Inhaltsübersicht enthält Befehlstext — vermutlich ist ein"
              + " Anführungszeichen unbalanciert; bitte manuell prüfen.");
    }
    var zeilen = uebersichtsZeilen(flach, "");
    if (zeilen.size() < 2) {
      return manuell(
          befehl, Grund.ZITAT_UNBRAUCHBAR, "Das Zitat enthält keine erkennbare Inhaltsübersicht.");
    }
    var norm = normen.get(normIndex);
    normen.set(
        normIndex,
        norm.mitAbsaetzen(
            List.of(new eu.mulk.aendggner.gesetz.Absatz(null, String.join("\n", zeilen)))));
    return angewandt(befehl);
  }

  // Zeilenanfänge einer Inhaltsübersicht: §-Angaben (nicht Querverweise) und Gliederungsmarken.
  private static final Pattern UEBERSICHT_MARKE =
      Pattern.compile(
          "(?=(?:§|Art\\.)\\s*\\d+[a-z]?\\s+"
              + "(?!Absatz|Absätze|Abs|Satz|Sätze|Nummer|Nummern|Nr|Buchstabe|Buchstaben"
              + "|und|bis|oder|sowie|des|der|dieses)"
              + "(?:\\(|\\p{Lu})"
              + "|(?<!\\S)(?:Teil|Abschnitt|Unterabschnitt|Kapitel|Buch) \\d+[a-z]?(?!\\S)"
              + "|(?<!\\S)Anhang(?!\\S))");

  /**
   * Zerlegt das Zitat in Angabe-Zeilen im Zeilenmodell der Inhaltsübersicht („§ N | Titel“).
   *
   * <p>Getrennt wird an <em>allen</em> Zeilenanfängen, die eine Übersicht kennt — an §-Angaben wie
   * an Gliederungsmarken. Beides ist nötig: Ein Zitat, das mit „Unterabschnitt 4 …“ beginnt und
   * darauf zwanzig Paragraphen aufführt, bliebe sonst eine einzige Zeile, und jede spätere Angabe
   * zu einem dieser Paragraphen fände sie nicht mehr. (Das GEG-Heft von 2023 tut genau das.)
   */
  private static List<String> angabenZeilen(String zitat, String einrueckung) {
    var zeilen = uebersichtsZeilen(zitat.strip().replaceAll("\\s+", " "), einrueckung);
    if (zeilen.isEmpty()) {
      zeilen.add(einrueckung + zitat.strip().replaceAll("\\s+", " "));
    }
    return zeilen;
  }

  /** Der Kopf einer Übersichtszeile: die Bezeichnung, hinter der der Titel steht. */
  private static final Pattern ZEILEN_KOPF =
      Pattern.compile(
          "^((?:Teil|Abschnitt|Unterabschnitt|Kapitel|Buch)\\s+\\d+[a-z]?|Anhang"
              + "|(?:§|Art\\.)\\s*\\d+[a-z]*)\\s*(.*)$");

  /**
   * Der flache Zitattext, zerlegt in Übersichtszeilen. Die Trennung besorgt {@link
   * #UEBERSICHT_MARKE}; wo eine Bezeichnung einen Titel bei sich führt, tritt der Strich zwischen
   * beide.
   */
  private static List<String> uebersichtsZeilen(String flach, String einrueckung) {
    var zeilen = new ArrayList<String>();
    for (var stueck : UEBERSICHT_MARKE.split(flach)) {
      var s = stueck.strip();
      if (s.isEmpty()) {
        continue;
      }
      var m = ZEILEN_KOPF.matcher(s);
      zeilen.add(
          m.matches() && !m.group(2).isEmpty()
              ? einrueckung + m.group(1) + " | " + m.group(2)
              : einrueckung + s);
    }
    return zeilen;
  }

  private static List<String> zeilenVon(Norm norm, int absatzIndex) {
    return norm.absaetze().get(absatzIndex).text().lines().toList();
  }

  private static void setzeZeilen(
      List<Norm> normen, int normIndex, int absatzIndex, List<String> zeilen) {
    var norm = normen.get(normIndex);
    var absaetze = new ArrayList<>(norm.absaetze());
    absaetze.set(absatzIndex, absaetze.get(absatzIndex).mitText(String.join("\n", zeilen)));
    normen.set(normIndex, norm.mitAbsaetzen(absaetze));
  }

  private static String einrueckungVon(String zeile) {
    return zeile.substring(0, zeile.length() - zeile.stripLeading().length());
  }

  private static String anzeige(Stelle.Komponente ziel) {
    return switch (ziel) {
      case Stelle.Paragraph p -> p.enbez();
      case Stelle.Gliederungseinheit g -> g.bezeichnung();
      default -> ziel.toString();
    };
  }

  private static AngewandteAenderung angewandt(Aenderungsbefehl befehl) {
    return new AngewandteAenderung(
        befehl, Status.ANGEWANDT, "", new LinkedHashSet<>(List.of(ENBEZ)), null);
  }

  private static AngewandteAenderung manuell(
      Aenderungsbefehl befehl, @Nullable Grund grund, String begruendung) {
    return new AngewandteAenderung(
        befehl,
        Status.MANUELL_PRUEFEN,
        begruendung,
        Set.of(),
        grund == null ? Grund.STELLE_NICHT_AUFLOESBAR : grund);
  }

  private static eu.mulk.aendggner.gesetz.Gesetz gesetzAus(List<Norm> normen) {
    return new eu.mulk.aendggner.gesetz.Gesetz("", null, null, normen);
  }
}
