package com.example.smartupiannotation.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "participants",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transactionId"],
            childColumns = ["transactionOwnerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionOwnerId"])]
)
data class ParticipantEntity(
    @PrimaryKey(autoGenerate = true)
    val participantId: Long = 0,
    val transactionOwnerId: Long,
    val participantName: String,
    val amountOwed: Double,
    val isPaid: Boolean = false
)
