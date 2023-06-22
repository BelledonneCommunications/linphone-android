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
package org.linphone.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.*
import org.linphone.BuildConfig
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log

class CoreContext(val context: Context) : HandlerThread("Core Thread") {
    lateinit var core: Core

    @SuppressLint("HandlerLeak")
    private lateinit var coreThread: Handler

    private val coreListener = object : CoreListenerStub() {
        override fun onGlobalStateChanged(core: Core, state: GlobalState, message: String) {
            Log.i("[Context] Global state changed: $state")
        }
    }

    override fun run() {
        Looper.prepare()

        val looper = Looper.myLooper() ?: return
        coreThread = Handler(looper)

        core = Factory.instance().createCoreWithConfig(corePreferences.config, context)

        core.isAutoIterateEnabled = false
        core.addListener(coreListener)

        val timer = Timer("Linphone core.iterate() scheduler")
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    coreThread.post {
                        core.iterate()
                    }
                }
            },
            0,
            50
        )

        computeUserAgent()
        core.start()

        Looper.loop()
    }

    override fun destroy() {
        core.stop()

        quitSafely()
    }

    fun isReady(): Boolean {
        return ::core.isInitialized
    }

    fun postOnCoreThread(lambda: (core: Core) -> Unit) {
        coreThread.post {
            lambda.invoke(core)
        }
    }

    private fun computeUserAgent() {
        // TODO FIXME
        val deviceName: String = "Linphone6"
        val appName: String = "Linphone Android"
        val androidVersion = BuildConfig.VERSION_NAME
        val userAgent = "$appName/$androidVersion ($deviceName) LinphoneSDK"
        val sdkVersion = context.getString(org.linphone.core.R.string.linphone_sdk_version)
        val sdkBranch = context.getString(org.linphone.core.R.string.linphone_sdk_branch)
        val sdkUserAgent = "$sdkVersion ($sdkBranch)"
        core.setUserAgent(userAgent, sdkUserAgent)
    }
}
