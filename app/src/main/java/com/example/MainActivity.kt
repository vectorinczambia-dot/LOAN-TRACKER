package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import com.example.data.repository.LoanRepository
import com.example.ui.auth.SignInScreen
import com.example.ui.auth.signOut
import com.example.ui.loan.LoanListScreen
import com.example.ui.loan.LoanViewModel
import com.example.ui.theme.LoanTrackerTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoanTrackerTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val auth = Firebase.auth
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = {
                currentUser = auth.currentUser
            }
        )
    } else {
        // Authenticated user session; feature ViewModel is instantiated safely with authenticated uid
        val repository = remember(user.uid) { LoanRepository(context) }
        val loanViewModel = remember(user.uid) {
            LoanViewModel(repository = repository, currentUserId = user.uid)
        }

        LoanListScreen(
            viewModel = loanViewModel,
            onSignOut = {
                signOut(
                    context = context,
                    credentialManager = credentialManager,
                    onSignOutComplete = {
                        currentUser = null
                    },
                    scope = scope
                )
            }
        )
    }
}
