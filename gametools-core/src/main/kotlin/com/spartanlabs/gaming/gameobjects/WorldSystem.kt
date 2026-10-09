package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.reflect.KClass
//endregion

/**
 * Opt-in, per-frame or event-driven add-on behaviour for a [World], installed via
 * [World.installSystem] and stepped via [World.stepSystems].
 *
 * A [WorldSystem] may claim a library-reserved slot via [coreSlot] for a guaranteed relative step
 * order (tier 1), or default to `null` (tier 2) for trust-the-caller install order, always
 * stepped after every tier-1 system.
 *
 * **A system serves exactly one [World] for its life.** It is bound by the first
 * [World.installSystem] call whose checks pass - before [onInstalled] runs - and the binding is
 * kept even if that install is rolled back. It is never unbound afterwards - not by
 * [World.uninstallSystem], and not by a rolled-back [onInstalled]. A system is therefore not
 * reusable across [World]s: build one per [World] (a factory is the natural shape). A bound system
 * keeps its [World] reachable even after it is uninstalled, so a long-lived or `static` system pins
 * the whole [World] in memory.
 *
 * **Uniqueness is declared, not checked by hand.** A [World] rejects installing the same instance
 * twice and a second claimant of the same [CoreSystemSlot], but not two distinct instances of the
 * same class. A system that must be unique per [World] declares a [uniqueRole], and the [World]
 * rejects a second installed system holding the same role before any of its code runs.
 *
 * Extend [AbstractWorldSystem] for the common case - it holds the binding for you. Implementing
 * this interface directly is also supported: supply [world] yourself, and [World.installSystem]
 * then requires that [world] is the very [World] (`===`) it is being installed on.
 *
 * Single-threaded, like [World]: every hook runs on the thread driving the [World] this system
 * serves, which is also why the binding needs no synchronisation.
 *
 * Implementing this interface requires opting in to [ExperimentalGameToolsApi], and so does
 * reading [coreSlot] or [uniqueRole]; the whole interface may change incompatibly in a Feature
 * release until it graduates.
 *
 * @see AbstractWorldSystem
 */
@SubclassOptInRequired(ExperimentalGameToolsApi::class)
interface WorldSystem {

    /**
     * The one [World] this system serves. Read-only to a consumer, and constant once it has
     * returned a value.
     *
     * An [AbstractWorldSystem] is bound by the first [World.installSystem] call whose checks pass -
     * before [onInstalled] runs - and kept even if that install is rolled back; reading this before
     * then throws. A direct implementor supplies this value before install (and may
     * throw from it while it has none yet - [World.installSystem] then propagates that exception
     * with nothing changed).
     *
     * @throws IllegalStateException for an [AbstractWorldSystem], if read before any
     *   [World.installSystem] call for this system has passed its checks
     */
    val world: World

    /**
     * A **notification**, not a gate: returning from it completes the install. Runs once per
     * install, after every [World.installSystem] check passed and this system was bound and
     * recorded - so [world]'s [World.installedSystems] contains this system while this call runs,
     * and this call may read [world]. Default: no-op.
     *
     * If this **throws**, the [World] removes this attempt's record (releasing its slot and role),
     * does **not** call [onUninstalled], keeps the binding, and rethrows the original exception
     * unchanged - so release whatever you acquired before throwing. Helper systems this call
     * installed on [world] stay installed.
     *
     * Legal but discouraged: calling [World.stepSystems] on [world] from here (when no step pass
     * is already running) steps this system before this call has returned; calling
     * [World.uninstallSystem] with this system removes it and runs [onUninstalled] before this
     * call returns. Re-installing this same system, or a second claimant of its [coreSlot], from
     * here is rejected with [IllegalArgumentException] - it is already recorded.
     */
    fun onInstalled() {}

    /**
     * Runs once, immediately after [World.uninstallSystem] removed this system from [world]. Must
     * fully undo whatever [onInstalled] acquired, because the same system may be installed again
     * on the same [World] (the binding is kept). Never called for a system that was never
     * installed, or whose install was rolled back. Default: no-op.
     */
    fun onUninstalled() {}

    /**
     * Runs once per [World.stepSystems] call while this system is installed, in step order.
     * Default: no-op. An exception thrown here propagates to the [World.stepSystems] caller and
     * ends that pass early - systems later in the pass do not step this time. Calling
     * [World.stepSystems] from inside this call throws [IllegalStateException].
     * [World.installSystem] and [World.uninstallSystem] are legal from inside this call - see
     * their own KDoc for how each affects the pass in progress.
     */
    fun step() {}

    /**
     * The tier-1 [CoreSystemSlot] this system claims, or `null` (the default) for tier 2 - always
     * stepped after every tier-1 system, in install order. Read exactly once, by
     * [World.installSystem], at install time - after the binding check and before [uniqueRole];
     * must return the same value for the lifetime of this object - changing what it returns
     * afterward has no effect on an already-installed system.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     */
    @ExperimentalGameToolsApi
    val coreSlot: CoreSystemSlot?
        get() = null

    /**
     * The role this system holds uniquely on its [World], or `null` (the default) for no
     * uniqueness constraint.
     *
     * Read exactly once, by [World.installSystem]; must be constant for the lifetime of this
     * object. A non-null role must be a supertype of this system (`role.isInstance(this)`),
     * otherwise the install is rejected with [IllegalArgumentException]. Two installed systems may
     * not declare equal roles (`==`): the second is **rejected, never substituted** for the first.
     *
     * The granularity is the author's choice: the exact class
     * (`override val uniqueRole get() = Me::class`) or a base type or interface shared by a
     * family of systems. For a system that also claims a [coreSlot], the slot check runs first
     * and guards the same thing; that overlap is accepted.
     *
     * Experimental - may change incompatibly in a Feature release until it graduates; see
     * [ExperimentalGameToolsApi].
     */
    @ExperimentalGameToolsApi
    val uniqueRole: KClass<out WorldSystem>?
        get() = null
}
