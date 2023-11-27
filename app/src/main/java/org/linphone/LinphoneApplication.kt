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
package org.linphone

import android.annotation.SuppressLint
import android.app.Application
import androidx.annotation.MainThread
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.google.android.material.color.DynamicColors
import org.linphone.core.CoreContext
import org.linphone.core.CorePreferences
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.LogLevel

@MainThread
class LinphoneApplication : Application(), ImageLoaderFactory {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var corePreferences: CorePreferences

        @SuppressLint("StaticFieldLeak")
        lateinit var coreContext: CoreContext
    }

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext

        Factory.instance().setLogCollectionPath(context.filesDir.absolutePath)
        Factory.instance().enableLogCollection(LogCollectionState.Enabled)
        // For VFS
        Factory.instance().setCacheDir(context.cacheDir.absolutePath)

        corePreferences = CorePreferences(context)
        corePreferences.copyAssetsFromPackage()
        val config = Factory.instance().createConfigWithFactory(
            corePreferences.configPath,
            corePreferences.factoryConfigPath
        )
        corePreferences.config = config

        val appName = context.getString(R.string.app_name)
        Factory.instance().setLoggerDomain(appName)
        Factory.instance().enableLogcatLogs(true)
        if (corePreferences.debugLogs) {
            Factory.instance().loggingService.setLogLevel(LogLevel.Message)
        }

        coreContext = CoreContext(context)
        coreContext.start()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(false)
            .components {
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
                add(ImageDecoderDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .networkCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
