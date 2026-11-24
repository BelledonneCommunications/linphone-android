/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.integration.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.annotation.AnyThread
import androidx.core.content.ContextCompat
import org.linphone.core.tools.Log
import org.linphone.integration.IntegrationPrefs
import org.linphone.integration.IntegrationPrefs.ContactsMode

/**
 * The contact actions on or off: the Linphone account is created (and synced) while the setting is on and the
 * contacts permissions are granted, and removed - with everything it added - when the setting is turned off.
 * While the app runs, a change in the address book is synced a few seconds later.
 */
object ContactsIntegration {
    private const val TAG = "[Contacts Integration]"
    private const val DEBOUNCE_MS = 10_000L

    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private lateinit var appContext: Context

    private val syncNow = Runnable { ContactsAccount.requestSync(appContext) }

    @AnyThread
    fun permissionsGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    /** bring the account in line with the setting and the permissions; then sync */
    @AnyThread
    fun apply(context: Context) {
        appContext = context.applicationContext
        val wanted = IntegrationPrefs.contactsMode != ContactsMode.OFF && permissionsGranted(appContext)
        if (wanted) {
            ContactsAccount.ensure(appContext)
            ContactsAccount.requestSync(appContext)
            observe(true)
        } else {
            observe(false)
            if (ContactsAccount.exists(appContext)) {
                Log.i("$TAG Contact actions off: removing the Linphone account and its rows")
                ContactsAccount.remove(appContext)
            }
        }
    }

    private fun observe(on: Boolean) {
        handler.post {
            if (on && observer == null) {
                val o = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        // many changes come in bursts (an account syncing its contacts): one sync after the last
                        handler.removeCallbacks(syncNow)
                        handler.postDelayed(syncNow, DEBOUNCE_MS)
                    }
                }
                appContext.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, o)
                observer = o
            } else if (!on && observer != null) {
                appContext.contentResolver.unregisterContentObserver(observer!!)
                observer = null
                handler.removeCallbacks(syncNow)
            }
        }
    }
}
