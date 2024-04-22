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
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.AvatarGenerator
import org.linphone.core.ChatRoom
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

        @WorkerThread
        fun generateBitmapForChatRoom(chatRoom: ChatRoom): String {
            val id = LinphoneUtils.getChatRoomId(chatRoom)
            val hash = id.hashCode().toString()
            val file = FileUtils.getFileStorageCacheDir("$hash.jpg", overrideExisting = true)
            if (file.exists()) {
                Log.i("$TAG Bitmap for conversation [$id]($hash) exists, using it")
                return FileUtils.getProperFilePath(file.absolutePath)
            }

            val list = arrayListOf<String>()
            for (participant in chatRoom.participants) {
                val contact =
                    coreContext.contactsManager.findContactByAddress(participant.address)
                val picture = contact?.photo
                if (picture != null) {
                    list.add(picture)
                }
            }
            if (list.isNotEmpty() && coreContext.contactsManager.areContactsAvailable()) {
                Log.i(
                    "$TAG Found at [${list.size}] participant(s) with a picture for conversation [$id]($hash), creating avatar"
                )
                val bitmap = generateBitmapFromList(list)
                if (bitmap == null) {
                    Log.e("$TAG Avatar couldn't be generated")
                    return ""
                }
                val outputStream: OutputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.close()
                val generatedPath = FileUtils.getProperFilePath(file.absolutePath)
                Log.i("$TAG Avatar for conversation [$id]($hash) was generated at [$generatedPath]")
                return generatedPath
            } else {
                Log.w(
                    "$TAG Not once picture found for conversation [$id], couldn't generate avatar"
                )
            }
            return ""
        }

        @WorkerThread
        fun generateBitmapFromList(list: ArrayList<String>): Bitmap? {
            val size = AppUtils.getDimension(R.dimen.avatar_in_call_size).toInt()
            return getBitmapFromMultipleAvatars(coreContext.context, size, list)
        }

        @AnyThread
        fun getBitmapFromMultipleAvatars(context: Context, size: Int, images: List<String>): Bitmap? {
            val drawables = images.mapNotNull {
                try {
                    val uri = Uri.parse(it)
                    val stream = context.contentResolver.openInputStream(uri)
                    val bm = BitmapFactory.decodeStream(stream)
                    if (bm != null) {
                        Bitmap.createScaledBitmap(bm, size, size, false)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("$TAG Failed to get scaled bitmap for URI [$it]")
                    null
                }
            }

            if (drawables.isEmpty()) {
                Log.e("$TAG Drawables list is empty, can't generate bitmap without at least one")
                return null
            } else {
                Log.i("$TAG Generating avatar using [${drawables.size}] drawables")
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

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

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
                    Rect(0, 0, size, size)
                }

                try {
                    canvas.drawBitmap(
                        drawables[i]/*.toBitmap(size, size, Bitmap.Config.ARGB_8888)*/,
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
