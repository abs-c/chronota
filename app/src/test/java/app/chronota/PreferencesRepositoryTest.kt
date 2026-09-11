package app.chronota

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.chronota.data.repository.PreferencesRepository
import app.chronota.domain.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun themeSurvivesClosingAndReopeningTheStore() = runBlocking {
        val file = File(folder.root, "test.preferences_pb")
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
        val first = PreferencesRepository(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        try {
            assertEquals(ThemeMode.SYSTEM, first.preferences.first().themeMode)
            first.setTheme(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, first.preferences.first().themeMode)
        } finally {
            firstJob.cancel()
            firstJob.join()
        }
        val secondJob = SupervisorJob()
        val secondScope = CoroutineScope(secondJob + Dispatchers.IO)
        try {
            val second = PreferencesRepository(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(ThemeMode.DARK, second.preferences.first().themeMode)
        } finally {
            secondJob.cancel()
            secondJob.join()
        }
    }

    @Test fun unknownPreferenceValueFallsBackWithoutErasingOtherKeys() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // Android FileStorage uses POSIX rename-over-existing semantics. Use the official
            // Okio storage adapter for this multi-write host test, including Windows runners.
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                    File(folder.root, "unknown.preferences_pb").toOkioPath()
                },
            )
            val otherKey = stringPreferencesKey("future_preference")
            store.edit {
                it[stringPreferencesKey("theme_mode")] = "FUTURE_MODE"
                it[otherKey] = "preserve me"
            }
            val repository = PreferencesRepository(store)
            assertEquals(ThemeMode.SYSTEM, repository.preferences.first().themeMode)
            repository.setTheme(ThemeMode.LIGHT)
            repository.setWeekView(false)
            repository.setLanguage("zh-CN")
            repository.setDayStart(240)
            repository.setPreferCategory(true)
            assertEquals(240, repository.preferences.first().dayStartMinutes)
            assertEquals(true, repository.preferences.first().preferCategoryName)
            assertEquals("zh-CN", repository.preferences.first().language)
            repository.setTimer(app.chronota.data.entity.TimerMode.POMODORO, 30, 8, 6)
            assertEquals(app.chronota.data.entity.TimerMode.POMODORO, repository.preferences.first().timerMode)
            assertEquals(30, repository.preferences.first().workMinutes)
            assertEquals(8, repository.preferences.first().breakMinutes)
            assertEquals(6, repository.preferences.first().timerCycles)
            assertEquals(false, repository.preferences.first().weekView)
            assertEquals(ThemeMode.LIGHT, repository.preferences.first().themeMode)
            assertEquals("preserve me", store.data.first()[otherKey])
        } finally {
            scope.cancel()
        }
    }
}
