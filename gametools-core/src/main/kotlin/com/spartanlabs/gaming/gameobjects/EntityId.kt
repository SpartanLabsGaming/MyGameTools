package com.spartanlabs.gaming.gameobjects

/**
 * A stable, opaque handle for one [GameObject] within a [World].
 *
 * A world numbers each object it takes ownership of, in the order it acquires them, starting
 * from `1` (see [World.byId]). The number never changes for the lifetime of that object and is
 * never reused for another, so a client can address an object by its id across frames even as
 * objects spawn and despawn around it - which raw list position cannot do.
 *
 * Treat the [raw] value as opaque: address by it, compare it, but do not parse it or persist
 * it across process restarts. Ids are allocated per [World], so the same [raw] value in two
 * different worlds refers to two different objects.
 *
 * @property raw the underlying number; [UNASSIGNED]'s is `0`, every world-assigned id's is positive
 */
@JvmInline
value class EntityId(val raw: Long) : Comparable<EntityId> {

    override fun compareTo(other: EntityId): Int = raw.compareTo(other.raw)

    override fun toString(): String = "#$raw"

    companion object {
        /**
         * The id of a [GameObject] that no [World] has numbered yet. Its [raw] value, `0`, is
         * also what a pre-`3.1` world-state snapshot (which carried no id) decodes to, so a
         * consumer can treat "id is [UNASSIGNED]" and "this payload predates stable ids" the
         * same way: as "not addressable".
         */
        val UNASSIGNED: EntityId = EntityId(0L)
    }
}
