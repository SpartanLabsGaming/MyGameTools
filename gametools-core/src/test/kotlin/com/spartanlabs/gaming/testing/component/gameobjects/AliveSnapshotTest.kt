package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.AliveSnapshot
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.Player
import com.spartanlabs.gaming.gameobjects.StatMod
//endregion

//region 2. Intended Function
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
//endregion

/** Covers [AliveSnapshot] capturing an [Alive]'s stats on top of its [ActorSnapshot], and its JSON round trip. */
class AliveSnapshotTest {

    private fun alive(maxHealth: Double = 100.0) = Alive(
        location = Point(3.0, 4.0),
        dimensions = Dimensions(width = 8.0, height = 6.0),
        maxHealth = maxHealth
    )

    @Test
    fun `from captures health, faction, and every combat stat on top of the underlying actor state`() {
        val actor = alive(maxHealth = 120.0).apply {
            health.current = 45.0
            faction = "red"
            damage = ModularStat(7.5)
            attackTime = ModularStat(2.0)
            attackSpeed = ModularStat(150.0)
            attackRange = ModularStat(600.0)
            evasion = ModularStat(0.3)
            destination = Point(30.0, 4.0)
        }

        val snapshot = AliveSnapshot from actor

        assertEquals(45.0, snapshot.health.value)
        assertEquals(120.0, snapshot.health.maxValue)
        assertEquals("red", snapshot.faction)
        assertEquals(7.5, snapshot.damage)
        assertEquals(2.0, snapshot.attackTime)
        assertEquals(150.0, snapshot.attackSpeed)
        assertEquals(600.0, snapshot.attackRange)
        assertEquals(0.3, snapshot.evasion)
        assertEquals(30.0, snapshot.actor.destination.x)
        assertEquals(3.0, snapshot.actor.visibleObject.gameObject.location.x)
    }

    @Test
    fun `a combat stat is captured with its stat mods folded into the value`() {
        val actor = alive().apply {
            damage = ModularStat(base = 10.0)
            damage.applyMod(StatMod("rage", 0.5)) // +50% -> effective 15.0
        }

        assertEquals(15.0, (AliveSnapshot from actor).damage)
    }

    @Test
    fun `the default combat stats match Alive's own defaults`() {
        val snapshot = AliveSnapshot from alive()

        assertEquals(10.0, snapshot.damage)
        assertEquals(1.7, snapshot.attackTime)
        assertEquals(100.0, snapshot.attackSpeed)
        assertEquals(750.0, snapshot.attackRange)
        assertEquals(0.0, snapshot.evasion)
    }

    @Test
    fun `ownerName is null when unowned and the player name when owned`() {
        val actor = alive()

        assertNull((AliveSnapshot from actor).ownerName)

        Player("alice").own(actor)
        assertEquals("alice", (AliveSnapshot from actor).ownerName)
    }

    @Test
    fun `the snapshot survives a JSON round trip unchanged`() {
        val snapshot = AliveSnapshot from alive().apply { faction = "blue" }

        val restored = Json.decodeFromString<AliveSnapshot>(Json.encodeToString(snapshot))

        assertEquals(snapshot, restored)
    }
}
