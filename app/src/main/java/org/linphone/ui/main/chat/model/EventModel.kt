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

import android.graphics.drawable.Drawable
import androidx.annotation.WorkerThread
import androidx.core.content.res.ResourcesCompat
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.EventLog
import org.linphone.utils.AppUtils
import org.linphone.utils.LinphoneUtils

class EventModel @WorkerThread constructor(private val eventLog: EventLog) {
    val text: String

    val icon: Drawable?

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
                LinphoneUtils.formatEphemeralExpiration(eventLog.ephemeralMessageLifetime).lowercase(
                    Locale.getDefault()
                )
            )
            else -> {
                eventLog.type.name
            }
        }

        icon = ResourcesCompat.getDrawable(
            coreContext.context.resources,
            when (eventLog.type) {
                EventLog.Type.ConferenceEphemeralMessageEnabled,
                EventLog.Type.ConferenceEphemeralMessageDisabled,
                EventLog.Type.ConferenceEphemeralMessageLifetimeChanged -> {
                    R.drawable.clock_countdown
                }
                EventLog.Type.ConferenceTerminated -> {
                    R.drawable.warning_circle
                }
                EventLog.Type.ConferenceSubjectChanged -> {
                    R.drawable.pencil_simple
                }
                EventLog.Type.ConferenceCreated,
                EventLog.Type.ConferenceParticipantAdded,
                EventLog.Type.ConferenceParticipantRemoved,
                EventLog.Type.ConferenceParticipantDeviceAdded,
                EventLog.Type.ConferenceParticipantDeviceRemoved -> {
                    R.drawable.door
                }
                EventLog.Type.ConferenceParticipantSetAdmin -> {
                    R.drawable.user_circle_check
                }
                else -> R.drawable.user_circle
            },
            coreContext.context.theme
        )
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
}
