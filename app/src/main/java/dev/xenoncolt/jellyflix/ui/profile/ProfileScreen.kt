package dev.xenoncolt.jellyflix.ui.profile

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xenoncolt.jellyflix.core.Format
import dev.xenoncolt.jellyflix.core.Tmdb
import dev.xenoncolt.jellyflix.core.Usernames
import dev.xenoncolt.jellyflix.data.remote.dto.MediaSummary
import dev.xenoncolt.jellyflix.data.repo.AuthProvider
import dev.xenoncolt.jellyflix.ui.components.MediaCard
import dev.xenoncolt.jellyflix.ui.components.PageBackground
import dev.xenoncolt.jellyflix.ui.components.RemoteImage
import dev.xenoncolt.jellyflix.ui.components.SectionHeader
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Profile. Login is optional everywhere in movhubs — it only unlocks bookmarks
 * and download history — so the signed-out state is a first-class screen, not
 * an error.
 */
@Composable
fun ProfileScreen(
    onMediaClick: (MediaSummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Comes before everything else: the account is on a placeholder handle until
    // this is answered.
    if (state.user?.usernamePending == true) {
        UsernameDialog(
            state = state.username,
            onValueChange = viewModel::onUsernameChanged,
            onSubmit = viewModel::submitUsername,
        )
    }

    PageBackground(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "header") {
                ProfileHeader(
                    state = state,
                    onPasswordSignIn = viewModel::signInWithPassword,
                    onProviderSignIn = { provider ->
                        // Google gets the native account sheet, which needs the
                        // Activity; Discord has no such API and stays on PKCE.
                        val activity = context as? Activity
                        if (provider == AuthProvider.GOOGLE && activity != null) {
                            viewModel.signInWithGoogle(activity)
                        } else {
                            viewModel.beginSignIn(provider)
                        }
                    },
                    onCreateAccount = viewModel::openAccountPage,
                    onSignOut = viewModel::signOut,
                )
            }

            state.user?.let { user ->
                item(key = "providers") {
                    ConnectedAccounts(
                        providers = user.providers,
                        onLink = viewModel::linkProvider,
                        onUnlink = viewModel::unlinkProvider,
                    )
                }
            }

            state.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Tokens.Danger500,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            state.stats?.let { stats ->
                item(key = "stats") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatTile("Downloads", stats.downloadCount.toString(), Modifier.weight(1f))
                        StatTile("Bookmarks", stats.bookmarkCount.toString(), Modifier.weight(1f))
                        StatTile("Titles", stats.distinctTitles.toString(), Modifier.weight(1f))
                        StatTile("Total", Format.bytes(stats.totalBytes), Modifier.weight(1f))
                    }
                }
            }

            if (state.bookmarks.isNotEmpty()) {
                item(key = "bookmarks_header") { SectionHeader(title = "Bookmarks") }
                item(key = "bookmarks") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = state.bookmarks,
                            key = { it.key },
                            contentType = { "poster" },
                        ) { bookmark ->
                            val summary = MediaSummary(
                                id = bookmark.mediaId,
                                mediaType = bookmark.mediaType,
                                title = bookmark.title,
                                posterPath = bookmark.posterPath,
                                year = bookmark.year,
                            )
                            MediaCard(media = summary, onClick = { onMediaClick(summary) })
                        }
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                item(key = "history_header") { SectionHeader(title = "Download history") }
                items(
                    items = state.history,
                    key = { "${it.mediaType.wire}_${it.mediaId}_${it.releaseTitle}" },
                    contentType = { "history" },
                ) { record ->
                    HistoryRow(record.title, record.releaseTitle, record.size, record.posterPath)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    onPasswordSignIn: (String, String) -> Unit,
    onProviderSignIn: (AuthProvider) -> Unit,
    onCreateAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Tokens.Ink800),
            contentAlignment = Alignment.Center,
        ) {
            if (state.user?.avatarUrl != null) {
                RemoteImage(
                    url = state.user.avatarUrl,
                    contentDescription = state.user.displayName,
                    modifier = Modifier.size(84.dp),
                    showFallbackIcon = false,
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Tokens.Ink500,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        when {
            state.loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Tokens.Brand400,
            )

            state.user != null -> {
                Text(
                    text = state.user.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Tokens.Mist200,
                )
                OutlinedButton(onClick = onSignOut) { Text("Sign out") }
            }

            else -> SignInForm(
                busy = state.signingIn,
                onPasswordSignIn = onPasswordSignIn,
                onProviderSignIn = onProviderSignIn,
                onCreateAccount = onCreateAccount,
            )
        }
    }
}

/**
 * Which providers sign in to this account.
 *
 * Both can be attached at once, and either then reaches the same bookmarks and
 * history. The server links them by itself when a provider reports a verified
 * email that already exists; this is for the other case, where the two accounts
 * carry different addresses and the user has to say so explicitly.
 */
@Composable
private fun ConnectedAccounts(
    providers: List<String>,
    onLink: (AuthProvider) -> Unit,
    onUnlink: (AuthProvider) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(title = "Connected accounts")

        AuthProvider.entries.forEach { provider ->
            val connected = providers.any { it.equals(provider.path, ignoreCase = true) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tokens.Ink800.copy(alpha = 0.82f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Tokens.Mist200,
                    )
                    Text(
                        text = if (connected) "Connected" else "Not connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connected) Tokens.Success500 else Tokens.Mist400,
                    )
                }

                if (connected) {
                    TextButton(onClick = { onUnlink(provider) }) {
                        Text("Disconnect", color = Tokens.Danger500)
                    }
                } else {
                    TextButton(onClick = { onLink(provider) }) {
                        Text("Connect", color = Tokens.Brand300)
                    }
                }
            }
        }

        Text(
            text = "Connecting opens the website, which is where the provider's " +
                "consent screen lives. Sign in there first if it asks.",
            style = MaterialTheme.typography.labelSmall,
            color = Tokens.Mist400,
        )
    }
}

/**
 * Asked for when the provider's handle was already taken, so the account is
 * sitting on a placeholder like `name-3f9c21`. Not dismissible: the username is
 * the login identifier for password sign-in, so leaving it as generated would
 * hand the user something they never chose.
 */
@Composable
private fun UsernameDialog(
    state: UsernameUiState,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Pick a username") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "The name from your provider was already taken, so one was " +
                        "generated for you. Choose your own — it's how you sign in on " +
                        "this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.Mist400,
                )

                OutlinedTextField(
                    value = state.value,
                    onValueChange = onValueChange,
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !state.submitting,
                    isError = state.message != null,
                    supportingText = {
                        val message = state.message
                        when {
                            message != null -> Text(message, color = Tokens.Danger500)
                            state.checking -> Text("Checking…", color = Tokens.Mist400)
                            state.available -> Text("Available", color = Tokens.Success500)
                            else -> Text(
                                "${Usernames.MIN_LENGTH}–${Usernames.MAX_LENGTH} characters, " +
                                    "letters, numbers, . _ -",
                                color = Tokens.Mist400,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !state.submitting && state.message == null && state.value.isNotBlank(),
            ) {
                Text(if (state.submitting) "Saving…" else "Save")
            }
        },
        containerColor = Tokens.Ink850,
        titleContentColor = Tokens.Mist200,
        textContentColor = Tokens.Mist400,
    )
}

/**
 * The signed-out state.
 *
 * Password first, because it is the only path that needs no browser at all, and
 * the two providers under it. There is deliberately **no sign-up here**:
 * accounts are created only through Discord or Google, and the handle comes
 * from the provider, so a registration form would have nothing to ask. The link
 * at the bottom sends anyone new to the website instead.
 */
@Composable
private fun SignInForm(
    busy: Boolean,
    onPasswordSignIn: (String, String) -> Unit,
    onProviderSignIn: (AuthProvider) -> Unit,
    onCreateAccount: () -> Unit,
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val submit = { onPasswordSignIn(identifier, password) }

    Text(
        text = "Sign in to sync",
        style = MaterialTheme.typography.headlineSmall,
        color = Tokens.Mist200,
    )
    Text(
        text = "Optional. Downloads work fine without an account — " +
            "signing in only adds bookmarks and history.",
        style = MaterialTheme.typography.bodyMedium,
        color = Tokens.Mist400,
        textAlign = TextAlign.Center,
    )

    OutlinedTextField(
        value = identifier,
        onValueChange = { identifier = it },
        label = { Text("Username or email") },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors(),
    )

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Rounded.VisibilityOff
                    } else {
                        Icons.Rounded.Visibility
                    },
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    tint = Tokens.Mist400,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors(),
    )

    Button(
        onClick = submit,
        enabled = !busy,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Tokens.Brand500,
            contentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text("Sign in", modifier = Modifier.padding(start = 8.dp))
    }

    Text(
        text = "Password sign-in works once you've set one on your profile on the website.",
        style = MaterialTheme.typography.labelSmall,
        color = Tokens.Mist400,
        textAlign = TextAlign.Center,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Tokens.Ink600)
        Text(
            text = "or",
            style = MaterialTheme.typography.labelMedium,
            color = Tokens.Mist400,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Tokens.Ink600)
    }

    AuthProvider.entries.forEach { provider ->
        OutlinedButton(
            onClick = { onProviderSignIn(provider) },
            enabled = !busy,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with ${provider.label}")
        }
    }

    TextButton(onClick = onCreateAccount, enabled = !busy) {
        Text(
            text = "New here? Create an account on the website",
            style = MaterialTheme.typography.labelMedium,
            color = Tokens.Brand300,
        )
    }
}

/** Field colours, since the app's palette is not Material's default dark one. */
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Tokens.Mist200,
    unfocusedTextColor = Tokens.Mist200,
    focusedBorderColor = Tokens.Brand400,
    unfocusedBorderColor = Tokens.Ink600,
    focusedLabelColor = Tokens.Brand300,
    unfocusedLabelColor = Tokens.Mist400,
    cursorColor = Tokens.Brand400,
)

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Tokens.Mist200,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Tokens.Mist400,
        )
    }
}

@Composable
private fun HistoryRow(
    title: String,
    releaseTitle: String,
    size: Long,
    posterPath: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.Ink800.copy(alpha = 0.82f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = Tmdb.poster(posterPath, Tmdb.PosterSize.W185),
            contentDescription = title,
            modifier = Modifier
                .size(width = 40.dp, height = 60.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Tokens.Mist200,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = releaseTitle,
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.Mist400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = Format.bytes(size),
            style = MaterialTheme.typography.labelMedium,
            color = Tokens.Mist400,
        )
    }
}
