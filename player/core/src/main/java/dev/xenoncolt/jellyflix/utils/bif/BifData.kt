package dev.xenoncolt.jellyflix.utils.bif

import android.graphics.Bitmap

data class BifData(
    val version: Int,
    val timestampMultiplier: Int,
    val imageCount: Int,
    val images: Map<Int, Bitmap>,
    val imageWidth: Int,
)
