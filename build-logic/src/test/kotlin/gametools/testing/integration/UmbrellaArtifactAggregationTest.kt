package gametools.testing.integration

// 3. Utility / Catch-all
// 3.1 Java Standard library
import java.io.File
import java.util.zip.ZipFile
// 4. Programming Infrastructure and Support
// 4.3 Testing
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Level 3 (integration) TestKit check that locks down issue #40: the source-less
 * `gametools` umbrella must publish documentation artifacts that aggregate the two
 * re-exported modules rather than empty archives, and every module's own `-javadoc.jar`
 * must render real API pages (the repo-wide Dokka `KotlinBasePlugin` fix).
 *
 * Runs one nested Gradle build against the real repository in [buildUmbrellaArtifacts] and
 * inspects the resulting jars under each module's `build/libs`. The `verifyUmbrella` guard
 * tasks fail the build on regression, so a mis-wired aggregation makes [buildUmbrellaArtifacts]
 * throw before any `@Test` runs.
 */
class UmbrellaArtifactAggregationTest {

    companion object {
        private val repoRoot = File(System.getProperty("gametools.repoRoot"))
        private lateinit var result: BuildResult

        @JvmStatic
        @BeforeAll
        fun buildUmbrellaArtifacts() {
            result = GradleRunner.create()
                .withProjectDir(repoRoot)
                .withArguments(
                    ":gametools:sourcesJar",
                    ":gametools:dokkaJavadocJar",
                    ":gametools-core:dokkaJavadocJar",
                    ":gametools-net:dokkaJavadocJar",
                    ":gametools:verifyUmbrellaSourcesJar",
                    ":gametools:verifyUmbrellaJavadocJar",
                    "--stacktrace",
                )
                .forwardOutput()
                .build()
        }

        /** Entry names inside the single `<module>/build/libs/<module>-*-<classifier>.jar`. */
        private fun jarEntries(moduleDir: String, jarPrefix: String, classifier: String): List<String> {
            val libs = repoRoot.resolve("$moduleDir/build/libs")
            val jar = libs.listFiles { f -> f.name.startsWith(jarPrefix) && f.name.endsWith("-$classifier.jar") }
                ?.singleOrNull()
                ?: error("expected exactly one $jarPrefix*-$classifier.jar under $libs, found ${libs.list()?.toList()}")
            return ZipFile(jar).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        }

        fun umbrellaSourcesEntries(): List<String> = jarEntries("gametools", "gametools", "sources")
        fun umbrellaJavadocEntries(): List<String> = jarEntries("gametools", "gametools", "javadoc")
        fun coreJavadocEntries(): List<String> = jarEntries("gametools-core", "gametools-core", "javadoc")
        fun netJavadocEntries(): List<String> = jarEntries("gametools-net", "gametools-net", "javadoc")
    }

    @Test
    fun sourcesJarBundlesKotlinFromBothModules() {
        val kt = umbrellaSourcesEntries().filter { it.endsWith(".kt") }
        assertTrue(kt.any { it.contains("gameobjects") }, "no gametools-core (.../gameobjects/...) sources in $kt")
        assertTrue(kt.any { it.contains("networking") }, "no gametools-net (.../networking/...) sources in $kt")
        assertTrue(kt.size >= 2, "expected the union of both modules' sources, got ${kt.size}: $kt")
    }

    @Test
    fun sourcesJarHasExactlyOneManifest() {
        val entries = umbrellaSourcesEntries()
        val manifests = entries.filter { it == "META-INF/MANIFEST.MF" }
        assertEquals(1, manifests.size, "expected exactly one MANIFEST.MF, folded module jars leaked theirs: ${entries.filter { it.startsWith("META-INF/") }}")
    }

    @Test
    fun javadocJarBundlesApiDocsFromBothModules() {
        val html = umbrellaJavadocEntries().filter { it.endsWith(".html") }
        assertTrue(html.any { it == "index.html" }, "no top-level index.html in $html")
        assertTrue(html.any { it.contains("gameobjects") }, "no gametools-core docs (.../gameobjects/...) in the aggregated site")
        assertTrue(html.any { it.contains("networking") }, "no gametools-net docs (.../networking/...) in the aggregated site")
        assertTrue(html.size >= 20, "javadoc jar looks manifest-only / chrome-only: ${html.size} html files")
    }

    @Test
    fun coreModuleDokkaJavadocJarIsNotEmpty() {
        // Direct regression guard for the repo-wide Dokka KotlinBasePlugin fix: reds before
        // javadocJarBundlesApiDocsFromBothModules if module Dokka breaks again, pointing at
        // the real cause rather than the umbrella aggregation.
        val html = coreJavadocEntries().filter { it.endsWith(".html") }
        assertTrue(html.any { it.contains("gameobjects") }, "gametools-core-javadoc.jar has no .../gameobjects/... pages")
        assertTrue(html.size >= 20, "gametools-core-javadoc.jar looks empty: ${html.size} html files")
    }

    @Test
    fun netModuleDokkaJavadocJarIsNotEmpty() {
        // Same guard for the other module - acceptance criterion #2 covers both.
        val html = netJavadocEntries().filter { it.endsWith(".html") }
        assertTrue(html.any { it.contains("networking") }, "gametools-net-javadoc.jar has no .../networking/... pages")
        assertTrue(html.size >= 20, "gametools-net-javadoc.jar looks empty: ${html.size} html files")
    }

    @Test
    fun regressionGuardsSucceedOnTheRealBuild() {
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":gametools:verifyUmbrellaSourcesJar")?.outcome,
            "the sources-jar regression guard did not run / pass on a correct build",
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":gametools:verifyUmbrellaJavadocJar")?.outcome,
            "the javadoc-jar regression guard did not run / pass on a correct build",
        )
    }
}
