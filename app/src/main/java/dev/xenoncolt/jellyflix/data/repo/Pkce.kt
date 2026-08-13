package dev.xenoncolt.jellyflix.data.repo

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PKCE helpers (RFC 7636), per MOBILE-HANDOFF section 6.5.
 *
 * Only S256 is supported by the server; `plain` is rejected. The verifier never
 * leaves the device except in the final HTTPS exchange, so an app that registers
 * the same `movhubs://` scheme and intercepts the redirect gets a one-time code
 * it cannot redeem.
 */
object Pkce {

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    /** base64url(random 48 bytes). */
    fun generateVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, BASE64_FLAGS)
    }

    /** base64url(sha256(verifier)). */
    fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, BASE64_FLAGS)
    }
}
