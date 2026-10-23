package jp.mushamusha.aibrowser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(private val context: Context) {
    companion object {
        private const val KEY_ALIAS = "musha_ai_browser_deepseek_key_v2"
        private const val OLD_KEY_ALIAS = "musha_ai_browser_deepseek_key_v1"
        private const val LEGACY_PREFS = "secure_store"
        private const val LEGACY_VALUE = "deepseek_api_key"
        private const val SECRET_FILE = "deepseek_api_key.enc"
    }

    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private val secretFile: File get() = File(context.noBackupFilesDir, SECRET_FILE)

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(alias: String): SecretKey? = runCatching {
        keyStore().getKey(alias, null) as? SecretKey
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        existingKey(KEY_ALIAS)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun saveApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            clearApiKey()
            return
        }
        writeEncrypted(encrypt(apiKey.trim(), getOrCreateKey()))
        legacyPrefs.edit().remove(LEGACY_VALUE).commit()
        deleteKeyAlias(OLD_KEY_ALIAS)
    }

    fun getApiKey(): String? {
        if (secretFile.exists()) {
            val packed = runCatching { secretFile.readBytes() }.getOrNull() ?: return null
            decryptWithAlias(packed, KEY_ALIAS)?.let { return it }
            decryptWithAlias(packed, OLD_KEY_ALIAS)?.let { migrated ->
                runCatching { saveApiKey(migrated) }
                return migrated
            }
            return null
        }

        val legacy = legacyPrefs.getString(LEGACY_VALUE, null) ?: return null
        val packed = runCatching { Base64.decode(legacy, Base64.NO_WRAP) }.getOrNull() ?: return null
        val migrated = decryptWithAlias(packed, OLD_KEY_ALIAS)
            ?: decryptWithAlias(packed, KEY_ALIAS)
            ?: return null
        runCatching { saveApiKey(migrated) }
        return migrated
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun clearApiKey() {
        legacyPrefs.edit().remove(LEGACY_VALUE).commit()
        secretFile.delete()
        secretFile.parentFile?.listFiles { f -> f.name.startsWith("$SECRET_FILE.tmp.") }?.forEach { it.delete() }
        deleteKeyAlias(KEY_ALIAS)
        deleteKeyAlias(OLD_KEY_ALIAS)
    }

    private fun deleteKeyAlias(alias: String) {
        runCatching {
            val ks = keyStore()
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        }
    }

    private fun writeEncrypted(packed: ByteArray) {
        val dir = secretFile.parentFile ?: error("No secret directory")
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, "$SECRET_FILE.tmp.${android.os.Process.myPid()}.${System.nanoTime()}")
        tmp.outputStream().use { out ->
            out.write(packed)
            out.flush()
            if (out is java.io.FileOutputStream) out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), secretFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Throwable) {
            Files.move(tmp.toPath(), secretFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encrypt(value: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return ByteArray(1 + iv.size + ciphertext.size).also { packed ->
            packed[0] = iv.size.toByte()
            System.arraycopy(iv, 0, packed, 1, iv.size)
            System.arraycopy(ciphertext, 0, packed, 1 + iv.size, ciphertext.size)
        }
    }

    private fun decryptWithAlias(packed: ByteArray, alias: String): String? {
        val key = existingKey(alias) ?: return null
        return decrypt(packed, key)
    }

    private fun decrypt(packed: ByteArray, key: SecretKey): String? = runCatching {
        require(packed.isNotEmpty())
        val ivSize = packed[0].toInt() and 0xFF
        require(ivSize in 12..16 && packed.size > 1 + ivSize)
        val iv = packed.copyOfRange(1, 1 + ivSize)
        val ciphertext = packed.copyOfRange(1 + ivSize, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()
}
