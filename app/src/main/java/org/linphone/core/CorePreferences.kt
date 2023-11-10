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
import java.io.File
import java.io.FileOutputStream
import org.linphone.LinphoneApplication.Companion.coreContext

class CorePreferences @UiThread constructor(private val context: Context) {
    companion object {
        private const val TAG = "[Preferences]"
    }

    private var _config: Config? = null

    @get:WorkerThread @set:WorkerThread
    var config: Config
        get() = _config ?: coreContext.core.config
        set(value) {
            _config = value
        }

    @get:WorkerThread @set:WorkerThread
    var debugLogs: Boolean
        get() = config.getBool("app", "debug", org.linphone.BuildConfig.DEBUG)
        set(value) {
            config.setBool("app", "debug", value)
        }

    @get:WorkerThread @set:WorkerThread
    var defaultFragment: Int
        get() = config.getInt("app", "default_page", -1)
        set(value) {
            config.setInt("app", "default_page", value)
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

    // Calls settings

    @get:WorkerThread @set:WorkerThread
    var routeAudioToBluetoothIfAvailable: Boolean
        get() = config.getBool("app", "route_audio_to_bluetooth_if_available", true)
        set(value) {
            config.setBool("app", "route_audio_to_bluetooth_if_available", value)
        }

    // This won't be done if bluetooth or wired headset is used
    @get:WorkerThread @set:WorkerThread
    var routeAudioToSpeakerWhenVideoIsEnabled: Boolean
        get() = config.getBool("app", "route_audio_to_speaker_when_video_enabled", true)
        set(value) {
            config.setBool("app", "route_audio_to_speaker_when_video_enabled", value)
        }

    @get:WorkerThread @set:WorkerThread
    var automaticallyStartCallRecording: Boolean
        get() = config.getBool("app", "auto_start_call_record", false)
        set(value) {
            config.setBool("app", "auto_start_call_record", value)
        }

    /* Voice Recordings */

    var voiceRecordingMaxDuration: Int
        get() = config.getInt("app", "voice_recording_max_duration", 600000) // in ms
        set(value) = config.setInt("app", "voice_recording_max_duration", value)

    /** -1 means auto, 0 no, 1 yes */
    @get:WorkerThread @set:WorkerThread
    var darkMode: Int
        get() {
            if (!darkModeAllowed) return 0
            return config.getInt("app", "dark_mode", -1)
        }
        set(value) {
            config.setInt("app", "dark_mode", value)
        }

    @get:WorkerThread
    private val darkModeAllowed: Boolean
        get() = config.getBool("app", "dark_mode_allowed", true)

    // Will disable chat feature completely
    @get:WorkerThread
    val disableChat: Boolean
        get() = config.getBool("app", "disable_chat_feature", false) // TODO FIXME: set it to true for first "release" without chat

    // Will disable meetings feature completely
    @get:WorkerThread
    val disableMeetings: Boolean
        get() = config.getBool("app", "disable_meetings_feature", false) // TODO FIXME: set it to true for first "release" without meetings

    @get:WorkerThread
    val defaultDomain: String
        get() = config.getString("app", "default_domain", "sip.linphone.org")!!

    @get:AnyThread
    val configPath: String
        get() = context.filesDir.absolutePath + "/.linphonerc"

    @get:AnyThread
    val factoryConfigPath: String
        get() = context.filesDir.absolutePath + "/linphonerc"

    @get:AnyThread
    val friendsDatabasePath: String
        get() = context.filesDir.absolutePath + "/friends.db"

    @get:AnyThread
    val linphoneDefaultValuesPath: String
        get() = context.filesDir.absolutePath + "/assistant_linphone_default_values"

    @get:AnyThread
    val thirdPartyDefaultValuesPath: String
        get() = context.filesDir.absolutePath + "/assistant_third_party_default_values"

    @get:AnyThread
    val ringtonesPath: String
        get() = context.filesDir.absolutePath + "/share/sounds/linphone/rings/"

    @UiThread
    fun copyAssetsFromPackage() {
        copy("linphonerc_default", configPath)
        copy("linphonerc_factory", factoryConfigPath, true)
        copy("assistant_linphone_default_values", linphoneDefaultValuesPath, true)
        copy("assistant_third_party_default_values", thirdPartyDefaultValuesPath, true)
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
