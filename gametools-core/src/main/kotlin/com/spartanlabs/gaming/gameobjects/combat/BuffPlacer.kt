package com.spartanlabs.gaming.gameobjects.combat

/**
 * Interface to be implemented by any object that can apply a [Buff] to an [Alive] object.
 * Implementations can be Projectile, Spell, Terrain, AOE Ground effects, etc.
 */
interface BuffPlacer {
    val buff: Buff
    infix fun applyTo(target: Alive) = target apply buff
}
class StunPlacer : BuffPlacer {
    override val buff = Buff(
        name = "Generic Stun",
        durationTicks = 1000,
        suppressedCapabilities = setOf(CoreCapability.MOVE)
    )
}

