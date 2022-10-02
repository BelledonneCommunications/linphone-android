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
package org.linphone.activities.main.chat.data

import android.content.Context
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contact.GenericContactData
import org.linphone.core.EventLog

class EventData(private val eventLog: EventLog) : GenericContactData(
    if (eventLog.type == EventLog.Type.ConferenceSecurityEvent) {
        eventLog.securityEventFaultyDeviceAddress!!
    } else {
        if (eventLog.participantAddress == null) {
            eventLog.peerAddress!!
        } else {
            eventLog.participantAddress!!
        }
    }
) {
    val text = MutableLiveData<String>()

    val isSecurity: Boolean by lazy {
        when (eventLog.type) {
            EventLog.Type.ConferenceSecurityEvent -> true
            else -> false
        }
    }

    val isGroupLeft: Boolean by lazy {
        when (eventLog.type) {
            EventLog.Type.ConferenceTerminated -> true
            else -> false
        }
    }

    init {
        updateEventText()
    }

    private fun getName(): String {
        return contact.value?.name ?: displayName.value ?: ""
    }

    private fun updateEventText() {
        val context: Context = coreContext.context

        text.value = when (eventLog.type) {
            EventLog.Type.ConferenceCreated -> context.getString(R.string.chat_event_conference_created)
            EventLog.Type.ConferenceTerminated -> context.getString(R.string.chat_event_conference_destroyed)
            EventLog.Type.ConferenceParticipantAdded -> context.getString(R.string.chat_event_participant_added).format(getName())
            EventLog.Type.ConferenceParticipantRemoved -> context.getString(R.string.chat_event_participant_removed).format(getName())
            EventLog.Type.ConferenceSubjectChanged -> context.getString(R.string.chat_event_subject_changed).format(eventLog.subject)
            EventLog.Type.ConferenceParticipantSetAdmin -> context.getString(R.string.chat_event_admin_set).format(getName())
            EventLog.Type.ConferenceParticipantUnsetAdmin -> context.getString(R.string.chat_event_admin_unset).format(getName())
            EventLog.Type.ConferenceParticipantDeviceAdded -> context.getString(R.string.chat_event_device_added).format(getName())
            EventLog.Type.ConferenceParticipantDeviceRemoved -> context.getString(R.string.chat_event_device_removed).format(getName())
            EventLog.Type.ConferenceSecurityEvent -> {
                val name = getName()
                when (eventLog.securityEventType) {
                    EventLog.SecurityEventType.EncryptionIdentityKeyChanged -> context.getString(R.string.chat_security_event_lime_identity_key_changed).format(name)
                    EventLog.SecurityEventType.ManInTheMiddleDetected -> context.getString(R.string.chat_security_event_man_in_the_middle_detected).format(name)
                    EventLog.SecurityEventType.SecurityLevelDowngraded -> context.getString(R.string.chat_security_event_security_level_downgraded).format(name)
                    EventLog.SecurityEventType.ParticipantMaxDeviceCountExceeded -> context.getString(R.string.chat_security_event_participant_max_count_exceeded).format(name)
                    else -> "Unexpected security event for $name: ${eventLog.securityEventType}"
                }
            }
            EventLog.Type.ConferenceEphemeralMessageDisabled -> context.getString(R.string.chat_event_ephemeral_disabled)
            EventLog.Type.ConferenceEphemeralMessageEnabled -> context.getString(R.string.chat_event_ephemeral_enabled).format(formatEphemeralExpiration(context, eventLog.ephemeralMessageLifetime))
            EventLog.Type.ConferenceEphemeralMessageLifetimeChanged -> context.getString(R.string.chat_event_ephemeral_lifetime_changed).format(formatEphemeralExpiration(context, eventLog.ephemeralMessageLifetime))
            else -> "Unexpected event: ${eventLog.type}"
        }
    }

    private fun formatEphemeralExpiration(context: Context, duration: Long): String {
        return when (duration) {
            0L -> context.getString(R.string.chat_room_ephemeral_message_disabled)
            60L -> context.getString(R.string.chat_room_ephemeral_message_one_minute)
            3600L -> context.getString(R.string.chat_room_ephemeral_message_one_hour)
            86400L -> context.getString(R.string.chat_room_ephemeral_message_one_day)
            259200L -> context.getString(R.string.chat_room_ephemeral_message_three_days)
            604800L -> context.getString(R.string.chat_room_ephemeral_message_one_week)
            else -> "Unexpected duration"
        }
    }
}
