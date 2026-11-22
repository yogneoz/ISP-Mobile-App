package com.example.notifications

import com.example.data.local.AnnouncementEntity
import com.example.data.local.PulseHrDao
import com.example.security.SecureTokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PulseHrMessagingService : FirebaseMessagingService() {

  @Inject lateinit var dao: PulseHrDao
  @Inject lateinit var fcmTokenManager: FcmTokenManager
  @Inject lateinit var notificationHelper: PulseHrNotificationHelper
  @Inject lateinit var secureTokenStore: SecureTokenStore

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    fcmTokenManager.onTokenReceived(token)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)

    val data = message.data
    val type = data["type"] ?: TYPE_LEAVE_STATUS_UPDATE
    val title = data["title"] ?: message.notification?.title
    val body = data["body"] ?: message.notification?.body

    serviceScope.launch {
      when (type) {
        TYPE_LEAVE_STATUS_UPDATE -> {
          val requestId = data["requestId"]
          val newStatus = data["status"]
          val reviewerNote = data["reviewerNote"]
          if (!requestId.isNullOrBlank() && !newStatus.isNullOrBlank()) {
            dao.updateLeaveStatus(requestId, newStatus, reviewerNote)
          }

          if (secureTokenStore.isLeaveAlertsEnabled()) {
            notificationHelper.showLeaveStatusNotification(
              title = title ?: "Leave Request Update",
              body =
                body
                  ?: "Your leave request status changed to ${newStatus ?: "UPDATED"}.",
            )
          }
        }

        TYPE_PAYROLL_PROCESSED -> {
          val monthLabel = data["monthLabel"] ?: "Current Cycle"
          val dateLabel = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())
          dao.insertAnnouncements(
            listOf(
              AnnouncementEntity(
                id = "ann_fcm_${UUID.randomUUID().toString().take(8)}",
                title = title ?: "Payroll Processed — $monthLabel",
                summary =
                  body ?: "Your payslip, SSF, and TDS statements for $monthLabel are ready.",
                category = "PAYROLL",
                publishedAt = dateLabel,
                isUrgent = true,
              )
            )
          )

          if (secureTokenStore.isPayrollAlertsEnabled()) {
            notificationHelper.showPayrollAlertNotification(
              title = title ?: "Payroll Processing Alert",
              body = body ?: "Your $monthLabel payslip is now finalized and available.",
            )
          }
        }

        else -> {
          if (secureTokenStore.isLeaveAlertsEnabled()) {
            notificationHelper.showLeaveStatusNotification(
              title = title ?: "PulseHR Alert",
              body = body ?: "You have a new HR update.",
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val TYPE_LEAVE_STATUS_UPDATE = "LEAVE_STATUS_UPDATE"
    const val TYPE_PAYROLL_PROCESSED = "PAYROLL_PROCESSED"
  }
}
