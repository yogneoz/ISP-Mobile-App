package com.example.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.DocumentOcrMetadata
import com.example.domain.model.DocumentType
import com.example.domain.model.EmployeeDocument
import com.example.domain.model.EmployeeProfile
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.RepoResult
import com.example.notifications.FcmStatus
import com.example.notifications.FcmTokenManager
import com.example.notifications.PulseHrNotificationHelper
import com.example.scanner.DocumentScanProcessor
import com.example.scanner.DocumentTextAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ProfileViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val employeeRepository: EmployeeRepository,
  private val documentRepository: DocumentRepository,
  private val documentTextAnalyzer: DocumentTextAnalyzer,
  private val fcmTokenManager: FcmTokenManager,
  private val notificationHelper: PulseHrNotificationHelper,
) : ViewModel() {

  val profile: StateFlow<EmployeeProfile?> =
    employeeRepository
      .observeEmployeeProfile()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val documents: StateFlow<List<EmployeeDocument>> =
    documentRepository
      .observeDocuments()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _vaultSearchQuery = MutableStateFlow("")
  val vaultSearchQuery: StateFlow<String> = _vaultSearchQuery.asStateFlow()

  private val _vaultCategoryFilter = MutableStateFlow<DocumentType?>(null)
  val vaultCategoryFilter: StateFlow<DocumentType?> = _vaultCategoryFilter.asStateFlow()

  val filteredDocuments: StateFlow<List<EmployeeDocument>> =
    combine(documents, _vaultSearchQuery, _vaultCategoryFilter) { docs, query, categoryFilter ->
      val q = query.trim().lowercase(Locale.US)
      docs.filter { doc ->
        val matchesCategory = categoryFilter == null || doc.documentType == categoryFilter
        val matchesQuery =
          q.isBlank() ||
            doc.title.lowercase(Locale.US).contains(q) ||
            doc.fileName.lowercase(Locale.US).contains(q) ||
            doc.documentType.label.lowercase(Locale.US).contains(q) ||
            doc.extractedText.orEmpty().lowercase(Locale.US).contains(q) ||
            doc.detectedVendorOrIssuer.orEmpty().lowercase(Locale.US).contains(q) ||
            doc.detectedReferenceId.orEmpty().lowercase(Locale.US).contains(q) ||
            doc.detectedAmount.orEmpty().lowercase(Locale.US).contains(q)
        matchesCategory && matchesQuery
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val fcmStatus: StateFlow<FcmStatus> = fcmTokenManager.fcmStatus

  private val _hasNotificationPermission =
    MutableStateFlow(notificationHelper.hasNotificationPermission())
  val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

  private val _feedback = MutableStateFlow<Pair<String, Boolean>?>(null) // message to isError
  val feedback: StateFlow<Pair<String, Boolean>?> = _feedback.asStateFlow()

  fun setVaultSearchQuery(query: String) {
    _vaultSearchQuery.value = query
  }

  fun setVaultCategoryFilter(type: DocumentType?) {
    _vaultCategoryFilter.value = type
  }

  fun refreshNotificationPermission() {
    _hasNotificationPermission.value = notificationHelper.hasNotificationPermission()
  }

  fun setLeaveAlertsEnabled(enabled: Boolean) {
    fcmTokenManager.setLeaveAlertsEnabled(enabled)
  }

  fun setPayrollAlertsEnabled(enabled: Boolean) {
    fcmTokenManager.setPayrollAlertsEnabled(enabled)
  }

  fun refreshFcmToken() {
    fcmTokenManager.initializeAndSyncToken()
  }

  fun updateContact(phone: String, emergencyName: String, emergencyPhone: String) {
    viewModelScope.launch {
      when (
        val res =
          employeeRepository.updateContactDetails(phone, emergencyName, emergencyPhone)
      ) {
        is RepoResult.Success -> _feedback.value = "Contact details updated." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun updateAvatarPhoto(uri: Uri) {
    viewModelScope.launch {
      when (val res = employeeRepository.updateProfilePhoto(uri)) {
        is RepoResult.Success -> _feedback.value = "Profile photo updated." to false
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun uploadDocument(
    uri: Uri,
    title: String,
    type: DocumentType,
    autoCategorizeFromOcr: Boolean = true,
  ) {
    viewModelScope.launch {
      val mimeType = context.contentResolver.getType(uri).orEmpty()
      var resolvedTitle = title
      var resolvedType = type
      var ocrMetadata: DocumentOcrMetadata? = null

      if (mimeType.startsWith("image/")) {
        val extraction =
          withContext(Dispatchers.IO) {
            runCatching {
              val bmp = DocumentScanProcessor.decodeScaledBitmapFromUri(context, uri)
              if (bmp != null) {
                try {
                  documentTextAnalyzer.analyzeBitmap(bmp)
                } finally {
                  bmp.recycle()
                }
              } else {
                null
              }
            }.getOrNull()
          }

        if (extraction != null && extraction.hasRecognizedText) {
          ocrMetadata = extraction.toOcrMetadata()
          if (resolvedTitle.isBlank()) {
            resolvedTitle = extraction.suggestedTitle
          }
          if (autoCategorizeFromOcr && type == DocumentType.COMPLIANCE) {
            resolvedType = extraction.suggestedCategory
          }
        }
      }

      when (val res = documentRepository.uploadDocument(uri, resolvedTitle, resolvedType, ocrMetadata)) {
        is RepoResult.Success -> {
          val ocrSuffix =
            if (ocrMetadata?.extractedText != null) " • Auto-tagged as ${resolvedType.label}"
            else ""
          _feedback.value =
            "Uploaded ${res.data.fileName} (${formatBytes(res.data.fileSizeBytes)})$ocrSuffix." to false
        }
        is RepoResult.Error -> _feedback.value = res.message to true
      }
    }
  }

  fun deleteDocument(documentId: String) {
    viewModelScope.launch {
      documentRepository.deleteDocument(documentId)
      _feedback.value = "Document removed." to false
    }
  }

  fun clearFeedback() {
    _feedback.value = null
  }

  private fun formatBytes(bytes: Long): String {
    return if (bytes >= 1024 * 1024) {
      String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    } else {
      "${(bytes / 1024).coerceAtLeast(1)} KB"
    }
  }
}
