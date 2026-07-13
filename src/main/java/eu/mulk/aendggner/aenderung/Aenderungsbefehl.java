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
   */
  record StrukturErsetzung(Stelle stelle, Ebene ebene, String text, Provenienz provenienz)
      implements Aenderungsbefehl {}

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
