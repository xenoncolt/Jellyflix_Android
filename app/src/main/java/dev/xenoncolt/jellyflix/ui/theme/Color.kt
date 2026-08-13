package dev.xenoncolt.jellyflix.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens, copied straight out of MOBILE-HANDOFF section 9.
 *
 * There's no light theme, so these are plain constants instead of anything
 * theme-switched. If we ever add one, this is where it starts.
 */
object Tokens {
    val Ink950 = Color(0xFF0B1224) // page background (darkest)
    val Ink900 = Color(0xFF101A30)
    val Ink850 = Color(0xFF142038) // dialogs, dropdowns, bottom sheets
    val Ink800 = Color(0xFF1A2742) // cards, surfaces
    val Ink700 = Color(0xFF22314F) // inputs, chips
    val Ink600 = Color(0xFF2D3D60)
    val Ink500 = Color(0xFF3D4F75)

    val Mist400 = Color(0xFF8FA0C4) // muted / secondary text
    val Mist300 = Color(0xFFAAB8D6)
    val Mist200 = Color(0xFFCCD6EA)

    val Brand500 = Color(0xFF2F6FE4) // primary action, MOVIE badge
    val Brand400 = Color(0xFF4A86F7) // hover / pressed
    val Brand300 = Color(0xFF7AA6FF) // links

    val Series500 = Color(0xFF7C3AED) // SERIES badge
    val Accent500 = Color(0xFFF5A524) // ratings, warnings, saved state
    val Danger500 = Color(0xFFE5484D)
    val Success500 = Color(0xFF30A46C)

    /** Top-centre radial highlight of the page gradient. */
    val GradientHighlight = Color(0xFF24345C)
    val GradientMid = Color(0xFF16223C)

    val Divider = Color(0x14FFFFFF) // white/8
    val SurfaceBorder = Color(0x1FFFFFFF)
}

/** Per-genre tile colours keyed by TMDB genre id (handoff section 9). */
val GenreColors: Map<Int, Color> = mapOf(
    28 to Color(0xFFC0392B),   // Action
    12 to Color(0xFF8E5CF0),   // Adventure
    16 to Color(0xFF1E88D6),   // Animation
    35 to Color(0xFFE0B427),   // Comedy
    80 to Color(0xFF2F6FE4),   // Crime
    99 to Color(0xFF1AA38A),   // Documentary
    18 to Color(0xFFD0407F),   // Drama
    10751 to Color(0xFF2E9E6B), // Family
    14 to Color(0xFF7C3AED),   // Fantasy
    36 to Color(0xFF9A6B3F),   // History
    27 to Color(0xFF8C2020),   // Horror
    10402 to Color(0xFFC0559E), // Music
    9648 to Color(0xFF4B5FA8), // Mystery
    10749 to Color(0xFFE0557E), // Romance
    878 to Color(0xFF2B8FC4),  // Science Fiction
    10770 to Color(0xFF5A6B8C), // TV Movie
    53 to Color(0xFF37474F),   // Thriller
    10752 to Color(0xFF6B7A3F), // War
    37 to Color(0xFFB07A3C),   // Western
    10759 to Color(0xFFC0392B), // Action & Adventure (tv)
    10762 to Color(0xFF1E88D6), // Kids
    10763 to Color(0xFF455A78), // News
    10764 to Color(0xFFE0B427), // Reality
    10765 to Color(0xFF2B8FC4), // Sci-Fi & Fantasy (tv)
    10766 to Color(0xFFD0407F), // Soap
    10767 to Color(0xFF1AA38A), // Talk
    10768 to Color(0xFF6B7A3F), // War & Politics (tv)
)

fun genreColor(id: Int): Color = GenreColors[id] ?: Tokens.Ink600
