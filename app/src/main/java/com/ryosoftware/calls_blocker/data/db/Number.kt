package com.ryosoftware.calls_blocker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class Type(val code: Int) {
    EXACT_COINCIDENCE(0),
    PREFIX(1);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Type =
            map[code] ?: EXACT_COINCIDENCE
    }
}

enum class Action(val code: Int) {
    ACTION_BLOCK(0),
    ACTION_ALLOW(1);

    companion object {
        private val map = entries.associateBy { it.code }

        fun fromCode(code: Int): Action =
            map[code] ?: ACTION_BLOCK
    }
}

class TypeConverter {
    @TypeConverter
    fun fromType(type: Type): Int =
        type.code

    @TypeConverter
    fun toType(code: Int): Type =
        Type.fromCode(code)
}

class ActionConverter {
    @TypeConverter
    fun fromAction(action: Action): Int =
        action.code

    @TypeConverter
    fun toAction(code: Int): Action =
        Action.fromCode(code)
}


@Entity(
    tableName = "numbers",
    indices = [Index(value = ["phone_number", "type", "action"], unique = true)]
)
data class Number(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    val description: String = "",
    @ColumnInfo(name = "type")
    val type: Type = Type.EXACT_COINCIDENCE,
    val action: Action = Action.ACTION_BLOCK,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
