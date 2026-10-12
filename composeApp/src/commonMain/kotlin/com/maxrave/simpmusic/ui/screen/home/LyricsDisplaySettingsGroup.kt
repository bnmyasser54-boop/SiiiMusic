package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.ui.component.SettingGroup
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.component.lyrics.LyricsDisplayPreferences
import com.maxrave.simpmusic.ui.theme.typo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.approximate_line_sync_lyrics
import simpmusic.composeapp.generated.resources.approximate_line_sync_lyrics_description
import simpmusic.composeapp.generated.resources.done
import simpmusic.composeapp.generated.resources.live_lyrics
import simpmusic.composeapp.generated.resources.live_lyrics_alignment
import simpmusic.composeapp.generated.resources.live_lyrics_alignment_center
import simpmusic.composeapp.generated.resources.live_lyrics_alignment_end
import simpmusic.composeapp.generated.resources.live_lyrics_alignment_start
import simpmusic.composeapp.generated.resources.live_lyrics_description
import simpmusic.composeapp.generated.resources.live_lyrics_line_count
import simpmusic.composeapp.generated.resources.live_lyrics_position
import simpmusic.composeapp.generated.resources.live_lyrics_text_size
import simpmusic.composeapp.generated.resources.lyrics_display
import simpmusic.composeapp.generated.resources.lyrics_page_font
import simpmusic.composeapp.generated.resources.lyrics_page_font_monospace
import simpmusic.composeapp.generated.resources.lyrics_page_font_poppins
import simpmusic.composeapp.generated.resources.lyrics_page_font_sans_serif
import simpmusic.composeapp.generated.resources.lyrics_page_font_serif
import simpmusic.composeapp.generated.resources.lyrics_page_line_spacing
import simpmusic.composeapp.generated.resources.lyrics_page_text_size
import simpmusic.composeapp.generated.resources.lyrics_page_tuning_description
import kotlin.math.roundToInt

@Composable
internal fun LyricsDisplaySettingsGroup(
    dataStoreManager: DataStoreManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val liveEnabled by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.LIVE_ENABLED_KEY)
            .map { LyricsDisplayPreferences.parseBoolean(it, LyricsDisplayPreferences.DEFAULT_LIVE_ENABLED) }
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_LIVE_ENABLED)
    val liveTextSize by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.LIVE_TEXT_SIZE_KEY)
            .map {
                LyricsDisplayPreferences.parseInt(
                    it,
                    LyricsDisplayPreferences.DEFAULT_LIVE_TEXT_SIZE_SP,
                    LyricsDisplayPreferences.MIN_LIVE_TEXT_SIZE_SP..LyricsDisplayPreferences.MAX_LIVE_TEXT_SIZE_SP,
                )
            }
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_LIVE_TEXT_SIZE_SP)
    val liveLineCount by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.LIVE_LINE_COUNT_KEY)
            .map {
                LyricsDisplayPreferences.parseInt(
                    it,
                    LyricsDisplayPreferences.DEFAULT_LIVE_LINE_COUNT,
                    LyricsDisplayPreferences.MIN_LIVE_LINE_COUNT..LyricsDisplayPreferences.MAX_LIVE_LINE_COUNT,
                )
            }
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_LIVE_LINE_COUNT)
    val liveVerticalOffset by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.LIVE_VERTICAL_OFFSET_KEY)
            .map {
                LyricsDisplayPreferences.parseInt(
                    it,
                    LyricsDisplayPreferences.DEFAULT_LIVE_VERTICAL_OFFSET_DP,
                    LyricsDisplayPreferences.MIN_LIVE_VERTICAL_OFFSET_DP..LyricsDisplayPreferences.MAX_LIVE_VERTICAL_OFFSET_DP,
                )
            }
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_LIVE_VERTICAL_OFFSET_DP)
    val liveAlignment by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.LIVE_ALIGNMENT_KEY)
            .map(LyricsDisplayPreferences::parseAlignment)
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_LIVE_ALIGNMENT)
    val approximateLineSync by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.APPROXIMATE_LINE_SYNC_KEY)
            .map { LyricsDisplayPreferences.parseBoolean(it, LyricsDisplayPreferences.DEFAULT_APPROXIMATE_LINE_SYNC) }
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_APPROXIMATE_LINE_SYNC)

    val pageFont by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.PAGE_FONT_KEY)
            .map(LyricsDisplayPreferences::parseFont)
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_PAGE_FONT)
    val pageTextSize by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.PAGE_TEXT_SIZE_KEY)
            .map {
                LyricsDisplayPreferences.parseInt(
                    it,
                    LyricsDisplayPreferences.DEFAULT_PAGE_TEXT_SIZE_SP,
                    LyricsDisplayPreferences.MIN_PAGE_TEXT_SIZE_SP..LyricsDisplayPreferences.MAX_PAGE_TEXT_SIZE_SP,
                )
            }
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_PAGE_TEXT_SIZE_SP)
    val pageLineSpacing by remember(dataStoreManager) {
        dataStoreManager.getString(LyricsDisplayPreferences.PAGE_LINE_SPACING_KEY)
            .map {
                LyricsDisplayPreferences.parseInt(
                    it,
                    LyricsDisplayPreferences.DEFAULT_PAGE_LINE_SPACING_DP,
                    LyricsDisplayPreferences.MIN_PAGE_LINE_SPACING_DP..LyricsDisplayPreferences.MAX_PAGE_LINE_SPACING_DP,
                )
            }
    }.collectAsStateWithLifecycle(LyricsDisplayPreferences.DEFAULT_PAGE_LINE_SPACING_DP)

    var showFontPicker by rememberSaveable { mutableStateOf(false) }
    var showAlignmentPicker by rememberSaveable { mutableStateOf(false) }
    fun save(key: String, value: String) {
        scope.launch { dataStoreManager.putString(key, value) }
    }

    val fontLabel =
        when (pageFont) {
            LyricsDisplayPreferences.FONT_SANS_SERIF -> stringResource(Res.string.lyrics_page_font_sans_serif)
            LyricsDisplayPreferences.FONT_SERIF -> stringResource(Res.string.lyrics_page_font_serif)
            LyricsDisplayPreferences.FONT_MONOSPACE -> stringResource(Res.string.lyrics_page_font_monospace)
            else -> stringResource(Res.string.lyrics_page_font_poppins)
        }
    val alignmentLabel =
        when (liveAlignment) {
            LyricsDisplayPreferences.ALIGN_START -> stringResource(Res.string.live_lyrics_alignment_start)
            LyricsDisplayPreferences.ALIGN_END -> stringResource(Res.string.live_lyrics_alignment_end)
            else -> stringResource(Res.string.live_lyrics_alignment_center)
        }

    SettingGroup(
        title = stringResource(Res.string.lyrics_display),
        footer = {
            Text(
                text = stringResource(Res.string.lyrics_page_tuning_description),
                style = typo().bodySmall,
            )
        },
    ) {
        SettingItem(
            title = stringResource(Res.string.lyrics_page_font),
            subtitle = fontLabel,
            onClick = { showFontPicker = true },
        )
        LyricsSliderSetting(
            title = stringResource(Res.string.lyrics_page_text_size),
            subtitle = "",
            value = pageTextSize.toFloat(),
            valueRange = LyricsDisplayPreferences.MIN_PAGE_TEXT_SIZE_SP.toFloat()..LyricsDisplayPreferences.MAX_PAGE_TEXT_SIZE_SP.toFloat(),
            valueLabel = { "${it.roundToInt()} sp" },
            onCommit = { save(LyricsDisplayPreferences.PAGE_TEXT_SIZE_KEY, it.roundToInt().toString()) },
        )
        LyricsSliderSetting(
            title = stringResource(Res.string.lyrics_page_line_spacing),
            subtitle = "",
            value = pageLineSpacing.toFloat(),
            valueRange = LyricsDisplayPreferences.MIN_PAGE_LINE_SPACING_DP.toFloat()..LyricsDisplayPreferences.MAX_PAGE_LINE_SPACING_DP.toFloat(),
            valueLabel = { "${it.roundToInt()} dp" },
            onCommit = { save(LyricsDisplayPreferences.PAGE_LINE_SPACING_KEY, it.roundToInt().toString()) },
        )
        SettingItem(
            title = stringResource(Res.string.live_lyrics),
            subtitle = stringResource(Res.string.live_lyrics_description),
            switch =
                liveEnabled to { enabled ->
                    save(LyricsDisplayPreferences.LIVE_ENABLED_KEY, enabled.toString())
                },
        )
        LyricsSliderSetting(
            title = stringResource(Res.string.live_lyrics_text_size),
            subtitle = "",
            value = liveTextSize.toFloat(),
            valueRange = LyricsDisplayPreferences.MIN_LIVE_TEXT_SIZE_SP.toFloat()..LyricsDisplayPreferences.MAX_LIVE_TEXT_SIZE_SP.toFloat(),
            valueLabel = { "${it.roundToInt()} sp" },
            enabled = liveEnabled,
            onCommit = { save(LyricsDisplayPreferences.LIVE_TEXT_SIZE_KEY, it.roundToInt().toString()) },
        )
        LyricsSliderSetting(
            title = stringResource(Res.string.live_lyrics_line_count),
            subtitle = "",
            value = liveLineCount.toFloat(),
            valueRange = LyricsDisplayPreferences.MIN_LIVE_LINE_COUNT.toFloat()..LyricsDisplayPreferences.MAX_LIVE_LINE_COUNT.toFloat(),
            steps = 1,
            valueLabel = { it.roundToInt().toString() },
            enabled = liveEnabled,
            onCommit = { save(LyricsDisplayPreferences.LIVE_LINE_COUNT_KEY, it.roundToInt().toString()) },
        )
        LyricsSliderSetting(
            title = stringResource(Res.string.live_lyrics_position),
            subtitle = "",
            value = liveVerticalOffset.toFloat(),
            valueRange =
                LyricsDisplayPreferences.MIN_LIVE_VERTICAL_OFFSET_DP.toFloat()..
                    LyricsDisplayPreferences.MAX_LIVE_VERTICAL_OFFSET_DP.toFloat(),
            valueLabel = { "${it.roundToInt()} dp" },
            enabled = liveEnabled,
            onCommit = { save(LyricsDisplayPreferences.LIVE_VERTICAL_OFFSET_KEY, it.roundToInt().toString()) },
        )
        SettingItem(
            title = stringResource(Res.string.live_lyrics_alignment),
            subtitle = alignmentLabel,
            isEnable = liveEnabled,
            onClick = { showAlignmentPicker = true },
        )
        SettingItem(
            title = stringResource(Res.string.approximate_line_sync_lyrics),
            subtitle = stringResource(Res.string.approximate_line_sync_lyrics_description),
            isEnable = liveEnabled,
            switch =
                approximateLineSync to { enabled ->
                    save(LyricsDisplayPreferences.APPROXIMATE_LINE_SYNC_KEY, enabled.toString())
                },
        )
    }

    if (showFontPicker) {
        LyricsChoiceDialog(
            title = stringResource(Res.string.lyrics_page_font),
            selected = pageFont,
            options =
                listOf(
                    LyricsDisplayPreferences.FONT_POPPINS to stringResource(Res.string.lyrics_page_font_poppins),
                    LyricsDisplayPreferences.FONT_SANS_SERIF to stringResource(Res.string.lyrics_page_font_sans_serif),
                    LyricsDisplayPreferences.FONT_SERIF to stringResource(Res.string.lyrics_page_font_serif),
                    LyricsDisplayPreferences.FONT_MONOSPACE to stringResource(Res.string.lyrics_page_font_monospace),
                ),
            onSelected = { selected ->
                save(LyricsDisplayPreferences.PAGE_FONT_KEY, selected)
                showFontPicker = false
            },
            onDismiss = { showFontPicker = false },
        )
    }
    if (showAlignmentPicker) {
        LyricsChoiceDialog(
            title = stringResource(Res.string.live_lyrics_alignment),
            selected = liveAlignment,
            options =
                listOf(
                    LyricsDisplayPreferences.ALIGN_START to stringResource(Res.string.live_lyrics_alignment_start),
                    LyricsDisplayPreferences.ALIGN_CENTER to stringResource(Res.string.live_lyrics_alignment_center),
                    LyricsDisplayPreferences.ALIGN_END to stringResource(Res.string.live_lyrics_alignment_end),
                ),
            onSelected = { selected ->
                save(LyricsDisplayPreferences.LIVE_ALIGNMENT_KEY, selected)
                showAlignmentPicker = false
            },
            onDismiss = { showAlignmentPicker = false },
        )
    }
}

@Composable
private fun LyricsSliderSetting(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    enabled: Boolean = true,
    steps: Int = 0,
    onCommit: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    SettingItem(
        title = title,
        subtitle = subtitle,
        isEnable = enabled,
        otherView = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = valueLabel(sliderValue), style = typo().bodySmall)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onCommit(sliderValue) },
                    valueRange = valueRange,
                    steps = steps,
                    enabled = enabled,
                )
            }
        },
    )
}

@Composable
private fun LyricsChoiceDialog(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(value) }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { onSelected(value) },
                        )
                        Text(text = label, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.done))
            }
        },
    )
}
