package com.qingguang.browser.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份口令加密：PBKDF2(SHA-256, 12000 次) 派生 256 位密钥，AES/GCM 加密。
 * 文件格式：QGEN1:base64( 16 字节盐 + 12 字节 IV + 密文 )
 */
object BackupCrypto {

    const val MAGIC = "QGEN1:"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun isEncrypted(text: String): Boolean = text.startsWith(MAGIC)

    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(SALT_LEN).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(TAG_BITS, iv)
        )
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, payload, 0, salt.size)
        System.arraycopy(iv, 0, payload, salt.size, iv.size)
        System.arraycopy(cipherText, 0, payload, salt.size + iv.size, cipherText.size)
        return MAGIC + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** 解密失败（口令错误或文件损坏）返回 null */
    fun decrypt(text: String, password: String): String? = runCatching {
        require(isEncrypted(text))
        val payload = Base64.decode(text.removePrefix(MAGIC), Base64.NO_WRAP)
        if (payload.size <= SALT_LEN + IV_LEN) return null
        val salt = payload.copyOfRange(0, SALT_LEN)
        val iv = payload.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val cipherText = payload.copyOfRange(SALT_LEN + IV_LEN, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(TAG_BITS, iv)
        )
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
