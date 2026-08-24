// SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Führt das Wasm-Modul in einem eigenen Thread aus: Web Image ist einthreadig, und ein Lauf über
// mehrere Sekunden würde die Oberfläche sonst einfrieren.

const bereit = new Promise((aufloesen) => {
  self.aendggnerBereit = aufloesen;
});

// Die Laufzeit von aendggner.js sucht das Wasm-Modul neben ihrer eigenen Datei und findet die
// über `document.currentScript` — den es im Worker nicht gibt, wo sie stattdessen auf
// `location.href` (also diese Datei) zurückfällt und ins Leere greift. Der Selbststart beim
// Import scheitert deshalb; danach starten wir die VM mit ausdrücklichem Pfad noch einmal.
self.importScripts("aendggner.js");

const gestartet = GraalVM.run([], Object.assign(new GraalVM.Config(), {
  wasm_path: new URL("aendggner.js.wasm", self.location.href).href,
}));

self.onmessage = async (nachricht) => {
  const { stamm, patches, vollstaendig, artikel, stichtag, nurText } = nachricht.data;
  try {
    await gestartet;
    await bereit;
    const ergebnis = self.aendggnerSynopse({
      stamm,
      patches,
      artikel: artikel ?? null,
      stichtag: stichtag ?? null,
      vollstaendig: Boolean(vollstaendig),
      nurText: Boolean(nurText),
    });
    if (ergebnis.fehler) {
      self.postMessage({ fehler: String(ergebnis.fehler) });
    } else if (nurText) {
      self.postMessage({ text: String(ergebnis.text) });
    } else {
      self.postMessage({
        html: String(ergebnis.html),
        angewandt: Number(ergebnis.angewandt),
        manuell: Number(ergebnis.manuell),
        normen: Number(ergebnis.normen),
      });
    }
  } catch (e) {
    self.postMessage({ fehler: e && e.message ? e.message : String(e) });
  }
};
