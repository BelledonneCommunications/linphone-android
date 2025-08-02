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
package com.naminfo.utils

import androidx.annotation.AnyThread
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.naminfo.R

class TimestampUtils {
    companion object {
        @AnyThread
        fun isToday(timestamp: Long, timestampInSecs: Boolean = true): Boolean {
            val cal = getCalendar()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            return isSameDay(cal, getCalendar())
        }

        @AnyThread
        fun isAfterToday(timestamp: Long, timestampInSecs: Boolean = true): Boolean {
            val cal = getCalendar()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp

            val tomorrow = getCalendar()
            tomorrow.timeInMillis = System.currentTimeMillis()

            if (cal.get(Calendar.ERA) > tomorrow.get(Calendar.ERA)) return true
            if (cal.get(Calendar.ERA) == tomorrow.get(Calendar.ERA) &&
                cal.get(Calendar.YEAR) > tomorrow.get(Calendar.YEAR)
            ) {
                return true
            }
            return cal.get(Calendar.ERA) == tomorrow.get(Calendar.ERA) &&
                cal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) >= tomorrow.get(Calendar.DAY_OF_YEAR)
        }

        @AnyThread
        fun isYesterday(timestamp: Long, timestampInSecs: Boolean = true): Boolean {
            val yesterday = getCalendar()
            yesterday.roll(Calendar.DAY_OF_MONTH, -1)
            val cal = getCalendar()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            return isSameDay(cal, yesterday)
        }

        @AnyThread
        fun dayOfWeek(timestamp: Long, timestampInSecs: Boolean = true): String {
            val calendar = getCalendar()
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
            val calendar = getCalendar()
            calendar.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            return calendar.get(Calendar.DAY_OF_MONTH).toString()
        }

        @AnyThread
        fun firstAndLastDayOfWeek(timestamp: Long, timestampInSecs: Boolean = true): String {
            val calendar = getCalendar()
            calendar.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
                calendar.add(Calendar.DATE, -1)
            }
            val firstDayOfWeek = calendar.get(Calendar.DAY_OF_MONTH).toString()
            val firstDayOfWeekMonth = calendar.getDisplayName(
                Calendar.MONTH,
                TextStyle.SHORT.ordinal,
                Locale.getDefault()
            )
            calendar.add(Calendar.DAY_OF_MONTH, 6)
            val lastDayOfWeek = calendar.get(Calendar.DAY_OF_MONTH).toString()
            val lastDayOfWeekMonth = calendar.getDisplayName(
                Calendar.MONTH,
                TextStyle.SHORT.ordinal,
                Locale.getDefault()
            )
            return if (firstDayOfWeekMonth == lastDayOfWeekMonth) {
                "$firstDayOfWeek - $lastDayOfWeek $lastDayOfWeekMonth"
            } else {
                "$firstDayOfWeek $firstDayOfWeekMonth - $lastDayOfWeek $lastDayOfWeekMonth"
            }
        }

        @AnyThread
        fun month(timestamp: Long, timestampInSecs: Boolean = true): String {
            val calendar = getCalendar()
            calendar.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            val month = calendar.getDisplayName(
                Calendar.MONTH,
                TextStyle.SHORT.ordinal,
                Locale.getDefault()
            )
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                ?: "?"

            val now = getCalendar()
            if (isSameYear(now, calendar)) {
                return month
            }

            val year = calendar.get(Calendar.YEAR)
            return "$month $year"
        }

        @AnyThread
        fun timeToString(time: Long, timestampInSecs: Boolean = true): String {
            val calendar = getCalendar()
            calendar.timeInMillis = if (timestampInSecs) time * 1000 else time
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
        }

        @AnyThread
        fun durationToString(duration: Int): String {
            val dateFormat = SimpleDateFormat(
                if (duration >= 3600) "HH:mm:ss" else "mm:ss",
                Locale.getDefault()
            )
            val cal = getCalendar()
            cal[0, 0, 0, 0, 0] = duration
            return dateFormat.format(cal.time)
        }

        @AnyThread
        fun toFullString(time: Long, timestampInSecs: Boolean = true): String {
            val calendar = getCalendar()
            calendar.timeInMillis = if (timestampInSecs) time * 1000 else time

            return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(calendar.time)
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

            val cal = getCalendar()
            cal.timeInMillis = if (timestampInSecs) timestamp * 1000 else timestamp
            val now = getCalendar()
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
        fun formatLifetime(seconds: Long): String {
            val days = seconds / 86400
            val hours = seconds / 3600
            return when {
                days >= 1L -> AppUtils.getStringWithPlural(
                    R.plurals.days,
                    days.toInt(),
                    "$days"
                )
                hours >= 1L -> {
                    String.format(
                        Locale.getDefault(),
                        "%02d:%02d:%02d",
                        seconds / 3600,
                        (seconds % 3600) / 60,
                        (seconds % 60)
                    )
                }
                else -> String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    (seconds % 3600) / 60,
                    (seconds % 60)
                )
            }
        }

        @AnyThread
        private fun isSameDay(
            cal1: Calendar,
            cal2: Calendar
        ): Boolean {
            return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        @AnyThread
        private fun isSameYear(
            cal1: Calendar,
            cal2: Calendar
        ): Boolean {
            return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
        }

        @AnyThread
        private fun getCalendar(): Calendar {
            return Calendar.getInstance(TimeZone.getDefault())
        }
    }
}
