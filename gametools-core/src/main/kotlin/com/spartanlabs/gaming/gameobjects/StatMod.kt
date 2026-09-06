package com.spartanlabs.gaming.gameobjects

/**
 * A single named adjustment applied to a [ModularStat].
 *
 * A mod is identified by its [name]: [StackingType.SETTING] and [StackingType.INCREMENTING]
 * mods fold into an already-applied mod of the same name, and [ModularStat.removeMod] drops
 * every mod sharing a name.
 *
 * @property name the source key this mod is tracked under
 * @property value the mod's magnitude - a flat amount for [Type.ADDITIVE]; for [Type.MULTIPLICATIVE] a bonus fraction or a factor, per [stackingType]
 * @property type whether the mod adjusts the stat's base additively or multiplicatively
 * @property stackingType how the mod combines with other mods of the same [name] and kind
 */
data class StatMod(
    val name: String,
    var value: Double,
    val type: Type = Type.MULTIPLICATIVE,
    val stackingType: StackingType = StackingType.ADDITIVE
) {

    //region KINDS
    /** How a [StatMod] feeds into a [ModularStat]'s value. */
    enum class Type {
        /** A flat amount added to the stat after scaling. */
        ADDITIVE,

        /** A factor the stat's base is scaled by. */
        MULTIPLICATIVE
    }

    /** How repeated [StatMod]s of the same [name] and [Type] combine. */
    enum class StackingType {
        /** The mod is inert - applying it changes nothing. */
        NONE,

        /** Replaces the value of an already-applied mod of the same [name]. */
        SETTING,

        /** Adds its value onto an already-applied mod of the same [name]. */
        INCREMENTING,

        /** Multiplicative mods with this stacking sum into one bonus on top of `1.0` (`0.2` + `0.1` -> `+30%`). */
        ADDITIVE,

        /** Multiplicative mods with this stacking multiply their factors together (`1.2` * `1.1` -> `+32%`). */
        MULTIPLICATIVE
    }
    //endregion
}
