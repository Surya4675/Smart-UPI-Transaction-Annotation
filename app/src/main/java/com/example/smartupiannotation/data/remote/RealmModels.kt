package com.example.smartupiannotation.data.remote

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class TransactionRealm : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var owner_id: String = "" // For partition-based or flexible sync
    var amount: Double = 0.0
    var receiverName: String = ""
    var transactionDate: Long = 0
    var bankName: String? = null
    var maskedAccount: String? = null
    var category: String? = null
    var note: String? = null
    var participants: RealmList<ParticipantRealm> = realmListOf()
}

class ParticipantRealm : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var participantName: String = ""
    var amountOwed: Double = 0.0
    var isPaid: Boolean = false
}
