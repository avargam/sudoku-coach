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

## 2.2 Módulo `vision-core`
- Módulo Kotlin/JVM (`kotlin("jvm")`, bytecode JVM 17 como el `solver`), con la API de OpenCV `compileOnly` y Bytedeco 4.14 `linux-x86_64` en los tests.
- Utilidad de test para cargar OpenCV y leer las fotos y etiquetas (`VisionTestData`).
- La CI añade `./gradlew :vision-core:test` (no necesita el SDK de Android).

**Hecho cuando:** un test carga una foto del conjunto con OpenCV en WSL y en la CI.

## 2.3 Detección del tablero y rectificación (D-07)
- Escala de grises, desenfoque, umbral adaptativo; contorno mayor aproximable a cuadrilátero convexo.
- Validación geométrica: relación de aspecto ≈ 1, área mínima, ángulos razonables.
- Homografía a 450×450 px (celdas de 50 px).
- Respaldo con Hough solo si las métricas de 2.6 lo piden.
- Salida: `BoardScan` con las 4 esquinas, la imagen rectificada y una puntuación de confianza.

**Tests:** fotos sintéticas (tablero dibujado con perspectiva conocida) para comprobar las esquinas con tolerancia; las fotos reales se miden en 2.6.

## 2.4 Segmentación en celdas y detección de vacías
- 81 recortes con margen ≈ 10 % para quitar las líneas.
- Celda vacía por proporción de tinta en la zona central tras binarizar.
- Para cada celda con tinta, recorte centrado del dígito (caja delimitadora + relleno) listo para el reconocedor.

## 2.5 Etiquetado asistido
- Herramienta JVM (`./gradlew :vision-core:labelHelper --args=NNN`): rectifica la foto, guarda una imagen con la rejilla numerada por filas y columnas y rellena la plantilla de etiqueta con las celdas vacías detectadas (`.`) y `?` donde hay tinta.
- Tú completas los dígitos y el origen. Cuando exista el reconocedor (2.8), sus resultados se usan como propuesta.
- **Validación de etiquetas** con el `solver`: un test comprueba que cada etiqueta no repite dígitos en ninguna unidad y que el tablero tiene solución (`Backtracker`), lo que detecta errores de transcripción.

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
