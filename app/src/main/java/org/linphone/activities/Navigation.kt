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
import org.linphone.LinphoneApplication.Companion.corePreferences
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

fun getRightToLeftAnimationNavOptions(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    if (!corePreferences.enableAnimations) return builder.build()
    return builder
        .setEnterAnim(R.anim.enter_right)
        .setExitAnim(R.anim.exit_left)
        .setPopEnterAnim(R.anim.enter_left)
        .setPopExitAnim(R.anim.exit_right)
        .build()
}

fun getLeftToRightAnimationNavOptions(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    if (!corePreferences.enableAnimations) return builder.build()
    return builder
        .setEnterAnim(R.anim.enter_left)
        .setExitAnim(R.anim.exit_right)
        .setPopEnterAnim(R.anim.enter_right)
        .setPopExitAnim(R.anim.exit_left)
        .build()
}

fun getRightBottomToLeftTopAnimationNavOptions(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    if (!corePreferences.enableAnimations) return builder.build()
    return builder
        .setEnterAnim(R.anim.enter_right_or_bottom)
        .setExitAnim(R.anim.exit_left_or_top)
        .setPopEnterAnim(R.anim.enter_left_or_top)
        .setPopExitAnim(R.anim.exit_right_or_bottom)
        .build()
}

fun getLeftTopToRightBottomAnimationNavOptions(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    if (!corePreferences.enableAnimations) return builder.build()
    return builder
        .setEnterAnim(R.anim.enter_left_or_top)
        .setExitAnim(R.anim.exit_right_or_bottom)
        .setPopEnterAnim(R.anim.enter_right_or_bottom)
        .setPopExitAnim(R.anim.exit_left_or_top)
        .build()
}

fun getRightBottomToLeftTopNoPopAnimationNavOptions(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    if (!corePreferences.enableAnimations) return builder.build()
    return builder
        .setEnterAnim(R.anim.enter_right_or_bottom)
        .setExitAnim(R.anim.exit_left_or_top)
        .build()
}

fun getLeftTopToRightBottomNoPopAnimationNavOptions(
    popUpTo: Int = -1,
    popUpInclusive: Boolean = false,
    singleTop: Boolean = true
): NavOptions {
    val builder = NavOptions.Builder()
    builder.setPopUpTo(popUpTo, popUpInclusive).setLaunchSingleTop(singleTop)
    if (!corePreferences.enableAnimations) return builder.build()
    return builder
        .setEnterAnim(R.anim.enter_left_or_top)
        .setExitAnim(R.anim.exit_right_or_bottom)
        .build()
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
        getRightToLeftAnimationNavOptions(R.id.dialerFragment, true)
    )
}

/* Tabs fragment related */

internal fun TabsFragment.navigateToCallHistory() {
    when (findNavController().currentDestination?.id) {
        R.id.masterContactsFragment -> findNavController().navigate(
            R.id.action_masterContactsFragment_to_masterCallLogsFragment,
            null,
            getLeftTopToRightBottomAnimationNavOptions()
        )
        R.id.dialerFragment -> findNavController().navigate(
            R.id.action_dialerFragment_to_masterCallLogsFragment,
            null,
            getLeftTopToRightBottomAnimationNavOptions()
        )
        R.id.masterChatRoomsFragment -> findNavController().navigate(
            R.id.action_masterChatRoomsFragment_to_masterCallLogsFragment,
            null,
            getLeftTopToRightBottomAnimationNavOptions()
        )
    }
}

internal fun TabsFragment.navigateToContacts() {
    when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> findNavController().navigate(
            R.id.action_masterCallLogsFragment_to_masterContactsFragment,
            null,
            getRightBottomToLeftTopAnimationNavOptions()
        )
        R.id.dialerFragment -> findNavController().navigate(
            R.id.action_dialerFragment_to_masterContactsFragment,
            null,
            getLeftTopToRightBottomAnimationNavOptions()
        )
        R.id.masterChatRoomsFragment -> findNavController().navigate(
            R.id.action_masterChatRoomsFragment_to_masterContactsFragment,
            null,
            getLeftTopToRightBottomAnimationNavOptions()
        )
    }
}

internal fun TabsFragment.navigateToDialer() {
    when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> findNavController().navigate(
            R.id.action_masterCallLogsFragment_to_dialerFragment,
            null,
            getRightBottomToLeftTopAnimationNavOptions()
        )
        R.id.masterContactsFragment -> findNavController().navigate(
            R.id.action_masterContactsFragment_to_dialerFragment,
            null,
            getRightBottomToLeftTopAnimationNavOptions()
        )
        R.id.masterChatRoomsFragment -> findNavController().navigate(
            R.id.action_masterChatRoomsFragment_to_dialerFragment,
            null,
            getLeftTopToRightBottomAnimationNavOptions()
        )
    }
}

internal fun TabsFragment.navigateToChatRooms() {
    when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> findNavController().navigate(
            R.id.action_masterCallLogsFragment_to_masterChatRoomsFragment,
            null,
            getRightBottomToLeftTopAnimationNavOptions()
        )
        R.id.masterContactsFragment -> findNavController().navigate(
            R.id.action_masterContactsFragment_to_masterChatRoomsFragment,
            null,
            getRightBottomToLeftTopAnimationNavOptions()
        )
        R.id.dialerFragment -> findNavController().navigate(
            R.id.action_dialerFragment_to_masterChatRoomsFragment,
            null,
            getRightBottomToLeftTopAnimationNavOptions()
        )
    }
}

/* Dialer related */

internal fun DialerFragment.navigateToContacts(uriToAdd: String?) {
    val deepLink = "linphone-android://contact/new/$uriToAdd"
    findNavController().navigate(
        Uri.parse(deepLink),
        getLeftTopToRightBottomNoPopAnimationNavOptions(R.id.masterContactsFragment, true)
    )
}

internal fun DialerFragment.navigateToConfigFileViewer() {
    val bundle = bundleOf("Secure" to true)
    findMasterNavController().navigate(
        R.id.action_global_configViewerFragment,
        bundle,
        getRightToLeftAnimationNavOptions()
    )
}

/* Chat related */

internal fun MasterChatRoomsFragment.navigateToChatRoom(args: Bundle) {
    val navHostFragment =
        childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
    val previousBackStackEntry = navHostFragment.navController.currentBackStackEntry
    if (previousBackStackEntry == null || previousBackStackEntry.destination.id == R.id.emptyChatFragment) {
        navHostFragment.navController.navigate(
            R.id.action_global_detailChatRoomFragment,
            args,
            popupTo(R.id.emptyChatFragment, true)
        )
    } else {
        navHostFragment.navController.navigate(
            R.id.action_global_detailChatRoomFragment,
            args,
            popupTo(R.id.chatRoomCreationFragment, true)
        )
    }
}

internal fun MasterChatRoomsFragment.navigateToChatRoomCreation(
    createGroupChatRoom: Boolean = false
) {
    val bundle = bundleOf("createGroup" to createGroupChatRoom)
    val navHostFragment =
        childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
    val previousBackStackEntry = navHostFragment.navController.currentBackStackEntry
    if (previousBackStackEntry == null || previousBackStackEntry.destination.id == R.id.emptyChatFragment) {
        navHostFragment.navController.navigate(
            R.id.action_global_chatRoomCreationFragment,
            bundle,
            popupTo(R.id.emptyChatFragment, true)
        )
    } else {
        navHostFragment.navController.navigate(
            R.id.action_global_chatRoomCreationFragment,
            bundle,
            popupTo(R.id.detailChatRoomFragment, true)
        )
    }
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
    findMasterNavController().navigate(Uri.parse(deepLink), getLeftToRightAnimationNavOptions())
}

internal fun DetailChatRoomFragment.navigateToImdn(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_imdnFragment,
            args,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun DetailChatRoomFragment.navigateToDevices() {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_devicesFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun DetailChatRoomFragment.navigateToGroupInfo() {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_groupInfoFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun DetailChatRoomFragment.navigateToEphemeralInfo() {
    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
        findNavController().navigate(
            R.id.action_detailChatRoomFragment_to_ephemeralFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun DetailChatRoomFragment.navigateToTextFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_textViewerFragment,
        bundle,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailChatRoomFragment.navigateToPdfFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_pdfViewerFragment,
        bundle,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailChatRoomFragment.navigateToImageFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_imageViewerFragment,
        bundle,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailChatRoomFragment.navigateToVideoFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_videoViewerFragment,
        bundle,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailChatRoomFragment.navigateToAudioFileViewer(secure: Boolean) {
    val bundle = bundleOf("Secure" to secure)
    findMasterNavController().navigate(
        R.id.action_global_audioViewerFragment,
        bundle,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailChatRoomFragment.navigateToEmptyChatRoom() {
    findNavController().navigate(
        R.id.action_global_emptyChatFragment,
        null,
        popupTo()
    )
}

internal fun ChatRoomCreationFragment.navigateToGroupInfo() {
    if (findNavController().currentDestination?.id == R.id.chatRoomCreationFragment) {
        findNavController().navigate(
            R.id.action_chatRoomCreationFragment_to_groupInfoFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun ChatRoomCreationFragment.navigateToChatRoom(args: Bundle) {
    if (findNavController().currentDestination?.id == R.id.chatRoomCreationFragment) {
        findNavController().navigate(
            R.id.action_chatRoomCreationFragment_to_detailChatRoomFragment,
            args,
            getRightToLeftAnimationNavOptions(R.id.emptyChatFragment, true)
        )
    }
}

internal fun ChatRoomCreationFragment.navigateToEmptyChatRoom() {
    findNavController().navigate(
        R.id.action_global_emptyChatFragment,
        null,
        popupTo()
    )
}

internal fun GroupInfoFragment.navigateToChatRoomCreation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.groupInfoFragment) {
        findNavController().navigate(R.id.action_groupInfoFragment_to_chatRoomCreationFragment,
            args,
            getLeftToRightAnimationNavOptions(R.id.chatRoomCreationFragment, true)
        )
    }
}

internal fun GroupInfoFragment.navigateToChatRoom(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.groupInfoFragment) {
        findNavController().navigate(
            R.id.action_groupInfoFragment_to_detailChatRoomFragment,
            args,
            getRightToLeftAnimationNavOptions(R.id.emptyChatFragment, true)
        )
    }
}

/* Contacts related */

internal fun MasterContactsFragment.navigateToContact() {
    if (findNavController().currentDestination?.id == R.id.masterContactsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_detailContactFragment,
            null,
            popupTo(R.id.emptyContactFragment, true)
        )
    }
}

internal fun MasterContactsFragment.navigateToContactEditor(sipUriToAdd: String? = null) {
    if (findNavController().currentDestination?.id == R.id.masterContactsFragment) {
        val bundle = if (sipUriToAdd != null) bundleOf("SipUri" to sipUriToAdd) else Bundle()
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_contactEditorFragment,
            bundle,
            popupTo(R.id.emptyContactFragment, true)
        )
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
        popupTo(R.id.masterContactsFragment, false)
    )
}

internal fun ContactEditorFragment.navigateToEmptyContact() {
    findNavController().navigate(
        R.id.action_global_emptyContactFragment,
        null,
        popupTo()
    )
}

internal fun DetailContactFragment.navigateToChatRoom(args: Bundle?) {
    findMasterNavController().navigate(
        R.id.action_global_masterChatRoomsFragment,
        args,
        getRightBottomToLeftTopAnimationNavOptions()
    )
}

internal fun DetailContactFragment.navigateToDialer(args: Bundle?) {
    findMasterNavController().navigate(
        R.id.action_global_dialerFragment,
        args,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailContactFragment.navigateToContactEditor() {
    if (findNavController().currentDestination?.id == R.id.detailContactFragment) {
        findNavController().navigate(
            R.id.action_detailContactFragment_to_contactEditorFragment,
            null,
            popupTo()
        )
    }
}

internal fun DetailContactFragment.navigateToEmptyContact() {
    findNavController().navigate(
        R.id.action_global_emptyContactFragment,
        null,
        popupTo()
    )
}

/* History related */

internal fun MasterCallLogsFragment.navigateToCallHistory() {
    if (findNavController().currentDestination?.id == R.id.masterCallLogsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.history_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_detailCallLogFragment,
            null,
            popupTo(R.id.emptyCallHistoryFragment, true)
        )
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
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailCallLogFragment.navigateToContacts(sipUriToAdd: String) {
    val deepLink = "linphone-android://contact/new/$sipUriToAdd"
    findMasterNavController().navigate(Uri.parse(deepLink), getRightToLeftAnimationNavOptions())
}

internal fun DetailCallLogFragment.navigateToContact(contact: NativeContact) {
    val deepLink = "linphone-android://contact/view/${contact.nativeId}"
    findMasterNavController().navigate(Uri.parse(deepLink), getRightBottomToLeftTopAnimationNavOptions())
}

internal fun DetailCallLogFragment.navigateToFriend(friendAddress: Address) {
    val deepLink = "linphone-android://contact/new/${friendAddress.asStringUriOnly()}"
    findMasterNavController().navigate(Uri.parse(deepLink), getRightToLeftAnimationNavOptions())
}

internal fun DetailCallLogFragment.navigateToChatRoom(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
        findMasterNavController().navigate(
            R.id.action_global_masterChatRoomsFragment,
            args,
            getRightBottomToLeftTopAnimationNavOptions()
        )
    }
}

internal fun DetailCallLogFragment.navigateToDialer(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
        findMasterNavController().navigate(
            R.id.action_global_dialerFragment,
            args,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun DetailCallLogFragment.navigateToEmptyCallHistory() {
    if (findNavController().currentDestination?.id == R.id.detailCallLogFragment) {
        findNavController().navigate(
            R.id.action_global_emptyFragment,
            null,
            popupTo()
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
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToTunnelSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_tunnelSettingsFragment,
            null,
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToAudioSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_audioSettingsFragment,
            null,
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToVideoSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_videoSettingsFragment,
            null,
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToCallSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_callSettingsFragment,
            null,
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToChatSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_chatSettingsFragment,
            null,
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToNetworkSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_networkSettingsFragment,
            null,
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToContactsSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_contactsSettingsFragment,
            null,
            popupTo()
        )
    }
}

internal fun SettingsFragment.navigateToAdvancedSettings() {
    if (findNavController().currentDestination?.id == R.id.settingsFragment) {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(
            R.id.action_global_advancedSettingsFragment,
            null,
            popupTo()
        )
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

internal fun AccountSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun AdvancedSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun AudioSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun CallSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun ChatSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun ContactsSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun NetworkSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun TunnelSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

internal fun VideoSettingsFragment.navigateToEmptySetting() {
    findNavController().navigate(
        R.id.action_global_emptySettingsFragment,
        null,
        popupTo()
    )
}

/* Side menu related */

internal fun SideMenuFragment.navigateToAccountSettings(identity: String) {
    val deepLink = "linphone-android://settings/$identity"
    findNavController().navigate(Uri.parse(deepLink), getRightToLeftAnimationNavOptions())
}

internal fun SideMenuFragment.navigateToSettings() {
    findNavController().navigate(
        R.id.action_global_settingsFragment,
        null,
        getRightToLeftAnimationNavOptions(R.id.settingsFragment)
    )
}

internal fun SideMenuFragment.navigateToAbout() {
    findNavController().navigate(
        R.id.action_global_aboutFragment,
        null,
        getRightToLeftAnimationNavOptions(R.id.aboutFragment)
    )
}

internal fun SideMenuFragment.navigateToRecordings() {
    findNavController().navigate(
        R.id.action_global_recordingsFragment,
        null,
        getRightToLeftAnimationNavOptions(R.id.recordingsFragment)
    )
}

/* Assistant related */

internal fun WelcomeFragment.navigateToEmailAccountCreation() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_emailAccountCreationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun WelcomeFragment.navigateToPhoneAccountCreation() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_phoneAccountCreationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun WelcomeFragment.navigateToAccountLogin() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_accountLoginFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun WelcomeFragment.navigateToGenericLogin() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_genericAccountLoginFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun WelcomeFragment.navigateToRemoteProvisioning() {
    if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
        findNavController().navigate(
            R.id.action_welcomeFragment_to_remoteProvisioningFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun AccountLoginFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.accountLoginFragment) {
        findNavController().navigate(
            R.id.action_accountLoginFragment_to_echoCancellerCalibrationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun AccountLoginFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.accountLoginFragment) {
        findNavController().navigate(
            R.id.action_accountLoginFragment_to_phoneAccountValidationFragment,
            args,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun GenericAccountLoginFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.genericAccountLoginFragment) {
        findNavController().navigate(
            R.id.action_genericAccountLoginFragment_to_echoCancellerCalibrationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun RemoteProvisioningFragment.navigateToQrCode() {
    if (findNavController().currentDestination?.id == R.id.remoteProvisioningFragment) {
        findNavController().navigate(
            R.id.action_remoteProvisioningFragment_to_qrCodeFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun RemoteProvisioningFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.remoteProvisioningFragment) {
        findNavController().navigate(
            R.id.action_remoteProvisioningFragment_to_echoCancellerCalibrationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun EmailAccountCreationFragment.navigateToEmailAccountValidation() {
    if (findNavController().currentDestination?.id == R.id.emailAccountCreationFragment) {
        findNavController().navigate(
            R.id.action_emailAccountCreationFragment_to_emailAccountValidationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun EmailAccountValidationFragment.navigateToAccountLinking(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.emailAccountValidationFragment) {
        findNavController().navigate(
            R.id.action_emailAccountValidationFragment_to_phoneAccountLinkingFragment,
            args,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun PhoneAccountCreationFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.phoneAccountCreationFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountCreationFragment_to_phoneAccountValidationFragment,
            args,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun PhoneAccountValidationFragment.navigateToAccountSettings(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.phoneAccountValidationFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountValidationFragment_to_accountSettingsFragment,
            args,
            getLeftToRightAnimationNavOptions(R.id.accountSettingsFragment, true)
        )
    }
}

internal fun PhoneAccountValidationFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.phoneAccountValidationFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountValidationFragment_to_echoCancellerCalibrationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}

internal fun PhoneAccountLinkingFragment.navigateToEchoCancellerCalibration() {
    if (findNavController().currentDestination?.id == R.id.phoneAccountLinkingFragment) {
        findNavController().navigate(
            R.id.action_phoneAccountLinkingFragment_to_echoCancellerCalibrationFragment,
            null,
            getRightToLeftAnimationNavOptions()
        )
    }
}
