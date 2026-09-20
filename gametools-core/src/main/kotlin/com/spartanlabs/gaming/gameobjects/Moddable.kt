package com.spartanlabs.gaming.gameobjects

import com.spartanlabs.gaming.gameobjects.combat.StatMod

/**
 * A stat a [com.spartanlabs.gaming.gameobjects.combat.StatMod] can be layered onto and later peeled back off - implemented by both
 * [com.spartanlabs.gaming.gameobjects.combat.ModularStat] and [com.spartanlabs.gaming.gameobjects.combat.CombinedStat].
 *
 * It lets a [GameObject] expose its stats by name through [GameObject.stats] without the
 * caller (or a [com.spartanlabs.gaming.gameobjects.combat.Buff]) needing to know which of the two concrete kinds it is holding.
 */
interface Moddable {

    /** Applies [mod] to this stat, taking its [com.spartanlabs.gaming.gameobjects.combat.StatMod.stackingType] into account. */
    fun applyMod(mod: StatMod)

    /** Removes every applied mod sharing [mod]'s [StatMod.name] from this stat. */
    fun removeMod(mod: StatMod)
}
