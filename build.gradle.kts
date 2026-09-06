// The root project holds no source. It exists to aggregate the modules' API documentation
// into one Dokka publication - every other concern lives in a module build file or in a
// `build-logic` convention plugin. See docs/module-split-plan.md.

plugins {
    id("org.jetbrains.dokka")
}

dependencies {
    dokka(project(":gametools-core"))
    dokka(project(":gametools-net"))
}

dokka {
    moduleName.set("GameTools")
}
