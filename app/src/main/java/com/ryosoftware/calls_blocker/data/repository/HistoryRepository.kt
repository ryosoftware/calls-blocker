package com.ryosoftware.calls_blocker.data.repository

import com.ryosoftware.calls_blocker.data.db.HistoryDao
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {
    val allEntries: Flow<List<HistoryEntry>> = dao.getAll()

    suspend fun add(entry: HistoryEntry) = dao.insert(entry)

    suspend fun remove(entry: HistoryEntry) = dao.delete(entry)

    suspend fun removeEntries(ids: List<Long>) {
        ids.chunked(500).forEach { chunk -> dao.deleteByIds(chunk) }
    }

    suspend fun removeByPhoneNumber(phoneNumber: String) = dao.deleteByPhoneNumber(phoneNumber)

    suspend fun countByPhoneNumber(phoneNumber: String): Int = dao.countByPhoneNumber(phoneNumber)

    suspend fun clearAll() = dao.clearAll()
}
