package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.domain.model.ApprovalItem
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.BranchLocation
import com.example.domain.model.ClaimCategory
import com.example.domain.model.CompanyAnnouncement
import com.example.domain.model.DelegationRule
import com.example.domain.model.EmployeeProfile
import com.example.domain.model.EmployeeShift
import com.example.domain.model.ExpenseClaim
import com.example.domain.model.HolidaySchedule
import com.example.domain.model.NeedsAttentionItem
import com.example.domain.model.RosterEntry
import com.example.domain.model.StockAuditRow
import com.example.domain.model.SyncStatus
import com.example.domain.model.VanStockItem
import com.example.domain.model.WorkOrder
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.RepoResult
import com.example.domain.repository.WorkplaceRepository
import com.example.ui.components.DashboardQuickActionMenu
import com.example.ui.components.SubmitExpenseClaimDialog
import com.example.ui.screens.workplace.WorkplaceViewModel
import com.example.ui.theme.PulseHrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DashboardQuickActionTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `quick action fab expands menu and triggers clock in, clock out, and inline expense submission`() {
    var clockInClicks = 0
    var clockOutClicks = 0
    var submittedClaim: Triple<ClaimCategory, String, String>? = null

    composeTestRule.setContent {
      PulseHrTheme {
        var expanded by remember { mutableStateOf(false) }
        var showExpenseDialog by remember { mutableStateOf(false) }

        if (showExpenseDialog) {
          SubmitExpenseClaimDialog(
            onDismiss = { showExpenseDialog = false },
            onSubmit = { category, amount, _, description, _ ->
              submittedClaim = Triple(category, amount, description)
              showExpenseDialog = false
            },
          )
        }

        DashboardQuickActionMenu(
          expanded = expanded,
          onExpandedChange = { expanded = it },
          hasCheckedIn = false,
          hasCheckedOut = false,
          checkInTimeLabel = null,
          checkOutTimeLabel = null,
          onClockInClick = { clockInClicks++ },
          onClockOutClick = { clockOutClicks++ },
          onSubmitExpenseClick = { showExpenseDialog = true },
        )
      }
    }

    // Initially collapsed
    composeTestRule.onNodeWithTag("dashboard_quick_action_fab").assertIsDisplayed()
    composeTestRule.onNodeWithTag("quick_action_clock_in").assertDoesNotExist()
    composeTestRule.onNodeWithTag("quick_action_clock_out").assertDoesNotExist()
    composeTestRule.onNodeWithTag("quick_action_submit_expense").assertDoesNotExist()

    // Expand -> reveals all three Quick Actions
    composeTestRule.onNodeWithTag("dashboard_quick_action_fab").performClick()
    composeTestRule.onNodeWithTag("quick_action_clock_in").assertIsDisplayed()
    composeTestRule.onNodeWithTag("quick_action_clock_out").assertIsDisplayed()
    composeTestRule.onNodeWithTag("quick_action_submit_expense").assertIsDisplayed()
    composeTestRule.onNodeWithText("Close").assertIsDisplayed()

    // Trigger Clock In -> calls callback & collapses
    composeTestRule.onNodeWithTag("quick_action_clock_in").performClick()
    assertEquals(1, clockInClicks)
    composeTestRule.onNodeWithTag("quick_action_clock_in").assertDoesNotExist()

    // Re-expand & Trigger Clock Out
    composeTestRule.onNodeWithTag("dashboard_quick_action_fab").performClick()
    composeTestRule.onNodeWithTag("quick_action_clock_out").performClick()
    assertEquals(1, clockOutClicks)
    composeTestRule.onNodeWithTag("quick_action_clock_out").assertDoesNotExist()

    // Re-expand & Trigger Submit Expense -> opens inline dialog directly
    composeTestRule.onNodeWithTag("dashboard_quick_action_fab").performClick()
    composeTestRule.onNodeWithTag("quick_action_submit_expense").performClick()
    composeTestRule.onNodeWithTag("submit_expense_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("claim_submit_button").assertIsNotEnabled()

    // Fill inline expense form & submit
    composeTestRule.onNodeWithTag("claim_category_chip_MEAL").performClick()
    composeTestRule.onNodeWithTag("claim_amount_input").performTextInput("28.50")
    composeTestRule.onNodeWithTag("claim_desc_input").performTextInput("Field crew lunch")
    composeTestRule.onNodeWithTag("claim_submit_button").assertIsEnabled().performClick()

    assertEquals(Triple(ClaimCategory.MEAL, "28.50", "Field crew lunch"), submittedClaim)
    composeTestRule.onNodeWithTag("submit_expense_dialog").assertDoesNotExist()
  }

  @Test
  fun `tapping scrim dismisses menu and shift subtitles reflect check-in state`() {
    var clockInClicks = 0

    composeTestRule.setContent {
      PulseHrTheme {
        var expanded by remember { mutableStateOf(true) }
        DashboardQuickActionMenu(
          expanded = expanded,
          onExpandedChange = { expanded = it },
          hasCheckedIn = true,
          hasCheckedOut = false,
          checkInTimeLabel = "09:05 AM",
          checkOutTimeLabel = null,
          onClockInClick = { clockInClicks++ },
          onClockOutClick = {},
          onSubmitExpenseClick = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Logged at 09:05 AM").assertIsDisplayed()
    composeTestRule.onNodeWithText("Complete shift & verify punch").assertIsDisplayed()

    composeTestRule.onNodeWithTag("quick_action_scrim").performClick()
    composeTestRule.onNodeWithTag("quick_action_clock_in").assertDoesNotExist()
    assertEquals(0, clockInClicks)
  }

  @Test
  fun `workplace viewmodel submits quick expense claim and exposes dashboard confirmation feedback`() =
    runTest {
      val dispatcher = UnconfinedTestDispatcher(testScheduler)
      Dispatchers.setMain(dispatcher)
      try {
        val repo = FakeQuickExpenseWorkplaceRepository()
        val empRepo = StubQuickEmployeeRepository()
        val vm = WorkplaceViewModel(workplaceRepository = repo, employeeRepository = empRepo)

        vm.submitExpenseClaim(
          category = ClaimCategory.FUEL,
          amountText = "42.75",
          ticketCode = "FTTH-4821",
          description = "Emergency generator fuel run",
          receiptAttached = true,
        )

        assertEquals(1, repo.submittedClaims.size)
        val saved = repo.submittedClaims.first()
        assertEquals(ClaimCategory.FUEL, saved.category)
        assertEquals(42.75, saved.amount, 0.001)
        assertEquals("FTTH-4821", saved.ticketCode)
        assertEquals("Emergency generator fuel run", saved.description)
        assertTrue(saved.receiptAttached)

        val fb = vm.feedback.value
        assertFalse(fb == null)
        assertFalse(fb!!.second)
        assertTrue(fb.first.contains("Fuel claim $42.75 submitted"))
      } finally {
        Dispatchers.resetMain()
      }
    }

  private class StubQuickEmployeeRepository : EmployeeRepository {
    override fun observeEmployeeProfile(): Flow<EmployeeProfile?> = MutableStateFlow(null)
    override fun observeBranches(): Flow<List<BranchLocation>> = MutableStateFlow(emptyList())
    override fun observeAnnouncements(): Flow<List<CompanyAnnouncement>> = MutableStateFlow(emptyList())
    override fun observeUpcomingHolidays(): Flow<List<HolidaySchedule>> = MutableStateFlow(emptyList())
    override suspend fun refreshEmployeeData(): RepoResult<Unit> = RepoResult.Success(Unit)
    override suspend fun ensureSessionDataSeeded() = Unit
    override suspend fun switchAssignedBranch(branchId: String): RepoResult<EmployeeProfile> =
      RepoResult.Error("Unused")
    override suspend fun calibrateBranchGeofence(
      branchId: String,
      latitude: Double,
      longitude: Double,
      radiusMeters: Double,
    ): RepoResult<BranchLocation> = RepoResult.Error("Unused")
    override suspend fun updateContactDetails(
      phone: String,
      emergencyContactName: String,
      emergencyContactPhone: String,
    ): RepoResult<EmployeeProfile> = RepoResult.Error("Unused")
    override suspend fun updateProfilePhoto(uri: Uri): RepoResult<EmployeeProfile> =
      RepoResult.Error("Unused")
  }

  private class FakeQuickExpenseWorkplaceRepository : WorkplaceRepository {
    val submittedClaims = mutableListOf<ExpenseClaim>()

    override fun observeWorkOrders(): Flow<List<WorkOrder>> = MutableStateFlow(emptyList())
    override fun observeVanStock(): Flow<List<VanStockItem>> = MutableStateFlow(emptyList())
    override fun observeClaims(): Flow<List<ExpenseClaim>> = MutableStateFlow(submittedClaims.toList())
    override fun observeApprovalItems(): Flow<List<ApprovalItem>> = MutableStateFlow(emptyList())
    override fun observeDelegations(): Flow<List<DelegationRule>> = MutableStateFlow(emptyList())
    override fun observeRosterEntries(): Flow<List<RosterEntry>> = MutableStateFlow(emptyList())
    override fun observeNeedsAttention(): Flow<List<NeedsAttentionItem>> = MutableStateFlow(emptyList())
    override fun observeAuditLogs(): Flow<List<AuditLogEntry>> = MutableStateFlow(emptyList())
    override fun observeStockAuditRows(): Flow<List<StockAuditRow>> = MutableStateFlow(emptyList())
    override fun observeEmployeeShifts(): Flow<List<EmployeeShift>> = MutableStateFlow(emptyList())
    override suspend fun syncShiftsFromBootstrap(shifts: List<EmployeeShift>): RepoResult<Unit> =
      RepoResult.Success(Unit)

    override suspend fun completeWorkOrder(
      workOrderId: String,
      opticalPowerDbm: Double,
      otdrLossDb: Double,
      customerSignatureName: String,
      consumedCableMeters: Int,
      serialUsed: String?,
      photoCount: Int,
      poleRouteNote: String?,
    ): RepoResult<WorkOrder> = RepoResult.Error("Unused")

    override suspend fun addPoleRouteLog(workOrderId: String, note: String): RepoResult<Unit> =
      RepoResult.Success(Unit)

    override suspend fun requestVanRestock(itemId: String, addQty: Int): RepoResult<Unit> =
      RepoResult.Success(Unit)

    override suspend fun submitExpenseClaim(
      category: ClaimCategory,
      amount: Double,
      ticketCode: String?,
      description: String,
      receiptAttached: Boolean,
    ): RepoResult<ExpenseClaim> {
      val claim =
        ExpenseClaim(
          id = "clm_quick_${submittedClaims.size + 1}",
          employeeId = "emp_103",
          employeeName = "Maya Shrestha",
          category = category,
          amount = amount,
          ticketCode = ticketCode,
          description = description,
          receiptAttached = receiptAttached,
          status = "PENDING",
          submittedAt = "2026-11-21",
          accountingOutboxStatus = "QUEUED",
          syncStatus = SyncStatus.SYNCED,
        )
      submittedClaims += claim
      return RepoResult.Success(claim)
    }

    override suspend fun actOnApprovalItem(
      approvalId: String,
      approve: Boolean,
      note: String,
    ): RepoResult<Unit> = RepoResult.Success(Unit)

    override suspend fun createDelegation(
      delegateEmployeeId: String,
      delegateName: String,
      endDate: String,
    ): RepoResult<Unit> = RepoResult.Success(Unit)

    override suspend fun assignDispatchTicket(
      workOrderId: String,
      assigneeEmployeeId: String,
      assigneeName: String,
      overrideLeaveWarning: Boolean,
      overrideReason: String?,
    ): RepoResult<Unit> = RepoResult.Success(Unit)

    override suspend fun resolveNeedsAttentionItem(
      needsAttentionId: String,
      correctedSerial: String?,
    ): RepoResult<String> = RepoResult.Success("")

    override suspend fun logSensitiveScreenView(screenName: String, actionLabel: String) = Unit
  }
}
