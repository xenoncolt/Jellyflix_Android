package dev.xenoncolt.jellyflix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.xenoncolt.jellyflix.data.repo.AccountRepository
import dev.xenoncolt.jellyflix.ui.nav.JellyflixNavHost
import dev.xenoncolt.jellyflix.ui.theme.JellyflixTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var account: AccountRepository

    private var startOnDownloads by mutableStateOf(false)

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        startOnDownloads = intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true
        handleAuthDeepLink(intent)

        setContent {
            JellyflixTheme {
                JellyflixNavHost(startOnDownloads = startOnDownloads)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    /**
     * Completes the PKCE flow when Discord bounces back through
     * `movhubs://auth?code=…` (handoff section 6.5).
     *
     * The exchange happens here rather than in a screen's ViewModel because the
     * deep link can land while the user is anywhere in the app or while the
     * process was dead and the Custom Tab is what relaunched it.
     */
    private fun handleAuthDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != BuildConfig.AUTH_SCHEME || data.host != "auth") return

        val code = data.getQueryParameter("code")
        if (code.isNullOrBlank()) return

        lifecycleScope.launch { account.completeSignIn(code) }

        // Don't re-run the exchange on a configuration change; the code is
        // single-use and the second attempt would just fail.
        setIntent(Intent(Intent.ACTION_MAIN).apply { setClass(this@MainActivity, MainActivity::class.java) })
    }
}
