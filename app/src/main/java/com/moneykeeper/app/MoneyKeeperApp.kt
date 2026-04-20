package com.moneykeeper.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.moneykeeper.app.crash.CrashLogger
import com.moneykeeper.app.notification.NotificationChannels
import com.moneykeeper.app.worker.WorkerScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyKeeperApp : Application(), Configuration.Provider {

    @Inject lateinit var workerConfiguration: Configuration

    override val workManagerConfiguration get() = workerConfiguration

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        NotificationChannels.createAll(this)
        WorkerScheduler.scheduleAll(this)
    }
}
