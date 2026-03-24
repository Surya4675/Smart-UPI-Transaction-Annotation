package com.example.smartupiannotation.data.repository

import com.example.smartupiannotation.data.local.dao.ParticipantDao
import com.example.smartupiannotation.data.local.dao.TransactionDao
import com.example.smartupiannotation.data.local.entity.ParticipantEntity
import com.example.smartupiannotation.data.local.entity.TransactionEntity
import com.example.smartupiannotation.data.local.entity.TransactionWithParticipants
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val participantDao: ParticipantDao
) {
    val allTransactions: Flow<List<TransactionWithParticipants>> = 
        transactionDao.getAllTransactionsWithParticipants()

    suspend fun insertTransaction(transaction: TransactionEntity, participants: List<ParticipantEntity>) {
        val transactionId = transactionDao.insertTransaction(transaction)
        // If updating, delete existing participants first
        if (transaction.transactionId != 0L) {
            participantDao.deleteParticipantsForTransaction(transaction.transactionId)
        }
        val participantsWithOwner = participants.map { it.copy(transactionOwnerId = transactionId) }
        participantDao.insertParticipants(participantsWithOwner)
    }

    suspend fun getTransactionById(id: Long): TransactionWithParticipants? {
        return transactionDao.getTransactionById(id)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }
}
