// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Nimmt die Dateien aus dem Formular entgegen, reicht sie an den Worker und zeigt die Synopse.
// Es gibt keinen Server: Alles läuft im Browser.

const formular = document.querySelector("#synopse-formular");
const knopf = formular.querySelector("button");
const meldung = document.querySelector("#meldung");

let worker = null;

function zeige(text, art) {
  meldung.textContent = text;
  meldung.className = art ? "meldung " + art : "meldung";
  meldung.hidden = !text;
}

/**
 * Der Dateiinhalt geht als Base64 an das Wasm-Modul: Die Umsetzung typisierter Felder nach
 * byte[] ist in Web Image derzeit defekt, Zeichenketten überqueren die Grenze zuverlässig.
 */
async function alsBase64(datei) {
  const bytes = new Uint8Array(await datei.arrayBuffer());
  let roh = "";
  const block = 0x8000; // btoa verträgt keine beliebig langen Argumentlisten.
  for (let i = 0; i < bytes.length; i += block) {
    roh += String.fromCharCode.apply(null, bytes.subarray(i, i + block));
  }
  return { name: datei.name, base64: btoa(roh) };
}

/** Hängt der Meldung einen Verweis auf das Ergebnis an; ein Blob, kein Server. */
function oeffne(inhalt, art, aufschrift) {
  // Es können mehrere Ergebnisse nebeneinander stehen (Synopse und fortgeschriebene Fassung);
  // dann trennt sie ein Punkt, damit die Aufschriften nicht aneinanderkleben.
  if (meldung.querySelector("a")) {
    meldung.append(" · ");
  }
  const verweis = document.createElement("a");
  const url = URL.createObjectURL(new Blob([inhalt], { type: art }));
  verweis.href = url;
  verweis.target = "_blank";
  verweis.textContent = aufschrift;
  meldung.append(verweis);
  return url;
}

formular.addEventListener("submit", async (ereignis) => {
  ereignis.preventDefault();

  const stammDatei = document.querySelector("#stamm").files[0];
  const patchDateien = Array.from(document.querySelector("#aenderung").files);
  if (!stammDatei || patchDateien.length === 0) {
    zeige("Bitte ein Stammgesetz und mindestens ein Änderungsdokument wählen.", "fehler");
    return;
  }

  const nachfassungDatei = document.querySelector("#nachfassung").files[0] ?? null;
  const neufassungGewuenscht = document.querySelector("#neufassung").checked;
  const artikel = document.querySelector("#artikel").value.trim();
  const stichtag = document.querySelector("#stichtag").value.trim();
  const nurText = document.querySelector("#nurtext").checked;
  const vollstaendig = document.querySelector("#vollstaendig").checked;

  knopf.disabled = true;
  zeige("Lade das Rechenwerk (einmalig einige Megabyte) und werte aus …", "arbeit");

  try {
    const [stamm, patches, nachfassung] = await Promise.all([
      alsBase64(stammDatei),
      Promise.all(patchDateien.map(alsBase64)),
      nachfassungDatei ? alsBase64(nachfassungDatei) : null,
    ]);

    if (!worker) {
      worker = new Worker("worker.js");
    }

    const ergebnis = await new Promise((aufloesen, ablehnen) => {
      worker.onmessage = (nachricht) => aufloesen(nachricht.data);
      worker.onerror = (fehler) => ablehnen(new Error(fehler.message || "Worker-Fehler"));
      worker.postMessage({
        stamm,
        patches,
        vollstaendig,
        artikel: artikel === "" ? null : artikel,
        stichtag: stichtag === "" ? null : stichtag,
        nachfassung,
        nurText,
      });
    });

    if (ergebnis.fehler) {
      zeige("Verarbeitung fehlgeschlagen: " + ergebnis.fehler, "fehler");
      return;
    }

    if (nurText) {
      zeige(
        `${ergebnis.text.length.toLocaleString("de-DE")} Zeichen gelesen; ` +
          `keine Synopse erstellt. `,
        "fertig",
      );
      const url = oeffne(ergebnis.text, "text/plain;charset=utf-8", "Gelesenen Text öffnen");
      window.open(url, "_blank");
      return;
    }

    // Die Zahl zählt die Einträge der Synopse. Sind die unveränderten Vorschriften mit
    // aufgenommen, so sind das nicht die geänderten Normen, und sie heißen dann anders.
    zeige(
      `${ergebnis.angewandt} Befehle angewandt, ${ergebnis.manuell} manuell zu prüfen, ` +
        `${ergebnis.normen} ${vollstaendig ? "Normen in der Synopse" : "geänderte Normen"}. ` +
        (ergebnis.abgleich ? `Abgleich: ${ergebnis.abgleich}. ` : ""),
      "fertig",
    );
    const url = oeffne(ergebnis.html, "text/html;charset=utf-8", "Synopse öffnen");
    window.open(url, "_blank");
    if (neufassungGewuenscht) {
      oeffne(
        ergebnis.neufassung,
        "text/plain;charset=utf-8",
        "Fortgeschriebene Fassung öffnen",
      );
    }
  } catch (e) {
    zeige("Verarbeitung fehlgeschlagen: " + (e && e.message ? e.message : e), "fehler");
  } finally {
    knopf.disabled = false;
  }
});

// Ein geschlossenes <details> druckt seinen Inhalt nicht; ein Vordruck gehört aber
// vollständig aufs Papier, auch der eingeklappte Teil.
const weitere = document.querySelector(".weitere");
let warOffen = false;
addEventListener("beforeprint", () => {
  warOffen = weitere.open;
  weitere.open = true;
});
addEventListener("afterprint", () => {
  weitere.open = warOffen;
});
