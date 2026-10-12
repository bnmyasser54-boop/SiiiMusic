package com.maxrave.simpmusic.ui.component.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.poppins_bold
import simpmusic.composeapp.generated.resources.poppins_medium
import simpmusic.composeapp.generated.resources.poppins_regular
import org.jetbrains.compose.resources.Font as ComposeFont

/** Preference keys and defaults shared by Settings, the player, and the full lyrics page. */
internal object LyricsDisplayPreferences {
    const val PAGE_FONT_KEY = "lyrics_page_font"
    const val PAGE_TEXT_SIZE_KEY = "lyrics_page_text_size_sp"
    const val PAGE_LINE_SPACING_KEY = "lyrics_page_line_spacing_dp"

    /**
     * The third lyrics style: ArchiveTune's "Enhanced" mode. Defined here rather than next to
     * [com.maxrave.domain.manager.DataStoreManager.LYRICS_STYLE_CLASSIC] on purpose — the core
     * submodule is upstream's repo, and this style is an app-level feature the core never needs
     * to know about: the value is stored and read as a plain string all the way through.
     */
    const val LYRICS_STYLE_ARCHIVE_TUNE = "ARCHIVE_TUNE"

    const val LIVE_ENABLED_KEY = "player_live_lyrics_enabled"
    const val LIVE_TEXT_SIZE_KEY = "player_live_lyrics_text_size_sp"
    const val LIVE_LINE_COUNT_KEY = "player_live_lyrics_line_count"
    const val LIVE_VERTICAL_OFFSET_KEY = "player_live_lyrics_vertical_offset_dp"
    const val LIVE_ALIGNMENT_KEY = "player_live_lyrics_alignment"
    const val APPROXIMATE_LINE_SYNC_KEY = "player_live_lyrics_approximate_word_sync"

    const val FONT_POPPINS = "POPPINS"
    const val FONT_SANS_SERIF = "SANS_SERIF"
    const val FONT_SERIF = "SERIF"
    const val FONT_MONOSPACE = "MONOSPACE"

    const val ALIGN_START = "START"
    const val ALIGN_CENTER = "CENTER"
    const val ALIGN_END = "END"

    const val DEFAULT_PAGE_FONT = FONT_POPPINS
    const val DEFAULT_PAGE_TEXT_SIZE_SP = 26
    const val DEFAULT_PAGE_LINE_SPACING_DP = 16

    const val DEFAULT_LIVE_ENABLED = true
    const val DEFAULT_LIVE_TEXT_SIZE_SP = 20
    const val DEFAULT_LIVE_LINE_COUNT = 2
    const val DEFAULT_LIVE_VERTICAL_OFFSET_DP = 0
    const val DEFAULT_LIVE_ALIGNMENT = ALIGN_CENTER
    const val DEFAULT_APPROXIMATE_LINE_SYNC = true

    const val MIN_PAGE_TEXT_SIZE_SP = 14
    const val MAX_PAGE_TEXT_SIZE_SP = 44
    const val MIN_PAGE_LINE_SPACING_DP = 0
    const val MAX_PAGE_LINE_SPACING_DP = 40
    const val MIN_LIVE_TEXT_SIZE_SP = 12
    const val MAX_LIVE_TEXT_SIZE_SP = 36
    const val MIN_LIVE_LINE_COUNT = 1
    const val MAX_LIVE_LINE_COUNT = 3
    const val MIN_LIVE_VERTICAL_OFFSET_DP = -30
    const val MAX_LIVE_VERTICAL_OFFSET_DP = 30

    fun parseBoolean(raw: String?, default: Boolean): Boolean = raw?.toBooleanStrictOrNull() ?: default

    fun parseInt(
        raw: String?,
        default: Int,
        range: IntRange,
    ): Int = raw?.toIntOrNull()?.coerceIn(range) ?: default

    fun parseFont(raw: String?): String =
        when (raw) {
            FONT_SANS_SERIF -> FONT_SANS_SERIF
            FONT_SERIF -> FONT_SERIF
            FONT_MONOSPACE -> FONT_MONOSPACE
            else -> DEFAULT_PAGE_FONT
        }

    fun parseAlignment(raw: String?): String =
        when (raw) {
            ALIGN_START -> ALIGN_START
            ALIGN_END -> ALIGN_END
            else -> ALIGN_CENTER
        }
}

/** Font choices for the large lyrics page. Poppins matches the app's bundled display font. */
@Composable
internal fun lyricsFontFamily(font: String): FontFamily =
    when (LyricsDisplayPreferences.parseFont(font)) {
        LyricsDisplayPreferences.FONT_SANS_SERIF -> FontFamily.SansSerif
        LyricsDisplayPreferences.FONT_SERIF -> FontFamily.Serif
        LyricsDisplayPreferences.FONT_MONOSPACE -> FontFamily.Monospace
        else ->
            FontFamily(
                ComposeFont(Res.font.poppins_regular, FontWeight.Normal, FontStyle.Normal),
                ComposeFont(Res.font.poppins_medium, FontWeight.Medium, FontStyle.Normal),
                ComposeFont(Res.font.poppins_bold, FontWeight.Bold, FontStyle.Normal),
            )
    }
