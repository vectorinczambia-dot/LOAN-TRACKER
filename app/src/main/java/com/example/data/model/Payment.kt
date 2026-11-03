package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val loanId: String = "",
    val amount: Double = 0.0,
    val paymentDate: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
