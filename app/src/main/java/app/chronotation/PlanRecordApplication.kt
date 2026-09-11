package app.chronotation

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import app.chronotation.data.repository.PreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import app.chronotation.data.entity.TimerMode
import app.chronotation.domain.phaseEnd
import java.time.Duration
import java.time.Instant

class PlanRecordApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> android.util.Log.e("plan-record", "Background reconciliation failed", error) })
    private val preferencesStore by lazy { PreferenceDataStoreFactory.create(scope = applicationScope, produceFile = { preferencesDataStoreFile("preferences") }) }
    val preferencesRepository by lazy { PreferencesRepository(preferencesStore) }
    private val databaseDelegate = lazy { app.chronotation.data.db.AppDatabase.open(this) }
    val database by databaseDelegate
    val repository by lazy { app.chronotation.data.repository.TimeRepository(database) }
    val timers by lazy { app.chronotation.data.repository.TimerRepository(repository) }
    val notifications by lazy { app.chronotation.feature.timer.NotificationScheduler(this, repository) }
    val backups by lazy {
        app.chronotation.data.backup.BackupCoordinator(
            app.chronotation.data.backup.BackupRepository(database, repository.clock), preferencesRepository, repository.clock)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            preferencesRepository.installDefaults(repository)
            timers.advance()
            combine(database.dao().plans(), database.dao().reminders(), database.dao().timer(), preferencesRepository.preferences.map { it.language }.distinctUntilChanged()) { _, _, _, _ -> Unit }
                .collect { notifications.reschedule() }
        }
        applicationScope.launch {
            database.dao().timer().collectLatest { timer ->
                if (timer?.mode == TimerMode.POMODORO && timer.pausedAt == null) {
                    delay(Duration.between(Instant.now(), timer.phaseEnd()).toMillis().coerceAtLeast(1))
                    withContext(NonCancellable) { if (timers.advance()) notifications.pomodoroNotice() }
                }
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        if (databaseDelegate.isInitialized()) database.close()
        super.onTerminate()
    }
}
