plugins {
    id("gametools.published-library")
}

mavenPublishing {
    coordinates("io.github.spartanlabsgaming", "gametools-core", "5.1.0")
    pom {
        name.set("GameTools Core")
        description.set("GameTools object model, stats, buffs, the typed event bus, the seeded deterministic tick, and the opt-in simulation loop.")
    }
}

// Test-only, module-wide opt-in to the Experimental WorldSystem seam (#76), so test fakes can
// implement and exercise it without per-class @OptIn. Never add this to the main source set -
// library code must not silently opt in. Removed when #79 graduates the seam.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>("compileTestKotlin") {
    compilerOptions.optIn.add("com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi")
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/SpartanLabsGaming/MyGameTools/blob/master/gametools-core/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
