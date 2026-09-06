package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.ActorSnapshot
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.AliveSnapshot
import com.spartanlabs.gaming.gameobjects.Buff
import com.spartanlabs.gaming.gameobjects.CoreCapability
import com.spartanlabs.gaming.gameobjects.DrawableSnapshot
import com.spartanlabs.gaming.gameobjects.StatMod
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.VisibleObjectSnapshot
//endregion

//region 2. Intended Function
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.random.Random
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. Treats `DrawableSnapshot from x` together with its
 * polymorphic JSON codec as one pure input-to-output mapping and pins its invariants -
 * round-trip identity, total and most-specific type selection, and deterministic encoding -
 * over a swept/generated set of objects rather than the hand-picked examples in
 * [com.spartanlabs.gaming.testing.component.gameobjects].
 */
class SnapshotRoundTripTest {

    private val random = Random(20260902)

    private fun randomPoint() = Point(random.nextDouble(-500.0, 500.0), random.nextDouble(-500.0, 500.0))
    private fun randomDimensions() = Dimensions(random.nextDouble(1.0, 60.0), random.nextDouble(1.0, 60.0))

    /** A randomised buff, half the time indefinite, half the time suppressing a capability. */
    private fun randomBuff(index: Int) = Buff(
        name = "buff-$index",
        durationTicks = if (random.nextBoolean()) -1 else random.nextInt(1, 20),
        statMods = mapOf("speed" to StatMod("mod-$index", random.nextDouble(-0.5, 0.5))),
        suppressedCapabilities = if (random.nextBoolean()) setOf(CoreCapability.MOVE) else emptySet()
    )

    /** One object of each kind, at randomised position/size/angle, optionally carrying [subObjects]. */
    private fun objectsOfEachKind(subObjects: List<VisibleObject> = emptyList()): List<VisibleObject> {
        fun attach(target: VisibleObject) = target.apply {
            angle = random.nextInt(0, 360)
            this.subObjects += subObjects
            repeat(random.nextInt(0, 3)) { applyBuff(randomBuff(it)) }
        }
        return listOf(
            attach(VisibleObject(dimensions = randomDimensions(), location = randomPoint())),
            attach(Actor(location = randomPoint(), dimensions = randomDimensions())),
            attach(Alive(location = randomPoint(), dimensions = randomDimensions(), maxHealth = random.nextDouble(1.0, 500.0)))
        )
    }

    /** Every snapshot in the tree rooted at [snapshot], itself included. */
    private fun flatten(snapshot: DrawableSnapshot): List<DrawableSnapshot> =
        listOf(snapshot) + snapshot.subObjects.flatMap(::flatten)

    @Test
    fun `every snapshot survives a polymorphic JSON round trip unchanged`() {
        val objects = objectsOfEachKind() +
            objectsOfEachKind(subObjects = objectsOfEachKind()) // also cover the nested case

        for (obj in objects) {
            val snapshot = DrawableSnapshot from obj
            val restored = Json.decodeFromString<DrawableSnapshot>(Json.encodeToString(snapshot))
            assertEquals(snapshot, restored, "round trip changed the snapshot for ${obj::class.simpleName}")
        }
    }

    @Test
    fun `an object's active buffs travel with its snapshot and survive the round trip`() {
        val actor = Actor(location = randomPoint(), dimensions = randomDimensions()).apply {
            applyBuff(Buff("root", durationTicks = 7, suppressedCapabilities = setOf(CoreCapability.MOVE)))
            applyBuff(Buff("haste", durationTicks = -1, statMods = mapOf("speed" to StatMod("haste", 0.3))))
        }

        val snapshot = DrawableSnapshot from actor
        val restored = Json.decodeFromString<DrawableSnapshot>(Json.encodeToString(snapshot))

        assertIs<ActorSnapshot>(restored)
        val buffs = restored.visibleObject.gameObject.buffs
        assertEquals(listOf("root", "haste"), buffs.map { it.name })
        assertEquals(listOf("move"), buffs.first().suppressedCapabilities)
        assertEquals(-1, buffs[1].durationTicks)
        assertEquals(snapshot, restored)
    }

    @Test
    fun `type selection is total and picks the most specific kind for the runtime class`() {
        repeat(25) {
            val (plain, actor, alive) = objectsOfEachKind()
            assertIs<VisibleObjectSnapshot>(DrawableSnapshot from plain)
            assertIs<ActorSnapshot>(DrawableSnapshot from actor)
            assertIs<AliveSnapshot>(DrawableSnapshot from alive)
        }
    }

    @Test
    fun `encoding a snapshot is deterministic`() {
        for (obj in objectsOfEachKind(subObjects = objectsOfEachKind())) {
            val once = Json.encodeToString(DrawableSnapshot from obj)
            val twice = Json.encodeToString(DrawableSnapshot from obj)
            assertEquals(once, twice, "re-snapshotting and re-encoding the same object diverged")
        }
    }

    @Test
    fun `sub-object snapshots recurse and drop every non-visible descendant at any depth`() {
        val hiddenLeaf = VisibleObject(location = randomPoint()).apply { visible = false }
        val visibleLeaf = VisibleObject(location = randomPoint(), dimensions = randomDimensions())
        val midLevel = Actor(location = randomPoint()).apply {
            subObjects += visibleLeaf
            subObjects += hiddenLeaf
        }
        val hiddenMid = VisibleObject(location = randomPoint()).apply {
            visible = false
            subObjects += VisibleObject(location = randomPoint()) // visible, but under a hidden parent
        }
        val root = VisibleObject(location = randomPoint()).apply {
            subObjects += midLevel
            subObjects += hiddenMid
        }

        val tree = DrawableSnapshot from root
        val nodeCount = flatten(tree).size

        // root + midLevel + visibleLeaf only; both hidden branches (and the child under hiddenMid) are gone
        assertEquals(3, nodeCount, "hidden sub-objects, and everything beneath them, must not be snapshotted")
        assertTrue(tree.subObjects.single().subObjects.size == 1, "the one surviving branch keeps its visible leaf")
    }

    @Test
    fun `a mixed world list round trips preserving order and per-entry type`() {
        val world: List<VisibleObject> = buildList {
            repeat(12) { addAll(objectsOfEachKind()) }
        }.shuffled(random)

        val snapshots: List<DrawableSnapshot> = world.map { DrawableSnapshot from it }
        val restored = Json.decodeFromString<List<DrawableSnapshot>>(Json.encodeToString(snapshots))

        assertEquals(snapshots, restored)
        for ((original, roundTripped) in snapshots.zip(restored)) {
            assertEquals(original::class, roundTripped::class, "an entry changed type on the wire")
        }
    }
}
