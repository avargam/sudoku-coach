plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

// App de extremo a extremo (Fase 3, D-18). AGP 9 incluye el soporte de Kotlin, sin plugin aparte.
android {
    namespace = "dev.sudokucoach.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.sudokucoach"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Solo la arquitectura del A57, como vision: x86_64 (emulador) sumaba 73 MB de OpenCV y ML Kit y
        // las copias de APK por usbipd se vuelven lentas a ratos. Si se usa el emulador, añadir "x86_64".
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
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
}

dependencies {
    implementation(project(":solver"))
    implementation(project(":vision"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation3:navigation3-runtime:1.2.0")
    implementation("androidx.navigation3:navigation3-ui:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests locales en la JVM: JUnit 4, el que ejecuta AGP para testDebugUnitTest.
    testImplementation(kotlin("test-junit"))

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
