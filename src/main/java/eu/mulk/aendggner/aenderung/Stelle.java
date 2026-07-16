package eu.mulk.aendggner.aenderung;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Eine Fundstelle im Stammgesetz, als Stapel immer feinerer Komponenten (z.B. „§ 5 Absatz 2 Satz
 * 1“). Verschachtelte Änderungsbefehle konkatenieren ihre Stellen mit {@link #plus}.
 */
public record Stelle(List<Komponente> komponenten) {

  public static final Stelle LEER = new Stelle(List.of());

  public Stelle {
    komponenten = List.copyOf(komponenten);
  }

  public sealed interface Komponente
      permits Paragraph,
          AbsatzNr,
          SatzNr,
          NummerNr,
          BuchstabeNr,
          Ueberschrift,
          Inhaltsuebersicht,
          Gliederungseinheit,
          Absatzbezeichnung {}

  /** „§ 5“, „§ 28a“ — die Paragraphennummer ohne „§ “. */
  public record Paragraph(String nummer) implements Komponente {}

  /** Eine Gliederungseinheit oberhalb des Paragraphen: „Teil 2“, „Abschnitt 3“, „Anlage 8“. */
  public record Gliederungseinheit(String art, String nummer) implements Komponente {
    public String bezeichnung() {
      return nummer.isEmpty() ? art : art + " " + nummer;
    }
  }

  /** „Die Absatzbezeichnung „(2)““ — die reine Absatznummer als Ziel einer Streichung. */
  public record Absatzbezeichnung(String nummer) implements Komponente {}

  /** „Absatz 2“ */
  public record AbsatzNr(String nummer) implements Komponente {}

  /** „Satz 1“ */
  public record SatzNr(String nummer) implements Komponente {}

  /** „Nummer 4“ */
  public record NummerNr(String nummer) implements Komponente {}

  /** „Buchstabe c“ (auch Doppelbuchstabe „aa“) */
  public record BuchstabeNr(String kennung) implements Komponente {}

  /** „Die Überschrift“ */
  public record Ueberschrift() implements Komponente {}

  /** „In der Inhaltsübersicht“ */
  public record Inhaltsuebersicht() implements Komponente {}

  public boolean istLeer() {
    return komponenten.isEmpty();
  }

  public Stelle plus(Stelle feiner) {
    if (feiner.istLeer()) {
      return this;
    }
    var neu = new ArrayList<>(komponenten);
    neu.addAll(feiner.komponenten);
    return new Stelle(neu);
  }

  public Optional<Paragraph> paragraph() {
    return komponenten.stream()
        .filter(Paragraph.class::isInstance)
        .map(Paragraph.class::cast)
        .findFirst();
  }

  public Optional<AbsatzNr> absatz() {
    return komponenten.stream()
        .filter(AbsatzNr.class::isInstance)
        .map(AbsatzNr.class::cast)
        .findFirst();
  }

  public boolean betrifftInhaltsuebersicht() {
    return komponenten.stream().anyMatch(Inhaltsuebersicht.class::isInstance);
  }

  public boolean betrifftUeberschrift() {
    return komponenten.stream().anyMatch(Ueberschrift.class::isInstance);
  }

  public List<Gliederungseinheit> gliederungsPfad() {
    return komponenten.stream()
        .filter(Gliederungseinheit.class::isInstance)
        .map(Gliederungseinheit.class::cast)
        .toList();
  }

  public boolean betrifftGliederung() {
    return komponenten.stream().anyMatch(Gliederungseinheit.class::isInstance);
  }

  /**
   * Wahr, wenn die Stelle eine Gliederungseinheit des Überschriften-Gerüsts (Teil, Abschnitt, …)
   * nennt. Anhänge und Anlagen zählen nicht dazu: die sind im gii-XML eigene Normen mit Text und
   * werden wie Paragraphen behandelt (siehe {@link #anlagenEnbez()}).
   */
  public boolean betrifftEchteGliederung() {
    return komponenten.stream()
        .filter(Gliederungseinheit.class::isInstance)
        .map(Gliederungseinheit.class::cast)
        .anyMatch(g -> !istAnlagenArt(g.art()));
  }

  /** Die enbez der adressierten Anhang-/Anlagen-Norm („Anhang“, „Anlage 2“), falls vorhanden. */
  public Optional<String> anlagenEnbez() {
    return komponenten.stream()
        .filter(Gliederungseinheit.class::isInstance)
        .map(Gliederungseinheit.class::cast)
        .filter(g -> istAnlagenArt(g.art()))
        .findFirst()
        .map(Gliederungseinheit::bezeichnung);
  }

  private static boolean istAnlagenArt(String art) {
    return art.equals("Anhang") || art.equals("Anlage");
  }

  public Optional<Absatzbezeichnung> absatzbezeichnung() {
    return komponenten.stream()
        .filter(Absatzbezeichnung.class::isInstance)
        .map(Absatzbezeichnung.class::cast)
        .findFirst();
  }

  public String anzeigeText() {
    if (komponenten.isEmpty()) {
      return "(gesamtes Gesetz)";
    }
    var sb = new StringBuilder();
    for (var komponente : komponenten) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(
          switch (komponente) {
            case Paragraph p -> "§ " + p.nummer();
            case AbsatzNr a -> "Absatz " + a.nummer();
            case SatzNr s -> "Satz " + s.nummer();
            case NummerNr n -> "Nummer " + n.nummer();
            case BuchstabeNr b -> "Buchstabe " + b.kennung();
            case Ueberschrift u -> "Überschrift";
            case Inhaltsuebersicht i -> "Inhaltsübersicht";
            case Gliederungseinheit g -> g.bezeichnung();
            case Absatzbezeichnung a -> "Absatzbezeichnung (" + a.nummer() + ")";
          });
    }
    return sb.toString();
  }
}
