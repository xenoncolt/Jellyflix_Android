package dev.xenoncolt.jellyflix.core

import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.xenoncolt.jellyflix.BuildConfig
import java.security.SecureRandom

/** What the app gets back from Google, and hands to movhubs to be verified. */
data class GoogleIdToken(
    val idToken: String,
    /** Echoed to the server so it can check the token was minted for this request. */
    val nonce: String,
)

sealed interface GoogleAuthResult {
    data class Success(val credential: GoogleIdToken) : GoogleAuthResult

    /** The user backed out. Not an error worth showing. */
    data object Cancelled : GoogleAuthResult

    /**
     * Nothing usable on this device — no Google account, no Play Services, or
     * this build's package/signature isn't registered. Callers fall back to the
     * browser rather than dead-ending.
     */
    data class Unavailable(val message: String) : GoogleAuthResult

    data class Failure(val message: String) : GoogleAuthResult
}

/**
 * Native Google sign-in through Credential Manager.
 *
 * This is Google's supported path — `GoogleSignInClient` is deprecated — and it
 * shows the system account sheet instead of a browser. The app receives an **ID
 * token**, not an authorization code: a signed JWT asserting who the user is,
 * which only the server can safely act on.
 *
 * Two client ids are involved and mixing them up is the usual failure:
 * [BuildConfig.GOOGLE_WEB_CLIENT_ID] is the *Web* client and is what goes in
 * `serverClientId`; the Android client (package name + signing SHA-1) is never
 * named in code and exists only so Play Services can verify the caller.
 *
 * Needs an **Activity** context. An application context throws at request time.
 */
object GoogleAuth {

    suspend fun signIn(activityContext: Context): GoogleAuthResult {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            return GoogleAuthResult.Unavailable("Google sign-in isn't configured in this build.")
        }

        val nonce = generateNonce()
        val manager = CredentialManager.create(activityContext)

        // First pass asks only for accounts that have already authorised this
        // app, which is what makes a returning user one tap. A first-time user
        // has none, so that throws and the retry below opens the full chooser.
        return runRequest(manager, activityContext, nonce, filterByAuthorized = true)
            ?: runRequest(manager, activityContext, nonce, filterByAuthorized = false)
            ?: GoogleAuthResult.Unavailable("No Google account is available on this device.")
    }

    /** @return null when the attempt found nothing and the caller should retry wider. */
    private suspend fun runRequest(
        manager: CredentialManager,
        activityContext: Context,
        nonce: String,
        filterByAuthorized: Boolean,
    ): GoogleAuthResult? {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorized)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(filterByAuthorized)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = manager.getCredential(activityContext, request)
            parse(response.credential, nonce)
        } catch (e: NoCredentialException) {
            // Only worth retrying while we were still filtering.
            if (filterByAuthorized) null else GoogleAuthResult.Unavailable(
                e.message ?: "No Google account is available on this device."
            )
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (e: GetCredentialException) {
            // Covers a missing/outdated Play Services and, most commonly, an
            // Android OAuth client whose SHA-1 doesn't match this build.
            GoogleAuthResult.Unavailable(e.message ?: "Google sign-in isn't available here.")
        }
    }

    private fun parse(credential: androidx.credentials.Credential, nonce: String): GoogleAuthResult {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleAuthResult.Failure("Unexpected credential type from Google.")
        }

        return runCatching { GoogleIdTokenCredential.createFrom(credential.data) }
            .fold(
                onSuccess = { GoogleAuthResult.Success(GoogleIdToken(it.idToken, nonce)) },
                onFailure = { GoogleAuthResult.Failure("Google returned an unreadable token.") },
            )
    }

    /**
     * Ties the token to this one request. Google embeds it in the ID token, so
     * the server can reject a token replayed from somewhere else.
     */
    private fun generateNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    /** Lets the account sheet offer a different account next time. */
    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
