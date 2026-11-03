package com.example.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddLoanDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        address: String,
        phone: String,
        principal: Double,
        term: String,
        dueDate: String,
        notes: String
    ) -> Unit
) {
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var principalText by remember { mutableStateOf("") }
    var loanTerm by remember { mutableStateOf("30 Days") }
    var dueDate by remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 30)
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        mutableStateOf(df.format(cal.time))
    }
    var notes by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf(false) }
    var principalError by remember { mutableStateOf(false) }

    val presetTerms = listOf("7 Days", "14 Days", "30 Days", "60 Days", "90 Days")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Client Loan",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_add_loan_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Client Full Name
                OutlinedTextField(
                    value = clientName,
                    onValueChange = {
                        clientName = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Client Full Name *") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Client name is required") } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Client Phone
                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = {
                        clientPhone = it
                        phoneError = it.isBlank()
                    },
                    label = { Text("Phone Number *") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    isError = phoneError,
                    supportingText = if (phoneError) { { Text("Phone number is required") } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_phone_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Client Address
                OutlinedTextField(
                    value = clientAddress,
                    onValueChange = {
                        clientAddress = it
                        addressError = it.isBlank()
                    },
                    label = { Text("Physical Address *") },
                    leadingIcon = {
                        Icon(Icons.Default.Home, contentDescription = null)
                    },
                    isError = addressError,
                    supportingText = if (addressError) { { Text("Address is required") } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_address_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Principal Amount
                OutlinedTextField(
                    value = principalText,
                    onValueChange = {
                        principalText = it
                        principalError = it.toDoubleOrNull() == null || it.toDouble() <= 0
                    },
                    label = { Text("Loan Principal ($) *") },
                    leadingIcon = {
                        Icon(Icons.Default.AttachMoney, contentDescription = null)
                    },
                    isError = principalError,
                    supportingText = if (principalError) { { Text("Enter a valid amount > 0") } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("principal_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Term Selector Chips
                Text(
                    text = "Loan Term & Suggestions",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetTerms.forEach { term ->
                        val selected = loanTerm.equals(term, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                loanTerm = term
                                val days = when (term) {
                                    "7 Days" -> 7
                                    "14 Days" -> 14
                                    "30 Days" -> 30
                                    "60 Days" -> 60
                                    "90 Days" -> 90
                                    else -> 30
                                }
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_YEAR, days)
                                val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                dueDate = df.format(cal.time)
                            },
                            label = { Text(term) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Loan Term input
                OutlinedTextField(
                    value = loanTerm,
                    onValueChange = { loanTerm = it },
                    label = { Text("Custom Term Duration *") },
                    leadingIcon = {
                        Icon(Icons.Default.Timer, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_term_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Due Date
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD) *") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("due_date_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notes / Collateral
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Collateral / ID (Optional)") },
                    leadingIcon = {
                        Icon(Icons.Default.Notes, contentDescription = null)
                    },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_notes_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("cancel_add_loan_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val validName = clientName.isNotBlank()
                            val validPhone = clientPhone.isNotBlank()
                            val validAddress = clientAddress.isNotBlank()
                            val pVal = principalText.toDoubleOrNull()
                            val validPrincipal = pVal != null && pVal > 0

                            nameError = !validName
                            phoneError = !validPhone
                            addressError = !validAddress
                            principalError = !validPrincipal

                            if (validName && validPhone && validAddress && validPrincipal) {
                                onSave(
                                    clientName.trim(),
                                    clientAddress.trim(),
                                    clientPhone.trim(),
                                    pVal!!,
                                    loanTerm.trim().ifBlank { "30 Days" },
                                    dueDate.trim().ifBlank {
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, 30)
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                    },
                                    notes.trim()
                                )
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_loan_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Create Loan")
                        }
                    }
                }
            }
        }
    }
}
