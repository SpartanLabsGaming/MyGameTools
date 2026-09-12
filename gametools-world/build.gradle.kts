// Phase 1 module (docs/phase-1-map-and-space-plan.md): the bounded map model, zones, physics
// and vision systems that build on gametools-core's World. Bootstrapped empty by issue #48;
// its first public types land with issue #46 (map model).

plugins {
    id("gametools.published-library")
}

dependencies {
    api(project(":gametools-core"))
}

mavenPublishing {
    coordinates("io.github.spartanlabsgaming", "gametools-world", "5.1.0")
    pom {
        name.set("GameTools World")
        description.set("The map, zone, physics and vision systems built on gametools-core's World.")
    }
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/SpartanLabsGaming/MyGameTools/blob/master/gametools-world/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
