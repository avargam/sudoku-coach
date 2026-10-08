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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
