package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.StatMod.StackingType
import com.spartanlabs.gaming.gameobjects.StatMod.Type
//endregion

/**
 * A [Double]-valued stat whose effective [value] is its [base] reshaped by any number of [StatMod]s.
 *
 * Mods are layered on with [applyMod] and taken off with [removeMod]; each change recomputes
 * [value] as `base * <multiplicative factor> + <additive amount>`. The type takes part in
 * arithmetic and comparison directly against [Double] and other `ModularStat`s, so it can stand
 * in for a plain number at call sites.
 *
 * @property base the unmodified starting value
 * @property value the current value with every applied [StatMod] folded in
 */
class ModularStat(var base: Double = 0.0) : Moddable {

    //region VALUE
    var value = base
        internal set

    /** The effective [value] rendered as a plain number, so a stat prints in place of a `Double`. */
    override fun toString() = value.toString()
    //endregion

    //region PLUS
    /** Sum of this stat's [value] and [modular]'s. */
    infix operator fun plus(modular: ModularStat) = value + modular.value
    /** Sum of this stat's [value] and [double]. */
    infix operator fun plus(double: Double) = value + double
    //endregion
    //region MINUS
    /** This stat's [value] less [modular]'s. */
    infix operator fun minus(modular: ModularStat) = value - modular.value
    /** This stat's [value] less [double]. */
    infix operator fun minus(double: Double) = value - double
    //endregion
    //region MULT
    /** Product of this stat's [value] and [modular]'s. */
    infix operator fun times(modular: ModularStat) = value * modular.value
    /** Product of this stat's [value] and [double]. */
    infix operator fun times(double: Double) = value * double
    //endregion
    //region DIV
    /** This stat's [value] divided by [modular]'s. */
    infix operator fun div(modular: ModularStat) = value / modular.value
    /** This stat's [value] divided by [double]. */
    infix operator fun div(double: Double) = value / double
    //endregion
    //region COMPARE
    /** Orders this stat against [modular] by [value]. */
    infix operator fun compareTo(modular: ModularStat) = value.compareTo(modular.value)
    /** Orders this stat against [double] by [value]. */
    infix operator fun compareTo(double: Double) = value.compareTo(double)
    //endregion
    //region UNARY
    /** This stat's [value], unchanged. */
    operator fun unaryPlus() = value
    /** This stat's [value], negated. */
    operator fun unaryMinus() = -value
    //endregion

    //region MODIFIERS
    private val mods = mutableListOf<StatMod>()
    private val additiveMods get() = mods.filter { it.type == Type.ADDITIVE }
    private val multiplicativeMods get() = mods.filter { it.type == Type.MULTIPLICATIVE }
    private val additivelyStackingMultMods get() = multiplicativeMods.filter { it.stackingType == StackingType.ADDITIVE }
    private val multiplicativelyStackingMultMods get() = multiplicativeMods.filter { it.stackingType == StackingType.MULTIPLICATIVE }
    private val additiveTotal get() = additiveMods.sumOf { it.value }
    private val addStackingTotal get() = additivelyStackingMultMods.sumOf { it.value }
    private val multStackingTotal get() = multiplicativelyStackingMultMods.fold(1.0) { acc, mod -> acc * mod.value }

    /**
     * The combined multiplicative factor: additively-stacking factors sum into one bonus on top
     * of `1.0`, and that is multiplied by the product of the multiplicatively-stacking factors.
     * With no multiplicative mods it is `1.0`, leaving [base] untouched.
     */
    private val multiplicativeTotal get() = (1.0 + addStackingTotal) * multStackingTotal

    /** Recomputes [value] as `base * multiplicativeTotal + additiveTotal`. */
    private fun calc() {
        value = base * multiplicativeTotal + additiveTotal
    }

    /** Runs [change] against [mod] and, when it reports the mod set changed, recomputes [value]. */
    private fun update(change: (StatMod) -> Boolean, mod: StatMod) = this.apply {
        if (change(mod)) calc()
    }

    /**
     * Folds [mod] into the applied set according to its [StatMod.stackingType], returning whether
     * the set actually changed:
     * - [StackingType.NONE] is a no-op.
     * - [StackingType.SETTING] / [StackingType.INCREMENTING] update an existing mod of the same
     *   [StatMod.name], or add [mod] when there is none.
     * - [StackingType.ADDITIVE] / [StackingType.MULTIPLICATIVE] always add [mod] as its own entry.
     */
    private fun onApplication(mod: StatMod): Boolean = when (mod.stackingType) {
        StackingType.NONE -> false
        StackingType.SETTING ->
            mods.firstOrNull { it.name == mod.name }?.let { it.value = mod.value; true } ?: mods.add(mod)
        StackingType.INCREMENTING ->
            mods.firstOrNull { it.name == mod.name }?.let { it.value += mod.value; true } ?: mods.add(mod)
        StackingType.ADDITIVE, StackingType.MULTIPLICATIVE -> mods.add(mod)
    }

    /** Applies [mod] to this stat, recomputing [value] when it takes effect. */
    override fun applyMod(mod: StatMod) { update(::onApplication, mod) }

    /** Removes every applied mod sharing [mod]'s [StatMod.name], recomputing [value] if any were removed. */
    override fun removeMod(mod: StatMod) { update({ removed -> mods.removeAll { it.name == removed.name } }, mod) }
    //endregion
}

//region Double <-> ModularStat interop (top level so `double + modular` resolves anywhere)
/** Sum of [this] and [modular]'s value. */
infix operator fun Double.plus(modular: ModularStat) = this + modular.value
/** [this] less [modular]'s value. */
infix operator fun Double.minus(modular: ModularStat) = this - modular.value
/** Product of [this] and [modular]'s value. */
infix operator fun Double.times(modular: ModularStat) = this * modular.value
/** [this] divided by [modular]'s value. */
infix operator fun Double.div(modular: ModularStat) = this / modular.value
/** Orders [this] against [modular] by its value. */
infix operator fun Double.compareTo(modular: ModularStat) = this.compareTo(modular.value)
//endregion
