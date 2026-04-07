package world.respect.kokebfidel.data.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import world.respect.kokebfidel.data.models.Activity

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subject: String,
    val thumbnailUrl: String,
    val color: Int,
    val description: String,
    val gameType: Int,
    val timeLimit: Int? = null,
    val easyActivities: List<Activity>,
    val mediumActivities: List<Activity>,
    val hardActivities: List<Activity>,
    val isActive: Boolean = true,
    val easyTimeLimit: Int? = null,
    val mediumTimeLimit: Int? = null,
    val hardTimeLimit: Int? = null,
    val easyPoints: Int? = null,
    val mediumPoints: Int? = null,
    val hardPoints: Int? = null
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val activityId: String,
    val gameId: String,
    val stars: Int,
    val isCompleted: Boolean = true
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE gameId = :gameId")
    fun getProgressByGame(gameId: String): kotlinx.coroutines.flow.Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    fun getAllProgress(): kotlinx.coroutines.flow.Flow<List<ProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ProgressEntity)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games")
    fun getAllGames(): kotlinx.coroutines.flow.Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<GameEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()
}

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromActivityList(value: List<Activity>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toActivityList(value: String): List<Activity> {
        val listType = object : TypeToken<List<Activity>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
}

@Database(entities = [GameEntity::class, ProgressEntity::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun progressDao(): ProgressDao
}
