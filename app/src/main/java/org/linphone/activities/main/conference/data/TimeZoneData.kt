/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.conference.data

import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class TimeZoneData(timeZone: TimeZone) : Comparable<TimeZoneData> {
    val id: String = timeZone.id
    private val hours: Long
    private val minutes: Long
    private val gmt: String

    init {
        hours = TimeUnit.MILLISECONDS.toHours(timeZone.rawOffset.toLong())
        minutes = abs(
            TimeUnit.MILLISECONDS.toMinutes(timeZone.rawOffset.toLong()) -
                TimeUnit.HOURS.toMinutes(hours)
        )

        gmt = if (hours > 0) {
            String.format("%s - GMT+%d:%02d", timeZone.id, hours, minutes)
        } else {
            String.format("%s - GMT%d:%02d", timeZone.id, hours, minutes)
        }
    }

    override fun toString(): String {
        return gmt
    }

    override fun compareTo(other: TimeZoneData): Int {
        if (hours == other.hours) {
            if (minutes == other.minutes) {
                return id.compareTo(other.id)
            }
            return minutes.compareTo(other.minutes)
        }
        return hours.compareTo(other.hours)
    }
}
