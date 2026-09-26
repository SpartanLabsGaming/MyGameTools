// 3. Utility / Catch-all
// 3.1 Java Standard library
import java.time.Duration

plugins {
    // `kotlin-dsl` is safe here: this is a separate included build, compiled on the build
    // script classpath. gradleApi()'s bundled slf4j provider never reaches a library module's
    // main or test runtime, so it cannot shadow logback the way it would if applied to the
    // library itself (see the root build's history / docs/module-split-plan.md §6).
    `kotlin-dsl`
}

dependencies {
    // Plugin marker artifacts, so the convention plugins can apply these by id with no
    // version. Kotlin and its serialization plugin stay in lockstep on one version.
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.2.0")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.4.20")
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.36.0")
    implementation("org.jetbrains.dokka:org.jetbrains.dokka.gradle.plugin:2.2.0")

    // Test - JUnit 5 + Gradle TestKit. Literal coordinate strings: this repo has no version
    // catalog (see the four plugin-marker lines above). `kotlin-dsl` already brings `java` /
    // `java-gradle-plugin`, so the `test` task and `src/test/kotlin` source set exist as soon
    // as these are on the classpath - no extra source-set wiring needed.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

//region issue #40 - Level-3 (testing.integration) coverage for the convention plugins
// Drives the real parent build via Gradle TestKit. Kept off the default `check` / `build`
// graph because each test spawns a nested Gradle invocation.
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Level 3 - TestKit checks on the build-logic convention plugins."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("gametools.testing.integration.*")
        // Tolerate zero matches: mirrors the modules' per-level tasks, where a module with no
        // tests at this level is a green no-op rather than a failure.
        isFailOnNoMatchingTests = false
    }
    // build-logic's projectDir is <repo>/build-logic, so ".." is the repo root the nested
    // build runs in.
    systemProperty("gametools.repoRoot", layout.projectDirectory.dir("..").asFile.absolutePath)
    // Sized for a cold nested Gradle build + a full two-module Dokka render on a CI runner.
    timeout.set(Duration.ofMinutes(15))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // The TestKit classes spawn nested Gradle builds - too heavy for `./gradlew build` (which
    // runs `test` via `check`). They run only through `:build-logic:integrationTest`.
    filter {
        excludeTestsMatching("gametools.testing.integration.*")
        // Every test class currently lives in that package, so after the exclusion `test` has
        // nothing to run - that empty match set must be a no-op, not a build failure.
        isFailOnNoMatchingTests = false
    }
}
//endregion
