#!/usr/bin/env bash
# Sesión de rendimiento del escáner en vivo (tarea 4.7): con la app en el escáner, `start` anota la
# temperatura y borra logcat y las estadísticas de fotogramas y de batería; tras escanear unos
# minutos, `stop` anota lo mismo y resume las líneas `LiveMeasure` de `FrameAnalyzer` (debug), los
# fotogramas entrecortados de `dumpsys gfxinfo` y el consumo estimado de la app. Con el móvil por
# USB se carga, así que el sistema se pone como desenchufado (`dumpsys battery unplug`) para que
# `batterystats` cuente el consumo, y `stop` lo devuelve.
#
#   tools/vision/perf_session.sh start
#   ... escanear páginas durante 5 minutos ...
#   tools/vision/perf_session.sh stop [salida.log]
set -euo pipefail

PKG=dev.sudokucoach
STATE=/tmp/sudoku-perf-start.txt

snapshot() {
    local battery thermal
    battery=$(adb shell dumpsys battery)
    thermal=$(adb shell dumpsys thermalservice | grep -m1 "Thermal Status" || true)
    echo "time=$(date +%s)"
    echo "temp=$(grep -m1 ' temperature:' <<<"$battery" | tr -dc 0-9)"
    echo "thermal=${thermal##*: }"
}

case "${1:-}" in
start)
    adb logcat -c
    adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
    adb shell dumpsys battery unplug
    adb shell dumpsys batterystats --reset >/dev/null
    snapshot >"$STATE"
    cat "$STATE"
    ;;
stop)
    out=${2:-perf-$(date +%Y%m%d-%H%M%S).log}
    adb logcat -d -s LiveMeasure >"$out"
    end=$(snapshot)
    # shellcheck disable=SC1090
    source "$STATE"
    t0=$time c0=$temp
    eval "$end"
    echo "Duración: $(((time - t0) / 60)) min $(((time - t0) % 60)) s"
    echo "Consumo estimado de la app (batterystats):"
    adb shell dumpsys batterystats --charged "$PKG" | grep -m3 -E "Estimated power use|UID u0a[0-9]+:|Uid u0a[0-9]+:" | sed 's/^/  /' || true
    adb shell dumpsys battery reset
    echo "Temperatura de la batería: $(awk "BEGIN{print $c0/10}") → $(awk "BEGIN{print $temp/10}") °C (estado térmico: $thermal)"
    echo "Interfaz:"
    adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile" | sed 's/^/  /'
    python3 - "$out" <<'EOF'
import re, statistics, sys
lines = open(sys.argv[1]).read().splitlines()
def values(key, pattern):
    return [float(m[1]) for l in lines if key in l for m in [re.search(pattern, l)] if m]
fps = values("frames size", r"fps=([\d.]+)")
for name in ("convert", "detect", "latency", "read"):
    p50 = values("frames size", name + r"=p50 ([\d.]+)")
    mx = values("frames size", name + r"=p50 [\d.]+ max ([\d.]+)")
    if p50:
        print(f"{name}: mediana de medianas {statistics.median(p50):.1f} ms, máximo {max(mx):.1f} ms ({len(p50)} ventanas)")
if fps:
    print(f"fps: mediana {statistics.median(fps):.1f}, mínimo {min(fps):.1f}")
review = values("review ms", r"review ms=(\d+)")
capture = values("capture reads", r"ms=(\d+)")
if review:
    print(f"capturas: {len(review)}; de quieto a la captura: mediana {statistics.median(capture):.0f} ms, máx {max(capture):.0f}; "
          f"a la revisión: mediana {statistics.median(review):.0f} ms, máx {max(review):.0f}")
EOF
    echo "Log: $out"
    ;;
*)
    echo "Uso: $0 start | stop [salida.log]" >&2
    exit 1
    ;;
esac
