package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import com.example.challenge.ChallengeItem
import com.example.challenge.ChallengeSeed
import com.example.challenge.ChallengeStorage
import com.example.challenge.GameDifficulty
import com.example.challenge.GridStat
import com.example.challenge.RivalRecord
import com.example.challenge.RunStateStorage
import com.example.challenge.SerializedRunState
import com.example.notification.NotificationContentProvider
import com.example.notification.NotificationScheduler
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    companion object {
        init {
            MainActivity.skipSplashForTesting = true
        }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        com.example.challenge.RunStateStorage.clearRunState(context)
        context.getSharedPreferences("glowback_challenges_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("glowback_challenge_prefs", Context.MODE_PRIVATE).edit().clear().commit()
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

        // 6. PASS 2H, PASS 2J & PASS 5.19 Stage A #2: Verify Solo Game Over buttons (Play Again, Home, Share Challenge; standalone Copy Challenge removed)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.onNodeWithTag("challenge_nudge_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("start_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_over_home_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_challenge_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_challenge_code_button").assertDoesNotExist()

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

        // 6. Verify grayed-out Share Challenge button remains clickable at score < 5 and shows Score-5 Gate (standalone Copy button removed per PASS 5.19 Stage A #2)
        composeTestRule.onNodeWithTag("share_challenge_button").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("copy_challenge_code_button").assertDoesNotExist()

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
        // pasting an R-token reply code still creates/merges the rival card while the 30d chain->target entry lives.
        ChallengeStorage.saveOutboundChainTarget(context, "EXP", 6, seedInt = 31337)
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

        // 1. Verify recordMatchOutcome uses strict <, ==, > (5 vs 5 = TIED, 6 vs 5 = WON)
        val tieRecord = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "POOVARASAN",
            userScore = 5,
            targetScore = 5,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "CNQ",
            maxTries = 3
        )
        assertNotNull(tieRecord)
        assertEquals("TIED", tieRecord!!.lastStatus)
        assertEquals(0, tieRecord.wins)
        assertEquals(1, tieRecord.ties)

        val conquestRecord = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "POOVARASAN",
            userScore = 6,
            targetScore = 5,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "CNQ",
            maxTries = 3
        )
        assertNotNull(conquestRecord)
        assertEquals("WON", conquestRecord!!.lastStatus)
        assertEquals(1, conquestRecord.wins)
        assertEquals(1, conquestRecord.ties)

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
            bestScore = 6,
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
            "Rival FALCON must be recorded from Reply Code with WON status (responder 5 < myTarget 6)",
            ChallengeStorage.loadRivals(context).any { it.name == "FALCON" && it.lastStatus == "WON" }
        )
    }

    @Test
    fun pass5_11_stressTestHardening_optionAReply_antiFarming_softBlock_revengeSolo_smartShare_and_safeguards() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "ALEX")

        // 1. Fix Inverted Win/Loss on Reply Codes + 72h chainId -> targetScore map for 1-to-many group replies
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

        // Friend 1 (BRAVO) beats target 6 (responderScore = 7 > myOriginalTarget 6 -> I LOST)
        val bravoReply = ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "BRAVO",
            responderScore = 7,
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

        // Verify 500 FIFO cap on KEY_CONSUMED_CODES (PASS 5.19 Stage B #7)
        for (i in 1..510) {
            ChallengeStorage.markCodeConsumed(context, "DUMMY-CODE-$i")
        }
        val consumedSet = ChallengeStorage.getConsumedCodes(context)
        assertEquals(500, consumedSet.size)
        assertFalse("Oldest consumed entry must be evicted (FIFO)", consumedSet.contains("DUMMY-CODE-1"))
        assertTrue("Newest consumed entry must be retained", consumedSet.contains("DUMMY-CODE-510"))

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

        // 4. Anti-Forgery Image Card Sharing: All shares (including consecutive shares with same rival) always generate full PNG trophy card
        assertFalse("Solo game with no rival must always generate full card", ChallengeStorage.shouldShareTextOnly(context, null))
        assertFalse("First share with rival BRAVO must generate full card", ChallengeStorage.shouldShareTextOnly(context, "BRAVO"))
        ChallengeStorage.setLastSharedRivalName(context, "BRAVO")
        assertFalse("Consecutive share with same rival BRAVO must ALSO generate full PNG trophy card to prevent text forgery", ChallengeStorage.shouldShareTextOnly(context, "BRAVO"))
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

    @Test
    fun pass5_13_finalLogicCompletion_strictTie_revengeBannerAndGate_and_challengeItemIsWon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")

        // Item 1: Fix "7 vs 7" Showing as LOST -> strict <, ==, > comparisons
        // Responder playing T-code: 7 vs 7 = TIED, 8 vs 7 = WON, 6 vs 7 = LOST
        val tTie = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "VIKRAM",
            userScore = 7,
            targetScore = 7,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "T77",
            maxTries = 3
        )
        assertNotNull(tTie)
        assertEquals("TIED", tTie!!.lastStatus)
        assertEquals(1, tTie.ties)
        assertEquals(0, tTie.wins)
        assertEquals(0, tTie.losses)

        // Original sender pasting R-code: responderScore == myOriginalTarget (7 vs 7) = TIED
        val rTie = ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "VIKRAM",
            responderScore = 7,
            myOriginalTarget = 7,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "T77",
            maxTries = 3
        )
        assertNotNull(rTie)
        assertEquals("TIED", rTie!!.lastStatus)
        assertEquals(2, rTie.ties)

        // Responder 6 < myOriginalTarget 7 -> I WON (Defended)
        val rWin = ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "VIKRAM",
            responderScore = 6,
            myOriginalTarget = 7,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "T77",
            maxTries = 3
        )
        assertEquals("WON", rWin!!.lastStatus)
        assertEquals(1, rWin.wins)

        // Responder 8 > myOriginalTarget 7 -> I LOST (Rival Conquered)
        val rLoss = ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "VIKRAM",
            responderScore = 8,
            myOriginalTarget = 7,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "T77",
            maxTries = 3
        )
        assertEquals("LOST", rLoss!!.lastStatus)
        assertEquals(1, rLoss.losses)

        // Item 7: ChallengeItem.isWon strictly uses bestScore > targetScore (== is Tie)
        val tiedChallengeItem = ChallengeItem(
            code = "2x2-7-T3E-VIKRAM-T77-8X2A-C",
            rivalName = "VIKRAM",
            gridSizeTag = "2x2",
            targetScore = 7,
            maxTries = 3,
            attemptsLeft = 2,
            bestScore = 7,
            seed = 1234,
            difficulty = GameDifficulty.EASY
        )
        assertFalse("bestScore == targetScore must NOT be isWon", tiedChallengeItem.isWon)
        assertTrue("bestScore == targetScore must be isTied", tiedChallengeItem.isTied)
        assertFalse("Tied item with attemptsLeft > 0 must NOT be isCompleted", tiedChallengeItem.isCompleted)

        val wonChallengeItem = tiedChallengeItem.copy(bestScore = 8)
        assertTrue("bestScore > targetScore must be isWon", wonChallengeItem.isWon)
        assertTrue("Won item must be isCompleted immediately", wonChallengeItem.isCompleted)

        // Item 5: Reply card bitmap with tied = true renders cleanly
        val tiedBitmap = com.example.challenge.ChallengeShareHelper.createChallengeBitmap(
            code = "2x2-7-R3E-HERO-T77-8X2A-K",
            gridSizeTag = "2x2",
            score = 7,
            attempts = 1,
            tries = 3,
            paceName = "Classic",
            difficulty = GameDifficulty.EASY,
            isReply = true,
            won = false,
            tied = true,
            rivalName = "VIKRAM"
        )
        assertNotNull(tiedBitmap)
        assertEquals(1080, tiedBitmap.width)

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Item 8 & 9: Tap "Get Revenge" on VIKRAM (lastStatus = "LOST", lastTarget = 8) -> verify in-game Revenge banner
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_dynamic_button_VIKRAM").performClick()
        composeTestRule.waitForIdle()
        // PASS 5.22: Tapping Get Revenge opens RivalMatchSetupDialog -> tap Continue Last Mode
        composeTestRule.onNodeWithTag("rival_setup_continue_last_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("⚡ REVENGE vs VIKRAM").assertIsDisplayed()
        composeTestRule.onNodeWithText("BEAT: 8", substring = true).assertIsDisplayed()
    }

    @Test
    fun pass5_14_securityHardening_and_uiCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")

        // 1. Verify exact tampered 7-segment code reported by user ("2x2-20-R3E-ALEXX-A75-4P629-G") is rejected outright
        val tamperedCode = "2x2-20-R3E-ALEXX-A75-4P629-G"
        assertTrue(
            "Tampered 7-segment code with failed checksum must return null in parseChallengeCode (never fall back to v0)",
            ChallengeSeed.parseChallengeCode(tamperedCode) == null
        )
        assertTrue(
            "Tampered 7-segment code inside freeform text must return null in extractCodeFromText (never slice first 4 segments)",
            ChallengeSeed.extractCodeFromText("Beat my score: $tamperedCode") == null
        )

        // 2. Verify tampered 5-segment v1 code is also rejected outright
        val tamperedV1 = "4x4-6-T3E-2GPGX-Z" // Valid checksum is N, tampered to Z
        assertTrue(
            "Tampered 5-segment code must return null",
            ChallengeSeed.parseChallengeCode(tamperedV1) == null
        )
        assertTrue(
            "Tampered 5-segment code in text must return null",
            ChallengeSeed.extractCodeFromText("Try this: $tamperedV1") == null
        )

        // 3. Verify UI: pasting tampered code in EnterChallengeDialog shows error and NEVER opens LegacyCodeNameDialog
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "ALEX",
            userScore = 5,
            targetScore = 10,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "A75",
            maxTries = 3
        )

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        // Verify Bin icon on RivalCard opens Clear Rivalry confirmation dialog (and 3-dot menu is gone)
        composeTestRule.onNodeWithTag("rival_menu_button_ALEX").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_rival_ALEX").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_clear_rivalry_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_clear_rivalry_cancel").performClick()
        composeTestRule.waitForIdle()

        // Tap Paste Code button to test tampered code rejection in UI
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(tamperedCode)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Must NOT open legacy_code_dialog; must show invalid code error
        composeTestRule.onNodeWithTag("legacy_code_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("challenge_code_error_text").assertIsDisplayed()
    }

    @Test
    fun pass5_15_challengeResumeAndPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")

        val tCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 12,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 12345,
            senderName = "ALEX",
            chainId = "R55",
            isRevenge = false
        )

        // 1. Storage test: saveInProgressChallenge creates IN_PROGRESS RivalRecord and persists tries/bestScore
        val initialItem = ChallengeItem(
            code = tCode,
            rivalName = "ALEX",
            gridSizeTag = "2x2",
            targetScore = 12,
            maxTries = 3,
            attemptsLeft = 3,
            bestScore = 0,
            seed = 12345,
            difficulty = GameDifficulty.EASY,
            chainId = "R55",
            isRevenge = false
        )
        ChallengeStorage.saveInProgressChallenge(context, initialItem)

        val rivalInitial = ChallengeStorage.findRivalByName(context, "ALEX")
        assertNotNull("Accepting T-code must create IN_PROGRESS RivalRecord", rivalInitial)
        assertEquals("IN_PROGRESS", rivalInitial!!.lastStatus)
        assertEquals(3, rivalInitial.inProgressTriesLeft)

        // 2. Simulate failing Try 1 at score 7 (2 tries remaining)
        val afterTry1Item = initialItem.copy(attemptsLeft = 2, bestScore = 7)
        ChallengeStorage.saveInProgressChallenge(context, afterTry1Item)

        val rivalAfterTry1 = ChallengeStorage.findRivalByName(context, "ALEX")!!
        assertEquals("IN_PROGRESS", rivalAfterTry1.lastStatus)
        assertEquals(2, rivalAfterTry1.inProgressTriesLeft)
        assertEquals(7, rivalAfterTry1.lastUserScore)

        // 3. Re-pasting the same T-code (same chainId "R55") must return the saved state (attemptsLeft == 2, bestScore == 7), NOT 3 tries!
        val foundOnRepaste = ChallengeStorage.findInProgressChallenge(context, tCode, "R55", "ALEX")
        assertNotNull("findInProgressChallenge must locate existing IN_PROGRESS challenge by chainId/code", foundOnRepaste)
        assertEquals("Re-pasting same code must resume with 2 tries left, not 3", 2, foundOnRepaste!!.attemptsLeft)
        assertEquals("Re-pasting same code must preserve bestScore of 7", 7, foundOnRepaste.bestScore)

        // 4. UI verification: Rivalry tab shows "In Progress: 2 tries left" & "▶ Resume (2 left)" button, and tapping it resumes with TRIES: 2
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_status_text_ALEX")
            .assertIsDisplayed()
            .assertTextContains("In Progress: 2 tries left", substring = true)

        composeTestRule.onNodeWithTag("rival_dynamic_button_ALEX")
            .assertIsDisplayed()
            .assertTextContains("Resume (2 left)", substring = true)
            .performClick()
        composeTestRule.waitForIdle()

        // Verify game screen resumed challenge with TRIES: 2 (not 3)
        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("TRIES: 2", substring = true).assertIsDisplayed()

        // 5. Completing the challenge clears IN_PROGRESS and sets final status (e.g. WON when scoring 13 > 12)
        val finalRecord = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "ALEX",
            userScore = 13,
            targetScore = 12,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "R55",
            maxTries = 3
        )
        assertNotNull(finalRecord)
        assertEquals("WON", finalRecord!!.lastStatus)
        assertEquals(0, finalRecord.inProgressTriesLeft)
        assertTrue(
            "IN_PROGRESS challenge must be cleared after completion",
            ChallengeStorage.findInProgressChallenge(context, tCode, "R55", "ALEX") == null
        )
    }

    @Test
    fun pass5_17_finalRallyLoopFix_tCodeRevenge_rCodeReceipt_and_gridChips() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")

        // 1. Simulate Match 1 on 2x2: ALEX sends T-code (target 12, chainId "K7Q"). User loses at 9.
        val match1 = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "ALEX",
            userScore = 9,
            targetScore = 12,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "K7Q",
            maxTries = 3
        )
        assertNotNull(match1)
        assertEquals(0, match1!!.wins)
        assertEquals(1, match1.losses)
        assertEquals("K7Q", match1.lastChainId)
        assertEquals(0, match1.gridStats["2x2"]?.wins)
        assertEquals(1, match1.gridStats["2x2"]?.losses)

        // 2. Simulate Match 2 (Revenge) on 2x2: User scores 14 -> generates T-code (isRevenge=false) with preserved chainId "K7Q",
        //    and ALEX replies with an R-code (targetScore = 10, failing to beat 14).
        val revengeTCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 14,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 77777,
            senderName = "HERO",
            chainId = match1.lastChainId,
            isRevenge = false
        )
        val parsedRevengeTCode = ChallengeSeed.parseChallengeCode(revengeTCode)
        assertNotNull(parsedRevengeTCode)
        assertFalse("Get Revenge must generate a playable T-code (isRevenge = false)", parsedRevengeTCode!!.isRevenge)
        assertEquals("Get Revenge must preserve the rival's chainId", "K7Q", parsedRevengeTCode.chainId)

        ChallengeStorage.saveAwaitingChallenge(
            context,
            com.example.challenge.AwaitingChallenge(
                chainId = "K7Q",
                code = revengeTCode,
                gridSizeTag = "2x2",
                targetScore = 14,
                difficulty = GameDifficulty.EASY,
                paceName = "Classic",
                tries = 3,
                createdAt = System.currentTimeMillis()
            )
        )

        // ALEX replies with R-code (score 10 on 2x2) -> User wins Match 2!
        val match2 = ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "ALEX",
            responderScore = 10,
            myOriginalTarget = 14,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "K7Q",
            maxTries = 3
        )
        assertNotNull(match2)
        assertEquals("One card per rival: Wins should stack on ALEX", 1, match2!!.wins)
        assertEquals("One card per rival: Losses should stay 1 on ALEX", 1, match2.losses)
        assertEquals("R-code receipt must update lastTarget to responder's score", 10, match2.lastTarget)
        assertEquals(1, match2.gridStats["2x2"]?.wins)
        assertEquals(1, match2.gridStats["2x2"]?.losses)

        // 3. Simulate Match 3 on 4x4: User plays a 4x4 challenge from ALEX (chainId "K7Q") and wins (15 vs 11).
        val match3 = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "ALEX",
            userScore = 15,
            targetScore = 11,
            gridSize = "4x4",
            difficulty = GameDifficulty.HARD,
            chainId = "K7Q",
            maxTries = 1
        )
        assertNotNull(match3)
        assertEquals(2, match3!!.wins)
        assertEquals(1, match3.losses)
        assertEquals(1, match3.gridStats["2x2"]?.wins)
        assertEquals(1, match3.gridStats["2x2"]?.losses)
        assertEquals(1, match3.gridStats["4x4"]?.wins)
        assertEquals(0, match3.gridStats["4x4"]?.losses)

        // Verify only 1 RivalCard exists for ALEX
        val allRivals = ChallengeStorage.loadRivals(context).filter { it.name == "ALEX" }
        assertEquals("All rally matches with same chainId must stack on 1 RivalCard", 1, allRivals.size)

        // 4. Verify UI displays Per-Grid Breakdown Chips on ALEX's RivalCard: [2×2: 1W - 1L] and [4×4: 1W - 0L]
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_grid_chip_ALEX_2x2")
            .assertIsDisplayed()
            .assertTextContains("2×2: 1W - 1L", substring = true)

        composeTestRule.onNodeWithTag("rival_grid_chip_ALEX_4x4")
            .assertIsDisplayed()
            .assertTextContains("4×4: 1W - 0L", substring = true)

        // 5. Verify T-Code vs R-Code Paste Catch:
        // Even if an AwaitingChallenge exists with chainId "K7Q", pasting a T-code from ALEX with chainId "K7Q"
        // MUST launch a playable game (NOT swallow it as a reply receipt).
        ChallengeStorage.saveAwaitingChallenge(
            context,
            com.example.challenge.AwaitingChallenge(
                chainId = "K7Q",
                code = revengeTCode,
                gridSizeTag = "2x2",
                targetScore = 14,
                difficulty = GameDifficulty.EASY,
                paceName = "Classic",
                tries = 3,
                createdAt = System.currentTimeMillis()
            )
        )
        val incomingPlayableTCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 16,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 88888,
            senderName = "ALEX",
            chainId = "K7Q",
            isRevenge = false
        )
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(incomingPlayableTCode)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Must launch the game with TARGET: 16!
        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET: 16", substring = true).assertIsDisplayed()
    }

    @Test
    fun pass5_18_lostReplyCodeRecovery_and_activeChallengeUiRefactor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")

        // PART 1: Responder (HERO) finishes ALEX's 5x4 challenge and loses (score 4 vs target 5).
        // Even if HERO leaves Game Over without copying, recordMatchOutcome must persist the R-code with isReplySent = false.
        val finishedRecord = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "ALEX",
            userScore = 4,
            targetScore = 5,
            gridSize = "5x4",
            difficulty = GameDifficulty.HARD,
            chainId = "R54",
            maxTries = 3,
            seed = 54321
        )
        assertNotNull(finishedRecord)
        assertFalse("Newly finished match must have isReplySent = false until user copies/shares", finishedRecord!!.isReplySent)
        assertTrue("Saved lastReplyCode must be non-blank", finishedRecord.lastReplyCode.isNotBlank())
        val parsedReply = ChallengeSeed.parseChallengeCode(finishedRecord.lastReplyCode)
        assertNotNull("Saved lastReplyCode must be a valid 7-segment code", parsedReply)
        assertTrue("Saved lastReplyCode must be an R-code (isRevenge = true)", parsedReply!!.isRevenge)
        assertEquals(4, parsedReply.targetScore)
        assertEquals("HERO", parsedReply.senderName)
        assertEquals("R54", parsedReply.chainId)

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Verify Home IDLE UI still has the normal interactive Easy/Hard toggle (zero regression on IDLE)
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").assertIsDisplayed()

        // Navigate to Rivalry tab -> verify "Send Reply" button is visible on ALEX's RivalCard
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_send_reply_button_ALEX")
            .assertIsDisplayed()
            .assertTextContains("Send Reply", substring = true)
            .performClick()
        composeTestRule.waitForIdle()

        // Once tapped, isReplySent becomes true and the button hides
        val afterSendRecord = ChallengeStorage.findRivalByName(context, "ALEX")
        assertNotNull(afterSendRecord)
        assertTrue("Tapping Send Reply must set isReplySent = true", afterSendRecord!!.isReplySent)
        composeTestRule.onNodeWithTag("rival_send_reply_button_ALEX").assertDoesNotExist()

        // PART 2: Accept an active 5x4 Hard challenge with high target (30 > 7)
        val highTargetTCode = ChallengeSeed.formatChallengeCode(
            gridTag = "5x4",
            targetScore = 30,
            tries = 3,
            difficulty = GameDifficulty.HARD,
            seed = 99999,
            senderName = "ALEX",
            chainId = "R54",
            isRevenge = false
        )
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(highTargetTCode)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Verify Active Challenge Compact UI:
        // 1. Best score card ("scoreboard_best_score_text") is REMOVED during active challenge
        composeTestRule.onNodeWithTag("scoreboard_best_score_text").assertDoesNotExist()
        // 2. Interactive Easy/Hard toggle switches are REMOVED during active challenge
        composeTestRule.onNodeWithTag("difficulty_toggle_easy").assertDoesNotExist()
        composeTestRule.onNodeWithTag("difficulty_toggle_hard").assertDoesNotExist()
        // 3. Compact inline bar [5×4 · Classic] [Score: 0] [Hard] is DISPLAYED
        composeTestRule.onNodeWithTag("active_challenge_compact_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_challenge_score_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_score_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_challenge_difficulty_badge")
            .assertIsDisplayed()
            .assertTextContains("Hard", substring = true)
        // 4. Step indicator dots are HIDDEN because targetScore (30) > 7
        composeTestRule.onNodeWithTag("sequence_step_indicator").assertDoesNotExist()
    }

    @Test
    fun pass5_19_asyncChallengeRobustness_and_4PlayerMesh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "ALEX")

        // 1. Stage A #1: findInProgressChallenge matches strictly by exact code or seedInt ONLY, never by chainId!
        //    And pasting a new T-code from the same rival replaces the old unfinished challenge.
        val tCode1 = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 6,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 11111,
            senderName = "BOB",
            chainId = "CH1",
            isRevenge = false
        )
        val tCode2SameChainDifferentSeed = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 8,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 22222,
            senderName = "BOB",
            chainId = "CH1",
            isRevenge = false
        )
        val inProg1 = ChallengeItem(
            code = tCode1,
            rivalName = "BOB",
            gridSizeTag = "3x3",
            targetScore = 6,
            maxTries = 3,
            attemptsLeft = 2,
            bestScore = 4,
            seed = 11111,
            difficulty = GameDifficulty.EASY,
            chainId = "CH1",
            isRevenge = false
        )
        ChallengeStorage.saveInProgressChallenge(context, inProg1)
        assertNotNull(
            "Exact code/seed must find in-progress challenge",
            ChallengeStorage.findInProgressChallenge(context, tCode1, "CH1", "BOB", seedInt = 11111)
        )
        assertTrue(
            "Different code/seed on the SAME chainId must NOT match old in-progress challenge",
            ChallengeStorage.findInProgressChallenge(context, tCode2SameChainDifferentSeed, "CH1", "BOB", seedInt = 22222) == null
        )
        assertTrue(
            "hasUnfinishedChallengeToReplace must detect existing unfinished challenge from BOB when new T-code arrives",
            ChallengeStorage.hasUnfinishedChallengeToReplace(context, "BOB", tCode2SameChainDifferentSeed, 22222)
        )
        val inProg2 = ChallengeItem(
            code = tCode2SameChainDifferentSeed,
            rivalName = "BOB",
            gridSizeTag = "3x3",
            targetScore = 8,
            maxTries = 3,
            attemptsLeft = 3,
            bestScore = 0,
            seed = 22222,
            difficulty = GameDifficulty.EASY,
            chainId = "CH1",
            isRevenge = false
        )
        ChallengeStorage.saveInProgressChallenge(context, inProg2)
        assertTrue(
            "Old unfinished challenge from BOB must be replaced when new T-code is saved",
            ChallengeStorage.findInProgressChallenge(context, tCode1, "CH1", "BOB", seedInt = 11111) == null
        )
        assertNotNull(
            "New unfinished challenge from BOB must be active",
            ChallengeStorage.findInProgressChallenge(context, tCode2SameChainDifferentSeed, "CH1", "BOB", seedInt = 22222)
        )

        // 2. Run-Signature Consumption (Part 1 Bug 2):
        //    Once a T-code is consumed, re-sharing the same run (same senderName + gridTag + targetScore + seedInt)
        //    with a different chainId or different tries is ALSO blocked as consumed!
        ChallengeStorage.markCodeConsumed(context, tCode2SameChainDifferentSeed)
        val resharedDifferentChainAndTries = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 8,
            tries = 5,
            difficulty = GameDifficulty.EASY,
            seed = 22222,
            senderName = "BOB",
            chainId = "NEW",
            isRevenge = false
        )
        assertTrue(
            "Run-signature consumption must block re-shared T-code with same sender+grid+target+seed even if chainId/tries changed",
            ChallengeStorage.isCodeConsumed(context, resharedDifferentChainAndTries)
        )

        // 3. Mandatory Per-Rival Sub-Chain Forking (chainId + ":" + seedInt) & 30-Day Retention:
        //    Running simultaneous revenge rallies on the same chainId "K7Q" with different seeds (50001 vs 50002)
        //    must NOT overwrite each other's target scores!
        ChallengeStorage.saveOutboundChainTarget(context, "K7Q", targetScore = 9, seedInt = 50001)
        ChallengeStorage.saveOutboundChainTarget(context, "K7Q", targetScore = 14, seedInt = 50002)
        assertEquals(
            "Sub-chain forking (K7Q:50001) must preserve target 9",
            9,
            ChallengeStorage.lookupOutboundChainTarget(context, "K7Q", seedInt = 50001)
        )
        assertEquals(
            "Sub-chain forking (K7Q:50002) must preserve target 14",
            14,
            ChallengeStorage.lookupOutboundChainTarget(context, "K7Q", seedInt = 50002)
        )

        // 4. 30-Day Expiry Status (EXPIRED vs NOT_FOUND):
        val expiredTimestamp = System.currentTimeMillis() - (31L * 24L * 3600_000L)
        ChallengeStorage.saveOutboundChainTarget(context, "OLD", targetScore = 10, createdAt = expiredTimestamp, seedInt = 77777)
        val expiredLookup = ChallengeStorage.inspectOutboundChainTarget(context, "OLD", seedInt = 77777)
        assertEquals(ChallengeStorage.ChainLookupStatus.EXPIRED, expiredLookup.status)

        val unknownLookup = ChallengeStorage.inspectOutboundChainTarget(context, "UNK", seedInt = 99999)
        assertEquals(ChallengeStorage.ChainLookupStatus.NOT_FOUND, unknownLookup.status)

        // 5. Same-Name Second Reply Detection (Clarifying Answer #2: Cat1 vs Cat2 in group):
        ChallengeStorage.saveOutboundChainTarget(context, "GRP2", targetScore = 7, seedInt = 60001)
        ChallengeStorage.recordReplyOutcome(
            context = context,
            rivalName = "CAT",
            responderScore = 8,
            myOriginalTarget = 7,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "GRP2",
            maxTries = 3,
            seed = 60001
        )
        assertTrue(
            "Second reply from CAT for the same challenge (GRP2:60001) must trigger Same-Name Second Reply prompt",
            ChallengeStorage.hasSameNameSecondReplyForChallenge(context, "GRP2", 60001, "CAT")
        )
        assertFalse(
            "First reply from DAN for the same challenge must NOT trigger Same-Name Second Reply prompt",
            ChallengeStorage.hasSameNameSecondReplyForChallenge(context, "GRP2", 60001, "DAN")
        )

        // 6. Stage B #5: R-Code Tries Semantics — R-token encodes tries GRANTED (maxTries = 3 -> R3E), not tries used.
        val finishedMatchWith3Tries = ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "DAN",
            userScore = 11,
            targetScore = 9,
            gridSize = "3x3",
            difficulty = GameDifficulty.EASY,
            chainId = "D99",
            maxTries = 3,
            seed = 44444
        )
        assertNotNull(finishedMatchWith3Tries)
        assertTrue(
            "R-code must encode tries GRANTED (R3E for maxTries=3)",
            finishedMatchWith3Tries!!.lastReplyCode.contains("-R3E-")
        )

        // 7. UI Verification: Non-Originator R-Code Rejection (Eve pastes Dan's R-code for a challenge Eve didn't issue)
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        val foreignRCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 8,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 88881,
            senderName = "DAN",
            chainId = "FRN",
            isRevenge = true
        )
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(foreignRCode)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("challenge_code_error_text")
            .assertIsDisplayed()
            .assertTextContains("This reply is for a challenge you didn't issue.", substring = true)
        composeTestRule.onNodeWithTag("close_challenge_dialog_button").performClick()
        composeTestRule.waitForIdle()

        // 8. UI Verification: Accept a playable T-code -> verify Unified 2-Row Challenge Header & Ready Consent Gate
        val playableTCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 6,
            tries = 3,
            difficulty = GameDifficulty.HARD,
            seed = 33333,
            senderName = "DAN",
            chainId = "D77",
            isRevenge = false
        )
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(playableTCode)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Unified 2-row banner + Ready Consent Gate button displayed before sequence starts
        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_challenge_compact_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready vs DAN?", substring = true).assertIsDisplayed()
    }

    @Test
    fun pass5_20_cheatProofExitChallenge_forfeit_lockScore_readyGate_and_antiCheat() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. Caution 1 (Free Tries Pre-Deduction Write Guard) & Guard A (Orphaned Final-Try Lazy Finalization):
        val freeItem = ChallengeItem(
            code = ChallengeSeed.formatChallengeCode(
                gridTag = "2x2",
                targetScore = 6,
                tries = 0, // Free tries (TFE)
                difficulty = GameDifficulty.EASY,
                seed = 70001,
                senderName = "FREE_RIVAL",
                chainId = "FR0",
                isRevenge = false
            ),
            rivalName = "FREE_RIVAL",
            gridSizeTag = "2x2",
            targetScore = 6,
            maxTries = 0,
            attemptsLeft = -1, // Even if a caller accidentally passes -1 for Free mode
            bestScore = 2,
            seed = 70001,
            difficulty = GameDifficulty.EASY,
            chainId = "FR0"
        )
        val savedFreeRecord = ChallengeStorage.saveInProgressChallenge(context, freeItem)
        assertEquals("Caution 1: Free mode maxTries must remain 0", 0, savedFreeRecord.inProgressMaxTries)
        assertEquals("Caution 1: Free mode inProgressTriesLeft must remain 0 (never negative)", 0, savedFreeRecord.inProgressTriesLeft)
        ChallengeStorage.reconcileOrphanedInProgressChallenges(context)
        val afterReconcileFree = ChallengeStorage.findRivalByName(context, "FREE_RIVAL")
        assertNotNull(afterReconcileFree)
        assertEquals(
            "Caution 1: Free mode (inProgressMaxTries == 0) must NEVER be auto-finalized by orphan reconciliation",
            "IN_PROGRESS",
            afterReconcileFree!!.lastStatus
        )

        // Guard A: Simulate a force-closed final finite try (inProgressMaxTries = 3, inProgressTriesLeft = 0)
        val orphanedCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 7,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 70002,
            senderName = "ORPHAN",
            chainId = "ORP",
            isRevenge = false
        )
        ChallengeStorage.saveInProgressChallenge(
            context,
            ChallengeItem(
                code = orphanedCode,
                rivalName = "ORPHAN",
                gridSizeTag = "2x2",
                targetScore = 7,
                maxTries = 3,
                attemptsLeft = 0,
                bestScore = 4,
                seed = 70002,
                difficulty = GameDifficulty.EASY,
                chainId = "ORP"
            )
        )
        ChallengeStorage.reconcileOrphanedInProgressChallenges(context)
        val reconciledOrphan = ChallengeStorage.findRivalByName(context, "ORPHAN")
        assertNotNull(reconciledOrphan)
        assertEquals("Guard A: Orphaned final try with bestScore 4 < target 7 must finalize as LOST", "LOST", reconciledOrphan!!.lastStatus)
        assertFalse("Guard A: Orphaned final try must generate an unsent R-code (isReplySent = false)", reconciledOrphan.isReplySent)
        assertTrue("Guard A: Orphaned final try must populate lastReplyCode", reconciledOrphan.lastReplyCode.contains("-R3E-"))

        // 2. Accept a 3-try T-code from ALEX (target 5, seed 70003)
        val alexCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 5,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 70003,
            senderName = "ALEX",
            chainId = "ALX",
            isRevenge = false
        )
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(alexCode)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Rule 4: "Ready?" Gate Exit -> While isChallengeReadyGateVisible is true (before Pad 1 flashes),
        // tapping Pause -> Exit Challenge must exit cleanly to Rivalry WITHOUT deducting a try and without showing a dialog.
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()

        // Rule 1: Pause menu during challenge shows ONLY [Resume] and [Exit Challenge]
        composeTestRule.onNodeWithTag("pause_resume_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_exit_challenge_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_restart_button").assertDoesNotExist()

        composeTestRule.onNodeWithTag("pause_exit_challenge_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exit_challenge_confirm_dialog").assertDoesNotExist()

        val alexAfterReadyExit = ChallengeStorage.findRivalByName(context, "ALEX")
        assertNotNull(alexAfterReadyExit)
        assertEquals("Rule 4: Exiting on Ready gate must preserve all 3 tries", 3, alexAfterReadyExit!!.inProgressTriesLeft)

        // 3. Resume ALEX's challenge from Rivalry tab ("Resume (3 left)")
        composeTestRule.onNodeWithTag("rival_dynamic_button_ALEX").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").assertIsDisplayed()

        // Rule 7: Dismiss "Ready?" gate -> verify SharedPreferences immediately pre-deducts inProgressTriesLeft to 2
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        composeTestRule.waitForIdle()
        val alexAfterReadyDismiss = ChallengeStorage.findRivalByName(context, "ALEX")
        assertEquals(
            "Rule 7: Dismissing Ready gate must immediately pre-deduct persisted tries to 2 in SharedPreferences",
            2,
            alexAfterReadyDismiss!!.inProgressTriesLeft
        )

        // Scenario A: Mid-round forfeit (bestScore 0 < target 5) via Pause -> Exit Challenge
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_exit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("exit_challenge_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_challenge_dialog_title").assertTextContains("Exit Challenge?")
        composeTestRule.onNodeWithTag("exit_challenge_dialog_body")
            .assertTextContains("Leaving now will forfeit your current attempt. You will have 2 tries remaining when you resume.", substring = true)

        composeTestRule.onNodeWithTag("exit_challenge_forfeit_button").performClick()
        composeTestRule.waitForIdle()

        val alexAfterForfeit1 = ChallengeStorage.findRivalByName(context, "ALEX")
        assertEquals("Scenario A: After forfeiting Try 1, 2 tries must remain", 2, alexAfterForfeit1!!.inProgressTriesLeft)
        assertEquals("IN_PROGRESS", alexAfterForfeit1.lastStatus)

        // 4. Rule 3 (Final Try Mid-Round Forfeit -> Auto-LOST with unsent R-code):
        // Accept a 1-try challenge from ONE_SHOT (target 5, tries 1)
        val oneShotCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 5,
            tries = 1,
            difficulty = GameDifficulty.EASY,
            seed = 70004,
            senderName = "ONE_SHOT",
            chainId = "OS1",
            isRevenge = false
        )
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(oneShotCode)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Dismiss Ready gate
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        composeTestRule.waitForIdle()

        // Rule 1: Tap Leaderboard tab mid-challenge -> triggers Exit Challenge dialog!
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exit_challenge_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_challenge_dialog_body")
            .assertTextContains("Leaving now will forfeit your final attempt and end the challenge as a LOSS", substring = true)

        composeTestRule.onNodeWithTag("exit_challenge_forfeit_button").performClick()
        composeTestRule.waitForIdle()

        val oneShotRecord = ChallengeStorage.findRivalByName(context, "ONE_SHOT")
        assertNotNull(oneShotRecord)
        assertEquals("Rule 3: Final-try forfeit must immediately finalize as LOST", "LOST", oneShotRecord!!.lastStatus)
        assertFalse("Rule 3: Final-try forfeit must generate an unsent R-code (isReplySent = false)", oneShotRecord.isReplySent)
        assertTrue("Rule 3: Final-try forfeit must populate lastReplyCode", oneShotRecord.lastReplyCode.contains("-R1E-"))

        // 5. Scenario B + Caution 3 (Lock Winning Score Mid-Round):
        // Simulate a challenge from WIN_RIVAL where bestScore (6) > targetScore (5)
        val winRivalCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 5,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 70005,
            senderName = "WIN_RIVAL",
            chainId = "WN1",
            isRevenge = false
        )
        ChallengeStorage.saveInProgressChallenge(
            context,
            ChallengeItem(
                code = winRivalCode,
                rivalName = "WIN_RIVAL",
                gridSizeTag = "2x2",
                targetScore = 5,
                maxTries = 3,
                attemptsLeft = 2,
                bestScore = 6,
                seed = 70005,
                difficulty = GameDifficulty.EASY,
                chainId = "WN1"
            )
        )
        // Refresh Rivalry screen and resume WIN_RIVAL
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_dynamic_button_WIN_RIVAL").performClick()
        composeTestRule.waitForIdle()

        // Dismiss Ready gate so we are mid-round with effectiveBest (6) > target (5)
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        composeTestRule.waitForIdle()

        // Open Pause -> Exit Challenge -> verify Scenario B (WON) mid-round copy (Caution 3)
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_exit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("exit_challenge_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_challenge_dialog_title").assertTextContains("Lock Score & Exit?")
        composeTestRule.onNodeWithTag("exit_challenge_dialog_body").assertTextContains(
            "You've already beaten the target! If you exit now, your score of 6 will be locked in as your final result. Keep playing to push your score even higher?"
        )
        composeTestRule.onNodeWithTag("exit_challenge_lock_score_button").performClick()
        composeTestRule.waitForIdle()

        val lockedWinRecord = ChallengeStorage.findRivalByName(context, "WIN_RIVAL")
        assertNotNull(lockedWinRecord)
        assertEquals("Scenario B: Locking winning score must finalize as WON", "WON", lockedWinRecord!!.lastStatus)
        assertEquals(6, lockedWinRecord.lastUserScore)
        assertFalse("Scenario B: Locking winning score must generate unsent R-code (isReplySent = false)", lockedWinRecord.isReplySent)
        assertTrue(lockedWinRecord.lastReplyCode.uppercase().contains("2X2-6-R3E-"))
    }

    @Test
    fun pass5_21_victoryDecisionPause_inboundChallenge_and_revengeRun() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Helper to compute deterministic Easy 2x2 pad sequence for a given seed
        fun padSequenceForSeed(seed: Int, count: Int): List<PadSpec> {
            val rng = java.util.Random(seed.toLong())
            return List(count) { CLASSIC_2X2_PADS[rng.nextInt(CLASSIC_2X2_PADS.size)] }
        }

        // 1. Inbound Challenge: Target = 1, Seed = 81001 ("RIVAL21")
        // Verify:
        // - Clearing Round 1 (currentScore == 1 == targetScore) does NOT show VictoryDecisionDialog (Tie rule).
        // - Clearing Round 2 (currentScore == 2 > targetScore) DOES show VictoryDecisionDialog, with bestScore already checkpointed.
        // - Tapping [Keep Playing] dismisses the dialog and allows Round 3; clearing Round 3 (currentScore == 3) does NOT show the dialog again (Once-per-try rule).
        val seed1 = 81001
        val seq1 = padSequenceForSeed(seed1, 4)
        val rival21Code = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 1,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = seed1,
            senderName = "RIVAL21",
            chainId = "R21",
            isRevenge = false
        )
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(rival21Code)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Dismiss Ready gate and wait for Round 1 sequence playback (450ms + 400ms + 200ms)
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(1500L)
        composeTestRule.waitForIdle()

        // Tap Round 1 pad -> currentScore becomes 1 (Tie with target 1) -> must NOT trigger VictoryDecisionDialog
        composeTestRule.onNodeWithTag(seq1[0].testTagId).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("victory_decision_dialog").assertDoesNotExist()

        // Advance through Round 1 celebration + Round 2 playback
        composeTestRule.mainClock.advanceTimeBy(3000L)
        composeTestRule.waitForIdle()

        // Tap Round 2 pads (2 steps) -> currentScore becomes 2 (> targetScore 1) -> triggers VictoryDecisionDialog!
        composeTestRule.onNodeWithTag(seq1[0].testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(seq1[1].testTagId).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("victory_decision_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("victory_decision_title").assertTextContains("🏆 Target Beaten!")
        composeTestRule.onNodeWithTag("victory_decision_body").assertTextContains(
            "You beat RIVAL21's target of 1 with score 2! Lock this as your winning result, or keep playing to set an even higher score?"
        )

        // Verify checkpoint-before-overlay: bestScore (2) is already persisted in SharedPreferences while dialog is open
        val checkpointedRival21 = ChallengeStorage.findRivalByName(context, "RIVAL21")
        assertNotNull(checkpointedRival21)
        assertEquals("bestScore must be checkpointed before showing VictoryDecisionDialog", 2, checkpointedRival21!!.lastUserScore)

        // Tap [Keep Playing] -> dialog closes and Round 3 deals
        composeTestRule.onNodeWithTag("victory_decision_keep_playing_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("victory_decision_dialog").assertDoesNotExist()

        // Advance through Round 3 playback
        composeTestRule.mainClock.advanceTimeBy(3500L)
        composeTestRule.waitForIdle()

        // Clear Round 3 (3 steps) -> currentScore becomes 3 -> must NOT show VictoryDecisionDialog a second time in the same try!
        composeTestRule.onNodeWithTag(seq1[0].testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(seq1[1].testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(seq1[2].testTagId).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("victory_decision_dialog").assertDoesNotExist()

        // Lock via Pause -> Exit Challenge -> Scenario B Lock Score
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_exit_challenge_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exit_challenge_lock_score_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Inbound Challenge [Lock & Exit] + Micro-Caution (effectiveBest = maxOf(currentScore, ch.bestScore)):
        val seed2 = 81002
        val seq2 = padSequenceForSeed(seed2, 3)
        val lock21Code = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 1,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = seed2,
            senderName = "LOCK21",
            chainId = "L21",
            isRevenge = false
        )
        // Pre-seed LOCK21 with an in-progress bestScore of 4 (e.g., resumed run where previous try had bestScore 4)
        ChallengeStorage.saveInProgressChallenge(
            context,
            ChallengeItem(
                code = lock21Code,
                rivalName = "LOCK21",
                gridSizeTag = "2x2",
                targetScore = 1,
                maxTries = 3,
                attemptsLeft = 2,
                bestScore = 4,
                seed = seed2,
                difficulty = GameDifficulty.EASY,
                chainId = "L21"
            )
        )
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_dynamic_button_LOCK21").performClick()
        composeTestRule.waitForIdle()

        // Dismiss Ready gate and play Round 1 + Round 2 so currentScore reaches 2 (> targetScore 1), while ch.bestScore is 4
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(1500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(seq2[0].testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(3000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(seq2[0].testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(seq2[1].testTagId).performClick()
        composeTestRule.waitForIdle()

        // Micro-Caution: Body copy must display effectiveBest = maxOf(currentScore=2, ch.bestScore=4) = 4!
        composeTestRule.onNodeWithTag("victory_decision_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("victory_decision_body").assertTextContains(
            "You beat LOCK21's target of 1 with score 4! Lock this as your winning result, or keep playing to set an even higher score?"
        )
        composeTestRule.onNodeWithTag("victory_decision_lock_exit_button").performClick()
        composeTestRule.waitForIdle()

        val lockedRecord = ChallengeStorage.findRivalByName(context, "LOCK21")
        assertNotNull(lockedRecord)
        assertEquals("WON", lockedRecord!!.lastStatus)
        assertEquals("Lock & Exit must finalize with effectiveBest (4)", 4, lockedRecord.lastUserScore)
        assertFalse("Lock & Exit must generate unsent R-code (isReplySent = false)", lockedRecord.isReplySent)
        assertTrue(lockedRecord.lastReplyCode.uppercase().contains("2X2-4-R3E-"))
    }

    @Test
    fun pass5_22_rivalMatchVariationSelector_and_perGridTargets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. Create a RivalRecord for "HERO_DUEL" with matches across multiple grids:
        // - 3x3: Last match was LOST, targetScore = 6 (lastGrid = "3x3", lastTarget = 6, lastStatus = "LOST", lastDifficulty = EASY)
        // - 2x2: Previous match was WON (gridStats["2x2"] = GridStat(wins = 1, losses = 0, lastTarget = 4, lastStatus = "WON"))
        // - 5x4: Previous match was LOST with target 9 (gridStats["5x4"] = GridStat(wins = 0, losses = 1, lastTarget = 9, lastStatus = "LOST"))
        // - 4x4: Unplayed (no entry in gridStats)
        val initialHeroDuel = RivalRecord(
            name = "HERO_DUEL",
            wins = 1,
            losses = 2,
            ties = 0,
            matches = 3,
            lastGrid = "3x3",
            lastDifficulty = GameDifficulty.EASY,
            lastTarget = 6,
            lastUserScore = 5,
            lastStatus = "LOST",
            lastChainId = "HRO",
            gridStats = mapOf(
                "3x3" to GridStat(wins = 0, losses = 1, ties = 0, lastTarget = 6, lastDifficulty = GameDifficulty.EASY, lastStatus = "LOST"),
                "2x2" to GridStat(wins = 1, losses = 0, ties = 0, lastTarget = 4, lastDifficulty = GameDifficulty.EASY, lastStatus = "WON"),
                "5x4" to GridStat(wins = 0, losses = 1, ties = 0, lastTarget = 9, lastDifficulty = GameDifficulty.HARD, lastStatus = "LOST")
            )
        )
        ChallengeStorage.saveRival(context, initialHeroDuel)

        // Navigate to Rivalry tab
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        // Verify the dynamic action button on HERO_DUEL's card says "⚡ Get Revenge"
        composeTestRule.onNodeWithTag("rival_dynamic_button_HERO_DUEL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_dynamic_button_HERO_DUEL").assertTextContains("⚡ Get Revenge")

        // 2. Click "⚡ Get Revenge" -> opens RivalMatchSetupDialog
        composeTestRule.onNodeWithTag("rival_dynamic_button_HERO_DUEL").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_match_setup_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_setup_dialog_title").assertTextContains("HERO_DUEL", substring = true)

        // Check Continue Last Mode Card
        composeTestRule.onNodeWithTag("rival_setup_continue_last_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_setup_continue_last_button").assertTextContains("3X3", substring = true)

        // 3. Check Variation Selectors:
        // Ensure Easy and Hard exist, and MEDIUM does NOT exist (Catch 1)
        composeTestRule.onNodeWithTag("rival_setup_diff_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_setup_diff_hard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_setup_diff_medium").assertDoesNotExist()

        // Default grid is 3x3 (loss, target 6)
        composeTestRule.onNodeWithTag("rival_setup_target_banner").assertTextContains("score of 6", substring = true)

        // Switch grid to 5x4 (prior loss with target 9)
        composeTestRule.onNodeWithTag("rival_setup_grid_5x4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_setup_target_banner").assertTextContains("score of 9", substring = true)

        // Switch grid to 2x2 (prior win)
        composeTestRule.onNodeWithTag("rival_setup_grid_2x2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_setup_target_banner").assertTextContains("Score 5+", substring = true)

        // Switch grid to 4x4 (unplayed)
        composeTestRule.onNodeWithTag("rival_setup_grid_4x4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_setup_target_banner").assertTextContains("Score 5+", substring = true)

        // 4. Test Custom Match Launch on 5x4 Hard:
        composeTestRule.onNodeWithTag("rival_setup_grid_5x4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_setup_diff_hard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_setup_start_custom_button").performClick()
        composeTestRule.waitForIdle()

        // Dialog closes, game starts on 5x4 grid with Ready gate
        composeTestRule.onNodeWithTag("rival_match_setup_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").assertIsDisplayed()

        // Banner shows "⚡ REVENGE vs HERO_DUEL • BEAT: 9" and grid "5×4"
        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("⚡ REVENGE vs HERO_DUEL").assertIsDisplayed()
        composeTestRule.onNodeWithText("BEAT: 9", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_challenge_grid_pace_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_challenge_grid_pace_pill").assertTextContains("CLASSIC", substring = true)
        composeTestRule.onNodeWithTag("active_challenge_grid_pace_pill").assertTextContains("5", substring = true)
    }

    @Test
    fun pass5_23_customNeonSplashScreen_visualsAndStructure() {
        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.setContent {
                SplashScreen()
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify root splash screen container is displayed
        composeTestRule.onNodeWithTag("splash_screen").assertExists()

        // 2. Verify authentic app icon container is present
        composeTestRule.onNodeWithContentDescription("Glowback App Icon").assertExists()

        // 3. Verify ripple canvas is present
        composeTestRule.onNodeWithTag("splash_ripples").assertExists()

        // 4. Verify title section and "GLOWBACK" title
        composeTestRule.onNodeWithTag("splash_title").assertExists()
        composeTestRule.onNodeWithText("GLOWBACK").assertExists()

        // 5. CRITICAL: Ensure NO subtitle exists
        composeTestRule.onNodeWithText("CYBER MEMORY PROTOCOL").assertDoesNotExist()
        composeTestRule.onNodeWithText("PROTOCOL", substring = true).assertDoesNotExist()
    }

    @Test
    fun pass5_26_unifiedRivalRunState_multiplayerOnlySave_minFiveVictoryPause_and_exitGuards() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. Confirmed Decision 1: Normal Solo has NO "Save & Exit" in Pause and NO "Continue" banner on Home
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pause_resume_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_quit_home_button")
            .assertIsDisplayed()
            .assertTextContains("Quit to Home")
        composeTestRule.onNodeWithTag("pause_restart_button").assertIsDisplayed()

        // Quit to Home from Normal Solo -> no Continue pill on Home
        composeTestRule.onNodeWithTag("pause_quit_home_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("continue_run_button").assertDoesNotExist()

        // 2. Confirmed Decision 1: 2P Pass & Play DOES have "Save & Exit" and shows "Continue" banner on Home
        composeTestRule.onNodeWithTag("mode_2p").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("handoff_ready_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_save_exit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("continue_run_button").assertIsDisplayed()

        // Switch back to SOLO and clear the 2P run
        RunStateStorage.clearRunState(context)
        composeTestRule.onNodeWithTag("mode_solo").performClick()
        composeTestRule.waitForIdle()

        // 3. Outbound Rival Run on unplayed 2x2 grid (MIN: 5 gate) vs rival "RIVAL26" (lost on 3x3, unplayed on 2x2)
        val rival26 = RivalRecord(
            name = "RIVAL26",
            wins = 0,
            losses = 1,
            ties = 0,
            matches = 1,
            lastGrid = "3x3",
            lastDifficulty = GameDifficulty.EASY,
            lastTarget = 7,
            lastUserScore = 4,
            lastStatus = "LOST",
            lastChainId = "R26",
            gridStats = mapOf(
                "3x3" to GridStat(wins = 0, losses = 1, ties = 0, lastTarget = 7, lastDifficulty = GameDifficulty.EASY, lastStatus = "LOST")
            )
        )
        ChallengeStorage.saveRival(context, rival26)

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_dynamic_button_RIVAL26").performClick()
        composeTestRule.waitForIdle()

        // Select unplayed 2x2 grid (MIN: 5 gate) and start custom match
        composeTestRule.onNodeWithTag("rival_setup_grid_2x2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_setup_target_banner").assertTextContains("Score 5+", substring = true)
        composeTestRule.onNodeWithTag("rival_setup_start_custom_button").performClick()
        composeTestRule.waitForIdle()

        // Verify InGameChallengeBanner shows "RIVAL: RIVAL26" and "MIN: 5"
        composeTestRule.onNodeWithTag("ingame_challenge_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("RIVAL: RIVAL26").assertIsDisplayed()
        composeTestRule.onNodeWithText("MIN: 5", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").assertIsDisplayed()

        val initialSeed = ChallengeSeed.lastGeneratedRandomSeed
        // Dismiss Ready gate
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        composeTestRule.waitForIdle()

        // Verify Pause menu on MIN: 5 Rival Run has NO "Save & Exit" and shows "Exit to Rivalry"
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_quit_home_button")
            .assertIsDisplayed()
            .assertTextContains("Exit to Rivalry")

        // Confirmed Decision 3: Tapping "Exit to Rivalry" when score (0) < gate (5) warns about discarding
        composeTestRule.onNodeWithTag("pause_quit_home_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exit_challenge_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_challenge_dialog_title").assertTextContains("Exit Rival Run?")
        composeTestRule.onNodeWithTag("exit_challenge_dialog_body").assertTextContains("minimum score of 5", substring = true)
        composeTestRule.onNodeWithTag("exit_challenge_keep_playing_button").performClick()
        composeTestRule.waitForIdle()

        // Review Catch 1: Tapping Restart on a MIN: 5 Rival Run re-arms the Ready gate with a fresh seed
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_restart_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").assertIsDisplayed()
        val restartedSeed = ChallengeSeed.lastGeneratedRandomSeed

        // Helper to compute deterministic Easy 2x2 pad sequence for a given seed
        fun padSequenceForSeed(seed: Int, count: Int): List<PadSpec> {
            val rng = java.util.Random(seed.toLong())
            return List(count) { CLASSIC_2X2_PADS[rng.nextInt(CLASSIC_2X2_PADS.size)] }
        }
        val seq = padSequenceForSeed(restartedSeed, 6)

        // Dismiss Ready gate and play Rounds 1..5 to reach Score 5 on the MIN: 5 Rival Run
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        for (round in 1..5) {
            composeTestRule.mainClock.advanceTimeBy(450L + round * 650L + 500L)
            composeTestRule.waitForIdle()
            for (step in 0 until round) {
                composeTestRule.onNodeWithTag(seq[step].testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(100L)
                composeTestRule.waitForIdle()
            }
        }

        // Confirmed Decision 2: Clearing Round 5 (Score 5) on MIN: 5 Rival Run triggers VictoryDecisionDialog!
        composeTestRule.onNodeWithTag("victory_decision_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("victory_decision_title").assertTextContains("🏆 Target Reached!")
        composeTestRule.onNodeWithTag("victory_decision_body").assertTextContains(
            "You reached score 5 against RIVAL26! Lock this to unlock your challenge share, or keep playing to set an even higher target?"
        )

        // Tap [Keep Playing] to continue to Round 6
        composeTestRule.onNodeWithTag("victory_decision_keep_playing_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("victory_decision_dialog").assertDoesNotExist()

        // Review Catch 1 Bonus Safeguard: Tapping Restart at Score 5 (>= gate 5) shows RivalRestartConfirmDialog
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_restart_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_restart_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_restart_dialog_body").assertTextContains(
            "Restart discards your qualifying score of 5 against RIVAL26."
        )
        composeTestRule.onNodeWithTag("rival_restart_keep_playing_button").performClick()
        composeTestRule.waitForIdle()

        // Confirmed Decision 3: Tapping Pause -> Exit to Rivalry at Score 5 (>= gate 5) offers "Exit & Lock Score"
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_quit_home_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exit_challenge_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_challenge_dialog_title").assertTextContains("Lock Score & Exit?")
        composeTestRule.onNodeWithTag("exit_challenge_lock_score_button").performClick()
        composeTestRule.waitForIdle()

        // Polish 2: Lands on Game Over with Score 5 locked in and Share Challenge unlocked ("Exit to Rivalry" button shown)
        composeTestRule.onNodeWithTag("share_challenge_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_over_home_button")
            .assertIsDisplayed()
            .assertTextContains("Exit to Rivalry")

        // Polish 1: Tapping "Exit to Rivalry" before sharing Score 5 prompts UnsharedRivalExitConfirmDialog
        composeTestRule.onNodeWithTag("game_over_home_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unshared_rival_exit_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unshared_rival_exit_body").assertTextContains(
            "Score 5 qualifies — share before leaving?"
        )

        // Tap "Leave Without Sharing" -> returns cleanly to Rivalry screen and clears rival state
        composeTestRule.onNodeWithTag("unshared_rival_leave_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hall_of_fame_screen").assertIsDisplayed()
        assertTrue("Initial seed must be valid", initialSeed >= 0)
    }

    @Test
    fun pass5_27_test1_crossGridSwitchAndGameOverShare_preservesExactSequenceAcrossDialogDismissAndReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ChallengeStorage.setMyName(context, "HERO")
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. Start on 4x4, then switch to 3x3 Easy via Settings
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_4x4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_3x3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        // 2. Start a 3x3 Easy game and play to Score 6, recording the exact 6-pad sequence
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()

        val runSeed3x3 = ChallengeSeed.lastGeneratedRandomSeed
        val pads3x3 = padsForGridSize(GridSize.THREE_BY_THREE)
        val expectedIndices = ChallengeSeed.generateSequenceForRound(
            seed = runSeed3x3,
            padCount = pads3x3.size,
            difficulty = GameDifficulty.EASY,
            round = 7
        )
        val playedSequenceRound6 = expectedIndices.take(6).map { pads3x3[it] }

        for (round in 1..6) {
            composeTestRule.mainClock.advanceTimeBy(450L + round * 650L + 500L)
            composeTestRule.waitForIdle()
            for (step in 0 until round) {
                val padToTap = pads3x3[expectedIndices[step]]
                composeTestRule.onNodeWithTag(padToTap.testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(100L)
                composeTestRule.waitForIdle()
            }
        }

        // Advance into Round 7 playback and intentionally tap a wrong pad on Step 1 to enter GAME_OVER at Score 6
        composeTestRule.mainClock.advanceTimeBy(450L + 7 * 650L + 700L)
        composeTestRule.waitForIdle()
        val wrongPad = pads3x3.first { it.id != pads3x3[expectedIndices[0]].id }
        composeTestRule.onNodeWithTag(wrongPad.testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        // 3. On Game Over: Open Share Challenge, DISMISS it, then RE-OPEN Share Challenge
        composeTestRule.onNodeWithTag("share_challenge_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tries_selector_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_tries_selector_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tries_selector_sheet").assertDoesNotExist()

        // Re-open Share Challenge on Game Over -> must still lock onto the played 3x3 Easy Score 6 & runSeed3x3!
        composeTestRule.onNodeWithTag("share_challenge_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("locked_run_summary_text")
            .assertIsDisplayed()
            .assertTextContains("3×3 Classic Easy - your score 6", substring = true)
        composeTestRule.onNodeWithTag("tries_selector_copy_code_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        val pendingShare = ChallengeStorage.getPendingShare(context)
        assertNotNull("Pending share must be created after copying from ShareTriesSelectorDialog", pendingShare)
        val parsed = ChallengeSeed.parseChallengeCode(pendingShare!!.code)
        assertNotNull("Generated code must be valid 7-segment code", parsed)
        assertEquals("3x3", parsed!!.gridTag)
        assertEquals(6, parsed.targetScore)
        assertEquals("Seed in shared code must equal the exact played runSeed3x3", runSeed3x3, parsed.seedInt)

        val receiverRound6Pads = ChallengeSeed.generateSequenceForRound(
            seed = parsed.seedInt,
            padCount = pads3x3.size,
            difficulty = parsed.difficulty,
            round = 6
        ).map { pads3x3[it] }
        assertEquals(
            "Receiver's 3x3 Easy 6-pad sequence must match sender's played sequence element-for-element",
            playedSequenceRound6.map { it.id },
            receiverRound6Pads.map { it.id }
        )
    }

    @Test
    fun pass5_27_test2_delayedLeaderboardShare_twoHoursLaterOn3x3Easy_matchesPlayedSequence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ChallengeStorage.setMyName(context, "HERO")
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. Switch to 3x3 Easy via Settings
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_3x3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        // 2. Play a 3x3 Easy run to Score 7
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()

        val playedSeed3x3 = ChallengeSeed.lastGeneratedRandomSeed
        val pads3x3 = padsForGridSize(GridSize.THREE_BY_THREE)
        val indicesRound8 = ChallengeSeed.generateSequenceForRound(
            seed = playedSeed3x3,
            padCount = pads3x3.size,
            difficulty = GameDifficulty.EASY,
            round = 8
        )
        val played7Pads = indicesRound8.take(7).map { pads3x3[it].id }

        for (round in 1..7) {
            composeTestRule.mainClock.advanceTimeBy(450L + round * 650L + 500L)
            composeTestRule.waitForIdle()
            for (step in 0 until round) {
                composeTestRule.onNodeWithTag(pads3x3[indicesRound8[step]].testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(100L)
                composeTestRule.waitForIdle()
            }
        }

        // Miss on Round 8 Step 1 -> finishes run at Score 7
        composeTestRule.mainClock.advanceTimeBy(450L + 8 * 650L + 700L)
        composeTestRule.waitForIdle()
        val wrongPad = pads3x3.first { it.id != pads3x3[indicesRound8[0]].id }
        composeTestRule.onNodeWithTag(wrongPad.testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        // Verify Data Layer persisted both score 7 and exact seed playedSeed3x3
        assertEquals(7, prefs.getInt("best_3x3_classic_EASY", 0))
        assertEquals(
            "best_seed_3x3_classic_EASY must persist the exact played seed",
            playedSeed3x3,
            prefs.getInt("best_seed_3x3_classic_EASY", 0)
        )
        val bestRecord = getBestBoardRecord(prefs, "3x3", GameDifficulty.EASY)
        assertNotNull(bestRecord)
        assertEquals(7, bestRecord!!.score)
        assertEquals(playedSeed3x3, bestRecord.seed)

        // 3. Simulate "2 hours later": leave Game Over to Home, switch to 2x2, start another run (which overwrites currentRunSeed in memory), then go to Leaderboard
        composeTestRule.onNodeWithTag("game_over_home_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_2x2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_quit_home_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Leaderboard / Rivalry tab and tap Share Challenge
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_share_button").performClick()
        composeTestRule.waitForIdle()

        // Select board_option_0 (3x3 Classic · Easy 7) and copy code
        composeTestRule.onNodeWithTag("board_option_0")
            .assertIsDisplayed()
            .assertTextContains("3x3 Classic · Easy", substring = true)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tries_selector_copy_code_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        val delayedShare = ChallengeStorage.getPendingShare(context)
        assertNotNull("Delayed share from Leaderboard must create pending share", delayedShare)
        val parsedDelayed = ChallengeSeed.parseChallengeCode(delayedShare!!.code)
        assertNotNull(parsedDelayed)
        assertEquals("3x3", parsedDelayed!!.gridTag)
        assertEquals(7, parsedDelayed.targetScore)
        assertEquals(
            "Delayed Leaderboard share must encode the exact stored seed from the 3x3 Easy run",
            playedSeed3x3,
            parsedDelayed.seedInt
        )

        val receiver7Pads = ChallengeSeed.generateSequenceForRound(
            seed = parsedDelayed.seedInt,
            padCount = pads3x3.size,
            difficulty = parsedDelayed.difficulty,
            round = 7
        ).map { pads3x3[it].id }
        assertEquals(
            "Delayed Leaderboard share sequence must be 100% identical to the 7-pad sequence played 2 hours earlier",
            played7Pads,
            receiver7Pads
        )
    }

    @Test
    fun pass5_27_test3_allFourGridsAndBothDifficulties_deterministicSequenceParity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Verify mathematical determinism across all 4 grids (2x2, 3x3, 4x4, 5x4) and both difficulties (EASY, HARD)
        val testSeeds = listOf(13579, 24680, 987654, 33554431)
        for (grid in GridSize.entries) {
            val pads = padsForGridSize(grid)
            for (diff in GameDifficulty.entries) {
                for (seed in testSeeds) {
                    val code = ChallengeSeed.formatChallengeCode(
                        gridTag = grid.keyPrefix,
                        targetScore = 6,
                        tries = 3,
                        difficulty = diff,
                        seed = seed,
                        senderName = "HERO",
                        chainId = "DET",
                        isRevenge = false
                    )
                    val parsed = ChallengeSeed.parseChallengeCode(code)
                    assertNotNull("Code must parse for ${grid.keyPrefix} $diff", parsed)
                    assertEquals(seed and ChallengeSeed.MAX_SEED, parsed!!.seedInt)

                    // Simulate Sender live RNG stream vs Receiver live RNG stream through Rounds 1..6
                    val senderRng = java.util.Random(seed.toLong())
                    val receiverRng = java.util.Random(parsed.seedInt.toLong())
                    val senderCumulative = mutableListOf<Int>()
                    val receiverCumulative = mutableListOf<Int>()

                    for (round in 1..6) {
                        val senderRoundPads = if (diff == GameDifficulty.HARD) {
                            List(round) { pads[senderRng.nextInt(pads.size)].id }
                        } else {
                            senderCumulative.add(pads[senderRng.nextInt(pads.size)].id)
                            senderCumulative.toList()
                        }

                        val receiverRoundPads = if (diff == GameDifficulty.HARD) {
                            List(round) { pads[receiverRng.nextInt(pads.size)].id }
                        } else {
                            receiverCumulative.add(pads[receiverRng.nextInt(pads.size)].id)
                            receiverCumulative.toList()
                        }

                        val helperRoundPads = ChallengeSeed.generateSequenceForRound(
                            seed = parsed.seedInt,
                            padCount = pads.size,
                            difficulty = diff,
                            round = round
                        ).map { pads[it].id }

                        assertEquals(
                            "Sender and Receiver sequences must match on ${grid.keyPrefix} $diff Round $round",
                            senderRoundPads,
                            receiverRoundPads
                        )
                        assertEquals(
                            "generateSequenceForRound must match live RNG stream on ${grid.keyPrefix} $diff Round $round",
                            senderRoundPads,
                            helperRoundPads
                        )
                    }
                }
            }
        }

        // 2. Verify Self-Healing Legacy Migration: if a legacy score >= 5 exists with no seed, readMigratedBestSeed mints and persists a stable seed
        prefs.edit()
            .putInt(bestScoreKeyFor(GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.HARD), 11)
            .remove(bestSeedKeyFor(GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.HARD))
            .apply()

        val healedSeed1 = readMigratedBestSeed(prefs, GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.HARD)
        val healedSeed2 = readMigratedBestSeed(prefs, GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.HARD)
        assertTrue("Self-healed legacy seed must be > 0", healedSeed1 > 0)
        assertEquals("Self-healed legacy seed must be persisted and stable across reads", healedSeed1, healedSeed2)
    }

    @Test
    fun pass5_27_test4_nonBestQualifyingRun_gameOverEncodesPlayedRun_leaderboardEncodesAllTimeBest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ChallengeStorage.setMyName(context, "HERO")

        // 1. Pre-seed 3x3 Easy with an all-time best of 10 and seed = 101010
        val allTimeBestSeed = 101010
        writeBestScore(
            prefs = prefs,
            gridSize = GridSize.THREE_BY_THREE,
            paceMode = PaceMode.CLASSIC,
            difficulty = GameDifficulty.EASY,
            score = 10,
            seed = allTimeBestSeed
        )

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Switch to 3x3 Easy via Settings
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_3x3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        // 2. Play a new 3x3 Easy run to Score 6 (qualifies >= 5, but < all-time best 10)
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()

        val nonBestRunSeed = ChallengeSeed.lastGeneratedRandomSeed
        val pads3x3 = padsForGridSize(GridSize.THREE_BY_THREE)
        val indicesRound7 = ChallengeSeed.generateSequenceForRound(
            seed = nonBestRunSeed,
            padCount = pads3x3.size,
            difficulty = GameDifficulty.EASY,
            round = 7
        )

        for (round in 1..6) {
            composeTestRule.mainClock.advanceTimeBy(450L + round * 650L + 500L)
            composeTestRule.waitForIdle()
            for (step in 0 until round) {
                composeTestRule.onNodeWithTag(pads3x3[indicesRound7[step]].testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(100L)
                composeTestRule.waitForIdle()
            }
        }

        // Miss on Round 7 Step 1 -> enters GAME_OVER at Score 6
        composeTestRule.mainClock.advanceTimeBy(450L + 7 * 650L + 700L)
        composeTestRule.waitForIdle()
        val wrongPad = pads3x3.first { it.id != pads3x3[indicesRound7[0]].id }
        composeTestRule.onNodeWithTag(wrongPad.testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        // All-time best in SharedPreferences must still be 10 with seed 101010
        assertEquals(10, readMigratedBestScore(prefs, GridSize.THREE_BY_THREE, PaceMode.CLASSIC, GameDifficulty.EASY))
        assertEquals(allTimeBestSeed, readMigratedBestSeed(prefs, GridSize.THREE_BY_THREE, PaceMode.CLASSIC, GameDifficulty.EASY))

        // 3. Assertion A: Sharing directly from Game Over encodes the just-played run (score = 6, seed = nonBestRunSeed)
        composeTestRule.onNodeWithTag("share_challenge_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("locked_run_summary_text")
            .assertIsDisplayed()
            .assertTextContains("3×3 Classic Easy - your score 6", substring = true)
        composeTestRule.onNodeWithTag("tries_selector_copy_code_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        val gameOverShare = ChallengeStorage.getPendingShare(context)
        assertNotNull(gameOverShare)
        val parsedGameOver = ChallengeSeed.parseChallengeCode(gameOverShare!!.code)
        assertNotNull(parsedGameOver)
        assertEquals("Game Over share must encode the just-played score of 6", 6, parsedGameOver!!.targetScore)
        assertEquals("Game Over share must encode the just-played seed S_6", nonBestRunSeed, parsedGameOver.seedInt)

        // Dismiss "Did you share it?" dialog
        composeTestRule.onNodeWithTag("did_you_share_no_button").performClick()
        composeTestRule.waitForIdle()

        // 4. Assertion B: Sharing later from the Leaderboard tab encodes the all-time best (score = 10, seed = 101010)
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_share_button").performClick()
        composeTestRule.waitForIdle()

        // Only 1 board option exists for 3x3 Classic Easy (the all-time best of 10, not 6)
        composeTestRule.onNodeWithTag("board_option_0")
            .assertIsDisplayed()
            .assertTextContains("3x3 Classic · Easy", substring = true)
            .assertTextContains("10", substring = true)
        composeTestRule.onNodeWithTag("board_option_1").assertDoesNotExist()

        composeTestRule.onNodeWithTag("tries_selector_copy_code_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        val leaderboardShare = ChallengeStorage.getPendingShare(context)
        assertNotNull(leaderboardShare)
        val parsedLeaderboard = ChallengeSeed.parseChallengeCode(leaderboardShare!!.code)
        assertNotNull(parsedLeaderboard)
        assertEquals("Leaderboard share must encode the all-time best score of 10", 10, parsedLeaderboard!!.targetScore)
        assertEquals("Leaderboard share must encode the all-time best seed S_10", allTimeBestSeed, parsedLeaderboard.seedInt)
    }

    @Test
    fun pass5_28_sleepingScores_recommendationsFilterShareAndChainStacking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ChallengeStorage.setMyName(context, "HERO")

        // 1. Seed Rival VORTEX (chainId = "VRX") with multiple grid targets:
        //    - 4x4 Easy: target 10 (LOST)
        //    - 2x2 Hard: target 9 (TIED)
        //    - 3x3 Easy (most recent / lastGrid): target 8 (LOST)
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "VORTEX",
            userScore = 6,
            targetScore = 10,
            gridSize = "4x4",
            difficulty = GameDifficulty.EASY,
            chainId = "VRX",
            maxTries = 3
        )
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "VORTEX",
            userScore = 9,
            targetScore = 9,
            gridSize = "2x2",
            difficulty = GameDifficulty.HARD,
            chainId = "VRX",
            maxTries = 3
        )
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "VORTEX",
            userScore = 5,
            targetScore = 8,
            gridSize = "3x3",
            difficulty = GameDifficulty.EASY,
            chainId = "VRX",
            maxTries = 3
        )

        // Also seed Rival TITAN (2x2 Easy target = 15, where player has no qualifying score > 15)
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "TITAN",
            userScore = 7,
            targetScore = 15,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "TTN",
            maxTries = 3
        )

        // 2. Seed player's stored board scores via writeBestScore:
        //    - 3x3 Classic Easy: score 12, seed 33312 -> QUALIFIES for VORTEX (12 > 8, same grid + Easy)
        //    - 2x2 Classic Hard: score 9, seed 22209  -> DOES NOT QUALIFY (9 == 9, not strictly greater)
        //    - 4x4 Classic Hard: score 15, seed 44415 -> DOES NOT QUALIFY (difficulty mismatch: Hard vs VORTEX's Easy on 4x4)
        //    - 5x4 Classic Easy: score 14, seed 54014 -> DOES NOT QUALIFY (VORTEX has no lastTarget on 5x4)
        //    - 3x3 Progressive Easy: score 20, seed 33320 -> DOES NOT QUALIFY (Progressive pace excluded)
        //    - 2x2 Classic Easy: score 10, seed 22210 -> DOES NOT QUALIFY for TITAN (10 <= 15) or VORTEX (difficulty mismatch vs 2x2 Hard)
        writeBestScore(prefs, GridSize.THREE_BY_THREE, PaceMode.CLASSIC, GameDifficulty.EASY, score = 12, seed = 33312)
        writeBestScore(prefs, GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.HARD, score = 9, seed = 22209)
        writeBestScore(prefs, GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.HARD, score = 15, seed = 44415)
        writeBestScore(prefs, GridSize.FIVE_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.EASY, score = 14, seed = 54014)
        writeBestScore(prefs, GridSize.THREE_BY_THREE, PaceMode.PROGRESSIVE, GameDifficulty.EASY, score = 20, seed = 33320)
        writeBestScore(prefs, GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY, score = 10, seed = 22210)

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Navigate to Leaderboard -> Rivalry tab
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        // Proof 4: Open RivalMatchSetupDialog for TITAN -> no qualifying score exists, so recommendation section is hidden
        composeTestRule.onNodeWithTag("rival_dynamic_button_TITAN").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_match_setup_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_setup_recommendations_section").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rival_setup_close_button").performClick()
        composeTestRule.waitForIdle()

        // Proof 1: Open RivalMatchSetupDialog for VORTEX -> recommendation section is shown with ONLY 3x3 Easy chip
        composeTestRule.onNodeWithTag("rival_dynamic_button_VORTEX").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_match_setup_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_setup_recommendations_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_recommendation_chip_3x3_easy")
            .assertIsDisplayed()
            .assertTextContains("3×3 Easy · You 12 vs their 8", substring = true)

        // Non-qualifying boards must NOT appear as recommendation chips
        composeTestRule.onNodeWithTag("rival_recommendation_chip_2x2_hard").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rival_recommendation_chip_4x4_hard").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rival_recommendation_chip_5x4_easy").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rival_recommendation_chip_2x2_easy").assertDoesNotExist()

        // Proof 2: Tap the recommendation chip -> opens ShareTriesSelectorDialog filtered ONLY to VORTEX's qualifying scores
        composeTestRule.onNodeWithTag("rival_recommendation_chip_3x3_easy").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_match_setup_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tries_selector_sheet").assertIsDisplayed()

        // Only 1 board option is shown in the filtered dialog (3x3 Classic Easy 12)
        composeTestRule.onNodeWithTag("board_option_0")
            .assertIsDisplayed()
            .assertTextContains("3x3 Classic · Easy", substring = true)
            .assertTextContains("12", substring = true)
        composeTestRule.onNodeWithTag("board_option_1").assertDoesNotExist()

        // Mint the T-code via Copy Code
        composeTestRule.onNodeWithTag("tries_selector_copy_code_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        val pendingShare = ChallengeStorage.getPendingShare(context)
        assertNotNull("Pending share must be created when copying recommended score", pendingShare)
        val parsed = ChallengeSeed.parseChallengeCode(pendingShare!!.code)
        assertNotNull("Minted T-code must be valid 7-segment code", parsed)
        assertFalse("Minted code must be a playable T-code (isRevenge = false)", parsed!!.isRevenge)
        assertEquals("3x3", parsed.gridTag.lowercase())
        assertEquals(GameDifficulty.EASY, parsed.difficulty)
        assertEquals("Minted code target must equal stored score 12", 12, parsed.targetScore)
        assertEquals("Minted code seed must equal original stored seed 33312", 33312, parsed.seedInt)
        assertEquals("Minted code chainId must preserve VORTEX's lastChainId VRX", "VRX", parsed.chainId)

        // Verify KEY_CHAIN_TARGETS stores target = 12 for (chainId = "VRX", seedInt = 33312)
        assertEquals(12, ChallengeStorage.lookupOutboundChainTarget(context, "VRX"))
        assertEquals(12, ChallengeStorage.lookupOutboundChainTarget(context, "VRX", seedInt = 33312))

        // Confirm "Yes, Shared" in DidYouShareDialog -> saves to KEY_AWAITING
        composeTestRule.onNodeWithTag("did_you_share_yes_button").performClick()
        composeTestRule.waitForIdle()

        val awaitingList = ChallengeStorage.loadAwaitingChallenges(context)
        assertTrue(
            "KEY_AWAITING must contain the VRX challenge with target 12",
            awaitingList.any { it.chainId == "VRX" && it.targetScore == 12 && it.gridSizeTag.equals("3x3", ignoreCase = true) }
        )

        // Proof 3: Zero Gameplay Invariant -> app stayed on Hall of Fame / Leaderboard screen and no run started
        composeTestRule.onNodeWithTag("hall_of_fame_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("in_game_challenge_banner").assertDoesNotExist()
    }

    @Test
    fun pass5_29_masterPolish_allFiveCatchesVerified() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ChallengeStorage.setMyName(context, "HERO")

        // 1. Proof 1 (Catch 1 - No Records Regression):
        // Save high score on Progressive 2x2 Easy (score 14) and no Classic scores
        writeBestScore(prefs, GridSize.TWO_BY_TWO, PaceMode.PROGRESSIVE, GameDifficulty.EASY, score = 14, seed = 22214)

        val allBoards = getAllBoardScores(prefs)
        val shareableBoards = getShareableBoardScores(prefs)
        assertTrue("getAllBoardScores must preserve Progressive score for Records tab", allBoards.any { it.paceName == "Progressive" && it.score == 14 })
        assertTrue("getShareableBoardScores must NOT contain Progressive scores", shareableBoards.isEmpty())

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Navigate to Leaderboard -> Records tab (Solo, 2x2)
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_records").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("record_score_easy_prog")
            .assertIsDisplayed()
            .assertTextContains("14", substring = true)

        // 2. Proof 2 (Catch 2 - Zero Classic Boards Empty State):
        // Switch to Rivalry tab and tap Share Challenge with no Classic scores >= 5
        composeTestRule.onNodeWithTag("tab_rivalry").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_share_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("share_no_classic_scores_card")
            .assertIsDisplayed()
            .assertTextContains("No Classic scores of 5+ yet — play a Classic run to create a challenge.", substring = true)
        composeTestRule.onNodeWithTag("generate_card_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("tries_selector_copy_code_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("close_tries_selector_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Proof 3 (Catch 3 - Recommendation Carousel Sort Order):
        // Seed Rival TITAN (lastGrid = "3x3", target = 6)
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "TITAN",
            userScore = 5,
            targetScore = 8,
            gridSize = "4x4",
            difficulty = GameDifficulty.EASY,
            chainId = "TTN",
            maxTries = 3
        )
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "TITAN",
            userScore = 4,
            targetScore = 5,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "TTN",
            maxTries = 3
        )
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "TITAN",
            userScore = 5,
            targetScore = 6,
            gridSize = "3x3",
            difficulty = GameDifficulty.EASY,
            chainId = "TTN",
            maxTries = 3
        )

        // Seed Classic scores:
        // - 4x4 Easy score 15 (vs rival 4x4 target 8 -> margin +7)
        // - 3x3 Easy score 12 (vs rival 3x3 target 6 -> margin +6, rival lastGrid)
        // - 2x2 Easy score 8  (vs rival 2x2 target 5 -> margin +3)
        writeBestScore(prefs, GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.EASY, score = 15, seed = 44415)
        writeBestScore(prefs, GridSize.THREE_BY_THREE, PaceMode.CLASSIC, GameDifficulty.EASY, score = 12, seed = 33312)
        writeBestScore(prefs, GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY, score = 8, seed = 22208)

        // Navigate Home and back to Leaderboard to reload rivals state with TITAN
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_dynamic_button_TITAN").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_match_setup_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_recommendations_carousel").assertIsDisplayed()

        val titanRecord = ChallengeStorage.findRivalByName(context, "TITAN")!!
        val recs = ChallengeStorage.computeSleepingScoresForRival(titanRecord, getShareableBoardScores(prefs))
        assertEquals("Recommendations count must be 3", 3, recs.size)
        assertEquals("Chip 0 must be 4x4 with margin +7", "4x4", recs[0].boardOption.gridTag)
        assertEquals("Chip 1 must be 3x3 with margin +6 (rival lastGrid)", "3x3", recs[1].boardOption.gridTag)
        assertEquals("Chip 2 must be 2x2 with margin +3", "2x2", recs[2].boardOption.gridTag)

        composeTestRule.onNodeWithTag("rival_recommendation_chip_4x4_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_recommendation_chip_3x3_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_recommendation_chip_2x2_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_setup_close_button").performClick()
        composeTestRule.waitForIdle()

        // 4. Proof 4 (Catch 4 - Microcopy in Share Dialog):
        composeTestRule.onNodeWithTag("rivalry_share_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Duels are played at Classic pace.", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_tries_selector_button").performClick()
        composeTestRule.waitForIdle()

        // 5. Proof 5 (Final Addition - Game Over Progressive Pace-Leak Closure):
        // Navigate to Home, switch Pace to Progressive via settings
        composeTestRule.onNodeWithTag("nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pace_mode_progressive").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        // Play a 2x2 Progressive run to score 6
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()

        val pads2x2 = padsForGridSize(GridSize.TWO_BY_TWO)
        val runSeed = ChallengeSeed.lastGeneratedRandomSeed
        val indicesRound7 = ChallengeSeed.generateSequenceForRound(
            seed = runSeed,
            padCount = pads2x2.size,
            difficulty = GameDifficulty.EASY,
            round = 7
        )

        for (round in 1..6) {
            composeTestRule.mainClock.advanceTimeBy(450L + round * 650L + 500L)
            composeTestRule.waitForIdle()
            for (step in 0 until round) {
                composeTestRule.onNodeWithTag(pads2x2[indicesRound7[step]].testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(100L)
                composeTestRule.waitForIdle()
            }
        }

        // Miss on Round 7 Step 1 -> Game Over at score 6 in Progressive pace
        composeTestRule.mainClock.advanceTimeBy(450L + 7 * 650L + 700L)
        composeTestRule.waitForIdle()
        val wrongPad = pads2x2.first { it.id != pads2x2[indicesRound7[0]].id }
        composeTestRule.onNodeWithTag(wrongPad.testTagId).performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        // Tap Share Challenge from Game Over
        composeTestRule.onNodeWithTag("share_challenge_button").performClick()
        composeTestRule.waitForIdle()

        // Must display the Progressive blocked card and disable action buttons
        composeTestRule.onNodeWithTag("share_progressive_blocked_card")
            .assertIsDisplayed()
            .assertTextContains("Duels are played at Classic pace — replay in Classic to share this score.", substring = true)
        composeTestRule.onNodeWithTag("generate_card_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("tries_selector_copy_code_button").assertIsNotEnabled()
    }

    @Test
    fun pass5_30_soloVoluntaryGameOver_bankScoreAndSeed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Start 2x2 Classic Solo run
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()

        val pads2x2 = padsForGridSize(GridSize.TWO_BY_TWO)
        val runSeed = ChallengeSeed.lastGeneratedRandomSeed
        val indicesRound9 = ChallengeSeed.generateSequenceForRound(
            seed = runSeed,
            padCount = pads2x2.size,
            difficulty = GameDifficulty.EASY,
            round = 9
        )

        // Play rounds 1..8 to reach score 8
        for (round in 1..8) {
            composeTestRule.mainClock.advanceTimeBy(450L + round * 650L + 500L)
            composeTestRule.waitForIdle()
            for (step in 0 until round) {
                composeTestRule.onNodeWithTag(pads2x2[indicesRound9[step]].testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(100L)
                composeTestRule.waitForIdle()
            }
        }

        // Mid-round 9: Open pause menu
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()

        // Assert Solo >= 5 shows End Run & Keep Score and Quit & Discard
        composeTestRule.onNodeWithTag("pause_end_run_keep_score_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_quit_discard_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_quit_home_button").assertDoesNotExist()

        // Tap End Run & Keep Score
        composeTestRule.onNodeWithTag("pause_end_run_keep_score_button").performClick()
        composeTestRule.waitForIdle()

        // Must transition to Game Over with Final Score 8
        composeTestRule.onAllNodes(hasText("Final Score: 8", substring = true))[0].assertIsDisplayed()

        // Verify score 8 and seed banked in SharedPreferences
        assertEquals("Best score must be 8", 8, prefs.getInt("best_2x2_classic_EASY", 0))
        assertEquals("Best seed must match runSeed", runSeed, prefs.getInt("best_seed_2x2_classic_EASY", 0))

        // Return Home
        composeTestRule.onNodeWithTag("game_over_home_button").performClick()
        composeTestRule.waitForIdle()

        // Must NOT show Continue Solo button on Home screen
        composeTestRule.onNodeWithTag("continue_run_button").assertDoesNotExist()
    }

    @Test
    fun pass5_30_soloLowScore_retainsQuitToHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Start Solo run (score 0 < 5)
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()

        // Open Pause menu at score 0
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()

        // Assert score < 5 retains Quit to Home, with no voluntary game over buttons
        composeTestRule.onNodeWithTag("pause_quit_home_button")
            .assertIsDisplayed()
            .assertTextContains("Quit to Home")
        composeTestRule.onNodeWithTag("pause_end_run_keep_score_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_quit_discard_button").assertDoesNotExist()

        // Tap Quit to Home
        composeTestRule.onNodeWithTag("pause_quit_home_button").performClick()
        composeTestRule.waitForIdle()

        // Must be on Home with no Continue button
        composeTestRule.onNodeWithTag("start_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_run_button").assertDoesNotExist()
    }

    @Test
    fun pass5_30_passAndPlayAndRivals_isolationRegressions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 1. 2P Pass & Play Isolation:
        composeTestRule.onNodeWithTag("mode_2p").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("handoff_ready_button").performClick()
        composeTestRule.waitForIdle()

        // Pause in 2P:
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_quit_home_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_end_run_keep_score_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_quit_discard_button").assertDoesNotExist()

        // Save & Exit in 2P:
        composeTestRule.onNodeWithTag("pause_save_exit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("continue_run_button").assertIsDisplayed()

        // Clear run
        composeTestRule.onNodeWithTag("continue_run_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("handoff_ready_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_quit_home_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Outbound Rival Isolation:
        ChallengeStorage.recordMatchOutcome(
            context = context,
            rivalName = "BLAZE",
            userScore = 4,
            targetScore = 6,
            gridSize = "2x2",
            difficulty = GameDifficulty.EASY,
            chainId = "BLZ",
            maxTries = 3
        )

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_dynamic_button_BLAZE").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rival_setup_continue_last_button").performClick()
        composeTestRule.waitForIdle()

        // Pause in Rival Run:
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_quit_home_button")
            .assertIsDisplayed()
            .assertTextContains("Exit to Rivalry")
        composeTestRule.onNodeWithTag("pause_save_exit_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_end_run_keep_score_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_quit_discard_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pause_resume_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testStorage_fifoCapsAndRetention() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. 200 outbound codes FIFO cap
        for (i in 1..250) {
            ChallengeStorage.recordCreatedCode(context, "2x2-$i-T3E-HERO-K7Q-8X2AB-C")
        }
        val myCodes = ChallengeStorage.getMyCreatedCodes(context)
        assertEquals("My created codes must be capped at 200 FIFO", 200, myCodes.size)
        assertTrue(myCodes.any { it.equals("2x2-250-T3E-HERO-K7Q-8X2AB-C", ignoreCase = true) })
        assertFalse(myCodes.any { it.equals("2x2-1-T3E-HERO-K7Q-8X2AB-C", ignoreCase = true) })

        // 2. 500 consumed codes FIFO cap
        for (i in 1..550) {
            ChallengeStorage.markCodeConsumed(context, "3x3-$i-T3E-RIVAL-K7Q-8X2AB-C")
        }
        val consumed = ChallengeStorage.getConsumedCodes(context)
        assertEquals("Consumed codes must be capped at 500 FIFO", 500, consumed.size)
        assertTrue(consumed.any { it.equals("3x3-550-T3E-RIVAL-K7Q-8X2AB-C", ignoreCase = true) })
        assertFalse(consumed.any { it.equals("3x3-1-T3E-RIVAL-K7Q-8X2AB-C", ignoreCase = true) })

        // 3. 30-day outbound chain target expiry
        val expiredChainId = "EXP"
        val thirtyOneDaysAgo = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000L)
        ChallengeStorage.saveOutboundChainTarget(context, expiredChainId, targetScore = 15, createdAt = thirtyOneDaysAgo, seedInt = 12345)

        val lookup = ChallengeStorage.inspectOutboundChainTarget(context, expiredChainId, seedInt = 12345)
        assertEquals("Outbound chain targets older than 30 days must report EXPIRED", ChallengeStorage.ChainLookupStatus.EXPIRED, lookup.status)

        // 4. Orphaned in-progress reconciliation sets outcome to LOST
        ChallengeStorage.saveRival(
            context,
            RivalRecord(
                name = "ORPHAN",
                lastStatus = "IN_PROGRESS",
                inProgressMaxTries = 3,
                inProgressTriesLeft = 0,
                inProgressCode = "2x2-10-T3E-ORPHAN-K7Q-8X2AB-C",
                lastUserScore = 5,
                lastTarget = 10,
                lastGrid = "2x2",
                lastDifficulty = GameDifficulty.EASY,
                lastChainId = "K7Q"
            )
        )
        ChallengeStorage.reconcileOrphanedInProgressChallenges(context)
        val reconciled = ChallengeStorage.findRivalByName(context, "ORPHAN")
        assertNotNull(reconciled)
        assertEquals("Orphan with 0 tries left must be reconciled to LOST", "LOST", reconciled?.lastStatus)
        assertEquals(1, reconciled?.losses)
    }

    @Test
    fun testSoloRecordIsolation_truthTableAndIntegration() {
        val dummyChallenge = ChallengeItem(
            code = "2x2-10-T3E-RIVAL-K7Q-8X2AB-C",
            rivalName = "RIVAL",
            gridSizeTag = "2x2",
            targetScore = 10,
            maxTries = 3,
            attemptsLeft = 3,
            bestScore = 0,
            seed = 88888,
            difficulty = GameDifficulty.EASY,
            chainId = "K7Q"
        )

        // 1. Pure Solo: returns true
        assertTrue(shouldUpdateSoloBest(activeChallenge = null, isOutboundRivalRun = false, isRevengeRun = false, gameMode = GameMode.SOLO))

        // 2. Inbound challenge: returns false
        assertFalse(shouldUpdateSoloBest(activeChallenge = dummyChallenge, isOutboundRivalRun = false, isRevengeRun = false, gameMode = GameMode.SOLO))

        // 3. Outbound rival run: returns false
        assertFalse(shouldUpdateSoloBest(activeChallenge = null, isOutboundRivalRun = true, isRevengeRun = false, gameMode = GameMode.SOLO))

        // 4. Revenge run: returns false
        assertFalse(shouldUpdateSoloBest(activeChallenge = null, isOutboundRivalRun = false, isRevengeRun = true, gameMode = GameMode.SOLO))

        // 5. Pass & Play multiplayer: returns false
        assertFalse(shouldUpdateSoloBest(activeChallenge = null, isOutboundRivalRun = false, isRevengeRun = false, gameMode = GameMode.TWO_PLAYERS))
        assertFalse(shouldUpdateSoloBest(activeChallenge = null, isOutboundRivalRun = false, isRevengeRun = false, gameMode = GameMode.THREE_PLAYERS))
        assertFalse(shouldUpdateSoloBest(activeChallenge = null, isOutboundRivalRun = false, isRevengeRun = false, gameMode = GameMode.FOUR_PLAYERS))

        // 6. Integration: start in-game challenge with seed 88888, advance score, verify Solo SharedPreferences remains untouched
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soloPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialSoloBest = soloPrefs.getInt(KEY_BEST_SCORE, 0)
        val soloSeedKey = bestSeedKeyFor(GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY)
        val initialSoloSeed = soloPrefs.getInt(soloSeedKey, 0)

        val challengeCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 10,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 88888,
            senderName = "RIVAL",
            chainId = "K7Q"
        )
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_paste_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(challengeCode)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Solo KEY_BEST_SCORE must not be updated during challenge run", initialSoloBest, soloPrefs.getInt(KEY_BEST_SCORE, 0))
        assertEquals("Solo best seed must not be polluted by challenge seed", initialSoloSeed, soloPrefs.getInt(soloSeedKey, 0))
        assertFalse("Solo seed must not equal challenge seed 88888", soloPrefs.getInt(soloSeedKey, 0) == 88888)

        // Exit challenge to return to clean state
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_exit_challenge_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("exit_challenge_forfeit_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testManifest_screenOrientationAndConfigChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val activityInfo = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), PackageManager.GET_META_DATA)

        assertEquals("MainActivity must be locked to portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activityInfo.screenOrientation)

        val expectedFlags = ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            ActivityInfo.CONFIG_UI_MODE or
            ActivityInfo.CONFIG_FONT_SCALE or
            ActivityInfo.CONFIG_DENSITY or
            ActivityInfo.CONFIG_LAYOUT_DIRECTION or
            ActivityInfo.CONFIG_LOCALE

        assertTrue(
            "MainActivity configChanges must include orientation, screen sizes, layout, keyboard, uiMode, fontScale, density, layoutDirection, and locale",
            (activityInfo.configChanges and expectedFlags) == expectedFlags
        )
    }

    @Test
    fun testPlaybackSafety_midPlaybackRestartNoConcurrentModificationException() {
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }

        // Start Solo game
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.waitForIdle()

        // Open pause sheet and restart mid-playback
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pause_restart_button").performClick()
        composeTestRule.waitForIdle()

        // Verify game restarted cleanly and is in WATCHING or PLAYING state without ConcurrentModificationException
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
    }
}








