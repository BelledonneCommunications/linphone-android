/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package com.naminfo.ui.main.meetings.model

import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class TimeZoneModel(timeZone: TimeZone) : Comparable<TimeZoneModel> {
    val id: String = timeZone.id

    private val hours: Long = TimeUnit.MILLISECONDS.toHours(timeZone.rawOffset.toLong())

    private val minutes: Long = abs(
        TimeUnit.MILLISECONDS.toMinutes(timeZone.rawOffset.toLong()) -
            TimeUnit.HOURS.toMinutes(hours)
    )

    private val gmt: String = if (hours >= 0) {
        String.format(Locale.getDefault(), "GMT+%02d:%02d - %s", hours, minutes, timeZone.id)
    } else {
        String.format(Locale.getDefault(), "GMT%02d:%02d - %s", hours, minutes, timeZone.id)
    }

    override fun toString(): String {
        return gmt
    }

    override fun compareTo(other: TimeZoneModel): Int {
        if (hours == other.hours) {
            if (minutes == other.minutes) {
                return id.compareTo(other.id)
            }
            return minutes.compareTo(other.minutes)
        }
        return hours.compareTo(other.hours)
    }
}
