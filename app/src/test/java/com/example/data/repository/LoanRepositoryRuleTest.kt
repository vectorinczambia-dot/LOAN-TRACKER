package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoanRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createLoan_validPayload_createsDocumentAndReturnsId(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = LoanRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createLoan(
        clientName = "Alice Borrower",
        clientAddress = "123 Apple Rd",
        clientPhone = "+1234567890",
        principal = 500.0,
        loanTerm = "30 days",
        dueDate = "2026-12-01",
        notes = "Short term personal loan"
      )
    }

    assertTrue(result.isSuccess)
    val loanId = result.getOrThrow()
    assertTrue(loanId.isNotEmpty())
  }

  @Test
  fun observeLoans_authenticatedOwner_returnsMatchingLoans(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = LoanRepository(firestore)

    val loanId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createLoan(
        clientName = "Bob Borrower",
        clientAddress = "456 Oak St",
        clientPhone = "+1987654321",
        principal = 1000.0,
        loanTerm = "60 days",
        dueDate = "2026-12-30",
        notes = "Business loan"
      ).getOrThrow()
    }

    val loans = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeLoans(auth.currentUser!!.uid).first { list ->
        list.any { it.id == loanId }
      }
    }
    assertTrue(loans.map { it.id }.contains(loanId))
    val loan = loans.first { it.id == loanId }
    assertEquals("Bob Borrower", loan.clientName)
    assertEquals(1000.0, loan.principal, 0.001)
    assertEquals(1000.0, loan.remainingBalance, 0.001)
    assertEquals("active", loan.status)
  }

  @Test
  fun recordPayment_updatesTotalsAndRecordsInstallment(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = LoanRepository(firestore)

    val loanId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createLoan(
        clientName = "Charlie Borrower",
        clientAddress = "789 Pine Ave",
        clientPhone = "+1555444333",
        principal = 400.0,
        loanTerm = "4 weeks",
        dueDate = "2026-11-30",
        notes = ""
      ).getOrThrow()
    }

    val paymentResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.recordPayment(
        loanId = loanId,
        amount = 150.0,
        paymentDate = "2026-11-05",
        notes = "First installment"
      )
    }
    assertTrue(paymentResult.isSuccess)

    val updatedLoan = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.getLoanById(auth.currentUser!!.uid, loanId).getOrThrow()
    }
    assertNotNull(updatedLoan)
    assertEquals(150.0, updatedLoan!!.totalPaid, 0.001)
    assertEquals(250.0, updatedLoan.remainingBalance, 0.001)

    val payments = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observePayments(auth.currentUser!!.uid, loanId).first { it.isNotEmpty() }
    }
    assertEquals(1, payments.size)
    assertEquals(150.0, payments[0].amount, 0.001)
  }

  @Test
  fun markLoanSettled_updatesStatusToSettled(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = LoanRepository(firestore)

    val loanId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createLoan(
        clientName = "David Borrower",
        clientAddress = "101 Maple Blvd",
        clientPhone = "+1222333444",
        principal = 200.0,
        loanTerm = "2 weeks",
        dueDate = "2026-11-20",
        notes = ""
      ).getOrThrow()
    }

    val settleResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.markLoanSettled(loanId, true)
    }
    assertTrue(settleResult.isSuccess)

    val updatedLoan = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.getLoanById(auth.currentUser!!.uid, loanId).getOrThrow()
    }
    assertNotNull(updatedLoan)
    assertEquals("settled", updatedLoan!!.status)
    assertEquals(0.0, updatedLoan.remainingBalance, 0.001)
  }

  @Test
  fun getLoanById_crossUserAccess_failsWithPermissionDenied(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val aliceRepo = LoanRepository(firestore)
    val aliceUid = auth.currentUser!!.uid
    val loanId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createLoan(
        clientName = "Private Borrower",
        clientAddress = "Secret Rd",
        clientPhone = "123",
        principal = 300.0,
        loanTerm = "1 month",
        dueDate = "2026-12-01",
        notes = ""
      ).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = LoanRepository(firestore)
    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getLoanById(aliceUid, loanId)
    }
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    val firestoreException = checkNotNull(exception?.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $exception"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  @Test
  fun observeLoans_unauthenticatedUser_failsWithPermissionDenied(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val aliceUid = auth.currentUser!!.uid
    auth.signOut()

    val repository = LoanRepository(firestore)
    var caughtException: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeLoans(aliceUid).first()
      }
      fail("Expected exception when unauthenticated")
    } catch (e: Exception) {
      caughtException = e
    }
    assertNotNull(caughtException)
    val firestoreException = checkNotNull(caughtException!!.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $caughtException"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
