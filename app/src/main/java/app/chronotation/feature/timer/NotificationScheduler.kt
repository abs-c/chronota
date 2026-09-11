package app.chronotation.feature.timer

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import app.chronotation.MainActivity
import app.chronotation.PlanRecordApplication
import app.chronotation.R
import app.chronotation.data.repository.TimeRepository
import app.chronotation.domain.*
import app.chronotation.data.entity.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import app.chronotation.localizedContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

class NotificationScheduler(private val context: Context, private val repository: TimeRepository) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val mutex = Mutex()
    private val registered = mutableSetOf<Long>()

    private suspend fun textContext(): Context {
        val application = context.applicationContext as? PlanRecordApplication ?: return context
        return localizedContext(context, application.preferencesRepository.preferences.first().language)
    }
    private suspend fun taskName(title: String, categoryId: Long?): String {
        val preferences = (context.applicationContext as? PlanRecordApplication)?.preferencesRepository?.preferences?.first()
        val category = repository.dao.allCategories().firstOrNull { it.id == categoryId }
        return displayName(title, category?.name, preferences?.preferCategoryName == true, textContext().getString(R.string.uncategorized))
    }
    private suspend fun channel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, textContext().getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun alarmIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, AlarmReceiver::class.java).setAction(ALARM_ACTION).setData("planrecord://alarm/$id".toUri()).putExtra("alarm_id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun schedule(id: Long, time: Long) {
        val intent = alarmIntent(id)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent); return }
            catch (_: SecurityException) { /* Permission can be revoked between the check and call. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent)
    }
    private fun permitted(): Boolean = notifications.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    suspend fun reschedule() = mutex.withLock {
        channel()
        val dao = repository.dao
        val plans = dao.allPlans().associateBy { it.id }
        val reminders = dao.allReminders()
        val ids = reminders.map { it.id }.toSet()
        (registered - ids).forEach { alarms.cancel(alarmIntent(it)); notifications.cancel("reminder", it.toInt()) }
        registered.clear(); registered.addAll(ids)
        val now = repository.clock.instant()
        reminders.forEach { reminder ->
            alarms.cancel(alarmIntent(reminder.id))
            val plan = plans[reminder.planId]
            val trigger = plan?.let { reminder.trigger(it, now) }
            if (trigger == null) return@forEach
            // A recurring plan reaches a new occurrence: forget the previous delivery and arm again.
            val current = if (reminder.deliveredAt != null && plan?.isRecurring() == true && trigger > reminder.deliveredAt) {
                dao.putReminder(reminder.copy(deliveredAt = null)); reminder.copy(deliveredAt = null)
            } else reminder
            if (current.deliveredAt == null) {
                if (trigger > now) schedule(current.id, trigger.toEpochMilli())
                else if (Duration.between(trigger, now).toHours() < 24) deliver(current.id)
            }
        }
        val timer = dao.currentTimer()
        alarms.cancel(alarmIntent(TIMER_ALARM))
        if (timer?.mode == TimerMode.POMODORO) schedule(TIMER_ALARM, maxOf(timer.phaseEnd().toEpochMilli(), now.toEpochMilli() + 1000))
        if (timer == null) notifications.cancel(TIMER_NOTIFICATION)
        else if (permitted()) {
            // The notification names what is running instead of ticking the elapsed time.
            val label = textContext().getString(when {
                timer.mode != TimerMode.POMODORO -> R.string.timer_running
                timer.phase == TimerPhase.BREAK -> R.string.timer_break
                else -> R.string.timer_work
            })
            val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(taskName(timer.title, timer.categoryId)).setContentText(label).setContentIntent(contentIntent()).setOngoing(true).setOnlyAlertOnce(true)
                .setShowWhen(false).setUsesChronometer(false).build()
            try { notifications.notify(TIMER_NOTIFICATION, notification) } catch (_: SecurityException) { }
        }
    }

    suspend fun deliver(id: Long) {
        if (!permitted()) return
        channel()
        repository.database.withTransaction {
            val reminder = repository.dao.reminder(id) ?: return@withTransaction
            val plan = repository.dao.plan(reminder.planId) ?: return@withTransaction
            val now = repository.clock.instant()
            val trigger = reminder.trigger(plan, now) ?: return@withTransaction
            if (reminder.deliveredAt != null || trigger > now || Duration.between(trigger, now).toHours() >= 24) return@withTransaction
            val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(taskName(plan.title, plan.categoryId)).setContentText(textContext().getString(R.string.reminder_notification))
                .setContentIntent(contentIntent()).setAutoCancel(true).setOnlyAlertOnce(true).build()
            try {
                notifications.notify("reminder", id.toInt(), notification)
                repository.dao.putReminder(reminder.copy(deliveredAt = now))
            } catch (_: SecurityException) { /* Keep undelivered so granting permission can recover it. */ }
        }
    }

    suspend fun pomodoroNotice() {
        if (!permitted()) return
        channel()
        try { notifications.notify(POMODORO_NOTIFICATION, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(textContext().getString(R.string.pomodoro_complete)).setContentText(textContext().getString(R.string.app_name))
            .setContentIntent(contentIntent()).setAutoCancel(true).build()) } catch (_: SecurityException) { }
    }

    companion object {
        const val ALARM_ACTION = "app.chronotation.ALARM"
        const val TIMER_ALARM = -1L
        private const val TIMER_NOTIFICATION = -100
        private const val POMODORO_NOTIFICATION = -101
        private const val CHANNEL = "plans_and_timers"
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(NotificationScheduler.ALARM_ACTION, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        val app = context.applicationContext as PlanRecordApplication
        app.applicationScope.launch {
            try {
                val timerId = intent.getLongExtra("alarm_id", 0)
                if (timerId == NotificationScheduler.TIMER_ALARM) {
                    if (app.timers.advance()) app.notifications.pomodoroNotice()
                } else if (timerId > 0) app.notifications.deliver(timerId)
                app.timers.advance()
                app.notifications.reschedule()
            } finally { pending.finish() }
        }
    }
}
