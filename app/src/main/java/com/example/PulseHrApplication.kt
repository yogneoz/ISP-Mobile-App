package com.example

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.example.data.repository.RouteTrackingRepositoryImpl
import com.example.location.GeofenceReminderScheduler
import com.example.notifications.FcmTokenManager
import com.example.notifications.PulseHrNotificationHelper
import com.example.security.SessionManager
import com.example.worker.SyncQueueProcessor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PulseHrApplication : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var sessionManager: SessionManager
  @Inject lateinit var notificationHelper: PulseHrNotificationHelper
  @Inject lateinit var fcmTokenManager: FcmTokenManager
  @Inject lateinit var syncQueueProcessor: SyncQueueProcessor
  @Inject lateinit var geofenceReminderScheduler: GeofenceReminderScheduler
  @Inject lateinit var routeTrackingRepository: RouteTrackingRepositoryImpl

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  override fun onCreate() {
    super.onCreate()
    ProcessLifecycleOwner.get().lifecycle.addObserver(sessionManager)
    notificationHelper.createNotificationChannels()
    fcmTokenManager.initializeAndSyncToken()
    syncQueueProcessor.ensurePeriodicCatchUpSync()
    routeTrackingRepository.ensurePeriodicLocationSyncScheduled()
    geofenceReminderScheduler.ensureScheduled()
  }
}
