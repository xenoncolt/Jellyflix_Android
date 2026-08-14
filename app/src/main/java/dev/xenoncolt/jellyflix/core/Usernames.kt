package dev.xenoncolt.jellyflix.core

/**
 * The username rules from handoff 6, checked locally.
 *
 * The server is still the authority — only it knows what is taken — but the
 * format rules are fixed, so catching a bad shape here saves a round trip and
 * lets the field complain while the user is still typing.
 *
 * 3 to 24 characters of `a-z 0-9 . _ -`, starting alphanumeric. Comparison is
 * case-insensitive server-side, so capitals are accepted rather than rejected.
 */
object Usernames {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 24

    private val ALLOWED = Regex("^[A-Za-z0-9._-]+$")

    /** A message to show, or null when the shape is fine. */
    fun formatError(candidate: String): String? {
        val value = candidate.trim()

        return when {
            value.isEmpty() -> "Pick a username."
            value.length < MIN_LENGTH -> "At least $MIN_LENGTH characters."
            value.length > MAX_LENGTH -> "At most $MAX_LENGTH characters."
            !value[0].isLetterOrDigit() -> "Must start with a letter or number."
            !ALLOWED.matches(value) -> "Only letters, numbers, and . _ -"
            else -> null
        }
    }

    fun isValid(candidate: String): Boolean = formatError(candidate) == null
}
