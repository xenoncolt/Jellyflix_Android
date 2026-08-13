package dev.xenoncolt.jellyflix.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.ui.components.MarkdownText
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Update prompt. Sits over whatever screen the user is on, appears once per
 * launch when GitHub has a newer tag, and is dismissible — an update nag you
 * can't get rid of is worse than an out-of-date app.
 */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.needsInstallPermission) {
        AlertDialog(
            onDismissRequest = viewModel::consumePermissionPrompt,
            title = { Text("Allow installing apps") },
            text = {
                Text(
                    "Android needs your permission before Jellyflix can install its own " +
                        "update. Turn it on for Jellyflix, then tap Update again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(viewModel.installPermissionIntent())
                    viewModel.consumePermissionPrompt()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::consumePermissionPrompt) { Text("Not now") }
            },
            containerColor = Tokens.Ink850,
            titleContentColor = Tokens.Mist200,
            textContentColor = Tokens.Mist400,
        )
        return
    }

    val update = state.available
    if (!state.showDialog || update == null) return

    AlertDialog(
        onDismissRequest = { if (!state.downloading) viewModel.dismiss() },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Version ${update.version}" +
                        if (update.sizeBytes > 0) " · ${Format.bytes(update.sizeBytes)}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = Tokens.Mist300,
                )

                if (update.notes.isNotBlank()) {
                    // Release bodies are Markdown, so headings, lists and code
                    // fences are rendered rather than shown as their source.
                    MarkdownText(
                        markdown = update.notes,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tokens.Mist400,
                    )
                }

                if (state.downloading) {
                    Text(
                        text = "Downloading… ${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Tokens.Mist300,
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Tokens.Brand400,
                        trackColor = Tokens.Ink700,
                    )
                }

                state.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Tokens.Danger500,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::downloadAndInstall,
                enabled = !state.downloading,
            ) { Text(if (state.error != null) "Retry" else "Update") }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::dismiss,
                enabled = !state.downloading,
            ) { Text("Later") }
        },
        containerColor = Tokens.Ink850,
        titleContentColor = Tokens.Mist200,
        textContentColor = Tokens.Mist400,
    )
}
