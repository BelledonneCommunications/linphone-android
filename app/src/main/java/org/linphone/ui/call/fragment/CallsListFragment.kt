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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.databinding.CallsListFragmentBinding
import org.linphone.ui.call.adapter.CallsListAdapter
import org.linphone.ui.call.viewmodel.CallsViewModel
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.DialogUtils

class CallsListFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Calls List Fragment]"
    }

    private lateinit var binding: CallsListFragmentBinding

    private lateinit var viewModel: CallsViewModel

    private lateinit var callViewModel: CurrentCallViewModel

    private lateinit var adapter: CallsListAdapter

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = CallsListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = requireActivity().run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }
        observeToastEvents(callViewModel)

        binding.callsList.setHasFixedSize(true)
        binding.callsList.layoutManager = LinearLayoutManager(requireContext())

        adapter.callLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = CallMenuDialogFragment(model) {
                    // onDismiss
                    adapter.resetSelection()
                }
                modalBottomSheet.show(parentFragmentManager, CallMenuDialogFragment.TAG)
                bottomSheetDialog = modalBottomSheet
            }
        }

        adapter.callClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                model.togglePauseResume()
            }
        }

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setMergeCallsClickListener {
            showMergeCallsIntoConferenceConfirmationDialog()
        }

        callViewModel.isSendingVideo.observe(viewLifecycleOwner) { sending ->
            coreContext.postOnCoreThread { core ->
                core.nativePreviewWindowId = if (sending) {
                    Log.i("$TAG We are sending video, setting capture preview surface")
                    binding.localPreviewVideoSurface
                } else {
                    Log.i("$TAG We are not sending video, clearing capture preview surface")
                    null
                }
            }
        }

        viewModel.calls.observe(viewLifecycleOwner) {
            Log.i("$TAG Calls list updated with [${it.size}] items")
            adapter.submitList(it)

            // Wait for adapter to have items before setting it in the RecyclerView,
            // otherwise scroll position isn't retained
            if (binding.callsList.adapter != adapter) {
                binding.callsList.adapter = adapter
            }
        }
    }

    override fun onResume() {
        super.onResume()

        (binding.root as? ViewGroup)?.doOnLayout {
            setupVideoPreview(binding.localPreviewVideoSurface)
        }
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null

        cleanVideoPreview(binding.localPreviewVideoSurface)
    }

    private fun showMergeCallsIntoConferenceConfirmationDialog() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getConfirmMergeCallsDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.mergeCallsIntoConference()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
