package dev.xenoncolt.jellyflix.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Asks for everything the app needs before the user hits the wall of it not
 * being granted.
 *
 * Both permissions used to be declared and never requested, which failed in ways
 * that gave the user nothing to act on: downloads ran with no notification, and
 * every folder they picked was rejected as unwritable because libtorrent had no
 * filesystem access to it.
 *
 * One prompt covers both, granted one at a time — Android will not show two
 * permission dialogs at once, and All files access is not a dialog at all but a
 * Settings page we hand off to.
 */
@Composable
fun PermissionsGate() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var missing by remember { mutableStateOf(AppPermissions.missing(context)) }
    // Survives rotation so a user who said "Not now" isn't asked again on every
    // configuration change. A fresh launch asks again, which is the point.
    var postponed by rememberSaveable { mutableStateOf(false) }

    // Coming back from the All files access Settings page reports no result —
    // there is nothing to read from the launcher — so the only reliable way to
    // notice a grant is to re-check whenever the app returns to the foreground.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = AppPermissions.missing(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android stops showing a runtime prompt once it has been refused twice, and
    // launching it again then does nothing at all — a Grant button that visibly
    // fails. Remember the refusal and send the user to Settings instead.
    var refused by remember { mutableStateOf(emptySet<AppPermission>()) }

    val next = missing.firstOrNull()

    val runtimeRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted && next != null) refused = refused + next
        missing = AppPermissions.missing(context)
    }

    val settingsRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { missing = AppPermissions.missing(context) }

    if (postponed || next == null) return

    val prompt = AppPermissions.runtimePermission(next)?.takeIf { next !in refused }

    val request = {
        if (prompt != null) {
            runtimeRequest.launch(prompt)
        } else {
            // Walk the Settings fallbacks and stop at the first one that opens;
            // the per-app pages are missing on some OEM builds.
            AppPermissions.settingsIntents(context, next)
                .any { runCatching { settingsRequest.launch(it) }.isSuccess }
        }
        Unit
    }

    AlertDialog(
        onDismissRequest = { postponed = true },
        title = { Text("Jellyflix needs a couple of permissions") },
        text = {
            Column {
                missing.forEach { permission ->
                    Text(
                        text = AppPermissions.title(permission),
                        style = MaterialTheme.typography.titleSmall,
                        color = Tokens.Mist200,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = AppPermissions.rationale(permission),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tokens.Mist400,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = request) {
                Text(
                    if (prompt != null) {
                        "Grant ${AppPermissions.title(next).lowercase()}"
                    } else {
                        "Open settings"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { postponed = true }) { Text("Not now") }
        },
        containerColor = Tokens.Ink850,
        titleContentColor = Tokens.Mist200,
        textContentColor = Tokens.Mist400,
    )
}
