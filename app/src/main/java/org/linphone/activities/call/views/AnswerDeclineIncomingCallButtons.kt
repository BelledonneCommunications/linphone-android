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
package org.linphone.activities.call.views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.databinding.DataBindingUtil
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.call.viewmodels.IncomingCallViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.CallIncomingAnswerDeclineButtonsBinding

class AnswerDeclineIncomingCallButtons : LinearLayout {
    private lateinit var binding: CallIncomingAnswerDeclineButtonsBinding
    private var mBegin = false
    private var mDeclineX = 0f
    private var mAnswerX = 0f
    private var mOldSize = 0f

    private val mAnswerTouchListener = OnTouchListener { view, motionEvent ->
        val curX: Float

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                binding.declineButton.visibility = View.GONE
                mAnswerX = motionEvent.x - view.width
                mBegin = true
                mOldSize = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                curX = motionEvent.x - view.width
                view.scrollBy((mAnswerX - curX).toInt(), view.scrollY)
                mOldSize -= mAnswerX - curX
                mAnswerX = curX
                if (mOldSize < -25) mBegin = false
                if (curX < (width / 4) - view.width && !mBegin) {
                    binding.viewModel?.answer(true)
                }
            }
            MotionEvent.ACTION_UP -> {
                binding.declineButton.visibility = View.VISIBLE
                view.scrollTo(0, view.scrollY)
            }
        }
        true
    }
    private val mDeclineTouchListener = OnTouchListener { view, motionEvent ->
        val curX: Float

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                binding.answerButton.visibility = View.GONE
                mDeclineX = motionEvent.x
            }
            MotionEvent.ACTION_MOVE -> {
                curX = motionEvent.x
                view.scrollBy((mDeclineX - curX).toInt(), view.scrollY)
                mDeclineX = curX
                if (curX > 3 * width / 4) {
                    binding.viewModel?.decline(true)
                }
            }
            MotionEvent.ACTION_UP -> {
                binding.answerButton.visibility = View.VISIBLE
                view.scrollTo(0, view.scrollY)
            }
        }
        true
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(
        context,
        attrs
    ) {
        init(context)
    }

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    fun setViewModel(viewModel: IncomingCallViewModel) {
        binding.viewModel = viewModel

        updateSlideMode()
    }

    private fun init(context: Context) {
        binding = DataBindingUtil.inflate(
            LayoutInflater.from(context), R.layout.call_incoming_answer_decline_buttons, this, true
        )

        updateSlideMode()
        configureAnimation()
    }

    private fun updateSlideMode() {
        val slideMode = binding.viewModel?.screenLocked?.value == true
        Log.i("[Call Incoming Decline Button] Slide mode is $slideMode")
        if (slideMode) {
            binding.answerButton.setOnTouchListener(mAnswerTouchListener)
            binding.declineButton.setOnTouchListener(mDeclineTouchListener)
        }
    }

    private fun configureAnimation() {
        if (!corePreferences.enableAnimations) return

        val accept1 = ObjectAnimator.ofFloat(binding.arrowAccept1, "alpha", 1f, 0.6f, 0.4f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }

        val accept2 = ObjectAnimator.ofFloat(binding.arrowAccept2, "alpha", 0.6f, 1f, 0.4f, 0.6f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }

        val accept3 = ObjectAnimator.ofFloat(binding.arrowAccept3, "alpha", 0.4f, 0.6f, 1f, 0.4f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }

        val hangup1 = ObjectAnimator.ofFloat(binding.arrowHangup1, "alpha", 1f, 0.6f, 0.4f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }

        val hangup2 = ObjectAnimator.ofFloat(binding.arrowHangup2, "alpha", 0.6f, 1f, 0.4f, 0.6f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }

        val hangup3 = ObjectAnimator.ofFloat(binding.arrowHangup3, "alpha", 0.4f, 0.6f, 1f, 0.4f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }

        AnimatorSet().apply {
            duration = 2000
            interpolator = LinearInterpolator()
            playTogether(accept1, accept2, accept3, hangup1, hangup2, hangup3)
            start()
        }
    }
}
