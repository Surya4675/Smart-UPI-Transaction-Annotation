package com.example.smartupiannotation.ui

import androidx.lifecycle.*
import com.example.smartupiannotation.data.local.ParticipantEntity
import com.example.smartupiannotation.data.local.TransactionEntity
import com.example.smartupiannotation.data.local.TransactionWithParticipants
import com.example.smartupiannotation.data.repository.TransactionRepository
import kotlinx.coroutines.launch
import java.util.*

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {

    val allTransactions: LiveData<List<TransactionWithParticipants>> = 
        repository.allTransactions.asLiveData()

    // Dashboard Calculations
    val totalAmountSpent: LiveData<Double> = allTransactions.map { transactions ->
        transactions.sumOf { it.transaction.amount }
    }

    val totalAmountOwed: LiveData<Double> = allTransactions.map { transactions ->
        transactions.flatMap { it.participants }
            .filter { !it.isPaid }
            .sumOf { it.amountOwed }
    }

    val monthlyExpenses: LiveData<List<Pair<String, Float>>> = allTransactions.map { transactions ->
        val calendar = Calendar.getInstance()
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        
        val expensesByMonth = mutableMapOf<Int, Double>()
        
        // Initialize last 6 months
        val currentMonth = calendar.get(Calendar.MONTH)
        for (i in 0..5) {
            val month = (currentMonth - i + 12) % 12
            expensesByMonth[month] = 0.0
        }

        transactions.forEach { trans ->
            calendar.timeInMillis = trans.transaction.transactionDate
            val month = calendar.get(Calendar.MONTH)
            if (expensesByMonth.containsKey(month)) {
                expensesByMonth[month] = expensesByMonth[month]!! + trans.transaction.amount
            }
        }

        expensesByMonth.entries
            .sortedBy { (month, _) -> (month - currentMonth - 1 + 12) % 12 } // Sort to show in chronological order
            .map { (month, total) -> monthNames[month] to total.toFloat() }
    }

    fun insertTransaction(transaction: TransactionEntity, participants: List<ParticipantEntity>) = viewModelScope.launch {
        repository.insertTransaction(transaction, participants)
    }

    suspend fun saveTransaction(transaction: TransactionEntity, participants: List<ParticipantEntity>) {
        repository.insertTransaction(transaction, participants)
    }

    suspend fun getTransactionById(id: Long): TransactionWithParticipants? {
        return repository.getTransactionById(id)
    }

    fun deleteTransaction(transaction: TransactionEntity) = viewModelScope.launch {
        repository.deleteTransaction(transaction)
    }
}

class TransactionViewModelFactory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
