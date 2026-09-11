// Convention plugin: a plain GameTools library module - Kotlin/JVM, serialization, the shared
// dependency set, and the five-level test-task wiring. Applied by `gametools.published-library`
// and usable on its own for a module that is not published.

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(23)
}

dependencies {
    // Spartan Laboratories Tools
    api("io.github.spartanlaboratories:WebTools:2.0.0c")
    // Direct dependency for the Color class; WebTools already brings the same version.
    api("io.github.spartanlaboratories:GeneralTools:2.2.0")

    // Serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging - modules only ever bind to the slf4j facade, so consumers stay free to pick
    // their own implementation.
    api("org.slf4j:slf4j-api:2.0.16")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // Tests supply the logback implementation the facade binds to, so the modules' structured
    // logging is actually exercised and visible.
    testImplementation("ch.qos.logback:logback-classic:1.5.18")
}

// A GameServer binds the fixed common UDP port, so no two test tasks that start one may run at
// the same time (Gradle runs independent tasks in parallel, including across modules). Every
// port-binding test task declares this single-permit service so Gradle serializes them.
val gameServerPortsLock: Provider<GameServerPortsLock> =
    gradle.sharedServices.registerIfAbsent("gameServerPortsLock", GameServerPortsLock::class) {
        maxParallelUsages = 1
    }

tasks.test {
    useJUnitPlatform()
    usesService(gameServerPortsLock)
}

// Tests are split by level into com.spartanlabs.gaming.testing.<level> packages (see
// ~/.claude/CLAUDE.md - Testing 5-Level Hierarchy). `test` runs every level; these per-level
// tasks let CI gate them independently and span every module. A level with no tests in this
// module is a green no-op.
fun registerLevelTest(name: String, levelPackage: String, bindsPorts: Boolean, summary: String) =
    tasks.register<Test>(name) {
        group = "verification"
        description = summary
        val testSources = sourceSets.test.get()
        testClassesDirs = testSources.output.classesDirs
        classpath = testSources.runtimeClasspath
        useJUnitPlatform()
        filter {
            includeTestsMatching("com.spartanlabs.gaming.testing.$levelPackage.*")
            isFailOnNoMatchingTests = false
        }
        if (bindsPorts) usesService(gameServerPortsLock)
    }

registerLevelTest("componentTest", "component", bindsPorts = false, "Level 2 - isolated component / business-logic tests.")
registerLevelTest("integrationTest", "integration", bindsPorts = true, "Level 3 - integration & external-interface tests.")
registerLevelTest("deterministicTest", "deterministic", bindsPorts = false, "Level 4a - deterministic pure-logic / input-output tests.")
registerLevelTest("e2eTest", "e2e", bindsPorts = true, "Level 4b - end-to-end system-integration tests.")
registerLevelTest("nonfunctionalTest", "nonfunctional", bindsPorts = true, "Level 4c - non-functional (scalability / robustness) tests.")
