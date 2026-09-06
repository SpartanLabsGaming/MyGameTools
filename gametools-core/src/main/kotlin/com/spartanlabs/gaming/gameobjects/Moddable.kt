package com.spartanlabs.gaming.gameobjects

/**
 * A stat a [StatMod] can be layered onto and later peeled back off - implemented by both
 * [ModularStat] and [CombinedStat].
 *
 * It lets a [GameObject] expose its stats by name through [GameObject.stats] without the
 * caller (or a [Buff]) needing to know which of the two concrete kinds it is holding.
 */
interface Moddable {

    /** Applies [mod] to this stat, taking its [StatMod.stackingType] into account. */
    fun applyMod(mod: StatMod)

    /** Removes every applied mod sharing [mod]'s [StatMod.name] from this stat. */
    fun removeMod(mod: StatMod)
}
