package com.sakuguard.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BackupManifest
import com.sakuguard.app.domain.model.BackupPayload
import com.sakuguard.app.domain.model.BackupRecordCounts
import com.sakuguard.app.domain.model.SerializableWallet
import com.sakuguard.app.domain.model.WalletType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveCodecTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { encodeDefaults = true }

    private fun samplePayload() = BackupPayload(
        wallets = listOf(
            SerializableWallet("w1", "BCA", WalletType.BANK_ACCOUNT, 1_000_000L, 0L, createdAt = 0L, updatedAt = 0L),
        ),
        categories = emptyList(),
        goals = emptyList(),
        transactions = emptyList(),
        goalAllocations = emptyList(),
        budgets = emptyList(),
    )

    @Test
    fun `unencrypted and encrypted round-trip succeeds`() {
        val plainFile = tempFolder.newFile("plain.saku")
        BackupArchiveCodec.encodeToFile(samplePayload(), null, 1, "0.1.0", 1_700_000_000_000L, plainFile)
        val (m1, p1) = BackupArchiveCodec.decodePayloadFromFile(plainFile, null)
        assertThat(m1.encrypted).isFalse()
        assertThat(p1.wallets.single().id).isEqualTo("w1")

        val encFile = tempFolder.newFile("enc.saku")
        BackupArchiveCodec.encodeToFile(samplePayload(), "Secret#99".toCharArray(), 1, "0.1.0", 1_700_000_000_000L, encFile)
        assertThrows(BackupException.PasswordRequiredException::class.java) {
            BackupArchiveCodec.decodePayloadFromFile(encFile, null)
        }
        val (m2, p2) = BackupArchiveCodec.decodePayloadFromFile(encFile, "Secret#99".toCharArray())
        assertThat(m2.encrypted).isTrue()
        assertThat(p2.wallets.single().id).isEqualTo("w1")
    }

    @Test
    fun `path traversal, unexpected entries, and checksum mismatch are rejected`() {
        val traversalZip = tempFolder.newFile("traversal.saku")
        ZipOutputStream(FileOutputStream(traversalZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../manifest.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("payload.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }
        assertThrows(BackupException.InvalidArchiveException::class.java) {
            BackupArchiveCodec.inspectFile(traversalZip)
        }

        val badChecksumZip = tempFolder.newFile("badsha.saku")
        val payloadBytes = json.encodeToString(samplePayload()).toByteArray(StandardCharsets.UTF_8)
        val badManifest = BackupManifest(
            backupFormatVersion = 1,
            appVersionCode = 1,
            appVersionName = "0.1.0",
            databaseSchemaVersion = 2,
            createdAtEpochMillis = 100L,
            encrypted = false,
            payloadSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            recordCounts = BackupRecordCounts(1, 0, 0, 0, 0, 0),
        )
        writeZip(badChecksumZip, json.encodeToString(badManifest).toByteArray(), "payload.json", payloadBytes)
        assertThrows(BackupException.ChecksumMismatchException::class.java) {
            BackupArchiveCodec.decodePayloadFromFile(badChecksumZip, null)
        }
    }

    @Test
    fun `unsupported version and mismatched recordCounts are rejected`() {
        val futureFile = tempFolder.newFile("future.saku")
        val payloadBytes = json.encodeToString(samplePayload()).toByteArray(StandardCharsets.UTF_8)
        val futureManifest = BackupManifest(
            backupFormatVersion = 99,
            appVersionCode = 99,
            appVersionName = "9.0.0",
            databaseSchemaVersion = 2,
            createdAtEpochMillis = 100L,
            encrypted = false,
            payloadSha256 = BackupCrypto.sha256Hex(payloadBytes),
            recordCounts = BackupRecordCounts(1, 0, 0, 0, 0, 0),
        )
        writeZip(futureFile, json.encodeToString(futureManifest).toByteArray(), "payload.json", payloadBytes)
        assertThrows(BackupException.UnsupportedVersionException::class.java) {
            BackupArchiveCodec.inspectFile(futureFile)
        }

        val mismatchFile = tempFolder.newFile("mismatch.saku")
        val wrongCountManifest = futureManifest.copy(
            backupFormatVersion = 1,
            recordCounts = BackupRecordCounts(999, 0, 0, 0, 0, 0),
        )
        writeZip(mismatchFile, json.encodeToString(wrongCountManifest).toByteArray(), "payload.json", payloadBytes)
        assertThrows(BackupException.InvalidArchiveException::class.java) {
            BackupArchiveCodec.decodePayloadFromFile(mismatchFile, null)
        }
    }

    private fun writeZip(target: File, manifestBytes: ByteArray, payloadName: String, payloadBytes: ByteArray) {
        ZipOutputStream(FileOutputStream(target)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(payloadName))
            zos.write(payloadBytes)
            zos.closeEntry()
        }
    }
}
