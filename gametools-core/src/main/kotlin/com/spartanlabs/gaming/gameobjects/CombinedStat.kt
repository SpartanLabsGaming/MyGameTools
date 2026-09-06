package com.spartanlabs.gaming.gameobjects

//region 2. Intended Function
import kotlinx.serialization.Serializable
//endregion

/**
 * A single numeric stat - health, mana, stamina - tracked as a [current] amount against a
 * [max] ceiling that carries its own [ModularStat] modifiers.
 *
 * [current] is free to sit anywhere, including at or below zero, so callers can test for
 * depletion; [fractionOfMax] reports it against the modified ceiling. [applyMod] / [removeMod]
 * adjust [max] and rescale [current] so its fraction of [max] is preserved. [current] also takes
 * part in arithmetic and comparison directly against [Double], [ModularStat] and other
 * `CombinedStat`s.
 *
 * @property max the ceiling, with any additive/multiplicative [StatMod]s applied
 * @property current the current amount
 */
@ConsistentCopyVisibility
data class CombinedStat private constructor(
    val max: ModularStat = ModularStat(0.0),
    var current: Double = max.value,
) : Moddable {

    //region CONSTRUCTION
    /** Starts the stat at [startingValue] against a ceiling of [maxValue]. */
    constructor(startingValue: Double, maxValue: Double) : this(ModularStat(maxValue), startingValue)

    /** Starts the stat full, with both the current amount and the ceiling at [startingValue]. */
    constructor(startingValue: Double) : this(ModularStat(startingValue))

    init {
        require(max.value > 0) { "maxValue must be positive but was ${max.value}" }
    }
    //endregion

    /** [current] as a fraction of the modified [max]; `1.0` when full, and free to leave `0.0..1.0`. */
    val fractionOfMax get() = current / max

    //region PLUS
    /** Sum of this stat's [current] and [other]'s. */
    infix operator fun plus(other: CombinedStat) = current + other.current
    /** Sum of this stat's [current] and [modular]'s value. */
    infix operator fun plus(modular: ModularStat) = current + modular.value
    /** Sum of this stat's [current] and [double]. */
    infix operator fun plus(double: Double) = current + double
    //endregion
    //region MINUS
    /** This stat's [current] less [other]'s. */
    infix operator fun minus(other: CombinedStat) = current - other.current
    /** This stat's [current] less [modular]'s value. */
    infix operator fun minus(modular: ModularStat) = current - modular.value
    /** This stat's [current] less [double]. */
    infix operator fun minus(double: Double) = current - double
    //endregion
    //region MULT
    /** Product of this stat's [current] and [other]'s. */
    infix operator fun times(other: CombinedStat) = current * other.current
    /** Product of this stat's [current] and [modular]'s value. */
    infix operator fun times(modular: ModularStat) = current * modular.value
    /** Product of this stat's [current] and [double]. */
    infix operator fun times(double: Double) = current * double
    //endregion
    //region DIV
    /** This stat's [current] divided by [other]'s. */
    infix operator fun div(other: CombinedStat) = current / other.current
    /** This stat's [current] divided by [modular]'s value. */
    infix operator fun div(modular: ModularStat) = current / modular.value
    /** This stat's [current] divided by [double]. */
    infix operator fun div(double: Double) = current / double
    //endregion
    //region COMPARE
    /** Orders this stat against [other] by [current]. */
    infix operator fun compareTo(other: CombinedStat) = current.compareTo(other.current)
    /** Orders this stat against [modular] by [current]. */
    infix operator fun compareTo(modular: ModularStat) = current.compareTo(modular.value)
    /** Orders this stat against [double] by [current]. */
    infix operator fun compareTo(double: Double) = current.compareTo(double)
    //endregion
    //region UNARY
    /** This stat's [current], unchanged. */
    operator fun unaryPlus() = current
    /** This stat's [current], negated. */
    operator fun unaryMinus() = -current
    //endregion

    //region MODIFIERS
    /**
     * Captures the current fraction of [max], runs [change] against [mod] to alter [max], then
     * moves [current] so that fraction still holds against the new ceiling.
     */
    private fun respec(change: (StatMod) -> Unit, mod: StatMod) {
        fractionOfMax.let { fraction ->
            change(mod)
            current = fraction * max
        }
    }

    /** Applies [mod] to [max], rescaling [current] to keep its fraction of the ceiling. */
    override fun applyMod(mod: StatMod) = respec(max::applyMod, mod)

    /** Removes [mod] from [max], rescaling [current] to keep its fraction of the ceiling. */
    override fun removeMod(mod: StatMod) = respec(max::removeMod, mod)
    //endregion
}

/**
 * An immutable, serializable copy of a [CombinedStat]'s two tracked numbers, the ceiling
 * captured through its [ModularStat] modifiers.
 *
 * @property value the current amount at snapshot time
 * @property maxValue the ceiling at snapshot time
 */
@Serializable
data class CombinedStatSnapshot(val value: Double, val maxValue: Double) {
    companion object {
        /** Takes a snapshot of [stat]'s current amount and its modified [CombinedStat.max]. */
        infix fun from(stat: CombinedStat): CombinedStatSnapshot =
            CombinedStatSnapshot(stat.current, stat.max.value)
    }
}

//region Double <-> CombinedStat interop (top level so `double + stat` resolves anywhere)
/** Sum of [this] and [stat]'s current amount. */
infix operator fun Double.plus(stat: CombinedStat) = this + stat.current
/** [this] less [stat]'s current amount. */
infix operator fun Double.minus(stat: CombinedStat) = this - stat.current
/** Product of [this] and [stat]'s current amount. */
infix operator fun Double.times(stat: CombinedStat) = this * stat.current
/** [this] divided by [stat]'s current amount. */
infix operator fun Double.div(stat: CombinedStat) = this / stat.current
/** Orders [this] against [stat] by its current amount. */
infix operator fun Double.compareTo(stat: CombinedStat) = this.compareTo(stat.current)
//endregion
