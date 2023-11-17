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
package org.linphone.ui.main.chat.model

import androidx.annotation.WorkerThread
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.EventLog
import org.linphone.utils.AppUtils

class EventModel @WorkerThread constructor(private val eventLog: EventLog) {
    val text: String

    init {
        text = when (eventLog.type) {
            EventLog.Type.ConferenceCreated -> AppUtils.getString(
                R.string.conversation_event_conference_created
            )
            EventLog.Type.ConferenceTerminated -> AppUtils.getString(
                R.string.conversation_event_conference_destroyed
            )
            EventLog.Type.ConferenceParticipantAdded -> AppUtils.getFormattedString(
                R.string.conversation_event_participant_added,
                getName()
            )
            EventLog.Type.ConferenceParticipantRemoved -> AppUtils.getFormattedString(
                R.string.conversation_event_participant_removed,
                getName()
            )
            EventLog.Type.ConferenceSubjectChanged -> AppUtils.getFormattedString(
                R.string.conversation_event_subject_changed,
                eventLog.subject.orEmpty()
            )
            EventLog.Type.ConferenceParticipantSetAdmin -> AppUtils.getFormattedString(
                R.string.conversation_event_admin_set,
                getName()
            )
            EventLog.Type.ConferenceParticipantUnsetAdmin -> AppUtils.getFormattedString(
                R.string.conversation_event_admin_unset,
                getName()
            )
            EventLog.Type.ConferenceParticipantDeviceAdded -> AppUtils.getFormattedString(
                R.string.conversation_event_device_added,
                getName()
            )
            EventLog.Type.ConferenceParticipantDeviceRemoved -> AppUtils.getFormattedString(
                R.string.conversation_event_device_removed,
                getName()
            )
            EventLog.Type.ConferenceEphemeralMessageEnabled -> AppUtils.getString(
                R.string.conversation_event_ephemeral_messages_enabled
            )
            EventLog.Type.ConferenceEphemeralMessageDisabled -> AppUtils.getString(
                R.string.conversation_event_ephemeral_messages_disabled
            )
            EventLog.Type.ConferenceEphemeralMessageLifetimeChanged -> AppUtils.getFormattedString(
                R.string.conversation_event_ephemeral_messages_lifetime_changed,
                formatEphemeralExpiration(eventLog.ephemeralMessageLifetime).lowercase(
                    Locale.getDefault()
                )
            )
            else -> {
                eventLog.type.name
            }
        }
    }

    @WorkerThread
    fun getName(): String {
        val address = eventLog.participantAddress ?: eventLog.peerAddress
        val name = if (address != null) {
            coreContext.contactsManager.findDisplayName(address)
        } else {
            "<?>"
        }
        return name
    }

    @WorkerThread
    private fun formatEphemeralExpiration(duration: Long): String {
        return when (duration) {
            0L -> AppUtils.getString(
                R.string.dialog_conversation_message_ephemeral_duration_disabled
            )
            60L -> AppUtils.getString(
                R.string.dialog_conversation_message_ephemeral_duration_one_minute
            )
            3600L -> AppUtils.getString(
                R.string.dialog_conversation_message_ephemeral_duration_one_hour
            )
            86400L -> AppUtils.getString(
                R.string.dialog_conversation_message_ephemeral_duration_one_day
            )
            259200L -> AppUtils.getString(
                R.string.dialog_conversation_message_ephemeral_duration_three_days
            )
            604800L -> AppUtils.getString(
                R.string.dialog_conversation_message_ephemeral_duration_one_week
            )
            else -> "$duration s"
        }
    }
}
