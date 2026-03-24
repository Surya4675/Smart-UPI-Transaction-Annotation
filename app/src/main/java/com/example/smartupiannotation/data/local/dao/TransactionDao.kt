package com.example.smartupiannotation.data.local.dao

import androidx.room.*
import com.example.smartupiannotation.data.local.entity.TransactionEntity
import com.example.smartupiannotation.data.local.entity.TransactionWithParticipants
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun getAllTransactionsWithParticipants(): Flow<List<TransactionWithParticipants>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE transactionId = :id")
    suspend fun getTransactionById(id: Long): TransactionWithParticipants?

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}
