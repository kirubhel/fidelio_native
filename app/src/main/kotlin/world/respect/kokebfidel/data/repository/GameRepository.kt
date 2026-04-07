package world.respect.kokebfidel.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import world.respect.kokebfidel.data.database.AppDatabase
import world.respect.kokebfidel.data.database.GameEntity
import world.respect.kokebfidel.data.models.Activity
import world.respect.kokebfidel.data.network.RespectApiService
import world.respect.kokebfidel.data.network.LibRespectCache
import world.respect.kokebfidel.data.network.LibRespectCacheInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class GameRepository(private val context: Context) {
    private val TAG = "GameRepository"
    
    private val db = Room.databaseBuilder(
        context,
        AppDatabase::class.java, "respect-kokebfidel-db"
    )
    .fallbackToDestructiveMigration()
    .build()

    private val libRespectCache = LibRespectCache.build()
    
    private val api = Retrofit.Builder()
        .baseUrl("https://learningcloud.et/api/")
        .client(OkHttpClient.Builder()
            .addInterceptor(LibRespectCacheInterceptor(libRespectCache))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RespectApiService::class.java)

    fun getProgressFlow(): Flow<Map<String, Int>> = 
        db.progressDao().getAllProgress().map { list ->
            list.associateBy({ it.activityId }, { it.stars })
        }

    /**
     * Get games from local database as a reactive Flow and trigger an async refresh.
     */
    fun getGames(): Flow<List<GameEntity>> = 
        db.gameDao().getAllGames().combine(db.progressDao().getAllProgress()) { games, progress ->
            val progressMap = progress.groupBy { it.gameId }
            games.map { game ->
                val gameProgress = progressMap[game.id]?.associate { it.activityId to it.stars } ?: emptyMap()
                game.copy(
                    easyActivities = game.easyActivities.map { it.copy(stars = gameProgress[it.id] ?: 0) },
                    mediumActivities = game.mediumActivities.map { it.copy(stars = gameProgress[it.id] ?: 0) },
                    hardActivities = game.hardActivities.map { it.copy(stars = gameProgress[it.id] ?: 0) }
                )
            }
        }.flowOn(Dispatchers.IO)

    suspend fun syncGameList() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Syncing subject list from Internet...")
                val response = api.getGames()
                if (response.isSuccessful) {
                    val remoteSummaries = response.body()?.data ?: emptyList()
                    Log.d(TAG, "Found ${remoteSummaries.size} remote subjects.")
                    
                    val syncScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
                    
                    remoteSummaries.forEach { summary ->
                        val existing = db.gameDao().getGameById(summary.id)
                        val isDataIncomplete = existing == null || existing.easyActivities.isEmpty()
                        
                        db.gameDao().insertGame(GameEntity(
                            id = summary.id,
                            title = summary.title,
                            subject = summary.course_name,
                            thumbnailUrl = summary.thumbnail_url ?: "assets/images/proud-mascot.png",
                            color = existing?.color ?: 0xFF60A5FA.toInt(),
                            description = summary.description,
                            gameType = summary.game_type,
                            easyActivities = existing?.easyActivities ?: emptyList(),
                            mediumActivities = existing?.mediumActivities ?: emptyList(),
                            hardActivities = existing?.hardActivities ?: emptyList(),
                            isActive = summary.is_active,
                            timeLimit = summary.time_limit
                        ))
                        
                        if (isDataIncomplete) {
                            Log.d(TAG, "Curriculum for ${summary.title} is empty or new. Launching background fetch...")
                            syncScope.launch {
                                try {
                                    fetchGameDetails(summary.id)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Background curriculum fetch failed for ${summary.id}", e)
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Sync failed: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Critical sync error", e) 
            }
            Unit
        }
    }

    suspend fun fetchGameDetails(gameId: String) {
        withContext(Dispatchers.IO) {
            // Check if we already have levels/activities locally
            val existing = db.gameDao().getGameById(gameId)
            if (existing != null && existing.easyActivities.isNotEmpty()) {
                Log.d(TAG, "Using local activities for $gameId")
                // We can still try a silent background update if network is available
            }

            try {
                val response = api.getGameDetails(gameId)
                if (response.isSuccessful) {
                    val detail = response.body()?.data ?: return@withContext
                    val gameData = detail.game_data
                    val levels = gameData["difficultyLevels"] as? Map<*, *>
                    val settings = gameData["difficultySettings"] as? Map<*, *>
                    
                    val easySettings = settings?.get("easy") as? Map<*, *>
                    val mediumSettings = settings?.get("medium") as? Map<*, *>
                    val hardSettings = settings?.get("hard") as? Map<*, *>
                    
                    fun Any?.toInt(): Int? = (this as? Double)?.toInt() ?: (this as? Float)?.toInt() ?: (this as? Int)

                    val entity = GameEntity(
                        id = detail.id,
                        title = detail.title,
                        subject = detail.course_name,
                        thumbnailUrl = detail.thumbnail_url ?: "assets/images/proud-mascot.png",
                        color = existing?.color ?: 0xFF60A5FA.toInt(),
                        description = detail.description,
                        gameType = detail.game_type,
                        easyActivities = flattenActivities(levels?.get("easy")),
                        mediumActivities = flattenActivities(levels?.get("medium")),
                        hardActivities = flattenActivities(levels?.get("hard")),
                        isActive = detail.is_active,
                        timeLimit = detail.time_limit,
                        easyTimeLimit = easySettings?.get("timeLimit").toInt(),
                        mediumTimeLimit = mediumSettings?.get("timeLimit").toInt(),
                        hardTimeLimit = hardSettings?.get("timeLimit").toInt(),
                        easyPoints = easySettings?.get("points").toInt(),
                        mediumPoints = mediumSettings?.get("points").toInt(),
                        hardPoints = hardSettings?.get("points").toInt()
                    )
                    db.gameDao().insertGame(entity)
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Detail sync error (likely offline)", e) 
            }
            Unit
        }
    }

    suspend fun saveProgress(gameId: String, activityId: String, stars: Int) {
        withContext(Dispatchers.IO) {
            db.progressDao().saveProgress(world.respect.kokebfidel.data.database.ProgressEntity(activityId, gameId, stars))
            Unit
        }
    }

    private fun flattenActivities(data: Any?): List<Activity> {
        val levelList = data as? List<*> ?: return emptyList()
        val allActivities = mutableListOf<Activity>()
        levelList.forEach { levelObj ->
            val level = levelObj as? Map<*, *> ?: return@forEach
            val activities = level["activities"] as? List<*> ?: return@forEach
            activities.forEach { activityObj ->
                val item = activityObj as? Map<*, *> ?: return@forEach
                allActivities.add(Activity(
                    id = item["id"] as? String ?: Math.random().toString(),
                    word = item["word"] as? String ?: "",
                    letters = (item["letters"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    imageUrl = item["picture"] as? String ?: ""
                ))
            }
        }
        return allActivities
    }
}
