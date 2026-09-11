package app.chronota.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TimeConverters {
    @TypeConverter fun instant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun instant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun date(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun date(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun time(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    @TypeConverter fun time(value: LocalTime?): Int? = value?.toSecondOfDay()
}
