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
- `applicationId` `dev.sudokucoach`, `minSdk` 26, `targetSdk`/`compileSdk` 37, solo `arm64-v8a` y `x86_64` (como `vision`; en 3.2 se quitó `x86_64`), y nombre "Sudoku Coach".
- Dependencias: `:solver`, `:vision`, `activity-compose`, `lifecycle-viewmodel-compose`, `material3` y Navigation 3 (D-18).
- **Tamaño del APK**: el de test de `vision` ocupa 124 MB (OpenCV para dos ABI y las 30 fotos). Medir el de la app en *debug* y *release* (con R8) y anotarlo; si pasa de ~60 MB en *release*, valorar los *App Bundles* por ABI.
- CI: el trabajo de Android pasa a ejecutar `:vision:assembleDebug :vision:assembleDebugAndroidTest :app:assembleDebug :app:testDebugUnitTest`.

**Hecho cuando:** la app se instala en el A57 una pantalla vacía con el tema y la CI está en verde.

**Resultado:** módulo `app` con AGP 9.4.1, el plugin de Compose de Kotlin 2.4.20, la BOM de Compose 2026.09.00, Navigation 3 1.2.0, Activity 1.13.0 y Lifecycle 2.11.0. `MainActivity` muestra con `NavDisplay` la pila de pantallas de `CoachViewModel` (por ahora solo `Route.Picking`, un marcador de texto). Atrás en la primera pantalla cierra la app. Los tests locales usan JUnit 4 (`kotlin("test-junit")`), porque con `kotlin("test")` AGP no elige variante. La app arranca en el A57 en 0,7 s; el tema propio queda para 3.1.

| APK | Tamaño | Detalle |
|---|---|---|
| *debug* | 126 MB | |
| *release* (R8) | 113 MB | `x86_64` 73 MB, `arm64-v8a` ≈ 40 MB (OpenCV 25 MB, ML Kit 11 MB, código 2 MB) |

Solo para el A57 serían unos 40 MB, por debajo del umbral de 60 MB, así que no se cambia nada: al publicar (Fase 7), el App Bundle reparte por ABI. R8 no necesitó reglas propias para OpenCV ni ML Kit.

`installDebug` se quedó colgado con el APK copiado en el móvil y sin instalar (sin ningún aviso en pantalla); `adb install` lo resolvió. En 3.2 se vio que pasa siempre y se sustituyó por `:app:deployDebug`.

## 3.1 Tema y tokens (D-12)
- `SudokuColors` con los 8 tokens de §5.3 en claro y oscuro, expuestos con un `CompositionLocal`, y un `ColorScheme` de Material 3 derivado de ellos para los componentes estándar. Sin Dynamic Color.
- Tipografía: IBM Plex Sans (regular, medium y bold) con cifras tabulares y licencia OFL en `app/src/main/assets/licenses/`.
- **Test de contraste** (JVM): ratio WCAG de cada par de uso (`ink`/`background`, `accent`/`background`, `onAccent`/`accent`, `ink`/`accentSoft`, `inkMuted`/`surface`) ≥ 4,5 en los dos modos. Así un cambio de paleta no rompe la legibilidad sin que se note.

**Hecho cuando:** el test de contraste pasa y la app sigue el modo del sistema.

**Resultado:** `SudokuColors` (`Light` y `Dark`) con `LocalSudokuColors`, `SudokuTheme.colors` para leerlos y `SudokuCoachTheme`, que deriva de ellos el `ColorScheme` de Material 3: el acento ocupa primario, secundario y terciario, y las superficies son `background` y `surface`, para que ningún componente estándar meta otro color. La ventana usa el mismo `background` en claro y oscuro, así que no hay destello al abrir. Comprobado en el A57 en los dos modos.

- **Fuente:** IBM Plex Sans **variable** de Google Fonts (un TTF de 537 KB, ejes `wght` 100–700 y `wdth`), en `res/font`, con los pesos 400, 500 y 700 por `FontVariation`; licencia en `assets/licenses/`. El paquete npm de IBM solo trae WOFF, que Android no lee. **Sin `tnum`**: la fuente no tiene esa característica porque sus cifras ya son tabulares (las diez miden 600 unidades).
- **Contraste:** `ContrastTest` comprueba 10 pares de texto en los dos modos, no solo los 5 previstos: también los candidatos eliminados y el dígito a colocar dentro de una celda del patrón. Ese caso daba 4,46 en claro con `inkMuted` `#6B6B6F`, así que el token pasa a **`#69696D`** (4,60; el cambio no se aprecia a la vista). El test falla con el valor antiguo.

## 3.2 Componente `SudokuBoard`
- Un único `Canvas` (no 81 composables): líneas finas `gridLine` y gruesas de caja `ink`, dígitos en negrita si son impresos y en regular si son manuscritos o de origen desconocido.
- Entrada: `BoardUi`, que tiene por celda el valor, el origen y una decoración (`Selected`, `Pattern`, `Placement`, `Suspicious`, `Error`), más los candidatos a mostrar y los eliminados. El componente no sabe nada de técnicas.
- Mini-rejilla 3×3 de candidatos solo en las celdas que la pista pide; eliminados tachados en `inkMuted`.
- Toque → índice de celda (`pointerInput`). Accesibilidad: un nodo semántico por celda ("Fila 3, columna 5: 7, impreso").
- Cuadrado, a todo el ancho y con tamaño de texto proporcional a la celda.

**Tests:** instrumentados con `createComposeRule`: tocar una celda la selecciona y la semántica describe el tablero. La revisión visual se hace en el A57 con una pantalla de catálogo solo en *debug* (todas las decoraciones en claro y oscuro), sin capturas de referencia por ahora.

**Resultado:** `SudokuBoard(board: BoardUi, onCellClick)` en `ui/board/`. `BoardUi` son 81 `CellUi` con el valor, el origen (`DigitOrigin`: `GIVEN` negrita, `USER` y `UNKNOWN` regular), un conjunto de `CellDecoration`, la colocación y los candidatos visibles y eliminados.

- **Dibujo:** una sola pasada en `drawWithCache`; los 36 glifos (9 dígitos × impreso, manuscrito, colocación y candidato) se miden una vez por tamaño de celda, con tamaños en px de la celda, así que no crecen con la escala de fuente del sistema. Rellenos, dígitos y candidatos, después las líneas (finas `gridLine`, de caja y borde `ink`) y por encima los contornos, metidos en la celda para no confundirse con las líneas.
- **Decoraciones:** `SELECTED` y `PATTERN` rellenan con `accentSoft`; `PLACEMENT` y `ERROR`, contorno continuo de acento (el error pinta además el dígito en acento); `SUSPICIOUS`, contorno punteado. El tablero de revisión no tiene colocaciones, así que continuo frente a punteado basta para distinguir error de sospechosa.
- **Toque y accesibilidad:** en vez de `pointerInput`, encima del dibujo hay 81 cajas transparentes, una por celda, con `clickable`, `contentDescription` ("Fila 1, columna 3: vacía. parte de la pista. candidatos 1, 3. se descarta 8"), `selected` y `testTag`. Así cada celda es un nodo que TalkBack recorre y pulsa, y que los tests pueden tocar.
- **Tests:** `SudokuBoardTest` (3, en el A57): tocar selecciona, descripción de impresos, manuscritos y vacías (81 nodos) y descripción de las marcas de la pista. Usa `createComposeRule` de `junit4.v2`, porque la v1 está obsoleta.
- **Catálogo:** `BoardCatalogActivity`, solo en *debug* (`adb shell am start -n dev.sudokucoach/dev.sudokucoach.app.debug.BoardCatalogActivity`), con todas las decoraciones; revisado en claro y oscuro.

**Instalación en el A57.** `installDebug` de AGP se queda colgado siempre (5 min sin instalar, también con el daemon reiniciado), mientras que `adb install` del mismo APK tarda 2–3 s. Se añade `./gradlew :app:deployDebug`, que compila e instala con el `adb` del SDK. `connectedDebugAndroidTest` instala bien y no cambia. Además, las copias por usbipd se vuelven lentas a ratos (de 50 MB/s a 0,6 MB/s con el mismo archivo), así que se quita `x86_64` de `app` y de `vision`: era solo para un emulador que no se usa. El APK de *debug* baja de 126 a 53 MB y el de test de `vision`, de 124 a 50 MB.

## 3.3 Lectura de la foto (`scan/`)
- **Photo Picker** (`PickVisualMedia`, solo imágenes): no pide permisos y en API 26–29 lo trae la biblioteca de compatibilidad.
- **Orientación y tamaño**: decodificar respetando la orientación EXIF (`ImageDecoder` en API ≥ 28; `BitmapFactory` + `androidx.exifinterface` por debajo) y reducir a **1600 px de lado mayor**, como `prepare_photos.py`, porque los umbrales del detector se ajustaron a ese tamaño. Muestreo al decodificar para no cargar 12 MP en memoria.
- `PhotoScanner` (interfaz) y su implementación con `BoardReader(MlKitGridReader())`, en `Dispatchers.Default`. OpenCV se carga al arrancar la app.
- **Origen en producción**: llevar la regla del contraste (≥ 0,4 → impreso) de los tests a `vision-core` (`Cell.origin`) y usarla en `VisionMetricsTest` y `LabelHelper` para no duplicarla.
- Fallos: sin tablero → mensaje "No encuentro el tablero" con la opción de elegir otra foto.

**Tests:** instrumentado con una foto del conjunto girada por EXIF, generada en el test: se detecta el tablero. JVM: la regla de origen en `vision-core`.

**Resultado:** en `scan/`, `PhotoScanner` (recibe la URI como texto, para probar el `ViewModel` en la JVM) con `ScannedBoard` y `ScanResult`; `PhotoDecoder` (`ImageDecoder` en API ≥ 28; `BitmapFactory` + `exifinterface` en API 26–27) y `VisionPhotoScanner` (`BoardReader` con `MlKitGridReader`, que libera los `Mat`). `CoachViewModel(scanner)` expone `scanState` (`Idle`, `Reading`, `Failed(NO_BOARD | UNREADABLE)`) y añade `Route.Reviewing`; `PickingScreen` abre el selector de fotos y `ReviewingScreen`, provisional, muestra el tablero leído. `vision` expone `vision-core` y OpenCV como `api`, y `DigitOrigin` pasa a `dev.sudokucoach.app.board`.

- **Origen:** `Cell.printed` y `Cell.PRINTED_CONTRAST` en `vision-core`, usados también por `VisionMetricsTest`, `LabelHelper` y `GridSegmenterTest`.
- **Originales de la cámara:** `PhotoScanTest.leeLosOriginalesDeLaCamara` lee los 30 originales (4000×3000 con EXIF), copiados a `/data/local/tmp/sudoku-raw/` porque no se versionan (en el bucle de copia hace falta `adb shell -n` y `adb push … </dev/null`, o `adb` se come la entrada del `while`). La primera pasada dio 7 lecturas seguras erróneas: 6 eran lápices tenues que el segmentador tomaba por celdas vacías. 4 ya fallaban con las fotos preparadas (ver 2.5), y reducir con `INTER_AREA`, `INTER_LANCZOS4` o `INTER_LINEAR` en vez de con `ImageDecoder` daba 5–7, así que no era la reducción. `GridSegmenter.minDigitHeight` baja de 0,35 a **0,26**: no se pierde ninguno en los dos conjuntos y no aparece tinta falsa nueva hasta 0,24. Métricas JVM: manuscritos 1455/1455 (antes 1451) y vacía/no vacía 2429/2430.
- Queda **006 F7C6**, un 1 leído como 4 con confianza ≥ 0,5. Repite el 4 de F7C9, así que lo marca la validación de D-19 (3.4); el test exige que no haya lecturas seguras erróneas *sin repetido*. En total, 1697/2201 dígitos con lectura segura y 0,3–0,6 s por foto en el A57.
- **Tests:** 6 nuevos de `CoachViewModelTest` en la JVM; en el A57, los 8 instrumentados (`PhotoScanTest` y `SudokuBoardTest`). La decodificación endereza una foto de 4000×3000 con EXIF 90° a 1200×1600 por las dos vías, y una foto pequeña no se amplía. Con la pantalla bloqueada, los tests de Compose fallan con "No compose hierarchies found".
- `leeLaFoto001ComoSuEtiqueta` usa la foto del conjunto tal cual: ampliarla a 4000×3000 y recomprimirla le quitaba nitidez y perdía un 7 a lápiz.
- **Prueba a mano** (usuario, A57): con varias fotos de la galería se ve el tablero leído. En algunos tableros completos queda una celda en blanco; se corrige fácilmente a mano y no se ajusta ahora (los tableros completos no son el caso de uso, y el reconocedor propio está planificado).

## 3.4 Tablero de revisión y validación (`review/`, D-19)
- `ReviewBoard`: 81 valores, origen (`GIVEN`, `USER`, `UNKNOWN`) y confianza de la lectura.
- **Sospechosas**: celda con tinta sin lectura, confianza < 0,5, o dígito repetido en una unidad.
- **Validación** según D-19 con `Backtracker`: repetidos, posibles errores del usuario, sin solución y varias soluciones.
- Al editar: la celda pasa a confianza 1; un dígito nuevo en una celda vacía tiene origen `UNKNOWN` y cuenta como impreso en la validación.

**Tests (JVM):** con las 30 etiquetas de `testdata/vision/labels` como tableros reales, todas pasan la validación. Con un manuscrito cambiado a propósito, se marca justo esa celda; con un impreso cambiado, se pide revisar.

**Resultado:** en `review/`, `ReviewBoard` (inmutable, con `ReviewBoard.from(ScannedBoard)`) y `BoardValidator`, en Kotlin puro.

- **Sospechosas:** tinta sin lectura, confianza < `MIN_CONFIDENCE` (0,5, D-08) o dígito repetido en una unidad.
- **Editar** (`edit(cell, digit)`, 0 borra): confianza 1 y tinta solo si hay dígito, así que una celda revisada deja de ser sospechosa salvo por repetido. Un dígito nuevo en una celda vacía tiene origen `UNKNOWN`; corregir una lectura conserva el origen, porque el contraste de la tinta sigue valiendo.
- **Validación** (`Validation`, con `allowsHints`): `Repeated(celdas)` → bloquea; tablero completo con una solución → `Ok`, con varias → `MultipleSolutions` (se permite seguir); sin solución, si los impresos y los `UNKNOWN` tienen solución única → `UserErrors(manuscritos que no coinciden)`, y si no → `Unsolvable` (revisar la lectura). Los posibles errores del usuario también bloquean la pista, porque `HintEngine` supone un tablero correcto.
- **Tests (JVM, 8):** las 30 etiquetas pasan sin sospechosas; un manuscrito cambiado sin crear repetidos da `UserErrors` justo en esa celda (en todas las etiquetas donde se puede); en un tablero lleno, cualquier cambio da un repetido; sospechosas, edición y un `UNKNOWN` mal puesto que no se achaca al usuario.
- **Límite de D-19, medido:** se probaron los 176 cambios de un impreso por un dígito que no crea repetidos. 112 piden revisar, 58 no se pueden detectar (el tablero sigue teniendo solución) y **6 culpan a manuscritos correctos** (004, 015 y 019), porque los impresos cambiados siguen teniendo solución única. El test exige ≥ 60 % de "revisar" y ≤ 5 % de culpas. La primera defensa es la confianza: en el A57 ninguna lectura segura de un impreso falló (3.3). Por eso el texto de 3.5 debe decir "posible error", no afirmarlo.

## 3.5 Pantalla de revisión
- Tablero, teclado numérico fijo (1–9 y borrar) y el aviso "N celdas por revisar", que al tocarlo salta a la siguiente sospechosa.
- "Ver pista" activo solo si la validación lo permite; si no, el motivo en una línea.
- Durante la lectura, la foto con un indicador de progreso.

**Hecho cuando:** con la foto 016 (un 9 a lápiz tenue en F2C9), la celda aparece como sospechosa, se corrige con el teclado y se llega a la pista.

**Resultado:** `ReviewingScreen` sigue la maqueta: barra con cerrar (vuelve a elegir foto), aviso "N celdas por revisar" con ◀ ▶ (o "Nada por revisar"), tablero, una línea sobre la celda seleccionada, teclado de 5 × 2 y, fijos abajo, el motivo de la validación y "Ver pista". Si la pantalla es baja, solo se desplaza la parte de arriba. Comprobada en el A57, donde cabe entera.

- **Estado:** `CoachViewModel.review: StateFlow<ReviewState?>` con el `ReviewBoard`, la celda seleccionada y la validación, que se recalcula en cada edición. Al leer la foto se selecciona la primera sospechosa. Acciones: `selectCell`, `enterDigit` (0 borra), `moveToSuspicious` (da la vuelta al llegar al final) y `showHint`, que no hace nada si la validación no lo permite.
- **Decoraciones:** seleccionada (`SELECTED`), sospechosa (punteado) y posible error del usuario (`ERROR`). La línea de la celda dice por qué se marca ("lectura poco segura: confírmala", "no se pudo leer: escribe el dígito", "el 4 se repite", "este 4 podría no encajar") o su origen. **A diferencia de la maqueta, los impresos también se pueden editar**, porque pueden estar mal leídos. Textos en una línea, para que todo quepa.
- **Validación en una línea** (D-19): repetidos, posibles errores del usuario (con `plurals` y "podrían", por el límite medido en 3.4), sin solución y varias soluciones, con la que se puede seguir.
- **Durante la lectura** se muestra la foto (reducida a 800 px con `PhotoDecoder`, que acepta otro lado mayor) con el indicador de progreso. `ScanState.Reading` lleva la foto.
- **Pista provisional:** `Route.Hint` y `HintScreen` con el patrón, la colocación y el nombre interno de la técnica (`NAKED_PAIR`); la pantalla de verdad es la 3.7. Atrás vuelve a la revisión con las correcciones.
- **Iconos:** cuatro `ImageVector` de trazo en `StrokeIcons` (cerrar, ◀, ▶, borrar), en vez de añadir la biblioteca de iconos de Material.
- **Tests:** 4 nuevos de `CoachViewModelTest` (saltar entre sospechosas, corregir y revalidar, un repetido bloquea la pista, "Ver pista" da el paso del motor). Instrumentado `ReviewFlowTest` con la lectura real de la foto 016 (añadida a los assets de test): F2C9 no se lee y sale sospechosa, de 14 sospechosas (la mayoría lecturas correctas con poca confianza). Las dos lecturas distintas de la etiqueta están entre ellas. Se corrigen con el teclado, se llega a "Nada por revisar" y "Ver pista" muestra la técnica que da `HintEngine` sobre la etiqueta.

## 3.6 Textos de explicación (D-14)
- `StepExplainer` (Kotlin puro): `Step` → `Explanation(técnica, plantilla, argumentos)`, con celdas como "F3C5", unidades como "fila 3", "columna 5" o "caja 2" y listas de dígitos como "1 y 3" o "1, 3 y 7".
- Plantillas en `res/values/strings.xml`: una por `TechniqueId` implementada, con nombre, nivel ("NIVEL 3 · SUBCONJUNTOS") y explicación. Se usan `plurals` donde hace falta (una o varias eliminaciones).
- Los nombres de las técnicas siguen el uso habitual en español, manteniendo los términos ingleses consolidados (X-Wing, Naked Pair → "par desnudo", Hidden Single → "único oculto"…). La lista se revisa con el usuario antes de escribir los textos.

**Tests (JVM):** cada técnica de `HintEngine.IMPLEMENTED` tiene plantilla, y para un paso real de cada técnica, sacado del banco del oráculo, los argumentos son correctos. Un test instrumentado resuelve todas las plantillas con sus argumentos, para detectar marcadores que no coinciden.

**Resultado:** en `explain/`, `StepExplainer` (Kotlin puro) da `Explanation(técnica, argumentos)` con argumentos **estructurados**: celda, celdas, unidad, dígito, dígitos y eliminaciones agrupadas por dígito. `ExplanationText` (Android) los pasa a texto con `strings.xml`. Así, todo lo que lee el usuario, incluidos "la fila 3", "y" o "se descarta/n", está en los recursos (D-14), y lo que se prueba en la JVM es el contenido, no la redacción. El `when` de `ExplanationText.texts` es exhaustivo: una técnica nueva no compila sin sus textos.

**Nombres, revisados con el usuario** (con el nombre inglés como subtítulo, para poder buscar la técnica):

| Nivel | Técnica | Nombre | Subtítulo |
|---|---|---|---|
| 1 · ÚNICOS | `FULL_HOUSE` | Última celda libre | Full House |
| | `NAKED_SINGLE` | Candidato único | Naked Single |
| | `HIDDEN_SINGLE` | Posición única | Hidden Single |
| 2 · INTERSECCIONES | `LOCKED_PAIR`, `LOCKED_TRIPLE` | Par / Trío bloqueado | Locked Pair / Triple |
| | `LOCKED_CANDIDATES_POINTING` | Bloqueo caja → línea | Locked Candidates (Pointing) |
| | `LOCKED_CANDIDATES_CLAIMING` | Bloqueo línea → caja | Locked Candidates (Claiming) |
| 3 · SUBCONJUNTOS | `NAKED_PAIR` … `NAKED_QUAD` | Par / Trío / Cuarteto desnudo | Naked Pair / Triple / Quad |
| | `HIDDEN_PAIR` … `HIDDEN_QUAD` | Par / Trío / Cuarteto oculto | Hidden Pair / Triple / Quad |

El usuario prefirió nombres descriptivos en el nivel 1 y los de los bloqueos, que dicen hacia dónde se elimina, a los literales (Único desnudo/oculto, Apuntando/Reclamando). Los niveles 4–7 ya tienen etiqueta (PECES, ALAS, UNICIDAD, CADENAS).

- **Redacción:** las celdas son "F3C5" y las listas, "1 y 3" o "1, 3 y 7". Las eliminaciones van agrupadas por dígito: "se descarta el 4 en F6C5", "se descartan el 3 en F4C1 y F8C1, y el 5 en F5C1" (la coma antes del último "y" separa grupos que ya llevan el suyo). El singular o plural se decide con `plurals` según el número de grupos. Ejemplo real con la foto 016: "En la columna 5, las celdas F5C5 y F9C5 solo admiten entre las dos los dígitos 1 y 4. Esos dígitos tienen que ir en ellas, así que se descarta el 4 en F6C5."
- **Tests (JVM, 10):** un paso real de cada técnica, el primero que aparece al resolver los puzzles del banco del oráculo (cuya ruta se pasa a los tests de `app`). Ningún puzzle del banco usa un cuarteto oculto, así que ese sale del tablero sintético de `HiddenSubsetsTest`. Se comprueba que las unidades contienen las celdas, que el bloqueo caja → línea tiene el patrón en una caja y el de línea → caja en una línea, que las eliminaciones son exactamente las del paso y que quedan dentro o fuera del patrón según la técnica.
- **Instrumentado (`ExplanationTextTest`):** cada plantilla usa exactamente los marcadores `%1$`…`%n$` de sus argumentos, con `%d` solo en los dígitos (un argumento de más se ignoraría sin aviso), no queda ningún `%` en el texto, y cuatro textos de ejemplo salen exactos.
- La pantalla de pista provisional muestra ya el nivel, el nombre, el subtítulo y la explicación, todo a la vez. El revelado gradual es la 3.7.
- Lint avisa de `PluralsCandidate` en las plantillas con `el %d solo…`: son falsos positivos, porque ahí el número nombra un dígito y no es una cantidad.

## 3.7 Pantalla de pista
- Tablero a todo el ancho y `BottomSheetScaffold` (no modal, para que el tablero se vea) con el revelado gradual de §5.3:
  1. Técnica y nivel.
  2. Celdas del patrón (`highlight`) y sus candidatos.
  3. Colocación, eliminaciones y explicación, con "Aplicar paso" y "Siguiente".
- **Estado con candidatos**: la pista trabaja sobre un `Grid` (valores y candidatos), no solo sobre los valores, porque un paso de eliminación cambia los candidatos sin colocar nada.
- "Aplicar paso" → `grid.apply(step)` y la siguiente pista empieza en el nivel 1. Tablero resuelto → mensaje final.
- **Sin paso disponible** (hacen falta técnicas de la Fase 6): mensaje "Este tablero necesita técnicas que aún no conozco" y opción "Revelar un dígito" con la solución del `Backtracker`.

**Resultado:** `HintScreen` sigue la maqueta: flecha de volver (a la revisión, con las correcciones), tablero y, debajo, un panel con las tres etapas ("1 · Técnica", "2 · Dónde", "3 · Qué"), el nivel, el nombre, el subtítulo en inglés, el texto de la etapa y los botones. **Es un panel fijo y no un `BottomSheetScaffold`** arrastrable: así el tablero nunca queda tapado y no hay que gestionar alturas. Si el texto no cabe, solo se desplaza el texto; el aviso de paso aplicado y los botones quedan fijos. Comprobada en el A57 en las tres etapas y tras aplicar.

- **Estado (`hint/HintState`, Kotlin puro):** el `Grid` con candidatos, el paso, el nivel, si está aplicado y el último dígito revelado. Transiciones inmutables: `revealMore`, `apply` (el tablero cambia y se sigue en el nivel 3), `next` (aplica el paso si no se había hecho y empieza la siguiente pista en el nivel 1) y `revealDigit`. `CoachViewModel.hint` es un `StateFlow<HintState?>`. Cada "Ver pista" empieza desde el tablero revisado: los pasos aplicados no vuelven a la revisión.
- **Un texto por etapa:** 1 presenta la técnica sin decir dónde ("Hay dos celdas de una misma fila, columna o caja que entre las dos solo admiten dos dígitos. ¿Las encuentras?"); 2 orienta con algunos argumentos de la explicación ("Fíjate en la columna 5 y en los candidatos de F5C5 y F9C5"); 3 es la explicación de 3.6. Las plantillas `intro_*` y `where_*` están en `strings.xml`, junto a `explain_*`.
- **Tablero por etapa:** en la 1 no cambia; en la 2 se rellena el patrón y, si el paso elimina, aparecen sus candidatos (en los pasos que colocan no se muestran, porque darían la respuesta); en la 3, la colocación con contorno y los descartes tachados, que desaparecen al aplicar.
- **Sin paso:** "Tablero resuelto", o "Este tablero necesita técnicas que aún no conozco" con "Revelar un dígito". Se revela la celda vacía con menos candidatos, la más fácil de comprobar a mano, con la solución del `Backtracker`, y se busca la siguiente pista.
- **Tests (JVM, 9 de `HintStateTest`):** etapas, aplicar (solo cambian los candidatos en un paso de eliminación; aplicar dos veces no hace nada), "Siguiente" con y sin aplicar antes, resolver 9 puzzles del banco solo con "Siguiente" hasta la solución del `Backtracker`, revelar un dígito (en el tablero vacío, donde ninguna técnica da paso) eligiendo la celda con menos candidatos, tablero resuelto y lo que dibuja el tablero en cada etapa. `ReviewFlowTest` recorre en el A57 las tres etapas, aplica el paso y pasa a la siguiente pista. `ExplanationTextTest` comprueba que los textos de etapa 1 no tienen marcadores y que los de etapa 2 solo usan argumentos que existen y con el tipo correcto.

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
