package world.respect.fidelio.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface RespectApiService {
    @GET("respect/games")
    suspend fun getGames(): Response<RespectListResponse>

    @GET("respect/gamesdata/{id}")
    suspend fun getGameDetails(@Path("id") id: String): Response<GameResponse>
}

data class RespectListResponse(
    val success: Boolean,
    val data: List<GameSummaryResponse>,
    val message: String
)

data class GameSummaryResponse(
    val id: String,
    val title: String,
    val description: String,
    val game_type: Int,
    val course_name: String,
    val grade_name: String,
    val is_active: Boolean,
    val thumbnail_url: String? = null
)

data class GameResponse(
    val success: Boolean,
    val data: GameDetailResponse,
    val message: String
)

data class GameDetailResponse(
    val id: String,
    val title: String,
    val description: String,
    val game_type: Int,
    val course_name: String,
    val grade_name: String,
    val is_active: Boolean,
    val thumbnail_url: String? = null,
    val game_data: Map<String, Any>
)
