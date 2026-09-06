package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.EventBus
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.spatial.Quadtree
//endregion

/**
 * A container for everything the game is simulating: a flat list of [gameObjects], a
 * [quadtree] spatial index over the [VisibleObject]s among them, an [EntityId] index ([byId])
 * over every object it owns, and an [events] bus that reports what happens each tick.
 *
 * [World] does not run itself - an external game loop calls [tick] once per frame. Each tick
 * rebuilds [quadtree] from the current positions of the owned objects, re-indexes [byId],
 * announces newly joined objects, then advances every owned object by one step and finally
 * drops the ones queued for removal. The indexes are rebuilt first so that an object's
 * [GameObject.tick] can query indexes that stay consistent for the whole frame.
 */
class World {

    /** Every object this world owns, visible or not, in insertion order. */
    val gameObjects: ArrayList<GameObject> = ArrayList()

    /**
     * Objects to drop from [gameObjects] at the end of the current [tick]. An object adds
     * itself here during its own tick - a dying [Alive] with [Alive.DeathResponse.REMOVAL]
     * does - and [tick] removes them once the pass is done, so removal never disturbs iteration.
     */
    val removeList: ArrayList<GameObject> = ArrayList()

    /**
     * Spatial index of the [VisibleObject]s in [gameObjects], keyed by world position and
     * rebuilt from scratch at the start of every [tick]. Between ticks it reflects the
     * positions the objects held when the last tick began.
     */
    val quadtree: Quadtree<Double, VisibleObject> = Quadtree()

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
     * Adds [gameObject] to [gameObjects] and numbers it (see [byId]); for an [Alive] it also
     * sets [Alive.world] so a [Alive.DeathResponse.REMOVAL] death can reach [removeList].
     * Adding straight to [gameObjects] still works, but then an [Alive] needs its [Alive.world]
     * set by hand and the object is not resolvable through [byId] until the next [tick].
     *
     * @param gameObject the object to bring into the world
     */
    fun add(gameObject: GameObject) {
        gameObjects.add(gameObject)
        enrol(gameObject)
        if (gameObject is Alive) gameObject.world = this
        announce(gameObject)
    }

    /**
     * Advances the world by one frame: rebuilds [quadtree] and [byId] from [gameObjects],
     * [GameObject.tick]s every owned object, then drops everything queued in [removeList].
     *
     * Objects are ticked over a snapshot of [gameObjects], so an object may add to [gameObjects]
     * or [removeList] during its own tick without disturbing the current pass. An object added
     * mid-pass is numbered and indexed by the *next* tick's rebuild, not this one.
     */
    fun tick() {
        rebuildQuadtree()
        reindexEntities()

        val ticking = gameObjects.toList()
        log.debug("World tick: advancing {} game object(s)", ticking.size)
        ticking.forEach(GameObject::tick)

        if (removeList.isNotEmpty()) {
            log.debug("World removing {} game object(s)", removeList.size)
            val removed = removeList.toList().distinct()
            gameObjects.removeAll(removed.toSet())
            removed.forEach { gone ->
                byId.remove(gone.entityId)
                announced.remove(gone.entityId)
                events.publish(GameEvent.EntityRemoved(gone))
            }
            removeList.clear()
        }
    }

    /** Clears [quadtree] and re-inserts every [VisibleObject] in [gameObjects] at its current position. */
    private fun rebuildQuadtree() {
        quadtree.clear()
        val visible = gameObjects.filterIsInstance<VisibleObject>()
        visible.forEach { quadtree.insert(it.location.x, it.location.y, it) }
        log.debug("World rebuilt its quadtree from {} visible object(s)", visible.size)
    }
}
