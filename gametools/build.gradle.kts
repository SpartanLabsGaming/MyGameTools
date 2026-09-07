// Umbrella artifact. Carries no source of its own - it re-exports every GameTools module via
// `api`, so a consumer that depends on `io.github.spartanlabsgaming:gametools` gets the whole
// framework on one dependency line, exactly as the pre-split `GameTools` artifact did.

plugins {
    id("gametools.published-library")
}

dependencies {
    api(project(":gametools-core"))
    api(project(":gametools-net"))
}

mavenPublishing {
    coordinates("io.github.spartanlabsgaming", "gametools", "5.0.0")
    pom {
        name.set("GameTools")
        description.set("Umbrella artifact re-exporting every GameTools module (gametools-core, gametools-net).")
    }
}
