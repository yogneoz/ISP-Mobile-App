package com.example.ui.screens.profile

import android.Manifest
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.DocumentType
import com.example.domain.model.EmployeeDocument
import com.example.domain.model.UserSession
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoTelemetryStyle
import com.example.ui.theme.PulseTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
  session: UserSession,
  isOfflineSimulated: Boolean = false,
  onLogSensitiveView: (String, String) -> Unit = { _, _ -> },
  onOpenDocumentScanner: () -> Unit,
  onChangePin: () -> Unit,
  onLockNow: () -> Unit,
  onSignOut: () -> Unit,
  onOpenAuditLog: () -> Unit = {},
  viewModel: ProfileViewModel = hiltViewModel(),
) {
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val documents by viewModel.documents.collectAsStateWithLifecycle()
  val filteredDocuments by viewModel.filteredDocuments.collectAsStateWithLifecycle()
  val vaultSearchQuery by viewModel.vaultSearchQuery.collectAsStateWithLifecycle()
  val vaultCategoryFilter by viewModel.vaultCategoryFilter.collectAsStateWithLifecycle()
  val fcmStatus by viewModel.fcmStatus.collectAsStateWithLifecycle()
  val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsStateWithLifecycle()
  val feedback by viewModel.feedback.collectAsStateWithLifecycle()

  var showEditContactDialog by remember { mutableStateOf(false) }
  var selectedDocType by remember { mutableStateOf(DocumentType.COMPLIANCE) }
  var customDocTitle by remember { mutableStateOf("") }
  var previewDocument by remember { mutableStateOf<EmployeeDocument?>(null) }

  previewDocument?.let { doc ->
    DocumentViewerDialog(
      document = doc,
      onDismiss = { previewDocument = null },
    )
  }

  // Zero-Permission Android Photo Picker for Profile Avatar
  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      uri?.let { viewModel.updateAvatarPhoto(it) }
    }

  // Storage Access Framework OpenDocument for <= 5MB PDF/JPEG/PNG Compliance Documents
  val documentPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      uri?.let {
        viewModel.uploadDocument(it, customDocTitle, selectedDocType)
        customDocTitle = ""
      }
    }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) {
      viewModel.refreshNotificationPermission()
    }

  if (showEditContactDialog && profile != null) {
    var phone by remember(profile) { mutableStateOf(profile!!.phone) }
    var emName by remember(profile) { mutableStateOf(profile!!.emergencyContactName) }
    var emPhone by remember(profile) { mutableStateOf(profile!!.emergencyContactPhone) }

    AlertDialog(
      onDismissRequest = { showEditContactDialog = false },
      title = { Text("Update Contact Details") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Mobile Phone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("edit_phone_input"),
          )
          OutlinedTextField(
            value = emName,
            onValueChange = { emName = it },
            label = { Text("Emergency Contact Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("edit_emergency_name_input"),
          )
          OutlinedTextField(
            value = emPhone,
            onValueChange = { emPhone = it },
            label = { Text("Emergency Contact Phone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("edit_emergency_phone_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateContact(phone, emName, emPhone)
            showEditContactDialog = false
          },
          modifier = Modifier.testTag("save_contact_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditContactDialog = false }) { Text("Cancel") }
      },
    )
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = "Profile & Document Vault",
          style = MaterialTheme.typography.headlineMedium,
        )
      }

      if (feedback != null) {
        val (msg, isError) = feedback!!
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color =
              if (isError) MaterialTheme.colorScheme.errorContainer
              else PulseTheme.semantic.verifiedContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color =
                  if (isError) MaterialTheme.colorScheme.onErrorContainer
                  else PulseTheme.semantic.onVerifiedContainer,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
            }
          }
        }
      }

      // Employee Master Card
      profile?.let { emp ->
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                  if (!emp.avatarUri.isNullOrBlank()) {
                    AsyncImage(
                      model = emp.avatarUri,
                      contentDescription = "Profile Photo",
                      modifier = Modifier.size(68.dp).clip(CircleShape),
                      contentScale = ContentScale.Crop,
                    )
                  } else {
                    Image(
                      painter = painterResource(id = R.drawable.img_avatar_default),
                      contentDescription = "Profile Photo",
                      modifier = Modifier.size(68.dp).clip(CircleShape),
                      contentScale = ContentScale.Crop,
                    )
                  }
                  Surface(
                    onClick = {
                      photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp).testTag("pick_avatar_button"),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Update Profile Photo",
                        modifier = Modifier.size(14.dp),
                      )
                    }
                  }
                }

                Column(modifier = Modifier.weight(1f)) {
                  Text(text = emp.fullName, style = MaterialTheme.typography.titleLarge)
                  Text(
                    text = "${emp.designation} • ${emp.department}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  StatusPill(
                    text = "${emp.employeeCode} • ${session.role.badgeLabel}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

              ProfileInfoRow("Assigned Branch", emp.branch.name)
              ProfileInfoRow("Corporate Email", emp.email)
              ProfileInfoRow("Phone", emp.phone)
              ProfileInfoRow(
                "Emergency Contact",
                "${emp.emergencyContactName} (${emp.emergencyContactPhone})",
              )
              ProfileInfoRow("Joined", emp.joinDate)

              OutlinedButton(
                onClick = { showEditContactDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("edit_contact_button"),
              ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Contact & Emergency Info")
              }
            }
          }
        }

        // Bank Account Info Card (Blocked / Masked when offline per v2 security policy)
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = if (isOfflineSimulated) Icons.Filled.Lock else Icons.Filled.AccountBalance,
                contentDescription = null,
                tint = if (isOfflineSimulated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
              )
              Column {
                Text(text = "Payroll Disbursement Bank", style = MaterialTheme.typography.titleSmall)
                Text(
                  text =
                    if (isOfflineSimulated) "Blocked offline — connect to view masked bank details"
                    else "${emp.bankName}  •  ${emp.bankAccountMasked}",
                  style = MonoTelemetryStyle,
                  color =
                    if (isOfflineSimulated) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Document Vault (hr_employee_documents)
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(text = "Compliance Document Vault", style = MaterialTheme.typography.titleMedium)
              StatusPill(
                text = "ML Kit OCR",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = Icons.Filled.AutoAwesome,
              )
            }
            Text(
              text =
                "Scan physical receipts, certificates, or HR forms with camera OCR auto-categorization, or upload PDF/JPEG/PNG (≤ 5.00 MB).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
              value = customDocTitle,
              onValueChange = { customDocTitle = it },
              label = { Text("Document Title (auto-filled by OCR if blank)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              DocumentType.entries
                .filter { it != DocumentType.PROFILE_PHOTO }
                .forEach { docType ->
                  FilterChip(
                    selected = selectedDocType == docType,
                    onClick = { selectedDocType = docType },
                    label = { Text(docType.label) },
                  )
                }
            }

            Button(
              onClick = onOpenDocumentScanner,
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("scan_document_camera_button"),
            ) {
              Icon(Icons.Filled.DocumentScanner, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Scan Receipt, Certificate, or Form")
            }

            OutlinedButton(
              onClick = {
                documentPickerLauncher.launch(
                  arrayOf("application/pdf", "image/jpeg", "image/png")
                )
              },
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("upload_document_button"),
            ) {
              Icon(Icons.Filled.UploadFile, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Or Upload Existing File (Auto-OCR Images)")
            }

            if (documents.isNotEmpty()) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              OutlinedTextField(
                value = vaultSearchQuery,
                onValueChange = viewModel::setVaultSearchQuery,
                label = { Text("Search Vault & OCR Extracted Text") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("vault_search_input"),
              )
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
              ) {
                item {
                  FilterChip(
                    selected = vaultCategoryFilter == null,
                    onClick = { viewModel.setVaultCategoryFilter(null) },
                    label = { Text("All (${documents.size})") },
                    modifier = Modifier.testTag("vault_filter_ALL"),
                  )
                }
                items(DocumentType.entries.filter { it != DocumentType.PROFILE_PHOTO }) { type ->
                  val count = documents.count { it.documentType == type }
                  if (count > 0 || vaultCategoryFilter == type) {
                    FilterChip(
                      selected = vaultCategoryFilter == type,
                      onClick = { viewModel.setVaultCategoryFilter(type) },
                      label = { Text("${type.label} ($count)") },
                      modifier = Modifier.testTag("vault_filter_${type.name}"),
                    )
                  }
                }
              }
            }
          }
        }
      }

      items(filteredDocuments, key = { it.id }) { doc ->
        val semantic = PulseTheme.semantic
        Card(
          onClick = {
            if (!isOfflineSimulated) {
              onLogSensitiveView("DOCUMENT_VIEW", "Viewed ${doc.title}")
              previewDocument = doc
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("doc_card_${doc.id}"),
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(text = doc.title, style = MaterialTheme.typography.titleSmall)
                Text(
                  text =
                    if (isOfflineSimulated) "${doc.documentType.label} • Blocked offline (no local shared cache)"
                    else "${doc.fileName} • ${String.format(Locale.US, "%.2f MB", doc.fileSizeBytes / (1024.0 * 1024.0))} • ${doc.verificationStatus}",
                  style = MonoTelemetryStyle,
                  color =
                    if (isOfflineSimulated) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = {
                  if (!isOfflineSimulated) {
                    onLogSensitiveView("DOCUMENT_VIEW", "Viewed ${doc.title}")
                    previewDocument = doc
                  }
                },
                enabled = !isOfflineSimulated,
                modifier = Modifier.testTag("view_doc_${doc.id}"),
              ) {
                Icon(
                  imageVector = if (isOfflineSimulated) Icons.Filled.Lock else Icons.Filled.Visibility,
                  contentDescription = "View document",
                  tint = if (isOfflineSimulated) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                )
              }
              IconButton(
                onClick = { viewModel.deleteDocument(doc.id) },
                modifier = Modifier.testTag("delete_doc_${doc.id}"),
              ) {
                Icon(
                  Icons.Filled.DeleteOutline,
                  contentDescription = "Delete document",
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }

            if (!isOfflineSimulated) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                StatusPill(
                  text = doc.documentType.label,
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                doc.detectedAmount?.let { amt ->
                  StatusPill(
                    text = amt,
                    containerColor = semantic.verifiedContainer,
                    contentColor = semantic.onVerifiedContainer,
                  )
                }
                doc.detectedVendorOrIssuer?.let { vendor ->
                  StatusPill(
                    text = vendor,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                doc.detectedReferenceId?.let { refId ->
                  StatusPill(
                    text = refId,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                doc.detectedDate?.let { d ->
                  StatusPill(
                    text = d,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              if (!doc.ocrConfidenceLabel.isNullOrBlank()) {
                Text(
                  text = doc.ocrConfidenceLabel,
                  style = MonoTelemetryStyle,
                  color = semantic.verified,
                )
              }
            }
          }
        }
      }

      // Firebase Cloud Messaging & Push Notifications Card
      item {
        val semantic = PulseTheme.semantic
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Filled.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text("Push Notifications (FCM)", style = MaterialTheme.typography.titleMedium)
              }
              IconButton(
                onClick = viewModel::refreshFcmToken,
                modifier = Modifier.testTag("refresh_fcm_button"),
              ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh FCM token")
              }
            }

            StatusPill(
              text = fcmStatus.registrationState,
              containerColor =
                if (fcmStatus.isFirebaseConfigured) semantic.verifiedContainer
                else semantic.warningContainer,
              contentColor =
                if (fcmStatus.isFirebaseConfigured) semantic.onVerifiedContainer
                else semantic.onWarningContainer,
            )

            if (!fcmStatus.fcmToken.isNullOrBlank()) {
              Text(
                text = "FCM Token: ${fcmStatus.fcmToken!!.take(20)}…",
                style = MonoTelemetryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              OutlinedButton(
                onClick = {
                  notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                modifier = Modifier.fillMaxWidth().testTag("enable_notifications_button"),
              ) {
                Text("Allow Push Notifications (Android 13+)")
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Leave Request Status Updates", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Topic: leave_updates • Alerts on approvals & rejections",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = fcmStatus.leaveAlertsEnabled,
                onCheckedChange = viewModel::setLeaveAlertsEnabled,
                modifier = Modifier.testTag("switch_leave_fcm_alerts"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Payroll Processing Alerts", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Topic: payroll_alerts • Payslip, SSF & TDS cycle notices",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = fcmStatus.payrollAlertsEnabled,
                onCheckedChange = viewModel::setPayrollAlertsEnabled,
                modifier = Modifier.testTag("switch_payroll_fcm_alerts"),
              )
            }
          }
        }
      }

      // Security & Compliance Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Filled.Security, contentDescription = null, tint = PulseTheme.semantic.verified)
              Text("Enterprise Security Posture", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text =
                "• Device Binding: ${session.deviceId} • Scope: ${session.branchScope}\n" +
                  "• Keystore AES-256 GCM EncryptedSharedPreferences (24h offline token window)\n" +
                  "• OkHttp TLS Certificate Pinning + Auto JWT Refresh\n" +
                  "• Auto-lock after 2 min in background; sensitive docs & payslips blocked offline",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = onChangePin,
                modifier = Modifier.weight(1f).testTag("change_pin_button"),
              ) {
                Text("Update PIN")
              }
              OutlinedButton(
                onClick = onLockNow,
                modifier = Modifier.weight(1f).testTag("profile_lock_now_button"),
              ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lock App")
              }
            }
            OutlinedButton(
              onClick = onOpenAuditLog,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("open_audit_log_from_profile_button"),
            ) {
              Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Security & Biometric Audit Log")
            }
            Button(
              onClick = onSignOut,
              colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
              ),
              modifier = Modifier.fillMaxWidth().testTag("profile_signout_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign Out")
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }
    }
  }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentViewerDialog(
  document: EmployeeDocument,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  var pdfPageIndex by remember(document.id) { mutableIntStateOf(0) }
  var pdfPageCount by remember(document.id) { mutableIntStateOf(0) }
  var pdfBitmap by remember(document.id) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(document.id, pdfPageIndex) {
    if (document.mimeType == "application/pdf" && document.localUri.isNotBlank()) {
      val rendered =
        withContext(Dispatchers.IO) {
          runCatching {
            val uri = Uri.parse(document.localUri)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
              PdfRenderer(pfd).use { renderer ->
                pdfPageCount = renderer.pageCount
                val idx = pdfPageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                if (renderer.pageCount > 0) {
                  renderer.openPage(idx).use { page ->
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                  }
                } else null
              }
            }
          }.getOrNull()
        }
      pdfBitmap = rendered
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(document.title) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text =
            "${document.documentType.label} • ${document.mimeType} • ${String.format(Locale.US, "%.2f MB", document.fileSizeBytes / (1024.0 * 1024.0))}",
          style = MonoTelemetryStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
          document.mimeType.startsWith("image/") && document.localUri.isNotBlank() -> {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(260.dp)
                  .background(Color(0xFF0F172A), MaterialTheme.shapes.medium),
              contentAlignment = Alignment.Center,
            ) {
              AsyncImage(
                model = document.localUri,
                contentDescription = document.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
              )
            }
          }
          pdfBitmap != null -> {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(260.dp)
                  .background(Color(0xFF0F172A), MaterialTheme.shapes.medium),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                bitmap = pdfBitmap!!.asImageBitmap(),
                contentDescription = "${document.title} page ${pdfPageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
              )
            }
            if (pdfPageCount > 1) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                TextButton(
                  onClick = { if (pdfPageIndex > 0) pdfPageIndex-- },
                  enabled = pdfPageIndex > 0,
                ) {
                  Text("Previous Page")
                }
                Text("Page ${pdfPageIndex + 1} / $pdfPageCount", style = MaterialTheme.typography.labelMedium)
                TextButton(
                  onClick = { if (pdfPageIndex + 1 < pdfPageCount) pdfPageIndex++ },
                  enabled = pdfPageIndex + 1 < pdfPageCount,
                ) {
                  Text("Next Page")
                }
              }
            }
          }
          else -> {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Archived Vault Record", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "File: ${document.fileName}\nUploaded: ${document.uploadedAt}\nStatus: ${document.verificationStatus}",
                  style = MonoTelemetryStyle,
                )
              }
            }
          }
        }

        val hasOcrFields =
          !document.extractedText.isNullOrBlank() ||
            !document.detectedVendorOrIssuer.isNullOrBlank() ||
            !document.detectedAmount.isNullOrBlank() ||
            !document.detectedDate.isNullOrBlank() ||
            !document.detectedReferenceId.isNullOrBlank()

        if (hasOcrFields) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth().testTag("viewer_ocr_panel"),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text = "ML Kit Extracted Data",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
                if (!document.extractedText.isNullOrBlank()) {
                  IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(document.extractedText)) },
                    modifier = Modifier.testTag("viewer_copy_ocr_button"),
                  ) {
                    Icon(
                      Icons.Filled.ContentCopy,
                      contentDescription = "Copy extracted text",
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }

              document.detectedVendorOrIssuer?.let { ProfileInfoRow("Vendor / Issuer", it) }
              document.detectedAmount?.let { ProfileInfoRow("Detected Amount", it) }
              document.detectedDate?.let { ProfileInfoRow("Document Date", it) }
              document.detectedReferenceId?.let { ProfileInfoRow("Reference ID", it) }

              if (!document.extractedText.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                  text = document.extractedText,
                  style = MonoTelemetryStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss, modifier = Modifier.testTag("close_doc_viewer_button")) {
        Text("Close")
      }
    },
  )
}
