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
        get() = config.getBool("app", "debug", BuildConfig.DEBUG)
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

    var makePublicDownloadedImages: Boolean
        get() = config.getBool("app", "make_downloaded_images_public_in_gallery", true)
        set(value) {
            config.setBool("app", "make_downloaded_images_public_in_gallery", value)
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

    var lastUpdateAvailableCheckTimestamp: Int
        get() = config.getInt("app", "version_check_url_last_timestamp", 0)
        set(value) {
            config.setInt("app", "version_check_url_last_timestamp", value)
        }

    /* Read only application settings previously in non_localizable_custom */

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

    val checkIfUpdateAvailableUrl: String?
        get() = config.getString("misc", "version_check_url_root", null)

    val checkUpdateAvailableInterval: Int
        get() = config.getInt("app", "version_check_interval", 86400000)

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
