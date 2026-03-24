package com.example.smartupiannotation.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithParticipants(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "transactionId",
        entityColumn = "transactionOwnerId"
    )
    val participants: List<ParticipantEntity>
)
