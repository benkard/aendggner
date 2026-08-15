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
  const { stamm, patches, vollstaendig, artikel } = nachricht.data;
  try {
    await gestartet;
    await bereit;
    const ergebnis = self.aendggnerSynopse({
      stamm,
      patches,
      artikel: artikel ?? null,
      vollstaendig: Boolean(vollstaendig),
    });
    if (ergebnis.fehler) {
      self.postMessage({ fehler: String(ergebnis.fehler) });
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
