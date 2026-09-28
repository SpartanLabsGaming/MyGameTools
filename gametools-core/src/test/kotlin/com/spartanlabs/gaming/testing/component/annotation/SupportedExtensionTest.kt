package com.spartanlabs.gaming.testing.component.annotation

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.SupportedExtension
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** A minimal fixture [SupportedExtension] can be applied to - top-level so [SupportedMarkedTypeAlias] can alias it (a typealias must be top-level). Named per-file so it does not collide with `ExperimentalGameToolsApiTest`'s own fixture of the same shape in this package. */
@SupportedExtension
private class SupportedMarkedClass

/** [SupportedExtension] applied to a type alias. */
@SupportedExtension
private typealias SupportedMarkedTypeAlias = SupportedMarkedClass

/**
 * Covers [SupportedExtension]'s five-target compile shape and its runtime-observable
 * meta-annotation facts - purely documentary, no `@RequiresOptIn` gate to verify.
 */
class SupportedExtensionTest {

    @SupportedExtension
    private fun markedFunction(): Int = 1

    @SupportedExtension
    private val markedProperty: Int = 2

    private class HasMarkedSecondaryConstructor(val value: Int) {
        @SupportedExtension
        constructor() : this(0)
    }

    @Test
    fun `it can be applied to a class, function, property, constructor, and type alias`() {
        val fromClass = SupportedMarkedClass()
        val fromFunction = markedFunction()
        val fromProperty = markedProperty
        val fromConstructor = HasMarkedSecondaryConstructor()
        val fromTypeAlias: SupportedMarkedTypeAlias = SupportedMarkedClass()

        assertEquals(SupportedMarkedClass::class, fromClass::class)
        assertEquals(1, fromFunction)
        assertEquals(2, fromProperty)
        assertEquals(0, fromConstructor.value)
        assertEquals(SupportedMarkedClass::class, fromTypeAlias::class)
    }

    @Test
    fun `it carries @MustBeDocumented, observable via the compiled JVM annotation`() {
        assertTrue(SupportedExtension::class.java.isAnnotationPresent(java.lang.annotation.Documented::class.java))
    }

    @SupportedExtension
    private class RuntimeVisibilityFixture

    @Test
    fun `usage is invisible at runtime (BINARY retention)`() {
        assertFalse(RuntimeVisibilityFixture::class.java.isAnnotationPresent(SupportedExtension::class.java))
    }

    @Test
    fun `it declares no members`() {
        assertTrue(SupportedExtension::class.java.declaredMethods.isEmpty())
    }
}
