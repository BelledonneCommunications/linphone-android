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

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log

class ImageUtils {
    companion object {
        fun getRoundBitmapFromUri(
            context: Context,
            fromPictureUri: Uri?
        ): Bitmap? {
            var bm: Bitmap? = null
            val roundBm: Bitmap?
            if (fromPictureUri != null) {
                bm = try {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(
                        context.contentResolver, fromPictureUri
                    )
                } catch (e: Exception) {
                    return null
                }
            }
            if (bm != null) {
                roundBm = getRoundBitmap(bm)
                if (roundBm != null) {
                    bm.recycle()
                    bm = roundBm
                }
            }
            return bm
        }

        fun rotateImage(source: Bitmap, angle: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(angle)
            val rotatedBitmap = Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true
            )
            source.recycle()
            return rotatedBitmap
        }

        fun getVideoPreview(path: String): Bitmap? {
            return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
        }

        suspend fun getFilesPathFromPickerIntent(data: Intent?, temporaryImageFilePath: File?): List<String> {
            var imageFilePath: String? = null
            if (data != null) {
                val clipData = data.clipData
                if (clipData != null) { // Multiple selection
                    Log.i("[Image Utils] Found ${clipData.itemCount} elements")
                    val list = arrayListOf<String>()
                    for (i in 0 until clipData.itemCount) {
                        val dataUri = clipData.getItemAt(i).uri
                        if (dataUri != null) {
                            imageFilePath = dataUri.toString()
                            Log.i("[Image Utils] Using data URI $imageFilePath")
                        }
                        imageFilePath = cleanFilePath(imageFilePath)
                        if (imageFilePath != null) list.add(imageFilePath)
                    }
                    return list
                } else { // Single selection
                    val dataUri = data.data
                    if (dataUri != null) {
                        imageFilePath = dataUri.toString()
                        Log.i("[Image Utils] Using data URI $imageFilePath")
                    } else if (temporaryImageFilePath?.exists() == true) {
                        imageFilePath = temporaryImageFilePath.absolutePath
                        Log.i("[Image Utils] Data URI is null, using $imageFilePath")
                    }
                    imageFilePath = cleanFilePath(imageFilePath)
                    if (imageFilePath != null) return arrayListOf(imageFilePath)
                }
            } else if (temporaryImageFilePath?.exists() == true) {
                imageFilePath = temporaryImageFilePath.absolutePath
                Log.i("[Image Utils] Data is null, using $imageFilePath")
                imageFilePath = cleanFilePath(imageFilePath)
                if (imageFilePath != null) return arrayListOf(imageFilePath)
            }
            return arrayListOf()
        }

        suspend fun getFilePathFromPickerIntent(data: Intent?, temporaryImageFilePath: File?): String? {
            var imageFilePath: String? = null
            if (data != null) {
                val clipData = data.clipData
                if (clipData != null) { // Multiple selection
                    Log.e("[Image Utils] Expecting only one file, got ${clipData.itemCount}")
                } else { // Single selection
                    val dataUri = data.data
                    if (dataUri != null) {
                        imageFilePath = dataUri.toString()
                        Log.i("[Image Utils] Using data URI $imageFilePath")
                    } else if (temporaryImageFilePath?.exists() == true) {
                        imageFilePath = temporaryImageFilePath.absolutePath
                        Log.i("[Image Utils] Data URI is null, using $imageFilePath")
                    }
                }
            } else if (temporaryImageFilePath?.exists() == true) {
                imageFilePath = temporaryImageFilePath.absolutePath
                Log.i("[Image Utils] Data is null, using $imageFilePath")
            }
            return cleanFilePath(imageFilePath)
        }

        private suspend fun cleanFilePath(filePath: String?): String? {
            if (filePath != null) {
                if (filePath.startsWith("content://") ||
                    filePath.startsWith("file://")
                ) {
                    val uriToParse = Uri.parse(filePath)
                    val result = FileUtils.getFilePath(coreContext.context, uriToParse)
                    Log.i("[Image Utils] Path was using a content or file scheme, real path is: $filePath")
                    if (result == null) {
                        Log.e("[Image Utils] Failed to get access to file $uriToParse")
                    }
                    return result
                }
            }
            return filePath
        }

        private fun getRoundBitmap(bitmap: Bitmap): Bitmap? {
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
