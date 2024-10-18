package dev.xenoncolt.jellyflix.utils

import dev.xenoncolt.jellyflix.models.FindroidItem
import dev.xenoncolt.jellyflix.models.FindroidSource
import dev.xenoncolt.jellyflix.models.UiText

interface Downloader {
    suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int = 0,
    ): Pair<Long, UiText?>

    suspend fun cancelDownload(item: FindroidItem, source: FindroidSource)

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource)

    suspend fun getProgress(downloadId: Long?): Pair<Int, Int>
}
