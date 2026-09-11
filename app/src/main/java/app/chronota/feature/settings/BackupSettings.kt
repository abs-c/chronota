package app.chronota.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.chronota.R
import app.chronota.data.backup.WebDavException
import app.chronota.data.backup.WebDavFailure
import app.chronota.data.repository.AppPreferences
import app.chronota.ui.components.*
import app.chronota.ui.theme.*
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the last attempt looks like right now, including one the user just triggered from here. */
private sealed interface BackupState {
    data object Idle : BackupState
    data object Busy : BackupState
    data class Failed(val error: Int) : BackupState
}

@Composable fun WebDavSettings(
    value: AppPreferences,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (url: String, user: String, password: String, folder: String, auto: Boolean, minutes: Int, done: () -> Unit) -> Unit,
    onTest: (url: String, user: String, password: String, folder: String, result: (Int?) -> Unit) -> Unit,
    onBackupNow: (url: String, user: String, password: String, folder: String, auto: Boolean, minutes: Int, result: (Int?) -> Unit) -> Unit,
    onRestore: (url: String, user: String, password: String, folder: String, result: (Int?) -> Unit) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(value.webDavUrl) }
    var user by rememberSaveable { mutableStateOf(value.webDavUser) }
    var password by rememberSaveable { mutableStateOf(value.webDavPassword) }
    var folder by rememberSaveable { mutableStateOf(value.webDavFolder) }
    var auto by rememberSaveable { mutableStateOf(value.autoBackupEnabled) }
    var time by rememberSaveable { mutableStateOf(value.autoBackupMinutes) }
    var state by remember { mutableStateOf<BackupState>(BackupState.Idle) }
    var message by remember { mutableStateOf<Int?>(null) }
    val zone = ZoneId.systemDefault()

    EditorSheet(R.string.backup_webdav, onDismiss) {
        PlainInput(stringResource(R.string.backup_url), url, { url = it }, keyboardType = KeyboardType.Uri)
        PlainInput(stringResource(R.string.backup_user), user, { user = it })
        PlainInput(stringResource(R.string.backup_password), password, { password = it }, password = true)
        PlainInput(stringResource(R.string.backup_folder), folder, { folder = it })
        OptionGroup {
            SwitchRow(R.string.backup_auto, auto, { auto = it })
            if (auto) TimeField(R.string.backup_auto_time, LocalTime.ofSecondOfDay(time * 60L), { time = it.hour * 60 + it.minute })
        }
        Text(lastBackupText(value, zone), Modifier.padding(horizontal = Space.md), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(stringResource(it), Modifier.padding(horizontal = Space.md), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        if (state is BackupState.Failed) Text(stringResource((state as BackupState.Failed).error), Modifier.padding(horizontal = Space.md), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        ActionButton(stringResource(R.string.backup_test), {
            state = BackupState.Busy
            onTest(url, user, password, folder) { error -> state = if (error == null) BackupState.Idle else BackupState.Failed(error); message = if (error == null) R.string.backup_ok else null }
        }, Modifier.testTag("backup_test"), enabled = !busy && state !is BackupState.Busy, secondary = true)
        ActionButton(stringResource(R.string.backup_now), {
            state = BackupState.Busy
            onBackupNow(url, user, password, folder, auto, time) { error -> state = if (error == null) BackupState.Idle else BackupState.Failed(error); if (error == null) message = R.string.backup_saved }
        }, Modifier.testTag("backup_now"), enabled = !busy && state !is BackupState.Busy, secondary = true)
        ActionButton(stringResource(R.string.backup_restore), {
            state = BackupState.Busy
            onRestore(url, user, password, folder) { error -> state = if (error == null) BackupState.Idle else BackupState.Failed(error); if (error == null) message = R.string.backup_restored }
        }, Modifier.testTag("backup_restore"), enabled = !busy && state !is BackupState.Busy, secondary = true)
        EditorActions(state is BackupState.Busy, { onSave(url, user, password, folder, auto, time, onDismiss) })
    }
}

@Composable private fun lastBackupText(value: AppPreferences, zone: ZoneId): String {
    val at = value.lastBackupAt ?: return stringResource(R.string.backup_never)
    val stamp = at.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    return stringResource(R.string.backup_last) + " · " + stamp
}

/** Turns any backup failure into the one line the settings page should show for it. */
fun backupErrorResource(error: Throwable): Int = when (error) {
    is WebDavException -> when (error.failure) {
        WebDavFailure.NOT_CONFIGURED -> R.string.backup_error_config
        WebDavFailure.AUTH -> R.string.backup_error_auth
        WebDavFailure.MISSING -> R.string.backup_error_missing
        WebDavFailure.ADDRESS -> R.string.backup_error_address
        WebDavFailure.NETWORK -> R.string.backup_error_network
        WebDavFailure.SERVER -> R.string.backup_error_server
    }
    is app.chronota.data.backup.BackupFormat.Unreadable -> R.string.backup_error_format
    else -> R.string.backup_error_server
}
