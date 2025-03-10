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
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.io.FileNotFoundException
import org.linphone.contacts.AvatarGenerator
import org.linphone.core.tools.Log
import androidx.core.net.toUri

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
            return builder.buildDrawable()
        }

        @WorkerThread
        fun getBitmap(
            context: Context,
            path: String?,
            round: Boolean = false
        ): Bitmap? {
            Log.d("$TAG Trying to create Bitmap from path [$path]")
            if (path != null) {
                try {
                    val fromPictureUri = path.toUri()
                    // We make a copy to ensure Bitmap will be Software and not Hardware, required for shortcuts
                    val bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(context.contentResolver, fromPictureUri)
                    ).copy(
                        Bitmap.Config.ARGB_8888,
                        true
                    )
                    return if (round) {
                        getRoundBitmap(bitmap)
                    } else {
                        bitmap
                    }
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

        @AnyThread
        private fun getRoundBitmap(bitmap: Bitmap): Bitmap {
            val output =
                Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val color = -0xbdbdbe
            val paint = Paint()
            val rect =
                Rect(0, 0, bitmap.width, bitmap.height)
            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = color
            canvas.drawCircle(
                bitmap.width / 2.toFloat(),
                bitmap.height / 2.toFloat(),
                bitmap.width / 2.toFloat(),
                paint
            )
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }
    }
}
