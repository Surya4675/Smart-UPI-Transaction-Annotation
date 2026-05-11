package com.example.smartupiannotation.data.remote

import android.util.Log
import com.example.smartupiannotation.data.local.ParticipantEntity
import com.example.smartupiannotation.data.local.TransactionDao
import com.example.smartupiannotation.data.local.TransactionEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SyncManager(private val transactionDao: TransactionDao) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun saveToCloud(transaction: TransactionEntity, participants: List<ParticipantEntity>) = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext
        
        try {
            val transactionData = hashMapOf(
                "ownerId" to user.uid,
                "amount" to transaction.amount,
                "receiverName" to transaction.receiverName,
                "transactionDate" to transaction.transactionDate,
                "bankName" to transaction.bankName,
                "maskedAccount" to transaction.maskedAccount,
                "category" to transaction.category,
                "note" to transaction.note,
                "participants" to participants.map { 
                    hashMapOf(
                        "name" to it.participantName,
                        "amount" to it.amountOwed,
                        "isPaid" to it.isPaid
                    )
                }
            )

            firestore.collection("users")
                .document(user.uid)
                .collection("transactions")
                .add(transactionData)
                .await()
            
            Log.d("SyncManager", "Transaction saved to Firestore")
        } catch (e: Exception) {
            Log.e("SyncManager", "Error saving to Firestore", e)
        }
    }

    suspend fun downloadFromCloud() = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext
        
        try {
            val snapshot = firestore.collection("users")
                .document(user.uid)
                .collection("transactions")
                .get()
                .await()

            for (doc in snapshot.documents) {
                val date = doc.getLong("transactionDate") ?: 0L
                val amount = doc.getDouble("amount") ?: 0.0
                
                // Simple heuristic to avoid duplicates
                val exists = transactionDao.getTransactionByDateAndAmount(date, amount)
                if (exists == null) {
                    val roomTx = TransactionEntity(
                        amount = amount,
                        receiverName = doc.getString("receiverName") ?: "Unknown",
                        transactionDate = date,
                        bankName = doc.getString("bankName"),
                        maskedAccount = doc.getString("maskedAccount"),
                        category = doc.getString("category"),
                        note = doc.getString("note")
                    )
                    
                    val participantsData = doc.get("participants") as? List<Map<String, Any>>
                    val roomParticipants = participantsData?.map { p ->
                        ParticipantEntity(
                            transactionOwnerId = 0,
                            participantName = p["name"] as? String ?: "Unknown",
                            amountOwed = (p["amount"] as? Number)?.toDouble() ?: 0.0,
                            isPaid = p["isPaid"] as? Boolean ?: false
                        )
                    } ?: emptyList()
                    
                    transactionDao.insertTransactionWithParticipants(roomTx, roomParticipants)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Error downloading from Firestore", e)
        }
    }
}
