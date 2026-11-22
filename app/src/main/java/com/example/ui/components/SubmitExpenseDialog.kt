package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.ClaimCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubmitExpenseClaimDialog(
  onDismiss: () -> Unit,
  onSubmit: (
    category: ClaimCategory,
    amountText: String,
    ticketCode: String,
    description: String,
    receiptAttached: Boolean,
  ) -> Unit,
  defaultTicketCode: String = "FTTH-4821",
) {
  var category by remember { mutableStateOf(ClaimCategory.FUEL) }
  var amount by remember { mutableStateOf("") }
  var ticketCode by remember { mutableStateOf(defaultTicketCode) }
  var description by remember { mutableStateOf("") }
  var attachReceipt by remember { mutableStateOf(true) }

  val parsedAmount = amount.toDoubleOrNull()
  val canSubmit = parsedAmount != null && parsedAmount > 0.0 && description.isNotBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("submit_expense_dialog"),
    icon = {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = { Text("Submit Expense / Overtime Claim") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          ClaimCategory.entries.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat.label) },
              modifier = Modifier.testTag("claim_category_chip_${cat.name}"),
            )
          }
        }
        OutlinedTextField(
          value = amount,
          onValueChange = { amount = it },
          label = { Text("Amount (USD)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("claim_amount_input"),
        )
        OutlinedTextField(
          value = ticketCode,
          onValueChange = { ticketCode = it },
          label = { Text("Linked Ticket / Branch Code") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("claim_ticket_input"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Receipt / Call-Out Description") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("claim_desc_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Receipt Attached", style = MaterialTheme.typography.bodySmall)
          Switch(
            checked = attachReceipt,
            onCheckedChange = { attachReceipt = it },
            modifier = Modifier.testTag("claim_receipt_switch"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmit(category, amount, ticketCode, description, attachReceipt) },
        enabled = canSubmit,
        modifier = Modifier.testTag("claim_submit_button"),
      ) {
        Text("Submit Claim")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("claim_cancel_button"),
      ) {
        Text("Cancel")
      }
    },
  )
}
