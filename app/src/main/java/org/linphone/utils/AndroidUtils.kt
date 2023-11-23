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
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Rational
import android.view.View
import androidx.annotation.AnyThread
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.MainThread
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.emoji2.text.EmojiCompat
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

@UiThread
fun View.showKeyboard() {
    this.requestFocus()
    /*val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)*/
    // WindowCompat.getInsetsController(window, this).show(WindowInsetsCompat.Type.ime())*/
    val compat = SoftwareKeyboardControllerCompat(this)
    compat.show()
}

@UiThread
fun View.hideKeyboard() {
    /*val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)*/
    val compat = SoftwareKeyboardControllerCompat(this)
    compat.hide()
}

class AppUtils {
    companion object {
        const val TAG = "[App Utils]"

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

        @AnyThread
        fun getStringWithPlural(@PluralsRes id: Int, count: Int, value: String): String {
            return coreContext.context.resources.getQuantityString(id, count, value)
        }

        @AnyThread @ColorInt
        fun getColor(@ColorRes colorId: Int): Int {
            return ContextCompat.getColor(
                coreContext.context,
                colorId
            )
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

        @AnyThread
        fun getFirstLetter(displayName: String): String {
            return getInitials(displayName, 1)
        }

        @AnyThread
        fun getInitials(displayName: String, limit: Int = 2): String {
            if (displayName.isEmpty()) return ""

            val split = displayName.uppercase(Locale.getDefault()).split(" ")
            var initials = ""
            var characters = 0
            val emoji = coreContext.emojiCompat

            for (i in split.indices) {
                if (split[i].isNotEmpty()) {
                    try {
                        if (emoji.loadState == EmojiCompat.LOAD_STATE_SUCCEEDED && emoji.hasEmojiGlyph(
                                split[i]
                            )
                        ) {
                            val glyph = emoji.process(split[i])
                            if (characters > 0) { // Limit initial to 1 emoji only
                                Log.d("$TAG We limit initials to one emoji only")
                                initials = ""
                            }
                            initials += glyph
                            break // Limit initial to 1 emoji only
                        } else {
                            initials += split[i][0]
                        }
                    } catch (ise: IllegalStateException) {
                        Log.e("$TAG Can't call hasEmojiGlyph: $ise")
                        initials += split[i][0]
                    }

                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
        }

        @AnyThread
        fun getDeviceName(context: Context): String {
            var name = Settings.Global.getString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME
            )
            if (name == null) {
                name = Settings.Secure.getString(
                    context.contentResolver,
                    "bluetooth_name"
                )
            }
            if (name == null) {
                name = Build.MANUFACTURER + " " + Build.MODEL
            }
            return name
        }
    }
}
