package org.linphone.utils

import org.junit.Test
import org.mockito.Mockito.*
import org.linphone.core.CallParams

class SipHeadersUtilsTest {
    @Test
    fun pstnHeadersIncludeDialNumberAndHdear() {
        val params = mock(CallParams::class.java)

        SipHeadersUtils.addDesktopPBXPstnHeaders(
            params,
            pstnGateway = "pbx.example.com",
            routeType = SipHeadersUtils.CallRoutingType.SIP_TO_PSTN,
            sessionId = "sess-123",
            dialNumber = "01816676486"
        )

        verify(params).addCustomHeader("X-Call-Routing", SipHeadersUtils.CallRoutingType.SIP_TO_PSTN)
        verify(params).addCustomHeader("X-PSTN-Gateway", "pbx.example.com")
        verify(params).addCustomHeader("X-Session-ID", "sess-123")
        verify(params).addCustomHeader("X-Dial-Number", "01816676486")
        verify(params).addCustomHeader("X-HDEAR", "01816676486")
    }
}
