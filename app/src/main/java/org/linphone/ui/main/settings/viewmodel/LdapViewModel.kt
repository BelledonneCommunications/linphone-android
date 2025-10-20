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
package org.linphone.ui.main.settings.viewmodel

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Ldap
import org.linphone.core.tools.Log
import org.linphone.ui.GenericViewModel
import org.linphone.utils.Event

class LdapViewModel : GenericViewModel() {
    companion object {
        private const val TAG = "[LDAP ViewModel]"
    }

    val isEdit = MutableLiveData<Boolean>()

    val isEnabled = MutableLiveData<Boolean>()

    val serverUrl = MutableLiveData<String>()

    val bindDn = MutableLiveData<String>()

    val password = MutableLiveData<String>()

    val showPassword = MutableLiveData<Boolean>()

    val useTls = MutableLiveData<Boolean>()

    val searchBase = MutableLiveData<String>()

    val searchFilter = MutableLiveData<String>()

    val maxResults = MutableLiveData<String>()

    val requestTimeout = MutableLiveData<String>()

    val requestDelay = MutableLiveData<String>()

    val minCharacters = MutableLiveData<String>()

    val nameAttributes = MutableLiveData<String>()

    val sipAttributes = MutableLiveData<String>()

    val sipDomain = MutableLiveData<String>()

    val verboseMode = MutableLiveData<Boolean>()

    val ldapServerOperationSuccessfulEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    private lateinit var ldapToEdit: Ldap

    init {
        isEdit.value = false
        isEnabled.value = true
        showPassword.value = false

        useTls.value = true
        minCharacters.value = "3"
        requestTimeout.value = "5"
        requestDelay.value = "2000"
        verboseMode.value = true
    }

    @UiThread
    fun loadLdap(url: String) {
        coreContext.postOnCoreThread { core ->
            val found = core.ldapList.find {
                it.params.server == url
            }
            if (found == null) {
                Log.e("$TAG Failed to find LDAP server with URL [$url]!")
                return@postOnCoreThread
            }

            isEdit.postValue(true)
            ldapToEdit = found
            val ldapParams = ldapToEdit.params
            isEnabled.postValue(ldapParams.enabled)

            serverUrl.postValue(ldapParams.server)
            bindDn.postValue(ldapParams.bindDn.orEmpty())
            useTls.postValue(ldapParams.isTlsEnabled)
            searchBase.postValue(ldapParams.baseObject)
            searchFilter.postValue(ldapParams.filter.orEmpty())
            maxResults.postValue(ldapParams.maxResults.toString())
            requestTimeout.postValue(ldapParams.timeout.toString())
            requestDelay.postValue(ldapParams.delay.toString())
            minCharacters.postValue(ldapParams.minChars.toString())
            nameAttributes.postValue(ldapParams.nameAttribute.orEmpty())
            sipAttributes.postValue(ldapParams.sipAttribute.orEmpty())
            sipDomain.postValue(ldapParams.sipDomain.orEmpty())
            verboseMode.postValue(ldapParams.debugLevel == Ldap.DebugLevel.Verbose)
            Log.i("$TAG Existing LDAP server values loaded")
        }
    }

    @UiThread
    fun delete() {
        coreContext.postOnCoreThread { core ->
            if (isEdit.value == true && ::ldapToEdit.isInitialized) {
                val serverUrl = ldapToEdit.params.server
                core.removeLdap(ldapToEdit)
                Log.i("$TAG Removed LDAP config for server URL [$serverUrl]")
                ldapServerOperationSuccessfulEvent.postValue(Event(true))
            }
        }
    }

    @UiThread
    fun toggleEnabled() {
        isEnabled.value = isEnabled.value == false
    }

    @UiThread
    fun toggleShowPassword() {
        showPassword.value = showPassword.value == false
    }

    @UiThread
    fun toggleTls() {
        useTls.value = useTls.value == false
    }

    @UiThread
    fun toggleDebug() {
        verboseMode.value = verboseMode.value == false
    }

    @UiThread
    fun addServer() {
        coreContext.postOnCoreThread { core ->
            try {
                val server = serverUrl.value.orEmpty().trim()
                val bindDn = bindDn.value.orEmpty().trim()
                val base = searchBase.value.orEmpty().trim()
                val filter = searchFilter.value.orEmpty().trim()
                val maxResults = maxResults.value.orEmpty().trim()
                val timeout = requestTimeout.value.orEmpty().trim()
                val delay = requestDelay.value.orEmpty().trim()
                val minChars = minCharacters.value.orEmpty().trim()
                val nameAttrs = nameAttributes.value.orEmpty().trim()
                val sipAttrs = sipAttributes.value.orEmpty().trim()
                val sipDomain = sipDomain.value.orEmpty().trim()
                val pwd = password.value.orEmpty().trim()
                if (
                    server.isEmpty() || bindDn.isEmpty() || base.isEmpty() || filter.isEmpty() ||
                    maxResults.isEmpty() || timeout.isEmpty() || delay.isEmpty() ||
                    minChars.isEmpty() || nameAttrs.isEmpty() || sipAttrs.isEmpty() ||
                    sipDomain.isEmpty()
                ) {
                    Log.e("$TAG All fields must be filled!")
                    showRedToast(R.string.settings_contacts_ldap_empty_field_error_toast, R.drawable.warning_circle)
                    return@postOnCoreThread
                }

                val ldapParams = core.createLdapParams()
                ldapParams.enabled = isEnabled.value == true
                ldapParams.server = server
                ldapParams.bindDn = bindDn
                if (!pwd.isEmpty()) {
                    ldapParams.password = pwd
                } else if (::ldapToEdit.isInitialized) {
                    ldapParams.password = ldapToEdit.params.password
                }
                ldapParams.authMethod = Ldap.AuthMethod.Simple
                ldapParams.isTlsEnabled = useTls.value == true
                ldapParams.serverCertificatesVerificationMode = Ldap.CertVerificationMode.Default
                ldapParams.baseObject = base
                ldapParams.filter = filter
                ldapParams.maxResults = maxResults.toInt()
                ldapParams.timeout = timeout.toInt()
                ldapParams.delay = delay.toInt()
                ldapParams.minChars = minChars.toInt()
                ldapParams.nameAttribute = nameAttrs
                ldapParams.sipAttribute = sipAttrs
                ldapParams.sipDomain = sipDomain
                ldapParams.debugLevel = if (verboseMode.value == true) {
                    Ldap.DebugLevel.Verbose
                } else {
                    Ldap.DebugLevel.Off
                }

                if (isEdit.value == true && ::ldapToEdit.isInitialized) {
                    ldapToEdit.params = ldapParams
                    Log.i("$TAG LDAP changes have been applied")
                } else {
                    val ldap = core.createLdapWithParams(ldapParams)
                    core.addLdap(ldap)
                    Log.i("$TAG New LDAP config created")
                }
                ldapServerOperationSuccessfulEvent.postValue(Event(true))
            } catch (e: Exception) {
                Log.e("$TAG Exception while creating LDAP: $e")
                showRedToast(R.string.settings_contacts_ldap_error_toast, R.drawable.warning_circle)
            }
        }
    }
}
