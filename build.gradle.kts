plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    kotlin("plugin.compose") version "2.4.20" apply false
    id("com.android.library") version "9.4.1" apply false
    id("com.android.application") version "9.4.1" apply false
    // Licencias de terceros para la pantalla "Acerca de" (tarea P.6).
    id("com.mikepenz.aboutlibraries.plugin.android") version "15.2.0" apply false
}

allprojects {
    group = "dev.sudokucoach"
    version = "0.1.0-SNAPSHOT"
}
