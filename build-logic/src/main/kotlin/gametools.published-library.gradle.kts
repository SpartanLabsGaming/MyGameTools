// Convention plugin: a GameTools library module that is published to Maven Central. Adds the
// vanniktech publishing setup, the shared POM metadata, and the Dokka javadoc jar on top of
// `gametools.kotlin-library`. Each module still declares its own `coordinates(...)` and its
// POM name / description.

import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("gametools.kotlin-library")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    // Build the Maven Central javadoc jar from this module's Dokka output rather than an
    // empty placeholder.
    configure(JavaLibrary(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = true))

    pom {
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

// Dokka "view source" links are configured per module (each has a different path on GitHub,
// and a source-less module like the umbrella has no `main` source set to configure).
