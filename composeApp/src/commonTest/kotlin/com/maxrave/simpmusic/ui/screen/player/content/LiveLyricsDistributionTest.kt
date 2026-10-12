package com.maxrave.simpmusic.ui.screen.player.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveLyricsDistributionTest {
    @Test
    fun lineSyncedWordsReceiveTimeInProportionToTheirLength() {
        val words = distributeLineSyncWords("I love music", startTimeMs = 1_000L, endTimeMs = 2_000L)

        assertEquals(
            listOf(
                LiveLyricWordTiming("I", 1_000L, 1_100L),
                LiveLyricWordTiming("love", 1_100L, 1_500L),
                LiveLyricWordTiming("music", 1_500L, 2_000L),
            ),
            words,
        )
    }

    @Test
    fun blankLyricsOrInvalidIntervalsHaveNoWordTimings() {
        assertTrue(distributeLineSyncWords("   ", startTimeMs = 0L, endTimeMs = 1_000L).isEmpty())
        assertTrue(distributeLineSyncWords("hello world", startTimeMs = 500L, endTimeMs = 500L).isEmpty())
        assertTrue(distributeLineSyncWords("hello world", startTimeMs = 900L, endTimeMs = 500L).isEmpty())
    }
}
