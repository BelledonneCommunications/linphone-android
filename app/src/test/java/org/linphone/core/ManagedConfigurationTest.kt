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

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.linphone.LinphoneApplication
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class ManagedConfigurationTest {

    @MockK(relaxed = true)
    private lateinit var context: Context

    @MockK(relaxed = true)
    private lateinit var restrictionsManager: RestrictionsManager

    @MockK(relaxed = true)
    private lateinit var core: Core

    @MockK(relaxed = true)
    private lateinit var config: Config

    @MockK(relaxed = true)
    private lateinit var coreContextMock: CoreContext

    @MockK(relaxed = true)
    private lateinit var corePreferencesMock: CorePreferences

    @MockK(relaxed = true)
    private lateinit var appliedEvent: MutableLiveData<Event<Boolean>>

    @MockK(relaxed = true)
    private lateinit var removedEvent: MutableLiveData<Event<Boolean>>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkStatic(Log::class)
        every { Log.i(*anyVararg()) } just Runs
        every { Log.w(*anyVararg()) } just Runs
        every { Log.e(*anyVararg()) } just Runs

        every { context.getSystemService(Context.RESTRICTIONS_SERVICE) } returns restrictionsManager
        every { core.config } returns config

        every { coreContextMock.mdmConfigAppliedEvent } returns appliedEvent
        every { coreContextMock.mdmConfigRemovedEvent } returns removedEvent
        LinphoneApplication.coreContext = coreContextMock
        LinphoneApplication.corePreferences = corePreferencesMock
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun bundleOf(vararg pairs: Pair<String, String?>): Bundle {
        val bundle = mockk<Bundle>(relaxed = true)
        every { bundle.isEmpty } returns pairs.isEmpty()
        for ((key, value) in pairs) {
            every { bundle.getString(key) } returns value
        }
        return bundle
    }

    private fun pushRestrictions(vararg pairs: Pair<String, String?>) {
        every { restrictionsManager.applicationRestrictions } returns bundleOf(*pairs)
    }

    @Test
    fun `getRestrictions returns null when system has no RestrictionsManager`() {
        every { context.getSystemService(Context.RESTRICTIONS_SERVICE) } returns null

        val result = ManagedConfiguration.getRestrictions(context)

        assert(result == null)
    }

    @Test
    fun `getRestrictions returns null when reading throws`() {
        every { restrictionsManager.applicationRestrictions } throws RuntimeException("boom")

        val result = ManagedConfiguration.getRestrictions(context)

        assert(result == null)
    }

    @Test
    fun `empty restrictions trigger resetConfig and post the removed event`() {
        pushRestrictions()
        every { core.globalState } returns GlobalState.Off

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify { corePreferencesMock.resetConfigToDefault() }
        verify { config.reload() }
        verify { removedEvent.postValue(any()) }
        verify(exactly = 0) { appliedEvent.postValue(any()) }
    }

    @Test
    fun `resetConfig stops and restarts Core when global state is On`() {
        every { core.globalState } returns GlobalState.On

        ManagedConfiguration.resetConfig(core)

        verify { core.stop() }
        verify { corePreferencesMock.resetConfigToDefault() }
        verify { config.reload() }
        verify { core.start() }
        verify { removedEvent.postValue(any()) }
    }

    @Test
    fun `resetConfig does not start Core when global state is Off`() {
        every { core.globalState } returns GlobalState.Off

        ManagedConfiguration.resetConfig(core)

        verify(exactly = 0) { core.stop() }
        verify { corePreferencesMock.resetConfigToDefault() }
        verify { config.reload() }
        verify(exactly = 0) { core.start() }
        verify { removedEvent.postValue(any()) }
    }

    @Test
    fun `xml-config is loaded as inline string into the rc config`() {
        val xml = "<config xmlns=\"http://www.linphone.org/xsds/lpconfig.xsd\"/>"
        pushRestrictions(ManagedConfiguration.KEY_XML_CONFIG to xml)
        every { core.globalState } returns GlobalState.Off
        every { core.provisioningUri } returns null
        every { core.rootCa } returns null

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 1) { config.loadFromXmlString(xml) }
        verify(exactly = 0) { core.provisioningUri = any() }
        verify(exactly = 0) { core.rootCa = any() }
        verify { appliedEvent.postValue(any()) }
    }

    @Test
    fun `config-uri replaces an outdated provisioning URI`() {
        val newUri = "https://provisioning.example.com/foo.xml"
        pushRestrictions(ManagedConfiguration.KEY_CONFIG_URI to newUri)
        every { core.provisioningUri } returns "https://old.example.com/old.xml"
        every { core.rootCa } returns null
        every { core.globalState } returns GlobalState.Off

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 1) { core.provisioningUri = newUri }
        verify(exactly = 0) { config.loadFromXmlString(any()) }
        verify { appliedEvent.postValue(any()) }
    }

    @Test
    fun `config-uri identical to current is not pushed again`() {
        val uri = "https://provisioning.example.com/foo.xml"
        pushRestrictions(ManagedConfiguration.KEY_CONFIG_URI to uri)
        every { core.provisioningUri } returns uri
        every { core.rootCa } returns null
        every { core.globalState } returns GlobalState.Off

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 0) { core.provisioningUri = any() }
    }

    @Test
    fun `root-ca different from current is pushed`() {
        val pemPath = "/data/local/tmp/ca.pem"
        pushRestrictions(ManagedConfiguration.KEY_ROOT_CA to pemPath)
        every { core.rootCa } returns "/old/ca.pem"
        every { core.provisioningUri } returns null
        every { core.globalState } returns GlobalState.Off

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 1) { core.rootCa = pemPath }
    }

    @Test
    fun `root-ca identical to current is not pushed again`() {
        val pemPath = "/data/local/tmp/ca.pem"
        pushRestrictions(ManagedConfiguration.KEY_ROOT_CA to pemPath)
        every { core.rootCa } returns pemPath
        every { core.provisioningUri } returns null
        every { core.globalState } returns GlobalState.Off

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 0) { core.rootCa = any() }
    }

    @Test
    fun `MDM config-uri overrides a config-uri set inside xml-config's misc section`() {
        val xml = "<config/>"
        val mdmUri = "https://mdm.example.com/foo.xml"
        val xmlInternalUri = "https://embedded-in-xml.example.com/foo.xml"
        pushRestrictions(
            ManagedConfiguration.KEY_XML_CONFIG to xml,
            ManagedConfiguration.KEY_CONFIG_URI to mdmUri
        )
        every { core.provisioningUri } returns null
        every { core.rootCa } returns null
        every { core.globalState } returns GlobalState.Off

        // Simulate xml-config setting [misc] config-uri when loaded.
        every { config.loadFromXmlString(xml) } answers {
            every { core.provisioningUri } returns xmlInternalUri
            0
        }

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 1) { config.loadFromXmlString(xml) }
        verify(exactly = 1) { core.provisioningUri = mdmUri }
    }

    @Test
    fun `MDM config-uri prevails even when it equals the pre-xml provisioning URI`() {
        // Edge case: MDM URI matches what was in core.provisioningUri before xml-config loaded,
        // but xml-config writes a different URI to [misc] config-uri. MDM must still win.
        val xml = "<config/>"
        val mdmUri = "https://mdm.example.com/foo.xml"
        val xmlInternalUri = "https://embedded-in-xml.example.com/foo.xml"
        pushRestrictions(
            ManagedConfiguration.KEY_XML_CONFIG to xml,
            ManagedConfiguration.KEY_CONFIG_URI to mdmUri
        )
        every { core.provisioningUri } returns mdmUri
        every { core.rootCa } returns null
        every { core.globalState } returns GlobalState.Off

        every { config.loadFromXmlString(xml) } answers {
            every { core.provisioningUri } returns xmlInternalUri
            0
        }

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 1) { config.loadFromXmlString(xml) }
        verify(exactly = 1) { core.provisioningUri = mdmUri }
    }

    @Test
    fun `MDM config-uri is not pushed when xml-config already produced the same URI`() {
        // Optimization: if loadFromXmlString happens to set [misc] config-uri to exactly
        // the MDM value, the redundant setter call is skipped. Final state still matches MDM.
        val xml = "<config/>"
        val mdmUri = "https://mdm.example.com/foo.xml"
        pushRestrictions(
            ManagedConfiguration.KEY_XML_CONFIG to xml,
            ManagedConfiguration.KEY_CONFIG_URI to mdmUri
        )
        every { core.provisioningUri } returns null
        every { core.rootCa } returns null
        every { core.globalState } returns GlobalState.Off

        every { config.loadFromXmlString(xml) } answers {
            every { core.provisioningUri } returns mdmUri
            0
        }

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 1) { config.loadFromXmlString(xml) }
        verify(exactly = 0) { core.provisioningUri = any() }
    }

    @Test
    fun `Core is restarted when global state is On after applying`() {
        pushRestrictions(ManagedConfiguration.KEY_XML_CONFIG to "<config/>")
        every { core.globalState } returns GlobalState.On
        every { core.provisioningUri } returns null
        every { core.rootCa } returns null

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify { core.stop() }
        verify { core.start() }
    }

    @Test
    fun `Core is not restarted when global state is not On`() {
        pushRestrictions(ManagedConfiguration.KEY_XML_CONFIG to "<config/>")
        every { core.globalState } returns GlobalState.Off
        every { core.provisioningUri } returns null
        every { core.rootCa } returns null

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 0) { core.stop() }
        verify(exactly = 0) { core.start() }
    }

    @Test
    fun `blank values are ignored`() {
        pushRestrictions(
            ManagedConfiguration.KEY_XML_CONFIG to "   ",
            ManagedConfiguration.KEY_CONFIG_URI to "",
            ManagedConfiguration.KEY_ROOT_CA to "  \n  "
        )
        every { core.provisioningUri } returns null
        every { core.rootCa } returns null
        every { core.globalState } returns GlobalState.Off

        ManagedConfiguration.applyMdmConfigToCore(context, core)

        verify(exactly = 0) { config.loadFromXmlString(any()) }
        verify(exactly = 0) { core.provisioningUri = any() }
        verify(exactly = 0) { core.rootCa = any() }
        verify { appliedEvent.postValue(any()) }
    }
}
