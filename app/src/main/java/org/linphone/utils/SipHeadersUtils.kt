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
package org.linphone.utils

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.linphone.core.CallParams
import org.linphone.core.tools.Log

/**
 * Utility class for managing SIP X-Headers in call parameters.
 * X-Headers are custom SIP headers that can be added to INVITE requests.
 */
class SipHeadersUtils {
    companion object {
        private const val TAG = "[SIP Headers Utils]"

        /**
         * Data class representing a custom SIP header
         */
        data class CustomHeader(
            val name: String,
            val value: String
        ) {
            fun isValid(): Boolean {
                return name.isNotBlank() && value.isNotBlank()
            }

            fun toHeaderString(): String = "$name: $value"
        }

        /**
         * Common X-Headers for DesktopPBX integration
         */
        object DesktopPBXHeaders {
            const val X_LINPHONE_FEATURES = "X-Linphone-Features"
            const val X_SOURCE_DEVICE = "X-Source-Device"
            const val X_CALL_ROUTING = "X-Call-Routing"
            const val X_SESSION_ID = "X-Session-ID"
            const val X_PRIORITY = "X-Priority"
            const val X_PSTN_GATEWAY = "X-PSTN-Gateway"
            const val X_SIP_PROXY = "X-SIP-Proxy"
            const val X_DIAL_NUMBER = "X-Dial-Number"
            const val X_HDEAR = "X-HDEAR"
        }

        /**
         * Call routing types for PSTN dialing
         */
        object CallRoutingType {
            const val SIP_TO_SIP = "sip-to-sip"
            const val SIP_TO_PSTN = "sip-to-pstn"
            const val PSTN_TO_SIP = "pstn-to-sip"
            const val INTERNAL_TRANSFER = "internal-transfer"
        }

        /**
         * Add a custom X-Header to call parameters
         * @param params CallParams to add the header to
         * @param headerName Name of the custom header (e.g., "X-Custom-Header")
         * @param headerValue Value of the custom header
         */
        @WorkerThread
        fun addCustomHeader(
            params: CallParams,
            headerName: String,
            headerValue: String
        ) {
            if (headerName.isBlank() || headerValue.isBlank()) {
                Log.w("$TAG Header name or value is empty, skipping")
                return
            }

            try {
                Log.i("$TAG Adding custom header [$headerName: $headerValue]")
                params.addCustomHeader(headerName, headerValue)
            } catch (e: Exception) {
                Log.e("$TAG Failed to add custom header [$headerName]: ${e.message}")
            }
        }

        /**
         * Add multiple custom headers to call parameters
         * @param params CallParams to add headers to
         * @param headers List of CustomHeader objects
         */
        @WorkerThread
        fun addCustomHeaders(
            params: CallParams,
            headers: List<CustomHeader>
        ) {
            for (header in headers) {
                if (header.isValid()) {
                    addCustomHeader(params, header.name, header.value)
                } else {
                    Log.w("$TAG Skipping invalid header: ${header.name}")
                }
            }
        }

        /**
         * Add DesktopPBX-specific headers for SIP-to-SIP routing
         * @param params CallParams to add headers to
         * @param sourceDevice Source device identifier
         * @param sessionId Unique session identifier
         */
        @WorkerThread
        fun addDesktopPBXSipToSipHeaders(
            params: CallParams,
            sourceDevice: String? = null,
            sessionId: String? = null
        ) {
            Log.i("$TAG Adding DesktopPBX SIP-to-SIP headers")

            addCustomHeader(
                params,
                DesktopPBXHeaders.X_CALL_ROUTING,
                CallRoutingType.SIP_TO_SIP
            )

            if (sourceDevice != null && sourceDevice.isNotBlank()) {
                addCustomHeader(params, DesktopPBXHeaders.X_SOURCE_DEVICE, sourceDevice)
            }

            if (sessionId != null && sessionId.isNotBlank()) {
                addCustomHeader(params, DesktopPBXHeaders.X_SESSION_ID, sessionId)
            }

            // Add features header indicating SIP capabilities
            addCustomHeader(
                params,
                DesktopPBXHeaders.X_LINPHONE_FEATURES,
                "sip-to-sip,e2e-encryption,video-call,audio-conference"
            )
        }

        /**
         * Add DesktopPBX-specific headers for PSTN dialing
         * @param params CallParams to add headers to
         * @param pstnGateway PSTN gateway address
         * @param routeType Type of PSTN routing
         * @param sessionId Unique session identifier
         */
        @WorkerThread
        fun addDesktopPBXPstnHeaders(
            params: CallParams,
            pstnGateway: String? = null,
            routeType: String = CallRoutingType.SIP_TO_PSTN,
            sessionId: String? = null,
            dialNumber: String? = null
        ) {
            Log.i("$TAG Adding DesktopPBX PSTN headers")

            addCustomHeader(params, DesktopPBXHeaders.X_CALL_ROUTING, routeType)

            if (pstnGateway != null && pstnGateway.isNotBlank()) {
                addCustomHeader(params, DesktopPBXHeaders.X_PSTN_GATEWAY, pstnGateway)
            }

            if (sessionId != null && sessionId.isNotBlank()) {
                addCustomHeader(params, DesktopPBXHeaders.X_SESSION_ID, sessionId)
            }

            if (dialNumber != null && dialNumber.isNotBlank()) {
                addCustomHeader(params, DesktopPBXHeaders.X_DIAL_NUMBER, dialNumber)
                // Legacy/partner header: provide dialed number under X-HDEAR as well
                addCustomHeader(params, DesktopPBXHeaders.X_HDEAR, dialNumber)
            }

            // Add features header indicating PSTN capabilities
            addCustomHeader(
                params,
                DesktopPBXHeaders.X_LINPHONE_FEATURES,
                "pstn-dialing,ivr-support,call-recording,transfer"
            )
        }

        /**
         * Add priority header for call handling
         * @param params CallParams to add header to
         * @param priority Priority level (e.g., "emergency", "urgent", "normal", "low")
         */
        @WorkerThread
        fun addPriorityHeader(
            params: CallParams,
            priority: String = "normal"
        ) {
            Log.i("$TAG Adding priority header with value [$priority]")
            addCustomHeader(params, DesktopPBXHeaders.X_PRIORITY, priority)
        }

        /**
         * Generate a unique session ID for call tracking
         */
        @AnyThread
        fun generateSessionId(): String {
            // Format: timestamp-randomUUID
            val timestamp = System.currentTimeMillis()
            val randomPart = kotlin.random.Random.nextLong(0, 1000000)
            return "$timestamp-$randomPart"
        }
    }
}
