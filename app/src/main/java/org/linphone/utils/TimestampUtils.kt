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

import androidx.annotation.AnyThread
import java.text.DateFormat
import java.text.Format
import java.text.SimpleDateFormat
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.linphone.LinphoneApplication.Companion.coreContext

class TimestampUtils {
    companion object {
        private const val TAG = "[Timestamp Utils]"

        @AnyThread
        fun isToday(timestamp: Long, timestampInSecs: Boolean = true): Boolean {
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            return isSameDay(cal, Calendar.getInstance())
        }

        @AnyThread
        fun isAfterToday(timestamp: Long, timestampInSecs: Boolean = true): Boolean {
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp

            val tomorrow = Calendar.getInstance()
            return cal[Calendar.ERA] >= tomorrow[Calendar.ERA] &&
                cal[Calendar.YEAR] >= tomorrow[Calendar.YEAR] &&
                cal[Calendar.DAY_OF_YEAR] >= tomorrow[Calendar.DAY_OF_YEAR]
        }

        @AnyThread
        fun isYesterday(timestamp: Long, timestampInSecs: Boolean = true): Boolean {
            val yesterday = Calendar.getInstance()
            yesterday.roll(Calendar.DAY_OF_MONTH, -1)
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            return isSameDay(cal, yesterday)
        }

        @AnyThread
        fun dateToString(date: Long, timestampInSecs: Boolean = true): String {
            val dateFormat: Format = android.text.format.DateFormat.getDateFormat(
                coreContext.context
            )
            val pattern = (dateFormat as SimpleDateFormat).toLocalizedPattern()

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = if (timestampInSecs) date * 1000 else date

            // See https://github.com/material-components/material-components-android/issues/882
            val dateFormatter = SimpleDateFormat(pattern, Locale.getDefault())
            dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
            return dateFormatter.format(calendar.time)
        }

        @AnyThread
        fun dayOfWeek(timestamp: Long, timestampInSecs: Boolean = true): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            val dayName = calendar.getDisplayName(
                Calendar.DAY_OF_WEEK,
                TextStyle.SHORT.ordinal,
                Locale.getDefault()
            )
            val upperCased = dayName?.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(
                        Locale.getDefault()
                    )
                } else {
                    it.toString()
                }
            } ?: "?"
            val shorten = upperCased.substring(0, 3)
            return "$shorten."
        }

        @AnyThread
        fun dayOfMonth(timestamp: Long, timestampInSecs: Boolean = true): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            return calendar.get(Calendar.DAY_OF_MONTH).toString()
        }

        @AnyThread
        fun month(timestamp: Long, timestampInSecs: Boolean = true): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            return calendar.getDisplayName(
                Calendar.MONTH,
                TextStyle.SHORT.ordinal,
                Locale.getDefault()
            )
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                ?: "?"
        }

        @AnyThread
        fun timeToString(time: Long, timestampInSecs: Boolean = true): String {
            val use24hFormat = android.text.format.DateFormat.is24HourFormat(
                coreContext.context
            )
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = if (timestampInSecs) time * 1000 else time

            return if (use24hFormat) {
                SimpleDateFormat("HH'h'mm", Locale.getDefault()).format(calendar.time)
            } else {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
            }
        }

        @AnyThread
        fun durationToString(duration: Int): String {
            val dateFormat = SimpleDateFormat(
                if (duration >= 3600) "HH:mm:ss" else "mm:ss",
                Locale.getDefault()
            )
            val cal = Calendar.getInstance()
            cal[0, 0, 0, 0, 0] = duration
            return dateFormat.format(cal.time)
        }

        @AnyThread
        fun toString(
            timestamp: Long,
            onlyDate: Boolean = false,
            timestampInSecs: Boolean = true,
            shortDate: Boolean = true,
            hideYear: Boolean = true
        ): String {
            val dateFormat = if (!onlyDate && isToday(timestamp, timestampInSecs)) {
                DateFormat.getTimeInstance(DateFormat.SHORT)
            } else {
                if (onlyDate) {
                    DateFormat.getDateInstance(if (shortDate) DateFormat.SHORT else DateFormat.FULL)
                } else {
                    DateFormat.getDateTimeInstance(
                        if (shortDate) DateFormat.SHORT else DateFormat.MEDIUM,
                        DateFormat.SHORT
                    )
                }
            } as SimpleDateFormat

            val cal = Calendar.getInstance()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            val now = Calendar.getInstance()
            if (hideYear && isSameYear(cal, now)) {
                // Remove the year part of the format
                dateFormat.applyPattern(
                    dateFormat.toPattern().replace(
                        "/?y+/?|,?\\s?y+\\s?".toRegex(),
                        if (shortDate) "" else " "
                    )
                )
            }

            val millis = if (timestampInSecs) timestamp * 1000 else timestamp
            return dateFormat.format(Date(millis))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        @AnyThread
        private fun isSameDay(
            cal1: Calendar,
            cal2: Calendar
        ): Boolean {
            return cal1[Calendar.ERA] == cal2[Calendar.ERA] &&
                cal1[Calendar.YEAR] == cal2[Calendar.YEAR] &&
                cal1[Calendar.DAY_OF_YEAR] == cal2[Calendar.DAY_OF_YEAR]
        }

        @AnyThread
        private fun isSameYear(
            cal1: Calendar,
            cal2: Calendar
        ): Boolean {
            return cal1[Calendar.ERA] == cal2[Calendar.ERA] &&
                cal1[Calendar.YEAR] == cal2[Calendar.YEAR]
        }
    }
}
