package org.linphone.contacts

import androidx.lifecycle.MutableLiveData
import org.linphone.core.ChatRoom

abstract class AbstractAvatarModel {
    val trust = MutableLiveData<ChatRoom.SecurityLevel>()

    val showTrust = MutableLiveData<Boolean>()

    val initials = MutableLiveData<String>()

    val images = MutableLiveData<ArrayList<String>>()

    val showConferenceIcon = MutableLiveData<Boolean>()
}
