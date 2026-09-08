package gametools.testing.integration

// 3. Utility / Catch-all
// 3.1 Java Standard library
import java.io.File
// 4. Programming Infrastructure and Support
// 4.3 Testing
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Level 3 (integration) TestKit check that guards the guards: the `verifyUmbrellaSourcesJar`
 * and `verifyUmbrellaJavadocJar` presence-checks from `gametools/build.gradle.kts` must
 * *fail* when nothing meaningful has been aggregated (issue #40, the negative direction).
 *
 * Each case builds a throwaway project in a [TempDir] - not the real build - whose archive is
 * deliberately empty / chrome-only and whose verification task carries a copy of the
 * production `check(...)` contract. Copied rather than imported: the convention-plugin build
 * logic is not on the test classpath as a library. [copiedContractStillMatchesProduction]
 * fails if the real task drifts from the copies below.
 */
class UmbrellaVerifyTaskContractTest {

    private companion object {
        private val repoRoot = File(System.getProperty("gametools.repoRoot"))

        /**
         * Contract substrings that must appear verbatim in both the production
         * `gametools/build.gradle.kts` guards and the copied task bodies in this file.
         */
        private val contractSubstrings = listOf(
            "check(kt.size >= 2)",
            "Umbrella sources jar has too few .kt files",
            "Umbrella sources jar is missing gametools-core sources",
            "Umbrella sources jar is missing gametools-net sources",
            "check(html.size >= 20)",
            "Umbrella javadoc jar has only ",
            "Umbrella javadoc jar is missing gametools-core docs",
            "Umbrella javadoc jar is missing gametools-net docs",
        )
    }

    @Test
    fun presenceCheckFailsWhenNothingIsAggregated(@TempDir tmp: File) {
        //language=kotlin
        File(tmp, "settings.gradle.kts").writeText("""rootProject.name = "throwaway"""" + "\n")
        //language=kotlin
        File(tmp, "build.gradle.kts").writeText(
            """
            import java.util.zip.ZipFile

            plugins { java }

            java { withSourcesJar() }

            tasks.register("verifyUmbrellaSourcesJar") {
                val jar = tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile }
                inputs.file(jar)
                doLast {
                    val kt = ZipFile(jar.get().asFile).use { zip ->
                        zip.entries().asSequence().map { it.name }.filter { it.endsWith(".kt") }.toList()
                    }
                    check(kt.size >= 2) { "Umbrella sources jar has too few .kt files (${'$'}{kt.size}): ${'$'}{jar.get().asFile}" }
                    check(kt.any { it.contains("gameobjects") }) { "Umbrella sources jar is missing gametools-core sources" }
                    check(kt.any { it.contains("networking") }) { "Umbrella sources jar is missing gametools-net sources" }
                }
            }
            """.trimIndent() + "\n",
        )

        val result = GradleRunner.create()
            .withProjectDir(tmp)
            .withArguments("verifyUmbrellaSourcesJar", "--stacktrace")
            .buildAndFail()

        assertTrue(
            result.output.contains("Umbrella sources jar has too few .kt files"),
            "guard failed for the wrong reason:\n${result.output}",
        )
    }

    @Test
    fun presenceCheckFailsWhenJavadocJarIsChromeOnly(@TempDir tmp: File) {
        // A real empty Dokka jar carries only index.html + navigation.html (+ CSS/JS chrome).
        File(tmp, "site").mkdirs()
        File(tmp, "site/index.html").writeText("<html></html>\n")
        File(tmp, "site/navigation.html").writeText("<html></html>\n")

        //language=kotlin
        File(tmp, "settings.gradle.kts").writeText("""rootProject.name = "throwaway"""" + "\n")
        //language=kotlin
        File(tmp, "build.gradle.kts").writeText(
            """
            import java.util.zip.ZipFile

            plugins { base }

            val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
                archiveClassifier.set("javadoc")
                from("site")
            }

            tasks.register("verifyUmbrellaJavadocJar") {
                val jar = dokkaJavadocJar.flatMap { it.archiveFile }
                inputs.file(jar)
                doLast {
                    val html = ZipFile(jar.get().asFile).use { zip ->
                        zip.entries().asSequence().map { it.name }.filter { it.endsWith(".html") }.toList()
                    }
                    check(html.size >= 20) { "Umbrella javadoc jar has only ${'$'}{html.size} html files: ${'$'}{jar.get().asFile}" }
                    check(html.any { it.contains("gameobjects") }) { "Umbrella javadoc jar is missing gametools-core docs" }
                    check(html.any { it.contains("networking") }) { "Umbrella javadoc jar is missing gametools-net docs" }
                }
            }
            """.trimIndent() + "\n",
        )

        val result = GradleRunner.create()
            .withProjectDir(tmp)
            .withArguments("verifyUmbrellaJavadocJar", "--stacktrace")
            .buildAndFail()

        assertTrue(
            result.output.contains("Umbrella javadoc jar has only "),
            "guard failed for the wrong reason:\n${result.output}",
        )
    }

    @Test
    fun copiedContractStillMatchesProduction() {
        val productionScript = repoRoot.resolve("gametools/build.gradle.kts").readText()
        val drifted = contractSubstrings.filterNot(productionScript::contains)
        assertTrue(
            drifted.isEmpty(),
            "gametools/build.gradle.kts no longer contains these guard contract fragments - " +
                "update the copied task bodies in this test to match: $drifted",
        )
    }
}
