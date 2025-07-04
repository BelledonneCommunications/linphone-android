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
package org.linphone.core

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import org.linphone.BuildConfig
import java.io.File
import java.io.FileOutputStream
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contacts.ContactLoader.Companion.LINPHONE_ADDRESS_BOOK_FRIEND_LIST

class CorePreferences
    @UiThread
    constructor(private val context: Context) {
    companion object {
        private const val TAG = "[Preferences]"

        const val CONFIG_FILE_NAME = ".linphonerc"
    }

    private var _config: Config? = null

    @get:WorkerThread @set:WorkerThread
    var config: Config
        get() = _config ?: coreContext.core.config
        set(value) {
            _config = value
        }

    @get:WorkerThread @set:WorkerThread
    var printLogsInLogcat: Boolean
        get() = config.getBool("app", "debug", org.linphone.BuildConfig.DEBUG)
        set(value) {
            config.setBool("app", "debug", value)
        }

    @get:WorkerThread @set:WorkerThread
    var sendLogsToCrashlytics: Boolean
        get() = config.getBool("app", "send_logs_to_crashlytics", BuildConfig.CRASHLYTICS_ENABLED)
        set(value) {
            config.setBool("app", "send_logs_to_crashlytics", value)
        }

    @get:WorkerThread @set:WorkerThread
    var firstLaunch: Boolean
        get() = config.getBool("app", "first_6.0_launch", true)
        set(value) {
            config.setBool("app", "first_6.0_launch", value)
        }

    @get:WorkerThread @set:WorkerThread
    var linphoneConfigurationVersion: Int
        get() = config.getInt("app", "config_version", 52005)
        set(value) {
            config.setInt("app", "config_version", value)
        }

    @get:WorkerThread @set:WorkerThread
    var autoStart: Boolean
        get() = config.getBool("app", "auto_start", true)
        set(value) {
            config.setBool("app", "auto_start", value)
        }

    @get:WorkerThread @set:WorkerThread
    var checkForUpdateServerUrl: String
        get() = config.getString("misc", "version_check_url_root", "").orEmpty()
        set(value) {
            config.setString("misc", "version_check_url_root", value)
        }

    @get:WorkerThread @set:WorkerThread
    var conditionsAndPrivacyPolicyAccepted: Boolean
        get() = config.getBool("app", "read_and_agree_terms_and_privacy", false)
        set(value) {
            config.setBool("app", "read_and_agree_terms_and_privacy", value)
        }

    @get:WorkerThread @set:WorkerThread
    var publishPresence: Boolean
        get() = config.getBool("app", "publish_presence", true)
        set(value) {
            config.setBool("app", "publish_presence", value)
        }

    @get:WorkerThread @set:WorkerThread
    var keepServiceAlive: Boolean
        get() = config.getBool("app", "keep_service_alive", false)
        set(value) {
            config.setBool("app", "keep_service_alive", value)
        }

    @get:WorkerThread @set:WorkerThread
    var deviceName: String
        get() = config.getString("app", "device", "").orEmpty().trim()
        set(value) {
            config.setString("app", "device", value.trim())
        }

    @get:WorkerThread @set:WorkerThread
    var showDeveloperSettings: Boolean
        get() = config.getBool("ui", "show_developer_settings", false)
        set(value) {
            config.setBool("ui", "show_developer_settings", value)
        }

    // Call settings

    // This won't be done if bluetooth or wired headset is used
    @get:WorkerThread @set:WorkerThread
    var routeAudioToSpeakerWhenVideoIsEnabled: Boolean
        get() = config.getBool("app", "route_audio_to_speaker_when_video_enabled", true)
        set(value) {
            config.setBool("app", "route_audio_to_speaker_when_video_enabled", value)
        }

    @get:WorkerThread @set:WorkerThread
    var callRecordingUseSmffFormat: Boolean
        get() = config.getBool("app", "use_smff_for_call_recording", false)
        set(value) {
            config.setBool("app", "use_smff_for_call_recording", value)
        }

    @get:WorkerThread @set:WorkerThread
    var automaticallyStartCallRecording: Boolean
        get() = config.getBool("app", "auto_start_call_record", false)
        set(value) {
            config.setBool("app", "auto_start_call_record", value)
        }

    @get:WorkerThread @set:WorkerThread
    var showDialogWhenCallingDeviceUuidDirectly: Boolean
        get() = config.getBool("app", "show_confirmation_dialog_zrtp_trust_call", true)
        set(value) {
            config.setBool("app", "show_confirmation_dialog_zrtp_trust_call", value)
        }

    @get:WorkerThread @set:WorkerThread
    var acceptEarlyMedia: Boolean
        get() = config.getBool("sip", "incoming_calls_early_media", false)
        set(value) {
            config.setBool("sip", "incoming_calls_early_media", value)
        }

    @get:WorkerThread @set:WorkerThread
    var allowOutgoingEarlyMedia: Boolean
        get() = config.getBool("misc", "real_early_media", false)
        set(value) {
            config.setBool("misc", "real_early_media", value)
        }

    @get:WorkerThread @set:WorkerThread
    var autoAnswerEnabled: Boolean
        get() = config.getBool("app", "auto_answer", false)
        set(value) {
            config.setBool("app", "auto_answer", value)
        }

    @get:WorkerThread @set:WorkerThread
    var autoAnswerDelay: Int
        get() = config.getInt("app", "auto_answer_delay", 0)
        set(value) {
            config.setInt("app", "auto_answer_delay", value)
        }

    // Conversation related

    @get:WorkerThread @set:WorkerThread
    var markConversationAsReadWhenDismissingMessageNotification: Boolean
        get() = config.getBool("app", "mark_as_read_notif_dismissal", false)
        set(value) {
            config.setBool("app", "mark_as_read_notif_dismissal", value)
        }

    var makePublicMediaFilesDownloaded: Boolean
        // Keep old name for backward compatibility
        get() = config.getBool("app", "make_downloaded_images_public_in_gallery", false)
        set(value) {
            config.setBool("app", "make_downloaded_images_public_in_gallery", value)
        }

    // Conference related

    @get:WorkerThread @set:WorkerThread
    var createEndToEndEncryptedMeetingsAndGroupCalls: Boolean
        get() = config.getBool("app", "create_e2e_encrypted_conferences", false)
        set(value) {
            config.setBool("app", "create_e2e_encrypted_conferences", value)
        }

    // Contacts related

    @get:WorkerThread @set:WorkerThread
    var contactsFilter: String
        get() = config.getString("ui", "contacts_filter", "")!! // Default value must be empty!
        set(value) {
            config.setString("ui", "contacts_filter", value)
        }

    @get:WorkerThread @set:WorkerThread
    var showFavoriteContacts: Boolean
        get() = config.getBool("ui", "show_favorites_contacts", true)
        set(value) {
            config.setBool("ui", "show_favorites_contacts", value)
        }

    @get:WorkerThread @set:WorkerThread
    var friendListInWhichStoreNewlyCreatedFriends: String
        get() = config.getString(
            "app",
            "friend_list_to_store_newly_created_contacts",
            LINPHONE_ADDRESS_BOOK_FRIEND_LIST
        )!!
        set(value) {
            config.setString("app", "friend_list_to_store_newly_created_contacts", value)
        }

    // Voice recordings related

    @get:WorkerThread @set:WorkerThread
    var voiceRecordingMaxDuration: Int
        get() = config.getInt("app", "voice_recording_max_duration", 600000) // in ms
        set(value) = config.setInt("app", "voice_recording_max_duration", value)

    // User interface related

    // -1 means auto, 0 no, 1 yes
    @get:WorkerThread @set:WorkerThread
    var darkMode: Int
        get() {
            if (!darkModeAllowed) return 0
            return config.getInt("app", "dark_mode", -1)
        }
        set(value) {
            config.setInt("app", "dark_mode", value)
        }

    // Allows to make screenshots
    @get:WorkerThread @set:WorkerThread
    var enableSecureMode: Boolean
        get() = config.getBool("ui", "enable_secure_mode", true)
        set(value) {
            config.setBool("ui", "enable_secure_mode", value)
        }

    @get:WorkerThread @set:WorkerThread
    var automaticallyShowDialpad: Boolean
        get() = config.getBool("ui", "automatically_show_dialpad", false)
        set(value) {
            config.setBool("ui", "automatically_show_dialpad", value)
        }

    @get:WorkerThread @set:WorkerThread
    var themeMainColor: String
        get() = config.getString("ui", "theme_main_color", "orange")!!
        set(value) {
            config.setString("ui", "theme_main_color", value)
        }

    // Customization options

    @get:WorkerThread
    val defaultDomain: String
        get() = config.getString("app", "default_domain", "sip.linphone.org")!!

    val pushNotificationCompatibleDomains: Array<String>
        get() = config.getStringList("app", "push_notification_domains", arrayOf("sip.linphone.org"))

    @get:WorkerThread
    val darkModeAllowed: Boolean
        get() = config.getBool("ui", "dark_mode_allowed", true)

    @get:WorkerThread
    val changeMainColorAllowed: Boolean
        get() = config.getBool("ui", "change_main_color_allowed", false)

    @get:WorkerThread
    val onlyDisplaySipUriUsername: Boolean
        get() = config.getBool("ui", "only_display_sip_uri_username", false)

    @get:WorkerThread
    val disableChat: Boolean
        get() = config.getBool("ui", "disable_chat_feature", false)

    @get:WorkerThread
    val disableMeetings: Boolean
        get() = config.getBool("ui", "disable_meetings_feature", false)

    @get:WorkerThread
    val disableBroadcasts: Boolean
        get() = config.getBool("ui", "disable_broadcast_feature", true) // TODO FIXME: not implemented yet

    @get:WorkerThread
    val disableCallRecordings: Boolean
        get() = config.getBool("ui", "disable_call_recordings_feature", false)

    @get:WorkerThread
    val maxAccountsCount: Int
        get() = config.getInt("ui", "max_account", 0) // 0 means no max

    @get:WorkerThread
    val hidePhoneNumbers: Boolean
        get() = config.getBool("ui", "hide_phone_numbers", false)

    @get:WorkerThread
    val hideSettings: Boolean
        get() = config.getBool("ui", "hide_settings", false)

    @get:WorkerThread
    val hideAccountSettings: Boolean
        get() = config.getBool("ui", "hide_account_settings", false)

    @get:WorkerThread
    val hideAdvancedSettings: Boolean
        get() = config.getBool("ui", "hide_advanced_settings", false)

    @get:WorkerThread
    val hideAssistantCreateAccount: Boolean
        get() = config.getBool("ui", "assistant_hide_create_account", false)

    @get:WorkerThread
    val hideAssistantScanQrCode: Boolean
        get() = config.getBool("ui", "assistant_disable_qr_code", false)

    @get:WorkerThread
    val hideAssistantThirdPartySipAccount: Boolean
        get() = config.getBool("ui", "assistant_hide_third_party_account", false)

    @get:WorkerThread
    val magicSearchResultsLimit: Int
        get() = config.getInt("ui", "max_number_of_magic_search_results", 300)

    @get:WorkerThread
    val singleSignOnClientId: String
        get() = config.getString("app", "oidc_client_id", "linphone")!!

    @get:WorkerThread
    val useUsernameAsSingleSignOnLoginHint: Boolean
        get() = config.getBool("ui", "use_username_as_sso_login_hint", false)

    @get:WorkerThread
    val thirdPartySipAccountDefaultTransport: String
        get() = config.getString("ui", "assistant_third_party_sip_account_transport", "tls")!!

    @get:WorkerThread
    val thirdPartySipAccountDefaultDomain: String
        get() = config.getString("ui", "assistant_third_party_sip_account_domain", "")!!

    @get:WorkerThread
    val assistantDirectlyGoToThirdPartySipAccountLogin: Boolean
        get() = config.getBool(
            "ui",
            "assistant_go_directly_to_third_party_sip_account_login",
            false
        )

    @get:WorkerThread
    val fetchContactsFromDefaultDirectory: Boolean
        get() = config.getBool("app", "fetch_contacts_from_default_directory", true)

    @get:WorkerThread
    val showLettersOnDialpad: Boolean
        get() = config.getBool("ui", "show_letters_on_dialpad", true)

    // Paths

    @get:AnyThread
    val configPath: String
        get() = context.filesDir.absolutePath + "/" + CONFIG_FILE_NAME

    @get:AnyThread
    val factoryConfigPath: String
        get() = context.filesDir.absolutePath + "/linphonerc"

    @get:AnyThread
    val linphoneDefaultValuesPath: String
        get() = context.filesDir.absolutePath + "/assistant_linphone_default_values"

    @get:AnyThread
    val thirdPartyDefaultValuesPath: String
        get() = context.filesDir.absolutePath + "/assistant_third_party_default_values"

    @get:AnyThread
    val vfsCachePath: String
        get() = context.cacheDir.absolutePath + "/evfs/"

    @get:AnyThread
    val ssoCacheFile: String
        get() = context.filesDir.absolutePath + "/auth_state.json"

    @get:AnyThread
    val messageReceivedInVisibleConversationNotificationSound: String
        get() = context.filesDir.absolutePath + "/share/sounds/linphone/incoming_chat.wav"

    @UiThread
    fun copyAssetsFromPackage() {
        copy("linphonerc_default", configPath)
        copy("linphonerc_factory", factoryConfigPath, true)
        copy("assistant_linphone_default_values", linphoneDefaultValuesPath, true)
        copy("assistant_third_party_default_values", thirdPartyDefaultValuesPath, true)
    }

    @AnyThread
    fun clearPreviousGrammars() {
        val cpimGrammar = File("${context.filesDir.absolutePath}/share/belr/grammars/cpim_grammar")
        if (cpimGrammar.exists()) {
            cpimGrammar.delete()
        }
        val icsGrammar = File("${context.filesDir.absolutePath}/share/belr/grammars/ics_grammar")
        if (icsGrammar.exists()) {
            icsGrammar.delete()
        }
        val identityGrammar = File(
            "${context.filesDir.absolutePath}/share/belr/grammars/identity_grammar"
        )
        if (identityGrammar.exists()) {
            identityGrammar.delete()
        }
        val mwiGrammar = File("${context.filesDir.absolutePath}/share/belr/grammars/mwi_grammar")
        if (mwiGrammar.exists()) {
            mwiGrammar.delete()
        }
        val sdpGrammar = File("${context.filesDir.absolutePath}/share/belr/grammars/sdp_grammar")
        if (sdpGrammar.exists()) {
            sdpGrammar.delete()
        }
        val sipGrammar = File("${context.filesDir.absolutePath}/share/belr/grammars/sip_grammar")
        if (sipGrammar.exists()) {
            sipGrammar.delete()
        }
        val vcard3Grammar = File(
            "${context.filesDir.absolutePath}/share/belr/grammars/vcard3_grammar"
        )
        if (vcard3Grammar.exists()) {
            vcard3Grammar.delete()
        }
        val vcardGrammar = File(
            "${context.filesDir.absolutePath}/share/belr/grammars/vcard_grammar"
        )
        if (vcardGrammar.exists()) {
            vcardGrammar.delete()
        }
    }

    @AnyThread
    private fun copy(from: String, to: String, overrideIfExists: Boolean = false) {
        val outFile = File(to)
        if (outFile.exists()) {
            if (!overrideIfExists) {
                android.util.Log.i(
                    context.getString(org.linphone.R.string.app_name),
                    "$TAG File $to already exists"
                )
                return
            }
        }
        android.util.Log.i(
            context.getString(org.linphone.R.string.app_name),
            "$TAG Overriding $to by $from asset"
        )

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
