package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object AttendanceRoute

@Serializable data object LeavesRoute

@Serializable data object PayrollRoute

@Serializable data object ProfileRoute

@Serializable data object LeaveHolidayDashboardRoute

@Serializable data object DocumentScannerRoute

@Serializable data object MyHrRoute

@Serializable data object FieldOpsRoute

@Serializable data object ClaimsRoute

@Serializable data class OpsHubRoute(val initialSection: String? = null)

@Serializable data object TravelRouteMapRoute

@Serializable data object AuditLogRoute

