package com.spartanlabs.gaming.annotation

/**
 * Marks a public declaration as **Supported Extension**: a seam for a likely-but-non-core need
 * - something a fair number of consumers will plausibly want, that is not what the surrounding
 * library exists fundamentally to provide.
 *
 * This tier carries **the same semver guarantee as Stable Core** - the tag marks *purpose*, not
 * a weaker stability promise. A breaking change to a `@SupportedExtension` declaration is a
 * breaking change to the library, governed by the same versioning rules
 * (`CONTRIBUTING.md` §Versioning) as anything else public. Contrast with an
 * [ExperimentalGameToolsApi]-gated seam, whose shape may still change in a Feature release
 * because no real consumer has built against it yet - nothing tagged `@SupportedExtension` is
 * unproven in that sense.
 *
 * Where this tag lands on an `interface`, its shipped default implementation(s) double as the
 * seam's **worked example** of how to extend it: written to be read, not merely to work.
 *
 * Purely documentary - `BINARY` retention, no `@RequiresOptIn` gate, no parameters. It changes
 * nothing about how the annotated declaration compiles or runs; it exists for Dokka, IDE
 * navigation, and a human deciding whether to extend something, not for the compiler to enforce.
 * Modelled on JetBrains' `org.jetbrains.annotations.ApiStatus.NonExtendable` precedent for a
 * documentary stability marker.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR, AnnotationTarget.TYPEALIAS,
)
annotation class SupportedExtension
