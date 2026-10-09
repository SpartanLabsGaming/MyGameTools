package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.EntityId
//endregion

/**
 * Signals that a [ZoneIndex.zoneOf] lookup found [entityId] in no zone as of the index's most
 * recent step - because the entity is outside the grid's extent, has left the `World`, is not yet
 * numbered, was never seen, or the index has not stepped yet.
 *
 * It is the *value* of an expected outcome: [ZoneIndex.zoneOf] never throws it but returns it
 * inside [Result.failure]; it is thrown only by a caller that unwraps with `getOrThrow()`. It is a
 * [NoSuchElementException], so a handler for that type handles it too.
 *
 * **Stackless by design**: no stack trace is recorded, so a miss costs two small objects - the
 * [Result] failure wrapper and this exception - and no stack walk. Diagnose from the message and [entityId].
 *
 * Experimental - may change incompatibly in a Feature release until it graduates; see
 * [ExperimentalGameToolsApi].
 *
 * @property entityId the id that was looked up
 * @see ZoneIndex.zoneOf
 */
@ExperimentalGameToolsApi
class UnzonedEntityException(val entityId: EntityId) :
    NoSuchElementException("entity $entityId is not placed in any zone") { // EntityId.toString() is "#<raw>"

    // Throwable's constructor calls this before `entityId` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
