package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Loan(
    @DocumentId val id: String = "",
    val userId: String = "",
    val clientName: String = "",
    val clientAddress: String = "",
    val clientPhone: String = "",
    val principal: Double = 0.0,
    val loanTerm: String = "",
    val dueDate: String = "",
    val notes: String = "",
    val status: String = "active", // "active" or "settled"
    val totalPaid: Double = 0.0,
    val remainingBalance: Double = 0.0,
    @ServerTimestamp val settledAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
