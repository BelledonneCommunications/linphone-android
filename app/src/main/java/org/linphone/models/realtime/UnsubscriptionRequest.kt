package org.linphone.models.realtime

import org.linphone.models.realtime.interfaces.IRealtimeRequest

class UnsubscriptionRequest(override val event: SubscriptionData) : IRealtimeRequest {
    override val name: String = "unSubscribe"
}
