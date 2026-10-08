# 06 · Plan detallado: Fase 2 (visión sobre fotos fijas)

Objetivo de la fase (de `04-plan-implementacion.md`): **de una foto a un tablero 9×9 de enteros**, con métricas medidas sobre fotos reales. Se apoya en D-03, D-05, D-07, D-08, D-15, D-16 y D-17.

**Hecho cuando:** la detección del tablero alcanza ≥ 90 % sobre el conjunto de fotos y se conoce la tasa de error por celda (separada en impresos y manuscritos).

## Arquitectura de la fase

```
              JVM pura (WSL + CI)                          Android (móvil)
┌───────────────────────────────────────────┐   ┌──────────────────────────────────┐
│ vision-core                               │   │ vision                           │
│  BoardDetector  → esquinas + homografía   │   │  Bitmap ⇄ Mat (OpenCV 4.14 AAR)  │
│  GridSegmenter  → 81 celdas + vacías      │◄──┤  MlKitDigitRecognizer            │
│  DigitRecognizer (interfaz)               │   │  (ML Kit, modelo incluido)       │
│  API org.opencv.* (compileOnly)           │   └──────────────────────────────────┘
│  tests: Bytedeco OpenCV 4.14 + fotos      │
└───────────────────────────────────────────┘
          ▲ fotos y etiquetas: testdata/vision/ (versionadas, reducidas, sin EXIF)
```

- `vision-core` usa la API Java oficial de OpenCV (`org.opencv.*`) como `compileOnly`. En la JVM la aporta Bytedeco 4.14 (solo nativos `linux-x86_64`); en Android, el AAR `org.opencv:opencv` 4.14. Mismo código, misma versión.
- El tipo de imagen entre módulos es `org.opencv.core.Mat`; no se crea un tipo propio.
- `DigitRecognizer` se declara en `vision-core`; la implementación con ML Kit vive en `vision` porque ML Kit solo existe en Android.

## Formato del conjunto de fotos

```
testdata/vision/
├── raw/                 originales (ignorado por git)
├── photos/NNN.jpg       copia versionada: lado mayor 1600 px, JPEG ~85, sin EXIF
└── labels/NNN.txt       etiqueta
```

Etiqueta `NNN.txt`:

```
# luz=natural angulo=moderado papel=periodico notas=sombra en la esquina
2..3..8..
.7...9.6.
...                      ← 9 líneas: dígito o '.' si está vacía
PP.P..P..
.M...P.P.
...                      ← 9 líneas: P impreso, M manuscrito, '.' vacía
```

La primera línea (metadatos) permite desglosar las métricas por condiciones.

---

## 2.0 Entorno Android en WSL (D-15)
- Instalar las *command-line tools* en `~/Android/Sdk` (sin `sudo`) y, con el Android CLI (`android sdk install`): `platform-tools`, `platforms/android-37.0` y `build-tools/37.0.0`. `ANDROID_HOME` en el perfil del shell; `local.properties` (ya ignorado) con `sdk.dir`.
- Versiones: AGP 9.4.x (compatible con Gradle 9.8.1), `compileSdk`/`targetSdk` = 37, `minSdk` = 26.
- Conectar el Galaxy A57 por **USB con usbipd-win**: en Windows, `usbipd bind --busid <id>` (una vez, como administrador) y `usbipd attach --wsl --busid <id> --auto-attach`; en WSL, regla udev para Samsung y `adb devices`. Bloqueador automático de Samsung desactivado y "Depuración USB" activada.

**Hecho cuando:** `adb devices` lista el A57 y una librería Android vacía compila con `./gradlew`.

**Resultado:** SDK en `~/Android/Sdk` (`platform-tools` 37.0.1, `platforms/android-37.0`, `build-tools/37.0.0`); el A57 aparece como `SM-A576B`, Android 16 (API 36), `arm64-v8a`. Esqueleto de `:vision` con AGP 9.4.1 (`compileSdk` 37, `minSdk` 26; AGP 9 trae el soporte de Kotlin integrado) y repositorio de Google en `settings.gradle.kts`. `DeviceSmokeTest` se ejecuta en el móvil con `./gradlew :vision:connectedDebugAndroidTest`. Sin SDK, `:solver:test` sigue funcionando (AGP solo exige el SDK en tareas de Android), así que la CI no cambia hasta 2.7.

## 2.1 Fotos *(paralelizable desde ya)*
- 30–50 fotos de sudokus en papel con dígitos impresos y manuscritos, variando luz (natural, artificial, sombra), ángulo (frontal, moderado, fuerte), papel y fondo. Incluir algunas difíciles a propósito.
- Traerlas a `testdata/vision/raw/` (con `adb pull` desde `DCIM` una vez emparejado el móvil).
- Script `tools/vision/prepare_photos.py` (Pillow): reescala, quita EXIF (la ubicación GPS no debe llegar al repo público), numera y crea la plantilla de etiqueta con la línea de metadatos.

**Resultado:** 30 fotos de `DCIM/Sudoku` del A57 (4000×3000, con GPS en el EXIF y `orientation` girada). `prepare_photos.py` aplica la orientación antes de quitar el EXIF, reescala a 1200×1600 y numera 001–030 (`sources.csv` guarda la correspondencia); 7 MB versionados. Las plantillas de etiqueta las genera la herramienta de 2.5. **Sesgo del conjunto:** todas son del mismo libro, con luz parecida y ángulos frontales o moderados; los manuscritos son a lápiz (gris claro) y hay recortes de otros sudokus y barras grises en casi todas. Para medir robustez hacen falta fotos más difíciles (otro papel, sombras, ángulos fuertes, curvatura).

## 2.2 Módulo `vision-core`
- Módulo Kotlin/JVM (`kotlin("jvm")`, bytecode JVM 17 como el `solver`), con la API de OpenCV `compileOnly` y Bytedeco 4.14 `linux-x86_64` en los tests.
- Utilidad de test para cargar OpenCV y leer las fotos y etiquetas (`VisionTestData`).
- La CI añade `./gradlew :vision-core:test` (no necesita el SDK de Android).

**Hecho cuando:** un test carga una foto del conjunto con OpenCV en WSL y en la CI.

**Resultado:** `vision-core` compila contra `org.bytedeco:opencv` 4.14.0-1.5.14 como `compileOnly`; los tests añaden los nativos `linux-x86_64` de `opencv`, `openblas` y `javacpp` (~32 MB el de OpenCV) y los cargan con `OpenCvTestSupport`. Primer código de producción: `DigitRecognizer` y `Prediction`. `VisionTestData` lee `testdata/vision/` (ruta pasada por Gradle) y `PhotoLabel` interpreta el formato de etiqueta. `OpenCvSetupTest` comprueba la versión (4.14), procesa y codifica una imagen sintética y lee las fotos del conjunto; este último se **omite** hasta que existan fotos (2.1). La CI ejecuta `:solver:test :vision-core:test`.

## 2.3 Detección del tablero y rectificación (D-07)
- Escala de grises, desenfoque, umbral adaptativo; contorno mayor aproximable a cuadrilátero convexo.
- Validación geométrica: relación de aspecto ≈ 1, área mínima, ángulos razonables.
- Homografía a 450×450 px (celdas de 50 px).
- Respaldo con Hough solo si las métricas de 2.6 lo piden.
- Salida: `BoardScan` con las 4 esquinas, la imagen rectificada y una puntuación de confianza.

**Tests:** fotos sintéticas (tablero dibujado con perspectiva conocida) para comprobar las esquinas con tolerancia; las fotos reales se miden en 2.6.

**Resultado:** `BoardDetector` detecta **30/30** fotos y los 30 tableros rectificados se revisaron a ojo (rejilla alineada en todos). La puntuación de rejilla mide, sobre la imagen rectificada, cuántas de las 20 líneas esperadas son claramente más oscuras que el centro de las celdas vecinas; descarta marcos y recuadros que no son un sudoku (comprobado con una mutación: sin ese filtro fallan los tests del distractor y del cuadrado vacío). La foto 030 (papel curvado) obligó a aproximar el cuadrilátero sobre la envolvente convexa con tolerancias crecientes (2–5 % del perímetro): con la más estricta salían 7 vértices. La curvatura del papel no la corrige la homografía; se nota levemente en 003, 009 y 030 y se absorberá con el margen de las celdas (2.4). Tests sintéticos: frente, giro, perspectiva, distractores (marco mayor, sudoku pequeño, barra gris) e imagen sin tablero. `DetectionReportTest` guarda en `vision-core/build/vision-debug/` la foto con el cuadrilátero y el tablero rectificado para revisarlos.

## 2.4 Segmentación en celdas y detección de vacías
- 81 recortes con margen ≈ 10 % para quitar las líneas.
- Celda vacía por proporción de tinta en la zona central tras binarizar.
- Para cada celda con tinta, recorte centrado del dígito (caja delimitadora + relleno) listo para el reconocedor.

**Resultado:** `GridSegmenter` binariza el tablero rectificado (umbral adaptativo de 15 px, que saca bien el lápiz), **borra las líneas de la rejilla** con aperturas morfológicas de 1,5 celdas y decide cada celda por la **unión de sus componentes conexas** (altura ≥ 35 % de la celda y tinta ≥ 35 px). Lo que se aprendió con las fotos:
- Contar tinta o exigir una sola mancha falla con el lápiz: un 7 tenue se binariza como la barra más trozos sueltos de la diagonal (001, 019). Por eso se unen los fragmentos.
- Con el papel curvado, una línea vertical y otra horizontal entran en el recorte como una L que ningún filtro por forma reconoce (009); borrar las líneas antes lo resuelve. Con un elemento de 0,8 celdas se borraban también los 1 altos; con 1,5 celdas no.
- Un resto de línea es fino en la dirección perpendicular al borde que toca: un 1 alto que toca el borde inferior no lo es (003).
- `inkContrast` (oscuridad de la tinta respecto al papel) es bimodal: lápiz 10–30 %, impreso 50–65 %; umbral 0,4 para proponer el origen.

Contra las etiquetas (2.5): **vacía/no vacía correcta en 2425/2430 celdas (99,8 %)** y **origen correcto en 2195/2197**. Los 5 fallos de vacía son 4 dígitos a lápiz muy tenues que no se detectan y una mancha por transparencia del reverso de la página tomada por dígito (ruido habitual en papel de libro fino); los 2 de origen están en el umbral (contraste 0,39–0,40). Tests sintéticos: impreso y lápiz, contraste, motas y líneas desplazadas, 1 alto tocando el borde y recorte del dígito.

## 2.5 Etiquetado asistido
- Herramienta JVM (`./gradlew :vision-core:labelHelper --args=NNN`): rectifica la foto, guarda una imagen con la rejilla numerada por filas y columnas y rellena la plantilla de etiqueta con las celdas vacías detectadas (`.`) y `?` donde hay tinta.
- Tú completas los dígitos y el origen. Cuando exista el reconocedor (2.8), sus resultados se usan como propuesta.
- **Validación de etiquetas** con el `solver`: un test comprueba que cada etiqueta no repite dígitos en ninguna unidad y que el tablero tiene solución (`Backtracker`), lo que detecta errores de transcripción.

**Resultado:** `./gradlew :vision-core:labelHelper` genera la plantilla (`.` vacía, `?` por transcribir, origen propuesto) y una imagen de revisión con filas y columnas numeradas en `vision-core/build/labeling/`. Las etiquetas con `?` cuentan como pendientes y no se usan en las métricas. **Transcripción:** la hizo Claude leyendo las imágenes de revisión, con revisión posterior del usuario (decisión tomada en la fase). Las **30 etiquetas pasan la validación** con el `solver` (21 tableros completos, donde un dígito mal leído casi siempre crea un repetido). Metadatos: `luz=interior`, `papel=libro`, `angulo` según la desviación de las esquinas (frontal < 4°, moderado; máximo 6,6°) y `notas` con la curvatura y las celdas dudosas. Los datos de prueba se declararon como entrada de la tarea `test` (sin `raw/`), porque Gradle no volvía a ejecutar los tests al cambiar una etiqueta.

**Celdas revisadas a mano** (las únicas en que etiqueta y segmentador discrepan). El usuario confirmó las 30 etiquetas, incluidas estas:

| Foto | Celda | Etiqueta | Motivo |
|---|---|---|---|
| 003 | F1C6 | 9 manuscrito | lápiz muy tenue; ¿escrito o borrado? |
| 003 | F4C1 | 8 impreso | contraste en el umbral (0,40) |
| 015 | F4C2 | 3 manuscrito | lápiz muy tenue; ¿escrito o borrado? |
| 016 | F2C9 | 9 manuscrito | trazo tenue; deducido por la columna |
| 019 | F1C8 | 8 impreso | contraste en el umbral (0,39) |
| 025 | F1C9 | 9 manuscrito | lápiz muy tenue; ¿escrito o borrado? |
| 026 | F3C3 | vacía | mancha por transparencia de la tinta del reverso de la página (confirmado); no puede ser un 1 |
| 027 | F1C2 | 5 manuscrito | lápiz tenue; lo exige la columna |

## 2.6 Métricas en la JVM
Test de métricas sobre todas las fotos, con informe por condiciones (metadatos):
- **Detección**: % de fotos con tablero detectado y validado.
- **Vacías**: aciertos vacía/no vacía por celda.

El test exige los umbrales cuando estén alcanzados (detección ≥ 90 %), como se hizo con el banco en la Fase 1. Si la detección no llega, se evalúa el respaldo con Hough (D-07).

## 2.7 Módulo `vision` (Android)
- Librería Android (AGP 9.4, `minSdk` 26) que depende de `vision-core` y del AAR de OpenCV 4.14.
- Conversión `Bitmap` ⇄ `Mat`.
- `MlKitDigitRecognizer` con ML Kit Text Recognition v2 **con el modelo incluido en la app** (`com.google.mlkit:text-recognition`), no la variante que se descarga con Play Services: así se cumple D-03 (sin conexión desde el primer uso).
- Se prueban dos estrategias y se mide cuál funciona mejor: reconocer cada celda recortada (ampliada y con margen) frente a reconocer la cuadrícula rectificada entera y asignar cada texto a su celda por posición.
- La CI compila `:vision` con el SDK de Android (`android-actions/setup-android`).

## 2.8 Métricas en el móvil
- Test instrumentado que recorre las fotos (copiadas como assets de `androidTest`), ejecuta el pipeline completo y escribe los resultados en JSON.
- `adb pull` del JSON y un test/informe en la JVM que calcula: **acierto por celda**, separado en **impresos** y **manuscritos**, confusiones más frecuentes (1/7, 4/9…) y tiempo por foto.
- Los resultados del reconocedor alimentan el etiquetado (2.5) de las fotos que falten.

## 2.9 Cierre de fase
- Comprobar el criterio: detección ≥ 90 % y tasa de error por celda conocida (impresos y manuscritos por separado).
- Revisar D-07 (¿hace falta Hough?) y D-08 (¿basta ML Kit hasta la Fase 5?) con los datos.
- Actualizar `03-diseno.md` §4 y las casillas de `04-plan-implementacion.md`.

---

## Orden de trabajo

```
2.0 entorno ─► 2.2 vision-core ─► 2.3 detección ─► 2.4 segmentación ─► 2.5 etiquetado ─► 2.6 métricas JVM
2.1 fotos (en paralelo desde el principio) ────────────────────────────────┘
2.0 ─► 2.7 vision (Android) ─► 2.8 métricas en el móvil ─► 2.9 cierre
```

## Riesgos

| Riesgo | Mitigación |
|---|---|
| WSL2 no ve el USB del móvil | usbipd-win con `--auto-attach`; alternativa: depuración inalámbrica (D-15) |
| Diferencias entre OpenCV de Bytedeco y el AAR de Android | Misma versión (4.14) y misma API `org.opencv.*`; las métricas del móvil (2.8) lo confirmarían |
| ML Kit reconoce mal dígitos aislados y pequeños | Ampliar recortes y añadir margen; alternativa de reconocer la cuadrícula entera (2.7); CNN propia en la Fase 5 |
| Pocas fotos o poco variadas | Etiquetar por condiciones y añadir fotos difíciles a propósito |
| Datos personales en las fotos (EXIF con GPS) | `prepare_photos.py` elimina EXIF antes de versionar |
| Tamaño del repositorio | Fotos reducidas (~200 KB); nativos de OpenCV solo para `linux-x86_64` |
