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
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.MainThread
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.view.SoftwareKeyboardControllerCompat
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
        fun getFormattedString(@StringRes id: Int, arg: Any): String {
            return coreContext.context.getString(id, arg)
        }

        @AnyThread
        fun getFormattedString(@StringRes id: Int, arg1: Any, arg2: Any): String {
            return coreContext.context.getString(id, arg1, arg2)
        }

        @AnyThread
        fun getStringWithPlural(@PluralsRes id: Int, count: Int, value: String): String {
            return coreContext.context.resources.getQuantityString(id, count, value)
        }

        @AnyThread
        fun getColorInt(@ColorRes id: Int): Int {
            return ContextCompat.getColor(coreContext.context, id)
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

            for (i in split.indices) {
                val split = split[i]
                if (split.isNotEmpty()) {
                    try {
                        val symbol = extractFirstSymbol(split)
                        initials += symbol
                        if (symbol.length > 1) {
                            break
                        }
                    } catch (e: Exception) {
                        Log.e("$TAG Failed to extract first symbol if any: $e")
                        initials += split[0]
                    }

                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
        }

        @AnyThread
        fun isTextOnlyContainsEmoji(text: String): Boolean {
            if (text.isEmpty()) return false

            var textToCheck = text
            do {
                val firstSymbol = extractFirstSymbol(textToCheck)
                val symbolLength = firstSymbol.length
                if (symbolLength <= 1) return false
                textToCheck = textToCheck.substring(symbolLength)
            }  while (textToCheck.isNotEmpty())

            return true
        }

        @AnyThread
        fun getDeviceName(context: Context): String {
            var name = Settings.Global.getString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME
            )

            if (name == null) {
                Log.w("$TAG Failed to obtain device name, trying to get bluetooth name")
                try {
                    name = Settings.Secure.getString(
                        context.contentResolver,
                        "bluetooth_name"
                    )
                } catch (e: SecurityException) {
                    Log.e("$TAG Failed to get bluetooth_name: $e")
                }
            }

            if (name == null) {
                Log.w("$TAG Failed to obtain bluetooth name, using device's manufacturer & model")
                name = "${Build.MANUFACTURER} ${Build.MODEL}"
            }

            // Some VoIP providers such as voip.ms seem to not like apostrophe in user-agent
            name = name.replace("'", "")
            return name
        }

        @AnyThread
        private fun extractFirstSymbol(text: String): String {
            val sequence = StringBuilder(text.length)
            var isInJoin = false
            var codePoint: Int

            var i = 0
            while (i < text.length) {
                codePoint = text.codePointAt(i)
                if (codePoint == 0x200D) {
                    isInJoin = true
                    if (sequence.isEmpty()) {
                        i = text.offsetByCodePoints(i, 1)
                        continue
                    }
                } else {
                    if (sequence.isNotEmpty() && !isInJoin) break
                    isInJoin = false
                }
                sequence.appendCodePoint(codePoint)
                i = text.offsetByCodePoints(i, 1)
            }

            if (isInJoin) {
                for (i in sequence.length - 1 downTo 0) {
                    if (sequence[i].code == 0x200D) sequence.deleteCharAt(i) else break
                }
            }

            return sequence.toString()
        }
    }
}
