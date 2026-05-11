package com.example.smartupiannotation.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<ParticipantEntity>)

    @Transaction
    suspend fun insertTransactionWithParticipants(transaction: TransactionEntity, participants: List<ParticipantEntity>) {
        val transactionId = insertTransaction(transaction)
        val updatedParticipants = participants.map { it.copy(transactionOwnerId = transactionId) }
        insertParticipants(updatedParticipants)
    }

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun getAllTransactionsWithParticipants(): Flow<List<TransactionWithParticipants>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId")
    suspend fun getTransactionWithParticipantsById(transactionId: Long): TransactionWithParticipants?

    @Query("SELECT * FROM transactions WHERE transactionDate = :date AND amount = :amount LIMIT 1")
    suspend fun getTransactionByDateAndAmount(date: Long, amount: Double): TransactionEntity?

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
}
