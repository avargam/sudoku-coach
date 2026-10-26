import javax.inject.Inject

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin.android")
}

/**
 * Copia las fotos 001 y 016 de prueba y todas las etiquetas (D-17) a un directorio generado que se usa como assets
 * de `androidTest` (tarea 3.3). Las etiquetas sirven también para los originales de la cámara, que no
 * se versionan y se copian al móvil a mano (ver `PhotoScanTest`).
 */
abstract class CopyTestPhoto : DefaultTask() {
    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun copy() {
        fs.sync {
            from(files)
            into(output)
        }
    }
}

val copyTestPhoto = tasks.register<CopyTestPhoto>("copyTestPhoto") {
    files.from(rootProject.files("testdata/vision/photos/001.jpg", "testdata/vision/photos/016.jpg"), rootProject.fileTree("testdata/vision/labels") { include("*.txt") })
}

// La release se publica en dos APK, una por ABI (tarea P.1): `arm64-v8a` para los móviles y `x86_64` para el
// emulador. La división por ABI vale para todas las variantes y no admite `abiFilters`, así que se activa solo
// al compilar la release; la de debug sigue con `arm64-v8a`.
val buildsRelease = gradle.startParameter.taskNames.any { "Release" in it }

// Firma de la release con la clave propia (D-23, tarea P.2), que nunca entra en el repo: en la CI, de variables
// de entorno; en local, de `~/.gradle/gradle.properties`. Sin ella, la release queda sin firmar, así que nadie
// la necesita para compilar.
fun signing(name: String): String? = providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull
val releaseKeystore = signing("SUDOKUCOACH_KEYSTORE")

// Versión (D-23, tarea P.3): la del tag `vX.Y.Z`, que el workflow de publicación pasa en `SUDOKUCOACH_VERSION`;
// en local, `0.0.0-dev`. El `versionCode` sale de X.Y.Z (X × 1 000 000 + Y × 1 000 + Z), para que cada
// versión publicada se instale encima de la anterior sin tocar el código.
val appVersion = providers.environmentVariable("SUDOKUCOACH_VERSION").orElse("0.0.0-dev").get()
val appVersionCode = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(appVersion)?.destructured
    ?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt() }
    ?.coerceAtLeast(1)
    ?: error("La versión $appVersion no es X.Y.Z")

// App de extremo a extremo (Fase 3, D-18). AGP 9 incluye el soporte de Kotlin, sin plugin aparte.
android {
    namespace = "dev.sudokucoach.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.sudokucoach"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // En debug, solo la arquitectura del A57, como vision: x86_64 (emulador) sumaba 73 MB de OpenCV y
        // ML Kit y las copias de APK por usbipd se vuelven lentas a ratos. Si se usa el emulador, añadir "x86_64".
        if (!buildsRelease) {
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signing("SUDOKUCOACH_KEYSTORE_PASSWORD")
                keyAlias = signing("SUDOKUCOACH_KEY_ALIAS")
                // En un almacén PKCS12 (el de keytool por defecto), la clave tiene la misma contraseña.
                keyPassword = signing("SUDOKUCOACH_KEY_PASSWORD") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = buildsRelease
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(copyTestPhoto, CopyTestPhoto::output)
    }
}

// Los tests de la revisión (tarea 3.4) usan las etiquetas de las fotos (D-17) como tableros reales, y
// los de las explicaciones (3.6), pasos reales del banco del oráculo del solver. Se declaran como
// entrada para que Gradle vuelva a ejecutar los tests al cambiar una etiqueta o el banco.
tasks.withType<Test>().configureEach {
    val labels = rootProject.file("testdata/vision/labels")
    val bank = rootProject.file("solver/src/test/resources/bank")
    systemProperty("sudokucoach.labels", labels.absolutePath)
    systemProperty("sudokucoach.bank", bank.absolutePath)
    // Secuencias grabadas (tarea 4.4): no se versionan, así que pueden no estar (en la CI).
    val sequences = rootProject.file("testdata/vision/sequences")
    systemProperty("sudokucoach.sequences", sequences.absolutePath)
    inputs.files(fileTree(sequences) { include("*/frames.txt", "*/label.txt") }).withPropertyName("sequences")
    inputs.dir(labels).withPropertyName("labels")
    inputs.dir(bank).withPropertyName("bank")
}

dependencies {
    implementation(project(":solver"))
    implementation(project(":vision"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // Orientación EXIF de las fotos en API 26–27, donde no hay ImageDecoder (tarea 3.3).
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.navigation3:navigation3-runtime:1.2.0")
    implementation("androidx.navigation3:navigation3-ui:1.2.0")
    // Cámara en vivo (Fase 4, D-20): visor de Compose (camera-compose) y, en 4.1, ImageAnalysis.
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-compose:1.6.2")
    // Lista de licencias de terceros, generada en la compilación (tarea P.6).
    implementation("com.mikepenz:aboutlibraries-core:15.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests locales en la JVM: JUnit 4, el que ejecuta AGP para testDebugUnitTest.
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    // Tests de Compose en el móvil (connectedDebugAndroidTest).
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// `installDebug` de AGP se queda colgado con el A57 (Android 16), con el APK sin instalar, mientras que
// `adb install` del mismo APK tarda 2-3 s. Esta tarea compila e instala con adb directamente.
tasks.register<Exec>("deployDebug") {
    group = "install"
    description = "Compila la app en debug y la instala en el móvil conectado con adb install."
    dependsOn("assembleDebug")
    val adb = androidComponents.sdkComponents.adb
    val apk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    doFirst {
        executable = adb.get().asFile.absolutePath
        args("install", "-r", "-t", apk.get().asFile.absolutePath)
    }
}
