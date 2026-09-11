package app.chronotation.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import app.chronotation.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM plan_property_values") fun planValues(): Flow<List<PlanPropertyValue>>
    @Query("SELECT * FROM plan_property_values WHERE planId = :id") suspend fun valuesForPlan(id: Long): List<PlanPropertyValue>
    @Query("DELETE FROM plan_property_values WHERE planId = :id") suspend fun clearPlanValues(id: Long)
    @Upsert suspend fun putPlanValue(value: PlanPropertyValue)
    @Query("UPDATE categories SET sortOrder = :position, parentId = :parent WHERE id = :id") suspend fun placeCategory(id: Long, position: Int, parent: Long?)
    @Query("SELECT * FROM property_definitions ORDER BY sortOrder, id") fun definitions(): Flow<List<PropertyDefinition>>
    @Query("SELECT * FROM property_values") fun values(): Flow<List<PropertyValue>>
    @Query("SELECT * FROM property_definitions") suspend fun allDefinitions(): List<PropertyDefinition>
    @Query("SELECT * FROM property_values WHERE recordId = :id") suspend fun recordValues(id: Long): List<PropertyValue>
    @Query("SELECT (SELECT count(*) FROM property_values WHERE definitionId = :id) + (SELECT count(*) FROM plan_property_values WHERE definitionId = :id)") suspend fun definitionUsage(id: Long): Int
    @Query("SELECT * FROM plan_property_values") suspend fun allPlanValues(): List<PlanPropertyValue>
    @Query("DELETE FROM property_values WHERE definitionId = :id AND value IN (:values)") suspend fun deleteDefinitionValues(id: Long, values: List<String>)
    @Query("DELETE FROM plan_property_values WHERE definitionId = :id AND value IN (:values)") suspend fun deleteDefinitionPlanValues(id: Long, values: List<String>)
    @Query("DELETE FROM property_values WHERE definitionId = :id") suspend fun dropDefinitionValues(id: Long)
    @Query("DELETE FROM plan_property_values WHERE definitionId = :id") suspend fun dropDefinitionPlanValues(id: Long)
    @Upsert suspend fun putDefinition(value: PropertyDefinition): Long
    @Upsert suspend fun putValue(value: PropertyValue)
    @Query("DELETE FROM property_values WHERE recordId = :id") suspend fun clearValues(id: Long)
    @Query("DELETE FROM property_definitions WHERE id = :id") suspend fun deleteDefinition(id: Long)
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM plans ORDER BY scheduledDate, startTime, id") fun plans(): Flow<List<Plan>>
    @Query("SELECT * FROM records ORDER BY startTime DESC") fun records(): Flow<List<Record>>
    @Query("SELECT * FROM reminders") fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM goals ORDER BY sortOrder, id") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: Long): Goal?
    @Upsert suspend fun putGoal(value: Goal): Long
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: Long)
    @Query("SELECT * FROM property_values") suspend fun allValues(): List<PropertyValue>
    @Query("SELECT * FROM timer_sessions WHERE id = 1") fun timer(): Flow<TimerSession?>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM plans") suspend fun allPlans(): List<Plan>
    @Query("SELECT * FROM records") suspend fun allRecords(): List<Record>
    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<Reminder>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: Long): Category?
    @Query("SELECT * FROM plans WHERE id = :id") suspend fun plan(id: Long): Plan?
    @Query("SELECT * FROM records WHERE id = :id") suspend fun record(id: Long): Record?
    @Query("SELECT * FROM timer_sessions WHERE id = 1") suspend fun currentTimer(): TimerSession?
    @Query("SELECT * FROM timer_slices ORDER BY start") suspend fun slices(): List<TimerSlice>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: Long): Reminder?
    @Upsert suspend fun putCategory(value: Category): Long
    @Upsert suspend fun putPlan(value: Plan): Long
    @Upsert suspend fun putRecord(value: Record): Long
    @Upsert suspend fun putTimer(value: TimerSession)
    @Upsert suspend fun putReminder(value: Reminder): Long
    @Insert suspend fun putSlice(value: TimerSlice)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long)
    @Query("DELETE FROM plans WHERE id = :id") suspend fun deletePlan(id: Long)
    @Query("DELETE FROM records WHERE id = :id") suspend fun deleteRecord(id: Long)
    @Query("DELETE FROM timer_sessions") suspend fun clearTimer()
    @Query("DELETE FROM timer_slices") suspend fun clearSlices()
    @Query("DELETE FROM reminders WHERE planId = :id") suspend fun clearReminders(id: Long)

    // Restoring a backup replaces everything, so each table needs emptying. Children first, parents
    // last, so no delete ever trips a foreign key.
    @Query("DELETE FROM plan_property_values") suspend fun clearPlanValueRows()
    @Query("DELETE FROM property_values") suspend fun clearValueRows()
    @Query("DELETE FROM reminders") suspend fun clearReminderRows()
    @Query("DELETE FROM goals") suspend fun clearGoalRows()
    @Query("DELETE FROM records") suspend fun clearRecordRows()
    @Query("DELETE FROM plans") suspend fun clearPlanRows()
    @Query("DELETE FROM property_definitions") suspend fun clearDefinitionRows()
    // A category points at another category, so children have to go first: SQLite checks the key as
    // each row is removed, not at the end of the statement. The app keeps categories one level deep.
    @Query("DELETE FROM categories WHERE parentId IS NOT NULL") suspend fun clearChildCategories()
    @Query("DELETE FROM categories WHERE parentId IS NULL") suspend fun clearRootCategories()
    @Query("SELECT (SELECT count(*) FROM categories WHERE parentId = :id) + (SELECT count(*) FROM plans WHERE categoryId = :id) + (SELECT count(*) FROM records WHERE categoryId = :id) + (SELECT count(*) FROM timer_sessions WHERE categoryId = :id) + (SELECT count(*) FROM property_definitions WHERE categoryId = :id)")
    suspend fun categoryUsage(id: Long): Int
}
