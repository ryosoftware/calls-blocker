package com.ryosoftware.calls_blocker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class Action(val code: Int) {
    BLOCK(1),
    ALLOW(2);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Action =
            map[code] ?: BLOCK
    }
}

enum class Type(val code: Int) {
    EXACT_COINCIDENCE(1),
    PREFIX(2);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Type =
            map[code] ?: EXACT_COINCIDENCE
    }
}

class ActionConverter {
    @TypeConverter
    fun fromAction(action: Action): Int =
        action.code

    @TypeConverter
    fun toAction(code: Int): Action =
        Action.fromCode(code)
}

class TypeConverter {
    @TypeConverter
    fun fromType(type: Type): Int =
        type.code

    @TypeConverter
    fun toType(code: Int): Type =
        Type.fromCode(code)
}

@Entity(
    tableName = "numbers",
    indices = [Index(value = ["phone_number", "action", "type"], unique = true)]
)
data class Number(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "description")
    val description: String = "",
    @ColumnInfo(name = "action")
    val action: Action,
    @ColumnInfo(name = "type")
    val type: Type = Type.EXACT_COINCIDENCE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
