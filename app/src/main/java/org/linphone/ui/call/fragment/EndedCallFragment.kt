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
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.tools.Log
import org.linphone.databinding.CallEndedFragmentBinding
import org.linphone.ui.call.viewmodel.CurrentCallViewModel

@UiThread
class EndedCallFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Ended Call Fragment]"

        private const val LOCALLY_TERMINATED_CALL_TIMEOUT: Long = 1000
        private const val REMOTELY_TERMINATED_CALL_TIMEOUT: Long = 2000
    }

    private lateinit var binding: CallEndedFragmentBinding

    private lateinit var callViewModel: CurrentCallViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallEndedFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Disable back gesture / button
        requireActivity().onBackPressedDispatcher.addCallback { }

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        observeToastEvents(callViewModel)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel

        Log.i("$TAG Showing ended call fragment")

        callViewModel.callDuration.observe(viewLifecycleOwner) { duration ->
            binding.chronometer.base = SystemClock.elapsedRealtime() - (1000 * duration)
            binding.chronometer.stop() // Do not start it and make sure it is stopped
        }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (callViewModel.terminatedByUsed) {
                    Log.i(
                        "$TAG Call terminated by user, waiting 1 second before finishing activity"
                    )
                    delay(LOCALLY_TERMINATED_CALL_TIMEOUT)
                } else {
                    Log.i(
                        "$TAG Call terminated by remote end, waiting 2 seconds before finishing activity"
                    )
                    delay(REMOTELY_TERMINATED_CALL_TIMEOUT)
                }

                withContext(Dispatchers.Main) {
                    Log.i("$TAG Finishing activity")
                    requireActivity().finish()
                }
            }
        }
    }
}
