package org.linphone.models.realtime

import org.linphone.models.realtime.interfaces.IRealtimeRequest

data class SubscriptionRequest(override val event: SubscriptionData) : IRealtimeRequest {
    override val name: String = "subscribe"
}
