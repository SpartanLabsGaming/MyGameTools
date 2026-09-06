plugins {
    id("gametools.published-library")
}

mavenPublishing {
    coordinates("io.github.spartanlabsgaming", "gametools-core", "4.0.0")
    pom {
        name.set("GameTools Core")
        description.set("GameTools object model, stats, buffs, the typed event bus, the seeded deterministic tick, and the opt-in simulation loop.")
    }
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
