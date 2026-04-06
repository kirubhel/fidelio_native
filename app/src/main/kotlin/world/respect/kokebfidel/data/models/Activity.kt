package world.respect.kokebfidel.data.models

data class Activity(
    val id: String,
    val word: String,
    val letters: List<String>,
    val imageUrl: String,
    val stars: Int = 0
)
