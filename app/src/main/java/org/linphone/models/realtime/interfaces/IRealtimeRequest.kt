package org.linphone.models.realtime.interfaces

import org.linphone.models.realtime.SubscriptionData

interface IRealtimeRequest {
    val name: String
    val event: SubscriptionData
}
