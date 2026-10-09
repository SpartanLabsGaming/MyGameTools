package com.spartanlabs.gaming.world.map

/**
 * Signals that a [TiledMap.spawnPoint] lookup found no spawn point named [name] - neither one the
 * map was built with nor one added later with [TiledMap.addSpawnPoint].
 *
 * It is the *value* of an expected outcome: [TiledMap.spawnPoint] never throws it but returns it
 * inside [Result.failure]; it is thrown only by a caller that unwraps with `getOrThrow()`. It is a
 * [NoSuchElementException], so a handler for that type handles it too.
 *
 * **Stackless by design**: no stack trace is recorded, so a miss costs two small objects - the
 * [Result] failure wrapper and this exception - and no stack walk. Diagnose from the message and [name].
 *
 * @property name the spawn-point name that was looked up
 * @see TiledMap.spawnPoint
 */
class MissingSpawnPointException(val name: String) :
    NoSuchElementException("no spawn point named '$name'") {

    // Throwable's constructor calls this before `name` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
