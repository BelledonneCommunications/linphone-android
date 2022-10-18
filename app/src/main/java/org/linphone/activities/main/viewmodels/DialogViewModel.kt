/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.R
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

class DialogViewModel(val message: String, val title: String = "") : ViewModel() {
    var showDoNotAskAgain: Boolean = false

    var showZrtp: Boolean = false

    var zrtpReadSas: String = ""

    var zrtpListenSas: String = ""

    var showTitle: Boolean = false

    var showIcon: Boolean = false

    var iconResource: Int = 0

    val doNotAskAgain = MutableLiveData<Boolean>()

    val dismissEvent = MutableLiveData<Event<Boolean>>()

    init {
        doNotAskAgain.value = false
        showTitle = title.isNotEmpty()
    }

    var showCancel: Boolean = false
    var cancelLabel: String = AppUtils.getString(R.string.dialog_cancel)
    private var onCancel: (Boolean) -> Unit = {}

    fun showCancelButton(cancel: (Boolean) -> Unit) {
        showCancel = true
        onCancel = cancel
    }

    fun showCancelButton(cancel: (Boolean) -> Unit, label: String = cancelLabel) {
        showCancel = true
        onCancel = cancel
        cancelLabel = label
    }

    fun onCancelClicked() {
        onCancel(doNotAskAgain.value == true)
    }

    var showDelete: Boolean = false
    var deleteLabel: String = AppUtils.getString(R.string.dialog_delete)
    private var onDelete: (Boolean) -> Unit = {}

    fun showDeleteButton(delete: (Boolean) -> Unit, label: String) {
        showDelete = true
        onDelete = delete
        deleteLabel = label
    }

    fun onDeleteClicked() {
        onDelete(doNotAskAgain.value == true)
    }

    var showOk: Boolean = false
    var okLabel: String = AppUtils.getString(R.string.dialog_ok)
    private var onOk: (Boolean) -> Unit = {}

    fun showOkButton(ok: (Boolean) -> Unit, label: String = okLabel) {
        showOk = true
        onOk = ok
        okLabel = label
    }

    fun onOkClicked() {
        onOk(doNotAskAgain.value == true)
    }

    fun dismiss() {
        dismissEvent.value = Event(true)
    }
}
