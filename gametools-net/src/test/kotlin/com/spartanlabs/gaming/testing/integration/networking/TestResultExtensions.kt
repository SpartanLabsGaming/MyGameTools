package com.spartanlabs.gaming.testing.integration.networking

/**
 * Chains a [Result]-returning [transform] onto this result, short-circuiting on failure.
 *
 * The production code composes its fallible steps this way, so the test helpers that drive
 * it do too - a fake client's handshake is a send, then a receive, then a parse, and the
 * first of those to fail should be the one the test reports.
 *
 * @param transform applied to the encapsulated value if this result is a success
 * @return [transform]'s result if this is a success, otherwise this failure unchanged
 */
internal inline fun <T, R> Result<T>.andThen(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = transform, onFailure = { cause -> Result.failure(cause) })
