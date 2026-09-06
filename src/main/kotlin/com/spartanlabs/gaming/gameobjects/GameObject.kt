package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.serializations.PointSnapshot
//endregion

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

//region 4. Programming Infrastructure and Support
// 4.1 Logging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//endregion

/** Shared slf4j logger for the game-object layer; bound to the facade only, never an implementation. */
internal val log: Logger = LoggerFactory.getLogger("com.spartanlabs.gaming.gameobjects")

/**
 * The root of every entity the game simulates, visible or not.
 *
 * A game object has a [location], an [active] flag, and a per-frame update hook. Subclasses
 * put their per-frame behaviour in [onUpdate] (calling `super.onUpdate()` first); the owning
 * game loop calls [tick], which runs [onUpdate] only while the object is [active].
 *
 * Two things it carries that subtypes build on:
 * - [capabilities] - the set of [Capability]s the type inherently has (an [Actor] can move, an
 *   [Alive] can also attack). [can] answers whether one is usable *right now*, taking any
 *   suppressing [Buff] into account, and each subtype gates its own per-tick work on it.
 * - [buffs] - temporary effects attached with [applyBuff]. For their duration a [Buff] layers
 *   [StatMod]s onto the object's named [stats] and/or holds [Capability]s suppressed; [tick]
 *   counts each one down and reverts it when it runs out.
 *
 * @param location the object's position in world space; defaults to the origin.
 */
abstract class GameObject(val location: Point = Point()) {

    /**
     * Creates a game object at ([x], [y]).
     * @param x the horizontal position in world space
     * @param y the vertical position in world space
     */
    constructor(x: Double, y: Double) : this(Point(x = x, y = y))

    /**
     * This object's stable identity within the [World] that owns it, or [EntityId.UNASSIGNED]
     * until a world takes ownership of it - through [World.add], or by finding it in
     * [World.gameObjects] at the top of a [World.tick].
     *
     * Assigned exactly once and never changed afterwards: re-adding the same instance, even to
     * a different world, keeps the id it was first given. Only a [World] assigns it.
     */
    var entityId: EntityId = EntityId.UNASSIGNED
        internal set

    /**
     * Whether this object participates in the simulation. `true` by default; while `false`,
     * [tick] is a no-op, so the object neither updates nor moves until it is reactivated.
     *
     * [com.spartanlabs.gaming.gameobjects.VisibleObject] overrides this to also keep its
     * `visible` flag in step with it.
     */
    open var active: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                log.debug("A {} became {}", this::class.simpleName, if (value) "active" else "inactive")
            }
        }

    /**
     * Advances this object by one simulation step, unless it is inactive.
     *
     * Called once per frame by the owning game loop (which this library does not provide).
     * While [active] is `true` it, in order: fires each active [Buff]'s [Buff.onTick] and
     * counts it down, runs [onUpdate], then removes any buff that has just run out (undoing
     * its stat mods and freeing its suppressed capabilities). A buff applied with a duration
     * of `n` is therefore in force for exactly `n` of these updates before it is dropped.
     * Override [onUpdate], not this, to add per-frame behaviour.
     */
    fun tick() {
        if (active) {
            ageBuffs()
            onUpdate()
            pruneExpiredBuffs()
        }
    }

    /**
     * This object's own per-frame logic, run by [tick] while [active].
     *
     * The base implementation does nothing; a subclass overrides it and calls
     * `super.onUpdate()` first so an ancestor's per-frame work still runs.
     */
    protected open fun onUpdate() {}

    /**
     * The straight-line distance from this object's [location] to [other]'s, or the failure from
     * [Point.distanceFrom] when either position holds a NaN coordinate.
     *
     * @param other the object to measure to
     */
    infix fun distanceFrom(other: GameObject): Result<Double> = location distanceFrom other.location

    //region CAPABILITIES
    /**
     * The [Capability]s this object type inherently has. Empty for a bare [GameObject]; a
     * subtype overrides this as `super.capabilities + ...` to add its own (see
     * [com.spartanlabs.gaming.gameobjects.Actor] and [com.spartanlabs.gaming.gameobjects.Alive]).
     *
     * This is only what the type *can* do in principle - use [can] to ask whether a capability
     * is usable right now, which also accounts for any suppressing [Buff].
     */
    open val capabilities: Set<Capability> = emptySet()

    /**
     * Whether [capability] is usable on this object at this moment: it must be one of the
     * object's [capabilities] and not currently held down by any active [Buff].
     *
     * The built-in hierarchy gates its own per-tick work on this - an [Actor] only moves while
     * `can(CoreCapability.MOVE)`, an [Alive] only runs its attack cycle while
     * `can(CoreCapability.ATTACK)` - and a consumer's subclass should do the same for its own
     * capabilities.
     *
     * @param capability the capability to test
     */
    fun can(capability: Capability): Boolean =
        capability in capabilities && activeBuffs.none { capability in it.suppressedCapabilities }

    /**
     * This object's stats keyed by the name a [Buff] addresses them by. Empty for a bare
     * [GameObject]; a subtype overrides this as `super.stats + ...` to expose its own
     * ([com.spartanlabs.gaming.gameobjects.Actor] adds `"speed"`,
     * [com.spartanlabs.gaming.gameobjects.Alive] adds `"health"`, `"damage"`, and its other
     * combat stats).
     *
     * A getter rather than a stored map, so it keeps tracking a stat property that is later
     * reassigned wholesale.
     */
    open val stats: Map<String, Moddable> get() = emptyMap()
    //endregion

    //region BUFFS
    /** Backing store for [buffs]; mutated only through [applyBuff] / [removeBuff]. */
    private val activeBuffs: MutableList<Buff> = mutableListOf()

    /** The [Buff]s currently on this object, in the order they were applied. A read-only view. */
    val buffs: List<Buff> get() = activeBuffs.toList()

    /**
     * Attaches [buff]: applies each of its [Buff.statMods] to the matching entry in [stats]
     * (a mod whose key this object does not expose is logged and skipped), records its
     * [Buff.suppressedCapabilities], and fires [Buff.onApplied]. From here [tick] drives it
     * until it expires.
     *
     * Call after construction - the object's [stats] and [capabilities] must be in place.
     *
     * @param buff the effect to attach
     */
    fun applyBuff(buff: Buff) {
        activeBuffs.add(buff)
        buff.statMods.forEach { (key, mod) ->
            val stat = stats[key]
            if (stat == null) log.warn("Buff '{}' targets unknown stat '{}' on a {}; that mod is skipped", buff.name, key, this::class.simpleName)
            else stat.applyMod(mod)
        }
        log.debug("Buff '{}' applied to a {} for {} tick(s)", buff.name, this::class.simpleName, buff.durationTicks)
        buff.onApplied(this)
    }

    /**
     * Detaches [buff] early: reverts each of its [Buff.statMods] via [Moddable.removeMod],
     * releases its capability suppressions, and fires [Buff.onExpired]. A no-op if the buff
     * is not attached.
     *
     * @param buff the exact buff instance to remove
     */
    fun removeBuff(buff: Buff) {
        if (!activeBuffs.remove(buff)) return
        buff.statMods.forEach { (key, mod) -> stats[key]?.removeMod(mod) }
        log.debug("Buff '{}' removed from a {}", buff.name, this::class.simpleName)
        buff.onExpired(this)
    }

    /**
     * Removes every attached buff whose [Buff.name] equals [name], each through [removeBuff].
     *
     * @param name the buff name to clear
     * @return how many buffs were removed
     */
    fun dispel(name: String): Int =
        activeBuffs.filter { it.name == name }.onEach { removeBuff(it) }.size

    /**
     * Run by [tick] before [onUpdate]: fires [Buff.onTick] on every active buff and decrements
     * the [Buff.durationTicks] of the ones still counting down (a negative duration is left
     * alone, so that buff never expires on its own).
     */
    private fun ageBuffs() {
        activeBuffs.toList().forEach { buff ->
            buff.onTick(this)
            if (buff.durationTicks > 0) buff.durationTicks--
        }
    }

    /** Run by [tick] after [onUpdate]: removes, via [removeBuff], every buff that has run out this tick. */
    private fun pruneExpiredBuffs() {
        activeBuffs.filter { it.isExpired }.forEach { removeBuff(it) }
    }
    //endregion
}

/**
 * An immutable, serializable copy of a [GameObject]'s networkable state: its [location] and
 * its active [buffs].
 *
 * @property location the object's position at the moment the snapshot was taken
 * @property buffs the object's active buffs at snapshot time, in application order; defaults
 *   to empty so older payloads without the field still decode
 */
@Serializable
data class GameObjectSnapshot(
    val location: PointSnapshot,
    val buffs: List<BuffSnapshot> = emptyList()) {
    companion object {
        /** Takes a snapshot of [gameObject]'s current state, its active buffs included. */
        infix fun from(gameObject: GameObject): GameObjectSnapshot =
            GameObjectSnapshot(
                PointSnapshot.from(gameObject.location),
                buffs = gameObject.buffs.map { BuffSnapshot from it }
            )
    }
}
