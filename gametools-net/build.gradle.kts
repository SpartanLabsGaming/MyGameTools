plugins {
    id("gametools.published-library")
}

dependencies {
    api(project(":gametools-core"))
}

mavenPublishing {
    coordinates("io.github.spartanlabsgaming", "gametools-net", "5.1.0")
    pom {
        name.set("GameTools Net")
        description.set("The UDP GameServer, built on WebTools, plus the MouseAction wire type.")
    }
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/SpartanLabsGaming/MyGameTools/blob/master/gametools-net/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
