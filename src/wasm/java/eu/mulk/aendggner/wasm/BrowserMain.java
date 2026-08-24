// SPDX-FileCopyrightText: 2020 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later
package eu.mulk.aendggner.wasm;

import eu.mulk.aendggner.Pipeline;
import eu.mulk.aendggner.Quelle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSValue;

/**
 * Einstiegspunkt der Browserfassung: stellt {@code globalThis.aendggnerSynopse} bereit und ruft
 * damit dieselbe {@link Pipeline} auf wie Befehlszeile und Tests.
 *
 * <p>Erwartet ein JS-Objekt <code>{stamm: {name, base64}, patches: [{name, base64}], artikel,
 * stichtag, vollstaendig, nurText}</code> und liefert <code>{html, angewandt, manuell,
 * normen}</code> — bei <code>nurText</code> stattdessen <code>{text}</code> — oder <code>{fehler}
 * </code> zurück. Geworfen wird nichts: Eine Ausnahme im Wasm hinterlässt auf der JS-Seite nur
 * einen unlesbaren Stapel, also wird jeder Fehler als Text zurückgereicht.
 *
 * <p>Der Dateiinhalt wandert als Base64-Text über die Grenze, nicht als {@code Uint8Array}: Die
 * Umsetzung typisierter Felder nach {@code byte[]} ist in Web Image derzeit defekt („byteArrayHub
 * is not defined“). Zeichenketten überqueren die Grenze zuverlässig, und die Base64-Dekodierung ist
 * reines Java.
 */
public final class BrowserMain {

  private BrowserMain() {}

  public static void main(String... args) {
    // JULs Standardformatter ermittelt den Aufrufer über StackWalker, den Web Image nicht kennt;
    // im Browser gibt es ohnehin kein Logdatei-Ziel.
    LogManager.getLogManager().reset();
    Logger.getLogger("").setLevel(Level.OFF);

    exportiere(BrowserMain::synopse);

    // Die Erreichbarkeitsanalyse sieht nur Aufrufe aus Java; dass JavaScript die exportierte
    // Funktion aufruft, weiß sie nicht — ohne diesen (nie durchlaufenen) Zweig bliebe die
    // gesamte Pipeline aus dem Image heraus und der erste Aufruf endete in einem
    // NoClassDefFoundError.
    melde();
  }

  @JS(args = "fn", value = "globalThis.aendggnerSynopse = fn;")
  private static native void exportiere(Function<JSObject, JSObject> fn);

  @JS(
      value =
          "if (typeof globalThis.aendggnerBereit === 'function') { globalThis.aendggnerBereit(); }")
  private static native void melde();

  private static JSObject synopse(JSObject eingabe) {
    var antwort = JSObject.create();
    try {
      var stamm = quelle(eingabe.get("stamm"));
      var patches = new ArrayList<Quelle>();
      var patchListe = eingabe.get("patches");
      for (int i = 0; i < anzahl(patchListe); i++) {
        patches.add(quelle(element(patchListe, i)));
      }

      var artikel = text(eingabe.get("artikel"));
      var stichtag = text(eingabe.get("stichtag"));
      var vollstaendig = Boolean.TRUE.equals(wahrheitswert(eingabe.get("vollstaendig")));

      if (Boolean.TRUE.equals(wahrheitswert(eingabe.get("nurText")))) {
        // Der Notausgang der Befehlszeile (--extract-only) steht auch hier offen: Wer einem Rest
        // nachgehen will, muss sehen können, was das Erzeugnis gelesen hat.
        antwort.set(
            "text", JSString.of(Pipeline.extrahiereText(stamm, List.copyOf(patches), false)));
        return antwort;
      }

      var ergebnis =
          Pipeline.erzeugeSynopse(
              stamm,
              List.copyOf(patches),
              artikel == null || artikel.isBlank() ? null : artikel,
              vollstaendig,
              stichtag == null || stichtag.isBlank() ? null : LocalDate.parse(stichtag.strip()));

      // Java-Werte kämen auf der JS-Seite als undurchsichtige Proxys an; JSString/JSNumber
      // erzeugen echte JS-Werte.
      antwort.set("html", JSString.of(ergebnis.html()));
      antwort.set("angewandt", JSNumber.of(ergebnis.anzahlAngewandt()));
      antwort.set("manuell", JSNumber.of(ergebnis.anzahlManuell()));
      antwort.set("normen", JSNumber.of(ergebnis.anzahlGeaenderteNormen()));
    } catch (Throwable e) {
      e.printStackTrace();
      var meldung = e.getMessage();
      antwort.set(
          "fehler",
          JSString.of(
              e.getClass().getSimpleName()
                  + (meldung == null || meldung.isBlank() ? "" : ": " + meldung)));
    }
    return antwort;
  }

  private static Quelle quelle(Object datei) {
    if (!(datei instanceof JSObject objekt)) {
      throw new IllegalArgumentException("Datei fehlt oder ist kein Objekt.");
    }
    var name = text(objekt.get("name"));
    var base64 = text(objekt.get("base64"));
    if (base64 == null || base64.isEmpty()) {
      throw new IllegalArgumentException(
          "Datei „" + (name == null ? "?" : name) + "“ enthält keine Daten.");
    }
    return new Quelle(name == null ? "Datei" : name, Base64.getDecoder().decode(base64));
  }

  /**
   * Die Interop reicht JS-Werte je nach Typ als {@link JSValue} oder als bereits umgesetztes
   * Java-Objekt herüber; die folgenden Helfer nehmen beides an, damit sich die Browserfassung nicht
   * an einer Fassung der experimentellen Web-Image-API festmacht.
   */
  private static String text(Object wert) {
    if (wert == null) {
      return null;
    }
    if (wert instanceof String s) {
      return s;
    }
    if (wert instanceof JSValue v) {
      return "undefined".equals(v.typeof()) ? null : v.asString();
    }
    return wert.toString();
  }

  private static Boolean wahrheitswert(Object wert) {
    if (wert instanceof Boolean b) {
      return b;
    }
    if (wert instanceof JSValue v) {
      // Ein nicht gesetztes Feld kommt als „undefined“ herüber; asBoolean() bräche daran.
      return "undefined".equals(v.typeof()) ? Boolean.FALSE : v.asBoolean();
    }
    return Boolean.FALSE;
  }

  private static int anzahl(Object liste) {
    if (!(liste instanceof JSObject objekt)) {
      return 0;
    }
    var laenge = objekt.get("length");
    if (laenge instanceof Number n) {
      return n.intValue();
    }
    if (laenge instanceof JSValue v) {
      return v.asInt();
    }
    return 0;
  }

  private static Object element(Object liste, int index) {
    var objekt = (JSObject) liste;
    var wert = objekt.get(Integer.valueOf(index));
    return wert != null ? wert : objekt.get(String.valueOf(index));
  }
}
