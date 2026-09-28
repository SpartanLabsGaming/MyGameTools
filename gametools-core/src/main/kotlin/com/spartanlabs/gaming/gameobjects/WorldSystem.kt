package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
//endregion

/**
 * Opt-in, per-frame or event-driven add-on behaviour for a [World], installed via
 * [World.installSystem] and stepped via [World.stepSystems].
 *
 * A [WorldSystem] may claim a library-reserved slot via [coreSlot] for a guaranteed relative step
 * order (tier 1), or default to `null` (tier 2) for trust-the-caller install order, always
 * stepped after every tier-1 system. A [World] rejects installing the same instance twice and a
 * second claimant of the same [CoreSystemSlot], but not two distinct instances of the same class;
 * an implementation that must be unique per [World] checks [World.installedSystems] inside its own
 * [installOn] - it never contains this system while [installOn] is running.
 *
 * A single [WorldSystem] instance may be installed on more than one [World]: every hook receives
 * the relevant [World] as a parameter and this interface holds no back-reference to any one
 * [World]. An implementation that keeps per-[World] state keys it by the [World] instance, or
 * rejects a second, different [World] from its own [installOn] with [IllegalStateException].
 *
 * Single-threaded, like [World]: every hook runs on the thread driving the [World] it is given.
 *
 * Implementing this interface requires opting in to [ExperimentalGameToolsApi], and so does reading
 * [coreSlot]; the whole interface may change incompatibly in a Feature release until it graduates.
 * Calling [installOn], [uninstallFrom] or [step] on a reference obtained some other way needs no
 * opt-in of its own.
 */
@SubclassOptInRequired(ExperimentalGameToolsApi::class)
interface WorldSystem {

    /**
     * Runs once, immediately after [World.installSystem]'s checks pass and before it records this
     * system - [world]'s [World.installedSystems] does not contain this system while this call is
     * running. Must be failure-atomic: if this throws, [world] records nothing for this system and
     * never calls [uninstallFrom] for this attempt, so an implementation must leave nothing behind,
     * including any helper [WorldSystem] it installed on [world] itself. Installing this same
     * system again, or a system claiming the same [coreSlot], from inside this call is rejected by
     * [world] with [IllegalArgumentException].
     *
     * @param world the [World] this system is being installed on
     */
    fun installOn(world: World)

    /**
     * Runs once, immediately after [World.uninstallSystem] removes this system from [world] -
     * releases whatever [installOn] acquired. Default: no-op. Never called for a system that was
     * never installed on [world] (an [World.uninstallSystem] call for one is an idempotent no-op).
     *
     * @param world the [World] this system is being uninstalled from
     */
    fun uninstallFrom(world: World) {}

    /**
     * Runs once per [World.stepSystems] call while this system is installed on [world], in step
     * order. Default: no-op. An exception thrown here propagates to the [World.stepSystems] caller
     * and ends that pass early - systems later in the pass do not step this time. Calling
     * [World.stepSystems] on [world] from inside this call throws [IllegalStateException].
     * [World.installSystem] and [World.uninstallSystem] are legal from inside this call - see
     * their own KDoc for how each affects the pass in progress.
     *
     * @param world the [World] driving this step
     */
    fun step(world: World) {}

    /**
     * The tier-1 [CoreSystemSlot] this system claims, or `null` (the default) for tier 2 - always
     * stepped after every tier-1 system, in install order. Read exactly once, by
     * [World.installSystem], at install time; must return the same value for the lifetime of this
     * object - changing what it returns afterward has no effect on an already-installed system.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     */
    @ExperimentalGameToolsApi
    val coreSlot: CoreSystemSlot?
        get() = null
}
