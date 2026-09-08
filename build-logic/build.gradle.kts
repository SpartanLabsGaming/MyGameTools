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
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.2.0")
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.36.0")
    implementation("org.jetbrains.dokka:org.jetbrains.dokka.gradle.plugin:2.2.0")
}
