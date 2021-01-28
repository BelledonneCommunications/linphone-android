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
package org.linphone.utils

import android.app.Activity
import android.content.*
import android.text.format.Formatter.formatShortFileSize
import android.util.TypedValue
import androidx.emoji.text.EmojiCompat
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log

/**
 * Various utility methods for application
 */
class AppUtils {
    companion object {
        fun getString(id: Int): String {
            return coreContext.context.getString(id)
        }

        fun getStringWithPlural(id: Int, count: Int): String {
            return coreContext.context.resources.getQuantityString(id, count, count)
        }

        fun getStringWithPlural(id: Int, count: Int, value: String): String {
            return coreContext.context.resources.getQuantityString(id, count, value)
        }

        fun getDimension(id: Int): Float {
            return coreContext.context.resources.getDimension(id)
        }

        fun getInitials(displayName: String, limit: Int = 2): String {
            if (displayName.isEmpty()) return ""

            val emoji = EmojiCompat.get()
            val split = displayName.toUpperCase(Locale.getDefault()).split(" ")
            var initials = ""
            var characters = 0

            for (i in split.indices) {
                if (split[i].isNotEmpty()) {
                    if (emoji.hasEmojiGlyph(split[i])) {
                        initials += emoji.process(split[i])
                    } else {
                        initials += split[i][0]
                    }
                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
        }

        fun pixelsToDp(pixels: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                pixels,
                coreContext.context.resources.displayMetrics
            )
        }

        fun bytesToDisplayableSize(bytes: Long): String {
            return formatShortFileSize(coreContext.context, bytes)
        }

        fun shareUploadedLogsUrl(activity: Activity, info: String) {
            val appName = activity.getString(R.string.app_name)
            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(
                Intent.EXTRA_EMAIL,
                arrayOf(activity.getString(R.string.about_bugreport_email))
            )
            intent.putExtra(Intent.EXTRA_SUBJECT, "$appName Logs")
            intent.putExtra(Intent.EXTRA_TEXT, info)
            intent.type = "text/plain"

            try {
                activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_uploaded_logs_link)))
            } catch (ex: ActivityNotFoundException) {
                Log.e(ex)
            }
        }
    }
}
