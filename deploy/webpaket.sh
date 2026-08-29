#!/bin/sh
# SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
# SPDX-License-Identifier: AGPL-3.0-or-later

# Macht aus dem Übersetzerergebnis ein auslieferbares Verzeichnis. Läuft in der Phase
# „package“ des Profils -Pwasm, unmittelbar nach native-image.
#
#   1. Der Textzwischenschritt aendggner.js.wat (mehrere hundert Megabyte) wird entfernt.
#   2. Das Modul geht durch wasm-opt -Oz, sofern Binaryen zur Hand ist.
#   3. Der Quelltext der gebauten Fassung wird als Tarball beigelegt — AGPLv3 §13 verlangt
#      beim Netzwerkbetrieb einen Quellcode-Zugang für die Nutzer:innen, und der Footer der
#      Startseite verweist darauf.
#   4. Die Startseite bekommt unter dem Titel das Datum des gebauten Commits.
#   5. Nur die vom Bau erzeugten Dateien bleiben stehen; alles hier Liegende wird
#      hochgeladen.
#   6. Auf Wunsch (VORKOMPRIMIEREN=1) werden die großen Dateien vorkomprimiert.
#
# Zielplattform ist Cloudflare Workers: dort gilt eine Grenze von 25 MiB je Datei
# (unkomprimiert), und komprimiert wird beim Ausliefern ohnehin. Deshalb ist die
# Vorkompression abgeschaltet und die Größe jeder Datei wird geprüft. Wer stattdessen
# selbst mit nginx ausliefert (deploy/nginx-aendggner.conf, gzip_static/brotli_static),
# ruft das Skript mit VORKOMPRIMIEREN=1 auf.
#
# Aufruf: deploy/webpaket.sh <ausgabeverzeichnis>   (Standard: target/web)

set -eu

ziel="${1:-target/web}"
quelle="$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"

# Cloudflare Workers nimmt keine Datei über 25 MiB an.
grenze_datei=26214400
# Der Quelltext misst ein Viertel Megabyte. Wächst das Archiv über diese Schranke, sind
# wieder Massendaten ins Repository geraten, die dort nicht hingehören (siehe
# .gitattributes) — dann lieber der Bau ab als ein unbrauchbares Paket.
grenze_archiv=8388608

if [ ! -d "$ziel" ]; then
  echo "webpaket: $ziel gibt es nicht — erst -Pwasm package laufen lassen." >&2
  exit 1
fi

groesse() {
  wc -c <"$1" | tr -d ' '
}

# 1. Zwischenschritt fort.
rm -f "$ziel/aendggner.js.wat"

# 2. Nachoptimierung. -Oz zieht die Codegröße noch einmal spürbar zusammen.
#
# Die Merkmale sind einzeln aufgezählt, nicht als --all-features: Binaryen darf ausgeben,
# was es darf, und mit --all-features nutzt es auch Vorschläge, die noch kein Browser
# annimmt — das Modul scheitert dann erst beim Instanziieren („invalid heap type 'exact'“
# aus den custom descriptors, „invalid import kind 127“ aus der kompakten
# Importsektion). Aufgeführt ist deshalb nur, was ausgeliefert in den Browsern steht und
# was Web Image braucht: WasmGC samt Referenztypen, das Ausnahmen-Proposal, endständige
# Aufrufe. Fehlt wasm-opt, bleibt das Modul, wie es ist — der Bau soll daran nicht
# scheitern.
wasm_merkmale="--enable-gc --enable-reference-types --enable-exception-handling
               --enable-tail-call --enable-bulk-memory --enable-bulk-memory-opt
               --enable-nontrapping-float-to-int --enable-sign-ext
               --enable-mutable-globals --enable-multivalue --enable-extended-const
               --enable-simd --enable-call-indirect-overlong"

if [ -f "$ziel/aendggner.js.wasm" ]; then
  vorher="$(groesse "$ziel/aendggner.js.wasm")"
  if command -v wasm-opt >/dev/null 2>&1; then
    # shellcheck disable=SC2086  # die Merkmalsliste soll in Wörter zerfallen
    wasm-opt $wasm_merkmale -Oz \
      -o "$ziel/aendggner.js.wasm.neu" "$ziel/aendggner.js.wasm"
    mv "$ziel/aendggner.js.wasm.neu" "$ziel/aendggner.js.wasm"
    echo "webpaket: wasm-opt: $vorher -> $(groesse "$ziel/aendggner.js.wasm") Bytes."
  else
    echo "webpaket: wasm-opt nicht gefunden — Modul bleibt unnachoptimiert" \
         "($vorher Bytes)." >&2
  fi
fi

# 3. Quelltext der gebauten Fassung.
#
# Ein veränderter Arbeitsbaum bricht den Bau ab: Der angebotene Quelltext muss der
# ausgelieferten Fassung entsprechen, sonst ist die Auflage aus §13 verfehlt. Wer nur
# ausprobieren will, setzt QUELLTEXT_UNGEPRUEFT=1.
if ! git -C "$quelle" rev-parse --git-dir >/dev/null 2>&1; then
  echo "webpaket: $quelle ist kein Git-Arbeitsbaum; ohne ihn lässt sich der Quelltext" >&2
  echo "          der gebauten Fassung nicht beilegen (AGPLv3 §13)." >&2
  exit 1
fi

if [ -n "$(git -C "$quelle" status --porcelain)" ] && [ "${QUELLTEXT_UNGEPRUEFT:-}" != 1 ]; then
  echo "webpaket: Der Arbeitsbaum trägt uneingecheckte Änderungen. Der beigelegte Quelltext" >&2
  echo "          wäre dann nicht der gebaute. Erst einchecken — oder für einen Probelauf" >&2
  echo "          QUELLTEXT_UNGEPRUEFT=1 setzen (dann nicht ausliefern)." >&2
  exit 1
fi

fassung="$(git -C "$quelle" rev-parse HEAD)"
archiv="$(CDPATH='' cd -- "$ziel" && pwd)/aendggner-quelltext.tar.gz"
git -C "$quelle" archive --format=tar.gz --prefix=aendggner/ HEAD -o "$archiv"

archivgroesse="$(groesse "$archiv")"
if [ "$archivgroesse" -gt "$grenze_archiv" ]; then
  echo "webpaket: Das Quelltextarchiv misst $archivgroesse Bytes und überschreitet damit" >&2
  echo "          die Schranke von $grenze_archiv. Vermutlich sind Massendaten ins" >&2
  echo "          Repository geraten; sie gehören in .gitattributes (export-ignore)." >&2
  exit 1
fi

{
  echo "ÄndGgner — Quelltext der ausgelieferten Fassung"
  echo
  echo "Commit:  $fassung"
  echo "Fassung: $(git -C "$quelle" log -1 --format=%cs HEAD)"
  echo "Gebaut:  $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "Vollständig in aendggner-quelltext.tar.gz; Bauanleitung darin in README.md."
  echo "Fortlaufend: https://git.benkard.de/mulk/aendggner"
  echo
  echo "Nicht im Archiv liegt der Beispielkorpus (src/test/resources/sampledata): Gesetzes-"
  echo "und Drucksachentexte fremder Urheberschaft, an denen die Tests messen. Gebaut wird"
  echo "ohne ihn; nur die Tests verlangen danach. Wer sie laufen lassen will, holt das"
  echo "Repository von der oben genannten Adresse, wo der Korpus vollständig liegt."
} > "$ziel/quelltext-fassung.txt"

# 4. Fassungsdatum der Startseite.
#
# Unter dem Titel steht „in der Fassung vom …“. Im Quelltext trägt die Marke das Datum des
# letzten Handanlegens; ausgeliefert wird das Datum des gebauten Commits, damit die Angabe
# nicht stillschweigend veraltet. Das Datum kommt aus %cs (JJJJ-MM-TT); date(1) bleibt
# außen vor, weil BSD und GNU sich über die Schalter nicht einig sind.
fassungsdatum="$(git -C "$quelle" log -1 --format=%cs HEAD)"
jahr="${fassungsdatum%%-*}"
rest="${fassungsdatum#*-}"
monat="${rest%%-*}"
tag="${rest#*-}"

case "$monat" in
  01) monatsname="Januar" ;;   02) monatsname="Februar" ;;   03) monatsname="März" ;;
  04) monatsname="April" ;;    05) monatsname="Mai" ;;       06) monatsname="Juni" ;;
  07) monatsname="Juli" ;;     08) monatsname="August" ;;    09) monatsname="September" ;;
  10) monatsname="Oktober" ;;  11) monatsname="November" ;;  12) monatsname="Dezember" ;;
  *) echo "webpaket: unlesbares Commit-Datum „$fassungsdatum“." >&2; exit 1 ;;
esac

# Führende Null im Tag ist im deutschen Fließtext unüblich.
tag="${tag#0}"
fassungstext="$tag. $monatsname $jahr"

startseite="$ziel/index.html"
if [ ! -f "$startseite" ]; then
  echo "webpaket: $startseite fehlt — die statischen Seiten sind nicht mitkopiert." >&2
  exit 1
fi

sed -e "s|<time datetime=\"[0-9-]*\">[^<]*</time>|<time datetime=\"$fassungsdatum\">$fassungstext</time>|" \
  "$startseite" > "$startseite.neu"

# Greift die Ersetzung nicht, ist die Marke im Vordruck umgebaut worden. Dann lieber der
# Bau ab als eine falsch datierte Fassung.
if ! grep -q "<time datetime=\"$fassungsdatum\">$fassungstext</time>" "$startseite.neu"; then
  rm -f "$startseite.neu"
  echo "webpaket: In index.html ist keine <time>-Marke unter dem Titel zu finden; das" >&2
  echo "          Fassungsdatum lässt sich nicht einsetzen." >&2
  exit 1
fi
mv "$startseite.neu" "$startseite"
echo "webpaket: Startseite trägt die Fassung vom $fassungstext."

# 5. Nur das Gebaute bleibt liegen — was hier steht, geht hoch.
rm -f "$ziel/.DS_Store"

# 6. Vorkompression, nur auf Anforderung (siehe Kopf). Der Tarball ist bereits gepackt und
# bleibt außen vor.
if [ "${VORKOMPRIMIEREN:-}" = 1 ]; then
  for datei in "$ziel"/*.wasm "$ziel"/*.js "$ziel"/*.css "$ziel"/*.html "$ziel"/*.svg \
               "$ziel"/*.txt; do
    [ -f "$datei" ] || continue
    gzip -9 -f -k -- "$datei"
    if command -v brotli >/dev/null 2>&1; then
      brotli -f -- "$datei"
    fi
  done

  if ! command -v brotli >/dev/null 2>&1; then
    echo "webpaket: brotli nicht gefunden — nur .gz angelegt." >&2
  fi
fi

# Was zu groß ist, nimmt Cloudflare nicht an. Lieber hier auffallen als beim Hochladen.
zugross=""
for datei in "$ziel"/*; do
  [ -f "$datei" ] || continue
  if [ "$(groesse "$datei")" -gt "$grenze_datei" ]; then
    zugross="$zugross  $(basename "$datei") ($(groesse "$datei") Bytes)
"
  fi
done
if [ -n "$zugross" ]; then
  echo "webpaket: Diese Dateien überschreiten die 25-MiB-Grenze von Cloudflare Workers:" >&2
  printf '%s' "$zugross" >&2
  exit 1
fi

echo "webpaket: $ziel ist auslieferbar (Fassung $(git -C "$quelle" rev-parse --short HEAD))."
