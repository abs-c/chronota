package app.chronota.data.backup

import app.chronota.data.repository.AppPreferences
import app.chronota.data.repository.PreferencesRepository
import java.time.Clock
import kotlinx.coroutines.flow.first

/**
 * The one place a WebDAV backup happens, whether a person asked for it or the alarm did: it reads the
 * stored account, does the transfer, and records when and how it went so the settings page can say.
 */
class BackupCoordinator(
    private val backups: BackupRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
) {
    suspend fun current(): AppPreferences = preferences.preferences.first()

    /** Uploads now and records the outcome. Returns the failure, or null when the backup landed. */
    suspend fun uploadNow(): WebDavFailure? {
        val current = preferences.preferences.first()
        if (!current.webDavConfigured) return WebDavFailure.NOT_CONFIGURED
        val failure = runCatching { backups.upload(current.webDavTarget, current.webDavFolder) }
            .exceptionOrNull()
            .let { error -> (error as? WebDavException)?.failure ?: if (error != null) WebDavFailure.SERVER else null }
        preferences.recordBackup(clock.instant(), failure)
        return failure
    }

    /**
     * Pulls the rolling file back down and restores it. Throws [WebDavException] when it cannot. The
     * target is what the settings panel has on screen, so a restore does not need a save first.
     */
    suspend fun restoreNow(target: WebDavTarget, folder: String): BackupSummary {
        if (!target.configured) throw WebDavException(WebDavFailure.NOT_CONFIGURED)
        return backups.restore(backups.download(target, folder))
    }

    /** Checks values the user has typed but not saved yet. */
    suspend fun verify(url: String, user: String, password: String, folder: String): WebDavFailure? =
        backups.verify(WebDavTarget(url, user, password), folder)

    suspend fun exportText(): String = backups.export()

    /** The name a local export is offered under. */
    fun fileName(): String = backups.fileName()

    /** Restores from text the user picked off the device. */
    suspend fun restoreText(text: String): BackupSummary = backups.restore(text)
}
