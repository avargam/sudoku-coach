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
        // Como en vision: solo la arquitectura del A57 y la de un posible emulador (OpenCV ocupa mucho por ABI).
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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
}
