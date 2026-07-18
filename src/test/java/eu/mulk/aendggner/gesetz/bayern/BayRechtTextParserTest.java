package eu.mulk.aendggner.gesetz.bayern;

import static org.assertj.core.api.Assertions.assertThat;

import eu.mulk.aendggner.gesetz.Gliederung;
import org.junit.jupiter.api.Test;

class BayRechtTextParserTest {

  private static final String BEISPIEL =
      """
      BayJG: Bayerisches Jagdgesetz (BayJG) Vom 13. Oktober 1978 (BayRS V S. 595) BayRS 792-1-W (Art.
      1–64)
      Bayerisches Jagdgesetz
      (BayJG)
      Vom 13. Oktober 1978
      (BayRS V S. 595)
      BayRS 792-1-W
      Vollzitat nach RedR: Bayerisches Jagdgesetz (BayJG) in der in der Bayerischen Rechtssammlung (BayRS
      792-1-L) veröffentlichten bereinigten Fassung
      I. Abschnitt Grundsätze
      Art. 1  Gesetzeszweck

      (1) ¹Die freilebende Tierwelt ist wesentlicher Bestandteil der heimischen Natur. ²Sie ist zu bewahren.
      (2) Dieses Gesetz soll dazu dienen:
      1.  einen artenreichen Wildbestand zu erhalten,

      2.  die Lebensgrundlagen des Wildes zu sichern.

      II. Abschnitt Jagdreviere, Hegegemeinschaften
      1. Allgemeine Vorschriften
      Art. 3  Feststellung der Jagdreviere

      Bestand, Umfang und Grenzen eines Jagdreviers werden durch die
      Jagdbehörde festgestellt.
      2. Jagdreviere
      Art. 8  Eigenjagdreviere

      (1) ¹Die Mindestgröße beträgt 81,755 ha. ²Die Art. 4 Abs. 3, Art. 5 Abs. 2 und
      Art. 11 Abs. 6 sind entsprechend anzuwenden.
      Art. 59  Enteignende Maßnahmen

      (1) Es ist nach den Vorschriften des Gesetzes über die entschädigungspflichtige Enteignung⁶) Entschädigung zu leisten.
      6) [Amtl. Anm.:] BayRS 2141-1-I
      Art. 60  (aufgehoben)

      Art. 63 (Änderungsbestimmung)

      Art. 64  Inkrafttreten; Aufhebung von Vorschriften

      (1) Dieses Gesetz tritt am 1. Januar 1979 in Kraft.
      """;

  @Test
  void parstTitelblock() {
    var gesetz = BayRechtTextParser.parse(BEISPIEL);
    assertThat(gesetz.jurabk()).isEqualTo("BayJG");
    assertThat(gesetz.langue()).isEqualTo("Bayerisches Jagdgesetz");
    assertThat(gesetz.kurzue()).isNull();
  }

  @Test
  void parstNormenMitTitelnUndAbsaetzen() {
    var gesetz = BayRechtTextParser.parse(BEISPIEL);
    assertThat(gesetz.normen())
        .extracting(n -> n.enbez())
        .containsExactly("Art. 1", "Art. 3", "Art. 8", "Art. 59", "Art. 60", "Art. 63", "Art. 64");

    var art1 = gesetz.norm("Art. 1").orElseThrow();
    assertThat(art1.titel()).isEqualTo("Gesetzeszweck");
    assertThat(art1.absaetze()).hasSize(2);
    assertThat(art1.absaetze().get(0).nummer()).isEqualTo("1");
    assertThat(art1.absaetze().get(0).text()).startsWith("¹Die freilebende Tierwelt");
    assertThat(art1.absaetze().get(1).text()).contains("1.  einen artenreichen");

    // Unnummerierter Einzelabsatz.
    var art3 = gesetz.norm("Art. 3").orElseThrow();
    assertThat(art3.absaetze()).hasSize(1);
    assertThat(art3.absaetze().get(0).nummer()).isNull();
  }

  @Test
  void querverweiseAmZeilenanfangEroeffnenKeineNorm() {
    // „Art. 11 Abs. 6 …“ am Zeilenanfang in Art. 8 ist ein Querverweis, kein Normkopf.
    var gesetz = BayRechtTextParser.parse(BEISPIEL);
    var art8 = gesetz.norm("Art. 8").orElseThrow();
    assertThat(art8.absaetze().get(0).text()).contains("Art. 11 Abs. 6 sind entsprechend");
    assertThat(gesetz.norm("Art. 11")).isEmpty();
  }

  @Test
  void parstGliederungenMitUnterUeberschriften() {
    var gesetz = BayRechtTextParser.parse(BEISPIEL);
    assertThat(gesetz.gliederungen())
        .extracting(Gliederung::anzeigeText)
        .containsExactly(
            "I. Abschnitt — Grundsätze",
            "II. Abschnitt — Jagdreviere, Hegegemeinschaften",
            "1. — Allgemeine Vorschriften",
            "2. — Jagdreviere");
    // Kennzahlen sind eindeutig und hängen an der Eltern-Gliederung.
    assertThat(gesetz.gliederungen())
        .extracting(Gliederung::kennzahl)
        .containsExactly("001", "002", "002.1", "002.2");
    assertThat(gesetz.norm("Art. 8").orElseThrow().gliederung().kennzahl()).isEqualTo("002.2");
  }

  @Test
  void erkenntWeggefalleneNormenUndFussnoten() {
    var gesetz = BayRechtTextParser.parse(BEISPIEL);
    assertThat(gesetz.norm("Art. 60").orElseThrow().weggefallen()).isTrue();
    assertThat(gesetz.norm("Art. 63").orElseThrow().weggefallen()).isFalse();

    // Fußnotenzeile bleibt superskript-normalisiert im tragenden Absatz.
    var art59 = gesetz.norm("Art. 59").orElseThrow();
    assertThat(art59.absaetze().get(0).text())
        .contains("Enteignung⁶)")
        .contains("⁶) [Amtl. Anm.:] BayRS 2141-1-I");
  }
}
