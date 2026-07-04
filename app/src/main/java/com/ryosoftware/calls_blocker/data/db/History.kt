package com.ryosoftware.calls_blocker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class Reason(val code: Int) {
    REASON_NONE(0),
    REASON_WHITELISTED_NUMBER(1),
    REASON_WHITELISTED_PREFIX(2),
    REASON_FIND_MY_PHONE(3),
    REASON_HIDDEN_NUMBER(4),
    REASON_BLACKLISTED_NUMBER(5),
    REASON_BLACKLISTED_PREFIX(6),
    REASON_UNKNOWN_NUMBER(7),
    REASON_GROUP(8),
    REASON_INTERNATIONAL_NUMBER(9),
    REASON_NOT_CALLED(10),
    REASON_REJECTED_BEFORE(11),
    REASON_REPEATED_CALL(12),
    REASON_SCHEDULE(13);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Reason =
            map[code] ?: REASON_NONE
    }
}

class CallReasonConverter {
    @TypeConverter
    fun fromCallReason(reason: Reason): Int =
        reason.code

    @TypeConverter
    fun toCallReason(code: Int): Reason =
        Reason.fromCode(code)
}

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "timestamp")
    val timeStamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "reason")
    val reason: Reason = Reason.REASON_NONE,
)
