package com.ryosoftware.calls_blocker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dismissed_block_suggestions")
data class BlockSuggestion(
    @PrimaryKey
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "dismissed_at")
    val dismissedAt: Long = System.currentTimeMillis(),
)
