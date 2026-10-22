# 08 · Plan detallado: Fase 3 (app mínima de extremo a extremo)

Objetivo de la fase (de `04-plan-implementacion.md`): **elegir una foto de la galería → revisar el tablero leído → ver la primera pista** y encadenar las siguientes. Se apoya en D-06, D-08, D-12, D-14 y D-16, y en D-18 y D-19, aceptadas al planificarla.

**Hecho cuando:** se puede usar la app con fotos de galería de principio a fin en el A57: con las 30 fotos del conjunto se llega a la primera pista, corrigiendo a mano como mucho las celdas que la app marca como sospechosas.

## Punto de partida

- `solver`: `Grid` (valores y candidatos, `apply(step)`), `HintEngine.nextStep`, `Backtracker` (solución y número de soluciones), `Step` estructurado y 13 técnicas hasta subconjuntos.
- `vision-core` + `vision`: `BoardReader` con `MlKitGridReader` lee una foto en ≈ 0,3 s en el A57; impresos 100 %, manuscritos 94,6 %, y ninguna lectura con confianza ≥ 0,5 es errónea.
- Diseño de las pantallas y tokens de color en `03-diseno.md` §5, con la maqueta en <https://claude.ai/artifact/RBrWurNUeeJH8y5QJqf7aZ>.
- Pendiente de la Fase 2 que afecta aquí: la regla del origen (impreso si el contraste de la tinta es ≥ 0,4) solo existe en los tests de `vision-core`, y el detector está ajustado a fotos de 1600 px de lado mayor.

## Arquitectura de la fase

```
app (Android, Compose)
├── ui/        SudokuBoard, pantallas Revisión y Pista, tema (tokens de §5.3)
├── state/     CoachViewModel: Picking → Reading → Reviewing → ShowingHint
├── review/    ReviewBoard, celdas sospechosas, validación (Kotlin puro, tests en la JVM)
├── explain/   Step → Explanation (clave + argumentos); textos en res/values/strings.xml
└── scan/      PhotoScanner: Uri → Bitmap orientado y reducido → BoardReader
        │                     │
        ▼                     ▼
     solver               vision (MlKitGridReader) → vision-core
```

- Todo lo que no dibuja ni toca Android (`review/`, `explain/`, la máquina de estados) es Kotlin puro y se prueba con tests locales (`:app:testDebugUnitTest`), en la JVM y en la CI, sin móvil.
- La visión entra por una interfaz `PhotoScanner`; los tests del `ViewModel` usan una falsa que devuelve una lectura fija.

## Decisiones

### D-18 · Arquitectura de la app
- **Una sola `Activity`, Compose y un `CoachViewModel`** con un `StateFlow<CoachState>` (flujo unidireccional, `03-diseno.md` §5.2).
- **Navegación con Navigation 3**: la pila de pantallas es una lista de rutas que guarda el propio `ViewModel`, lo que encaja con la máquina de estados del diseño y da el gesto de atrás predictivo. Alternativa si da problemas con AGP 9: pintar la pantalla según el estado, con `BackHandler`; con tres pantallas basta. La Fase 4 añade el escáner, así que conviene ya una pila real.
- **Sin inyección de dependencias** (ni Hilt ni Koin): una `ViewModelProvider.Factory` con los colaboradores. Se reconsidera si la app crece.
- **Fuente empaquetada** (IBM Plex Sans, OFL) en `res/font`, no descargable: así se cumple D-03 sin depender de Google Play Services.

### D-19 · Validación antes de dar pistas
Una pista sobre un tablero mal leído o con errores del usuario no tiene sentido, y el `HintEngine` supone un tablero correcto. Antes de pedir una pista:
1. **Repetidos** en fila, columna o caja: se marcan y se bloquea "Ver pista".
2. **Sin solución con todos los dígitos**: si los impresos solos tienen solución única, los manuscritos que no coinciden con ella se marcan como **posibles errores del usuario** ("Este 4 no encaja con la solución"). Si ni los impresos tienen solución, la lectura está mal: se pide revisar.
3. **Varias soluciones**: se avisa y se permite seguir, porque las técnicas de la Fase 1 no suponen unicidad. Las de unicidad (Fase 6) se desactivarán en ese caso.

Esto aprovecha el origen de cada dígito, que el segmentador acierta en un 99,9 %.

---

## 3.0 Módulo `app`
- `include(":app")`, plugin `com.android.application` 9.4.1, plugin de Compose de Kotlin (`org.jetbrains.kotlin.plugin.compose` 2.4.20) y la BOM de Compose estable más reciente compatible. Las versiones se fijan al empezar, comprobando la matriz de compatibilidad, como se hizo con AGP en 2.0.
- `applicationId` `dev.sudokucoach`, `minSdk` 26, `targetSdk`/`compileSdk` 37, solo `arm64-v8a` y `x86_64` (como `vision`), y nombre "Sudoku Coach".
- Dependencias: `:solver`, `:vision`, `activity-compose`, `lifecycle-viewmodel-compose`, `material3` y Navigation 3 (D-18).
- **Tamaño del APK**: el de test de `vision` ocupa 124 MB (OpenCV para dos ABI y las 30 fotos). Medir el de la app en *debug* y *release* (con R8) y anotarlo; si pasa de ~60 MB en *release*, valorar los *App Bundles* por ABI.
- CI: el trabajo de Android pasa a ejecutar `:vision:assembleDebug :vision:assembleDebugAndroidTest :app:assembleDebug :app:testDebugUnitTest`.

**Hecho cuando:** `./gradlew :app:installDebug` instala en el A57 una pantalla vacía con el tema y la CI está en verde.

## 3.1 Tema y tokens (D-12)
- `SudokuColors` con los 8 tokens de §5.3 en claro y oscuro, expuestos con un `CompositionLocal`, y un `ColorScheme` de Material 3 derivado de ellos para los componentes estándar. Sin Dynamic Color.
- Tipografía: IBM Plex Sans (regular, medium y bold) con cifras tabulares (`fontFeatureSettings = "tnum"`) y licencia OFL en `app/src/main/assets/licenses/`.
- **Test de contraste** (JVM): ratio WCAG de cada par de uso (`ink`/`background`, `accent`/`background`, `onAccent`/`accent`, `ink`/`accentSoft`, `inkMuted`/`surface`) ≥ 4,5 en los dos modos. Así un cambio de paleta no rompe la legibilidad sin que se note.

**Hecho cuando:** el test de contraste pasa y la app sigue el modo del sistema.

## 3.2 Componente `SudokuBoard`
- Un único `Canvas` (no 81 composables): líneas finas `gridLine` y gruesas de caja `ink`, dígitos en negrita si son impresos y en regular si son manuscritos o de origen desconocido.
- Entrada: `BoardUi`, que tiene por celda el valor, el origen y una decoración (`Selected`, `Pattern`, `Placement`, `Suspicious`, `Error`), más los candidatos a mostrar y los eliminados. El componente no sabe nada de técnicas.
- Mini-rejilla 3×3 de candidatos solo en las celdas que la pista pide; eliminados tachados en `inkMuted`.
- Toque → índice de celda (`pointerInput`). Accesibilidad: un nodo semántico por celda ("Fila 3, columna 5: 7, impreso").
- Cuadrado, a todo el ancho y con tamaño de texto proporcional a la celda.

**Tests:** instrumentados con `createComposeRule`: tocar una celda la selecciona y la semántica describe el tablero. La revisión visual se hace en el A57 con una pantalla de catálogo solo en *debug* (todas las decoraciones en claro y oscuro), sin capturas de referencia por ahora.

## 3.3 Lectura de la foto (`scan/`)
- **Photo Picker** (`PickVisualMedia`, solo imágenes): no pide permisos y en API 26–29 lo trae la biblioteca de compatibilidad.
- **Orientación y tamaño**: decodificar respetando la orientación EXIF (`ImageDecoder` en API ≥ 28; `BitmapFactory` + `androidx.exifinterface` por debajo) y reducir a **1600 px de lado mayor**, como `prepare_photos.py`, porque los umbrales del detector se ajustaron a ese tamaño. Muestreo al decodificar para no cargar 12 MP en memoria.
- `PhotoScanner` (interfaz) y su implementación con `BoardReader(MlKitGridReader())`, en `Dispatchers.Default`. OpenCV se carga al arrancar la app.
- **Origen en producción**: llevar la regla del contraste (≥ 0,4 → impreso) de los tests a `vision-core` (`Cell.origin`) y usarla en `VisionMetricsTest` y `LabelHelper` para no duplicarla.
- Fallos: sin tablero → mensaje "No encuentro el tablero" con la opción de elegir otra foto.

**Tests:** instrumentado con una foto del conjunto girada por EXIF, generada en el test: se detecta el tablero. JVM: la regla de origen en `vision-core`.

## 3.4 Tablero de revisión y validación (`review/`, D-19)
- `ReviewBoard`: 81 valores, origen (`GIVEN`, `USER`, `UNKNOWN`) y confianza de la lectura.
- **Sospechosas**: celda con tinta sin lectura, confianza < 0,5, o dígito repetido en una unidad.
- **Validación** según D-19 con `Backtracker`: repetidos, posibles errores del usuario, sin solución y varias soluciones.
- Al editar: la celda pasa a confianza 1; un dígito nuevo en una celda vacía tiene origen `UNKNOWN` y cuenta como impreso en la validación.

**Tests (JVM):** con las 30 etiquetas de `testdata/vision/labels` como tableros reales, todas pasan la validación. Con un manuscrito cambiado a propósito, se marca justo esa celda; con un impreso cambiado, se pide revisar.

## 3.5 Pantalla de revisión
- Tablero, teclado numérico fijo (1–9 y borrar) y el aviso "N celdas por revisar", que al tocarlo salta a la siguiente sospechosa.
- "Ver pista" activo solo si la validación lo permite; si no, el motivo en una línea.
- Durante la lectura, la foto con un indicador de progreso.

**Hecho cuando:** con la foto 016 (un 9 a lápiz tenue en F2C9), la celda aparece como sospechosa, se corrige con el teclado y se llega a la pista.

## 3.6 Textos de explicación (D-14)
- `StepExplainer` (Kotlin puro): `Step` → `Explanation(técnica, plantilla, argumentos)`, con celdas como "F3C5", unidades como "fila 3", "columna 5" o "caja 2" y listas de dígitos como "1 y 3" o "1, 3 y 7".
- Plantillas en `res/values/strings.xml`: una por `TechniqueId` implementada, con nombre, nivel ("NIVEL 3 · SUBCONJUNTOS") y explicación. Se usan `plurals` donde hace falta (una o varias eliminaciones).
- Los nombres de las técnicas siguen el uso habitual en español, manteniendo los términos ingleses consolidados (X-Wing, Naked Pair → "par desnudo", Hidden Single → "único oculto"…). La lista se revisa con el usuario antes de escribir los textos.

**Tests (JVM):** cada técnica de `HintEngine.IMPLEMENTED` tiene plantilla, y para un paso real de cada técnica, sacado del banco del oráculo, los argumentos son correctos. Un test instrumentado resuelve todas las plantillas con sus argumentos, para detectar marcadores que no coinciden.

## 3.7 Pantalla de pista
- Tablero a todo el ancho y `BottomSheetScaffold` (no modal, para que el tablero se vea) con el revelado gradual de §5.3:
  1. Técnica y nivel.
  2. Celdas del patrón (`highlight`) y sus candidatos.
  3. Colocación, eliminaciones y explicación, con "Aplicar paso" y "Siguiente".
- **Estado con candidatos**: la pista trabaja sobre un `Grid` (valores y candidatos), no solo sobre los valores, porque un paso de eliminación cambia los candidatos sin colocar nada.
- "Aplicar paso" → `grid.apply(step)` y la siguiente pista empieza en el nivel 1. Tablero resuelto → mensaje final.
- **Sin paso disponible** (hacen falta técnicas de la Fase 6): mensaje "Este tablero necesita técnicas que aún no conozco" y opción "Revelar un dígito" con la solución del `Backtracker`.

## 3.8 Estado y navegación (D-18)
- `CoachState`: `Picking`, `Reading`, `Reviewing(board, validation)`, `ShowingHint(grid, step, revealLevel)` y `Failed(motivo)`.
- `SavedStateHandle` guarda el tablero como texto (valores, origen y candidatos) para sobrevivir al cierre del proceso. La foto no se guarda.
- Atrás: de la pista a la revisión, que conserva las correcciones; de la revisión a elegir foto.

**Tests (JVM):** transiciones del `ViewModel` con un `PhotoScanner` falso y `kotlinx-coroutines-test`: lectura correcta, lectura fallida, edición, validación bloqueante, aplicar paso hasta resolver un tablero del banco.

## 3.9 Prueba de extremo a extremo y cierre
- Test instrumentado con una foto del conjunto como asset: `PhotoScanner` real → `ReviewBoard` igual a la etiqueta salvo las sospechosas → primera pista igual a `HintEngine.nextStep` sobre la etiqueta.
- **Recorrido manual** en el A57 con las 30 fotos (copiadas a la galería): anotar por foto las celdas corregidas, si se llega a la pista y el tiempo de lectura. Es la medida del criterio de la fase.
- Actualizar `03-diseno.md` §5 con lo implementado, las casillas de `04-plan-implementacion.md` y D-18 y D-19 en `02-decisiones.md`.

---

## Orden de trabajo

```
3.0 módulo app ─► 3.1 tema ─► 3.2 SudokuBoard ─┬─► 3.5 revisión ─┬─► 3.7 pista ─► 3.9 cierre
3.3 lectura de la foto ────────────────────────┤                 │
3.4 revisión y validación (JVM) ───────────────┘                 │
3.6 textos (JVM, paralelizable desde 3.0) ───────────────────────┘
3.8 estado y navegación: crece con cada pantalla
```

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Incompatibilidades entre AGP 9.4, Kotlin 2.4 y Compose o Navigation 3 | Fijar versiones en 3.0 con la matriz oficial; alternativa sin biblioteca de navegación (D-18) |
| Fotos de galería distintas a las del conjunto (otro tamaño, orientación, capturas de pantalla) | Reducir a 1600 px y aplicar EXIF (3.3); las fotos difíciles se miden en la Fase 4 |
| El usuario no ve qué celdas revisar | Sospechosas por confianza y por reglas, contador navegable; validación de D-19 |
| Pista engañosa por un error del usuario en el papel | D-19: los manuscritos que contradicen la solución de los impresos se marcan antes de dar pistas |
| APK grande por OpenCV (dos ABI) | Medir en 3.0; App Bundle por ABI si hace falta |
| Textos de técnicas poco claros | Revisar la terminología con el usuario antes de 3.6; los textos son recursos, fáciles de cambiar |
| Sin Android Studio no hay vistas previas de Compose | Pantalla de catálogo en *debug* en el móvil (3.2); Android Studio por WSLg si hiciera falta (D-15) |
