package com.example.ui.screens.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.geofence.ClientVisitPin
import com.example.domain.geofence.MercatorBounds
import com.example.domain.geofence.RouteLocationCalculator
import com.example.domain.geofence.TravelMapProjection
import com.example.domain.model.BranchLocation
import com.example.domain.model.EmployeeProfile
import com.example.domain.model.LocationBreadcrumb
import com.example.domain.model.RouteEventType
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.RepoResult
import com.example.domain.repository.RouteTrackingRepository
import com.example.domain.repository.WorkplaceRepository
import com.example.location.LocationFixResult
import com.example.location.LocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TravelMapLayerVisibility(
  val showTravelPath: Boolean = true,
  val showClientVisits: Boolean = true,
  val showGeofence: Boolean = true,
  val showLabels: Boolean = true,
)

data class TravelMapUiState(
  val profile: EmployeeProfile? = null,
  val branches: List<BranchLocation> = emptyList(),
  val availableDates: List<String> = emptyList(),
  val selectedDateFilter: String? = null, // null = latest workday, "ALL" = all days
  val visibleWaypoints: List<LocationBreadcrumb> = emptyList(),
  val clientVisitPins: List<ClientVisitPin> = emptyList(),
  val bounds: MercatorBounds =
    MercatorBounds(0.1606, 0.1612, 0.3880, 0.3886, 37.405, -122.082),
  val boundsWidthMeters: Double = 4500.0,
  val totalDistanceMeters: Double = 0.0,
  val isCurrentlyCheckedIn: Boolean = false,
  val selectedStopIndex: Int = 0,
  val selectedClientWorkOrderId: String? = null,
  val layers: TravelMapLayerVisibility = TravelMapLayerVisibility(),
)

private data class TravelMapControls(
  val dateFilter: String?,
  val selectedStopIndex: Int,
  val selectedClientId: String?,
  val layers: TravelMapLayerVisibility,
)

@HiltViewModel
class TravelRouteMapViewModel @Inject constructor(
  private val routeTrackingRepository: RouteTrackingRepository,
  private val employeeRepository: EmployeeRepository,
  private val workplaceRepository: WorkplaceRepository,
  private val routeCalculator: RouteLocationCalculator,
  private val mapProjection: TravelMapProjection,
  private val locationManager: LocationManager,
) : ViewModel() {

  private val _selectedDateFilter = MutableStateFlow<String?>(null)
  private val _selectedStopIndex = MutableStateFlow(0)
  private val _selectedClientId = MutableStateFlow<String?>(null)
  private val _layers = MutableStateFlow(TravelMapLayerVisibility())

  private val _isPlayingTimeline = MutableStateFlow(false)
  val isPlayingTimeline: StateFlow<Boolean> = _isPlayingTimeline.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val controlsFlow =
    combine(
      _selectedDateFilter,
      _selectedStopIndex,
      _selectedClientId,
      _layers,
    ) { dateFilter, stopIdx, clientId, layers ->
      TravelMapControls(dateFilter, stopIdx, clientId, layers)
    }

  val uiState: StateFlow<TravelMapUiState> =
    combine(
      routeTrackingRepository.observeBreadcrumbs(),
      workplaceRepository.observeWorkOrders(),
      employeeRepository.observeBranches(),
      employeeRepository.observeEmployeeProfile(),
      combine(routeTrackingRepository.activeRouteSessionIdFlow, controlsFlow) { activeId, ctrl ->
        activeId to ctrl
      },
    ) { allWaypoints, workOrders, branches, profile, (activeId, ctrl) ->
      val dates = allWaypoints.map { it.dateIso }.distinct().sortedDescending()
      val effectiveDate =
        when (ctrl.dateFilter) {
          "ALL" -> "ALL"
          null -> dates.firstOrNull()
          else -> if (ctrl.dateFilter in dates) ctrl.dateFilter else dates.firstOrNull()
        }

      val filteredWaypoints =
        if (effectiveDate == "ALL" || effectiveDate == null) {
          allWaypoints.sortedBy { it.capturedAtEpochMs }
        } else {
          allWaypoints.filter { it.dateIso == effectiveDate }.sortedBy { it.capturedAtEpochMs }
        }

      val clientPins = mapProjection.buildClientVisitPins(workOrders, filteredWaypoints)
      val bounds = mapProjection.computeBounds(filteredWaypoints, clientPins, branches)
      val widthMeters = mapProjection.boundsWidthMeters(bounds)
      val totalDist =
        if (effectiveDate == "ALL") {
          filteredWaypoints
            .groupBy { it.sessionId }
            .values
            .sumOf { routeCalculator.totalRouteDistanceMeters(it) }
        } else {
          routeCalculator.totalRouteDistanceMeters(filteredWaypoints)
        }

      val clampedStopIndex =
        if (filteredWaypoints.isEmpty()) 0
        else ctrl.selectedStopIndex.coerceIn(0, filteredWaypoints.lastIndex)

      TravelMapUiState(
        profile = profile,
        branches = branches,
        availableDates = dates,
        selectedDateFilter = effectiveDate,
        visibleWaypoints = filteredWaypoints,
        clientVisitPins = clientPins,
        bounds = bounds,
        boundsWidthMeters = widthMeters,
        totalDistanceMeters = totalDist,
        isCurrentlyCheckedIn = !activeId.isNullOrBlank(),
        selectedStopIndex = clampedStopIndex,
        selectedClientWorkOrderId = ctrl.selectedClientId,
        layers = ctrl.layers,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelMapUiState())

  init {
    viewModelScope.launch {
      _isPlayingTimeline.collectLatest { playing ->
        if (playing) {
          while (isActive) {
            delay(1_150L)
            val total = uiState.value.visibleWaypoints.size
            if (total <= 1) {
              _isPlayingTimeline.value = false
              break
            }
            val next = _selectedStopIndex.value + 1
            if (next >= total) {
              _selectedStopIndex.value = 0
              _isPlayingTimeline.value = false
              break
            } else {
              _selectedStopIndex.value = next
            }
          }
        }
      }
    }
  }

  fun selectDateFilter(filter: String) {
    _isPlayingTimeline.value = false
    _selectedDateFilter.value = filter
    _selectedStopIndex.value = 0
    _selectedClientId.value = null
  }

  fun selectStopIndex(index: Int) {
    _isPlayingTimeline.value = false
    _selectedStopIndex.value = index.coerceAtLeast(0)
    _selectedClientId.value = null
  }

  fun selectClientPin(pin: ClientVisitPin) {
    _isPlayingTimeline.value = false
    _selectedClientId.value = pin.workOrderId
    val matchedIdx =
      uiState.value.visibleWaypoints.indexOfFirst { it.id == pin.matchedWaypointId }
    if (matchedIdx >= 0) {
      _selectedStopIndex.value = matchedIdx
    }
  }

  fun stepTimeline(delta: Int) {
    _isPlayingTimeline.value = false
    val maxIdx = (uiState.value.visibleWaypoints.size - 1).coerceAtLeast(0)
    _selectedStopIndex.value = (_selectedStopIndex.value + delta).coerceIn(0, maxIdx)
  }

  fun togglePlayTimeline() {
    val total = uiState.value.visibleWaypoints.size
    if (total <= 1) return
    if (!_isPlayingTimeline.value && _selectedStopIndex.value >= total - 1) {
      _selectedStopIndex.value = 0
    }
    _isPlayingTimeline.value = !_isPlayingTimeline.value
  }

  fun toggleLayer(transform: (TravelMapLayerVisibility) -> TravelMapLayerVisibility) {
    _layers.value = transform(_layers.value)
  }

  fun recordCurrentPinToMap() {
    viewModelScope.launch {
      if (!locationManager.hasLocationPermission()) {
        _statusMessage.value = "Grant Location permission to pin current GPS on the map."
        return@launch
      }
      _isBusy.value = true
      try {
        when (val fix = locationManager.acquireCurrentLocation()) {
          is LocationFixResult.Success -> {
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
                _selectedDateFilter.value = res.data.dateIso
                val newList = uiState.value.visibleWaypoints
                _selectedStopIndex.value = newList.lastIndex.coerceAtLeast(0)
                _statusMessage.value = "Pinned current location (${res.data.siteLabel}) to route map."
              }
              is RepoResult.Error -> _statusMessage.value = res.message
            }
          }
          is LocationFixResult.PermissionDenied -> _statusMessage.value = "Location permission denied."
          is LocationFixResult.Unavailable -> _statusMessage.value = fix.reason
        }
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun projectToNormalized(lat: Double, lon: Double) =
    mapProjection.projectNormalized(lat, lon, uiState.value.bounds)

  fun formatDistance(meters: Double): String = routeCalculator.formatDistance(meters)

  fun segmentDistanceFromPrevious(index: Int): Double? {
    val list = uiState.value.visibleWaypoints
    if (index <= 0 || index >= list.size) return null
    val prev = list[index - 1]
    val curr = list[index]
    if (prev.sessionId != curr.sessionId) return null
    return routeCalculator.haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }
}
