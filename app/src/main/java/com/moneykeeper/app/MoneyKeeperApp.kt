package com.moneykeeper.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application-класс. `@HiltAndroidApp` запускает Hilt-граф.
 *
 * WorkManager (§8.7) и NotificationChannels (§8.2) будут инициализированы здесь после
 * реализации раздела 8. Сейчас — голый скелет.
 */
@HiltAndroidApp
class MoneyKeeperApp : Application()
