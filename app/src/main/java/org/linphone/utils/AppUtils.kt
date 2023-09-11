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
package org.linphone.utils

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.util.Rational
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AnyThread
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.ToastBinding

class AppUtils {
    companion object {
        @AnyThread
        fun getDimension(@DimenRes id: Int): Float {
            return coreContext.context.resources.getDimension(id)
        }

        @AnyThread
        fun getString(@StringRes id: Int): String {
            return coreContext.context.getString(id)
        }

        @AnyThread
        fun getFormattedString(@StringRes id: Int, args: Any): String {
            return coreContext.context.getString(id, args)
        }

        @MainThread
        fun getPipRatio(
            activity: Activity,
            forcePortrait: Boolean = false,
            forceLandscape: Boolean = false
        ): Rational {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            var height = displayMetrics.heightPixels
            var width = displayMetrics.widthPixels

            val aspectRatio = width / height
            if (aspectRatio < 1 / 2.39) {
                height = 2.39.toInt()
                width = 1
            } else if (aspectRatio > 2.39) {
                width = 2.39.toInt()
                height = 1
            }

            val ratio = if (width > height) {
                if (forcePortrait) {
                    Rational(height, width)
                } else {
                    Rational(width, height)
                }
            } else {
                if (forceLandscape) {
                    Rational(height, width)
                } else {
                    Rational(width, height)
                }
            }
            return ratio
        }

        @MainThread
        fun getRedToast(
            context: Context,
            parent: ViewGroup,
            message: String,
            @DrawableRes icon: Int
        ): ToastBinding {
            val redToast: ToastBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.toast,
                parent,
                false
            )
            redToast.message = message
            redToast.icon = icon
            redToast.shadowColor = R.drawable.shape_toast_red_shadow
            redToast.textColor = R.color.red_danger
            redToast.root.visibility = View.GONE
            return redToast
        }

        @MainThread
        fun getGreenToast(
            context: Context,
            parent: ViewGroup,
            message: String,
            @DrawableRes icon: Int
        ): ToastBinding {
            val greenToast: ToastBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.toast,
                parent,
                false
            )
            greenToast.message = message
            greenToast.icon = icon
            greenToast.shadowColor = R.drawable.shape_toast_green_shadow
            greenToast.textColor = R.color.green_online
            greenToast.root.visibility = View.GONE
            return greenToast
        }

        @MainThread
        fun getBlueToast(
            context: Context,
            parent: ViewGroup,
            message: String,
            @DrawableRes icon: Int
        ): ToastBinding {
            val blueToast: ToastBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.toast,
                parent,
                false
            )
            blueToast.message = message
            blueToast.icon = icon
            blueToast.shadowColor = R.drawable.shape_toast_blue_shadow
            blueToast.textColor = R.color.blue_trusted
            blueToast.root.visibility = View.GONE
            return blueToast
        }
    }
}
