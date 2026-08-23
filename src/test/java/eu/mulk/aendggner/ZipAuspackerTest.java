// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Die Archive werden zur Laufzeit gebaut statt als Binärdatei abgelegt: So steht neben jedem Fall,
 * was ihn ausmacht, und der Quellbaum bleibt frei von undurchsichtigen Beilagen.
 */
class ZipAuspackerTest {

  private static final Path MINI_GII = Path.of("src/test/resources/eu/mulk/aendggner/mini-gii.xml");

  @Test
  void packtDenXmlEintragAus() throws Exception {
    var xml = Files.readAllBytes(MINI_GII);
    var archiv = zip(Map.of("BJNR000000000.xml", xml), ZipEntry.DEFLATED);

    var ausgepackt = ZipAuspacker.auspacken(new Quelle("xml.zip", archiv));

    assertThat(ausgepackt.inhalt()).isEqualTo(xml);
    // Der Name folgt dem Eintrag, nicht der Verpackung: Protokoll und Ladefehler sollen das
    // Gesetz benennen.
    assertThat(ausgepackt.name()).isEqualTo("BJNR000000000.xml");
  }

  @Test
  void packtAuchUnkomprimierteEintraegeAus() throws Exception {
    var xml = Files.readAllBytes(MINI_GII);
    var archiv = zip(Map.of("BJNR000000000.xml", xml), ZipEntry.STORED);

    assertThat(ZipAuspacker.auspacken(new Quelle("xml.zip", archiv)).inhalt()).isEqualTo(xml);
  }

  @Test
  void uebergehtBeiwerkNebenDemXml() throws Exception {
    var xml = Files.readAllBytes(MINI_GII);
    // So sehen die Archive der Gesetze mit Anlagen aus: neben dem Norm-XML liegen Bilddateien.
    var eintraege = new LinkedHashMap<String, byte[]>();
    eintraege.put("__MACOSX/", new byte[0]);
    eintraege.put("BJNR000000000.xml", xml);
    eintraege.put("anlage_1.gif", new byte[] {'G', 'I', 'F', '8'});

    assertThat(ZipAuspacker.auspacken(new Quelle("xml.zip", zip(eintraege, ZipEntry.DEFLATED))))
        .extracting(Quelle::name)
        .isEqualTo("BJNR000000000.xml");
  }

  @Test
  void reichtNichtArchiveUnveraendertDurch() throws Exception {
    var quelle = new Quelle("stamm.xml", Files.readAllBytes(MINI_GII));

    assertThat(ZipAuspacker.auspacken(quelle)).isSameAs(quelle);
  }

  @Test
  void meldetArchivOhneXml() {
    var archiv = zip(Map.of("liesmich.txt", "nichts hierin".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> ZipAuspacker.auspacken(new Quelle("xml.zip", archiv)))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("keine XML-Datei");
  }

  @Test
  void meldetArchivMitMehrerenXml() {
    var eintraege = new LinkedHashMap<String, byte[]>();
    eintraege.put("eins.xml", "<a/>".getBytes(StandardCharsets.UTF_8));
    eintraege.put("zwei.xml", "<b/>".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> ZipAuspacker.auspacken(new Quelle("xml.zip", zip(eintraege))))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("eins.xml, zwei.xml");
  }

  @Test
  void meldetBeschaedigtesArchiv() throws Exception {
    var archiv = zip(Map.of("BJNR000000000.xml", Files.readAllBytes(MINI_GII)));
    // Das Zentralverzeichnis steht am Ende; ein abgeschnittenes Archiv verliert es.
    var abgeschnitten = new byte[archiv.length / 2];
    System.arraycopy(archiv, 0, abgeschnitten, 0, abgeschnitten.length);

    assertThatThrownBy(() -> ZipAuspacker.auspacken(new Quelle("xml.zip", abgeschnitten)))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("beschädigt");
  }

  @Test
  void gezipptesStammgesetzErgibtDasselbeGesetz() throws Exception {
    var xml = Files.readAllBytes(MINI_GII);

    var direkt = Pipeline.ladeStammgesetz(new Quelle("mini-gii.xml", xml));
    var gezippt = Pipeline.ladeStammgesetz(new Quelle("xml.zip", zip(Map.of("mini-gii.xml", xml))));

    assertThat(gezippt.normen()).isEqualTo(direkt.normen());
    assertThat(gezippt.jurabk()).isEqualTo(direkt.jurabk());
  }

  private static byte[] zip(Map<String, byte[]> eintraege) {
    return zip(eintraege, ZipEntry.DEFLATED);
  }

  private static byte[] zip(Map<String, byte[]> eintraege, int verfahren) {
    var aus = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(aus)) {
      for (var eintrag : eintraege.entrySet()) {
        var kopf = new ZipEntry(eintrag.getKey());
        kopf.setMethod(verfahren);
        if (verfahren == ZipEntry.STORED) {
          // Gespeicherte Einträge verlangen Größe und Prüfsumme vorab.
          var pruefsumme = new CRC32();
          pruefsumme.update(eintrag.getValue());
          kopf.setSize(eintrag.getValue().length);
          kopf.setCompressedSize(eintrag.getValue().length);
          kopf.setCrc(pruefsumme.getValue());
        }
        zip.putNextEntry(kopf);
        zip.write(eintrag.getValue());
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new AssertionError("Das Testarchiv ließ sich nicht bauen", e);
    }
    return aus.toByteArray();
  }
}
