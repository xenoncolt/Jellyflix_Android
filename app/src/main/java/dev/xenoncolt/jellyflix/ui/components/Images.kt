package dev.xenoncolt.jellyflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.xenoncolt.jellyflix.ui.theme.Tokens

/**
 * Poster / backdrop / profile images.
 *
 * All of them go through Coil with a fixed crossfade and a flat placeholder
 * rather than a spinner: a poster grid with twenty spinners in it looks broken,
 * and the placeholder doubles as the empty state when TMDB has no artwork.
 */

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Tokens.Ink800,
    showFallbackIcon: Boolean = true,
) {
    Box(
        modifier = modifier.background(placeholderColor),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            if (showFallbackIcon) {
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = Tokens.Ink600,
                    modifier = Modifier.fillMaxSize(0.32f),
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(url)
                    .crossfade(180)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
