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
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import java.io.FileNotFoundException
import org.linphone.core.tools.Log

class ImageUtils {
    companion object {
        private const val TAG = "[Image Utils]"

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

        @AnyThread
        suspend fun getBitmapFromMultipleAvatars(context: Context, size: Int, images: List<String>): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val drawables = images.mapNotNull {
                val request = ImageRequest.Builder(context)
                    .data(it)
                    .size(size / 2)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).drawable
            }

            val rectangles = if (drawables.size == 2) {
                arrayListOf(
                    Rect(0, 0, size / 2, size),
                    Rect(size / 2, 0, size, size)
                )
            } else if (drawables.size == 3) {
                arrayListOf(
                    Rect(0, 0, size / 2, size / 2),
                    Rect(size / 2, 0, size, size / 2),
                    Rect(0, size / 2, size, size)
                )
            } else if (drawables.size >= 4) {
                arrayListOf(
                    Rect(0, 0, size / 2, size / 2),
                    Rect(size / 2, 0, size, size / 2),
                    Rect(0, size / 2, size / 2, size),
                    Rect(size / 2, size / 2, size, size)
                )
            } else {
                arrayListOf(Rect(0, 0, size, size))
            }

            for (i in 0 until rectangles.size) {
                val src = if (drawables.size == 3 && i == 2) {
                    // To prevent deformation for the bottom image when merging 3 of them
                    val quarter = size / 4
                    Rect(0, quarter, size, 3 * quarter)
                } else if (drawables.size == 2) {
                    // To prevent deformation when two images are next to each other
                    val quarter = size / 4
                    Rect(quarter, 0, 3 * quarter, size)
                } else {
                    null
                }

                try {
                    canvas.drawBitmap(
                        drawables[i].toBitmap(size, size, Bitmap.Config.ARGB_8888),
                        src,
                        rectangles[i],
                        null
                    )
                } catch (_: Exception) {}
            }

            return bitmap
        }
    }
}
