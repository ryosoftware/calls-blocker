package com.ryosoftware.calls_blocker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.CountryNameProvider
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NumbersViewModel @Inject constructor(
    private val repo: NumberRepository,
    private val countryNameProvider: CountryNameProvider,
    val logger: Logger,
) : ViewModel() {
    val blockedNumbersCount: StateFlow<Int> = repo.blockedNumbersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allowedNumbersCount: StateFlow<Int> = repo.allowedNumbersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val incomingExactBlocks: StateFlow<List<Number>> = repo.incomingExactBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingPrefixBlocks: StateFlow<List<Number>> = repo.incomingPrefixBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingExactAllows: StateFlow<List<Number>> = repo.incomingExactAllows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingPrefixAllows: StateFlow<List<Number>> = repo.incomingPrefixAllows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    sealed class AddNumberError {
        data object DuplicateExact : AddNumberError()
        data object DuplicatePrefix : AddNumberError()
    }

    private val _addNumberError = MutableStateFlow<AddNumberError?>(null)
    val addNumberError: StateFlow<AddNumberError?> = _addNumberError.asStateFlow()

    fun addNumber(phoneNumber: String, description: String, action: Action, type: Type): Boolean {
        if (type == Type.EXACT_COINCIDENCE) {
            val isDuplicate = repo.isIncomingAddedExact(phoneNumber)
            if (isDuplicate) {
                _addNumberError.value = AddNumberError.DuplicateExact
                return false
            }
        } else if (type == Type.PREFIX) {
            val isDuplicate = repo.isIncomingAddedPrefix(phoneNumber)
            if (isDuplicate) {
                _addNumberError.value = AddNumberError.DuplicatePrefix
                return false
            }
        }
        _addNumberError.value = null
        viewModelScope.launch {
            repo.add(phoneNumber, description, action, type)
        }
        return true
    }

    fun clearAddNumberError() {
        _addNumberError.value = null
    }

    suspend fun addAll(numbers: List<Triple<String, Type, String?>>): Int = repo.addAll(numbers)

    fun removeEntries(ids: List<Long>) {
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                repo.removeEntries(ids)
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun removeNumber(number: Number) {
        viewModelScope.launch {
            repo.remove(number)
        }
    }

    fun updateDescription(number: Number) {
        viewModelScope.launch {
            repo.updateDescription(number)
        }
    }

    fun getCountryName(country: Country): String =
        countryNameProvider.get(country)
}
