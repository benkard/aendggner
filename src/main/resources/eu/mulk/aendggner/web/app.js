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

formular.addEventListener("submit", async (ereignis) => {
  ereignis.preventDefault();

  const stammDatei = document.querySelector("#stamm").files[0];
  const patchDateien = Array.from(document.querySelector("#aenderung").files);
  if (!stammDatei || patchDateien.length === 0) {
    zeige("Bitte ein Stammgesetz und mindestens ein Änderungsdokument wählen.", "fehler");
    return;
  }

  knopf.disabled = true;
  zeige("Lade das Rechenwerk (einmalig einige Megabyte) und werte aus …", "arbeit");

  try {
    const [stamm, patches] = await Promise.all([
      alsBase64(stammDatei),
      Promise.all(patchDateien.map(alsBase64)),
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
        vollstaendig: document.querySelector("#vollstaendig").checked,
        artikel: null,
      });
    });

    if (ergebnis.fehler) {
      zeige("Verarbeitung fehlgeschlagen: " + ergebnis.fehler, "fehler");
      return;
    }

    const blob = new Blob([ergebnis.html], { type: "text/html;charset=utf-8" });
    const adresse = URL.createObjectURL(blob);

    // Kein window.open: Der Aufruf käme nach dem Warten auf den Worker und gälte dem Browser
    // nicht mehr als Nutzerhandlung — er würde als Popup blockiert. Stattdessen ein Link, der
    // sich öffnen und ebenso gut speichern lässt.
    zeige(
      `${ergebnis.angewandt} Befehle angewandt, ${ergebnis.manuell} manuell zu prüfen, ` +
        `${ergebnis.normen} geänderte Normen. `,
      "fertig",
    );
    const verweis = document.createElement("a");
    verweis.href = adresse;
    verweis.target = "_blank";
    verweis.textContent = "Synopse öffnen";
    meldung.append(verweis);
  } catch (e) {
    zeige("Verarbeitung fehlgeschlagen: " + (e && e.message ? e.message : e), "fehler");
  } finally {
    knopf.disabled = false;
  }
});
