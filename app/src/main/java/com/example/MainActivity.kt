package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.AppCapability
import com.example.domain.model.AuthChallengePurpose
import com.example.domain.model.DutyStatus
import com.example.domain.model.UserSession
import com.example.notifications.PulseHrNotificationHelper
import com.example.ui.MainViewModel
import com.example.ui.SyncStatusSummary
import com.example.ui.components.PinVerificationDialog
import com.example.ui.navigation.AttendanceRoute
import com.example.ui.navigation.AuditLogRoute
import com.example.ui.navigation.ClaimsRoute
import com.example.ui.navigation.DocumentScannerRoute
import com.example.ui.navigation.FieldOpsRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.LeaveHolidayDashboardRoute
import com.example.ui.navigation.LeavesRoute
import com.example.ui.navigation.MyHrRoute
import com.example.ui.navigation.OpsHubRoute
import com.example.ui.navigation.PayrollRoute
import com.example.ui.navigation.ProfileRoute
import com.example.ui.navigation.TravelRouteMapRoute
import com.example.ui.screens.attendance.AttendanceScreen
import com.example.ui.screens.auth.AppLockScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.LeaveHolidayDashboardScreen
import com.example.ui.screens.leaves.LeavesScreen
import com.example.ui.screens.payroll.PayrollScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.route.TravelRouteMapScreen
import com.example.ui.screens.scanner.DocumentScannerScreen
import com.example.ui.screens.security.AuditLogScreen
import com.example.ui.screens.workplace.ClaimsScreen
import com.example.ui.screens.workplace.FieldOpsScreen
import com.example.ui.screens.workplace.MyHrHubScreen
import com.example.ui.screens.workplace.OpsHubScreen
import com.example.ui.theme.PulseHrTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private data class NavDestinationItem<T : Any>(
  val route: T,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

  private val pendingDeepLinkRoute = MutableStateFlow<String?>(null)

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingDeepLinkRoute.value =
      intent?.getStringExtra(PulseHrNotificationHelper.EXTRA_TARGET_ROUTE)
    setContent {
      val windowSizeClass = calculateWindowSizeClass(this)
      val deepLinkTarget by pendingDeepLinkRoute.collectAsStateWithLifecycle()
      PulseHrTheme {
        PulseHrRoot(
          activity = this,
          isExpandedWidth = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
          pendingDeepLinkRoute = deepLinkTarget,
          onDeepLinkConsumed = { pendingDeepLinkRoute.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingDeepLinkRoute.value =
      intent.getStringExtra(PulseHrNotificationHelper.EXTRA_TARGET_ROUTE)
  }
}

@Composable
fun PulseHrRoot(
  activity: FragmentActivity,
  isExpandedWidth: Boolean,
  pendingDeepLinkRoute: String? = null,
  onDeepLinkConsumed: () -> Unit = {},
  mainViewModel: MainViewModel = hiltViewModel(),
) {
  val session by mainViewModel.session.collectAsStateWithLifecycle()
  val isAppLocked by mainViewModel.isAppLocked.collectAsStateWithLifecycle()
  val isPayrollUnlocked by mainViewModel.isPayrollUnlocked.collectAsStateWithLifecycle()
  val lastLockReason by mainViewModel.lastLockReason.collectAsStateWithLifecycle()
  val isOfflineSimulated by mainViewModel.isOfflineSimulated.collectAsStateWithLifecycle()
  val pendingSyncCount by mainViewModel.pendingSyncCount.collectAsStateWithLifecycle()
  val loginLoading by mainViewModel.loginLoading.collectAsStateWithLifecycle()
  val loginError by mainViewModel.loginError.collectAsStateWithLifecycle()
  val pinChallenge by mainViewModel.pinChallenge.collectAsStateWithLifecycle()
  val syncMessage by mainViewModel.syncMessage.collectAsStateWithLifecycle()
  val biometricEntryError by mainViewModel.biometricEntryError.collectAsStateWithLifecycle()
  val biometricPromptActive by mainViewModel.biometricPromptActive.collectAsStateWithLifecycle()
  val dutyStatus by mainViewModel.dutyStatus.collectAsStateWithLifecycle()
  val syncStatusSummary by mainViewModel.syncStatusSummary.collectAsStateWithLifecycle()
  val unresolvedNeedsAttentionCount by mainViewModel.unresolvedNeedsAttentionCount.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(syncMessage) {
    val msg = syncMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      mainViewModel.clearSyncMessage()
    }
  }

  pinChallenge?.let { challenge ->
    PinVerificationDialog(
      purpose = challenge.purpose,
      isSettingNewPin = challenge.isSettingNewPin,
      errorMessage = challenge.errorMessage,
      canRetryBiometric = mainViewModel.canUseBiometrics(),
      onRetryBiometric = {
        mainViewModel.dismissPinDialog()
        mainViewModel.requestAuthentication(activity, challenge.purpose, challenge.onSuccess)
      },
      onPinSubmitted = mainViewModel::submitPin,
      onDismiss = mainViewModel::dismissPinDialog,
    )
  }

  val activeSession = session
  when {
    activeSession == null -> {
      LoginScreen(
        isLoading = loginLoading,
        errorMessage = loginError,
        onLogin = mainViewModel::login,
      )
    }
    isAppLocked -> {
      AppLockScreen(
        session = activeSession,
        lockReason = lastLockReason,
        biometricAvailability = mainViewModel.getBiometricAvailability(),
        biometricError = biometricEntryError,
        isBiometricPromptActive = biometricPromptActive,
        onTriggerBiometric = {
          mainViewModel.requestAppEntryBiometric(activity, activeSession.email)
        },
        onVerifyPin = mainViewModel::verifyAppUnlockPin,
        onSignOut = mainViewModel::logout,
      )
    }
    else -> {
      AuthenticatedShell(
        session = activeSession,
        isExpandedWidth = isExpandedWidth,
        isPayrollUnlocked = isPayrollUnlocked,
        isOfflineSimulated = isOfflineSimulated,
        pendingSyncCount = pendingSyncCount,
        dutyStatus = dutyStatus,
        syncStatusSummary = syncStatusSummary,
        unresolvedNeedsAttentionCount = unresolvedNeedsAttentionCount,
        snackbarHostState = snackbarHostState,
        pendingDeepLinkRoute = pendingDeepLinkRoute,
        onDeepLinkConsumed = onDeepLinkConsumed,
        onChangeDutyStatus = mainViewModel::setDutyStatus,
        onToggleOffline = mainViewModel::setOfflineSimulation,
        onSyncNow = mainViewModel::syncPendingNow,
        onLockSession = mainViewModel::lockAppNow,
        onRelockPayroll = mainViewModel::relockPayroll,
        onSignOut = mainViewModel::logout,
        onLogSensitiveView = mainViewModel::logSensitiveScreenView,
        onChangePin = {
          mainViewModel.openPinDialog(
            purpose = AuthChallengePurpose.APP_UNLOCK,
            isSettingNewPin = true,
            onSuccess = {},
          )
        },
        onRequestAuth = { purpose, onAuth ->
          mainViewModel.requestAuthentication(activity, purpose, onAuth)
        },
      )
    }
  }
}

@Composable
private fun AuthenticatedShell(
  session: UserSession,
  isExpandedWidth: Boolean,
  isPayrollUnlocked: Boolean,
  isOfflineSimulated: Boolean,
  pendingSyncCount: Int,
  dutyStatus: DutyStatus,
  syncStatusSummary: SyncStatusSummary,
  unresolvedNeedsAttentionCount: Int,
  snackbarHostState: SnackbarHostState,
  pendingDeepLinkRoute: String?,
  onDeepLinkConsumed: () -> Unit,
  onChangeDutyStatus: (DutyStatus) -> Unit,
  onToggleOffline: (Boolean) -> Unit,
  onSyncNow: () -> Unit,
  onLockSession: () -> Unit,
  onRelockPayroll: () -> Unit,
  onSignOut: () -> Unit,
  onLogSensitiveView: (String, String) -> Unit,
  onChangePin: () -> Unit,
  onRequestAuth: (AuthChallengePurpose, () -> Unit) -> Unit,
) {
  val navController = rememberNavController()
  val scope = rememberCoroutineScope()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val isScannerActive = currentDestination?.hasRoute(DocumentScannerRoute::class) == true

  val navItems =
    remember(session.capabilities) {
      buildList {
        add(NavDestinationItem(HomeRoute, "Today", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"))
        if (session.hasCapability(AppCapability.ESS_VIEW)) {
          add(
            NavDestinationItem(
              MyHrRoute,
              "My HR",
              Icons.Filled.Person,
              Icons.Outlined.Person,
              "nav_my_hr",
            )
          )
        }
        if (
          session.hasCapability(AppCapability.JOBS_EXECUTE) ||
            session.hasCapability(AppCapability.STOCK_VAN)
        ) {
          add(
            NavDestinationItem(
              FieldOpsRoute,
              "Field",
              Icons.Filled.Build,
              Icons.Outlined.Build,
              "nav_field",
            )
          )
        }
        if (session.hasCapability(AppCapability.CLAIMS_SUBMIT)) {
          add(
            NavDestinationItem(
              ClaimsRoute,
              "Claims",
              Icons.Filled.ReceiptLong,
              Icons.Outlined.ReceiptLong,
              "nav_claims",
            )
          )
        }
        add(
          NavDestinationItem(
            OpsHubRoute(),
            "Ops",
            Icons.Filled.AdminPanelSettings,
            Icons.Outlined.AdminPanelSettings,
            "nav_ops",
          )
        )
      }
    }

  fun navigateTopLevel(route: Any) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  LaunchedEffect(pendingDeepLinkRoute) {
    when (pendingDeepLinkRoute) {
      PulseHrNotificationHelper.ROUTE_ATTENDANCE -> {
        navController.navigate(AttendanceRoute)
        onDeepLinkConsumed()
      }
      PulseHrNotificationHelper.ROUTE_LEAVES -> {
        navController.navigate(LeavesRoute)
        onDeepLinkConsumed()
      }
      PulseHrNotificationHelper.ROUTE_PAYROLL -> {
        navController.navigate(PayrollRoute)
        onDeepLinkConsumed()
      }
    }
  }

  Scaffold(
    contentWindowInsets = if (isScannerActive) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (!isExpandedWidth && !isScannerActive) {
        NavigationBar {
          navItems.forEach { item ->
            val selected = currentDestination?.hasRoute(item.route::class) == true
            NavigationBarItem(
              selected = selected,
              onClick = { navigateTopLevel(item.route) },
              icon = {
                Icon(
                  imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                  contentDescription = item.label,
                )
              },
              label = { Text(item.label) },
              modifier = Modifier.testTag(item.testTag),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (isExpandedWidth && !isScannerActive) {
        NavigationRail(modifier = Modifier.fillMaxHeight()) {
          navItems.forEach { item ->
            val selected = currentDestination?.hasRoute(item.route::class) == true
            NavigationRailItem(
              selected = selected,
              onClick = { navigateTopLevel(item.route) },
              icon = {
                Icon(
                  imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                  contentDescription = item.label,
                )
              },
              label = { Text(item.label) },
              modifier = Modifier.testTag(item.testTag),
            )
          }
        }
      }

      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        NavHost(
          navController = navController,
          startDestination = HomeRoute,
        ) {
          composable<HomeRoute> {
            HomeScreen(
              session = session,
              isOfflineSimulated = isOfflineSimulated,
              pendingSyncCount = pendingSyncCount,
              dutyStatus = dutyStatus,
              syncStatusSummary = syncStatusSummary,
              unresolvedNeedsAttentionCount = unresolvedNeedsAttentionCount,
              onChangeDutyStatus = onChangeDutyStatus,
              onToggleOffline = onToggleOffline,
              onSyncNow = onSyncNow,
              onLockSession = onLockSession,
              onNavigateToAttendance = { navController.navigate(AttendanceRoute) },
              onNavigateToLeaves = { navController.navigate(LeavesRoute) },
              onNavigateToAnalytics = { navController.navigate(LeaveHolidayDashboardRoute) },
              onNavigateToField = { navigateTopLevel(FieldOpsRoute) },
              onNavigateToOps = { section -> navController.navigate(OpsHubRoute(section)) },
              onNavigateToTravelMap = { navController.navigate(TravelRouteMapRoute) },
              onRequestAuth = onRequestAuth,
            )
          }
          composable<MyHrRoute> {
            MyHrHubScreen(
              session = session,
              isOfflineSimulated = isOfflineSimulated,
              onOpenAttendance = { navController.navigate(AttendanceRoute) },
              onOpenLeaves = { navController.navigate(LeavesRoute) },
              onOpenPayroll = { navController.navigate(PayrollRoute) },
              onOpenProfileDocs = { navController.navigate(ProfileRoute) },
            )
          }
          composable<FieldOpsRoute> {
            FieldOpsScreen(
              session = session,
              onOpenTravelMap = { navController.navigate(TravelRouteMapRoute) },
            )
          }
          composable<ClaimsRoute> {
            ClaimsScreen(
              onOpenReceiptScanner = { navController.navigate(DocumentScannerRoute) },
            )
          }
          composable<OpsHubRoute> { entry ->
            val route = entry.toRoute<OpsHubRoute>()
            OpsHubScreen(
              session = session,
              initialSectionName = route.initialSection,
              onOpenAuditLog = { navController.navigate(AuditLogRoute) },
            )
          }
          composable<LeaveHolidayDashboardRoute> {
            LeaveHolidayDashboardScreen(
              onBack = { navController.popBackStack() },
              onNavigateToLeaves = { navController.navigate(LeavesRoute) },
            )
          }
          composable<AttendanceRoute> {
            AttendanceScreen(
              session = session,
              isOfflineSimulated = isOfflineSimulated,
              pendingSyncCount = pendingSyncCount,
              onToggleOffline = onToggleOffline,
              onSyncNow = onSyncNow,
              onRequestAuth = onRequestAuth,
              onNavigateToTravelMap = { navController.navigate(TravelRouteMapRoute) },
            )
          }
          composable<LeavesRoute> {
            LeavesScreen(
              session = session,
              onRequestAuth = onRequestAuth,
            )
          }
          composable<PayrollRoute> {
            PayrollScreen(
              isPayrollUnlocked = isPayrollUnlocked,
              isOfflineSimulated = isOfflineSimulated,
              onRequestUnlock = onRequestAuth,
              onRelockPayroll = onRelockPayroll,
            )
          }
          composable<ProfileRoute> {
            ProfileScreen(
              session = session,
              isOfflineSimulated = isOfflineSimulated,
              onLogSensitiveView = onLogSensitiveView,
              onOpenDocumentScanner = { navController.navigate(DocumentScannerRoute) },
              onChangePin = onChangePin,
              onLockNow = onLockSession,
              onSignOut = onSignOut,
              onOpenAuditLog = { navController.navigate(AuditLogRoute) },
            )
          }
          composable<DocumentScannerRoute> {
            DocumentScannerScreen(
              onBack = { navController.popBackStack() },
              onDocumentSaved = { savedMsg ->
                navController.popBackStack()
                scope.launch { snackbarHostState.showSnackbar(savedMsg) }
              },
            )
          }
          composable<TravelRouteMapRoute> {
            TravelRouteMapScreen(onBack = { navController.popBackStack() })
          }
          composable<AuditLogRoute> {
            AuditLogScreen(
              onBack = { navController.popBackStack() },
              onTriggerBiometricVerify = { onRequestAuth(AuthChallengePurpose.APP_UNLOCK) {} },
            )
          }
        }
      }
    }
  }
}
