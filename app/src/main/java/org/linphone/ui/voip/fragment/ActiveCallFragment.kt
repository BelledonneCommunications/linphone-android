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
package org.linphone.ui.voip.fragment

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.VoipActiveCallFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.voip.VoipActivity
import org.linphone.ui.voip.model.ZrtpSasConfirmationDialogModel
import org.linphone.ui.voip.viewmodel.CurrentCallViewModel
import org.linphone.utils.AppUtils
import org.linphone.utils.DialogUtils

@UiThread
class ActiveCallFragment : GenericFragment() {
    private lateinit var binding: VoipActiveCallFragmentBinding

    private lateinit var callViewModel: CurrentCallViewModel

    // For moving video preview purposes

    private var previewX: Float = 0f
    private var previewY: Float = 0f

    private val previewTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                previewX = view.x - event.rawX
                previewY = view.y - event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(event.rawX + previewX)
                    .y(event.rawY + previewY)
                    .setDuration(0)
                    .start()
                true
            }
            else -> {
                view.performClick()
                false
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = VoipActiveCallFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = callViewModel

        callViewModel.toggleExtraActionMenuVisibilityEvent.observe(viewLifecycleOwner) {
            /*it.consume { opened ->
                val visibility = if (opened) View.VISIBLE else View.GONE
                binding.extraActions.slideInExtraActionsMenu(binding.root as ViewGroup, visibility)
            }*/
        }

        callViewModel.isRemoteDeviceTrusted.observe(viewLifecycleOwner) { trusted ->
            if (trusted) {
                (requireActivity() as VoipActivity).showBlueToast(
                    "This call can be trusted",
                    R.drawable.trusted
                )
                // TODO: improve
                binding.avatar.avatarBorderColor = resources.getColor(
                    R.color.trusted_blue,
                    requireContext().theme
                )
                binding.avatar.avatarBorderWidth = AppUtils.getDimension(
                    R.dimen.avatar_trust_border_width
                ).toInt()
            }
        }

        callViewModel.showZrtpSasDialogEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val model = ZrtpSasConfirmationDialogModel(pair.first, pair.second)
                val dialog = DialogUtils.getZrtpSasConfirmationDialog(requireActivity(), model)

                model.dismissEvent.observe(viewLifecycleOwner) { event ->
                    event.consume {
                        dialog.dismiss()
                    }
                }

                model.trustVerified.observe(viewLifecycleOwner) { event ->
                    event.consume { verified ->
                        callViewModel.updateZrtpSas(verified)
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }
        }

        callViewModel.callDuration.observe(viewLifecycleOwner) { duration ->
            binding.chronometer.base = SystemClock.elapsedRealtime() - (1000 * duration)
            binding.chronometer.start()
        }
    }

    override fun onResume() {
        super.onResume()

        coreContext.postOnCoreThread { core ->
            core.nativeVideoWindowId = binding.remoteVideoSurface
            coreContext.core.nativePreviewWindowId = binding.localPreviewVideoSurface
            binding.localPreviewVideoSurface.setOnTouchListener(previewTouchListener)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.localPreviewVideoSurface.setOnTouchListener(null)
    }
}
