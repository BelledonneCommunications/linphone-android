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
package org.linphone.activities.main

import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.assistant.fragments.*
import org.linphone.activities.main.chat.fragments.ChatRoomCreationFragment
import org.linphone.activities.main.chat.fragments.DetailChatRoomFragment
import org.linphone.activities.main.chat.fragments.GroupInfoFragment
import org.linphone.activities.main.chat.fragments.MasterChatRoomsFragment
import org.linphone.activities.main.contact.fragments.ContactEditorFragment
import org.linphone.activities.main.contact.fragments.DetailContactFragment
import org.linphone.activities.main.contact.fragments.MasterContactsFragment
import org.linphone.activities.main.fragments.TabsFragment
import org.linphone.activities.main.history.fragments.DetailCallLogFragment
import org.linphone.activities.main.history.fragments.MasterCallLogsFragment
import org.linphone.activities.main.settings.fragments.AccountSettingsFragment
import org.linphone.activities.main.settings.fragments.SettingsFragment
import org.linphone.activities.main.sidemenu.fragments.SideMenuFragment
import org.linphone.contact.NativeContact
import org.linphone.core.Address

internal fun Fragment.findMasterNavController(): NavController {
    return if (!resources.getBoolean(R.bool.isTablet)) {
        findNavController()
    } else {
        parentFragment?.parentFragment?.findNavController() ?: findNavController()
    }
}

fun getRightToLeftAnimationNavOptions(): NavOptions {
    return NavOptions.Builder()
        .setEnterAnim(R.anim.enter_right)
        .setExitAnim(R.anim.exit_left)
        .setPopEnterAnim(R.anim.enter_left)
        .setPopExitAnim(R.anim.exit_right)
        .build()
}

fun getLeftToRightAnimationNavOptions(): NavOptions {
    return NavOptions.Builder()
        .setEnterAnim(R.anim.enter_left)
        .setExitAnim(R.anim.exit_right)
        .setPopEnterAnim(R.anim.enter_right)
        .setPopExitAnim(R.anim.exit_left)
        .build()
}

fun getRightToLeftNoPopAnimationNavOptions(): NavOptions {
    return NavOptions.Builder()
        .setEnterAnim(R.anim.enter_right)
        .setExitAnim(R.anim.exit_left)
        .build()
}

fun getLeftToRightNoPopAnimationNavOptions(): NavOptions {
    return NavOptions.Builder()
        .setEnterAnim(R.anim.enter_left)
        .setExitAnim(R.anim.exit_right)
        .build()
}

/* Main activity related */

internal fun MainActivity.navigateToDialer(args: Bundle?) {
    findNavController(R.id.nav_host_fragment).navigate(
        R.id.action_global_dialerFragment,
        args,
        getRightToLeftAnimationNavOptions()
    )
}

/* Tabs fragment related */

internal fun TabsFragment.navigateToCallHistory() {
    when (findNavController().currentDestination?.id) {
        R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_masterCallLogsFragment, null, getLeftToRightNoPopAnimationNavOptions())
        R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterCallLogsFragment, null, getLeftToRightNoPopAnimationNavOptions())
        R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_masterCallLogsFragment, null, getLeftToRightNoPopAnimationNavOptions())
    }
}

internal fun TabsFragment.navigateToContacts() {
    when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_masterContactsFragment, null, getRightToLeftNoPopAnimationNavOptions())
        R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterContactsFragment, null, getLeftToRightNoPopAnimationNavOptions())
        R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_masterContactsFragment, null, getLeftToRightNoPopAnimationNavOptions())
    }
}

internal fun TabsFragment.navigateToDialer() {
    when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_dialerFragment, null, getRightToLeftNoPopAnimationNavOptions())
        R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_dialerFragment, null, getRightToLeftNoPopAnimationNavOptions())
        R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_dialerFragment, null, getLeftToRightNoPopAnimationNavOptions())
    }
}

internal fun TabsFragment.navigateToChatRooms() {
    when (findNavController().currentDestination?.id) {
        R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_masterChatRoomsFragment, null, getRightToLeftNoPopAnimationNavOptions())
        R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_masterChatRoomsFragment, null, getRightToLeftNoPopAnimationNavOptions())
        R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterChatRoomsFragment, null, getRightToLeftNoPopAnimationNavOptions())
    }
}

/* Chat related */

internal fun MasterChatRoomsFragment.navigateToChatRoom() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
            findNavController().navigate(R.id.action_masterChatRoomsFragment_to_detailChatRoomFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_detailChatRoomFragment)
    }
}

internal fun MasterChatRoomsFragment.navigateToChatRoomCreation(
    createGroupChatRoom: Boolean = false
) {
    val bundle = bundleOf("createGroup" to createGroupChatRoom)
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.masterChatRoomsFragment) {
            findNavController().navigate(
                R.id.action_masterChatRoomsFragment_to_chatRoomCreationFragment,
                bundle,
                getRightToLeftAnimationNavOptions()
            )
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.chat_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_chatRoomCreationFragment, bundle)
    }
}

internal fun DetailChatRoomFragment.navigateToContacts(sipUriToAdd: String) {
    val deepLink = "linphone-android://contact/new/$sipUriToAdd"
    findMasterNavController().navigate(Uri.parse(deepLink), getLeftToRightAnimationNavOptions())
}

internal fun DetailChatRoomFragment.navigateToChatRooms() {
    val deepLink = "linphone-android://chat/"
    findMasterNavController().navigate(Uri.parse(deepLink), getLeftToRightAnimationNavOptions())
}

internal fun DetailChatRoomFragment.navigateToImdn(args: Bundle?) {
    findNavController().navigate(R.id.action_detailChatRoomFragment_to_imdnFragment, args, getRightToLeftAnimationNavOptions())
}

internal fun DetailChatRoomFragment.navigateToDevices() {
    findNavController().navigate(R.id.action_detailChatRoomFragment_to_devicesFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun DetailChatRoomFragment.navigateToGroupInfo() {
    findNavController().navigate(R.id.action_detailChatRoomFragment_to_groupInfoFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun DetailChatRoomFragment.navigateToEphemeralInfo() {
    findNavController().navigate(R.id.action_detailChatRoomFragment_to_ephemeralFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun ChatRoomCreationFragment.navigateToGroupInfo(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.chatRoomCreationFragment) {
        findNavController().navigate(R.id.action_chatRoomCreationFragment_to_groupInfoFragment, args, getRightToLeftAnimationNavOptions())
    }
}

internal fun ChatRoomCreationFragment.navigateToChatRoom() {
    if (findNavController().currentDestination?.id == R.id.chatRoomCreationFragment) {
        findNavController().navigate(R.id.action_chatRoomCreationFragment_to_detailChatRoomFragment, null, getRightToLeftAnimationNavOptions())
    }
}

internal fun GroupInfoFragment.navigateToChatRoomCreation(args: Bundle?) {
    if (findNavController().currentDestination?.id == R.id.groupInfoFragment) {
        findNavController().navigate(R.id.action_groupInfoFragment_to_chatRoomCreationFragment, args, getLeftToRightAnimationNavOptions())
    }
}

internal fun GroupInfoFragment.navigateToChatRoom() {
    if (findNavController().currentDestination?.id == R.id.groupInfoFragment) {
        findNavController().navigate(R.id.action_groupInfoFragment_to_detailChatRoomFragment, null, getRightToLeftAnimationNavOptions())
    }
}

/* Contacts related */

internal fun MasterContactsFragment.navigateToContact() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.masterContactsFragment) {
            findNavController().navigate(R.id.action_masterContactsFragment_to_detailContactFragment,
            null,
            getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_detailContactFragment, null, getRightToLeftAnimationNavOptions())
    }
}

internal fun MasterContactsFragment.navigateToContactEditor(sipUriToAdd: String? = null) {
    val bundle = if (sipUriToAdd != null) bundleOf("SipUri" to sipUriToAdd) else Bundle()
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.masterContactsFragment) {
            findNavController().navigate(R.id.action_masterContactsFragment_to_contactEditorFragment, bundle, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.contacts_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_contactEditorFragment, bundle)
    }
}

internal fun ContactEditorFragment.navigateToContact(contact: NativeContact) {
    val deepLink = "linphone-android://contact/view/${contact.nativeId}"
    if (!resources.getBoolean(R.bool.isTablet)) {
        findMasterNavController().navigate(Uri.parse(deepLink), getRightToLeftAnimationNavOptions())
    } else {
        findMasterNavController().navigate(Uri.parse(deepLink))
    }
}

internal fun DetailContactFragment.navigateToChatRooms(args: Bundle?) {
    findNavController().navigate(R.id.action_global_masterChatRoomsFragment, args, getRightToLeftAnimationNavOptions())
}

internal fun DetailContactFragment.navigateToDialer(args: Bundle?) {
    findNavController().navigate(
        R.id.action_global_dialerFragment,
        args,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun DetailContactFragment.navigateToContactEditor() {
    findNavController().navigate(R.id.action_detailContactFragment_to_contactEditorFragment, null, getRightToLeftAnimationNavOptions())
}

/* History related */

internal fun MasterCallLogsFragment.navigateToCallHistory() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.masterCallLogsFragment) {
            findNavController().navigate(R.id.action_masterCallLogsFragment_to_detailCallLogFragment,
            null,
            getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.history_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_detailCallLogFragment)
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
    findMasterNavController().navigate(Uri.parse(deepLink), getRightToLeftAnimationNavOptions())
}

internal fun DetailCallLogFragment.navigateToFriend(friendAddress: Address) {
    val deepLink = "linphone-android://contact/new/${friendAddress.asStringUriOnly()}"
    findMasterNavController().navigate(Uri.parse(deepLink), getRightToLeftAnimationNavOptions())
}

internal fun DetailCallLogFragment.navigateToChatRooms(args: Bundle?) {
    findNavController().navigate(R.id.action_global_masterChatRoomsFragment, args, getRightToLeftAnimationNavOptions())
}

internal fun DetailCallLogFragment.navigateToDialer(args: Bundle?) {
    findNavController().navigate(
        R.id.action_global_dialerFragment,
        args,
        getRightToLeftAnimationNavOptions()
    )
}

/* Settings related */

internal fun SettingsFragment.navigateToAccountSettings(identity: String) {
    val bundle = bundleOf("Identity" to identity)
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(
                R.id.action_settingsFragment_to_accountSettingsFragment,
                bundle,
                getRightToLeftAnimationNavOptions()
            )
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_accountSettingsFragment, bundle)
    }
}

internal fun SettingsFragment.navigateToTunnelSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_tunnelSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_tunnelSettingsFragment)
    }
}

internal fun SettingsFragment.navigateToAudioSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_audioSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_audioSettingsFragment)
    }
}

internal fun SettingsFragment.navigateToVideoSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_videoSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_videoSettingsFragment)
    }
}

internal fun SettingsFragment.navigateToCallSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_callSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_callSettingsFragment)
    }
}

internal fun SettingsFragment.navigateToChatSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_chatSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_chatSettingsFragment)
    }
}

internal fun SettingsFragment.navigateToNetworkSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_networkSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_networkSettingsFragment)
    }
}

internal fun SettingsFragment.navigateToContactsSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_contactsSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_contactsSettingsFragment)
    }
}

internal fun SettingsFragment.navigateToAdvancedSettings() {
    if (!resources.getBoolean(R.bool.isTablet)) {
        if (findNavController().currentDestination?.id == R.id.settingsFragment) {
            findNavController().navigate(R.id.action_settingsFragment_to_advancedSettingsFragment, null, getRightToLeftAnimationNavOptions())
        }
    } else {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.settings_nav_container) as NavHostFragment
        navHostFragment.navController.navigate(R.id.action_global_advancedSettingsFragment)
    }
}

internal fun AccountSettingsFragment.navigateToPhoneLinking(args: Bundle?) {
    findNavController().navigate(
        R.id.action_accountSettingsFragment_to_phoneAccountLinkingFragment,
        args,
        getRightToLeftAnimationNavOptions()
    )
}

internal fun PhoneAccountLinkingFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    findNavController().navigate(R.id.action_phoneAccountLinkingFragment_to_phoneAccountValidationFragment, args, getRightToLeftAnimationNavOptions())
}

/* Side menu related */

internal fun SideMenuFragment.navigateToAccountSettings(identity: String) {
    if (!resources.getBoolean(R.bool.isTablet)) {
        // If not a tablet, navigate directly to account settings fragment
        val deepLink = "linphone-android://account-settings/$identity"
        findNavController().navigate(Uri.parse(deepLink), getRightToLeftAnimationNavOptions())
    } else {
        // On tablet, to keep the categories list on left side, navigate to settings fragment first
        val deepLink = "linphone-android://settings/$identity"
        findNavController().navigate(Uri.parse(deepLink))
    }
}

internal fun SideMenuFragment.navigateToSettings() {
    findNavController().navigate(R.id.action_global_settingsFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun SideMenuFragment.navigateToAbout() {
    findNavController().navigate(R.id.action_global_aboutFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun SideMenuFragment.navigateToRecordings() {
    findNavController().navigate(R.id.action_global_recordingsFragment, null, getRightToLeftAnimationNavOptions())
}

/* Assistant related */

internal fun WelcomeFragment.navigateToEmailAccountCreation() {
    findNavController().navigate(R.id.action_welcomeFragment_to_emailAccountCreationFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun WelcomeFragment.navigateToPhoneAccountCreation() {
    findNavController().navigate(R.id.action_welcomeFragment_to_phoneAccountCreationFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun WelcomeFragment.navigateToAccountLogin() {
    findNavController().navigate(R.id.action_welcomeFragment_to_accountLoginFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun WelcomeFragment.navigateToGenericLogin() {
    findNavController().navigate(R.id.action_welcomeFragment_to_genericAccountLoginFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun WelcomeFragment.navigateToRemoteProvisioning() {
    findNavController().navigate(R.id.action_welcomeFragment_to_remoteProvisioningFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun AccountLoginFragment.navigateToEchoCancellerCalibration() {
    findNavController().navigate(R.id.action_accountLoginFragment_to_echoCancellerCalibrationFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun AccountLoginFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    findNavController().navigate(R.id.action_accountLoginFragment_to_phoneAccountValidationFragment, args, getRightToLeftAnimationNavOptions())
}

internal fun GenericAccountLoginFragment.navigateToEchoCancellerCalibration() {
    findNavController().navigate(R.id.action_genericAccountLoginFragment_to_echoCancellerCalibrationFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun RemoteProvisioningFragment.navigateToQrCode() {
    findNavController().navigate(R.id.action_remoteProvisioningFragment_to_qrCodeFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun RemoteProvisioningFragment.navigateToEchoCancellerCalibration() {
    findNavController().navigate(R.id.action_remoteProvisioningFragment_to_echoCancellerCalibrationFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun EmailAccountCreationFragment.navigateToEmailAccountValidation() {
    findNavController().navigate(R.id.action_emailAccountCreationFragment_to_emailAccountValidationFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun EmailAccountValidationFragment.navigateToAccountLinking(args: Bundle?) {
    findNavController().navigate(R.id.action_emailAccountValidationFragment_to_phoneAccountLinkingFragment, args, getRightToLeftAnimationNavOptions())
}

internal fun PhoneAccountCreationFragment.navigateToPhoneAccountValidation(args: Bundle?) {
    findNavController().navigate(R.id.action_phoneAccountCreationFragment_to_phoneAccountValidationFragment, args, getRightToLeftAnimationNavOptions())
}

internal fun PhoneAccountValidationFragment.navigateToAccountSettings(args: Bundle?) {
    findNavController().navigate(R.id.action_phoneAccountValidationFragment_to_accountSettingsFragment, args, getLeftToRightAnimationNavOptions())
}

internal fun PhoneAccountValidationFragment.navigateToEchoCancellerCalibration() {
    findNavController().navigate(R.id.action_phoneAccountValidationFragment_to_echoCancellerCalibrationFragment, null, getRightToLeftAnimationNavOptions())
}

internal fun PhoneAccountLinkingFragment.navigateToEchoCancellerCalibration() {
    findNavController().navigate(R.id.action_phoneAccountLinkingFragment_to_echoCancellerCalibrationFragment, null, getRightToLeftAnimationNavOptions())
}
