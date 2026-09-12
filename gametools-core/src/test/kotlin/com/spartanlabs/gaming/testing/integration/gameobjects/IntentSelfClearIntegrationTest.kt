package com.spartanlabs.gaming.testing.integration.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.AttackIntent
import com.spartanlabs.gaming.gameobjects.Idle
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
//endregion

/**
 * Level 3 - integration between [com.spartanlabs.gaming.gameobjects.Intent], [Alive], and the
 * [World]'s [com.spartanlabs.gaming.event.EventBus]: no sockets are involved, but this is the
 * first point where an [AttackIntent]'s self-clear subscription (Decision C) is exercised
 * through a real [World.tick] loop rather than direct method calls, the way it runs in
 * production.
 */
class IntentSelfClearIntegrationTest {

    private val world = World(seed = 1L)

    private fun alive(x: Double, maxHealth: Double = 100.0) =
        Alive(Point(x, 0.0), Dimensions(10.0, 10.0), maxHealth).also(world::add)

    @Test
    fun `an attacker's intent returns to Idle by the tick after its target dies, with exactly one IntentCleared`() {
        val events = mutableListOf<GameEvent>().also { log -> world.events.subscribe { log += it } }
        val attacker = alive(0.0).apply { attackSpeed = ModularStat(100_000.0) }
        val target = alive(20.0, maxHealth = 25.0) // within default attackRange

        attacker.issue(AttackIntent(target))
        repeat(20) { world.tick() } // closes to range, swings, and kills the target

        assertFalse(target.isAlive)
        assertSame(Idle, attacker.intent, "no manual intervention should be needed to clear the intent")

        repeat(5) { world.tick() } // further ticks must not re-fire the self-clear
        assertEquals(
            1,
            events.count { it is GameEvent.IntentCleared },
            "IntentCleared should fire once, not once per tick it stays cleared"
        )
    }
}
