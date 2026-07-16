package com.ryosoftware.calls_blocker.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.ryosoftware.calls_blocker.R

enum class Direction(val code: Int) {
    INCOMING(0),
    OUTGOING(1);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Direction =
            map[code] ?: INCOMING
    }
}

enum class Reason(val code: Int, val resource: Int? = 0) {
    NONE(0),
    WHITELISTED_NUMBER(11, R.string.reason_whitelisted_number),
    WHITELISTED_PREFIX(12, R.string.reason_whitelisted_prefix),
    BLOCK_ALL(21, R.string.reason_block_all),
    HIDDEN_NUMBER(31, R.string.reason_hidden),
    BLACKLISTED_NUMBER(41, R.string.reason_blacklisted_number),
    BLACKLISTED_PREFIX(42, R.string.reason_blacklisted_prefix),
    NOT_A_CONTACT(51, R.string.reason_unknown),
    MEMBER_OF_BLOCKED_GROUP_OF_CONTACTS(52, R.string.reason_group),
    INTERNATIONAL_NUMBER(61, R.string.reason_international),
    NOT_CALLED(71, R.string.reason_not_called),
    REJECTED_BEFORE(72, R.string.reason_rejected_before),
    REPEATED_CALL(73, R.string.reason_repeated_call),
    SCHEDULE(81, R.string.reason_schedule),
    FIND_MY_PHONE(91, R.string.reason_find_my_phone),
    FIND_MY_PHONE_CANCELLED(92, R.string.reason_find_my_phone_cancelled);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Reason =
            map[code] ?: NONE

        fun Reason.toString(context: Context) =
            if ((resource != null) && (resource != 0)) context.getString(resource) else null
    }
}

enum class NumberType(val code: Int, val resource: Int? = 0) {
    UNKNOWN(0),
    MOBILE(1, R.string.number_type_mobile),
    FIXED_LINE(2, R.string.number_type_fixed_line),
    FIXED_LINE_OR_MOBILE(3),
    VOIP(4, R.string.number_type_voip),
    TOLL_FREE(5, R.string.number_type_toll_free),
    PREMIUM_RATE(6, R.string.number_type_premium_rate),
    SHARED_COST(7, R.string.number_type_shared_cost),
    PERSONAL_NUMBER(8, R.string.number_type_personal_number),
    PAGER(9, R.string.number_type_pager),
    UAN(10, R.string.number_type_uan),
    VOICEMAIL(11, R.string.number_type_voicemail);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): NumberType =
            map[code] ?: UNKNOWN

        fun NumberType.toString(context: Context) =
            if ((resource != null) && (resource != 0)) context.getString(resource) else null
    }
}

const val FLAG_CALL_SILENCED     = 1 shl 0
const val FLAG_SKIP_CALL_LOG     = 1 shl 1
const val FLAG_SKIP_NOTIFICATION = 1 shl 2

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

class NumberTypeConverter {
    @TypeConverter
    fun fromNumberType(type: NumberType): Int =
        type.code

    @TypeConverter
    fun toNumberType(code: Int): NumberType =
        NumberType.fromCode(code)
}

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "number_type")
    val type: NumberType,
    @ColumnInfo(name = "direction")
    val direction: Direction = Direction.INCOMING,
    @ColumnInfo(name = "timestamp")
    val timeStamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "reason")
    val reason: Reason = Reason.NONE,
    @ColumnInfo(name = "flags")
    val flags: Int = 0,
)
