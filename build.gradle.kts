// The root project holds no source. It exists to aggregate the modules' API documentation
// into one Dokka publication - every other concern lives in a module build file or in a
// `build-logic` convention plugin. See docs/module-split-plan.md.

plugins {
    // issue #40 (javadoc half): hoist KGP + the Kotlin serialization plugin onto the root's
    // plugin classpath so every subproject shares ONE plugin classloader with Dokka. Without
    // this, Dokka (resolved for the root from settings pluginManagement) sits in the root
    // classloader while KGP (applied to modules by the build-logic convention plugin) sits in
    // per-module child classloaders; Dokka then can't load KotlinBasePlugin and every module's
    // dokkaGeneratePublicationHtml renders an empty site. Gradle #25616 / #35117; Dokka prints
    // this exact remedy in its own warning. The root has no source, so `apply false`.
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("org.jetbrains.dokka")
}

dependencies {
    dokka(project(":gametools-core"))
    dokka(project(":gametools-net"))
}

dokka {
    moduleName.set("GameTools")
}
