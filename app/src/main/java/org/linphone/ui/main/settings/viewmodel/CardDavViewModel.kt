/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Factory
import org.linphone.core.FriendList
import org.linphone.core.FriendListListenerStub
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class CardDavViewModel : ViewModel() {
    companion object {
        private const val TAG = "[CardDAV ViewModel]"
    }

    val isEdit = MutableLiveData<Boolean>()

    val displayName = MutableLiveData<String>()

    val serverUrl = MutableLiveData<String>()

    val username = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val realm = MutableLiveData<String>()

    val showPassword = MutableLiveData<Boolean>()

    val syncInProgress = MutableLiveData<Boolean>()

    val showGreenToastEvent: MutableLiveData<Event<Pair<Int, Int>>> by lazy {
        MutableLiveData<Event<Pair<Int, Int>>>()
    }

    val showRedToastEvent: MutableLiveData<Event<Pair<Int, Int>>> by lazy {
        MutableLiveData<Event<Pair<Int, Int>>>()
    }

    private lateinit var friendList: FriendList

    private val friendListListener = object : FriendListListenerStub() {
        @WorkerThread
        override fun onSyncStatusChanged(
            friendList: FriendList,
            status: FriendList.SyncStatus,
            message: String?
        ) {
            Log.i(
                "$TAG Friend list [${friendList.displayName}] sync status changed to [$status] with message [$message]"
            )
            when (status) {
                FriendList.SyncStatus.Successful -> {
                    syncInProgress.postValue(false)
                    showGreenToastEvent.postValue(
                        Event(
                            Pair(
                                R.string.settings_contacts_carddav_sync_successful_toast,
                                R.drawable.check
                            )
                        )
                    )
                }
                FriendList.SyncStatus.Failure -> {
                    syncInProgress.postValue(false)
                    showRedToastEvent.postValue(
                        Event(
                            Pair(
                                R.string.settings_contacts_carddav_sync_error_toast,
                                R.drawable.warning_circle
                            )
                        )
                    )
                    if (isEdit.value == false) {
                        Log.e("$TAG Synchronization failed, removing Friend list from Core")
                        friendList.removeListener(this)
                        coreContext.core.removeFriendList(friendList)
                    }
                }
                else -> {}
            }
        }
    }

    init {
        isEdit.value = false
        showPassword.value = false
        syncInProgress.value = false
    }

    override fun onCleared() {
        if (::friendList.isInitialized) {
            friendList.removeListener(friendListListener)
        }

        super.onCleared()
    }

    @UiThread
    fun loadFriendList(name: String) {
        coreContext.postOnCoreThread { core ->
            val found = core.getFriendListByName(name)
            if (found == null) {
                Log.e("$TAG Failed to find friend list with display name [$name]!")
                return@postOnCoreThread
            }

            isEdit.postValue(true)
            friendList = found
            friendList.addListener(friendListListener)

            displayName.postValue(name)
            serverUrl.postValue(friendList.uri)
            Log.i("$TAG Existing friend list CardDAV values loaded")
        }
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            if (isEdit.value == true && ::friendList.isInitialized) {
                val name = friendList.displayName
                core.removeFriendList(friendList)
                Log.i("$TAG Removed friends list with display name [$name]")
                showGreenToastEvent.postValue(
                    Event(
                        Pair(
                            R.string.settings_contacts_carddav_deleted_toast,
                            R.drawable.trash_simple
                        )
                    )
                )
            }
        }
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    fun addAddressBook() {
        val name = displayName.value.orEmpty().trim()
        val server = serverUrl.value.orEmpty().trim()
        if (name.isEmpty() || server.isEmpty()) {
            showRedToastEvent.postValue(
                Event(
                    Pair(
                        R.string.settings_contacts_carddav_mandatory_field_not_filled_toast,
                        R.drawable.warning_circle
                    )
                )
            )
            return
        }

        val user = username.value.orEmpty().trim()
        val pwd = password.value.orEmpty().trim()
        val authRealm = realm.value.orEmpty().trim()

        coreContext.postOnCoreThread { core ->
            // TODO: add dialog to ask user before removing existing friend list & auth info ?
            if (isEdit.value == false) {
                val foundFriendList = core.getFriendListByName(name)
                if (foundFriendList != null) {
                    Log.w("$TAG Friend list [$name] already exists, removing it first")
                    core.removeFriendList(foundFriendList)
                }
            }

            if (user.isNotEmpty() && authRealm.isNotEmpty()) {
                val foundAuthInfo = core.findAuthInfo(authRealm, user, null)
                if (foundAuthInfo != null) {
                    Log.w("$TAG Auth info with username [$user] already exists, removing it first")
                    core.removeAuthInfo(foundAuthInfo)
                }

                Log.i("$TAG Adding auth info with username [$user]")
                val authInfo = Factory.instance().createAuthInfo(
                    user,
                    null,
                    pwd,
                    null,
                    authRealm,
                    null
                )
                core.addAuthInfo(authInfo)
            }

            if (isEdit.value == true && ::friendList.isInitialized) {
                Log.i(
                    "$TAG Changes were made to CardDAV friend list [$name], synchronizing it"
                )
            } else {
                friendList = core.createFriendList()
                friendList.displayName = name
                friendList.type = FriendList.Type.CardDAV
                friendList.uri = server
                friendList.isDatabaseStorageEnabled = true
                friendList.addListener(friendListListener)
                core.addFriendList(friendList)

                Log.i(
                    "$TAG CardDAV friend list [$name] created with server URL [$server], synchronizing it"
                )
            }

            syncInProgress.postValue(true)
            friendList.synchronizeFriendsFromServer()
        }
    }
}
