package world.respect.fidelio.data.models

data class Game(
    val id: String,
    val title: String,
    val subject: String,
    val thumbnailUrl: String,
    val color: Int, // ARGB Color
    val easyActivities: List<Activity>,
    val mediumActivities: List<Activity>,
    val hardActivities: List<Activity>,
    val isActive: Boolean = true,
    val description: String = "",
    val gameType: Int = 4
)
