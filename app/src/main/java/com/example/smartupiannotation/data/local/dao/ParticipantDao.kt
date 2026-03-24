package com.example.smartupiannotation.data.local.dao

import androidx.room.*
import com.example.smartupiannotation.data.local.entity.ParticipantEntity

@Dao
interface ParticipantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: ParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<ParticipantEntity>)

    @Update
    suspend fun updateParticipant(participant: ParticipantEntity)

    @Delete
    suspend fun deleteParticipant(participant: ParticipantEntity)

    @Query("DELETE FROM participants WHERE transactionOwnerId = :transactionId")
    suspend fun deleteParticipantsForTransaction(transactionId: Long)
}
