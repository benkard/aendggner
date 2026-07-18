package eu.mulk.aendggner.aenderung;

import org.jspecify.annotations.Nullable;

/**
 * Ein einzelner Änderungsbefehl eines Änderungsgesetzes, wie er im Handbuch der Rechtsförmlichkeit
 * vorgesehen ist. Nicht erkannte Befehle landen als {@link UnbekannterBefehl} im Ergebnis — sie
 * werden nie stillschweigend verworfen.
 */
public sealed interface Aenderungsbefehl {

  /** Die betroffene Stelle im Stammgesetz. */
  Stelle stelle();

  /** Herkunft des Befehls im Änderungsgesetz. */
  Provenienz provenienz();

  /** Strukturelle Ebene eines eingefügten oder angefügten Elements. */
  enum Ebene {
    PARAGRAPH,
    ABSATZ,
    SATZ,
    NUMMER,
    BUCHSTABE
  }

  /** Anker für Wörter-Einfügungen innerhalb eines Textbereichs. */
  sealed interface WortAnker {
    record NachWoertern(String woerter) implements WortAnker {}

    record VorWoertern(String woerter) implements WortAnker {}

    record AmEnde() implements WortAnker {}

    record VorKommaAmEnde() implements WortAnker {}
  }

  /** „… werden die Wörter „A“ durch die Wörter „B“ ersetzt.“ */
  record Ersetzung(
      Stelle stelle, String alt, String neu, boolean jeweils, boolean amEnde, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /** „§ X wird wie folgt gefasst: „…““ */
  record Neufassung(Stelle stelle, String neuerText, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /**
   * „§ 2 Absatz 2 wird durch die folgenden Absätze 2 und 3 ersetzt: „…““ — ein Ziel wird durch
   * einen Block ersetzt, der auch mehrere neue Einheiten enthalten darf.
   *
   * @param stelle das (erste) zu ersetzende Ziel.
   * @param bisStelle bei einem zusammenhängenden Bereich („Die Absätze 8 und 9 …“, „Die Sätze 4 bis
   *     5 …“) das letzte Ziel; {@code null} bei einem Einzelziel.
   */
  record StrukturErsetzung(
      Stelle stelle, @Nullable Stelle bisStelle, Ebene ebene, String text, Provenienz provenienz)
      implements Aenderungsbefehl {

    /** Einzelziel-Konstruktor (kein Bereich). */
    public StrukturErsetzung(Stelle stelle, Ebene ebene, String text, Provenienz provenienz) {
      this(stelle, null, ebene, text, provenienz);
    }
  }

  /** „… werden nach dem Wort „A“ die Wörter „B“ eingefügt.“ */
  record WoerterEinfuegung(Stelle stelle, WortAnker anker, String woerter, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /**
   * „Nach § 28 wird folgender § 28a eingefügt: „…““
   *
   * @param stelle die Ankerstelle, relativ zu der eingefügt wird.
   * @param vorher {@code true} bei „Vor …“, {@code false} bei „Nach …“.
   * @param bezeichnung die Bezeichnung des neuen Elements (z.B. „28a“, „5a“); {@code null}, wenn
   *     der Befehl keine nennt (z.B. „folgender Satz“).
   */
  record StrukturEinfuegung(
      Stelle stelle,
      boolean vorher,
      Ebene ebene,
      @Nullable String bezeichnung,
      String text,
      Provenienz provenienz)
      implements Aenderungsbefehl {}

  /** „Folgender Absatz 9 wird angefügt: „…““ / „Dem Absatz 3 wird folgender Satz angefügt: …“ */
  record Anfuegung(
      Stelle stelle, Ebene ebene, @Nullable String bezeichnung, String text, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /** „§ X / Absatz Y wird aufgehoben.“ */
  record Aufhebung(Stelle stelle, Provenienz provenienz) implements Aenderungsbefehl {}

  /** „… werden die Wörter „A“ gestrichen.“ */
  record Streichung(Stelle stelle, String woerter, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /** „Der bisherige Absatz 2 wird Absatz 3.“ */
  record Umnummerierung(Stelle stelle, Stelle neu, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /**
   * „Der Wortlaut wird Absatz 1.“ — der bisher unnummerierte Normtext erhält die Absatznummer, wird
   * also zum ersten Absatz (Vorbereitung für das Anfügen weiterer Absätze).
   */
  record WortlautZuAbsatz(Stelle stelle, String nummer, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /**
   * „Der Wortlaut wird Satz 1.“ (bayerisches Landesrecht) — der bisher unnummerierte Text erhält
   * die amtliche Satznummer als Superskript (Vorbereitung für das Anfügen weiterer Sätze).
   */
  record WortlautZuSatz(Stelle stelle, String nummer, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /**
   * „Dem Wortlaut werden die folgenden Abs. 1 bis 4 vorangestellt: „…““ (bayerisches Landesrecht) —
   * neue Absätze treten vor den bisherigen Normtext.
   */
  record WortlautVoranstellung(Stelle stelle, String text, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /**
   * „Fußnote 1 wird aufgehoben.“ / „Die Fußnoten 9 und 10 werden aufgehoben.“ (bayerisches
   * Landesrecht) — entfernt die Fußnotenzeile(n) „ⁿ) [Amtl. Anm.:] …“ samt der Inline-Marker „ⁿ)“
   * aus der Kontextnorm.
   */
  record FussnotenAufhebung(Stelle stelle, java.util.List<String> nummern, Provenienz provenienz)
      implements Aenderungsbefehl {

    public FussnotenAufhebung {
      nummern = java.util.List.copyOf(nummern);
    }
  }

  /**
   * „In Satz 1 wird die Satznummerierung „1“ gestrichen.“ (bayerisches Landesrecht) — entfernt die
   * amtliche Superskript-Satznummer am Anfang des bezeichneten Bereichs (etwa nachdem der zweite
   * und letzte Satz eines Absatzes aufgehoben wurde).
   */
  record SatznummerierungStreichung(Stelle stelle, String nummer, Provenienz provenienz)
      implements Aenderungsbefehl {}

  /**
   * „Nach § 33 werden die folgenden Überschriften zu Teil 3 und zu Teil 3 Abschnitt 1 eingefügt:
   * „…““ bzw. „Die bisherigen Überschriften zu Teil 4 und Teil 4 Abschnitt 1 werden durch die
   * folgende Überschrift zu Abschnitt 2 ersetzt: „…““ — neue Gliederungs-Überschriften im
   * Gliederungsbaum.
   *
   * @param stelle der Anker-§ („Nach § 33“); leer bei der Ersetzungsform.
   * @param neue die neuen Einheiten in Zitatreihenfolge (Titel stehen im Zitat).
   * @param ersetzte die zu ersetzenden bisherigen Einheiten (leer bei der Einfügeform); jede als
   *     Pfad („Teil 4 Abschnitt 1“ → [Teil 4, Abschnitt 1]).
   * @param text das Zitat mit den Überschriften.
   */
  record GliederungsUeberschriften(
      Stelle stelle,
      java.util.List<Stelle.Gliederungseinheit> neue,
      java.util.List<java.util.List<Stelle.Gliederungseinheit>> ersetzte,
      String text,
      Provenienz provenienz)
      implements Aenderungsbefehl {

    public GliederungsUeberschriften {
      neue = java.util.List.copyOf(neue);
      ersetzte = java.util.List.copyOf(ersetzte);
    }
  }

  /**
   * „In A und B wird jeweils …“ — ein Befehl, der dieselbe Operation auf mehrere, per „und“
   * koordinierte Stellen anwendet. Die Teilbefehle teilen sich Provenienz und Befehlszeile; der
   * Applier fasst sie zu einem Protokolleintrag zusammen.
   */
  record Sammelbefehl(java.util.List<Aenderungsbefehl> teilbefehle) implements Aenderungsbefehl {

    public Sammelbefehl {
      teilbefehle = java.util.List.copyOf(teilbefehle);
    }

    @Override
    public Stelle stelle() {
      return teilbefehle.get(0).stelle();
    }

    @Override
    public Provenienz provenienz() {
      return teilbefehle.get(0).provenienz();
    }
  }

  /** Fallback für alles, was der Parser nicht versteht — muss manuell geprüft werden. */
  record UnbekannterBefehl(Stelle stelle, String originalText, Provenienz provenienz)
      implements Aenderungsbefehl {}
}
