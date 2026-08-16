package eu.mulk.aendggner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Holt das gii-Norm-XML aus einem Archiv heraus, wie gesetze-im-internet.de es ausliefert.
 *
 * <p>Das Portal gibt kein rohes XML aus, sondern je Gesetz nur {@code …/<kurz>/xml.zip}. Ohne
 * diesen Schritt müsste jede:r die heruntergeladene Datei erst von Hand entpacken, bevor sie ins
 * Formular passt — die Einführung der Browserfassung verweist deshalb unmittelbar auf das Archiv.
 *
 * <p>Das ZIP-Format wird hier von Hand gelesen statt mit {@link java.util.zip.ZipInputStream}: Von
 * allen zip-Klassen des JDK trägt in der Wasm-Fassung allein {@link Inflater}, den {@code
 * eu.mulk.aendggner.wasm.InflaterErsatz} durch die reine Java-Umsetzung von jzlib ersetzt. {@code
 * ZipInputStream}, {@code ZipFile} und {@code CRC32} greifen auf native Bindungen zurück, die Web
 * Image nicht kennt; die Prüfsumme rechnet deshalb {@link #crc32} selbst.
 *
 * <p>Gelesen wird über das Zentralverzeichnis am Dateiende, nicht über die Local Header: Bei
 * Einträgen mit Data Descriptor stehen Größe und Prüfsumme im Local Header auf null und erst hinter
 * den komprimierten Daten — das Zentralverzeichnis trägt sie immer.
 */
public final class ZipAuspacker {

  /** „PK\05\06“ — Kennung des End-of-Central-Directory-Records. */
  private static final int EOCD_SIGNATUR = 0x06054b50;

  /** „PK\01\02“ — Kennung eines Eintrags im Zentralverzeichnis. */
  private static final int ZENTRAL_SIGNATUR = 0x02014b50;

  /** „PK\03\04“ — Kennung eines Local Headers. */
  private static final int LOKAL_SIGNATUR = 0x04034b50;

  /** Fester Teil des EOCD-Records; dahinter steht nur noch der Archivkommentar. */
  private static final int EOCD_MINDESTLAENGE = 22;

  /** Der Kommentar ist auf 64 KiB begrenzt; weiter zurück muss die Suche nach dem EOCD nicht. */
  private static final int KOMMENTAR_MAX = 0xFFFF;

  private static final int VERFAHREN_GESPEICHERT = 0;
  private static final int VERFAHREN_DEFLATE = 8;

  /** Bit 0 des Allgemeinen Kennzeichens: Der Eintrag ist verschlüsselt. */
  private static final int KENNZEICHEN_VERSCHLUESSELT = 1;

  private ZipAuspacker() {}

  /**
   * Liefert bei einem Archiv den enthaltenen XML-Eintrag, sonst die Quelle unverändert.
   *
   * <p>Die gelieferte Quelle trägt den Eintragsnamen, damit Protokoll und Ladefehler das Gesetz
   * benennen statt seine Verpackung. Die Quellenzeile der Synopse bleibt davon unberührt: Sie nennt
   * weiterhin die Datei, die angegeben wurde ({@code uwg.zip}), denn sie soll den Weg zur
   * Fundstelle zurück beschreiben.
   */
  public static Quelle auspacken(Quelle quelle) throws IOException {
    if (DateiTyp.erkenne(quelle.inhalt()) != DateiTyp.ZIP) {
      return quelle;
    }

    var eintraege = zentralverzeichnis(quelle);
    var xml = einzigesXml(eintraege, quelle.name());
    return new Quelle(xml.name(), entpacke(quelle.inhalt(), xml, quelle.name()));
  }

  /** Ein Eintrag des Zentralverzeichnisses, soweit hier gebraucht. */
  private record Eintrag(
      String name,
      int verfahren,
      int kennzeichen,
      long crc,
      int gepackt,
      int entpackt,
      int offset) {

    String dateiname() {
      int schraeg = name.lastIndexOf('/');
      return schraeg < 0 ? name : name.substring(schraeg + 1);
    }
  }

  private static List<Eintrag> zentralverzeichnis(Quelle quelle) throws IOException {
    var bytes = quelle.inhalt();
    int eocd = sucheEocd(bytes, quelle.name());

    int anzahl = u16(bytes, eocd + 10);
    int anfang = (int) u32(bytes, eocd + 16);

    var eintraege = new ArrayList<Eintrag>(anzahl);
    int p = anfang;
    for (int i = 0; i < anzahl; i++) {
      if (p < 0 || p + 46 > bytes.length || (int) u32(bytes, p) != ZENTRAL_SIGNATUR) {
        throw beschaedigt(quelle.name(), "das Zentralverzeichnis bricht ab");
      }
      int namensLaenge = u16(bytes, p + 28);
      int zusatzLaenge = u16(bytes, p + 30);
      int kommentarLaenge = u16(bytes, p + 32);
      if (p + 46 + namensLaenge > bytes.length) {
        throw beschaedigt(quelle.name(), "ein Eintragsname reicht über das Dateiende hinaus");
      }
      eintraege.add(
          new Eintrag(
              // Ohne gesetztes Bit 11 des Allgemeinen Kennzeichens schreibt das Format CP437;
              // die hier vorkommenden Namen sind reines ASCII, für das beides zusammenfällt.
              new String(bytes, p + 46, namensLaenge, StandardCharsets.UTF_8),
              u16(bytes, p + 10),
              u16(bytes, p + 8),
              u32(bytes, p + 16),
              (int) u32(bytes, p + 20),
              (int) u32(bytes, p + 24),
              (int) u32(bytes, p + 42)));
      p += 46 + namensLaenge + zusatzLaenge + kommentarLaenge;
    }
    return eintraege;
  }

  /** Sucht den EOCD-Record von hinten: Vor ihm steht nur noch der Archivkommentar. */
  private static int sucheEocd(byte[] bytes, String name) throws IOException {
    int frueheste = Math.max(0, bytes.length - EOCD_MINDESTLAENGE - KOMMENTAR_MAX);
    for (int p = bytes.length - EOCD_MINDESTLAENGE; p >= frueheste; p--) {
      if ((int) u32(bytes, p) == EOCD_SIGNATUR) {
        return p;
      }
    }
    throw beschaedigt(name, "das Ende des Zentralverzeichnisses fehlt");
  }

  private static Eintrag einzigesXml(List<Eintrag> eintraege, String archiv) throws IOException {
    var xml = new ArrayList<Eintrag>();
    for (var eintrag : eintraege) {
      // Verzeichniseinträge tragen keinen Inhalt; „__MACOSX/“ ist Beiwerk, das macOS beim Packen
      // hinzufügt und dessen Namen die der echten Einträge spiegeln.
      if (eintrag.name().endsWith("/") || eintrag.name().startsWith("__MACOSX/")) {
        continue;
      }
      // gii legt Gesetzen mit Anlagen deren Bilddateien mit ins Archiv — es ist deshalb nicht
      // einerlei, welcher Eintrag genommen wird.
      if (eintrag.dateiname().toLowerCase().endsWith(".xml")) {
        xml.add(eintrag);
      }
    }

    if (xml.isEmpty()) {
      throw new IOException(
          "Das Archiv %s enthält keine XML-Datei; erwartet wird das gii-Norm-XML von"
                  .formatted(archiv)
              + " gesetze-im-internet.de (xml.zip)");
    }
    if (xml.size() > 1) {
      throw new IOException(
          "Das Archiv %s enthält mehrere XML-Dateien (%s); bitte die gewünschte entpackt angeben"
              .formatted(archiv, String.join(", ", xml.stream().map(Eintrag::name).toList())));
    }
    return xml.getFirst();
  }

  private static byte[] entpacke(byte[] bytes, Eintrag eintrag, String archiv) throws IOException {
    if ((eintrag.kennzeichen() & KENNZEICHEN_VERSCHLUESSELT) != 0) {
      throw new IOException(
          "Der Eintrag %s im Archiv %s ist verschlüsselt".formatted(eintrag.name(), archiv));
    }

    // Der Local Header wiederholt Name und Zusatzfeld, und zwar mit eigenen Längen — die Daten
    // beginnen erst dahinter.
    int lokal = eintrag.offset();
    if (lokal < 0 || lokal + 30 > bytes.length || (int) u32(bytes, lokal) != LOKAL_SIGNATUR) {
      throw beschaedigt(
          archiv,
          "der Eintrag %s steht nicht dort, wo das Zentralverzeichnis ihn führt"
              .formatted(eintrag.name()));
    }
    int daten = lokal + 30 + u16(bytes, lokal + 26) + u16(bytes, lokal + 28);
    if (daten < 0 || eintrag.gepackt() < 0 || daten + eintrag.gepackt() > bytes.length) {
      throw beschaedigt(
          archiv, "der Eintrag %s reicht über das Dateiende hinaus".formatted(eintrag.name()));
    }

    var inhalt =
        switch (eintrag.verfahren()) {
          case VERFAHREN_GESPEICHERT -> {
            var kopie = new byte[eintrag.gepackt()];
            System.arraycopy(bytes, daten, kopie, 0, eintrag.gepackt());
            yield kopie;
          }
          case VERFAHREN_DEFLATE ->
              inflate(bytes, daten, eintrag.gepackt(), eintrag.entpackt(), eintrag.name(), archiv);
          default ->
              throw new IOException(
                  "Der Eintrag %s im Archiv %s ist mit dem unbekannten Verfahren %d gepackt"
                      .formatted(eintrag.name(), archiv, eintrag.verfahren()));
        };

    if (crc32(inhalt) != eintrag.crc()) {
      throw beschaedigt(
          archiv, "die Prüfsumme des Eintrags %s stimmt nicht".formatted(eintrag.name()));
    }
    return inhalt;
  }

  /**
   * Rohes Deflate ohne zlib-Rahmen — daher {@code new Inflater(true)}. Die Ausgabegröße steht im
   * Zentralverzeichnis, es wird also genau einmal ausgelesen und nicht nachgewachsen.
   */
  private static byte[] inflate(
      byte[] bytes, int von, int gepackt, int entpackt, String eintrag, String archiv)
      throws IOException {
    if (entpackt < 0) {
      throw beschaedigt(
          archiv, "der Eintrag %s gibt eine unbrauchbare Größe an".formatted(eintrag));
    }
    var inflater = new Inflater(true);
    try {
      inflater.setInput(bytes, von, gepackt);
      var aus = new byte[entpackt];
      int gefuellt = 0;
      while (gefuellt < entpackt) {
        int geschrieben = inflater.inflate(aus, gefuellt, entpackt - gefuellt);
        if (geschrieben == 0 && (inflater.finished() || inflater.needsInput())) {
          throw beschaedigt(
              archiv, "der Eintrag %s endet vor der angegebenen Größe".formatted(eintrag));
        }
        gefuellt += geschrieben;
      }
      return aus;
    } catch (DataFormatException e) {
      throw new IOException(
          "Der Eintrag %s im Archiv %s lässt sich nicht entpacken: %s"
              .formatted(eintrag, archiv, e.getMessage()),
          e);
    } finally {
      inflater.end();
    }
  }

  /**
   * CRC-32 (IEEE 802.3) in reinem Java. {@code java.util.zip.CRC32} ruft eine native Routine des
   * JDK auf, die Web Image nicht kennt; die Tabelle wird deshalb hier aufgebaut.
   */
  private static long crc32(byte[] daten) {
    long crc = 0xFFFFFFFFL;
    for (var b : daten) {
      crc ^= b & 0xFF;
      for (int i = 0; i < 8; i++) {
        crc = (crc >>> 1) ^ (0xEDB88320L & -(crc & 1));
      }
    }
    return crc ^ 0xFFFFFFFFL;
  }

  private static IOException beschaedigt(String archiv, String grund) {
    return new IOException("Das Archiv %s ist beschädigt: %s".formatted(archiv, grund));
  }

  private static int u16(byte[] bytes, int p) {
    return (bytes[p] & 0xFF) | ((bytes[p + 1] & 0xFF) << 8);
  }

  private static long u32(byte[] bytes, int p) {
    return (bytes[p] & 0xFFL)
        | ((bytes[p + 1] & 0xFFL) << 8)
        | ((bytes[p + 2] & 0xFFL) << 16)
        | ((bytes[p + 3] & 0xFFL) << 24);
  }
}
