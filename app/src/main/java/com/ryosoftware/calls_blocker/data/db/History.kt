package com.ryosoftware.calls_blocker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class Direction(val code: Int) {
    INCOMING(0),
    OUTGOING(1);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Direction =
            map[code] ?: INCOMING
    }
}

enum class Reason(val code: Int) {
    NONE(0),
    WHITELISTED_NUMBER(11),
    WHITELISTED_PREFIX(12),
    BLOCK_ALL(21),
    HIDDEN_NUMBER(31),
    BLACKLISTED_NUMBER(41),
    BLACKLISTED_PREFIX(42),
    NOT_A_CONTACT(51),
    MEMBER_OF_BLOCKED_GROUP_OF_CONTACTS(52),
    INTERNATIONAL_NUMBER(61),
    NOT_CALLED(71),
    REJECTED_BEFORE(72),
    REPEATED_CALL(73),
    SCHEDULE(81),
    FIND_MY_PHONE(91),
    FIND_MY_PHONE_CANCELLED(92);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Reason =
            map[code] ?: NONE
    }
}

class DirectionConverter {
    @TypeConverter
    fun fromDirection(direction: Direction): Int =
        direction.code

    @TypeConverter
    fun toDirection(code: Int): Direction =
        Direction.fromCode(code)
}

class ReasonConverter {
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
    @ColumnInfo(name = "direction")
    val direction: Direction = Direction.INCOMING,
    @ColumnInfo(name = "timestamp")
    val timeStamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "reason")
    val reason: Reason = Reason.NONE,
)
