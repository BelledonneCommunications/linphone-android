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
package org.linphone

import android.app.Application
import org.linphone.core.CoreContext
import org.linphone.core.CorePreferences
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.tools.Log

class LinphoneApplication : Application() {
    companion object {
        lateinit var corePreferences: CorePreferences
        lateinit var coreContext: CoreContext
    }

    override fun onCreate() {
        super.onCreate()
        val appName = getString(R.string.app_name)
        android.util.Log.i("[$appName]", "Application is being created")

        Factory.instance().setLogCollectionPath(applicationContext.filesDir.absolutePath)
        Factory.instance().enableLogCollection(LogCollectionState.Enabled)

        corePreferences = CorePreferences(applicationContext)
        corePreferences.copyAssetsFromPackage()

        val config = Factory.instance().createConfigWithFactory(corePreferences.configPath, corePreferences.factoryConfigPath)
        corePreferences.config = config

        Factory.instance().setDebugMode(corePreferences.debugLogs, appName)

        coreContext = CoreContext(applicationContext, config)
        coreContext.start()
        Log.i("[Application] Created")
    }
}
