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

import android.os.Bundle
import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.tools.Log
import org.linphone.ui.call.viewmodel.CurrentCallViewModel

@UiThread
class TransferCallFragment : AbstractNewTransferCallFragment() {
    companion object {
        private const val TAG = "[Transfer Call Fragment]"
    }

    override val title: String
        get() = getString(R.string.call_transfer_title)

    private lateinit var callViewModel: CurrentCallViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
    }

    @WorkerThread
    override fun action(address: Address) {
        Log.i("$TAG Transferring current call to [${address.asStringUriOnly()}]")
        callViewModel.blindTransferCallTo(address)

        coreContext.postOnMainThread {
            try {
                findNavController().popBackStack()
            } catch (ise: IllegalStateException) {
                Log.e("$TAG Can't go back: $ise")
            }
        }
    }
}
