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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CallTranslatorSessionClientTest {
    @Test
    fun `createSession posts callee payload and parses credentials`() {
        val connection = FakeHttpURLConnection(
            httpResponseCode = 201,
            responseBody = """{"session_id":"session-1","join_token":"token-1"}"""
        )
        val client = CallTranslatorSessionClient(
            endpointUrl = "https://example.test/sessions",
            openConnection = { connection }
        )

        val session = client.createSession("700", "sip:700@example.test")

        assertEquals("session-1", session.sessionId)
        assertEquals("token-1", session.joinToken)
        assertEquals("POST", connection.requestMethod)
        assertEquals("application/json", connection.capturedRequestProperties["Accept"])
        assertEquals("application/json; charset=utf-8", connection.capturedRequestProperties["Content-Type"])
        val requestJson = JSONObject(connection.requestBody.toString(Charsets.UTF_8.name()))
        assertEquals("700", requestJson.getString("callee"))
        assertEquals("sip:700@example.test", requestJson.getString("callee_uri"))
        assertEquals(true, connection.disconnected)
    }

    @Test
    fun `parseSession rejects missing credentials`() {
        val client = CallTranslatorSessionClient()

        assertThrows(CallTranslatorSessionClient.SessionRequestException::class.java) {
            client.parseSession("""{"session_id":"session-1"}""")
        }
    }

    @Test
    fun `createSession rejects non success response`() {
        val connection = FakeHttpURLConnection(
            httpResponseCode = 500,
            responseBody = """{"error":"boom"}"""
        )
        val client = CallTranslatorSessionClient(
            endpointUrl = "https://example.test/sessions",
            openConnection = { connection }
        )

        assertThrows(CallTranslatorSessionClient.SessionRequestException::class.java) {
            client.createSession("700", "sip:700@example.test")
        }
        assertEquals(true, connection.disconnected)
    }

    private class FakeHttpURLConnection(
        url: URL = URL("https://example.test/sessions"),
        private val httpResponseCode: Int,
        private val responseBody: String
    ) : HttpURLConnection(url) {
        val requestBody = ByteArrayOutputStream()
        val capturedRequestProperties = mutableMapOf<String, String>()
        var disconnected = false

        override fun connect() {
        }

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = requestBody

        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))

        override fun getResponseCode(): Int = httpResponseCode

        override fun setRequestProperty(key: String, value: String) {
            capturedRequestProperties[key] = value
        }
    }
}
