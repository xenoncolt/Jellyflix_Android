package dev.xenoncolt.jellyflix.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.xenoncolt.jellyflix.torrent.StorageLocations

/**
 * What the app needs granted, and how each one is actually granted.
 *
 * The two are not the same kind of thing, which is the whole reason this exists:
 * notifications are a normal runtime prompt, while storage is either a runtime
 * prompt or a Settings page you have to send the user to, depending on the
 * Android version. Keeping that in one place stops each caller re-deriving it.
 */
enum class AppPermission {

    /**
     * The one that actually stopped downloads working, so it is asked for first.
     *
     * libtorrent writes with plain POSIX calls, so it needs real filesystem
     * access to put files anywhere outside the app's own folder. The folder
     * picker's SAF grant does not provide that, so without this every chosen
     * folder failed its write probe and downloads were stuck in app storage.
     */
    Storage,

    /**
     * Downloads run in a foreground service, and from Android 13 a service
     * without this permission runs with no visible notification at all — no
     * progress, no pause/resume/cancel, no "finished". The download still works;
     * the user just cannot see or control it.
     */
    Notifications,

    /**
     * The app updates itself from its own APK rather than a store, and handing
     * that APK to the system installer needs this. Asked for last because it is
     * the only one nothing breaks without today — the update simply cannot be
     * applied when the time comes.
     */
    InstallUpdates,
}

object AppPermissions {

    fun isGranted(context: Context, permission: AppPermission): Boolean = when (permission) {
        AppPermission.Storage -> StorageLocations.hasFullStorageAccess(context)
        AppPermission.Notifications -> hasNotifications(context)
        AppPermission.InstallUpdates -> context.packageManager.canRequestPackageInstalls()
    }

    /** Everything still outstanding, in the order worth asking for it. */
    fun missing(context: Context): List<AppPermission> =
        AppPermission.entries.filter { !isGranted(context, it) }

    private fun hasNotifications(context: Context): Boolean {
        // Before Android 13 there is no permission to hold; notifications are on
        // unless the user turned the channel off, which is not ours to ask about.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The runtime permission to request, or null when this one cannot be granted
     * by a prompt and needs [settingsIntents] instead.
     */
    fun runtimePermission(permission: AppPermission): String? = when (permission) {
        AppPermission.Storage ->
            // From API 30 "All files access" has no prompt — Settings only.
            Manifest.permission.WRITE_EXTERNAL_STORAGE
                .takeIf { Build.VERSION.SDK_INT < Build.VERSION_CODES.R }

        AppPermission.Notifications ->
            Manifest.permission.POST_NOTIFICATIONS
                .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

        // "Install unknown apps" has never had a prompt.
        AppPermission.InstallUpdates -> null
    }

    /**
     * Settings pages that can grant it, best first. Callers walk the list until
     * one opens: the per-app pages are missing on some OEM builds, and the app
     * details page is the last resort that always exists.
     */
    fun settingsIntents(context: Context, permission: AppPermission): List<Intent> {
        val packageUri = Uri.parse("package:${context.packageName}")

        val specific = when (permission) {
            AppPermission.Storage -> StorageLocations.fullStorageAccessIntents(context)

            AppPermission.InstallUpdates ->
                listOf(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri))

            // Only reached if the runtime prompt has been refused for good.
            AppPermission.Notifications -> emptyList()
        }

        return specific + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }

    fun title(permission: AppPermission): String = when (permission) {
        AppPermission.Storage -> "All files access"
        AppPermission.Notifications -> "Notifications"
        AppPermission.InstallUpdates -> "Install updates"
    }

    fun rationale(permission: AppPermission): String = when (permission) {
        AppPermission.Storage ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "So downloads can be saved to the folder you choose. " +
                    "Android only grants this from Settings."
            } else {
                "So downloads can be saved to the folder you choose."
            }

        AppPermission.Notifications ->
            "So downloads can show progress and let you pause or cancel them."

        AppPermission.InstallUpdates ->
            "So the app can install its own updates. Granted from Settings."
    }
}
