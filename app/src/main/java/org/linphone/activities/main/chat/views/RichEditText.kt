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
package org.linphone.activities.main.chat.views

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import org.linphone.activities.main.chat.ContentReceiver
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log

/**
 * Allows for image input inside an EditText, usefull for keyboards with gif support for example.
 */
class RichEditText : AppCompatEditText {
    constructor(context: Context) : super(context) {
        initReceiveContentListener()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initReceiveContentListener()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initReceiveContentListener()
    }

    private fun initReceiveContentListener() {
        ViewCompat.setOnReceiveContentListener(
            this, ContentReceiver.MIME_TYPES,
            ContentReceiver { uri ->
                Log.i("[Rich Edit Text] Received URI: $uri")
                val activity = context as Activity
                val sharedViewModel = activity.run {
                    ViewModelProvider(activity as ViewModelStoreOwner).get(SharedMainViewModel::class.java)
                }
                sharedViewModel.richContentUri.value = uri
            }
        )
    }
}
