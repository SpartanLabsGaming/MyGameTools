pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        // Version declared once here so the root project and each module can apply
        // `id("org.jetbrains.dokka")` with no version (the convention plugin applies it too;
        // re-declaring in a module's `plugins {}` block is what generates its DSL accessors).
        id("org.jetbrains.dokka") version "2.2.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "MyGameTools"

includeBuild("build-logic")

include("gametools-core", "gametools-net", "gametools")
