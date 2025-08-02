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
package com.naminfo.utils

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.naminfo.R
import com.naminfo.databinding.ToastBinding

@UiThread
fun View.slideInToastFromTop(
    root: ViewGroup,
    visible: Boolean
) {
    val view = this
    val transition: Transition = Slide(Gravity.TOP)
    transition.duration = 600
    transition.addTarget(view)

    TransitionManager.beginDelayedTransition(root, transition)
    view.visibility = if (visible) View.VISIBLE else View.GONE
}

@UiThread
fun View.slideInToastFromTopForDuration(
    root: ViewGroup,
    lifecycleScope: LifecycleCoroutineScope,
    duration: Long = 4000
) {
    val view = this
    val transition: Transition = Slide(Gravity.TOP)
    transition.duration = 600
    transition.addTarget(view)

    TransitionManager.beginDelayedTransition(root, transition)
    view.visibility = View.VISIBLE

    lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            delay(duration)
            withContext(Dispatchers.Main) {
                root.removeView(view)
            }
        }
    }
}

class ToastUtils {
    companion object {
        @MainThread
        fun getRedToast(
            context: Context,
            parent: ViewGroup,
            message: String,
            @DrawableRes icon: Int,
            doNotTint: Boolean = false
        ): ToastBinding {
            val redToast: ToastBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.toast,
                parent,
                false
            )
            redToast.doNotTint = doNotTint
            redToast.message = message
            redToast.icon = icon
            redToast.shadowColor = R.drawable.shape_toast_red_background
            redToast.textColor = R.color.danger_500
            redToast.root.visibility = View.GONE
            return redToast
        }

        @MainThread
        fun getGreenToast(
            context: Context,
            parent: ViewGroup,
            message: String,
            @DrawableRes icon: Int,
            doNotTint: Boolean = false
        ): ToastBinding {
            val greenToast: ToastBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.toast,
                parent,
                false
            )
            greenToast.doNotTint = doNotTint
            greenToast.message = message
            greenToast.icon = icon
            greenToast.shadowColor = R.drawable.shape_toast_green_background
            greenToast.textColor = R.color.success_500
            greenToast.root.visibility = View.GONE
            return greenToast
        }

        @MainThread
        fun getBlueToast(
            context: Context,
            parent: ViewGroup,
            message: String,
            @DrawableRes icon: Int,
            doNotTint: Boolean = false
        ): ToastBinding {
            val blueToast: ToastBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.toast,
                parent,
                false
            )
            blueToast.doNotTint = doNotTint
            blueToast.message = message
            blueToast.icon = icon
            blueToast.shadowColor = R.drawable.shape_toast_blue_background
            blueToast.textColor = R.color.info_500
            blueToast.root.visibility = View.GONE
            return blueToast
        }
    }
}
