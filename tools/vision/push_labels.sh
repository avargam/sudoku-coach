#!/usr/bin/env bash
# Copia al móvil las etiquetas de las secuencias (testdata/vision/sequences/<id>/label.txt), que lee
# SequenceScanTest (tarea 4.4). Los fotogramas no se copian: ya están en el móvil, donde los grabó la
# app, y enviar decenas de MB por usbipd se atasca.
#   tools/vision/push_labels.sh
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
adb="${ADB:-$(grep sdk.dir "$root/local.properties" | cut -d= -f2)/platform-tools/adb}"
dest=/data/local/tmp/sudoku-sequences
for label in "$root"/testdata/vision/sequences/S*/label.txt; do
    [ -f "$label" ] || continue
    s=$(basename "$(dirname "$label")")
    "$adb" shell mkdir -p "$dest/$s"
    "$adb" push -q "$label" "$dest/$s/label.txt" >/dev/null
    echo "$s: etiqueta copiada"
done
