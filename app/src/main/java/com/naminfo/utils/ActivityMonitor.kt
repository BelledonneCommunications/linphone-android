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
package com.naminfo.utils

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import com.naminfo.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.core.tools.service.AndroidDispatcher

@UiThread
class ActivityMonitor : ActivityLifecycleCallbacks {
    companion object {
        private const val TAG = "[Activity Monitor]"
    }

    private val activities = ArrayList<Activity>()
    private var mActive = false
    private var mRunningActivities = 0
    private var mLastChecker: InactivityChecker? = null

    @Synchronized
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d("$TAG onActivityCreated [$activity]")
        if (!activities.contains(activity)) activities.add(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d("$TAG onActivityStarted [$activity]")
    }

    @Synchronized
    override fun onActivityResumed(activity: Activity) {
        Log.d("$TAG onActivityResumed [$activity]")
        if (!activities.contains(activity)) {
            activities.add(activity)
        }
        mRunningActivities++
        checkActivity()
    }

    @Synchronized
    override fun onActivityPaused(activity: Activity) {
        Log.d("$TAG onActivityPaused [$activity]")
        if (!activities.contains(activity)) {
            activities.add(activity)
        } else {
            mRunningActivities--
            checkActivity()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d("$TAG onActivityStopped [$activity]")
    }

    @Synchronized
    override fun onActivityDestroyed(activity: Activity) {
        Log.d("$TAG onActivityDestroyed [$activity]")
        activities.remove(activity)
    }

    @AnyThread
    fun isInForeground(): Boolean {
        return mActive
    }

    private fun startInactivityChecker() {
        if (mLastChecker != null) mLastChecker!!.cancel()
        AndroidDispatcher.dispatchOnUIThreadAfter(
            InactivityChecker().also { mLastChecker = it },
            2000
        )
    }

    private fun checkActivity() {
        if (mRunningActivities == 0) {
            if (mActive) startInactivityChecker()
        } else if (mRunningActivities > 0) {
            if (!mActive) {
                mActive = true
                onForegroundMode()
            }
            if (mLastChecker != null) {
                mLastChecker!!.cancel()
                mLastChecker = null
            }
        }
    }

    private fun onBackgroundMode() {
        Log.i("$TAG onBackgroundMode()")
        coreContext.onBackground()
    }

    private fun onForegroundMode() {
        Log.i("$TAG onForegroundMode()")
        coreContext.onForeground()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    internal inner class InactivityChecker : Runnable {
        private var isCanceled = false

        fun cancel() {
            isCanceled = true
        }

        override fun run() {
            if (!isCanceled) {
                if (mRunningActivities == 0 && mActive) {
                    mActive = false
                    onBackgroundMode()
                }
            }
        }
    }
}
