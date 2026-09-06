package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.HomingProjectile
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.spatial.Quadtree
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/** Covers [HomingProjectile] chasing an [Alive], hitting it once on collision, and spending itself. */
class HomingProjectileTest {

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

    private fun projectile(x: Double, y: Double, damage: Double, target: Alive) = HomingProjectile(
        location = Point(x, y),
        dimensions = Dimensions(width = 4.0, height = 4.0),
        damage = damage,
        target = target,
        quadtree = quadtree
    )

    @Test
    fun `it homes on its target and exposes the given damage`() {
        val target = alive(100.0, 0.0)
        val shot = projectile(0.0, 0.0, damage = 25.0, target = target)

        assertEquals(25.0, shot.damage)
        assertTrue(shot.movement is Movement.Homing)
        assertEquals(target, (shot.movement as Movement.Homing).target)
    }

    @Test
    fun `it does not hit while still outside collision range`() {
        val target = alive(100.0, 0.0)
        val shot = projectile(0.0, 0.0, damage = 30.0, target = target)

        index(target)
        shot.tick() // steps to (10,0); target is at (100,0)

        assertEquals(100.0, target.health.current)
        assertTrue(shot.active)
    }

    @Test
    fun `it hits on collision before reaching the target's exact location`() {
        val target = alive(30.0, 0.0, size = 20.0)
        val shot = projectile(0.0, 0.0, damage = 30.0, target = target)

        repeat(2) { index(target); shot.tick() } // reach = (4+20)/2 = 12; collides at (20,0)

        assertEquals(70.0, target.health.current, absoluteTolerance = 1e-9)
        assertFalse(shot.active)
        assertFalse(shot.visible)
        assertTrue(shot.location.x < target.location.x, "should have hit before arriving")
    }

    @Test
    fun `it deals damage only once`() {
        val target = alive(15.0, 0.0, size = 20.0)
        val shot = projectile(0.0, 0.0, damage = 40.0, target = target)

        repeat(10) { index(target); shot.tick() }

        assertEquals(60.0, target.health.current, absoluteTolerance = 1e-9)
        assertFalse(shot.active)
    }
}
