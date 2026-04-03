package world.respect.fidelio.data.utils

import world.respect.fidelio.data.database.GameEntity
import world.respect.fidelio.data.models.Game

fun GameEntity.toDomain(): Game {
    return Game(
        id = id,
        title = title,
        subject = subject,
        thumbnailUrl = thumbnailUrl,
        color = color,
        description = description,
        gameType = gameType,
        easyActivities = easyActivities,
        mediumActivities = mediumActivities,
        hardActivities = hardActivities,
        isActive = isActive
    )
}
