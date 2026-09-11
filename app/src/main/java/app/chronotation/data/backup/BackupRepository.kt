package app.chronotation.data.backup

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.chronotation.data.db.AppDatabase
import app.chronotation.data.entity.Category
import java.time.Clock
import java.time.Instant

/** What a backup or a restore moved, so the UI can say something concrete rather than "done". */
data class BackupSummary(val categories: Int, val plans: Int, val records: Int, val goals: Int, val reminders: Int) {
    val total: Int get() = categories + plans + records + goals + reminders
}

/**
 * Reads every table into one document and puts one back.
 *
 * A restore is all-or-nothing: the tables are emptied and refilled inside a single transaction, so a
 * malformed file or a foreign key that cannot be satisfied leaves the database exactly as it was. That
 * is the whole reason the deletes live here instead of a wipe-then-import two-step.
 */
class BackupRepository(private val database: AppDatabase, private val clock: Clock) {
    private val dao get() = database.dao()

    suspend fun export(): String {
        val data = BackupFormat.Data(
            categories = dao.allCategories(), plans = dao.allPlans(), records = dao.allRecords(),
            reminders = dao.allReminders(), goals = dao.allGoals(), definitions = dao.allDefinitions(),
            values = dao.allValues(), planValues = dao.allPlanValues(),
        )
        return BackupFormat.write(data, database.openHelper.readableDatabase.version, clock.instant())
    }

    suspend fun restore(text: String): BackupSummary = database.withTransaction {
        val data = BackupFormat.read(text)
        // Children first, so no delete trips a foreign key.
        // restore starts the tracking from scratch anyway.
        dao.clearSlices()
        dao.clearTimer()
        // Children first, so no delete trips a foreign key.
        dao.clearPlanValueRows()
        dao.clearValueRows()
        dao.clearReminderRows()
        dao.clearGoalRows()
        dao.clearRecordRows()
        dao.clearPlanRows()
        dao.clearDefinitionRows()
        dao.clearChildCategories()
        dao.clearRootCategories()
        // ...and parents first when writing them back. A category can point at another category, so
        // the list is walked until every row whose parent exists has been written.
        insertCategories(data.categories)
        data.definitions.forEach { dao.putDefinition(it) }
        data.plans.forEach { dao.putPlan(it) }
        data.records.forEach { dao.putRecord(it) }
        data.reminders.forEach { dao.putReminder(it) }
        data.goals.forEach { dao.putGoal(it) }
        data.values.forEach { dao.putValue(it) }
        data.planValues.forEach { dao.putPlanValue(it) }
        BackupSummary(data.categories.size, data.plans.size, data.records.size, data.goals.size, data.reminders.size)
    }

    private suspend fun insertCategories(categories: List<Category>) {
        val remaining = categories.toMutableList()
        val written = mutableSetOf<Long>()
        // Bounded by the number of rows: each pass writes at least the roots, and a cycle cannot exist
        // in a tree, so the loop always drains.
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { it.parentId == null || it.parentId in written }
            if (ready.isEmpty()) {
                // Nothing has a satisfied parent left; write the rest as roots rather than lose them.
                remaining.forEach { dao.putCategory(it.copy(parentId = null)) }
                return
            }
            ready.forEach { dao.putCategory(it); written += it.id }
            remaining.removeAll(ready)
        }
    }

    /** The file name a backup takes, so successive runs do not overwrite one another. */
    fun fileName(at: Instant = clock.instant()): String = "chronota-" + at.toString().replace(":", "-").substringBefore(".") + ".json"

    /** The single file a WebDAV backup overwrites. */
    fun latestName(): String = "chronota-latest.json"

    /**
     * Writes a backup to WebDAV. One rolling file rather than a dated archive: the space is the user's
     * and the file is small, but a growing pile of them helps nobody — a dated copy is what the local
     * export is for.
     */
    suspend fun upload(target: WebDavTarget, folder: String): String = withContext(Dispatchers.IO) {
        val text = export()
        val client = WebDavClient(target, folder)
        client.ensureFolder()
        client.put(latestName(), text)
        latestName()
    }

    suspend fun exportText(): String = export()

    /** Reads the rolling file back, for a restore. */
    suspend fun download(target: WebDavTarget, folder: String): String = withContext(Dispatchers.IO) {
        WebDavClient(target, folder).get(latestName())
    }

    /** Checks an address, an account and a folder before anything is written to them. */
    suspend fun verify(target: WebDavTarget, folder: String): WebDavFailure? = withContext(Dispatchers.IO) {
        try {
            val client = WebDavClient(target, folder)
            client.ensureFolder()
            client.probe()
            null
        } catch (error: WebDavException) {
            error.failure
        }
    }
}
