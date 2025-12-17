/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.history.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.databinding.HistoryListLongPressMenuBinding

@UiThread
class HistoryMenuDialogFragment(
    private val contactExists: Boolean,
    private val onDismiss: (() -> Unit)? = null,
    private val onAddToContact: (() -> Unit)? = null,
    private val onGoToContact: (() -> Unit)? = null,
    private val onCopyNumberOrAddressToClipboard: (() -> Unit)? = null,
    private val onDeleteCallLog: (() -> Unit)? = null
) : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "HistoryMenuDialogFragment"
    }

    override fun onCancel(dialog: DialogInterface) {
        onDismiss?.invoke()
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDismiss?.invoke()
        super.onDismiss(dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // Makes sure all menu entries are visible,
        // required for landscape mode (otherwise only first item is visible)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = HistoryListLongPressMenuBinding.inflate(layoutInflater)
        view.contactExists = contactExists
        view.disableAddContact = corePreferences.disableAddContact

        view.setCopyNumberClickListener {
            onCopyNumberOrAddressToClipboard?.invoke()
            dismiss()
        }

        view.setDeleteClickListener {
            onDeleteCallLog?.invoke()
            dismiss()
        }

        view.setAddToContactsListener {
            onAddToContact?.invoke()
            dismiss()
        }

        view.setGoToContactClickListener {
            onGoToContact?.invoke()
            dismiss()
        }

        return view.root
    }
}
