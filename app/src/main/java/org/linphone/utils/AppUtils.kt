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
package org.linphone.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import androidx.emoji2.text.EmojiCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import java.util.*
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log

/**
 * Various utility methods for application
 */
class AppUtils {
    companion object {
        fun getString(id: Int): String {
            return coreContext.context.getString(id)
        }

        fun getStringWithPlural(id: Int, count: Int): String {
            return coreContext.context.resources.getQuantityString(id, count, count)
        }

        fun getStringWithPlural(id: Int, count: Int, value: String): String {
            return coreContext.context.resources.getQuantityString(id, count, value)
        }

        fun getDimension(id: Int): Float {
            return coreContext.context.resources.getDimension(id)
        }

        fun getInitials(displayName: String, limit: Int = 2): String {
            if (displayName.isEmpty()) return ""

            val split = displayName.uppercase(Locale.getDefault()).split(" ")
            var initials = ""
            var characters = 0

            val emoji = try {
                EmojiCompat.get()
            } catch (ise: IllegalStateException) {
                Log.e("[App Utils] Can't get EmojiCompat: $ise")
                null
            }

            for (i in split.indices) {
                if (split[i].isNotEmpty()) {
                    try {
                        if (emoji?.hasEmojiGlyph(split[i]) == true) {
                            initials += emoji.process(split[i])
                        } else {
                            initials += split[i][0]
                        }
                    } catch (ise: IllegalStateException) {
                        Log.e("[App Utils] Can't call hasEmojiGlyph: $ise")
                        initials += split[i][0]
                    }

                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
        }

        fun pixelsToDp(pixels: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                pixels,
                coreContext.context.resources.displayMetrics
            )
        }

        fun dpToPixels(context: Context, dp: Float): Float {
            return dp * context.resources.displayMetrics.density
        }

        fun bytesToDisplayableSize(bytes: Long): String {
            return formatShortFileSize(coreContext.context, bytes)
        }

        fun shareUploadedLogsUrl(activity: Activity, info: String) {
            val appName = activity.getString(R.string.app_name)
            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(
                Intent.EXTRA_EMAIL,
                arrayOf(activity.getString(R.string.about_bugreport_email))
            )
            intent.putExtra(Intent.EXTRA_SUBJECT, "$appName Logs")
            intent.putExtra(Intent.EXTRA_TEXT, info)
            intent.type = "text/plain"

            try {
                activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_uploaded_logs_link)))
            } catch (ex: ActivityNotFoundException) {
                Log.e(ex)
            }
        }

        fun getDividerDecoration(context: Context, layoutManager: LinearLayoutManager): DividerItemDecoration {
            val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
            val divider = ResourcesCompat.getDrawable(context.resources, R.drawable.divider, null)
            if (divider != null) dividerItemDecoration.setDrawable(divider)
            return dividerItemDecoration
        }

        fun createBundleWithSharedTextAndFiles(sharedViewModel: SharedMainViewModel): Bundle {
            val bundle = Bundle()
            bundle.putString("TextToShare", sharedViewModel.textToShare.value.orEmpty())
            bundle.putStringArrayList("FilesToShare", sharedViewModel.filesToShare.value)

            // Remove values from shared view model
            sharedViewModel.textToShare.value = ""
            sharedViewModel.filesToShare.value = arrayListOf()

            return bundle
        }

        fun isMediaVolumeLow(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.i("[Media Volume] Current value is $currentVolume, max value is $maxVolume")
            return currentVolume <= maxVolume * 0.5
        }

        fun acquireAudioFocusForVoiceRecordingOrPlayback(context: Context): AudioFocusRequestCompat {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioAttrs = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                .build()

            val request =
                AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(audioAttrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
            when (AudioManagerCompat.requestAudioFocus(audioManager, request)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.i("[Audio Focus] Voice recording/playback audio focus request granted")
                }
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    Log.w("[Audio Focus] Voice recording/playback audio focus request failed")
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    Log.w("[Audio Focus] Voice recording/playback audio focus request delayed")
                }
            }
            return request
        }

        fun releaseAudioFocusForVoiceRecordingOrPlayback(
            context: Context,
            request: AudioFocusRequestCompat
        ) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
            Log.i("[Audio Focus] Voice recording/playback audio focus request abandoned")
        }
    }
}
