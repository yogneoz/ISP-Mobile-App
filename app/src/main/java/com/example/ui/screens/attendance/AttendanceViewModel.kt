package com.example.ui.screens.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.geofence.RouteLocationCalculator
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BranchLocation
import com.example.domain.model.CompanyAnnouncement
import com.example.domain.model.EmployeeProfile
import com.example.domain.model.EmployeeRouteSession
import com.example.domain.model.EmployeeShift
import com.example.domain.model.GeofenceEvaluation
import com.example.domain.model.HolidaySchedule
import com.example.domain.model.LeaveBalance
import com.example.domain.model.LeaveRequest
import com.example.domain.model.LeaveStatus
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.PendingSyncAction
import com.example.domain.model.RouteEventType
import com.example.domain.model.WorkHoursScheduleConfig
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.LeaveRepository
import com.example.domain.repository.RepoResult
import com.example.domain.repository.RouteTrackingRepository
import com.example.domain.repository.WorkplaceRepository
import com.example.location.GeofenceReminderScheduler
import com.example.location.LocationFixResult
import com.example.location.LocationManager
import com.example.location.ProactiveShiftSuggestion
import com.example.location.ProactiveShiftSuggestionEngine
import com.example.security.SecureTokenStore
import com.example.voice.VoiceCommandManager
import com.example.voice.VoiceListeningUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AttendanceSummaryStats(
  val presentDays: Int = 0,
  val overtimeHours: Double = 0.0,
  val totalLeaveRemaining: Double = 0.0,
)

data class PunchConfirmationState(
  val isClockIn: Boolean,
  val evaluation: GeofenceEvaluation,
  val branch: BranchLocation,
  val timestampLabel: String,
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
  private val employeeRepository: EmployeeRepository,
  private val attendanceRepository: AttendanceRepository,
  private val leaveRepository: LeaveRepository,
  private val routeTrackingRepository: RouteTrackingRepository,
  private val workplaceRepository: WorkplaceRepository,
  private val routeLocationCalculator: RouteLocationCalculator,
  private val locationManager: LocationManager,
  private val geofenceReminderScheduler: GeofenceReminderScheduler,
  private val voiceCommandManager: VoiceCommandManager,
  private val tokenStore: SecureTokenStore,
) : ViewModel() {

  val profile: StateFlow<EmployeeProfile?> =
    employeeRepository
      .observeEmployeeProfile()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val branches: StateFlow<List<BranchLocation>> =
    employeeRepository
      .observeBranches()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val attendanceRecords: StateFlow<List<AttendanceRecord>> =
    attendanceRepository
      .observeAttendanceRecords()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val pendingSyncActions: StateFlow<List<PendingSyncAction>> =
    attendanceRepository
      .observePendingSyncActions()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val announcements: StateFlow<List<CompanyAnnouncement>> =
    employeeRepository
      .observeAnnouncements()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val teamPendingApprovals: StateFlow<List<LeaveRequest>> =
    leaveRepository
      .observeTeamPendingRequests()
      .map { list -> list.filter { it.status == LeaveStatus.PENDING } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val leaveBalances: StateFlow<List<LeaveBalance>> =
    leaveRepository
      .observeLeaveBalances()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val upcomingHolidays: StateFlow<List<HolidaySchedule>> =
    employeeRepository
      .observeUpcomingHolidays()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val workHoursSchedule: StateFlow<WorkHoursScheduleConfig> = tokenStore.workHoursScheduleFlow

  val employeeShifts: StateFlow<List<EmployeeShift>> =
    workplaceRepository
      .observeEmployeeShifts()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val summaryStats: StateFlow<AttendanceSummaryStats> =
    combine(
      attendanceRecords,
      leaveBalances,
    ) { records, balances ->
      val present =
        records.count {
          it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.HALF_DAY
        }
      val ot = records.sumOf { it.overtimeHours }
      val leaveTotal = balances.sumOf { it.remainingDays }
      AttendanceSummaryStats(
        presentDays = present,
        overtimeHours = ot,
        totalLeaveRemaining = leaveTotal,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceSummaryStats())

  private val _hasLocationPermission = MutableStateFlow(locationManager.hasLocationPermission())
  val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

  private val _isLocating = MutableStateFlow(false)
  val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

  private val _locationFix = MutableStateFlow<LocationFixResult?>(null)
  val locationFix: StateFlow<LocationFixResult?> = _locationFix.asStateFlow()

  private val _geofenceEvaluation = MutableStateFlow<GeofenceEvaluation?>(null)
  val geofenceEvaluation: StateFlow<GeofenceEvaluation?> = _geofenceEvaluation.asStateFlow()

  private val _punchConfirmation = MutableStateFlow<PunchConfirmationState?>(null)
  val punchConfirmation: StateFlow<PunchConfirmationState?> = _punchConfirmation.asStateFlow()

  private val _blockedEvaluation = MutableStateFlow<GeofenceEvaluation?>(null)
  val blockedEvaluation: StateFlow<GeofenceEvaluation?> = _blockedEvaluation.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _voiceCheckInSession = MutableStateFlow<PunchConfirmationState?>(null)
  val voiceCheckInSession: StateFlow<PunchConfirmationState?> = _voiceCheckInSession.asStateFlow()

  val voiceListeningState: StateFlow<VoiceListeningUiState> = voiceCommandManager.state

  private val _hasMicPermission = MutableStateFlow(voiceCommandManager.hasMicrophonePermission())
  val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

  private val _voiceAuthPhrase = MutableStateFlow(tokenStore.getVoiceAuthPhrase())
  val voiceAuthPhrase: StateFlow<String> = _voiceAuthPhrase.asStateFlow()

  val activeRouteSessionId: StateFlow<String?> =
    routeTrackingRepository.activeRouteSessionIdFlow

  val lastRouteSyncEpochMs: StateFlow<Long?> =
    routeTrackingRepository.lastRouteSyncEpochMsFlow

  val activeOrLatestRouteSession: StateFlow<EmployeeRouteSession?> =
    routeTrackingRepository
      .observeActiveOrLatestRouteSession()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  val allRouteBreadcrumbs: StateFlow<List<LocationBreadcrumb>> =
    routeTrackingRepository
      .observeBreadcrumbs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val proactiveShiftSuggestion: StateFlow<ProactiveShiftSuggestion?> =
    combine(
      employeeShifts,
      attendanceRecords,
      allRouteBreadcrumbs,
      _geofenceEvaluation,
      activeRouteSessionId,
    ) { shifts, records, breadcrumbs, eval, routeSessionId ->
      val now = java.util.Calendar.getInstance()
      val todayAd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
      val todayRec = records.firstOrNull { it.attendanceDateAd == todayAd }
      ProactiveShiftSuggestionEngine.suggestShiftForOffsiteStart(
        calendar = now,
        schedule = workHoursSchedule.value,
        shifts = shifts,
        attendanceHistory = records,
        recentBreadcrumbs = breadcrumbs,
        currentlyInsideGeofence = eval?.isInsideGeofence ?: false,
        currentLatitude = eval?.userLatitude,
        currentLongitude = eval?.userLongitude,
        distanceFromBranchMeters = eval?.distanceMeters,
        hasCheckedInToday = todayRec?.checkInTime != null,
        hasCheckedOutToday = todayRec?.checkOutTime != null,
        hasActiveFieldRoute = !routeSessionId.isNullOrBlank(),
        routeCalculator = routeLocationCalculator,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _isRouteActionBusy = MutableStateFlow(false)
  val isRouteActionBusy: StateFlow<Boolean> = _isRouteActionBusy.asStateFlow()

  init {
    viewModelScope.launch {
      profile.collect { emp ->
        if (emp != null) {
          evaluateCurrentFix(emp.branch)
        }
      }
    }
    viewModelScope.launch {
      voiceCommandManager.state.collect { vState ->
        if (vState is VoiceListeningUiState.Authenticated && _voiceCheckInSession.value != null) {
          executeVoiceVerifiedCheckIn(vState.matchedPhrase)
        }
      }
    }
    // Foreground periodic route location capture (every 60s while Checked In)
    viewModelScope.launch {
      activeRouteSessionId.collectLatest { sessionId ->
        if (!sessionId.isNullOrBlank()) {
          while (isActive) {
            delay(60_000L)
            if (locationManager.hasLocationPermission()) {
              val fix = locationManager.acquireCurrentLocation()
              if (fix is LocationFixResult.Success) {
                routeTrackingRepository.pushLocationWaypoint(
                  latitude = fix.latitude,
                  longitude = fix.longitude,
                  accuracyMeters = fix.accuracyMeters,
                  eventType = RouteEventType.WAYPOINT,
                )
              }
            }
          }
        }
      }
    }
    refreshGpsLocation()
  }

  fun toggleRouteCheckInOut() {
    viewModelScope.launch {
      _hasLocationPermission.value = locationManager.hasLocationPermission()
      if (!_hasLocationPermission.value) {
        _statusBanner.value =
          "Grant GPS permission first so Check-In / Check-Out can record your coordinates."
        return@launch
      }
      _isRouteActionBusy.value = true
      try {
        val fix = locationManager.acquireCurrentLocation()
        _locationFix.value = fix
        when (fix) {
          is LocationFixResult.Success -> {
            val isCurrentlyCheckedIn = !activeRouteSessionId.value.isNullOrBlank()
            val result =
              if (isCurrentlyCheckedIn) {
                routeTrackingRepository.checkOutShift(
                  fix.latitude,
                  fix.longitude,
                  fix.accuracyMeters,
                )
              } else {
                routeTrackingRepository.checkInShift(
                  fix.latitude,
                  fix.longitude,
                  fix.accuracyMeters,
                )
              }
            when (result) {
              is RepoResult.Success -> {
                val pt = result.data
                val syncNote =
                  if (pt.syncStatus.name == "SYNCED") "Synced to database"
                  else "Saved offline; queued for periodic sync"
                _statusBanner.value =
                  "${pt.eventType.label} at ${pt.siteLabel} • $syncNote"
              }
              is RepoResult.Error -> _statusBanner.value = result.message
            }
          }
          is LocationFixResult.PermissionDenied -> {
            _statusBanner.value = "Location permission denied."
          }
          is LocationFixResult.Unavailable -> {
            _statusBanner.value = fix.reason
          }
        }
      } finally {
        _isRouteActionBusy.value = false
      }
    }
  }

  fun pushLocationNow() {
    viewModelScope.launch {
      _hasLocationPermission.value = locationManager.hasLocationPermission()
      if (!_hasLocationPermission.value) {
        _statusBanner.value = "Grant Location permission to push current GPS coordinates."
        return@launch
      }
      _isRouteActionBusy.value = true
      try {
        when (val fix = locationManager.acquireCurrentLocation()) {
          is LocationFixResult.Success -> {
            _locationFix.value = fix
            when (
              val res =
                routeTrackingRepository.pushLocationWaypoint(
                  latitude = fix.latitude,
                  longitude = fix.longitude,
                  accuracyMeters = fix.accuracyMeters,
                  eventType = RouteEventType.MANUAL_PUSH,
                )
            ) {
              is RepoResult.Success -> {
                val pt = res.data
                val syncNote =
                  if (pt.syncStatus.name == "SYNCED") "pushed to database"
                  else "queued offline in Room"
                _statusBanner.value =
                  "Location pin logged (${pt.siteLabel}) • $syncNote."
              }
              is RepoResult.Error -> _statusBanner.value = res.message
            }
          }
          is LocationFixResult.PermissionDenied -> {
            _statusBanner.value = "Location permission denied."
          }
          is LocationFixResult.Unavailable -> {
            _statusBanner.value = fix.reason
          }
        }
      } finally {
        _isRouteActionBusy.value = false
      }
    }
  }

  fun syncRouteDatabaseNow() {
    viewModelScope.launch {
      _isRouteActionBusy.value = true
      try {
        when (val res = routeTrackingRepository.syncPendingLocationsToDatabase()) {
          is RepoResult.Success -> {
            _statusBanner.value =
              if (res.data > 0) "Synced ${res.data} route waypoint(s) to database."
              else "All employee route waypoints are already synced."
          }
          is RepoResult.Error -> {
            _statusBanner.value = res.message
          }
        }
      } finally {
        _isRouteActionBusy.value = false
      }
    }
  }

  fun formatRouteDistance(meters: Double): String = routeLocationCalculator.formatDistance(meters)

  fun onLocationPermissionUpdated() {
    _hasLocationPermission.value = locationManager.hasLocationPermission()
    if (_hasLocationPermission.value) {
      refreshGpsLocation()
    }
  }

  fun refreshGpsLocation() {
    viewModelScope.launch {
      _hasLocationPermission.value = locationManager.hasLocationPermission()
      if (!_hasLocationPermission.value) {
        _locationFix.value = LocationFixResult.PermissionDenied
        profile.value?.branch?.let { evaluateCurrentFix(it) }
        return@launch
      }
      _isLocating.value = true
      val fix = locationManager.acquireCurrentLocation()
      _locationFix.value = fix
      val currentEmp = profile.value
      if (currentEmp != null) {
        val eval = locationManager.evaluateFixAgainstBranch(currentEmp.branch, fix)
        _geofenceEvaluation.value = eval
        if (eval.userLatitude != null) {
          geofenceReminderScheduler.handleEvaluatedPerimeter(
            isInsideNow = eval.isInsideGeofence,
            branchName = currentEmp.branch.name,
            employeeId = currentEmp.id,
            userLatitude = eval.userLatitude,
            userLongitude = eval.userLongitude,
            distanceFromBranchMeters = eval.distanceMeters,
          )
        }
      }
      _isLocating.value = false
    }
  }

  private fun evaluateCurrentFix(branch: BranchLocation) {
    val fix = _locationFix.value
    _geofenceEvaluation.value = fix?.let { locationManager.evaluateFixAgainstBranch(branch, it) }
  }

  fun updateWorkHoursSchedule(config: WorkHoursScheduleConfig) {
    geofenceReminderScheduler.updateSchedule(config)
    _statusBanner.value =
      if (config.enabled) "Work-hours geofence alerts active (${config.formattedWindow()})."
      else "Work-hours geofence alerts paused."
  }

  fun triggerGeofenceReminderPreview(isEntry: Boolean) {
    val branchName = profile.value?.branch?.name ?: "Assigned Branch"
    val activeShift =
      GeofenceReminderScheduler.findActiveOrJustEndedShift(
        shifts = employeeShifts.value,
        calendar = java.util.Calendar.getInstance(),
      )
    geofenceReminderScheduler.triggerWorkingHoursReminderPreview(
      isEntry = isEntry,
      branchName = branchName,
      activeShift = activeShift,
    )
    _statusBanner.value =
      if (isEntry) "Sent local Clock-In geofence arrival notification for $branchName."
      else "Sent local Clock-Out reminder for ${activeShift?.shiftName ?: branchName}."
  }

  fun triggerProactiveShiftSuggestionPreview() {
    val branchName = profile.value?.branch?.name ?: "Assigned Branch"
    val eval = _geofenceEvaluation.value
    val latestOutsidePoint =
      allRouteBreadcrumbs.value.lastOrNull { !it.isInsideGeofence }
    val previewSuggestion =
      proactiveShiftSuggestion.value
        ?: ProactiveShiftSuggestionEngine.suggestShiftForOffsiteStart(
          calendar = java.util.Calendar.getInstance(),
          schedule = workHoursSchedule.value,
          shifts = employeeShifts.value,
          attendanceHistory = attendanceRecords.value,
          recentBreadcrumbs = allRouteBreadcrumbs.value,
          currentlyInsideGeofence = false,
          currentLatitude = eval?.userLatitude ?: latestOutsidePoint?.latitude,
          currentLongitude = eval?.userLongitude ?: latestOutsidePoint?.longitude,
          distanceFromBranchMeters = eval?.distanceMeters ?: 1_850.0,
          hasCheckedInToday = false,
          hasCheckedOutToday = false,
          hasActiveFieldRoute = false,
          leadWindowMinutes = 720,
          routeCalculator = routeLocationCalculator,
        )
    if (previewSuggestion == null) {
      _statusBanner.value = "No synced shifts found to suggest right now."
      return
    }
    geofenceReminderScheduler.triggerProactiveShiftSuggestionPreview(
      branchName = branchName,
      suggestion = previewSuggestion,
    )
    _statusBanner.value =
      "Sent proactive shift suggestion: ${previewSuggestion.shift.shiftName} (${previewSuggestion.confidenceScore}% match)."
  }

  fun initiateClockAction(isClockIn: Boolean) {
    viewModelScope.launch {
      val todayAd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
      val todayRecord = attendanceRecords.value.firstOrNull { it.attendanceDateAd == todayAd }
      if (isClockIn && todayRecord?.checkInTime != null) {
        _statusBanner.value = "Already clocked in today at ${todayRecord.checkInTime}."
        return@launch
      }
      if (!isClockIn && todayRecord?.checkInTime == null) {
        _statusBanner.value = "Clock in first before clocking out for today."
        return@launch
      }
      if (!isClockIn && todayRecord?.checkOutTime != null) {
        _statusBanner.value = "Already clocked out today at ${todayRecord.checkOutTime}."
        return@launch
      }

      val currentProfile = profile.value ?: return@launch
      _hasLocationPermission.value = locationManager.hasLocationPermission()
      if (!_hasLocationPermission.value) {
        _blockedEvaluation.value =
          locationManager.evaluateFixAgainstBranch(
            currentProfile.branch,
            LocationFixResult.PermissionDenied,
          )
        return@launch
      }

      _isLocating.value = true
      val verification = locationManager.verifyOfficePerimeter(currentProfile.branch)
      _locationFix.value = verification.fixResult
      _geofenceEvaluation.value = verification.evaluation
      _isLocating.value = false

      if (!verification.isWithinPerimeter) {
        _blockedEvaluation.value = verification.evaluation
      } else {
        val nowLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        _punchConfirmation.value =
          PunchConfirmationState(
            isClockIn = isClockIn,
            evaluation = verification.evaluation,
            branch = currentProfile.branch,
            timestampLabel = nowLabel,
          )
      }
    }
  }

  fun dismissBlockedDialog() {
    _blockedEvaluation.value = null
  }

  fun dismissPunchConfirmation() {
    _punchConfirmation.value = null
  }

  fun executeVerifiedPunch() {
    val confirmation = _punchConfirmation.value ?: return
    _punchConfirmation.value = null
    viewModelScope.launch {
      val lat = confirmation.evaluation.userLatitude
      val lon = confirmation.evaluation.userLongitude
      val acc = confirmation.evaluation.gpsAccuracyMeters
      val result =
        if (confirmation.isClockIn) attendanceRepository.clockIn(lat, lon, acc)
        else attendanceRepository.clockOut(lat, lon, acc)
      when (result) {
        is RepoResult.Success -> {
          val mode =
            if (result.data.syncStatus.name == "SYNCED") "Synced (${result.data.integrityStatus})"
            else "Saved offline; queued in WorkManager"
          _statusBanner.value =
            "${if (confirmation.isClockIn) "Clocked In" else "Clocked Out"} • $mode"
        }
        is RepoResult.Error -> {
          _statusBanner.value = result.message
        }
      }
    }
  }

  fun initiateVoiceCheckIn() {
    viewModelScope.launch {
      val currentProfile = profile.value ?: return@launch
      _hasLocationPermission.value = locationManager.hasLocationPermission()
      if (!_hasLocationPermission.value) {
        _blockedEvaluation.value =
          locationManager.evaluateFixAgainstBranch(
            currentProfile.branch,
            LocationFixResult.PermissionDenied,
          )
        return@launch
      }

      _isLocating.value = true
      val verification = locationManager.verifyOfficePerimeter(currentProfile.branch)
      _locationFix.value = verification.fixResult
      _geofenceEvaluation.value = verification.evaluation
      _isLocating.value = false

      if (!verification.isWithinPerimeter) {
        _blockedEvaluation.value = verification.evaluation
      } else {
        voiceCommandManager.resetState()
        _hasMicPermission.value = voiceCommandManager.hasMicrophonePermission()
        _voiceAuthPhrase.value = tokenStore.getVoiceAuthPhrase()
        val nowLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
        _voiceCheckInSession.value =
          PunchConfirmationState(
            isClockIn = true,
            evaluation = verification.evaluation,
            branch = currentProfile.branch,
            timestampLabel = nowLabel,
          )
        if (_hasMicPermission.value && voiceCommandManager.isSpeechRecognitionAvailable()) {
          voiceCommandManager.startListening(_voiceAuthPhrase.value)
        }
      }
    }
  }

  fun onMicPermissionGranted() {
    _hasMicPermission.value = voiceCommandManager.hasMicrophonePermission()
    if (_hasMicPermission.value && _voiceCheckInSession.value != null) {
      voiceCommandManager.startListening(_voiceAuthPhrase.value)
    }
  }

  fun startVoiceListening() {
    _hasMicPermission.value = voiceCommandManager.hasMicrophonePermission()
    voiceCommandManager.startListening(_voiceAuthPhrase.value)
  }

  fun stopVoiceListening() {
    voiceCommandManager.stopListening()
  }

  fun updateVoiceAuthPhrase(newPhrase: String) {
    val cleaned = newPhrase.trim()
    if (cleaned.length < 4) return
    tokenStore.saveVoiceAuthPhrase(cleaned)
    _voiceAuthPhrase.value = tokenStore.getVoiceAuthPhrase()
  }

  fun dismissVoiceCheckIn() {
    voiceCommandManager.resetState()
    _voiceCheckInSession.value = null
  }

  private fun executeVoiceVerifiedCheckIn(matchedPhrase: String) {
    val session = _voiceCheckInSession.value ?: return
    _voiceCheckInSession.value = null
    voiceCommandManager.resetState()
    viewModelScope.launch {
      val perimeterCheck = locationManager.verifyOfficePerimeter(session.branch)
      if (!perimeterCheck.isWithinPerimeter) {
        _blockedEvaluation.value = perimeterCheck.evaluation
        return@launch
      }
      val lat = perimeterCheck.evaluation.userLatitude ?: session.evaluation.userLatitude
      val lon = perimeterCheck.evaluation.userLongitude ?: session.evaluation.userLongitude
      val acc = perimeterCheck.evaluation.gpsAccuracyMeters ?: session.evaluation.gpsAccuracyMeters
      when (val result = attendanceRepository.clockIn(lat, lon, acc)) {
        is RepoResult.Success -> {
          val mode =
            if (result.data.syncStatus.name == "SYNCED") "Synced with HR server"
            else "Saved offline; queued in WorkManager"
          _statusBanner.value =
            "Voice Check-In Authenticated (\"$matchedPhrase\") • $mode"
        }
        is RepoResult.Error -> {
          _statusBanner.value = result.message
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    voiceCommandManager.resetState()
  }

  fun switchAssignedBranch(branchId: String) {
    viewModelScope.launch {
      when (val res = employeeRepository.switchAssignedBranch(branchId)) {
        is RepoResult.Success -> {
          evaluateCurrentFix(res.data.branch)
          _statusBanner.value = "Assigned branch updated to ${res.data.branch.name}."
        }
        is RepoResult.Error -> _statusBanner.value = res.message
      }
    }
  }

  fun calibrateBranchRadius(radiusMeters: Double, pinToCurrentGps: Boolean) {
    viewModelScope.launch {
      val currentBranch = profile.value?.branch ?: return@launch
      val fix = _locationFix.value as? LocationFixResult.Success
      val targetLat = if (pinToCurrentGps && fix != null) fix.latitude else currentBranch.latitude
      val targetLon = if (pinToCurrentGps && fix != null) fix.longitude else currentBranch.longitude
      when (
        val res =
          employeeRepository.calibrateBranchGeofence(
            currentBranch.id,
            targetLat,
            targetLon,
            radiusMeters,
          )
      ) {
        is RepoResult.Success -> {
          evaluateCurrentFix(res.data)
          _statusBanner.value =
            "Geofence calibrated (${res.data.geofenceRadiusMeters.toInt()}m radius)."
        }
        is RepoResult.Error -> _statusBanner.value = res.message
      }
    }
  }

  fun clearStatusBanner() {
    _statusBanner.value = null
  }
}
