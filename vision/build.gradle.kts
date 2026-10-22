import javax.inject.Inject

plugins {
    id("com.android.library")
}

/**
 * Copia las fotos de prueba (D-17) a un directorio generado que se usa como assets de `androidTest`
 * (tarea 2.8). No se apunta directamente a `testdata/vision` para no meter `raw/` en el APK de test.
 */
abstract class CopyTestPhotos : DefaultTask() {
    @get:InputFiles
    abstract val photos: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun copy() {
        fs.sync {
            from(photos)
            into(output.dir("photos"))
        }
    }
}

val copyTestPhotos = tasks.register<CopyTestPhotos>("copyTestPhotos") {
    photos.from(rootProject.fileTree("testdata/vision/photos") { include("*.jpg") })
}

// Librería Android de visión (D-16). AGP 9 incluye el soporte de Kotlin, sin plugin aparte.
android {
    namespace = "dev.sudokucoach.vision"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // El AAR de OpenCV trae nativos para 4 arquitecturas (123 MB). Basta con la del A57 (arm64-v8a)
        // y la de un posible emulador (x86_64).
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Tras ejecutar DeviceMetricsTest, guarda las predicciones del móvil junto a las fotos para que
// vision-core calcule las métricas (tarea 2.8). Ver vision/README.md.
tasks.register<Copy>("saveDeviceMetrics") {
    group = "vision"
    description = "Copia device-metrics.json del último test instrumentado a testdata/vision/results/."
    from(layout.buildDirectory.dir("outputs/connected_android_test_additional_output")) {
        include("**/device-metrics.json")
        // .../connected/SM-A576B - 16/device-metrics.json → mlkit-SM-A576B.json
        eachFile { path = "mlkit-${relativePath.segments.dropLast(1).last().substringBefore(" - ")}.json" }
    }
    into(rootProject.file("testdata/vision/results"))
    includeEmptyDirs = false
    mustRunAfter("connectedDebugAndroidTest")
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(copyTestPhotos, CopyTestPhotos::output)
    }
}

dependencies {
    implementation(project(":vision-core"))
    // Misma versión que Bytedeco en vision-core (D-16): la API org.opencv.* que vision-core usa como compileOnly.
    implementation("org.opencv:opencv:4.14.0")
    // Modelo incluido en el APK, sin descarga por Play Services (D-03, D-08).
    implementation("com.google.mlkit:text-recognition:16.0.1")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
