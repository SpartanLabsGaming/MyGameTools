package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.event.EventBus
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.simulation.RandomSource
import com.spartanlabs.gaming.simulation.SeededRandom
import com.spartanlabs.gaming.spatial.Quadtree
import com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex
import com.spartanlabs.gaming.spatial.SpatialIndex
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.random.Random
//endregion

/**
 * A container for everything the game is simulating: a flat list of [gameObjects], a
 * [spatialIndex] over the [VisibleObject]s among them, an [EntityId] index ([byId])
 * over every object it owns, an [events] bus that reports what happens each tick, a
 * seeded [rng] source that makes those ticks reproducible, and an optional [space]
 * describing the bounded playfield it simulates in.
 *
 * [World] does not run itself - an external game loop calls [tick] once per frame.
 *
 * ### What one [tick] does, in order
 * 1. [tickCount] is incremented.
 * 2. [spatialIndex] is reconciled against the current positions of the owned [VisibleObject]s -
 *    moved objects are relocated, new ones inserted, unchanged ones untouched.
 * 3. [byId] is rebuilt from [gameObjects]; [GameEvent.EntitySpawned] fires for any object seen
 *    for the first time, in [gameObjects] order.
 * 4. every owned object is [GameObject.tick]ed, in [gameObjects] insertion order, over a
 *    snapshot of the list taken before the pass - so an object may add to [gameObjects] or
 *    [removeList] during its own tick without disturbing the pass (a mid-pass addition is
 *    numbered and ticked from the *next* frame).
 * 5. everything queued in [removeList] is dropped and a [GameEvent.EntityRemoved] fires for each.
 *
 * [GameEvent]s are delivered synchronously as they are published, not batched at the end.
 * Given the same [seed] and the same sequence of external calls - including [installSystem],
 * [uninstallSystem], and [stepSystems] - two worlds produce the same result.
 *
 * ### Installed systems
 * A [World] can also host opt-in [WorldSystem]s ([installSystem], [uninstallSystem],
 * [installedSystems]). [tick] never steps them: a driver calls [stepSystems] once per frame after
 * [tick], e.g. `SimulationLoop(world, onTick = { world.stepSystems() })`. Systems that claim a
 * [CoreSystemSlot] step first, in slot order; the rest follow in install order. A [World] with
 * nothing installed pays nothing for this. Experimental - see [ExperimentalGameToolsApi].
 *
 * @param seed the seed for [random]; defaults to a fresh value, logged on construction so a
 *   run can be reproduced by pinning it
 */
class World(val seed: Long = Random.nextLong()) {

    init {
        log.info("World created with seed {}", seed)
    }

    /**
     * The simulation's source of randomness for this world, seeded from [seed]. Every random
     * choice the engine makes for an object this world owns goes through here, so a fixed
     * [seed] plus a fixed input sequence gives a fixed result. Named `rng` rather than
     * `random` so it does not shadow a caller's own `random` inside an `apply { }` block.
     */
    val rng: RandomSource = SeededRandom(seed)

    /** How many times [tick] has been called on this world. `0` before the first tick. */
    var tickCount: Long = 0L
        private set

    /** Every object this world owns, visible or not, in insertion order. */
    val gameObjects: ArrayList<GameObject> = ArrayList()

    /**
     * Objects to drop from [gameObjects] at the end of the current [tick]. An object adds
     * itself here during its own tick - a dying [Alive] with [Alive.DeathResponse.REMOVAL]
     * does - and [tick] removes them once the pass is done, so removal never disturbs iteration.
     */
    val removeList: ArrayList<GameObject> = ArrayList()

    /**
     * The broad-phase index over this world's [VisibleObject]s, reconciled incrementally at the
     * start of every [tick] (see [reconcileSpatialIndex]) rather than rebuilt from scratch.
     * Defaults to a [QuadtreeSpatialIndex], matching `5.1.0`'s query semantics exactly; assign a
     * [UniformGrid][com.spartanlabs.gaming.spatial.UniformGrid] for a roughly uniform-density
     * field instead. After replacing this mid-game, call [reindexSpatial] once so the new
     * (empty) index is populated and every object's incremental-reconcile marker is reset
     * against it.
     */
    var spatialIndex: SpatialIndex<VisibleObject> = QuadtreeSpatialIndex()

    /**
     * The pre-`5.2.0` [Quadtree] view of [spatialIndex]: the live tree itself when
     * [spatialIndex] is a [QuadtreeSpatialIndex] (the default - no copying), or a freshly built
     * snapshot from [gameObjects] otherwise (an `O(n)` rebuild on every access - a
     * [UniformGrid][com.spartanlabs.gaming.spatial.UniformGrid]-backed world still using this
     * accessor, [Actor.nearby], or a [Quadtree]-typed [DirectionalProjectile] /
     * [HomingProjectile] pays that cost; migrate to [spatialIndex] directly to avoid it).
     */
    @Deprecated(
        "Use spatialIndex; Quadtree is one SpatialIndex implementation among several now.",
        ReplaceWith("spatialIndex")
    )
    val quadtree: Quadtree<Double, VisibleObject>
        get() = (spatialIndex as? QuadtreeSpatialIndex<VisibleObject>)?.tree
            ?: Quadtree<Double, VisibleObject>().apply {
                gameObjects.filterIsInstance<VisibleObject>()
                    .forEach { insert(it.location.x, it.location.y, it) }
            }

    /**
     * The bus this world publishes [GameEvent]s on: [GameEvent.EntitySpawned] /
     * [GameEvent.EntityRemoved] as objects join and leave, plus the combat and death events an
     * owned [Alive] raises. Subscribe to it to react to what the simulation does without
     * wiring into the code that does it.
     */
    val events: EventBus = EventBus()

    /**
     * The bounded playfield this world simulates in, or `null` for the pre-Phase-1 unbounded
     * plane (every coordinate in bounds and walkable). Purely descriptive here: [tick] does not
     * consult [space] - no existing [add]/[tick] behaviour changes when this is set. A
     * `gametools-world` `TiledMap` is the standard implementation; a system that wants to
     * enforce bounds or walkability (movement, physics, pathfinding) queries [space] itself.
     * `null` by default, so an existing [World] behaves exactly as it did before this property
     * existed.
     */
    var space: Space? = null

    //region ENTITY IDENTITY
    /** The last [EntityId.raw] handed out; the next object this world numbers gets `nextRawId + 1`. */
    private var nextRawId: Long = 0L

    /**
     * Every object this world owns, and their [VisibleObject.subObjects] trees, keyed by
     * [GameObject.entityId]. Rebuilt from [gameObjects] at the start of every [tick] and also
     * updated eagerly by [add], so a just-added object is resolvable before the first tick but
     * a direct `gameObjects += ...` addition is only resolvable from the next tick on.
     */
    private val byId: HashMap<EntityId, GameObject> = HashMap()

    /**
     * The ids a [GameEvent.EntitySpawned] has already been fired for. An id is dropped when its
     * object leaves the world, so an instance that is removed and later re-added is announced
     * again.
     */
    private val announced: HashSet<EntityId> = HashSet()

    /**
     * The object this world owns whose [GameObject.entityId] is [id], or `null` if no such
     * object is currently owned - which is exactly the signal a command handler wants when a
     * client names an object that has since died or been removed.
     *
     * Resolves objects added through [add] immediately; objects added by mutating
     * [gameObjects] directly become resolvable after the next [tick].
     *
     * @param id the stable id a client addressed
     */
    fun byId(id: EntityId): GameObject? = byId[id]

    /**
     * Numbers [gameObject] if it has no id yet, indexes it in [byId], and recurses into its
     * [VisibleObject.subObjects] so nested drawables (health bars, nameplates) are addressable
     * too. An object that already has an id keeps it - re-enrolling is just a re-index.
     *
     * @param gameObject the object to bring into this world's id index
     */
    private fun enrol(gameObject: GameObject) {
        if (gameObject.entityId == EntityId.UNASSIGNED) {
            gameObject.entityId = EntityId(++nextRawId)
            log.debug("World numbered a {} as {}", gameObject::class.simpleName, gameObject.entityId)
        }
        byId[gameObject.entityId] = gameObject
        if (gameObject is VisibleObject) gameObject.subObjects.forEach(::enrol)
    }

    /**
     * Clears [byId], re-enrols every object in [gameObjects] (and its sub-object tree) in list
     * order, then fires [GameEvent.EntitySpawned] for any top-level object not announced yet
     * and forgets announcements for objects no longer owned.
     */
    private fun reindexEntities() {
        byId.clear()
        gameObjects.forEach(::enrol)
        gameObjects.forEach(::announce)
        announced.retainAll(gameObjects.mapTo(HashSet()) { it.entityId })
    }

    /**
     * Fires [GameEvent.EntitySpawned] for [gameObject] the first time this world sees it as a
     * top-level object. Sub-objects are indexed (see [enrol]) but not announced - they are part
     * of their parent, not independent arrivals.
     *
     * @param gameObject a freshly enrolled top-level object (its id is already assigned)
     */
    private fun announce(gameObject: GameObject) {
        if (announced.add(gameObject.entityId)) {
            log.debug("World announcing a new {} ({})", gameObject::class.simpleName, gameObject.entityId)
            events.publish(GameEvent.EntitySpawned(gameObject))
        }
    }
    //endregion

    /**
     * Adds [gameObject] to [gameObjects] and numbers it (see [byId]); for an [Actor] it also
     * sets [Actor.world] so a [Alive.DeathResponse.REMOVAL] death can reach [removeList] and an
     * [Actor.issue] can publish on [events]. Adding straight to [gameObjects] still works, but
     * then an [Actor] needs its [Actor.world] set by hand and the object is not resolvable
     * through [byId] until the next [tick].
     *
     * @param gameObject the object to bring into the world
     */
    fun add(gameObject: GameObject) {
        gameObjects.add(gameObject)
        enrol(gameObject)
        if (gameObject is Actor) gameObject.world = this
        announce(gameObject)
    }

    /**
     * Advances the world by one frame. See the class doc for the exact order of operations.
     * Never steps an installed [WorldSystem] - see [stepSystems].
     */
    fun tick() {
        tickCount++
        reconcileSpatialIndex()
        reindexEntities()

        val ticking = gameObjects.toList()
        log.debug("World tick: advancing {} game object(s)", ticking.size)
        ticking.forEach(GameObject::tick)

        if (removeList.isNotEmpty()) {
            log.debug("World removing {} game object(s)", removeList.size)
            val removed = removeList.toList().distinct()
            gameObjects.removeAll(removed.toSet())
            removed.forEach { gone ->
                // gone may have moved during its own tick (step 4) after already being
                // reconciled this tick (step 2), so the index still holds it at the
                // step-2 position, not its current location.
                if (gone is VisibleObject) {
                    gone.lastIndexedLocation?.let { spatialIndex.remove(it.x, it.y, gone) }
                    gone.lastIndexedLocation = null
                }
                byId.remove(gone.entityId)
                announced.remove(gone.entityId)
                events.publish(GameEvent.EntityRemoved(gone))
            }
            removeList.clear()
        }
    }

    /**
     * Reconciles [spatialIndex] against the positions the owned [VisibleObject]s hold right now
     * - a first-seen object is [SpatialIndex.insert]ed, a moved one is [SpatialIndex.move]d, and
     * an unchanged one costs nothing. Replaces the pre-`5.2.0` clear-and-reinsert-everything
     * `rebuildQuadtree` this method used to be.
     *
     * `internal` rather than `private` solely so a same-module nonfunctional benchmark
     * (`SpatialIndexScalabilityTest`) can call it directly, isolated from the rest of [tick]'s
     * work, for a true reconcile-vs-rebuild comparison against [reindexSpatial]. Not part of the
     * public API.
     */
    internal fun reconcileSpatialIndex() {
        gameObjects.forEach { obj ->
            if (obj !is VisibleObject) return@forEach
            val last = obj.lastIndexedLocation
            val x = obj.location.x
            val y = obj.location.y
            when {
                last == null -> spatialIndex.insert(x, y, obj)
                last.x != x || last.y != y -> spatialIndex.move(last.x, last.y, x, y, obj)
            }
            obj.lastIndexedLocation = IndexedPosition(x, y)
        }
    }

    /**
     * Clears [spatialIndex] and re-inserts every [VisibleObject] in [gameObjects] at its current
     * position, resetting each one's incremental-reconcile marker to match. [tick] no longer
     * does this every frame; call it after bulk-mutating positions outside of [tick], or right
     * after assigning a new [spatialIndex].
     */
    fun reindexSpatial() {
        spatialIndex.clear()
        gameObjects.filterIsInstance<VisibleObject>().forEach { obj ->
            spatialIndex.insert(obj.location.x, obj.location.y, obj)
            obj.lastIndexedLocation = IndexedPosition(obj.location.x, obj.location.y)
        }
    }

    //region INSTALLED SYSTEMS
    /**
     * One successful [installSystem] call for a [WorldSystem]. Two records for the same
     * [WorldSystem] instance (e.g. uninstalled then reinstalled) are distinct - what lets a step
     * pass tell "this installation was removed after my snapshot was taken" apart from "this
     * instance was reinstalled", without ever calling [equals] on a consumer's [WorldSystem]
     * (which may itself be a `data class`).
     */
    @OptIn(ExperimentalGameToolsApi::class)
    private class InstalledSystemRecord(val system: WorldSystem, val slot: CoreSystemSlot?) {
        /** Cleared by [uninstallSystem] at removal; [stepSystems] skips a record whose [active] is false. */
        var active: Boolean = true
    }

    /** An [installSystem] call still inside [WorldSystem.installOn], not yet recorded. */
    @OptIn(ExperimentalGameToolsApi::class)
    private class Reservation(val system: WorldSystem, val slot: CoreSystemSlot?)

    /**
     * Every installed system's [InstalledSystemRecord], kept in step order at insert time: tier 1
     * ([InstalledSystemRecord.slot] non-null) by [CoreSystemSlot.order], then tier 2 (`slot ==
     * null`) in install order. The only backing structure for [installedSystems], slot occupancy,
     * and [stepSystems]'s own order - never a hash- or tree-keyed collection, so iteration order
     * never depends on a [CoreWorldSystemSlot] constant's (identity-based, per-run) hash code.
     */
    private val installedRecords: MutableList<InstalledSystemRecord> = mutableListOf()

    /**
     * In-flight [installSystem] calls, most recently pushed last. Consulted by both `require`s in
     * [installSystem] so a re-entrant call sees every installation still in progress up the call
     * stack, not just [installedRecords].
     */
    private val installReservations: ArrayDeque<Reservation> = ArrayDeque()

    /** `true` for the duration of one [stepSystems] call, so a re-entrant call is rejected. */
    private var stepping: Boolean = false

    /**
     * Inserts [record] at its step-order position: immediately before the first record that is
     * tier 2 or whose slot has a greater [CoreSystemSlot.order] than [record]'s (tier 1), or at
     * the end (tier 2).
     */
    @OptIn(ExperimentalGameToolsApi::class)
    private fun insertInStepOrder(record: InstalledSystemRecord) {
        val slot = record.slot
        if (slot == null) {
            installedRecords.add(record)
            return
        }
        // A tier-1 record goes before the first record that is tier 2 or has a strictly greater
        // order. "Strictly" means an equal order (library slots never share one) lands after the
        // existing record, so install order breaks the tie deterministically.
        val insertAt = installedRecords.indexOfFirst { it.slot == null || it.slot.order > slot.order }
        installedRecords.add(if (insertAt == -1) installedRecords.size else insertAt, record)
    }

    /**
     * Installs [system] onto this [World]: calls [WorldSystem.installOn] once, then - only if it
     * returns normally - records [system] in step order so a later [stepSystems] call steps it.
     *
     * Checked, both as [IllegalArgumentException], before [WorldSystem.installOn] runs:
     * - [system] must not already be installed on this [World], and must not itself be in the
     *   middle of an [installSystem] call further up the call stack (a re-entrant self-install).
     * - if [WorldSystem.coreSlot] (read exactly once, right here) is non-null, that slot must not
     *   already be claimed by another installed or currently-installing system.
     *
     * [WorldSystem.installOn] must be failure-atomic: if it throws, this [World] records nothing
     * for [system] and never calls [WorldSystem.uninstallFrom] for this attempt - any partial
     * state [system] itself acquired (e.g. a helper [WorldSystem] it installed) is [system]'s own
     * responsibility to undo. The exception propagates to the caller unchanged, and [system] may
     * be installed again later.
     *
     * [installedSystems] never contains [system] while [WorldSystem.installOn] is still running.
     * Single-threaded, like every other [World] member: call this only from the thread driving
     * this [World]. This member, [uninstallSystem], [installedSystems], and [stepSystems] are
     * gated [ExperimentalGameToolsApi] - their shape may still change incompatibly in a Feature
     * release until they graduate.
     *
     * Legal to call from inside a [WorldSystem.step] running as part of an active [stepSystems]
     * pass: [system] is not in that pass's snapshot, so it steps from the *next* [stepSystems]
     * call, not the one already in progress.
     *
     * @param system the system to install
     * @throws IllegalArgumentException if [system] is already installed, is already being
     *   installed (a re-entrant call), or claims a [WorldSystem.coreSlot] another installed or
     *   currently-installing system already holds
     */
    @ExperimentalGameToolsApi
    fun installSystem(system: WorldSystem) {
        require(installedRecords.none { it.system === system } && installReservations.none { it.system === system }) {
            "system $system is already installed on, or is already being installed on, this World"
        }
        val slot = system.coreSlot
        if (slot != null) {
            val occupant = installedRecords.firstOrNull { it.slot == slot }?.system
                ?: installReservations.firstOrNull { it.slot == slot }?.system
            require(occupant == null) { "slot $slot is already claimed by $occupant" }
        }
        installReservations.addLast(Reservation(system, slot))
        try {
            system.installOn(this)
        } finally {
            installReservations.removeLast()
        }
        insertInStepOrder(InstalledSystemRecord(system, slot))
        log.info("World installed a {} (slot={})", system::class.simpleName, slot)
    }

    /**
     * Removes [system] from this [World]'s installed systems, then calls
     * [WorldSystem.uninstallFrom] once. Idempotent: a [system] that is not currently installed is
     * a no-op.
     *
     * [system] is removed - absent from [installedSystems] and from the next [stepSystems] pass -
     * *before* [WorldSystem.uninstallFrom] runs, the mirror image of [installSystem]'s own
     * ordering. If [WorldSystem.uninstallFrom] throws, [system] stays removed either way and the
     * exception propagates to the caller unchanged.
     *
     * Single-threaded, like every other [World] member.
     *
     * Legal to call from inside a [WorldSystem.step] running as part of an active [stepSystems]
     * pass: [system] is skipped for the remainder of that pass, whether it is [system] itself or
     * another system's [WorldSystem.step] making the call.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     *
     * @param system the system to uninstall
     */
    @ExperimentalGameToolsApi
    fun uninstallSystem(system: WorldSystem) {
        val index = installedRecords.indexOfFirst { it.system === system }
        if (index == -1) {
            log.debug("World received an uninstallSystem call for a {} that was not installed - no-op", system::class.simpleName)
            return
        }
        val record = installedRecords.removeAt(index)
        record.active = false
        log.info("World uninstalled a {} (slot={})", system::class.simpleName, record.slot)
        system.uninstallFrom(this)
    }

    /**
     * The [WorldSystem]s currently installed on this [World], tier 1 (by [CoreSystemSlot.order])
     * then tier 2 (in [installSystem] order) - the exact order [stepSystems] steps them in. A
     * fresh copy on every read; mutating it does not affect this [World]'s registry. Never
     * contains a system that is still inside its own [WorldSystem.installOn] call. Empty for a
     * [World] with nothing installed.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     */
    @ExperimentalGameToolsApi
    val installedSystems: List<WorldSystem>
        get() = installedRecords.map { it.system }

    /**
     * Steps every currently-installed [WorldSystem] once, tier 1 (by [CoreSystemSlot.order]) then
     * tier 2 (in [installSystem] order), over a snapshot taken at the start of this call - not
     * [installedSystems] recomputed mid-pass. Never called by [tick]; a driver (typically
     * [com.spartanlabs.gaming.simulation.SimulationLoop]'s `onTick`) calls it once per frame, e.g.
     * `SimulationLoop(world, onTick = { world.stepSystems() })`.
     *
     * A system [uninstallSystem]-ed earlier in this same pass is skipped for the rest of the pass.
     * A system [installSystem]-ed during this pass steps from the *next* [stepSystems] call, not
     * this one. If a [WorldSystem.step] throws, the exception propagates to the caller, no system
     * after it steps this pass, and this [World]'s registry is left exactly as [WorldSystem.step]
     * left it - the next [stepSystems] call runs normally.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     *
     * @throws IllegalStateException if called re-entrantly - from inside a [WorldSystem.step] this
     *   same call is already running, directly or indirectly
     */
    @ExperimentalGameToolsApi
    fun stepSystems() {
        check(!stepping) { "World.stepSystems() was called re-entrantly, from inside a WorldSystem.step() this same call is already running" }
        stepping = true
        try {
            val snapshot = installedRecords.toList()
            log.debug("World stepping {} system(s)", snapshot.size)
            snapshot.forEach { record -> if (record.active) record.system.step(this) }
        } finally {
            stepping = false
        }
    }
    //endregion
}
