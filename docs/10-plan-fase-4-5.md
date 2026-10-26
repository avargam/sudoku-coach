# 10 · Plan detallado: Fase 4.5 (publicación)

Objetivo de la fase: **hacer público el repositorio como pieza de portafolio, con una APK descargable**. Quien llegue al repo tiene que entender en un minuto qué hace la app y por qué es interesante, instalarla en su móvil sin compilar nada y, si quiere, compilarla y probarla desde el código siguiendo el README. Se intercala antes de la CNN (Fase 5) sin renumerar las fases siguientes; las tareas se numeran P.0–P.8 para no confundirlas con las de la Fase 4.

**Hecho cuando:**
- el repo es público, con un README en inglés que explica el proyecto con capturas y una demo, y una versión en español;
- los documentos de diseño y planificación (`docs/`) no están en el repo público, ni en su historial;
- en la página de Releases hay una APK firmada con la clave propia, generada por la CI al crear un tag, que se instala en un Android ≥ 8 y funciona de principio a fin (cámara, galería, revisión y pista);
- desde un clon limpio, con los requisitos que dice el README, compilan la app y pasan los tests de la JVM.

**Fuera de alcance:** Google Play (pide cuenta de desarrollador, ficha y revisión; se reconsidera más adelante), el overlay sobre la cámara y la revisión de accesibilidad (siguen en la Fase 7), y cambios de funcionalidad.

## Punto de partida

- **README** en español, de trabajo: estado de las fases, tabla de documentos y comandos. No tiene capturas ni explica el proyecto a quien no lo conoce.
- **CI** (`.github/workflows/ci.yml`): tests de la JVM y compilación de debug en cada push a `main` y en cada PR. No genera APKs descargables.
- **Release:** `assembleRelease` compila con R8 y recursos reducidos y da una APK sin firmar de 41 MB (la de debug pesa 77 MB). En 3.0 compiló sin reglas propias para OpenCV ni ML Kit, pero con una pantalla de marcador: **no se ha probado con la app completa**. R8 puede romper ML Kit, OpenCV, CameraX o la serialización del estado guardado (3.8).
- **Solo `arm64-v8a`** (para que la APK de debug no pasara de 77 MB): sirve para casi todos los móviles actuales, pero no para el emulador de un PC x86_64.
- **Licencias:** Apache-2.0 (D-10). La fuente IBM Plex Sans (OFL) ya lleva su licencia en `assets/licenses`. El material GPL de HoDoKu está fuera del repo (D-09). Las fotos de prueba no tienen EXIF y se mantienen (decisión del usuario); son páginas de un libro, así que el README lo dirá.
- **Historial:** 58 commits; los autores son el nombre del usuario, con el correo `noreply` de GitHub y el de la universidad.

## Decisiones

### D-23 · Distribución: APK firmada en GitHub Releases, generada por la CI
- La APK se publica en **GitHub Releases**, no en Google Play.
- Se firma con una **clave de release propia**, creada por el usuario con `keytool` y guardada fuera del repo. En GitHub va como secretos del repo (keystore en base64 y contraseñas). Con la misma clave, cada versión nueva se instala encima de la anterior.
- Un **workflow** se lanza al crear un tag `v*`: compila la release, la firma, calcula su SHA-256 y crea la Release con la APK y las notas. El `versionName` sale del tag y el `versionCode` se calcula a partir de él, para que no haya que tocar el código para publicar.
- Sin los secretos (en un clon o un fork), `assembleRelease` sigue dando la APK sin firmar: nadie necesita la clave para compilar.

### D-24 · README en inglés, con versión en español
- `README.md` en **inglés**, para el público más amplio del portafolio. Enlaza a `README.es.md`, en español, con el mismo contenido.
- Lo que hoy explican los documentos de diseño y le sirve a quien visita el repo (cómo funciona, cifras, cómo compilar) pasa al README, porque `docs/` deja de ser pública (D-25).

### D-25 · Documentos de diseño y planificación privados
- Por decisión del usuario, `docs/` (problema, decisiones, diseño, planes de fase y puesta en marcha) **no se publica**: se añade a `.gitignore` y se saca del índice y del **historial** antes de hacer público el repo. Con solo ignorarla no bastaría: está en 42 de los 58 commits.
- Para no perder su historial ni su copia de seguridad, `docs/` pasa a un **repo privado aparte** (p. ej. `sudoku-coach-docs`), que se clona dentro de la carpeta del proyecto, ignorada por el principal.
- Los comentarios del código que citan decisiones (D-xx) y tareas se quedan: explican el porqué en el punto donde se aplica, aunque el documento no sea público. El README dice que son notas de diseño privadas.

## P.0 Plan y decisiones
- Este documento, D-23 y D-24 en `02-decisiones.md` y la fase en `04-plan-implementacion.md` (con las tareas de README y publicación que estaban en la Fase 7).

## P.1 Release que funciona
- Instalar la release (firmada con la clave de debug solo para probar) en el A57 y recorrer la app de principio a fin: escáner en vivo, galería, revisión, pista, y cierre y vuelta del proceso (estado guardado, 3.8).
- Corregir lo que rompa R8 con reglas en `proguard-rules.pro`, cada una con su motivo.
- Que el botón de grabar y los registros de medida no aparezcan en la release (dependen de `FLAG_DEBUGGABLE`): comprobarlo.
- **ABI:** decidir con números entre (a) solo `arm64-v8a`, (b) dividir por ABI (`splits`) y publicar dos APKs, `arm64-v8a` y `x86_64` para el emulador, o (c) una universal. Se mide el tamaño de cada una.

**Hecho cuando:** la release recorre la app sin fallos en el A57 y la elección de ABI está anotada con sus tamaños.

**Resultado:** la release (R8 en modo completo, el de AGP 9, con recursos reducidos) arranca en 0,18 s y no muestra el botón de grabar ni los registros de medida.

- **Fallo encontrado:** el escáner cerraba la app al empezar a leer, y la galería decía "No se pudo abrir la foto". En los dos casos, `TextRecognition.getClient` daba un `NullPointerException`, porque R8 alteraba las clases internas con las que ML Kit registra sus componentes. Se corrige con una regla de consumidor en `vision/consumer-rules.pro` que conserva `com.google.mlkit.**`; la APK crece 65 KB. En 3.0 no se vio porque la release solo se compiló.
- **Con la regla:** el usuario recorrió en el A57 el escáner en vivo, la revisión, la pista y la galería sin fallos. Tras matar el proceso en segundo plano, la app vuelve a la misma pista y etapa (estado guardado, 3.8).
- **ABI**, release sin firmar:

| APK | Tamaño | Para quién |
|---|---|---|
| `arm64-v8a` | 41 MB | Casi todos los móviles actuales |
| `x86_64` | 77 MB | El emulador en un PC Intel o AMD |
| Universal | 114 MB | Cualquiera, con el triple de descarga |

**Elegido (usuario):** dos APK, `arm64-v8a` como descarga principal y `x86_64` para el emulador. La división por ABI (`splits`) vale para todas las variantes y no admite `abiFilters`, así que se activa solo al compilar la release; debug sigue con `arm64-v8a`, y `deployDebug` y los tests no cambian.

## P.2 Firma
- El usuario crea la clave (`keytool -genkeypair …`; el comando va en `07-puesta-en-marcha.md`) y la guarda fuera del repo, con una copia de seguridad: si se pierde, las versiones nuevas no se podrán instalar encima.
- `signingConfigs.release` lee la clave de variables de entorno (`SUDOKUCOACH_KEYSTORE`, contraseñas y alias), o de `~/.gradle/gradle.properties` en local. Sin ellas, la release queda sin firmar, como hoy.
- `.gitignore` excluye `*.jks` y `*.keystore`.

**Hecho cuando:** en local, con la clave, sale una APK firmada que `apksigner verify` acepta e instala en el A57.

**Resultado:** `signingConfigs.release` lee `SUDOKUCOACH_KEYSTORE`, `SUDOKUCOACH_KEYSTORE_PASSWORD` y `SUDOKUCOACH_KEY_ALIAS` de las variables de entorno o de `~/.gradle/gradle.properties`; sin ellas, la release sale sin firmar. Se probó primero con una clave desechable. El usuario creó la clave real (RSA 4096, alias `sudoku-coach`, en `~/keys/`, con copia de seguridad) y las dos APK salen firmadas con ella (esquema v2, certificado `CN=Alonso Vargas`, SHA-256 `47c4bd84…ca89e2`). Pasos en `07-puesta-en-marcha.md` §6.

- Para instalarla hubo que **desinstalar la app del A57**, firmada con la clave de debug: se perdieron el estado guardado y las 12 secuencias del móvil, que están completas en `testdata/vision/sequences` (mismos fotogramas que contó `SequenceScanTest`). `SequenceScanTest` se saltará hasta volver a grabar o copiar secuencias; `SequenceReplayTest` y `LiveTuningTest` no cambian, porque usan los `frames.txt` locales.
- El usuario recorrió la release firmada en el A57 (permiso de la cámara, escaneo, revisión y pista) sin fallos.
- Mientras esté instalada la release, `deployDebug` falla por firma distinta: para volver a debug, `adb uninstall dev.sudokucoach` (borra los datos de la app).

## P.3 Workflow de publicación
- `.github/workflows/release.yml`, en tags `v*`: comprueba que el tag es una versión válida, compila con la clave de los secretos, verifica la firma, calcula el SHA-256 y crea la Release con la APK (`sudoku-coach-<versión>.apk`) y las notas.
- Las notas salen de un apartado del `CHANGELOG.md` (nuevo, en inglés, una sección por versión con lo que cambia para quien usa la app).
- Se prueba antes en un fork o con un tag de prueba (`v0.0.0-test`) que se borra después.

**Hecho cuando:** un tag de prueba crea una Release con la APK firmada, que se descarga desde el móvil y se instala.

**Resultado:**
- **Versión:** `versionName` sale de `SUDOKUCOACH_VERSION` (el tag sin la `v`; en local, `0.0.0-dev`), y `versionCode` es X × 1 000 000 + Y × 1 000 + Z (0.5.0 → 5000).
- **`release.yml`:** en tags `v*`, valida el tag (los de sufijo, como `v0.0.0-test`, salen como prerelease), decodifica la clave de los secretos en un temporal que se borra al terminar, compila, verifica la firma de las dos APK, las publica como `sudoku-coach-<versión>-<abi>.apk` con `SHA256SUMS.txt`, y toma las notas del apartado `## [X.Y.Z]` de `CHANGELOG.md`.
- **Secretos del repo:** `SUDOKUCOACH_KEYSTORE_BASE64`, `SUDOKUCOACH_KEYSTORE_PASSWORD` y `SUDOKUCOACH_KEY_ALIAS`.
- Los pasos de shell (validación del tag y notas) se probaron en local, y después en GitHub con el tag `v0.0.0-test`: la Release (prerelease) salió con las dos APK y `SHA256SUMS.txt`, y el usuario descargó la de `arm64-v8a` desde el navegador del A57 y la instaló (`versionName` 0.0.0-test). El tag se borró después; la Release de prueba se borra a mano en la web.

## P.4 Compilar desde un clon limpio
- Clonar en un directorio nuevo y seguir solo el README. Requisitos: JDK 21, Android SDK con `platforms;android-37.0` y `build-tools;37.0.0` (lo que instala la CI) y GTK2 para los tests de `vision-core`.
- Comprobar que nada depende de archivos sin versionar: `local.properties` (se genera o se explica `ANDROID_HOME`), las fotos originales y las secuencias (sus tests se saltan sin ellas), y el oráculo de HoDoKu (solo hace falta para regenerar el banco).
- Arreglar lo que falle y anotar en el README qué tests necesitan el móvil o datos que no están en el repo.

**Hecho cuando:** en un clon limpio, `./gradlew :solver:test :vision-core:test :app:testDebugUnitTest :app:assembleDebug` pasa siguiendo solo el README.

**Resultado:** clon nuevo de `feat/fase-4-5` desde GitHub (8 MB):

- **Sin SDK de Android** (sin `ANDROID_HOME` ni `local.properties`): los tests del `solver` pasan; el resto falla con el mensaje de AGP "SDK location not found", que dice qué hacer. El README tiene que pedir `ANDROID_HOME`, con los paquetes `platforms;android-37.0` y `build-tools;37.0.0`.
- **Con el SDK**, el comando completo pasa: 168 tests (`solver` 59, `vision-core` 40, `app` 69) y la APK de debug. Se saltan 3, que necesitan las secuencias grabadas, que no se versionan (`SequenceReplayTest` ×2 y `LiveTuningTest`). La release, sin la clave, sale sin firmar (P.2).
- **Arreglado:** `tools/vision/push_labels.sh` y `pull_sequences.sh` sacaban `adb` de `local.properties`; ahora usan `$ADB`, el `adb` del PATH o el de `ANDROID_HOME`.
- **Para el README (P.5):**
  - requisitos: JDK 21, el SDK con esos paquetes y `ANDROID_HOME`, y GTK2 para los tests de `vision-core`;
  - qué necesita el móvil: los tests instrumentados y `deployDebug`;
  - qué necesita datos que no están en el repo: los originales de la cámara (`PhotoScanTest` y `EndToEndTest` los usan si están) y las secuencias;
  - el oráculo de HoDoKu solo hace falta para regenerar el banco del `solver`.

## P.5 README
- **`README.md` en inglés:**
  - qué hace la app en dos frases, con una demo: un GIF o un vídeo corto del escáner llegando a la pista;
  - capturas de escáner, revisión y pista, en claro y oscuro;
  - botón de descarga a la última Release, con cómo instalar una APK fuera de Google Play (permitir orígenes desconocidos) y los requisitos (Android 8+, `arm64`);
  - **cómo funciona:** el camino cámara → detección y segmentación (OpenCV) → lectura (ML Kit) → votación → revisión → motor de técnicas → pista, con un diagrama;
  - **lo técnicamente interesante, con cifras:** las 13 técnicas, que coinciden con HoDoKu en 16 753 pasos; la lectura de dígitos (impresos 100 %, manuscritos 94,6 %); la votación en vivo y las sospechosas sin errores sin marcar; la revisión en < 2 s; todo en el dispositivo;
  - compilar y probar desde el código, la estructura de módulos, el estado y la hoja de ruta (Fases 5–7), y la licencia y créditos (OpenCV, ML Kit, IBM Plex Sans, HoDoKu como oráculo);
  - una nota sobre los datos de prueba (fotos de páginas de un libro, solo para medir la visión).
- **`README.es.md`** con el mismo contenido en español, y enlaces cruzados en la cabecera de los dos.
- **Imágenes** en `media/`, reducidas: capturas con `adb exec-out screencap`, con datos de prueba (nunca la galería del usuario), y la demo grabada con `adb shell screenrecord` mientras el usuario escanea. El GIF necesita `ffmpeg` en WSL. Si pesa demasiado (> 5 MB), se sube un MP4 como adjunto de GitHub o se muestran capturas.

**Hecho cuando:** el README se lee en GitHub (vista previa de la rama) con las imágenes, la demo y todos los enlaces funcionando, y alguien que no conoce el proyecto entiende qué hace y cómo instalarlo.

**Resultado:** `README.md` en inglés, que sustituye al de trabajo y ya no enlaza `docs/` (D-25), y `README.es.md` con el mismo contenido. Secciones: qué hace, instalar, cómo funciona (con un diagrama Mermaid), lo destacable con cifras, compilar desde el código (con los requisitos de P.4), estructura, hoja de ruta, datos de prueba, y créditos y licencia.

- **Imágenes** en `media/`, sacadas de dos grabaciones de pantalla del A57 en modo oscuro (`adb shell screenrecord`), mientras el usuario escaneaba la página 217 del libro: `demo.gif` (13,8 s, 320 px, 2,2 MB); las capturas sueltas se quitaron, a petición del usuario, porque el GIF ya enseña las tres pantallas. Se recortó la barra de estado, porque la hora saltaba entre las dos grabaciones.
- **Fallo visto en la grabación:** en la pista, tras **Aplicar paso**, la línea "Paso aplicado: N candidatos descartados" tapa el final de la explicación de la tercera etapa. Las imágenes están tomadas antes de aplicar.
- El usuario revisó el README en la vista previa de GitHub y le parece bien. El fallo del texto tapado queda como está, por decisión del usuario.

## P.6 Revisión antes de hacerlo público
- **Secretos e información privada:** buscar en todo el historial claves, rutas personales, `local.properties` y tokens (`git log -p` con patrones, y `gitleaks` si se puede instalar). Confirmar que no se versionan los originales, las secuencias ni el reglib de HoDoKu.
- **Correos en el historial:** algunos commits llevan el correo de la universidad, que quedará público. **Decidido (usuario): se acepta**, así que la reescritura de P.7 solo saca `docs/`.
- **Archivos grandes:** lo más pesado del historial, para que el clon no sea lento.
- **Licencias de terceros:** que la APK lleve las que pide cada dependencia (OpenCV Apache-2.0, OFL de la fuente) y que el uso de ML Kit cumple sus condiciones. Si hace falta, una pantalla o un enlace "Licencias" en la app.
- **Ajustes del repo en GitHub (los hace el usuario):** descripción, *topics* (`android`, `kotlin`, `jetpack-compose`, `opencv`, `sudoku`, `computer-vision`), enlace a la Release y vista previa para redes sociales.

**Hecho cuando:** la revisión no deja nada pendiente o lo pendiente está anotado y aceptado por el usuario.

## P.7 Sacar `docs/` del repo público (D-25)
- **Antes de tocar nada:** crear el repo privado de documentos con el contenido actual de `docs/` y su historial (`git filter-repo --subdirectory-filter docs` sobre una copia del repo), y comprobar que está completo.
- **En el repo principal:** `docs/` en `.gitignore` y `git rm -r --cached docs`. Sacar del README, de `app/README.md`, `vision/README.md`, `tools/vision/README.md` y `testdata/vision/README.md` los enlaces a `docs/`. Lo que haga falta para compilar y probar (de `07-puesta-en-marcha.md`) pasa al README; las imágenes del README van en `media/`, no en `docs/media/`.
- **Reescribir el historial** para quitar `docs/` de todos los commits (`git filter-repo --path docs --invert-paths`), en la misma pasada que el autor si se decide cambiar el correo (P.6). Va al final, con las PRs fusionadas y justo antes de hacerlo público: cambia todos los hashes, obliga a un `push --force` de `main` y de las ramas, y las PRs y clones anteriores quedan desfasados. Se hace sobre un clon nuevo, con una copia de seguridad del repo entero.
- Comprobar en un clon nuevo del remoto que `git log --all -- docs` no devuelve nada y que compila y pasan los tests.

**Hecho cuando:** el repo privado tiene `docs/` con su historial, y en el remoto principal no queda `docs/` en ningún commit.

## P.8 Primera versión pública y cierre
- Fusionar en `main`, crear el tag de la primera versión pública (`v0.5.0`, que sigue a la Fase 4) y comprobar la Release.
- Instalar la APK de la Release en el A57. Antes hay que desinstalar la de debug, porque tienen firmas distintas. Recorrer la app.
- Hacer público el repo (el usuario).
- Actualizar, en el repo privado de documentos, `04-plan-implementacion.md` y `07-puesta-en-marcha.md` (cómo publicar una versión), y el estado del README.

**Hecho cuando:** el repo es público y la APK de la Release se instala y funciona en el A57.

---

## Orden de trabajo

```
P.0 plan ─► P.1 release que funciona ─► P.2 firma ─► P.3 workflow ─┐
        └─► P.4 clon limpio ─────────────────────────────────────────┼─► P.6 revisión ─► P.7 sacar docs/ ─► P.8 publicación
        └─► P.5 README (las capturas y la demo, tras P.1) ───────────┘   (la reescritura del historial va justo antes de hacerlo público)
```

## Riesgos

| Riesgo | Mitigación |
|---|---|
| R8 rompe ML Kit, OpenCV o el estado guardado solo en la release | Se recorre la release en el A57 antes de firmar nada (P.1); reglas mínimas y comentadas |
| Se pierde la clave de firma | Copia de seguridad fuera del PC, anotada en `07-puesta-en-marcha.md`; sin ella, solo se puede publicar desinstalando la versión anterior |
| Los secretos se filtran en los logs de la CI | Solo en `release.yml`, que corre en tags y no en PRs de forks; la keystore se decodifica en un archivo temporal que se borra al terminar |
| La APK no se instala en el móvil de quien visita el repo | Requisitos claros en el README (Android 8+, `arm64`); APK `x86_64` si se elige en P.1 |
| La demo pesa demasiado para el README | GIF corto y reducido; si no, MP4 como adjunto o capturas |
| Se pierden los documentos al sacarlos del repo, o la reescritura del historial borra algo más | Repo privado de documentos creado y comprobado antes; copia de seguridad del repo entero; reescritura sobre un clon nuevo y comprobada antes del `push --force` |
| El README queda cojo sin los documentos | Lo que le sirve al visitante (cómo funciona, cifras, compilar) se escribe en el README (P.5) |
