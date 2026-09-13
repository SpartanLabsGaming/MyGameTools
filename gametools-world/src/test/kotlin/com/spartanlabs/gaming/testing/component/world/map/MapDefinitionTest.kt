package com.spartanlabs.gaming.testing.component.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.serializations.DimensionsSnapshot
import com.spartanlabs.geometry.serializations.PointSnapshot
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.world.map.ObstacleSnapshot
import com.spartanlabs.gaming.world.map.SpawnPointSnapshot
import com.spartanlabs.gaming.world.map.TerrainTypeSnapshot
import com.spartanlabs.gaming.world.map.toDomain
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
//endregion

/**
 * Level 2 - component. Direct unit coverage of the three snapshot-to-domain `toDomain()`
 * conversions ([TerrainTypeSnapshot.toDomain], [ObstacleSnapshot.toDomain],
 * [SpawnPointSnapshot.toDomain]) - field-by-field mapping, and that a mutable [Point]/[Dimensions]
 * built from a conversion is never the same instance across two separate calls (the aliasing risk
 * the plan's risk table flags).
 */
class MapDefinitionTest {

    @Test
    fun `TerrainTypeSnapshot toDomain maps every field`() {
        val snapshot = TerrainTypeSnapshot(walkable = true, movementCost = 1.5, heightLevel = 2, blocksVision = true)

        val domain = snapshot.toDomain()

        assertEquals(true, domain.walkable)
        assertEquals(1.5, domain.movementCost)
        assertEquals(2, domain.heightLevel)
        assertEquals(true, domain.blocksVision)
    }

    @Test
    fun `ObstacleSnapshot toDomain maps center and halfExtents`() {
        val snapshot = ObstacleSnapshot(center = PointSnapshot(5.0, 10.0), halfExtents = DimensionsSnapshot(2.0, 3.0))

        val domain = snapshot.toDomain()

        assertEquals(Point(5.0, 10.0), domain.center)
        assertEquals(Dimensions(2.0, 3.0), domain.halfExtents)
    }

    @Test
    fun `ObstacleSnapshot toDomain never aliases a Point or Dimensions instance across calls`() {
        val snapshot = ObstacleSnapshot(center = PointSnapshot(5.0, 10.0), halfExtents = DimensionsSnapshot(2.0, 3.0))

        val first = snapshot.toDomain()
        val second = snapshot.toDomain()

        assertNotSame(first.center, second.center, "each toDomain() call must construct a fresh Point, not share one")
        assertNotSame(first.halfExtents, second.halfExtents, "each toDomain() call must construct a fresh Dimensions, not share one")

        // Point/Dimensions are mutable upstream (GeneralTools) - mutating one converted instance
        // must not be observable through the other, which would only happen if they aliased.
        first.center.x = 999.0
        assertEquals(5.0, second.center.x)
    }

    @Test
    fun `SpawnPointSnapshot toDomain maps every field including null facing and team`() {
        val snapshot = SpawnPointSnapshot(name = "red-spawn", position = PointSnapshot(1.0, 2.0), facing = 90, team = "red")

        val domain = snapshot.toDomain()

        assertEquals("red-spawn", domain.name)
        assertEquals(Point(1.0, 2.0), domain.position)
        assertEquals(90, domain.facing)
        assertEquals("red", domain.team)
    }

    @Test
    fun `SpawnPointSnapshot toDomain leaves facing and team null when the snapshot omits them`() {
        val snapshot = SpawnPointSnapshot(name = "unaffiliated", position = PointSnapshot(1.0, 1.0))

        val domain = snapshot.toDomain()

        assertNull(domain.facing)
        assertNull(domain.team)
    }

    @Test
    fun `SpawnPointSnapshot toDomain never aliases a Point instance across calls`() {
        val snapshot = SpawnPointSnapshot(name = "start", position = PointSnapshot(3.0, 4.0))

        val first = snapshot.toDomain()
        val second = snapshot.toDomain()

        assertNotSame(first.position, second.position, "each toDomain() call must construct a fresh Point, not share one")

        first.position.x = 999.0
        assertEquals(3.0, second.position.x)
    }
}
