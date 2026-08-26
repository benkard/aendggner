// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.aenderung.parse;

import eu.mulk.aendggner.aenderung.parse.FontgroessenFilter.Zeile;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Bringt die Zeilen einer Seite in die Reihenfolge, in der ein Mensch sie liest.
 *
 * <p>Die Extraktion folgt sonst dem Inhaltsstrom, und das trägt weit: Gesetzblätter zeichnen ihre
 * Spalten in aller Regel nacheinander, während eine bloße Sortierung nach der Höhe sie
 * verschränkte. Es trägt aber nicht überall. Das Berliner Gesetz- und Verordnungsblatt zeichnet den
 * ganzseitenbreiten Titelblock des Gesetzes <em>zuletzt</em> — nach beiden Spalten, obgleich er
 * über ihnen steht. Im Inhaltsstrom steht er damit mitten in einem Zitat, das über den
 * Seitenwechsel läuft: „… zur Sicherung des Be-“, Titelblock, „triebs von Unterkünften …“. Der
 * Wortlaut der Anlage trug den Titel des Änderungsgesetzes in sich.
 *
 * <p>Maßgeblich ist deshalb das Satzbild, und zwar in der einfachen Gestalt, die die Gesetzblätter
 * durchweg haben: <b>zwei Spalten, dazwischen eine Rinne, und darüber oder dazwischen einzelne
 * ganzseitenbreite Zeilen</b> — Kolumnentitel, Seitenfuß, Titelblock. Gesucht wird die Rinne: die
 * senkrechte Linie, die möglichst wenige Zeilen überschreiten und zu deren beiden Seiten je eine
 * Spalte steht. Die wenigen Zeilen, die sie doch überschreiten, sind die ganzseitenbreiten; sie
 * zerlegen die Seite in Bänder. Gelesen wird Band für Band von oben nach unten, in jedem Band erst
 * die linke, dann die rechte Spalte, und die breite Zeile an ihrem Platz dazwischen.
 *
 * <p>Wo sich keine solche Rinne findet — einspaltiger Satz, Titelseiten —, bleibt es beim
 * Inhaltsstrom. Ebenso <b>innerhalb</b> einer Spalte: Der Eingriff versetzt Spalten und breite
 * Zeilen gegeneinander, bringt aber niemals den Satz einer Spalte durcheinander, deren Strom schon
 * stimmte. Eine Seite, von der auch nur eine Zeile ohne Geometrie kommt, bleibt unangetastet.
 *
 * <p>Der einfache XY-Schnitt der Literatur — erst waagerecht am weitesten Weißraumband teilen, dann
 * senkrecht — leistet das <em>nicht</em>: Auf einer zweispaltigen Seite mit Kolumnentitel liegt das
 * weiteste Band regelmäßig mitten im Satzspiegel, und die Seite zerfiele in oben und unten, ehe sie
 * in links und rechts zerfällt. Gelesen würde alsdann links oben, rechts oben, links unten, rechts
 * unten — im Infektionsschutzgesetz zerriss das eine Aufzählung mitten entzwei.
 */
final class Lesereihenfolge {

  /** Mindestbreite (pt) der Rinne zwischen den Spalten. */
  private static final float RINNE_MIN_PT = 12f;

  /** Zeilen, die die Rinne überschreiten dürfen, mindestens (Kolumnentitel, Titelblock, Fuß). */
  private static final int BREITE_ZEILEN_MIN = 4;

  /** … und höchstens, als Anteil der Zeilen der Seite. */
  private static final double BREITE_ZEILEN_ANTEIL = 0.1;

  /** Mindestzahl der Zeilen je Spalte. */
  private static final int SPALTEN_ZEILEN_MIN = 3;

  /** … und ihr Mindestanteil an den Zeilen der Seite: Zwei Streuzeilen bilden keine Spalte. */
  private static final double SPALTEN_ZEILEN_ANTEIL = 0.2;

  /**
   * Zulässiger Abstand der Rinne von der Mitte des Satzspiegels, als Anteil seiner Breite. Ein
   * Gesetzblatt teilt seine Seite in der Mitte; was weit daneben liegt, ist keine Spaltenrinne,
   * sondern der Zwischenraum einer Tabelle.
   */
  private static final double MITTE_ABWEICHUNG = 0.1;

  /**
   * Mindestbreite jeder Spalte, als Anteil des Satzspiegels. Die Zellen einer Tabelle sind
   * schmäler; die beiden Spalten eines Gesetzblatts messen je knapp die Hälfte.
   */
  private static final double SPALTEN_BREITE_ANTEIL = 0.3;

  private Lesereihenfolge() {}

  /**
   * Ordnet die Zeilen seitenweise. Die Seiten selbst bleiben in ihrer Folge; nur was innerhalb
   * einer Seite steht, wird umgestellt.
   */
  static List<Zeile> ordne(List<Zeile> zeilen) {
    var ergebnis = new ArrayList<Zeile>(zeilen.size());
    int i = 0;
    while (i < zeilen.size()) {
      int j = i;
      int seite = zeilen.get(i).seite();
      while (j < zeilen.size() && zeilen.get(j).seite() == seite) {
        j++;
      }
      var seitenZeilen = zeilen.subList(i, j);
      ergebnis.addAll(vermessbar(seitenZeilen) ? ordneSeite(seitenZeilen) : seitenZeilen);
      i = j;
    }
    return ergebnis;
  }

  /** Ohne Seitenangabe oder ohne vollständige Koordinaten ist nichts zu ordnen. */
  private static boolean vermessbar(List<Zeile> zeilen) {
    if (zeilen.isEmpty() || zeilen.get(0).seite() <= 0) {
      return false;
    }
    for (var zeile : zeilen) {
      if (Float.isNaN(zeile.grundlinie())
          || Float.isNaN(zeile.startX())
          || Float.isNaN(zeile.endX())
          || zeile.endX() < zeile.startX()) {
        return false;
      }
    }
    return true;
  }

  private static List<Zeile> ordneSeite(List<Zeile> seite) {
    if (seite.size() < 2 * SPALTEN_ZEILEN_MIN) {
      return seite;
    }
    var rinne = findeRinne(seite);
    if (rinne == null) {
      return seite;
    }

    var breite = new ArrayList<Zeile>();
    var links = new ArrayList<Zeile>();
    var rechts = new ArrayList<Zeile>();
    for (var zeile : seite) {
      if (zeile.endX() <= rinne.x()) {
        links.add(zeile);
      } else if (zeile.startX() >= rinne.x()) {
        rechts.add(zeile);
      } else {
        breite.add(zeile);
      }
    }
    breite.sort((a, b) -> Float.compare(a.grundlinie(), b.grundlinie()));

    var ergebnis = new ArrayList<Zeile>(seite.size());
    float oben = Float.NEGATIVE_INFINITY;
    for (var trenner : breite) {
      bandAusgeben(ergebnis, links, rechts, oben, trenner.grundlinie());
      ergebnis.add(trenner);
      oben = trenner.grundlinie();
    }
    bandAusgeben(ergebnis, links, rechts, oben, Float.POSITIVE_INFINITY);
    return ergebnis;
  }

  /**
   * Ein Band zwischen zwei breiten Zeilen: erst die linke Spalte, dann die rechte, jede von oben
   * nach unten.
   *
   * <p>Die Spalte folgt ihrer Grundlinie und nicht dem Inhaltsstrom. Wo der Strom ohnehin von oben
   * nach unten läuft — der Regelfall —, ändert das nichts; die Sortierung ist stabil, gleich hohe
   * Zeilen behalten ihre Folge. Wo er es nicht tut, bewahrt sie vor dem Schlimmsten: Im
   * hamburgischen Gesetzblatt fällt das Schlusswort der Eingangsformel („verordnet:“) mitten in
   * einen Absatz der rechten Spalte und zerschneidet dort ein Wort.
   */
  private static void bandAusgeben(
      List<Zeile> ergebnis, List<Zeile> links, List<Zeile> rechts, float oben, float unten) {
    for (var spalte : List.of(links, rechts)) {
      var band = new ArrayList<Zeile>();
      for (var zeile : spalte) {
        if (zeile.grundlinie() > oben && zeile.grundlinie() < unten) {
          band.add(zeile);
        }
      }
      band.sort((a, b) -> Float.compare(a.grundlinie(), b.grundlinie()));
      ergebnis.addAll(band);
    }
  }

  /**
   * Die Rinne einer Seite.
   *
   * @param x die Linie in der Rinne: Zeilen links davon enden vor ihr, Zeilen rechts beginnen
   *     hinter ihr, und was beides nicht tut, ist eine breite Zeile.
   * @param breite der Abstand zwischen dem rechten Rand der linken und dem linken Rand der rechten
   *     Spalte (pt).
   */
  private record Rinne(float x, float breite) {}

  /**
   * Sucht die weiteste senkrechte Lücke, die von höchstens einer Handvoll Zeilen überschritten wird
   * und zu deren beiden Seiten eine Spalte steht. Findet sich keine, so ist die Seite nicht
   * zweispaltig gesetzt — dann bleibt es beim Inhaltsstrom, also beim bisherigen Ergebnis.
   */
  private static @Nullable Rinne findeRinne(List<Zeile> seite) {
    int hoechstensBreit =
        Math.max(BREITE_ZEILEN_MIN, (int) Math.round(BREITE_ZEILEN_ANTEIL * seite.size()));
    int mindestensSpalte =
        Math.max(SPALTEN_ZEILEN_MIN, (int) Math.round(SPALTEN_ZEILEN_ANTEIL * seite.size()));

    // Der Satzspiegel, gemessen an den Zeilen selbst: Die Seitenbreite steht hier nicht zur
    // Verfügung, und der bedruckte Bereich ist ohnehin das treffendere Maß.
    float satzLinks = Float.POSITIVE_INFINITY;
    float satzRechts = Float.NEGATIVE_INFINITY;
    for (var zeile : seite) {
      satzLinks = Math.min(satzLinks, zeile.startX());
      satzRechts = Math.max(satzRechts, zeile.endX());
    }
    float satzBreite = satzRechts - satzLinks;
    if (satzBreite <= 0) {
      return null;
    }

    Rinne beste = null;
    // Als Prüflinien genügen die rechten Zeilenenden: Zwischen zwei benachbarten Enden ändert
    // sich keine Zuordnung.
    for (var kandidat : seite) {
      float x = kandidat.endX();
      if (Math.abs(x - (satzLinks + satzRechts) / 2) > MITTE_ABWEICHUNG * satzBreite) {
        continue;
      }
      int breit = 0;
      int linksZahl = 0;
      int rechtsZahl = 0;
      float linkerRand = Float.NEGATIVE_INFINITY;
      float rechterRand = Float.POSITIVE_INFINITY;
      for (var zeile : seite) {
        if (zeile.endX() <= x) {
          linksZahl++;
          linkerRand = Math.max(linkerRand, zeile.endX());
        } else if (zeile.startX() >= x) {
          rechtsZahl++;
          rechterRand = Math.min(rechterRand, zeile.startX());
        } else if (++breit > hoechstensBreit) {
          break;
        }
      }
      if (breit > hoechstensBreit
          || linksZahl < mindestensSpalte
          || rechtsZahl < mindestensSpalte) {
        continue;
      }
      float weite = rechterRand - linkerRand;
      if (weite < RINNE_MIN_PT
          || linkerRand - satzLinks < SPALTEN_BREITE_ANTEIL * satzBreite
          || satzRechts - rechterRand < SPALTEN_BREITE_ANTEIL * satzBreite) {
        continue;
      }
      if (beste == null || weite > beste.breite()) {
        beste = new Rinne((linkerRand + rechterRand) / 2, weite);
      }
    }
    return beste;
  }
}
