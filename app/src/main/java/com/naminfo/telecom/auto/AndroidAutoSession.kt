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
package com.naminfo.telecom.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import org.linphone.core.tools.Log

class AndroidAutoSession : Session() {
    companion object {
        private const val TAG = "[Android Auto Session]"
    }

    override fun onCreateScreen(intent: Intent): Screen {
        Log.i("$TAG Creating Screen object for host with API level [${carContext.carAppApiLevel}]")
        return AndroidAutoScreen(carContext)
    }
}
