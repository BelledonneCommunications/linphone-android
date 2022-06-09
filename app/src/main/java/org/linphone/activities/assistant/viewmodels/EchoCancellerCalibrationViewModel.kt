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

package org.linphone.activities.assistant.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.EcCalibratorStatus
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class EchoCancellerCalibrationViewModel : ViewModel() {
    val echoCalibrationTerminated = MutableLiveData<Event<Boolean>>()

    private val listener = object : CoreListenerStub() {
        override fun onEcCalibrationResult(core: Core, status: EcCalibratorStatus, delayMs: Int) {
            if (status == EcCalibratorStatus.InProgress) return
            echoCancellerCalibrationFinished(status, delayMs)
        }
    }

    init {
        coreContext.core.addListener(listener)
    }

    fun startEchoCancellerCalibration() {
        coreContext.core.startEchoCancellerCalibration()
    }

    fun echoCancellerCalibrationFinished(status: EcCalibratorStatus, delay: Int) {
        coreContext.core.removeListener(listener)
        when (status) {
            EcCalibratorStatus.DoneNoEcho -> {
                Log.i("[Echo Canceller Calibration] Done, no echo")
            }
            EcCalibratorStatus.Done -> {
                Log.i("[Echo Canceller Calibration] Done, delay is ${delay}ms")
            }
            EcCalibratorStatus.Failed -> {
                Log.w("[Echo Canceller Calibration] Failed")
            }
            EcCalibratorStatus.InProgress -> {
                Log.i("[Echo Canceller Calibration] In progress")
            }
        }
        echoCalibrationTerminated.value = Event(true)
    }
}
