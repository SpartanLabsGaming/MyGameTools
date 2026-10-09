package com.spartanlabs.gaming.testing.component.annotation

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** A minimal fixture [ExperimentalGameToolsApi] can be applied to - top-level so [ExperimentalMarkedTypeAlias] can alias it (a typealias must be top-level). Named per-file so it does not collide with `SupportedExtensionTest`'s own fixture of the same shape in this package. */
@ExperimentalGameToolsApi
private class ExperimentalMarkedClass

/** [ExperimentalGameToolsApi] applied to a type alias. */
@ExperimentalGameToolsApi
private typealias ExperimentalMarkedTypeAlias = ExperimentalMarkedClass

/**
 * Covers [ExperimentalGameToolsApi]'s five-target compile shape and its runtime-observable
 * meta-annotation facts. Its own `@RequiresOptIn(ERROR)` gate is a compile-time fact, verified
 * manually (`docs/plans/87-world-systems/76-world-system-core/plan.md` §6 Level 1), not asserted
 * here - `RequiresOptIn` is itself `BINARY`-retained and invisible to runtime reflection.
 */
class ExperimentalGameToolsApiTest {

    @ExperimentalGameToolsApi
    private fun markedFunction(): Int = 1

    @ExperimentalGameToolsApi
    private val markedProperty: Int = 2

    private class HasMarkedSecondaryConstructor(val value: Int) {
        @ExperimentalGameToolsApi
        constructor() : this(0)
    }

    @Test
    fun `it can be applied to a class, function, property, constructor, and type alias`() {
        val fromClass = ExperimentalMarkedClass()
        val fromFunction = markedFunction()
        val fromProperty = markedProperty
        val fromConstructor = HasMarkedSecondaryConstructor()
        val fromTypeAlias: ExperimentalMarkedTypeAlias = ExperimentalMarkedClass()

        assertEquals(ExperimentalMarkedClass::class, fromClass::class)
        assertEquals(1, fromFunction)
        assertEquals(2, fromProperty)
        assertEquals(0, fromConstructor.value)
        assertEquals(ExperimentalMarkedClass::class, fromTypeAlias::class)
    }

    @Test
    fun `it carries @MustBeDocumented, observable via the compiled JVM annotation`() {
        // Kotlin's @MustBeDocumented compiles to the JVM's own java.lang.annotation.Documented -
        // that is the certain, toolchain-independent fact to assert here.
        assertTrue(ExperimentalGameToolsApi::class.java.isAnnotationPresent(java.lang.annotation.Documented::class.java))
    }

    @ExperimentalGameToolsApi
    private class RuntimeVisibilityFixture

    @Test
    fun `usage is invisible at runtime (BINARY retention)`() {
        assertFalse(RuntimeVisibilityFixture::class.java.isAnnotationPresent(ExperimentalGameToolsApi::class.java))
    }

    @Test
    fun `it declares no members`() {
        assertTrue(ExperimentalGameToolsApi::class.java.declaredMethods.isEmpty())
    }
}
