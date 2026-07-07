package com.ryosoftware.calls_blocker.data.repository

import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import com.ryosoftware.calls_blocker.data.db.ScheduleRuleDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduleRuleRepository(private val dao: ScheduleRuleDao) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _rules = MutableStateFlow<List<ScheduleRule>>(emptyList())
    val rules: Flow<List<ScheduleRule>> = _rules.asStateFlow()

    init {
        scope.launch {
            dao.getAll().collect { _rules.value = it }
        }
    }
    suspend fun add(rule: ScheduleRule) = dao.insert(rule)

    suspend fun update(rule: ScheduleRule) = dao.update(rule)

    suspend fun remove(rule: ScheduleRule) = dao.delete(rule)

    fun isInScheduleBlock(): Boolean {
        val now = java.time.ZonedDateTime.now()
        val currentDay = now.dayOfWeek.value
        val currentMinute = now.toLocalTime().toSecondOfDay() / 60

        return _rules.value.any { rule ->
            val start = (rule.startDay - 1) * 1440 + rule.startMinute
            val end = (rule.endDay - 1) * 1440 + rule.endMinute
            val current = (currentDay - 1) * 1440 + currentMinute

            if (start < end) {
                current in start until end
            } else {
                current >= start || current < end
            }
        }
    }
}
