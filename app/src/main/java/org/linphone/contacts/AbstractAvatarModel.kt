package org.linphone.contacts

import androidx.lifecycle.MutableLiveData
import org.linphone.core.SecurityLevel

abstract class AbstractAvatarModel {
    val trust = MutableLiveData<SecurityLevel>()

    val showTrust = MutableLiveData<Boolean>()

    val initials = MutableLiveData<String>()

    val images = MutableLiveData<ArrayList<String>>()

    val forceConversationIcon = MutableLiveData<Boolean>()

    val forceConferenceIcon = MutableLiveData<Boolean>()

    val defaultToConversationIcon = MutableLiveData<Boolean>()

    val defaultToConferenceIcon = MutableLiveData<Boolean>()

    val skipInitials = MutableLiveData<Boolean>()
}
