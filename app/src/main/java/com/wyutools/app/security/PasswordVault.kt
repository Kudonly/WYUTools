package com.wyutools.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 使用 Android Keystore 加密保存密码，密钥不可导出。 */
object PasswordVault {
    private const val PREFS_NAME = "secure_passwords"
    private const val KEY_ALIAS = "wyutools_password_vault"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    @Synchronized
    fun save(context: Context, key: String, password: String): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (password.isEmpty()) {
            prefs.edit().remove(key).apply()
            return true
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
            prefs.edit().putString(key, payload).apply()
        }.isSuccess
    }

    @Synchronized
    fun load(context: Context, key: String): String {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payload = prefs.getString(key, null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)),
            )
            String(
                cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)),
                Charsets.UTF_8,
            )
        }.getOrElse {
            prefs.edit().remove(key).apply()
            ""
        }
    }

    @Synchronized
    fun clear(context: Context, key: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
