package app.chronota.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.chronota.data.dao.AppDao
import app.chronota.data.entity.*

@Database(entities = [Category::class, Plan::class, Record::class, TimerSession::class,
    TimerSlice::class, Reminder::class, PropertyDefinition::class, PropertyValue::class, PlanPropertyValue::class, Goal::class], version = 11, exportSchema = true)
@TypeConverters(TimeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // One minute is the smallest unit the app records, so rows written before this rule
                // carry seconds that mean nothing. Only the remainder under a minute is cut, so every
                // row keeps the minute it fell in and no data is lost.
                db.execSQL("UPDATE records SET startTime = startTime - ((startTime % 60000) + 60000) % 60000")
                db.execSQL("UPDATE records SET endTime = endTime - ((endTime % 60000) + 60000) % 60000")
                db.execSQL("UPDATE plans SET startTime = startTime - ((startTime % 60) + 60) % 60 WHERE startTime IS NOT NULL")
                db.execSQL("UPDATE plans SET endTime = endTime - ((endTime % 60) + 60) % 60 WHERE endTime IS NOT NULL")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN expiredAt INTEGER")
                // Finished cycles become goals of their own, so history survives deleting the goal it
                // came from; instances are not linked to each other any more.
                db.execSQL("INSERT INTO goals (name, categoryId, titleFilter, propertyDefinitionId, propertyValue, metric, direction, target, period, periodValue, repeat, enabled, startAt, createdAt, sortOrder, expiredAt) " +
                    "SELECT name, categoryId, '', NULL, '', metric, direction, target, 'WEEK', 1, 'NONE', 1, startAt, createdAt, 0, endAt FROM goal_entries")
                db.execSQL("DROP TABLE goal_entries")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE property_definitions ADD COLUMN unit TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN startAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE goals SET startAt = createdAt")
                db.execSQL("UPDATE goals SET period = 'MINUTE' WHERE period = 'SECOND'")
                db.execSQL("ALTER TABLE plans ADD COLUMN skipDates TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS goal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, goalId INTEGER NOT NULL, name TEXT NOT NULL, categoryId INTEGER, metric TEXT NOT NULL, direction TEXT NOT NULL, target INTEGER NOT NULL, actual INTEGER NOT NULL, startAt INTEGER NOT NULL, endAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(goalId) REFERENCES goals(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_entries_goalId ON goal_entries(goalId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_entries_startAt ON goal_entries(startAt)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE goals ADD COLUMN periodValue INTEGER NOT NULL DEFAULT 1")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryId INTEGER, titleFilter TEXT NOT NULL, propertyDefinitionId INTEGER, propertyValue TEXT NOT NULL, metric TEXT NOT NULL, direction TEXT NOT NULL, target INTEGER NOT NULL, period TEXT NOT NULL, repeat TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(propertyDefinitionId) REFERENCES property_definitions(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_categoryId ON goals(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_propertyDefinitionId ON goals(propertyDefinitionId)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE plans ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE plans ADD COLUMN recurrenceUntil INTEGER")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE property_definitions ADD COLUMN multiline INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE property_definitions ADD COLUMN defaultValue TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plans ADD COLUMN allDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE plans SET allDay = 1 WHERE scheduledDate IS NOT NULL AND startTime IS NULL")
                db.execSQL("ALTER TABLE property_definitions ADD COLUMN required INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE property_definitions ADD COLUMN optionColors TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE timer_sessions ADD COLUMN propertiesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_property_values (planId INTEGER NOT NULL, definitionId INTEGER NOT NULL, value TEXT NOT NULL, PRIMARY KEY(planId, definitionId), FOREIGN KEY(planId) REFERENCES plans(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(definitionId) REFERENCES property_definitions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_property_values_definitionId ON plan_property_values(definitionId)")
            }
        }
        /** Every migration the app knows, oldest first: adding a schema version means adding one here. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)

        /** The file name is where installed data lives: never rename it, migrate the schema instead. */
        fun open(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext, AppDatabase::class.java, "chronota.db",
        ).addMigrations(*MIGRATIONS).build()
    }
}
