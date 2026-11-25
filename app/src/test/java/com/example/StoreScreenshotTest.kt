package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.Settings
import android.view.View
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.challenge.AwaitingChallenge
import com.example.challenge.ChallengeSeed
import com.example.challenge.ChallengeStorage
import com.example.challenge.GameDifficulty
import com.example.challenge.GridStat
import com.example.challenge.PendingOutboundChallenge
import com.example.challenge.RivalRecord
import com.example.challenge.RunStateStorage
import com.example.challenge.StoredReply
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opt-in Robolectric test suite that renders 7 genuine Play Store screenshots directly from
 * GlowBack's real Jetpack Compose screens and ViewModels using fictional demo data.
 *
 * Gated via [Assume] behind `STORE_SCREENSHOTS=true` (env var) or `-DstoreScreenshots=true`
 * so routine `gradle :app:testDebugUnitTest` runs skip all 7 tests and write zero files.
 *
 * Generate all 7 PNGs into `play_store_assets/v2/raw/` with:
 *   STORE_SCREENSHOTS=true gradle :app:testDebugUnitTest --tests com.example.StoreScreenshotTest --rerun-tasks --console=plain
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class StoreScreenshotTest {

    companion object {
        private const val PLAYER_NAME = "NOVA"
        private const val DIALOG_SCRIM_ALPHA = 153 // 60% black, matching Android Dialog dimAmount = 0.6f

        init {
            MainActivity.skipSplashForTesting = true
        }

        private fun isScreenshotGenerationEnabled(): Boolean {
            val envFlag = System.getenv("STORE_SCREENSHOTS")
            val propFlag = System.getProperty("storeScreenshots")
            return envFlag.equals("true", ignoreCase = true) ||
                propFlag.equals("true", ignoreCase = true)
        }
    }

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(TestRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    Assume.assumeTrue(
                        "Skipping StoreScreenshotTest (set STORE_SCREENSHOTS=true to render Play Store screenshots)",
                        isScreenshotGenerationEnabled()
                    )
                    base.evaluate()
                }
            }
        })
        .around(TestRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    seedDemoStateBeforeLaunch()
                    try {
                        base.evaluate()
                    } finally {
                        val context = ApplicationProvider.getApplicationContext<Context>()
                        Settings.Global.putFloat(
                            context.contentResolver,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                            1.0f
                        )
                    }
                }
            }
        })
        .around(composeTestRule)

    private fun seedDemoStateBeforeLaunch() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Default to normal motion (1.0f) so gameplay pad glow animations are untouched.
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )

        // Reset all persisted state for deterministic renders.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        RunStateStorage.clearRunState(context)
        context.getSharedPreferences("glowback_challenges_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("glowback_challenge_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        // Suppress first-launch banners/onboarding and pre-seed believable Solo & Multiplayer records.
        prefs.edit()
            .putBoolean(KEY_INSTRUMENT_INVITE_DISMISSED, true)
            .putBoolean(KEY_CHALLENGE_NUDGE_SEEN, true)
            .putBoolean(KEY_REPLY_HINT_DONE, true)
            .putBoolean(KEY_SCORE5_NUDGE_SEEN, true)
            .putBoolean(KEY_SCORES_MIGRATED_V2, true)
            .putInt(KEY_LAUNCH_COUNT, 12)
            .putInt("longest_survival_2p", 12)
            .putInt("longest_survival_3p", 9)
            .putInt("longest_survival_4p", 7)
            .putInt(playerWinsKey(0), 7)
            .putInt(playerWinsKey(1), 5)
            .putInt(playerWinsKey(2), 3)
            .putInt(playerWinsKey(3), 2)
            .commit()

        // Seed all 4 grid sizes x 2 pace modes x 2 difficulties with fixed seeds.
        val soloRecords = listOf(
            Triple(GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.EASY) to (19 to 118421),
            Triple(GridSize.TWO_BY_TWO, PaceMode.PROGRESSIVE, GameDifficulty.EASY) to (15 to 229517),
            Triple(GridSize.TWO_BY_TWO, PaceMode.CLASSIC, GameDifficulty.HARD) to (13 to 337109),
            Triple(GridSize.TWO_BY_TWO, PaceMode.PROGRESSIVE, GameDifficulty.HARD) to (10 to 448213),

            Triple(GridSize.THREE_BY_THREE, PaceMode.CLASSIC, GameDifficulty.EASY) to (14 to 559301),
            Triple(GridSize.THREE_BY_THREE, PaceMode.PROGRESSIVE, GameDifficulty.EASY) to (11 to 661423),
            Triple(GridSize.THREE_BY_THREE, PaceMode.CLASSIC, GameDifficulty.HARD) to (10 to 772547),
            Triple(GridSize.THREE_BY_THREE, PaceMode.PROGRESSIVE, GameDifficulty.HARD) to (8 to 883619),

            Triple(GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.EASY) to (12 to 994721),
            Triple(GridSize.FOUR_BY_FOUR, PaceMode.PROGRESSIVE, GameDifficulty.EASY) to (9 to 1105837),
            Triple(GridSize.FOUR_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.HARD) to (11 to 1216949),
            Triple(GridSize.FOUR_BY_FOUR, PaceMode.PROGRESSIVE, GameDifficulty.HARD) to (7 to 1327053),

            Triple(GridSize.FIVE_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.EASY) to (9 to 1438169),
            Triple(GridSize.FIVE_BY_FOUR, PaceMode.PROGRESSIVE, GameDifficulty.EASY) to (7 to 1549271),
            Triple(GridSize.FIVE_BY_FOUR, PaceMode.CLASSIC, GameDifficulty.HARD) to (6 to 1650383),
            Triple(GridSize.FIVE_BY_FOUR, PaceMode.PROGRESSIVE, GameDifficulty.HARD) to (5 to 1761497)
        )
        for ((board, scoreAndSeed) in soloRecords) {
            writeBestScore(
                prefs = prefs,
                gridSize = board.first,
                paceMode = board.second,
                difficulty = board.third,
                score = scoreAndSeed.first,
                seed = scoreAndSeed.second
            )
        }

        // Configure fictional player identity and silence blinking tooltip.
        ChallengeStorage.setMyName(context, PLAYER_NAME)
        ChallengeStorage.markCardExpandTooltipSeen(context)
        ChallengeStorage.markCardExpandTooltipBlinkDone(context)

        // Anchor relative timestamps deterministically so time-ago labels are stable across reruns.
        val baseNow = System.currentTimeMillis()
        val minuteMs = 60_000L
        val hourMs = 60 * minuteMs
        val dayMs = 24 * hourMs

        val nishaReplyCode = ChallengeSeed.formatChallengeCode(
            gridTag = "5x4",
            targetScore = 10,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 2145901,
            senderName = PLAYER_NAME,
            chainId = "N8X4",
            isRevenge = true
        )
        val rohanPendingCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 13,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 2256817,
            senderName = PLAYER_NAME,
            chainId = "R5V6",
            isRevenge = false
        )
        val broadcastCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 6,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 2367923,
            senderName = PLAYER_NAME,
            chainId = "AW9Q",
            isRevenge = false
        )

        val demoRivals = listOf(
            RivalRecord(
                name = "ARJUN",
                wins = 5,
                losses = 2,
                ties = 0,
                matches = 7,
                lastGrid = "3x3",
                lastDifficulty = GameDifficulty.EASY,
                lastTarget = 10,
                lastUserScore = 14,
                lastStatus = "WON",
                lastChainId = "A4K9",
                updatedAt = baseNow - 25 * minuteMs,
                gridStats = mapOf(
                    "3x3" to GridStat(
                        wins = 3,
                        losses = 1,
                        ties = 0,
                        lastTarget = 10,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 25 * minuteMs
                    ),
                    "2x2" to GridStat(
                        wins = 2,
                        losses = 1,
                        ties = 0,
                        lastTarget = 8,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 18 * hourMs
                    )
                )
            ),
            RivalRecord(
                name = "MEERA",
                wins = 3,
                losses = 4,
                ties = 0,
                matches = 7,
                lastGrid = "4x4",
                lastDifficulty = GameDifficulty.HARD,
                lastTarget = 11,
                lastUserScore = 9,
                lastStatus = "LOST",
                lastChainId = "M7R2",
                updatedAt = baseNow - 2 * hourMs,
                gridStats = mapOf(
                    "4x4" to GridStat(
                        wins = 1,
                        losses = 2,
                        ties = 0,
                        lastTarget = 11,
                        lastDifficulty = GameDifficulty.HARD,
                        lastStatus = "LOST",
                        lastPlayedAt = baseNow - 2 * hourMs
                    ),
                    "3x3" to GridStat(
                        wins = 1,
                        losses = 1,
                        ties = 0,
                        lastTarget = 9,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 14 * hourMs
                    ),
                    "2x2" to GridStat(
                        wins = 1,
                        losses = 0,
                        ties = 0,
                        lastTarget = 7,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 2 * dayMs
                    ),
                    "5x4" to GridStat(
                        wins = 0,
                        losses = 1,
                        ties = 0,
                        lastTarget = 6,
                        lastDifficulty = GameDifficulty.HARD,
                        lastStatus = "LOST",
                        lastPlayedAt = baseNow - 3 * dayMs
                    )
                )
            ),
            RivalRecord(
                name = "KABIR",
                wins = 2,
                losses = 2,
                ties = 1,
                matches = 5,
                lastGrid = "3x3",
                lastDifficulty = GameDifficulty.HARD,
                lastTarget = 8,
                lastUserScore = 8,
                lastStatus = "TIED",
                lastChainId = "K3P8",
                updatedAt = baseNow - 5 * hourMs,
                gridStats = mapOf(
                    "3x3" to GridStat(
                        wins = 1,
                        losses = 1,
                        ties = 1,
                        lastTarget = 8,
                        lastDifficulty = GameDifficulty.HARD,
                        lastStatus = "TIED",
                        lastPlayedAt = baseNow - 5 * hourMs
                    ),
                    "4x4" to GridStat(
                        wins = 1,
                        losses = 1,
                        ties = 0,
                        lastTarget = 7,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 2 * dayMs
                    )
                )
            ),
            RivalRecord(
                name = "NISHA",
                wins = 4,
                losses = 1,
                ties = 0,
                matches = 5,
                lastGrid = "5x4",
                lastDifficulty = GameDifficulty.EASY,
                lastTarget = 7,
                lastUserScore = 10,
                lastStatus = "WON",
                lastChainId = "N8X4",
                updatedAt = baseNow - 1 * dayMs,
                gridStats = mapOf(
                    "5x4" to GridStat(
                        wins = 2,
                        losses = 0,
                        ties = 0,
                        lastTarget = 7,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 30 * minuteMs
                    ),
                    "3x3" to GridStat(
                        wins = 2,
                        losses = 1,
                        ties = 0,
                        lastTarget = 9,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 1 * dayMs
                    )
                ),
                lastReplyCode = nishaReplyCode,
                isReplySent = false,
                lastReplies = mapOf(
                    "5x4" to StoredReply(
                        code = nishaReplyCode,
                        mintedAt = baseNow - 30 * minuteMs,
                        copiedAt = null
                    )
                )
            ),
            RivalRecord(
                name = "ROHAN",
                wins = 1,
                losses = 3,
                ties = 0,
                matches = 4,
                lastGrid = "2x2",
                lastDifficulty = GameDifficulty.EASY,
                lastTarget = 12,
                lastUserScore = 9,
                lastStatus = "LOST",
                lastChainId = "R5V6",
                updatedAt = baseNow - 2 * dayMs,
                gridStats = mapOf(
                    "2x2" to GridStat(
                        wins = 1,
                        losses = 2,
                        ties = 0,
                        lastTarget = 12,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "LOST",
                        lastPlayedAt = baseNow - 2 * dayMs
                    ),
                    "3x3" to GridStat(
                        wins = 0,
                        losses = 1,
                        ties = 0,
                        lastTarget = 8,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "LOST",
                        lastPlayedAt = baseNow - 4 * dayMs
                    )
                ),
                pendingOutbounds = listOf(
                    PendingOutboundChallenge(
                        gridTag = "2x2",
                        difficulty = GameDifficulty.EASY,
                        target = 13,
                        tries = 3,
                        code = rohanPendingCode,
                        mintedAt = baseNow - 40 * minuteMs,
                        rivalName = "ROHAN",
                        seed = 2256817,
                        chainId = "R5V6"
                    )
                )
            ),
            RivalRecord(
                name = "ZARA",
                wins = 6,
                losses = 3,
                ties = 0,
                matches = 9,
                lastGrid = "4x4",
                lastDifficulty = GameDifficulty.EASY,
                lastTarget = 13,
                lastUserScore = 15,
                lastStatus = "WON",
                lastChainId = "Z2M7",
                updatedAt = baseNow - 3 * dayMs,
                gridStats = mapOf(
                    "4x4" to GridStat(
                        wins = 3,
                        losses = 2,
                        ties = 0,
                        lastTarget = 13,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 3 * dayMs
                    ),
                    "3x3" to GridStat(
                        wins = 3,
                        losses = 1,
                        ties = 0,
                        lastTarget = 11,
                        lastDifficulty = GameDifficulty.EASY,
                        lastStatus = "WON",
                        lastPlayedAt = baseNow - 5 * dayMs
                    )
                )
            )
        )

        for (rival in demoRivals) {
            ChallengeStorage.saveRival(context, rival)
        }

        ChallengeStorage.saveAwaitingChallenge(
            context,
            AwaitingChallenge(
                chainId = "AW9Q",
                code = broadcastCode,
                gridSizeTag = "3x3",
                targetScore = 6,
                difficulty = GameDifficulty.EASY,
                paceName = "Classic",
                tries = 3,
                createdAt = baseNow - 35 * minuteMs,
                rivalName = null
            )
        )
    }

    private fun dismissOnboardingIfVisible() {
        composeTestRule.waitForIdle()
        runCatching {
            composeTestRule.onNodeWithTag("onboarding_got_it_button").performClick()
            composeTestRule.waitForIdle()
        }
    }

    private fun enableReducedMotionForHallOfFame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    private fun resolveRawOutputDir(): File {
        val overrideDir = System.getenv("STORE_SCREENSHOTS_OUT_DIR")?.takeIf { it.isNotBlank() }
        val targetDir = if (overrideDir != null) {
            File(overrideDir).canonicalFile
        } else {
            val userDir = File(System.getProperty("user.dir") ?: ".")
            val candidates = listOf(
                File(userDir, "../play_store_assets/v2/raw"),
                File(userDir, "play_store_assets/v2/raw")
            )
            val existingParent = candidates.firstOrNull { it.parentFile?.parentFile?.exists() == true }
            (existingParent ?: candidates.first()).canonicalFile
        }
        if (!targetDir.exists()) {
            assertTrue("Failed to create raw screenshots directory: $targetDir", targetDir.mkdirs())
        }
        return targetDir
    }

    private fun drawViewToBitmap(view: View): Bitmap {
        assertTrue("View has invalid size: ${view.width}x${view.height}", view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    /**
     * Captures the real Activity [View] hierarchy rendered by Robolectric's native Skia engine
     * (`@GraphicsMode(NATIVE)`). Works synchronously at the exact active Compose frame without
     * hitting `WindowCapture.forceRedraw()`'s 2s timeout on SDK 36 Robolectric.
     */
    private fun captureActivityBitmap(): Bitmap {
        lateinit var bitmap: Bitmap
        composeTestRule.activityRule.scenario.onActivity { activity ->
            bitmap = drawViewToBitmap(activity.window.decorView)
        }
        return bitmap
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAllWindowRootViews(): List<View> {
        val wmgClass = Class.forName("android.view.WindowManagerGlobal")
        val instance = wmgClass.getMethod("getInstance").invoke(null)
        val viewsField = wmgClass.getDeclaredField("mViews").apply { isAccessible = true }
        return (viewsField.get(instance) as? List<View>)?.toList().orEmpty()
    }

    /**
     * Captures a screen when a Compose [androidx.compose.ui.window.Dialog] is open.
     * Draws the real Activity window [View] and the real Dialog window [View] from
     * `WindowManagerGlobal` via Robolectric's native Skia renderer, then composites the Dialog
     * bitmap over the Activity bitmap with Android's standard 60% translucent black dim scrim
     * (`Color.argb(153, 0, 0, 0)`). Zero UI pixels are hand-drawn.
     */
    private fun captureActivityWithDialogOverlay(label: String): Bitmap {
        lateinit var activityBmp: Bitmap
        lateinit var dialogBmp: Bitmap

        composeTestRule.activityRule.scenario.onActivity { activity ->
            val activityDecor = activity.window.decorView
            activityBmp = drawViewToBitmap(activityDecor)

            val allViews = getAllWindowRootViews()
            val dialogDecor = allViews.lastOrNull { it !== activityDecor && it.width > 0 && it.height > 0 }
            assertTrue(
                "$label expected an attached Dialog window view in WindowManagerGlobal (found ${allViews.size} views)",
                dialogDecor != null
            )
            dialogBmp = drawViewToBitmap(dialogDecor!!)
        }

        val combined = Bitmap.createBitmap(activityBmp.width, activityBmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawBitmap(activityBmp, 0f, 0f, null)

        val scrimPaint = Paint().apply {
            color = Color.argb(DIALOG_SCRIM_ALPHA, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, combined.width.toFloat(), combined.height.toFloat(), scrimPaint)

        val drawLeft: Float
        val drawTop: Float
        if (dialogBmp.width == activityBmp.width && dialogBmp.height == activityBmp.height) {
            drawLeft = 0f
            drawTop = 0f
        } else {
            drawLeft = ((activityBmp.width - dialogBmp.width) / 2f).coerceAtLeast(0f)
            drawTop = ((activityBmp.height - dialogBmp.height) / 2f).coerceAtLeast(0f)
        }
        canvas.drawBitmap(dialogBmp, drawLeft, drawTop, null)

        println(
            "STORE_SCREENSHOT_COMPOSITE $label activity=${activityBmp.width}x${activityBmp.height} " +
                "dialog=${dialogBmp.width}x${dialogBmp.height} offset=(${drawLeft.toInt()},${drawTop.toInt()}) " +
                "scrimAlpha=$DIALOG_SCRIM_ALPHA"
        )
        return combined
    }

    private fun saveAndVerifyPng(bitmap: Bitmap, fileName: String) {
        assertTrue(
            "$fileName width must be >= 1000 px (got ${bitmap.width}x${bitmap.height})",
            bitmap.width >= 1000
        )

        // Verify the rendered image is genuinely non-blank and visually rich.
        val sampleStepX = (bitmap.width / 80).coerceAtLeast(1)
        val sampleStepY = (bitmap.height / 140).coerceAtLeast(1)
        val colorCounts = HashMap<Int, Int>(512)
        var totalSampled = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y) and 0x00FFFFFF
                colorCounts[pixel] = (colorCounts[pixel] ?: 0) + 1
                totalSampled++
                x += sampleStepX
            }
            y += sampleStepY
        }
        val distinctColors = colorCounts.size
        val maxColorFraction = (colorCounts.values.maxOrNull() ?: 0).toDouble() / totalSampled.coerceAtLeast(1)

        assertTrue(
            "$fileName appears blank or flat: only $distinctColors distinct RGB colors sampled",
            distinctColors >= 50
        )
        assertTrue(
            "$fileName is dominated by one color (${"%.1f".format(maxColorFraction * 100)}%)",
            maxColorFraction < 0.95
        )

        val outFile = File(resolveRawOutputDir(), fileName)
        FileOutputStream(outFile).use { fos ->
            assertTrue("PNG compression failed for $fileName", bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos))
            fos.flush()
        }
        assertTrue("Saved PNG missing: ${outFile.absolutePath}", outFile.exists() && outFile.length() > 1024L)
        println(
            "STORE_SCREENSHOT $fileName ${bitmap.width}x${bitmap.height} " +
                "bytes=${outFile.length()} distinctColors=$distinctColors " +
                "maxColorPct=${"%.1f".format(maxColorFraction * 100)} path=${outFile.absolutePath}"
        )
    }

    private fun collapseRivalCardIfExpanded(name: String) {
        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("rival_card_$name"))
        composeTestRule.waitForIdle()
        val cardNode = composeTestRule.onNodeWithTag("rival_card_$name").fetchSemanticsNode()
        val stateDesc = cardNode.config.getOrElseNullable(SemanticsProperties.StateDescription) { null }
        if (stateDesc == "Expanded") {
            composeTestRule.onNodeWithTag("rival_chevron_$name").performClick()
            composeTestRule.waitForIdle()
        }
    }

    private fun collapseAwaitingCardIfExpanded(chainId: String) {
        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("awaiting_card_$chainId"))
        composeTestRule.waitForIdle()
        val cardNode = composeTestRule.onNodeWithTag("awaiting_card_$chainId").fetchSemanticsNode()
        val stateDesc = cardNode.config.getOrElseNullable(SemanticsProperties.StateDescription) { null }
        if (stateDesc == "Expanded") {
            composeTestRule.onNodeWithTag("awaiting_chevron_$chainId").performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun render_01_gameplay() {
        // Keep ANIMATOR_DURATION_SCALE = 1.0f (normal animations) so pad glow is authentic.
        dismissOnboardingIfVisible()

        // Switch to 3x3 Classic Easy.
        composeTestRule.onNodeWithTag("config_pill_grid").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_3x3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        // Step the coroutine + animation clock explicitly so every round advances deterministically.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("start_game_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(1400L) // Round 1 WATCHING (450 + 600 = 1050ms) -> PLAYING

        val runSeed = ChallengeSeed.lastGeneratedRandomSeed
        assertTrue("Expected non-zero generated Solo seed", runSeed > 0)
        val pads = padsForGridSize(GridSize.THREE_BY_THREE)
        val targetRound = 10
        val fullSequence = ChallengeSeed.generateSequenceForRound(
            seed = runSeed,
            padCount = pads.size,
            difficulty = GameDifficulty.EASY,
            round = targetRound
        )

        // Play rounds 1..8 cleanly to reach Round 9 PLAYING (score = 8).
        for (round in 1..8) {
            composeTestRule.onNodeWithTag(pads[0].testTagId).assertIsEnabled()
            for (step in 0 until round) {
                composeTestRule.onNodeWithTag(pads[fullSequence[step]].testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(200L)
            }
            // Advance past round clear celebration (640ms) + next round WATCHING (450 + (round+1)*600).
            val nextWatchingMs = 640L + 450L + (round + 1) * 600L + 250L
            composeTestRule.mainClock.advanceTimeBy(nextWatchingMs)
        }

        // Play Round 9 (9 taps) to reach Score 9, then step into Round 10 WATCHING.
        composeTestRule.onNodeWithTag(pads[0].testTagId).assertIsEnabled()
        for (step in 0 until 8) {
            composeTestRule.onNodeWithTag(pads[fullSequence[step]].testTagId).performClick()
            composeTestRule.mainClock.advanceTimeBy(200L)
        }
        composeTestRule.onNodeWithTag(pads[fullSequence[8]].testTagId).performClick()

        // Prefer catching a vibrant inner/center pad during Round 10 playback if present.
        val preferredPadIds = setOf(4, 1, 3, 5, 7)
        val targetStepIndex = fullSequence.indexOfFirst { it in preferredPadIds }.takeIf { it >= 0 } ?: 0
        val targetPadTag = pads[fullSequence[targetStepIndex]].testTagId

        // Advance past Round 9 clear celebration (640ms) + Round 10 WATCHING lead-in (450ms)
        // + preceding steps (600ms each), stopping 160ms early so we always catch the rising edge.
        val leadMs = (640L + 450L + targetStepIndex * 600L - 160L).coerceAtLeast(0L)
        var elapsed = 0L
        while (elapsed < leadMs) {
            val delta = minOf(50L, leadMs - elapsed)
            composeTestRule.mainClock.advanceTimeBy(delta)
            elapsed += delta
        }

        // Wait until targetPadTag is idle first (in case an earlier identical pad was still lit),
        // then fine-step in 16ms frame increments until targetPadTag enters the 'lit' state.
        var litFound = false
        for (attempt in 0 until 80) {
            val node = composeTestRule.onNodeWithTag(targetPadTag).fetchSemanticsNode()
            if (node.config.getOrElseNullable(SemanticsProperties.StateDescription) { null } == "lit") {
                litFound = true
                break
            }
            composeTestRule.mainClock.advanceTimeBy(16L)
        }
        assertTrue("Expected $targetPadTag to enter 'lit' state during Round 10 WATCHING", litFound)

        // Advance 144ms (9 x 16ms frames) from the rising edge of the 400ms flash so
        // animateColorAsState(85ms) and spring scale peak cleanly while remaining firmly 'lit'.
        repeat(9) {
            composeTestRule.mainClock.advanceTimeBy(16L)
        }

        composeTestRule.onNodeWithTag(targetPadTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "lit"))

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "01_gameplay.png")
    }

    @Test
    fun render_02_setup() {
        dismissOnboardingIfVisible()

        // Select 3x3 so the Home pill reflects 3x3 · Classic, then open SettingsSheetDialog so
        // the 4 grid sizes + Pace selector appear over the visible Home [Easy | Hard] slider.
        composeTestRule.onNodeWithTag("config_pill_grid").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_3x3").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_sheet_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_size_2x2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_size_3x3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_size_4x4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grid_size_5x4").assertIsDisplayed()

        val bitmap = captureActivityWithDialogOverlay("02_setup")
        saveAndVerifyPng(bitmap, "02_setup.png")
    }

    @Test
    fun render_03_share() {
        dismissOnboardingIfVisible()
        enableReducedMotionForHallOfFame()

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("rival_card_ARJUN"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_dynamic_button_ARJUN").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_recommendation_chip_3x3_easy")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tries_selector_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_challenge_header_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("targeted_share_note_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("targeted_share_info_icon").assertIsDisplayed()

        val bitmap = captureActivityWithDialogOverlay("03_share")
        saveAndVerifyPng(bitmap, "03_share.png")
    }

    @Test
    fun render_04_hall_of_fame() {
        dismissOnboardingIfVisible()
        enableReducedMotionForHallOfFame()

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        // Confirm blinking tooltip is hidden.
        composeTestRule.onNodeWithText("Tap card to expand/collapse", substring = true)
            .assertDoesNotExist()

        // Collapse broadcast awaiting card and secondary rivals; keep ARJUN expanded at the top.
        collapseAwaitingCardIfExpanded("AW9Q")
        for (name in listOf("MEERA", "KABIR", "NISHA", "ROHAN", "ZARA")) {
            collapseRivalCardIfExpanded(name)
        }

        // Scroll back to the top so HALL OF FAME header + awaiting + rivals are framed cleanly.
        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("awaiting_card_AW9Q"))
        composeTestRule.waitForIdle()

        // Assert expanded ARJUN has bin icon (not 3-dot) and collapsed MEERA has 3-dot (not bin).
        composeTestRule.onNodeWithTag("rival_delete_button_ARJUN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_menu_button_ARJUN").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rival_menu_button_MEERA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_delete_button_MEERA").assertDoesNotExist()

        // Verify header chevron and bin icon sit inside card bounds without overlapping.
        val arjunCardBounds = composeTestRule.onNodeWithTag("rival_card_ARJUN").getUnclippedBoundsInRoot()
        val arjunChevronBounds = composeTestRule.onNodeWithTag("rival_chevron_ARJUN").getUnclippedBoundsInRoot()
        val arjunDeleteBounds = composeTestRule.onNodeWithTag("rival_delete_button_ARJUN").getUnclippedBoundsInRoot()
        assertTrue(
            "ARJUN chevron and delete button overlap: $arjunChevronBounds vs $arjunDeleteBounds",
            arjunChevronBounds.right <= arjunDeleteBounds.left
        )
        assertTrue(
            "ARJUN delete button exceeds card bounds: $arjunDeleteBounds vs $arjunCardBounds",
            arjunDeleteBounds.right <= arjunCardBounds.right
        )

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "04_hall_of_fame.png")
    }

    @Test
    fun render_05_revenge() {
        dismissOnboardingIfVisible()
        enableReducedMotionForHallOfFame()

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Tap card to expand/collapse", substring = true)
            .assertDoesNotExist()

        // Collapse awaiting card and all rivals except MEERA so MEERA's Get Revenge card is front and center.
        collapseAwaitingCardIfExpanded("AW9Q")
        for (name in listOf("ARJUN", "KABIR", "NISHA", "ROHAN", "ZARA")) {
            collapseRivalCardIfExpanded(name)
        }

        // Scroll to top so the header, collapsed ARJUN, and expanded MEERA (Get Revenge) sit cleanly in frame.
        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("awaiting_card_AW9Q"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rival_dynamic_button_MEERA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_delete_button_MEERA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_menu_button_MEERA").assertDoesNotExist()

        val meeraCardBounds = composeTestRule.onNodeWithTag("rival_card_MEERA").getUnclippedBoundsInRoot()
        val meeraChevronBounds = composeTestRule.onNodeWithTag("rival_chevron_MEERA").getUnclippedBoundsInRoot()
        val meeraDeleteBounds = composeTestRule.onNodeWithTag("rival_delete_button_MEERA").getUnclippedBoundsInRoot()
        assertTrue(
            "MEERA chevron and delete button overlap: $meeraChevronBounds vs $meeraDeleteBounds",
            meeraChevronBounds.right <= meeraDeleteBounds.left
        )
        assertTrue(
            "MEERA delete button exceeds card bounds: $meeraDeleteBounds vs $meeraCardBounds",
            meeraDeleteBounds.right <= meeraCardBounds.right
        )

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "05_revenge.png")
    }

    @Test
    fun render_06_records() {
        dismissOnboardingIfVisible()
        enableReducedMotionForHallOfFame()

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_records").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("records_tab_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("records_grid_3×3").performClick()
        composeTestRule.waitForIdle()

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "06_records.png")
    }

    @Test
    fun render_07_offline() {
        dismissOnboardingIfVisible()

        // Switch Home board to 4x4 Hard for a crisp 16-pad hero board.
        composeTestRule.onNodeWithTag("config_pill_grid").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("grid_size_4x4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_done_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("difficulty_toggle_hard").performClick()
        composeTestRule.waitForIdle()

        assertEquals(16, padsForGridSize(GridSize.FOUR_BY_FOUR).size)
        composeTestRule.onNodeWithTag("pad_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pad_15").assertIsDisplayed()

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "07_offline.png")
    }

    /**
     * Launches a real incoming 3x3 Easy 1-try challenge from ARJUN (target 6, fixed seed)
     * through the real Hall of Fame "Paste Code" -> EnterChallengeDialog flow, plays
     * [roundsToClear] rounds cleanly at normal animation speed (ANIMATOR_DURATION_SCALE = 1.0f),
     * handles VictoryDecisionDialog when beating the target, and intentionally misses on the
     * following round so the app transitions naturally to the real Game Over screen.
     */
    private fun launchAndPlayIncomingArjunChallenge(
        roundsToClear: Int,
        expectVictoryDecisionAfterRound: Int? = null
    ) {
        val targetScore = 6
        val fixedSeed = 2589247
        val challengeCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = targetScore,
            tries = 1,
            difficulty = GameDifficulty.EASY,
            seed = fixedSeed,
            senderName = "ARJUN",
            chainId = "A4K9",
            isRevenge = false
        )

        // Keep ANIMATOR_DURATION_SCALE = 1.0f (normal animations) for gameplay result shots.
        dismissOnboardingIfVisible()

        // Navigate to Hall of Fame -> Paste Code -> EnterChallengeDialog.
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("challenge_code_input").performTextReplacement(challengeCode)
        composeTestRule.waitForIdle()

        // Pause clock so we control the 150ms submit flash + challenge playback deterministically.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("submit_challenge_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(400L)

        // Dismiss the Ready vs ARJUN gate to start Round 1 WATCHING.
        composeTestRule.onNodeWithTag("challenge_ready_gate_button").assertIsDisplayed().performClick()
        composeTestRule.mainClock.advanceTimeBy(1400L) // Round 1 WATCHING (450 + 600 = 1050ms) -> PLAYING

        val pads = padsForGridSize(GridSize.THREE_BY_THREE)
        val failRound = roundsToClear + 1
        val fullSequence = ChallengeSeed.generateSequenceForRound(
            seed = fixedSeed,
            padCount = pads.size,
            difficulty = GameDifficulty.EASY,
            round = failRound
        )

        for (round in 1..roundsToClear) {
            composeTestRule.onNodeWithTag(pads[0].testTagId).assertIsEnabled()
            for (step in 0 until round) {
                composeTestRule.onNodeWithTag(pads[fullSequence[step]].testTagId).performClick()
                composeTestRule.mainClock.advanceTimeBy(200L)
            }

            if (expectVictoryDecisionAfterRound != null && round == expectVictoryDecisionAfterRound) {
                // VictoryDecisionDialog opens immediately when beating targetScore; tap "Keep Playing"
                // so the player finishes on the real Game Over screen showing "Challenge Won! 🎉".
                composeTestRule.mainClock.advanceTimeBy(200L)
                composeTestRule.onNodeWithTag("victory_decision_dialog").assertIsDisplayed()
                composeTestRule.onNodeWithTag("victory_decision_keep_playing_button").performClick()
                // Advance past celebration (640ms) + next round WATCHING (450 + (round+1)*600).
                val nextWatchingMs = 640L + 450L + (round + 1) * 600L + 350L
                composeTestRule.mainClock.advanceTimeBy(nextWatchingMs)
            } else {
                val nextWatchingMs = 640L + 450L + (round + 1) * 600L + 250L
                composeTestRule.mainClock.advanceTimeBy(nextWatchingMs)
            }
        }

        // Now in failRound PLAYING: deliberately tap a wrong pad to conclude the 1-try duel.
        composeTestRule.onNodeWithTag(pads[0].testTagId).assertIsEnabled()
        val wrongPadIdx = (fullSequence[0] + 1) % pads.size
        composeTestRule.onNodeWithTag(pads[wrongPadIdx].testTagId).performClick()

        // Settle wrong-pad shake, red flash, and banner accent color transition completely.
        repeat(80) {
            composeTestRule.mainClock.advanceTimeBy(16L)
        }
    }

    @Test
    fun render_08_waiting() {
        dismissOnboardingIfVisible()
        enableReducedMotionForHallOfFame()

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Tap card to expand/collapse", substring = true)
            .assertDoesNotExist()

        // Keep "Unknown challenger" (AW9Q) expanded at the top; collapse all rival cards below.
        for (name in listOf("ARJUN", "MEERA", "KABIR", "NISHA", "ROHAN", "ZARA")) {
            collapseRivalCardIfExpanded(name)
        }

        // Scroll all the way to the top action row so AWAITING CHALLENGES (1) + expanded Unknown challenger
        // sit cleanly framed in the upper half of the screen (matching captions.json y=520).
        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("rivalry_paste_button"))
        composeTestRule.waitForIdle()

        // Verify expanded awaiting card state and expected semantics before capture.
        composeTestRule.onNodeWithText("Unknown challenger", substring = true).assertIsDisplayed()
        assertTrue(
            "Expected 'waiting' text in semantics tree",
            composeTestRule.onAllNodesWithText("waiting", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        composeTestRule.onNodeWithText("Paste Reply Code", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy Code Again", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("awaiting_cancel_button_AW9Q").assertIsDisplayed()

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "08_waiting.png")
    }

    @Test
    fun render_09_reply_ready() {
        dismissOnboardingIfVisible()
        enableReducedMotionForHallOfFame()

        // Move NISHA to the top of RIVAL BATTLES for this shot only (no effect on 01-07).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nisha = requireNotNull(ChallengeStorage.findRivalByName(context, "NISHA")) {
            "Expected seeded rival NISHA"
        }
        ChallengeStorage.saveRival(context, nisha.copy(updatedAt = System.currentTimeMillis() + 60_000L))

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Tap card to expand/collapse", substring = true)
            .assertDoesNotExist()

        // Collapse awaiting card and all other rivals, leaving NISHA expanded at #1.
        collapseAwaitingCardIfExpanded("AW9Q")
        for (name in listOf("ARJUN", "MEERA", "KABIR", "ROHAN", "ZARA")) {
            collapseRivalCardIfExpanded(name)
        }

        // Scroll to the collapsed awaiting card, then perform a slow (zero-fling) drag so NISHA's
        // expanded card sits right at the top of the Rivalry list under the tabs.
        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("awaiting_card_AW9Q"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rivalry_tab_content").performTouchInput {
            swipeUp(startY = bottom * 0.70f, endY = bottom * 0.42f, durationMillis = 900L)
        }
        composeTestRule.waitForIdle()

        // Ensure NISHA is expanded before asserting her unsent reply button.
        val nishaState = composeTestRule.onNodeWithTag("rival_card_NISHA")
            .fetchSemanticsNode()
            .config
            .getOrElseNullable(SemanticsProperties.StateDescription) { null }
        if (nishaState == "Collapsed") {
            composeTestRule.onNodeWithTag("rival_chevron_NISHA").performClick()
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("rival_card_NISHA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_delete_button_NISHA").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rival_send_reply_button_NISHA").assertIsDisplayed()
        assertTrue(
            "Expected 'reply ready' label in semantics tree",
            composeTestRule.onAllNodesWithText("reply ready", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        composeTestRule.onNodeWithText("Copy Reply Code", substring = true).assertIsDisplayed()

        val nishaBounds = composeTestRule.onNodeWithTag("rival_card_NISHA").getUnclippedBoundsInRoot()
        println("STORE_SCREENSHOT_LAYOUT 09_reply_ready nishaTopDp=${nishaBounds.top.value} nishaBottomDp=${nishaBounds.bottom.value}")

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "09_reply_ready.png")
    }

    @Test
    fun render_10_paste_code() {
        dismissOnboardingIfVisible()
        enableReducedMotionForHallOfFame()

        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()

        // Keep Hall of Fame background clean (AW9Q collapsed, ARJUN expanded).
        collapseAwaitingCardIfExpanded("AW9Q")
        for (name in listOf("MEERA", "KABIR", "NISHA", "ROHAN", "ZARA")) {
            collapseRivalCardIfExpanded(name)
        }
        composeTestRule.onNodeWithTag("rivalry_tab_content")
            .performScrollToNode(hasTestTag("awaiting_card_AW9Q"))
        composeTestRule.waitForIdle()

        // Open the real "Enter challenge code" dialog (EnterChallengeDialog).
        composeTestRule.onNodeWithTag("rivalry_paste_button").performClick()
        composeTestRule.waitForIdle()

        // Generate a genuine 7-segment challenge code via the app's own ChallengeSeed generator
        // (3-char chain ID matching the dialog's format example so all 27 chars fit cleanly).
        val meeraChallengeCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 9,
            tries = 3,
            difficulty = GameDifficulty.EASY,
            seed = 2478131,
            senderName = "MEERA",
            chainId = "M7R",
            isRevenge = false
        )
        assertTrue(
            "Generated challenge code must parse cleanly",
            ChallengeSeed.parseChallengeCode(meeraChallengeCode) != null
        )

        composeTestRule.onNodeWithTag("challenge_code_input").performTextReplacement(meeraChallengeCode)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("enter_challenge_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("enter_challenge_dialog_title")
            .assertTextContains("Enter challenge code", substring = true)
        composeTestRule.onNodeWithTag("challenge_code_input")
            .assertTextContains(meeraChallengeCode.uppercase(), substring = true)
        composeTestRule.onNodeWithTag("submit_challenge_button").assertIsDisplayed()
        println("STORE_SCREENSHOT_CODE 10_paste_code code=${meeraChallengeCode.uppercase()}")

        val bitmap = captureActivityWithDialogOverlay("10_paste_code")
        saveAndVerifyPng(bitmap, "10_paste_code.png")
    }

    @Test
    fun render_11_result_won() {
        launchAndPlayIncomingArjunChallenge(
            roundsToClear = 7,
            expectVictoryDecisionAfterRound = 7
        )

        composeTestRule.onNodeWithTag("status_header_text")
            .assertIsDisplayed()
            .assertTextContains("Challenge Won! 🎉", substring = true)
        composeTestRule.onNodeWithTag("status_subtitle_text")
            .assertIsDisplayed()
            .assertTextContains("You beat ARJUN's target of 6 with score 7!", substring = true)

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "11_result_won.png")
    }

    @Test
    fun render_12_result_lost() {
        launchAndPlayIncomingArjunChallenge(
            roundsToClear = 4,
            expectVictoryDecisionAfterRound = null
        )

        composeTestRule.onNodeWithTag("status_header_text")
            .assertIsDisplayed()
            .assertTextContains("Challenge Lost", substring = true)
        composeTestRule.onNodeWithTag("status_subtitle_text")
            .assertIsDisplayed()
            .assertTextContains("Out of tries! Target was 6 (Best: 4)", substring = true)

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "12_result_lost.png")
    }

    @Test
    fun render_13_result_tied() {
        launchAndPlayIncomingArjunChallenge(
            roundsToClear = 6,
            expectVictoryDecisionAfterRound = null
        )

        composeTestRule.onNodeWithTag("status_header_text")
            .assertIsDisplayed()
            .assertTextContains("Challenge Tied! 🤝", substring = true)
        composeTestRule.onNodeWithTag("status_subtitle_text")
            .assertIsDisplayed()
            .assertTextContains("You matched ARJUN's target of 6!", substring = true)

        val bitmap = captureActivityBitmap()
        saveAndVerifyPng(bitmap, "13_result_tied.png")
    }
}
