package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.DirectionalProjectile
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.spatial.Quadtree
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [DirectionalProjectile] travelling along a heading and piercing [Alive]s once each. */
class DirectionalProjectileTest {

    private val quadtree = Quadtree<Double, VisibleObject>()

    /** Rebuilds [quadtree] from [actors] the way a per-frame game loop would. */
    private fun index(vararg actors: Actor) {
        quadtree.clear()
        actors.forEach { quadtree.insert(it.location.x, it.location.y, it) }
    }

    private fun alive(x: Double, y: Double, size: Double = 10.0, maxHealth: Double = 100.0) = Alive(
        location = Point(x, y),
        dimensions = Dimensions(width = size, height = size),
        maxHealth = maxHealth
    )

    private fun projectile(angle: Int, maxDuration: Int, damage: Double, searchRadius: Double = 15.0) =
        DirectionalProjectile(
            location = Point(0.0, 0.0),
            dimensions = Dimensions(width = 4.0, height = 4.0),
            damage = damage,
            directionAngle = angle,
            maxDuration = maxDuration,
            quadtree = quadtree,
            searchRadius = searchRadius
        )

    @Test
    fun `it uses directional movement along the given heading`() {
        val shot = projectile(angle = 90, maxDuration = 3, damage = 10.0)

        assertTrue(shot.movement === Movement.Directional)
        assertEquals(90, shot.angle)
    }

    @Test
    fun `it travels along its heading each tick until it expires`() {
        val shot = projectile(angle = 0, maxDuration = 3, damage = 10.0) // speed 10

        repeat(3) { index(); shot.tick() }

        assertEquals(30.0, shot.location.x, absoluteTolerance = 1e-9)
        assertFalse(shot.active)
        assertFalse(shot.visible)
    }

    @Test
    fun `it damages an alive it passes through exactly once`() {
        val a = alive(25.0, 0.0, size = 20.0, maxHealth = 100.0)
        val shot = projectile(angle = 0, maxDuration = 5, damage = 30.0)

        repeat(5) { index(a); shot.tick() }

        assertEquals(70.0, a.health.current, absoluteTolerance = 1e-9)
    }

    @Test
    fun `it pierces multiple alives, hitting each once`() {
        val a = alive(20.0, 0.0, size = 16.0, maxHealth = 100.0)
        val b = alive(45.0, 0.0, size = 16.0, maxHealth = 100.0)
        val shot = projectile(angle = 0, maxDuration = 5, damage = 25.0, searchRadius = 12.0)

        repeat(5) { index(a, b); shot.tick() }

        assertEquals(75.0, a.health.current, absoluteTolerance = 1e-9)
        assertEquals(75.0, b.health.current, absoluteTolerance = 1e-9)
    }

    @Test
    fun `it does not damage an alive it never touches`() {
        val a = alive(0.0, 100.0, size = 10.0, maxHealth = 100.0)
        val shot = projectile(angle = 0, maxDuration = 5, damage = 30.0)

        repeat(5) { index(a); shot.tick() }

        assertEquals(100.0, a.health.current)
    }

    @Test
    fun `a non-positive maxDuration is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DirectionalProjectile(
                location = Point(0.0, 0.0),
                dimensions = Dimensions(width = 4.0, height = 4.0),
                damage = 10.0,
                directionAngle = 0,
                maxDuration = 0,
                quadtree = quadtree,
                searchRadius = 10.0
            )
        }
    }
}
