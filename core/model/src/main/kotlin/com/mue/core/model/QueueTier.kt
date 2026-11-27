package com.mue.core.model

sealed interface QueueTier {
    data class Context(val items: List<Track>, val origin: String) : QueueTier
    data class User(val items: List<Track>) : QueueTier
    data class Autoplay(val items: List<Track>) : QueueTier
}

data class TieredQueue(
    val context: QueueTier.Context = QueueTier.Context(emptyList(), "Queue"),
    val user: QueueTier.User = QueueTier.User(emptyList()),
    val autoplay: QueueTier.Autoplay = QueueTier.Autoplay(emptyList()),
    val currentTier: Tier = Tier.CONTEXT,
    val currentIndex: Int = 0
) {
    enum class Tier { CONTEXT, USER, AUTOPLAY }

    fun toFlatList(): List<Track> = context.items + user.items + autoplay.items
}
