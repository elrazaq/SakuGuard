package com.sakuguard.app.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.sakuguard.app.BuildConfig
import com.sakuguard.app.core.database.SakuGuardDatabase
import com.sakuguard.app.core.database.dao.BudgetDao
import com.sakuguard.app.core.database.dao.CategoryDao
import com.sakuguard.app.core.database.dao.GoalAllocationDao
import com.sakuguard.app.core.database.dao.GoalDao
import com.sakuguard.app.core.database.dao.TransactionDao
import com.sakuguard.app.core.database.dao.WalletDao
import com.sakuguard.app.data.mapper.toDomain
import com.sakuguard.app.data.mapper.toEntity
import com.sakuguard.app.domain.model.BackupException
import com.sakuguard.app.domain.model.BackupExportResult
import com.sakuguard.app.domain.model.BackupInspectionResult
import com.sakuguard.app.domain.model.BackupPayload
import com.sakuguard.app.domain.model.BackupRestoreResult
import com.sakuguard.app.domain.model.toDomain
import com.sakuguard.app.domain.model.toSerializable
import com.sakuguard.app.domain.repository.BackupRepository
import com.sakuguard.app.domain.usecase.BackupValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SakuGuardDatabase,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao,
    private val goalAllocationDao: GoalAllocationDao,
    private val budgetDao: BudgetDao,
) : BackupRepository {

    companion object {
        private const val INSERT_BATCH_SIZE = 500
    }

    override suspend fun exportBackup(
        destinationUri: String,
        password: CharArray?,
    ): BackupExportResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "saku_backup_tmp").apply { mkdirs() }
        val tempFile = File(tempDir, "export_${UUID.randomUUID()}.saku")
        val uri = Uri.parse(destinationUri)

        try {
            coroutineContext.ensureActive()
            val payload = database.withTransaction {
                BackupPayload(
                    wallets = walletDao.getAllWallets().map { it.toDomain().toSerializable() },
                    categories = categoryDao.getAllCategories().map { it.toDomain().toSerializable() },
                    goals = goalDao.getAllGoals().map { it.toDomain().toSerializable() },
                    transactions = transactionDao.getAllTransactions().map { it.toDomain().toSerializable() },
                    goalAllocations = goalAllocationDao.getAllAllocations().map { it.toDomain().toSerializable() },
                    budgets = budgetDao.getAllBudgets().map { it.toDomain().toSerializable() },
                )
            }

            coroutineContext.ensureActive()
            val now = System.currentTimeMillis()
            val manifest = BackupArchiveCodec.encodeToFile(
                payload = payload,
                password = password,
                appVersionCode = BuildConfig.VERSION_CODE,
                appVersionName = BuildConfig.VERSION_NAME,
                createdAtEpochMillis = now,
                outputFile = tempFile,
            )

            coroutineContext.ensureActive()
            try {
                val outStream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw BackupException.IoFailureException("Cannot open destination output stream")
                outStream.use { output ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            } catch (e: BackupException) {
                cleanupDestinationBestEffort(uri)
                throw e
            } catch (e: Exception) {
                cleanupDestinationBestEffort(uri)
                throw BackupException.IoFailureException("Failed to write backup to destination", e)
            }

            BackupExportResult(
                createdAtEpochMillis = manifest.createdAtEpochMillis,
                recordCounts = manifest.recordCounts,
                encrypted = manifest.encrypted,
            )
        } finally {
            tempFile.delete()
            password?.fill('\u0000')
        }
    }

    override suspend fun inspectBackup(sourceUri: String): BackupInspectionResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "saku_backup_tmp").apply { mkdirs() }
        val tempFile = File(tempDir, "inspect_${UUID.randomUUID()}.saku")
        try {
            copyUriToBoundedTempFile(Uri.parse(sourceUri), tempFile)
            val manifest = BackupArchiveCodec.inspectFile(tempFile)
            BackupInspectionResult(manifest = manifest)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun restoreBackup(
        sourceUri: String,
        password: CharArray?,
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "saku_backup_tmp").apply { mkdirs() }
        val tempFile = File(tempDir, "restore_${UUID.randomUUID()}.saku")
        try {
            coroutineContext.ensureActive()
            copyUriToBoundedTempFile(Uri.parse(sourceUri), tempFile)

            coroutineContext.ensureActive()
            val (manifest, payload) = BackupArchiveCodec.decodePayloadFromFile(tempFile, password)

            coroutineContext.ensureActive()
            BackupValidator.validate(payload)

            val wallets = payload.wallets.map { it.toDomain().toEntity() }
            val categories = payload.categories.map { it.toDomain().toEntity() }
            val goals = payload.goals.map { it.toDomain().toEntity() }
            val transactions = payload.transactions.map { it.toDomain().toEntity() }
            val allocations = payload.goalAllocations.map { it.toDomain().toEntity() }
            val budgets = payload.budgets.map { it.toDomain().toEntity() }

            database.withTransaction {
                coroutineContext.ensureActive()
                // Delete in reverse foreign-key order
                goalAllocationDao.deleteAll()
                budgetDao.deleteAll()
                transactionDao.deleteAll()
                goalDao.deleteAll()
                categoryDao.deleteAll()
                walletDao.deleteAll()

                // Insert in forward foreign-key order in bounded chunks
                wallets.chunked(INSERT_BATCH_SIZE).forEach {
                    coroutineContext.ensureActive()
                    walletDao.insertAll(it)
                }
                categories.chunked(INSERT_BATCH_SIZE).forEach {
                    coroutineContext.ensureActive()
                    categoryDao.insertAll(it)
                }
                goals.chunked(INSERT_BATCH_SIZE).forEach {
                    coroutineContext.ensureActive()
                    goalDao.insertAll(it)
                }
                transactions.chunked(INSERT_BATCH_SIZE).forEach {
                    coroutineContext.ensureActive()
                    transactionDao.insertAll(it)
                }
                allocations.chunked(INSERT_BATCH_SIZE).forEach {
                    coroutineContext.ensureActive()
                    goalAllocationDao.insertAll(it)
                }
                budgets.chunked(INSERT_BATCH_SIZE).forEach {
                    coroutineContext.ensureActive()
                    budgetDao.insertAll(it)
                }
            }

            BackupRestoreResult(
                recordCounts = manifest.recordCounts,
                createdAtEpochMillis = manifest.createdAtEpochMillis,
            )
        } finally {
            tempFile.delete()
            password?.fill('\u0000')
        }
    }

    private suspend fun copyUriToBoundedTempFile(uri: Uri, targetFile: File) {
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            throw BackupException.IoFailureException("Cannot open selected backup file", e)
        } ?: throw BackupException.IoFailureException("Cannot open selected backup file")

        input.use { stream ->
            FileOutputStream(targetFile).use { out ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > BackupArchiveCodec.MAX_ARCHIVE_BYTES) {
                        throw BackupException.SizeLimitExceededException("Backup archive exceeds 64 MB limit")
                    }
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
        }
    }

    private fun cleanupDestinationBestEffort(uri: Uri) {
        runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    }
}
