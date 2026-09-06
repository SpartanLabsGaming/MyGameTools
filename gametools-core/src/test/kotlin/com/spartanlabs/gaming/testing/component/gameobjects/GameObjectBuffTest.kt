package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Buff
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.StatMod
import com.spartanlabs.gaming.gameobjects.VisibleObject
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers the buff machinery on [GameObject]: applying and reverting [StatMod]s through
 * [GameObject.stats], the [GameObject.tick]-driven countdown, and [GameObject.removeBuff] /
 * [GameObject.dispel].
 */
class GameObjectBuffTest {

    /** A buff that records the target and count of each lifecycle callback. */
    private class SpyBuff(name: String, durationTicks: Int) : Buff(name, durationTicks) {
        var applied = 0
        var ticks = 0
        var expired = 0
        override fun onApplied(target: GameObject) { applied++ }
        override fun onTick(target: GameObject) { ticks++ }
        override fun onExpired(target: GameObject) { expired++ }
    }

    private fun actor() = Actor(location = Point(0.0, 0.0)).apply {
        movement = Movement.Directional
        angle = 0
    }

    @Test
    fun `applying a buff modifies the named stat and expiry restores it`() {
        val a = actor()
        a.applyBuff(Buff("haste", durationTicks = 2, statMods = mapOf("speed" to StatMod("haste", 0.5))))

        assertEquals(15.0, a.speed.value, absoluteTolerance = 1e-9)

        a.tick() // duration 2 -> 1
        a.tick() // duration 1 -> 0, pruned after onUpdate
        assertEquals(10.0, a.speed.value, absoluteTolerance = 1e-9, "the mod is gone once the buff expires")
        assertTrue(a.buffs.isEmpty())
    }

    @Test
    fun `a buff with duration n is in force for exactly n ticks`() {
        val a = actor()
        a.applyBuff(Buff("slow", durationTicks = 3, statMods = mapOf("speed" to StatMod("slow", -0.9))))

        repeat(3) {
            assertEquals(1.0, a.speed.value, absoluteTolerance = 1e-9)
            a.tick()
        }
        assertEquals(10.0, a.speed.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun `an indefinite buff persists across ticks until it is removed by hand`() {
        val a = actor()
        val aura = Buff("aura", durationTicks = -1, statMods = mapOf("speed" to StatMod("aura", 1.0)))
        a.applyBuff(aura)

        repeat(50) { a.tick() }
        assertEquals(20.0, a.speed.value, absoluteTolerance = 1e-9)

        a.removeBuff(aura)
        assertEquals(10.0, a.speed.value, absoluteTolerance = 1e-9)
    }

    @Test
    fun `dispel removes every buff sharing a name and reports the count`() {
        val a = actor()
        a.applyBuff(Buff("poison", durationTicks = -1, statMods = mapOf("speed" to StatMod("poison-1", -0.1))))
        a.applyBuff(Buff("poison", durationTicks = -1, statMods = mapOf("speed" to StatMod("poison-2", -0.1))))
        a.applyBuff(Buff("blessing", durationTicks = -1, statMods = mapOf("speed" to StatMod("blessing", 0.2))))

        val removed = a.dispel("poison")

        assertEquals(2, removed)
        assertEquals(listOf("blessing"), a.buffs.map { it.name })
        assertEquals(12.0, a.speed.value, absoluteTolerance = 1e-9, "only the blessing mod is left")
    }

    @Test
    fun `a mod aimed at a stat the object does not expose is skipped, leaving the rest applied`() {
        val a = actor()
        a.applyBuff(
            Buff(
                "mixed",
                durationTicks = -1,
                statMods = mapOf(
                    "speed" to StatMod("mixed-speed", 0.5),
                    "nonsense" to StatMod("mixed-nonsense", 5.0)
                )
            )
        )

        assertEquals(15.0, a.speed.value, absoluteTolerance = 1e-9)
        assertTrue(a.buffs.single().name == "mixed", "the buff is still attached despite the skipped mod")
    }

    @Test
    fun `applying a buff to a stat-less object is harmless`() {
        val plain = VisibleObject(location = Point(0.0, 0.0))
        plain.applyBuff(Buff("odd", durationTicks = -1, statMods = mapOf("speed" to StatMod("odd", 1.0))))

        assertEquals(listOf("odd"), plain.buffs.map { it.name })
    }

    @Test
    fun `lifecycle hooks fire once on apply, once per tick, and once on expiry`() {
        val a = actor()
        val spy = SpyBuff("spy", durationTicks = 3)
        a.applyBuff(spy)

        assertEquals(1, spy.applied)

        repeat(3) { a.tick() }

        assertEquals(3, spy.ticks)
        assertEquals(1, spy.expired)
        assertFalse(spy in a.buffs)
    }

    @Test
    fun `removeBuff on a buff that is not attached does nothing`() {
        val a = actor()
        val stranger = Buff("stranger", durationTicks = 5, statMods = mapOf("speed" to StatMod("stranger", 1.0)))

        a.removeBuff(stranger)

        assertEquals(10.0, a.speed.value, absoluteTolerance = 1e-9)
        assertTrue(a.buffs.isEmpty())
    }

    @Test
    fun `the exposed buff list is a detached snapshot of the live buffs`() {
        val a = actor()
        val buff = Buff("x", durationTicks = 5)
        a.applyBuff(buff)

        val firstView = a.buffs
        a.dispel("x")

        assertSame(buff, firstView.single(), "the earlier view still holds the buff instance")
        assertTrue(a.buffs.isEmpty(), "a fresh view reflects the removal")
    }
}
