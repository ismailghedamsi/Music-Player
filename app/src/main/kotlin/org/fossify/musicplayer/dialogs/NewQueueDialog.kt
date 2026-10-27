package org.fossify.musicplayer.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.DialogNewQueueBinding

/** Name prompt for a new (or renamed) named queue. Empty names are refused. */
class NewQueueDialog(val activity: Activity, val defaultName: String, val callback: (name: String) -> Unit) {
    private val binding by activity.viewBinding(DialogNewQueueBinding::inflate)

    init {
        binding.newQueueTitle.setText(defaultName)
        binding.newQueueTitle.setSelection(defaultName.length)
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.queue_name) { alertDialog ->
                    alertDialog.showKeyboard(binding.newQueueTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = binding.newQueueTitle.value
                        if (name.isEmpty()) {
                            activity.toast(org.fossify.commons.R.string.empty_name)
                        } else {
                            alertDialog.dismiss()
                            callback(name)
                        }
                    }
                }
            }
    }
}
