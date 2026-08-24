#!/bin/sh
# SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
# SPDX-License-Identifier: AGPL-3.0-or-later

# Baut die Browserfassung und schiebt sie zu Cloudflare Workers (README § 15).
#
#   1. ./mvnw -Pwasm package — erzeugt target/web und lässt deploy/webpaket.sh darüber
#      laufen (wasm-opt, Quelltext-Tarball, 25-MiB-Prüfung). Bricht bei uneingecheckten
#      Änderungen ab, denn der beigelegte Quelltext muss der gebaute sein.
#   2. wrangler deploy — lädt target/web nach wrangler.toml hoch.
#
# JAVA_HOME muss auf Oracle GraalVM 25.1 oder neuer zeigen; ist es nicht gesetzt, wird
# /Library/Java/JavaVirtualMachines/graalvm-25.jdk genommen.
#
# Aufruf: deploy/cloudflare.sh [--nur-bauen | --nur-hochladen]

set -eu

wurzel="$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$wurzel"

: "${JAVA_HOME:=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home}"
export JAVA_HOME

bauen=1
hochladen=1
case "${1:-}" in
  --nur-bauen)     hochladen=0 ;;
  --nur-hochladen) bauen=0 ;;
  '') ;;
  *) echo "cloudflare: unbekannte Angabe „$1“ (--nur-bauen | --nur-hochladen)" >&2; exit 2 ;;
esac

if [ "$bauen" = 1 ]; then
  ./mvnw -Pwasm package
fi

if [ "$hochladen" = 1 ]; then
  if command -v wrangler >/dev/null 2>&1; then
    wrangler deploy
  else
    npx --yes wrangler deploy
  fi
fi
