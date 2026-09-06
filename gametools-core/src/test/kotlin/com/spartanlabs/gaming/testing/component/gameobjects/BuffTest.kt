package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Buff
import com.spartanlabs.gaming.gameobjects.BuffSnapshot
import com.spartanlabs.gaming.gameobjects.CoreCapability
import com.spartanlabs.gaming.gameobjects.StatMod
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [Buff]'s expiry predicate and the [BuffSnapshot] projection taken for the wire. */
class BuffTest {

    @Test
    fun `a buff is expired only when its remaining duration is exactly zero`() {
        assertFalse(Buff("a", durationTicks = 5).isExpired)
        assertFalse(Buff("a", durationTicks = 1).isExpired)
        assertTrue(Buff("a", durationTicks = 0).isExpired)
    }

    @Test
    fun `a negative duration is never considered expired`() {
        assertFalse(Buff("permanent", durationTicks = -1).isExpired)
        assertFalse(Buff("permanent", durationTicks = -999).isExpired)
    }

    @Test
    fun `a snapshot captures name, remaining duration, and suppressed capability ids`() {
        val buff = Buff(
            name = "stun",
            durationTicks = 4,
            statMods = mapOf("speed" to StatMod("stun-slow", -0.5)),
            suppressedCapabilities = setOf(CoreCapability.MOVE, CoreCapability.ATTACK)
        )

        val snapshot = BuffSnapshot from buff

        assertEquals("stun", snapshot.name)
        assertEquals(4, snapshot.durationTicks)
        assertEquals(setOf("move", "attack"), snapshot.suppressedCapabilities.toSet())
    }

    @Test
    fun `an indefinite buff keeps its negative duration in its snapshot`() {
        assertEquals(-1, (BuffSnapshot from Buff("aura", durationTicks = -1)).durationTicks)
    }
}
