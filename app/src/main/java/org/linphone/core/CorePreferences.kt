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
import android.content.SharedPreferences
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

    @UiThread
    fun chatRoomMuted(id: String): Boolean {
        val sharedPreferences: SharedPreferences = coreContext.context.getSharedPreferences(
            "notifications",
            Context.MODE_PRIVATE
        )
        return sharedPreferences.getBoolean(id, false)
    }

    @UiThread
    fun muteChatRoom(id: String, mute: Boolean) {
        val sharedPreferences: SharedPreferences = coreContext.context.getSharedPreferences(
            "notifications",
            Context.MODE_PRIVATE
        )
        val editor = sharedPreferences.edit()
        editor.putBoolean(id, mute)
        editor.apply()
    }

    @get:WorkerThread @set:WorkerThread
    var publishPresence: Boolean
        get() = config.getBool("app", "publish_presence", true)
        set(value) {
            config.setBool("app", "publish_presence", value)
        }

    // Will disable chat feature completely
    @get:WorkerThread
    val disableChat: Boolean
        get() = config.getBool("app", "disable_chat_feature", false)

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

    @UiThread
    fun copyAssetsFromPackage() {
        copy("linphonerc_default", configPath)
        copy("linphonerc_factory", factoryConfigPath, true)
        copy("assistant_linphone_default_values", linphoneDefaultValuesPath, true)
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
