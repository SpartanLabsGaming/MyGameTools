package com.spartanlabs.gaming.gameobjects

/**
 * A participant in the game that owns a roster of [Alive] actors - the units or characters
 * under one person's or one AI's control.
 *
 * This is a game-domain owner and is unrelated to the networking-level "player" tracked by
 * [com.spartanlabs.gaming.networking.GameServer], which only keys connections by name.
 *
 * Ownership is kept in step with [Alive.owner]: [own] and [disown] drive that property, and
 * assigning [Alive.owner] directly moves the actor on and off the corresponding roster. An
 * [Alive] therefore appears on at most one player's roster at a time.
 *
 * @param name a label identifying the player; not required to be unique
 */
class Player(val name: String) {

    /** Backing store for [ownedAlives]; mutated only via [own]/[disown] and the [Alive.owner] setter. */
    private val roster: ArrayList<Alive> = ArrayList()

    /** The [Alive] actors this player owns, in the order they were acquired. */
    val ownedAlives: List<Alive> get() = roster

    /** The owned actors that are still alive (see [Alive.isAlive]). */
    val livingAlives: List<Alive> get() = roster.filter { it.isAlive }

    init {
        log.debug("Created player '{}'", name)
    }

    /**
     * Takes ownership of [alive], transferring it off its previous owner's roster if it had
     * one. Sets [Alive.owner] to this player.
     *
     * @param alive the actor to take ownership of
     * @return `true` if ownership changed, `false` if this player already owned [alive]
     */
    fun own(alive: Alive): Boolean {
        if (alive.owner === this) return false
        alive.owner = this
        return true
    }

    /**
     * Releases [alive] if this player owns it, clearing its [Alive.owner].
     *
     * @param alive the actor to release
     * @return `true` if [alive] was owned by this player and has been released, `false` otherwise
     */
    fun disown(alive: Alive): Boolean {
        if (alive.owner !== this) return false
        alive.owner = null
        return true
    }

    /** `true` when [alive] is on this player's roster. */
    fun owns(alive: Alive): Boolean = alive in roster

    /** Adds [alive] to the roster. Called only by the [Alive.owner] setter, which owns the invariant. */
    internal fun addToRoster(alive: Alive) {
        if (alive !in roster) {
            roster.add(alive)
            log.debug("Player '{}' now owns {} Alive(s)", name, roster.size)
        }
    }

    /** Removes [alive] from the roster. Called only by the [Alive.owner] setter. */
    internal fun removeFromRoster(alive: Alive) {
        if (roster.remove(alive)) log.debug("Player '{}' released an Alive; {} left", name, roster.size)
    }
}
