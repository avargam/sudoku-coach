# Puesta en marcha del entorno

Qué hay que hacer para retomar el desarrollo en el entorno de D-15: Windows con WSL2 (Ubuntu), el
repositorio en `~/sudoku-coach` y el Galaxy A57 por USB.

**No hay servidores que levantar.** Todo se ejecuta en el dispositivo (D-03): no hay backend, base de
datos ni emulador. Solo hacen falta una terminal de WSL y, para lo que toca Android, el móvil
conectado.

---

## 1. Cada sesión

### 1.1 Abrir el proyecto

En una terminal de WSL (o en VS Code con la extensión WSL, abriendo la carpeta desde WSL):

```bash
cd ~/sudoku-coach
git status                      # rama actual y cambios pendientes
./gradlew :solver:test :vision-core:test
```

Con esto basta para trabajar en el `solver` o en `vision-core`. Ninguno de los dos necesita el móvil.

### 1.2 Conectar el móvil (solo para `vision` y, más adelante, `app`)

1. **Móvil:** desbloqueado y conectado por cable. En la notificación "USB para…", elegir
   **Transferir archivos**.
2. **Windows, PowerShell normal** (no hace falta administrador). Hay que dejar esta ventana abierta:
   ```powershell
   usbipd list                                         # buscar "SAMSUNG Android" y su BUSID (p. ej. 2-3)
   usbipd attach --wsl --busid <BUSID> --auto-attach
   ```
   `--auto-attach` vuelve a conectar el móvil a WSL si se desconecta. Solo funciona mientras la
   ventana siga abierta.
3. **WSL:**
   ```bash
   adb devices
   ```
   Tiene que aparecer `R5GL706P6NJ	device`. Si pone `unauthorized`, aceptar en el móvil el diálogo
   "¿Permitir depuración USB?" (conviene marcar "Permitir siempre desde este ordenador").

### 1.3 Ejecutar en el móvil

```bash
./gradlew :vision:connectedDebugAndroidTest       # tests instrumentados (≈ 30 s)

# Métricas de reconocimiento (≈ 4 min) y su informe:
./gradlew :vision:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.metrics=true :vision:saveDeviceMetrics
./gradlew :vision-core:test                       # → vision-core/build/reports/vision/device.md
```

Los resultados de los tests quedan en `vision/build/reports/androidTests/connected/debug/index.html`.

### 1.4 Al terminar

- **Commits:** los hace el usuario. Claude propone cuándo hacerlos y con qué mensaje.
- **Móvil:** se puede desconectar sin más. Si se quiere liberar de WSL antes:
  `usbipd detach --busid <BUSID>` en PowerShell.

---

## 2. Terminales que hay abiertas

| Dónde | Para qué | ¿Hay que dejarla abierta? |
|---|---|---|
| PowerShell (Windows) | `usbipd attach --auto-attach` | Sí, mientras se use el móvil |
| WSL / VS Code | Gradle, adb, git, Claude Code | Sí |

---

## 3. Configuración que ya está hecha (por si hay que rehacerla)

Solo hace falta repetirla si se reinstala WSL, se cambia de PC o de móvil.

| Pieza | Estado actual | Cómo se hizo |
|---|---|---|
| JDK 21 | `openjdk-21-jdk` en WSL | `sudo apt install openjdk-21-jdk` |
| Gradle | wrapper versionado (9.8.1) | Nada que instalar: `./gradlew` lo descarga |
| Android SDK | `~/Android/Sdk` | Command-line tools 23 y luego `android sdk install "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"` |
| Variables | En `~/.bashrc` | `export ANDROID_HOME="$HOME/Android/Sdk"` y `export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"` |
| SDK para Gradle | `local.properties` (ignorado por git) | `sdk.dir=/home/avargam/Android/Sdk` |
| usbipd-win | Instalado en Windows | `winget install usbipd`; **una sola vez**, en PowerShell como administrador: `usbipd bind --busid <BUSID>` |
| Permisos USB | `/etc/udev/rules.d/51-android.rules` | `SUBSYSTEM=="usb", ATTR{idVendor}=="04e8", MODE="0660", GROUP="plugdev"`, el usuario en el grupo `plugdev` y `sudo udevadm control --reload-rules` |
| Móvil | Modo desarrollador y depuración USB activos | Ajustes → Información del teléfono → Información de software → tocar 7 veces "Número de compilación". Desactivar **Bloqueador automático** (Ajustes → Seguridad y privacidad) y activar Opciones de desarrollador → **Depuración USB** |
| Python (herramientas) | `tools/oracle/.venv` y `tools/vision/.venv` (ignorados) | `python3 -m venv .venv && .venv/bin/pip install -r requirements.txt` en cada carpeta |

Las herramientas de Python solo hacen falta para regenerar el banco del oráculo
(`tools/oracle/README.md`) o preparar fotos nuevas (`tools/vision/README.md`). No se usan para
compilar ni para los tests.

---

## 4. Problemas frecuentes

Todos han pasado durante el desarrollo.

| Síntoma | Causa | Solución |
|---|---|---|
| `adb devices` sale vacío y `usbipd list` muestra el móvil como **Shared** | No está conectado a WSL | `usbipd attach --wsl --busid <BUSID> --auto-attach` |
| `usbipd list` lo muestra como **Not shared** | Cambio de puerto USB o actualización de usbipd | `usbipd bind --busid <BUSID>` en PowerShell como administrador y luego `attach` |
| El móvil llega a WSL pero `adb devices` sigue vacío | La depuración USB se ha desactivado (Samsung lo hace a veces al reiniciar o actualizar) o el Bloqueador automático se ha vuelto a activar | Reactivarla en Opciones de desarrollador. Para comprobarlo: `cat /sys/bus/usb/devices/1-1:*/interface` debe incluir `ADB Interface` |
| `No connected devices!` en Gradle aunque `adb devices` muestre el móvil | El daemon de Gradle conserva el estado de antes de conectar | `./gradlew --stop` y repetir |
| Un test largo (métricas) falla sin mensaje | El móvil se ha desconectado a mitad. Suele ser el cable o la pantalla bloqueada | Reconectar. Para tests largos conviene tener el cable fijo y la pantalla sin bloqueo automático: Opciones de desarrollador → **Pantalla activa** mientras carga |
| `adb: command not found` | La terminal no ha cargado `~/.bashrc` | `source ~/.bashrc` o abrir otra terminal |
| Los tests de `vision-core` no se vuelven a ejecutar al cambiar una etiqueta | — | Ya está resuelto: `testdata/vision` es entrada de la tarea `test`. Si hiciera falta, usar `--rerun-tasks` |

---

## 5. CI

GitHub Actions (`.github/workflows/ci.yml`) se ejecuta en cada PR y en `main`, sin que haya que hacer
nada en local. Tiene dos trabajos:

- **Tests JVM:** `:solver:test :vision-core:test`.
- **Compilación Android:** instala el SDK y compila `:vision` y sus tests instrumentados. No los
  ejecuta, porque no hay móvil.
