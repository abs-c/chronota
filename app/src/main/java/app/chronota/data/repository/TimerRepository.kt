package app.chronota.data.repository

import androidx.room.withTransaction
import app.chronota.data.entity.*
import java.time.Duration
import java.time.Instant
import app.chronota.domain.phaseEnd

/** What a running timer contributes to the record editor that finishes it. */
data class TimerDraft(val record: Record, val properties: Map<Long, String>)

class TimerRepository(private val records: TimeRepository) {
    private val db = records.database
    private val dao = db.dao()
    private fun now() = records.clock.instant()

    suspend fun start(title: String, categoryId: Long?, planId: Long?, mode: TimerMode = TimerMode.TIMER,
        workMinutes: Int = 25, breakMinutes: Int = 5, cycles: Int = 4, properties: Map<Long, String> = emptyMap()) = db.withTransaction {
        if (dao.currentTimer() != null) throw RuleViolation("timer_running")

        records.validateCategory(categoryId)
        // Required attributes are only demanded when the finished record is saved or edited.
        records.validateProperties(categoryId, properties, required = false)

        if (workMinutes !in 1..180 || breakMinutes !in 1..60 || cycles !in 1..12) throw RuleViolation("invalid_time")
        dao.clearSlices()
        dao.putTimer(TimerSession(title = title.trim(), categoryId = categoryId, sourcePlanId = null, mode = mode,
            startTimestamp = now(), workMinutes = workMinutes, breakMinutes = breakMinutes, cycles = cycles,
            propertiesJson = org.json.JSONObject().apply { properties.forEach { (key, value) -> put(key.toString(), value) } }.toString()))
    }

    /**
     * Throws the running timer away. Nothing is recorded — a cancelled run is not a moment that
     * happened, so unlike [finish] it leaves neither a record nor a slice behind.
     */
    suspend fun cancel() = db.withTransaction {
        dao.clearSlices()
        dao.clearTimer()
    }

    /** [properties] are the attribute values entered when the timer is finished. */
    suspend fun finish(properties: Map<Long, String> = emptyMap()): Int = db.withTransaction {
        settle(now())
        var session = dao.currentTimer() ?: return@withTransaction 0
        if (properties.isNotEmpty()) {
            val merged = org.json.JSONObject(session.propertiesJson.ifBlank { "{}" })
                .apply { properties.forEach { (key, value) -> put(key.toString(), value) } }.toString()
            session = session.copy(propertiesJson = merged)
            dao.putTimer(session)
        }
        val count = flushWork(session, now())
        dao.clearTimer()
        count
    }

    /** The record the running timer would save, with the values already entered for it. */
    suspend fun draft(): TimerDraft? = db.withTransaction {
        settle(now())
        val session = dao.currentTimer() ?: return@withTransaction null
        val end = now()
        val start = dao.slices().minOfOrNull { it.start } ?: session.segmentStartedAt ?: session.startTimestamp
        TimerDraft(
            Record(title = session.title, categoryId = session.categoryId, sourcePlanId = null,
                startTime = minOf(start, end), endTime = end,
                createdBy = if (session.mode == TimerMode.TIMER) RecordSource.TIMER else RecordSource.POMODORO,
                timerToken = session.token, timerPart = session.nextPart),
            if (session.propertiesJson.isBlank()) emptyMap() else org.json.JSONObject(session.propertiesJson).let { json -> json.keys().asSequence().associate { it.toLong() to json.getString(it) } })
    }

    /** Stores the edited [record] instead of the timer's own one and stops the timer. */
    suspend fun finishAs(record: Record, properties: Map<Long, String>): Long = db.withTransaction {
        settle(now())
        dao.clearSlices()
        dao.clearTimer()
        records.saveRecord(record.copy(id = 0), properties)
    }

    suspend fun advance(): Boolean = db.withTransaction { settle(now()) }

    private suspend fun settle(instant: Instant): Boolean {
        var session = dao.currentTimer() ?: return false
        var changed = false
        while (session.mode == TimerMode.POMODORO && session.pausedAt == null && session.phaseEnd() <= instant) {
            changed = true
            val boundary = session.phaseEnd()
            if (session.phase == TimerPhase.WORK) {
                val count = flushWork(session, boundary)
                if (session.cycle >= session.cycles) { dao.clearTimer(); return true }
                session = session.copy(phase = TimerPhase.BREAK, phaseStartedAt = boundary, phasePausedDuration = 0,
                    segmentStartedAt = null, nextPart = session.nextPart + count)
            } else {
                session = session.copy(phase = TimerPhase.WORK, phaseStartedAt = boundary, phasePausedDuration = 0,
                    segmentStartedAt = boundary, cycle = session.cycle + 1)
            }
            dao.putTimer(session)
        }
        return changed
    }

    private suspend fun flushWork(session: TimerSession, end: Instant): Int {
        val intervals = dao.slices().map { it.start to it.end }.toMutableList()
        if (session.phase == TimerPhase.WORK) session.segmentStartedAt?.let { start ->
            if (Duration.between(start, end).toMillis() > 0) intervals.add(start to end)
        }
        intervals.forEachIndexed { index, (start, stop) ->
            records.saveRecord(Record(title = session.title, categoryId = session.categoryId, sourcePlanId = session.sourcePlanId,
                startTime = start, endTime = stop, createdBy = if (session.mode == TimerMode.TIMER) RecordSource.TIMER else RecordSource.POMODORO,
                timerToken = session.token, timerPart = session.nextPart + index),
                properties = if (session.propertiesJson.isBlank()) emptyMap() else org.json.JSONObject(session.propertiesJson).let { json -> json.keys().asSequence().associate { it.toLong() to json.getString(it) } }, enforceRequired = false)
        }
        dao.clearSlices()
        return intervals.size
    }
}
