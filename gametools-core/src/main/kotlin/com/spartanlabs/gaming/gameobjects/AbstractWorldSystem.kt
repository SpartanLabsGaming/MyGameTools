package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
//endregion

/**
 * The ready-made base class for a [WorldSystem]: it holds the system's [World] binding, so a
 * subclass only overrides the hooks it needs and reads [world] from them.
 *
 * ```kotlin
 * @OptIn(ExperimentalGameToolsApi::class)
 * class Counter : AbstractWorldSystem() {
 *     var spawned = 0
 *         private set
 *     private var subscription: EventBus.Subscription? = null
 *
 *     override val uniqueRole get() = Counter::class          // at most one Counter per World
 *
 *     override fun onInstalled() {
 *         subscription = world.events.subscribe { if (it is GameEvent.EntitySpawned) spawned++ }
 *     }
 *
 *     override fun onUninstalled() {
 *         subscription?.cancel()
 *         subscription = null
 *     }
 * }
 * ```
 *
 * A helper, not a seam: [WorldSystem] is the substitution point, and a system that cannot extend
 * this class implements [WorldSystem] directly and supplies [world] itself.
 *
 * Constructible before it has a [World]: it is bound by the first [World.installSystem] call whose
 * checks pass - before [WorldSystem.onInstalled] runs - and kept even if that install is rolled
 * back. The binding is **for life** - it also survives [World.uninstallSystem] - so a re-install
 * is accepted only by the same [World]. Reading [world] before any [World.installSystem] call for
 * this system has passed its checks throws [IllegalStateException].
 *
 * Single-threaded, like [World]. Subclassing requires opting in to [ExperimentalGameToolsApi];
 * may change incompatibly in a Feature release until it graduates.
 */
@SubclassOptInRequired(ExperimentalGameToolsApi::class)
abstract class AbstractWorldSystem : WorldSystem {

    //region BINDING
    // Private storage on purpose: a non-private `lateinit` would compile to a public JVM field.
    // Written at most once (by bindTo), never reset.
    private lateinit var bound: World

    /**
     * The one [World] this system serves - bound by the first [World.installSystem] call whose
     * checks pass, before [WorldSystem.onInstalled] runs, and kept even if that install is rolled
     * back. `final`, so a subclass cannot report a different [World] than the one it is bound to.
     *
     * @throws IllegalStateException if read before any [World.installSystem] call for this system
     *   has passed its checks
     */
    final override val world: World
        get() {
            check(::bound.isInitialized) {
                "${this::class.simpleName ?: this::class.java.name}.world was read before any World.installSystem call for this system passed its checks"
            }
            return bound
        }

    /**
     * Binds this system to [world] - write-once: stores it when unbound, does nothing when already
     * bound to the same [world]. Called only by [World.installSystem].
     *
     * @param world the [World] this system is being installed on
     * @throws IllegalStateException if this system is already bound to a different [World]
     *   (unreachable through [World.installSystem], which rejects that case first)
     */
    @JvmSynthetic
    internal fun bindTo(world: World) {
        check(!::bound.isInitialized || bound === world) {
            "${this::class.simpleName ?: this::class.java.name} is already bound to a different World"
        }
        if (!::bound.isInitialized) bound = world
    }

    /**
     * The [World] this system is bound to, or `null` while it is unbound. Never throws.
     *
     * @return the bound [World], or `null` if no [World.installSystem] call for this system has
     *   passed its checks yet
     */
    @JvmSynthetic
    internal fun boundWorldOrNull(): World? = if (::bound.isInitialized) bound else null
    //endregion
}
