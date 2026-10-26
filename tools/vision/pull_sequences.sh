#!/usr/bin/env bash
# Copia las secuencias grabadas en el móvil (D-22, tarea 4.4) a testdata/vision/sequences/, ignorada
# por git, y los resultados de SequenceScanTest (frames.txt). La app de debug los guarda en su
# almacenamiento interno, al que se llega con run-as.
#   tools/vision/pull_sequences.sh           # copia las que falten
#   tools/vision/pull_sequences.sh --clean   # y después las borra del móvil (SequenceScanTest
#                                            # las lee de ahí: borrarlas solo al terminar de medir)
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
# adb: $ADB, el del PATH o el del SDK (ANDROID_HOME, o sdk.dir de local.properties si existe).
sdk="${ANDROID_HOME:-$(grep -s sdk.dir "$root/local.properties" | cut -d= -f2 || true)}"
adb="${ADB:-$(command -v adb || echo "$sdk/platform-tools/adb")}"
dest="$root/testdata/vision/sequences"
mkdir -p "$dest"

for s in $("$adb" shell run-as dev.sudokucoach ls files/sequences 2>/dev/null | tr -d '\r'); do
    if [ -d "$dest/$s" ]; then
        echo "$s: ya está"
        continue
    fi
    "$adb" exec-out run-as dev.sudokucoach tar c -C files/sequences "$s" | tar x -C "$dest"
    echo "$s: $(ls "$dest/$s" | grep -c '\.jpg$') fotogramas"
done

# Resultados de SequenceScanTest: la lectura de cada fotograma, junto a su secuencia.
for r in $("$adb" shell run-as dev.sudokucoach ls files/replays 2>/dev/null | tr -d '\r'); do
    s="${r%.txt}"
    [ -d "$dest/$s" ] || continue
    "$adb" exec-out run-as dev.sudokucoach cat "files/replays/$r" > "$dest/$s/frames.txt"
    echo "$s: frames.txt"
done

if [ "${1:-}" = "--clean" ]; then
    "$adb" shell run-as dev.sudokucoach rm -rf files/sequences
    echo "Borradas del móvil"
fi
