package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Loan
import com.example.data.model.Payment
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LoanRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeLoans(userId: String): Flow<List<Loan>> = flow {
        val path = "users/$userId/loans"
        val query = db.collection("users").document(userId).collection("loans")
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Loan::class.java)
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePayments(userId: String, loanId: String): Flow<List<Payment>> = flow {
        val path = "users/$userId/loans/$loanId/payments"
        val query = db.collection("users").document(userId).collection("loans").document(loanId).collection("payments")
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Payment::class.java)
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getLoanById(userId: String, loanId: String): Result<Loan?> {
        val path = "users/$userId/loans/$loanId"
        return try {
            val doc = db.collection("users").document(userId).collection("loans").document(loanId).get().await()
            Result.success(doc.toObject(Loan::class.java))
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createLoan(
        clientName: String,
        clientAddress: String,
        clientPhone: String,
        principal: Double,
        loanTerm: String,
        dueDate: String,
        notes: String
    ): Result<String> {
        val uid = requireUserId()
        val path = "users/$uid/loans"
        val loansRef = db.collection("users").document(uid).collection("loans")
        val docRef = loansRef.document()

        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "clientName" to clientName.trim(),
            "clientAddress" to clientAddress.trim(),
            "clientPhone" to clientPhone.trim(),
            "principal" to principal,
            "loanTerm" to loanTerm.trim(),
            "dueDate" to dueDate.trim(),
            "notes" to notes.trim(),
            "status" to "active",
            "totalPaid" to 0.0,
            "remainingBalance" to principal,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordPayment(
        loanId: String,
        amount: Double,
        paymentDate: String,
        notes: String
    ): Result<String> {
        val uid = requireUserId()
        val loanDocRef = db.collection("users").document(uid).collection("loans").document(loanId)
        val paymentsRef = loanDocRef.collection("payments")
        val paymentDocRef = paymentsRef.document()

        val paymentPayload = hashMapOf<String, Any>(
            "userId" to uid,
            "loanId" to loanId,
            "amount" to amount,
            "paymentDate" to paymentDate.trim(),
            "notes" to notes.trim(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            // First read the loan document to calculate new totals
            val loanSnapshot = loanDocRef.get().await()
            val existingLoan = loanSnapshot.toObject(Loan::class.java)
                ?: throw IllegalStateException("Loan document not found")

            val newTotalPaid = existingLoan.totalPaid + amount
            val newRemaining = maxOf(0.0, existingLoan.principal - newTotalPaid)
            val newStatus = if (newRemaining <= 0.0) "settled" else existingLoan.status

            val updatePayload = hashMapOf<String, Any>(
                "totalPaid" to newTotalPaid,
                "remainingBalance" to newRemaining,
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus == "settled" && existingLoan.status != "settled") {
                updatePayload["settledAt"] = FieldValue.serverTimestamp()
            }

            // Write payment and update loan
            paymentDocRef.set(paymentPayload).await()
            loanDocRef.update(updatePayload).await()

            Result.success(paymentDocRef.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "${loanDocRef.path}/payments")
            Result.failure(e)
        }
    }

    suspend fun markLoanSettled(
        loanId: String,
        settled: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("loans").document(loanId)
        val updatePayload = hashMapOf<String, Any>(
            "status" to if (settled) "settled" else "active",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (settled) {
            updatePayload["settledAt"] = FieldValue.serverTimestamp()
            updatePayload["remainingBalance"] = 0.0
        }

        return try {
            docRef.update(updatePayload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteLoan(loanId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("loans").document(loanId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
