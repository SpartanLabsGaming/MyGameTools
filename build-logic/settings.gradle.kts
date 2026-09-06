// Standalone build that holds the shared Gradle configuration for every GameTools module as
// precompiled convention plugins. Included by the root settings via `includeBuild`.

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
