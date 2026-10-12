@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.extension.parseRichSyncWords
import com.maxrave.simpmusic.ui.component.rememberLyricLayoutDirection
import com.maxrave.simpmusic.ui.component.lyrics.LyricsDisplayPreferences
import com.maxrave.simpmusic.ui.theme.typo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.done
import simpmusic.composeapp.generated.resources.live_lyrics_quick_line_count
import simpmusic.composeapp.generated.resources.live_lyrics_quick_position
import simpmusic.composeapp.generated.resources.live_lyrics_quick_settings
import simpmusic.composeapp.generated.resources.live_lyrics_quick_text_size
import simpmusic.composeapp.generated.resources.show_live_lyrics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.roundToInt

/**
 * Compact lyric preview placed directly above the player seek bar. Rich-sync timing is used as-is;
 * ordinary line-synced lyrics get a lightweight estimated word clock weighted by word length.
 */
@Composable
internal fun LiveLyricsStrip(
    state: NowPlayingContentState,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    dataStoreManager: DataStoreManager = koinInject(),
) {
    val lyrics = state.screenData.lyricsData?.lyrics
    val lines = lyrics?.lines.orEmpty()
    val syncType = lyrics?.syncType
    val hasTimedLyrics = !lines.isEmpty() && (syncType == "LINE_SYNCED" || syncType == "RICH_SYNCED")

    var showQuickSettings by rememberSaveable { mutableStateOf(false) }
    var quickEnabled by remember(state.liveLyricsEnabled) { mutableStateOf(state.liveLyricsEnabled) }
    var quickTextSize by remember(state.liveLyricsTextSizeSp) { mutableFloatStateOf(state.liveLyricsTextSizeSp.toFloat()) }
    var quickLineCount by remember(state.liveLyricsLineCount) { mutableFloatStateOf(state.liveLyricsLineCount.toFloat()) }
    var quickVerticalOffset by remember(state.liveLyricsVerticalOffsetDp) { mutableFloatStateOf(state.liveLyricsVerticalOffsetDp.toFloat()) }
    val scope = rememberCoroutineScope()

    // While the quick dialog is open, its slider positions are also the preview values behind it.
    val displayEnabled = if (showQuickSettings) quickEnabled else state.liveLyricsEnabled
    val displayTextSize = if (showQuickSettings) quickTextSize.roundToInt() else state.liveLyricsTextSizeSp
    val displayLineCount = if (showQuickSettings) quickLineCount.roundToInt() else state.liveLyricsLineCount
    val displayVerticalOffset = if (showQuickSettings) quickVerticalOffset.roundToInt() else state.liveLyricsVerticalOffsetDp
    val rawPosition = state.timelineState.current - state.lyricsOffsetMs
    val playhead = rememberLiveLyricsPlayhead(rawPosition, state.controllerState.isPlaying && state.currentLyricLineIndex >= 0)
    val firstLineIndex = state.currentLyricLineIndex.coerceAtLeast(0)
    val alignment =
        when (state.liveLyricsAlignment) {
            LyricsDisplayPreferences.ALIGN_START -> TextAlign.Start
            LyricsDisplayPreferences.ALIGN_END -> TextAlign.End
            else -> TextAlign.Center
        }

    if (hasTimedLyrics && displayEnabled) {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .offset(y = displayVerticalOffset.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenLyrics,
                        onLongClick = { showQuickSettings = true },
                        onLongClickLabel = "Live lyrics settings",
                    ).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            repeat(displayLineCount.coerceIn(LyricsDisplayPreferences.MIN_LIVE_LINE_COUNT, LyricsDisplayPreferences.MAX_LIVE_LINE_COUNT)) { slot ->
                val lineIndex = firstLineIndex + slot
                AnimatedContent(
                    targetState = lineIndex,
                    transitionSpec = {
                        val enter =
                            fadeIn(tween(LIVE_LINE_CHANGE_MS, easing = FastOutSlowInEasing)) +
                                slideInVertically(tween(LIVE_LINE_CHANGE_MS, easing = FastOutSlowInEasing)) { it / 4 }
                        val exit =
                            fadeOut(tween(LIVE_LINE_CHANGE_MS, easing = FastOutSlowInEasing)) +
                                slideOutVertically(tween(LIVE_LINE_CHANGE_MS, easing = FastOutSlowInEasing)) { -it / 4 }
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    label = "liveLyricsLine$slot",
                ) { shownIndex ->
                    val line = lines.getOrNull(shownIndex)
                    if (line == null) {
                        Spacer(Modifier.height(0.dp))
                    } else {
                        val isCurrent = shownIndex == state.currentLyricLineIndex
                        val timedWords =
                            remember(line, shownIndex, lines, syncType, isCurrent, state.liveLyricsApproximateLineSync, state.timelineState.total) {
                                if (!isCurrent) {
                                    emptyList()
                                } else {
                                    liveWordTimings(
                                        line = line,
                                        lineIndex = shownIndex,
                                        lines = lines,
                                        syncType = syncType.orEmpty(),
                                        trackDurationMs = state.timelineState.total,
                                        approximateLineSync = state.liveLyricsApproximateLineSync,
                                    )
                                }
                            }
                        val displayText =
                            if (isCurrent && timedWords.isNotEmpty()) {
                                timedWords.joinToString(" ") { it.text }
                            } else {
                                line.words.stripRichSyncTimestamps()
                            }
                        if (displayText.isNotBlank()) {
                            val lineDirection = rememberLyricLayoutDirection(displayText)
                            if (isCurrent && timedWords.isNotEmpty()) {
                                SweptLiveLyricLine(
                                    words = timedWords,
                                    playhead = playhead,
                                    fontSizeSp = displayTextSize,
                                    alignment = alignment,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
                                    Text(
                                        text = displayText,
                                        style = liveLyricStyle(displayTextSize).copy(textDirection = TextDirection.ContentOrLtr),
                                        color = if (isCurrent) Color.White else Color.White.copy(alpha = UPCOMING_LINE_ALPHA),
                                        textAlign = alignment,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .basicMarquee(
                                                    iterations = Int.MAX_VALUE,
                                                    animationMode = MarqueeAnimationMode.Immediately,
                                                ),
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.height(0.dp))
                        }
                    }
                }
            }
        }
    }

    if (showQuickSettings) {
        AlertDialog(
            onDismissRequest = { showQuickSettings = false },
            title = { Text(stringResource(Res.string.live_lyrics_quick_settings)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.show_live_lyrics),
                            modifier = Modifier.weight(1f),
                            style = typo().bodyMedium,
                        )
                        Switch(
                            checked = quickEnabled,
                            onCheckedChange = { enabled ->
                                quickEnabled = enabled
                                scope.launch {
                                    dataStoreManager.putString(LyricsDisplayPreferences.LIVE_ENABLED_KEY, enabled.toString())
                                }
                            },
                        )
                    }
                    LiveLyricsQuickSlider(
                        title = stringResource(Res.string.live_lyrics_quick_text_size),
                        value = quickTextSize,
                        valueRange = LyricsDisplayPreferences.MIN_LIVE_TEXT_SIZE_SP.toFloat()..LyricsDisplayPreferences.MAX_LIVE_TEXT_SIZE_SP.toFloat(),
                        label = { "${it.roundToInt()} sp" },
                        onValueChange = { value ->
                            quickTextSize = value
                            scope.launch {
                                dataStoreManager.putString(LyricsDisplayPreferences.LIVE_TEXT_SIZE_KEY, value.roundToInt().toString())
                            }
                        },
                    )
                    LiveLyricsQuickSlider(
                        title = stringResource(Res.string.live_lyrics_quick_line_count),
                        value = quickLineCount,
                        valueRange = LyricsDisplayPreferences.MIN_LIVE_LINE_COUNT.toFloat()..LyricsDisplayPreferences.MAX_LIVE_LINE_COUNT.toFloat(),
                        steps = 1,
                        label = { it.roundToInt().toString() },
                        onValueChange = { value ->
                            quickLineCount = value
                            scope.launch {
                                dataStoreManager.putString(LyricsDisplayPreferences.LIVE_LINE_COUNT_KEY, value.roundToInt().toString())
                            }
                        },
                    )
                    LiveLyricsQuickSlider(
                        title = stringResource(Res.string.live_lyrics_quick_position),
                        value = quickVerticalOffset,
                        valueRange =
                            LyricsDisplayPreferences.MIN_LIVE_VERTICAL_OFFSET_DP.toFloat()..
                                LyricsDisplayPreferences.MAX_LIVE_VERTICAL_OFFSET_DP.toFloat(),
                        label = { "${it.roundToInt()} dp" },
                        onValueChange = { value ->
                            quickVerticalOffset = value
                            scope.launch {
                                dataStoreManager.putString(LyricsDisplayPreferences.LIVE_VERTICAL_OFFSET_KEY, value.roundToInt().toString())
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickSettings = false }) {
                    Text(stringResource(Res.string.done))
                }
            },
        )
    }
}

internal data class LiveLyricWordTiming(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

/** Distributes one line's interval across tokens in proportion to their character length. */
internal fun distributeLineSyncWords(
    text: String,
    startTimeMs: Long,
    endTimeMs: Long,
): List<LiveLyricWordTiming> {
    val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty() || endTimeMs <= startTimeMs) return emptyList()
    val totalWeight = words.sumOf { it.length.coerceAtLeast(1) }.toLong().coerceAtLeast(1L)
    val duration = endTimeMs - startTimeMs
    var consumedWeight = 0L
    return words.mapIndexed { index, word ->
        val wordStart = startTimeMs + duration * consumedWeight / totalWeight
        consumedWeight += word.length.coerceAtLeast(1).toLong()
        val wordEnd =
            if (index == words.lastIndex) {
                endTimeMs
            } else {
                (startTimeMs + duration * consumedWeight / totalWeight).coerceAtLeast(wordStart + 1L)
            }
        LiveLyricWordTiming(word, wordStart, wordEnd)
    }
}

private fun liveWordTimings(
    line: Line,
    lineIndex: Int,
    lines: List<Line>,
    syncType: String,
    trackDurationMs: Long,
    approximateLineSync: Boolean,
): List<LiveLyricWordTiming> {
    val lineStart = line.startTimeMs.toLongOrNull() ?: return emptyList()
    val lineEnd =
        line.endTimeMs.toLongOrNull()?.takeIf { it > lineStart }
            ?: lines.getOrNull(lineIndex + 1)?.startTimeMs?.toLongOrNull()?.takeIf { it > lineStart }
            ?: trackDurationMs.takeIf { it > lineStart }
            ?: (lineStart + FALLBACK_LINE_DURATION_MS)

    if (syncType == "LINE_SYNCED") {
        if (!approximateLineSync) return emptyList()
        return distributeLineSyncWords(line.words.stripRichSyncTimestamps(), lineStart, lineEnd)
    }
    if (syncType != "RICH_SYNCED") return emptyList()

    val parsed = parseRichSyncWords(line.words, line.startTimeMs, line.endTimeMs) ?: return emptyList()
    return parsed.words.mapIndexed { index, word ->
        val end =
            parsed.words.getOrNull(index + 1)?.startTimeMs
                ?: lineEnd.takeIf { it > word.startTimeMs }
                ?: (word.startTimeMs + LAST_WORD_DURATION_FALLBACK_MS)
        LiveLyricWordTiming(word.text, word.startTimeMs, end.coerceAtLeast(word.startTimeMs + 1L))
    }
}

@Composable
private fun LiveLyricsQuickSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    label: (Float) -> String,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = typo().bodySmall, modifier = Modifier.weight(1f))
            Text(text = label(value), style = typo().bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun SweptLiveLyricLine(
    words: List<LiveLyricWordTiming>,
    playhead: State<Long>,
    fontSizeSp: Int,
    alignment: TextAlign,
    modifier: Modifier = Modifier,
) {
    val text = remember(words) { words.joinToString(" ") { it.text } }
    val ranges = remember(words) {
        var start = 0
        words.map { word ->
            val range = start until start + word.text.length
            start += word.text.length + 1
            range
        }
    }
    val lineDirection = rememberLyricLayoutDirection(text)
    var layout by remember(text, fontSizeSp, alignment) { mutableStateOf<TextLayoutResult?>(null) }
    val style = liveLyricStyle(fontSizeSp).copy(textDirection = TextDirection.ContentOrLtr)

    CompositionLocalProvider(LocalLayoutDirection provides lineDirection) {
        Box(
            modifier =
                modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                ),
        ) {
            Text(
                text = text,
                style = style.copy(color = UNSUNG_LYRIC_COLOR),
                textAlign = alignment,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth(),
                onTextLayout = { layout = it },
            )
            Text(
                text = text,
                style = style.copy(color = Color.White),
                textAlign = alignment,
                maxLines = 1,
                softWrap = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            val measured = layout ?: return@drawWithContent
                            val sweep = liveLyricsSweepX(words, ranges, measured, playhead.value) ?: return@drawWithContent
                            if (measured.getParagraphDirection(0) == ResolvedTextDirection.Rtl) {
                                clipRect(left = sweep) { this@drawWithContent.drawContent() }
                            } else {
                                clipRect(right = sweep) { this@drawWithContent.drawContent() }
                            }
                        },
            )
        }
    }
}

private fun liveLyricsSweepX(
    words: List<LiveLyricWordTiming>,
    ranges: List<IntRange>,
    layout: TextLayoutResult,
    positionMs: Long,
): Float? {
    val currentIndex = words.indexOfLast { it.startTimeMs <= positionMs }
    if (currentIndex < 0) return null
    val word = words[currentIndex]
    val range = ranges[currentIndex]
    val progress =
        ((positionMs - word.startTimeMs).toFloat() / (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L))
            .coerceIn(0f, 1f)
    val textLength = layout.layoutInput.text.length.coerceAtLeast(1)
    val left = layout.getHorizontalPosition(range.first.coerceIn(0, textLength), usePrimaryDirection = true)
    val right = layout.getHorizontalPosition((range.last + 1).coerceIn(0, textLength), usePrimaryDirection = true)
    return left + (right - left) * progress
}

@Composable
private fun liveLyricStyle(fontSizeSp: Int) =
    typo().bodyLarge.copy(
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.SemiBold,
    )

@Composable
private fun rememberLiveLyricsPlayhead(
    rawMs: Long,
    running: Boolean,
): State<Long> {
    val playhead = remember { mutableLongStateOf(rawMs) }
    val latestRaw by rememberUpdatedState(rawMs)
    LaunchedEffect(running) {
        if (!running) {
            snapshotFlow { latestRaw }.collect { playhead.longValue = it }
            return@LaunchedEffect
        }
        var reported = latestRaw
        var reportedAt = -1L
        var lastFrame = -1L
        var position = playhead.longValue.toDouble()
        while (true) {
            withFrameNanos { now ->
                if (reportedAt < 0L || latestRaw != reported) {
                    reported = latestRaw
                    reportedAt = now
                }
                val target = reported + ((now - reportedAt) / 1_000_000.0).coerceAtMost(MAX_PLAYHEAD_EXTRAPOLATION_MS)
                val step = if (lastFrame < 0L) 0.0 else (now - lastFrame) / 1_000_000.0
                lastFrame = now
                position =
                    if (abs(target - position) > PLAYHEAD_SNAP_MS) {
                        target
                    } else {
                        val advanced = position + step
                        max(position, advanced + (target - advanced) * (step / PLAYHEAD_CATCH_UP_MS).coerceAtMost(1.0))
                    }
                playhead.longValue = position.roundToLong()
            }
        }
    }
    return playhead
}

private const val LIVE_LINE_CHANGE_MS = 260
private const val UPCOMING_LINE_ALPHA = 0.55f
private const val UNSUNG_LYRIC_COLOR_ALPHA = 0.42f
private val UNSUNG_LYRIC_COLOR = Color.White.copy(alpha = UNSUNG_LYRIC_COLOR_ALPHA)
private const val FALLBACK_LINE_DURATION_MS = 3_500L
private const val LAST_WORD_DURATION_FALLBACK_MS = 600L
private const val PLAYHEAD_CATCH_UP_MS = 150.0
private const val PLAYHEAD_SNAP_MS = 500.0
private const val MAX_PLAYHEAD_EXTRAPOLATION_MS = 250.0
