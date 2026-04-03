package world.respect.fidelio.data.models

data class Activity(
    val id: String,
    val word: String,
    val letters: List<String>,
    val imageUrl: String,
    val stars: Int = 0
)
