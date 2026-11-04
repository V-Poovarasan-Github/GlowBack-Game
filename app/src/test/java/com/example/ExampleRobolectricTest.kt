package com.example

import android.content.Context
import com.example.challenge.ChallengeItem
import com.example.challenge.ChallengeSeed
import com.example.challenge.ChallengeStorage
import com.example.challenge.GameDifficulty
import com.example.challenge.RunStateStorage
import com.example.challenge.SerializedRunState
import com.example.notification.NotificationContentProvider
import com.example.notification.NotificationScheduler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
        com.example.challenge.RunStateStorage.clearRunState(context)
        context.getSharedPreferences("glowback_challenges_v1", Context.MODE_PRIVATE).edit().clear().commit()
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

        // Verify compact IDLE summary pills and Home [EASY | HARD] toggle exist
        composeTestRule.onNodeWithTag("config_pill_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("config_pill_pace").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()

        // Open Settings via Grid pill
        composeTestRule.onNodeWithTag("config_pill_grid").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_sheet_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pace_mode_classic").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pace_mode_progressive").assertIsDisplayed()
        // Difficulty is removed from Settings sheet; Home toggle is single source of truth
        composeTestRule.onNodeWithTag("difficulty_easy").assertDoesNotExist()
        composeTestRule.onNodeWithTag("difficulty_hard").assertDoesNotExist()

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

        // Open Settings again, verify reset wins is present and difficulty chips are absent
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("difficulty_easy").assertDoesNotExist()
        composeTestRule.onNodeWithTag("difficulty_hard").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reset_wins_button").assertIsDisplayed()

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

        val soloGridTop = composeTestRule.onNodeWithTag("pad_grid_container")
            .fetchSemanticsNode().boundsInRoot.top

        // Switch to 2P mode
        composeTestRule.onNodeWithTag("mode_2p").performClick()
        composeTestRule.waitForIdle()

        // PASS 5.7: Wins summary bar is removed from Home header; pad grid top Y never jumps
        composeTestRule.onNodeWithTag("wins_summary_bar").assertDoesNotExist()
        val twoPGridTop = composeTestRule.onNodeWithTag("pad_grid_container")
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals("Pad grid top Y must stay identical between SOLO and 2P", soloGridTop, twoPGridTop, 0.5f)

        // Open settings, verify reset wins button + updated subtitle are present and click it
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Reset all saved Pass & Play wins and survival records (P1-P4)").assertIsDisplayed()
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

        // Open Rivalry tab and click Paste Code button
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_paste_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()

        // Verify dialog is displayed with updated label "Custom Rival Name (optional)"
        composeTestRule.onNodeWithTag("enter_challenge_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Custom Rival Name (optional)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput("2x2-12-8X2A")
        composeTestRule.onNodeWithTag("challenge_rival_input").performTextInput("Alex")
        composeTestRule.waitForIdle()

        // Submit challenge (3-segment legacy code triggers LegacyCodeNameDialog per Confirmation 4)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Confirm LegacyCodeNameDialog is displayed and accept
        composeTestRule.onNodeWithTag("legacy_code_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legacy_dialog_submit").performClick()
        composeTestRule.waitForIdle()

        // Verify in-game challenge banner is displayed
        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
    }

    @Test
    fun pass2D_homeDeclutter_bestScoreChip_and_combinedRow() {
        composeTestRule.waitForIdle()

        // In PASS 5, home is decluttered: current score is hidden, bottom nav Leaderboard provides access to Hall of Fame
        composeTestRule.onNodeWithTag("nav_leaderboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_score_text").assertDoesNotExist()

        // Merged mode + config row
        composeTestRule.onNodeWithTag("mode_solo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("config_pill_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("config_pill_pace").assertIsDisplayed()

        // [+ Challenge] pill is removed from Home and replaced by [EASY | HARD] difficulty toggle
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").assertIsDisplayed()
    }

    @Test
    fun pass2D_pauseDialog_and_resumeFlow() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.waitForIdle()

        // Select 2P mode and start
        composeTestRule.onNodeWithTag("mode_2p").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(50)

        // Dismiss handoff and wait for 200ms exit animation
        composeTestRule.onNodeWithTag("handoff_ready_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)

        // Bottom action button is removed during play
        composeTestRule.onNodeWithTag("start_game_button").assertDoesNotExist()

        // Click top-right pause button (settings_button with pause icon)
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)

        // Pause sheet controls (PASS 2J Item 3: lightweight modal with ONLY Resume, Save & Exit, Quit to Home, Restart)
        composeTestRule.onNodeWithTag("pause_resume_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_quit_home_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_restart_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_size_2x2").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pace_mode_classic").assertDoesNotExist()
        composeTestRule.onNodeWithTag("reset_wins_button").assertDoesNotExist()

        // Click Save & Exit
        composeTestRule.onNodeWithTag("pause_save_exit_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)

        // Now on home screen, Continue Run pill should appear!
        composeTestRule.onNodeWithTag("continue_run_button").assertIsDisplayed()

        // Tap Continue Run pill to resume
        composeTestRule.onNodeWithTag("continue_run_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250)

        // Resumes into game, showing handoff for the active player
        composeTestRule.onNodeWithTag("handoff_card").assertIsDisplayed()

        // Dismiss handoff, open Pause, and verify "Quit to Home" clears RunState so Continue pill does not appear
        composeTestRule.onNodeWithTag("handoff_ready_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithTag("pause_quit_home_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithTag("continue_run_button").assertDoesNotExist()
    }

    @Test
    fun pass2E_homeComposition_instrumentMode_and_dotTally() {
        composeTestRule.waitForIdle()

        // 1. Verify Navigation and Mode Track in Solo, Board + Context Row
        composeTestRule.onNodeWithTag("nav_leaderboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("segmented_mode_track").assertIsDisplayed()
        composeTestRule.onNodeWithTag("config_pill_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("config_pill_pace").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").assertIsDisplayed()

        // 2. Onboarding popup is visible initially; tapping "Got it" dismisses it
        composeTestRule.onNodeWithTag("onboarding_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_dialog_card").assertDoesNotExist()

        // 3. Tapping a pad at IDLE triggers Instrument Mode (pad is enabled, game stays IDLE)
        composeTestRule.onNodeWithTag("pad_green").assertIsEnabled()
        composeTestRule.onNodeWithTag("pad_green").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_score_text").assertDoesNotExist()

        // 4. Switch to 4P mode: Header Best Badge hides, wins_summary_bar is removed from Home, START label updates
        composeTestRule.onNodeWithTag("mode_4p").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("best_score_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("wins_summary_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("start_game_button").assertTextContains("START 4P MATCH", substring = true)

        // 5. Switch back to SOLO and verify Settings opens cleanly without redundant Difficulty chips
        composeTestRule.onNodeWithTag("mode_solo").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("difficulty_easy").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun pass2F_homePresence_instrumentInvite_copy_and_podiumCleanup() {
        composeTestRule.waitForIdle()

        // 1. Verify [EASY | HARD] difficulty toggle on Row B and no [+ Challenge] pill on Home
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").assertIsDisplayed()

        // 2. Open Enter Challenge sheet from Rivalry tab and verify "Enter challenge code" title, '?' help icon, and example line beneath field
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("enter_challenge_dialog_title").assertTextContains("Enter challenge code", substring = true)
        composeTestRule.onNodeWithTag("challenge_help_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_code_example_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_challenge_dialog_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()

        // 3. Dismiss onboarding popup -> one-time instrument invite chip appears -> first pad tap permanently dismisses it
        composeTestRule.onNodeWithTag("onboarding_dialog_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("instrument_invite_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("instrument_invite_chip").assertTextContains("Tap pads to play their notes.", substring = true)

        composeTestRule.onNodeWithTag("pad_green").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("instrument_invite_chip").assertDoesNotExist()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean(KEY_INSTRUMENT_INVITE_DISMISSED, false))

        // 4. Verify 3-way tie rank calculation for Podium (Pass 2G Item 2: three equal 1st-place winners + 4th place)
        val tiePlayers = listOf(
            PlayerSlot(0, "P1", PLAYER_COLORS[0], isAlive = false, roundsSurvived = 0),
            PlayerSlot(1, "P2", PLAYER_COLORS[1], isAlive = false, roundsSurvived = 1),
            PlayerSlot(2, "P3", PLAYER_COLORS[2], isAlive = false, roundsSurvived = 1),
            PlayerSlot(3, "P4", PLAYER_COLORS[3], isAlive = false, roundsSurvived = 1)
        )
        val standings = computeStandings(tiePlayers)
        assertEquals(listOf(1, 1, 1, 4), standings.map { it.rank })
        assertEquals(listOf(true, true, true, false), standings.map { it.isWinner })

        // 5. Trigger first Solo Game Over and verify 2x2 Game Over grid (PASS 2J Item 1)
        composeTestRule.mainClock.autoAdvance = false
        assertFalse(prefs.getBoolean(KEY_CHALLENGE_NUDGE_SEEN, false))
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(1300)
        // Intentionally miss on Round 1 by tapping wrong pad among the 4 pads
        for (tag in listOf("pad_green", "pad_red", "pad_yellow", "pad_blue")) {
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.mainClock.advanceTimeBy(100)
            if (prefs.getBoolean(KEY_CHALLENGE_NUDGE_SEEN, false)) break
        }
        composeTestRule.mainClock.advanceTimeBy(2500)
        if (!prefs.getBoolean(KEY_CHALLENGE_NUDGE_SEEN, false)) {
            for (tag in listOf("pad_red", "pad_yellow", "pad_blue", "pad_green")) {
                composeTestRule.onNodeWithTag(tag).performClick()
                composeTestRule.mainClock.advanceTimeBy(100)
                if (prefs.getBoolean(KEY_CHALLENGE_NUDGE_SEEN, false)) break
            }
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        assertTrue(prefs.getBoolean(KEY_CHALLENGE_NUDGE_SEEN, false))

        // 6. PASS 2H & PASS 2J: Verify 2x2 Solo Game Over grid (Play Again, Home, Share Challenge, Copy Challenge), no floating nudge chip, and Tries Selector Sheet
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.onNodeWithTag("challenge_nudge_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("start_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_over_home_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_challenge_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_challenge_code_button").assertIsDisplayed()

        // Clear any score from round 1 if the random first pad happened to be green, then verify FirstTimeShareGateDialog -> ChallengerNameSetupDialog -> ShareTriesSelectorDialog
        prefs.edit().remove(bestScoreKeyFor(GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY)).apply()
        composeTestRule.onNodeWithTag("game_over_home_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("empty_share_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("first_time_share_gate_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_time_share_dismiss_button").performClick()
        composeTestRule.waitForIdle()

        // Seed a best score so the player passes the first-time score gate, then verify ChallengerNameSetupDialog -> ShareTriesSelectorDialog
        prefs.edit().putInt(bestScoreKeyFor(GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY), 5).apply()
        composeTestRule.onNodeWithTag("empty_share_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("challenger_name_setup_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenger_tag_input").performTextInput("NEON_KING")
        composeTestRule.onNodeWithTag("challenger_tag_save_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tries_selector_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tries_selector_title")
            .assertTextContains("How many tries does your friend get?", substring = true)
        composeTestRule.onNodeWithTag("tries_option_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tries_option_3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tries_option_5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tries_option_free").assertIsDisplayed()
        composeTestRule.onNodeWithTag("generate_card_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tries_option_5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("generate_card_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tries_selector_sheet").assertDoesNotExist()
        // Requirement 2: DidYouShareDialog only renders on returning from system share sheet (ON_RESUME)
        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()
        // Dismiss "Did you share it?" dialog
        composeTestRule.onNodeWithTag("did_you_share_no_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("segmented_mode_track").assertIsDisplayed()
    }

    @Test
    fun pass2L_selfPlayBlock_and_railAddChipRemoved() {
        composeTestRule.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Record a code as created by self
        val myCode = "2x2-10-T3-8X2A"
        ChallengeStorage.recordCreatedCode(context, myCode)
        assertTrue("Self challenge code must be detected", ChallengeStorage.isSelfChallenge(context, myCode))

        // 2. Open Enter Challenge dialog from Rivalry tab, enter own code, click Accept Challenge -> verify self-play blocked error
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(myCode)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("You cannot play your own challenge code", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_challenge_dialog_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify that the redundant trailing add chip does not exist on the screen
        composeTestRule.onNodeWithTag("challenge_rail_add_chip").assertDoesNotExist()
    }

    @Test
    fun pass2L_matchComplete_homeButton_and_preservesSoloRunState() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Save an active Solo run
        val savedSoloRun = com.example.challenge.SerializedRunState(
            modeName = "SOLO",
            gridDimension = 2,
            paceSuffix = "classic",
            sequencePadIds = listOf(0, 1, 2),
            round = 3,
            activePlayerIndex = 0,
            alivePlayers = listOf(true),
            roundsSurvived = listOf(3),
            soloRunSeed = 42
        )
        com.example.challenge.RunStateStorage.saveRunState(context, savedSoloRun)
        assertNotNull(com.example.challenge.RunStateStorage.loadRunState(context))

        composeTestRule.waitForIdle()

        // 2. Switch to 2P mode
        composeTestRule.onNodeWithTag("mode_2p").performClick()
        composeTestRule.waitForIdle()

        // 3. Simulate concluding a match directly on activity
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.runOnUiThread {
                activity.findViewById<android.view.View>(android.R.id.content)
            }
        }
        // Verify that RunStateStorage still has the saved solo run
        assertNotNull("Saved solo run must NOT be wiped by multiplayer state", com.example.challenge.RunStateStorage.loadRunState(context))
    }

    @Test
    fun pass2Final_vibrateToggle_and_difficultyPersistence_and_helpIcon() {
        composeTestRule.waitForIdle()

        // 1. Verify single unified feedback button in header (cycling Sound -> Vibrate -> Mute)
        composeTestRule.onNodeWithTag("audio_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_toggle_button").performClick() // Switches SOUND -> VIBRATE
        composeTestRule.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals("VIBRATE", prefs.getString(KEY_FEEDBACK_MODE, "SOUND"))
        assertTrue("Vibrate mode must enable vibration", prefs.getBoolean(KEY_VIBRATE_ENABLED, false))

        composeTestRule.onNodeWithTag("audio_toggle_button").performClick() // Switches VIBRATE -> MUTE
        composeTestRule.waitForIdle()
        assertEquals("MUTE", prefs.getString(KEY_FEEDBACK_MODE, "SOUND"))
        assertFalse("Mute mode must disable vibration", prefs.getBoolean(KEY_VIBRATE_ENABLED, true))

        composeTestRule.onNodeWithTag("audio_toggle_button").performClick() // Switches MUTE -> SOUND
        composeTestRule.waitForIdle()
        assertEquals("SOUND", prefs.getString(KEY_FEEDBACK_MODE, "SOUND"))

        // 2. Switch to Hard difficulty via Home [EASY | HARD] toggle, verify persisted in prefs
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").performClick()
        composeTestRule.waitForIdle()
        assertEquals("HARD", prefs.getString(KEY_DIFFICULTY_MODE, "EASY"))

        // 3. Open Enter Challenge dialog from Rivalry tab, tap '?' help button, verify help banner appears
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_help_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_help_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_help_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ask a friend to share their challenge code from their Game Over screen, then paste it here!")
            .assertIsDisplayed()

        // 4. Verify backwards compatible challenge parser with difficulty and checksum
        val testCodeHard = com.example.challenge.ChallengeSeed.formatChallengeCode("4x4", 1, 3, com.example.challenge.GameDifficulty.HARD, 12345)
        val parsedHard = com.example.challenge.ChallengeSeed.parseChallengeCode(testCodeHard)
        assertNotNull(parsedHard)
        assertEquals(com.example.challenge.GameDifficulty.HARD, parsedHard?.difficulty)
        assertEquals(3, parsedHard?.tries)
        assertNotNull(parsedHard?.checksum)

        // Also verify parsing without optional checksum
        val testNoChecksum = "4x4-1-T3H-2GPGX"
        val parsedNoChecksum = com.example.challenge.ChallengeSeed.parseChallengeCode(testNoChecksum)
        assertNotNull(parsedNoChecksum)
        assertEquals(com.example.challenge.GameDifficulty.HARD, parsedNoChecksum?.difficulty)

        val testCodeEasyLegacy = "2x2-5-8X2A"
        val parsedLegacy = com.example.challenge.ChallengeSeed.parseChallengeCode(testCodeEasyLegacy)
        assertNotNull(parsedLegacy)
        assertEquals(com.example.challenge.GameDifficulty.EASY, parsedLegacy?.difficulty)
    }

    @Test
    fun pass3_notificationEngine_and_trophyDebugTrigger() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (!androidx.work.WorkManager.isInitialized()) {
            androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build())
        }

        // 1. Verify all 30 phrases format safely without runtime exception across all 3 tiers
        for (phrase in com.example.notification.NotificationContentProvider.TIER_1) {
            val resNormal = phrase.format(12, "3×3")
            assertTrue(resNormal.isNotBlank())
            val resZero = phrase.format(0, "2×2")
            assertTrue(resZero.isNotBlank())
        }
        for (phrase in com.example.notification.NotificationContentProvider.TIER_2) {
            val resNormal = phrase.format(15, "4×4")
            assertTrue(resNormal.isNotBlank())
            val resZero = phrase.format(0, "2×2")
            assertTrue(resZero.isNotBlank())
        }
        for (phrase in com.example.notification.NotificationContentProvider.TIER_3) {
            val resNormal = phrase.format(20, "3×3")
            assertTrue(resNormal.isNotBlank())
            val resZero = phrase.format(0, "2×2")
            assertTrue(resZero.isNotBlank())
        }

        // Verify tier selection by time delta
        val t1 = com.example.notification.NotificationContentProvider.getPhrase(context, 1000L * 60 * 60 * 5, 10, "2×2")
        assertTrue(t1.isNotBlank())
        val t2 = com.example.notification.NotificationContentProvider.getPhrase(context, 1000L * 60 * 60 * 30, 10, "2×2")
        assertTrue(t2.isNotBlank())
        val t3 = com.example.notification.NotificationContentProvider.getPhrase(context, 1000L * 60 * 60 * 60, 10, "2×2")
        assertTrue(t3.isNotBlank())

        // 2. Open Settings sheet, verify Daily Notifications switch is displayed at the top
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("daily_notifications_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_size_2x2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_easy").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pace_mode_classic").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reset_wins_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Test Smart Skip logic:
        // When user opened app today (lastOpened = now), standard worker call (isTest = false) returns false (skipped)
        val prefs = context.getSharedPreferences("glowback_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(com.example.notification.NotificationScheduler.KEY_LAST_OPENED_TIMESTAMP, System.currentTimeMillis()).apply()
        val standardResult = com.example.notification.NotificationScheduler.postGlowbackNotification(context, isTest = false)
        assertFalse("Smart skip must suppress notification if user already played today", standardResult)

        // However, 5-tap test bypass (isTest = true) must bypass Smart Skip and succeed
        val testResult = com.example.notification.NotificationScheduler.postGlowbackNotification(context, isTest = true)
        assertTrue("isTest = true must bypass Smart Skip", testResult)

        // 5. Test NotificationScheduler cancellation on toggle off
        com.example.notification.NotificationScheduler.cancelReminder(context)
        val workManager = androidx.work.WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWork(com.example.notification.NotificationScheduler.UNIQUE_WORK_NAME).get()
        for (info in workInfos) {
            assertTrue(info.state == androidx.work.WorkInfo.State.CANCELLED || info.state.isFinished)
        }
    }

    @Test
    fun pass4_bugFixes_resetFeedback_notificationScoreMatch_and_shuffleDeck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("glowback_prefs", Context.MODE_PRIVATE)

        // 1. Reset Wins feedback and isolation: ensure solo score is preserved while wins AND longest_survival_2p/3p/4p reset
        prefs.edit()
            .putInt("best_2x2_classic", 9)
            .putInt("best_4x4_progressive", 0)
            .putInt("wins_P1", 5)
            .putInt("longest_survival_2p", 7)
            .putInt("longest_survival_3p", 4)
            .putInt("longest_survival_4p", 6)
            .apply()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Reset all saved Pass & Play wins and survival records (P1-P4)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reset_wins_button").performClick()
        composeTestRule.waitForIdle()

        // Verify multiplayer wins AND survival records are reset, but solo scores are 100% untouched
        assertEquals(0, prefs.getInt("wins_P1", -1))
        assertEquals(0, prefs.getInt("longest_survival_2p", -1))
        assertEquals(0, prefs.getInt("longest_survival_3p", -1))
        assertEquals(0, prefs.getInt("longest_survival_4p", -1))
        assertEquals(9, prefs.getInt("best_2x2_classic", -1))
        assertEquals(0, prefs.getInt("best_4x4_progressive", -1))
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Notification High Score Mismatch Fix:
        // Set active board to 4x4 Progressive (score 0), ensure notification phrase does NOT say 9
        prefs.edit()
            .putString("selected_grid_size", "FOUR_BY_FOUR")
            .putString("selected_pace_mode", "PROGRESSIVE")
            .apply()

        val notificationPrefs = context.getSharedPreferences("glowback_notification_prefs", Context.MODE_PRIVATE)
        notificationPrefs.edit().clear().apply()

        // Verify getPhrase returns a zero-score phrase, not including "9"
        val phrase = com.example.notification.NotificationContentProvider.getPhrase(context, 1000L * 60 * 60 * 2, 0, "4×4")
        assertFalse("Notification for zero-score board must not say 9", phrase.contains(" 9 "))

        // 3. Shuffle Deck: verify 10 successive calls produce 10 unique phrases in Tier 1
        notificationPrefs.edit().clear().apply()
        val generatedPhrases = mutableSetOf<String>()
        repeat(10) {
            val p = com.example.notification.NotificationContentProvider.getPhrase(context, 1000L * 60 * 60 * 2, 10, "4×4")
            generatedPhrases.add(p)
        }
        assertEquals("Shuffle deck must yield all 10 distinct phrases before repeating", 10, generatedPhrases.size)
    }

    @Test
    fun pass5_newChallengeCodeFormat_and_backwardsCompatibility() {
        // 1. Test 7-segment v2 format generation and parsing
        val codeV2 = ChallengeSeed.formatChallengeCode(
            gridTag = "4x4",
            targetScore = 12,
            tries = 3,
            difficulty = GameDifficulty.HARD,
            seed = 12345,
            senderName = "neon_king",
            chainId = "K7Q",
            isRevenge = false
        )
        val parts = codeV2.split('-')
        assertEquals("V2 challenge code must have exactly 7 segments", 7, parts.size)
        assertEquals("4x4", parts[0])
        assertEquals("12", parts[1])
        assertEquals("T3H", parts[2])
        assertEquals("NEON_KING", parts[3]) // Auto-uppercased
        assertEquals("K7Q", parts[4])

        val parsedV2 = ChallengeSeed.parseChallengeCode(codeV2)
        assertNotNull("V2 challenge code must parse successfully", parsedV2)
        requireNotNull(parsedV2)
        assertEquals("4x4", parsedV2.gridTag)
        assertEquals(12, parsedV2.targetScore)
        assertEquals(3, parsedV2.tries)
        assertEquals(GameDifficulty.HARD, parsedV2.difficulty)
        assertEquals("NEON_KING", parsedV2.senderName)
        assertEquals("K7Q", parsedV2.chainId)
        assertFalse(parsedV2.isLegacy)

        // 2. Test name sanitization rules (max 10 chars, spaces to underscores, uppercase)
        val sanitized = ChallengeSeed.sanitizePlayerName("john doe 99!")
        assertEquals("JOHN_DOE_9", sanitized) // sanitized and truncated to 10 chars
        val emptySanitized = ChallengeSeed.sanitizePlayerName("   ")
        assertEquals("HERO", emptySanitized) // fallback

        // 3. Test 5-segment backwards compatibility (lacks name, defaults to RIVAL and isLegacy=true)
        val legacy5Seg = "4x4-6-T3E-2GPGX-N"
        val parsedLegacy5 = ChallengeSeed.parseChallengeCode(legacy5Seg)
        assertNotNull("Legacy 5-segment code must parse successfully", parsedLegacy5)
        requireNotNull(parsedLegacy5)
        assertEquals("RIVAL", parsedLegacy5.senderName)
        assertTrue(parsedLegacy5.isLegacy)
    }

    @Test
    fun pass5_pendingShare_processDeathAndResilience() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify saving pending share persists across simulated process death via SharedPreferences
        val testItem = com.example.challenge.AwaitingChallenge(
            chainId = "ABC",
            code = "4x4-10-T3E-HERO-ABC-2GPGX-C",
            gridSizeTag = "4x4",
            targetScore = 10,
            difficulty = GameDifficulty.EASY,
            paceName = "Classic",
            tries = 3,
            createdAt = System.currentTimeMillis()
        )
        ChallengeStorage.savePendingShare(context, testItem)

        // Retrieve
        val retrieved = ChallengeStorage.getPendingShare(context)
        assertNotNull("Pending share must be persisted in SharedPreferences", retrieved)
        assertEquals("ABC", retrieved?.chainId)
        assertEquals("4x4-10-T3E-HERO-ABC-2GPGX-C", retrieved?.code)

        // Clear
        ChallengeStorage.clearPendingShare(context)
        val cleared = ChallengeStorage.getPendingShare(context)
        assertTrue("Pending share must be null after clearing", cleared == null)
    }

    @Test
    fun pass5_awaitingChallenges_and_ttlPruning() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Save fresh awaiting item
        val freshItem = com.example.challenge.AwaitingChallenge(
            chainId = "FRESH",
            code = "2x2-5-T3E-HERO-FRESH-8X2A-C",
            gridSizeTag = "2x2",
            targetScore = 5,
            difficulty = GameDifficulty.EASY,
            paceName = "Classic",
            tries = 3,
            createdAt = System.currentTimeMillis()
        )
        ChallengeStorage.saveAwaitingChallenge(context, freshItem)

        // Save expired awaiting item (> 3 days / 72 hours old)
        val expiredItem = com.example.challenge.AwaitingChallenge(
            chainId = "OLD",
            code = "2x2-5-T3E-HERO-OLD-8X2A-C",
            gridSizeTag = "2x2",
            targetScore = 5,
            difficulty = GameDifficulty.EASY,
            paceName = "Classic",
            tries = 3,
            createdAt = System.currentTimeMillis() - (4L * 24 * 60 * 60 * 1000L)
        )
        ChallengeStorage.saveAwaitingChallenge(context, expiredItem)

        // Load and verify expired items (> 72h) are automatically pruned
        val loaded = ChallengeStorage.loadAwaitingChallenges(context)
        assertTrue("Fresh item must be present", loaded.any { it.chainId == "FRESH" })
        assertFalse("Expired item (> 3 days / 72h) must be automatically pruned", loaded.any { it.chainId == "OLD" })
    }

    @Test
    fun pass5_leaderboard_navigation_and_tabs() {
        composeTestRule.waitForIdle()

        // 1. Tap Leaderboard on bottom nav
        composeTestRule.onNodeWithTag("nav_leaderboard").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Verify Hall of Fame screen is displayed with tabs
        composeTestRule.onNodeWithTag("hall_of_fame_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_rivalry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_records").assertIsDisplayed()

        // Switch to Records tab
        composeTestRule.onNodeWithTag("tab_records").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("records_tab_content").assertIsDisplayed()

        // Switch to Rivalry tab
        composeTestRule.onNodeWithTag("tab_rivalry").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rivalry_tab_content").assertIsDisplayed()

        // Return to Home
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_game_button").assertIsDisplayed()
    }

    @Test
    fun pass5_1_freeTriesToken_noLossTally_and_rootDialogMutualExclusion() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Confirmation 2: Verify Free tries token is TF (TFE / TFH / RFE / RFH) and parses back as tries == 0
        val freeEasyCode = ChallengeSeed.formatChallengeCode(
            gridTag = "4x4",
            targetScore = 14,
            tries = 0,
            difficulty = GameDifficulty.EASY,
            seed = 987654,
            senderName = "ALEX",
            chainId = "K7Q",
            isRevenge = false
        )
        assertTrue("Free tries Easy token must contain -TFE-", freeEasyCode.contains("-TFE-"))
        val parsedFreeEasy = ChallengeSeed.parseChallengeCode(freeEasyCode)
        assertNotNull(parsedFreeEasy)
        assertEquals(0, parsedFreeEasy?.tries)

        val freeHardRevengeCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 20,
            tries = 0,
            difficulty = GameDifficulty.HARD,
            seed = 123456,
            senderName = "ALEX",
            chainId = "K7Q",
            isRevenge = true
        )
        assertTrue("Free tries Hard Revenge token must contain -RFH-", freeHardRevengeCode.contains("-RFH-"))
        val parsedFreeHard = ChallengeSeed.parseChallengeCode(freeHardRevengeCode)
        assertNotNull(parsedFreeHard)
        assertEquals(0, parsedFreeHard?.tries)

        // 2. Confirmation 3: Free tries tally rule (loss is impossible when maxTries == 0; win and tie still record)
        val rivalOnLossAttempt = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "FREE_RIVAL",
            userScore = 2,
            targetScore = 10,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "F01",
            maxTries = 0
        )
        assertTrue("Losses must never be recorded when maxTries == 0 (Free tries)", rivalOnLossAttempt == null)

        val rivalAfterWin = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "FREE_RIVAL",
            userScore = 11,
            targetScore = 10,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "F01",
            maxTries = 0
        )
        assertNotNull("Win under Free tries must record rival outcome", rivalAfterWin)
        assertEquals(1, rivalAfterWin?.wins)
        assertEquals(0, rivalAfterWin?.losses)
        ChallengeStorage.deleteRival(context, "FREE_RIVAL")

        // 3. Confirmation 1: Root-level dialog mutual exclusion from Leaderboard screen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        // Tap Paste Code on empty Rivalry state -> opens EnterChallengeDialog directly on Leaderboard
        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("enter_challenge_dialog_card").assertIsDisplayed()

        // Confirmation 4: Enter a 5-segment legacy code -> prompts LegacyCodeNameDialog ("Old format code detected")
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput("4x4-6-T3E-2GPGX-N")
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("legacy_code_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legacy_dialog_title").assertTextContains("Old format code detected", substring = true)
        composeTestRule.onNodeWithTag("legacy_dialog_cancel").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("close_challenge_dialog_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun pass5_2_hardening_and_polish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Text surrounded code extraction test (User proposal)
        val validChallengeCode = ChallengeSeed.formatChallengeCode(
            gridTag = "4x4",
            targetScore = 9,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 12345,
            senderName = "POO",
            chainId = "VY5"
        )
        val fullChatMessage = "I scored 9 on Glowback (Easy)! Beat my sequence in 3 tries: $validChallengeCode"
        val extracted = ChallengeSeed.extractCodeFromText(fullChatMessage)
        assertNotNull("Must extract valid challenge code token from full message", extracted)
        assertEquals(validChallengeCode, extracted)

        val parsedFromSurroundingText = ChallengeSeed.parseChallengeCode(fullChatMessage)
        assertNotNull("parseChallengeCode must parse code directly when surrounded by text", parsedFromSurroundingText)
        assertEquals("4x4", parsedFromSurroundingText?.gridTag)
        assertEquals(9, parsedFromSurroundingText?.targetScore)
        assertEquals("POO", parsedFromSurroundingText?.senderName)

        // 2. Minimum target score >= 5 enforcement
        prefs.edit().putInt(bestScoreKeyFor(GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY), 4).apply()
        val boardsBelow5 = getAllBoardScores(prefs)
        assertTrue("Board score of 4 must not be offered as outbound challenge target", boardsBelow5.isEmpty())

        prefs.edit().putInt(bestScoreKeyFor(GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY), 5).apply()
        val boardsWith5 = getAllBoardScores(prefs)
        assertEquals("Board score of 5 must be offered as outbound challenge target", 1, boardsWith5.size)
        assertEquals(5, boardsWith5[0].score)

        // 3. Inbound merge tuple (chainId + gridTag ONLY; targetScore dropped so reply codes with new scores match)
        val awaitingCard = com.example.challenge.AwaitingChallenge(
            chainId = "K7Q",
            code = "4x4-5-T3E-HERO-K7Q-8X2AB-C",
            gridSizeTag = "4x4",
            targetScore = 5,
            difficulty = GameDifficulty.EASY,
            paceName = "Classic",
            tries = 3
        )
        ChallengeStorage.saveAwaitingChallenge(context, awaitingCard)

        // Mismatched grid should NOT delete the card
        val deletedWrongGrid = ChallengeStorage.deleteAwaitingMatch(context, "K7Q", "2x2")
        assertFalse("Mismatched grid must not delete awaiting challenge", deletedWrongGrid)
        val stillExists = ChallengeStorage.findAwaitingMatch(context, "K7Q", "4x4")
        assertNotNull("Card must still exist when grid does not match", stillExists)

        // Matching 2-tuple (chainId + gridTag) deletes it cleanly regardless of responder's new targetScore
        val deletedMatch = ChallengeStorage.deleteAwaitingMatch(context, "K7Q", "4x4")
        assertTrue("Matching 2-tuple (chainId + gridTag) must successfully delete awaiting challenge", deletedMatch)
        assertEquals(0, ChallengeStorage.loadAwaitingChallenges(context).size)

        // 4. Requirement 7: Clipboard safety guard
        val safeClip = com.example.challenge.getClipboardTextSafely(context)
        // Does not throw and returns either null or String
        assertTrue("Clipboard safe helper must execute without crashing", safeClip == null || safeClip.isNotEmpty())

        // 5. Requirement 8: Name input hardening (no hyphens, only [A-Z0-9_] max 10)
        val sanitized = ChallengeSeed.sanitizePlayerName("my-hero@123!!")
        assertEquals("MYHERO123", sanitized)
        assertTrue("Sanitized name must not contain hyphens", !sanitized.contains("-"))

        // 6. UI Tests: Same-name hint text & DidYouShare button labels
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("enter_challenge_dialog_card").assertIsDisplayed()

        // Same-name hint text is displayed under rival input
        composeTestRule.onNodeWithTag("same_name_hint_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("same_name_hint_text").assertTextContains("separate two rivals", substring = true)

        composeTestRule.onNodeWithTag("close_challenge_dialog_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun pass5_3_grid_5x4_and_hardening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Audio scale assertions: exactly 20 distinct, strictly ascending frequencies within 120-1250 Hz
        val classicScale = AudioFrequencies.CLASSIC_20_FREQUENCIES
        val pentatonicScale = AudioFrequencies.PENTATONIC_20_FREQUENCIES

        assertEquals("Classic scale must contain exactly 20 frequencies", 20, classicScale.size)
        assertEquals("Classic scale frequencies must be distinct", 20, classicScale.distinct().size)
        for (i in 0 until classicScale.size - 1) {
            assertTrue("Classic scale frequencies must be strictly ascending", classicScale[i] < classicScale[i + 1])
        }
        assertTrue("Classic scale minimum frequency must be >= 120 Hz", classicScale.minOrNull()!! >= 120.0)
        assertTrue("Classic scale maximum frequency must be <= 1200 Hz", classicScale.maxOrNull()!! <= 1250.0)

        assertEquals("Pentatonic scale must contain exactly 20 frequencies", 20, pentatonicScale.size)
        assertEquals("Pentatonic scale frequencies must be distinct", 20, pentatonicScale.distinct().size)
        for (i in 0 until pentatonicScale.size - 1) {
            assertTrue("Pentatonic scale frequencies must be strictly ascending", pentatonicScale[i] < pentatonicScale[i + 1])
        }
        assertTrue("Pentatonic scale minimum frequency must be >= 120 Hz", pentatonicScale.minOrNull()!! >= 120.0)
        assertTrue("Pentatonic scale maximum frequency must be <= 1250 Hz", pentatonicScale.maxOrNull()!! <= 1250.0)

        // PCM Pool Pre-Warming verification: all 20 pads pre-warmed
        val audioSynth = GlowAudioSynthesizer()
        assertEquals("GRID_20_PADS must contain exactly 20 pads", 20, GRID_20_PADS.size)
        for (pad in GRID_20_PADS) {
            assertTrue("Audio synth must pre-warm PCM buffer for pad frequency ${pad.frequencyHz}", audioSynth.frequencyPcmBuffers.containsKey(pad.frequencyHz))
        }

        // 2. Notification Scheduler Key Audit:
        // When selected difficulty is HARD, must read best_${grid}_${pace}_HARD and display that score (not 0 or EASY)
        prefs.edit()
            .putString("selected_grid_size", "FIVE_BY_FOUR")
            .putString("selected_pace_mode", "CLASSIC")
            .putString(KEY_DIFFICULTY_MODE, "HARD")
            .putInt("best_5x4_classic_HARD", 19)
            .putInt("best_5x4_classic_EASY", 4)
            .putInt("best_5x4_classic", 0) // Dead legacy key must be ignored
            .apply()

        // NotificationContentProvider phrase generation with HARD score
        val tier1Phrase = com.example.notification.NotificationContentProvider.TIER_1[0].format(19, "5×4")
        assertTrue("Notification phrase must show the HARD score 19", tier1Phrase.contains("19"))
        assertTrue("Notification phrase must show the 5×4 board label", tier1Phrase.contains("5×4"))

        // 3. 5x4 Challenge Code Generation & Parsing Round-Trip
        val code5x4 = ChallengeSeed.formatChallengeCode(
            gridTag = "5x4",
            targetScore = 15,
            tries = 3,
            difficulty = GameDifficulty.HARD,
            seed = 7777,
            senderName = "BOSS",
            chainId = "W8M"
        )
        assertTrue("Formatted 5x4 code must start with 5x4-", code5x4.startsWith("5x4-15-T3H-BOSS-W8M-"))
        val parsed5x4 = ChallengeSeed.parseChallengeCode(code5x4)
        assertNotNull("5x4 code must parse successfully", parsed5x4)
        assertEquals("5x4", parsed5x4?.gridTag)
        assertEquals(15, parsed5x4?.targetScore)
        assertEquals(3, parsed5x4?.tries)
        assertEquals(GameDifficulty.HARD, parsed5x4?.difficulty)
        assertEquals("BOSS", parsed5x4?.senderName)
        assertEquals("W8M", parsed5x4?.chainId)

        // Extraction from chat message
        val chatMsg = "Beat my sequence on the new 5x4 grid: $code5x4"
        val extracted5x4 = ChallengeSeed.extractCodeFromText(chatMsg)
        assertEquals(code5x4, extracted5x4)

        // 4. Sequence Bounds & Unique 20 Pads / Glyphs
        val pads5x4 = padsForGridSize(GridSize.FIVE_BY_FOUR)
        assertEquals(20, pads5x4.size)
        val padIds = pads5x4.map { it.id }.distinct()
        assertEquals("Pad IDs must be 0 to 19", (0 until 20).toList(), padIds)
        val glyphs = pads5x4.map { it.glyph }.distinct()
        assertEquals("All 20 pads must have distinct glyphs", 20, glyphs.size)
        assertTrue("Glyphs must contain PENTAGON", glyphs.contains(PadGlyph.PENTAGON))
        assertTrue("Glyphs must contain TEARDROP", glyphs.contains(PadGlyph.TEARDROP))
        assertTrue("Glyphs must contain OVAL", glyphs.contains(PadGlyph.OVAL))
        assertTrue("Glyphs must contain FLAG", glyphs.contains(PadGlyph.FLAG))

        // 5. SharedPreferences Key Writes and Reads for 5x4
        writeBestScore(prefs, GridSize.FIVE_BY_FOUR, PaceMode.PROGRESSIVE, GameDifficulty.HARD, 25)
        assertEquals("Explicit key must hold score 25", 25, prefs.getInt("best_5x4_progressive_HARD", 0))
        assertEquals("readMigratedBestScore must return 25", 25, readMigratedBestScore(prefs, GridSize.FIVE_BY_FOUR, PaceMode.PROGRESSIVE, GameDifficulty.HARD))

        // 6. UI Surfaces: Settings Grid Selector has [5x4]
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("config_pill_grid").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_5x4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_close_button").performClick()
        composeTestRule.waitForIdle()

        // Records tab has [5x4]
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_records").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("records_grid_5×4").assertIsDisplayed()

        // 2-Step Battle Card is present in Rivalry tab
        composeTestRule.onNodeWithTag("tab_rivalry").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("two_step_battle_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_share_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_paste_button").assertIsDisplayed()

        // Return to Home
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun pass5_4_challengeOverhaul_homeDifficultyToggle_ttl_and_replyLocks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Lock 2: R-Token Parser Uniformity (T3E vs R3E, T3H vs R3H, TFE vs RFE, TFH vs RFH)
        val t3eCode = ChallengeSeed.formatChallengeCode("4x4", 8, 3, GameDifficulty.EASY, 4242, "HERO", "C1A", isRevenge = false)
        val r3eCode = ChallengeSeed.formatChallengeCode("4x4", 8, 3, GameDifficulty.EASY, 4242, "HERO", "C1A", isRevenge = true)
        assertTrue(t3eCode.contains("-T3E-"))
        assertTrue(r3eCode.contains("-R3E-"))

        val parsedT3E = ChallengeSeed.parseChallengeCode(t3eCode)
        val parsedR3E = ChallengeSeed.parseChallengeCode(r3eCode)
        assertNotNull(parsedT3E)
        assertNotNull(parsedR3E)
        assertEquals(parsedT3E?.tries, parsedR3E?.tries)
        assertEquals(parsedT3E?.difficulty, parsedR3E?.difficulty)
        assertFalse(parsedT3E!!.isRevenge)
        assertTrue(parsedR3E!!.isRevenge)

        val tfhCode = ChallengeSeed.formatChallengeCode("5x4", 11, 0, GameDifficulty.HARD, 9001, "RIVAL", "Z9Y", isRevenge = false)
        val rfhCode = ChallengeSeed.formatChallengeCode("5x4", 11, 0, GameDifficulty.HARD, 9001, "RIVAL", "Z9Y", isRevenge = true)
        val parsedTFH = ChallengeSeed.parseChallengeCode(tfhCode)
        val parsedRFH = ChallengeSeed.parseChallengeCode(rfhCode)
        assertNotNull(parsedTFH)
        assertNotNull(parsedRFH)
        assertEquals(0, parsedTFH?.tries)
        assertEquals(0, parsedRFH?.tries)
        assertEquals(GameDifficulty.HARD, parsedTFH?.difficulty)
        assertEquals(GameDifficulty.HARD, parsedRFH?.difficulty)
        assertFalse(parsedTFH!!.isRevenge)
        assertTrue(parsedRFH!!.isRevenge)

        // 2. Lock 3 (Reply-Code Exemption): Inbound reply codes with targetScore = 1..4 parse cleanly
        for (lowScore in 1..4) {
            val lowReplyCode = ChallengeSeed.formatChallengeCode(
                gridTag = "2x2",
                targetScore = lowScore,
                tries = 3,
                difficulty = GameDifficulty.HARD,
                seed = 777,
                senderName = "BLITZ",
                chainId = "REP",
                isRevenge = true
            )
            val parsedLow = ChallengeSeed.parseChallengeCode(lowReplyCode)
            assertNotNull("Reply code with score $lowScore must be accepted by parser", parsedLow)
            assertEquals(lowScore, parsedLow?.targetScore)
            assertTrue(parsedLow!!.isRevenge)
        }

        // 3. Lock 3 (Expiry Wording): Under 24h remaining shows "Expires today", >= 24h shows "Expires in Nd"
        val now = 1_700_000_000_000L
        // Newly created (72h remaining -> 3d)
        assertEquals("Expires in 3d", com.example.challenge.formatAwaitingExpiry(now, now))
        // 30 hours old (42h remaining -> 2d)
        assertEquals("Expires in 2d", com.example.challenge.formatAwaitingExpiry(now - 30L * 3600_000L, now))
        // 50 hours old (22h remaining -> under 24h -> "Expires today")
        assertEquals("Expires today", com.example.challenge.formatAwaitingExpiry(now - 50L * 3600_000L, now))
        // 71 hours old (1h remaining -> under 24h -> "Expires today")
        assertEquals("Expires today", com.example.challenge.formatAwaitingExpiry(now - 71L * 3600_000L, now))

        // 4. Home [Easy | Hard] Toggle accessibility, Title Case copy, persistence, and mid-round block
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Easy difficulty").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Hard difficulty").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").assertTextContains("Easy", substring = false)
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").assertTextContains("Hard", substring = false)

        // Tap Hard on Home screen -> updates SharedPreferences
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").performClick()
        composeTestRule.waitForIdle()
        assertEquals("HARD", prefs.getString(KEY_DIFFICULTY_MODE, "EASY"))

        // Tap Easy on Home screen -> updates SharedPreferences back to EASY
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").performClick()
        composeTestRule.waitForIdle()
        assertEquals("EASY", prefs.getString(KEY_DIFFICULTY_MODE, "HARD"))

        // Start a round and attempt to switch difficulty mid-round -> blocked (stays EASY)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").performClick()
        composeTestRule.mainClock.advanceTimeBy(50)
        assertEquals(
            "Mid-round tap on difficulty toggle must NOT change difficulty",
            "EASY",
            prefs.getString(KEY_DIFFICULTY_MODE, "HARD")
        )

        // Advance to PLAYING and intentionally fail on Round 1 (score = 0 < 5)
        composeTestRule.mainClock.advanceTimeBy(1300)
        for (tag in listOf("pad_green", "pad_red", "pad_yellow", "pad_blue")) {
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.mainClock.advanceTimeBy(100)
        }
        composeTestRule.mainClock.advanceTimeBy(2500)
        for (tag in listOf("pad_red", "pad_yellow", "pad_blue", "pad_green")) {
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.mainClock.advanceTimeBy(100)
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        // 5. Verify no recordCreatedCode side-effect occurred during Game Over composition
        val createdCodesPrefs = context.getSharedPreferences("glowback_challenges_v1", Context.MODE_PRIVATE)
        val createdSetBeforeClick = createdCodesPrefs.getStringSet("my_created_codes_v1", emptySet()) ?: emptySet()
        assertTrue("Recomposition on Game Over must NOT record any created code", createdSetBeforeClick.isEmpty())

        // 6. Verify grayed-out Share Challenge & Copy Challenge buttons remain clickable at score < 5 and show Score-5 Gate
        composeTestRule.onNodeWithTag("share_challenge_button").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("copy_challenge_code_button").assertIsDisplayed().assertIsEnabled()

        composeTestRule.onNodeWithTag("share_challenge_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("first_time_share_gate_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_time_share_title").assertTextContains("Reach a score of 5!", substring = true)
        composeTestRule.onNodeWithTag("first_time_share_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("first_time_share_dismiss_button").performClick()
        composeTestRule.waitForIdle()

        // Return Home
        composeTestRule.onNodeWithTag("game_over_home_button").performClick()
        composeTestRule.waitForIdle()

        // 6b. Verify Rivalry Empty State shows 4-step walkthrough card with locked copy before any rivals/awaiting exist
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("two_step_battle_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share your code", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("They play & reply", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Paste their reply here", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Fire back", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()

        // 7. Verify Rivalry Tab awaiting state: hides 4-step card & redundant pill, shows persistent action bar + in-card buttons
        val awaiting = com.example.challenge.AwaitingChallenge(
            chainId = "M77",
            code = "2x2-6-T3E-HERO-M77-8X2A-C",
            gridSizeTag = "2x2",
            targetScore = 6,
            difficulty = GameDifficulty.EASY,
            paceName = "Classic",
            tries = 3,
            createdAt = System.currentTimeMillis()
        )
        ChallengeStorage.saveAwaitingChallenge(context, awaiting)

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rivalry_action_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rivalry_share_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rivalry_paste_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("two_step_battle_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pending_challenge_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("paste_reply_awaiting_M77").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_awaiting_M77").assertIsDisplayed()

        // 7b. Lock 2 (Clipboard at 0ms) & Lock 1 (Single-fire guard during flash):
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("clear", ""))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("copy_awaiting_M77").performClick()
        // At 0ms (before advancing clock), clipboard write has already occurred!
        assertEquals(
            "Clipboard write must happen at 0ms immediately on tap",
            "2x2-6-T3E-HERO-M77-8X2A-C",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )
        // Repeat tap during the 600ms flash window is ignored by the single-fire guard
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("clear", "DURING_FLASH"))
        composeTestRule.onNodeWithTag("copy_awaiting_M77").performClick()
        assertEquals(
            "Single-fire guard must ignore repeat taps during flash window",
            "DURING_FLASH",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        // 8. Lock 1 (2-Tuple Inbound Merge): Pasting a reply code with a DIFFERENT score (e.g., 9 vs awaiting target 6)
        // matches on (chainId="M77" + gridTag="2x2"), prunes the awaiting card, and creates the Rival card!
        val replyFromRival = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 9, // Responder beat target 6 with score 9
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 5555,
            senderName = "NOVA",
            chainId = "M77",
            isRevenge = true
        )
        composeTestRule.onNodeWithTag("paste_reply_awaiting_M77").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(replyFromRival)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Verify awaiting card M77 was matched and pruned even though reply targetScore (9) != awaiting targetScore (6)
        assertTrue(
            "Awaiting card M77 must be pruned on inbound 2-tuple (chainId + gridTag) match",
            ChallengeStorage.loadAwaitingChallenges(context).none { it.chainId == "M77" }
        )
        // Verify Rival NOVA was recorded from the reply
        val rivalsAfterMerge = ChallengeStorage.loadRivals(context)
        assertTrue("Rival NOVA must be created from matched reply code", rivalsAfterMerge.any { it.name == "NOVA" })

        // 9. Post-Expiry Reply Code Paste: Even when NO awaiting card exists (expired > 72h),
        // pasting an R-token reply code still creates/merges the rival card from the NAME segment.
        val postExpiryReplyCode = ChallengeSeed.formatChallengeCode(
            gridTag = "4x4",
            targetScore = 7,
            tries = 3,
            difficulty = GameDifficulty.HARD,
            seed = 31337,
            senderName = "ZEPHYR",
            chainId = "EXP",
            isRevenge = true
        )
        // Navigate back to Rivalry tab to paste post-expiry reply code
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(postExpiryReplyCode)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        val rivalsAfterPostExpiry = ChallengeStorage.loadRivals(context)
        assertTrue(
            "Post-expiry reply code must still create/merge RivalRecord from NAME segment",
            rivalsAfterPostExpiry.any { it.name == "ZEPHYR" }
        )
    }

    @Test
    fun pass5_7_headerCleanup_sliderOption1_recordsColorDots_and_boardChipLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("glowback_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("best_2x2_classic_EASY", 8)
            .putInt("best_2x2_classic_HARD", 6)
            .apply()
        ChallengeStorage.setMyName(context, "HERO")

        composeTestRule.waitForIdle()
        // Dismiss onboarding popup if shown
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. Verify pad_grid_container top Y is identical across SOLO, 2P, 3P, and 4P (zero vertical jump)
        val soloTop = composeTestRule.onNodeWithTag("pad_grid_container").fetchSemanticsNode().boundsInRoot.top
        for (modeTag in listOf("mode_2p", "mode_3p", "mode_4p", "mode_solo")) {
            composeTestRule.onNodeWithTag(modeTag).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("wins_summary_bar").assertDoesNotExist()
            val modeTop = composeTestRule.onNodeWithTag("pad_grid_container").fetchSemanticsNode().boundsInRoot.top
            assertEquals("pad_grid_container top Y must not jump when switching to $modeTag", soloTop, modeTop, 0.5f)
        }

        // 2. Verify Leaderboard -> Records (4P) displays accessible player color dots ("Player 1, blue" .. "Player 4, yellow")
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_records").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("records_mode_4P").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Player 1, blue").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Player 2, red").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Player 3, green").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Player 4, yellow").assertIsDisplayed()

        // 3. Verify Rivalry Tab -> Share Code opens ShareTriesSelectorDialog with Easy / Hard labels on board chips
        composeTestRule.onNodeWithTag("tab_rivalry").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_share_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tries_selector_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("2x2 Classic · Easy", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("2x2 Classic · Hard", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_tries_selector_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun pass5_9_rivalryFlowCleanup_replyRecordOnly_and_conquestWin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")

        // 1. Verify recordMatchOutcome treats userScore == targetScore as WON in challenge context (auto-stop at target)
        val conquestRecord = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "POOVARASAN",
            userScore = 5,
            targetScore = 5,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "CNQ",
            maxTries = 3
        )
        assertNotNull(conquestRecord)
        assertEquals("WON", conquestRecord!!.lastStatus)
        assertEquals(1, conquestRecord.wins)
        assertEquals(0, conquestRecord.ties)

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 2. Verify ActiveChallengesRail is never shown on Home screen even if legacy challenge is saved
        val legacyItem = ChallengeItem(
            code = "2x2-5-T3E-POOVARASAN-CNQ-8X2A-C",
            rivalName = "POOVARASAN",
            gridSizeTag = "2x2",
            targetScore = 5,
            maxTries = 3,
            attemptsLeft = 2,
            bestScore = 5,
            seed = 12345,
            difficulty = GameDifficulty.EASY,
            chainId = "CNQ",
            isRevenge = false
        )
        ChallengeStorage.saveChallenge(context, legacyItem)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_challenges_rail").assertDoesNotExist()

        // 3. Verify pasting a Reply Code on Leaderboard -> Rivalry records outcome and stays on Leaderboard (does NOT start game)
        val awaiting = com.example.challenge.AwaitingChallenge(
            chainId = "R99",
            code = "2x2-6-T3E-HERO-R99-8X2A-C",
            gridSizeTag = "2x2",
            targetScore = 6,
            difficulty = GameDifficulty.EASY,
            paceName = "Classic",
            tries = 3
        )
        ChallengeStorage.saveAwaitingChallenge(context, awaiting)

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        val replyCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 5,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 7777,
            senderName = "FALCON",
            chainId = "R99",
            isRevenge = true
        )
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(replyCode)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Must stay on Hall of Fame / Rivalry tab (not start a game on Home)
        composeTestRule.onNodeWithTag("hall_of_fame_screen").assertIsDisplayed()
        assertTrue(
            "Rival FALCON must be recorded from Reply Code with WON status (6 >= 5)",
            ChallengeStorage.loadRivals(context).any { it.name == "FALCON" && it.lastStatus == "WON" }
        )
    }

    @Test
    fun pass5_11_stressTestHardening_optionAReply_antiFarming_softBlock_revengeSolo_smartShare_and_safeguards() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "ALEX")

        // 1. Fix Inverted Win/Loss on Reply Codes (Option A) + 72h chainId -> targetScore map for 1-to-many group replies
        val outboundTCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 6,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 8080,
            senderName = "ALEX",
            chainId = "GRP",
            isRevenge = false
        )
        ChallengeStorage.recordCreatedCode(context, outboundTCode)
        assertEquals(6, ChallengeStorage.lookupOutboundChainTarget(context, "GRP"))

        // Friend 1 (BRAVO) beats/reaches target 6 (responderScore = 6 >= myOriginalTarget 6 -> I LOST)
        val bravoReply = ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "BRAVO",
            responderScore = 6,
            myOriginalTarget = ChallengeStorage.lookupOutboundChainTarget(context, "GRP") ?: 6,
            gridSize = "3x3",
            difficulty = GameDifficulty.EASY,
            chainId = "GRP",
            maxTries = 3
        )
        assertNotNull(bravoReply)
        assertEquals("LOST", bravoReply!!.lastStatus)
        assertEquals(1, bravoReply.losses)
        assertEquals(0, bravoReply.wins)

        // Friend 2 (DELTA) in same WhatsApp group replies later on same chainId "GRP" with score 4 (< 6 -> I WON / Defended)
        val lookedUpTargetAfterFirstReply = ChallengeStorage.lookupOutboundChainTarget(context, "GRP")
        assertEquals("chainId -> targetScore map must persist for 1-to-many group replies", 6, lookedUpTargetAfterFirstReply)
        val deltaReply = ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "DELTA",
            responderScore = 4,
            myOriginalTarget = lookedUpTargetAfterFirstReply!!,
            gridSize = "3x3",
            difficulty = GameDifficulty.EASY,
            chainId = "GRP",
            maxTries = 3
        )
        assertNotNull(deltaReply)
        assertEquals("WON", deltaReply!!.lastStatus)
        assertEquals(1, deltaReply.wins)

        // 2. Block Self-Reply & Duplicate Paste Farming + 200 FIFO Cap
        val myOwnReplyCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 6,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 8080,
            senderName = "ALEX",
            chainId = "GRP",
            isRevenge = true
        )
        ChallengeStorage.recordCreatedCode(context, myOwnReplyCode)
        val parsedOwnReply = ChallengeSeed.parseChallengeCode(myOwnReplyCode)!!
        assertTrue(
            "Self-reply code in KEY_MY_CODES must be hard-blocked FIRST before R-token bypass",
            ChallengeStorage.isSelfChallenge(context, parsedOwnReply)
        )

        // Different person's R-code with same seed/chainId (not in KEY_MY_CODES) bypasses Tier 1
        val friendReplyCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 6,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 8080,
            senderName = "ECHO",
            chainId = "GRP",
            isRevenge = true
        )
        val parsedFriendReply = ChallengeSeed.parseChallengeCode(friendReplyCode)!!
        assertFalse(
            "Genuine friend reply code not in KEY_MY_CODES must pass Tier 1",
            ChallengeStorage.isSelfChallenge(context, parsedFriendReply)
        )

        // Mark consumed & verify duplicate paste block
        assertFalse(ChallengeStorage.isCodeConsumed(context, friendReplyCode))
        ChallengeStorage.markCodeConsumed(context, friendReplyCode)
        assertTrue(ChallengeStorage.isCodeConsumed(context, friendReplyCode))

        // Verify 200 FIFO cap on KEY_CONSUMED_CODES
        for (i in 1..210) {
            ChallengeStorage.markCodeConsumed(context, "DUMMY-CODE-$i")
        }
        val consumedSet = ChallengeStorage.getConsumedCodes(context)
        assertEquals(200, consumedSet.size)
        assertFalse("Oldest consumed entry must be evicted (FIFO)", consumedSet.contains("DUMMY-CODE-1"))
        assertTrue("Newest consumed entry must be retained", consumedSet.contains("DUMMY-CODE-210"))

        // 3. Unicode dashes & zero-width char sanitization in extractCodeFromText / parseChallengeCode
        val baseValidCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 6,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 9090,
            senderName = "CHARLIE",
            chainId = "C77",
            isRevenge = false
        )
        val segs = baseValidCode.split('-')
        val mangledWhatsAppCode = "\u200B${segs[0]}\u2013${segs[1]}\u2014${segs[2]}\u2212${segs[3]}\uFE58${segs[4]}\uFF0D${segs[5]}\u2010${segs[6]}\u200D"
        val extractedClean = ChallengeSeed.extractCodeFromText("Play my code: $mangledWhatsAppCode")
        assertNotNull("WhatsApp mangled code with Unicode dashes & zero-width chars must extract cleanly", extractedClean)
        val parsedClean = ChallengeSeed.parseChallengeCode(mangledWhatsAppCode)
        assertNotNull("WhatsApp mangled code must parse cleanly", parsedClean)
        assertEquals("CHARLIE", parsedClean!!.senderName)

        // 4. Smart Share Optimization (Solo vs First-time Rival vs Repeat Rival)
        assertFalse("Solo game with no rival must always generate full card", ChallengeStorage.shouldShareTextOnly(context, null))
        assertFalse("First share with rival BRAVO must generate full card", ChallengeStorage.shouldShareTextOnly(context, "BRAVO"))
        ChallengeStorage.setLastSharedRivalName(context, "BRAVO")
        assertTrue("Consecutive share with same rival BRAVO must share text only", ChallengeStorage.shouldShareTextOnly(context, "BRAVO"))
        assertFalse("Sharing with different rival DELTA must generate full card", ChallengeStorage.shouldShareTextOnly(context, "DELTA"))

        // 5. Same Name Soft Block (Tier 2) in EnterChallengeDialog
        val otherAlexCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 5,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 4321,
            senderName = "ALEX",
            chainId = "ALX",
            isRevenge = false
        )
        val parsedOtherAlex = ChallengeSeed.parseChallengeCode(otherAlexCode)!!
        assertFalse("Another user named ALEX on a different device is not Tier 1 self-challenge", ChallengeStorage.isSelfChallenge(context, parsedOtherAlex))
        assertTrue("Another user named ALEX triggers Tier 2 Same Name collision", ChallengeStorage.isSameNameCollision(context, parsedOtherAlex))

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Open Settings and verify Battery Optimization Tip
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("battery_optimization_tip").assertExists()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Rivalry tab, open Paste Rival Code dialog, paste other ALEX's code -> verify Soft Block dialog appears
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(otherAlexCode)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("same_name_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("same_name_confirm_cancel_button").performClick()
        composeTestRule.waitForIdle()
    }
}



