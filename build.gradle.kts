import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    // This is a plain Kotlin library, not a Gradle plugin. `kotlin-dsl` would drag in
    // gradleApi(), whose bundled slf4j provider outranks logback and silently discards
    // every INFO/DEBUG record the library logs.
    `java-library`
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Spartan Laboratories Tools
    api("io.github.spartanlaboratories:WebTools:2.0.0c")
    // Direct dependency for the Color class; WebTools already brings the same 2.0.1.
    api("io.github.spartanlaboratories:GeneralTools:2.0.1")

    // Serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging - the library only ever binds to the slf4j facade, so that consumers
    // remain free to pick their own implementation.
    api("org.slf4j:slf4j-api:2.0.16")

    // Test
    testImplementation(kotlin("test"))

    // Tests supply the logback implementation the facade binds to, so that the
    // library's structured logging is actually exercised and visible.
    testImplementation("ch.qos.logback:logback-classic:1.5.18")
}

// A GameServer binds the fixed common UDP port, so no two test tasks that start one may run
// at the same time (Gradle runs independent tasks in parallel). Every port-binding test task
// declares this single-permit service so Gradle serializes them.
abstract class GameServerPortsLock : BuildService<BuildServiceParameters.None>
val gameServerPortsLock: Provider<GameServerPortsLock> =
    gradle.sharedServices.registerIfAbsent("gameServerPortsLock", GameServerPortsLock::class) {
        maxParallelUsages = 1
    }

tasks.test {
    useJUnitPlatform()
    usesService(gameServerPortsLock)
}

// Tests are split by level into com.spartanlabs.gaming.testing.<level> packages
// (see ~/.claude/CLAUDE.md - Testing 5-Level Hierarchy). `test` runs every level; these
// per-level tasks let CI gate them independently. Only the levels that currently have
// tests are wired up.
fun registerLevelTest(name: String, levelPackage: String, bindsPorts: Boolean, summary: String) =
    tasks.register<Test>(name) {
        group = "verification"
        description = summary
        val testSources = sourceSets.test.get()
        testClassesDirs = testSources.output.classesDirs
        classpath = testSources.runtimeClasspath
        useJUnitPlatform()
        filter { includeTestsMatching("com.spartanlabs.gaming.testing.$levelPackage.*") }
        if (bindsPorts) usesService(gameServerPortsLock)
    }

registerLevelTest("componentTest", "component", bindsPorts = false, "Level 2 - isolated component / business-logic tests.")
registerLevelTest("integrationTest", "integration", bindsPorts = true, "Level 3 - integration & external-interface tests.")
registerLevelTest("deterministicTest", "deterministic", bindsPorts = false, "Level 4a - deterministic pure-logic / input-output tests.")
registerLevelTest("e2eTest", "e2e", bindsPorts = true, "Level 4b - end-to-end system-integration tests.")
registerLevelTest("nonfunctionalTest", "nonfunctional", bindsPorts = true, "Level 4c - non-functional (scalability / robustness) tests.")

dokka {
    moduleName.set("GameTools")
    dokkaSourceSets.main {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/SpartanLabsGaming/MyGameTools/blob/master/src/main/kotlin")
            // "view source" links jump to the exact line
            remoteLineSuffix.set("#L")
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    // Build the Maven Central javadoc jar from Dokka output instead of an empty placeholder.
    configure(JavaLibrary(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = true))
    coordinates("io.github.spartanlabsgaming", "GameTools", "3.1.0")

    pom {
        name.set("Game Tools")
        description.set("A set of generic game tools.")
        inceptionYear.set("2026")
        url.set("https://github.com/SpartanLabsGaming/MyGameTools")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("SpaSinghOut")
                name.set("Spartak Singh")
                url.set("https://github.com/SpaSinghOut")
            }
        }
        scm {
            url.set("https://github.com/SpartanLabsGaming/MyGameTools/")
            connection.set("scm:git:git://github.com/SpartanLabsGaming/MyGameTools.git")
            developerConnection.set("scm:git:ssh://git@github.com/SpartanLabsGaming/MyGameTools.git")
        }
    }
}