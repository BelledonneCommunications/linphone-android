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
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentProviderOperation
import android.content.Context
import android.content.SyncResult
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.integration.IntegrationPrefs
import org.linphone.integration.IntegrationPrefs.ContactsMode
import org.linphone.integration.calls.ManagedAccounts
import org.linphone.utils.LinphoneUtils

/**
 * The actions "Call via <account>" and "Message via <account>" on the phone's contacts.
 *
 * For every contact with something callable (its phone numbers, or only its SIP addresses), the Linphone account
 * ([ContactsAccount]) has one raw contact, joined to the contact (AggregationExceptions, keep together), with one
 * data row per address and action (mime types [MIME_CALL], [MIME_MESSAGE], declared in
 * res/xml/integration_contacts.xml). The contacts app and the dialer show those rows with Linphone's icon;
 * tapping one opens [ContactActionActivity].
 *
 * A sync is a diff: contacts whose actions did not change are not touched (so a sync of an unchanged address book
 * writes nothing, and the change notifications it would cause can't loop).
 */
object ContactsPublisher {
    private const val TAG = "[Contacts Publisher]"

    const val MIME_CALL = "vnd.android.cursor.item/vnd.org.linphone.integration.call"
    const val MIME_MESSAGE = "vnd.android.cursor.item/vnd.org.linphone.integration.message"
    private const val BATCH = 300

    /** one action row: its mime type and the address it calls or messages */
    private data class Row(val mime: String, val target: String)

    private class Wanted(val name: String, val contactRawId: Long, val rows: MutableSet<Row> = LinkedHashSet())

    private class Existing(
        val rawId: Long,
        var name: String? = null,
        val rows: MutableSet<Row> = HashSet(),
        /** the summaries (DATA2: "via <account>") the rows were written with */
        val summaries: MutableSet<String> = HashSet()
    )

    fun sync(context: Context, account: Account, provider: ContentProviderClient, result: SyncResult) {
        if (!granted(context, Manifest.permission.READ_CONTACTS) || !granted(context, Manifest.permission.WRITE_CONTACTS)) {
            Log.w("$TAG No contacts permission")
            return
        }
        val mode = IntegrationPrefs.contactsMode
        val label = accountLabel(context)
        val summary = context.getString(R.string.integration_contact_summary, label)
        val wanted = if (mode == ContactsMode.OFF) emptyMap() else wanted(provider, account, mode)
        val existing = existing(provider, account)

        val ops = ArrayList<ContentProviderOperation>()
        var added = 0
        var changed = 0
        var removed = 0
        for ((key, w) in wanted) {
            val rows = w.rows.toSet()
            val e = existing[key]
            if (e == null) {
                insert(ops, account, key, w, rows, label, context)
                added++
            } else if (e.rows != rows || e.name != w.name || e.summaries.any { it != summary }) {
                update(ops, e, w, rows, label, context)
                changed++
            }
            flushIfFull(provider, ops)
        }
        for ((key, e) in existing) {
            if (key !in wanted) {
                ops.add(
                    ContentProviderOperation.newDelete(asSyncAdapter(RawContacts.CONTENT_URI, account))
                    .withSelection("${RawContacts._ID}=?", arrayOf(e.rawId.toString())).build()
                )
                removed++
                flushIfFull(provider, ops)
            }
        }
        if (ops.isNotEmpty()) provider.applyBatch(ops)
        result.stats.numInserts += added
        result.stats.numUpdates += changed
        result.stats.numDeletes += removed
        Log.i("$TAG ${wanted.size} contact(s): $added added, $changed changed, $removed removed")
    }

    private fun flushIfFull(provider: ContentProviderClient, ops: ArrayList<ContentProviderOperation>) {
        if (ops.size >= BATCH) {
            provider.applyBatch(ops)
            ops.clear()
        }
    }

    /** what the phone's contacts (not this account's own raw contacts) need */
    private fun wanted(provider: ContentProviderClient, account: Account, mode: ContactsMode): Map<String, Wanted> {
        val out = LinkedHashMap<String, Wanted>()
        val mimes = if (mode == ContactsMode.SIP_ONLY) {
            arrayOf(SipAddress.CONTENT_ITEM_TYPE)
        } else {
            arrayOf(Phone.CONTENT_ITEM_TYPE, SipAddress.CONTENT_ITEM_TYPE)
        }
        val selection = "${Data.MIMETYPE} IN (${mimes.joinToString { "?" }}) AND " +
            "(${RawContacts.ACCOUNT_TYPE} IS NULL OR ${RawContacts.ACCOUNT_TYPE}<>?) AND ${Data.IN_VISIBLE_GROUP}=1"
        provider.query(
            Data.CONTENT_URI,
            arrayOf(Data.LOOKUP_KEY, Data.DISPLAY_NAME_PRIMARY, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1),
            selection,
            mimes + account.type,
            null
        )?.use { c ->
            val call = IntegrationPrefs.contactsCallAction
            val message = IntegrationPrefs.contactsMessageAction
            val seenDigits = HashMap<String, MutableSet<String>>()
            while (c.moveToNext()) {
                val key = c.getString(0) ?: continue
                val value = c.getString(4)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val w = out.getOrPut(key) { Wanted(c.getString(1).orEmpty(), c.getLong(2)) }
                val target = if (c.getString(3) == SipAddress.CONTENT_ITEM_TYPE) {
                    if (value.startsWith("sip:")) value else "sip:$value"
                } else {
                    // one action per number, however it is written (+420 603 …, 603…, (603) …)
                    val digits = PhoneNumberUtils.normalizeNumber(value)
                    if (digits.isEmpty() || !seenDigits.getOrPut(key) { HashSet() }.add(digits)) continue
                    value
                }
                if (call) w.rows.add(Row(MIME_CALL, target))
                if (message) w.rows.add(Row(MIME_MESSAGE, target))
            }
        }
        return out.filterValues { it.rows.isNotEmpty() }
    }

    /** this account's raw contacts (by the lookup key of the contact they belong to) and their rows */
    private fun existing(provider: ContentProviderClient, account: Account): Map<String, Existing> {
        val byRaw = HashMap<Long, Pair<String, Existing>>()
        provider.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.SOURCE_ID),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.DELETED}=0",
            arrayOf(account.type, account.name),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val raw = c.getLong(0)
                val key = c.getString(1) ?: continue
                byRaw[raw] = key to Existing(raw)
            }
        }
        if (byRaw.isEmpty()) return emptyMap()
        provider.query(
            Data.CONTENT_URI,
            arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=?",
            arrayOf(account.type, account.name),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val e = byRaw[c.getLong(0)]?.second ?: continue
                when (val mime = c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> e.name = c.getString(2)
                    MIME_CALL, MIME_MESSAGE -> {
                        c.getString(2)?.let { e.rows.add(Row(mime, it)) }
                        e.summaries.add(c.getString(3).orEmpty())
                    }
                }
            }
        }
        return byRaw.values.associate { it }
    }

    private fun insert(
        ops: ArrayList<ContentProviderOperation>,
        account: Account,
        key: String,
        w: Wanted,
        rows: Set<Row>,
        label: String,
        context: Context
    ) {
        val first = ops.size
        ops.add(
            ContentProviderOperation.newInsert(asSyncAdapter(RawContacts.CONTENT_URI, account))
            .withValue(RawContacts.ACCOUNT_TYPE, account.type)
            .withValue(RawContacts.ACCOUNT_NAME, account.name)
            .withValue(RawContacts.SOURCE_ID, key)
            .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(asSyncAdapter(Data.CONTENT_URI, account))
            .withValueBackReference(Data.RAW_CONTACT_ID, first)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, w.name)
            .build()
        )
        for (row in rows) ops.add(rowInsert(account, row, label, context).withValueBackReference(Data.RAW_CONTACT_ID, first).build())
        // joined to the phone's contact, so its rows show on that contact, not on a contact of its own
        ops.add(
            ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
            .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
            .withValue(AggregationExceptions.RAW_CONTACT_ID1, w.contactRawId)
            .withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, first)
            .build()
        )
    }

    private fun update(
        ops: ArrayList<ContentProviderOperation>,
        e: Existing,
        w: Wanted,
        rows: Set<Row>,
        label: String,
        context: Context
    ) {
        val account = ContactsAccount.account(context)
        ops.add(
            ContentProviderOperation.newDelete(asSyncAdapter(Data.CONTENT_URI, account))
            .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)", arrayOf(e.rawId.toString(), MIME_CALL, MIME_MESSAGE))
            .build()
        )
        if (e.name != w.name) {
            ops.add(
                ContentProviderOperation.newUpdate(asSyncAdapter(Data.CONTENT_URI, account))
                .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(e.rawId.toString(), StructuredName.CONTENT_ITEM_TYPE))
                .withValue(StructuredName.DISPLAY_NAME, w.name)
                .build()
            )
        }
        for (row in rows) ops.add(rowInsert(account, row, label, context).withValue(Data.RAW_CONTACT_ID, e.rawId).build())
        ops.add(
            ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
            .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
            .withValue(AggregationExceptions.RAW_CONTACT_ID1, w.contactRawId)
            .withValue(AggregationExceptions.RAW_CONTACT_ID2, e.rawId)
            .build()
        )
    }

    /** DATA1 the address, DATA2 the summary (the account), DATA3 the detail ("Call +420 …") */
    private fun rowInsert(account: Account, row: Row, label: String, context: Context): ContentProviderOperation.Builder {
        val shown = row.target.removePrefix("sip:")
        val detail = context.getString(
            if (row.mime == MIME_CALL) R.string.integration_contact_call else R.string.integration_contact_message,
            shown
        )
        return ContentProviderOperation.newInsert(asSyncAdapter(Data.CONTENT_URI, account))
            .withValue(Data.MIMETYPE, row.mime)
            .withValue(Data.DATA1, row.target)
            .withValue(Data.DATA2, context.getString(R.string.integration_contact_summary, label))
            .withValue(Data.DATA3, detail)
    }

    private fun asSyncAdapter(uri: Uri, account: Account): Uri = uri.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
        .build()

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** the default SIP account's name, from the Core when it is up (the sync thread waits a moment for it) */
    private fun accountLabel(context: Context): String {
        val fallback = context.getString(R.string.app_name)
        if (!coreContext.isReady()) return fallback
        var label = fallback
        val done = CountDownLatch(1)
        coreContext.postOnCoreThread {
            LinphoneUtils.getDefaultAccount()?.let { label = ManagedAccounts.label(context, it) }
            done.countDown()
        }
        done.await(10, TimeUnit.SECONDS)
        return label
    }
}
