/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
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

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class CallTranslatorSessionClient(
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
    private val openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) {
    companion object {
        const val DEFAULT_ENDPOINT_URL = "https://example.invalid/api/call-translator/sessions"

        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
    }

    data class Session(
        val sessionId: String,
        val joinToken: String
    )

    class SessionRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

    @Throws(SessionRequestException::class)
    fun createSession(callee: String, calleeUri: String): Session {
        val connection = try {
            openConnection(URL(endpointUrl))
        } catch (exception: Exception) {
            throw SessionRequestException("Failed to open call translator session endpoint", exception)
        }

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val requestBody = JSONObject()
                .put("callee", callee)
                .put("callee_uri", calleeUri)
                .toString()
                .toByteArray(Charsets.UTF_8)

            connection.setRequestProperty("Content-Length", requestBody.size.toString())
            connection.outputStream.use { output ->
                output.write(requestBody)
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    reader.readText()
                }.orEmpty()
            }

            if (responseCode !in 200..299) {
                throw SessionRequestException(
                    "Call translator session endpoint returned HTTP $responseCode: $responseBody"
                )
            }

            return parseSession(responseBody)
        } catch (exception: SessionRequestException) {
            throw exception
        } catch (exception: Exception) {
            throw SessionRequestException("Failed to request call translator session", exception)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseSession(responseBody: String): Session {
        val json = try {
            JSONObject(responseBody)
        } catch (exception: Exception) {
            throw SessionRequestException("Call translator session response isn't valid JSON", exception)
        }

        val sessionId = json.optString("session_id").trim()
        val joinToken = json.optString("join_token").trim()

        if (sessionId.isEmpty() || joinToken.isEmpty()) {
            throw SessionRequestException("Call translator session response is missing session_id or join_token")
        }

        return Session(sessionId, joinToken)
    }
}
