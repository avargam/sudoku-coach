# 03 · Outline del diseño

## 1. Visión general

```
Cámara (CameraX)
   │
   ▼
Detección del tablero ──► Corrección de perspectiva (homografía) ──► 81 celdas
   │                                                                   │
   │                                                                   ▼
   │                                                        Reconocimiento de dígitos
   │                                                                   │
   │                                                                   ▼
   │                                                  Validación + revisión/corrección
   │                                                                   │
   │                                                                   ▼
   │                                                  Motor de técnicas (solver)
   │                                                                   │
   ▼                                                                   ▼
Overlay sobre imagen en vivo  ◄──────────────  Paso: técnica + celdas + explicación
(homografía inversa)
```

## 2. Módulos

| Módulo | Tipo | Responsabilidad | Depende de |
|---|---|---|---|
| `solver` | Kotlin/JVM puro | Modelo del tablero, candidatos, técnicas, motor de pistas | — |
| `vision-core` | Kotlin/JVM puro | Detección del tablero, rectificación, segmentación de celdas, interfaces `BoardRecognizer` y `DigitRecognizer` | OpenCV 4.14 (API `org.opencv.*`, `compileOnly`) |
| `vision` | Android library | `Bitmap` ⇄ `Mat`, reconocedores (ML Kit con la lámina; LiteRT en la Fase 5) | `vision-core`, OpenCV 4.14 AAR, ML Kit |
| `app` | Android app | Cámara, pantallas, overlay, orquestación | `vision`, `solver` |

Reglas: `solver` nunca depende de Android ni de `vision`; `vision-core` nunca depende de Android (D-16).

## 3. Módulo `solver`

### 3.1 Modelo

- `Grid`: 81 valores (`0` = vacía) y 81 máscaras de candidatos (bit *d* = candidato *d*).
- `Units`: 27 unidades (9 filas, 9 columnas, 9 cajas) y pares de cada celda.
- `Step`: resultado de una técnica.

```kotlin
enum class Category { SINGLES, INTERSECTIONS, SUBSETS, FISH, WINGS, UNIQUENESS, CHAINS }

enum class TechniqueId(val category: Category) {
    FULL_HOUSE(SINGLES), NAKED_SINGLE(SINGLES), HIDDEN_SINGLE(SINGLES),
    LOCKED_PAIR(INTERSECTIONS), LOCKED_TRIPLE(INTERSECTIONS),
    LOCKED_CANDIDATES_POINTING(INTERSECTIONS), LOCKED_CANDIDATES_CLAIMING(INTERSECTIONS),
    NAKED_PAIR(SUBSETS), NAKED_TRIPLE(SUBSETS), HIDDEN_PAIR(SUBSETS),
    HIDDEN_TRIPLE(SUBSETS), NAKED_QUAD(SUBSETS), HIDDEN_QUAD(SUBSETS),
    // ... se amplía por familias
}

data class Step(
    val technique: TechniqueId,
    val highlight: List<Int>,            // celdas que forman el patrón
    val units: List<Int>,                // unidades implicadas (0..26), p. ej. la columna del par
    val digits: List<Int>,               // dígitos del patrón, p. ej. {1, 3} en un Naked Pair
    val placement: Placement?,           // dígito a colocar (si aplica)
    val eliminations: List<Elimination>, // candidatos a eliminar (si aplica)
)
```

La UI solo dibuja un `Step`; no conoce ninguna técnica concreta, salvo para elegir la plantilla de texto de cada `TechniqueId` (D-14). El `solver` no genera frases; solo ofrece `Step.describe()`, una descripción técnica para depurar y para los mensajes de los tests.

### 3.2 Técnicas y orden

Cada técnica implementa `Technique.find(grid): Step?`. El `HintEngine` recibe una lista ordenada de técnicas y devuelve el primer paso encontrado. El orden por defecto es el de HoDoKu (D-13), que en niveles avanzados intercala familias; el **nivel** de la tabla es la categoría pedagógica que ve el usuario ("NIVEL 3 · SUBCONJUNTOS"), no el orden del motor.

| Nivel | Categoría | Técnicas | Fase | Estado |
|---|---|---|---|---|
| 1 | Singles | Full House, Naked Single, Hidden Single | 1 | ✅ |
| 2 | Intersecciones | Locked Pair/Triple, Locked Candidates: Pointing (tipo 1) y Claiming (tipo 2) | 1 | ✅ |
| 3 | Subconjuntos | Naked/Hidden Pair, Triple, Quad | 1 | ✅ |

Orden del motor en la Fase 1 (el de HoDoKu): Full House → Naked Single → Hidden Single → Locked Pair → Locked Triple → Locked Candidates 1 (Pointing) → Locked Candidates 2 (Claiming) → Naked Pair → Naked Triple → Hidden Pair → Hidden Triple → Naked Quad → Hidden Quad.
| 4 | Fish | X-Wing, Swordfish, Jellyfish | 6 | ⬜ |
| 5 | Wings | XY-Wing, XYZ-Wing, W-Wing | 6 | ⬜ |
| 6 | Unicidad | Unique Rectangle (requiere unicidad) | 6 | ⬜ |
| 7 | Cadenas | Coloración simple, cadenas | 6 | ⬜ |

### 3.3 Verificación

- Tests unitarios por técnica con tableros construidos a propósito.
- Test de consistencia: todo paso devuelto es compatible con la solución única (comprobado contra un backtracking de referencia).
- Contraste contra `hodoku-py` (ver `tools/oracle/` y D-09): banco de puzzles generado y pasos esperados exportados a JSON y versionados; los tests de Kotlin los leen sin necesitar Python.
- reglib de HoDoKu: test opcional en local; se omite si los datos no están descargados.

## 4. Módulos `vision-core` y `vision`

Todo lo de §4.1 y §4.2 vive en `vision-core` y se prueba en la JVM con las fotos de `testdata/vision/` (D-16, D-17). El plan detallado está en `06-plan-fase-2.md`.

### 4.1 Detección y rectificación (`BoardDetector`, D-07)

1. Escala de grises, desenfoque suave, umbral adaptativo.
2. Contornos; del mayor al menor, aproximar la envolvente convexa a un cuadrilátero con tolerancias crecientes (2–5 % del perímetro; el papel curvado necesita las mayores).
3. Validar: cuadrilátero convexo, relación de aspecto ≈ 1, área mínima y **puntuación de rejilla** (cuántas de las 20 líneas interiores esperadas son más oscuras que las celdas vecinas), que descarta marcos y recuadros que no son un sudoku.
4. Homografía a 450×450 px (celdas de 50 px). Salida: `BoardScan` con esquinas, imagen rectificada y confianza.
5. Respaldo con Hough: no hizo falta en la Fase 2 (30/30); se reconsidera si las fotos de la Fase 4 lo piden.

### 4.2 Segmentación de celdas (`GridSegmenter`)

- Binarizar el tablero rectificado con umbral adaptativo y **borrar las líneas de la rejilla** con aperturas morfológicas de 1,5 celdas (las líneas del papel curvado entran en las celdas aunque se recorte un margen).
- Celda con dígito si la unión de sus componentes conexas alcanza el 35 % de la altura de la celda y un mínimo de tinta; se unen los fragmentos porque el lápiz tenue se binariza a trozos.
- Para cada dígito: caja delimitadora, recorte y **origen propuesto** (impreso / manuscrito) según el contraste de la tinta con el papel (umbral 0,4).

### 4.3 Reconocimiento

```kotlin
fun interface BoardRecognizer {                     // punto de sustitución (D-08)
    fun recognize(rectified: Mat, cells: List<Cell>): List<Prediction>   // 81 predicciones
}
interface DigitRecognizer {                         // por celda; se adapta con BoardRecognizer.cellByCell
    fun recognize(cell: Mat): Prediction            // dígito 0..9 + confianza
}
```

Las interfaces están en `vision-core` y usan `org.opencv.core.Mat`; `BoardReader` encadena detección, segmentación y un `BoardRecognizer`. Implementaciones en `vision`:

- `MlKitGridReader` (MVP): redibuja los dígitos en una **lámina** (líneas de hasta 9, glifos de 48 px con el contraste estirado y separación igual a su altura, sin rejilla) y la lee con una sola llamada a ML Kit, con el modelo incluido en la app. Cada lectura va a la celda de su glifo.
- `MlKitDigitRecognizer`: celda a celda; solo como referencia en las métricas (77 % / 29 %, frente a 100 % / 94,6 % de la lámina).
- `TfLiteDigitRecognizer` (CNN en LiteRT, Fase 5), por celda.

### 4.4 Robustez

- **Votación multi-frame**: una celda se acepta tras N lecturas iguales consecutivas.
- **Validación de reglas**: duplicados en fila/columna/caja marcan celdas sospechosas.
- **Confianza por celda**: por debajo de 0,5 se pide confirmación (con ML Kit, ninguna lectura por encima fue errónea en la Fase 2); las celdas con tinta sin lectura también.
- **Unicidad**: backtracking rápido para verificar solución única antes de usar técnicas que la asumen.

## 5. Módulo `app`

### 5.1 Pantallas

1. **Escáner**: cámara a pantalla completa; el contorno del tablero detectado se dibuja en el acento y la captura es automática cuando la lectura se estabiliza (votación multi-frame). Botones secundarios: cerrar, flash, elegir de galería; disparador manual como respaldo. *(Fase 4. En la Fase 3 la entrada es **Elegir foto**: el selector de fotos del sistema, sin permisos, y la foto con un indicador de progreso mientras se lee.)*
2. **Revisión**: tablero reconocido y editable. Tocar una celda la selecciona; debajo hay un teclado numérico fijo (1–9 + borrar). Las celdas sospechosas (tinta sin lectura, confianza < 0,5 o dígito repetido) llevan borde punteado de acento y un aviso "N celdas por revisar" salta entre ellas con ◀ ▶. Una línea bajo el tablero dice por qué se marca la celda seleccionada o cuál es su origen. Los impresos también se pueden editar, porque pueden estar mal leídos. El motivo de la validación (D-19) y "Ver pista" quedan fijos abajo; la pista solo se activa si la validación lo permite.
3. **Pista**: tablero a todo el ancho y debajo un panel fijo (no un *bottom sheet* arrastrable, para que el tablero nunca quede tapado) con las tres etapas, la técnica, el texto de la etapa y los botones. El revelado es gradual (ver §5.3). En la etapa 3, "Aplicar paso" (el tablero cambia y se sigue en la misma pista) y "Siguiente" (aplica si hace falta y pasa a la siguiente pista en la etapa 1). Sin técnica disponible: "Revelar un dígito" de la solución, en la celda vacía con menos candidatos.
4. *(Posterior)* **Overlay en vivo** dibujado sobre la cámara mediante la homografía inversa.

### 5.2 Estado

Flujo unidireccional (`ViewModel` + `StateFlow`): `Scanning → Reviewing(board) → ShowingHint(board, step, revealLevel)`.

`board` incluye, además de los valores, el origen de cada dígito (`GIVEN` impreso / `USER` manuscrito / `UNKNOWN`) y las celdas sospechosas. `revealLevel` va de 1 a 3.

Implementado en la Fase 3 (D-18): la pila de pantallas de Navigation 3 (`Picking → Reviewing → Hint`) más un `StateFlow` por pantalla: `ScanState` (leyendo o fallo), `ReviewState` (`ReviewBoard` con valores, origen, confianza y tinta; celda seleccionada; validación) y `HintState` (`Grid` con candidatos antes del paso, el paso, la etapa, si está aplicado y el dígito revelado). Todo salvo la lectura en curso se guarda como texto en un `SavedStateHandle` y sobrevive al cierre del proceso.

### 5.3 Diseño visual (D-12)

**Estilo**: minimalista monocromo. El tablero es lo principal; un único acento azul marca todo lo que tiene que ver con la pista. Paleta propia (sin Dynamic Color) y modo claro y oscuro desde el inicio.

**Tokens de color**

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `background` | `#FFFFFF` | `#0F0F10` | Fondo de pantalla |
| `surface` | `#F4F4F5` | `#1A1A1C` | Bottom sheet, teclado |
| `ink` | `#111111` | `#EDEDED` | Dígitos, texto, líneas gruesas de caja |
| `inkMuted` | `#69696D` | `#9A9A9F` | Texto secundario, candidatos eliminados |
| `gridLine` | `#D4D4D8` | `#34343A` | Líneas finas entre celdas |
| `accent` | `#1F5FD1` | `#6EA0FF` | Colocación, contornos, celdas sospechosas, botón principal |
| `accentSoft` | `#E3ECFB` | `#1C2B47` | Relleno de celdas del patrón y de la celda seleccionada |
| `onAccent` | `#FFFFFF` | `#0F0F10` | Texto sobre el acento |

**Tipografía**: una sola familia sans con cifras tabulares (IBM Plex Sans, licencia OFL), empaquetada en la app como fuente variable (D-03, D-18). Pistas impresas en **negrita** (`ink`), dígitos del usuario en peso regular (`ink`, algo más ligeros). Si el origen es `UNKNOWN`, todos los dígitos se muestran en peso regular.

**Lenguaje del resaltado** (un acento con variaciones)

| Elemento del `Step` | Representación |
|---|---|
| `highlight` (celdas del patrón) | Relleno `accentSoft` |
| `placement` | Dígito en `accent`, celda con contorno `accent` |
| `eliminations` | Candidato en `inkMuted` tachado |
| Candidatos relevantes | Mini-rejilla 3×3 solo en las celdas implicadas; el resto del tablero queda limpio |

**Maqueta**: lienzo con las tres pantallas en claro y oscuro: <https://claude.ai/artifact/RBrWurNUeeJH8y5QJqf7aZ>.

**Revelado gradual** (los niveles salen directamente de los campos de `Step`):

1. **Técnica**: solo el nombre (`technique`) y su nivel, con el nombre en inglés de subtítulo y una frase que invita a buscarla; el tablero no cambia.
2. **Dónde**: se rellenan las celdas de `highlight` y aparecen sus candidatos (salvo en los pasos que colocan, donde darían la respuesta), con una frase que dice dónde mirar.
3. **Qué**: se muestran `placement` y `eliminations`, y se despliega la explicación que la app compone a partir del `Step` (D-14): `StepExplainer` da argumentos estructurados y las plantillas (`intro_*`, `where_*`, `explain_*`) están en `strings.xml`.

El *bottom sheet* tiene un botón "Mostrar más" que sube de nivel; en el nivel 3 aparecen "Aplicar paso" y "Siguiente".

**Riesgo abierto**: en cadenas y coloración (nivel 7) puede hacer falta un segundo tono de acento o distinguir enlaces fuertes y débiles con líneas continuas o discontinuas.

## 6. Datos para el reconocimiento

- **Sintético**: dígitos 1–9 renderizados con muchas fuentes, con ruido, desenfoque, rotaciones y cambios de contraste.
- **MNIST** para variedad manuscrita.
- **Propio**: fotos reales de los sudokus del usuario, celdas etiquetadas.
- Clase extra "vacía".

## 7. Pruebas

| Nivel | Qué | Cómo |
|---|---|---|
| Unidad | Técnicas del solver | JUnit/Kotlin test, sin Android |
| Propiedad | Pasos compatibles con la solución | Backtracking de referencia |
| Oráculo | Mismas técnicas que HoDoKu | `hodoku-py` en `tools/oracle` |
| Visión (JVM) | Detección y segmentación sobre fotos etiquetadas | `testdata/vision` + métricas en `vision-core` |
| Visión (móvil) | Reconocimiento con ML Kit | Test instrumentado sobre las mismas fotos |
| Instrumentadas | Flujo completo en dispositivo | Android instrumented tests |

## 8. Referencias

- HoDoKu: <https://hodoku.sourceforge.net/en/docs_intro.php>
- hodoku-py (port de HoDoKu con API de pistas): <https://pypi.org/project/hodoku-py/>
- KyouyamaKazusa/Sudoku (SDK de técnicas en C#): <https://github.com/kyoyama-kazusa/Sudoku>
- Ejemplo OpenCV + ML Kit para Android: <https://github.com/hypertensiune/android-sudoku-solver-ocr>
- LiteRT samples (clasificación de dígitos MNIST): <https://github.com/google-ai-edge/litert-samples>
