package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.ActorSnapshot
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.AliveSnapshot
import com.spartanlabs.gaming.gameobjects.DrawableSnapshot
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.VisibleObjectSnapshot
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
import kotlin.test.assertIs
//endregion

/** Covers [DrawableSnapshot] dispatching to the right snapshot type and its polymorphic JSON form. */
class DrawableSnapshotTest {

    private fun alive() = Alive(location = Point(1.0, 2.0), dimensions = Dimensions(4.0, 4.0), maxHealth = 50.0)

    @Test
    fun `an Actor is snapshotted as an ActorSnapshot`() {
        assertIs<ActorSnapshot>(DrawableSnapshot from Actor(location = Point(1.0, 2.0)))
    }

    @Test
    fun `an Alive is snapshotted as an AliveSnapshot`() {
        assertIs<AliveSnapshot>(DrawableSnapshot from alive())
    }

    @Test
    fun `a plain VisibleObject is snapshotted as a VisibleObjectSnapshot`() {
        assertIs<VisibleObjectSnapshot>(DrawableSnapshot from VisibleObject(width = 5.0, height = 5.0))
    }

    @Test
    fun `an Actor nested as a sub-object is snapshotted as an ActorSnapshot`() {
        val parent = VisibleObject().apply { subObjects += Actor(location = Point(3.0, 3.0)) }

        val snapshot = DrawableSnapshot from parent

        assertIs<ActorSnapshot>(snapshot.subObjects.single())
    }

    @Test
    fun `a mixed list round-trips through polymorphic JSON keeping each entry's type`() {
        val actor = Actor(location = Point(1.0, 1.0)).apply { destination = Point(9.0, 9.0) }
        val unit = alive().apply { faction = "red" }
        val plain = VisibleObject(texture = "wall.png")
        val list: List<DrawableSnapshot> =
            listOf(DrawableSnapshot from actor, DrawableSnapshot from unit, DrawableSnapshot from plain)

        val restored = Json.decodeFromString<List<DrawableSnapshot>>(Json.encodeToString(list))

        assertEquals(list, restored)
        assertIs<ActorSnapshot>(restored[0])
        assertIs<AliveSnapshot>(restored[1])
        assertIs<VisibleObjectSnapshot>(restored[2])
    }
}
