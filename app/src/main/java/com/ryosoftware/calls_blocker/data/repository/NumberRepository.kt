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
    private var blockedExactNumbers: HashSet<String> = HashSet()

    @Volatile
    private var blockedPrefixes: HashSet<String> = HashSet()

    @Volatile
    private var allowedExactNumbers: HashSet<String> = HashSet()

    @Volatile
    private var allowedPrefixes: HashSet<String> = HashSet()

    private val _prefixBlocks = MutableStateFlow<List<Number>>(emptyList())
    val prefixBlocks: Flow<List<Number>> = _prefixBlocks.asStateFlow()

    private val _manualBlocks = MutableStateFlow<List<Number>>(emptyList())
    val manualBlocks: Flow<List<Number>> = _manualBlocks.asStateFlow()

    private val _manualAllows = MutableStateFlow<List<Number>>(emptyList())
    val manualAllows: Flow<List<Number>> = _manualAllows.asStateFlow()

    private val _prefixAllows = MutableStateFlow<List<Number>>(emptyList())
    val prefixAllows: Flow<List<Number>> = _prefixAllows.asStateFlow()

    init {
        scope.launch {
            blockedExactNumbers = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK).toHashSet()
            blockedPrefixes = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_BLOCK).toHashSet()
            allowedExactNumbers = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_ALLOW).toHashSet()
            allowedPrefixes = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_ALLOW).toHashSet()
            loadLatch.countDown()
            loadAllLists()
        }
    }

    private suspend fun loadTypeToList(type: Type, action: Action = Action.ACTION_BLOCK): List<Number> {
        val result = mutableListOf<Number>()
        val batchSize = 500
        var offset = 0
        do {
            val batch = dao.getByTypeBatch(type, action, batchSize, offset)
            result.addAll(batch)
            offset += batchSize
        } while (batch.size == batchSize)
        return result
    }

    private suspend fun loadAllLists() {
        _manualBlocks.value = loadTypeToList(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK)
        _prefixBlocks.value = loadTypeToList(Type.PREFIX, Action.ACTION_BLOCK)
        _manualAllows.value = loadTypeToList(Type.EXACT_COINCIDENCE, Action.ACTION_ALLOW)
        _prefixAllows.value = loadTypeToList(Type.PREFIX, Action.ACTION_ALLOW)
    }

    val blockedNumbersCount: Flow<Int> = dao.getCountByAction(Action.ACTION_BLOCK)
    val allowedNumbersCount: Flow<Int> = dao.getCountByAction(Action.ACTION_ALLOW)

    suspend fun add(phoneNumber: String, description: String, type: Type, action: Action = Action.ACTION_BLOCK) {
        dao.insert(
            Number(
                phoneNumber = phoneNumber,
                description = description,
                type = type,
                action = action
            )
        )
        if (type == Type.EXACT_COINCIDENCE) {
            if (action == Action.ACTION_BLOCK) {
                blockedExactNumbers.add(phoneNumber)
                _manualBlocks.value = loadTypeToList(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK)
            } else {
                allowedExactNumbers.add(phoneNumber)
                _manualAllows.value = loadTypeToList(Type.EXACT_COINCIDENCE, Action.ACTION_ALLOW)
            }
        }
        else {
            if (action == Action.ACTION_BLOCK) {
                blockedPrefixes.add(phoneNumber)
                _prefixBlocks.value = loadTypeToList(Type.PREFIX, Action.ACTION_BLOCK)
            } else {
                allowedPrefixes.add(phoneNumber)
                _prefixAllows.value = loadTypeToList(Type.PREFIX, Action.ACTION_ALLOW)
            }
        }
    }

    suspend fun addAll(numbers: List<Pair<String, Type>>): Int {
        val beforeExact = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK).toHashSet()
        val beforePrefix = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_BLOCK).toHashSet()
        val entries = numbers.map { (phoneNumber, type) ->
            Number(phoneNumber = phoneNumber, description = "", type = type, action = Action.ACTION_BLOCK)
        }
        entries.chunked(500).forEach { chunk -> dao.insertAll(chunk) }
        blockedExactNumbers = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK).toHashSet()
        blockedPrefixes = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_BLOCK).toHashSet()
        loadAllLists()
        return (blockedExactNumbers.size + blockedPrefixes.size) - (beforeExact.size + beforePrefix.size)
    }

    suspend fun remove(number: Number) {
        dao.delete(number)
        if (number.type == Type.EXACT_COINCIDENCE) {
            if (number.action == Action.ACTION_BLOCK) {
                blockedExactNumbers.remove(number.phoneNumber)
                _manualBlocks.value = loadTypeToList(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK)
            } else {
                allowedExactNumbers.remove(number.phoneNumber)
                _manualAllows.value = loadTypeToList(Type.EXACT_COINCIDENCE, Action.ACTION_ALLOW)
            }
        }
        else {
            if (number.action == Action.ACTION_BLOCK) {
                blockedPrefixes.remove(number.phoneNumber)
                _prefixBlocks.value = loadTypeToList(Type.PREFIX, Action.ACTION_BLOCK)
            } else {
                allowedPrefixes.remove(number.phoneNumber)
                _prefixAllows.value = loadTypeToList(Type.PREFIX, Action.ACTION_ALLOW)
            }
        }
    }

    suspend fun removeByPhoneNumber(phoneNumber: String) {
        dao.deleteByPhoneNumber(phoneNumber)
        blockedExactNumbers = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK).toHashSet()
        blockedPrefixes = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_BLOCK).toHashSet()
        allowedExactNumbers = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_ALLOW).toHashSet()
        allowedPrefixes = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_ALLOW).toHashSet()
        loadAllLists()
    }

    suspend fun removeEntries(ids: List<Long>) {
        ids.chunked(500).forEach { chunk -> dao.deleteByIds(chunk) }
        blockedExactNumbers = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_BLOCK).toHashSet()
        blockedPrefixes = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_BLOCK).toHashSet()
        allowedExactNumbers = dao.getNumbersByTypeAndAction(Type.EXACT_COINCIDENCE, Action.ACTION_ALLOW).toHashSet()
        allowedPrefixes = dao.getNumbersByTypeAndAction(Type.PREFIX, Action.ACTION_ALLOW).toHashSet()
        loadAllLists()
    }

    fun isBlockedExact(phoneNumber: String): Boolean {
        loadLatch.await()
        return phoneNumber in blockedExactNumbers
    }

    fun isBlockedByPrefix(phoneNumber: String): Boolean {
        loadLatch.await()
        return blockedPrefixes.any { phoneNumber.startsWith(it) }
    }

    fun isAllowedExact(phoneNumber: String): Boolean {
        loadLatch.await()
        return phoneNumber in allowedExactNumbers
    }

    fun isAllowedByPrefix(phoneNumber: String): Boolean {
        loadLatch.await()
        return allowedPrefixes.any { phoneNumber.startsWith(it) }
    }

    fun isAddedExact(phoneNumber: String): Boolean {
        loadLatch.await()
        return (isBlockedExact(phoneNumber) || isAllowedExact(phoneNumber))
    }

    fun isAddedPrefix(prefix: String): Boolean {
        loadLatch.await()
        return (prefix in blockedPrefixes || prefix in allowedPrefixes)
    }
}
