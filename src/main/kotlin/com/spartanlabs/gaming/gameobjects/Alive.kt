package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.generaltools.Color
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
//endregion

//region 2. Intended Function
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
//endregion

/**
 * An [Actor] with [health] that can be depleted.
 *
 * It carries a red [healthBar] sub-object that follows the actor and whose width tracks
 * [health] as a fraction of its maximum, both refreshed every [tick].
 *
 * @param location where the actor starts
 * @param dimensions the actor's size
 * @param maxHealth the actor's starting and maximum health; must be positive
 */
open class Alive(
    location: Point,
    dimensions: Dimensions,
    maxHealth: Double
) : Actor(location = location, dimensions = dimensions) {

    //region STATS
    /** The actor's health, from `maxHealth` down to (and past) zero. */
    var health: CombinedStat = CombinedStat(startingValue = maxHealth, maxValue = maxHealth)

    /** How much health this actor removes from a target on a successful hit; defaults to `10.0`. */
    var damage: ModularStat = ModularStat(10.0)

    /** Swing progress a pending attack must reach before it lands, paced by [attackSpeed]; defaults to `1.7`. */
    var attackTime: ModularStat = ModularStat(1.7)

    /** How fast swing progress accrues while an attack is in progress (`attackSpeed / 10000` per tick); defaults to `100.0`. */
    var attackSpeed: ModularStat = ModularStat(100.0)

    /** How close, in world units, this actor must be to its target before it starts swinging; defaults to `750.0`. */
    var attackRange: ModularStat = ModularStat(750.0)

    /** Probability in `0.0..1.0` that this actor dodges an incoming hit; defaults to `0.0` (never). */
    var evasion: ModularStat = ModularStat(0.0)

    /** An alive adds [CoreCapability.ATTACK] to whatever its supertypes provide. */
    override val capabilities: Set<Capability> = super.capabilities + CoreCapability.ATTACK

    /**
     * An alive exposes its health and combat stats for a [Buff] to modify, keyed by name, on
     * top of its supertypes' stats: `"health"`, `"damage"`, `"attackTime"`, `"attackSpeed"`,
     * `"attackRange"`, `"evasion"`.
     */
    override val stats: Map<String, Moddable> get() = super.stats + mapOf(
        "health" to health,
        "damage" to damage,
        "attackTime" to attackTime,
        "attackSpeed" to attackSpeed,
        "attackRange" to attackRange,
        "evasion" to evasion,
    )
    //endregion
    //region OWNERSHIP
    /**
     * The [World] this actor belongs to, or `null` when it is not in one. [World.add] sets it;
     * a [DeathResponse.REMOVAL] death needs it so the actor can reach [World.removeList].
     */
    var world: World? = null

    /**
     * The side this actor belongs to, used to tell friend from foe. Defaults to
     * [DEFAULT_FACTION]; callers compare factions however their game needs.
     */
    var faction: String = DEFAULT_FACTION

    /**
     * The [Player] this actor belongs to, or `null` when it is unowned.
     *
     * Kept in step with [Player.ownedAlives] in both directions: assigning a new owner pulls
     * this actor off the previous owner's roster and adds it to the new one, and
     * [Player.own] / [Player.disown] drive this property.
     */
    var owner: Player? = null
        set(value) {
            if (field === value) return
            field?.removeFromRoster(this)
            field = value
            value?.addToRoster(this)
            log.debug("An Alive is now owned by {}", value?.name ?: "no one")
        }

    /** `true` when this actor has an [owner]. */
    val hasOwner: Boolean get() = owner != null
    //endregion
    //region COMBAT
    /** Stage of the attack cycle driven each tick by [considerAttack]. */
    private enum class AttackState { NONE, ISSUED, INPROGRESS }
    private var attackState: AttackState = AttackState.NONE
    private var attackTarget: Alive? = null
    private var attackProgress: Double = 0.0

    /**
     * Orders this actor to attack [target]: it will close to [attackRange], then swing on a
     * loop until told otherwise. Fires [onAttackIssued] here and [Alive.onTargetedByAttack] on
     * [target].
     */
    fun issueAttack(target: Alive) {
        attackState = AttackState.ISSUED
        attackTarget = target
        onAttackIssued()
        target.onTargetedByAttack()
    }

    /** Hook run on the attacker the moment [issueAttack] is called. Does nothing by default. */
    protected open fun onAttackIssued() {}

    /** Hook run on the target the moment it is named in an [issueAttack]. Does nothing by default. */
    protected open fun onTargetedByAttack() {}

    /**
     * One tick of the attack cycle: while a target is out of [attackRange] this actor walks
     * toward it, and once in range it swings via [progressAttack]. A failed distance check is
     * logged and treated as in-range so the actor keeps engaging.
     */
    private fun considerAttack() = when (attackState) {
        AttackState.NONE -> {}
        AttackState.ISSUED -> {
            val distance = distanceFrom(attackTarget!!).getOrElse {
                log.warn("Failed to calculate distance between two Alives during attack stage")
                0.0
            }
            if (distance > attackRange)
                destination = attackTarget!!.location
            else
                attackState = AttackState.INPROGRESS
        }
        AttackState.INPROGRESS -> progressAttack()
    }

    /** Accrues swing progress by [attackSpeed] each tick and, once it reaches [attackTime], lands a swing. */
    private fun progressAttack() {
        attackProgress += attackSpeed / 10000.0
        if (attackProgress >= attackTime) {
            attackProgress = 0.0
            attackState = AttackState.ISSUED
            this attack attackTarget!!
        }
    }

    /** Runs one swing at [target]: [onAttack] / [onAttacked] hooks, then an evasion-gated [hit]. */
    private infix fun attack(target: Alive) {
        this onAttack target
        target onAttacked this
        this attemptHit target
    }

    /** Hook run on the attacker at the start of each swing. Does nothing by default. */
    protected open infix fun onAttack(target: Alive) {}

    /** Hook run on the target at the start of each swing against it. Does nothing by default. */
    protected open infix fun onAttacked(attacker: Alive) {}

    /** Rolls [target]'s [evasion]; on a miss the swing is dropped, otherwise it proceeds to [hit]. */
    private infix fun attemptHit(target: Alive) = if (Math.random() > target.evasion) hit(target) else Unit

    /** A landed swing: [onHitting] / [onHitBy] hooks, then [dealDamage]. */
    private infix fun hit(target: Alive) {
        this onHitting target
        target onHitBy this
        this dealDamage target
    }

    /** Hook run on the attacker when a swing lands. Does nothing by default. */
    protected open infix fun onHitting(target: Alive) {}

    /** Hook run on the target when a swing lands on it. Does nothing by default. */
    protected open infix fun onHitBy(attacker: Alive) {}

    /** Applies this actor's [damage] to [target]: [onDamaging] / [onDamaged] hooks, then [takeDamage]. */
    private infix fun dealDamage(target: Alive) {
        this onDamaging target
        target onDamaged this
        target takeDamage damage.value
    }

    /** Hook run on the attacker as damage is dealt. Does nothing by default. */
    protected open infix fun onDamaging(target: Alive) {}

    /** Hook run on the target as damage is dealt to it. Does nothing by default. */
    protected open infix fun onDamaged(attacker: Alive) {}

    /** Subtracts [incomingDamage], after [calculateDamageTaken], from [health]. */
    private infix fun takeDamage(incomingDamage: Double) {
        health.current -= calculateDamageTaken(incomingDamage)
    }

    /** Converts raw [incomingDamage] into the amount actually lost; the base rule passes it through unchanged. */
    private infix fun calculateDamageTaken(incomingDamage: Double): Double = incomingDamage
    //endregion
    //region DEATH
    /** `true` while [health] is above zero. */
    val isAlive get() = health.current > 0.0

    /** Where a [DeathResponse.RESPAWN] returns this actor to. Defaults to its creation position. */
    var respawn: Point = Point(location)

    /** What this actor does the moment its [health] runs out. Defaults to [DeathResponse.REMOVAL]. */
    var deathResponse: DeathResponse = DeathResponse.REMOVAL
    /** What an [Alive] does the moment its [health] runs out. */
    enum class DeathResponse {
        /** Queue the actor into its [World.removeList] so it drops out of the game. */
        REMOVAL,

        /** Send the actor back to its [respawn] point at full [health]. */
        RESPAWN
    }
    /** Guards [die] so one death triggers one response; cleared once the actor is alive again. */
    private var deathHandled = false
    /**
     * Applies [deathResponse] the first tick this actor's [health] runs out. Runs once per
     * death; a [DeathResponse.RESPAWN] actor can die again once it is back.
     *
     * - [DeathResponse.REMOVAL] queues the actor into its [world]'s [World.removeList].
     * - [DeathResponse.RESPAWN] moves it to [respawn] and restores full [health].
     */
    protected open fun die() {
        if (deathHandled) return
        deathHandled = true
        log.debug("An Alive died at {} (response {})", location, deathResponse)
        when (deathResponse) {
            DeathResponse.REMOVAL -> {
                val host = world
                if (host == null) log.warn("An Alive died with REMOVAL but has no world; it stays in play")
                else host.removeList.add(this)
            }
            DeathResponse.RESPAWN -> {
                location.setTo(respawn)
                health.current = health.max.value
                deathHandled = false
                log.debug("An Alive respawned at {} with {} health", respawn, health.max.value)
            }
        }
        onDeath()
    }

    /** Extension point invoked once after [die] has applied the [deathResponse]. Does nothing by default. */
    protected open fun onDeath() {}

    /** Triggers [die] the first tick [health] is depleted, and re-arms it once the actor is alive again. */
    private fun contemplateLife() = if (!isAlive) die() else deathHandled = false
    //endregion
    //region HEALTH BAR
    /** [healthBar]'s width at full health, captured before any tick scales it down. */
    private val fullHealthBarWidth = dimensions.width

    /**
     * [healthBar]'s vertical offset from the actor's origin, fixed at the actor's starting size:
     * half the actor's height to clear its top edge, plus half the bar's height so the bar rests
     * just above it.
     */
    private val healthBarYOffset = dimensions.height * (0.5 + (HEALTH_BAR_HEIGHT_FRACTION / 2))

    /**
     * A red bar drawn a fraction of the actor's height above its origin.
     *
     * Built from fresh [Dimensions]/[Point] rather than the constructor arguments, so that
     * sizing and positioning it never mutates the actor's own geometry (which shares those
     * objects). Its position is refreshed every [tick] to follow the actor.
     */
    val healthBar = VisibleObject(
        area = Square(
            dimensions = Dimensions(
                width = fullHealthBarWidth,
                height = dimensions.height * HEALTH_BAR_HEIGHT_FRACTION
            ),
            location = Point(x = location.x, y = location.y - healthBarYOffset)
        ),
        color = Color(255, 0, 0)
    )
    /** Moves [healthBar] back onto the actor and scales its width to the current [health] fraction. */
    private fun updateHealthBar() {
        healthBar.location.setTo(location.x, location.y - healthBarYOffset)
        healthBar.dimensions.width = fullHealthBarWidth * health.fractionOfMax.coerceIn(0.0, 1.0)
    }
    //endregion
    //region GENERAL
    init {
        subObjects.add(healthBar)
        log.debug("Spawned an Alive at {} with {} health", location, maxHealth)
    }

    /**
     * Advances the actor: applies its [deathResponse] if it has just died, refreshes the
     * [healthBar]'s position and width, then - while [CoreCapability.ATTACK] is usable - runs
     * one tick of any pending attack. An alive whose attack capability is suppressed by a
     * [Buff] keeps its attack order but its swing progress is frozen until the suppression
     * lifts; death handling and the health bar are unaffected.
     */
    override fun onUpdate() {
        super.onUpdate()
        contemplateLife()
        updateHealthBar()
        if (can(CoreCapability.ATTACK)) considerAttack()
        else log.debug("An Alive's attack is frozen this tick; its attack capability is suppressed.")
    }
    //endregion

    companion object {
        /** [healthBar]'s height as a fraction of actor height; also feeds [healthBarYOffset]. */
        private const val HEALTH_BAR_HEIGHT_FRACTION = 0.2

        /** The [faction] a new [Alive] starts in. */
        const val DEFAULT_FACTION = "neutral"
    }
}
//region SERIALIZATION
/**
 * An immutable, serializable copy of an [Alive]'s state, layered on its [ActorSnapshot]: its
 * health, side, owner, and combat stats. Sent in place of an [ActorSnapshot] whenever a
 * broadcast object is an [Alive] (see [DrawableSnapshot]).
 *
 * Each combat stat is captured as its effective [ModularStat.value] at snapshot time, with
 * every applied [StatMod] already folded in.
 *
 * @property id the actor's stable [EntityId.raw] ([DrawableSnapshot.UNIDENTIFIED] if unowned)
 * @property actor the underlying [ActorSnapshot] - movement, drawable state, sub-objects
 * @property health the actor's health at snapshot time
 * @property faction the side the actor belongs to at snapshot time
 * @property ownerName the name of the actor's [Alive.owner], or `null` when it is unowned
 * @property damage the health the actor removes from a target when it attacks ([Alive.damage])
 * @property attackTime the swing progress a pending attack must reach before it lands ([Alive.attackTime])
 * @property attackSpeed how fast swing progress accrues while attacking ([Alive.attackSpeed])
 * @property attackRange how close the actor must be to its target before it swings ([Alive.attackRange])
 * @property evasion the actor's chance in `0.0..1.0` to dodge an incoming hit ([Alive.evasion])
 */
@Serializable
@SerialName("alive")
data class AliveSnapshot(
    override val id: Long = DrawableSnapshot.UNIDENTIFIED,
    val actor: ActorSnapshot,
    val health: CombinedStatSnapshot,
    val faction: String,
    val ownerName: String?,
    val damage: Double,
    val attackTime: Double,
    val attackSpeed: Double,
    val attackRange: Double,
    val evasion: Double) : DrawableSnapshot {

    /** The actor's sub-object snapshots - the same list as [actor]'s. */
    override val subObjects: List<DrawableSnapshot> get() = actor.subObjects

    companion object {
        /** Takes a snapshot of [alive]'s stats along with its movement, drawable state, and sub-objects. */
        infix fun from(alive: Alive): AliveSnapshot = AliveSnapshot(
            id = alive.entityId.raw,
            actor = ActorSnapshot.from(alive),
            health = CombinedStatSnapshot.from(alive.health),
            faction = alive.faction,
            ownerName = alive.owner?.name,
            damage = alive.damage.value,
            attackTime = alive.attackTime.value,
            attackSpeed = alive.attackSpeed.value,
            attackRange = alive.attackRange.value,
            evasion = alive.evasion.value
        )
    }
}
//endregion