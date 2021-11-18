/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities

import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import org.linphone.R
import org.linphone.activities.assistant.fragments.*
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.fragments.ChatRoomCreationFragment
import org.linphone.activities.main.chat.fragments.DetailChatRoomFragment
import org.linphone.activities.main.chat.fragments.GroupInfoFragment
import org.linphone.activities.main.chat.fragments.MasterChatRoomsFragment
import org.linphone.activities.main.contact.fragments.ContactEditorFragment
import org.linphone.activities.main.contact.fragments.DetailContactFragment
import org.linphone.activities.main.contact.fragments.MasterContactsFragment
import org.linphone.activities.main.dialer.fragments.DialerFragment
import org.linphone.activities.main.fragments.TabsFragment
import org.linphone.activities.main.history.fragments.DetailCallLogFragment
import org.linphone.activities.main.history.fragments.MasterCallLogsFragment
import org.linphone.activities.main.settings.fragments.*
import org.linphone.activities.main.sidemenu.fragments.SideMenuFragment
import org.linphone.contact.NativeContact
import org.linphone.core.Address

internal fun Fragment.findMasterNavController(): NavController {
    return parentFragment?.parentFragment?.findNavController() ?: findNavController()
}

fun popupTo(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    return builder.build()
}

/* Main activity related */

internal fun MainActivity.navigateToDialer(args: Bundle?) {
    findNavController(R.id.nav_host_fragment).navigate(
        R.id.action_global_dialerFragment,
        args,
        popupTo(R.id.dialerFragment, true)
    )
}

internal fun MainActivity.navigateToChatRooms(args: Bundle? = null) {
    findNavController(R.id.nav_host_fragment).navigate(
        R.id.action_global_masterChatRoomsFragment,
        args,
        popupTo(R.id.masterChatRoomsFragment, true)
    )
}

internal fun MainActivity.navigateToChatRoom(localAddress: String?, peerAddress: String?) {
    val deepLink = "linphone-android://chat-room/$localAddress/$peerAddress"
    findNavController(R.id.nav_host_fragment).navigate(
        Uri.parse(deepLink),
        popupTo(R.id.masterChatRoomsFragment, true)
    )
}

internal fun MainActivity.navigateToContact(contactId: String?) {
    val deepLink = "linphone-android://contact/view/$contactId"
    findNavController(R.id.nav_host_fragment).navigate(
        Uri.parse(deepLink),
        popupTo(R.id.masterContactsFragment, true)
    )
}

/* Tabs fragment related */

internal fun TabsFragment.navigateToCallHistory() {
    val action = when (findNavController().currentDestination?.id) {
        R.id.masterContactsFragment -> R.id.action_masterContactsFragment_to_masterCallLogsFragment
        R.id.dialerFragment -> R.id.action_dialerFragment_to_masterCallLogsFragment
        R.id.masterChatRoomsFragment -> R.id.action_masterChatRoomsFragment_to_masterCallLogsFragment
        else -> R.id.action_global_masterCallLogsFragment
    }
    findNavController().navigate(
        action,
        null,
        popupTo(R.id.masterCallLogsFragment, true)
    )
}

internal fun TabsFragment.navigateToContacts() {
    val action = when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> R.id.action_masterCallLogsFragment_to_masterContactsFragment
        R.id.dialerFragment -> R.id.action_dialerFragment_to_masterContactsFragment
        R.id.masterChatRoomsFragment -> R.id.action_masterChatRoomsFragment_to_masterContactsFragment
        else -> R.id.action_global_masterContactsFragment
    }
    findNavController().navigate(
        action,
        null,
        popupTo(R.id.masterContactsFragment, true)
    )
}

internal fun TabsFragment.navigateToDialer() {
    val action = when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> R.id.action_masterCallLogsFragment_to_dialerFragment
        R.id.masterContactsFragment -> R.id.action_masterContactsFragment_to_dialerFragment
        R.id.masterChatRoomsFragment -> R.id.action_masterChatRoomsFragment_to_dialerFragment
        else -> R.id.action_global_dialerFragment
    }
    findNavController().navigate(
        action,
        null,
        popupTo(R.id.dialerFragment, true)
    )
}

internal fun TabsFragment.navigateToChatRooms() {
    val action = when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> R.id.action_masterCallLogsFragment_to_masterChatRoomsFragment
        R.id.masterContactsFragment -> R.id.action_masterContactsFragment_to_masterChatRoomsFragment
        R.id.dialerFragment -> R.id.action_dialerFragment_to_masterChatRoomsFragment
        else -> R.id.action_global_masterChatRoomsFragment
    }
    findNavController().navigate(
        action,
        null,
        popupTo(R.id.masterChatRoomsFragment, true)
    )
}

/* Dialer related */

internal fun DialerFragment.navigateToContacts(uriToAdd: String?) {
    val deepLink = "linphone-android://contact/new/$uriToAdd"
    findNavController().navigate(
        Uri.parse(deepLink),
        popupTo(R.id.masterContactsFragment, true)
    )
}

internal fun DialerFragment.navigateToConfigFileViewer() {
    val bundle = bundleOf("Secure" to true)
    findMasterNavController().navigate(
        R.id.action_global_configViewerFragment,
        bundle,
        popupTo()
    )
}

/* Chat related */

internal fun MasterChatRoomsFragment.navigateToChatRoom(args: Bundle) {
    val navHostFragment =
        childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
    val previousBackStackEntry = navHostFragment.navController.currentBackStackEntry
    val popUpToFragmentId = when (previousBackStackEntry?.destination?.id) {
        R.id.detailChatRoomFragment -> R.id.detailChatRoomFragment
        R.id.chatRoomCreationFragment -> R.id.chatRoomCreationFragment
        else -> R.id.emptyChatFragment
    }
    navHostFragment.navController.navigate(
        R.id.action_global_detailChatRoomFragment,
        args,
        popupTo(popUpToFragmentId, true)
    )
}

internal fun MasterChatRoomsFragment.navigateToChatRoomCreation(
    createGroupChatRoom: Boolean = false,
    slidingPane: SlidingPaneLayout
) {
    val bundle = bundleOf("createGroup" to createGroupChatRoom)
    val navHostFragment =
        childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
    val previousBackStackEntry = navHostFragment.navController.currentBackStackEntry
    val popUpToFragmentId = when (previousBackStackEntry?.destination?.id) {
        R.id.detailChatRoomFragment -> R.id.detailChatRoomFragment
        R.id.chatRoomCreationFragment -> R.id.chatRoomCreationFragment
        else -> R.id.emptyChatFragment
    }
    navHostFragment.navController.navigate(
        R.id.action_global_chatRoomCreationFragment,
        bundle,
        popupTo(popUpToFragmentId, true)
    )
    if (!slidingPane.isOpen) slidingPane.openPane()
}

internal fun MasterChatRoomsFragment.clearDisplayedChatRoom() {
    if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_emptyChatFragment,
            null,
            popupTo(R.id.emptyChatFragment, true)
        )
    }
}

internal fun DetailChatRoomFragment.navigateToContacts(sipUriToAdd: String) {
    val deepLink = "linphone-android://contact/new/$sipUriToAdd"
    findMasterNavController().navigate(Uri.parse(deepLink))
}

internal fun DetailChatRoomFragment.navigateToImdn(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_imdnFragment,
            args,
            popupTo()
        )
    }
}

internal fun DetailChatRoomFragment.navigateToDevices() {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_devicesFragment,
            null,
            popupTo()
        )
    }
}

internal fun DetailChatRoomFragment.navigateToGroupInfo() {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_groupInfoFragment,
            null,
            popupTo(R.id.groupInfoFragment, true)
        )
    }
}

internal fun DetailChatRoomFragment.navigateToEphemeralInfo() {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_ephemeralFragment,
            null,
            popupTo()
        )
    }
}

internal fun DetailChatRoomFragment.navigateToTextFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_textViewerFragment,
        bundle,
        popupTo()
    )
}

internal fun DetailChatRoomFragment.navigateToPdfFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_pdfViewerFragment,
        bundle,
        popupTo()
    )
}

internal fun DetailChatRoomFragment.navigateToImageFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_imageViewerFragment,
        bundle,
        popupTo()
    )
}

internal fun DetailChatRoomFragment.navigateToVideoFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_videoViewerFragment,
        bundle,
        popupTo()
    )
}

internal fun DetailChatRoomFragment.navigateToAudioFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_audioViewerFragment,
        bundle,
        popupTo()
    )
}

internal fun DetailChatRoomFragment.navigateToEmptyChatRoom() {
    findNavController().navigate(
        R.id.action_global_emptyChatFragment,
        null,
        popupTo(R.id.emptyChatFragment, true)
    )
}

internal fun ChatRoomCreationFragment.navigateToGroupInfo() {
    if (findNavController().currentDestination?.id == R.id.chatRoomCreationFragment) {
        findNavController().navigate(
            R.id.action_chatRoomCreationFragment_to_groupInfoFragment,
            null,
            popupTo(R.id.groupInfoFragment, true)
        )
    }
}

internal fun ChatRoomCreationFragment.navigateToChatRoom(args: Bundle) {
    if (findNavController().currentDestination?.id == R.id.chatRoomCreationFragment) {
        findNavController().navigate(
            R.id.action_chatRoomCreationFragment_to_detailChatRoomFragment,
            args,
            popupTo(R.id.chatRoomCreationFragment, true)
        )
    }
}

internal fun ChatRoomCreationFragment.navigateToEmptyChatRoom() {
    findNavController().navigate(
        R.id.action_global_emptyChatFragment,
        null,
        popupTo(R.id.emptyChatFragment, true)
    )
}

internal fun GroupInfoFragment.navigateToChatRoomCreation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.groupInfoFragment) {
        findNavController().navigate(
            R.id.action_groupInfoFragment_to_chatRoomCreationFragment,
            args,
            popupTo(R.id.chatRoomCreationFragment, true)
        )
    }
}

internal fun GroupInfoFragment.navigateToChatRoom(args: Bundle?, created: Boolean) {
    if (findNavController().currentDestination?.id == R.id.groupInfoFragment) {
        val popUpToFragmentId = if (created) { // To remove all creation fragments from back stack
            R.id.chatRoomCreationFragment
        } else {
            R.id.detailChatRoomFragment
        }
        findNavController().navigate(
            R.id.action_groupInfoFragment_to_detailChatRoomFragment,
            args,
            popupTo(popUpToFragmentId, true)
        )
    }
}

/* Contacts related */

internal fun MasterContactsFragment.navigateToContact() {
    if (findNavController().currentDestination?.id == R.id.masterContactsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
        val previousBackStackEntry = navHostFragment.navController.currentBackStackEntry
        val popUpToFragmentId = when (previousBackStackEntry?.destination?.id) {
            R.id.detailContactFragment -> R.id.detailContactFragment
            R.id.contactEditorFragment -> R.id.contactEditorFragment
            else -> R.id.emptyContactFragment
        }
        navHostFragment.navController.navigate(
            R.id.action_global_detailContactFragment,
            null,
            popupTo(popUpToFragmentId, true)
        )
    }
}

internal fun MasterContactsFragment.navigateToContactEditor(
    sipUriToAdd: String? = null,
    slidingPane: SlidingPaneLayout
) {
    if (findNavController().currentDestination?.id == R.id.masterContactsFragment) {
        val bundle = if (sipUriToAdd != null) bundleOf("SipUri" to sipUriToAdd) else Bundle()
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_contactEditorFragment,
            bundle,
            popupTo(R.id.emptyContactFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun MasterContactsFragment.clearDisplayedContact() {
    if (findNavController().currentDestination?.id == R.id.masterContactsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_emptyContactFragment,
            null,
            popupTo(R.id.emptyContactFragment, true)
        )
    }
}

internal fun ContactEditorFragment.navigateToContact(contact: NativeContact) {
    val bundle = Bundle()
    bundle.putString("id", contact.nativeId)
    findNavController().navigate(
        R.id.action_contactEditorFragment_to_detailContactFragment,
        bundle,
        popupTo(R.id.contactEditorFragment, true)
    )
}

internal fun ContactEditorFragment.navigateToEmptyContact() {
    findNavController().navigate(
        R.id.action_global_emptyContactFragment,
        null,
        popupTo(R.id.emptyContactFragment, true)
    )
}

internal fun DetailContactFragment.navigateToChatRoom(args: Bundle?) {
    findMasterNavController().navigate(
        R.id.action_global_masterChatRoomsFragment,
        args,
        popupTo(R.id.masterChatRoomsFragment, true)
    )
}

internal fun DetailContactFragment.navigateToDialer(args: Bundle?) {
    findMasterNavController().navigate(
        R.id.action_global_dialerFragment,
        args,
        popupTo(R.id.dialerFragment, true)
    )
}

internal fun DetailContactFragment.navigateToContactEditor() {
    if (findNavController().currentDestination?.id == R.id.detailContactFragment) {
        findNavController().navigate(
            R.id.action_detailContactFragment_to_contactEditorFragment,
            null,
            popupTo(R.id.contactEditorFragment, true)
        )
    }
}

internal fun DetailContactFragment.navigateToEmptyContact() {
    findNavController().navigate(
        R.id.action_global_emptyContactFragment,
        null,
        popupTo(R.id.emptyContactFragment, true)
    )
}

/* History related */

internal fun MasterCallLogsFragment.navigateToCallHistory(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.masterCallLogsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.history_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_detailCallLogFragment,
            null,
            popupTo(R.id.detailCallLogFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun MasterCallLogsFragment.clearDisplayedCallHistory() {
    if (findNavController().currentDestination?.id == R.id.masterCallLogsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.history_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_emptyFragment,
            null,
            popupTo(R.id.emptyCallHistoryFragment, true)
        )
    }
}

internal fun MasterCallLogsFragment.navigateToDialer(args: Bundle?) {
    findNavController().navigate(
        R.id.action_global_dialerFragment,
        args,
        popupTo(R.id.dialerFragment, true)
    )
}

internal fun DetailCallLogFragment.navigateToContacts(sipUriToAdd: String) {
    val deepLink = "linphone-android://contact/new/$sipUriToAdd"
    findMasterNavController().navigate(Uri.parse(deepLink))
}

internal fun DetailCallLogFragment.navigateToContact(contact: NativeContact) {
    val deepLink = "linphone-android://contact/view/${contact.nativeId}"
    findMasterNavController().navigate(Uri.parse(deepLink))
}

internal fun DetailCallLogFragment.navigateToFriend(friendAddress: Address) {
    val deepLink = "linphone-android://contact/new/${friendAddress.asStringUriOnly()}"
    findMasterNavController().navigate(Uri.parse(deepLink))
}

internal fun DetailCallLogFragment.navigateToChatRoom(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
        findMasterNavController().navigate(
            R.id.action_global_masterChatRoomsFragment,
            args,
            popupTo(R.id.masterChatRoomsFragment, true)
        )
    }
}

internal fun DetailCallLogFragment.navigateToDialer(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
        findMasterNavController().navigate(
            R.id.action_global_dialerFragment,
            args,
            popupTo(R.id.dialerFragment, true)
        )
    }
}

internal fun DetailCallLogFragment.navigateToEmptyCallHistory() {
    if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
        findNavController().navigate(
            R.id.action_global_emptyFragment,
            null,
            popupTo(R.id.emptyCallHistoryFragment, true)
        )
    }
}

/* Settings related */

internal fun SettingsFragment.navigateToAccountSettings(identity: String) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val bundle = bundleOf("Identity" to identity)
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_accountSettingsFragment,
            bundle,
            popupTo(R.id.accountSettingsFragment, true)
        )
    }
}

internal fun SettingsFragment.navigateToTunnelSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_tunnelSettingsFragment,
            null,
            popupTo(R.id.tunnelSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun SettingsFragment.navigateToAudioSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_audioSettingsFragment,
            null,
            popupTo(R.id.audioSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun SettingsFragment.navigateToVideoSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_videoSettingsFragment,
            null,
            popupTo(R.id.videoSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun SettingsFragment.navigateToCallSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_callSettingsFragment,
            null,
            popupTo(R.id.callSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun SettingsFragment.navigateToChatSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_chatSettingsFragment,
            null,
            popupTo(R.id.chatSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun SettingsFragment.navigateToNetworkSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_networkSettingsFragment,
            null,
            popupTo(R.id.networkSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun SettingsFragment.navigateToContactsSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_contactsSettingsFragment,
            null,
            popupTo(R.id.contactsSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun SettingsFragment.navigateToAdvancedSettings(slidingPane: SlidingPaneLayout) {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_advancedSettingsFragment,
            null,
            popupTo(R.id.advancedSettingsFragment, true)
        )
        if (!slidingPane.isOpen) slidingPane.openPane()
    }
}

internal fun AccountSettingsFragment.navigateToPhoneLinking(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.accountSettingsFragment) {
        findNavController().navigate(
            R.id.action_accountSettingsFragment_to_phoneAccountLinkingFragment,
            args,
            popupTo()
        )
    }
}

internal fun PhoneAccountLinkingFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.phoneAccountLinkingFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountLinkingFragment_to_phoneAccountValidationFragment,
            args,
            popupTo()
        )
    }
}

internal fun navigateToEmptySetting(navController: NavController) {
    navController.navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo(R.id.emptySettingsFragment, true)
    )
}

internal fun AccountSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun AdvancedSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun AudioSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun CallSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun ChatSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun ContactsSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun NetworkSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun TunnelSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

internal fun VideoSettingsFragment.navigateToEmptySetting() {
    navigateToEmptySetting(findNavController())
}

/* Side menu related */

internal fun SideMenuFragment.navigateToAccountSettings(identity: String) {
    val deepLink = "linphone-android://settings/$identity"
    findNavController().navigate(Uri.parse(deepLink))
}

internal fun SideMenuFragment.navigateToSettings() {
    findNavController().navigate(
        R.id.action_global_settingsFragment,
        null,
        popupTo(R.id.settingsFragment, true)
    )
}

internal fun SideMenuFragment.navigateToAbout() {
    findNavController().navigate(
        R.id.action_global_aboutFragment,
        null,
        popupTo(R.id.aboutFragment, true)
    )
}

internal fun SideMenuFragment.navigateToRecordings() {
    findNavController().navigate(
        R.id.action_global_recordingsFragment,
        null,
        popupTo(R.id.recordingsFragment, true)
    )
}

/* Assistant related */

internal fun WelcomeFragment.navigateToEmailAccountCreation() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_emailAccountCreationFragment,
            null,
            popupTo()
        )
    }
}

internal fun WelcomeFragment.navigateToPhoneAccountCreation() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_phoneAccountCreationFragment,
            null,
            popupTo()
        )
    }
}

internal fun WelcomeFragment.navigateToAccountLogin() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_accountLoginFragment,
            null,
            popupTo()
        )
    }
}

internal fun WelcomeFragment.navigateToGenericLogin() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_genericAccountLoginFragment,
            null,
            popupTo()
        )
    }
}

internal fun WelcomeFragment.navigateToRemoteProvisioning() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_remoteProvisioningFragment,
            null,
            popupTo()
        )
    }
}

internal fun AccountLoginFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.accountLoginFragment) {
        findNavController().navigate(
            R.id.action_accountLoginFragment_to_echoCancellerCalibrationFragment,
            null,
            popupTo()
        )
    }
}

internal fun AccountLoginFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.accountLoginFragment) {
        findNavController().navigate(
            R.id.action_accountLoginFragment_to_phoneAccountValidationFragment,
            args,
            popupTo()
        )
    }
}

internal fun GenericAccountLoginFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.genericAccountLoginFragment) {
        findNavController().navigate(
            R.id.action_genericAccountLoginFragment_to_echoCancellerCalibrationFragment,
            null,
            popupTo()
        )
    }
}

internal fun RemoteProvisioningFragment.navigateToQrCode() {
    if (findNavController().currentDestination?.id == R.id.remoteProvisioningFragment) {
        findNavController().navigate(
            R.id.action_remoteProvisioningFragment_to_qrCodeFragment,
            null,
            popupTo()
        )
    }
}

internal fun RemoteProvisioningFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.remoteProvisioningFragment) {
        findNavController().navigate(
            R.id.action_remoteProvisioningFragment_to_echoCancellerCalibrationFragment,
            null,
            popupTo()
        )
    }
}

internal fun EmailAccountCreationFragment.navigateToEmailAccountValidation() {
    if (findNavController().currentDestination?.id == R.id.emailAccountCreationFragment) {
        findNavController().navigate(
            R.id.action_emailAccountCreationFragment_to_emailAccountValidationFragment,
            null,
            popupTo()
        )
    }
}

internal fun EmailAccountValidationFragment.navigateToAccountLinking(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.emailAccountValidationFragment) {
        findNavController().navigate(
            R.id.action_emailAccountValidationFragment_to_phoneAccountLinkingFragment,
            args,
            popupTo()
        )
    }
}

internal fun PhoneAccountCreationFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.phoneAccountCreationFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountCreationFragment_to_phoneAccountValidationFragment,
            args,
            popupTo()
        )
    }
}

internal fun PhoneAccountValidationFragment.navigateToAccountSettings(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.phoneAccountValidationFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountValidationFragment_to_accountSettingsFragment,
            args,
            popupTo(R.id.accountSettingsFragment, true)
        )
    }
}

internal fun PhoneAccountValidationFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.phoneAccountValidationFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountValidationFragment_to_echoCancellerCalibrationFragment,
            null,
            popupTo()
        )
    }
}

internal fun PhoneAccountLinkingFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.phoneAccountLinkingFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountLinkingFragment_to_echoCancellerCalibrationFragment,
            null,
            popupTo()
        )
    }
}
