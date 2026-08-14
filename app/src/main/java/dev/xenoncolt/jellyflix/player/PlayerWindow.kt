package dev.xenoncolt.jellyflix.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

/** What the rotation button cycles through. */
enum class ScreenOrientation(val label: String) {
    /** Follows the device, including its auto-rotate setting. */
    Auto("Auto"),
    Landscape("Landscape"),
    Portrait("Portrait");

    fun next(): ScreenOrientation = entries[(ordinal + 1) % entries.size]

    val activityInfo: Int
        get() = when (this) {
            Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // sensorLandscape, not landscape: a phone held the other way up
            // should still flip rather than showing the video upside down.
            Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
}

/**
 * Applies an orientation for as long as the player is on screen, then puts it
 * back.
 *
 * Restoring matters: leaving the activity pinned to landscape would rotate the
 * whole app after the user backs out of the player.
 */
@Composable
fun ApplyOrientation(orientation: ScreenOrientation) {
    val context = LocalContext.current

    DisposableEffect(orientation) {
        val activity = context as? Activity
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = orientation.activityInfo

        onDispose {
            if (previous != null) activity.requestedOrientation = previous
        }
    }
}

/**
 * Tracks whether the app is currently in a picture-in-picture window.
 *
 * The overlay controls are meaningless at that size — they would cover most of
 * the video — so the player hides them while this is true.
 */
@Composable
fun rememberInPictureInPicture(): Boolean {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return false

    var inPip by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode
        )
    }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    return inPip
}

/**
 * Shrinks the app into a picture-in-picture window.
 *
 * The aspect ratio comes from the video itself so the window isn't letterboxed,
 * clamped because Android rejects anything outside roughly 1:2.39–2.39:1 and
 * throws rather than ignoring it.
 *
 * @return false when the device or build doesn't support it.
 */
fun Activity.enterPip(videoWidth: Int, videoHeight: Int): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
        return false
    }

    val ratio = pipRatio(videoWidth, videoHeight)

    return runCatching {
        enterPictureInPictureMode(
            PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
                .build()
        )
    }.getOrDefault(false)
}

private fun pipRatio(width: Int, height: Int): Rational {
    if (width <= 0 || height <= 0) return Rational(16, 9)

    // Android's documented limits. Outside them enterPictureInPictureMode
    // throws IllegalArgumentException instead of falling back.
    val ratio = width.toDouble() / height.toDouble()
    return when {
        ratio > 2.39 -> Rational(239, 100)
        ratio < 1 / 2.39 -> Rational(100, 239)
        else -> Rational(width, height)
    }
}
