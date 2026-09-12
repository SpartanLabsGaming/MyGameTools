pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        // Versions declared once here so the root project and each module can apply these ids
        // with no version (the convention plugins apply Kotlin/serialization/Dokka too;
        // re-declaring in a module's `plugins {}` block is what generates its DSL accessors).
        // The root build applies KGP + serialization with `apply false` purely to pull them
        // onto the root plugin classpath, so Gradle shares ONE plugin classloader with every
        // subproject and Dokka can resolve `KotlinBasePlugin` (issue #40, Gradle #25616). All
        // three ids stay on one version, matching the `build-logic` marker deps.
        id("org.jetbrains.kotlin.jvm") version "2.2.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
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

include("gametools-core", "gametools-net", "gametools-world", "gametools")
