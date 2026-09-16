package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
//endregion

/**
 * Bookkeeping of which [Zone] each [GameObject] a [World] owns currently falls within, against
 * a fixed [grid]. Not updated automatically - call [refresh] once per frame (a natural place: a
 * [com.spartanlabs.gaming.simulation.SimulationLoop]'s `onTick` callback, or a direct call right
 * after [World.tick]) to bring it up to date with the frame's final positions.
 *
 * @param grid the static partition this index tracks entities against
 */
class ZoneIndex(private val grid: ZoneGrid) {

    /** Every currently-indexed entity's owning object and last-known [Zone], keyed by [EntityId]. */
    private val indexed: MutableMap<EntityId, Pair<GameObject, Zone>> = HashMap()

    /** The entities most recently placed in each [Zone] by [refresh]. */
    private val entitiesByZone: MutableMap<Zone, MutableSet<EntityId>> = HashMap()

    /**
     * Recomputes every owned [GameObject]'s zone against [grid] and publishes
     * [EntityChangedZone] on [world]'s [World.events] for every entity whose zone changed since
     * the last [refresh]: entering a zone for the first time ([EntityChangedZone.from] `null`),
     * moving between zones, leaving the grid's covered extent ([EntityChangedZone.to] `null`),
     * or leaving [world] entirely ([EntityChangedZone.to] `null`, naming the entity's
     * last-known reference). An entity not yet numbered by [world]
     * ([EntityId.UNASSIGNED]) is skipped, mirroring [World.byId]'s own "not yet resolvable"
     * handling.
     *
     * @param world the world whose entities to reconcile against [grid]
     */
    fun refresh(world: World) {
        val current = HashSet<EntityId>()
        world.gameObjects.forEach { obj ->
            val id = obj.entityId
            if (id == EntityId.UNASSIGNED) return@forEach
            current += id
            val newZone = grid.zoneAt(obj.location, clamped = false).getOrNull()
            val oldZone = indexed[id]?.second
            if (newZone == oldZone) return@forEach

            oldZone?.let { entitiesByZone[it]?.remove(id) }
            if (newZone != null) {
                indexed[id] = obj to newZone
                entitiesByZone.getOrPut(newZone) { mutableSetOf() }.add(id)
            } else {
                indexed.remove(id)
            }
            world.events.publish(EntityChangedZone(obj, oldZone, newZone))
        }
        (indexed.keys - current).forEach { id ->
            val (obj, oldZone) = indexed.remove(id)!!
            entitiesByZone[oldZone]?.remove(id)
            world.events.publish(EntityChangedZone(obj, oldZone, null))
        }
    }

    /** The zone [entityId] was placed in by the most recent [refresh], or `null`. */
    fun zoneOf(entityId: EntityId): Zone? = indexed[entityId]?.second

    /** The entities the most recent [refresh] placed in [zone]; empty if none. A fresh copy, not a live view. */
    fun entitiesIn(zone: Zone): Set<EntityId> = entitiesByZone[zone]?.toSet() ?: emptySet()
}
