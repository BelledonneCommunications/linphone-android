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
package org.linphone.core

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Pair
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.linphone.core.tools.Log

class VFS {
    companion object {
        private const val TAG = "[VFS]"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val ALIAS = "vfs"
        private const val LINPHONE_VFS_ENCRYPTION_AES256GCM128_SHA256 = 2
        private const val VFS_IV = "vfsiv"
        private const val VFS_KEY = "vfskey"
        private const val ENCRYPTED_SHARED_PREFS_FILE = "encrypted.pref"

        fun isEnabled(context: Context): Boolean {
            val preferences = getEncryptedSharedPreferences(context)
            if (preferences == null) {
                Log.e("$TAG Failed to get encrypted shared preferences!")
                return false
            }
            return preferences.getBoolean("vfs_enabled", false)
        }

        fun enable(context: Context): Boolean {
            val preferences = getEncryptedSharedPreferences(context)
            if (preferences == null) {
                Log.e("$TAG Failed to get encrypted shared preferences, VFS won't be enabled!")
                return false
            }

            if (preferences.getBoolean("vfs_enabled", false)) {
                Log.w("$TAG VFS is already enabled, skipping...")
                return false
            }

            preferences.edit().putBoolean("vfs_enabled", true).apply()
            return true
        }

        fun setup(context: Context) {
            // Use Android logger as our isn't ready yet
            try {
                android.util.Log.i(TAG, "$TAG Initializing...")
                val preferences = getEncryptedSharedPreferences(context)
                if (preferences == null) {
                    Log.e("$TAG Failed to get encrypted shared preferences, can't initialize VFS!")
                    return
                }

                if (preferences.getString(VFS_IV, null) == null) {
                    android.util.Log.i(TAG, "$TAG No initialization vector found, generating it")
                    generateSecretKey()
                    encryptToken(generateToken()).let { data ->
                        preferences
                            .edit()
                            .putString(VFS_IV, data.first)
                            .putString(VFS_KEY, data.second)
                            .commit()
                    }
                }

                Factory.instance().setVfsEncryption(
                    LINPHONE_VFS_ENCRYPTION_AES256GCM128_SHA256,
                    getVfsKey(preferences).toByteArray().copyOfRange(0, 32),
                    32
                )

                android.util.Log.i(TAG, "$TAG Initialized")
            } catch (e: Exception) {
                android.util.Log.wtf(TAG, "$TAG Unable to activate VFS encryption: $e")
            }
        }

        private fun getEncryptedSharedPreferences(context: Context): SharedPreferences? {
            return try {
                val masterKey: MasterKey = MasterKey.Builder(
                    context,
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS
                ).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

                EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_SHARED_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (kse: KeyStoreException) {
                Log.e("[VFS] Keystore exception: $kse")
                null
            } catch (e: Exception) {
                Log.e("[VFS] Exception: $e")
                null
            }
        }

        @Throws(java.lang.Exception::class)
        private fun generateSecretKey() {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            keyGenerator.generateKey()
        }

        @Throws(java.lang.Exception::class)
        private fun getSecretKey(): SecretKey? {
            val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
            ks.load(null)
            val entry = ks.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        @Throws(java.lang.Exception::class)
        private fun generateToken(): String {
            return sha512(UUID.randomUUID().toString())
        }

        @Throws(java.lang.Exception::class)
        private fun encryptData(textToEncrypt: String): Pair<ByteArray, ByteArray> {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            return Pair<ByteArray, ByteArray>(
                iv,
                cipher.doFinal(textToEncrypt.toByteArray(StandardCharsets.UTF_8))
            )
        }

        @Throws(java.lang.Exception::class)
        private fun decryptData(encrypted: String?, encryptionIv: ByteArray): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, encryptionIv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val encryptedData = Base64.decode(encrypted, Base64.DEFAULT)
            return String(cipher.doFinal(encryptedData), StandardCharsets.UTF_8)
        }

        @Throws(java.lang.Exception::class)
        private fun encryptToken(token: String): Pair<String?, String?> {
            val encryptedData = encryptData(token)
            return Pair<String?, String?>(
                Base64.encodeToString(encryptedData.first, Base64.DEFAULT),
                Base64.encodeToString(encryptedData.second, Base64.DEFAULT)
            )
        }

        @Throws(java.lang.Exception::class)
        private fun sha512(input: String): String {
            val md = MessageDigest.getInstance("SHA-512")
            val messageDigest = md.digest(input.toByteArray())
            val no = BigInteger(1, messageDigest)
            var hashtext = no.toString(16)
            while (hashtext.length < 32) {
                hashtext = "0$hashtext"
            }
            return hashtext
        }

        @Throws(java.lang.Exception::class)
        private fun getVfsKey(sharedPreferences: SharedPreferences): String {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            return decryptData(
                sharedPreferences.getString(VFS_KEY, null),
                Base64.decode(sharedPreferences.getString(VFS_IV, null), Base64.DEFAULT)
            )
        }
    }
}
