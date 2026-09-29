package org.linphone.interfaces

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.linphone.models.callhistory.CallTypes
import org.linphone.models.callhistory.PbxType
import org.linphone.utils.GsonUtils
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Serves sample gateway responses (src/test/resources/gateway) to CTGatewayService through the
 * same Gson configuration the app uses, checking request paths and how the JSON maps onto the models.
 *
 * The fixtures are written from the model classes, not captured from the gateway, so they test our
 * reading of the API rather than what the server actually sends.
 */
class CTGatewayServiceContractTest {

    private lateinit var server: MockWebServer
    private lateinit var gateway: CTGatewayService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(GsonUtils.create()))
            .build()
            .create(CTGatewayService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueFixture(name: String) {
        val body = javaClass.getResource("/gateway/$name")!!.readText()
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/json"))
    }

    private fun assertRequest(method: String, path: String) {
        val request = server.takeRequest()
        assertEquals(method, request.method)
        assertEquals(path, request.path)
    }

    @Test
    fun `getUserInfo maps the current user`() = runBlocking {
        enqueueFixture("users-me.json")

        val user = gateway.getUserInfo().body()!!

        assertRequest("GET", "/api/v1.0/users/me")
        assertEquals("5b0c4f7e-1a2b-4c3d-8e9f-000000000001", user.id)
        assertEquals("Test User", user.displayName)
        assertEquals("GB", user.pbxCountryCode)
        assertTrue("isEnabled sent as 1", user.isEnabled)
        assertTrue(user.hasClientPermission())
        assertEquals(2, user.deviceCount.toInt())
        assertTrue(user.clientProfileSettings.presenceSelectionEnabled)
        assertTrue(user.clientProfileSettings.queueControlEnabled)
        assertFalse(user.clientProfileSettings.agentControlDisplayed)
    }

    @Test
    fun `getUserBranding maps tenant branding`() = runBlocking {
        enqueueFixture("users-me-branding.json")

        val branding = gateway.getUserBranding().body()!!

        assertRequest("GET", "/api/v1.0/users/me/branding")
        assertEquals("Example Brand", branding.brandName)
        assertEquals("https://docs.example.com", branding.documentationRootUrl)
        assertEquals("Example Identity", branding.identityPortal!!.name)
        assertNull(branding.identityPortal!!.clientStartupScript)
        assertEquals("Example Customer Portal", branding.customerPortal!!.name)
        assertNull("absent portals stay null", branding.systemPortal)
        assertEquals("#123456", branding.cssVariables["--primary-color"])
    }

    @Test
    fun `getUserBranding treats an empty body as no branding`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = gateway.getUserBranding()

        assertTrue(response.isSuccessful)
        assertNull(response.body())
    }

    @Test
    fun `getReportResult maps call history`() = runBlocking {
        enqueueFixture("usercallhistory-report.json")

        val report = gateway.getReportResult("request-1").body()!!

        assertRequest("GET", "/api/v1.0/usercallhistory/report?requestId=request-1")
        assertEquals(1, report.status)
        assertEquals(2, report.data!!.size)

        val answered = report.data!![0]
        assertTrue(answered.answered)
        assertFalse(answered.missedCall)
        assertEquals(ZonedDateTime.of(2025, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC), answered.startTime)
        assertEquals(CallTypes.External, answered.callType)
        assertEquals(2, answered.callDirection)
        assertEquals(PbxType.DimensionsVoice, answered.pbxType)
        assertEquals("Sale", answered.interactionTags.single().value)

        val missed = report.data!![1]
        assertTrue("missedCall sent as 1", missed.missedCall)
        assertFalse("answered sent as 0", missed.answered)
        assertEquals("start time without a zone is still read", 8, missed.startTime!!.hour)
        assertEquals("callType sent as a number", CallTypes.Internal, missed.callType)
        assertEquals(PbxType.Kazoo, missed.pbxType)
        assertTrue(missed.interactionTags.isEmpty())
    }
}
