package com.sakuguard.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.BackupException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class BackupCryptoTest {

    private val aad = BackupCrypto.canonicalAad(
        backupFormatVersion = 1,
        databaseSchemaVersion = 2,
        createdAtEpochMillis = 1_700_000_000_000L,
        appVersionCode = 1,
        kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
        kdfIterations = 120_000,
    )

    @Test
    fun `encrypt and decrypt round-trip succeeds with exact plaintext`() {
        val plaintext = """{"wallets":[],"transactions":[]}""".toByteArray(StandardCharsets.UTF_8)
        val enc = BackupCrypto.encrypt(plaintext, "SakuPass123!".toCharArray(), aad, iterations = 120_000)

        val dec = BackupCrypto.decrypt(
            ciphertext = enc.ciphertext,
            password = "SakuPass123!".toCharArray(),
            kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
            kdfIterations = enc.kdfIterations,
            saltBase64 = enc.saltBase64,
            ivBase64 = enc.ivBase64,
            cipherAlgorithm = BackupCrypto.CIPHER_ALGORITHM,
            aad = aad,
        )

        assertThat(dec).isEqualTo(plaintext)
    }

    @Test
    fun `wrong password or tampered ciphertext or tampered AAD throws WrongPasswordOrCorruptedException`() {
        val plaintext = "secret ledger".toByteArray(StandardCharsets.UTF_8)
        val enc = BackupCrypto.encrypt(plaintext, "CorrectPass".toCharArray(), aad, iterations = 120_000)

        assertThrows(BackupException.WrongPasswordOrCorruptedException::class.java) {
            BackupCrypto.decrypt(
                ciphertext = enc.ciphertext,
                password = "WrongPass".toCharArray(),
                kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
                kdfIterations = enc.kdfIterations,
                saltBase64 = enc.saltBase64,
                ivBase64 = enc.ivBase64,
                cipherAlgorithm = BackupCrypto.CIPHER_ALGORITHM,
                aad = aad,
            )
        }

        val tampered = enc.ciphertext.copyOf().apply { this[0] = (this[0].toInt() xor 0xFF).toByte() }
        assertThrows(BackupException.WrongPasswordOrCorruptedException::class.java) {
            BackupCrypto.decrypt(
                ciphertext = tampered,
                password = "CorrectPass".toCharArray(),
                kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
                kdfIterations = enc.kdfIterations,
                saltBase64 = enc.saltBase64,
                ivBase64 = enc.ivBase64,
                cipherAlgorithm = BackupCrypto.CIPHER_ALGORITHM,
                aad = aad,
            )
        }

        val wrongAad = BackupCrypto.canonicalAad(1, 2, 999L, 1, BackupCrypto.KDF_ALGORITHM, 120_000)
        assertThrows(BackupException.WrongPasswordOrCorruptedException::class.java) {
            BackupCrypto.decrypt(
                ciphertext = enc.ciphertext,
                password = "CorrectPass".toCharArray(),
                kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
                kdfIterations = enc.kdfIterations,
                saltBase64 = enc.saltBase64,
                ivBase64 = enc.ivBase64,
                cipherAlgorithm = BackupCrypto.CIPHER_ALGORITHM,
                aad = wrongAad,
            )
        }
    }

    @Test
    fun `kdf iteration bounds and invalid algorithms are rejected`() {
        val enc = BackupCrypto.encrypt("data".toByteArray(), "Pass1".toCharArray(), aad, iterations = 120_000)

        assertThrows(BackupException.InvalidArchiveException::class.java) {
            BackupCrypto.decrypt(
                ciphertext = enc.ciphertext,
                password = "Pass1".toCharArray(),
                kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
                kdfIterations = 10_000, // below MIN_KDF_ITERATIONS
                saltBase64 = enc.saltBase64,
                ivBase64 = enc.ivBase64,
                cipherAlgorithm = BackupCrypto.CIPHER_ALGORITHM,
                aad = aad,
            )
        }

        assertThrows(BackupException.InvalidArchiveException::class.java) {
            BackupCrypto.decrypt(
                ciphertext = enc.ciphertext,
                password = "Pass1".toCharArray(),
                kdfAlgorithm = "PBKDF2WithHmacSHA1",
                kdfIterations = enc.kdfIterations,
                saltBase64 = enc.saltBase64,
                ivBase64 = enc.ivBase64,
                cipherAlgorithm = BackupCrypto.CIPHER_ALGORITHM,
                aad = aad,
            )
        }
    }
}
