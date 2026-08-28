<!--
SPDX-FileCopyrightText: 2020-2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
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
- [§ 6a Die fortgeschriebene Fassung; die Kette](#-6a-die-fortgeschriebene-fassung-die-kette)
- [§ 6b Abgleich mit der amtlichen Nachfassung](#-6b-abgleich-mit-der-amtlichen-nachfassung)
- [§ 6c Probe auf die Inhaltsübersicht](#-6c-probe-auf-die-inhaltsübersicht)
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

(5) Die Begründungen sind dort nach der Art des Grundes gebündelt und ausgezählt
(Befehl nicht erkannt, Stelle nicht auffindbar, Zieltext nicht vorhanden,
Fundstelle mehrdeutig, Bereich unbrauchbar, Zitat unbrauchbar, Bestand
widerspricht dem Befehl, nicht unterstützt, Anwendung fehlgeschlagen). Der
ausformulierte Grund bleibt daneben stehen; gebündelt ist nur, was ohne Ordnung
eine bloße Liste wäre. Dieselbe Auszählung gibt `--dump-befehle` unter der
Aufschrift „Gründe nach Häufigkeit“ aus.

(5a) Jeder Befehl nennt überdies die **Seite des Änderungsdokuments**, auf der er
steht („Artikel 1 23. b) (S. 8)“). Wer einem Rest nachgehen will, findet ihn damit
im Heft, statt achtzig Seiten zu durchsuchen. Die Seite wird nicht mitgezählt,
sondern am Wortlaut wiedergefunden: Der Auszug hält seinen Wortbestand seitenweise
fest, und der Befehlstext wird darin gesucht, beide heruntergebrochen auf
Buchstaben und Ziffern — Silbentrennung, Anführungszeichen und Satzzeichen, also
gerade das, woran die Aufbereitung (§ 9) arbeitet, stören den Vergleich dann nicht.
Gesucht wird von der Stelle aus, bis zu der die Erschließung gediehen ist; derselbe
kurze Wortlaut („Absatz 3 wird aufgehoben“) steht in einem Heft dutzendfach. Was
sich nicht zweifelsfrei wiederfindet, bleibt ohne Seitenangabe — eine falsche Seite
wäre schlimmer als keine. Klartexteingaben (§ 5 Absatz 2) haben kein Satzbild und
tragen deshalb keine.

(6) Gibt die Quelle des Stammgesetzes ihren Stand an und ist ihr Wortlaut jünger
als die Fassung, die der Einleitungssatz des Änderungsgesetzes fortschreibt, so
wird das eigens gerügt. Das ist die häufigste Ursache liegengebliebener Befehle
und aus der Begründung „kommt im Zieltext nicht vor“ allein nicht zu erkennen.

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
Für die Herstellung der Browserfassung gilt abweichend § 14 Absatz 5.

## § 5 Zulässige Eingaben

(1) Als Stammgesetz des Bundes wird XML im gii-norm-Format von
[gesetze-im-internet.de](https://www.gesetze-im-internet.de/) angenommen. Das
Portal gibt seine Werke nur als Archiv aus (`…/<kennung>/xml.zip`); das Archiv wird
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
4. Der Kopf darf hinter dem Titelblock die Zeilen „Stand: …“ und
   „Fortgeschrieben durch: …“ führen. Jene gibt den Stand der Quelle wieder,
   diese jedes Heft, das auf den Wortlaut bereits angewandt worden ist; beide
   schreibt `--neufassung`, und beide werden zurückgelesen (§ 6a Absatz 4).

(3) Als Änderungsdokument werden PDF-Dateien des Bundesgesetzblattes, der
Gesetz- und Verordnungsblätter der Länder und der Drucksachen von Bundestag,
Bundesrat und Landtagen sowie Klartext angenommen. Die Dokumentart wird aus dem
Text erschlossen, nicht aus dem Dateinamen (§ 10). Änderungsanträge dürfen
zusammen mit demjenigen Entwurf angegeben werden, den sie ändern (§ 11).

(4) Jede Eingabe — Stammgesetz, Änderungsdokument und Nachfassung — darf statt
eines Dateipfades auch eine Anschrift sein:

1. `http://…` oder `https://…` lädt das Werk unmittelbar, etwa eine Drucksache
   von `dserver.bundestag.de`;
2. `gii:<kennung>` steht für das Bundesrecht und wird zu
   `https://www.gesetze-im-internet.de/<kennung>/xml.zip` aufgelöst. Die Kennung
   ist der vorletzte Teil der Portaladresse; sie ergibt sich aus der Abkürzung des
   Gesetzes, klein geschrieben, wobei jedes andere Zeichen als Buchstabe, Ziffer
   und Bindestrich zum Unterstrich wird (`gii:"UWG 2004"` → `uwg_2004`). Führt sie
   ins Leere, so wird im Verzeichnis des Portals nachgeschlagen: `gii:geg` findet
   sich selbst, `gii:uwg` findet `uwg_2004`. Bleiben mehrere übrig, so wird nicht
   geraten, sondern aufgezählt.

Ein vorhandener Dateipfad hat stets den Vorrang. Geladenes wird unter
`~/.cache/aendggner` (oder `$XDG_CACHE_HOME`) abgelegt und beim nächsten Lauf von
dort genommen; ist das Verzeichnis nicht beschreibbar, wird eben jedes Mal geladen.
Ein Vermittler wird aus `HTTPS_PROXY`/`HTTP_PROXY` übernommen. Die Browserfassung
(§ 14) kennt diesen Weg nicht — dort verweigert der Wagen des Benutzers einer
fremden Anschrift die Auskunft.

```shell
java -jar target/aendggner-0.1.0-SNAPSHOT.jar "gii:UWG 2004" \
  https://dserver.bundestag.de/btd/21/018/2101855.pdf -o synopse.html
```

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

**`--stichtag <JJJJ-MM-TT>`**
: Die an diesem Tage geltende Fassung erzeugen. Tritt das Änderungsgesetz
  gestaffelt in Kraft, so bleiben die an jenem Tage noch nicht geltenden Befehle
  unangewandt und werden gesondert ausgewiesen (§ 8 Absatz 6). Ohne diese Angabe
  werden alle Befehle angewandt.

**`--neufassung <file>`**
: Neben der Synopse die fortgeschriebene Fassung als kanonischen Klartext (§ 5
  Absatz 2) ausgeben; „`-`“ bezeichnet die Standardausgabe. Die Ausgabe ist als
  Stammgesetz eines weiteren Änderungsheftes wieder einlesbar (§ 6a).

**`--nachfassung <file>`**
: Das Ergebnis Norm für Norm gegen die amtliche Nachfassung halten (§ 6b). Es
  gelten dieselben Eingaben wie für das Stammgesetz, auch die Anschriften des § 5
  Absatz 4.

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

## § 6a Die fortgeschriebene Fassung; die Kette

(1) Ein Änderungsgesetz sagt, was zu tun ist; das Erzeugnis rechnet aus, was danach
gilt. Diese Fassung ist nicht bloß die rechte Spalte der Synopse, sondern ein
eigenes Erzeugnis: Der Schalter `--neufassung` (§ 6 Absatz 2) gibt sie als
kanonischen Klartext im Format des § 5 Absatz 2 aus.

(2) Daraus folgt die **Kette**. Wer zwei Hefte nacheinander auf dasselbe Stammgesetz
anwenden will, gibt die Ausgabe des ersten Laufes als Stammgesetz des zweiten ein:

```shell
java -jar target/aendggner-0.1.0-SNAPSHOT.jar stamm.txt heft-1.pdf \
  --neufassung zwischenfassung.txt -o synopse-1.html
java -jar target/aendggner-0.1.0-SNAPSHOT.jar zwischenfassung.txt heft-2.pdf \
  --neufassung endfassung.txt -o synopse-2.html
```

Auf diesem Wege lässt sich eine konsolidierte Fassung aus dem Stammheft und den
Änderungsheften des Gesetzblattes zusammensetzen, wo kein Portal sie fertig liefert
(§ 13 Absatz 3). Mehrere Hefte in einem Lauf sind ohnehin zulässig und werden
gleichfalls nacheinander angewandt, jedes auf das Ergebnis des vorigen; die Kette
über mehrere Läufe ist für das gedacht, was über einen Lauf hinausreicht.

(3) Tragen sämtliche Hefte eines Laufes ein Ausfertigungsdatum, so werden sie in
dessen Reihenfolge angewandt und nicht in der der Aufrufargumente — jedes Heft setzt
den Stand voraus, den das vorige hinterlässt. Fehlt einem das Datum, so bleibt es bei
der Aufrufreihenfolge; geraten wird nicht. Umgestellt wird nie stillschweigend: Die
Synopse nennt die gewählte Folge.

(4) Die Kette meint verschiedene Hefte. Dasselbe Heft ein zweites Mal auf seine
eigene Ausgabe anzuwenden ist keine Wiederholung ohne Folgen: Ein Befehl ist keine
Zustandsbeschreibung, sondern eine Anordnung, und wer zweimal anfügt, fügt zweimal
an. Dem Wortlaut allein ist das nicht anzusehen — die Befehle greifen ein zweites Mal
anstandslos. Die Fassung führt deshalb mit, welche Hefte auf ihr schon vollzogen
sind: Der kanonische Klartext trägt dafür im Kopf die Zeilen

```text
Stand: Zuletzt geändert durch Art. 6 G v. 12.5.2026 I Nr. 139
Fortgeschrieben durch: Änderungsgesetz vom 12. Februar 2026
```

und der Lader liest sie zurück. Trifft ein Heft ein zweites Mal auf dieselbe Fassung,
so wird das gerügt; abgebrochen wird nicht, denn verworfen wird hier nichts
(§ 1 Absatz 4). Ein Heft nennt sich dabei nach dem, was in ihm selbst steht — nach
seiner Drucksachennummer, sonst nach seinem Ausfertigungsdatum („Vom 12. Februar
2026“) —, und nicht nach seinem Dateinamen. Führt ein Sammelheft mehrere
Verkündungen, so bleibt es unbezeichnet: Welche von ihnen es ausmacht, sagt es nicht,
und ein falsch bezeichnetes Heft wäre schlimmer als ein unbezeichnetes.

(5) Aus dem Vermerk folgt zugleich, dass die Altersrüge (§ 1 Absatz 6) in der Kette
weiterträgt: Wer ein Heft anwendet, macht den Wortlaut so jung wie dieses Heft, und
ein weiteres Heft, das eine ältere Fassung fortschreiben will, wird als solches
erkannt — auch dann, wenn die Standzeile der Quelle davon noch nichts weiß.

(6) Ausgegeben wird allein, was das Datenmodell trägt; erfunden wird nichts. Für
Bundesrecht ist die Ausgabe deshalb eine getreue Wiedergabe des Wortlauts, aber kein
Rundlauf ins gii-XML: Jenes Format führt einen Fußnotenapparat und Standangaben, die
der Klartext nicht aufnimmt. Was der Klartext trägt, trägt er vollständig — dass der
Leser aus der Ausgabe dasselbe Gesetz wiedergewinnt, ist an sämtlichen
Klartext-Stammfassungen des Beispielkorpus geprüft (§ 17 Absatz 3).

## § 6b Abgleich mit der amtlichen Nachfassung

(1) Ob ein Lauf gelungen ist, entscheidet nicht die Zahl der angewandten Befehle,
sondern der Wortlaut hinterher. Der Schalter `--nachfassung` (§ 6 Absatz 2) nimmt die
amtliche Fassung nach dem Änderungsgesetz entgegen und stellt das Ergebnis Norm für
Norm dagegen:

```shell
java -jar target/aendggner-0.1.0-SNAPSHOT.jar ASOG-Bln-alt.txt GVBl-2026-17.pdf \
  --artikel 1 --nachfassung ASOG-Bln-neu.txt -o synopse.html
# → Abgleich mit der amtlichen Nachfassung: 171 von 171 Normen gleich
```

(2) Ausgewiesen werden die fehlenden, die überzähligen und die abweichenden Normen;
bei den abweichenden zeigt die Synopse den Unterschied wortweise unter der Aufschrift
**Abgleich mit der amtlichen Nachfassung**, links die amtliche, rechts die errechnete
Fassung. Verglichen wird nach Normalisierung des Leerraums: Wie ein Portal umbricht,
ist kein Rechtsinhalt — jedes Wort und jedes Satzzeichen dagegen schon. Die
Überschrift zählt zum Wortlaut, denn auf sie zielen eigene Befehle.

(3) Geht der Abgleich nicht auf, so endet der Lauf mit dem Rückgabewert 3. Das ist für
Massenläufe gedacht, die die Ausgabe nicht lesen.

(4) Es ist derselbe Abgleich, den die Akzeptanzprüfungen fahren (§ 17 Absatz 2).
Damit messen Werkzeug und Prüfung an einem Maßstab, und die Erschließung eines
weiteren Landes braucht keinen eigenen Prüfcode mehr: Stammfassung, Heft, Nachfassung
— das Erzeugnis sagt selbst, wo es danebenliegt.

## § 6c Probe auf die Inhaltsübersicht

(1) Ein Änderungsgesetz, das Paragraphen einfügt, aufhebt, umnummeriert oder ihre
Überschriften neu fasst, muss die Angaben der Inhaltsübersicht eigens mitändern; das
Handbuch der Rechtsförmlichkeit verlangt dafür eigene Befehle (§ 7 Absatz 2 Nummer 7).
Bleiben sie aus oder greifen sie nicht, so bleibt die Übersicht hinter dem Text
zurück — **ohne dass ein einziger Befehl liegenbliebe**. Das zeigt keine Zahl des
Protokolls an.

(2) Nach vollzogener Anwendung wird die Inhaltsübersicht deshalb gegen den Normbestand
gehalten und jede Abweichung gerügt. Geprüft wird allein der Unterschied: die Normen,
deren Bezeichnung oder Überschrift *dieser Lauf* verändert hat. Was die Quelle von sich
aus ungenau führt, geht die Probe nichts an — das wäre ein Befund über die Quelle und
nicht über den Lauf.

(3) Geheilt wird nichts. Die Inhaltsübersicht ist eine Norm wie jede andere; sie ohne
Befehl fortzuschreiben hieße, Recht zu erfinden.

(4) Die Rüge nennt den Befund und nicht dessen Ursache. Es kommt nämlich vor, dass die
amtliche Fassung selbst beides verschieden führt: Das Gebäudeenergiegesetz schreibt in
der Überschrift des § 71k „Gas“ und in der Angabe dazu „Erdgas“.

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
    soeben umnummerierte Einheit, weil ihr Zieltext nichts unterscheidet; eine
    Wortoperation dagegen löst zunächst norm-weit auf und fällt erst dann auf jene
    Einheit zurück, wenn die weite Suche mehrdeutig bleibt (§ 8 Absatz 5);
13. der Halbsatz als Ebene der Anfügung („wird der Punkt durch einen Strichpunkt
    ersetzt und folgender Halbsatz angefügt: „…““). Er beginnt keinen neuen Satz,
    sondern setzt den bestehenden hinter dem Strichpunkt fort; angefügt wird er
    deshalb nur, wenn der Zieltext tatsächlich auf einen Strichpunkt endet.
    „Strichpunkt“ gilt dabei als Nebenform des Semikolons;
14. der bezugspunktlose Chapeau-Lokator („In der Einleitung“, „Im Eingangssatz“) —
    er meint dasselbe wie „im Satzteil vor Nummer 1“ und trägt wie dieser keine
    eigene Stellenkomponente;
15. Verb-Rahmen, deren Unterpunkte allein die Fundstelle tragen („Es werden
    ersetzt: … in § 35 Absatz 3 die Angabe „X“ jeweils durch die Angabe „Y“,“),
    sowie
16. die Neufassung der Gesetzesüberschrift sowie
17. die Verweisung auf einen anderen Punkt desselben Artikels („Die
    Inhaltsübersicht wird entsprechend der vorstehenden Nummer 8 Buchst. a
    geändert“). Übertragen wird nicht der Wortlaut des verwiesenen Punktes,
    sondern sein **Ergebnis**: Jener ändert die Überschrift eines Paragraphen,
    und die Angabe der Inhaltsübersicht wird auf den Titel gesetzt, den der
    Paragraph danach trägt. Das trifft, was „entsprechend“ meint, und erspart es,
    jede Befehlsform ein zweites Mal auf dem Zeilenmodell der Übersicht
    nachzubilden. Zielt der verwiesene Punkt auf etwas anderes als eine
    Überschrift, so bleibt der Befehl liegen, und die Rüge sagt es: Die Übersicht
    führt allein Bezeichnung und Überschrift, und was im Absatz eines Paragraphen
    geschieht, hat in ihr kein Gegenstück.

18. die hamburgischen Nebenformen: „die Textstelle „…““ neben „die Wörter“ und
    „die Angabe“, „hinter“ neben „nach“ (als Ort der Einfügung wie als Wortanker)
    sowie die subjektlose Anfügung „Es wird folgender Absatz 3 angefügt: „…““,
    deren Ziel allein aus dem Rahmen folgt; ferner
19. die Neufassung einer Paragraphenfolge („§§ 6 und 7 erhalten folgende
    Fassung: „…““) — sie wird in Einzel-Neufassungen zerlegt.

(3) Landesrechtliche Befehlsformen bestimmt ergänzend § 13.

## § 8 Reihenfolge der Anwendung

(1) Angewandt wird nicht stur in Dokumentreihenfolge. Umnummerierungen beziehen
sich stets auf die ursprüngliche Zählung, nicht auf den Stand nach den
vorangegangenen Punkten; wer eine Bezeichnung räumt, kommt deshalb vor den, der
sie neu besetzt.

(2) Aus Absatz 1 folgt

1. die absteigende Abarbeitung einer aufsteigenden Kaskade („Der bisherige
   Absatz 3 wird Absatz 4“, „Der bisherige Absatz 4 wird Absatz 5“, …),
2. der Vorrang einer Umnummerierung vor derjenigen Einfügung, die deren
   Bezeichnung neu vergibt, sowie
3. der Vorrang einer Aufhebung vor demjenigen Schritt, der die aufgehobene
   Bezeichnung neu vergibt. Auch die Aufhebung räumt nämlich eine Bezeichnung;
   dass sie mitunter einen Platzhalter hinterlässt („9. (weggefallen)“) und dann
   gerade nichts freigibt, steht erst am Bestand fest und schadet nicht: Vorrang
   erhält sie nur gegenüber Schritten, die dieselbe Bezeichnung neu vergeben, und
   eine zugleich weggefallene und neu vergebene Bezeichnung gibt es nicht.

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

(5) Bleibt die Begleitklausel eines Verbunds bei norm-weiter Auflösung mehrdeutig,
so entscheidet die soeben umnummerierte Einheit. Der Vorrang bleibt bei der weiten
Suche, denn nicht jede Begleitklausel meint jene Einheit; findet sie gar keine
Fundstelle, so meint der Befehl etwas anderes und bleibt zu Recht liegen — nur die
Mehrdeutigkeit wird durch den engeren Skopus aufgelöst. Bleibt der Anker auch dort
mehrdeutig, so steht die Mehrdeutigkeit im Protokoll und nicht die Begründung des
zweiten Versuchs. Diese Regel gehört zur Anwendung und nicht zur Erkennung: Ob ein
Anker mehrdeutig ist, steht erst nach den vorangegangenen Punkten fest — beim Lesen
gibt es die neue Bezeichnung noch gar nicht.

(6) Ein Änderungsgesetz tritt nicht notwendig auf einen Schlag in Kraft. Der
Schlussartikel wird deshalb gelesen und jeder Befehl der besondersten Anordnung
zugeordnet, die ihn erfasst („Artikel 1 Nummer 2 Buchstabe c tritt am 19. Juni
2026 in Kraft“); im Übrigen gilt die Grundregel. Mit dem Schalter `--stichtag`
(§ 6 Absatz 2) ergibt sich die an einem bestimmten Tage geltende Fassung; die
noch nicht in Kraft getretenen Befehle bleiben dann unangewandt und werden
gesondert ausgewiesen. Ohne diese Angabe werden alle Befehle angewandt; tritt das
Gesetz gestaffelt in Kraft, so rügt die Synopse, dass die gezeigte Fassung an
keinem einzigen Tage so gegolten hat. Nennt eine Anordnung kein bestimmtes Datum,
sondern knüpft an die Verkündung an, so wird keines erfunden — der Verkündungstag
steht nicht im Gesetzestext.

## § 9 Aufbereitung der Druckwerke

(1) Die PDF-Aufbereitung toleriert die üblichen Drucksachen-Artefakte, nämlich
Seitenköpfe und -füße, Vorabfassungs-Wasserzeichen, vertauschte oder gerade
Anführungszeichen, verklebte Wortgrenzen und zerlegt kodierte Umlaute. Zum
Leerraum zählen dabei auch die typographischen Ausschlüsse, die Javas Begriff
des Leerzeichens nicht kennt: Das hamburgische Gesetzblatt setzt zwischen
Paragraphenzeichen und Nummer ein schmales Leerzeichen (U+2009), und ohne dessen
Normalisierung wäre „§ 1“ dort kein Normkopf. Die
Brotschrift wird seitenweise bestimmt, sodass auch Ministeriumsentwürfe mit
gemischten Layouts vollständig ausgelesen werden.

(2) Die Reihenfolge, in der gelesen wird, ergibt sich aus dem Satzbild und nicht
aus dem Inhaltsstrom. Gesucht wird die **Rinne** zwischen den beiden Spalten:
die senkrechte Linie, die möglichst wenige Zeilen überschreiten, die nahe der
Mitte des Satzspiegels liegt und zu deren beiden Seiten je eine Spalte von
nennenswerter Breite steht. Die wenigen Zeilen, die sie gleichwohl
überschreiten, sind die ganzseitenbreiten — Kolumnentitel, Seitenfuß,
Titelblock —; sie zerlegen die Seite in Bänder. Gelesen wird Band für Band von
oben nach unten, in jedem Band erst die linke, dann die rechte Spalte, jede von
oben nach unten. Findet sich keine Rinne, so bleibt es beim Inhaltsstrom.

Innerhalb einer Spalte folgt die Lesung der Grundlinie und nicht dem Strom. Wo
dieser ohnehin von oben nach unten läuft — der Regelfall —, ändert das nichts,
denn die Sortierung ist stabil; wo er es nicht tut, bewahrt sie vor dem
Schlimmsten. Im hamburgischen Gesetzblatt fällt das Schlusswort der
Eingangsformel („verordnet:“) mitten in einen Absatz der rechten Spalte und
zerschneidet dort ein Wort.

(3) Absatz 2 ist keine Förmelei. Das Berliner Gesetz- und Verordnungsblatt
zeichnet den Titelblock des Gesetzes **zuletzt**, obgleich er über beiden
Spalten steht; im Inhaltsstrom stand er damit mitten in einem Zitat, das über
den Seitenwechsel läuft, und der Wortlaut der Anlage trug den Titel des
Änderungsgesetzes in sich. Der übliche XY-Schnitt der Literatur — erst
waagerecht am weitesten Weißraumband, dann senkrecht — leistet das nicht: Auf
einer zweispaltigen Seite mit Kolumnentitel liegt das weiteste Band regelmäßig
mitten im Satzspiegel; gelesen würde alsdann links oben, rechts oben, links
unten, rechts unten. Die Spalte hat deshalb den Vorrang vor dem Band.

(4) Fehlt im amtlichen Satz ein schließendes Anführungszeichen, so endet das
Zitat an der nächsten Strukturgrenze, nämlich an einer Artikel-Überschrift oder —
dort, wo die Anführungszeichen eines Artikels nachweislich nicht aufgehen — am
nächsten Aufzählungspunkt des Änderungsdokuments. Der Vorgang wird als Warnung
gemeldet.

(5) Auf den Beispieldaten (§ 17 Absatz 2) werden hiernach alle Befehle der
Fassungen des Bundesgesetzblattes und der aktuellen Entwürfe angewandt; für
UWG, AGG, ProdHaftG und — seit dieser Welle — für den Artikel 1 der GEG-Novelle
mit seinen 119 Befehlen verbleibt kein Befehl zur Prüfung von Hand. Im Übrigen
gilt § 1 Absatz 4.

(6) Bleibt die Aufbereitung im Einzelfall fehlerhaft, so ist nach § 6 Absatz 2
mittels des Schalters `--extract-only` zu verfahren; in der Browserfassung
mittels des Ankreuzfeldes nach § 14 Absatz 2.

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
Überschriften und deren Teile schreiben ihr Ordinale aus), Schleswig-Holstein
(Gemeindeordnung und Kreisordnung, zwei Stammgesetze in einem Änderungsgesetz) und
Nordrhein-Westfalen — dort alle vier ändernden Artikel eines Heftes:
Telemedienzuständigkeitsgesetz, Landesmediengesetz, Ausführungsgesetz zum
17. Rundfunkänderungsstaatsvertrag und, mit 101 Befehlen an 31 Normen der größte
Fall des Heftes, das WDR-Gesetz —, jeweils mit Akzeptanztests gegen die amtlichen
Nachfassungen.

(2a) Hamburg bringt zwei Eigenheiten, die im übrigen Landesrecht nicht vorkommen.
Zum einen gliedert sich dort das *Änderungsdokument* in Paragraphen (§ 1 Änderung,
§ 2 Inkrafttreten), während dasselbe Heft davor eine Verkündung führt, die sich in
Artikel gliedert; ein Sammelheft trägt also beide Gliederungen nebeneinander, und
die §-Teilung greift deshalb nicht erst dann, wenn ein Heft überhaupt keinen
Artikel führt, sondern schon dann, wenn kein Artikel das Stammgesetz betrifft. Zum
anderen sind die Befehle **dezimal** gegliedert („6.1“, „7.1.1“, „7.2.3“), wo die
übrigen Blätter a)/aa)/aaa) setzen; die Ebene steht dabei in der Zahl selbst, und
der Gliederungspfad wiederholt sie nicht. Der Fall (Verordnung über den
Gutachterausschuss für Grundstückswerte, HmbGVBl. Nr. 17/2026) ist gegen die
amtliche Nachfassung belegt: 21 Befehle gelesen, 17 angewandt, zehn von vierzehn
Normen gleich. Von den vier Abweichungen liegen drei an der Vorlage — zwei Befehle
schreiben „wir“ statt „wird“, und in einem dritten Fall setzt das Gesetzblatt ein
Komma, das das Portal führt, nicht.

(3) Die meisten Landesportale geben ihre Stammfassungen einer Skriptabfrage nicht
aus, sondern nur ihre leere Anwendungshülle; einem Browser dagegen schon, und auf
diesem Wege sind die hessische, die beiden schleswig-holsteinischen und die
Berliner Stammfassung beschafft worden — letztere einschließlich der Anlage, deren
Nummern als eigene Normen geführt werden („Anlage Nummer 23“), sodass beide Artikel
des Berliner Gesetzes volle Akzeptanzfälle sind. Brandenburg macht die Ausnahme:
BRAVORS gibt seine Vorschriften als gewöhnliches HTML aus und führt unter
„Änderungshistorie“ jede Fassung einzeln, sodass Vor- und Nachfassung derselben
Quelle entstammen (Fraktionsgesetz, alle vier Befehle, alle 24 Normen).
Thüringens Portal gibt sie ebensowenig aus; dort ist die konsolidierte
Fassung aus dem Stammheft und den drei Änderungsheften des Gesetzblattes
zusammengesetzt, die die Parlamentsdatenbank des Landtags frei ausgibt.

(4) Nicht jedes Portal gibt frühere Fassungen überhaupt her. Rheinland-Pfalz,
das Saarland und Sachsen-Anhalt führen nur die aktuelle Gesamtausgabe nebst einer
Aufzählung der Änderungen; die Fassungsliste, über die Hessen, Schleswig-Holstein,
Berlin und Baden-Württemberg jede Fassung datierbar ausgeben, fehlt dort. Für
Rheinland-Pfalz (Ausbildungs- und Prüfungsordnung der Unfallkasse) reicht die
Prüfung deshalb nur bis zur Befehlserkennung — nicht weil das Werkzeug es nicht
könnte, sondern weil die Vorfassung nicht zu beschaffen ist. Der Fall hat
gleichwohl vier allgemeine Funde gebracht: die Nebenform „die Worte“ für „die
Wörter“, den Seitenfuß des dortigen Gesetzblattes, der im Inhaltsstrom das
Zitat einer Neufassung zerschneidet, den Strichpunkt samt Halbsatz-Anfügung
(§ 7 Absatz 2 Nummer 13) und den bezugspunktlosen Chapeau-Lokator (§ 7 Absatz 2
Nummer 14). Auch die Verweisung auf einen anderen
Punkt desselben Artikels (§ 7 Absatz 2 Nummer 17) stammt von dort; sie wird
gelesen und ausdrücklich als Grenze gerügt. Sämtliche 23 Befehle des Heftes sind
damit erschlossen.

(5) Welche Konvention welches Land beisteuert, welche Stammfassungen woher
stammen und was noch offen ist, verzeichnet
`src/test/resources/sampledata/Landesrecht-Beispiele.adoc`.

## § 14 Browserfassung

(1) Neben der Befehlszeilenfassung besteht eine Browserfassung, die dieselbe
Pipeline (`eu.mulk.aendggner.Pipeline`, § 16 Absatz 1) über ein
Upload-Formular zugänglich macht: Stammgesetz- und Änderungsgesetz-Datei(en)
wählen, Synopse erhalten.

(2) Der Vordruck stellt außer den beiden Dateifeldern dieselben Angaben zur
Verfügung wie die Befehlszeile nach § 6 Absatz 2, nämlich das Feld
„Anzuwendender Artikel“ (`--artikel`), das Feld „Stichtag“ (`--stichtag`), das
Dateifeld „Amtliche Nachfassung“ (`--nachfassung`), das Ankreuzfeld
„Unveränderte Vorschriften mit in die Synopse aufnehmen“ (`--vollstaendig`), das
Ankreuzfeld „Auch die fortgeschriebene Fassung als Klartext ausgeben“
(`--neufassung`) und das
Ankreuzfeld „Statt der Synopse nur den maschinell gelesenen Text der
Änderungsdokumente ausgeben“ (`--extract-only`). Das letztgenannte ist kein
Zierat: Ohne es hätte, wer im Browser arbeitet, keine Möglichkeit, einem
unerklärlich unangewandten Befehl auf den Grund zu gehen. Der ausgegebene Text
ist derselbe, den der Parser bekommt; er lässt sich sichern, von Hand
berichtigen und als Klartextdatei wieder einreichen. Ebensowenig Zierat ist die
Ausgabe der fortgeschriebenen Fassung: Ohne sie bliebe die Kette nach § 6a
Absatz 2 der Befehlszeile vorbehalten.

(3) Von diesen Angaben stehen die selten auszufüllenden — „Anzuwendender
Artikel“, „Stichtag“, die Nachfassung, die Textausgabe und die Ausgabe der
fortgeschriebenen Fassung — hinter der zugeklappten Aufschrift
„Weitere Angaben“; die beiden Dateifelder und das Ankreuzfeld für die
unveränderten Vorschriften bleiben offen im Vordruck. Dieses Ankreuzfeld ist
abweichend von der Befehlszeile vorangekreuzt: Wer eine Synopse ansieht, will in
aller Regel das ganze Gesetz vor sich haben und nicht nur seine geänderten
Stellen. Beim Drucken klappt der Abschnitt selbsttätig auf, damit der Vordruck
vollständig aufs Papier kommt, und schließt sich danach wieder.

(4) Über dem Formular steht ein zugeklappter Einführungsblock, der zwei
durchgerechnete Fälle mit Verweisen auf die amtlichen Fundstellen anbietet,
nämlich das UWG (`gesetze-im-internet.de/uwg_2004/xml.zip`) mit dem Dritten Gesetz
zu seiner Änderung (BGBl. 2026 I Nr. 43) und das AGG
(`gesetze-im-internet.de/agg/xml.zip`) mit dem Regierungsentwurf BT-Drs. 21/6178.
Mitausgeliefert wird nichts; die Seite ruft die Dateien auch nicht selbst ab, sie
verweist nur darauf.

(5) Ein Server ist nicht erforderlich: Die vollständige Verarbeitung —
PDF-Textgewinnung mit PDFBox eingeschlossen — läuft als WebAssembly-Modul im
Browser, übersetzt mit GraalVM Web Image aus demselben Java-Quelltext.
Ausgeliefert werden nur statische Dateien; die gewählten Dokumente verlassen den
Rechner der Nutzer:innen nicht. Die Herstellung verlangt abweichend von § 4
Absatz 3 Oracle GraalVM 25.1 oder neuer, denn Web Image ist nur dort enthalten,
nicht in der Community Edition:

```shell
JAVA_HOME=/pfad/zu/oracle-graalvm ./mvnw -Pwasm package
```

(6) Ergebnis ist `target/web/` mit `index.html`, `app.js`, `worker.js`,
`style.css`, `favicon.svg`, `aendggner.js` und `aendggner.js.wasm` (rund 17 MB,
komprimiert etwa 5 MB). Das Verzeichnis ist so, wie es dasteht, auslieferbar:
`deploy/webpaket.sh` läuft am Ende desselben Befehls, wirft den mehrere hundert
Megabyte großen Textzwischenschritt `aendggner.js.wat` fort, schickt das Modul
durch `wasm-opt -Oz` und legt den Quelltext der gebauten Fassung als
`aendggner-quelltext.tar.gz` samt `quelltext-fassung.txt` bei.

(7) Die Größe des Moduls beruht auf vier Vorkehrungen; wer eine davon zurücknimmt,
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

(8) Im Quelltextarchiv fehlt der Beispielkorpus; `.gitattributes` nimmt
`src/test/resources/sampledata` von `git archive` aus. Es sind Gesetzes- und
Drucksachentexte fremder Urheberschaft, an denen allein die Tests messen —
Quelltext im Sinne der AGPLv3 sind sie nicht, gebaut wird ohne sie, und sie
machten das Archiv dreißigmal so groß wie den Quelltext (29,7 MB statt 0,26 MB).
`quelltext-fassung.txt` sagt dies und verweist für den vollständigen Korpus auf
die Anschrift nach § 3 Absatz 2. Wächst das Archiv wieder über 8 MiB, so bricht
`webpaket.sh` ab; alsdann sind Massendaten ins Repository geraten, die dort nicht
hingehören.

(9) Trägt der Arbeitsbaum uneingecheckte Änderungen, so bricht die Herstellung
nach Absatz 4 ab, denn der beigelegte Quelltext wäre alsdann nicht der gebaute.
Für einen Probelauf hilft `QUELLTEXT_UNGEPRUEFT=1`.

(10) Zur örtlichen Ansicht genügt `file://` nicht; Browser laden Wasm-Module und
Worker nur über HTTP (<http://localhost:8000/>):

```shell
jwebserver -d target/web
```

(11) Die Befehlszeilenfassung (§ 6) bleibt hiervon unberührt und ist weiterhin der
Weg für Massenläufe.

## § 15 Ausrollen

(1) Erforderlich ist nur ein Ort für statische Dateien. Ausgeliefert wird über
Cloudflare Workers. Dort gilt eine Grenze von 25 MiB je Datei — unkomprimiert
gemessen —, und komprimiert wird beim Ausliefern ohnehin. `webpaket.sh` legt
deshalb keine `.gz`/`.br`-Beilagen mehr an und hält am Ende jede Datei gegen
diese Grenze; überschreitet eine sie, so bricht der Bau ab, statt das Hochladen
scheitern zu lassen. Die Phase `package` baut nur; `deploy` lädt das Gebaute
überdies mit `wrangler` nach `wrangler.toml` hoch:

```shell
JAVA_HOME=/pfad/zu/oracle-graalvm ./mvnw -Pwasm package   # nur bauen
JAVA_HOME=/pfad/zu/oracle-graalvm ./mvnw -Pwasm deploy    # bauen und ausliefern
```

Vorausgesetzt wird ein angemeldetes `wrangler` im Pfad. Ohne `-Pwasm` liefert
`deploy` nichts aus: Das Erzeugnis geht in kein Maven-Repositorium, weshalb das
Ziel `deploy:deploy` stillgelegt ist und die Phase allein der Auslieferung der
Browserfassung dient.

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
Ergebnis gegen die amtliche Nachfassung stellen — mit demselben Abgleich, den
`--nachfassung` fährt (§ 6b Absatz 4). Die hierfür verwendeten
Beispieldaten nebst Herkunftsnachweis (`SOURCES`) liegen unter
`src/test/resources/sampledata/`.

(3) Der Textausgeber (§ 6a) wird nicht am Augenschein geprüft, sondern am
**Rundlauf**: Was er schreibt, muss der Lader wieder zu demselben Gesetz lesen.
Geprüft wird das an sämtlichen Klartext-Stammfassungen des Beispielkorpus und
überdies am Bundesrecht, dessen Ausgangsformat das gii-XML ist. Der Rundlauf ist
kein Selbstzweck: Er deckt gerade die Verluste auf, die ein Ausgeber sonst
stillschweigend einbaut, und hat auf Anhieb zwei Lücken des Lesers zutage
gefördert — die unvollständige Nachfolgerliste der Unter-Überschriften und die
aufgehobene Sammelnorm des Bundesrechts („§§ 17 u. 18 (weggefallen)“), deren
Kopfzeile zuvor in den Wortlaut der Vornorm gefallen wäre. Der Rundlauf umfasst
auch den Kopf: Standangabe und angewandte Hefte müssen ihn überstehen, sonst
verlöre die Kette bei jedem Schritt ihr Gedächtnis (§ 6a Absatz 4).

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
