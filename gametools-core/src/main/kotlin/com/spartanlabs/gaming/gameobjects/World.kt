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
import kotlin.reflect.KClass
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
 * [installedSystems]), each bound to exactly one [World] for life, and looked up by role with
 * [systemOf], a miss being a [Result.failure] carrying a [MissingWorldSystemException]. [tick]
 * never steps them: a driver calls [stepSystems] once per frame after [tick], e.g.
 * `SimulationLoop(world, onTick = { world.stepSystems() })`. Systems that claim a
 * [CoreSystemSlot] step first, in slot order; the rest follow in install order. A [World] with
 * nothing installed pays nothing for this. Experimental - see [ExperimentalGameToolsApi].
 *
 * @param seed the seed for [rng]; defaults to a fresh value, logged on construction so a
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
     * One [installSystem] attempt that passed its checks. Recorded *before*
     * [WorldSystem.onInstalled] runs, so a throwing [WorldSystem.onInstalled] can be rolled back
     * by removing exactly this record. Two records for the same [WorldSystem] instance (e.g.
     * uninstalled then reinstalled) are distinct - what lets a step pass tell "this installation
     * was removed after my snapshot was taken" apart from "this instance was reinstalled", and
     * what lets a roll-back target *this attempt's* record by identity, without ever calling
     * [equals] on a consumer's [WorldSystem] (which may itself be a `data class`).
     */
    @OptIn(ExperimentalGameToolsApi::class)
    private class InstalledSystemRecord(
        val system: WorldSystem,
        val slot: CoreSystemSlot?,
        /** Read once from [WorldSystem.uniqueRole] at install; never re-read. */
        val role: KClass<out WorldSystem>?,
    ) {
        /**
         * Cleared by [uninstallSystem] at removal and by [rollBack]; [stepSystems] skips a record
         * whose [active] is false.
         */
        var active: Boolean = true
    }

    /**
     * Every installed system's [InstalledSystemRecord], kept in step order at insert time: tier 1
     * ([InstalledSystemRecord.slot] non-null) by [CoreSystemSlot.order], then tier 2 (`slot ==
     * null`) in install order. The only backing structure for [installedSystems], slot occupancy,
     * role uniqueness, [systemOf], and [stepSystems]'s own order - never a hash- or tree-keyed
     * collection, so iteration order never depends on a [CoreWorldSystemSlot] constant's or a
     * [KClass]'s (identity-based, per-run) hash code.
     */
    private val installedRecords: MutableList<InstalledSystemRecord> = mutableListOf()

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
     * Installs [system] onto this [World], in this order:
     * 1. **checks** - all read-only, so a rejected [system] leaves this [World] and [system]
     *    exactly as they were (see `@throws`);
     * 2. **bind** - an unbound [AbstractWorldSystem] is bound to this [World] for life;
     * 3. **record** - [system] joins [installedSystems] in step order;
     * 4. **notify** - [WorldSystem.onInstalled] runs once.
     *
     * The checks run in a fixed order: identity duplicate, binding, [WorldSystem.coreSlot] (read
     * exactly once, here), then [WorldSystem.uniqueRole] (read exactly once, here) - so a second
     * claimant of an occupied slot is rejected with the *slot* message even if it also declares a
     * taken role. For a direct implementor of [WorldSystem] (not an [AbstractWorldSystem]), the
     * binding check reads its [WorldSystem.world]; if that getter throws, the exception propagates
     * unchanged with nothing changed and neither [WorldSystem.coreSlot] nor
     * [WorldSystem.uniqueRole] read.
     *
     * **Roll-back.** If [WorldSystem.onInstalled] throws, this attempt's record is removed (its
     * slot and role are free again), [WorldSystem.onUninstalled] is **not** called, the binding
     * is kept, and the original exception is rethrown unchanged. Helper systems the failing hook
     * installed stay installed. [system] may be installed again later, on this same [World] only.
     * A roll-back logs one `WARN` line naming [system]'s class, its slot and role, and the cause's
     * type and message; the throwable itself is not attached, because the caller receives it.
     *
     * [installedSystems] contains [system] while its [WorldSystem.onInstalled] runs, and
     * [systemOf] finds it under its [WorldSystem.uniqueRole]. Legal but
     * discouraged from inside that hook: [stepSystems] (when no step pass is already running)
     * steps [system] before the hook returns, and [uninstallSystem] with [system] genuinely
     * uninstalls it before the hook returns.
     *
     * Legal to call from inside a [WorldSystem.step] running as part of an active [stepSystems]
     * pass: [system] is not in that pass's snapshot, so it steps from the *next* [stepSystems]
     * call, not the one already in progress.
     *
     * Single-threaded, like every other [World] member: call this only from the thread driving
     * this [World]. This member, [uninstallSystem], [installedSystems], and [stepSystems] are
     * gated [ExperimentalGameToolsApi] - their shape may still change incompatibly in a Feature
     * release until they graduate.
     *
     * Any exception [WorldSystem.onInstalled] throws is rethrown unchanged, after the roll-back.
     *
     * @param system the system to install
     * @throws IllegalArgumentException if [system] is already installed on this [World]
     *   (including a re-entrant self-install from its own [WorldSystem.onInstalled]); if it is
     *   bound to a different [World], or a direct implementor's [WorldSystem.world] is not this
     *   [World]; if its [WorldSystem.coreSlot] is already claimed (checked before the role); or if
     *   its [WorldSystem.uniqueRole] is not a supertype of [system] or is already held by another
     *   installed system
     * @throws MissingWorldSystemException if [WorldSystem.onInstalled] unwraps a missing peer's
     *   [systemOf] lookup with `getOrThrow()` - rethrown unchanged, after the roll-back
     */
    @ExperimentalGameToolsApi
    fun installSystem(system: WorldSystem) {
        // 1. identity duplicate (never equals(): a consumer's system may be a data class)
        require(installedRecords.none { it.system === system }) {
            "system $system is already installed on this World"
        }
        // 2. binding: a direct implementor's throwing getter propagates here, nothing changed
        requireBindableHere(system)
        // 3. slot - read once, checked BEFORE the role
        val slot = system.coreSlot
        if (slot != null) {
            val occupant = installedRecords.firstOrNull { it.slot == slot }?.system
            require(occupant == null) { "slot $slot is already claimed by $occupant" }
        }
        // 4. role - read once; KClass compared with ==, never ===; messages use role.java.name,
        // never the KClass (its toString() needs kotlin-reflect)
        val role = system.uniqueRole
        if (role != null) {
            require(role.isInstance(system)) {
                "system $system declares uniqueRole ${role.java.name} but is not an instance of it"
            }
            val holder = installedRecords.firstOrNull { it.role == role }?.system
            require(holder == null) { "uniqueRole ${role.java.name} is already held by $holder" }
        }
        // 5. bind (AbstractWorldSystem only; a direct implementor already knows its World)
        (system as? AbstractWorldSystem)?.takeIf { it.boundWorldOrNull() == null }?.bindTo(this)
        // 6. record in step order
        val record = InstalledSystemRecord(system, slot, role)
        insertInStepOrder(record)
        log.info("World installed a {} (slot={}, role={})", system::class.simpleName, slot, role?.java?.name)
        // 7 + 8. notify; a throw rolls this attempt back and is rethrown UNCHANGED
        runCatching { system.onInstalled() }
            .onFailure { cause -> rollBack(record, cause) }
            .getOrThrow()
    }

    /**
     * [installSystem]'s binding check: throws [IllegalArgumentException] if [system] is bound to, or reports, a
     * [World] other than this one. A direct implementor's throwing [WorldSystem.world] getter
     * propagates unchanged.
     */
    @OptIn(ExperimentalGameToolsApi::class)
    private fun requireBindableHere(system: WorldSystem) {
        when (system) {
            is AbstractWorldSystem -> require(system.boundWorldOrNull().let { it == null || it === this }) {
                "system $system is already bound to a different World" // bound for life elsewhere
            }
            else -> require(system.world === this) { // a throwing getter propagates
                "system $system reports a world that is not this World"
            }
        }
    }

    /**
     * [installSystem]'s roll-back, run when [WorldSystem.onInstalled] threw [cause]. Cannot fail. Removes
     * *this* [record] (by identity) if it is still present - never another record of the same
     * instance, e.g. one a nested re-install created - and marks it inactive. Keeps the binding
     * and does not call [WorldSystem.onUninstalled].
     */
    @OptIn(ExperimentalGameToolsApi::class)
    private fun rollBack(record: InstalledSystemRecord, cause: Throwable) {
        val index = installedRecords.indexOfFirst { it === record }
        if (index != -1) installedRecords.removeAt(index)
        record.active = false
        // No throwable argument: the exception propagates to the caller, who owns it.
        log.warn(
            "World rolled back the install of a {} (slot={}, role={}): onInstalled() threw {}: {}",
            record.system::class.simpleName, record.slot, record.role?.java?.name, cause.javaClass.name, cause.message,
        )
    }

    /**
     * Removes [system] from this [World]'s installed systems, then calls
     * [WorldSystem.onUninstalled] once. Idempotent: a [system] that is not currently installed is
     * a no-op.
     *
     * [system] is removed - absent from [installedSystems] and from the next [stepSystems] pass -
     * *before* [WorldSystem.onUninstalled] runs. If [WorldSystem.onUninstalled] throws, [system]
     * stays removed either way and the exception propagates to the caller unchanged.
     *
     * The binding is kept: [system] may be installed again on this same [World], and is rejected
     * by any other [World]. Called from inside [system]'s own [WorldSystem.onInstalled], this
     * genuinely uninstalls it (legal, but discouraged).
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
     * Any exception [WorldSystem.onUninstalled] throws is rethrown unchanged, after [system] has
     * been removed.
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
        log.info("World uninstalled a {} (slot={}, role={})", system::class.simpleName, record.slot, record.role?.java?.name)
        system.onUninstalled()
    }

    /**
     * The [WorldSystem]s currently installed on this [World], tier 1 (by [CoreSystemSlot.order])
     * then tier 2 (in [installSystem] order) - the exact order [stepSystems] steps them in. A
     * fresh copy on every read; mutating it does not affect this [World]'s registry. *Contains* a
     * system while its own [WorldSystem.onInstalled] is running (it is recorded first); does not
     * contain one whose install was rolled back. Empty for a [World] with nothing installed.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     */
    @ExperimentalGameToolsApi
    val installedSystems: List<WorldSystem>
        get() = installedRecords.map { it.system }

    /**
     * The installed system that declared exactly [role] as its [WorldSystem.uniqueRole], as a
     * [Result].
     *
     * **Exact key.** The lookup compares [role] with the role each system declared at install
     * (`==`); it is not an `is` check. Given `open class Base : AbstractWorldSystem()` and
     * `class Derived : Base()`:
     * - a system that declared no role is never found, under any type;
     * - a `Derived` that declared `Base::class` is found by `systemOf(Base::class)`, **not** by
     *   `systemOf(Derived::class)`;
     * - a substitute that declared a different role is not found under this one, even if it is an
     *   instance of [role].
     *
     * At most one system can match, because [installSystem] rejects a second holder of a role.
     * A pure read: no hook runs, nothing is logged, and the registry is unchanged.
     *
     * It reads the live registry, never a step pass's snapshot: it finds a system from inside its
     * own [WorldSystem.onInstalled]; it does not find one whose install was rolled back or which
     * was uninstalled; during a [stepSystems] pass, a system uninstalled earlier in the pass is not
     * found, and one installed mid-pass is found although it steps only from the next pass.
     *
     * Using it:
     * - Look a peer up when you use it, or be ready for it to disappear: a reference cached in
     *   [WorldSystem.onInstalled] goes stale if the peer is uninstalled later, and nothing
     *   notifies the holder.
     * - A system that requires a peer unwraps the lookup in [WorldSystem.onInstalled] with
     *   `getOrThrow()`: if the peer is absent, the [MissingWorldSystemException] naming the
     *   missing role is thrown, the roll-back undoes the install, and nothing stays recorded. The
     *   exception carries no stack trace, so its message - and the roll-back's log line - is the
     *   diagnostic. The peer must already be installed when the lookup runs - the lookup neither
     *   waits for nor orders installs.
     * - A system that can live without a peer unwraps it with `getOrNull()`, `onSuccess { ... }`
     *   or `fold(...)`. A miss is cheap but not free: it allocates two small objects - the
     *   [Result] failure wrapper and a stackless [MissingWorldSystemException] - with no stack
     *   walk, so polling for an absent optional peer on every [WorldSystem.step] costs those two
     *   allocations each frame.
     *
     * Single-threaded, like every other [World] member; callable from every hook and from
     * [WorldSystem.step]. Experimental - may change incompatibly in a Feature release until it
     * graduates; see [ExperimentalGameToolsApi].
     *
     * @param role the exact role a system declared as its [WorldSystem.uniqueRole]
     * @return [Result.success] with the installed system that declared exactly [role], or
     *   [Result.failure] carrying a [MissingWorldSystemException] whose
     *   [MissingWorldSystemException.role] is [role] if none did
     */
    @ExperimentalGameToolsApi
    fun <T : WorldSystem> systemOf(role: KClass<T>): Result<T> =
        installedRecords.firstOrNull { it.role == role } // exact key on the RECORDED role
            ?.let { Result.success(role.java.cast(it.system)) } // sound: install checked role.isInstance(system)
            ?: Result.failure(MissingWorldSystemException(role)) // stackless; its message names role.java.name

    /**
     * The installed system that declared exactly [T] as its [WorldSystem.uniqueRole], as a
     * [Result] - the same lookup as `systemOf(T::class)`. It reads like `filterIsInstance<T>()`,
     * but it is the same exact-key lookup: it finds only a system that *declared* [T], never one
     * that merely is a [T].
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     *
     * @param T the exact role a system declared as its [WorldSystem.uniqueRole], and also the
     *   result's type
     * @return [Result.success] with the installed system that declared exactly [T], or
     *   [Result.failure] carrying a [MissingWorldSystemException] whose
     *   [MissingWorldSystemException.role] is `T::class` if none did
     * @see systemOf
     */
    @ExperimentalGameToolsApi
    inline fun <reified T : WorldSystem> systemOf(): Result<T> = systemOf(T::class)

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
     * A call from inside a system's [WorldSystem.onInstalled], when no pass is running, steps
     * that system before its [WorldSystem.onInstalled] returns - legal, but discouraged.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     *
     * Any exception a [WorldSystem.step] throws is rethrown unchanged, ending the pass.
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
            snapshot.forEach { record -> if (record.active) record.system.step() }
        } finally {
            stepping = false
        }
    }
    //endregion
}
