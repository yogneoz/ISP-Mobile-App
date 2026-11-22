package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.location.ProactiveShiftSuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PulseHrNotificationHelper @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val leaveChannel =
      NotificationChannel(
        CHANNEL_LEAVE_UPDATES,
        "Leave Request Status Updates",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Real-time notifications when leave requests are approved or declined."
      }

    val payrollChannel =
      NotificationChannel(
        CHANNEL_PAYROLL_ALERTS,
        "Payroll Processing Alerts",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Monthly payslip finalization, SSF, and TDS processing alerts."
      }

    val attendanceRemindersChannel =
      NotificationChannel(
        CHANNEL_ATTENDANCE_REMINDERS,
        "Attendance Geofence Reminders",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description =
          "Local alerts reminding employees to clock in/out when entering or leaving the branch geofence during working hours."
      }

    manager.createNotificationChannels(
      listOf(leaveChannel, payrollChannel, attendanceRemindersChannel)
    )
  }

  fun hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun showLeaveStatusNotification(
    title: String,
    body: String,
    notificationId: Int = System.currentTimeMillis().toInt(),
  ) {
    postNotification(
      channelId = CHANNEL_LEAVE_UPDATES,
      title = title,
      body = body,
      targetRoute = ROUTE_LEAVES,
      notificationId = notificationId,
    )
  }

  fun showPayrollAlertNotification(
    title: String,
    body: String,
    notificationId: Int = System.currentTimeMillis().toInt(),
  ) {
    postNotification(
      channelId = CHANNEL_PAYROLL_ALERTS,
      title = title,
      body = body,
      targetRoute = ROUTE_PAYROLL,
      notificationId = notificationId,
    )
  }

  fun showGeofenceAttendanceReminder(
    isEntry: Boolean,
    branchName: String,
    workHoursLabel: String,
    shiftName: String? = null,
    isShiftEndFallback: Boolean = false,
    notificationId: Int = if (isEntry) 4101 else 4102,
  ) {
    val shiftPrefix = shiftName?.let { "$it • " } ?: ""
    val title =
      when {
        isEntry -> "Arrived at $branchName — Clock In"
        isShiftEndFallback -> "${shiftPrefix}Shift Ended — Confirm Clock Out"
        else -> "Left $branchName — Clock Out Reminder"
      }
    val body =
      when {
        isEntry ->
          "You entered the office perimeter during ${shiftPrefix}shift hours ($workHoursLabel). Tap to submit your clock-in punch."
        isShiftEndFallback ->
          "Your ${shiftPrefix}shift window ($workHoursLabel) has ended and your clock-out is still open (off-site allowed). Tap to confirm clock-out."
        else ->
          "You exited the office perimeter before clocking out (${shiftPrefix}$workHoursLabel). Tap to complete your clock-out punch."
      }

    postNotification(
      channelId = CHANNEL_ATTENDANCE_REMINDERS,
      title = title,
      body = body,
      targetRoute = ROUTE_ATTENDANCE,
      notificationId = notificationId,
    )
  }

  fun showProactiveShiftSuggestionNotification(
    suggestion: ProactiveShiftSuggestion,
    branchName: String,
    notificationId: Int = 4150 + suggestion.shift.id.hashCode().and(0x3FF),
  ) {
    val shift = suggestion.shift
    val title = "Start ${shift.shiftName}? (${suggestion.confidenceScore}% match)"
    val body =
      "Starting outside $branchName • Suggested shift: ${shift.formattedTimeWindow()} (${shift.workMode.label}). ${suggestion.reasonSummary}. Tap to clock in."

    postNotification(
      channelId = CHANNEL_ATTENDANCE_REMINDERS,
      title = title,
      body = body,
      targetRoute = ROUTE_ATTENDANCE,
      notificationId = notificationId,
    )
  }

  private fun postNotification(
    channelId: String,
    title: String,
    body: String,
    targetRoute: String,
    notificationId: Int,
  ) {
    createNotificationChannels()
    if (!hasNotificationPermission()) return

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_TARGET_ROUTE, targetRoute)
      }

    val pendingIntent =
      PendingIntent.getActivity(
        context,
        targetRoute.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    try {
      NotificationManagerCompat.from(context).notify(notificationId, notification)
    } catch (_: SecurityException) {
      // Ignore if notification permission was revoked concurrently.
    }
  }

  companion object {
    const val CHANNEL_LEAVE_UPDATES = "pulsehr_leave_updates"
    const val CHANNEL_PAYROLL_ALERTS = "pulsehr_payroll_alerts"
    const val CHANNEL_ATTENDANCE_REMINDERS = "pulsehr_attendance_reminders"
    const val EXTRA_TARGET_ROUTE = "pulsehr_target_route"
    const val ROUTE_LEAVES = "leaves"
    const val ROUTE_PAYROLL = "payroll"
    const val ROUTE_ATTENDANCE = "attendance"
  }
}
