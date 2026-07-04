package com.ryosoftware.calls_blocker.data.repository

import com.ryosoftware.calls_blocker.data.db.BlockSuggestionDao

class BlockSuggestionsRepository(private val dao: BlockSuggestionDao) {
    suspend fun isAdded(phoneNumber: String): Boolean =
        dao.count(phoneNumber) != 0
}