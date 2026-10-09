package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.reflect.KClass
//endregion

/**
 * Signals that a [World.systemOf] lookup found no installed system that declared [role] as its
 * [WorldSystem.uniqueRole].
 *
 * It is the *value* of an expected failure: [World.systemOf] never throws it but returns it
 * inside [Result.failure]. It is thrown only by a caller that unwraps the lookup with
 * `getOrThrow()` - for instance the required-peer pattern in [WorldSystem.onInstalled], where the
 * throw rolls the install back. It is a [NoSuchElementException], so code that handles that type
 * handles this one too.
 *
 * **Stackless by design**: it records no stack trace, so a miss costs two small objects - the
 * [Result] failure wrapper and this exception - and no stack walk. Diagnose from the message and [role] (and, for a failed install, [World]'s roll-back log
 * line).
 *
 * Experimental - may change incompatibly in a Feature release until it graduates; see
 * [ExperimentalGameToolsApi].
 *
 * @property role the role that was looked up; compare it with `==` and print it with
 *   `role.java.name`, never the [KClass] itself
 * @see World.systemOf
 */
@ExperimentalGameToolsApi
class MissingWorldSystemException(val role: KClass<out WorldSystem>) :
    NoSuchElementException("no system installed on this World declared uniqueRole ${role.java.name}") {

    // Throwable's constructor calls this before `role` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
