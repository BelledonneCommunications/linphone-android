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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.Data
import androidx.annotation.WorkerThread
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Address
import org.linphone.core.ChatRoom
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.utils.LinphoneUtils

/**
 * A tap on "Call via …" or "Message via …" in the system contacts or dialer (ACTION_VIEW of a data row of
 * [ContactsPublisher]), and sms:/smsto: links (SENDTO, VIEW). No UI of its own.
 *
 *  - call: a Linphone call - through the system phone app when the account is managed (CoreContext.startCall)
 *  - message: Linphone's conversation with that address (found, or created)
 */
class ContactActionActivity : Activity() {
    companion object {
        private const val TAG = "[Contact Action]"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        // a conversation is opened from here (this activity is in the foreground; once it is gone, Android would
        // not let the app start one): it finishes when that is done
        val waiting = when {
            uri == null -> {
                Log.w("$TAG No data")
                false
            }
            uri.scheme == "sms" || uri.scheme == "smsto" || uri.scheme == "mms" || uri.scheme == "mmsto" -> {
                val number = Uri.decode(uri.schemeSpecificPart.substringBefore('?')).split(',', ';').first().trim()
                if (number.isNotEmpty()) {
                    openConversation(number)
                    true
                } else {
                    Log.w("$TAG [$uri] has no number")
                    false
                }
            }
            else -> fromContactRow(uri)
        }
        if (!waiting) finish()
    }

    /** true: a conversation is being opened (the activity finishes then) */
    private fun fromContactRow(uri: Uri): Boolean {
        var mime: String? = null
        var target: String? = null
        try {
            contentResolver.query(uri, arrayOf(Data.MIMETYPE, Data.DATA1), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    mime = c.getString(0)
                    target = c.getString(1)
                }
            }
        } catch (e: SecurityException) {
            Log.e("$TAG Can't read the contact row: $e")
        }
        val t = target ?: run {
            Log.w("$TAG No address in [$uri]")
            return false
        }
        return when (mime) {
            ContactsPublisher.MIME_CALL -> {
                startCall(t)
                false
            }
            ContactsPublisher.MIME_MESSAGE -> {
                openConversation(t)
                true
            }
            else -> {
                Log.w("$TAG Unknown row type [$mime]")
                false
            }
        }
    }

    private fun startCall(target: String) {
        coreContext.postOnCoreThread { _ ->
            val address = addressOf(target) ?: return@postOnCoreThread
            Log.i("$TAG Calling [${address.asStringUriOnly()}]")
            coreContext.startCall(address)
        }
    }

    private fun openConversation(target: String) {
        val app = applicationContext
        coreContext.postOnCoreThread { core ->
            val address = addressOf(target)
            if (address == null) {
                coreContext.postOnMainThread { finish() }
                return@postOnCoreThread
            }
            val params = LinphoneUtils.getChatRoomParamsForOneToOneConversationWithAddress(address)
            if (params == null) {
                Log.w("$TAG No conversation possible with [${address.asStringUriOnly()}]")
                coreContext.postOnMainThread { finish() }
                return@postOnCoreThread
            }
            val local = LinphoneUtils.getDefaultAccount()?.params?.identityAddress
            val participants = arrayOf(address)
            val room = core.searchChatRoom(params, local, null, participants)
                ?: core.createChatRoom(params, participants)
            if (room == null) {
                Log.e("$TAG Could not open a conversation with [${address.asStringUriOnly()}]")
                coreContext.postOnMainThread { finish() }
                return@postOnCoreThread
            }
            // a group-capable (Flexisip) conversation is created asynchronously: then the list opens instead
            val id = if (room.state == ChatRoom.State.Created) LinphoneUtils.getConversationId(room) else ""
            Log.i("$TAG Opening conversation [$id] with [${address.asStringUriOnly()}]")
            val intent = Intent(app, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.ARGUMENTS_CHAT, true)
                putExtra(MainActivity.ARGUMENTS_CONVERSATION_ID, id)
            }
            coreContext.postOnMainThread {
                if (!isFinishing && !isDestroyed) startActivity(intent) else app.startActivity(intent)
                finish()
            }
        }
    }

    /** a SIP address, or a phone number on the default account (normalized as its settings say) */
    @WorkerThread
    private fun addressOf(target: String): Address? {
        val core = coreContext.core
        if (target.startsWith("sip:") || target.startsWith("sips:")) return core.interpretUrl(target, false)
        val account = LinphoneUtils.getDefaultAccount()
        val number = target.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (number.isEmpty()) return null
        val normalized = account?.normalizePhoneNumber(number) ?: number
        return account?.normalizeSipUri(normalized) ?: core.interpretUrl(normalized, true)
    }
}
