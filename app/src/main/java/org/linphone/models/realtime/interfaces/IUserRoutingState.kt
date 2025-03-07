package org.linphone.models.realtime.interfaces

interface IUserRoutingState : IProvisioningState {
    val timeout: Int

    fun clone(): IUserRoutingState
}
