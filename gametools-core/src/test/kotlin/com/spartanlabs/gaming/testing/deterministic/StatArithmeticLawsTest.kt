package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.CombinedStat
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.compareTo
import com.spartanlabs.gaming.gameobjects.minus
import com.spartanlabs.gaming.gameobjects.plus
import com.spartanlabs.gaming.gameobjects.times
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.math.sign
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. Exercises the pure arithmetic and comparison operators on
 * [ModularStat] and [CombinedStat] as algebraic laws (commutativity, identity, order
 * consistency, referential transparency) swept over a grid of inputs, rather than as
 * individual worked examples the way [com.spartanlabs.gaming.testing.component.gameobjects]
 * does.
 */
class StatArithmeticLawsTest {

    private val values = listOf(-40.0, -1.5, 0.0, 0.25, 3.0, 17.0, 250.0)

    @Test
    fun `adding a Double to a ModularStat is commutative and equals adding the raw values`() {
        for (statValue in values) for (addend in values) {
            val stat = ModularStat(statValue)
            assertEquals(statValue + addend, stat + addend, "stat + double")
            assertEquals(addend + stat, stat + addend, "double + stat should match stat + double")
        }
    }

    @Test
    fun `zero is the additive identity for a ModularStat`() {
        for (statValue in values) {
            assertEquals(statValue, ModularStat(statValue) + 0.0)
            assertEquals(statValue, 0.0 + ModularStat(statValue))
        }
    }

    @Test
    fun `comparing a ModularStat agrees in sign with comparing the underlying values`() {
        for (left in values) for (right in values) {
            val expected = (left - right).sign
            assertEquals(expected, (ModularStat(left) compareTo right).toDouble().sign, "stat vs double")
            assertEquals(expected, (left compareTo ModularStat(right)).toDouble().sign, "double vs stat")
            assertEquals(
                expected,
                (ModularStat(left) compareTo ModularStat(right)).toDouble().sign,
                "stat vs stat"
            )
        }
    }

    @Test
    fun `ModularStat comparison induces a transitive total order over a sample`() {
        val stats = values.sorted().map(::ModularStat)
        for (i in stats.indices) for (j in i + 1 until stats.size) {
            assertTrue(stats[i] < stats[j], "${stats[i].value} should order before ${stats[j].value}")
        }
    }

    @Test
    fun `CombinedStat arithmetic against a Double operates on its current amount`() {
        for (current in values.filter { it != 0.0 }) for (operand in values) {
            val stat = CombinedStat(startingValue = current, maxValue = 1_000.0)
            assertEquals(current + operand, stat + operand)
            assertEquals(current - operand, stat - operand)
            assertEquals(current * operand, stat * operand)
            assertEquals(current / operand, stat / operand)
        }
    }

    @Test
    fun `a Double on the left of a CombinedStat matches operating on its current amount`() {
        for (current in values.filter { it != 0.0 }) for (operand in values) {
            val stat = CombinedStat(startingValue = current, maxValue = 1_000.0)
            assertEquals(operand + current, operand + stat)
            assertEquals(operand - current, operand - stat)
            assertEquals(operand * current, operand * stat)
            assertEquals((operand - current).sign, (operand compareTo stat).toDouble().sign)
        }
    }

    @Test
    fun `the stat operators are referentially transparent - repeated evaluation is identical and leaves the operands unchanged`() {
        val stat = ModularStat(12.0)
        val combined = CombinedStat(startingValue = 30.0, maxValue = 100.0)

        assertEquals(stat + 5.0, stat + 5.0)
        assertEquals(7.0 + combined, 7.0 + combined)

        assertEquals(12.0, stat.value, "operator use must not mutate the ModularStat")
        assertEquals(30.0, combined.current, "operator use must not mutate the CombinedStat")
    }
}
