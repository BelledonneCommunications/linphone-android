/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.receivers

import android.content.ClipData
import android.net.Uri
import android.view.View
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import org.linphone.core.tools.Log

class RichContentReceiver(private val contentReceived: (uri: Uri) -> Unit) : OnReceiveContentListener {
    companion object {
        val MIME_TYPES = arrayOf("image/png", "image/gif", "image/jpeg")
    }

    override fun onReceiveContent(view: View, payload: ContentInfoCompat): ContentInfoCompat? {
        val (uriContent, remaining) = payload.partition { item -> item.uri != null }
        if (uriContent != null) {
            val clip: ClipData = uriContent.clip
            for (i in 0 until clip.itemCount) {
                val uri: Uri = clip.getItemAt(i).uri
                Log.i("[Content Receiver] Found URI: $uri")
                contentReceived(uri)
            }
        }
        // Return anything that your app didn't handle. This preserves the default platform
        // behavior for text and anything else that you aren't implementing custom handling for.
        return remaining
    }
}
