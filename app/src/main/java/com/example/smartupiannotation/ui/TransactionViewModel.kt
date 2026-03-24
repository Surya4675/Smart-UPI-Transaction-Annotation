package com.example.smartupiannotation.ui

import androidx.lifecycle.*
import com.example.smartupiannotation.data.local.entity.ParticipantEntity
import com.example.smartupiannotation.data.local.entity.TransactionEntity
import com.example.smartupiannotation.data.local.entity.TransactionWithParticipants
import com.example.smartupiannotation.data.repository.TransactionRepository
import kotlinx.coroutines.launch

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {

    val allTransactions: LiveData<List<TransactionWithParticipants>> = 
        repository.allTransactions.asLiveData()

    fun insertTransaction(transaction: TransactionEntity, participants: List<ParticipantEntity>) = viewModelScope.launch {
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
