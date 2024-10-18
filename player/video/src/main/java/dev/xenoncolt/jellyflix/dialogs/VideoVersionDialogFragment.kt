package dev.xenoncolt.jellyflix.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.xenoncolt.jellyflix.models.FindroidItem
import dev.xenoncolt.jellyflix.player.video.R

fun getVideoVersionDialog(
    context: Context,
    item: FindroidItem,
    onItemSelected: (which: Int) -> Unit,
    onCancel: () -> Unit,
): AlertDialog {
    val items = item.sources.map { "${it.name} - ${it.type}" }.toTypedArray()
    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.select_a_version)
        .setItems(items) { _, which ->
            onItemSelected(which)
        }
        .setOnCancelListener {
            onCancel()
        }
        .create()
    return dialog
}
