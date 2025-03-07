package org.linphone.models.realtime

enum class SubscriptionState {
    // / <summary>
    // / The event is flagged for subscription but we've yet to send the request to the server.
    // / This will generally happen when the connection has gone down and has not yet
    // / been reestablished.
    // / </summary>
    FlaggedForSubscription,

    // / <summary>
    // / We have issued an subscription request but have yet to receive a response from the server
    // / </summary>
    Subscribing,

    // / <summary>
    // / The event has been subscribed to at the realtime server.
    // / </summary>
    Subscribed,

    // / <summary>
    // / The event is flagged for unsubscription but we've yet to send the request to the server.
    // / </summary>
    FlaggedForUnsubscription,

    // / <summary>
    // / We have issued an unsubscription request but have yet to receive a response from the server
    // / </summary>
    Unsubscribing
}
