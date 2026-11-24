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

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import android.provider.ContactsContract
import org.linphone.R
import org.linphone.core.tools.Log

/**
 * The "Linphone" account in the system's accounts and contacts. It holds no contacts of its own: its raw contacts
 * are joined to the phone's contacts and carry the actions "Call via …" and "Message via …" that the contacts app
 * and the dialer show ([ContactsPublisher]). No password, nothing to sign in to.
 */
object ContactsAccount {
    private const val TAG = "[Contacts Account]"

    /** must match android:accountType in res/xml/integration_authenticator.xml and integration_syncadapter.xml */
    const val TYPE = "org.linphone.integration"

    fun account(context: Context) = Account(context.getString(R.string.app_name), TYPE)

    /** create the account (once) and let the system sync it */
    fun ensure(context: Context): Account {
        val account = account(context)
        val am = AccountManager.get(context)
        if (am.getAccountsByType(TYPE).none { it == account }) {
            if (am.addAccountExplicitly(account, null, null)) {
                Log.i("$TAG Account created")
            } else {
                Log.w("$TAG The account could not be created")
            }
        }
        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
        // and a periodic run, for changes made while the app was not running
        ContentResolver.addPeriodicSync(account, ContactsContract.AUTHORITY, Bundle.EMPTY, 6 * 60 * 60L)
        return account
    }

    /** the account and everything it put into the contacts go away */
    fun remove(context: Context) {
        val am = AccountManager.get(context)
        for (a in am.getAccountsByType(TYPE)) {
            am.removeAccountExplicitly(a)
            Log.i("$TAG Account removed")
        }
    }

    fun exists(context: Context) = AccountManager.get(context).getAccountsByType(TYPE).isNotEmpty()

    /** run a sync now (the system schedules it, on its own thread) */
    fun requestSync(context: Context) {
        if (!exists(context)) return
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(account(context), ContactsContract.AUTHORITY, extras)
    }
}

/** the account's authenticator: there are no credentials, so every request is answered with "nothing to do" */
class ContactsAuthenticatorService : Service() {
    private lateinit var authenticator: Authenticator

    override fun onCreate() {
        authenticator = Authenticator(this)
    }

    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder

    private class Authenticator(context: Context) : AbstractAccountAuthenticator(context) {
        override fun editProperties(r: AccountAuthenticatorResponse?, accountType: String?): Bundle? = null

        override fun addAccount(
            r: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<out String>?,
            options: Bundle?
        ): Bundle? = null

        override fun confirmCredentials(r: AccountAuthenticatorResponse?, account: Account?, options: Bundle?): Bundle? = null

        override fun getAuthToken(
            r: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?
        ): Bundle? = null

        override fun getAuthTokenLabel(authTokenType: String?): String? = null

        override fun updateCredentials(
            r: AccountAuthenticatorResponse?,
            account: Account?,
            authTokenType: String?,
            options: Bundle?
        ): Bundle? = null

        override fun hasFeatures(r: AccountAuthenticatorResponse?, account: Account?, features: Array<out String>?): Bundle {
            return Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
        }
    }
}

/** the account's sync adapter: [ContactsPublisher] brings the actions up to date */
class ContactsSyncService : Service() {
    companion object {
        private val lock = Any()
        private var adapter: SyncAdapter? = null
    }

    override fun onCreate() {
        synchronized(lock) {
            if (adapter == null) adapter = SyncAdapter(applicationContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = adapter?.syncAdapterBinder

    private class SyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, true) {
        override fun onPerformSync(
            account: Account,
            extras: Bundle,
            authority: String,
            provider: ContentProviderClient,
            syncResult: SyncResult
        ) {
            try {
                ContactsPublisher.sync(context, account, provider, syncResult)
            } catch (e: Exception) {
                Log.e("[Contacts Sync] Failed: $e")
                syncResult.databaseError = true
            }
        }
    }
}
