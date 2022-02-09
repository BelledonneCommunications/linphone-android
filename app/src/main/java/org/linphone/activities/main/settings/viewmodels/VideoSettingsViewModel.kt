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
package org.linphone.activities.main.settings.viewmodels

import androidx.databinding.ViewDataBinding
import androidx.lifecycle.MutableLiveData
import java.lang.NumberFormatException
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.core.Factory
import org.linphone.core.tools.Log

class VideoSettingsViewModel : GenericSettingsViewModel() {
    val enableVideoListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isVideoCaptureEnabled = newValue
            core.isVideoDisplayEnabled = newValue
            if (!newValue) {
                tabletPreview.value = false
                initiateCall.value = false
                autoAccept.value = false
            }
        }
    }
    val enableVideo = MutableLiveData<Boolean>()

    val tabletPreviewListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            prefs.videoPreview = newValue
        }
    }
    val tabletPreview = MutableLiveData<Boolean>()
    val isTablet = MutableLiveData<Boolean>()

    val initiateCallListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val policy = core.videoActivationPolicy
            policy.automaticallyInitiate = newValue
            core.videoActivationPolicy = policy
        }
    }
    val initiateCall = MutableLiveData<Boolean>()

    val autoAcceptListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            val policy = core.videoActivationPolicy
            policy.automaticallyAccept = newValue
            core.videoActivationPolicy = policy
        }
    }
    val autoAccept = MutableLiveData<Boolean>()

    val cameraDeviceListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            core.videoDevice = cameraDeviceLabels.value.orEmpty()[position]
        }
    }
    val cameraDeviceIndex = MutableLiveData<Int>()
    val cameraDeviceLabels = MutableLiveData<ArrayList<String>>()

    val videoSizeListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            core.setPreferredVideoDefinitionByName(videoSizeLabels.value.orEmpty()[position])
        }
    }
    val videoSizeIndex = MutableLiveData<Int>()
    val videoSizeLabels = MutableLiveData<ArrayList<String>>()

    val videoPresetListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            videoPresetIndex.value = position // Needed to display/hide two below settings
            core.videoPreset = videoPresetLabels.value.orEmpty()[position]
        }
    }
    val videoPresetIndex = MutableLiveData<Int>()
    val videoPresetLabels = MutableLiveData<ArrayList<String>>()

    val preferredFpsListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            core.preferredFramerate = preferredFpsLabels.value.orEmpty()[position].toFloat()
        }
    }
    val preferredFpsIndex = MutableLiveData<Int>()
    val preferredFpsLabels = MutableLiveData<ArrayList<String>>()

    val bandwidthLimitListener = object : SettingListenerStub() {
        override fun onTextValueChanged(newValue: String) {
            try {
                core.downloadBandwidth = newValue.toInt()
                core.uploadBandwidth = newValue.toInt()
            } catch (nfe: NumberFormatException) {
            }
        }
    }
    val bandwidthLimit = MutableLiveData<Int>()

    val videoCodecs = MutableLiveData<ArrayList<ViewDataBinding>>()

    init {
        enableVideo.value = core.isVideoEnabled && core.videoSupported()
        tabletPreview.value = prefs.videoPreview
        isTablet.value = coreContext.context.resources.getBoolean(R.bool.isTablet)
        initiateCall.value = core.videoActivationPolicy.automaticallyInitiate
        autoAccept.value = core.videoActivationPolicy.automaticallyAccept

        initCameraDevicesList()
        initVideoSizeList()
        initVideoPresetList()
        initFpsList()

        bandwidthLimit.value = core.downloadBandwidth
    }

    fun initCameraDevicesList() {
        val labels = arrayListOf<String>()
        for (camera in core.videoDevicesList) {
            if (prefs.hideStaticImageCamera && camera.startsWith("StaticImage")) {
                Log.w("[Video Settings] Do not display StaticImage camera")
            } else {
                labels.add(camera)
            }
        }

        cameraDeviceLabels.value = labels
        val index = labels.indexOf(core.videoDevice)
        if (index == -1) {
            val firstDevice = cameraDeviceLabels.value.orEmpty().firstOrNull()
            Log.w("[Video Settings] Device not found in labels list: ${core.videoDevice}, replace it by $firstDevice")
            if (firstDevice != null) {
                cameraDeviceIndex.value = 0
                core.videoDevice = firstDevice
            }
        } else {
            cameraDeviceIndex.value = index
        }
    }

    private fun initVideoSizeList() {
        val labels = arrayListOf<String>()

        for (size in Factory.instance().supportedVideoDefinitions) {
            labels.add(size.name.orEmpty())
        }

        videoSizeLabels.value = labels
        videoSizeIndex.value = labels.indexOf(core.preferredVideoDefinition.name)
    }

    private fun initVideoPresetList() {
        val labels = arrayListOf<String>()

        labels.add("default")
        labels.add("high-fps")
        labels.add("custom")

        videoPresetLabels.value = labels
        videoPresetIndex.value = labels.indexOf(core.videoPreset)
    }

    private fun initFpsList() {
        val labels = arrayListOf("5", "10", "15", "20", "25", "30")
        preferredFpsLabels.value = labels
        preferredFpsIndex.value = labels.indexOf(core.preferredFramerate.toInt().toString())
    }
}
