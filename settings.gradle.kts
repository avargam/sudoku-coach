rootProject.name = "sudoku-coach"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":solver")
// Pendientes (se activan al crearlos desde Android Studio):
// include(":vision")
// include(":app")
