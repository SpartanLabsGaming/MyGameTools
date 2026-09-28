package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
//endregion

/**
 * A library-reserved "tier 1" ordering slot a [WorldSystem] may claim via [WorldSystem.coreSlot]
 * for a guaranteed relative step order, regardless of install order.
 *
 * `sealed` so only this module can declare a slot - a consumer cannot mint a competing
 * "core-looking" slot; every library-defined slot is a [CoreWorldSystemSlot] constant. A
 * consumer's own [WorldSystem] may still legitimately return an *existing* [CoreWorldSystemSlot]
 * value (a deliberate replacement of a shipped adapter) - [World.installSystem]'s
 * one-claimant-per-slot check makes that safe.
 *
 * Only *relative* [order] across slots is contractual; a future built-in slot may be inserted
 * between two existing ones, renumbering them - do not compare [order] against a literal
 * constant. Library-defined slots always have pairwise-distinct [order] values, so the relative
 * step order of any two claimed slots never depends on install order.
 *
 * May change incompatibly in a Feature release until it graduates - see [ExperimentalGameToolsApi].
 */
@ExperimentalGameToolsApi
sealed interface CoreSystemSlot {
    /** This slot's position relative to every other [CoreSystemSlot]; a lower value steps first. */
    val order: Int
}

/**
 * The library-defined [CoreSystemSlot]s, in ascending [order]. New constants may be added in a
 * Feature release - do not write an exhaustive `when` over this enum without an `else` branch.
 *
 * @property order see [CoreSystemSlot.order]
 */
@ExperimentalGameToolsApi
enum class CoreWorldSystemSlot(override val order: Int) : CoreSystemSlot {
    /** Reserved for a future physics `WorldSystem` adapter. */
    PHYSICS(0),

    /** Reserved for a future zone-membership `WorldSystem` adapter. */
    ZONE(1),
}
