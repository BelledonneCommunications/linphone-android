/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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
package org.linphone.activities.main.settings.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.NumberFormatException
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.core.Ldap
import org.linphone.core.LdapAuthMethod
import org.linphone.core.LdapCertVerificationMode
import org.linphone.core.LdapDebugLevel
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class LdapSettingsViewModelFactory(private val index: Int) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (index >= 0 && index <= coreContext.core.ldapList.size) {
            val ldap = coreContext.core.ldapList[index]
            return LdapSettingsViewModel(ldap, index.toString()) as T
        }

        val ldapParams = coreContext.core.createLdapParams()
        val ldap = coreContext.core.createLdapWithParams(ldapParams)
        return LdapSettingsViewModel(ldap, "-1") as T
    }
}

class LdapSettingsViewModel(private val ldap: Ldap, val index: String) : GenericSettingsViewModel() {
    lateinit var ldapSettingsListener: SettingListenerStub

    val ldapConfigDeletedEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val ldapEnableListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = ldap.params.clone()
            params.enabled = newValue
            ldap.params = params
        }
    }
    val ldapEnable = MutableLiveData<Boolean>()

    val deleteListener = object : SettingListenerStub() {
        override fun onClicked() {
            coreContext.core.removeLdap(ldap)
            ldapConfigDeletedEvent.value = Event(true)
        }
    }

    val ldapServerListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.server = newValue
            ldap.params = params
        }
    }
    val ldapServer = MutableLiveData<String>()

    val ldapBindDnListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.bindDn = newValue
            ldap.params = params
        }
    }
    val ldapBindDn = MutableLiveData<String>()

    val ldapPasswordListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.password = newValue
            ldap.params = params
        }
    }
    val ldapPassword = MutableLiveData<String>()

    val ldapAuthMethodListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            val params = ldap.params.clone()
            params.authMethod = LdapAuthMethod.fromInt(ldapAuthMethodValues[position])
            ldap.params = params
            ldapAuthMethodIndex.value = position
        }
    }
    val ldapAuthMethodIndex = MutableLiveData<Int>()
    val ldapAuthMethodLabels = MutableLiveData<ArrayList<String>>()
    private val ldapAuthMethodValues = arrayListOf<Int>()

    val ldapTlsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = ldap.params.clone()
            params.isTlsEnabled = newValue
            ldap.params = params
        }
    }
    val ldapTls = MutableLiveData<Boolean>()

    val ldapCertCheckListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            val params = ldap.params.clone()
            params.serverCertificatesVerificationMode = LdapCertVerificationMode.fromInt(ldapCertCheckValues[position])
            ldap.params = params
            ldapCertCheckIndex.value = position
        }
    }
    val ldapCertCheckIndex = MutableLiveData<Int>()
    val ldapCertCheckLabels = MutableLiveData<ArrayList<String>>()
    private val ldapCertCheckValues = arrayListOf<Int>()

    val ldapSearchBaseListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.baseObject = newValue
            ldap.params = params
        }
    }
    val ldapSearchBase = MutableLiveData<String>()

    val ldapSearchFilterListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.filter = newValue
            ldap.params = params
        }
    }
    val ldapSearchFilter = MutableLiveData<String>()

    val ldapSearchMaxResultsListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val intValue = newValue.toInt()
                val params = ldap.params.clone()
                params.maxResults = intValue
                ldap.params = params
            } catch (nfe: NumberFormatException) {
                Log.e("[LDAP Settings] Failed to set max results ($newValue): $nfe")
            }
        }
    }
    val ldapSearchMaxResults = MutableLiveData<Int>()

    val ldapSearchTimeoutListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val intValue = newValue.toInt()
                val params = ldap.params.clone()
                params.timeout = intValue
                ldap.params = params
            } catch (nfe: NumberFormatException) {
                Log.e("[LDAP Settings] Failed to set timeout ($newValue): $nfe")
            }
        }
    }
    val ldapSearchTimeout = MutableLiveData<Int>()

    val ldapRequestDelayListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val intValue = newValue.toInt()
                val params = ldap.params.clone()
                params.delay = intValue
                ldap.params = params
            } catch (nfe: NumberFormatException) {
                Log.e("[LDAP Settings] Failed to set request delay ($newValue): $nfe")
            }
        }
    }
    val ldapRequestDelay = MutableLiveData<Int>()

    val ldapMinimumCharactersListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                val intValue = newValue.toInt()
                val params = ldap.params.clone()
                params.minChars = intValue
                ldap.params = params
            } catch (nfe: NumberFormatException) {
                Log.e("[LDAP Settings] Failed to set minimum characters ($newValue): $nfe")
            }
        }
    }
    val ldapMinimumCharacters = MutableLiveData<Int>()

    val ldapNameAttributeListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.nameAttribute = newValue
            ldap.params = params
        }
    }
    val ldapNameAttribute = MutableLiveData<String>()

    val ldapSipAttributeListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.sipAttribute = newValue
            ldap.params = params
        }
    }
    val ldapSipAttribute = MutableLiveData<String>()

    val ldapSipDomainListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            val params = ldap.params.clone()
            params.sipDomain = newValue
            ldap.params = params
        }
    }
    val ldapSipDomain = MutableLiveData<String>()

    val ldapDebugListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val params = ldap.params.clone()
            params.debugLevel = if (newValue) LdapDebugLevel.Verbose else LdapDebugLevel.Off
            ldap.params = params
        }
    }
    val ldapDebug = MutableLiveData<Boolean>()

    init {
        val params = ldap.params

        ldapEnable.value = params.enabled
        ldapServer.value = params.server
        ldapBindDn.value = params.bindDn
        ldapPassword.value = params.password
        ldapTls.value = params.isTlsEnabled
        ldapSearchBase.value = params.baseObject
        ldapSearchFilter.value = params.filter
        ldapSearchMaxResults.value = params.maxResults
        ldapSearchTimeout.value = params.timeout
        ldapRequestDelay.value = params.delay
        ldapMinimumCharacters.value = params.minChars
        ldapNameAttribute.value = params.nameAttribute
        ldapSipAttribute.value = params.sipAttribute
        ldapSipDomain.value = params.sipDomain
        ldapDebug.value = params.debugLevel == LdapDebugLevel.Verbose

        initAuthMethodList()
        initTlsCertCheckList()
    }

    private fun initAuthMethodList() {
        val labels = arrayListOf<String>()

        labels.add(prefs.getString(R.string.contacts_settings_ldap_auth_method_anonymous))
        ldapAuthMethodValues.add(LdapAuthMethod.Anonymous.toInt())

        labels.add(prefs.getString(R.string.contacts_settings_ldap_auth_method_simple))
        ldapAuthMethodValues.add(LdapAuthMethod.Simple.toInt())

        ldapAuthMethodLabels.value = labels
        ldapAuthMethodIndex.value = ldapAuthMethodValues.indexOf(ldap.params.authMethod.toInt())
    }

    private fun initTlsCertCheckList() {
        val labels = arrayListOf<String>()

        labels.add(prefs.getString(R.string.contacts_settings_ldap_cert_check_auto))
        ldapCertCheckValues.add(LdapCertVerificationMode.Default.toInt())

        labels.add(prefs.getString(R.string.contacts_settings_ldap_cert_check_disabled))
        ldapCertCheckValues.add(LdapCertVerificationMode.Disabled.toInt())

        labels.add(prefs.getString(R.string.contacts_settings_ldap_cert_check_enabled))
        ldapCertCheckValues.add(LdapCertVerificationMode.Enabled.toInt())

        ldapCertCheckLabels.value = labels
        ldapCertCheckIndex.value = ldapCertCheckValues.indexOf(ldap.params.serverCertificatesVerificationMode.toInt())
    }
}
