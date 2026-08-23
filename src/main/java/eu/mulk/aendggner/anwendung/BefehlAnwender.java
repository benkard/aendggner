// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.anwendung;

import eu.mulk.aendggner.aenderung.Aenderungsbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Anfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Aufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.BereichsUmnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ebene;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Ersetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.FussnotenAufhebung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.GliederungsUeberschriften;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Neufassung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Sammelbefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.SatznummerierungStreichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Streichung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.StrukturErsetzung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.Umnummerierung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.UnbekannterBefehl;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WoerterEinfuegung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortAnker;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautVoranstellung;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuAbsatz;
import eu.mulk.aendggner.aenderung.Aenderungsbefehl.WortlautZuSatz;
import eu.mulk.aendggner.aenderung.Stelle;
import eu.mulk.aendggner.gesetz.Absatz;
import eu.mulk.aendggner.gesetz.Gesetz;
import eu.mulk.aendggner.gesetz.Gliederung;
import eu.mulk.aendggner.gesetz.Norm;
import eu.mulk.aendggner.gesetz.Superskript;
import java.util.ArrayList;
import java.util.Arrays;
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

  /**
   * Ein Protokolleintrag: was mit dem Befehl geschehen ist. Der ausformulierte Grund bleibt
   * maßgeblich; {@code grund} ordnet ihn nur einer Art zu und ist bei angewandten Befehlen leer.
   */
  public record AngewandteAenderung(
      Aenderungsbefehl befehl,
      Status status,
      String begruendung,
      Set<String> betroffeneEnbez,
      @Nullable Grund grund) {}

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
    var gliederungen = new ArrayList<>(alt.gliederungen());
    var protokoll = new ArrayList<AngewandteAenderung>();
    String neuerLangtitel = null;

    // Bereichsweise Umnummerierungen nennen Bezeichnungen, meinen aber Einheiten — erst am Gesetz
    // steht fest, welche. Sie werden deshalb zuerst entfaltet; alles Weitere sieht nur noch
    // gewöhnliche Umnummerierungen.
    befehle = entfalteBereiche(befehle, normen);

    // Angewandt wird schrittweise in Sachreihenfolge, protokolliert befehlsweise in der
    // Reihenfolge des Änderungsgesetzes.
    var schritte = schritte(befehle);
    var ergebnisse = new AngewandteAenderung[schritte.size()];
    for (int index : anwendungsReihenfolge(schritte)) {
      var schritt = schritte.get(index);
      // „Die Überschrift wird wie folgt gefasst / durch die folgende Überschrift ersetzt“ auf
      // oberster Ebene meint die Überschrift des Gesetzes selbst — innerhalb eines Verbunds
      // dagegen nie, dort steht sie für die Überschrift der Einheit, die der Verbund betrifft.
      if (schritt.ganzerBefehl()
          && schritt.teil() instanceof Neufassung n
          && istNurUeberschrift(n.stelle())) {
        neuerLangtitel = n.neuerText().replaceAll("\\s+", " ").strip();
        ergebnisse[index] = angewandt(schritt.teil(), "(Gesetzesüberschrift)");
        continue;
      }
      ergebnisse[index] = wendeAn(normen, gliederungen, schritt.teil());
    }
    protokoll.addAll(verdichte(befehle, schritte, ergebnisse));

    var neu = alt.mitNormen(normen).mitGliederungen(gliederungen);
    if (neuerLangtitel != null) {
      neu = neu.mitLangue(neuerLangtitel);
    }
    return new AnwendungsErgebnis(neu, protokoll);
  }

  /**
   * Ein einzeln anzuwendender Schritt: der Teilbefehl selbst und der Index des Befehls, in dessen
   * Protokolleintrag sein Ergebnis eingeht. Ein Verbund ({@link Sammelbefehl}) zerfällt in je einen
   * Schritt pro Teilbefehl; jeder andere Befehl ist sein eigener einziger Schritt ({@code
   * ganzerBefehl}).
   */
  private record Schritt(int befehlIndex, Aenderungsbefehl teil, boolean ganzerBefehl) {}

  /** Faltet die Befehlsliste zur Schrittliste auf, in Dokumentreihenfolge. */
  private static List<Schritt> schritte(List<Aenderungsbefehl> befehle) {
    var schritte = new ArrayList<Schritt>(befehle.size());
    for (int i = 0; i < befehle.size(); i++) {
      var befehl = befehle.get(i);
      if (befehl instanceof Sammelbefehl s) {
        falte(s, i, schritte);
      } else {
        schritte.add(new Schritt(i, befehl, true));
      }
    }
    return schritte;
  }

  private static void falte(Sammelbefehl befehl, int befehlIndex, List<Schritt> ziel) {
    for (var teil : befehl.teilbefehle()) {
      if (teil instanceof Sammelbefehl geschachtelt) {
        falte(geschachtelt, befehlIndex, ziel);
      } else {
        ziel.add(new Schritt(befehlIndex, teil, false));
      }
    }
  }

  /**
   * Anwendungsreihenfolge der Schritte. Sie folgt dem Dokument, mit einer Ausnahme:
   * Umnummerierungen beziehen sich stets auf die ursprüngliche Zählung, nicht auf den Stand nach
   * den vorangegangenen Punkten. Wer eine Bezeichnung räumt, muss daher vor den kommen, der sie neu
   * besetzt — sonst trüge das Gesetz vorübergehend zwei gleich bezeichnete Einheiten und die
   * Fundstelle wäre mehrdeutig. Aus dieser einen Regel folgt beides: die absteigende Reihenfolge
   * einer aufsteigenden Kaskade („Abs. 3 wird 4“, „Abs. 4 wird 5“, …) und der Vorrang einer
   * Umnummerierung vor einer Einfügung, die deren Bezeichnung neu vergibt.
   *
   * <p>Verschoben wird stets nur nach vorn, und wer vorrückt, nimmt mit, was ihm selbst vorausgehen
   * muss: Vor jedem Schritt laufen erst diejenigen, die ihm eine Bezeichnung räumen, und vor diesen
   * wiederum die ihren. Ohne diese Mitnahme zerrisse eine Kette wie „Nr. 15 wird Nr. 16“, „Nrn. 13
   * und 14 werden die Nrn. 14 und 15“, „nach Nr. 12 wird Nr. 13 eingefügt“: die Einfügung zöge nur
   * das letzte Glied vor sich, und dieses beträfe eine Bezeichnung, die noch besetzt ist. Ein
   * Schritt ohne solche Voraussetzung bleibt dagegen an seinem Platz — daran hängt, dass eine
   * Begleitänderung ihre Dokumentstelle behält.
   *
   * <p>Geordnet werden <em>Schritte</em> und nicht Befehle, weil ein Verbund aus Umnummerierung und
   * Begleitänderung zwei gegenläufige zeitliche Ansprüche in einer Einheit trägt: Die
   * Umnummerierung gehört nach vorn, ihre Begleitänderung aber an ihre Dokumentstelle, denn sie
   * setzt die vorangegangenen Punkte als vollzogen voraus. „Die bisherige Nr. 11 wird Nr. 12 und
   * die Angabe „schriftliche“ wird gestrichen“ (BayJG Art. 56 Abs. 1) trifft nach vorn gezogen noch
   * zwei Fundstellen und wäre mehrdeutig; an seinem Platz belassen genau eine, weil der vorherige
   * Punkt die andere längst ersetzt hat.
   */
  /**
   * Entfaltet jede {@link BereichsUmnummerierung} zu einzelnen {@link Umnummerierung}en, indem sie
   * die im Ausgangsbereich <em>vorhandenen</em> Normen der Reihe nach den neuen Bezeichnungen
   * zuordnet. Aufgehobene Platzhalter zählen nicht mit: Sie tragen keinen Inhalt, und zwei
   * Einheiten gleicher Bezeichnung kann es nicht geben — dieselbe Regel, nach der ein Platzhalter
   * einer Einfügung weicht. Geht die Zählung nicht auf, bleibt der Befehl ungeteilt und wird als
   * manuell zu prüfen gemeldet.
   */
  private static List<Aenderungsbefehl> entfalteBereiche(
      List<Aenderungsbefehl> befehle, List<Norm> normen) {
    var entfaltet = new ArrayList<Aenderungsbefehl>(befehle.size());
    for (var befehl : befehle) {
      if (befehl instanceof BereichsUmnummerierung bereich) {
        var teile = entfalte(bereich, normen);
        entfaltet.add(teile != null ? teile : befehl);
      } else {
        entfaltet.add(befehl);
      }
    }
    return entfaltet;
  }

  private static @Nullable Aenderungsbefehl entfalte(
      BereichsUmnummerierung bereich, List<Norm> normen) {
    var von = paragraphNummer(bereich.stelle());
    var bis = paragraphNummer(bereich.bis());
    var neuVon = paragraphNummer(bereich.neu());
    var neuBis = paragraphNummer(bereich.neuBis());
    if (von == null || bis == null || neuVon == null || neuBis == null) {
      return null;
    }
    var sigel = ((Stelle.Paragraph) bereich.stelle().komponenten().get(0)).sigel();
    var vorhanden = new ArrayList<String>();
    for (var norm : normen) {
      var enbez = norm.enbez();
      if (enbez == null || !enbez.startsWith(sigel + " ") || norm.weggefallen()) {
        continue;
      }
      var nummer = enbez.substring(sigel.length() + 1).strip();
      if (istImBereich(nummer, von, bis)) {
        vorhanden.add(nummer);
      }
    }
    int anzahl = neuBis - neuVon + 1;
    if (vorhanden.size() != anzahl) {
      return null;
    }
    var teile = new ArrayList<Aenderungsbefehl>(anzahl);
    for (int i = 0; i < anzahl; i++) {
      teile.add(
          new Umnummerierung(
              new Stelle(List.of(new Stelle.Paragraph(vorhanden.get(i), sigel))),
              new Stelle(List.of(new Stelle.Paragraph(String.valueOf(neuVon + i), sigel))),
              bereich.provenienz()));
    }
    return teile.size() == 1 ? teile.get(0) : new Sammelbefehl(teile);
  }

  /** Die numerische Bezeichnung eines Paragraphen-/Artikelziels, sonst {@code null}. */
  private static @Nullable Integer paragraphNummer(Stelle stelle) {
    if (stelle.komponenten().size() != 1
        || !(stelle.komponenten().get(0) instanceof Stelle.Paragraph p)) {
      return null;
    }
    var ziffern = p.nummer().replaceAll("[^0-9]", "");
    return ziffern.isEmpty() ? null : Integer.valueOf(ziffern);
  }

  /** Liegt die Bezeichnung („9“, „9a“) im Bereich der beiden Zahlen? */
  private static boolean istImBereich(String nummer, int von, int bis) {
    var ziffern = nummer.replaceAll("[^0-9]", "");
    if (ziffern.isEmpty()) {
      return false;
    }
    int n = Integer.parseInt(ziffern);
    return n >= von && n <= bis;
  }

  private static List<Integer> anwendungsReihenfolge(List<Schritt> schritte) {
    int anzahl = schritte.size();
    var raeumt = new ArrayList<Set<String>>(anzahl);
    var belegt = new ArrayList<Set<String>>(anzahl);
    for (var schritt : schritte) {
      raeumt.add(geraeumteBezeichnungen(schritt.teil()));
      belegt.add(belegteBezeichnungen(schritt.teil()));
    }
    var reihenfolge = new ArrayList<Integer>(anzahl);
    // 0 = offen, 1 = in Arbeit (Zyklus-Bremse), 2 = eingereiht.
    var stand = new byte[anzahl];
    for (int i = 0; i < anzahl; i++) {
      reiheEin(i, stand, raeumt, belegt, reihenfolge);
    }
    return reihenfolge;
  }

  /**
   * Reiht den Schritt ein, nachdem alle Schritte eingereiht sind, die ihm eine seiner Bezeichnungen
   * räumen. Eine ringförmige Abhängigkeit — zwei Schritte, die einander räumen — bricht an der
   * Marke „in Arbeit“ ab; die beteiligten Schritte behalten dann ihre Dokumentreihenfolge.
   */
  private static void reiheEin(
      int schritt,
      byte[] stand,
      List<Set<String>> raeumt,
      List<Set<String>> belegt,
      List<Integer> reihenfolge) {
    if (stand[schritt] != 0) {
      return;
    }
    stand[schritt] = 1;
    var eigene = belegt.get(schritt);
    if (!eigene.isEmpty()) {
      for (int vorgaenger = 0; vorgaenger < stand.length; vorgaenger++) {
        if (vorgaenger != schritt && raeumt.get(vorgaenger).stream().anyMatch(eigene::contains)) {
          reiheEin(vorgaenger, stand, raeumt, belegt, reihenfolge);
        }
      }
    }
    stand[schritt] = 2;
    reihenfolge.add(schritt);
  }

  /**
   * Verdichtet die Schrittergebnisse zu je einem Protokolleintrag pro Befehl. Ein Verbund gilt nur
   * dann als angewandt, wenn jeder seiner Teile griff; sonst nennt die Begründung die gescheiterten
   * Teile in ihrer Reihenfolge im Verbund.
   */
  private static List<AngewandteAenderung> verdichte(
      List<Aenderungsbefehl> befehle, List<Schritt> schritte, AngewandteAenderung[] ergebnisse) {
    var protokoll = new ArrayList<AngewandteAenderung>(befehle.size());
    int schritt = 0;
    for (int i = 0; i < befehle.size(); i++) {
      int von = schritt;
      while (schritt < schritte.size() && schritte.get(schritt).befehlIndex() == i) {
        schritt++;
      }
      if (von == schritt) {
        // Ein Verbund ohne Teilbefehle — nichts anzuwenden, aber auch nichts zu verschweigen.
        protokoll.add(manuell(befehle.get(i), Grund.NICHT_ERKANNT, "Verbund ohne Teilbefehle."));
        continue;
      }
      if (schritte.get(von).ganzerBefehl()) {
        protokoll.add(ergebnisse[von]);
        continue;
      }
      protokoll.add(
          verbund(befehle.get(i), Arrays.asList(ergebnisse).subList(von, schritt), schritte, von));
    }
    return protokoll;
  }

  private static AngewandteAenderung verbund(
      Aenderungsbefehl befehl,
      List<AngewandteAenderung> teilErgebnisse,
      List<Schritt> schritte,
      int von) {
    var betroffene = new LinkedHashSet<String>();
    var fehler = new ArrayList<String>();
    Grund ersterGrund = null;
    for (int k = 0; k < teilErgebnisse.size(); k++) {
      var ergebnis = teilErgebnisse.get(k);
      betroffene.addAll(ergebnis.betroffeneEnbez());
      if (ergebnis.status() != Status.ANGEWANDT) {
        if (ersterGrund == null) {
          ersterGrund = ergebnis.grund();
        }
        fehler.add(
            "Teil "
                + (k + 1)
                + " ("
                + schritte.get(von + k).teil().stelle().anzeigeText()
                + "): "
                + ergebnis.begruendung());
      }
    }
    if (fehler.isEmpty()) {
      return new AngewandteAenderung(befehl, Status.ANGEWANDT, "", betroffene, null);
    }
    // Ein Verbund kann aus mehreren Gründen liegenbleiben; maßgeblich für die Bündelung ist der
    // erste, der aufgetreten ist. Die Wortlaute stehen ohnehin alle nebeneinander.
    return new AngewandteAenderung(
        befehl, Status.MANUELL_PRUEFEN, String.join(" ", fehler), betroffene, ersterGrund);
  }

  /** Bezeichnungen, die ein Befehl freigibt — die Ausgangsstellen seiner Umnummerierungen. */
  private static Set<String> geraeumteBezeichnungen(Aenderungsbefehl befehl) {
    var raeumt = new LinkedHashSet<String>();
    for (var u : umnummerierungen(befehl)) {
      raeumt.add(u.stelle().anzeigeText());
    }
    return raeumt;
  }

  /** Bezeichnungen, die ein Befehl neu vergibt — Umnummerierungsziele und eingefügte Einheiten. */
  private static Set<String> belegteBezeichnungen(Aenderungsbefehl befehl) {
    var belegt = new LinkedHashSet<String>();
    for (var u : umnummerierungen(befehl)) {
      belegt.add(u.neu().anzeigeText());
    }
    for (var e : einfuegungen(befehl)) {
      var bezeichnungen = neueBezeichnungen(e);
      // Nennt der Befehl keine Einzelbezeichnung („die folgenden Nrn. 5 bis 7“), stehen sie als
      // Aufzählungsmarken im eingefügten Block.
      if (bezeichnungen.isEmpty()) {
        bezeichnungen = markenBezeichnungen(e.stelle(), e.ebene(), e.text());
      }
      for (var bezeichnung : bezeichnungen) {
        // Trägt die neue Einheit die Bezeichnung ihres eigenen Ankers („Dem Abs. 1 wird folgender
        // Abs. 1 vorangestellt“), so setzt der Befehl die alte Zählung voraus: er muss vor der
        // Umnummerierung laufen, die sie auflöst, nicht danach.
        if (!bezeichnung.equals(e.stelle().anzeigeText())) {
          belegt.add(bezeichnung);
        }
      }
    }
    // „Absatz 2 wird durch die folgenden Absätze 2 und 3 ersetzt: „(2) … (3) …““ — welche
    // Bezeichnungen der Ersatzblock vergibt, steht ebenfalls in seinen Marken.
    for (var e : ersetzungen(befehl)) {
      belegt.addAll(markenBezeichnungen(e.stelle(), e.ebene(), e.text()));
    }
    return belegt;
  }

  // Aufzählungsmarken in Zitatblöcken, je Ebene.
  private static final Pattern NUMMER_MARKER = Pattern.compile("(?m)^[ \\t]*(\\d+[a-z]?)\\.[ \\t]");
  private static final Pattern BUCHSTABE_MARKER =
      Pattern.compile("(?m)^[ \\t]*([a-z]{1,3})\\)[ \\t]");

  /** Die Bezeichnungen, die die Marken eines Zitatblocks vergeben, relativ zur Ankerstelle. */
  private static List<String> markenBezeichnungen(Stelle stelle, Ebene ebene, String text) {
    var muster =
        switch (ebene) {
          case ABSATZ -> ABSATZ_MARKER;
          case NUMMER -> NUMMER_MARKER;
          case BUCHSTABE -> BUCHSTABE_MARKER;
          default -> null;
        };
    if (muster == null) {
      return List.of();
    }
    var bezeichnungen = new ArrayList<String>();
    var marker = muster.matcher(text);
    while (marker.find()) {
      bezeichnungen.add(mitMarke(stelle, ebene, marker.group(1)));
    }
    return bezeichnungen;
  }

  /** Die Stelle mit ausgetauschter feinster Komponente („§ 3 Absatz 2“ → „§ 3 Absatz 3“). */
  private static String mitMarke(Stelle stelle, Ebene ebene, String bezeichnung) {
    Stelle.Komponente komponente =
        ebene == Ebene.BUCHSTABE
            ? new Stelle.BuchstabeNr(bezeichnung)
            : ebene == Ebene.NUMMER
                ? new Stelle.NummerNr(bezeichnung)
                : new Stelle.AbsatzNr(bezeichnung);
    var komponenten = new ArrayList<>(stelle.komponenten());
    komponenten.removeIf(k -> rang(k) >= rang(komponente));
    komponenten.add(komponente);
    return new Stelle(komponenten).anzeigeText();
  }

  private static final Pattern BEZEICHNUNGS_BEREICH =
      Pattern.compile("(\\d+)(?:[a-z])? bis (\\d+)(?:[a-z])?");

  /**
   * Die vollen Bezeichnungen der eingefügten Einheiten: die Ankerstelle, deren feinste Komponente
   * durch die neue Bezeichnung ersetzt ist („Nach Art. 29a Abs. 5 Satz 1 … folgender Satz 2“ →
   * „Art. 29a Abs. 5 Satz 2“). Ein Block („die folgenden Nrn. 5 bis 7“) belegt alle Bezeichnungen
   * des Bereichs.
   */
  private static List<String> neueBezeichnungen(StrukturEinfuegung s) {
    if (s.bezeichnung() == null) {
      return List.of();
    }
    var bereich = BEZEICHNUNGS_BEREICH.matcher(s.bezeichnung());
    var nummern = new ArrayList<String>();
    if (bereich.matches()) {
      int von = Integer.parseInt(bereich.group(1));
      int bis = Integer.parseInt(bereich.group(2));
      for (int n = von; n <= bis && n - von < 100; n++) {
        nummern.add(String.valueOf(n));
      }
    } else {
      nummern.add(s.bezeichnung());
    }
    return nummern.stream().map(n -> volleBezeichnung(s, n)).toList();
  }

  private static String volleBezeichnung(StrukturEinfuegung s, String bezeichnung) {
    var komponente =
        switch (s.ebene()) {
          case PARAGRAPH ->
              new Stelle.Paragraph(
                  bezeichnung, s.stelle().paragraph().map(Stelle.Paragraph::sigel).orElse("§"));
          case ABSATZ -> new Stelle.AbsatzNr(bezeichnung);
          case SATZ -> new Stelle.SatzNr(bezeichnung);
          case NUMMER -> new Stelle.NummerNr(bezeichnung);
          case BUCHSTABE -> new Stelle.BuchstabeNr(bezeichnung);
        };
    var komponenten = new ArrayList<>(s.stelle().komponenten());
    if (!komponenten.isEmpty()
        && rang(komponenten.get(komponenten.size() - 1)) >= rang(komponente)) {
      komponenten.remove(komponenten.size() - 1);
    }
    komponenten.add(komponente);
    return new Stelle(komponenten).anzeigeText();
  }

  private static int rang(Stelle.Komponente komponente) {
    return switch (komponente) {
      case Stelle.Paragraph p -> 1;
      case Stelle.AbsatzNr a -> 2;
      case Stelle.SatzNr s -> 3;
      case Stelle.NummerNr n -> 4;
      case Stelle.BuchstabeNr b -> 5;
      default -> 0;
    };
  }

  /** Die Umnummerierungen eines Befehls — auch die in einem Verbund ({@link Sammelbefehl}). */
  private static List<Umnummerierung> umnummerierungen(Aenderungsbefehl befehl) {
    return switch (befehl) {
      case Umnummerierung u -> List.of(u);
      case Sammelbefehl s ->
          s.teilbefehle().stream().flatMap(t -> umnummerierungen(t).stream()).toList();
      default -> List.of();
    };
  }

  /** Die Struktur-Einfügungen eines Befehls — auch die in einem Verbund. */
  private static List<StrukturEinfuegung> einfuegungen(Aenderungsbefehl befehl) {
    return switch (befehl) {
      case StrukturEinfuegung e -> List.of(e);
      case Sammelbefehl s ->
          s.teilbefehle().stream().flatMap(t -> einfuegungen(t).stream()).toList();
      default -> List.of();
    };
  }

  /** Die Struktur-Ersetzungen eines Befehls — auch die in einem Verbund. */
  private static List<StrukturErsetzung> ersetzungen(Aenderungsbefehl befehl) {
    return switch (befehl) {
      case StrukturErsetzung e -> List.of(e);
      case Sammelbefehl s ->
          s.teilbefehle().stream().flatMap(t -> ersetzungen(t).stream()).toList();
      default -> List.of();
    };
  }

  private static boolean istNurUeberschrift(Stelle stelle) {
    return stelle.komponenten().size() == 1
        && stelle.komponenten().get(0) instanceof Stelle.Ueberschrift;
  }

  private static AngewandteAenderung wendeAn(
      List<Norm> normen, List<Gliederung> gliederungen, Aenderungsbefehl befehl) {
    if (befehl instanceof UnbekannterBefehl) {
      return manuell(befehl, Grund.NICHT_ERKANNT, "Befehl nicht erkannt.");
    }
    // Sammelbefehle vor den Spezialweichen dispatchen (jeder Teil wird einzeln geroutet).
    if (befehl instanceof Sammelbefehl s) {
      return wendeSammelAn(normen, gliederungen, s);
    }
    try {
      if (befehl.stelle().betrifftInhaltsuebersicht()) {
        var speziell = InhaltsuebersichtAnwender.wendeAn(normen, befehl);
        if (speziell != null) {
          return speziell;
        }
        // Wortweise Operationen laufen durch die normalen Zweige — die Inhaltsübersicht ist eine
        // gewöhnliche Norm, deren enbez der StellenAufloeser bereits auflöst.
      }
      // Änderungen an Gliederungs-Überschriften (Teil/Abschnitt/…) wirken auf den Gliederungsbaum.
      // Anhänge/Anlagen sind dagegen eigene Normen und laufen durch die normalen Zweige.
      else if (befehl.stelle().betrifftEchteGliederung()) {
        return switch (befehl) {
          case Neufassung n -> wendeGliederungNeufassungAn(gliederungen, n);
          case Aufhebung a -> wendeGliederungStreichungAn(gliederungen, a);
          case Umnummerierung u -> wendeGliederungUmnummerierungAn(gliederungen, u);
          default ->
              manuell(
                  befehl,
                  Grund.NICHT_UNTERSTUETZT,
                  "Strukturänderung wird nicht automatisch angewandt.");
        };
      }
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
        // Bereiche werden vor der Anwendung entfaltet (siehe #entfalteBereiche); hierher gelangt
        // nur, was sich am Gesetz nicht auflösen ließ.
        case BereichsUmnummerierung b ->
            manuell(
                befehl,
                Grund.BEREICH_UNGUELTIG,
                "Der Bereich "
                    + b.stelle().anzeigeText()
                    + " bis "
                    + b.bis().anzeigeText()
                    + " trägt nicht so viele Einheiten, wie der Befehl neue Bezeichnungen nennt.");
        case WortlautZuAbsatz w -> wendeWortlautZuAbsatzAn(normen, w);
        case WortlautZuSatz w -> wendeWortlautZuSatzAn(normen, w);
        case WortlautVoranstellung w -> wendeWortlautVoranstellungAn(normen, w);
        case FussnotenAufhebung f -> wendeFussnotenAufhebungAn(normen, f);
        case SatznummerierungStreichung s -> wendeSatznummerierungStreichungAn(normen, s);
        case GliederungsUeberschriften g ->
            wendeGliederungsUeberschriftenAn(normen, gliederungen, g);
        case Sammelbefehl s -> wendeSammelAn(normen, gliederungen, s);
        case UnbekannterBefehl u -> manuell(befehl, Grund.NICHT_ERKANNT, "Befehl nicht erkannt.");
      };
    } catch (RuntimeException e) {
      return manuell(befehl, Grund.FEHLGESCHLAGEN, "Anwendung fehlgeschlagen: " + e);
    }
  }

  // --- Gliederungs-Überschriften -------------------------------------------------------------

  private static AngewandteAenderung wendeGliederungNeufassungAn(
      List<Gliederung> gliederungen, Neufassung befehl) {
    int idx = findeGliederung(gliederungen, befehl.stelle().gliederungsPfad());
    if (idx < 0) {
      return manuell(befehl, Grund.STELLE_NICHT_AUFLOESBAR, "Gliederungseinheit nicht gefunden.");
    }
    var alt = gliederungen.get(idx);
    var titel = befehl.neuerText().replaceAll("\\s+", " ").strip();
    // Führende Eigenbezeichnung („Abschnitt 2 …“ oder „2. Abschnitt …“) aus dem Zitat entfernen.
    var label = Pattern.compile("^(\\d+[a-z]?\\.\\s+\\S+|\\S+\\s+\\d+[a-z]?)\\s+").matcher(titel);
    if (label.find()
        && kanonischeBezeichnung(label.group(1)).equals(kanonischeBezeichnung(alt.bezeichnung()))) {
      titel = titel.substring(label.end()).strip();
    } else if (titel.startsWith(alt.bezeichnung())) {
      titel = titel.substring(alt.bezeichnung().length()).strip();
    }
    gliederungen.set(idx, alt.mitTitel(titel.isEmpty() ? null : titel));
    return angewandt(befehl, alt.bezeichnung());
  }

  private static AngewandteAenderung wendeGliederungStreichungAn(
      List<Gliederung> gliederungen, Aufhebung befehl) {
    int idx = findeGliederung(gliederungen, befehl.stelle().gliederungsPfad());
    if (idx < 0) {
      return manuell(befehl, Grund.STELLE_NICHT_AUFLOESBAR, "Gliederungseinheit nicht gefunden.");
    }
    var alt = gliederungen.get(idx);
    gliederungen.set(idx, alt.mitTitel("(weggefallen)"));
    return angewandt(befehl, alt.bezeichnung());
  }

  /**
   * „Der bisherige Abschnitt 2 wird zu Abschnitt 3.“ — Bezeichnung der Gliederungseinheit umsetzen.
   */
  private static AngewandteAenderung wendeGliederungUmnummerierungAn(
      List<Gliederung> gliederungen, Umnummerierung befehl) {
    int idx = findeGliederung(gliederungen, befehl.stelle().gliederungsPfad());
    if (idx < 0) {
      return manuell(
          befehl,
          Grund.STELLE_NICHT_AUFLOESBAR,
          "Gliederungseinheit nicht gefunden: " + befehl.stelle().anzeigeText());
    }
    var neuPfad = befehl.neu().gliederungsPfad();
    if (neuPfad.isEmpty()) {
      return manuell(befehl, Grund.NICHT_ERKANNT, "Neue Gliederungsbezeichnung fehlt.");
    }
    var neueBezeichnung = neuPfad.get(neuPfad.size() - 1).bezeichnung();
    var alt = gliederungen.get(idx);
    gliederungen.set(idx, alt.mitBezeichnung(neueBezeichnung));
    return angewandt(befehl, neueBezeichnung);
  }

  /**
   * „Nach § 33 werden die folgenden Überschriften zu Teil 3 und zu Teil 3 Abschnitt 1 eingefügt“
   * bzw. „Die bisherigen Überschriften zu X werden durch die folgende Überschrift zu Y ersetzt“:
   * neue Gliederungen entstehen im Gliederungsbaum, und die Normen des betroffenen Blocks werden
   * der (innersten) neuen Einheit zugeordnet.
   */
  private static AngewandteAenderung wendeGliederungsUeberschriftenAn(
      List<Norm> normen, List<Gliederung> gliederungen, GliederungsUeberschriften befehl) {
    // Titel der neuen Einheiten aus dem Zitat ziehen: das Zitat reiht „<Bezeichnung> <Titel>“
    // in Befehlreihenfolge aneinander.
    var flach = befehl.text().replaceAll("\\s+", " ").strip();
    var starts = new int[befehl.neue().size()];
    int suchAb = 0;
    for (int i = 0; i < befehl.neue().size(); i++) {
      var bezeichnung = befehl.neue().get(i).bezeichnung();
      starts[i] = flach.indexOf(bezeichnung, suchAb);
      if (starts[i] < 0) {
        return manuell(
            befehl,
            Grund.ZITAT_UNBRAUCHBAR,
            "Die Überschrift zu „" + bezeichnung + "“ fehlt im Zitat.");
      }
      suchAb = starts[i] + bezeichnung.length();
    }
    var neueGliederungen = new ArrayList<Gliederung>();
    for (int i = 0; i < befehl.neue().size(); i++) {
      var bezeichnung = befehl.neue().get(i).bezeichnung();
      int titelVon = starts[i] + bezeichnung.length();
      int titelBis = i + 1 < starts.length ? starts[i + 1] : flach.length();
      var titel = flach.substring(titelVon, titelBis).strip();
      neueGliederungen.add(new Gliederung(bezeichnung, titel.isEmpty() ? null : titel));
    }
    var ziel = neueGliederungen.get(neueGliederungen.size() - 1);

    if (!befehl.ersetzte().isEmpty()) {
      // Ersetzungsform: die bisherigen Einheiten weichen den neuen.
      var indizes = new ArrayList<Integer>();
      for (var pfad : befehl.ersetzte()) {
        int idx = findeGliederung(gliederungen, List.copyOf(pfad));
        if (idx < 0) {
          return manuell(
              befehl,
              Grund.STELLE_NICHT_AUFLOESBAR,
              "Gliederungseinheit nicht gefunden: " + pfad.get(pfad.size() - 1).bezeichnung());
        }
        indizes.add(idx);
      }
      var alte =
          indizes.stream().map(gliederungen::get).collect(java.util.stream.Collectors.toSet());
      int einfuegePos = java.util.Collections.min(indizes);
      indizes.sort(java.util.Comparator.reverseOrder());
      for (int idx : indizes) {
        gliederungen.remove(idx);
      }
      gliederungen.addAll(einfuegePos, neueGliederungen);
      for (int k = 0; k < normen.size(); k++) {
        var g = normen.get(k).gliederung();
        if (g != null && alte.contains(g)) {
          normen.set(k, normen.get(k).mitGliederung(ziel));
        }
      }
      return angewandt(befehl, neueGliederungen.stream().map(Gliederung::bezeichnung).toList());
    }

    // Einfügeform: hinter dem Anker-§.
    var aufloesung = loeseNormAuf(normen, befehl.stelle());
    if (aufloesung.fehler() != null) {
      return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
    }
    var anker = normen.get(aufloesung.normIndex());
    int gliederungsPos =
        anker.gliederung() != null
            ? gliederungen.indexOf(anker.gliederung()) + 1
            : gliederungen.size();
    if (gliederungsPos == 0) {
      gliederungsPos = gliederungen.size();
    }
    gliederungen.addAll(gliederungsPos, neueGliederungen);
    int normPos = aufloesung.normIndex() + 1;
    if (normPos < normen.size()) {
      // Der zusammenhängende Block mit unveränderter bisheriger Gliederung wird umgehängt;
      // spätere Überschriften-Befehle ordnen ihre Abschnitte ihrerseits neu zu.
      var bisherige = normen.get(normPos).gliederung();
      for (int k = normPos;
          k < normen.size() && java.util.Objects.equals(normen.get(k).gliederung(), bisherige);
          k++) {
        normen.set(k, normen.get(k).mitGliederung(ziel));
      }
    }
    return angewandt(befehl, neueGliederungen.stream().map(Gliederung::bezeichnung).toList());
  }

  /**
   * Findet die Gliederungseinheit zum Pfad („Teil 3 Abschnitt 2“): jede Ebene wird per Bezeichnung
   * innerhalb des Kennzahl-Präfixes der übergeordneten Ebene aufgelöst.
   */
  private static int findeGliederung(
      List<Gliederung> gliederungen, List<Stelle.Gliederungseinheit> pfad) {
    if (pfad.isEmpty()) {
      return -1;
    }
    String praefix = "";
    int gefunden = -1;
    for (var einheit : pfad) {
      gefunden = -1;
      for (int i = 0; i < gliederungen.size(); i++) {
        var g = gliederungen.get(i);
        if (kanonischeBezeichnung(g.bezeichnung())
                .equals(kanonischeBezeichnung(einheit.bezeichnung()))
            && (g.kennzahl() == null || g.kennzahl().startsWith(praefix))) {
          gefunden = i;
          break;
        }
      }
      if (gefunden < 0) {
        return -1;
      }
      var kennzahl = gliederungen.get(gefunden).kennzahl();
      if (kennzahl != null) {
        praefix = kennzahl;
      }
    }
    return gefunden;
  }

  /** Normalisiert Gliederungsbezeichnungen: „2. Abschnitt“ und „Abschnitt 2“ sind dieselbe. */
  static String kanonischeBezeichnung(String bezeichnung) {
    return bezeichnung.strip().replaceFirst("^(\\d+[a-z]?)\\.\\s+(\\S+)$", "$2 $1");
  }

  // --- Wortweise Textoperationen -------------------------------------------------------------

  private static AngewandteAenderung wendeErsetzungAn(List<Norm> normen, Ersetzung befehl) {
    return bearbeiteText(
        normen,
        befehl,
        ohneFussnoten(
            text -> {
              if (befehl.amEnde()) {
                var gestutzt = text.stripTrailing();
                if (!gestutzt.endsWith(befehl.alt())) {
                  return TextErgebnis.fehler(
                      "Der Text endet nicht mit „" + befehl.alt() + "“.", Grund.ZIELTEXT_FEHLT);
                }
                var rumpf = gestutzt.substring(0, gestutzt.length() - befehl.alt().length());
                var fuge =
                    brauchtFuge(befehl.alt(), befehl.neu())
                            && !rumpf.isEmpty()
                            && !Character.isWhitespace(rumpf.charAt(rumpf.length() - 1))
                        ? " "
                        : "";
                return TextErgebnis.ok(rumpf + fuge + befehl.neu());
              }
              int anzahl = zaehleVorkommen(text, befehl.alt());
              if (anzahl == 0) {
                return TextErgebnis.fehler(
                    "„" + befehl.alt() + "“ kommt im Zieltext nicht vor.", Grund.ZIELTEXT_FEHLT);
              }
              if (anzahl > 1 && !befehl.jeweils()) {
                return TextErgebnis.fehler(
                    "„"
                        + befehl.alt()
                        + "“ kommt "
                        + anzahl
                        + "-mal vor (ohne „jeweils“ mehrdeutig).",
                    Grund.MEHRDEUTIG);
              }
              if (brauchtFuge(befehl.alt(), befehl.neu())) {
                return TextErgebnis.ok(ersetzeMitFuge(text, befehl.alt(), befehl.neu()));
              }
              return TextErgebnis.ok(ersetzeWortweise(text, befehl.alt(), befehl.neu()));
            }));
  }

  private static final Pattern NUR_SATZZEICHEN = Pattern.compile("\\p{Punct}+");

  /**
   * Tritt an die Stelle eines Satzzeichens ein Wort oder ein Klammerzusatz, so gehört ein
   * Leerzeichen davor. „Die bisherige Nr. 7 wird Nr. 5 und das Komma wird durch das Wort „und“
   * ersetzt“ führt sonst auf „…Fachkräfte)und“ statt auf „…Fachkräfte) und“; so setzt es auch die
   * amtliche Nachfassung. Maßgeblich ist, dass das Ersetzte <em>nur</em> aus Satzzeichen besteht —
   * eine Ersetzung ganzer Wörter bringt ihren Zwischenraum schon mit.
   */
  private static boolean brauchtFuge(String alt, String neu) {
    return NUR_SATZZEICHEN.matcher(alt).matches()
        && !neu.isEmpty()
        && (Character.isLetter(neu.codePointAt(0)) || neu.charAt(0) == '(');
  }

  /** Ersetzt jedes Vorkommen, das als Wort steht (siehe {@link #findeVorkommen}). */
  private static String ersetzeWortweise(String text, String alt, String neu) {
    var sb = new StringBuilder();
    int von = 0;
    for (int idx = findeVorkommen(text, alt, 0); idx >= 0; idx = findeVorkommen(text, alt, von)) {
      sb.append(text, von, idx).append(neu);
      von = idx + alt.length();
    }
    return sb.append(text, von, text.length()).toString();
  }

  private static String ersetzeMitFuge(String text, String alt, String neu) {
    var sb = new StringBuilder();
    int von = 0;
    for (int idx = text.indexOf(alt); idx >= 0; idx = text.indexOf(alt, von)) {
      sb.append(text, von, idx);
      if (idx > 0 && !Character.isWhitespace(text.charAt(idx - 1))) {
        sb.append(' ');
      }
      sb.append(neu);
      von = idx + alt.length();
    }
    return sb.append(text, von, text.length()).toString();
  }

  private static AngewandteAenderung wendeStreichungAn(List<Norm> normen, Streichung befehl) {
    return bearbeiteText(
        normen,
        befehl,
        ohneFussnoten(
            text -> {
              int anzahl = zaehleVorkommen(text, befehl.woerter());
              if (anzahl == 0) {
                return TextErgebnis.fehler(
                    "„" + befehl.woerter() + "“ kommt im Zieltext nicht vor.",
                    Grund.ZIELTEXT_FEHLT);
              }
              if (anzahl > 1) {
                return TextErgebnis.fehler(
                    "„" + befehl.woerter() + "“ kommt " + anzahl + "-mal vor.", Grund.MEHRDEUTIG);
              }
              int naht = findeVorkommen(text, befehl.woerter(), 0);
              return TextErgebnis.ok(
                  heileNaht(
                      text.substring(0, naht) + text.substring(naht + befehl.woerter().length()),
                      naht));
            }));
  }

  private static AngewandteAenderung wendeWoerterEinfuegungAn(
      List<Norm> normen, WoerterEinfuegung befehl) {
    return bearbeiteText(
        normen,
        befehl,
        ohneFussnoten(
            text ->
                switch (befehl.anker()) {
                  case WortAnker.NachWoertern nach -> {
                    var pruefung = eindeutigeFundstelle(text, nach.woerter());
                    if (pruefung.fehler() != null) {
                      yield TextErgebnis.fehler(pruefung.fehler(), pruefung.grund());
                    }
                    int ende = pruefung.index() + nach.woerter().length();
                    yield TextErgebnis.ok(
                        text.substring(0, ende)
                            + fuge(befehl.woerter())
                            + befehl.woerter()
                            + text.substring(ende));
                  }
                  case WortAnker.VorWoertern vor -> {
                    var pruefung = eindeutigeFundstelle(text, vor.woerter());
                    if (pruefung.fehler() != null) {
                      yield TextErgebnis.fehler(pruefung.fehler(), pruefung.grund());
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
                      yield TextErgebnis.fehler(
                          "Der Zieltext endet nicht mit einem Komma.", Grund.ZIELTEXT_FEHLT);
                    }
                    yield TextErgebnis.ok(
                        gestutzt.substring(0, gestutzt.length() - 1)
                            + " "
                            + befehl.woerter()
                            + ",");
                  }
                  case WortAnker.AmEnde ignoriert -> {
                    var gestutzt = text.stripTrailing();
                    if (gestutzt.endsWith(".")
                        || gestutzt.endsWith(",")
                        || gestutzt.endsWith(";")) {
                      var satzzeichen = gestutzt.charAt(gestutzt.length() - 1);
                      yield TextErgebnis.ok(
                          gestutzt.substring(0, gestutzt.length() - 1)
                              + " "
                              + befehl.woerter()
                              + satzzeichen);
                    }
                    yield TextErgebnis.ok(gestutzt + " " + befehl.woerter());
                  }
                }));
  }

  // --- Strukturoperationen -------------------------------------------------------------------

  private static AngewandteAenderung wendeNeufassungAn(List<Norm> normen, Neufassung befehl) {
    var stelle = befehl.stelle();

    if (stelle.betrifftUeberschrift()) {
      var aufloesung = loeseNormAuf(normen, stelle);
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
      }
      var norm = normen.get(aufloesung.normIndex());
      var titel = befehl.neuerText().replaceFirst("^(?:§|Art\\.)\\s*\\S+\\s+", "").strip();
      normen.set(aufloesung.normIndex(), norm.mitTitel(titel));
      return angewandt(befehl, norm.enbez());
    }

    if (nurNorm(stelle)) {
      var aufloesung = loeseNormAuf(normen, stelle);
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
      }
      var alteNorm = normen.get(aufloesung.normIndex());
      var neueNorm = parseNorm(befehl.neuerText(), alteNorm.enbez(), alteNorm);
      normen.set(aufloesung.normIndex(), neueNorm);
      return angewandt(befehl, alteNorm.enbez());
    }

    if (stelle.absatz().isPresent() && feinsteIstAbsatz(stelle)) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.grund(), nicht.begruendung());
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

    // Neufassung einer Nummer / eines Buchstabens: Der neue Wortlaut tritt an die Stelle der
    // alten Einheit — und zwar auf deren Einrückung. Ohne sie stünde die neugefasste Zeile in
    // Spalte 0, und der Zeilenblock ihrer Marke reichte fortan bis ans Absatzende, weil er so
    // weit reicht, wie tiefer eingerückt wird (StellenAufloeser.zeilenBlock): ein späterer
    // Befehl „Nach Nr. 4 werden die folgenden Nrn. 5 bis 7 eingefügt“ träte dann hinter die
    // letzte Nummer des Absatzes statt hinter die Nr. 4.
    if (feinsteIstAufzaehlung(befehl.stelle())) {
      return bearbeiteBereich(
          normen,
          befehl,
          (text, bereich) ->
              TextErgebnis.ok(
                  text.substring(0, bereich.von())
                      + rueckeZitatEin(
                          normalisiereZitatText(befehl.neuerText()),
                          einrueckungVon(text, bereich.von()))
                      + text.substring(bereich.bis())));
    }

    // Neufassung eines Satzes / Halbsatzes: Bereich ersetzen. Eine Einrückung gibt es hier
    // nicht — der Bereich beginnt mitten in der Zeile.
    return bearbeiteText(
        normen, befehl, text -> TextErgebnis.ok(normalisiereZitatText(befehl.neuerText())));
  }

  /** Ein Ziel (Absatz, Satz, Nummer, Buchstabe) wird durch einen Block ersetzt (ggf. 1 → N). */
  private static AngewandteAenderung wendeStrukturErsetzungAn(
      List<Norm> normen, StrukturErsetzung befehl) {
    return switch (befehl.ebene()) {
      case ABSATZ -> {
        var stelle = befehl.stelle();
        if (stelle.absatz().isEmpty()) {
          yield manuell(
              befehl,
              Grund.STELLE_NICHT_AUFLOESBAR,
              "Ersetzungsziel nennt keinen Absatz: " + stelle.anzeigeText());
        }
        var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
        if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
          yield manuell(befehl, nicht.grund(), nicht.begruendung());
        }
        var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
        var norm = normen.get(fundstelle.normIndex());
        int vonIndex = fundstelle.absatzIndex();
        int bisIndex = vonIndex;
        // Bereich („Die Absätze 8 und 9 werden … ersetzt“): das letzte Ziel bestimmt das Ende.
        if (befehl.bisStelle() != null) {
          var e2 = StellenAufloeser.aufloese(gesetzAus(normen), befehl.bisStelle());
          if (e2 instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
            yield manuell(befehl, nicht.grund(), nicht.begruendung());
          }
          var f2 = ((StellenAufloeser.Ergebnis.Gefunden) e2).fundstelle();
          if (f2.normIndex() != fundstelle.normIndex() || f2.absatzIndex() == null) {
            yield manuell(
                befehl,
                Grund.BEREICH_UNGUELTIG,
                "Ersetzungsbereich liegt nicht in einer einzigen Norm.");
          }
          bisIndex = f2.absatzIndex();
        }
        if (bisIndex < vonIndex) {
          yield manuell(
              befehl, Grund.BEREICH_UNGUELTIG, "Ersetzungsbereich ist leer oder absteigend.");
        }
        var absaetze = new ArrayList<>(norm.absaetze());
        for (int k = bisIndex; k >= vonIndex; k--) {
          absaetze.remove(k);
        }
        absaetze.addAll(vonIndex, parseAbsaetze(befehl.text()));
        normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
        yield angewandt(befehl, norm.enbez());
      }
      case SATZ -> {
        if (befehl.bisStelle() == null) {
          yield bearbeiteBereich(
              normen,
              befehl,
              (text, bereich) ->
                  TextErgebnis.ok(
                      text.substring(0, bereich.von())
                          + befehl.text().strip().replaceAll("\\s+", " ")
                          + text.substring(bereich.bis())));
        }
        yield wendeSatzBereichsErsetzungAn(normen, befehl);
      }
      case NUMMER, BUCHSTABE -> {
        if (befehl.bisStelle() != null) {
          yield wendeZeilenBereichsErsetzungAn(normen, befehl);
        }
        yield bearbeiteBereich(
            normen,
            befehl,
            (text, bereich) -> {
              var einrueckung = einrueckungVon(text, bereich.von());
              var ersatz = rueckeZitatEin(normalisiereZitatText(befehl.text()), einrueckung);
              return TextErgebnis.ok(
                  text.substring(0, bereich.von()) + ersatz + text.substring(bereich.bis()));
            });
      }
      case PARAGRAPH -> {
        // „§ 71 wird durch die folgenden §§ 71 bis 71p ersetzt: „…““ — der adressierte §-Bereich
        // wird entfernt und durch die Paragraphen des Blocks ersetzt.
        var aufloesung = loeseNormAuf(normen, befehl.stelle());
        if (aufloesung.fehler() != null) {
          yield manuell(befehl, aufloesung.grund(), aufloesung.fehler());
        }
        int vonIndex = aufloesung.normIndex();
        int bisIndex = vonIndex;
        if (befehl.bisStelle() != null) {
          var a2 = loeseNormAuf(normen, befehl.bisStelle());
          if (a2.fehler() != null) {
            yield manuell(befehl, a2.grund(), a2.fehler());
          }
          bisIndex = a2.normIndex();
        }
        if (bisIndex < vonIndex) {
          yield manuell(
              befehl, Grund.BEREICH_UNGUELTIG, "Ersetzungsbereich ist leer oder absteigend.");
        }
        var neue = parseNormenBlock(befehl.text(), normen.get(vonIndex).gliederung());
        if (neue.isEmpty()) {
          yield manuell(
              befehl, Grund.ZITAT_UNBRAUCHBAR, "Im Ersetzungsblock wurde kein Paragraph erkannt.");
        }
        for (int k = bisIndex; k >= vonIndex; k--) {
          normen.remove(k);
        }
        normen.addAll(vonIndex, neue);
        yield angewandt(befehl, neue.stream().map(Norm::enbez).toList());
      }
    };
  }

  /**
   * Ersetzt einen zusammenhängenden Satz-Bereich („Die Sätze 4 und 5 werden … gefasst“) durch einen
   * Block: vom Anfang des ersten bis zum Ende des letzten adressierten Satzes (beide müssen im
   * selben Absatz derselben Norm liegen).
   */
  private static AngewandteAenderung wendeSatzBereichsErsetzungAn(
      List<Norm> normen, StrukturErsetzung befehl) {
    var e1 = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
    if (e1 instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.grund(), nicht.begruendung());
    }
    var e2 = StellenAufloeser.aufloese(gesetzAus(normen), befehl.bisStelle());
    if (e2 instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.grund(), nicht.begruendung());
    }
    var f1 = ((StellenAufloeser.Ergebnis.Gefunden) e1).fundstelle();
    var f2 = ((StellenAufloeser.Ergebnis.Gefunden) e2).fundstelle();
    if (f1.normIndex() != f2.normIndex()
        || f1.absatzIndex() == null
        || !f1.absatzIndex().equals(f2.absatzIndex())
        || f1.bereich() == null
        || f2.bereich() == null) {
      return manuell(
          befehl, Grund.BEREICH_UNGUELTIG, "Satz-Bereich liegt nicht in einem einzigen Absatz.");
    }
    int von = f1.bereich().von();
    int bis = f2.bereich().bis();
    if (bis < von) {
      return manuell(befehl, Grund.BEREICH_UNGUELTIG, "Satz-Bereich ist leer oder absteigend.");
    }
    var norm = normen.get(f1.normIndex());
    var absaetze = new ArrayList<>(norm.absaetze());
    var absatz = absaetze.get(f1.absatzIndex());
    var text = absatz.text();
    var neu =
        text.substring(0, von)
            + befehl.text().strip().replaceAll("\\s+", " ")
            + text.substring(bis);
    absaetze.set(f1.absatzIndex(), absatz.mitText(neu));
    normen.set(f1.normIndex(), norm.mitAbsaetzen(absaetze));
    return angewandt(befehl, norm.enbez());
  }

  /**
   * Ersetzt einen zusammenhängenden Nummern-/Buchstaben-Bereich („Nummer 3 bis 6 wird durch die
   * folgenden Nummern 3 und 4 ersetzt“) durch den zitierten Block: vom Zeilenanfang der ersten bis
   * zum Zeilenende der letzten Einheit (beide im selben Absatz derselben Norm).
   */
  private static AngewandteAenderung wendeZeilenBereichsErsetzungAn(
      List<Norm> normen, StrukturErsetzung befehl) {
    var e1 = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
    if (e1 instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.grund(), nicht.begruendung());
    }
    var e2 = StellenAufloeser.aufloese(gesetzAus(normen), befehl.bisStelle());
    if (e2 instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.grund(), nicht.begruendung());
    }
    var f1 = ((StellenAufloeser.Ergebnis.Gefunden) e1).fundstelle();
    var f2 = ((StellenAufloeser.Ergebnis.Gefunden) e2).fundstelle();
    if (f1.normIndex() != f2.normIndex()
        || f1.absatzIndex() == null
        || !f1.absatzIndex().equals(f2.absatzIndex())
        || f1.bereich() == null
        || f2.bereich() == null) {
      return manuell(
          befehl,
          Grund.BEREICH_UNGUELTIG,
          "Ersetzungsbereich liegt nicht in einem einzigen Absatz.");
    }
    int von = f1.bereich().von();
    int bis = f2.bereich().bis();
    if (bis < von) {
      return manuell(
          befehl, Grund.BEREICH_UNGUELTIG, "Ersetzungsbereich ist leer oder absteigend.");
    }
    var norm = normen.get(f1.normIndex());
    var absaetze = new ArrayList<>(norm.absaetze());
    var absatz = absaetze.get(f1.absatzIndex());
    var text = absatz.text();
    var einrueckung = einrueckungVon(text, von);
    var ersatz = rueckeZitatEin(normalisiereZitatText(befehl.text()), einrueckung);
    absaetze.set(
        f1.absatzIndex(), absatz.mitText(text.substring(0, von) + ersatz + text.substring(bis)));
    normen.set(f1.normIndex(), norm.mitAbsaetzen(absaetze));
    return angewandt(befehl, norm.enbez());
  }

  private static AngewandteAenderung wendeStrukturEinfuegungAn(
      List<Norm> normen, StrukturEinfuegung befehl) {
    var wortAnker = befehl.anker();
    if (wortAnker != null) {
      return wendeWortankerEinfuegungAn(normen, befehl, wortAnker);
    }
    return switch (befehl.ebene()) {
      case PARAGRAPH -> {
        var aufloesung = loeseNormAuf(normen, befehl.stelle());
        if (aufloesung.fehler() != null) {
          yield manuell(befehl, aufloesung.grund(), aufloesung.fehler());
        }
        var anker = normen.get(aufloesung.normIndex());
        int position = aufloesung.normIndex() + (befehl.vorher() ? 0 : 1);

        // „Nach § 60a werden die folgenden §§ 60b und 60c eingefügt: „…““ — Block mehrerer §§.
        if (befehl.bezeichnung() == null) {
          var neue = parseNormenBlock(befehl.text(), anker.gliederung());
          if (neue.isEmpty()) {
            yield manuell(
                befehl, Grund.ZITAT_UNBRAUCHBAR, "Im Einfügeblock wurde kein Paragraph erkannt.");
          }
          for (var n : neue) {
            if (StellenAufloeser.normIndex(gesetzAus(normen), n.enbez()) >= 0) {
              yield manuell(
                  befehl,
                  Grund.BESTAND_WIDERSPRICHT,
                  n.enbez() + " existiert bereits im Stammgesetz.");
            }
          }
          normen.addAll(position, neue);
          yield angewandt(befehl, neue.stream().map(Norm::enbez).toList());
        }

        var sigelNeu = befehl.stelle().paragraph().map(Stelle.Paragraph::sigel).orElse("§");
        var enbezNeu = sigelNeu + " " + befehl.bezeichnung();
        if (StellenAufloeser.normIndex(gesetzAus(normen), enbezNeu) >= 0) {
          yield manuell(
              befehl, Grund.BESTAND_WIDERSPRICHT, enbezNeu + " existiert bereits im Stammgesetz.");
        }
        var neueNorm =
            parseNorm(
                befehl.text(),
                enbezNeu,
                new Norm(enbezNeu, null, anker.gliederung(), List.of(), false));
        normen.add(position, neueNorm);
        yield angewandt(befehl, enbezNeu);
      }
      case ABSATZ -> {
        var stelle = befehl.stelle();
        if (stelle.absatz().isEmpty()) {
          yield manuell(
              befehl,
              Grund.STELLE_NICHT_AUFLOESBAR,
              "Einfügeanker nennt keinen Absatz: " + stelle.anzeigeText());
        }
        var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
        if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
          yield manuell(befehl, nicht.grund(), nicht.begruendung());
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
                // Der Einfügeblock darf mehrere Einheiten enthalten („die Nummern 4a bis 4c“);
                // jede Aufzählungszeile des Zitats bleibt eine eigene Zeile.
                var block = rueckeZitatEin(normalisiereZitatText(befehl.text()), einrueckung);
                var neuerText =
                    befehl.vorher()
                        ? text.substring(0, position) + block + "\n" + text.substring(position)
                        : text.substring(0, position) + "\n" + block + text.substring(position);
                // Vergibt der Block eine Bezeichnung, die ein leerer Platzhalter der Altfassung
                // noch hält („7. (aufgehoben)“), so weicht dieser: Zwei Einheiten gleicher
                // Bezeichnung kann es nicht geben, und Inhalt geht dabei nicht verloren. Es ist
                // dieselbe Regel, die schon bei der Umnummerierung auf eine weggefallene
                // Bezeichnung gilt, und so zeigt es die amtliche Nachfassung.
                for (var marke : platzhalterMarken(block, befehl.ebene())) {
                  neuerText = entferneWeggefallenenPlatzhalter(neuerText, marke);
                }
                return TextErgebnis.ok(neuerText);
              });
    };
  }

  /**
   * „Vor den Wörtern „Aus dem Bereich Verkehr:“ wird folgender Absatz 5 eingefügt: „…““ — die
   * Position der neuen Einheit bestimmt hier ein Wortanker, nicht die Struktur. Der Einfügeblock
   * tritt deshalb als eigene Zeile vor bzw. hinter die Zeile des Ankers; welche strukturelle Ebene
   * der Befehl nennt, ist dabei ohne Belang (in einer Anlage stehen Absätze und Nummern als Zeilen
   * eines Textes).
   */
  private static AngewandteAenderung wendeWortankerEinfuegungAn(
      List<Norm> normen, StrukturEinfuegung befehl, WortAnker anker) {
    return bearbeiteText(
        normen,
        befehl,
        ohneFussnoten(
            text -> {
              var woerter =
                  switch (anker) {
                    case WortAnker.NachWoertern nach -> nach.woerter();
                    case WortAnker.VorWoertern vor -> vor.woerter();
                    // „am Ende“ und „vor dem Komma am Ende“ sind keine Einfügeanker für ganze
                    // Einheiten: Wo eine Einheit ans Ende tritt, sagt das Gesetzblatt „angefügt“,
                    // und dafür gibt es die Anfügung. Die Grenze ist bewusst gezogen und nicht
                    // etwa eine offene Lücke — im gesamten Prüfbestand (elf Änderungsdokumente aus
                    // Bund und sieben Ländern) kommt die Verbindung kein einziges Mal vor.
                    case WortAnker.AmEnde ignoriert -> null;
                    case WortAnker.VorKommaAmEnde ignoriert -> null;
                  };
              if (woerter == null) {
                return TextErgebnis.fehler(
                    "Eine ganze Einheit wird nicht „am Ende“ eingefügt, sondern angefügt;"
                        + " so bezeichnet es auch das Gesetzblatt.",
                    Grund.NICHT_UNTERSTUETZT);
              }
              var pruefung = eindeutigeFundstelle(text, woerter);
              if (pruefung.fehler() != null) {
                return TextErgebnis.fehler(pruefung.fehler(), pruefung.grund());
              }
              int zeilenAnfang = text.lastIndexOf('\n', pruefung.index()) + 1;
              int zeilenEnde = text.indexOf('\n', pruefung.index());
              if (zeilenEnde < 0) {
                zeilenEnde = text.length();
              }
              var block =
                  rueckeZitatEin(
                      normalisiereZitatText(befehl.text()), einrueckungVon(text, zeilenAnfang));
              return TextErgebnis.ok(
                  befehl.vorher()
                      ? text.substring(0, zeilenAnfang)
                          + block
                          + "\n"
                          + text.substring(zeilenAnfang)
                      : text.substring(0, zeilenEnde) + "\n" + block + text.substring(zeilenEnde));
            }));
  }

  private static AngewandteAenderung wendeAnfuegungAn(List<Norm> normen, Anfuegung befehl) {
    return switch (befehl.ebene()) {
      case ABSATZ -> {
        var aufloesung = loeseNormAuf(normen, befehl.stelle());
        if (aufloesung.fehler() != null) {
          yield manuell(befehl, aufloesung.grund(), aufloesung.fehler());
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
              text -> {
                // Auch Blöcke mehrerer Einheiten („Die folgenden Nummern 9 bis 11 werden
                // angefügt“): jede Aufzählungszeile des Zitats bleibt eine eigene Zeile.
                var block =
                    normalisiereZitatText(befehl.text())
                        .lines()
                        .map(zeile -> "  " + zeile.strip())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                return TextErgebnis.ok(text.stripTrailing() + "\n" + block);
              });
      case PARAGRAPH -> haengeParagraphenAn(normen, befehl);
    };
  }

  /**
   * „Dem Gesetz werden die folgenden §§ … angefügt“ — ohne Anker. Die verankerte Form („Nach § 114
   * wird folgender § 115 angefügt“) ist eine Struktureinfügung und läuft dort; hier geht es um den
   * Fall, dass der Befehl nur das Ende bezeichnet.
   *
   * <p>Angehängt wird ans Ende des Gesetzes, bei benannter Gliederungseinheit ans Ende ihres
   * Blocks. Trägt das Zitat keinen Normkopf, bleibt der Befehl liegen — ein Wortlaut ohne
   * Bezeichnung ließe sich nur raten.
   */
  private static AngewandteAenderung haengeParagraphenAn(List<Norm> normen, Anfuegung befehl) {
    var pfad = befehl.stelle().gliederungsPfad();
    var gliederung = pfad.isEmpty() ? null : gliederungVon(normen, pfad);
    var neue = parseNormenBlock(befehl.text(), gliederung);
    if (neue.isEmpty()) {
      return manuell(
          befehl, Grund.ZITAT_UNBRAUCHBAR, "Im angefügten Block wurde kein Paragraph erkannt.");
    }
    for (var n : neue) {
      if (StellenAufloeser.normIndex(gesetzAus(normen), n.enbez()) >= 0) {
        return manuell(
            befehl, Grund.BESTAND_WIDERSPRICHT, n.enbez() + " existiert bereits im Stammgesetz.");
      }
    }
    normen.addAll(endeDesBlocks(normen, gliederung), neue);
    return angewandt(befehl, neue.stream().map(Norm::enbez).toList());
  }

  /** Die Gliederung, die der Pfad benennt; {@code null}, wenn keine Norm ihr angehört. */
  private static @Nullable Gliederung gliederungVon(
      List<Norm> normen, List<Stelle.Gliederungseinheit> pfad) {
    var bezeichnung = pfad.get(pfad.size() - 1).bezeichnung();
    for (var norm : normen) {
      var g = norm.gliederung();
      if (g != null && g.bezeichnung().equals(bezeichnung)) {
        return g;
      }
    }
    return null;
  }

  /** Hinter die letzte Norm der Gliederung — ohne Gliederung ans Ende des Gesetzes. */
  private static int endeDesBlocks(List<Norm> normen, @Nullable Gliederung gliederung) {
    if (gliederung == null) {
      return normen.size();
    }
    int ende = normen.size();
    for (int i = 0; i < normen.size(); i++) {
      if (gliederung.equals(normen.get(i).gliederung())) {
        ende = i + 1;
      }
    }
    return ende;
  }

  private static AngewandteAenderung wendeAufhebungAn(List<Norm> normen, Aufhebung befehl) {
    var stelle = befehl.stelle();

    if (stelle.absatzbezeichnung().isPresent()) {
      var aufloesung = loeseNormAuf(normen, stelle);
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
      }
      var norm = normen.get(aufloesung.normIndex());
      var nummer = stelle.absatzbezeichnung().get().nummer();
      var absaetze = new ArrayList<>(norm.absaetze());
      for (int i = 0; i < absaetze.size(); i++) {
        if (nummer.equals(absaetze.get(i).nummer())) {
          // Gestrichen wird die Bezeichnung, nicht der Absatz: „Die Absatzbezeichnung „(1)“ wird
          // gestrichen“ nimmt dem Wortlaut seine Nummer und lässt ihn im Übrigen unberührt. Das
          // unterscheidet diesen Befehl von der Aufhebung über einen Absatz-Lokator („Abs. 1 wird
          // aufgehoben“), die den Wortlaut beseitigt und einen nummerierten Platzhalter
          // zurücklässt. Beides zusammen — Streichung der Bezeichnung des einen, Aufhebung des
          // anderen Absatzes — führt eine zweigliedrige Vorschrift auf einen unnummerierten
          // Wortlaut zurück (so § 13 der hessischen Verkehrsrechts-Zuständigkeitsverordnung).
          absaetze.set(i, new Absatz(null, absaetze.get(i).text()));
          normen.set(aufloesung.normIndex(), norm.mitAbsaetzen(absaetze));
          return angewandt(befehl, norm.enbez());
        }
      }
      return manuell(
          befehl, Grund.STELLE_NICHT_AUFLOESBAR, "Absatz (" + nummer + ") nicht gefunden.");
    }

    if (nurNorm(stelle)) {
      var aufloesung = loeseNormAuf(normen, stelle);
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
      }
      var norm = normen.get(aufloesung.normIndex());
      if (norm.weggefallen()) {
        // Idempotent: Die Aufhebung einer bereits weggefallenen Norm ist bereits vollzogen.
        return angewandt(befehl, norm.enbez());
      }
      normen.set(aufloesung.normIndex(), norm.alsWeggefallen());
      return angewandt(befehl, norm.enbez());
    }

    if (stelle.absatz().isPresent() && feinsteIstAbsatz(stelle)) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), stelle);
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.grund(), nicht.begruendung());
      }
      var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
      var norm = normen.get(fundstelle.normIndex());
      var absaetze = new ArrayList<>(norm.absaetze());
      var alter = absaetze.get(fundstelle.absatzIndex());
      // Ein Platzhalter braucht eine Nummer, um einen Platz zu halten. Führt die Norm nach der
      // Aufhebung keine Absatzzählung mehr — weil ihr erster Absatz unnummeriert ist — und steht
      // der aufgehobene Absatz am Ende, so ist kein Platz zu halten: der Absatz entfällt ganz.
      boolean ohneZaehlung = !absaetze.isEmpty() && absaetze.get(0).nummer() == null;
      if (ohneZaehlung && fundstelle.absatzIndex() == absaetze.size() - 1) {
        absaetze.remove((int) fundstelle.absatzIndex());
      } else {
        absaetze.set(fundstelle.absatzIndex(), new Absatz(alter.nummer(), "(weggefallen)"));
      }
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }

    // Satz / Nummer / Buchstabe aufheben: Bereich entfernen bzw. als weggefallen markieren.
    return bearbeiteBereich(
        normen,
        befehl,
        (text, bereich) -> {
          var label = labelVon(stelle);
          if (label != null && haeltPlatz(text, bereich.bis(), label)) {
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
    // „§ 9a wird zu § 9.“ — Umbenennung einer ganzen Norm.
    if (nurParagraph(befehl.stelle()) && nurParagraph(befehl.neu())) {
      var enbezAlt = befehl.stelle().paragraph().get().enbez();
      var enbezNeu = befehl.neu().paragraph().get().enbez();
      int idx = StellenAufloeser.normIndex(gesetzAus(normen), enbezAlt);
      if (idx < 0) {
        return manuell(
            befehl, Grund.BESTAND_WIDERSPRICHT, enbezAlt + " existiert nicht im Gesetz.");
      }
      if (!enbezNeu.equals(enbezAlt)) {
        int zielIdx = StellenAufloeser.normIndex(gesetzAus(normen), enbezNeu);
        if (zielIdx >= 0 && !normen.get(zielIdx).weggefallen()) {
          return manuell(
              befehl, Grund.BESTAND_WIDERSPRICHT, enbezNeu + " existiert bereits im Gesetz.");
        }
        // Eine bereits weggefallene Zielnorm wird durch die Umnummerierung überschrieben.
        if (zielIdx >= 0) {
          normen.remove(zielIdx);
          if (zielIdx < idx) {
            idx--;
          }
        }
      }
      normen.set(idx, normen.get(idx).mitEnbez(enbezNeu));
      return angewandt(befehl, enbezNeu);
    }

    var altAbsatz = befehl.stelle().absatz();
    var neuAbsatz = befehl.neu().absatz();
    // Nur wenn der Absatz selbst das Umnummerierungsziel ist — bei „Satz 5 wird Satz 4“ stammt
    // eine etwaige Absatzangabe aus dem Kontextrahmen und der Satz-Zweig unten ist zuständig.
    if (altAbsatz.isPresent()
        && neuAbsatz.isPresent()
        && feinsteIstAbsatz(befehl.stelle())
        && feinsteIstAbsatz(befehl.neu())) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.grund(), nicht.begruendung());
      }
      var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
      var norm = normen.get(fundstelle.normIndex());
      var absaetze = new ArrayList<>(norm.absaetze());
      int quelleIdx = fundstelle.absatzIndex();
      var neueNummer = neuAbsatz.get().nummer();
      // Ein leerer Platzhalter-Absatz mit der Zielnummer (weggefallen/gegenstandslos) wird durch
      // die
      // Umnummerierung überschrieben (analog zur Norm-Umnummerierung auf eine weggefallene
      // Zielnorm).
      for (int i = absaetze.size() - 1; i >= 0; i--) {
        if (i != quelleIdx
            && neueNummer.equals(absaetze.get(i).nummer())
            && istLeererPlatzhalter(absaetze.get(i).text())) {
          absaetze.remove(i);
          if (i < quelleIdx) {
            quelleIdx--;
          }
        }
      }
      absaetze.set(quelleIdx, new Absatz(neueNummer, absaetze.get(quelleIdx).text()));
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }
    // Nummer-/Buchstaben-Umnummerierung: anders als ein Absatz trägt eine Aufzählungseinheit ihre
    // Bezeichnung als Marke im Absatztext („22. den Sozialverband …“); sie wird dort ausgetauscht.
    // Ein weggefallener Platzhalter mit der Zielmarke weicht dabei — genau wie bei der
    // Absatz-Umnummerierung, und wie es die amtliche Nachfassung zeigt.
    var alteMarke = aufzaehlungsMarke(befehl.stelle());
    var neueMarke = aufzaehlungsMarke(befehl.neu());
    if (alteMarke != null && neueMarke != null && !alteMarke.equals(neueMarke)) {
      return bearbeiteBereich(
          normen,
          befehl,
          (text, bereich) -> {
            var marke =
                Pattern.compile("^([ \\t]*)" + Pattern.quote(alteMarke))
                    .matcher(text.substring(bereich.von(), bereich.bis()));
            if (!marke.find()) {
              return TextErgebnis.fehler(
                  "„"
                      + alteMarke
                      + "“ steht nicht am Anfang von "
                      + befehl.stelle().anzeigeText()
                      + ".",
                  Grund.ZIELTEXT_FEHLT);
            }
            var umbenannt =
                text.substring(0, bereich.von())
                    + marke.group(1)
                    + neueMarke
                    + text.substring(bereich.von() + marke.end());
            return TextErgebnis.ok(entferneWeggefallenenPlatzhalter(umbenannt, neueMarke));
          });
    }

    // Satz-Umnummerierung: unnummerierte Sätze brauchen keine Textänderung; amtlich nummerierte
    // (bayerisches Landesrecht, „Satz 5 wird Satz 4.“) erhalten die neue Satznummer im Text.
    var altSatz = letzteKomponente(befehl.stelle(), Stelle.SatzNr.class);
    var neuSatz = letzteKomponente(befehl.neu(), Stelle.SatzNr.class);
    if (altSatz != null && neuSatz != null) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.grund(), nicht.begruendung());
      }
      var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
      if (fundstelle.bereich() != null && fundstelle.absatzIndex() != null) {
        var norm = normen.get(fundstelle.normIndex());
        var absaetze = new ArrayList<>(norm.absaetze());
        var absatz = absaetze.get(fundstelle.absatzIndex());
        var text = absatz.text();
        var marker =
            Superskript.LAUF
                .matcher(text)
                .region(fundstelle.bereich().von(), fundstelle.bereich().bis());
        if (marker.lookingAt() && Superskript.istSatzanfang(text, marker.start(), marker.end())) {
          var neuerText =
              text.substring(0, marker.start())
                  + Superskript.zuSuperskript(neuSatz.nummer())
                  + text.substring(marker.end());
          absaetze.set(fundstelle.absatzIndex(), absatz.mitText(neuerText));
          normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
          return angewandt(befehl, norm.enbez());
        }
      }
    }
    return angewandt(befehl, "(keine Textänderung nötig)");
  }

  /**
   * Die Fuge vor einem hinter einen Wortanker eingefügten Text: ein Leerzeichen, außer wenn der
   * Einschub mit einem Satzzeichen beginnt („nach der Angabe „§ 39 Absatz 5“ die Angabe „, bei der
   * Erstellung …““ ergibt „§ 39 Absatz 5, bei der …“, nicht „§ 39 Absatz 5 , bei der …“).
   */
  private static String fuge(String einschub) {
    return einschub.isEmpty() || ",;.:!?".indexOf(einschub.charAt(0)) < 0 ? " " : "";
  }

  /** Die Aufzählungsmarke der feinsten Komponente („Nummer 25“ → „25.“, „Buchstabe b“ → „b)“). */
  private static @Nullable String aufzaehlungsMarke(Stelle stelle) {
    var komponenten = stelle.komponenten();
    if (komponenten.isEmpty()) {
      return null;
    }
    return switch (komponenten.get(komponenten.size() - 1)) {
      case Stelle.NummerNr n -> n.nummer() + ".";
      case Stelle.BuchstabeNr b -> b.kennung() + ")";
      default -> null;
    };
  }

  private static final Pattern LEERER_PLATZHALTER =
      Pattern.compile("\\((?:weggefallen|gegenstandslos|aufgehoben)\\)");

  /**
   * Die Aufzählungsmarken, die ein eingefügter Block vergibt („5.“, „6.“, „7.“) — ohne solche, die
   * er selbst als leeren Platzhalter führt, denn ein Platzhalter verdrängt keinen Platzhalter.
   */
  private static List<String> platzhalterMarken(String block, Ebene ebene) {
    var muster =
        switch (ebene) {
          case NUMMER -> NUMMER_MARKER;
          case BUCHSTABE -> BUCHSTABE_MARKER;
          default -> null;
        };
    if (muster == null) {
      return List.of();
    }
    var marken = new ArrayList<String>();
    var marker = muster.matcher(block);
    while (marker.find()) {
      int zeilenEnde = block.indexOf('\n', marker.end());
      var rest = block.substring(marker.end(), zeilenEnde < 0 ? block.length() : zeilenEnde);
      if (LEERER_PLATZHALTER.matcher(rest.strip()).matches()) {
        continue;
      }
      marken.add(marker.group(1) + (ebene == Ebene.BUCHSTABE ? ")" : "."));
    }
    return marken;
  }

  /**
   * Entfernt die weggefallene Aufzählungszeile mit der gegebenen Marke — die soeben umbenannte
   * Zeile bleibt stehen, weil nur ein leerer Platzhalter getroffen wird.
   */
  private static String entferneWeggefallenenPlatzhalter(String text, String marke) {
    return text.replaceFirst(
        "(?m)^[ \\t]*"
            + Pattern.quote(marke)
            + "[ \\t]+\\((?:weggefallen|gegenstandslos|aufgehoben)\\)\\n?",
        "");
  }

  /** Ein leerer Platzhalter-Absatz ohne Inhalt („(weggefallen)“, „(gegenstandslos)“). */
  private static boolean istLeererPlatzhalter(String text) {
    var t = text.strip();
    return t.equals("(weggefallen)") || t.equals("(gegenstandslos)");
  }

  private static <K extends Stelle.Komponente> @Nullable K letzteKomponente(
      Stelle stelle, Class<K> art) {
    K letzte = null;
    for (var komponente : stelle.komponenten()) {
      if (art.isInstance(komponente)) {
        letzte = art.cast(komponente);
      }
    }
    return letzte;
  }

  private static AngewandteAenderung wendeWortlautZuAbsatzAn(
      List<Norm> normen, WortlautZuAbsatz befehl) {
    var aufloesung = loeseNormAuf(normen, befehl.stelle());
    if (aufloesung.fehler() != null) {
      return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
    }
    var norm = normen.get(aufloesung.normIndex());
    // Steht genau ein unnummerierter Absatz zwischen nummerierten (bayerische Folge „Dem Wortlaut
    // werden … Abs. 1 bis 4 vorangestellt“ → „Der bisherige Wortlaut wird Abs. 5“), erhält nur
    // dieser die Nummer. Sind alle Absätze unnummeriert, wird der Gesamtwortlaut zu einem Absatz.
    var unnummerierte = norm.absaetze().stream().filter(a -> a.nummer() == null).toList();
    if (unnummerierte.size() == 1 && norm.absaetze().size() > 1) {
      var absaetze = new ArrayList<>(norm.absaetze());
      int idx = absaetze.indexOf(unnummerierte.get(0));
      absaetze.set(idx, new Absatz(befehl.nummer(), unnummerierte.get(0).text()));
      normen.set(aufloesung.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }
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

  /** „Der Wortlaut wird Satz 1.“ — der Zieltext erhält die amtliche Satznummer als Superskript. */
  private static AngewandteAenderung wendeWortlautZuSatzAn(
      List<Norm> normen, WortlautZuSatz befehl) {
    var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
    if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.grund(), nicht.begruendung());
    }
    var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
    var norm = normen.get(fundstelle.normIndex());
    Integer absatzIndex = fundstelle.absatzIndex();
    if (absatzIndex == null) {
      if (norm.absaetze().size() != 1) {
        return manuell(
            befehl,
            Grund.MEHRDEUTIG,
            norm.enbez() + " hat " + norm.absaetze().size() + " Absätze; Ziel unklar.");
      }
      absatzIndex = 0;
    }
    var absaetze = new ArrayList<>(norm.absaetze());
    var absatz = absaetze.get(absatzIndex);
    var marke = Superskript.zuSuperskript(befehl.nummer());
    if (!absatz.text().startsWith(marke)) {
      absaetze.set(absatzIndex, absatz.mitText(marke + absatz.text()));
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
    }
    return angewandt(befehl, norm.enbez());
  }

  /** „Dem Wortlaut werden die folgenden Abs. 1 bis 4 vorangestellt: „…““ */
  private static AngewandteAenderung wendeWortlautVoranstellungAn(
      List<Norm> normen, WortlautVoranstellung befehl) {
    var aufloesung = loeseNormAuf(normen, befehl.stelle());
    if (aufloesung.fehler() != null) {
      return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
    }
    var norm = normen.get(aufloesung.normIndex());

    // „Dem Wortlaut des Absatzes 3 werden die folgenden Sätze vorangestellt“ — nennt der Befehl
    // einen Absatz, treten die neuen Sätze vor dessen Text, nicht vor die ganze Norm.
    if (befehl.stelle().absatz().isPresent()) {
      var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
      if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
        return manuell(befehl, nicht.grund(), nicht.begruendung());
      }
      var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
      var absaetze = new ArrayList<>(norm.absaetze());
      var absatz = absaetze.get(fundstelle.absatzIndex());
      absaetze.set(
          fundstelle.absatzIndex(),
          absatz.mitText(
              normalisiereZitatText(befehl.text()).strip() + " " + absatz.text().stripLeading()));
      normen.set(fundstelle.normIndex(), norm.mitAbsaetzen(absaetze));
      return angewandt(befehl, norm.enbez());
    }

    var neue = parseAbsaetze(befehl.text());
    if (neue.isEmpty()) {
      return manuell(befehl, Grund.ZITAT_UNBRAUCHBAR, "Im Zitat wurde kein Absatz erkannt.");
    }
    var absaetze = new ArrayList<>(neue);
    absaetze.addAll(norm.absaetze());
    normen.set(aufloesung.normIndex(), norm.mitAbsaetzen(absaetze));
    return angewandt(befehl, norm.enbez());
  }

  /** „Fußnote 1 wird aufgehoben.“ — entfernt Fußnotenzeile und Inline-Marker in der Kontextnorm. */
  private static AngewandteAenderung wendeFussnotenAufhebungAn(
      List<Norm> normen, FussnotenAufhebung befehl) {
    var aufloesung = loeseNormAuf(normen, befehl.stelle());
    if (aufloesung.fehler() != null) {
      return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
    }
    var norm = normen.get(aufloesung.normIndex());
    var absaetze = new ArrayList<>(norm.absaetze());
    var fehlend = new ArrayList<String>();
    for (var nummer : befehl.nummern()) {
      var marke = Superskript.zuSuperskript(nummer) + ")";
      var zeilenMuster =
          Pattern.compile("(?m)^" + Pattern.quote(marke) + "\\s*\\[Amtl\\. Anm\\.:\\].*(?:\n|$)");
      boolean gefunden = false;
      for (int i = 0; i < absaetze.size(); i++) {
        var text = absaetze.get(i).text();
        var ohneZeile = zeilenMuster.matcher(text).replaceAll("");
        var ohneMarker = ohneZeile.replace(marke, "");
        if (!ohneMarker.equals(text)) {
          absaetze.set(i, absaetze.get(i).mitText(ohneMarker.stripTrailing()));
          gefunden = true;
        }
      }
      if (!gefunden) {
        fehlend.add(nummer);
      }
    }
    if (!fehlend.isEmpty()) {
      return manuell(
          befehl,
          Grund.ZIELTEXT_FEHLT,
          "Fußnote " + String.join(", ", fehlend) + " kommt in " + norm.enbez() + " nicht vor.");
    }
    normen.set(aufloesung.normIndex(), norm.mitAbsaetzen(absaetze));
    return angewandt(befehl, norm.enbez());
  }

  /** „In Satz 1 wird die Satznummerierung „1“ gestrichen.“ */
  private static AngewandteAenderung wendeSatznummerierungStreichungAn(
      List<Norm> normen, SatznummerierungStreichung befehl) {
    return bearbeiteBereich(
        normen,
        befehl,
        (text, bereich) -> {
          var marke = Superskript.zuSuperskript(befehl.nummer());
          if (!text.startsWith(marke, bereich.von())) {
            return TextErgebnis.fehler(
                "Die Satznummerierung „" + befehl.nummer() + "“ steht nicht am Anfang des Ziels.",
                Grund.ZIELTEXT_FEHLT);
          }
          return TextErgebnis.ok(
              text.substring(0, bereich.von()) + text.substring(bereich.von() + marke.length()));
        });
  }

  /**
   * Ein Mehrfachziel-Befehl („In A und B wird jeweils …“): wendet jeden Teilbefehl nacheinander an
   * (jeder mutiert den fortlaufenden Zwischenstand) und fasst sie zu einem Protokolleintrag
   * zusammen. Nur wenn alle Teile gelingen, gilt der Befehl als angewandt; sonst wird er zur
   * manuellen Prüfung markiert (bereits angewandte Teile bleiben wirksam).
   */
  /**
   * Ein Verbund, der erst hier auftaucht (in einem anderen Verbund geschachtelt), wird für sich
   * genommen aufgefaltet und geordnet. Der Regelfall — der Verbund als eigener Gliederungspunkt —
   * ist dagegen schon in {@link #anwenden} aufgefaltet und läuft nie hier durch.
   */
  private static AngewandteAenderung wendeSammelAn(
      List<Norm> normen, List<Gliederung> gliederungen, Sammelbefehl befehl) {
    var schritte = new ArrayList<Schritt>(befehl.teilbefehle().size());
    falte(befehl, 0, schritte);
    var ergebnisse = new AngewandteAenderung[schritte.size()];
    for (int index : anwendungsReihenfolge(schritte)) {
      ergebnisse[index] = wendeAn(normen, gliederungen, schritte.get(index).teil());
    }
    return verbund(befehl, Arrays.asList(ergebnisse), schritte, 0);
  }

  // --- Gemeinsame Helfer ---------------------------------------------------------------------

  private record TextErgebnis(
      @Nullable String text, @Nullable String fehler, @Nullable Grund grund) {
    static TextErgebnis ok(String text) {
      return new TextErgebnis(text, null, null);
    }

    static TextErgebnis fehler(String begruendung, Grund grund) {
      return new TextErgebnis(null, begruendung, grund);
    }
  }

  private interface TextOperation {
    TextErgebnis wende(String text);
  }

  // Fußnoten-Definitionszeile am Zeilenanfang („⁶) [Amtl. Anm.:] …“), die dem tragenden Absatz
  // anhängt. Für wortweise Operationen (Streichen/Ersetzen/Einfügen) ist sie kein Inhalt: der
  // eingebettete Marker „Gesetzbuchs²)“ soll getroffen werden, der gleichlautende Definitionskopf
  // „²)“ nicht. Fußnoten werden daher vor der Operation abgetrennt und danach unverändert
  // wieder angehängt (ihre eigene Aufhebung läuft über FussnotenAufhebung).
  private static final Pattern FUSSNOTEN_DEFINITION = Pattern.compile("(?m)^[⁰¹²³⁴⁵⁶⁷⁸⁹]+\\)");

  private static TextOperation ohneFussnoten(TextOperation operation) {
    return text -> {
      var m = FUSSNOTEN_DEFINITION.matcher(text);
      if (!m.find()) {
        return operation.wende(text);
      }
      int grenze = m.start();
      var ergebnis = operation.wende(text.substring(0, grenze));
      if (ergebnis.fehler() != null) {
        return ergebnis;
      }
      return TextErgebnis.ok(ergebnis.text() + text.substring(grenze));
    };
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
    // „In der Überschrift …“: die Operation wirkt auf den Titel der Norm, nicht auf ihren Text.
    if (befehl.stelle().betrifftUeberschrift()) {
      var aufloesung = loeseNormAuf(normen, befehl.stelle());
      if (aufloesung.fehler() != null) {
        return manuell(befehl, aufloesung.grund(), aufloesung.fehler());
      }
      var norm = normen.get(aufloesung.normIndex());
      if (norm.titel() == null) {
        return manuell(
            befehl, Grund.BESTAND_WIDERSPRICHT, norm.enbez() + " hat keine Überschrift.");
      }
      var titelErgebnis = operation.wende(norm.titel());
      if (titelErgebnis.fehler() != null) {
        return manuell(befehl, titelErgebnis.grund(), titelErgebnis.fehler());
      }
      normen.set(aufloesung.normIndex(), norm.mitTitel(titelErgebnis.text()));
      return angewandt(befehl, norm.enbez());
    }

    var ergebnis = StellenAufloeser.aufloese(gesetzAus(normen), befehl.stelle());
    if (ergebnis instanceof StellenAufloeser.Ergebnis.NichtGefunden nicht) {
      return manuell(befehl, nicht.grund(), nicht.begruendung());
    }
    var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
    var norm = normen.get(fundstelle.normIndex());

    if (fundstelle.absatzIndex() != null) {
      var absaetze = new ArrayList<>(norm.absaetze());
      var absatz = absaetze.get(fundstelle.absatzIndex());
      var neuerText = wendeAufBereichAn(absatz.text(), fundstelle.bereich(), operation);
      if (neuerText.fehler() != null) {
        return manuell(befehl, neuerText.grund(), neuerText.fehler());
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
    Grund letzterGrund = Grund.BESTAND_WIDERSPRICHT;
    for (int i = 0; i < absaetze.size(); i++) {
      var versuch = operation.wende(absaetze.get(i).text());
      if (versuch.fehler() == null) {
        if (trefferIndex != null) {
          return manuell(
              befehl,
              Grund.MEHRDEUTIG,
              "Mehrere Absätze von " + norm.enbez() + " kommen infrage; mehrdeutig.");
        }
        trefferIndex = i;
        treffer = versuch;
      } else {
        letzterFehler = versuch.fehler();
        letzterGrund = versuch.grund();
      }
    }
    if (trefferIndex == null) {
      return manuell(befehl, letzterGrund, letzterFehler);
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
      return manuell(befehl, nicht.grund(), nicht.begruendung());
    }
    var fundstelle = ((StellenAufloeser.Ergebnis.Gefunden) ergebnis).fundstelle();
    if (fundstelle.absatzIndex() == null || fundstelle.bereich() == null) {
      return manuell(
          befehl,
          Grund.STELLE_NICHT_AUFLOESBAR,
          "„" + befehl.stelle().anzeigeText() + "“ bezeichnet keinen konkreten Textbereich.");
    }
    var norm = normen.get(fundstelle.normIndex());
    var absaetze = new ArrayList<>(norm.absaetze());
    var absatz = absaetze.get(fundstelle.absatzIndex());
    var neuerText = operation.wende(absatz.text(), fundstelle.bereich());
    if (neuerText.fehler() != null) {
      return manuell(befehl, neuerText.grund(), neuerText.fehler());
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

  private record NormAufloesung(int normIndex, @Nullable String fehler, @Nullable Grund grund) {}

  private static NormAufloesung loeseNormAuf(List<Norm> normen, Stelle stelle) {
    String enbez;
    if (stelle.paragraph().isPresent()) {
      enbez = stelle.paragraph().get().enbez();
    } else if (stelle.anlagenEnbez().isPresent()) {
      enbez = stelle.anlagenEnbez().get();
    } else {
      return new NormAufloesung(
          -1,
          "Stelle nennt keinen Paragraphen: " + stelle.anzeigeText(),
          Grund.STELLE_NICHT_AUFLOESBAR);
    }
    int index = StellenAufloeser.normIndex(gesetzAus(normen), enbez);
    if (index < 0) {
      return new NormAufloesung(
          -1, enbez + " existiert nicht im Gesetz.", Grund.BESTAND_WIDERSPRICHT);
    }
    return new NormAufloesung(index, null, null);
  }

  private static boolean nurParagraph(Stelle stelle) {
    return stelle.komponenten().size() == 1
        && stelle.komponenten().get(0) instanceof Stelle.Paragraph;
  }

  /** Wahr, wenn die Stelle als Ganzes eine Norm meint: ein einzelner § oder ein Anhang/Anlage. */
  private static boolean nurNorm(Stelle stelle) {
    return stelle.komponenten().size() == 1
        && (stelle.komponenten().get(0) instanceof Stelle.Paragraph
            || stelle.anlagenEnbez().isPresent());
  }

  private static boolean feinsteIstAbsatz(Stelle stelle) {
    return stelle.komponenten().stream()
        .noneMatch(
            k ->
                k instanceof Stelle.SatzNr
                    || k instanceof Stelle.HalbsatzNr
                    || k instanceof Stelle.NummerNr
                    || k instanceof Stelle.BuchstabeNr);
  }

  /**
   * Wahr, wenn die feinste Komponente der Stelle eine Aufzählungseinheit ist (Nummer/Buchstabe).
   */
  private static boolean feinsteIstAufzaehlung(Stelle stelle) {
    var komponenten = stelle.komponenten();
    if (komponenten.isEmpty()) {
      return false;
    }
    var feinste = komponenten.get(komponenten.size() - 1);
    return feinste instanceof Stelle.NummerNr || feinste instanceof Stelle.BuchstabeNr;
  }

  /**
   * Heilt den Weißraum an der Stelle, an der eine Streichung Text herausgenommen hat: Aus zwei
   * Leerzeichen wird eines, vor einem Satzzeichen bleibt keines. Geheilt wird ausdrücklich nur die
   * Naht und nicht der ganze Zieltext — dieser ist bei einer Stelle ohne Fein-Komponente der
   * gesamte Absatz, und eine Heilung über ihn hinweg fräße die Einrückung der Aufzählungszeilen:
   * Aus den zwei Leerzeichen vor jeder Marke würde eines. Traf die Streichung den Zeilenanfang, so
   * ist der Weißraum vor der Naht Einrückung und bleibt unangetastet.
   */
  private static String heileNaht(String text, int naht) {
    int von = naht;
    while (von > 0 && text.charAt(von - 1) == ' ') {
      von--;
    }
    int bis = naht;
    while (bis < text.length() && text.charAt(bis) == ' ') {
      bis++;
    }
    if (bis == von) {
      return text;
    }
    if (von == 0 || text.charAt(von - 1) == '\n') {
      return text.substring(0, naht) + text.substring(bis);
    }
    if (bis < text.length() && ",;.".indexOf(text.charAt(bis)) >= 0) {
      return text.substring(0, von) + text.substring(bis);
    }
    if (bis - von == 1) {
      return text;
    }
    return text.substring(0, von) + " " + text.substring(bis);
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

  /**
   * Hält die aufgehobene Aufzählungseinheit einen Platz? Ein Platzhalter „9. (weggefallen)“ ist nur
   * dort sinnvoll, wo ihm eine weitere Einheit derselben Art folgt, deren Bezeichnung er unberührt
   * lassen soll. Steht die aufgehobene Einheit am Ende der Aufzählung — auch dann, wenn ihr nur
   * weitere Platzhalter folgen —, so ist kein Platz zu halten und sie entfällt ganz. So endet § 14
   * der hessischen Verkehrsrechts-Zuständigkeitsverordnung nach Aufhebung der Nrn. 9 bis 11 mit der
   * Nr. 6, wie es auch die amtliche Nachfassung tut.
   */
  private static boolean haeltPlatz(String text, int ab, String label) {
    var art =
        label.endsWith(")")
            ? Pattern.compile("^\\s*[a-zA-Z]{1,2}\\)\\s+(.*)$")
            : Pattern.compile("^\\s*\\d+[a-z]?\\.\\s+(.*)$");
    return text.substring(ab)
        .lines()
        .map(art::matcher)
        .filter(java.util.regex.Matcher::matches)
        .anyMatch(m -> !m.group(1).strip().equals("(weggefallen)"));
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

  private record Fundpruefung(int index, @Nullable String fehler, @Nullable Grund grund) {}

  private static Fundpruefung eindeutigeFundstelle(String text, String woerter) {
    int anzahl = zaehleVorkommen(text, woerter);
    if (anzahl == 0) {
      return new Fundpruefung(
          -1, "„" + woerter + "“ kommt im Zieltext nicht vor.", Grund.ZIELTEXT_FEHLT);
    }
    if (anzahl > 1) {
      return new Fundpruefung(
          -1, "„" + woerter + "“ kommt " + anzahl + "-mal vor; mehrdeutig.", Grund.MEHRDEUTIG);
    }
    return new Fundpruefung(findeVorkommen(text, woerter, 0), null, null);
  }

  private static int zaehleVorkommen(String text, String suchtext) {
    if (suchtext.isEmpty()) {
      return 0;
    }
    int anzahl = 0;
    int index = 0;
    while ((index = findeVorkommen(text, suchtext, index)) >= 0) {
      anzahl++;
      index += suchtext.length();
    }
    return anzahl;
  }

  /**
   * Das nächste Vorkommen des Suchtextes ab {@code von} — als <em>Wort</em>, nicht als beliebige
   * Zeichenfolge. Ein Befehl, der „das Wort ‚schwerwiegende‘“ nennt, meint dieses Wort und nicht
   * die ersten dreizehn Buchstaben von „schwerwiegender“; ohne diese Grenze ersetzte die
   * Doppelanweisung des § 36 IfSG ihr zweites Wort mit, um es danach nicht mehr zu finden. Die
   * Grenze wird nur dort verlangt, wo der Suchtext selbst mit einem Buchstaben oder einer Ziffer
   * anfängt bzw. endet: Ein Satzzeichen, eine Klammer oder ein Bindestrich am Rand bringt seine
   * Grenze schon mit.
   */
  private static int findeVorkommen(String text, String suchtext, int von) {
    if (suchtext.isEmpty()) {
      return -1;
    }
    for (int index = text.indexOf(suchtext, von);
        index >= 0;
        index = text.indexOf(suchtext, index + 1)) {
      if (stehtAlsWort(text, index, suchtext)) {
        return index;
      }
    }
    return -1;
  }

  private static boolean stehtAlsWort(String text, int index, String suchtext) {
    int ende = index + suchtext.length();
    if (istWortzeichen(suchtext.charAt(0)) && index > 0 && istWortzeichen(text.charAt(index - 1))) {
      return false;
    }
    return !istWortzeichen(suchtext.charAt(suchtext.length() - 1))
        || ende >= text.length()
        || !istWortzeichen(text.charAt(ende));
  }

  private static boolean istWortzeichen(char c) {
    return Character.isLetterOrDigit(c);
  }

  // Eine §-Überschrift beginnt mit „§ N“, gefolgt von einem großgeschriebenen Titelwort — im
  // Gegensatz zu Querverweisen wie „§ 71 Absatz 1“ oder „§§ 42 bis 45“. Die Negativliste schließt
  // die Untergliederungs- und Verbindungswörter aus, sodass an solchen Stellen nicht getrennt wird.
  private static final Pattern PARAGRAPH_UEBERSCHRIFT =
      Pattern.compile(
          "(?=(?:§|Art\\.)\\s*\\d+[a-z]?\\s+"
              + "(?!Absatz|Absätze|Abs|Satz|Sätze|Nummer|Nummern|Nr|Buchstabe|Buchstaben|Buchst"
              + "|und|bis|oder|sowie|des|der|dieses|genannten)"
              + "\\p{Lu})");

  /**
   * Zerlegt einen Zitatblock mehrerer Paragraphen an den §-Überschriften (nicht an Querverweisen)
   * und parst jeden Abschnitt zu einer {@link Norm}. Die Gliederung wird von der Vorlage
   * übernommen.
   */
  private static List<Norm> parseNormenBlock(String block, @Nullable Gliederung gliederung) {
    var normen = new ArrayList<Norm>();
    for (var stueck : PARAGRAPH_UEBERSCHRIFT.split(block.strip())) {
      var s = stueck.strip();
      var m = Pattern.compile("^(§|Art\\.)\\s*(\\d+[a-z]?)\\b").matcher(s);
      if (s.isEmpty() || !m.find()) {
        continue;
      }
      var enbez = m.group(1) + " " + m.group(2);
      normen.add(parseNorm(s, enbez, new Norm(enbez, null, gliederung, List.of(), false)));
    }
    return normen;
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
        titel = kopf.replaceFirst("^(?:§|Art\\.)\\s*\\S+\\s*", "").replaceAll("\\s+", " ").strip();
        if (titel.isEmpty()) {
          titel = vorlage.titel();
        }
      }
      return new Norm(
          enbez, titel, vorlage.gliederung(), parseAbsaetze(text.substring(erster)), false);
    }

    // Ohne Absatzmarker (Einzelabsatz-Normen wie „§ 19 Außerkrafttreten Dieses Gesetz tritt …“):
    // Steht „§ N“ allein auf der ersten Zeile, ist die folgende Zeile die Überschrift und der Rest
    // der Normtext. Die Überschrift darf dabei über mehrere Zeilen laufen — der Satz bricht sie am
    // Spaltenrand um („Zahlungen an den örtlichen Träger“ / „der öffentlichen Jugendhilfe“).
    // Fortgesetzt wird sie nur über klein beginnende Zeilen: Ein Normtext beginnt großgeschrieben
    // oder mit einem Marker.
    var zeilen = text.lines().map(String::strip).filter(z -> !z.isEmpty()).toList();
    if (zeilen.size() >= 3 && zeilen.get(0).matches("(?:§|Art\\.)\\s*\\d+[a-z]?")) {
      int nachTitel = titelEnde(zeilen, 1);
      titel = String.join(" ", zeilen.subList(1, nachTitel));
      var rest = String.join("\n", zeilen.subList(nachTitel, zeilen.size()));
      return new Norm(
          enbez,
          titel,
          vorlage.gliederung(),
          List.of(new Absatz(null, normalisiereZitatText(rest))),
          false);
    }
    // Dieselbe Norm, deren Überschrift der Satz an den Kopf gezogen hat („§ 1 Zahlungen an die
    // Wohnsitzgemeinde“ in einer Zeile). Überschrift ist der Zeilenrest nur dann, wenn er keinen
    // Satz abschließt — sonst trägt die Zeile bereits den Normtext.
    var kopfZeile =
        zeilen.isEmpty()
            ? null
            : Pattern.compile("^(?:§|Art\\.)\\s*\\d+[a-z]?\\s+(\\S.*)$").matcher(zeilen.get(0));
    if (zeilen.size() >= 2
        && kopfZeile != null
        && kopfZeile.matches()
        && !kopfZeile.group(1).endsWith(".")) {
      var kopf = new ArrayList<String>();
      kopf.add(kopfZeile.group(1).strip());
      // Die Überschrift steht hier schon in Zeile 0; fortgesetzt werden kann sie ab Zeile 1.
      int nachTitel = titelEnde(zeilen, 0);
      kopf.addAll(zeilen.subList(1, nachTitel));
      var rest = String.join("\n", zeilen.subList(nachTitel, zeilen.size()));
      if (!rest.isBlank()) {
        return new Norm(
            enbez,
            String.join(" ", kopf),
            vorlage.gliederung(),
            List.of(new Absatz(null, normalisiereZitatText(rest))),
            false);
      }
    }
    // Fallback: gesamter Text (ohne „§ N“-Präfix) als unnummerierter Absatz, Titel unverändert.
    var inhalt = text.replaceFirst("^(?:§|Art\\.)\\s*\\S+\\s*", "");
    return new Norm(
        enbez,
        titel,
        vorlage.gliederung(),
        List.of(new Absatz(null, normalisiereZitatText(inhalt))),
        false);
  }

  /**
   * Der Index hinter der Überschrift: Ab {@code von} laufen so lange Überschriftenzeilen, wie sie
   * klein beginnen — ein umbrochener Titel setzt klein fort, ein Normtext beginnt groß oder mit
   * einem Marker.
   */
  private static int titelEnde(List<String> zeilen, int von) {
    int i = von + 1;
    while (i < zeilen.size()
        && !zeilen.get(i).isEmpty()
        && Character.isLowerCase(zeilen.get(i).codePointAt(0))) {
      i++;
    }
    return i;
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

  /**
   * Zitattext in die kanonische Zeilenform bringen: Leerzeilen entfallen, Zeilen werden gestutzt,
   * Aufzählungspunkte eingerückt. Zeilenumbrüche bleiben erhalten — der TextBereiniger hat weiche
   * (Blocksatz-)Umbrüche bereits zu Fließtext zusammengezogen, verbleibende Umbrüche sind also
   * beabsichtigt (z.B. die Kurzüberschrift über einer hängend eingerückten Definition im
   * UWG-Anhang) und müssen dieselbe Form erhalten wie beim Flatten des Stammgesetz-XML.
   */
  /** Eine Zeile, die mit einem Aufzählungsmarker beginnt („3. “, „d) “). */
  private static final Pattern AUFZAEHLUNGSZEILE =
      Pattern.compile("^(\\d+[a-z]?\\.|[a-z]{1,3}\\))\\s.*");

  // Der Zwischenraum zwischen der Aufzählungsmarke und ihrem Text: im Gesetzblattsatz steht dort
  // die Marken-Tabulatorspalte, im kanonischen Klartext ein einzelnes Leerzeichen.
  private static final Pattern MARKEN_ZWISCHENRAUM =
      Pattern.compile("^(\\d+[a-z]?\\.|[a-z]{1,3}\\))[ \\t]+");

  private static String normalisiereZitatText(String text) {
    var zeilen = text.split("\n");
    var sb = new StringBuilder();
    // Fortsetzungszeilen innerhalb eines Aufzählungspunkts (z.B. der Definitionstext unter einer
    // Kurzüberschrift) werden tiefer eingerückt als die Aufzählungszeile — dieselbe Form, die der
    // ContentFlattener aus dem Stammgesetz-XML erzeugt, und Voraussetzung dafür, dass die
    // Stellenauflösung sie als Kindzeilen der Einheit erkennt.
    var fortsetzungsEinzug = "";
    for (var zeile : zeilen) {
      var gestutzt = MARKEN_ZWISCHENRAUM.matcher(zeile.strip()).replaceFirst("$1 ");
      if (gestutzt.isEmpty()) {
        continue;
      }
      if (sb.length() == 0) {
        sb.append(gestutzt);
        if (AUFZAEHLUNGSZEILE.matcher(gestutzt).matches()) {
          fortsetzungsEinzug = "  ";
        }
      } else if (AUFZAEHLUNGSZEILE.matcher(gestutzt).matches()) {
        // Aufzählungspunkt: eigene Zeile mit Einzug.
        sb.append("\n  ").append(gestutzt);
        fortsetzungsEinzug = "    ";
      } else {
        sb.append('\n').append(fortsetzungsEinzug).append(gestutzt);
      }
    }
    return sb.toString();
  }

  /**
   * Rückt die kanonischen Zitatzeilen auf die Ziel-Einrückung um: Aufzählungszeilen (und die erste
   * Zeile) auf {@code einrueckung}, Fortsetzungszeilen — etwa der Definitionstext unter einer
   * Kurzüberschrift — zwei Zeichen tiefer, damit sie Kindzeilen der Einheit bleiben (dieselbe Form,
   * die der ContentFlattener aus dem Stammgesetz-XML erzeugt).
   */
  private static String rueckeZitatEin(String zitat, String einrueckung) {
    var sb = new StringBuilder();
    boolean erste = true;
    for (var zeile : zitat.split("\n", -1)) {
      var inhalt = zeile.strip();
      if (!erste) {
        sb.append('\n');
      }
      sb.append(einrueckung);
      if (!erste && !AUFZAEHLUNGSZEILE.matcher(inhalt).matches()) {
        sb.append("  ");
      }
      sb.append(inhalt);
      erste = false;
    }
    return sb.toString();
  }

  private static AngewandteAenderung angewandt(Aenderungsbefehl befehl, String enbez) {
    return new AngewandteAenderung(
        befehl, Status.ANGEWANDT, "", new LinkedHashSet<>(List.of(enbez)), null);
  }

  private static AngewandteAenderung angewandt(Aenderungsbefehl befehl, List<String> enbezliste) {
    return new AngewandteAenderung(
        befehl, Status.ANGEWANDT, "", new LinkedHashSet<>(enbezliste), null);
  }

  /**
   * Der Befehl bleibt liegen. Die Art des Grundes darf durchgereicht sein ({@link NormAufloesung},
   * {@link TextErgebnis}, {@link StellenAufloeser.Ergebnis.NichtGefunden} tragen sie mit ihrem
   * Wortlaut, weil nur die erzeugende Stelle sie kennt); fehlt sie, gilt die unauffindbare Stelle.
   */
  private static AngewandteAenderung manuell(
      Aenderungsbefehl befehl, @Nullable Grund grund, String begruendung) {
    return new AngewandteAenderung(
        befehl,
        Status.MANUELL_PRUEFEN,
        begruendung,
        Set.of(),
        grund == null ? Grund.STELLE_NICHT_AUFLOESBAR : grund);
  }

  private static Gesetz gesetzAus(List<Norm> normen) {
    return new Gesetz("", null, null, normen);
  }
}
