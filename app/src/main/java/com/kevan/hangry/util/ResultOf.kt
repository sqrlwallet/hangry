package com.kevan.hangry.util

import kotlinx.coroutines.CancellationException

/**
 * Like runCatching, but cancellation propagates instead of turning into a failed Result - so a
 * cancelled sync stops rather than carrying on with empty data.
 */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
