package app.chronotation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.chronotation.data.db.AppDatabase
import app.chronotation.data.entity.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @Test fun versionOneUpgradePreservesRecordsPropertiesAndReminders() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-2.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/1.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0),(2,'Reading',1,4283669953,'book',0)")
            old.execSQL("INSERT INTO plans(id,title,categoryId,scheduledDate,startTime,endTime,endDayOffset,zoneId,estimatedDuration,deadline,note,createdAt,updatedAt) VALUES(1,'Old day',2,20000,NULL,NULL,0,'UTC',30,NULL,'Keep note',1,1)")
            old.execSQL("INSERT INTO records(id,title,categoryId,startTime,endTime,sourcePlanId,note,createdBy,createdAt,timerToken,timerPart) VALUES(1,'Actual',2,1000,2000,1,'Keep record','MANUAL',1,NULL,NULL)")
            old.execSQL("INSERT INTO property_definitions(id,categoryId,name,type,options,sortOrder) VALUES(1,2,'Pages','NUMBER','',0)")
            old.execSQL("INSERT INTO property_values(recordId,definitionId,value) VALUES(1,1,'24')")
            old.execSQL("INSERT INTO reminders(id,planId,anchor,minutesBefore,deliveredAt) VALUES(1,1,'START',15,NULL)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val dao = db.dao()
            assertTrue(dao.plan(1)!!.allDay)
            assertEquals("Keep note", dao.plan(1)!!.note)
            assertEquals(Recurrence.NONE, dao.plan(1)!!.recurrence)
            assertEquals(1, dao.plan(1)!!.recurrenceInterval)
            assertNull(dao.plan(1)!!.recurrenceUntil)
            assertEquals("Actual", dao.record(1)!!.title)
            assertEquals("24", dao.recordValues(1).single().value)
            assertFalse(dao.allDefinitions().single().required)
            assertFalse(dao.allDefinitions().single().multiline)
            assertEquals("", dao.allDefinitions().single().optionColors)
            assertEquals(15, dao.allReminders().single().minutesBefore)
            dao.putPlanValue(PlanPropertyValue(1, 1, "40"))
            assertEquals("40", dao.valuesForPlan(1).single().value)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun versionFourUpgradeAddsRecurrenceDefaultsWithoutTouchingRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-4-5.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/4.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO plans(id,title,categoryId,scheduledDate,startTime,endTime,endDayOffset,zoneId,estimatedDuration,deadline,note,createdAt,updatedAt,allDay) VALUES(1,'Keep me',1,20000,36000,39600,0,'UTC',NULL,NULL,'note',1,1,0)")
            old.version = 4
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val dao = db.dao()
            val plan = dao.plan(1)!!
            assertEquals("Keep me", plan.title)
            assertEquals("note", plan.note)
            assertEquals(1L, plan.categoryId)
            assertEquals(Recurrence.NONE, plan.recurrence)
            assertEquals(1, plan.recurrenceInterval)
            assertNull(plan.recurrenceUntil)
            dao.putPlan(plan.copy(recurrence = Recurrence.MONTHLY, recurrenceInterval = 2, recurrenceUntil = LocalDate.of(2026, 12, 31)))
            val stored = dao.plan(plan.id)!!
            assertEquals(Recurrence.MONTHLY, stored.recurrence)
            assertEquals(2, stored.recurrenceInterval)
            assertEquals(LocalDate.of(2026, 12, 31), stored.recurrenceUntil)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun versionFiveUpgradeAddsGoalsWithoutTouchingRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-5-6.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/5.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO records(id,title,categoryId,startTime,endTime,sourcePlanId,note,createdBy,createdAt,timerToken,timerPart) VALUES(1,'Keep me',1,1000,2000,NULL,'note','MANUAL',1,NULL,NULL)")
            old.version = 5
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val dao = db.dao()
            assertEquals("Keep me", dao.record(1)!!.title)
            val goal = Goal(name = "Sleep", categoryId = 1, metric = GoalMetric.TIME, direction = GoalDirection.AT_LEAST, target = 300 * 60_000L, periodUnit = GoalUnit.WEEK, repeat = GoalRepeat.RESET)
            val id = dao.putGoal(goal)
            val stored = dao.goal(id)!!
            assertEquals("Sleep", stored.name)
            assertEquals(1L, stored.categoryId)
            assertEquals(GoalMetric.TIME, stored.metric)
            assertEquals(300 * 60_000L, stored.target)
            assertEquals(GoalUnit.WEEK, stored.periodUnit)
            assertEquals(1, stored.periodValue)
            assertEquals(GoalRepeat.RESET, stored.repeat)
            dao.deleteGoal(id)
            assertNull(dao.goal(id))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun versionSixUpgradeAddsGoalNameAndPeriodValueWithoutTouchingRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-6-7.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/6.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO goals(id,categoryId,titleFilter,propertyDefinitionId,propertyValue,metric,direction,target,period,repeat,enabled,createdAt,sortOrder) VALUES(1,1,'',NULL,'','TIME','AT_LEAST',18000000,'WEEK','CYCLE',1,1,0)")
            old.version = 6
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val goal = db.dao().goal(1)!!
            assertEquals("", goal.name)
            assertEquals(1, goal.periodValue)
            assertEquals(GoalUnit.WEEK, goal.periodUnit)
            assertEquals(GoalRepeat.CYCLE, goal.repeat)
            assertEquals(18_000_000L, goal.target)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun versionSevenUpgradeAddsGoalStartHistoryAndPlanSkips() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-7-8.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/7.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO plans(id,title,categoryId,scheduledDate,startTime,endTime,endDayOffset,zoneId,estimatedDuration,deadline,note,createdAt,updatedAt,allDay,recurrence,recurrenceInterval,recurrenceUntil) VALUES(1,'Keep me',1,20000,36000,39600,0,'UTC',NULL,NULL,'note',1,1,0,'WEEKLY',2,NULL)")
            old.execSQL("INSERT INTO goals(id,name,categoryId,titleFilter,propertyDefinitionId,propertyValue,metric,direction,target,period,periodValue,repeat,enabled,createdAt,sortOrder) VALUES(1,'Sleep',1,'',NULL,'','TIME','AT_LEAST',18000000,'WEEK',1,'CYCLE',1,12345,0)")
            old.version = 7
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val dao = db.dao()
            assertEquals("Keep me", dao.plan(1)!!.title)
            assertEquals("", dao.plan(1)!!.skipDates)
            val goal = dao.goal(1)!!
            assertEquals("Sleep", goal.name)
            assertEquals(12345L, goal.startAt.toEpochMilli())
            assertNull(goal.expiredAt)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    /** The registered migration list covers every schema version the app has ever shipped. */
    @Test fun everyShippedSchemaHasAMigration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-registered.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/9.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT OR REPLACE INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0)")
            old.version = 9
        }
        // Opening it with the registered migration list proves none is missing; an unregistered hop
        // would throw here instead of on the user's device.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            assertEquals("Study", db.dao().category(1)!!.name)
            assertTrue(db.dao().allGoals().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    /** Finished cycles live in their own goals now, so deleting one instance keeps the others. */
    @Test fun finishedCyclesBecomeExpiredGoals() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-9-10.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/9.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO plans(id,title,categoryId,scheduledDate,startTime,endTime,endDayOffset,zoneId,estimatedDuration,deadline,note,createdAt,updatedAt,allDay,recurrence,recurrenceInterval,recurrenceUntil) VALUES(1,'Keep me',1,20000,36000,39600,0,'UTC',NULL,NULL,'note',1,1,0,'WEEKLY',2,NULL)")
            old.execSQL("INSERT INTO goals(id,name,categoryId,titleFilter,propertyDefinitionId,propertyValue,metric,direction,target,period,periodValue,repeat,enabled,startAt,createdAt,sortOrder) VALUES(1,'Sleep',1,'',NULL,'','TIME','AT_LEAST',18000000,'WEEK',1,'CYCLE',1,5000,12345,0)")
            old.execSQL("INSERT INTO goal_entries(id,goalId,name,categoryId,metric,direction,target,actual,startAt,endAt,createdAt) VALUES(7,1,'Sleep',1,'TIME','AT_LEAST',18000000,12000000,1000,5000,1000)")
            old.version = 9
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val dao = db.dao()
            val goals = dao.allGoals().sortedBy { it.startAt }
            assertEquals(2, goals.size)
            // The finished cycle came over as its own expired goal, with the goal that produced it.
            val finished = goals.first()
            assertEquals("Sleep", finished.name)
            assertEquals(1000L, finished.startAt.toEpochMilli())
            assertEquals(5000L, finished.expiredAt!!.toEpochMilli())
            assertEquals(18_000_000L, finished.target)
            val running = goals.last()
            assertEquals(5000L, running.startAt.toEpochMilli())
            assertNull(running.expiredAt)
            // Deleting one of them leaves the other alone.
            dao.deleteGoal(finished.id)
            assertEquals(listOf(running.id), dao.allGoals().map { it.id })
            assertEquals("Keep me", dao.plan(1)!!.title)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    /** Recorded moments are kept to the minute, so upgrading clears seconds already in the database. */
    @Test fun storedSecondsAreClearedOnUpgrade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-10-11.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/app.chronotation.data.db.AppDatabase/10.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO categories(id,name,parentId,color,icon,sortOrder) VALUES(1,'Study',NULL,NULL,NULL,0)")
            // A plan at 10:00:59-11:00:30, and a record from 1m00.005s to 2m00.999s.
            old.execSQL("INSERT INTO plans(id,title,categoryId,scheduledDate,startTime,endTime,endDayOffset,zoneId,estimatedDuration,deadline,note,createdAt,updatedAt,allDay) VALUES(1,'Keep me',1,20000,36059,39630,0,'UTC',NULL,NULL,'note',1,1,0)")
            old.execSQL("INSERT INTO records(id,title,categoryId,startTime,endTime,sourcePlanId,note,createdBy,createdAt,timerToken,timerPart) VALUES(1,'Keep me too',1,60005,120999,NULL,'note','TIMER',1,NULL,NULL)")
            old.version = 10
        }
        // Only this one hop, to prove it is the hop that clears the seconds.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_10_11).build()
        try {
            val dao = db.dao()
            // Only the remainder under a minute goes: each row keeps the minute it fell in.
            val plan = dao.plan(1)!!
            assertEquals(LocalTime.of(10, 0), plan.startTime)
            assertEquals(LocalTime.of(11, 0), plan.endTime)
            assertEquals("Keep me", plan.title)
            assertEquals("note", plan.note)
            val record = dao.record(1)!!
            assertEquals(60_000L, record.startTime.toEpochMilli())
            assertEquals(120_000L, record.endTime.toEpochMilli())
            assertEquals("Keep me too", record.title)
            assertEquals(RecordSource.TIMER, record.createdBy)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
