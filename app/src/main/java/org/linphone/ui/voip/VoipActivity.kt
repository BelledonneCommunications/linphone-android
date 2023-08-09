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
package org.linphone.ui.voip

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication
import org.linphone.R
import org.linphone.databinding.VoipActivityBinding
import org.linphone.ui.voip.viewmodel.CallsViewModel

class VoipActivity : AppCompatActivity() {
    private lateinit var binding: VoipActivityBinding

    private lateinit var callViewModel: CallsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        val inCallBlackColor = ContextCompat.getColor(
            this,
            R.color.in_call_black
        )
        window.statusBarColor = inCallBlackColor
        window.navigationBarColor = inCallBlackColor

        while (!LinphoneApplication.coreContext.isReady()) {
            Thread.sleep(20)
        }

        binding = DataBindingUtil.setContentView(this, R.layout.voip_activity)
        binding.lifecycleOwner = this

        callViewModel = run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }

        callViewModel.noMoreCallEvent.observe(this) {
            it.consume {
                finish()
            }
        }
    }
}
