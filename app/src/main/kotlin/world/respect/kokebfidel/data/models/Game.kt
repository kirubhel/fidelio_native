package world.respect.kokebfidel.data.models

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
    val gameType: Int = 4,
    val timeLimit: Int? = null,
    val easyTimeLimit: Int? = null,
    val mediumTimeLimit: Int? = null,
    val hardTimeLimit: Int? = null,
    val easyPoints: Int? = null,
    val mediumPoints: Int? = null,
    val hardPoints: Int? = null
)
