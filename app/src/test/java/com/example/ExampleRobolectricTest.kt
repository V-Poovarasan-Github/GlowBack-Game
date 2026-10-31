package com.example

import android.content.Context
import com.example.challenge.ChallengeItem
import com.example.challenge.ChallengeStorage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun audioSynthesizer_precomputesClassicAnd16PentatonicPitchesAndEnvelopes() {
        val synth = GlowAudioSynthesizer()
        try {
            // Verify classic 2x2 pitches
            val expectedFreqs = mapOf(
                GlowColor.GREEN to 261.63,
                GlowColor.RED to 329.63,
                GlowColor.YELLOW to 392.00,
                GlowColor.BLUE to 523.25
            )

            for (color in GlowColor.entries) {
                val pcm = synth.padPcmBuffers[color]
                assertNotNull("PCM buffer for $color must be pre-computed", pcm)
                requireNotNull(pcm)
                assertTrue("PCM buffer must not be empty", pcm.size > 4000)
                assertEquals("Start of fade-in must be 0", 0, pcm.first().toInt())
                assertTrue("End of fade-out must be near 0", abs(pcm.last().toInt()) < 500)
                assertEquals(expectedFreqs[color]!!, color.frequencyHz, 0.01)
            }

            // Verify all 16 4x4 PadSpecs have unique glyphs, unique pitches, and pre-computed PCM buffers
            assertEquals(16, GRID_16_PADS.size)
            assertEquals(16, GRID_16_PADS.map { it.glyph }.distinct().size)
            assertEquals(16, GRID_16_PADS.map { it.frequencyHz }.distinct().size)
            for (pad in GRID_16_PADS) {
                assertNotNull("PCM buffer for ${pad.glyph} must exist", synth.frequencyPcmBuffers[pad.frequencyHz])
            }

            assertTrue(synth.errorBuzzPcm.size > 8000)
            assertTrue(synth.clearArpeggioPcm.size > 7000)
        } finally {
            synth.release()
        }
    }

    @Test
    fun pass2A_settingsSheet_gridSizes_and_pacePills() {
        composeTestRule.waitForIdle()

        // Verify compact IDLE summary pills exist
        composeTestRule.onNodeWithTag("config_pill_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("config_pill_pace").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()

        // Open Settings via Grid pill
        composeTestRule.onNodeWithTag("config_pill_grid").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_sheet_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pace_mode_classic").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pace_mode_progressive").assertIsDisplayed()
        composeTestRule.onNodeWithTag("volume_slider").assertIsDisplayed()

        // Select 4x4 grid size
        composeTestRule.onNodeWithTag("grid_size_4x4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        // Verify all 16 pads (pad_0 .. pad_15) are rendered on the 4x4 grid
        for (i in 0 until 16) {
            composeTestRule.onNodeWithTag("pad_$i").assertIsDisplayed()
        }

        // Open settings, select 3x3 grid size and verify balanced 9 pads
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_3x3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()
        for (i in 0 until 9) {
            composeTestRule.onNodeWithTag("pad_$i").assertIsDisplayed()
        }

        // Open Settings again, verify shape toggle and sound chips
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shape_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sound_on_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sound_muted_chip").assertIsDisplayed()

        // Switch back to 2x2 via settings
        composeTestRule.onNodeWithTag("grid_size_2x2").performClick()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pad_green").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pad_red").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pad_yellow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pad_blue").assertIsDisplayed()
    }

    @Test
    fun gameplay_inputLockedInWatching_honestFailure_watchMyRound_and_lifecycleReplay() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.waitForIdle()

        // Ensure 2x2 mode
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)

        // Verify input is strictly locked during WATCHING
        composeTestRule.onNodeWithTag("pad_green").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("pad_red").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("pad_yellow").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("pad_blue").assertIsNotEnabled()

        // Simulate Lifecycle ON_STOP mid-WATCHING, then ON_RESUME
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.mainClock.advanceTimeBy(100)

        // Verify banner displays lifecycle fairness note
        composeTestRule.onNodeWithTag("status_subtitle_text")
            .assertTextContains("Round replayed - you were away.", substring = true)

        // Advance past WATCHING into PLAYING
        composeTestRule.mainClock.advanceTimeBy(1300)
        composeTestRule.onNodeWithTag("pad_green").assertIsEnabled()

        composeTestRule.onNodeWithTag("pad_green").performClick()
        composeTestRule.mainClock.advanceTimeBy(200)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_BEST_SCORE, 0) == 1) {
            composeTestRule.onNodeWithTag("status_header_text")
                .assertTextContains("Round 1 cleared!", substring = true)
            composeTestRule.mainClock.advanceTimeBy(2500)
            for (tag in listOf("pad_red", "pad_yellow", "pad_blue", "pad_green")) {
                composeTestRule.onNodeWithTag(tag).performClick()
                composeTestRule.mainClock.advanceTimeBy(100)
            }
        }

        composeTestRule.mainClock.advanceTimeBy(200)

        composeTestRule.onNodeWithTag("status_header_text")
            .assertTextContains("Game Over!", substring = true)
        composeTestRule.onNodeWithTag("status_subtitle_text")
            .assertTextContains("Missed step", substring = true)
        composeTestRule.onNodeWithTag("status_subtitle_text")
            .assertTextContains("expected", substring = true)

        composeTestRule.onNodeWithTag("watch_my_round_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("watch_my_round_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(400)

        composeTestRule.onNodeWithTag("status_header_text")
            .assertTextContains("Game Over!", substring = true)
        composeTestRule.onNodeWithTag("status_subtitle_text")
            .assertTextContains("Replaying Round", substring = true)
        composeTestRule.onNodeWithTag("pad_green").assertIsNotEnabled()
    }

    @Test
    fun pass2B_modeSelector_and_winsSummary() {
        composeTestRule.waitForIdle()

        // Verify mode selector chips
        composeTestRule.onNodeWithTag("mode_solo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_2p").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_3p").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_4p").assertIsDisplayed()

        // Switch to 2P mode
        composeTestRule.onNodeWithTag("mode_2p").performClick()
        composeTestRule.waitForIdle()

        // Wins summary bar should now be displayed for multiplayer
        composeTestRule.onNodeWithTag("wins_summary_bar").assertIsDisplayed()

        // Open settings, verify reset wins button is present and click it
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_wins_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reset_wins_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        // Switch back to SOLO
        composeTestRule.onNodeWithTag("mode_solo").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun pass2B_multiplayer_handoff_and_playerRail_flow() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.waitForIdle()

        // Select 2P mode
        composeTestRule.onNodeWithTag("mode_2p").performClick()
        composeTestRule.waitForIdle()

        // Start 2P game
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(50)

        // Handoff card and ready button must be displayed for P1
        composeTestRule.onNodeWithTag("handoff_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("handoff_ready_button").assertIsDisplayed()

        // Tap pulsing ready button to begin P1's turn
        composeTestRule.onNodeWithTag("handoff_ready_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(50)

        // Player rail must be displayed showing P1 and P2
        composeTestRule.onNodeWithTag("player_rail_p1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_rail_p2").assertIsDisplayed()
    }

    @Test
    fun pass2C_async_challenge_flow() {
        composeTestRule.waitForIdle()

        // Verify enter challenge pill is displayed and click it
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").performClick()
        composeTestRule.waitForIdle()

        // Verify dialog is displayed
        composeTestRule.onNodeWithTag("enter_challenge_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput("2x2-12-8X2A")
        composeTestRule.onNodeWithTag("challenge_rival_input").performTextInput("Alex")
        composeTestRule.waitForIdle()

        // Submit challenge
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Verify in-game challenge banner is displayed
        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
    }
}

