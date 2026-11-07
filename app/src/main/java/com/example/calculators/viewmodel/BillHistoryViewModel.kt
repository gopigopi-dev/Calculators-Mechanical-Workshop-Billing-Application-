package com.example.calculators.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calculators.data.BillEntity
import com.example.calculators.data.BillRepository
import com.example.calculators.data.BillWithItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Phase 10 Bill History screen.
 */
data class BillHistoryUiState(
    val allBills: List<BillWithItems> = emptyList(),
    val searchQuery: String = "",
    val dateFilter: String = "",
    val selectedBillDetail: BillWithItems? = null,
    val pendingDeleteBill: BillEntity? = null
) {
    val filteredBills: List<BillWithItems>
        get() = BillRepository.filterBills(
            bills = allBills,
            searchQuery = searchQuery,
            dateFilter = dateFilter
        )

    val availableDateKeys: List<String>
        get() = allBills.map { it.bill.dateKey }.distinct()
}

/**
 * Phase 10: ViewModel managing Bill History search, date filtering, bill detail inspection,
 * and confirmed bill deletion.
 */
class BillHistoryViewModel(
    private val repository: BillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillHistoryUiState())
    val uiState: StateFlow<BillHistoryUiState> = _uiState.asStateFlow()

    init {
        refreshHistory()
        viewModelScope.launch {
            repository.finalizedBillsFlow
                .catch { /* Ignore closed database exceptions during test sandbox teardown */ }
                .collectLatest { bills ->
                    _uiState.update { current ->
                        val updatedSelected = current.selectedBillDetail?.let { selected ->
                            bills.firstOrNull { it.bill.billId == selected.bill.billId }
                        }
                        current.copy(
                            allBills = bills,
                            selectedBillDetail = updatedSelected
                        )
                    }
                }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            try {
                val snapshot = repository.getFinalizedBillsSnapshot()
                _uiState.update { current ->
                    current.copy(allBills = snapshot)
                }
            } catch (_: Exception) {
                // Ignore if database is closing
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            current.copy(searchQuery = query)
        }
    }

    fun onDateFilterChanged(dateFilter: String) {
        _uiState.update { current ->
            current.copy(dateFilter = dateFilter)
        }
    }

    fun clearFilters() {
        _uiState.update { current ->
            current.copy(
                searchQuery = "",
                dateFilter = ""
            )
        }
    }

    fun openBillDetail(billId: String) {
        val target = _uiState.value.allBills.firstOrNull { it.bill.billId == billId }
        if (target != null) {
            _uiState.update { current ->
                current.copy(selectedBillDetail = target)
            }
        } else {
            viewModelScope.launch {
                val loaded = repository.getBillWithItemsById(billId)
                _uiState.update { current ->
                    current.copy(selectedBillDetail = loaded)
                }
            }
        }
    }

    fun closeBillDetail() {
        _uiState.update { current ->
            current.copy(selectedBillDetail = null)
        }
    }

    fun requestDeleteBill(bill: BillEntity) {
        _uiState.update { current ->
            current.copy(pendingDeleteBill = bill)
        }
    }

    fun cancelDeleteBill() {
        _uiState.update { current ->
            current.copy(pendingDeleteBill = null)
        }
    }

    fun confirmDeleteBill() {
        val target = _uiState.value.pendingDeleteBill ?: return
        viewModelScope.launch {
            repository.deleteBill(target.billId)
            val snapshot = repository.getFinalizedBillsSnapshot()
            _uiState.update { current ->
                current.copy(
                    allBills = snapshot,
                    pendingDeleteBill = null,
                    selectedBillDetail = if (current.selectedBillDetail?.bill?.billId == target.billId) {
                        null
                    } else {
                        current.selectedBillDetail
                    }
                )
            }
        }
    }

    companion object {
        fun provideFactory(repository: BillRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BillHistoryViewModel(repository) as T
                }
            }
        }
    }
}
