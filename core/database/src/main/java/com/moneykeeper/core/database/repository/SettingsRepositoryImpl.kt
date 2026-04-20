package com.moneykeeper.core.database.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moneykeeper.core.domain.repository.AppSettings
import com.moneykeeper.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val DEPOSIT_NOTIFICATIONS = booleanPreferencesKey("deposit_notifications_enabled")
        val RECURRING_REMINDERS   = booleanPreferencesKey("recurring_reminders_enabled")
        val DEFAULT_NOTIFY_DAYS   = intPreferencesKey("default_notify_days_before")
        val THEME_MODE            = stringPreferencesKey("theme_mode")
        val CURRENCY_CODE         = stringPreferencesKey("currency_code")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            depositNotificationsEnabled = prefs[Keys.DEPOSIT_NOTIFICATIONS] ?: true,
            recurringRemindersEnabled   = prefs[Keys.RECURRING_REMINDERS] ?: true,
            defaultNotifyDaysBefore     = prefs[Keys.DEFAULT_NOTIFY_DAYS] ?: 7,
            themeMode                   = prefs[Keys.THEME_MODE] ?: "system",
            currencyCode                = prefs[Keys.CURRENCY_CODE] ?: "RUB",
        )
    }

    override suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEPOSIT_NOTIFICATIONS] = settings.depositNotificationsEnabled
            prefs[Keys.RECURRING_REMINDERS]   = settings.recurringRemindersEnabled
            prefs[Keys.DEFAULT_NOTIFY_DAYS]   = settings.defaultNotifyDaysBefore
            prefs[Keys.THEME_MODE]            = settings.themeMode
            prefs[Keys.CURRENCY_CODE]         = settings.currencyCode
        }
    }
}
