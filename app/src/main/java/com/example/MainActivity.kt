package com.example

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Core Game States as specified in Gameplay Mechanics.
 */
enum class GameState {
    IDLE,
    WATCHING,
    PLAYING,
    GAME_OVER
}

/**
 * Hybrid sequence speed modes:
 * - CLASSIC: Fixed 400ms flash, 200ms gap between colors.
 * - PROGRESSIVE: Starts at 400ms flash / 200ms gap and speeds up as rounds progress.
 */
enum class PaceMode(val label: String) {
    CLASSIC("Classic (400ms)"),
    PROGRESSIVE("Progressive Speed")
}

/**
 * The 4 distinct colored pads arranged in a 2x2 grid with Dim (inactive) and Bright/Glowing (active) states,
 * paired with distinct native DTMF tones.
 */
enum class SimonColor(
    val displayName: String,
    val testTagId: String,
    val dimColor: Color,
    val brightColor: Color,
    val glowColor: Color,
    val toneType: Int
) {
    GREEN(
        displayName = "Green",
        testTagId = "pad_green",
        dimColor = Color(0xFF064E3B),
        brightColor = Color(0xFF10B981),
        glowColor = Color(0xFF6EE7B7),
        toneType = ToneGenerator.TONE_DTMF_1
    ),
    RED(
        displayName = "Red",
        testTagId = "pad_red",
        dimColor = Color(0xFF7F1D1D),
        brightColor = Color(0xFFEF4444),
        glowColor = Color(0xFFFCA5A5),
        toneType = ToneGenerator.TONE_DTMF_3
    ),
    YELLOW(
        displayName = "Yellow",
        testTagId = "pad_yellow",
        dimColor = Color(0xFF78350F),
        brightColor = Color(0xFFF59E0B),
        glowColor = Color(0xFFFDE68A),
        toneType = ToneGenerator.TONE_DTMF_7
    ),
    BLUE(
        displayName = "Blue",
        testTagId = "pad_blue",
        dimColor = Color(0xFF1E3A8A),
        brightColor = Color(0xFF3B82F6),
        glowColor = Color(0xFF93C5FD),
        toneType = ToneGenerator.TONE_DTMF_9
    )
}

/**
 * Safe wrapper around Android's native [ToneGenerator] requiring zero external sound assets.
 */
class SimonTonePlayer {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        } catch (_: Throwable) {
            toneGenerator = null
        }
    }

    fun playPadTone(color: SimonColor, durationMs: Int = 220) {
        try {
            toneGenerator?.startTone(color.toneType, durationMs)
        } catch (_: Throwable) {
            // Ignore audio hardware failures on headless/muted devices
        }
    }

    fun playErrorTone(durationMs: Int = 450) {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, durationMs)
        } catch (_: Throwable) {
            // Ignore audio hardware failures
        }
    }

    fun playSuccessChime(durationMs: Int = 140) {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, durationMs)
        } catch (_: Throwable) {
            // Ignore audio hardware failures
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (_: Throwable) {
            // Ignore release errors
        } finally {
            toneGenerator = null
        }
    }
}

private const val PREFS_NAME = "simon_says_prefs"
private const val KEY_BEST_SCORE = "best_high_score"

private val SimonDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    secondary = Color(0xFF10B981),
    onSecondary = Color(0xFF0F172A),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = SimonDarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SimonSaysGameScreen()
                }
            }
        }
    }
}

@Composable
fun SimonSaysGameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Persistent Best Score via SharedPreferences
    val sharedPrefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var paceMode by remember { mutableStateOf(PaceMode.CLASSIC) }
    var feedbackEnabled by remember { mutableStateOf(true) }

    val sequence = remember { mutableStateListOf<SimonColor>() }
    var playerStepIndex by remember { mutableIntStateOf(0) }
    var currentScore by remember { mutableIntStateOf(0) }
    var bestScore by remember {
        mutableIntStateOf(sharedPrefs.getInt(KEY_BEST_SCORE, 0))
    }

    // Tracks which button is currently lit/glowing
    var activeColor by remember { mutableStateOf<SimonColor?>(null) }
    // Used to re-trigger the WATCHING coroutine even if round count is 1 on a fresh restart
    var roundTrigger by remember { mutableIntStateOf(0) }
    var tapFlashJob by remember { mutableStateOf<Job?>(null) }

    // Manage native ToneGenerator lifecycle safely
    val tonePlayer = remember { SimonTonePlayer() }
    DisposableEffect(Unit) {
        onDispose {
            tonePlayer.release()
        }
    }

    fun updateBestScoreIfNeeded(newScore: Int) {
        if (newScore > bestScore) {
            bestScore = newScore
            sharedPrefs.edit().putInt(KEY_BEST_SCORE, newScore).apply()
        }
    }

    // Calculate sequence timing based on hybrid PaceMode
    val flashDurationMs = remember(paceMode, sequence.size) {
        when (paceMode) {
            PaceMode.CLASSIC -> 400L
            PaceMode.PROGRESSIVE -> max(200L, 400L - (sequence.size - 1) * 18L)
        }
    }
    val gapDurationMs = remember(paceMode, sequence.size) {
        when (paceMode) {
            PaceMode.CLASSIC -> 200L
            PaceMode.PROGRESSIVE -> max(100L, 200L - (sequence.size - 1) * 9L)
        }
    }

    // Start or restart a game
    fun startNewGame() {
        tapFlashJob?.cancel()
        activeColor = null
        currentScore = 0
        playerStepIndex = 0
        sequence.clear()
        val firstColor = SimonColor.entries[Random.nextInt(SimonColor.entries.size)]
        sequence.add(firstColor)
        roundTrigger++
        gameState = GameState.WATCHING
    }

    // Step through the AI sequence smoothly when in WATCHING state
    LaunchedEffect(gameState, roundTrigger) {
        if (gameState == GameState.WATCHING && sequence.isNotEmpty()) {
            activeColor = null
            // Brief pause before the sequence begins flashing
            delay(450L)

            for (color in sequence) {
                activeColor = color
                if (feedbackEnabled) {
                    tonePlayer.playPadTone(color, flashDurationMs.toInt().coerceAtMost(350))
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                delay(flashDurationMs)

                activeColor = null
                delay(gapDurationMs)
            }

            playerStepIndex = 0
            gameState = GameState.PLAYING
        }
    }

    // Handle player taps during PLAYING state
    fun onColorPadTapped(tappedColor: SimonColor) {
        // Input Locking: strictly ignore taps unless in PLAYING state
        if (gameState != GameState.PLAYING || sequence.isEmpty()) return

        val expectedColor = sequence.getOrNull(playerStepIndex) ?: return

        if (tappedColor == expectedColor) {
            // Correct tap! Flash the button briefly and play its tone
            tapFlashJob?.cancel()
            activeColor = tappedColor
            if (feedbackEnabled) {
                tonePlayer.playPadTone(tappedColor, 180)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

            val nextStep = playerStepIndex + 1
            playerStepIndex = nextStep

            if (nextStep >= sequence.size) {
                // Completed the entire sequence for this round!
                val updatedScore = currentScore + 1
                currentScore = updatedScore
                updateBestScoreIfNeeded(updatedScore)

                // Transition immediately to WATCHING to lock input while finishing the tap flash
                val nextRandomColor = SimonColor.entries[Random.nextInt(SimonColor.entries.size)]
                sequence.add(nextRandomColor)
                gameState = GameState.WATCHING

                tapFlashJob = coroutineScope.launch {
                    delay(220L)
                    activeColor = null
                    if (feedbackEnabled) {
                        tonePlayer.playSuccessChime()
                    }
                    roundTrigger++
                }
            } else {
                // Still more steps left in the current round
                tapFlashJob = coroutineScope.launch {
                    delay(180L)
                    if (gameState == GameState.PLAYING) {
                        activeColor = null
                    }
                }
            }
        } else {
            // Wrong color tapped -> switch immediately to GAME_OVER with error tone
            tapFlashJob?.cancel()
            activeColor = tappedColor
            if (feedbackEnabled) {
                tonePlayer.playErrorTone(450)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            updateBestScoreIfNeeded(currentScore)
            gameState = GameState.GAME_OVER
            tapFlashJob = coroutineScope.launch {
                delay(350L)
                activeColor = null
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A),
                            Color(0xFF090D16)
                        ),
                        radius = 1400f
                    )
                )
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: Title, Sound Toggle, Scoreboard, and Hybrid Pace Mode Chips
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TopTitleRow(
                        feedbackEnabled = feedbackEnabled,
                        onToggleFeedback = { feedbackEnabled = !feedbackEnabled }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ScoreboardRow(
                        currentScore = currentScore,
                        bestScore = bestScore
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PaceSelectorRow(
                        selectedPace = paceMode,
                        enabled = gameState != GameState.WATCHING,
                        onSelectPace = { paceMode = it }
                    )
                }

                // Status Header Banner ("Watch Carefully!", "Your Turn!", "Game Over!")
                StatusBanner(
                    gameState = gameState,
                    currentScore = currentScore,
                    playerStepIndex = playerStepIndex,
                    sequenceSize = sequence.size,
                    flashDurationMs = flashDurationMs
                )

                // Center 2x2 Neon Game Pad Grid
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val gridSide = minOf(maxWidth, maxHeight, 360.dp)
                    SimonPadGrid(
                        modifier = Modifier.size(gridSide),
                        activeColor = activeColor,
                        inputEnabled = gameState == GameState.PLAYING,
                        onPadClick = ::onColorPadTapped
                    )
                }

                // Bottom Prominent Action Button ("Start Game" / "Play Again" / "Restart")
                BottomActionSection(
                    gameState = gameState,
                    onStartOrRetry = ::startNewGame
                )
            }
        }
    }
}

@Composable
private fun TopTitleRow(
    feedbackEnabled: Boolean,
    onToggleFeedback: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "SIMON SAYS",
                color = Color(0xFFF8FAFC),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        IconButton(
            onClick = onToggleFeedback,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), CircleShape)
                .testTag("audio_toggle_button")
        ) {
            Icon(
                imageVector = if (feedbackEnabled) {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.AutoMirrored.Filled.VolumeOff
                },
                contentDescription = if (feedbackEnabled) {
                    "Mute sound and haptics"
                } else {
                    "Unmute sound and haptics"
                },
                tint = if (feedbackEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun ScoreboardRow(
    currentScore: Int,
    bestScore: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current Score Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CURRENT SCORE",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentScore.toString(),
                    color = Color(0xFFF8FAFC),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("current_score_text")
                )
            }
        }

        // Best High Score Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BEST HIGH SCORE",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bestScore.toString(),
                    color = Color(0xFFFBBF24),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("best_score_text")
                )
            }
        }
    }
}

@Composable
private fun PaceSelectorRow(
    selectedPace: PaceMode,
    enabled: Boolean,
    onSelectPace: (PaceMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaceMode.entries.forEach { mode ->
            val isSelected = selectedPace == mode
            val tag = if (mode == PaceMode.CLASSIC) "pace_mode_classic" else "pace_mode_progressive"
            FilterChip(
                selected = isSelected,
                onClick = { onSelectPace(mode) },
                enabled = enabled,
                label = {
                    Text(
                        text = mode.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF1E293B),
                    labelColor = Color(0xFFCBD5E1),
                    iconColor = Color(0xFF94A3B8),
                    selectedContainerColor = Color(0xFF0284C7).copy(alpha = 0.28f),
                    selectedLabelColor = Color(0xFF38BDF8),
                    selectedLeadingIconColor = Color(0xFF38BDF8)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = enabled,
                    selected = isSelected,
                    borderColor = Color(0xFF334155),
                    selectedBorderColor = Color(0xFF38BDF8)
                ),
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

@Composable
private fun StatusBanner(
    gameState: GameState,
    currentScore: Int,
    playerStepIndex: Int,
    sequenceSize: Int,
    flashDurationMs: Long
) {
    val bannerAccentColor by animateColorAsState(
        targetValue = when (gameState) {
            GameState.IDLE -> Color(0xFF38BDF8)
            GameState.WATCHING -> Color(0xFFFBBF24)
            GameState.PLAYING -> Color(0xFF10B981)
            GameState.GAME_OVER -> Color(0xFFEF4444)
        },
        animationSpec = tween(250),
        label = "bannerAccentColor"
    )

    val statusTitle = when (gameState) {
        GameState.IDLE -> "Ready to Play?"
        GameState.WATCHING -> "Watch Carefully!"
        GameState.PLAYING -> "Your Turn!"
        GameState.GAME_OVER -> "Game Over!"
    }

    val statusSubtitle = when (gameState) {
        GameState.IDLE -> "Memorize the glowing neon sequence and repeat it."
        GameState.WATCHING -> "Round ${sequenceSize.coerceAtLeast(1)} • Pace ${flashDurationMs}ms • Input Locked"
        GameState.PLAYING -> "Step ${playerStepIndex + 1} of $sequenceSize • Tap the matching pads"
        GameState.GAME_OVER -> "Final Score: $currentScore • Tap Play Again to beat your record!"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.92f)
        ),
        border = BorderStroke(1.5.dp, bannerAccentColor.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = statusTitle,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "statusHeaderTransition"
            ) { title ->
                Text(
                    text = title,
                    color = bannerAccentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("status_header_text")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusSubtitle,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("status_subtitle_text")
            )

            if (sequenceSize > 0 && (gameState == GameState.PLAYING || gameState == GameState.WATCHING)) {
                Spacer(modifier = Modifier.height(10.dp))
                SequenceStepIndicator(
                    totalSteps = sequenceSize,
                    completedSteps = if (gameState == GameState.PLAYING) playerStepIndex else 0,
                    accentColor = bannerAccentColor
                )
            }
        }
    }
}

@Composable
private fun SequenceStepIndicator(
    totalSteps: Int,
    completedSteps: Int,
    accentColor: Color
) {
    val visibleDots = totalSteps.coerceAtMost(12)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until visibleDots) {
            val isCompleted = i < completedSteps
            Box(
                modifier = Modifier
                    .size(if (isCompleted) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) accentColor else Color(0xFF475569)
                    )
            )
        }
        if (totalSteps > visibleDots) {
            Text(
                text = "+${totalSteps - visibleDots}",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SimonPadGrid(
    modifier: Modifier = Modifier,
    activeColor: SimonColor?,
    inputEnabled: Boolean,
    onPadClick: (SimonColor) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Row: GREEN and RED
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SimonPadButton(
                simonColor = SimonColor.GREEN,
                isActive = activeColor == SimonColor.GREEN,
                inputEnabled = inputEnabled,
                onClick = { onPadClick(SimonColor.GREEN) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            SimonPadButton(
                simonColor = SimonColor.RED,
                isActive = activeColor == SimonColor.RED,
                inputEnabled = inputEnabled,
                onClick = { onPadClick(SimonColor.RED) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
        }

        // Bottom Row: YELLOW and BLUE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SimonPadButton(
                simonColor = SimonColor.YELLOW,
                isActive = activeColor == SimonColor.YELLOW,
                inputEnabled = inputEnabled,
                onClick = { onPadClick(SimonColor.YELLOW) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            SimonPadButton(
                simonColor = SimonColor.BLUE,
                isActive = activeColor == SimonColor.BLUE,
                inputEnabled = inputEnabled,
                onClick = { onPadClick(SimonColor.BLUE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun SimonPadButton(
    simonColor: SimonColor,
    isActive: Boolean,
    inputEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.045f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "${simonColor.name}_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isActive) simonColor.brightColor else simonColor.dimColor,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "${simonColor.name}_color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) {
            simonColor.glowColor
        } else {
            simonColor.brightColor.copy(alpha = 0.38f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "${simonColor.name}_border"
    )

    val padShape = RoundedCornerShape(28.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .shadow(
                elevation = if (isActive) 24.dp else 6.dp,
                shape = padShape,
                ambientColor = if (isActive) simonColor.glowColor else Color.Black,
                spotColor = if (isActive) simonColor.brightColor else Color.Black
            )
            .clip(padShape)
            .background(containerColor)
            .border(
                width = if (isActive) 4.dp else 2.dp,
                color = borderColor,
                shape = padShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = inputEnabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = "${simonColor.displayName} game pad"
            }
            .testTag(simonColor.testTagId),
        contentAlignment = Alignment.Center
    ) {
        // Pure Compose Canvas specular neon glow inside the pad
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.65f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isActive) {
                        listOf(
                            Color.White.copy(alpha = 0.65f),
                            simonColor.glowColor.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            simonColor.brightColor.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    },
                    center = centerOffset,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = centerOffset
            )
        }

        Text(
            text = simonColor.displayName.uppercase(),
            color = if (isActive) {
                Color(0xFF0F172A)
            } else {
                Color.White.copy(alpha = 0.65f)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun BottomActionSection(
    gameState: GameState,
    onStartOrRetry: () -> Unit
) {
    val buttonLabel = when (gameState) {
        GameState.IDLE -> "Start Game"
        GameState.GAME_OVER -> "Play Again"
        GameState.WATCHING, GameState.PLAYING -> "Restart Game"
    }

    val buttonContainerColor = when (gameState) {
        GameState.IDLE -> Color(0xFF10B981)
        GameState.GAME_OVER -> Color(0xFF38BDF8)
        GameState.WATCHING, GameState.PLAYING -> Color(0xFF334155)
    }

    val buttonContentColor = when (gameState) {
        GameState.IDLE, GameState.GAME_OVER -> Color(0xFF0F172A)
        GameState.WATCHING, GameState.PLAYING -> Color(0xFFF8FAFC)
    }

    Button(
        onClick = onStartOrRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("start_game_button"),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainerColor,
            contentColor = buttonContentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp
        )
    ) {
        Icon(
            imageVector = if (gameState == GameState.IDLE) {
                Icons.Default.PlayArrow
            } else {
                Icons.Default.Refresh
            },
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buttonLabel,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun SimonSaysGamePreview() {
    MaterialTheme(colorScheme = SimonDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SimonSaysGameScreen()
        }
    }
}
