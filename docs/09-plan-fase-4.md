# 09 · Plan detallado: Fase 4 (cámara en vivo)

Objetivo de la fase (de `04-plan-implementacion.md`): **apuntar con la cámara al sudoku y llegar a la revisión sin hacer una foto**. La app detecta el tablero en cada fotograma, dibuja su contorno, lee varios fotogramas cuando el tablero está quieto, vota por celda y pasa sola a la revisión. Además, se reduce el número de celdas que hay que revisar. Se apoya en D-07, D-08 y D-18, y en D-20 a D-22, aceptadas al planificarla.

**Hecho cuando:** en el A57, escaneando en vivo páginas del libro y con las secuencias grabadas como medida (≥ 10):
- se pasa a la revisión en **< 2 s** desde que el tablero está quieto (criterio de `01-problema.md`);
- hay **≤ 8 celdas sospechosas por tablero** de media (hoy ≈ 20 por foto);
- **ninguna lectura errónea queda sin marcar**, igual que en la Fase 3.

**Fuera de alcance:** las fotos difíciles (otro papel, sombra fuerte, ángulo > 15°, curvatura), aplazadas por decisión del usuario. D-07 mantiene su salvedad: todo lo medido es de un solo libro. El overlay sobre la cámara con la pista es de la Fase 7.

## Punto de partida

- `BoardReader` lee una foto de 1600 px en ≈ 0,3 s en el A57; la detección y la segmentación tardan ≈ 30 ms en la JVM, y el resto es ML Kit (lámina, D-08).
- La app (Fase 3) lee fotos de galería, revisa con D-19 y da pistas; la entrada es `PickingScreen`.
- **Sospechosas:** con el umbral de confianza de 0,5, en los 30 originales de la cámara salen ≈ 20 por foto y solo ≈ 3 están mal (`EndToEndTest`). En la Fase 2, el 90 % de los dígitos equivocados eran el mismo error, **un 1 manuscrito leído como 4**, y todos tenían confianza < 0,5.
- **Consecuencia para la meta:** la votación entre fotogramas corrige los errores que cambian de un fotograma a otro, no un error **sistemático** como el 1/4, que se repetirá en todos. Por eso se mide antes de confiar en ella (4.1) y se ajusta el umbral de confianza con datos (4.5). Si la meta no se alcanza sin dejar errores sin marcar, se anota y el resto se deja para la CNN propia (Fase 5), en vez de relajar la condición de 0 errores sin marcar.

## Arquitectura de la fase

```
app
├── camera/   CameraX: Preview + ImageAnalysis; ImageProxy (plano Y) → Mat gris
├── scan/     LiveScanner: detección por fotograma, estabilidad, lecturas y votación
├── ui/       ScanningScreen: visor, contorno en el acento, estado, flash, galería, disparador
└── debug/    grabación de secuencias de fotogramas (solo debug)
vision-core
└── live/     StabilityTracker, CellVoter (Kotlin puro, tests en la JVM)
```

- Lo que decide (estabilidad y votación) es Kotlin puro en `vision-core`: se prueba en la JVM con lecturas inventadas y con secuencias grabadas.
- El escáner sustituye a `PickingScreen` como primera pantalla; la galería sigue como botón dentro del escáner y como única entrada si no se concede el permiso de la cámara.

## Decisiones

### D-20 · Cámara con CameraX
- **CameraX** con `Preview` e `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, YUV 420, ≈ 1920 × 1080). Se usa solo el **plano Y** como imagen en gris, sin pasar por `Bitmap`, y se gira según `rotationDegrees`. Las versiones se fijan en 4.0, como en las fases anteriores.
- **Visor en Compose** (`CameraXViewfinder` de `camera-compose`) si funciona con la BOM de la app; si no, `PreviewView` dentro de un `AndroidView`.
- El análisis va en un solo hilo de fondo; ML Kit ya trabaja así.
- **Permiso de la cámara** en tiempo de ejecución. Si se deniega, la app sigue funcionando con la galería y lo explica.

### D-21 · Lectura en vivo con votación por celda
1. **Cada fotograma**, solo detección (`BoardDetector`): da el contorno que se dibuja y alimenta la estabilidad.
2. **Estable** cuando las esquinas se mueven menos de un umbral (fracción del lado del tablero) durante ≈ 0,3 s.
3. **Con el tablero estable**, se leen fotogramas completos (`BoardReader`, ≈ 0,3 s cada uno), de uno en uno, y se **vota por celda**: valor por mayoría y confianza combinada, que sube si coinciden lecturas seguras y baja si discrepan. Si el tablero se mueve o se pierde, la votación empieza de cero.
4. **Captura automática** cuando todas las celdas con tinta tienen mayoría clara, o al llegar al máximo de lecturas (≈ 5, dentro de los 2 s). El **disparador manual** usa lo votado hasta ese momento.
5. El resultado es un `ScannedBoard` como el de la galería, así que la revisión y la pista no cambian.

Los umbrales (estabilidad, mayoría, confianza combinada, número de lecturas) se fijan con las secuencias grabadas (D-22), no a priori.

### D-22 · Secuencias grabadas como datos de prueba
- La cámara no se puede automatizar en los tests. En su lugar, una herramienta **solo de debug** graba, al escanear, los fotogramas en gris (JPEG, reducidos) de una sesión; se copian al PC con `adb`.
- **No se versionan** (pesan y vienen de la cámara del usuario, como `testdata/vision/raw/`, D-17). Se etiquetan con la herramienta de etiquetado de la Fase 2, a partir de un fotograma.
- Sirven para tres cosas: probar estabilidad y votación en la JVM (con lecturas guardadas), medir la lectura real con ML Kit en el A57 (test instrumentado, como `PhotoScanTest`) y ajustar los umbrales.

---

## 4.0 Dependencias, permiso y esqueleto del escáner
- CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose` o `camera-view`) con versiones fijadas tras comprobar la compatibilidad con AGP 9.4, Compose y `minSdk` 26.
- `Route.Scanning` como primera pantalla, en lugar de `Route.Picking`. El permiso se pide al entrar; si se deniega, queda la galería con un texto que lo explica.
- Visor a pantalla completa y botón de galería, que abre el selector actual.

**Hecho cuando:** la app abre la cámara en el A57, el visor se ve fluido y la galería sigue llegando a la revisión.

**Resultado:** CameraX 1.6.2 (`camera-camera2`, `camera-lifecycle`, `camera-compose`), la última estable, compatible con la BOM de Compose 2026.09.00 y `minSdk` 26. El visor es `CameraXViewfinder` de Compose, sin `PreviewView` (`camera/CameraViewfinder.kt`); la cámara se enlaza al ciclo de vida de la pantalla y se suelta al salir o ir a segundo plano. `Route.Picking` pasa a `Route.Scanning` y `PickingScreen` a `ScanningScreen`: con permiso, visor a pantalla completa con "Apunta al sudoku" y el botón de galería; sin él, la entrada de la Fase 3 con el motivo y "Permitir la cámara", y el permiso se vuelve a comprobar al volver a la app. Comprobado en el A57: el visor se ve fluido y la galería sigue llegando a la revisión. `deployDebug` se quedó colgado una vez; `adb install` del APK tardó 4 s.

## 4.1 Fotogramas, detección por fotograma y medida de la votación
- `ImageProxy` → `Mat` en gris desde el plano Y, respetando `rowStride`, con la rotación aplicada y reducido a 1600 px de lado mayor si hace falta, como las fotos.
- Medir en el A57: tiempo de la detección por fotograma (presupuesto inicial ≤ 50 ms, para analizar ≥ 15 fotogramas/s), tiempo de una lectura completa, y fotogramas por segundo efectivos.
- **Medida de la votación, antes de construir encima:** con el tablero quieto, comparar en el A57, sobre 3–5 tableros, la lectura de un fotograma de vídeo con la de la foto de galería del mismo tablero, y cuánto cambian las lecturas entre fotogramas (qué celdas discrepan y cuáles fallan siempre igual).

**Hecho cuando:** hay cifras de tiempos y una primera respuesta a "¿la votación reduce las sospechosas?". Si los fotogramas leen claramente peor que una foto, se reconsidera D-21 (por ejemplo, una foto fija al estabilizarse) antes de seguir.

**Resultado:** `toGrayMat` (plano Y → `Mat` girado) y `FrameAnalyzer` (detección en cada fotograma) en un `ImageAnalysis` de 16:9 a 1920 × 1080, con el visor también en 16:9. En debug, `FrameAnalyzer` escribe los tiempos en logcat y, abriendo la app con `--ez measure true`, cada lectura completa; `tools/vision/live_measure.py` las compara con la etiqueta y con la lectura de la foto (`results/`).

Tiempos en el A57:

| | Mediana | Máximo |
|---|---|---|
| Plano Y → `Mat` girado, sin reducir | 0,7 ms | 2 ms |
| Ídem, reducido a 1600 px | 4–7 ms | 27 ms |
| Detección sin tablero | 18–22 ms | 29 ms |
| Detección con tablero (rectificación y rejilla incluidas) | ≈ 35 ms | 59 ms |
| Lectura completa de un fotograma | ≈ 205 ms | |

Sin tablero, el análisis va a ≈ 25 fotogramas/s (16,6 reduciendo a 1600 px). Reducir no mejora la lectura (030: 31,5 sospechosas por fotograma reduciendo, 30,8 sin reducir), así que los fotogramas se usan a 1080 × 1920. La detección con tablero cabe en los 50 ms con poco margen; se revisa en 4.7.

Lectura (sospechosas por tablero, con el umbral 0,5) en tres páginas que no han cambiado desde su foto; la votación es una mayoría simple de 5 lecturas seguidas, solo para esta primera respuesta:

| Tablero | Foto (Fase 2) | Un fotograma | Votación de 5 | Errores sin marcar al votar |
|---|---|---|---|---|
| 030 | 22 | 30,8 | 20,9 | 0 |
| 025 | 18 | 24,2 | 14,3 | 0 |
| 022 | 14 | 18,5 | 9,5 | 0 |
| **Media** | **18** | **24,5** | **14,9** | **0** |

- **Un fotograma lee peor que la foto** (≈ +35 % de sospechosas y más errores), pero **votando 5 queda por debajo** (−17 %). No es "claramente peor": D-21 se mantiene.
- **La votación sola no llega a la meta** (≤ 8): los errores son sistemáticos y se repiten en casi todos los fotogramas, 1 manuscritos leídos como 4 y 7 manuscritos sin leer (la foto los lee, pero con confianza baja, así que también salen como sospechosas). Queda para 4.5 (umbral con datos) y la Fase 5.
- Sueltos, algún fotograma movido da una lectura muy distinta (en un caso, 42 celdas mal sin marcar): la estabilidad de 4.3 debe impedir que voten.
- Al medir, la página 019 tenía ≈ 15 dígitos más que en su foto: las secuencias de 4.4 se etiquetan sobre su propio fotograma, no con la etiqueta de la foto.

## 4.2 Contorno en vivo
- Dibujar el cuadrilátero detectado sobre el visor, en el acento (D-12), traduciendo coordenadas del análisis al visor con las utilidades de CameraX, no a mano.
- Estado en una línea: "Busca un sudoku", "Acércate", "Mantén el móvil quieto", "Leyendo…".
- Accesibilidad: el estado se anuncia en TalkBack cuando cambia.

**Hecho cuando:** el contorno sigue al tablero en el A57 sin retraso visible y se ajusta a sus bordes al girar el móvil.

**Resultado:** `FrameAnalyzer` pasa las esquinas a coordenadas normalizadas del búfer (`unrotate`, que deshace el giro de `toGrayMat`) y `LiveScanner` (Kotlin puro, tests en la JVM) da el contorno y el estado. El contorno se dibuja con un `Canvas` sobre el visor: del búfer a la pantalla, con la inversa del `MutableCoordinateTransformer` de `CameraXViewfinder`. El paso del búfer del análisis al del visor supone que ven lo mismo, porque los dos son 16:9; la transformación sensor → búfer de `SurfaceRequest` no se puede usar porque `CameraXViewfinder` ocupa su único listener. "Acércate" por debajo del 25 % del fotograma (provisional, se ajusta con las secuencias) y el contorno se mantiene 5 fotogramas sin detección para que no parpadee. Estados de esta tarea: "Busca un sudoku", "Acércate" y "Mantén el móvil quieto"; "Leyendo…" llega con la lectura (4.3). Comprobado en el A57 por el usuario: el contorno sigue al tablero y se ajusta a sus bordes.

## 4.3 Estabilidad y votación (`vision-core`, D-21)
- `StabilityTracker`: decide si el tablero está quieto a partir de las esquinas de los últimos fotogramas.
- `CellVoter`: acumula lecturas (dígito, confianza, tinta y origen por celda) y da el valor por mayoría, la confianza combinada, el origen y si la lectura está completa.
- El origen (impreso o manuscrito) también se vota, porque el contraste cambia con la luz de cada fotograma.

**Tests (JVM):** esquinas quietas y en movimiento; votación unánime, dividida, con celdas que aparecen y desaparecen, y con un error que se repite en todos los fotogramas (no debe ganar confianza); reinicio al moverse.

**Resultado:** `StabilityTracker`, `CellVoter` y `CellReading` en `vision-core/live`, con 14 tests en la JVM.
- **Estabilidad:** ninguna esquina se aleja más del 2 % del lado medio durante 0,3 s, comparando con el primer fotograma del periodo quieto (una deriva lenta también lo reinicia); huecos de hasta 0,2 s sin detección no lo interrumpen.
- **Votación:** tinta y origen por mayoría; dígito, el más leído entre las lecturas con dígito; confianza combinada = mejor confianza de las lecturas que coinciden × fracción de lecturas con tinta que coinciden, así que un error repetido con poca confianza no la gana y las lecturas sin dígito la bajan. Completa con 3 lecturas si todas las celdas con tinta tienen un valor con ≥ 60 %, o con 5.
- **En la app:** `LiveScanner` solo lee con el tablero quieto y a buena distancia ("Leyendo…"), empieza de cero si se mueve y entrega el tablero votado una sola vez; `CoachViewModel.onBoardScanned` abre la revisión. Se adelanta de 4.6 el paso a la revisión, para probarlo; la vibración, el flash y el disparador quedan allí. `BoardReader.read(scan)` lee el tablero ya detectado en el fotograma, sin detectarlo dos veces, y la galería y la cámara comparten la conversión a `ScannedBoard`.
- Primera prueba en el A57: dos capturas en 0,69 s y 0,87 s desde que el tablero quedó quieto (4 y 3 lecturas), con 9 celdas poco seguras cada una. Mientras se lee un fotograma (≈ 0,2 s) el contorno no se mueve, porque la lectura va en el hilo del análisis; se revisa en 4.7. Los umbrales son provisionales hasta 4.5.

## 4.4 Grabación de secuencias (D-22)
- En debug, un interruptor en el escáner graba la sesión: fotogramas en gris reducidos, con su marca de tiempo.
- Guión de grabación: ≥ 10 páginas del libro, con 3–5 s quieto y algo de movimiento al principio. Si se puede, las mismas páginas que alguna foto del conjunto, para comparar.
- Etiquetado con la herramienta de la Fase 2 sobre un fotograma de cada secuencia.

**Hecho cuando:** hay ≥ 10 secuencias etiquetadas y un test instrumentado que las reproduce con el lector real y la votación, y escribe sospechosas, errores sin marcar y tiempo hasta la captura.

**Resultado:** 12 secuencias de 10 páginas (dos repetidas), de 159–191 fotogramas a ≈ 23 fotogramas/s, todas etiquetadas.
- **Grabación:** botón "Grabar" del escáner en debug (`FrameRecorder`); se para sola a los 8 s y, mientras graba, no lee ni captura. Los fotogramas se guardan **sin reducir**, al contrario de lo que decía D-22, para que reproducirlos lea lo mismo que en vivo: ≈ 60 MB por secuencia, en el almacenamiento interno de la app. `tools/vision/pull_sequences.sh` los trae a `testdata/vision/sequences/` (ignorada por git). Enviarlos de vuelta al móvil por usbipd se atasca, así que el test los lee de donde se grabaron y solo se envían las etiquetas (`push_labels.sh`).
- **Etiquetado:** `labelHelper --sequences` hace la plantilla sobre el primer fotograma con tablero desde el 70 % de la secuencia y la rellena con la etiqueta de una foto si coinciden la tinta y los impresos (la tinta sola no basta: todos los tableros llenos son iguales). Ninguna página coincidió con las 30 fotos; `prefill_labels.py` rellenó los `?` con lo más leído por ML Kit y se revisaron todos contra la imagen. La mayoría de ML Kit fallaba en ≈ 30 celdas, casi todas 1 manuscritos leídos como 4. En 3 secuencias el segmentador no ve 4 dígitos a lápiz muy tenues (S…205600 F1C9 y F2C9, S…205621 F8C8, S…205631 F1C5): se etiquetan como manuscritos y, si se captura, serán errores sin marcar.
- **Reproducción:** `SequenceScanTest` (móvil) pasa cada fotograma una vez por el lector real y guarda `frames.txt`; `replay` reproduce `LiveScanner` con esos tiempos, descartando los fotogramas que llegan mientras se lee, y lo usan el test del móvil y `SequenceReplayTest` (JVM, mismo resultado). Las lecturas del test tardan 0,26–0,34 s, más que en vivo (≈ 0,2 s), así que los tiempos reproducidos son algo pesimistas, y como dependen de esos tiempos, dos ejecuciones del test no capturan exactamente los mismos fotogramas; para comparar umbrales se usa siempre el mismo `frames.txt`.

Con los umbrales de 4.3:

| Secuencia | Captura | Lecturas | Sospechosas | Errores | Sin marcar |
|---|---|---|---|---|---|
| S…201411 | 1839 ms | 3 | 7 | 0 | 0 |
| S…205443 | 707 ms | 3 | 18 | 3 | 0 |
| S…205500 | 1439 ms | 3 | 10 | 1 | 0 |
| S…205550 | 1205 ms | 3 | 33 | 5 | 0 |
| S…205611 | 1303 ms | 3 | 11 | 2 | 0 |
| otras 7 | sin captura | 0–4 | | | |

**7 de 12 no capturan**, por dos causas que se ajustan en 4.5: en 3 el tablero ocupa menos del 25 % del fotograma ("Acércate") casi todo el tiempo (las medianas van del 23 % al 30 %, así que el umbral es demasiado alto para cómo se sostiene el móvil), y en las demás la estabilidad (2 % del lado respecto al inicio del periodo) se reinicia por la deriva de la mano antes de completar la votación, que con 4 lecturas aún no tiene mayoría clara en todas las celdas. Entre fotogramas seguidos, el movimiento es de ≈ 0,3 % del lado.

## 4.5 Ajuste con datos: menos sospechosas sin errores sin marcar
- Primero, el umbral de confianza con los datos que ya hay: la confianza de las 2201 lecturas de la Fase 2 y de los 30 originales. ¿Hay un umbral por debajo de 0,5 sin ningún error por encima? Ese es el mínimo que se gana sin votar.
- Después, los umbrales de la votación (D-21) con las secuencias: se busca la combinación que minimiza las sospechosas **con 0 errores sin marcar**.
- El ajuste se hace con parte de las secuencias y se comprueba con el resto, para no ajustar a la medida de los datos.
- Si un error sistemático (como el 1/4) impide la meta, se documenta cuántas sospechosas quedan por esa causa y se pasa a la Fase 5.

**Hecho cuando:** los umbrales están fijados y el informe da la media de sospechosas por tablero en las secuencias de comprobación.

**Resultado:** umbrales fijados con `LiveTuningTest` (JVM, `frames.txt` de las 12 secuencias): 7 secuencias de ajuste y 5 de comprobación (páginas distintas), 1440 combinaciones × 11 umbrales de confianza.

- **Umbral de confianza con los datos que ya había:** en las fotos de la Fase 2, el 0,5 era exacto: el peor error tenía 0,46. Pero **en los fotogramas no es seguro**: 166 de 101 019 lecturas erróneas superan 0,5 y alguna llega a 0,94, sobre todo en fotogramas movidos (0,7 por fotograma movido frente a 0,05 quieto). Y casi todos los errores seguros son **"4" en celdas manuscritas**: en las fotos, 45 de los 203 4 manuscritos están mal (son 1) con confianza de hasta 0,46, mientras que los demás errores no pasan de 0,29. De ahí dos umbrales (D-08): **0,6 para los 4 manuscritos y 0,35 para el resto**. Aplicados a las fotos, las sospechosas bajan de 19,8 a 13,2 por foto sin ningún error más sin marcar (hay 5 sin marcar con las dos reglas: dígitos tenues que el segmentador no ve en las fotos reducidas).
- **Votación:** la confianza votada pasa de la mejor a la **media** de las lecturas que coinciden. Con la mejor, una lectura suelta de 0,57 de un 1 leído como 4 en todos los fotogramas superaba el umbral, y solo la marcaba la regla de repetidos; con la media, el peor error votado queda en 0,44 (4 manuscrito) y 0,23 (resto), con margen frente a 0,6 y 0,35. 4 lecturas como máximo, completa desde 3 si todas las celdas tienen ≥ 80 % de acuerdo.
- **Celdas dudosas:** una celda con tinta en ≥ 20 % de las lecturas, pero no en la mayoría, queda con tinta y sin confianza, y se marca. Sin esto, los dígitos a lápiz muy tenues (4.4) quedaban como vacíos: el mejor ajuste sin celdas dudosas deja 2 errores sin marcar.
- **Estabilidad y distancia:** movimiento de hasta el **8 %** del lado (el 2 % se reiniciaba por la deriva de la mano) durante 0,3 s, y "Acércate" por debajo del **15 %** del fotograma (antes 25 %; los resultados son iguales entre 10 % y 20 %).

| | Capturan | Sospechosas | Sin marcar | Tiempo (mediana / máx.) |
|---|---|---|---|---|
| Umbrales de 4.3, ajuste | 2/7 | 8,5 | 0 | 1,8 / 1,8 s |
| Umbrales de 4.3, comprobación | 3/5 | 20,7 | 0 | 1,2 / 1,3 s |
| **Elegidos, ajuste** | **7/7** | **13,1** | **0** | 1,7 / 2,4 s |
| **Elegidos, comprobación** | **5/5** | **15,0** | **0** | 1,6 / 1,7 s |

En las 12 secuencias, con los valores por defecto ya cambiados (`SequenceReplayTest`): todas capturan con 4 lecturas, **13,9 sospechosas de media** (4–25) y ningún error sin marcar. **La meta de ≤ 8 no se alcanza** sin arriesgar errores sin marcar: lo que queda son sobre todo 1 manuscritos, que ML Kit lee como 4 con la misma confianza que los 4 de verdad, y manuscritos con confianza baja. Hay configuraciones con 8,6–11 sospechosas, pero con algún error por encima del umbral que solo marca la regla de repetidos; se descartan. El resto queda para la CNN propia (Fase 5), que puede aprender la letra del usuario. Los tiempos usan las lecturas del test (0,26–0,34 s, frente a ≈ 0,2 s en vivo), así que el máximo de 2,4 s es pesimista; se mide en vivo en 4.7.

## 4.6 Pantalla del escáner completa
- Flash (linterna), galería, cerrar y disparador manual como respaldo, con el estilo de la maqueta (iconos de trazo de `StrokeIcons`).
- Captura automática → vibración breve → revisión. El tablero leído entra en la revisión como el de la galería.
- Iconos de la barra de estado sobre el visor: en 4.0 no se vieron una vez (solo el punto verde de la cámara) y luego sí; comprobar que se fuerzan claros sobre la cámara.
- La cámara se suelta al salir de la pantalla y al ir a segundo plano. El estado guardado (3.8) no incluye la cámara: al volver se empieza a escanear de nuevo.
- Añadir la pantalla del escáner a la maqueta de diseño antes de implementarla, para revisarla.

**Hecho cuando:** se escanea, se revisa y se ve la pista sin tocar la galería, en claro y oscuro.

**Resultado:** pantalla según la maqueta (lienzo de diseño, revisada antes de implementar), sin botón de cerrar: el escáner es la primera pantalla y se sale con atrás.

- **Arriba**, la linterna (solo si la cámara tiene, con `Camera.cameraControl.enableTorch`); en debug, además, el botón de grabar secuencias. **Abajo**, la galería y el disparador manual. En medio, una tarjeta con el estado (buscando, acércate, quieto, leyendo con "N de 4" y barra de progreso), que TalkBack anuncia al cambiar.
- **Disparador manual:** se activa con un tablero detectado. Al pulsarlo vibra, el círculo se encoge y la tarjeta dice "Capturando…": si ya hay lecturas votadas captura con ellas; si no, con la siguiente lectura (`LiveScanner.captureNow`).
- **Al capturar:** vibración (la automática; la manual ya vibró al pulsar) y un **barrido de las celdas** en el acento, en diagonal y con la perspectiva del papel (`squareToQuad`), ≈ 0,44 s; luego se abre la revisión con el tablero como el de la galería. Se prototiparon en la maqueta un destello (descartado por molesto) y 5 alternativas; se eligió el barrido.
- **Barra de estado:** con el tema claro del sistema, `enableEdgeToEdge` ponía los iconos oscuros, invisibles sobre la cámara (lo que se vio en 4.0); el escáner los fuerza claros y los devuelve al salir. Con la navegación de tres botones, la barra inferior también salía blanca: se fuerza oscura igual (comprobado en tema claro en 4.7).
- La cámara se suelta al salir y en segundo plano (va con el ciclo de vida); al volver de la revisión se escanea de nuevo, y "Cerrar" en la revisión pasa a "Escanear otro sudoku".

## 4.7 Rendimiento
- Presupuesto por fotograma y tiempo desde "estable" hasta la revisión (< 2 s), medidos en el A57 con las secuencias y en vivo.
- Comprobar que el análisis no se acumula (se descartan fotogramas, no se encolan), el calor y la batería en una sesión de 5 minutos, y que la interfaz no se entrecorta.

**Hecho cuando:** las cifras están anotadas y se cumple el criterio de tiempo.

**Resultado:** sesión en vivo en el A57 el 2026-10-25 (`tools/vision/perf_session.sh`, líneas `LiveMeasure` de debug): 30 capturas de varias páginas del libro, con la cámara activa ≈ 1,6 min de los 4,5 min en primer plano (el resto, en la revisión).

| | Mediana | Máximo |
|---|---|---|
| Conversión a `Mat` gris | 0,7 ms | 4,5 ms |
| Detección del tablero, por fotograma | 18,9 ms | 51 ms |
| Lectura completa (ML Kit), cuando se pide | 118 ms | 261 ms |
| Retraso del fotograma desde el sensor | 108 ms | 155 ms |
| Fotogramas analizados por segundo | 22,4 | mín. 16,6 |
| De quieto a la captura | 0,80 s | 1,21 s |
| **De quieto a la revisión** (captura + barrido de 0,44 s) | **1,26 s** | **1,66 s** |

- **Criterio de tiempo cumplido:** las 30 capturas abren la revisión en menos de 2 s. Las lecturas en vivo (0,12 s) son más rápidas que las del test con las secuencias (0,26–0,34 s), así que el máximo de 2,4 s de 4.5 era pesimista, como se esperaba.
- **El análisis no se acumula:** el retraso desde el sensor es estable (≈ 0,1 s, sin crecer en la sesión) también en las ventanas con lecturas; con `STRATEGY_KEEP_ONLY_LATEST` se descartan los fotogramas que llegan mientras se lee.
- **Interfaz:** 6,5 % de fotogramas entrecortados (mediana 7 ms, p90 18 ms, p99 81 ms) en toda la sesión, revisión incluida. No se midió dónde caen; lo más probable son las transiciones entre el escáner y la revisión, en las que se crea y se suelta la cámara. En uso, el visor con el contorno no se notó entrecortado.
- **Calor y batería:** la temperatura de la batería no cambió (25,5 °C) y el estado térmico siguió en 0. Consumo estimado de la app (`batterystats`): 32 mAh en 4,5 min en primer plano, ≈ 0,4 A, unos 9 % de batería por hora de escaneo continuo en los 5000 mAh del A57. La sesión tuvo menos de 5 minutos seguidos de cámara; el uso real (un escaneo de unos segundos por sudoku) queda muy por debajo.

## 4.8 Prueba de extremo a extremo y cierre
- Test instrumentado con las secuencias: lector real + votación → `ScannedBoard` → corregir solo las sospechosas → primera pista igual a la del motor sobre la etiqueta (como `EndToEndTest`).
- Prueba en vivo en el A57 con varias páginas del libro, anotando el tiempo hasta la captura y las sospechosas.
- Actualizar `03-diseno.md` §4.4 y §5.1, las casillas de `04-plan-implementacion.md` y D-20 a D-22 en `02-decisiones.md`.

**Resultado:** prueba de extremo a extremo en `SequenceScanTest` (en el A57) y en `SequenceReplayTest` (JVM, con los `frames.txt` del móvil): lector real y votación → `ScannedBoard` → se corrigen solo las sospechosas → validación (D-19) → primera pista igual a la del motor sobre la etiqueta. 7 de las 12 páginas están resueltas a lápiz en el libro, así que en ellas se comprueba que, corrigiendo las sospechosas, el tablero queda completo y válido; la pista se compara en las otras 5.

- **El test encontró un error sin marcar** que la 4.5 no vio: en S20261025-205600, el 7 a lápiz de F1C9 es tan tenue que el segmentador solo lo ve en el 11 % de los fotogramas. La regla de celda dudosa (≥ 20 % de las lecturas con tinta) solo lo salvaba cuando, por casualidad, alguna de las 4 lecturas votadas lo veía. Reproduciendo cada secuencia desde 20 arranques distintos, 115 de 720 pasadas dejaban F1C9 o F8C8 sin marcar.
- **Solución:** (1) el segmentador marca como **trazo tenue** una celda vacía cuyo trazo no llega a dígito, pero sí a 0,20 de alto y 25 px de tinta (`Cell.faint`); en los fotogramas, los 3 dígitos tenues de las secuencias pasan de verse en el 11–40 % a verse en el 58–81 %, y en las celdas vacías solo una pasa del 2 %. (2) La votación cuenta como observaciones, además de las lecturas, los fotogramas de la espera hasta que el tablero está quieto, que solo se segmentan (6–8 ms cada uno en el A57, 12 como máximo). Una celda sin dígito votado y con tinta o trazo en ≥ 20 % de las observaciones queda dudosa. Con esto, **0 de 720 pasadas** dejan un error sin marcar, con cualquier umbral entre 0,1 y 0,3, y las sospechosas pasan de 14,2 a 14,3 de media. `SequenceReplayTest` comprueba ahora esas pasadas, no solo una.

| | Capturan | Sospechosas | Sin marcar | Pista o tablero completo correctos | Tiempo hasta la captura |
|---|---|---|---|---|---|
| Secuencias, en el A57 | 12/12 | 14,8 de media (5–26) | 0 | 12/12 | 0,87–1,31 s |
| En vivo, 6 páginas | 6/6 | 12,3 de media (2–31) | — (sin etiqueta) | — | 0,56–0,91 s; a la revisión 1,02–1,37 s |

## Cierre de la fase

| Criterio | Resultado |
|---|---|
| Revisión en < 2 s desde que el tablero está quieto | **Cumplido:** mediana 1,26 s y máximo 1,66 s en 30 capturas en vivo (4.7), 1,02–1,37 s tras 4.8 |
| ≤ 8 celdas sospechosas por tablero de media | **No cumplido:** 13,9–14,8 en las secuencias y 12–15 en vivo, frente a ≈ 20 por foto al empezar. Lo que queda es sobre todo el 1 manuscrito leído como 4 con confianza alta, que la votación no corrige porque se repite en todos los fotogramas; pasa a la CNN propia (Fase 5) |
| Ninguna lectura errónea sin marcar | **Cumplido** en las 12 secuencias, en el A57 y en 720 pasadas en la JVM, y en las 32 fotos de `EndToEndTest` |

Quedan para más adelante: medir con más de un libro y con fotos difíciles (aplazado por decisión del usuario), y que 7 de las 12 secuencias sean páginas ya resueltas, con pocas celdas vacías para medir la tinta falsa.

---

## Orden de trabajo

```
4.0 dependencias y escáner ─► 4.1 fotogramas y medida ─┬─► 4.2 contorno ──────────┬─► 4.6 escáner completo ─► 4.7 rendimiento ─► 4.8 cierre
                                                       ├─► 4.3 estabilidad y voto ┤
                                                       └─► 4.4 secuencias ─► 4.5 ajuste con datos
4.5 (primera parte, umbral con los datos actuales): paralelizable desde el principio
```

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Los fotogramas de vídeo leen peor que una foto (menos nitidez, compresión, enfoque) | Se mide en 4.1 antes de construir encima; alternativa: foto fija al estabilizarse (D-21) |
| La votación no reduce las sospechosas porque el error principal (1/4) es sistemático | Se mide en 4.1 y 4.5; umbral de confianza ajustado con datos; el resto pasa a la Fase 5 sin relajar los 0 errores sin marcar |
| ML Kit tarda ≈ 0,3 s por lectura y limita las lecturas dentro de los 2 s | Máximo de ≈ 5 lecturas; la detección por fotograma es aparte y rápida |
| Las esquinas se ordenan distinto entre fotogramas y las celdas no se corresponden al votar | Orden de esquinas estable (por posición en la imagen) y test con secuencias; el móvil se sostiene en vertical |
| La cámara no se puede automatizar en los tests | Secuencias grabadas (D-22) reproducidas en la JVM y en el móvil |
| Calor o batería en sesiones largas | Análisis que descarta fotogramas, detección ligera y lectura solo con el tablero quieto; se mide en 4.7 |
| Coordenadas del contorno mal traducidas al visor (rotación, recorte) | Utilidades de transformación de CameraX y prueba visual girando el móvil |
| Todo lo medido es de un solo libro | Fuera de alcance por decisión; queda anotado en D-07 para cuando haya fotos difíciles |
