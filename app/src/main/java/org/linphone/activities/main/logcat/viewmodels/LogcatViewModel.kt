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
package org.linphone.activities.main.logcat.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.tools.Log
import org.linphone.utils.LogsUploadViewModel

class LogcatViewModel : LogsUploadViewModel() {
    val logcat = MutableLiveData<String>()

    init {
        viewModelScope.launch { fetchLogcat() }
    }

    private suspend fun fetchLogcat() = withContext(Dispatchers.IO) {
        val process = Runtime.getRuntime().exec("logcat -d")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val logs = StringBuilder()

        var line: String? = null
        do {
            try {
                line = reader.readLine()
                line ?: break

                val split = line.split(" ")
                if (split.size > 7) {
                    val date = split[0]
                    val time = split[1]
                    val level = split[4]
                    var log = StringBuilder()
                    log.append(line.subSequence(line.indexOf(split[7]), line.length))
                    log.append("\r\n")
                    logs.append(log)
                } else {
                    logs.append("$line \r\n")
                }
            } catch (ioe: IOException) {
                Log.e("[Logcat] Read exception: $ioe")
            }
        } while (line != null)

        logcat.postValue(logs.toString())
    }
}
