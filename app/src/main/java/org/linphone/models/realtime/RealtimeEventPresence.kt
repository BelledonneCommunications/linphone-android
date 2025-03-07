package org.linphone.models.realtime

import PresenceEventData
import androidx.annotation.Keep

@Keep
class RealtimeEventPresence(
    val data: PresenceEventData,
    val id: String,
    val tenantId: String,
    val userId: String
)
