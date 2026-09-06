package com.spartanlabs.gaming.gameobjects

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

/**
 * A temporary effect layered onto a [GameObject]: for [durationTicks] ticks it applies
 * [StatMod]s to the object's named [GameObject.stats] and/or holds a set of [Capability]s
 * suppressed, then reverts both.
 *
 * Attach one with [GameObject.applyBuff] and it is driven automatically: [GameObject.tick]
 * runs [onTick], counts [durationTicks] down, and once it hits zero removes the buff - undoing
 * its mods, freeing its capabilities, and firing [onExpired]. Remove one early with
 * [GameObject.removeBuff] or [GameObject.dispel].
 *
 * Stacking is the caller's concern. Two buffs whose [statMods] carry a [StatMod] of the same
 * [StatMod.name] on the same stat share fate on removal, because [Moddable.removeMod] drops
 * every mod of that name; give concurrent buffs distinct mod names, or lean on [StatMod]'s
 * own [StatMod.StackingType] deliberately. A capability stays suppressed while *any* active
 * buff lists it, so overlapping suppressors resolve on their own.
 *
 * Subclass to add behaviour: override [onApplied] / [onTick] / [onExpired] for effects such
 * as damage-over-time or a visual cue.
 *
 * @property name the key this buff is tracked and dispelled by
 * @property durationTicks ticks left before the buff expires; decremented each
 *   [GameObject.tick]. A negative value never counts down, so the buff lasts until it is
 *   removed explicitly.
 * @property statMods stat key (as exposed by [GameObject.stats]) to the [StatMod] applied for
 *   the duration; an entry whose key the object does not expose is logged and skipped.
 * @property suppressedCapabilities the capabilities held unusable for the duration.
 */
open class Buff(
    val name: String,
    var durationTicks: Int,
    val statMods: Map<String, StatMod> = emptyMap(),
    val suppressedCapabilities: Set<Capability> = emptySet(),
) {

    /** `true` once a counting-down buff has run out; always `false` while [durationTicks] is negative. */
    val isExpired: Boolean get() = durationTicks == 0

    /** Hook run right after this buff is attached and its mods and suppressions take effect. Does nothing by default. */
    open fun onApplied(target: GameObject) {}

    /** Hook run once per [GameObject.tick] while this buff is active, before it counts down. Does nothing by default. */
    open fun onTick(target: GameObject) {}

    /** Hook run right after this buff is removed and its mods and suppressions are reverted. Does nothing by default. */
    open fun onExpired(target: GameObject) {}
}

/**
 * An immutable, serializable copy of a [Buff]'s client-facing state: what it is, how long it
 * has left, and which capabilities it is holding down. Carried by [GameObjectSnapshot] so a
 * client can render buff timers and grey out disabled abilities.
 *
 * @property name the buff's [Buff.name]
 * @property durationTicks the buff's [Buff.durationTicks] at snapshot time (negative means indefinite)
 * @property suppressedCapabilities the [Capability.id]s the buff is suppressing at snapshot time
 */
@Serializable
data class BuffSnapshot(
    val name: String,
    val durationTicks: Int,
    val suppressedCapabilities: List<String>) {

    companion object {
        /** Takes a snapshot of [buff]'s name, remaining duration, and suppressed capability ids. */
        infix fun from(buff: Buff): BuffSnapshot = BuffSnapshot(
            name = buff.name,
            durationTicks = buff.durationTicks,
            suppressedCapabilities = buff.suppressedCapabilities.map { it.id }
        )
    }
}
