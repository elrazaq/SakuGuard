package com.sakuguard.app.domain.repository

import com.sakuguard.app.domain.model.BackupExportResult
import com.sakuguard.app.domain.model.BackupInspectionResult
import com.sakuguard.app.domain.model.BackupRestoreResult

interface BackupRepository {
    suspend fun exportBackup(destinationUri: String, password: CharArray?): BackupExportResult
    suspend fun inspectBackup(sourceUri: String): BackupInspectionResult
    suspend fun restoreBackup(sourceUri: String, password: CharArray?): BackupRestoreResult
}
