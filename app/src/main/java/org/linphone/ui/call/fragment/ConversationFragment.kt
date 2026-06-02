/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.ui.call.view.RoundCornersTextureView
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
import org.linphone.ui.fileviewer.FileViewerActivity
import org.linphone.ui.fileviewer.MediaViewerActivity
import org.linphone.ui.main.chat.fragment.ConversationFragment

class ConversationFragment : ConversationFragment() {
    companion object {
        private const val TAG = "[In-call Conversation Fragment]"
    }

    private lateinit var callViewModel: CurrentCallViewModel

    private lateinit var localPreviewVideoSurface: RoundCornersTextureView

    private var videoPreviewX: Float = 0f
    private var videoPreviewY: Float = 0f

    // For moving video preview purposes
    private val videoPreviewTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                videoPreviewX = view.x - event.rawX
                videoPreviewY = view.y - event.rawY
                true
            }
            MotionEvent.ACTION_UP -> {
                videoPreviewX = view.x
                videoPreviewY = view.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                view.animate()
                    .x(event.rawX + videoPreviewX)
                    .y(event.rawY + videoPreviewY)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callViewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        Log.i("$TAG Creating an in-call ConversationFragment")
        sendMessageViewModel.isCallConversation.value = true
        viewModel.isCallConversation.value = true

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        sharedViewModel.displayFileEvent.observe(viewLifecycleOwner) {
            it.consume { bundle ->
                if (findNavController().currentDestination?.id == R.id.inCallConversationFragment) {
                    val path = bundle.getString("path", "")
                    val isMedia = bundle.getBoolean("isMedia", false)
                    if (path.isEmpty()) {
                        Log.e("$TAG Can't navigate to file viewer for empty path!")
                        return@consume
                    }

                    Log.i(
                        "$TAG Navigating to [${if (isMedia) "media" else "file"}] viewer fragment with path [$path]"
                    )
                    if (isMedia) {
                        val intent = Intent(requireActivity(), MediaViewerActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    } else {
                        val intent = Intent(requireActivity(), FileViewerActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
            }
        }

        val layout = layoutInflater.inflate(R.layout.call_video_local_preview_surface, binding.constraintLayout, false)
        binding.constraintLayout.addView(layout)
        localPreviewVideoSurface = layout.findViewById(R.id.local_preview_video_surface)

        callViewModel.isSendingVideo.observe(viewLifecycleOwner) { sending ->
            coreContext.postOnCoreThread { core ->
                core.nativePreviewWindowId = if (sending) {
                    Log.i("$TAG We are sending video, setting capture preview surface")
                    localPreviewVideoSurface
                } else {
                    Log.i("$TAG We are not sending video, clearing capture preview surface")
                    null
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        (binding.root as? ViewGroup)?.doOnLayout {
            setupVideoPreview(localPreviewVideoSurface)
        }
    }

    override fun onPause() {
        super.onPause()

        cleanVideoPreview(localPreviewVideoSurface)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVideoPreview(localPreviewVideoSurface: RoundCornersTextureView) {
        if (requireActivity().isInPictureInPictureMode) {
            Log.i("$TAG Activity is in PiP mode, do not move video preview")
            return
        }

        localPreviewVideoSurface.setOnTouchListener(videoPreviewTouchListener)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun cleanVideoPreview(localPreviewVideoSurface: RoundCornersTextureView) {
        localPreviewVideoSurface.setOnTouchListener(null)
    }
}
