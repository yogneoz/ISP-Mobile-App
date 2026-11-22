package com.example

import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AuditLogEntity
import com.example.data.local.PulseHrDatabase
import com.example.data.seed.EnterpriseDatabaseSeeder
import com.example.domain.model.AppCapability
import com.example.domain.model.ApprovalItem
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditOutcome
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.ClaimCategory
import com.example.domain.model.DelegationRule
import com.example.domain.model.EmployeeShift
import com.example.domain.model.ExpenseClaim
import com.example.domain.model.NeedsAttentionItem
import com.example.domain.model.RosterEntry
import com.example.domain.model.StockAuditRow
import com.example.domain.model.UserRole
import com.example.domain.model.UserSession
import com.example.domain.model.VanStockItem
import com.example.domain.model.WorkOrder
import com.example.domain.repository.RepoResult
import com.example.domain.repository.WorkplaceRepository
import com.example.security.BiometricAuditRecorder
import com.example.security.BiometricAuthOutcome
import com.example.security.BiometricAvailability
import com.example.security.BiometricCapability
import com.example.security.BiometricPromptInterface
import com.example.security.SecureTokenStore
import com.example.ui.screens.security.AuditLogFilter
import com.example.ui.screens.security.AuditLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class AuditLogTest {

  private lateinit var context: Context
  private lateinit var db: PulseHrDatabase
  private lateinit var tokenStore: SecureTokenStore
  private lateinit var recorder: BiometricAuditRecorder

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, PulseHrDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val prefs = context.getSharedPreferences("audit_log_test_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    tokenStore = SecureTokenStore.createForTesting(prefs)
    tokenStore.saveAuthSession(
      accessToken = "tok_test",
      refreshToken = "ref_test",
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
          deviceId = "dev_and_test99",
        ),
    )
    recorder = BiometricAuditRecorder(dao = db.pulseHrDao(), tokenStore = tokenStore)
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  @Test
  fun `recordEventSuspend persists biometric success failed and device metadata in descending order`() =
    runBlocking {
      val dao = db.pulseHrDao()

      recorder.recordEventSuspend(
        eventCategory = "BIOMETRIC_AUTH",
        screenName = "APP_LOCK",
        actionLabel = "Biometric verified • Unlock PulseHR",
        outcome = AuditOutcome.SUCCESS,
        authMethod = "BIOMETRIC_STRONG",
        failureReason = null,
        timestampEpochMs = 1_700_000_001_000L,
      )
      recorder.recordEventSuspend(
        eventCategory = "BIOMETRIC_AUTH",
        screenName = "PAYSLIP_VIEW",
        actionLabel = "Biometric authentication failed • View Payslip",
        outcome = AuditOutcome.FAILED,
        authMethod = "BIOMETRIC_STRONG",
        failureReason = "Biometric sample did not match enrolled credential",
        timestampEpochMs = 1_700_000_002_000L,
      )
      recorder.recordEventSuspend(
        eventCategory = "PIN_FALLBACK",
        screenName = "PIN_VERIFICATION",
        actionLabel = "App PIN verified • View Payslip",
        outcome = AuditOutcome.SUCCESS,
        authMethod = "PIN_FALLBACK",
        failureReason = null,
        timestampEpochMs = 1_700_000_003_000L,
      )

      val stored = dao.observeAuditLogs().first()
      assertEquals(3, stored.size)

      // Newest first (createdAtEpochMs DESC)
      assertEquals("PIN_FALLBACK", stored[0].eventCategory)
      assertEquals("SUCCESS", stored[0].outcome)
      assertEquals("PIN_FALLBACK", stored[0].authMethod)
      assertNull(stored[0].failureReason)

      val failedRow = stored[1]
      assertEquals("BIOMETRIC_AUTH", failedRow.eventCategory)
      assertEquals("FAILED", failedRow.outcome)
      assertEquals("BIOMETRIC_STRONG", failedRow.authMethod)
      assertEquals(
        "Biometric sample did not match enrolled credential",
        failedRow.failureReason,
      )
      assertEquals("dev_and_test99", failedRow.deviceId)
      assertEquals("maya.shrestha@pulsehr.io", failedRow.actorEmail)
      assertTrue(failedRow.deviceModel.contains("Android"))
      assertTrue(failedRow.deviceModel.contains("API"))
      assertTrue(failedRow.timestampLabel.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))

      assertEquals("BIOMETRIC_AUTH", stored[2].eventCategory)
      assertEquals("SUCCESS", stored[2].outcome)
    }

  @Test
  fun `seeder populates both successful and failed biometric audit history`() = runBlocking {
    val dao = db.pulseHrDao()
    val seeder = EnterpriseDatabaseSeeder(dao)
    val session = tokenStore.getCurrentSession()!!

    seeder.seedForSession(session)

    val seededLogs = dao.observeAuditLogs().first()
    val biometricLogs = seededLogs.filter { it.eventCategory == "BIOMETRIC_AUTH" }
    assertTrue("Expected seeded biometric entries", biometricLogs.size >= 2)
    assertTrue(biometricLogs.any { it.outcome == "SUCCESS" })
    assertTrue(
      biometricLogs.any { it.outcome == "FAILED" && !it.failureReason.isNullOrBlank() }
    )
    assertTrue(seededLogs.all { it.deviceModel.isNotBlank() && it.deviceId.isNotBlank() })
  }

  @Test
  fun `AuditLogViewModel summarizes outcomes and applies all filters accurately`() = runTest {
    val entries =
      listOf(
        AuditLogEntity(
          id = "aud_1",
          actorId = "emp_103",
          actorEmail = "maya@pulsehr.io",
          deviceId = "dev_and_test99",
          screenName = "APP_LOCK",
          actionLabel = "Biometric verified • Unlock PulseHR",
          timestampLabel = "2026-11-21 09:00:00",
          createdAtEpochMs = 4_000L,
          eventCategory = "BIOMETRIC_AUTH",
          outcome = "SUCCESS",
          authMethod = "BIOMETRIC_STRONG",
          deviceModel = "Google Pixel 9 • Android 16 (API 36)",
          failureReason = null,
        ),
        AuditLogEntity(
          id = "aud_2",
          actorId = "emp_103",
          actorEmail = "maya@pulsehr.io",
          deviceId = "dev_and_test99",
          screenName = "PAYSLIP_VIEW",
          actionLabel = "Biometric authentication failed • View Payslip",
          timestampLabel = "2026-11-21 08:45:00",
          createdAtEpochMs = 3_000L,
          eventCategory = "BIOMETRIC_AUTH",
          outcome = "FAILED",
          authMethod = "BIOMETRIC_STRONG",
          deviceModel = "Google Pixel 9 • Android 16 (API 36)",
          failureReason = "Unrecognized fingerprint sample",
        ),
        AuditLogEntity(
          id = "aud_3",
          actorId = "emp_103",
          actorEmail = "maya@pulsehr.io",
          deviceId = "dev_and_test99",
          screenName = "BIOMETRIC_PROMPT",
          actionLabel = "Switched to App PIN fallback • View Payslip",
          timestampLabel = "2026-11-21 08:44:30",
          createdAtEpochMs = 2_000L,
          eventCategory = "BIOMETRIC_AUTH",
          outcome = "FALLBACK",
          authMethod = "PIN_FALLBACK",
          deviceModel = "Google Pixel 9 • Android 16 (API 36)",
          failureReason = "User selected App PIN fallback",
        ),
        AuditLogEntity(
          id = "aud_4",
          actorId = "emp_103",
          actorEmail = "maya@pulsehr.io",
          deviceId = "dev_and_test99",
          screenName = "DEVICE_BINDING",
          actionLabel = "Device registered in AES-256 Keystore",
          timestampLabel = "2026-11-20 18:00:00",
          createdAtEpochMs = 1_000L,
          eventCategory = "DEVICE_BINDING",
          outcome = "INFO",
          authMethod = "SYSTEM",
          deviceModel = "Google Pixel 9 • Android 16 (API 36)",
          failureReason = null,
        ),
      )

    db.pulseHrDao().insertAuditLogs(entries)

    val repo = DaoBackedAuditWorkplaceRepository(db)
    val fakePrompt = FakeBiometricPrompt(BiometricAvailability.AVAILABLE_STRONG)
    val vm =
      AuditLogViewModel(
        context = context,
        workplaceRepository = repo,
        auditRecorder = recorder,
        tokenStore = tokenStore,
        biometricPrompt = fakePrompt,
      )

    val collectLogs = launch { vm.filteredLogs.collect {} }
    val collectSummary = launch { vm.summary.collect {} }

    val summary = vm.summary.first { it.totalEvents == 4 }
    assertEquals(1, summary.biometricSuccessCount)
    assertEquals(1, summary.biometricFailedCount)
    assertEquals(1, summary.fallbackOrCancelledCount)
    assertEquals("dev_and_test99", summary.deviceId)
    assertEquals(BiometricAvailability.AVAILABLE_STRONG, summary.biometricAvailability)
    assertTrue(summary.deviceModel.isNotBlank())

    assertEquals(4, vm.filteredLogs.value.size)

    vm.setFilter(AuditLogFilter.BIOMETRIC_ONLY)
    assertEquals(3, vm.filteredLogs.value.size)

    vm.setFilter(AuditLogFilter.SUCCESS)
    assertEquals(listOf("aud_1"), vm.filteredLogs.value.map { it.id })

    vm.setFilter(AuditLogFilter.FAILED)
    val failedOnly = vm.filteredLogs.value
    assertEquals(listOf("aud_2"), failedOnly.map { it.id })
    assertNotNull(failedOnly.first().failureReason)

    vm.setFilter(AuditLogFilter.FALLBACK)
    assertEquals(listOf("aud_3"), vm.filteredLogs.value.map { it.id })

    vm.setFilter(AuditLogFilter.ALL)
    assertEquals(4, vm.filteredLogs.value.size)

    collectLogs.cancel()
    collectSummary.cancel()
  }

  private class FakeBiometricPrompt(
    private val availability: BiometricAvailability,
  ) : BiometricPromptInterface {
    override fun getAvailabilityStatus(context: Context): BiometricAvailability = availability

    override fun getCapability(context: Context): BiometricCapability =
      BiometricCapability.STRONG_BIOMETRIC

    override fun canAuthenticateWithBiometrics(context: Context): Boolean = true

    override fun hasEnrolledFingerprintOrFace(context: Context): Boolean = true

    override suspend fun authenticateForAppEntry(
      activity: FragmentActivity,
      employeeEmail: String,
    ): BiometricAuthOutcome = BiometricAuthOutcome.Succeeded

    override suspend fun authenticate(
      activity: FragmentActivity,
      title: String,
      subtitle: String,
      negativeButtonText: String,
      confirmationRequired: Boolean,
    ): BiometricAuthOutcome = BiometricAuthOutcome.Succeeded

    override fun authenticate(
      activity: FragmentActivity,
      purpose: AuthChallengePurpose,
      onSuccess: () -> Unit,
      onUsePinFallback: () -> Unit,
      onError: (String) -> Unit,
    ) {
      onSuccess()
    }
  }

  private class DaoBackedAuditWorkplaceRepository(
    private val db: PulseHrDatabase,
  ) : WorkplaceRepository {
    override fun observeAuditLogs(): Flow<List<AuditLogEntry>> =
      db.pulseHrDao().observeAuditLogs().map { list ->
        list.map {
          AuditLogEntry(
            id = it.id,
            actorId = it.actorId,
            actorEmail = it.actorEmail,
            deviceId = it.deviceId,
            screenName = it.screenName,
            actionLabel = it.actionLabel,
            timestampLabel = it.timestampLabel,
            createdAtEpochMs = it.createdAtEpochMs,
            eventCategory = it.eventCategory,
            outcome = runCatching { AuditOutcome.valueOf(it.outcome) }.getOrDefault(AuditOutcome.INFO),
            authMethod = it.authMethod,
            deviceModel = it.deviceModel,
            failureReason = it.failureReason,
          )
        }
      }

    override fun observeWorkOrders(): Flow<List<WorkOrder>> = MutableStateFlow(emptyList())
    override fun observeVanStock(): Flow<List<VanStockItem>> = MutableStateFlow(emptyList())
    override fun observeClaims(): Flow<List<ExpenseClaim>> = MutableStateFlow(emptyList())
    override fun observeApprovalItems(): Flow<List<ApprovalItem>> = MutableStateFlow(emptyList())
    override fun observeDelegations(): Flow<List<DelegationRule>> = MutableStateFlow(emptyList())
    override fun observeRosterEntries(): Flow<List<RosterEntry>> = MutableStateFlow(emptyList())
    override fun observeNeedsAttention(): Flow<List<NeedsAttentionItem>> = MutableStateFlow(emptyList())
    override fun observeStockAuditRows(): Flow<List<StockAuditRow>> = MutableStateFlow(emptyList())
    override fun observeEmployeeShifts(): Flow<List<EmployeeShift>> = MutableStateFlow(emptyList())
    override suspend fun syncShiftsFromBootstrap(shifts: List<EmployeeShift>): RepoResult<Unit> = RepoResult.Success(Unit)
    override suspend fun completeWorkOrder(
      workOrderId: String,
      opticalPowerDbm: Double,
      otdrLossDb: Double,
      customerSignatureName: String,
      consumedCableMeters: Int,
      serialUsed: String?,
      photoCount: Int,
      poleRouteNote: String?,
    ): RepoResult<WorkOrder> = RepoResult.Error("Unused in AuditLogTest")
    override suspend fun addPoleRouteLog(workOrderId: String, note: String): RepoResult<Unit> = RepoResult.Success(Unit)
    override suspend fun requestVanRestock(itemId: String, addQty: Int): RepoResult<Unit> = RepoResult.Success(Unit)
    override suspend fun submitExpenseClaim(
      category: ClaimCategory,
      amount: Double,
      ticketCode: String?,
      description: String,
      receiptAttached: Boolean,
    ): RepoResult<ExpenseClaim> = RepoResult.Error("Unused in AuditLogTest")
    override suspend fun actOnApprovalItem(approvalId: String, approve: Boolean, note: String): RepoResult<Unit> = RepoResult.Success(Unit)
    override suspend fun createDelegation(delegateEmployeeId: String, delegateName: String, endDate: String): RepoResult<Unit> = RepoResult.Success(Unit)
    override suspend fun assignDispatchTicket(
      workOrderId: String,
      assigneeEmployeeId: String,
      assigneeName: String,
      overrideLeaveWarning: Boolean,
      overrideReason: String?,
    ): RepoResult<Unit> = RepoResult.Success(Unit)
    override suspend fun resolveNeedsAttentionItem(needsAttentionId: String, correctedSerial: String?): RepoResult<String> = RepoResult.Success("")
    override suspend fun logSensitiveScreenView(screenName: String, actionLabel: String) = Unit
  }
}
