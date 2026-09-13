package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
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
 * over every object it owns, an [events] bus that reports what happens each tick, and a
 * seeded [rng] source that makes those ticks reproducible.
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
 * Given the same [seed] and the same sequence of external calls, two worlds produce the same
 * result.
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
}
