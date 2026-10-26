#!/usr/bin/env bash
# Copia al móvil las etiquetas de las secuencias (testdata/vision/sequences/<id>/label.txt), que lee
# SequenceScanTest (tarea 4.4). Los fotogramas no se copian: ya están en el móvil, donde los grabó la
# app, y enviar decenas de MB por usbipd se atasca.
#   tools/vision/push_labels.sh
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
# adb: $ADB, el del PATH o el del SDK (ANDROID_HOME, o sdk.dir de local.properties si existe).
sdk="${ANDROID_HOME:-$(grep -s sdk.dir "$root/local.properties" | cut -d= -f2 || true)}"
adb="${ADB:-$(command -v adb || echo "$sdk/platform-tools/adb")}"
dest=/data/local/tmp/sudoku-sequences
for label in "$root"/testdata/vision/sequences/S*/label.txt; do
    [ -f "$label" ] || continue
    s=$(basename "$(dirname "$label")")
    "$adb" shell mkdir -p "$dest/$s"
    "$adb" push -q "$label" "$dest/$s/label.txt" >/dev/null
    echo "$s: etiqueta copiada"
done
