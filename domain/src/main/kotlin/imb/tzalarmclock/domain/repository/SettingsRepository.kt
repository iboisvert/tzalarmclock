package imb.tzalarmclock.domain.repository

import imb.tzalarmclock.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Storage for the app-wide settings.
 *
 * Whole-object reads and writes, matching the spec's "saved whenever the
 * settings page is closed, loaded whenever the app is opened".
 */
interface SettingsRepository {

    /**
     * The current settings, emitting again on every change. Emits
     * [AppSettings.DEFAULTS] on a fresh install.
     */
    fun observeSettings(): Flow<AppSettings>

    /** One-shot read, for callers that only need a value once (e.g. a ringing alarm). */
    suspend fun getSettings(): AppSettings

    /** Replaces the stored settings wholesale. */
    suspend fun save(settings: AppSettings)
}
