package com.spartanlabs.gaming.annotation

/**
 * Marks a public declaration as **Experimental**: a seam whose shape has not yet been proven by a
 * real consumer. Unlike [SupportedExtension], a declaration tagged [ExperimentalGameToolsApi] may
 * change incompatibly in a Feature release, not only a Major one - see `CONTRIBUTING.md`
 * §Versioning - until it graduates to Stable Core (untagged) or [SupportedExtension].
 *
 * Gated at [RequiresOptIn.Level.ERROR] - deliberate opt-in is the whole point of this tier (this
 * is a small, deliberate seam an unintentional touch should fail to compile against, not a vast,
 * everyday surface a consumer is expected to brush against constantly). A consumer opts in with
 * `@OptIn(ExperimentalGameToolsApi::class)` at the narrowest scope that needs it, or the
 * `-opt-in=com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi` compiler flag for a whole
 * module.
 *
 * Never deleted, even once nothing in this library still uses it: a consumer's own
 * `@OptIn(ExperimentalGameToolsApi::class)` would fail to compile with an unresolved reference if
 * this class were removed. It stays live as the tier's permanent marker for the next Experimental
 * seam.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR, AnnotationTarget.TYPEALIAS,
)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This declaration is Experimental: it may change incompatibly in a Feature release until it graduates to Stable Core or Supported Extension.",
)
annotation class ExperimentalGameToolsApi
