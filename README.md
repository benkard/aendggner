<!--
SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# ÄndGgner — Nichtamtliche Zentralstelle für die maschinelle Fortschreibung von Stammgesetzen anhand von Änderungsvorschriften des Bundes und der Länder

Matthias Andreas Benkard

Ein Änderungsgesetz sagt nicht, was künftig gilt. Es sagt nur, was zu tun ist:
„In § 4 Absatz 2 Satz 1 werden die Wörter „X“ durch die Wörter „Y“ ersetzt.“
Wer wissen will, wie die Vorschrift danach lautet, muss Hunderte solcher Befehle
von Hand nachvollziehen — und wer wissen will, was ein Entwurf bewirken _würde_,
findet in keinem amtlichen Angebot eine Antwort, denn konsolidiert wird erst nach
der Verkündung.

ÄndGgner nimmt diese Arbeit ab. Eingegeben werden ein Stammgesetz in geltender
Fassung und ein oder mehrere Änderungsdokumente — verkündete Änderungsgesetze aus
dem Bundesgesetzblatt und den Gesetz- und Verordnungsblättern der Länder ebenso
wie Referenten-, Regierungs- und Fraktionsentwürfe, Änderungsanträge und
Beschlussempfehlungen. Ausgegeben wird eine zweispaltige Synopse: links die
bisherige, rechts die künftige Fassung, die Unterschiede wortweise hervorgehoben.

```shell
./mvnw package
java -jar target/aendggner-0.1.0-SNAPSHOT.jar stammgesetz.xml aenderungsgesetz.pdf -o synopse.html
```

Ohne Einrichtung läuft dasselbe im Browser unter
<https://aendggner.app.kellertomaten.de/>; die dort angebotenen zwei
durchgerechneten Beispiele sind der kürzeste Weg zum ersten Ergebnis
(§ 14).

## Inhalt

- [§ 1 Gegenstand und Zweck](#-1-gegenstand-und-zweck)
- [§ 2 Begriffsbestimmungen](#-2-begriffsbestimmungen)
- [§ 3 Bezugsquellen und Anschriften](#-3-bezugsquellen-und-anschriften)
- [§ 4 Herstellung des Erzeugnisses](#-4-herstellung-des-erzeugnisses)
- [§ 5 Zulässige Eingaben](#-5-zulässige-eingaben)
- [§ 6 Betrieb der Befehlszeilenfassung](#-6-betrieb-der-befehlszeilenfassung)
- [§ 7 Erkannte Änderungsbefehle](#-7-erkannte-änderungsbefehle)
- [§ 8 Reihenfolge der Anwendung](#-8-reihenfolge-der-anwendung)
- [§ 9 Aufbereitung der Druckwerke](#-9-aufbereitung-der-druckwerke)
- [§ 10 Quellformate: Gesetz, Entwurf, Antrag](#-10-quellformate-gesetz-entwurf-antrag)
- [§ 11 Änderungsanträge](#-11-änderungsanträge)
- [§ 12 Beschlussempfehlungen; die beschlossene Fassung](#-12-beschlussempfehlungen-die-beschlossene-fassung)
- [§ 13 Landesrecht](#-13-landesrecht)
- [§ 14 Browserfassung](#-14-browserfassung)
- [§ 15 Ausrollen](#-15-ausrollen)
- [§ 16 Aufbau des Quelltextes](#-16-aufbau-des-quelltextes)
- [§ 17 Prüfung](#-17-prüfung)
- [Lizenz](#lizenz)

## § 1 Gegenstand und Zweck

(1) Dieses Handbuch regelt Herstellung, Betrieb und Anwendung des
Softwareerzeugnisses „ÄndGgner“ (nachstehend: das Erzeugnis).

(2) Das Erzeugnis dient der maschinellen Fortschreibung von Stammgesetzen anhand
von Änderungsvorschriften des Bundes und der Länder. Es erschließt aus einem
Änderungsdokument die darin enthaltenen Änderungsbefehle, wendet sie auf die
geltende Fassung des Stammgesetzes an und stellt beide Fassungen einander
gegenüber.

(3) Das Erzeugnis ist nichtamtlich. Seine Ausgabe ist weder eine amtliche
Bekanntmachung noch eine Rechtsauskunft; maßgeblich bleibt allein der Wortlaut
des Gesetz- und Verordnungsblattes. Bei Entwürfen, Anträgen und
Beschlussempfehlungen zeigt die Synopse überdies nur, was gälte, wenn die Vorlage
so beschlossen würde; sie trägt hierauf den Hinweis **Entwurfsfassung — nicht
geltendes Recht**.

(4) Es gilt der Grundsatz der Nichtverwerfung: Ein Befehl, der sich nicht
zweifelsfrei anwenden lässt — etwa weil er gegen eine ältere Gesetzesfassung
gerichtet ist, deren Zieltext nicht mehr besteht —, wird mitsamt der Begründung
seines Scheiterns in den Abschnitt **Manuell prüfen** der Synopse aufgenommen. Er
wird niemals stillschweigend übergangen.

## § 2 Begriffsbestimmungen

Im Sinne dieses Handbuchs ist

**Stammgesetz**
: das zu ändernde Gesetz in seiner geltenden (konsolidierten) Fassung, also
  dasjenige Werk, dessen Fortschreibung begehrt wird. Bundesrecht wird als
  gii-Norm-XML von [gesetze-im-internet.de](https://www.gesetze-im-internet.de/)
  angenommen, Landesrecht als konsolidierte Fassung aus dem jeweiligen
  Landesportal (§ 5).

**Änderungsdokument**
: das Werk, das die Änderung anordnet. Hierzu zählen das verkündete
  Änderungsgesetz, der Gesetzentwurf, der Änderungsantrag und die
  Beschlussempfehlung (§ 10).

**Änderungsbefehl**
: die einzelne Anordnung innerhalb des Änderungsdokuments, gerichtet auf eine
  bestimmte Stelle des Stammgesetzes („In § 4 Absatz 2 Satz 1 werden die Wörter
  „X“ durch die Wörter „Y“ ersetzt“). Welche Befehlsformen erkannt werden,
  bestimmt § 7.

**Norm**
: die kleinste selbständig bezeichnete Einheit des Stammgesetzes, im Bundesrecht
  und im Landesrecht der übrigen Länder der Paragraph, im bayerischen Landesrecht
  der Artikel (§ 13). Auch die Inhaltsübersicht und die Gesetzesüberschrift
  werden als Normen geführt, weil Änderungsbefehle auf sie zielen.

**Synopse**
: die Ausgabe des Erzeugnisses: eine zweispaltige Gegenüberstellung der bisherigen
  und der künftigen Fassung im Format HTML, gefolgt vom Protokoll der Anwendung
  und dem Abschnitt **Manuell prüfen** nach § 1 Absatz 4.

**Angewandt**
: gilt ein Befehl erst, wenn er im Wortlaut des Stammgesetzes gegriffen hat. Bei
  Verbünden mehrerer Teile (§ 8 Absatz 3) gilt der Befehl nur dann als angewandt,
  wenn jeder seiner Teile gegriffen hat.

## § 3 Bezugsquellen und Anschriften

(1) Die Browserfassung wird betrieben unter
<https://aendggner.app.kellertomaten.de/>.

(2) Fortlaufende Quelltextquelle ist
<https://git.benkard.de/mulk/aendggner>. Die jeweils betriebene
Fassung liegt der Browserfassung überdies als `aendggner-quelltext.tar.gz` bei
(§ 15 Absatz 5).

(3) Das Erzeugnis steht unter der GNU Affero General Public License, Fassung 3;
der Lizenztext ist der Datei `COPYING` zu entnehmen.

(4) Der Stand des Quelltextes wird nach Kalendertagen im Verzeichnis der
Fassungen (`FASSUNGEN.txt`) geführt.

## § 4 Herstellung des Erzeugnisses

(1) Die ausführbare Archivdatei wird durch den nachstehenden Befehl unter
`target/aendggner-${REVISION}.jar` erzeugt:

```shell
./mvnw package
```

(2) Die Fassungsbezeichnung `${REVISION}` nach Absatz 1 lautet
`0.1.0-SNAPSHOT`, soweit sie nicht durch Übergabe des Schalters `-Drevision` an
`mvnw` abweichend bestimmt wird.

(3) Vorausgesetzt wird ein Java-Entwicklungsbausatz der Fassung 21 oder neuer.
Für die Herstellung der Browserfassung gilt abweichend § 14 Absatz 3.

## § 5 Zulässige Eingaben

(1) Als Stammgesetz des Bundes wird XML im gii-norm-Format von
[gesetze-im-internet.de](https://www.gesetze-im-internet.de/) angenommen. Das
Portal gibt seine Werke nur als Archiv aus (`…/<kurz>/xml.zip`); das Archiv wird
unmittelbar angenommen und der enthaltene XML-Eintrag daraus entpackt, ein
Zwischenschritt von Hand entfällt.

(2) Als Stammgesetz eines Landes wird die konsolidierte Fassung als PDF oder als
kanonischer Klartext im Format der `--extract-only`-Ausgabe (§ 6 Absatz 2)
angenommen. Hierbei gilt:

1. Eine Zeile „Inhaltsübersicht“ eröffnet die gleichnamige Norm, auf die die
   Angabe-Befehle zielen; ihre Zeilen tragen das Übersichtsformat
   „§ N | Titel“.
2. Amtliche Satznummern und Fußnotenmarker stehen als Unicode-Superskripte im
   Text („¹Die freilebende Tierwelt …“, „Enteignung⁶)“). Ob sie erhalten bleiben,
   ergibt sich aus der geladenen Stammfassung, nicht aus einer Länderkennung.
3. Das Zitiersigel (§ oder Art.) folgt gleichfalls aus den Normköpfen der
   Stammfassung.

(3) Als Änderungsdokument werden PDF-Dateien des Bundesgesetzblattes, der
Gesetz- und Verordnungsblätter der Länder und der Drucksachen von Bundestag,
Bundesrat und Landtagen sowie Klartext angenommen. Die Dokumentart wird aus dem
Text erschlossen, nicht aus dem Dateinamen (§ 10). Änderungsanträge dürfen
zusammen mit demjenigen Entwurf angegeben werden, den sie ändern (§ 11).

## § 6 Betrieb der Befehlszeilenfassung

(1) Nach der Herstellung nach § 4 wird das Erzeugnis wie folgt aufgerufen:

```shell
java -jar target/aendggner-0.1.0-SNAPSHOT.jar \
  stammgesetz.xml aenderungsgesetz.pdf -o synopse.html
```

(2) Von den Schaltern sind die folgenden von allgemeiner Bedeutung:

**`-o, --output <file>`**
: Ausgabedatei (Vorgabe `synopse.html`; „`-`“ bezeichnet die Standardausgabe).

**`--vollstaendig`**
: Auch unveränderte Normen in die Synopse aufnehmen.

**`--artikel <n>`**
: Nur den bezeichneten Artikel des Änderungsgesetzes anwenden. Ohne diese Angabe
  werden alle Artikel angewandt, deren Einleitung das Stammgesetz nennt.

**`--extract-only`**
: Nur den bereinigten Lineartext des Änderungsdokuments ausgeben. Dies ist
  angezeigt, wenn die PDF-Aufbereitung (§ 9) fehlerhaft bleibt: Der Text ist zu
  prüfen, von Hand zu berichtigen und als Klartextdatei wieder einzuspeisen.

(3) Ein Aufruf unmittelbar aus dem Arbeitsbaum, ohne vorherige Herstellung nach
§ 4, ist zulässig:

```shell
./mvnw exec:java
```

(4) Die vollständige Schalterliste einschließlich der Schalter für die
Fehlersuche gibt `--help` aus.

## § 7 Erkannte Änderungsbefehle

(1) Erkannt werden die gebräuchlichsten Änderungsbefehle des Handbuchs der
Rechtsförmlichkeit, nämlich Ersetzen, Neufassung, Einfügen, Anfügen, Aufheben,
Streichen und Umnummerierung.

(2) Von den Sonderformen sind erkannt:

1. Bereichs- und Koordinationsziele („Die Absätze 8 und 9 werden durch die
   folgenden Absätze 8 bis 10 ersetzt“);
2. strukturelle Streichungen ganzer Einheiten („§ 9 wird gestrichen“);
3. §- und Gliederungs-Umnummerierungen („§ 9a wird zu § 9“, „Der bisherige
   Abschnitt 2 wird zu Abschnitt 3“);
4. das Einfügen und Ersetzen ganzer §-Blöcke;
5. Chapeau-Lokatoren („Im Satzteil vor Nummer 1 …“);
6. Änderungen an Anhängen und Anlagen („Der Anhang wird wie folgt geändert: …
   Nach Nummer 2 wird die folgende Nummer 2a eingefügt“);
7. Angabe-Befehle auf die Inhaltsübersicht (gefasst, ersetzt, eingefügt,
   gestrichen); sie werden auf die Inhaltsübersichts-Norm angewandt;
8. das Einfügen und Ersetzen von Gliederungs-Überschriften („Nach § 33 werden
   die folgenden Überschriften zu Teil 3 … eingefügt“);
9. Voranstellungen;
10. Mehrfach-Ersetzungs- und -Einfügepaare;
11. Einfügungen, deren Position ein Wortanker statt einer Stellenangabe bestimmt
    („Vor den Wörtern „Aus dem Bereich Verkehr:“ wird folgender Absatz 5
    eingefügt“);
12. Verbünde aus Umnummerierung und Folgeänderung („§ 50 wird zu § 38 und wird
    wie folgt geändert“, „Die bisherige Nr. 7 wird Nr. 5 und das Komma wird durch
    das Wort „und“ ersetzt“). Eine Satzzeichen-Operation meint dabei stets die
    soeben umnummerierte Einheit, weil ihr Zieltext nichts unterscheidet;
13. Verb-Rahmen, deren Unterpunkte allein die Fundstelle tragen („Es werden
    ersetzt: … in § 35 Absatz 3 die Angabe „X“ jeweils durch die Angabe „Y“,“),
    sowie
14. die Neufassung der Gesetzesüberschrift.

(3) Landesrechtliche Befehlsformen bestimmt ergänzend § 13.

## § 8 Reihenfolge der Anwendung

(1) Angewandt wird nicht stur in Dokumentreihenfolge. Umnummerierungen beziehen
sich stets auf die ursprüngliche Zählung, nicht auf den Stand nach den
vorangegangenen Punkten; wer eine Bezeichnung räumt, kommt deshalb vor den, der
sie neu besetzt.

(2) Aus Absatz 1 folgt

1. die absteigende Abarbeitung einer aufsteigenden Kaskade („Der bisherige
   Absatz 3 wird Absatz 4“, „Der bisherige Absatz 4 wird Absatz 5“, …) sowie
2. der Vorrang einer Umnummerierung vor derjenigen Einfügung, die deren
   Bezeichnung neu vergibt.

Wer vorrückt, nimmt dabei seine eigenen Räumer mit: Auch eine über mehrere Punkte
verschränkte Kaskade („Die bisherige Nr. 15 wird Nr. 16“, „Die bisherigen Nrn. 13
und 14 werden die Nrn. 14 und 15“, „Nach Nr. 12 wird folgende Nr. 13 eingefügt“)
tritt geschlossen vor die Einfügung, nicht bloß mit ihrem letzten Glied.

(3) Geordnet werden nicht die Befehle, sondern die einzelnen Anwendungsschritte.
Ein Verbund aus Umnummerierung und Folgeänderung (§ 7 Absatz 2 Nummer 12) trägt
nämlich zwei gegenläufige zeitliche Ansprüche: Die Umnummerierung gehört nach
vorn, die Folgeänderung dagegen an ihre Dokumentstelle, weil sie die
vorangegangenen Punkte als vollzogen voraussetzt — „Die bisherige Nr. 11 wird
Nr. 12 und die Angabe „schriftliche“ wird gestrichen“ ist vorgezogen mehrdeutig,
weil erst ein früherer Punkt die zweite Fundstelle des Wortes beseitigt.
Verschoben wird stets nur nach vorn.

(4) Das Protokoll bleibt gleichwohl befehlsweise: Ein Verbund gilt nur dann als
angewandt, wenn jeder seiner Teile gegriffen hat; sonst nennt die Meldung den
Teil und den Grund.

## § 9 Aufbereitung der Druckwerke

(1) Die PDF-Aufbereitung toleriert die üblichen Drucksachen-Artefakte, nämlich
Seitenköpfe und -füße, Vorabfassungs-Wasserzeichen, vertauschte oder gerade
Anführungszeichen, verklebte Wortgrenzen und zerlegt kodierte Umlaute. Die
Brotschrift wird seitenweise bestimmt, sodass auch Ministeriumsentwürfe mit
gemischten Layouts vollständig ausgelesen werden.

(2) Fehlt im amtlichen Satz ein schließendes Anführungszeichen, so endet das
Zitat an der nächsten Strukturgrenze, nämlich an einer Artikel-Überschrift oder —
dort, wo die Anführungszeichen eines Artikels nachweislich nicht aufgehen — am
nächsten Aufzählungspunkt des Änderungsdokuments. Der Vorgang wird als Warnung
gemeldet.

(3) Auf den Beispieldaten (§ 17 Absatz 2) werden hiernach alle Befehle der
Fassungen des Bundesgesetzblattes und der aktuellen Entwürfe angewandt; für
UWG, AGG und ProdHaftG verbleibt kein Befehl zur Prüfung von Hand. Im Übrigen
gilt § 1 Absatz 4.

(4) Bleibt die Aufbereitung im Einzelfall fehlerhaft, so ist nach § 6 Absatz 2
mittels des Schalters `--extract-only` zu verfahren.

## § 10 Quellformate: Gesetz, Entwurf, Antrag

Ein Änderungsbefehl steht nicht nur im verkündeten Gesetzblatt. Dasselbe Vorhaben
durchläuft als Referenten-, Regierungs- und Fraktionsentwurf, als Änderungsantrag
und als Beschlussempfehlung mehrere Fassungen, und die Frage „was gälte, wenn das
durchkommt?“ stellt sich in jeder davon. Das Erzeugnis erschließt die Art eines
Dokuments deshalb aus seinem Kopf — nie aus dem Dateinamen, der lügen kann (im
Beispielkorpus heißt ein Entschließungsantrag
`BT-Drs-21-7071_Beschlussempfehlung.pdf`), und nie aus einer Kennung, die von
außen mitzugeben wäre.

Unterschieden werden:

**Änderungsgesetz**
: Das verkündete Artikelgesetz aus BGBl, GVBl oder GVOBl. Der Regelfall.

**Gesetzentwurf**
: Referenten-, Regierungs- und Fraktionsentwürfe, auch als Drucksache von
  Bundestag, Bundesrat oder Landtag. Der Begründungsteil hinter dem
  Regelungstext erzeugt keine Befehle; erkannt wird er an „Begründung“ ebenso wie
  an den Entwurfsvarianten („A. Allgemeiner Teil“, „Zu Artikel 1“).

**Änderungsantrag**
: Ändert nicht das Stammgesetz, sondern eine **Drucksache**; das Nähere bestimmt
  § 11.

**Beschlussempfehlung**
: Trägt ihre Fassung in einer zweispaltigen Zusammenstellung, die aufgelöst wird;
  die Synopse zeigt alsdann die vom Ausschuss beschlossene Fassung. Das Nähere
  bestimmt § 12.

**Dokument ohne Änderungsbefehle**
: Entschließungs- und schlichter Antrag, Plenarprotokoll, Bericht. Sie werden
  übergangen und gemeldet — nicht stillschweigend zu null Befehlen verarbeitet.

Sobald ein Entwurf, ein Antrag oder eine Beschlussempfehlung beteiligt ist, trägt
die Synopse den Hinweis **Entwurfsfassung — nicht geltendes Recht** (§ 1 Absatz 3);
die Quellenzeile nennt je Datei die erkannte Art.

## § 11 Änderungsanträge

(1) Ein Änderungsantrag ist eine Meta-Änderung: Er ändert den Entwurf, nicht das
Gesetz. Sein Rahmensatz adressiert deshalb zwei Ebenen zugleich — „In § 3 Nr. 22
wird § 18 Nr. 1 wie folgt geändert:“ nennt erst die Stelle _in der Drucksache_
(den 22. Änderungsbefehl ihres dritten Paragraphen) und dann die Stelle _in dem
Text, den dieser Befehl zitiert_.

(2) Der Antrag ist zusammen mit seinem Entwurf anzugeben:

```shell
java -jar target/aendggner-0.1.0-SNAPSHOT.jar \
  BayJG-alt.txt Ltg-Drs-19-9707_Gesetzentwurf.pdf \
  Ltg-Drs-19-10365_Aenderungsantrag-Gruene.pdf -o synopse.html
```

(3) Angewandt wird alsdann erst der Antrag auf den Entwurf und danach der so
geänderte Entwurf auf das Stammgesetz; die Synopse zeigt also, was gälte, wenn
Entwurf _und_ Antrag durchkämen. Welcher Entwurf gemeint ist, entscheidet die
Drucksachennummer, die der Antrag selbst nennt („(Drs. 19/9707)“), nicht die
Reihenfolge der Argumente.

(4) Fehlt der Entwurf, so bleibt der Antrag unangewandt und wird gemeldet. Ihn
ersatzweise auf das Stammgesetz loszulassen wäre falsch, denn seine
Stellenangaben zielen auf die Drucksache.

(5) Erkannt wird auch die elliptische Antragsform, die das Hilfsverb nur einmal
in der Beschlussformel führt („1. In Nr. 1.29 die Angabe „,“ am Ende durch die
Angabe „;“ ersetzt.“).

## § 12 Beschlussempfehlungen; die beschlossene Fassung

(1) Die maßgebliche Fassung einer Beschlussempfehlung steht in einer
zweispaltigen Zusammenstellung: links der Entwurf, rechts die Beschlüsse des
Ausschusses. Anders als beim alten BGBl und beim Berliner GVBl stehen die Spalten
**nicht** nacheinander im Inhaltsstrom, sondern zeilenweise verschränkt; getrennt
werden sie deshalb über die Koordinaten (`PatchTextExtraktor.extrahiereSpalten`,
Schnitt an der Blattmitte, aber nur an einem tatsächlichen Spaltensteg, damit
ganzseitenbreite Zeilen ungeschnitten bleiben).

(2) Die rechte Spalte für sich gelesen ist kein vollständiges Dokument: Sie druckt
Unverändertes nicht ab, sondern vermerkt bloß „unverändert“ — und zwar nicht nur
je Gliederungspunkt, sondern auch zeilenweise innerhalb zitierter Blöcke, weshalb
ihre Anführungszeichen nicht aufgehen. Eine Auflösung über die Gliederungspfade
scheitert daran nachweislich.

(3) Maßgeblich ist stattdessen die **Grundlinie**: Beide Spalten sind zeilensynchron
gesetzt, jeder Vermerk steht auf der Höhe der Entwurfszeile, die er meint. Der
`ZusammenstellungsLeser` führt beide Spalten über Seite und Grundlinie in eine
gemeinsame Lesereihenfolge zusammen und entscheidet dann Zeile für Zeile:
„unverändert“ holt den Wortlaut aus der Entwurfsspalte, „entfällt“ streicht ihn,
sonst gilt die Ausschussspalte. Dabei meint „unverändert“ den Wortlaut, nicht die
Zählung; streicht der Ausschuss einen Punkt, so rücken die folgenden auf, und
seine Marke tritt an die Stelle derjenigen des Entwurfs.

(4) Die Quellenzeile der Synopse weist die verwendete Spalte als
`[Beschlussempfehlung …, Ausschussfassung]` aus.

(5) Lässt sich die Zusammenstellung nicht auflösen, so wird die Datei mit
Begründung übergangen, samt Hinweis auf die Drucksachennummer des Entwurfs, der
sich stattdessen eignet. Eine halb aufgelöste Fassung auszugeben wäre schlimmer
als keine.

(6) Belegt ist beides: dass die **linke** Spalte Befehl für Befehl den
Regierungsentwurf ergibt, aus dem die Zusammenstellung gebaut ist, und dass die
aufgelöste Fassung mehr Befehle trägt als er — der Ausschuss hat der GEG-Novelle
zwei Artikel hinzugefügt. Zwei Beispiele stehen im Test: BT-Drs. 20/7619 (GEG)
und BT-Drs. 19/24334 (Drittes Bevölkerungsschutzgesetz).

## § 13 Landesrecht

(1) Für das bayerische Landesrecht versteht das Erzeugnis die dortigen
Konventionen. Stammgesetze gliedern sich in Artikel („Art. 6 Abs. 2 Satz 1
Nr. 2“, durchgängig abgekürzt zitiert), Änderungsgesetze dagegen in Paragraphen —
auch mehrere Gesetze in einem GVBl-Heft, aus denen die auf das Stammgesetz
zielenden §§ (einschließlich „Weitere Änderung“) anhand des Einleitungssatzes
ausgewählt werden. Amtliche Satznummern bleiben als Superskripte erhalten und
dienen als exakte Satzgrenzen. Zusätzlich erkannt werden

1. die bayerischen Befehlsformen („Fußnote 1 wird aufgehoben“, „In Satz 1 wird
   die Satznummerierung „1“ gestrichen“, „Dem Wortlaut werden die folgenden
   Abs. 1 bis 4 vorangestellt“, „Der bisherige Wortlaut wird Abs. 5“),
   Halbsatz-Ziele und Klauselketten mit gemeinsamem Schlussverb sowie
2. das Fortführungszeichen des GVBl, wonach jedes neugefasste Aufzählungsglied
   erneut mit „ öffnet.

Der Fall ist gegen die amtliche Nachfassung belegt: Alle 154 auf das BayJG
zielenden Befehle des Heftes 6/2026 werden an 54 Normen selbsttätig angewandt,
auch die verschränkte Neunummerierung des Bußgeldkatalogs in Art. 56.

(2) Die übrigen Länder gliedern ihre Stammgesetze wie der Bund in Paragraphen;
die Unterschiede liegen im Gesetzblatt-Satz und in den Befehlsidiomen. Belegt sind
Sachsen (SächsBeamtVG), Niedersachsen (NEFG), Thüringen (ThürKigaFinVO), Hessen
(Verkehrsrechts-Zuständigkeitsverordnung — deren Paragraphen tragen keine
Überschriften und deren Teile schreiben ihr Ordinale aus) und
Nordrhein-Westfalen — dort alle vier ändernden Artikel eines Heftes:
Telemedienzuständigkeitsgesetz, Landesmediengesetz, Ausführungsgesetz zum
17. Rundfunkänderungsstaatsvertrag und, mit 101 Befehlen an 31 Normen der größte
Fall des Heftes, das WDR-Gesetz —, jeweils mit Akzeptanztests gegen die amtlichen
Nachfassungen.

(3) Für Schleswig-Holstein und Berlin reicht die Prüfung bis zur
Befehlserkennung — dort vollständig, aber ohne Anwendung —, weil deren
Stammfassungen noch nicht beschafft sind. Die Landesportale geben sie einer
Skriptabfrage nicht aus, sondern nur ihre leere Anwendungshülle; einem Browser
dagegen schon, und auf diesem Wege ist die hessische Stammfassung beschafft
worden. Thüringens Portal gibt sie ebensowenig aus; dort ist die konsolidierte
Fassung aus dem Stammheft und den drei Änderungsheften des Gesetzblattes
zusammengesetzt, die die Parlamentsdatenbank des Landtags frei ausgibt.

(4) Welche Konvention welches Land beisteuert, welche Stammfassungen woher
stammen und was noch offen ist, verzeichnet
`src/test/resources/sampledata/Landesrecht-Beispiele.adoc`.

## § 14 Browserfassung

(1) Neben der Befehlszeilenfassung besteht eine Browserfassung, die dieselbe
Pipeline (`eu.mulk.aendggner.Pipeline`, § 16 Absatz 1) über ein
Upload-Formular zugänglich macht: Stammgesetz- und Änderungsgesetz-Datei(en)
wählen, Synopse erhalten.

(2) Über dem Formular steht ein zugeklappter Einführungsblock, der zwei
durchgerechnete Fälle mit Verweisen auf die amtlichen Fundstellen anbietet,
nämlich das UWG (`gesetze-im-internet.de/uwg_2004/xml.zip`) mit dem Dritten Gesetz
zu seiner Änderung (BGBl. 2026 I Nr. 43) und das AGG
(`gesetze-im-internet.de/agg/xml.zip`) mit dem Regierungsentwurf BT-Drs. 21/6178.
Mitausgeliefert wird nichts; die Seite ruft die Dateien auch nicht selbst ab, sie
verweist nur darauf.

(3) Ein Server ist nicht erforderlich: Die vollständige Verarbeitung —
PDF-Textgewinnung mit PDFBox eingeschlossen — läuft als WebAssembly-Modul im
Browser, übersetzt mit GraalVM Web Image aus demselben Java-Quelltext.
Ausgeliefert werden nur statische Dateien; die gewählten Dokumente verlassen den
Rechner der Nutzer:innen nicht. Die Herstellung verlangt abweichend von § 4
Absatz 3 Oracle GraalVM 25.1 oder neuer, denn Web Image ist nur dort enthalten,
nicht in der Community Edition:

```shell
JAVA_HOME=/pfad/zu/oracle-graalvm ./mvnw -Pwasm package
```

(4) Ergebnis ist `target/web/` mit `index.html`, `app.js`, `worker.js`,
`style.css`, `favicon.svg`, `aendggner.js` und `aendggner.js.wasm` (rund 17 MB,
komprimiert etwa 5 MB). Das Verzeichnis ist so, wie es dasteht, auslieferbar:
`deploy/webpaket.sh` läuft am Ende desselben Befehls, wirft den mehrere hundert
Megabyte großen Textzwischenschritt `aendggner.js.wat` fort, schickt das Modul
durch `wasm-opt -Oz` und legt den Quelltext der gebauten Fassung als
`aendggner-quelltext.tar.gz` samt `quelltext-fassung.txt` bei.

(5) Die Größe des Moduls beruht auf vier Vorkehrungen; wer eine davon zurücknimmt,
handelt sich die Megabyte wieder ein:

1. `-Os` beim Übersetzen statt der auf Durchsatz gerichteten Voreinstellung;
2. kein Picocli-Annotationsprozessor im Profil `wasm`. Er meldete die
   Befehlszeilenklasse zur Reflexion an, worauf die Erreichbarkeitsanalyse die
   gesamte Befehlszeilenfassung ins Browser-Image zog, die dort niemand aufruft;
3. ein enger Ressourcen-Glob in `reachability-metadata.json`. Das frühere
   `org/apache/fontbox/**` bettete 3,3 MB CJK-CMaps, `Scripts.txt` und — weil
   `**` auch Klassendateien trifft — 0,7 MB `.class`-Dateien ein, von denen
   deutsche Gesetzes-PDFs nichts brauchen. Geblieben sind die beiden
   Identity-CMaps; `org/apache/pdfbox/resources/**` bleibt vollständig, damit die
   Breitenberechnung bei nicht eingebetteten Schriften unangetastet ist;
4. `wasm-opt -Oz` als Nachlauf. Die zugelassenen Wasm-Merkmale sind in
   `webpaket.sh` einzeln aufgezählt und nicht als `--all-features` erteilt: Sonst
   nutzt Binaryen auch Vorschläge, die noch kein Browser annimmt, und das Modul
   scheitert erst beim Instanziieren.

(6) Im Quelltextarchiv fehlt der Beispielkorpus; `.gitattributes` nimmt
`src/test/resources/sampledata` von `git archive` aus. Es sind Gesetzes- und
Drucksachentexte fremder Urheberschaft, an denen allein die Tests messen —
Quelltext im Sinne der AGPLv3 sind sie nicht, gebaut wird ohne sie, und sie
machten das Archiv dreißigmal so groß wie den Quelltext (29,7 MB statt 0,26 MB).
`quelltext-fassung.txt` sagt dies und verweist für den vollständigen Korpus auf
die Anschrift nach § 3 Absatz 2. Wächst das Archiv wieder über 8 MiB, so bricht
`webpaket.sh` ab; alsdann sind Massendaten ins Repository geraten, die dort nicht
hingehören.

(7) Trägt der Arbeitsbaum uneingecheckte Änderungen, so bricht die Herstellung
nach Absatz 3 ab, denn der beigelegte Quelltext wäre alsdann nicht der gebaute.
Für einen Probelauf hilft `QUELLTEXT_UNGEPRUEFT=1`.

(6) Zur örtlichen Ansicht genügt `file://` nicht; Browser laden Wasm-Module und
Worker nur über HTTP (<http://localhost:8000/>):

```shell
jwebserver -d target/web
```

(7) Die Befehlszeilenfassung (§ 6) bleibt hiervon unberührt und ist weiterhin der
Weg für Massenläufe.

## § 15 Ausrollen

(1) Erforderlich ist nur ein Ort für statische Dateien. Ausgeliefert wird über
Cloudflare Workers. Dort gilt eine Grenze von 25 MiB je Datei — unkomprimiert
gemessen —, und komprimiert wird beim Ausliefern ohnehin. `webpaket.sh` legt
deshalb keine `.gz`/`.br`-Beilagen mehr an und hält am Ende jede Datei gegen
diese Grenze; überschreitet eine sie, so bricht der Bau ab, statt das Hochladen
scheitern zu lassen.

```shell
JAVA_HOME=/pfad/zu/oracle-graalvm ./mvnw -Pwasm package
```

(2) Wer die Fassung stattdessen selbst ausliefert — unter einem Unterpfad einer
bestehenden Domain, wie zuvor unter <https://aendggner.app.kellertomaten.de/> —,
braucht die Vorkompression und fordert sie beim Bau an:

```shell
JAVA_HOME=/pfad/zu/oracle-graalvm VORKOMPRIMIEREN=1 ./mvnw -Pwasm package
rsync -av --delete target/web/ server:/var/www/aendggner/
```

(3) `deploy/nginx-aendggner.conf` ist kein eigener `server`-Block, sondern ein
Schnipsel zum Einfügen in den vorhandenen (`include`). Er bringt mit:

1. die Weiterleitung von `/aendggner` auf `/aendggner/`, ohne die alle relativen
   Verweise der Seite auf die Domainwurzel zielten;
2. den MIME-Typ `application/wasm`, ohne den der Browser die Instanziierung des
   Moduls verweigert;
3. `gzip_static`/`brotli_static` für die nach Absatz 2 vorkomprimierten Dateien,
   statt 17 MB je Abruf neu zu packen;
4. `Cache-Control: no-cache` statt einer Haltefrist. Die Dateinamen tragen keine
   Fassungskennung, und ein Browser mit altem `app.js` und neuem `.wasm` bekäme
   sonst eine Mischfassung, die es nie gegeben hat. Revalidiert wird per ETag; das
   unveränderte Modul kostet alsdann ein 304 ohne Rumpf;
5. die Sicherheitskopfzeilen samt einer Content-Security-Policy. Zwei ihrer
   Freigaben sind unvermeidlich und in der Datei begründet: `'wasm-unsafe-eval'`
   für die Instanziierung des Moduls und `'unsafe-inline'` für Stile, weil die
   Synopse als `blob:`-Dokument die Richtlinie der erzeugenden Seite erbt, ihr
   Stylesheet aber eingebettet trägt.

(4) `impressum.html` und `datenschutz.html` tragen die Angaben nach § 5 DDG und
Art. 13 DSGVO. Für den Regelfall — Auslieferung über Cloudflare — nennt
`datenschutz.html` Cloudflare als Auftragsverarbeiter nach Art. 28 DSGVO samt
Drittlandsübermittlung und sagt, dass Zugriffsprotokolle dort anfallen und vom
Verantwortlichen nicht abgerufen werden. Die daneben für die eigene Auslieferung
(Absatz 2) genannte Frist von 14 Tagen muss zu derjenigen des eigenen Servers
passen. Wer bei Cloudflare weitere Funktionen einschaltet — Web Analytics,
Logpush, Bot Management, Turnstile —, muss die Erklärung ergänzen; sie ist so
gefasst, dass keine davon in Betrieb ist.

(5) Der Footer der Startseite verweist auf den beigelegten Quelltext-Tarball; dies
verlangt AGPLv3 § 13 für den Netzwerkbetrieb. Als fortlaufende Zweitquelle ist
die Anschrift nach § 3 Absatz 2 genannt.

## § 16 Aufbau des Quelltextes

(1) Gemeinsamer Kern von Befehlszeile und Browser ist `eu.mulk.aendggner.Pipeline`:
Stammgesetz laden, Änderungsdokumente erkennen, zerlegen und anwenden, Synopse
ausgeben. Die Pipeline kennt kein Dateisystem; Eingaben erreichen sie als
`eu.mulk.aendggner.Quelle` (Name und Bytes).

(2) Die Pakete unterhalb von `src/main/java/eu/mulk/aendggner/` gliedern sich wie
folgt:

**`aenderung/`**
: Erkennen der Dokumentart, Textbereinigung und Zerlegung in Änderungsbefehle
  (`parse/`), Herkunftsnachweis (`Provenienz`), Stellenangaben (`Stelle`).

**`anwendung/`**
: Anwenden der Befehle auf die Stammfassung, Auflösen der Stellenangaben, Zerlegen
  in Sätze, Fortschreiben der Inhaltsübersicht.

**`gesetz/`**
: Die Stammfassung nebst Gliederung, Normen, Absätzen und Superskripten; `gii/`
  liest das Bundesformat, `land/` die Fassungen der Länder.

**`synopse/`**
: Wortdiff, Aufbau und HTML-Ausgabe der Synopse.

(3) Der Kern ist reines Java ohne Dateisystem- und ohne Netzzugriff; nur vier
Stellen berühren die Plattform (PDFBox, MIME-Erkennung, XML-Parser,
Dateizugriff). Sie sind hinter `eu.mulk.aendggner.Quelle` (Name und Bytes) und
`eu.mulk.aendggner.DateiTyp` (Signaturbytes statt Tika) gebündelt; hierauf beruht,
dass Befehlszeile und Browser dieselbe Pipeline speisen. Ein Java-Server ist
deshalb entbehrlich.

(4) Zwei Eigenheiten von Web Image sind zu beachten und im Quelltext vermerkt:

1. Die nativen zlib-Bindungen des JDK fehlen (`java.util.zip.Inflater`), ohne die
   kein PDF lesbar ist. `src/wasm/java/.../InflaterErsatz.java` ersetzt sie durch
   die reine Java-Umsetzung von jzlib.
2. Typisierte Felder lassen sich derzeit nicht nach `byte[]` umsetzen; der
   Dateiinhalt wandert deshalb als Base64-Text über die JS-Grenze.

## § 17 Prüfung

(1) Herstellung und Prüfung erfolgen durch den nachstehenden Befehl:

```shell
./mvnw verify
```

(2) Die Prüfung umfasst neben den Einzelprüfungen Akzeptanzprüfungen, die
vollständige Änderungshefte auf die zugehörige Stammfassung anwenden und das
Ergebnis gegen die amtliche Nachfassung stellen. Die hierfür verwendeten
Beispieldaten nebst Herkunftsnachweis (`SOURCES`) liegen unter
`src/test/resources/sampledata/`.

## Lizenz

Der Quelltext steht unter der GNU Affero General Public License, Version 3 oder
(nach Wahl) einer späteren Fassung — `AGPL-3.0-or-later`. Den Wortlaut trägt
`LICENSES/AGPL-3.0-or-later.txt`; `COPYING` verweist als symbolische Verknüpfung
darauf.

Das Erzeugnis folgt der [REUSE-Spezifikation](https://reuse.software/spec/): Jede
Datei ist einer Urheberschaft und einer SPDX-Lizenz zugeordnet, entweder durch
eine Kopfzeile in der Datei selbst oder — bei Binärdateien und Dateien ohne
Kommentarsyntax — durch einen Eintrag in `REUSE.toml`. Geprüft wird das mit:

```shell
uvx reuse lint
```

Drei Lizenzen kommen vor:

**`AGPL-3.0-or-later`**
: Der eigene Quelltext samt Weboberfläche, Bauwerk und Dokumentation.

**`Apache-2.0`**
: Der unverändert mitgelieferte Maven-Wrapper (`mvnw`, `mvnw.cmd`,
  `.mvn/wrapper/`).

**`LicenseRef-AmtlichesWerk`**
: Der Beispielkorpus unter `src/test/resources/sampledata/`: Gesetzblätter,
  Drucksachen, Plenarprotokolle und ministerielle Entwürfe des Bundes und der
  Länder. Sie sind nach § 5 UrhG amtliche Werke und damit gemeinfrei; das
  Änderungsverbot und das Gebot der Quellenangabe (§§ 62, 63 UrhG) bleiben
  unberührt. Die Fundstellen weist die Datei `SOURCES` des jeweiligen
  Verzeichnisses nach. Der Korpus ist kein „Corresponding Source“ im Sinne der
  AGPL — gebaut wird ohne ihn —, und `.gitattributes` nimmt ihn deshalb vom
  Quelltextarchiv aus.

Die Kopfzeilen der Java-Dateien pflegt Spotless (`licenseHeader`); der an die
Phase `verify` gebundene Lauf von `spotless:check` hält den Bau an, sobald eine
Quelldatei ohne Kopfzeile hinzukommt.
