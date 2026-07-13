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
      permits Paragraph, AbsatzNr, SatzNr, NummerNr, BuchstabeNr, Ueberschrift, Inhaltsuebersicht {}

  /** „§ 5“, „§ 28a“ — die Paragraphennummer ohne „§ “. */
  public record Paragraph(String nummer) implements Komponente {}

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
          });
    }
    return sb.toString();
  }
}
