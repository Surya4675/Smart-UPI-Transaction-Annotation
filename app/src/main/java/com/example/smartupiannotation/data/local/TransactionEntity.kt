package com.example.smartupiannotation.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val transactionId: Long = 0,
    val amount: Double,
    val receiverName: String,
    val transactionDate: Long,
    val bankName: String? = null,
    val maskedAccount: String? = null,
    val category: String? = null,
    val note: String? = null
)
