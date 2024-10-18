package dev.xenoncolt.jellyflix.models

data class FavoriteSection(
    val id: Int,
    val name: UiText,
    var items: List<FindroidItem>,
)
