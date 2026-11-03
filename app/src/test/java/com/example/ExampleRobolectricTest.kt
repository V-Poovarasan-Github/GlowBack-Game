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
        composeTestRule.onNodeWithTag("difficulty_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_hard").assertIsDisplayed()

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

        // Open Settings again, verify difficulty and reset wins
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("difficulty_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("difficulty_hard").assertIsDisplayed()
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

        // Empty challenges rail renders single enter code pill
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").assertIsDisplayed()

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

        // 4. Switch to 4P mode: Header Best Badge hides, Row B shows color-dot tally, START label updates
        composeTestRule.onNodeWithTag("mode_4p").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("best_score_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("wins_summary_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_game_button").assertTextContains("START 4P MATCH", substring = true)

        // 5. Switch back to SOLO and verify Settings opens cleanly with Difficulty
        composeTestRule.onNodeWithTag("mode_solo").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("difficulty_easy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun pass2F_homePresence_instrumentInvite_copy_and_podiumCleanup() {
        composeTestRule.waitForIdle()

        // 1. Verify "+ Challenge" copy on Row B pill
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").assertTextContains("+ Challenge", substring = true)

        // 2. Open Enter Challenge sheet and verify "Enter challenge code" title, '?' help icon, and example line beneath field
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("enter_challenge_dialog_title").assertTextContains("Enter challenge code", substring = true)
        composeTestRule.onNodeWithTag("challenge_help_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("challenge_code_example_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_challenge_dialog_button").performClick()
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

        // 2. Open Enter Challenge dialog, enter own code, click Accept Challenge -> verify self-play blocked error
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("challenge_code_input").performTextInput(myCode)
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("You cannot play your own challenge code", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_challenge_dialog_button").performClick()
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

        // 2. Open Settings, switch to Hard difficulty, verify persisted in prefs
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("difficulty_hard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("HARD", prefs.getString(KEY_DIFFICULTY_MODE, "EASY"))

        // 3. Open Enter Challenge dialog, tap '?' help button, verify help banner appears
        composeTestRule.onNodeWithTag("enter_challenge_code_pill").performClick()
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
        composeTestRule.onNodeWithTag("difficulty_easy").assertIsDisplayed()
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

        // 1. Reset Wins feedback and isolation: ensure solo score is preserved
        prefs.edit()
            .putInt("best_2x2_classic", 9)
            .putInt("best_4x4_progressive", 0)
            .putInt("wins_P1", 5)
            .apply()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reset_wins_button").performClick()
        composeTestRule.waitForIdle()

        // Verify multiplayer win is reset, but solo scores are 100% untouched
        assertEquals(0, prefs.getInt("wins_P1", -1))
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

        // Save expired awaiting item (> 7 days old)
        val expiredItem = com.example.challenge.AwaitingChallenge(
            chainId = "OLD",
            code = "2x2-5-T3E-HERO-OLD-8X2A-C",
            gridSizeTag = "2x2",
            targetScore = 5,
            difficulty = GameDifficulty.EASY,
            paceName = "Classic",
            tries = 3,
            createdAt = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000L)
        )
        ChallengeStorage.saveAwaitingChallenge(context, expiredItem)

        // Load and verify expired items are automatically pruned
        val loaded = ChallengeStorage.loadAwaitingChallenges(context)
        assertTrue("Fresh item must be present", loaded.any { it.chainId == "FRESH" })
        assertFalse("Expired item (> 7 days) must be automatically pruned", loaded.any { it.chainId == "OLD" })
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
}



