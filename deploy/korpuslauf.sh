#!/bin/sh
# SPDX-FileCopyrightText: 2026 Matthias Andreas Benkard <code@mail.matthias.benkard.de>
# SPDX-License-Identifier: AGPL-3.0-or-later

# Führt den Massenlauf über den Beispielkorpus (§ 6d des Handbuchs) und legt Bericht und
# Einzelsynopsen in einem Ausgabeverzeichnis ab.
#
# Anders als die Akzeptanzprüfungen, die dasselbe im Rahmen von „mvnw verify“ tun, dient
# dieses Skript dem Hinsehen: Es behält die Synopsen, damit sich jeder Rest an seinem
# eigenen Dokument nachschlagen lässt, und es nennt den Bericht in der Fassung, in der er
# auch als Grundlinie liegt.
#
#   deploy/korpuslauf.sh [ausgabeverzeichnis] [auftragsliste]
#
# Voreinstellungen: target/korpus und src/test/resources/sampledata/korpus.tsv.
# Rückgabewert 3, wenn eine Kennzahl hinter die Grundlinie zurückfällt (wie beim Abgleich
# mit der amtlichen Nachfassung, § 6b Absatz 3).
#
# Ist eine neue Grundlinie zu setzen — weil eine Kennzahl gestiegen ist —, so tut das:
#   cp target/korpus/bericht.tsv src/test/resources/sampledata/korpus-grundlinie.tsv
# nebst der Vorbemerkung, die in jener Datei steht.

set -eu

wurzel="$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"
ziel="${1:-$wurzel/target/korpus}"
liste="${2:-$wurzel/src/test/resources/sampledata/korpus.tsv}"
grundlinie="$wurzel/src/test/resources/sampledata/korpus-grundlinie.tsv"
jar="$wurzel/target/aendggner-0.1.0-SNAPSHOT.jar"

if [ ! -f "$liste" ]; then
  echo "Die Auftragsliste $liste fehlt." >&2
  exit 1
fi

if [ ! -f "$jar" ]; then
  echo "Das Erzeugnis fehlt; es wird hergestellt."
  (cd "$wurzel" && ./mvnw -q package -DskipTests)
fi

mkdir -p "$ziel"

set +e
java -jar "$jar" \
  --korpus "$liste" \
  --grundlinie "$grundlinie" \
  --synopsen "$ziel/synopsen" \
  -o "$ziel/bericht.tsv"
ergebnis=$?
set -e

echo
echo "Bericht:   $ziel/bericht.tsv"
echo "Synopsen:  $ziel/synopsen"
exit "$ergebnis"
