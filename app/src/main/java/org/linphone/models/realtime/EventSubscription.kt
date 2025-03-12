package org.linphone.models.realtime

import org.linphone.models.realtime.interfaces.IRealtimeRequest

data class EventSubscription(
    val requestInfo: IRealtimeRequest,
    var subscriptionState: SubscriptionState
)
