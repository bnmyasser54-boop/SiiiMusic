/*
 * Ported from ArchiveTune's "Enhanced" lyrics mode (LyricsEnhanced.kt)
 * © Rukamori — github.com/rukamori — GPL-3.0 License. Notices kept per GPL-3.0 §4/§5.
 *
 * The renderer underneath is MochaRealm's accompanist KaraokeLyricsView — the very library
 * and version pair ArchiveTune pins (com.mocharealm.accompanist:lyrics-ui 1.0.19 +
 * lyrics-core 0.4.7) — so the drawing itself is ArchiveTune's own, not a re-implementation.
 * What this file ports is ArchiveTune's composition around it:
 *   - the frame-locked playback position engine (withFrameNanos + drift correction) that
 *     makes the karaoke sweep and the autoscroll run at the display's real refresh rate
 *     instead of the player's ~50 ms tick, which is what the old styles feel like;
 *   - the LRC lead (300 ms) vs word-sync lead (0 ms);
 *   - the exact type scale (bold 1.0× main, 0.82× accompaniment, 0.55× phonetic);
 *   - the 0.38 × height focus offset and the 72 dp keep-alive zone;
 *   - the gap→line-end heuristics that give the view the interludes its breathing dots live in.
 *
 * Adapted to SimpMusic's data model: lyrics arrive as `Lyrics`/`Line` (line timestamps in
 * strings, rich sync as `<mm:ss.xx>` markers inside `words`), translations are matched by
 * time against `translatedLyrics`, and romanization goes through the app's own romanizer.
 * The typography honors the lyrics-page font settings (font family + size), which is this
 * app's counterpart of ArchiveTune's lyrics font preference.
 */

package com.maxrave.simpmusic.ui.component.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.lyrics.RomanizationDictionaryState
import com.maxrave.domain.data.model.lyrics.RomanizationLanguage
import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.LyricsRomanizerRepository
import com.maxrave.simpmusic.extension.parseRichSyncWords
import com.maxrave.simpmusic.ui.screen.player.content.stripRichSyncTimestamps
import com.maxrave.simpmusic.viewModel.NowPlayingScreenData
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.unavailable
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * ArchiveTune's constants, kept at their original values — the shape of the motion IS them.
 */
/** Line-synced (LRC) sheets lead the song by this much; word-synced sheets need no lead. */
private const val LRC_LEAD_MS = 300L
private const val WORD_SYNC_LEAD_MS = 0L
/** Past this drift between the projected playhead and the player's own tick, snap instead of easing. */
private const val SMOOTH_PLAYBACK_MAX_DRIFT_MS = 250.0
/** Per-frame share of the drift folded back in, and the ceiling on that correction. */
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.08
private const val SMOOTH_PLAYBACK_MAX_CORRECTION_PER_FRAME_MS = 2.0
/** A syllable must last at least this long or the sweep can never catch it. */
private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

/** Same gap rules ArchiveTune applies, in milliseconds. */
private const val LINE_END_MAX_GAP_MS = 3_000L
private const val LINE_END_FALLBACK_DURATION_MS = 4_000L

/** The provider caption rides over the bottom fade, quieter than any lyric. */
private const val ENHANCED_FOOTER_ALPHA = 0.6f

/**
 * One unsynced lyric row: ArchiveTune's plain view, plus this app's romanization and
 * translation as quiet sub-lines so switching to this style never hides what the other
 * styles were showing.
 */
private data class EnhancedPlainLine(
    val itemId: String,
    val text: String,
    val romanized: String?,
    val translation: String?,
)

/**
 * The ArchiveTune "Enhanced" lyrics page: MochaRealm's karaoke renderer driven by a
 * frame-locked playhead, composed exactly the way ArchiveTune composes it.
 *
 * Reads the lyrics-page typography preferences itself (font family + size), the way
 * ArchiveTune's own enhanced mode reads its lyrics font preference. Line spacing is the
 * renderer's own fixed rhythm in this mode — ArchiveTune's is too, and the spacing slider
 * would fight the spring placement between lines.
 *
 * @param onLineClick same contract as [com.maxrave.simpmusic.ui.component.LyricsView] —
 *   the tapped line's position as a 0..100 progress percent.
 */
@Composable
internal fun ArchiveTuneEnhancedLyricsView(
    lyricsData: NowPlayingScreenData.LyricsData,
    timeLine: StateFlow<TimeLine>,
    onLineClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    footerContent: (@Composable () -> Unit)? = null,
    dataStoreManager: DataStoreManager = koinInject(),
    romanizer: LyricsRomanizerRepository = koinInject(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    // --- The app's lyrics-page typography, merged in the way the other page styles merge it.
    val storedPageFont by dataStoreManager
        .getString(LyricsDisplayPreferences.PAGE_FONT_KEY)
        .collectAsStateWithLifecycle(null)
    val storedPageTextSize by dataStoreManager
        .getString(LyricsDisplayPreferences.PAGE_TEXT_SIZE_KEY)
        .collectAsStateWithLifecycle(null)
    val lyricsFont = lyricsFontFamily(LyricsDisplayPreferences.parseFont(storedPageFont))
    val lyricsTextSize =
        LyricsDisplayPreferences.parseInt(
            storedPageTextSize,
            LyricsDisplayPreferences.DEFAULT_PAGE_TEXT_SIZE_SP,
            LyricsDisplayPreferences.MIN_PAGE_TEXT_SIZE_SP..LyricsDisplayPreferences.MAX_PAGE_TEXT_SIZE_SP,
        ).toFloat()

    // The listener's audio-delay correction, applied at read time like every other style:
    // never written into the (cached, shared) lyric rows themselves.
    val lyricsOffsetMs by dataStoreManager.lyricsOffsetMs.collectAsStateWithLifecycle(0)

    val textColor = textColorOverride ?: Color.White

    // --- ArchiveTune's exact type scale, on the app's chosen family and size.
    val normalTextStyle =
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = lyricsTextSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = lyricsFont,
        )
    val accompanimentTextStyle =
        MaterialTheme.typography.titleLarge.copy(
            fontSize = (lyricsTextSize * 0.82f).sp,
            fontFamily = lyricsFont,
        )
    val phoneticTextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = (lyricsTextSize * 0.55f).sp,
            fontWeight = FontWeight.Normal,
        )

    // --- Romanization, the same pipeline the other styles run.
    val romanizationStored by dataStoreManager.romanizationLanguages.collectAsStateWithLifecycle("")
    val romanizationLanguages =
        remember(romanizationStored) { RomanizationLanguage.parse(romanizationStored) }
    val japaneseDictionaryState by romanizer.japaneseDictionaryState.collectAsStateWithLifecycle()
    val japaneseDictionaryReady by remember {
        derivedStateOf { japaneseDictionaryState == RomanizationDictionaryState.READY }
    }
    // A Japanese selection needs the dictionary pack; fetch it from here if a backup
    // restored the preference without the pack (same as LyricsView does).
    LaunchedEffect(romanizationLanguages, lyricsData.lyrics) {
        if (RomanizationLanguage.JAPANESE in romanizationLanguages) romanizer.ensureJapaneseDictionary()
    }

    val rawLines = lyricsData.lyrics.lines.orEmpty()
    val syncType = lyricsData.lyrics.syncType
    val isRichSynced = syncType == "RICH_SYNCED"
    val isLineSynced = syncType == "LINE_SYNCED"
    val isSynced = isRichSynced || isLineSynced

    val translationByLineIndex =
        remember(rawLines, lyricsData.translatedLyrics?.first?.lines) {
            buildTranslationByLineIndex(
                originalLines = rawLines,
                translatedLines = lyricsData.translatedLyrics?.first?.lines.orEmpty(),
                thresholdMs = 1000L,
            )
        }
    // Romanized text per line, recomputed the moment the Japanese dictionary lands — a line
    // read before that came back null and a plain `remember` would keep the null on screen.
    val romanizedByLineIndex =
        if (romanizationLanguages.isEmpty()) {
            emptyMap<Int, String?>()
        } else {
            remember(rawLines, romanizationLanguages, japaneseDictionaryReady, isRichSynced) {
                rawLines.associateByIndexedLine { line ->
                    val source = if (isRichSynced) line.words.stripRichSyncTimestamps() else line.words
                    romanizer.romanize(source, romanizationLanguages)
                }
            }
        }

    val lyricsSessionKey =
        remember(lyricsData.lyrics.lines, lyricsData.lyrics.syncType) {
            lyricsData.lyrics.lines.orEmpty() to lyricsData.lyrics.syncType
        }

    val syncedLyrics =
        remember(
            rawLines,
            isRichSynced,
            isLineSynced,
            translationByLineIndex,
            romanizedByLineIndex,
        ) {
            SyncedLyrics(
                lines =
                    buildEnhancedSyncedLines(
                        rawLines = rawLines,
                        isRichSynced = isRichSynced,
                        translations = translationByLineIndex,
                        romanized = romanizedByLineIndex,
                    ),
            )
        }

    // An unsynced sheet's translations match by POSITION (every row is stamped "0", so a
    // time match would answer only the first line — the trap LyricsView's unsynced branch
    // already avoids). Synced sheets match by time.
    val plainTranslationByIndex =
        remember(rawLines, lyricsData.translatedLyrics?.first?.lines) {
            val translated = lyricsData.translatedLyrics?.first?.lines.orEmpty()
            if (translated.isEmpty()) {
                emptyMap<Int, String?>()
            } else {
                rawLines.indices.associateWith { index -> translated.getOrNull(index)?.words }
            }
        }

    val plainLines =
        remember(rawLines, translationByLineIndex, plainTranslationByIndex, romanizedByLineIndex) {
            rawLines.mapIndexedNotNull { index, line ->
                val text = line.words.trim()
                if (text.isBlank()) {
                    null
                } else {
                    EnhancedPlainLine(
                        itemId = "enhanced-plain:$index",
                        text = text,
                        romanized = romanizedByLineIndex[index],
                        translation = translationByLineIndex[index] ?: plainTranslationByIndex[index],
                    )
                }
            }
        }

    // Word-synced lines start their sweep exactly on time; LRC lines lead by 300 ms so the
    // page arrives at the line as it is sung, not after it has begun.
    val latestLeadMs = rememberUpdatedState(if (isRichSynced) WORD_SYNC_LEAD_MS else LRC_LEAD_MS)
    val latestLyricsSyncOffset = rememberUpdatedState(lyricsOffsetMs)
    val latestPlaybackSpeed by dataStoreManager.playbackSpeed.collectAsStateWithLifecycle(1f)
    val latestSpeed = rememberUpdatedState(latestPlaybackSpeed)
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val latestIsPlaying = rememberUpdatedState(controllerState.isPlaying)
    val latestOnLineClick = rememberUpdatedState(onLineClick)
    // Read through a lambda, not captured: the track length is seeded asynchronously, and
    // a click's seek must use the total the moment it is clicked.
    val totalMsProvider = { timeLine.value.total }

    // The playhead. The player publishes its position on a ~50 ms tick; drawing straight
    // off that tick is what makes the other styles read like 30 fps. This engine, ported
    // verbatim from ArchiveTune, advances the position EVERY FRAME from the frame clock and
    // folds the tick back in as a small bounded correction, so the karaoke sweep and the
    // autoscroll run at the display's real refresh rate and never jar when a tick lands.
    val playbackPositionMs = remember { mutableLongStateOf(timeLine.value.current.coerceAtLeast(0L)) }
    val listState = key(lyricsSessionKey) { rememberLazyListState() }

    LaunchedEffect(timeLine, lyricsSessionKey, latestPlaybackSpeed) {
        var smoothedPositionMs = timeLine.value.current.coerceAtLeast(0L).toDouble()
        var previousFrameNanos = 0L
        while (isActive) {
            val rawPosition = timeLine.value.current.coerceAtLeast(0L)
            if (!latestIsPlaying.value) {
                // Paused: hold exactly on the last published position, no projection.
                smoothedPositionMs = rawPosition.toDouble()
                previousFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                delay(100L)
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                if (previousFrameNanos == 0L) {
                    previousFrameNanos = frameNanos
                    smoothedPositionMs = rawPosition.toDouble()
                } else {
                    val elapsedMs =
                        ((frameNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000.0) *
                            latestSpeed.value.toDouble()
                    val projectedPositionMs = smoothedPositionMs + elapsedMs
                    val driftMs = rawPosition.toDouble() - projectedPositionMs

                    smoothedPositionMs =
                        if (abs(driftMs) > SMOOTH_PLAYBACK_MAX_DRIFT_MS) {
                            // Seek, stall, or a track change: trust the player outright.
                            rawPosition.toDouble()
                        } else {
                            val correctionMs =
                                (driftMs * SMOOTH_PLAYBACK_DRIFT_CORRECTION).coerceIn(
                                    -SMOOTH_PLAYBACK_MAX_CORRECTION_PER_FRAME_MS,
                                    SMOOTH_PLAYBACK_MAX_CORRECTION_PER_FRAME_MS,
                                )
                            projectedPositionMs + correctionMs
                        }
                    previousFrameNanos = frameNanos
                }

                val nextPosition = smoothedPositionMs.roundToLong().coerceAtLeast(0L)
                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
            }
        }
    }

    // What the view reads every frame: the smoothed position, then the listener's delay
    // correction and the format's lead, all at read time.
    val playbackSyncPosition: () -> Int =
        remember {
            {
                (
                    playbackPositionMs.longValue +
                        latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value
                ).coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }

    val showTranslations = translationByLineIndex.isNotEmpty()
    val showPhonetics = romanizedByLineIndex.isNotEmpty()

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp),
    ) {
        when {
            !isSynced -> {
                if (plainLines.isEmpty()) {
                    EnhancedEmptyHint(textColor)
                } else {
                    EnhancedPlainLyricsView(
                        lines = plainLines,
                        listState = listState,
                        textColor = textColor,
                        textStyle = normalTextStyle,
                        phoneticTextStyle = phoneticTextStyle,
                    )
                }
            }

            syncedLyrics.lines.isEmpty() -> {
                EnhancedEmptyHint(textColor)
            }

            else -> {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ArchiveTune parks the sung line 38% down the viewport, not dead center —
                    // more of the upcoming page stays in view.
                    val lyricsViewportOffset =
                        remember(maxHeight) { maxHeight * 0.38f }

                    key(lyricsSessionKey, syncedLyrics) {
                        KaraokeLyricsView(
                            listState = listState,
                            lyrics = syncedLyrics,
                            currentPosition = playbackSyncPosition,
                            onLineClicked = { line ->
                                val totalMs = totalMsProvider()
                                if (line.start > 0 && totalMs > 0L) {
                                    latestOnLineClick.value(line.start * 100f / totalMs.toFloat())
                                }
                            },
                            // The other styles have no long-press action on a line; this one
                            // keeps that contract instead of growing a selection mode.
                            onLinePressed = { },
                            textColor = textColor,
                            normalLineTextStyle = normalTextStyle,
                            accompanimentLineTextStyle = accompanimentTextStyle,
                            phoneticTextStyle = phoneticTextStyle,
                            // ArchiveTune renders with SrcOver; the glow-less, exact look.
                            blendMode = BlendMode.SrcOver,
                            useBlurEffect = true,
                            showTranslation = showTranslations,
                            showPhonetic = showPhonetics,
                            offset = lyricsViewportOffset,
                            keepAliveZone = 72.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // The provider caption can't ride inside the renderer's own list (it owns its items),
        // so it floats over the bottom fade — quieter than any lyric, the way the other
        // styles dim it.
        if (footerContent != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .alpha(ENHANCED_FOOTER_ALPHA)
                        .padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
            ) {
                footerContent()
            }
        }
    }
}

@Composable
private fun EnhancedEmptyHint(textColor: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EnhancedPlainLyricsView(
    lines: List<EnhancedPlainLine>,
    listState: LazyListState,
    textColor: Color,
    textStyle: TextStyle,
    phoneticTextStyle: TextStyle,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 12.dp,
                top = 120.dp,
                end = 12.dp,
                bottom = 96.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = lines,
            key = { line -> line.itemId },
            contentType = { "enhanced_plain_lyric_line" },
        ) { line ->
            EnhancedPlainLyricLineItem(
                line = line,
                textColor = textColor,
                textStyle = textStyle,
                phoneticTextStyle = phoneticTextStyle,
            )
        }
    }
}

@Composable
private fun EnhancedPlainLyricLineItem(
    line: EnhancedPlainLine,
    textColor: Color,
    textStyle: TextStyle,
    phoneticTextStyle: TextStyle,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                // An unsynced sheet has nothing to seek to; the row is inert like the
                // other styles' unsynced rows.
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = line.text,
            style = textStyle,
            color = textColor,
        )
        // Original first, then how to say it, then what it means — the same order the
        // other styles stack their sub-lines in.
        line.romanized?.let { romanized ->
            Text(
                text = romanized,
                style = phoneticTextStyle,
                color = textColor.copy(alpha = 0.7f),
            )
        }
        line.translation?.let { translation ->
            Text(
                text = translation,
                style = phoneticTextStyle,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}

/** `associateBy` over mapIndexed with a value transform — spelled out once here. */
private inline fun <T, R> List<T>.associateByIndexedLine(valueOf: (T) -> R): Map<Int, R> {
    val result = mutableMapOf<Int, R>()
    forEachIndexed { index, item -> result[index] = valueOf(item) }
    return result
}

/**
 * Matched the same way the Classic/Apple styles match synced translations: each timed
 * original line takes the translated line whose start time is nearest, and only when that
 * is within [thresholdMs] — a translation sheet whose timestamps disagree with the
 * original's by more than that is worse than no translation. The earlier of two equally
 * near translated lines wins, matching the original helper's tie-break.
 */
private fun buildTranslationByLineIndex(
    originalLines: List<Line>,
    translatedLines: List<Line>,
    thresholdMs: Long,
): Map<Int, String> {
    if (translatedLines.isEmpty()) return emptyMap()
    val sortedTranslated =
        translatedLines
            .mapIndexedNotNull { index, line ->
                line.startTimeMs.toLongOrNull()?.let { Pair(it, line.words) }
            }.sortedBy { it.first }
    if (sortedTranslated.isEmpty()) return emptyMap()

    val result = mutableMapOf<Int, String>()
    var j = 0
    originalLines.forEachIndexed { index, original ->
        val ts = original.startTimeMs.toLongOrNull() ?: return@forEachIndexed
        while (j < sortedTranslated.size && sortedTranslated[j].first < ts - thresholdMs) j++
        var bestWords: String? = null
        var bestDiff = Long.MAX_VALUE
        if (j < sortedTranslated.size) {
            val candidate = sortedTranslated[j]
            bestWords = candidate.second
            bestDiff = abs(candidate.first - ts)
        }
        if (j + 1 < sortedTranslated.size) {
            val candidate = sortedTranslated[j + 1]
            val diff = abs(candidate.first - ts)
            if (diff < bestDiff) {
                bestWords = candidate.second
                bestDiff = diff
            }
        }
        if (bestWords != null && bestDiff <= thresholdMs) {
            result[index] = bestWords
        }
    }
    return result
}

/**
 * ArchiveTune's line building, over this app's [Line] rows. Rich-synced rows become
 * karaoke lines with one syllable per word; line-synced rows become plain synced lines,
 * or wrapping karaoke lines when the line has a romanized reading.
 */
private fun buildEnhancedSyncedLines(
    rawLines: List<Line>,
    isRichSynced: Boolean,
    translations: Map<Int, String>,
    romanized: Map<Int, String?>,
): List<ISyncedLine> {
    val lines = mutableListOf<ISyncedLine>()

    rawLines.forEachIndexed { index, line ->
        val startMs = line.startTimeMs.toLongOrNull() ?: return@forEachIndexed
        if (startMs < 0L) return@forEachIndexed
        if (line.words.isBlank()) return@forEachIndexed

        val translation = translations[index]
        val romanizedText = romanized[index]?.trim()?.takeIf { it.isNotEmpty() }
        val lineStart = startMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        if (isRichSynced) {
            val parsed =
                parseRichSyncWords(line.words, line.startTimeMs, line.endTimeMs)
            if (parsed == null) {
                // An unparseable rich-sync row falls back to the line-synced branch
                // rather than vanishing — the other styles fall back the same way.
                appendLineSynced(lines, rawLines, index, line, lineStart, translation, romanizedText)
                return@forEachIndexed
            }
            val phoneticWords =
                romanizedText
                    ?.split(Regex("\\s+"))
                    ?.filter(String::isNotEmpty)
                    .orEmpty()
            val wordTimings = parsed.words
            val syllables =
                wordTimings.mapIndexed { wordIndex, word ->
                    val syllableStart = word.startTimeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    val rawEnd =
                        wordTimings.getOrNull(wordIndex + 1)?.startTimeMs
                            ?: parsed.lineEndTimeMs.takeIf { it != Long.MAX_VALUE && it > word.startTimeMs }
                            ?: word.startTimeMs + LINE_END_FALLBACK_DURATION_MS
                    val syllableEnd =
                        rawEnd.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                            .coerceAtLeast(syllableStart + MIN_KARAOKE_SYLLABLE_DURATION_MS)
                    KaraokeSyllable(
                        content = word.text,
                        start = syllableStart,
                        end = syllableEnd,
                        phonetic = phoneticWords.getOrNull(wordIndex),
                    )
                }
            if (syllables.isEmpty()) return@forEachIndexed
            val lineEnd = maxOf(syllables.last().end, lineStart + 1)
            lines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = syllables,
                    translation = translation,
                    alignment = KaraokeAlignment.Start,
                    start = lineStart,
                    end = lineEnd,
                ),
            )
        } else {
            appendLineSynced(lines, rawLines, index, line, lineStart, translation, romanizedText)
        }
    }

    return lines
}

/**
 * The line-synced append: the stored end time when it is usable, else ArchiveTune's gap
 * rules (a short gap runs to the next line; a long one caps the line at 4 s so the
 * renderer's breathing dots own the silence). A romanized reading turns the line into a
 * wrapping karaoke line so the phonetics ride the sweep.
 */
private fun appendLineSynced(
    lines: MutableList<ISyncedLine>,
    rawLines: List<Line>,
    index: Int,
    line: Line,
    lineStart: Int,
    translation: String?,
    romanizedText: String?,
) {
    val startMs = line.startTimeMs.toLongOrNull() ?: lineStart.toLong()
    val storedEnd = line.endTimeMs.toLongOrNull()?.takeIf { it > startMs }
    val nextStart = rawLines.getOrNull(index + 1)?.startTimeMs?.toLongOrNull()?.takeIf { it > startMs }
    val lineEnd =
        (
            storedEnd
                ?: if (nextStart != null) {
                    val gap = nextStart - startMs
                    if (gap > LINE_END_MAX_GAP_MS) {
                        minOf(nextStart - 1L, startMs + LINE_END_FALLBACK_DURATION_MS)
                    } else {
                        nextStart - 1L
                    }
                } else {
                    startMs + LINE_END_FALLBACK_DURATION_MS
                }
        ).coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
            .coerceAtLeast(lineStart + 1)

    if (romanizedText == null) {
        lines.add(
            SyncedLine(
                content = line.words,
                translation = translation,
                start = lineStart,
                end = lineEnd,
            ),
        )
    } else {
        lines.add(
            KaraokeLine.MainKaraokeLine(
                syllables =
                    buildWrappingKaraokeSyllables(
                        content = line.words,
                        romanizedText = romanizedText,
                        start = lineStart,
                        end = lineEnd,
                    ),
                translation = translation,
                alignment = KaraokeAlignment.Start,
                start = lineStart,
                end = lineEnd,
            ),
        )
    }
}

/**
 * ArchiveTune's romanization wrapper: the line splits into grapheme-aware wrapping units
 * (whole words, or single characters for CJK), and the romanized words distribute across
 * the units that contain letters, so each unit lights up with its reading attached.
 */
private fun buildWrappingKaraokeSyllables(
    content: String,
    romanizedText: String,
    start: Int,
    end: Int,
): List<KaraokeSyllable> {
    val contentUnits = content.toLyricsWrappingUnits().ifEmpty { listOf(content) }
    val phoneticWords = romanizedText.split(Regex("\\s+")).filter(String::isNotEmpty)
    val phoneticAnchorIndices =
        contentUnits.indices.filter { index -> contentUnits[index].any(Char::isLetterOrDigit) }
    val phoneticsByUnit = MutableList<String?>(contentUnits.size) { null }

    if (phoneticAnchorIndices.isNotEmpty() && phoneticWords.isNotEmpty()) {
        phoneticWords.forEachIndexed { wordIndex, word ->
            val anchorIndex = wordIndex * phoneticAnchorIndices.size / phoneticWords.size
            val unitIndex = phoneticAnchorIndices[anchorIndex]
            phoneticsByUnit[unitIndex] = listOfNotNull(phoneticsByUnit[unitIndex], word).joinToString(" ")
        }
    }

    val duration = (end - start).coerceAtLeast(contentUnits.size)
    return contentUnits.mapIndexed { index, unit ->
        val unitStart = start + (duration * index / contentUnits.size)
        val unitEnd = start + (duration * (index + 1) / contentUnits.size)
        KaraokeSyllable(
            content = unit,
            start = unitStart,
            end = unitEnd.coerceAtLeast(unitStart + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phoneticsByUnit[index],
        )
    }
}

/**
 * ArchiveTune's wrapping-unit splitter: words stay whole, CJK characters stand alone, and
 * grapheme clusters (not raw chars) are the unit, so combined scripts never split apart.
 */
private fun String.toLyricsWrappingUnits(): List<String> {
    if (isEmpty()) return emptyList()

    val units = mutableListOf<String>()
    val currentWord = StringBuilder()
    val characterIterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    characterIterator.setText(this)

    fun flushCurrentWord() {
        if (currentWord.isNotEmpty()) {
            units += currentWord.toString()
            currentWord.clear()
        }
    }

    var start = characterIterator.first()
    var end = characterIterator.next()
    while (end != BreakIterator.DONE) {
        val grapheme = substring(start, end)
        val codePoint = grapheme.codePointAt(0)
        when {
            grapheme.all(Char::isWhitespace) -> {
                currentWord.append(grapheme)
                flushCurrentWord()
            }

            codePoint.isCjkCodePoint() -> {
                flushCurrentWord()
                units += grapheme
            }

            else -> {
                currentWord.append(grapheme)
            }
        }
        start = end
        end = characterIterator.next()
    }
    flushCurrentWord()

    return units
}

private fun Int.isCjkCodePoint(): Boolean =
    when (Character.UnicodeScript.of(this)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true

        else -> false
    }
