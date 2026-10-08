plugins {
    id("com.android.library")
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

dependencies {
    implementation(project(":vision-core"))
    // Misma versión que Bytedeco en vision-core (D-16): la API org.opencv.* que vision-core usa como compileOnly.
    implementation("org.opencv:opencv:4.14.0")
    // Modelo incluido en el APK, sin descarga por Play Services (D-03, D-08).
    implementation("com.google.mlkit:text-recognition:16.0.1")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
