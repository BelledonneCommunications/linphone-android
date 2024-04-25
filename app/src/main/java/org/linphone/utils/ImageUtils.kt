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
package org.linphone.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.io.FileNotFoundException
import org.linphone.contacts.AvatarGenerator
import org.linphone.core.tools.Log

class ImageUtils {
    companion object {
        private const val TAG = "[Image Utils]"

        @AnyThread
        fun getGeneratedAvatar(context: Context, size: Int = 0, textSize: Int = 0, initials: String): BitmapDrawable {
            val builder = AvatarGenerator(context)
            builder.setInitials(initials)
            if (size > 0) {
                builder.setAvatarSize(
                    AppUtils.getDimension(size).toInt()
                )
            }
            if (textSize > 0) {
                builder.setTextSize(AppUtils.getDimension(textSize))
            }
            return builder.build()
        }

        @WorkerThread
        fun getBitmap(
            context: Context,
            path: String?
        ): Bitmap? {
            Log.d("$TAG Trying to create Bitmap from path [$path]")
            if (path != null) {
                try {
                    val fromPictureUri = Uri.parse(path)
                    if (fromPictureUri == null) {
                        Log.e("$TAG Failed to parse path [$path] as URI")
                        return null
                    }

                    // We make a copy to ensure Bitmap will be Software and not Hardware, required for shortcuts
                    return ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, fromPictureUri)
                    ).copy(
                        Bitmap.Config.ARGB_8888,
                        true
                    )
                } catch (fnfe: FileNotFoundException) {
                    Log.e("$TAG File [$path] not found: $fnfe")
                    return null
                } catch (e: Exception) {
                    Log.e("$TAG Failed to get bitmap using path [$path]: $e")
                    return null
                }
            }

            Log.e("$TAG Can't get bitmap from null URI")
            return null
        }
    }
}
