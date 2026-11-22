package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.domain.geofence.GeofenceEvaluator
import com.example.domain.model.BranchLocation
import com.example.domain.model.GeofenceEvaluation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

sealed interface LocationFixResult {
  data class Success(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
  ) : LocationFixResult

  data object PermissionDenied : LocationFixResult

  data class Unavailable(val reason: String) : LocationFixResult
}

data class PerimeterVerificationResult(
  val fixResult: LocationFixResult,
  val evaluation: GeofenceEvaluation,
  val isWithinPerimeter: Boolean,
)

/**
 * Coordinates FusedLocationProviderClient acquisition and branch office perimeter verification
 * before permitting attendance clock-in or clock-out submissions.
 */
@Singleton
class LocationManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val fusedLocationClient: FusedLocationProviderClient,
  private val geofenceEvaluator: GeofenceEvaluator,
) {

  fun hasLocationPermission(): Boolean {
    val fineGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarseGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
  }

  /**
   * Acquires the employee's current GPS coordinates via FusedLocationProviderClient (with last-known
   * fused location and platform LocationManager fallbacks) and verifies whether they lie within
   * [branch]'s office perimeter.
   */
  suspend fun verifyOfficePerimeter(branch: BranchLocation): PerimeterVerificationResult {
    val fix = acquireCurrentLocation()
    val evaluation = evaluateFixAgainstBranch(branch, fix)
    return PerimeterVerificationResult(
      fixResult = fix,
      evaluation = evaluation,
      isWithinPerimeter = evaluation.isInsideGeofence,
    )
  }

  fun evaluateFixAgainstBranch(
    branch: BranchLocation,
    fix: LocationFixResult,
  ): GeofenceEvaluation {
    return when (fix) {
      is LocationFixResult.Success ->
        geofenceEvaluator.evaluate(branch, fix.latitude, fix.longitude, fix.accuracyMeters)
      is LocationFixResult.PermissionDenied ->
        GeofenceEvaluation(
          isInsideGeofence = false,
          distanceMeters = null,
          branchRadiusMeters = branch.geofenceRadiusMeters,
          branchName = branch.name,
          userLatitude = null,
          userLongitude = null,
          gpsAccuracyMeters = null,
          failureReason =
            "Location permission denied. Grant GPS access so PulseHR can verify you are within ${branch.name}.",
        )
      is LocationFixResult.Unavailable ->
        GeofenceEvaluation(
          isInsideGeofence = false,
          distanceMeters = null,
          branchRadiusMeters = branch.geofenceRadiusMeters,
          branchName = branch.name,
          userLatitude = null,
          userLongitude = null,
          gpsAccuracyMeters = null,
          failureReason = fix.reason,
        )
    }
  }

  fun isCoordinateWithinPerimeter(
    branch: BranchLocation,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float? = null,
  ): Boolean {
    return geofenceEvaluator.evaluate(branch, latitude, longitude, accuracyMeters).isInsideGeofence
  }

  @SuppressLint("MissingPermission")
  suspend fun acquireCurrentLocation(): LocationFixResult {
    if (!hasLocationPermission()) {
      return LocationFixResult.PermissionDenied
    }

    // 1. High-accuracy fresh fix from FusedLocationProviderClient
    val fusedFix =
      withTimeoutOrNull(4_500L) {
        suspendCancellableCoroutine<Location?> { cont ->
          val cts = CancellationTokenSource()
          cont.invokeOnCancellation { cts.cancel() }
          fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
      }
    if (fusedFix != null) {
      return LocationFixResult.Success(
        latitude = fusedFix.latitude,
        longitude = fusedFix.longitude,
        accuracyMeters = fusedFix.accuracy,
        provider = fusedFix.provider ?: "Fused GPS",
      )
    }

    // 2. Fallback to cached FusedLocationProviderClient lastLocation
    val lastFused =
      withTimeoutOrNull(1_500L) {
        suspendCancellableCoroutine<Location?> { cont ->
          fusedLocationClient.lastLocation
            .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
      }
    if (lastFused != null) {
      return LocationFixResult.Success(
        latitude = lastFused.latitude,
        longitude = lastFused.longitude,
        accuracyMeters = lastFused.accuracy,
        provider = lastFused.provider ?: "Fused LastFix",
      )
    }

    // 3. Fallback to Android system LocationManager if Play Services is unavailable
    val systemLocationManager =
      context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
    val systemLoc =
      listOf(
          android.location.LocationManager.GPS_PROVIDER,
          android.location.LocationManager.NETWORK_PROVIDER,
        )
        .firstNotNullOfOrNull { provider ->
          runCatching { systemLocationManager?.getLastKnownLocation(provider) }.getOrNull()
        }

    return if (systemLoc != null) {
      LocationFixResult.Success(
        latitude = systemLoc.latitude,
        longitude = systemLoc.longitude,
        accuracyMeters = systemLoc.accuracy,
        provider = systemLoc.provider ?: "System GPS",
      )
    } else {
      LocationFixResult.Unavailable(
        "Unable to obtain GPS fix. Ensure device Location is enabled and try refreshing GPS."
      )
    }
  }
}
