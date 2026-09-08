// Umbrella artifact. Carries no source of its own - it re-exports every GameTools module via
// `api`, so a consumer that depends on `io.github.spartanlabsgaming:gametools` gets the whole
// framework on one dependency line, exactly as the pre-split `GameTools` artifact did.

// 3. Utility / Catch-all
// 3.1 Java Standard library
import java.util.zip.ZipFile

plugins {
    id("gametools.published-library")
}

//region issue #40 - the umbrella carries no source of its own, so mirror the api() re-export
// into the documentation artifacts: fold each re-exported module's -sources.jar into the
// umbrella's, and aggregate both modules' Dokka HTML into the umbrella's -javadoc.jar.

// Resolvable-only: carries each re-exported module's published sources archive (its
// `sourcesElements` outgoing variant - the same one that feeds gametools-core-*-sources.jar)
// into the umbrella `sourcesJar` task.
val reexportedModuleSources: Configuration = configurations.create("reexportedModuleSources") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
//endregion

dependencies {
    api(project(":gametools-core"))
    api(project(":gametools-net"))

    //region issue #40 - documentation-artifact aggregation (see the region above / below)
    reexportedModuleSources(project(path = ":gametools-core", configuration = "sourcesElements"))
    reexportedModuleSources(project(path = ":gametools-net", configuration = "sourcesElements"))

    // Dokka v2 folds every module named here into this project's dokkaGeneratePublicationHtml
    // output - the same wiring the root aggregator uses.
    dokka(project(":gametools-core"))
    dokka(project(":gametools-net"))
    //endregion
}

//region issue #40 - populate the umbrella -sources.jar / -javadoc.jar

tasks.named<Jar>("sourcesJar") {
    // `dependsOn` the configuration so each module's own `sourcesJar` runs first; `zipTree`
    // wrapped in a provider does not carry that task dependency on its own.
    dependsOn(reexportedModuleSources)
    from(provider { reexportedModuleSources.files.map(::zipTree) })
    exclude("META-INF/**")            // drop each module jar's MANIFEST; keep the umbrella's own
    // Silently drops the 2nd copy on a same-path collision; safe today (the modules share no
    // packages), revisit on a module-boundary move - issue #40 § 6.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// The umbrella has no API of its own; keep it out of its own aggregated site.
dokka {
    dokkaSourceSets.configureEach { suppress.set(true) }
}

// vanniktech 0.36.0 registers the Dokka javadoc jar task as `dokkaJavadocJar`; its task type
// (com.vanniktech.maven.publish.tasks.JavadocJar) extends org.gradle.jvm.tasks.Jar, not the
// narrower org.gradle.api.tasks.bundling.Jar the KTS `Jar` accessor resolves to.
val umbrellaJavadocJar = tasks.named<org.gradle.jvm.tasks.Jar>("dokkaJavadocJar")

// The verification actions read the archives with the JDK's `ZipFile` (not Gradle's `zipTree`)
// and capture only the archive-file `Provider`, so they stay configuration-cache safe - a
// `doLast` that referenced a script-level helper or `project` would fail CC serialization.

val verifyUmbrellaSourcesJar = tasks.register("verifyUmbrellaSourcesJar") {
    description = "Fails if the umbrella -sources.jar has regressed to empty (issue #40)."
    group = "verification"
    val jar = tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val kt = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.filter { it.endsWith(".kt") }.toList()
        }
        // >= 2: at least one source file from each of the two re-exported modules.
        check(kt.size >= 2) { "Umbrella sources jar has too few .kt files (${kt.size}): ${jar.get().asFile}" }
        check(kt.any { it.contains("gameobjects") }) { "Umbrella sources jar is missing gametools-core sources" }
        check(kt.any { it.contains("networking") }) { "Umbrella sources jar is missing gametools-net sources" }
        logger.lifecycle("Umbrella sources jar OK: ${kt.size} .kt files")
    }
}

val verifyUmbrellaJavadocJar = tasks.register("verifyUmbrellaJavadocJar") {
    description = "Fails if the umbrella -javadoc.jar is manifest-only / missing a module (issue #40)."
    group = "verification"
    val jar = umbrellaJavadocJar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val html = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.filter { it.endsWith(".html") }.toList()
        }
        // >= 20: an empty Dokka jar is just index.html + navigation.html; a real two-module
        // site is dozens of package/type pages, so 20 clears "chrome-only" with wide margin.
        check(html.size >= 20) { "Umbrella javadoc jar has only ${html.size} html files: ${jar.get().asFile}" }
        check(html.any { it.contains("gameobjects") }) { "Umbrella javadoc jar is missing gametools-core docs" }
        check(html.any { it.contains("networking") }) { "Umbrella javadoc jar is missing gametools-net docs" }
        logger.lifecycle("Umbrella javadoc jar OK: ${html.size} html files")
    }
}

tasks.named("check") { dependsOn(verifyUmbrellaSourcesJar, verifyUmbrellaJavadocJar) }
//endregion

mavenPublishing {
    coordinates("io.github.spartanlabsgaming", "gametools", "5.0.0")
    pom {
        name.set("GameTools")
        description.set("Umbrella artifact re-exporting every GameTools module (gametools-core, gametools-net).")
    }
}
