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
package org.linphone.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log

class CorePreferences constructor(private val context: Context) {
    private var _config: Config? = null
    var config: Config
        get() = _config ?: coreContext.core.config
        set(value) {
            _config = value
        }

    /* App settings */

    var debugLogs: Boolean
        get() = config.getBool("app", "debug", org.linphone.BuildConfig.DEBUG)
        set(value) {
            config.setBool("app", "debug", value)
        }

    var autoStart: Boolean
        get() = config.getBool("app", "auto_start", true)
        set(value) {
            config.setBool("app", "auto_start", value)
        }

    var keepServiceAlive: Boolean
        get() = config.getBool("app", "keep_service_alive", false)
        set(value) {
            config.setBool("app", "keep_service_alive", value)
        }

    /* UI */

    var forcePortrait: Boolean
        get() = config.getBool("app", "force_portrait_orientation", false)
        set(value) {
            config.setBool("app", "force_portrait_orientation", value)
        }

    var replaceSipUriByUsername: Boolean
        get() = config.getBool("app", "replace_sip_uri_by_username", false)
        set(value) {
            config.setBool("app", "replace_sip_uri_by_username", value)
        }

    var enableAnimations: Boolean
        get() = config.getBool("app", "enable_animations", false)
        set(value) {
            config.setBool("app", "enable_animations", value)
        }

    /** -1 means auto, 0 no, 1 yes */
    var darkMode: Int
        get() {
            if (!darkModeAllowed) return 0
            return config.getInt("app", "dark_mode", -1)
        }
        set(value) {
            config.setInt("app", "dark_mode", value)
        }

    /* Audio */

    val echoCancellerCalibration: Int
        get() = config.getInt("sound", "ec_delay", -1)

    /* Video */

    var videoPreview: Boolean
        get() = config.getBool("app", "video_preview", false)
        set(value) = config.setBool("app", "video_preview", value)

    val hideStaticImageCamera: Boolean
        get() = config.getBool("app", "hide_static_image_camera", true)

    /* Chat */

    var makePublicMediaFilesDownloaded: Boolean
        // Keep old name for backward compatibility
        get() = config.getBool("app", "make_downloaded_images_public_in_gallery", true)
        set(value) {
            config.setBool("app", "make_downloaded_images_public_in_gallery", value)
        }

    var hideChatMessageContentInNotification: Boolean
        get() = config.getBool("app", "hide_chat_message_content_in_notification", false)
        set(value) {
            config.setBool("app", "hide_chat_message_content_in_notification", value)
        }

    var hideEmptyRooms: Boolean
        get() = config.getBool("app", "hide_empty_chat_rooms", true)
        set(value) {
            config.setBool("app", "hide_empty_chat_rooms", value)
        }

    var hideRoomsFromRemovedProxies: Boolean
        get() = config.getBool("app", "hide_chat_rooms_from_removed_proxies", true)
        set(value) {
            config.setBool("app", "hide_chat_rooms_from_removed_proxies", value)
        }

    var deviceName: String
        get() = config.getString("app", "device_name", Compatibility.getDeviceName(context))!!
        set(value) = config.setString("app", "device_name", value)

    var chatRoomShortcuts: Boolean
        get() = config.getBool("app", "chat_room_shortcuts", true)
        set(value) {
            config.setBool("app", "chat_room_shortcuts", value)
        }

    /* Contacts */

    var storePresenceInNativeContact: Boolean
        get() = config.getBool("app", "store_presence_in_native_contact", false)
        set(value) {
            config.setBool("app", "store_presence_in_native_contact", value)
        }

    var showNewContactAccountDialog: Boolean
        get() = config.getBool("app", "show_new_contact_account_dialog", true)
        set(value) {
            config.setBool("app", "show_new_contact_account_dialog", value)
        }

    var displayOrganization: Boolean
        get() = config.getBool("app", "display_contact_organization", contactOrganizationVisible)
        set(value) {
            config.setBool("app", "display_contact_organization", value)
        }

    var contactsShortcuts: Boolean
        get() = config.getBool("app", "contact_shortcuts", false)
        set(value) {
            config.setBool("app", "contact_shortcuts", value)
        }

    /* Call */

    var vibrateWhileIncomingCall: Boolean
        get() = config.getBool("app", "incoming_call_vibration", true)
        set(value) {
            config.setBool("app", "incoming_call_vibration", value)
        }

    var acceptEarlyMedia: Boolean
        get() = config.getBool("sip", "incoming_calls_early_media", false)
        set(value) {
            config.setBool("sip", "incoming_calls_early_media", value)
        }

    var autoAnswerEnabled: Boolean
        get() = config.getBool("app", "auto_answer", false)
        set(value) {
            config.setBool("app", "auto_answer", value)
        }

    var autoAnswerDelay: Int
        get() = config.getInt("app", "auto_answer_delay", 0)
        set(value) {
            config.setInt("app", "auto_answer_delay", value)
        }

    var showCallOverlay: Boolean
        get() = config.getBool("app", "call_overlay", false)
        set(value) {
            config.setBool("app", "call_overlay", value)
        }

    var callRightAway: Boolean
        get() = config.getBool("app", "call_right_away", false)
        set(value) {
            config.setBool("app", "call_right_away", value)
        }

    /* Assistant */

    var firstStart: Boolean
        get() = config.getBool("app", "first_start", true)
        set(value) {
            config.setBool("app", "first_start", value)
        }

    var xmlRpcServerUrl: String?
        get() = config.getString("assistant", "xmlrpc_url", null)
        set(value) {
            config.setString("assistant", "xmlrpc_url", value)
        }

    /* Dialog related */

    var limeSecurityPopupEnabled: Boolean
        get() = config.getBool("app", "lime_security_popup_enabled", true)
        set(value) {
            config.setBool("app", "lime_security_popup_enabled", value)
        }

    /* Other */

    var voiceMailUri: String?
        get() = config.getString("app", "voice_mail", null)
        set(value) {
            config.setString("app", "voice_mail", value)
        }

    var redirectDeclinedCallToVoiceMail: Boolean
        get() = config.getBool("app", "redirect_declined_call_to_voice_mail", true)
        set(value) {
            config.setBool("app", "redirect_declined_call_to_voice_mail", value)
        }

    var lastUpdateAvailableCheckTimestamp: Int
        get() = config.getInt("app", "version_check_url_last_timestamp", 0)
        set(value) {
            config.setInt("app", "version_check_url_last_timestamp", value)
        }

    var useLegacyPushNotificationFormat: Boolean
        get() = config.getBool("net", "use_legacy_push_notification_params", false)
        set(value) {
            config.setBool("net", "use_legacy_push_notification_params", value)
        }

    /* Read only application settings, some were previously in non_localizable_custom */

    val defaultDomain: String
        get() = config.getString("app", "default_domain", "sip.linphone.org")!!

    val debugPopupCode: String
        get() = config.getString("app", "debug_popup_magic", "#1234#")!!

    val fetchContactsFromDefaultDirectory: Boolean
        get() = config.getBool("app", "fetch_contacts_from_default_directory", true)

    val hideContactsWithoutPresence: Boolean
        get() = config.getBool("app", "hide_contacts_without_presence", false)

    val rlsUri: String
        get() = config.getString("app", "rls_uri", "sip:rls@sip.linphone.org")!!

    val conferenceServerUri: String
        get() = config.getString("app", "default_conference_factory_uri", "sip:conference-factory@sip.linphone.org")!!

    val limeX3dhServerUrl: String
        get() = config.getString("app", "default_lime_x3dh_server_url", "https://lime.linphone.org/lime-server/lime-server.php")!!

    val allowMultipleFilesAndTextInSameMessage: Boolean
        get() = config.getBool("app", "allow_multiple_files_and_text_in_same_message", true)

    val contactOrganizationVisible: Boolean
        get() = config.getBool("app", "display_contact_organization", true)

    val showBorderOnContactAvatar: Boolean
        get() = config.getBool("app", "show_border_on_contact_avatar", false)

    val showBorderOnBigContactAvatar: Boolean
        get() = config.getBool("app", "show_border_on_big_contact_avatar", true)

    val checkIfUpdateAvailableUrl: String?
        get() = config.getString("misc", "version_check_url_root", null)

    val checkUpdateAvailableInterval: Int
        get() = config.getInt("app", "version_check_interval", 86400000)

    val showIncomingChatMessagesDeliveryStatus: Boolean
        get() = config.getBool("app", "show_incoming_messages_delivery_status", false)

    // If enabled, this will cause the video to "freeze" on your correspondent screen
    // as you won't send video packets anymore
    val hideCameraPreviewInPipMode: Boolean
        get() = config.getBool("app", "hide_camera_preview_in_pip_mode", false)

    val dtmfKeypadVibration: Boolean
        get() = config.getBool("app", "dtmf_keypad_vibraton", false)

    /* Assistant */

    val showCreateAccount: Boolean
        get() = config.getBool("app", "assistant_create_account", true)

    val showLinphoneLogin: Boolean
        get() = config.getBool("app", "assistant_linphone_login", true)

    val showGenericLogin: Boolean
        get() = config.getBool("app", "assistant_generic_login", true)

    val showRemoteProvisioning: Boolean
        get() = config.getBool("app", "assistant_remote_provisioning", true)

    /* Side Menu */

    var defaultAccountAvatarPath: String?
        get() = config.getString("app", "default_avatar_path", null)
        set(value) {
            config.setString("app", "default_avatar_path", value)
        }

    val showAccountsInSideMenu: Boolean
        get() = config.getBool("app", "side_menu_accounts", true)

    val showAssistantInSideMenu: Boolean
        get() = config.getBool("app", "side_menu_assistant", true)

    val showSettingsInSideMenu: Boolean
        get() = config.getBool("app", "side_menu_settings", true)

    val showRecordingsInSideMenu: Boolean
        get() = config.getBool("app", "side_menu_recordings", true)

    val showAboutInSideMenu: Boolean
        get() = config.getBool("app", "side_menu_about", true)

    val showQuitInSideMenu: Boolean
        get() = config.getBool("app", "side_menu_quit", true)

    /* Settings */

    val showAccountSettings: Boolean
        get() = config.getBool("app", "settings_accounts", true)

    val showTunnelSettings: Boolean
        get() = config.getBool("app", "settings_tunnel", true)

    val showAudioSettings: Boolean
        get() = config.getBool("app", "settings_audio", true)

    val showVideoSettings: Boolean
        get() = config.getBool("app", "settings_video", true)

    val showCallSettings: Boolean
        get() = config.getBool("app", "settings_call", true)

    val showChatSettings: Boolean
        get() = config.getBool("app", "settings_chat", true)

    val showNetworkSettings: Boolean
        get() = config.getBool("app", "settings_network", true)

    val showContactsSettings: Boolean
        get() = config.getBool("app", "settings_contacts", true)

    val showAdvancedSettings: Boolean
        get() = config.getBool("app", "settings_advanced", true)

    /* Other stuff */

    private val darkModeAllowed: Boolean
        get() = config.getBool("app", "dark_mode_allowed", true)

    /* Assets stuff */

    val configPath: String
        get() = context.filesDir.absolutePath + "/.linphonerc"

    val factoryConfigPath: String
        get() = context.filesDir.absolutePath + "/linphonerc"

    val linphoneDefaultValuesPath: String
        get() = context.filesDir.absolutePath + "/assistant_linphone_default_values"

    val defaultValuesPath: String
        get() = context.filesDir.absolutePath + "/assistant_default_values"

    val ringtonePath: String
        get() = context.filesDir.absolutePath + "/share/sounds/linphone/rings/notes_of_the_optimistic.mkv"

    val userCertificatesPath: String
        get() = context.filesDir.absolutePath + "/user-certs"

    val zrtpSecretsPath: String
        get() = context.filesDir.absolutePath + "/zrtp_secrets"

    val callHistoryDatabasePath: String
        get() = context.filesDir.absolutePath + "/linphone-log-history.db"

    fun copyAssetsFromPackage() {
        copy("linphonerc_default", configPath)
        copy("linphonerc_factory", factoryConfigPath, true)
        copy("assistant_linphone_default_values", linphoneDefaultValuesPath, true)
        copy("assistant_default_values", defaultValuesPath, true)
    }

    fun getString(resource: Int): String {
        return context.getString(resource)
    }

    private fun copy(from: String, to: String, overrideIfExists: Boolean = false) {
        val outFile = File(to)
        if (outFile.exists()) {
            if (!overrideIfExists) {
                Log.i("[Preferences] File $to already exists")
                return
            }
        }
        Log.i("[Preferences] Overriding $to by $from asset")

        val outStream = FileOutputStream(outFile)
        val inFile = context.assets.open(from)
        val buffer = ByteArray(1024)
        var length: Int = inFile.read(buffer)

        while (length > 0) {
            outStream.write(buffer, 0, length)
            length = inFile.read(buffer)
        }

        inFile.close()
        outStream.flush()
        outStream.close()
    }
}
