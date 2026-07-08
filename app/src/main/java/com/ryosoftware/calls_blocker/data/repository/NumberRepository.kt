package com.ryosoftware.calls_blocker.data.repository

import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

class NumberRepository(private val dao: NumberDao) {
    @Volatile
    private var blockedIncomingExactNumbers: HashSet<String> = HashSet()

    @Volatile
    private var blockedIncomingPrefixes: HashSet<String> = HashSet()

    @Volatile
    private var allowedIncomingExactNumbers: HashSet<String> = HashSet()

    @Volatile
    private var allowedIncomingPrefixes: HashSet<String> = HashSet()

    private val _incomingExactBlocks = MutableStateFlow<List<Number>>(emptyList())
    val incomingExactBlocks: Flow<List<Number>> = _incomingExactBlocks.asStateFlow()

    private val _incomingPrefixBlocks = MutableStateFlow<List<Number>>(emptyList())
    val incomingPrefixBlocks: Flow<List<Number>> = _incomingPrefixBlocks.asStateFlow()

    private val _incomingExactAllows = MutableStateFlow<List<Number>>(emptyList())
    val incomingExactAllows: Flow<List<Number>> = _incomingExactAllows.asStateFlow()

    private val _incomingPrefixAllows = MutableStateFlow<List<Number>>(emptyList())
    val incomingPrefixAllows: Flow<List<Number>> = _incomingPrefixAllows.asStateFlow()

    init {
        runBlocking(Dispatchers.IO) {
            reloadSets()
            loadAllLists()
        }
    }

    private suspend fun reloadSets() {
        blockedIncomingExactNumbers = dao.getNumbersByType(Action.BLOCK, Type.EXACT_COINCIDENCE).toHashSet()
        blockedIncomingPrefixes = dao.getNumbersByType(Action.BLOCK, Type.PREFIX).toHashSet()
        allowedIncomingExactNumbers = dao.getNumbersByType(Action.ALLOW, Type.EXACT_COINCIDENCE).toHashSet()
        allowedIncomingPrefixes = dao.getNumbersByType(Action.ALLOW, Type.PREFIX).toHashSet()
    }

    private suspend fun loadTypeToList(action: Action, type: Type): List<Number> {
        val result = mutableListOf<Number>()
        val batchSize = 500
        var offset = 0
        do {
            val batch = dao.getByTypeBatch(action, type, batchSize, offset)
            result.addAll(batch)
            offset += batchSize
        } while (batch.size == batchSize)
        return result
    }

    private suspend fun loadAllLists() {
        _incomingExactBlocks.value = loadTypeToList(Action.BLOCK, Type.EXACT_COINCIDENCE)
        _incomingPrefixBlocks.value = loadTypeToList(Action.BLOCK, Type.PREFIX)
        _incomingExactAllows.value = loadTypeToList(Action.ALLOW, Type.EXACT_COINCIDENCE)
        _incomingPrefixAllows.value = loadTypeToList(Action.ALLOW, Type.PREFIX)
    }

    val blockedNumbersCount: Flow<Int> = dao.getCountByAction(Action.BLOCK)
    val allowedNumbersCount: Flow<Int> = dao.getCountByAction(Action.ALLOW)

    suspend fun add(phoneNumber: String, description: String, action: Action, type: Type) {
        dao.insert(Number(phoneNumber = phoneNumber, description = description, action = action, type = type))
        reloadSets()
        loadAllLists()
    }

    suspend fun addAll(numbers: List<Triple<String, Type, String?>>): Int {
        val before = blockedIncomingExactNumbers.size + blockedIncomingPrefixes.size
        val entries = numbers.map { (phoneNumber, type, description) ->
            Number(phoneNumber = phoneNumber, description = description ?: "", action = Action.BLOCK, type = type)
        }
        entries.chunked(500).forEach { chunk -> dao.insertAll(chunk) }
        reloadSets()
        loadAllLists()
        return (blockedIncomingExactNumbers.size + blockedIncomingPrefixes.size) - before
    }

    suspend fun remove(number: Number) {
        dao.delete(number)
        reloadSets()
        loadAllLists()
    }

    suspend fun updateDescription(number: Number) {
        dao.updateDescription(number.id, number.description)
        loadAllLists()
    }

    suspend fun removeByPhoneNumber(phoneNumber: String) {
        dao.deleteByPhoneNumber(phoneNumber, Action.BLOCK)
        dao.deleteByPhoneNumber(phoneNumber, Action.ALLOW)
        reloadSets()
        loadAllLists()
    }

    suspend fun removeEntries(ids: List<Long>) {
        ids.chunked(500).forEach { chunk -> dao.deleteByIds(chunk) }
        reloadSets()
        loadAllLists()
    }

    fun isIncomingBlockedExact(phoneNumber: String): Boolean =
        phoneNumber in blockedIncomingExactNumbers

    fun isIncomingBlockedByPrefix(phoneNumber: String): Boolean =
        blockedIncomingPrefixes.any { phoneNumber.startsWith(it) }

    fun isIncomingAllowedExact(phoneNumber: String): Boolean =
        phoneNumber in allowedIncomingExactNumbers

    fun isIncomingAllowedByPrefix(phoneNumber: String): Boolean =
        allowedIncomingPrefixes.any { phoneNumber.startsWith(it) }

    fun isIncomingAddedExact(phoneNumber: String): Boolean =
        isIncomingBlockedExact(phoneNumber) || isIncomingAllowedExact(phoneNumber)

    fun isIncomingAddedPrefix(prefix: String): Boolean =
        prefix in blockedIncomingPrefixes || prefix in allowedIncomingPrefixes
}
