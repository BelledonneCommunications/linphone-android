/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.files.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.lifecycle.ViewModelProvider
import org.linphone.R
import org.linphone.activities.main.files.viewmodels.ImageFileViewModel
import org.linphone.activities.main.files.viewmodels.ImageFileViewModelFactory
import org.linphone.activities.main.fragments.SecureFragment
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.databinding.ImageViewerFragmentBinding

class ImageViewerFragment : SecureFragment<ImageViewerFragmentBinding>() {
    private lateinit var viewModel: ImageFileViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f

    override fun getLayoutId(): Int = R.layout.image_viewer_fragment

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        val filePath = sharedViewModel.fileToOpen.value
        filePath ?: return

        viewModel = ViewModelProvider(
            this,
            ImageFileViewModelFactory(filePath)
        )[ImageFileViewModel::class.java]
        binding.viewModel = viewModel

        isSecure = arguments?.getBoolean("Secure") ?: false

        gestureDetector = GestureDetector(requireContext(), object :
            GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                scaleFactor = if (scaleFactor == 1.0f) 2.0f else 1.0f
                binding.imageView.scaleX = scaleFactor
                binding.imageView.scaleY = scaleFactor
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent?,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleFactor <= 1.0f) return false

                val scrollX = binding.imageView.scrollX + distanceX.toInt()
                binding.imageView.scrollX = scrollX

                val scrollY = binding.imageView.scrollY + distanceY.toInt()
                binding.imageView.scrollY = scrollY
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(requireContext(), object :
            ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.1f, 5.0f)
                binding.imageView.scaleX = scaleFactor
                binding.imageView.scaleY = scaleFactor
                return false
            }
        })

        binding.imageView.setOnTouchListener { _, event ->
            val previousScaleFactor = scaleFactor
            scaleGestureDetector.onTouchEvent(event)

            if (previousScaleFactor != scaleFactor) {
                // Prevent touch event from going further
                return@setOnTouchListener true
            }

            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }
}
