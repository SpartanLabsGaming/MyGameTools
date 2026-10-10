package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.reflect.KClass
//endregion

/**
 * The zone system: tracks which [Zone] of a fixed [grid] each [GameObject] its [World] owns
 * falls within, and publishes an [EntityChangedZone] on [World.events] whenever an entity enters,
 * crosses between, or leaves zones.
 *
 * Install it with [World.installSystem]; it is then updated once per [World.stepSystems] call,
 * always after any [CoreWorldSystemSlot.PHYSICS]-slot system whatever the install order (it claims
 * [CoreWorldSystemSlot.ZONE]). Installing publishes nothing - the first step places every entity.
 * There is no separate refresh call.
 *
 * **One [ZoneIndex], one [World], for life.** Its bookkeeping is keyed by [EntityId], every
 * [World] numbers its entities from the same start, and the bookkeeping cannot be reset - so an
 * index that has seen one [World] would silently mix its state into another's. A second [World]
 * therefore rejects it with [IllegalArgumentException], while it is installed and after it is
 * uninstalled alike. Re-installing it on the same [World] is fine, and its next step publishes
 * what changed meanwhile. Another [World] needs another [ZoneIndex]; the same [ZoneGrid] can back
 * both. The bound [World] stays reachable from the index after uninstall, so create one index per
 * [World] and do not keep one in a static.
 *
 * Code that holds only a [World] gets the installed index from [World.systemOf]
 * (`world.systemOf<ZoneIndex>()`) - a [Result], a failure when none is installed. The lookup is by
 * exact key: a substitute [CoreWorldSystemSlot.ZONE] system is not found under [ZoneIndex], and is
 * rejected while a [ZoneIndex] holds the slot.
 *
 * Not thread-safe: call [zoneOf] and [entitiesIn] only from the thread driving the bound [World]
 * (the one calling [World.stepSystems]).
 *
 * Experimental - may change incompatibly in a Feature release until it graduates; see
 * [ExperimentalGameToolsApi]. Using [ZoneIndex], including [zoneOf] and [entitiesIn], needs the
 * opt-in.
 *
 * @param grid the static partition this index tracks entities against
 */
@ExperimentalGameToolsApi
class ZoneIndex(private val grid: ZoneGrid) : AbstractWorldSystem() {

    // Both maps are keyed by EntityId and never reset, so they are valid for exactly one World -
    // which is why the binding is for life. They are deliberately retained across uninstall and
    // re-install, so the first step after a re-install publishes what changed meanwhile.

    /** Every currently-indexed entity's owning object and last-known [Zone], keyed by [EntityId]. */
    private val indexed: MutableMap<EntityId, Pair<GameObject, Zone>> = HashMap()

    /** The entities most recently placed in each [Zone] by [step]. */
    private val entitiesByZone: MutableMap<Zone, MutableSet<EntityId>> = HashMap()

    /** [CoreWorldSystemSlot.ZONE]: this index always steps after any `PHYSICS`-slot system. */
    override val coreSlot: CoreSystemSlot = CoreWorldSystemSlot.ZONE

    /** [ZoneIndex] itself: at most one per [World], and the key [World.systemOf] finds it by. */
    override val uniqueRole: KClass<out WorldSystem> = ZoneIndex::class

    /**
     * Recomputes every owned [GameObject]'s zone against [grid] and publishes
     * [EntityChangedZone] on [World.events] for every entity whose zone changed since the previous
     * step: entering a zone for the first time ([EntityChangedZone.from] `null`), moving between
     * zones, leaving the grid's covered extent ([EntityChangedZone.to] `null`), or leaving the
     * [World] entirely ([EntityChangedZone.to] `null`, naming the entity's last-known reference).
     * An entity not yet numbered by the [World] ([EntityId.UNASSIGNED]) is skipped, mirroring
     * [World.byId]'s own "not yet resolvable" handling.
     *
     * Events are published synchronously, during this call: first those of the owned entities, in
     * [World.gameObjects] order (including any that left the grid's extent), then the "left the
     * [World]" events, in an unspecified but deterministic order. Listeners may add objects to or
     * remove them from the [World] while the step runs - it works over a snapshot of
     * [World.gameObjects] - and such changes are seen on the next step.
     *
     * Called by [World.stepSystems], the entry point; calling it directly is not part of the usage
     * contract.
     *
     * @throws IllegalStateException if this index was never installed on a [World]
     */
    override fun step() {
        // Read once: an index that was never installed fails here, before any bookkeeping changes.
        val world = this.world
        val current = HashSet<EntityId>()
        // Iterate a snapshot, as World.tick does: an EntityChangedZone listener runs synchronously
        // inside this loop and may add or remove objects; those changes are seen on the next step.
        world.gameObjects.toList().forEach { obj ->
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

    /**
     * The zone [entityId] was placed in by the most recent step, as a [Result]. Never throws and
     * never returns `null`; a miss allocates two small objects - the [Result] failure wrapper and a
     * stackless [UnzonedEntityException] - and walks no stack. Works before install (a failure).
     *
     * @param entityId the entity to look up
     * @return [Result.success] with that [Zone], or [Result.failure] carrying an
     *   [UnzonedEntityException] for [entityId] if the most recent step placed it in no zone -
     *   because it is outside the grid's extent, has left the [World], is not yet numbered
     *   ([EntityId.UNASSIGNED]), was never seen, or the index has not stepped yet
     */
    fun zoneOf(entityId: EntityId): Result<Zone> =
        indexed[entityId]?.second?.let { Result.success(it) }
            ?: Result.failure(UnzonedEntityException(entityId))

    /**
     * The entities the most recent step placed in [zone]; empty if none. A fresh copy, not a live
     * view. Works before install (empty).
     *
     * @param zone the zone to list
     * @return the ids of the entities in [zone]
     */
    fun entitiesIn(zone: Zone): Set<EntityId> = entitiesByZone[zone]?.toSet() ?: emptySet()
}
