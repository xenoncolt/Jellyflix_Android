package dev.xenoncolt.jellyflix.core

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * A tiny result type. Named [Outcome] rather than `Result` to avoid colliding
 * with `kotlin.Result`, which cannot be used as a generic return type without
 * inline-class friction.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.data
}

/**
 * User-facing failure. The server's error bodies are `{ "error": string }` and
 * are already scrubbed of internal detail, so they are safe to show verbatim.
 */
data class AppError(
    val message: String,
    val requiresAuth: Boolean = false,
    val isNetwork: Boolean = false,
    /**
     * The title isn't out yet. A 409, and an expected state rather than a
     * failure — the server refuses so a fuzzy indexer match can't be downloaded
     * by accident.
     */
    val notReleased: Boolean = false,
    val statusCode: Int? = null,
) {
    /** A stale `guid` (404) means "search again", not "this button is dead". */
    val isStaleGuid: Boolean get() = statusCode == 404
}

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Runs [block], mapping the usual transport failures onto [AppError]. */
suspend inline fun <T> runCatchingApi(crossinline block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: HttpException) {
    Outcome.Failure(e.toAppError())
} catch (e: IOException) {
    Outcome.Failure(
        AppError(
            message = "Can't reach movhubs. Check your connection.",
            isNetwork = true,
        )
    )
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e // never swallow cancellation
} catch (e: Exception) {
    Outcome.Failure(AppError(message = e.message ?: "Something went wrong"))
}

fun HttpException.toAppError(): AppError {
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val parsed = raw
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { errorJson.decodeFromString<ApiErrorBody>(it) }.getOrNull() }

    return AppError(
        message = parsed?.error ?: defaultMessageFor(code()),
        requiresAuth = parsed?.requiresAuth == true || code() == 401,
        notReleased = parsed?.notReleased == true,
        statusCode = code(),
    )
}

private fun defaultMessageFor(code: Int): String = when (code) {
    401 -> "Sign in to do that."
    404 -> "Not found."
    502 -> "The indexer didn't answer. Try again."
    503 -> "Torrent search isn't configured on the server."
    else -> "Request failed ($code)."
}

@kotlinx.serialization.Serializable
data class ApiErrorBody(
    val error: String = "Something went wrong",
    val requiresAuth: Boolean = false,
    val notReleased: Boolean = false,
)
