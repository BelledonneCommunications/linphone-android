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
import android.text.Spanned
import android.util.TypedValue
import androidx.core.text.HtmlCompat
import java.util.*
import java.util.regex.Pattern
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

        fun getTextWithHttpLinks(input: String): Spanned {
            var text = input
            if (text.contains("<")) {
                text = text.replace("<", "&lt;")
            }
            if (text.contains(">")) {
                text = text.replace(">", "&gt;")
            }
            if (text.contains("\n")) {
                text = text.replace("\n", "<br>")
            }
            if (text.contains("http://")) {
                val indexHttp = text.indexOf("http://")
                val indexFinHttp =
                    if (text.indexOf(" ", indexHttp) == -1) text.length else text.indexOf(
                        " ",
                        indexHttp
                    )
                val link = text.substring(indexHttp, indexFinHttp)
                val linkWithoutScheme = link.replace("http://", "")
                text = text.replaceFirst(
                    Pattern.quote(link).toRegex(),
                    "<a href=\"$link\">$linkWithoutScheme</a>"
                )
            }
            if (text.contains("https://")) {
                val indexHttp = text.indexOf("https://")
                val indexFinHttp =
                    if (text.indexOf(" ", indexHttp) == -1) text.length else text.indexOf(
                        " ",
                        indexHttp
                    )
                val link = text.substring(indexHttp, indexFinHttp)
                val linkWithoutScheme = link.replace("https://", "")
                text = text.replaceFirst(
                    Pattern.quote(link).toRegex(),
                    "<a href=\"$link\">$linkWithoutScheme</a>"
                )
            }
            return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }

        fun getInitials(displayName: String): String {
            if (displayName.isEmpty()) return ""

            val split = displayName.toUpperCase(Locale.getDefault()).split(" ")
            return when (split.size) {
                0 -> ""
                1 -> split[0][0].toString()
                else -> split[0][0].toString() + split[1][0].toString()
            }
        }

        fun pixelsToDp(pixels: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                pixels,
                coreContext.context.resources.displayMetrics
            )
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
