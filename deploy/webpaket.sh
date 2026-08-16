#!/bin/sh
# Macht aus dem Übersetzerergebnis ein auslieferbares Verzeichnis. Läuft in der Phase
# „package“ des Profils -Pwasm, unmittelbar nach native-image.
#
#   1. Der Textzwischenschritt aendggner.js.wat (mehrere hundert Megabyte) wird entfernt.
#   2. Der Quelltext der gebauten Fassung wird als Tarball beigelegt — AGPLv3 §13 verlangt
#      beim Netzwerkbetrieb einen Quellcode-Zugang für die Nutzer:innen, und der Footer der
#      Startseite verweist darauf.
#   3. Die großen Dateien werden vorkomprimiert, damit nginx sie mit gzip_static bzw.
#      brotli_static ausliefern kann, statt 24 MB je Abruf neu zu packen.
#
# Aufruf: deploy/webpaket.sh <ausgabeverzeichnis>   (Standard: target/web)

set -eu

ziel="${1:-target/web}"
quelle="$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"

if [ ! -d "$ziel" ]; then
  echo "webpaket: $ziel gibt es nicht — erst -Pwasm package laufen lassen." >&2
  exit 1
fi

# 1. Zwischenschritt fort.
rm -f "$ziel/aendggner.js.wat"

# 2. Quelltext der gebauten Fassung.
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
git -C "$quelle" archive --format=tar.gz --prefix=aendggner/ HEAD \
  -o "$(CDPATH='' cd -- "$ziel" && pwd)/aendggner-quelltext.tar.gz"

{
  echo "ÄndGgner — Quelltext der ausgelieferten Fassung"
  echo
  echo "Commit:  $fassung"
  echo "Gebaut:  $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "Vollständig in aendggner-quelltext.tar.gz; Bauanleitung darin in README.adoc."
  echo "Fortlaufend: https://gerrit.benkard.de/plugins/gitiles/aendggner"
} > "$ziel/quelltext-fassung.txt"

# 3. Vorkompression. Der Tarball ist bereits gepackt und bleibt außen vor.
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

echo "webpaket: $ziel ist auslieferbar (Fassung $(git -C "$quelle" rev-parse --short HEAD))."
