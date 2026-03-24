package com.example.smartupiannotation.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smartupiannotation.data.local.dao.ParticipantDao
import com.example.smartupiannotation.data.local.dao.TransactionDao
import com.example.smartupiannotation.data.local.entity.ParticipantEntity
import com.example.smartupiannotation.data.local.entity.TransactionEntity

@Database(entities = [TransactionEntity::class, ParticipantEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun participantDao(): ParticipantDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upi_annotation_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
