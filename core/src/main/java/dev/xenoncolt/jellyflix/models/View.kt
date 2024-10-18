package dev.xenoncolt.jellyflix.models

import java.util.UUID

data class View(
    val id: UUID,
    val name: String,
    var items: List<FindroidItem>? = null,
    val type: CollectionType,
)
