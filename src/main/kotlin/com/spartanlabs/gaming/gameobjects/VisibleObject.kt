package com.spartanlabs.gaming.gameobjects
//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.generaltools.Color
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.Square
import com.spartanlabs.geometry.serializations.DimensionsSnapshot
//endregion

//region 2. Intended Function
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.math.abs
//endregion

/**
 * A [GameObject] that has something to draw: an [area], a [color], a [texture], and a
 * facing [angle]. It does not move on its own - [Actor] is the moving subclass - but it can
 * carry [subObjects] that are drawn relative to it (a health bar, a nameplate, ...).
 *
 * The four secondary constructors are conveniences for the common case of "just give me a
 * box at a position", accepting a [Dimensions]/[Point] pair, raw sizes, or raw coordinates
 * in place of a fully built [Square].
 *
 * @param area the object's position and size as one [Square]
 * @param color the tint applied when drawing, opaque white by default
 * @param texture the name of the texture to draw, `"default.png"` by default
 */
open class VisibleObject(
    val area: Square = Square(
        dimensions  = Dimensions(width = 25.0, height = 25.0),
        location    = Point()
    ),
    var color: Color = Color(255, 255, 255, 255),
    var texture: String = "default.png") : GameObject(area.location) {

    /** Creates a visible object of [dimensions] at [location]. */
    constructor(dimensions: Dimensions = Dimensions(width = 25.0, height = 25.0), location: Point = Point(x = 0.0, y = 0.0))
            : this(Square(dimensions = dimensions, location = location))

    /** Creates a visible object [width] by [height] at [location]. */
    constructor(width: Double = 25.0, height: Double = 25.0, location: Point = Point(x = 0.0, y = 0.0))
            : this(Square(dimensions = Dimensions(width = width, height = height), location = location))

    /** Creates a visible object of [dimensions] at ([x], [y]). */
    constructor(dimensions: Dimensions = Dimensions(width = 25.0, height = 25.0), x: Double = 0.0, y: Double = 0.0)
            : this(Square(dimensions = dimensions, location = Point(x = x, y = y)))

    /** Creates a visible object [width] by [height] at ([x], [y]). */
    constructor(width: Double = 25.0, height: Double = 25.0, x: Double = 0.0, y: Double = 0.0)
            : this(Square(dimensions = Dimensions(width = width, height = height), location = Point(x = x, y = y)))

    /** Shortcut for [area]'s size; reads and writes straight through to it. */
    var dimensions
        get() = area.dimensions
        set(value) { area.dimensions = value }

    /**
     * The direction the object faces, in whole degrees counter-clockwise from the positive
     * x-axis.
     *
     * A value outside `0..359` is normalised into range (a full turn is 360 degrees) and the
     * out-of-range assignment is logged, rather than rejected, so a caller doing raw angle
     * arithmetic does not have to wrap it themselves.
     */
    var angle = 0
        set(value) {
            field = value.mod(360)
            if (value != field) log.warn("Angle {} normalised to {} degrees.", value, field)
        }

    /**
     * Whether this object rotates to face a direction, i.e. whether its [angle] is meaningful
     * to a renderer. `false` by default: a plain object is drawn upright regardless of [angle].
     */
    var turns: Boolean = false

    /**
     * Whether this object is sent to clients. `true` by default; a non-visible object - and
     * any of its [subObjects] that are also non-visible - is left out of world-state
     * snapshots entirely, so the client never hears about it.
     *
     * Kept in step with [active]: deactivating or reactivating the object sets [visible] to
     * match. It can still be set on its own to hide an object that keeps simulating.
     */
    var visible: Boolean = true

    /** Toggling a visible object's [active] state also sets [visible] to the same value. */
    override var active: Boolean
        get() = super.active
        set(value) {
            super.active = value
            visible = value
        }

    /** Objects drawn relative to this one, e.g. a health bar. Drawn in insertion order. */
    val subObjects: MutableList<VisibleObject> = mutableListOf()

    /**
     * `true` when this object's axis-aligned bounds overlap [other]'s.
     *
     * Each box is taken to be centred on its [location] with its [dimensions], so the two
     * touch when the gap between their centres is no larger than the summed half-extents on
     * every axis. A zero-size box can still collide (an exact-position overlap counts).
     *
     * @param other the object to test against
     */
    open fun collidesWith(other: VisibleObject): Boolean =
        abs(location.x - other.location.x) <= (dimensions.width + other.dimensions.width) / 2.0 &&
            abs(location.y - other.location.y) <= (dimensions.height + other.dimensions.height) / 2.0
}

/**
 * An immutable, serializable copy of a [Color]'s channels.
 *
 * @property r red channel, `0..255`
 * @property g green channel, `0..255`
 * @property b blue channel, `0..255`
 * @property a alpha channel, `0..255`
 */
@Serializable
data class ColorSnapshot(val r: Int, val g: Int, val b: Int, val a: Int) {
    companion object {
        /** Takes a snapshot of [color]'s channels. */
        infix fun from(color: Color): ColorSnapshot =
            ColorSnapshot(color.red, color.green, color.blue, color.alpha)
    }
}

/**
 * The shared supertype of the per-object entries in a world-state broadcast. The most specific
 * kind is picked for each object: [AliveSnapshot] for an [Alive], else [ActorSnapshot] for an
 * [Actor], else a plain [VisibleObjectSnapshot].
 *
 * It serializes polymorphically - every entry carries a `type` field naming which kind it is
 * - so [com.spartanlabs.gaming.networking.GameServer.broadcast] can send one mixed list and
 * each object is decoded back as the right type.
 */
@Serializable
sealed interface DrawableSnapshot {

    /**
     * The stable [EntityId.raw] of the object this snapshots, so a client can track an object
     * across frames by id rather than by list position. [UNIDENTIFIED] (`0`) when the object
     * was not owned by a [World] at snapshot time, or when the payload predates stable ids.
     */
    val id: Long

    /** Snapshots of this object's visible [VisibleObject.subObjects], in order. */
    val subObjects: List<DrawableSnapshot>

    companion object {
        /** The [id] of a snapshot whose object had no [World]-assigned [EntityId]. Matches [EntityId.UNASSIGNED]. */
        const val UNIDENTIFIED: Long = 0L

        /** Snapshots [visibleObject] as the most specific kind that fits it. */
        infix fun from(visibleObject: VisibleObject): DrawableSnapshot = when (visibleObject) {
            is Alive -> AliveSnapshot.from(visibleObject)
            is Actor -> ActorSnapshot.from(visibleObject)
            else -> VisibleObjectSnapshot.from(visibleObject)
        }
    }
}

/**
 * An immutable, serializable copy of a [VisibleObject]'s drawable state, including its
 * [subObjects] snapshotted recursively.
 *
 * @property id the object's stable [EntityId.raw] ([DrawableSnapshot.UNIDENTIFIED] if unowned)
 * @property gameObject the underlying [GameObjectSnapshot] (position)
 * @property dimensions the object's size at snapshot time
 * @property color the object's tint at snapshot time
 * @property texture the object's texture name at snapshot time
 * @property angle the object's facing in degrees at snapshot time
 * @property turns whether [angle] is meaningful for this object at snapshot time
 * @property subObjects snapshots of the object's visible [VisibleObject.subObjects], in order,
 *   each as its own kind ([ActorSnapshot] or plain [VisibleObjectSnapshot])
 */
@Serializable
@SerialName("visibleObject")
data class VisibleObjectSnapshot(
    override val id: Long = DrawableSnapshot.UNIDENTIFIED,
    val gameObject: GameObjectSnapshot,
    val dimensions: DimensionsSnapshot,
    val color: ColorSnapshot,
    val texture: String,
    val angle: Int,
    val turns: Boolean,
    override val subObjects: List<DrawableSnapshot>) : DrawableSnapshot {
    companion object {
        /** Takes a snapshot of [visibleObject] and, recursively, its visible sub-objects. */
        infix fun from(visibleObject: VisibleObject): VisibleObjectSnapshot = VisibleObjectSnapshot(
            id = visibleObject.entityId.raw,
            gameObject = GameObjectSnapshot.from(visibleObject),
            dimensions = DimensionsSnapshot.from(visibleObject.dimensions),
            color = ColorSnapshot.from(visibleObject.color),
            texture = visibleObject.texture,
            angle = visibleObject.angle,
            turns = visibleObject.turns,
            subObjects = visibleObject.subObjects.filter { it.visible }.map { DrawableSnapshot from it }
        )
    }
}
