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
package org.linphone.contact

import android.accounts.Account
import android.app.Service
import android.content.*
import android.os.Bundle
import android.os.IBinder

// Below classes are required to be able to use our own contact MIME type entry...
class DummySyncAdapter(context: Context, autoInit: Boolean) : AbstractThreadedSyncAdapter(context, autoInit) {
    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult?
    ) { }
}

class DummySyncService : Service() {
    companion object {
        private val syncAdapterLock = Any()
        private var syncAdapter: DummySyncAdapter? = null
    }

    override fun onCreate() {
        synchronized(syncAdapterLock) {
            if (syncAdapter == null) {
                syncAdapter = DummySyncAdapter(applicationContext, true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return syncAdapter?.syncAdapterBinder
    }
}
