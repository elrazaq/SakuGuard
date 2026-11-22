package com.sakuguard.app.data.backup

import com.sakuguard.app.domain.model.BackupException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    const val DEFAULT_KDF_ITERATIONS = 210_000
    const val MIN_KDF_ITERATIONS = 100_000
    const val MAX_KDF_ITERATIONS = 1_000_000
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val KEY_BITS = 256
    const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()

    data class EncryptedPayload(
        val ciphertext: ByteArray,
        val saltBase64: String,
        val ivBase64: String,
        val kdfIterations: Int,
    )

    fun canonicalAad(
        backupFormatVersion: Int,
        databaseSchemaVersion: Int,
        createdAtEpochMillis: Long,
        appVersionCode: Int,
        kdfAlgorithm: String?,
        kdfIterations: Int?,
    ): ByteArray {
        val canonical =
            "sakuguard-saku-v1|format=$backupFormatVersion|schema=$databaseSchemaVersion|" +
                "created=$createdAtEpochMillis|app=$appVersionCode|kdf=${kdfAlgorithm.orEmpty()}|iter=${kdfIterations ?: 0}"
        return canonical.toByteArray(StandardCharsets.UTF_8)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
        aad: ByteArray,
        iterations: Int = DEFAULT_KDF_ITERATIONS,
    ): EncryptedPayload {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }

        val keyBytes = deriveKeyBytes(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plaintext)
            EncryptedPayload(
                ciphertext = ciphertext,
                saltBase64 = Base64.getEncoder().encodeToString(salt),
                ivBase64 = Base64.getEncoder().encodeToString(iv),
                kdfIterations = iterations,
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decrypt(
        ciphertext: ByteArray,
        password: CharArray,
        kdfAlgorithm: String?,
        kdfIterations: Int?,
        saltBase64: String?,
        ivBase64: String?,
        cipherAlgorithm: String?,
        aad: ByteArray,
    ): ByteArray {
        if (kdfAlgorithm != KDF_ALGORITHM || cipherAlgorithm != CIPHER_ALGORITHM) {
            throw BackupException.InvalidArchiveException("Unsupported encryption parameters")
        }
        val iterations = kdfIterations ?: throw BackupException.InvalidArchiveException("Missing KDF iterations")
        if (iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
            throw BackupException.InvalidArchiveException("KDF iterations out of allowed bounds")
        }
        val salt = decodeBase64Strict(saltBase64, SALT_BYTES, "salt")
        val iv = decodeBase64Strict(ivBase64, IV_BYTES, "IV")

        val keyBytes = deriveKeyBytes(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw BackupException.WrongPasswordOrCorruptedException("Invalid password or tampered backup", e)
        } catch (e: Exception) {
            throw BackupException.WrongPasswordOrCorruptedException("Decryption failed", e)
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun decodeBase64Strict(encoded: String?, expectedLen: Int, label: String): ByteArray {
        val raw = encoded ?: throw BackupException.InvalidArchiveException("Missing $label")
        val decoded = try {
            Base64.getDecoder().decode(raw)
        } catch (e: IllegalArgumentException) {
            throw BackupException.InvalidArchiveException("Malformed $label", e)
        }
        if (decoded.size != expectedLen) {
            throw BackupException.InvalidArchiveException("Invalid $label length")
        }
        return decoded
    }

    private fun deriveKeyBytes(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
