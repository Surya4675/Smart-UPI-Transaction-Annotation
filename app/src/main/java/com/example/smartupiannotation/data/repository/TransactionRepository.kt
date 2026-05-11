package com.example.smartupiannotation.data.repository

import com.example.smartupiannotation.data.local.ParticipantEntity
import com.example.smartupiannotation.data.local.TransactionDao
import com.example.smartupiannotation.data.local.TransactionEntity
import com.example.smartupiannotation.data.local.TransactionWithParticipants
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<TransactionWithParticipants>> = 
        transactionDao.getAllTransactionsWithParticipants()

    suspend fun insertTransaction(transaction: TransactionEntity, participants: List<ParticipantEntity>) {
        transactionDao.insertTransactionWithParticipants(transaction, participants)
    }

    suspend fun getTransactionById(id: Long): TransactionWithParticipants? {
        return transactionDao.getTransactionWithParticipantsById(id)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }
}
