package app.chronota.data.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.chronota.ChronotaApplication
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

import kotlinx.coroutines.launch

/**
 * The automatic WebDAV backup: one alarm a day at a wall-clock time the user picks.
 *
 * An alarm rather than a periodic worker because the app already schedules exact alarms for reminders,
 * and because a backup wants to land at a stated time rather than whenever the system feels like it.
 * The alarm is re-armed after every run and after a reboot.
 */
object BackupScheduler {
    const val ACTION = "app.chronota.BACKUP"
    private const val REQUEST = 4100

    /** The next occurrence of [minutesOfDay] after [now]: today if it is still ahead, else tomorrow. */
    fun nextTrigger(minutesOfDay: Int, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val local = now.atZone(zone)
        val today = local.toLocalDate().atTime(LocalTime.ofSecondOfDay(minutesOfDay.coerceIn(0, 1439) * 60L))
        val candidate = today.atZone(zone).toInstant()
        return if (candidate > now) candidate else today.plusDays(1).atZone(zone).toInstant()
    }

    fun schedule(context: Context, minutesOfDay: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextTrigger(minutesOfDay, Instant.now()).toEpochMilli()
        val intent = pending(context)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent); return }
            catch (_: SecurityException) { /* Permission can be revoked between the check and the call. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(context, REQUEST,
        Intent(context, BackupReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/**
 * Runs the automatic backup and re-arms tomorrow's. Nothing here touches the UI: if it fails, the run
 * is simply skipped and the failure is recorded as the last attempt, so the settings page can say so.
 */
class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BackupScheduler.ACTION) return
        val pending = goAsync()
        val app = context.applicationContext as ChronotaApplication
        app.applicationScope.launch {
            try {
                val preferences = app.backups.current()
                if (preferences.webDavConfigured) app.backups.uploadNow()
                if (preferences.autoBackupEnabled) BackupScheduler.schedule(context, preferences.autoBackupMinutes)
            } finally {
                pending.finish()
            }
        }
    }
}
