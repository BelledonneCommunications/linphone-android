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
package org.linphone.ui.call.fragment

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.tools.Log

@UiThread
class NewCallFragment : AbstractNewTransferCallFragment() {
    companion object {
        private const val TAG = "[New Call Fragment]"
    }

    override val title: String
        get() = getString(R.string.call_action_start_new_call)

    @WorkerThread
    override fun action(address: Address) {
        Log.i("$TAG Calling [${address.asStringUriOnly()}]")
        coreContext.startAudioCall(address)

        coreContext.postOnMainThread {
            findNavController().popBackStack()
        }
    }
}
