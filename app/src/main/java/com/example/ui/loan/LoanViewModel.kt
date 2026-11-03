package com.example.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Loan
import com.example.data.model.Payment
import com.example.data.repository.LoanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class LoanMetrics(
    val totalLoans: Int = 0,
    val activeLoans: Int = 0,
    val settledLoans: Int = 0,
    val totalPrincipal: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalOutstanding: Double = 0.0
)

enum class LoanFilter {
    ALL, ACTIVE, SETTLED
}

class LoanViewModel(
    private val repository: LoanRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LoanFilter.ALL)
    val selectedFilter: StateFlow<LoanFilter> = _selectedFilter.asStateFlow()

    private val _selectedLoan = MutableStateFlow<Loan?>(null)
    val selectedLoan: StateFlow<Loan?> = _selectedLoan.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    // Real-time observation of all loans owned by this user
    val loansState: StateFlow<UiState<List<Loan>>> = repository.observeLoans(currentUserId)
        .map<List<Loan>, UiState<List<Loan>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(error.localizedMessage ?: "Failed to load loans"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LoanFilter) {
        _selectedFilter.value = filter
    }

    fun selectLoan(loan: Loan?) {
        _selectedLoan.value = loan
        if (loan != null) {
            viewModelScope.launch {
                repository.observePayments(currentUserId, loan.id)
                    .catch { /* handle silently or log */ }
                    .collect { paymentList ->
                        _payments.value = paymentList.sortedByDescending { it.paymentDate }
                    }
            }
        } else {
            _payments.value = emptyList()
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun createLoan(
        clientName: String,
        clientAddress: String,
        clientPhone: String,
        principal: Double,
        loanTerm: String,
        dueDate: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true

        viewModelScope.launch {
            val result = repository.createLoan(
                clientName = clientName,
                clientAddress = clientAddress,
                clientPhone = clientPhone,
                principal = principal,
                loanTerm = loanTerm,
                dueDate = dueDate,
                notes = notes
            )
            _isSubmitting.value = false
            if (result.isSuccess) {
                _actionMessage.value = "Loan for $clientName created successfully!"
                onSuccess()
            } else {
                _actionMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to create loan"
            }
        }
    }

    fun recordPayment(
        loanId: String,
        amount: Double,
        paymentDate: String,
        notes: String,
        onSuccess: () -> Unit
    ) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true

        viewModelScope.launch {
            val result = repository.recordPayment(
                loanId = loanId,
                amount = amount,
                paymentDate = paymentDate,
                notes = notes
            )
            _isSubmitting.value = false
            if (result.isSuccess) {
                _actionMessage.value = "Payment of $${String.format("%.2f", amount)} recorded!"
                // Update selected loan if open
                val updatedLoanResult = repository.getLoanById(currentUserId, loanId)
                if (updatedLoanResult.isSuccess) {
                    _selectedLoan.value = updatedLoanResult.getOrNull()
                }
                onSuccess()
            } else {
                _actionMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to record payment"
            }
        }
    }

    fun toggleLoanSettled(loan: Loan) {
        val willBeSettled = loan.status != "settled"
        viewModelScope.launch {
            val result = repository.markLoanSettled(loan.id, willBeSettled)
            if (result.isSuccess) {
                _actionMessage.value = if (willBeSettled) "Loan marked as settled!" else "Loan marked as active!"
                val updatedLoanResult = repository.getLoanById(currentUserId, loan.id)
                if (updatedLoanResult.isSuccess) {
                    _selectedLoan.value = updatedLoanResult.getOrNull()
                }
            } else {
                _actionMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update status"
            }
        }
    }

    fun deleteLoan(loan: Loan, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteLoan(loan.id)
            if (result.isSuccess) {
                _actionMessage.value = "Loan deleted"
                if (_selectedLoan.value?.id == loan.id) {
                    _selectedLoan.value = null
                }
                onDeleted()
            } else {
                _actionMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete loan"
            }
        }
    }

    fun computeMetrics(loans: List<Loan>): LoanMetrics {
        var totalPrincipal = 0.0
        var totalCollected = 0.0
        var totalOutstanding = 0.0
        var activeCount = 0
        var settledCount = 0

        for (loan in loans) {
            totalPrincipal += loan.principal
            totalCollected += loan.totalPaid
            totalOutstanding += loan.remainingBalance
            if (loan.status == "settled") {
                settledCount++
            } else {
                activeCount++
            }
        }

        return LoanMetrics(
            totalLoans = loans.size,
            activeLoans = activeCount,
            settledLoans = settledCount,
            totalPrincipal = totalPrincipal,
            totalCollected = totalCollected,
            totalOutstanding = totalOutstanding
        )
    }

    fun filterLoans(loans: List<Loan>, query: String, filter: LoanFilter): List<Loan> {
        return loans.filter { loan ->
            val matchesFilter = when (filter) {
                LoanFilter.ALL -> true
                LoanFilter.ACTIVE -> loan.status == "active"
                LoanFilter.SETTLED -> loan.status == "settled"
            }
            val matchesQuery = query.isBlank() ||
                    loan.clientName.contains(query, ignoreCase = true) ||
                    loan.clientPhone.contains(query, ignoreCase = true) ||
                    loan.clientAddress.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }.sortedByDescending { it.createdAt }
    }
}
