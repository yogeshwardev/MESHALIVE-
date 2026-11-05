package com.meshalive.app.mesh

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * DEMO-GRADE encryption: AES-256-GCM with a key derived (PBKDF2) from a passphrase
 * shared by every phone and a fixed salt. Anyone who knows the passphrase can read
 * and forge messages; there is no per-user identity or forward secrecy.
 */
class Crypto(passphrase: String) {
    private val key: SecretKeySpec = deriveKey(passphrase)
    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all, 0, 12))
        String(cipher.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private val SALT = "MeshAlive-static-salt-v1".toByteArray()

        private fun deriveKey(passphrase: String): SecretKeySpec {
            val spec = PBEKeySpec(passphrase.toCharArray(), SALT, 10_000, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        }
    }
}
