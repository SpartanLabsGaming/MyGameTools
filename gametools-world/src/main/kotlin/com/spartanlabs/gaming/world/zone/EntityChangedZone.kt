package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
//endregion

/**
 * A [ZoneIndex.step] found [entity]'s zone membership had changed since the previous step, and
 * published this on the owning [World]'s [World.events] bus.
 *
 * The four shapes this can take:
 * - `from = null`, `to` non-null - [entity] entered a zone for the first time (its first
 *   step while inside the grid's extent).
 * - `from` and `to` both non-null and different - [entity] crossed from one zone directly into
 *   another.
 * - `from` non-null, `to = null` - [entity] left the grid's covered extent, its position no
 *   longer resolving to any [Zone].
 * - `from` non-null, `to = null` - [entity] left the [World] entirely (despawned) while it was
 *   still zoned; [entity] names its last-known reference.
 *
 * The last two shapes are indistinguishable from this event's fields alone - both carry a
 * non-null [from] and a `null` [to]. A consumer that needs to tell them apart should check
 * whether [entity] still resolves via [World.byId]: if it does, the entity merely left the
 * grid's extent; if it doesn't, the entity despawned.
 *
 * For when, and in what order, these are published within a step, see [ZoneIndex.step].
 *
 * Publishing is raw: an entity oscillating exactly on a zone boundary publishes one of these on
 * every [ZoneIndex.step] it crosses on. A consumer that wants to dampen that "border flicker"
 * does so on its own side - Phase 1 reports transitions as they happen and nothing more.
 *
 * @property entity the object whose zone membership changed
 * @property from the zone [entity] was in before this step, or `null` if it had none
 * @property to the zone [entity] is in after this step, or `null` if it now has none
 */
data class EntityChangedZone(val entity: GameObject, val from: Zone?, val to: Zone?) : GameEvent
