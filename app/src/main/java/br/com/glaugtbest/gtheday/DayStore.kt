package br.com.glaugtbest.gtheday

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "days")
data class DayRecord(
    @PrimaryKey val date: String,
    val water: Boolean = false,
    val creatine: Boolean = false,
    val strength: Boolean = false,
    val cardio: Int? = null,
    val breakfast: String = "",
    val lunch: String = "",
    val snack: String = "",
    val dinner: String = "",
    val weight: Double? = null,
    val waist: Double? = null
) {
    fun meal(habit: Habit): String = when (habit) {
        Habit.BREAKFAST -> breakfast
        Habit.LUNCH -> lunch
        Habit.SNACK -> snack
        Habit.DINNER -> dinner
        else -> error("Hábito sem refeição")
    }

    fun isDone(habit: Habit): Boolean = when (habit) {
        Habit.WATER -> water
        Habit.CREATINE -> creatine
        Habit.STRENGTH -> strength
        Habit.CARDIO -> cardio != null
        else -> meal(habit).isNotBlank()
    }

    fun restoreHabit(habit: Habit, previous: DayRecord): DayRecord = when (habit) {
        Habit.WATER -> copy(water = previous.water)
        Habit.CREATINE -> copy(creatine = previous.creatine)
        Habit.STRENGTH -> copy(strength = previous.strength)
        Habit.CARDIO -> copy(cardio = previous.cardio)
        Habit.BREAKFAST -> copy(breakfast = previous.breakfast)
        Habit.LUNCH -> copy(lunch = previous.lunch)
        Habit.SNACK -> copy(snack = previous.snack)
        Habit.DINNER -> copy(dinner = previous.dinner)
    }

    fun toggle(habit: Habit): DayRecord = when (habit) {
        Habit.WATER -> copy(water = !water)
        Habit.CREATINE -> copy(creatine = !creatine)
        Habit.STRENGTH -> copy(strength = !strength)
        else -> error("Hábito sem marcação")
    }

    fun withMeal(habit: Habit, value: String): DayRecord = when (habit) {
        Habit.BREAKFAST -> copy(breakfast = value)
        Habit.LUNCH -> copy(lunch = value)
        Habit.SNACK -> copy(snack = value)
        Habit.DINNER -> copy(dinner = value)
        else -> error("Hábito sem refeição")
    }
}

@Dao
interface DayDao {
    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun read(date: String): DayRecord?

    @Query("SELECT * FROM days WHERE date = :date")
    fun observe(date: String): Flow<DayRecord?>

    @Query("SELECT * FROM days WHERE date BETWEEN :first AND :last ORDER BY date")
    fun observeWeek(first: String, last: String): Flow<List<DayRecord>>

    @Upsert
    suspend fun write(record: DayRecord)
}

@Database(entities = [DayRecord::class], version = 1, exportSchema = false)
abstract class DayDatabase : RoomDatabase() {
    abstract fun days(): DayDao
}
