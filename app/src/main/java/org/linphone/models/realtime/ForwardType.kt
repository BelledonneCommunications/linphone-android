package org.linphone.models.realtime

enum class ForwardType(val forwardTypeName: String) {
    UserForwardStateOff("UserForwardStateOff"),
    UserForwardStateOn("UserForwardStateOn"),
    UserForwardStateFailover("UserForwardStateFailover")
}
