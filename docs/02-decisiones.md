# 02 · Decisiones

Registro de decisiones del proyecto. Estados: **Aceptada**, **Provisional** (se revisa tras medir), **Pendiente**.

## Resumen

| ID | Decisión | Estado |
|---|---|---|
| D-01 | Plataforma: Android | Aceptada |
| D-02 | Stack: Kotlin nativo | Aceptada |
| D-03 | Procesamiento 100 % on-device | Aceptada |
| D-04 | Técnicas hasta nivel avanzado | Aceptada |
| D-05 | Dígitos impresos + manuscritos | Aceptada |
| D-06 | Candidatos no se leen del papel; se calculan | Aceptada |
| D-07 | Detección de tablero: contorno + homografía (OpenCV); Hough solo si lo piden las métricas | Aceptada |
| D-08 | Reconocimiento: ML Kit (lámina) para el MVP, CNN propia en LiteRT como objetivo | Aceptada |
| D-09 | Motor de técnicas propio en Kotlin; HoDoKu como oráculo de pruebas | Aceptada |
| D-10 | Licencia: Apache-2.0; repositorio privado hasta la Fase 7 | Aceptada |
| D-11 | Estructura multi-módulo con `solver` en Kotlin/JVM puro | Aceptada |
| D-12 | Diseño visual: minimalista monocromo con acento azul | Aceptada |
| D-13 | Orden de técnicas alineado con HoDoKu, configurable | Aceptada |
| D-14 | `Step` estructurado; los textos se componen en la app | Aceptada |
| D-15 | Entorno: todo en WSL (JDK 21, Gradle wrapper, Android SDK por línea de comandos); app en el móvil por USB con usbipd-win | Aceptada |
| D-16 | `vision` separado en `vision-core` (JVM) y `vision` (Android); OpenCV 4.14; `minSdk` 26 | Aceptada |
| D-17 | Fotos de prueba reducidas y versionadas, con dígitos y origen por celda | Aceptada |
| D-18 | App: una `Activity`, Compose, `ViewModel` con máquina de estados y Navigation 3 | Aceptada |
| D-19 | Validación antes de las pistas: repetidos, errores del usuario y unicidad | Aceptada |
| D-20 | Cámara con CameraX: `Preview` + `ImageAnalysis`, plano Y como gris; galería si no hay permiso | Aceptada |
| D-21 | Lectura en vivo: detección por fotograma, lectura con el tablero quieto y votación por celda | Aceptada |
| D-22 | Secuencias de fotogramas grabadas en debug como datos de prueba, sin versionar | Aceptada |

---

## D-01 · Plataforma: Android
Solo Android en v1. Evita el coste de mantener dos bases de código y permite concentrarse en el pipeline.

## D-02 · Stack: Kotlin nativo
Acceso directo a CameraX, OpenCV para Android, ML Kit y LiteRT. Se descartó Flutter/React Native por ser el pipeline de visión el centro del proyecto.

## D-03 · Procesamiento 100 % en el dispositivo
Funciona sin conexión, es más privado y sin costes de servidor. Contrapartida: más trabajo de integración y modelos pequeños.

## D-04 · Técnicas hasta nivel avanzado
El catálogo objetivo cubre singles, subconjuntos, intersecciones, fish, wings, unicidad y cadenas. Se implementan por familias, de la más simple a la más compleja.

## D-05 · Dígitos impresos y manuscritos
El reconocedor debe manejar ambos. Esto desfavorece a un OCR genérico y favorece una CNN entrenada con dataset mixto.

## D-06 · Candidatos calculados, no leídos
El usuario no anota candidatos; la app los deriva de los dígitos. Simplifica mucho el reconocimiento. Consecuencia: las pistas suponen un solucionador que mantiene candidatos completos.

## D-07 · Detección del tablero
Contorno mayor con 4 esquinas + homografía con OpenCV. Se añade validación geométrica (cuadrado, área mínima, rejilla 9×9). Si falla en fotos reales, se incorpora Hough lines como respaldo. Limitaciones conocidas del enfoque: falla si hay una forma mayor que el tablero y si el tablero está rotado más de ~45°.

**Aceptada al cerrar la Fase 2:** detecta 30/30 fotos; la puntuación de rejilla descarta marcos mayores y recuadros que no son sudokus, y aproximar sobre la envolvente convexa resuelve el papel curvado. **Hough no se implementa.** Salvedad: las 30 fotos son de un solo libro, con luz interior y ángulos ≤ 6,6°. Las fotos difíciles (otro papel, sombra, ángulo > 15°, curvatura fuerte) se miden en la Fase 4, donde la cámara en vivo las produce de forma natural; si la detección baja del 90 %, se reabre.

## D-08 · Reconocimiento de dígitos
- **MVP**: ML Kit Text Recognition v2 sobre el tablero rectificado (dígitos de al menos 16×16 px).
- **Objetivo**: CNN propia con LiteRT (antes TensorFlow Lite), 10 clases (vacía + 1–9), entrenada con dataset sintético de fuentes + MNIST + fotos propias.
- Ambos detrás de una interfaz `DigitRecognizer`, de modo que el reemplazo no toque el resto.
- Criterio de revisión: comparar ambos sobre un conjunto de fotos propias etiquetadas.
- **Concretado al planificar la Fase 2:** se empieza con ML Kit, usando el **modelo incluido en la app** (`com.google.mlkit:text-recognition`) y no la variante que se descarga con Play Services, para cumplir D-03 desde el primer uso. Como ML Kit solo funciona en Android, su acierto se mide en el móvil con un test instrumentado; detección y segmentación se miden en la JVM (D-16).

**Aceptada al cerrar la Fase 2.** ML Kit basta para el MVP con la estrategia de la **lámina** (`MlKitGridReader`): los dígitos se redibujan en líneas, con el contraste estirado y sin rejilla, y se leen en una sola llamada. En el A57, sobre 30 fotos: impresos 746/746 (100 %), manuscritos 1376/1455 (94,6 %), 0,3 s por foto. Celda a celda solo llega al 77 % / 29 % y tarda 8,5 s, así que se descarta.
- **Umbral de confianza 0,5**: ninguna lectura con confianza ≥ 0,5 es errónea (1706/1706); por debajo, el dígito se marca "por confirmar" en la revisión (21 % de los leídos). Los no leídos (29) también se piden al usuario. **Cambiado en la tarea 4.5** a dos umbrales: **0,6 para los 4 manuscritos y 0,35 para el resto**. En las fotos, el 22 % de los 4 manuscritos son 1 mal leídos (confianza de hasta 0,46) y los demás errores no pasan de 0,29; votados en vivo, hasta 0,44 y 0,23. Las sospechosas por foto bajan de 19,8 a 13,2 sin ningún error más sin marcar.
- **Punto de sustitución**: la interfaz por celda no sirve para la lámina, así que el reconocedor se inyecta a nivel de tablero (`BoardRecognizer`: tablero rectificado + celdas → 81 predicciones). Un `DigitRecognizer` por celda, como la CNN, se adapta con `BoardRecognizer.cellByCell`.
- **Motivo para la CNN (Fase 5)**: el 90 % de los errores son 1 manuscritos leídos como 4, propios de la letra del usuario; ML Kit no se puede ajustar a ella.
- El **origen** (impreso / manuscrito) ya lo propone el segmentador por el contraste de la tinta (99,9 % correcto), sin reconocedor.

## D-09 · Motor de técnicas
Implementación propia en Kotlin, sin portar el núcleo de HoDoKu, usando `hodoku-py` (port en Python de HoDoKu 2.2.0, GPL-3.0) **solo como oráculo** de pruebas. Razones: aprendizaje, sin acoplamiento a su GUI, salida estructurada pensada para la UI y libertad de licencia. Alternativa descartada por ahora: embeber el núcleo de HoDoKu (GPL-3.0).

Uso concreto de `hodoku-py`:
- **Generador** del banco de puzzles de prueba, clasificados por dificultad. Los puzzles generados son datos, no código GPL, y se versionan.
- **Pasos esperados**: un script local exporta a JSON el camino de resolución de HoDoKu y se versiona. La CI solo ejecuta `./gradlew :solver:test`, sin Python.
- **reglib** (la suite de regresión de HoDoKu, un tablero por técnica): se descarga y se usa solo en local. No se copia al repositorio porque es material GPL.

**Aceptada al cerrar la Fase 1:** el motor propio coincide con HoDoKu en los 16 753 pasos del banco (técnica, efecto y celdas del patrón), sin incorporar código de HoDoKu. El esquema oráculo + banco versionado se mantiene para la Fase 6.

## D-10 · Licencia: Apache-2.0
Licencia permisiva con cláusula de patentes, habitual en el ecosistema Android/Kotlin. Consecuencia: **no se puede copiar ni portar código de HoDoKu ni de `hodoku-py`** al repositorio; solo se usan como herramienta externa. El repositorio vive en GitHub en modo **privado** y se hace público en la Fase 7.

## D-11 · Estructura multi-módulo
`solver` es Kotlin/JVM puro (sin dependencias de Android) para poder probarlo con JUnit rápido y sin emulador. `vision` y `app` dependen de él, no al revés.

## D-12 · Diseño visual
Estilo **minimalista monocromo**: el tablero es lo principal y un único color de acento (**azul**) señala todo lo relacionado con la pista. Detalle en `03-diseno.md` §5.3.

- **Paleta propia fija**, sin Dynamic Color (Material You), para garantizar el contraste de los resaltados.
- **Modo claro y oscuro desde la Fase 3** (tokens definidos para ambos desde el inicio).
- **Pantalla de pista**: tablero a todo el ancho y explicación en un *bottom sheet*.
- **Resaltado con un solo acento y variaciones**: relleno suave para las celdas del patrón, acento sólido para el dígito a colocar, candidatos eliminados tachados en gris.
- **Pistas impresas en negrita, dígitos del usuario en peso regular** (requiere que `vision` clasifique el origen; mientras no exista, todos se muestran igual).
- **Revelado gradual por niveles**: técnica → celdas implicadas → resultado y explicación.
- **Candidatos**: solo se muestran los de las celdas implicadas en el paso.
- **Escáner** con cámara a pantalla completa, contorno del tablero en el acento y captura automática al estabilizarse.
- **Revisión** con teclado numérico fijo (1–9 + borrar) y celdas sospechosas con borde punteado de acento y un contador navegable.

Razones: legibilidad, accesibilidad (el azul es distinguible para la mayoría de los tipos de daltonismo) y un coste de diseño acotado. Riesgo conocido: en cadenas y coloración (nivel 7) un solo acento puede no bastar para separar el patrón de las eliminaciones; se valorará un segundo tono de azul o distintos estilos de línea.

## D-13 · Orden de técnicas alineado con HoDoKu
El `HintEngine` prueba las técnicas en el **mismo orden que la configuración por defecto de HoDoKu** (`SolverConfig` de `hodoku-py`). Así el oráculo puede comparar paso a paso qué técnica se elige. El orden es una lista inyectable en el `HintEngine` (configurable).

Consecuencias:
- Se añaden **Full House**, **Locked Pair** y **Locked Triple**, que existen en HoDoKu y no estaban en el catálogo.
- El **orden del motor** y la **categoría pedagógica** que se muestra al usuario (`03-diseno.md` §3.2) son cosas distintas. En las técnicas de la Fase 1 coinciden, pero más adelante HoDoKu intercala familias (por ejemplo, Remote Pair y BUG+1 antes que Skyscraper o las wings). Las categorías siguen las de HoDoKu (`SolutionCategory`); por ejemplo, Locked Pair y Locked Triple son intersecciones, no subconjuntos.
- El orden de referencia está anotado en `tools/oracle/README.md`, extraído de `SolverConfig` de `hodoku-py` 0.2.1.
- Cuando varias instancias de una misma técnica son posibles, no se exige elegir la misma que HoDoKu; basta con que el paso propio esté entre los que HoDoKu encuentra para esa técnica en ese estado.

## D-14 · `Step` estructurado; los textos se componen en la app
El `solver` no genera frases. Un `Step` lleva el identificador de la técnica (con su categoría), las celdas, unidades y dígitos del patrón, la colocación y las eliminaciones. La app compone la explicación con recursos de Android (traducibles, con formato). El `solver` solo ofrece una descripción técnica para depurar y para los mensajes de los tests. Consecuencia: la tarea "textos de explicación en español" pasa de la Fase 1 a la Fase 3.

## D-15 · Entorno de desarrollo
- **WSL2 (Ubuntu)**: `openjdk-21-jdk` instalado con apt y **Gradle wrapper** versionado (`./gradlew`); Gradle global solo se usa una vez para generar el wrapper. El `solver` compila a bytecode JVM 17 para que Android lo pueda usar.
- **Android en WSL, por línea de comandos** (revisado al cerrar la Fase 1; antes era Android Studio en Windows). El repositorio sigue en `~/sudoku-coach` y no hay clon en Windows. El Android SDK (command-line tools) se instala en `~/Android/Sdk`, sin `sudo`, y la app se compila con `./gradlew`. Así la compilación y los tests de `vision` y `app` se ejecutan en el mismo entorno que los del `solver`.
- **Ejecución en el dispositivo físico** (Samsung Galaxy A57, Android 16) por **depuración USB**. Como WSL2 no ve los USB de Windows, el móvil se pasa a WSL con **usbipd-win** (`usbipd bind` una vez como administrador; `usbipd attach --wsl --auto-attach`), y el `adb` de Linux lo ve como un USB normal; una regla udev para Samsung (`idVendor` `04e8`, grupo `plugdev`) da permisos al usuario. Se descartó usar el `adb` de Windows como servidor (dos adb que mantener y red entre WSL y Windows). En Samsung hay que desactivar el **Bloqueador automático** para poder depurar. La depuración inalámbrica queda como alternativa.
- **SDK:** las *command-line tools* 23 sustituyen `sdkmanager` por el **Android CLI** (`android sdk install ...`). Paquetes: `platform-tools` 37.0.1, `platforms/android-37.0` (`compileSdk` 37) y `build-tools/37.0.0`.
- **Opcionales:** el emulador (WSL tiene `/dev/kvm` y WSLg; requiere añadir el usuario al grupo `kvm` y probablemente subir la memoria de WSL, hoy ~7,6 GB) y Android Studio dentro de WSL vía WSLg, solo si hacen falta las vistas previas de Compose o el inspector de interfaz.
- **Consecuencia para la Fase 2:** los algoritmos de visión se prueban en la JVM, sin dispositivo (D-16).
- **CI**: GitHub Actions con `./gradlew :solver:test`.

## D-16 · `vision-core` (JVM) + `vision` (Android), OpenCV 4.14, `minSdk` 26
- **`vision-core`**: módulo Kotlin/JVM puro con detección, rectificación, segmentación y la interfaz `DigitRecognizer`. Usa la API Java oficial de OpenCV (`org.opencv.*`) como `compileOnly`; en los tests la aporta **Bytedeco 4.14** (solo nativos `linux-x86_64`). Se prueba en WSL y en la CI con las fotos reales, igual que el `solver`.
- **`vision`**: librería Android que depende de `vision-core`, del AAR oficial **`org.opencv:opencv` 4.14** y de ML Kit. Solo adapta `Bitmap` ⇄ `Mat` e implementa el reconocedor de ML Kit.
- Misma versión de OpenCV en los dos lados para que el código de `vision-core` se comporte igual en la JVM y en el móvil. Se descartó OpenCV 5.0 (sin binding de JVM equivalente) y openpnp (estancado en 4.9).
- El tipo de imagen entre módulos es `org.opencv.core.Mat`; no se crea uno propio.
- **`minSdk` 26** (Android 8.0) para la app y las librerías Android: cubre prácticamente todos los móviles en uso y CameraX, ML Kit y Compose lo soportan.

## D-17 · Conjunto de fotos de prueba
- 30–50 fotos propias. Los originales quedan en `testdata/vision/raw/` (ignorado por git); se versiona una copia **reescalada (lado mayor 1600 px), en JPEG y sin EXIF**, porque el repositorio será público en la Fase 7 y el EXIF puede llevar la ubicación.
- Cada foto tiene una etiqueta de texto con **los 81 dígitos y el origen de cada celda** (impreso, manuscrito o vacía) y una línea de metadatos (luz, ángulo, papel) para desglosar las métricas. El origen hace falta para medir por separado los manuscritos y para la Fase 5 (D-12).
- Etiquetado **asistido por el pipeline**: una herramienta de `vision-core` rectifica la foto y prerrellena las celdas vacías; cuando haya reconocedor, también propone los dígitos. Un test valida cada etiqueta con el `solver` (sin repetidos y con solución).

## D-18 · Arquitectura de la app
Aceptada al planificar la Fase 3; detalle en `08-plan-fase-3.md`: una sola `Activity` con Compose y un `CoachViewModel` con `StateFlow`; Navigation 3 con la pila de pantallas en el `ViewModel`; sin inyección de dependencias; IBM Plex Sans empaquetada (D-03).

Al implementarla (Fase 3): la máquina de estados es la pila de Navigation 3 más un `StateFlow` por pantalla (lectura, revisión, pista), sin un `CoachState` único que duplique la pila. La lógica que no dibuja (revisión, validación, explicaciones, pista, códec del estado) es Kotlin puro con tests en la JVM. El estado se guarda en un `SavedStateHandle` como texto en cada cambio y sobrevive al cierre del proceso; el paso de la pista no se guarda, se recalcula con el motor. La fuente es la variable de Google Fonts (las cifras ya son tabulares).

## D-19 · Validación antes de las pistas
Aceptada al planificar la Fase 3; detalle en `08-plan-fase-3.md`: los repetidos bloquean la pista; si el tablero no tiene solución pero los impresos sí, los manuscritos que no coinciden con ella se marcan como posibles errores del usuario; con varias soluciones se avisa y se sigue.

Al implementarla (Fase 3): los posibles errores del usuario también bloquean la pista, porque el motor supone un tablero correcto; los dígitos de origen desconocido cuentan como impresos. **Límite medido:** de 176 impresos cambiados por un dígito que no crea repetidos, 112 piden revisar la lectura, 58 no se pueden detectar (el tablero sigue teniendo solución) y 6 (3 %) culpan a manuscritos correctos; por eso el aviso dice "podría". La primera defensa es la confianza de la lectura: ninguna lectura segura de un impreso falló en las pruebas.

## D-20 · Cámara con CameraX
Aceptada al planificar la Fase 4; detalle en `09-plan-fase-4.md`: `Preview` e `ImageAnalysis` (último fotograma, YUV 420, ≈ 1920 × 1080) y el plano Y como imagen en gris, sin `Bitmap`; visor en Compose si es posible; permiso en tiempo de ejecución, con la galería como alternativa si se deniega.

## D-21 · Lectura en vivo con votación por celda
Aceptada al planificar la Fase 4, con preferencia del usuario por los fotogramas frente a una foto fija al estabilizarse: detección en cada fotograma para el contorno y la estabilidad; con el tablero quieto, lecturas completas de varios fotogramas y votación por celda (mayoría y confianza combinada); captura automática cuando la votación se estabiliza o al llegar al máximo de lecturas. Umbrales fijados con datos. Se reconsidera si en 4.1 los fotogramas leen claramente peor que una foto. Salvedad conocida: la votación no corrige errores sistemáticos como el 1 manuscrito leído como 4.

## D-22 · Secuencias grabadas como datos de prueba
Aceptada al planificar la Fase 4: la cámara no se automatiza en los tests, así que una herramienta de debug graba sesiones de fotogramas en gris que se etiquetan y se reproducen en la JVM y en el móvil. No se versionan, como los originales de D-17.

---

## Preguntas abiertas

- ¿Qué orden de técnicas usar al elegir "el siguiente paso"? Propuesta: el orden de dificultad de la sección 3 del diseño, configurable.
- Dataset de entrenamiento: ¿cuántas fotos propias se pueden recopilar y etiquetar?
- ¿Cómo se distinguen patrón y eliminaciones en cadenas/coloración con un solo acento? (ver D-12)

### Resueltas

- ~~¿Se separa `vision` en un núcleo JVM puro y una capa Android?~~ Sí (D-16).
- ~~¿Se distingue visualmente entre pista impresa y dígito del usuario?~~ Sí, por peso tipográfico (D-12).
- ~~¿Se ofrece un modo "mostrar solo la técnica" antes de revelar las celdas exactas?~~ Sí, revelado gradual por niveles (D-12).
