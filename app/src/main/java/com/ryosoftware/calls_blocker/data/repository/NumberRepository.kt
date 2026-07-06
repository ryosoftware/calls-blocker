package com.ryosoftware.calls_blocker.data.repository

import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch

class NumberRepository(private val dao: NumberDao) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loadLatch = CountDownLatch(1)

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
        scope.launch {
            blockedIncomingExactNumbers = dao.getNumbersByType(Action.BLOCK, Type.EXACT_COINCIDENCE).toHashSet()
            blockedIncomingPrefixes = dao.getNumbersByType(Action.BLOCK, Type.PREFIX).toHashSet()
            allowedIncomingExactNumbers = dao.getNumbersByType(Action.ALLOW, Type.EXACT_COINCIDENCE).toHashSet()
            allowedIncomingPrefixes = dao.getNumbersByType(Action.ALLOW, Type.PREFIX).toHashSet()
            loadLatch.countDown()
            loadAllLists()
        }
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
        dao.insert(
            Number(
                phoneNumber = phoneNumber,
                description = description,
                action = action,
                type = type
            )
        )
        if (action == Action.BLOCK) {
            if (type == Type.EXACT_COINCIDENCE) {
                blockedIncomingExactNumbers.add(phoneNumber)
                _incomingExactBlocks.value = loadTypeToList(Action.BLOCK, Type.EXACT_COINCIDENCE)
            } else if (type == Type.PREFIX) {
                blockedIncomingPrefixes.add(phoneNumber)
                _incomingPrefixBlocks.value = loadTypeToList(Action.BLOCK, Type.PREFIX)
            }
        } else if (action == Action.ALLOW) {
            if (type == Type.EXACT_COINCIDENCE) {
                allowedIncomingExactNumbers.add(phoneNumber)
                _incomingExactAllows.value = loadTypeToList(Action.ALLOW, Type.EXACT_COINCIDENCE)
            } else if (type == Type.PREFIX) {
                allowedIncomingPrefixes.add(phoneNumber)
                _incomingPrefixAllows.value = loadTypeToList(Action.ALLOW, Type.PREFIX)
            }
        }
    }

    suspend fun addAll(numbers: List<Pair<String, Type>>): Int {
        val beforeExact = dao.getNumbersByType(Action.BLOCK, Type.EXACT_COINCIDENCE).toHashSet()
        val beforePrefix = dao.getNumbersByType(Action.BLOCK, Type.PREFIX).toHashSet()
        val entries = numbers.map { (phoneNumber, type) ->
            Number(phoneNumber = phoneNumber, description = "", action = Action.BLOCK, type = type)
        }
        entries.chunked(500).forEach { chunk -> dao.insertAll(chunk) }
        blockedIncomingExactNumbers = dao.getNumbersByType(Action.BLOCK, Type.EXACT_COINCIDENCE).toHashSet()
        blockedIncomingPrefixes = dao.getNumbersByType(Action.BLOCK, Type.PREFIX).toHashSet()
        loadAllLists()
        return (blockedIncomingExactNumbers.size + blockedIncomingPrefixes.size) - (beforeExact.size + beforePrefix.size)
    }

    suspend fun remove(number: Number) {
        dao.delete(number)
        if (number.action == Action.BLOCK) {
            if (number.type == Type.EXACT_COINCIDENCE) {
                blockedIncomingExactNumbers.remove(number.phoneNumber)
                _incomingExactBlocks.value =
                    loadTypeToList(Action.BLOCK, Type.EXACT_COINCIDENCE)
            } else if (number.type == Type.PREFIX) {
                blockedIncomingPrefixes.remove(number.phoneNumber)
                _incomingPrefixBlocks.value = loadTypeToList(Action.BLOCK, Type.PREFIX)
            }
        } else if (number.action == Action.ALLOW) {
            if (number.type == Type.EXACT_COINCIDENCE) {
                allowedIncomingExactNumbers.remove(number.phoneNumber)
                _incomingExactAllows.value = loadTypeToList(Action.ALLOW, Type.EXACT_COINCIDENCE)
            } else if (number.type == Type.PREFIX) {
                allowedIncomingPrefixes.remove(number.phoneNumber)
                _incomingPrefixAllows.value = loadTypeToList(Action.ALLOW, Type.PREFIX)
            }
        }
    }

    suspend fun updateDescription(number: Number) {
        dao.updateDescription(number.id, number.description)
        loadAllLists()
    }

    suspend fun removeByPhoneNumber(phoneNumber: String) {
        dao.deleteByPhoneNumber(phoneNumber,  Action.BLOCK)
        dao.deleteByPhoneNumber(phoneNumber, Action.ALLOW)
        blockedIncomingExactNumbers = dao.getNumbersByType(Action.BLOCK, Type.EXACT_COINCIDENCE).toHashSet()
        blockedIncomingPrefixes = dao.getNumbersByType(Action.BLOCK, Type.PREFIX).toHashSet()
        allowedIncomingExactNumbers = dao.getNumbersByType(Action.ALLOW, Type.EXACT_COINCIDENCE).toHashSet()
        allowedIncomingPrefixes = dao.getNumbersByType(Action.ALLOW, Type.PREFIX).toHashSet()
        loadAllLists()
    }

    suspend fun removeEntries(ids: List<Long>) {
        ids.chunked(500).forEach { chunk -> dao.deleteByIds(chunk) }
        blockedIncomingExactNumbers = dao.getNumbersByType(Action.BLOCK, Type.EXACT_COINCIDENCE).toHashSet()
        blockedIncomingPrefixes = dao.getNumbersByType(Action.BLOCK, Type.PREFIX).toHashSet()
        allowedIncomingExactNumbers = dao.getNumbersByType(Action.ALLOW, Type.EXACT_COINCIDENCE).toHashSet()
        allowedIncomingPrefixes = dao.getNumbersByType(Action.ALLOW, Type.PREFIX).toHashSet()
        loadAllLists()
    }

    fun isIncomingBlockedExact(phoneNumber: String): Boolean {
        loadLatch.await()
        return phoneNumber in blockedIncomingExactNumbers
    }

    fun isIncomingBlockedByPrefix(phoneNumber: String): Boolean {
        loadLatch.await()
        return blockedIncomingPrefixes.any { phoneNumber.startsWith(it) }
    }

    fun isIncomingAllowedExact(phoneNumber: String): Boolean {
        loadLatch.await()
        return phoneNumber in allowedIncomingExactNumbers
    }

    fun isIncomingAllowedByPrefix(phoneNumber: String): Boolean {
        loadLatch.await()
        return allowedIncomingPrefixes.any { phoneNumber.startsWith(it) }
    }

    fun isIncomingAddedExact(phoneNumber: String): Boolean {
        loadLatch.await()
        return (isIncomingBlockedExact(phoneNumber) || isIncomingAllowedExact(phoneNumber))
    }

    fun isIncomingAddedPrefix(prefix: String): Boolean {
        loadLatch.await()
        return (prefix in blockedIncomingPrefixes || prefix in allowedIncomingPrefixes)
    }
}
