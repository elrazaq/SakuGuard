package com.sakuguard.app.data.backup

import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BackupManifest
import com.sakuguard.app.domain.model.BackupPayload
import com.sakuguard.app.domain.model.BackupRecordCounts
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupArchiveCodec {

    const val BACKUP_FORMAT_VERSION = 1
    const val SUPPORTED_DB_SCHEMA_VERSION = 2

    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_PAYLOAD_PLAIN = "payload.json"
    const val ENTRY_PAYLOAD_ENCRYPTED = "payload.enc"

    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_PAYLOAD_BYTES = 128 * 1024 * 1024
    const val MAX_ENTRY_COUNT = 2
    const val MAX_COMPRESSION_RATIO = 50L
    const val RATIO_ENFORCEMENT_MIN_BYTES = 1024L * 1024L

    private val ALLOWED_ENTRIES = setOf(ENTRY_MANIFEST, ENTRY_PAYLOAD_PLAIN, ENTRY_PAYLOAD_ENCRYPTED)

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encodeToFile(
        payload: BackupPayload,
        password: CharArray?,
        appVersionCode: Int,
        appVersionName: String,
        createdAtEpochMillis: Long,
        outputFile: File,
    ): BackupManifest {
        val plainBytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        if (plainBytes.size > MAX_PAYLOAD_BYTES) {
            throw BackupException.SizeLimitExceededException("Backup payload exceeds maximum allowed size")
        }

        val counts = BackupRecordCounts(
            wallets = payload.wallets.size,
            categories = payload.categories.size,
            goals = payload.goals.size,
            transactions = payload.transactions.size,
            goalAllocations = payload.goalAllocations.size,
            budgets = payload.budgets.size,
        )

        val useEncryption = password != null && password.isNotEmpty()
        val payloadEntryName: String
        val payloadBytes: ByteArray
        val manifest: BackupManifest

        if (useEncryption) {
            val aad = BackupCrypto.canonicalAad(
                backupFormatVersion = BACKUP_FORMAT_VERSION,
                databaseSchemaVersion = SUPPORTED_DB_SCHEMA_VERSION,
                createdAtEpochMillis = createdAtEpochMillis,
                appVersionCode = appVersionCode,
                kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
                kdfIterations = BackupCrypto.DEFAULT_KDF_ITERATIONS,
            )
            val encrypted = BackupCrypto.encrypt(plainBytes, password!!, aad)
            plainBytes.fill(0)
            payloadEntryName = ENTRY_PAYLOAD_ENCRYPTED
            payloadBytes = encrypted.ciphertext
            manifest = BackupManifest(
                backupFormatVersion = BACKUP_FORMAT_VERSION,
                appVersionCode = appVersionCode,
                appVersionName = appVersionName,
                databaseSchemaVersion = SUPPORTED_DB_SCHEMA_VERSION,
                createdAtEpochMillis = createdAtEpochMillis,
                encrypted = true,
                kdfAlgorithm = BackupCrypto.KDF_ALGORITHM,
                kdfIterations = encrypted.kdfIterations,
                saltBase64 = encrypted.saltBase64,
                ivBase64 = encrypted.ivBase64,
                cipherAlgorithm = BackupCrypto.CIPHER_ALGORITHM,
                payloadSha256 = BackupCrypto.sha256Hex(payloadBytes),
                recordCounts = counts,
            )
        } else {
            payloadEntryName = ENTRY_PAYLOAD_PLAIN
            payloadBytes = plainBytes
            manifest = BackupManifest(
                backupFormatVersion = BACKUP_FORMAT_VERSION,
                appVersionCode = appVersionCode,
                appVersionName = appVersionName,
                databaseSchemaVersion = SUPPORTED_DB_SCHEMA_VERSION,
                createdAtEpochMillis = createdAtEpochMillis,
                encrypted = false,
                payloadSha256 = BackupCrypto.sha256Hex(payloadBytes),
                recordCounts = counts,
            )
        }

        val manifestBytes = json.encodeToString(manifest).toByteArray(StandardCharsets.UTF_8)
        if (manifestBytes.size > MAX_MANIFEST_BYTES) {
            throw BackupException.SizeLimitExceededException("Backup manifest exceeds maximum allowed size")
        }

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zos.write(manifestBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry(payloadEntryName))
            zos.write(payloadBytes)
            zos.closeEntry()
        }

        if (outputFile.length() > MAX_ARCHIVE_BYTES) {
            throw BackupException.SizeLimitExceededException("Backup archive exceeds maximum allowed size")
        }

        // Self-verify written archive before returning
        inspectFile(outputFile)
        return manifest
    }

    fun inspectFile(archiveFile: File): BackupManifest {
        val len = archiveFile.length()
        if (len <= 0L) {
            throw BackupException.InvalidArchiveException("Backup file is empty")
        }
        if (len > MAX_ARCHIVE_BYTES) {
            throw BackupException.SizeLimitExceededException("Backup archive exceeds 64 MB limit")
        }

        return try {
            ZipFile(archiveFile).use { zip ->
                val entries = validateArchiveStructure(zip)
                val manifestEntry = entries.getValue(ENTRY_MANIFEST)
                val manifestBytes = readBoundedEntry(zip, manifestEntry, MAX_MANIFEST_BYTES)
                val manifest = parseManifest(manifestBytes)
                validateCompatibility(manifest)

                val expectedPayloadEntry = if (manifest.encrypted) ENTRY_PAYLOAD_ENCRYPTED else ENTRY_PAYLOAD_PLAIN
                if (!entries.containsKey(expectedPayloadEntry)) {
                    throw BackupException.InvalidArchiveException("Archive payload entry mismatch")
                }
                manifest
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException.InvalidArchiveException("Malformed .saku archive", e)
        }
    }

    fun decodePayloadFromFile(
        archiveFile: File,
        password: CharArray?,
    ): Pair<BackupManifest, BackupPayload> {
        val manifest = inspectFile(archiveFile)
        if (manifest.encrypted && (password == null || password.isEmpty())) {
            throw BackupException.PasswordRequiredException("Password required to restore encrypted backup")
        }

        return try {
            ZipFile(archiveFile).use { zip ->
                val entries = validateArchiveStructure(zip)
                val payloadEntryName = if (manifest.encrypted) ENTRY_PAYLOAD_ENCRYPTED else ENTRY_PAYLOAD_PLAIN
                val payloadEntry = entries.getValue(payloadEntryName)
                val rawBytes = readBoundedEntry(zip, payloadEntry, MAX_PAYLOAD_BYTES)

                val actualSha = BackupCrypto.sha256Hex(rawBytes)
                if (!actualSha.equals(manifest.payloadSha256, ignoreCase = true)) {
                    throw BackupException.ChecksumMismatchException("Backup checksum mismatch")
                }

                val jsonBytes = if (manifest.encrypted) {
                    val aad = BackupCrypto.canonicalAad(
                        backupFormatVersion = manifest.backupFormatVersion,
                        databaseSchemaVersion = manifest.databaseSchemaVersion,
                        createdAtEpochMillis = manifest.createdAtEpochMillis,
                        appVersionCode = manifest.appVersionCode,
                        kdfAlgorithm = manifest.kdfAlgorithm,
                        kdfIterations = manifest.kdfIterations,
                    )
                    BackupCrypto.decrypt(
                        ciphertext = rawBytes,
                        password = password!!,
                        kdfAlgorithm = manifest.kdfAlgorithm,
                        kdfIterations = manifest.kdfIterations,
                        saltBase64 = manifest.saltBase64,
                        ivBase64 = manifest.ivBase64,
                        cipherAlgorithm = manifest.cipherAlgorithm,
                        aad = aad,
                    )
                } else {
                    rawBytes
                }

                if (jsonBytes.size > MAX_PAYLOAD_BYTES) {
                    throw BackupException.SizeLimitExceededException("Decrypted payload exceeds maximum size")
                }

                val payload = try {
                    json.decodeFromString<BackupPayload>(String(jsonBytes, StandardCharsets.UTF_8))
                } finally {
                    if (manifest.encrypted) jsonBytes.fill(0)
                }

                validateManifestCounts(manifest.recordCounts, payload)
                manifest to payload
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException.InvalidArchiveException("Failed to decode backup payload", e)
        }
    }

    private fun validateArchiveStructure(zip: ZipFile): Map<String, ZipEntry> {
        val seen = linkedMapOf<String, ZipEntry>()
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            val name = entry.name
            if (entry.isDirectory || !isSafeEntryName(name)) {
                throw BackupException.InvalidArchiveException("Unexpected or unsafe archive entry: $name")
            }
            if (name !in ALLOWED_ENTRIES) {
                throw BackupException.InvalidArchiveException("Unexpected archive entry: $name")
            }
            if (seen.containsKey(name)) {
                throw BackupException.InvalidArchiveException("Duplicate archive entry: $name")
            }
            seen[name] = entry
            if (seen.size > MAX_ENTRY_COUNT) {
                throw BackupException.InvalidArchiveException("Archive contains too many entries")
            }
        }

        if (seen.size != MAX_ENTRY_COUNT || !seen.containsKey(ENTRY_MANIFEST)) {
            throw BackupException.InvalidArchiveException("Invalid archive structure")
        }
        val hasPlain = seen.containsKey(ENTRY_PAYLOAD_PLAIN)
        val hasEnc = seen.containsKey(ENTRY_PAYLOAD_ENCRYPTED)
        if (hasPlain == hasEnc) {
            throw BackupException.InvalidArchiveException("Archive must contain exactly one payload entry")
        }
        return seen
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name != name.trim()) return false
        if (name.contains("..") || name.contains('/') || name.contains('\\')) return false
        return name.none { it.code < 32 }
    }

    private fun readBoundedEntry(zip: ZipFile, entry: ZipEntry, maxBytes: Int): ByteArray {
        val declaredSize = entry.size
        if (declaredSize > maxBytes.toLong()) {
            throw BackupException.SizeLimitExceededException("Archive entry exceeds maximum allowed size")
        }
        val compressedSize = entry.compressedSize
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0L

        zip.getInputStream(entry).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                totalRead += read
                if (totalRead > maxBytes.toLong()) {
                    throw BackupException.SizeLimitExceededException("Decompressed entry exceeds maximum allowed size")
                }
                if (compressedSize > 0L &&
                    totalRead > RATIO_ENFORCEMENT_MIN_BYTES &&
                    totalRead > compressedSize * MAX_COMPRESSION_RATIO
                ) {
                    throw BackupException.InvalidArchiveException("Suspicious compression ratio detected")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun parseManifest(bytes: ByteArray): BackupManifest = try {
        json.decodeFromString<BackupManifest>(String(bytes, StandardCharsets.UTF_8))
    } catch (e: Exception) {
        throw BackupException.InvalidArchiveException("Malformed manifest.json", e)
    }

    private fun validateCompatibility(manifest: BackupManifest) {
        if (manifest.backupFormatVersion != BACKUP_FORMAT_VERSION ||
            manifest.databaseSchemaVersion != SUPPORTED_DB_SCHEMA_VERSION
        ) {
            throw BackupException.UnsupportedVersionException(
                "Unsupported backup format (${manifest.backupFormatVersion}) or schema (${manifest.databaseSchemaVersion})",
            )
        }
    }

    private fun validateManifestCounts(counts: BackupRecordCounts, payload: BackupPayload) {
        if (counts.wallets != payload.wallets.size ||
            counts.categories != payload.categories.size ||
            counts.goals != payload.goals.size ||
            counts.transactions != payload.transactions.size ||
            counts.goalAllocations != payload.goalAllocations.size ||
            counts.budgets != payload.budgets.size
        ) {
            throw BackupException.InvalidArchiveException("Manifest record counts do not match payload")
        }
    }
}
