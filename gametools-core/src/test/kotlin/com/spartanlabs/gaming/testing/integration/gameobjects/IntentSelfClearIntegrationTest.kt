package com.spartanlabs.gaming.testing.integration.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.combat.Alive
import com.spartanlabs.gaming.gameobjects.combat.AttackIntent
import com.spartanlabs.gaming.gameobjects.combat.Idle
import com.spartanlabs.gaming.gameobjects.combat.ModularStat
import com.spartanlabs.gaming.gameobjects.World
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
 * Level 3 - integration between [com.spartanlabs.gaming.gameobjects.combat.Intent], [Alive], and the
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

    @Test
    fun `an attacker's intent also clears when its target leaves the world alive`() {
        val events = mutableListOf<GameEvent>().also { log -> world.events.subscribe { log += it } }
        val attacker = alive(0.0)
        val target = alive(5_000.0) // out of range, so no swing lands before it is removed

        attacker.issue(AttackIntent(target))
        world.tick()

        world.removeList += target
        world.tick() // target drops out of the world, still alive
        world.tick() // attacker notices and self-clears

        assertTrue(target.isAlive)
        assertSame(Idle, attacker.intent, "leaving the world alive must self-clear the intent too, not just death")
        assertEquals(1, events.count { it is GameEvent.IntentCleared })
    }
}
