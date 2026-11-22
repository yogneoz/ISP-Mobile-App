package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EmployeeDocumentEntity
import com.example.data.local.PulseHrDatabase
import com.example.data.remote.PulseHrApiService
import com.example.data.repository.DocumentRepositoryImpl
import com.example.domain.model.AppCapability
import com.example.domain.model.DocumentType
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.notifications.FcmTokenManager
import com.example.notifications.PulseHrNotificationHelper
import com.example.scanner.DocumentOcrExtraction
import com.example.scanner.DocumentOcrParser
import com.example.scanner.DocumentTextAnalyzer
import com.example.scanner.LiveOcrHint
import com.example.scanner.ScanExportFormat
import com.example.security.SecureTokenStore
import com.example.ui.screens.profile.ProfileViewModel
import com.example.ui.screens.scanner.DocumentScannerViewModel
import com.example.ui.screens.scanner.ScannerStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
class DocumentOcrAnalyzerTest {

  private lateinit var context: Context
  private lateinit var db: PulseHrDatabase
  private lateinit var tokenStore: SecureTokenStore
  private lateinit var documentRepo: DocumentRepositoryImpl

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, PulseHrDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val prefs = context.getSharedPreferences("doc_ocr_test_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    tokenStore = SecureTokenStore.createForTesting(prefs)
    tokenStore.saveAuthSession(
      accessToken = "tok_ocr",
      refreshToken = "ref_ocr",
      expiresAtEpochMs = System.currentTimeMillis() + 3600_000L,
      session =
        UserSession(
          userId = "usr_maya",
          email = "maya.shrestha@pulsehr.io",
          role = UserRole.HR_SELF_SERVICE,
          employeeId = "emp_103",
          isAuthenticated = true,
          capabilities = AppCapability.entries.toSet(),
          branchScope = "MV-HQ",
          deviceId = "dev_and_ocr1",
        ),
    )
    documentRepo = DocumentRepositoryImpl(context, db.pulseHrDao(), tokenStore)
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  @Test
  fun `parseAndCategorize classifies physical receipt and extracts vendor total date and ref`() {
    val receiptText =
      """
      CHEVRON STATION #4102
      1420 El Camino Real
      Receipt #RC-88241
      Date: 2026-11-21
      Fuel Unleaded 10.50 Gal
      Subtotal $40.00
      Sales Tax $4.20
      GRAND TOTAL $44.20
      VISA **** 9012
      Thank you for your business
      """.trimIndent()

    val parsed = DocumentOcrParser.parseAndCategorize(receiptText)
    assertEquals(DocumentType.RECEIPT, parsed.suggestedCategory)
    assertEquals("CHEVRON STATION #4102", parsed.vendorOrIssuer)
    assertEquals("$44.20", parsed.detectedAmount)
    assertEquals("2026-11-21", parsed.detectedDate)
    assertEquals("RC-88241", parsed.detectedReferenceId)
    assertTrue(parsed.confidenceScore >= 0.70f)
    assertTrue(parsed.suggestedTitle.contains("CHEVRON STATION"))
  }

  @Test
  fun `parseAndCategorize classifies training certificate with issuer date and cert ID`() {
    val certText =
      """
      NATIONAL SAFETY COUNCIL
      CERTIFICATE OF COMPLETION
      This is to certify that Maya Shrestha
      completed OSHA 30-Hour Fiber Field Safety Training
      Cert No: CERT-2026-9914
      Issued: Nov 14, 2026
      Valid Thru: Nov 14, 2028
      """.trimIndent()

    val parsed = DocumentOcrParser.parseAndCategorize(certText)
    assertEquals(DocumentType.CERTIFICATE, parsed.suggestedCategory)
    assertEquals("NATIONAL SAFETY COUNCIL", parsed.vendorOrIssuer)
    assertEquals("CERT-2026-9914", parsed.detectedReferenceId)
    assertEquals("Nov 14, 2026", parsed.detectedDate)
    assertTrue(parsed.confidenceScore >= 0.70f)
  }

  @Test
  fun `parseAndCategorize classifies HR onboarding or reimbursement form`() {
    val formText =
      """
      PulseHR People Operations
      Employee Reimbursement Form
      Form ID: HRF-4082
      Employee ID: emp_103
      Department: FTTH Field Engineering
      Applicant Signature: Maya Shrestha
      Date: 11/21/2026
      """.trimIndent()

    val parsed = DocumentOcrParser.parseAndCategorize(formText)
    assertEquals(DocumentType.HR_FORM, parsed.suggestedCategory)
    assertEquals("HRF-4082", parsed.detectedReferenceId)
    assertEquals("11/21/2026", parsed.detectedDate)
  }

  @Test
  fun `parseAndCategorize classifies Tax PAN card`() {
    val panText =
      """
      INLAND REVENUE DEPARTMENT
      PERMANENT ACCOUNT NUMBER (PAN)
      PAN No: 604-882-194
      Issued Date: 2026-03-04
      """.trimIndent()

    val parsed = DocumentOcrParser.parseAndCategorize(panText)
    assertEquals(DocumentType.TAX_PAN, parsed.suggestedCategory)
    assertEquals("604-882-194", parsed.detectedReferenceId)
    assertEquals("2026-03-04", parsed.detectedDate)
  }

  @Test
  fun `DocumentScannerViewModel auto-categorizes scan via ML Kit analyzer and persists OCR fields`() =
    runBlocking {
      val fakeText =
        """
        SHELL FLEET FUEL
        Receipt #SH-55210
        Date: 2026-11-21
        Total Due $62.50
        """.trimIndent()
      val fakeAnalyzer = FakeDocumentTextAnalyzer(DocumentOcrParser.parseAndCategorize(fakeText))
      val vm =
        DocumentScannerViewModel(
          context = context,
          documentRepository = documentRepo,
          documentTextAnalyzer = fakeAnalyzer,
        )

      val bmp = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
      Canvas(bmp).drawColor(Color.WHITE)

      vm.onCapturedBitmap(bmp)
      vm.uiState.first { it.stage == ScannerStage.CROP_ADJUST }

      vm.applyCropAndEnhance()
      val reviewState = vm.uiState.first { it.stage == ScannerStage.FILTER_REVIEW && it.ocrExtraction != null }
      assertEquals(DocumentType.RECEIPT, reviewState.documentType)
      assertNotNull(reviewState.ocrExtraction)
      assertEquals("$62.50", reviewState.ocrExtraction?.detectedAmount)
      assertTrue(reviewState.documentTitle.contains("SHELL FLEET FUEL"))

      vm.updateExportFormat(ScanExportFormat.JPEG)
      vm.saveToDocumentVault()
      val savedState = vm.uiState.first { it.savedSuccessMessage != null || it.errorMessage != null }
      assertEquals(null, savedState.errorMessage)
      assertNotNull(savedState.savedSuccessMessage)

      val docs = documentRepo.observeDocuments().first()
      assertEquals(1, docs.size)
      val savedDoc = docs.first()
      assertEquals(DocumentType.RECEIPT, savedDoc.documentType)
      assertEquals("$62.50", savedDoc.detectedAmount)
      assertEquals("SH-55210", savedDoc.detectedReferenceId)
      assertEquals("2026-11-21", savedDoc.detectedDate)
      assertTrue(savedDoc.extractedText.orEmpty().contains("SHELL FLEET FUEL"))
    }

  @Test
  fun `ProfileViewModel filters vault documents by category and OCR extracted text`() = runTest {
    db.pulseHrDao().insertDocuments(
      listOf(
        EmployeeDocumentEntity(
          id = "doc_r1",
          employeeId = "emp_103",
          title = "Shell Fleet Fuel Receipt",
          documentType = DocumentType.RECEIPT.name,
          fileName = "shell.pdf",
          mimeType = "application/pdf",
          fileSizeBytes = 22_000L,
          uploadedAt = "2026-11-21",
          localUri = "",
          verificationStatus = "PENDING_REVIEW",
          extractedText = "SHELL FLEET FUEL\nTotal Due $62.50",
          detectedVendorOrIssuer = "SHELL FLEET FUEL",
          detectedDate = "2026-11-21",
          detectedAmount = "$62.50",
          detectedReferenceId = "SH-55210",
          ocrConfidenceLabel = "ML Kit OCR • 92% Expense Receipt",
        ),
        EmployeeDocumentEntity(
          id = "doc_c1",
          employeeId = "emp_103",
          title = "OSHA Fiber Safety Certificate",
          documentType = DocumentType.CERTIFICATE.name,
          fileName = "osha.pdf",
          mimeType = "application/pdf",
          fileSizeBytes = 41_000L,
          uploadedAt = "2026-11-14",
          localUri = "",
          verificationStatus = "VERIFIED",
          extractedText = "NATIONAL SAFETY COUNCIL\nOSHA 30-Hour Fiber Safety",
          detectedVendorOrIssuer = "NATIONAL SAFETY COUNCIL",
          detectedDate = "2026-11-14",
          detectedReferenceId = "CERT-2026-9914",
          ocrConfidenceLabel = "ML Kit OCR • 95% Certificate / License",
        ),
      )
    )

    val repoMockEmployee = FakeEmployeeRepository()
    val helper = PulseHrNotificationHelper(context)
    val fakeApi =
      java.lang.reflect.Proxy.newProxyInstance(
        PulseHrApiService::class.java.classLoader,
        arrayOf(PulseHrApiService::class.java),
      ) { _, _, _ -> throw UnsupportedOperationException("Unused in test") } as PulseHrApiService
    val fcm = FcmTokenManager(context, tokenStore, fakeApi)
    val vm =
      ProfileViewModel(
        context = context,
        employeeRepository = repoMockEmployee,
        documentRepository = documentRepo,
        documentTextAnalyzer = FakeDocumentTextAnalyzer(DocumentOcrParser.emptyResult()),
        fcmTokenManager = fcm,
        notificationHelper = helper,
      )

    val job = launch { vm.filteredDocuments.collect {} }
    val allDocs = vm.filteredDocuments.first { it.size == 2 }
    assertEquals(2, allDocs.size)

    vm.setVaultCategoryFilter(DocumentType.CERTIFICATE)
    assertEquals(listOf("doc_c1"), vm.filteredDocuments.value.map { it.id })

    vm.setVaultCategoryFilter(null)
    vm.setVaultSearchQuery("$62.50")
    assertEquals(listOf("doc_r1"), vm.filteredDocuments.value.map { it.id })

    vm.setVaultSearchQuery("OSHA")
    assertEquals(listOf("doc_c1"), vm.filteredDocuments.value.map { it.id })
    job.cancel()
  }

  private class FakeDocumentTextAnalyzer(
    private val result: DocumentOcrExtraction,
  ) : DocumentTextAnalyzer {
    override suspend fun analyzeBitmap(bitmap: Bitmap): DocumentOcrExtraction = result

    override suspend fun detectLiveTextHint(bitmap: Bitmap): LiveOcrHint =
      LiveOcrHint(blockCount = result.blockCount, lineCount = result.lineCount, categoryHint = result.suggestedCategory)
  }

  private class FakeEmployeeRepository : com.example.domain.repository.EmployeeRepository {
    override fun observeEmployeeProfile() = kotlinx.coroutines.flow.MutableStateFlow(null)
    override fun observeBranches() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.domain.model.BranchLocation>())
    override fun observeAnnouncements() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.domain.model.CompanyAnnouncement>())
    override fun observeUpcomingHolidays() = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.example.domain.model.HolidaySchedule>())
    override suspend fun refreshEmployeeData() = com.example.domain.repository.RepoResult.Success(Unit)
    override suspend fun ensureSessionDataSeeded() = Unit
    override suspend fun switchAssignedBranch(branchId: String) = com.example.domain.repository.RepoResult.Error("Unused")
    override suspend fun calibrateBranchGeofence(branchId: String, latitude: Double, longitude: Double, radiusMeters: Double) =
      com.example.domain.repository.RepoResult.Error("Unused")
    override suspend fun updateContactDetails(phone: String, emergencyContactName: String, emergencyContactPhone: String) =
      com.example.domain.repository.RepoResult.Error("Unused")
    override suspend fun updateProfilePhoto(uri: Uri) = com.example.domain.repository.RepoResult.Error("Unused")
  }
}
