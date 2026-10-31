package com.example

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.challenge.ActiveChallengesRail
import com.example.challenge.ChallengeItem
import com.example.challenge.ChallengeSeed
import com.example.challenge.ChallengeShareHelper
import com.example.challenge.ChallengeStorage
import com.example.challenge.EnterChallengeDialog
import com.example.challenge.InGameChallengeBanner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random
import kotlinx.coroutines.*

enum class GameState { IDLE, WATCHING, PLAYING, GAME_OVER }

enum class GameMode(val playerCount: Int, val label: String, val tagId: String) {
    SOLO(1, "SOLO", "mode_solo"),
    TWO_PLAYERS(2, "2P", "mode_2p"),
    THREE_PLAYERS(3, "3P", "mode_3p"),
    FOUR_PLAYERS(4, "4P", "mode_4p")
}

data class PlayerSlot(
    val index: Int,
    val name: String,
    val color: Color,
    val isAlive: Boolean = true,
    val roundsSurvived: Int = 0
)

data class PlayerStanding(
    val player: PlayerSlot,
    val rank: Int,
    val medal: String,
    val isWinner: Boolean
)

val PLAYER_COLORS = listOf(
    Color(0xFF38BDF8), // P1 Cyan
    Color(0xFFF43F5E), // P2 Coral/Rose
    Color(0xFFF59E0B), // P3 Amber
    Color(0xFF10B981)  // P4 Emerald Green
)

enum class PaceMode(val label: String, val shortLabel: String, val keySuffix: String) {
    CLASSIC("Classic (400ms)", "Classic", "classic"),
    PROGRESSIVE("Progressive Speed", "Progressive", "progressive")
}

enum class GridSize(
    val dimension: Int,
    val padCount: Int,
    val label: String,
    val keyPrefix: String,
    val testTagId: String,
    val spacingDp: Int,
    val cornerDp: Int
) {
    TWO_BY_TWO(2, 4, "2×2", "2x2", "grid_size_2x2", 16, 28),
    THREE_BY_THREE(3, 9, "3×3", "3x3", "grid_size_3x3", 10, 20),
    FOUR_BY_FOUR(4, 16, "4×4", "4x4", "grid_size_4x4", 8, 16)
}

enum class PadGlyph(val displayName: String) {
    TRIANGLE("Triangle"), CIRCLE("Circle"), SQUARE("Square"), DIAMOND("Diamond"),
    STAR("Star"), PLUS("Plus"), CROSS("Cross"), RING("Ring"),
    HEART("Heart"), BOLT("Bolt"), MOON("Moon"), HEXAGON("Hexagon"),
    UP_ARROW("Up Arrow"), HOURGLASS("Hourglass"), CROWN("Crown"), SHIELD("Shield")
}

enum class GlowColor(
    val displayName: String,
    val testTagId: String,
    val dimColor: Color,
    val brightColor: Color,
    val glowColor: Color,
    val frequencyHz: Double,
    val shapeName: String,
    val glyph: PadGlyph
) {
    GREEN("Green", "pad_green", Color(0xFF064E3B), Color(0xFF10B981), Color(0xFF6EE7B7), 261.63, "Triangle", PadGlyph.TRIANGLE),
    RED("Red", "pad_red", Color(0xFF7F1D1D), Color(0xFFEF4444), Color(0xFFFCA5A5), 329.63, "Circle", PadGlyph.CIRCLE),
    YELLOW("Yellow", "pad_yellow", Color(0xFF78350F), Color(0xFFF59E0B), Color(0xFFFDE68A), 392.00, "Square", PadGlyph.SQUARE),
    BLUE("Blue", "pad_blue", Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF93C5FD), 523.25, "Diamond", PadGlyph.DIAMOND)
}

typealias SimonColor = GlowColor

data class PadSpec(
    val id: Int,
    val colorName: String,
    val glyph: PadGlyph,
    val dimColor: Color,
    val brightColor: Color,
    val glowColor: Color,
    val frequencyHz: Double,
    val testTagId: String
)

val CLASSIC_2X2_PADS: List<PadSpec> = GlowColor.entries.mapIndexed { idx, sc ->
    PadSpec(idx, sc.displayName, sc.glyph, sc.dimColor, sc.brightColor, sc.glowColor, sc.frequencyHz, sc.testTagId)
}

val GRID_16_PADS: List<PadSpec> = listOf(
    // Row 1: Warm Reds / Corals
    PadSpec(0, "Crimson", PadGlyph.TRIANGLE, Color(0xFF6B1220), Color(0xFFEF4444), Color(0xFFFCA5A5), 196.00, "pad_0"),
    PadSpec(1, "Coral", PadGlyph.CIRCLE, Color(0xFF771D1D), Color(0xFFF87171), Color(0xFFFECACA), 220.00, "pad_1"),
    PadSpec(2, "Rose", PadGlyph.SQUARE, Color(0xFF701A3E), Color(0xFFF43F5E), Color(0xFFFDA4AF), 261.63, "pad_2"),
    PadSpec(3, "Vermilion", PadGlyph.DIAMOND, Color(0xFF7C2D12), Color(0xFFFB923C), Color(0xFFFED7AA), 293.66, "pad_3"),
    // Row 2: Ambers / Golds
    PadSpec(4, "Amber", PadGlyph.STAR, Color(0xFF78350F), Color(0xFFF59E0B), Color(0xFFFDE68A), 329.63, "pad_4"),
    PadSpec(5, "Gold", PadGlyph.PLUS, Color(0xFF713F12), Color(0xFFEAB308), Color(0xFFFEF08A), 392.00, "pad_5"),
    PadSpec(6, "Tangerine", PadGlyph.CROSS, Color(0xFF6C2E09), Color(0xFFFFB703), Color(0xFFFFE599), 440.00, "pad_6"),
    PadSpec(7, "Honey", PadGlyph.RING, Color(0xFF5C4813), Color(0xFFFACC15), Color(0xFFFEF9C3), 523.25, "pad_7"),
    // Row 3: Emeralds / Teals
    PadSpec(8, "Emerald", PadGlyph.HEART, Color(0xFF064E3B), Color(0xFF10B981), Color(0xFF6EE7B7), 587.33, "pad_8"),
    PadSpec(9, "Mint", PadGlyph.BOLT, Color(0xFF14532D), Color(0xFF22C55E), Color(0xFF86EFAC), 659.25, "pad_9"),
    PadSpec(10, "Teal", PadGlyph.MOON, Color(0xFF134E4A), Color(0xFF14B8A6), Color(0xFF5EEAD4), 783.99, "pad_10"),
    PadSpec(11, "Cyan", PadGlyph.HEXAGON, Color(0xFF164E63), Color(0xFF06B6D4), Color(0xFF67E8F9), 880.00, "pad_11"),
    // Row 4: Blues / Violets
    PadSpec(12, "Cobalt", PadGlyph.UP_ARROW, Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF93C5FD), 1046.50, "pad_12"),
    PadSpec(13, "Indigo", PadGlyph.HOURGLASS, Color(0xFF312E81), Color(0xFF6366F1), Color(0xFFA5B4FC), 1174.66, "pad_13"),
    PadSpec(14, "Violet", PadGlyph.CROWN, Color(0xFF4C1D95), Color(0xFF8B5CF6), Color(0xFFC4B5FD), 1318.51, "pad_14"),
    PadSpec(15, "Magenta", PadGlyph.SHIELD, Color(0xFF701A75), Color(0xFFD946EF), Color(0xFFF0ABFC), 1567.98, "pad_15")
)

val GRID_9_PADS: List<PadSpec> = listOf(
    // Row 1: Warm Reds / Corals (Crimson Triangle, Coral Circle, Rose Square)
    GRID_16_PADS[0].copy(id = 0, testTagId = "pad_0"),
    GRID_16_PADS[1].copy(id = 1, testTagId = "pad_1"),
    GRID_16_PADS[2].copy(id = 2, testTagId = "pad_2"),
    // Row 2: Golds / Emeralds (Amber Star, Gold Plus, Emerald Heart)
    GRID_16_PADS[4].copy(id = 3, testTagId = "pad_3"),
    GRID_16_PADS[5].copy(id = 4, testTagId = "pad_4"),
    GRID_16_PADS[8].copy(id = 5, testTagId = "pad_5"),
    // Row 3: Teals / Blues / Violets (Teal Moon, Cobalt Up Arrow, Violet Crown)
    GRID_16_PADS[10].copy(id = 6, testTagId = "pad_6"),
    GRID_16_PADS[12].copy(id = 7, testTagId = "pad_7"),
    GRID_16_PADS[14].copy(id = 8, testTagId = "pad_8")
)

fun padsForGridSize(gridSize: GridSize): List<PadSpec> = when (gridSize) {
    GridSize.TWO_BY_TWO -> CLASSIC_2X2_PADS
    GridSize.THREE_BY_THREE -> GRID_9_PADS
    GridSize.FOUR_BY_FOUR -> GRID_16_PADS
}

class GlowAudioSynthesizer {
    companion object {
        const val SAMPLE_RATE = 22050
        private const val FADE_MS = 10

        internal fun synthesizeWavePcm(freqHz: Double, durationMs: Int, amp: Double, oddHarmonics: Boolean = false): ShortArray {
            val total = ((SAMPLE_RATE * durationMs) / 1000).coerceAtLeast(1)
            val fade = ((SAMPLE_RATE * FADE_MS) / 1000).coerceAtLeast(1)
            val buf = ShortArray(total)
            val w = 2.0 * PI * freqHz / SAMPLE_RATE
            for (i in 0 until total) {
                val env = when {
                    i < fade -> i.toDouble() / fade
                    i > total - fade -> (total - i).toDouble() / fade
                    else -> 1.0
                }.coerceIn(0.0, 1.0)
                val raw = if (oddHarmonics) {
                    sin(i * w) * 0.60 + sin(i * w * 3.0) * 0.25 + sin(i * w * 5.0) * 0.15
                } else {
                    sin(i * w) * 0.85 + sin(i * w * 2.0) * 0.15
                }
                buf[i] = (raw * amp * env * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            return buf
        }

        private fun concatPcm(vararg parts: ShortArray): ShortArray {
            val out = ShortArray(parts.sumOf { it.size })
            var pos = 0
            for (p in parts) {
                System.arraycopy(p, 0, out, pos, p.size)
                pos += p.size
            }
            return out
        }

        internal fun synthesizeDescendingErrorBuzzPcm(durationMs: Int = 420): ShortArray =
            concatPcm(synthesizeWavePcm(196.00, durationMs / 2, 0.78, true), synthesizeWavePcm(130.81, durationMs - durationMs / 2, 0.78, true))

        internal fun synthesizeRisingArpeggioPcm(): ShortArray =
            concatPcm(synthesizeWavePcm(523.25, 110, 0.70), synthesizeWavePcm(659.25, 110, 0.72), synthesizeWavePcm(783.99, 160, 0.75))
    }

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeTracks = ConcurrentLinkedQueue<AudioTrack>()

    val frequencyPcmBuffers: Map<Double, ShortArray> = (CLASSIC_2X2_PADS + GRID_16_PADS)
        .map { it.frequencyHz }.distinct().associateWith { synthesizeWavePcm(it, 240, 0.72) }

    val padPcmBuffers: Map<GlowColor, ShortArray> = GlowColor.entries.associateWith {
        frequencyPcmBuffers[it.frequencyHz] ?: synthesizeWavePcm(it.frequencyHz, 240, 0.72)
    }

    val errorBuzzPcm: ShortArray = synthesizeDescendingErrorBuzzPcm(420)
    val clearArpeggioPcm: ShortArray = synthesizeRisingArpeggioPcm()

    fun playPadSpecTone(pad: PadSpec, volume: Float = 0.85f) =
        frequencyPcmBuffers[pad.frequencyHz]?.let { playStaticPcmAsync(it, 240L, volume) } ?: Unit

    fun playPadTone(color: GlowColor, volume: Float = 0.85f) =
        padPcmBuffers[color]?.let { playStaticPcmAsync(it, 240L, volume) } ?: Unit

    fun playErrorBuzz(volume: Float = 0.85f) = playStaticPcmAsync(errorBuzzPcm, 420L, volume)
    fun playRoundClearArpeggio(volume: Float = 0.85f) = playStaticPcmAsync(clearArpeggioPcm, 380L, volume)

    private fun playStaticPcmAsync(pcmBuffer: ShortArray, durationMs: Long, volume: Float) {
        val vol = volume.coerceIn(0f, 1f)
        if (vol <= 0f) return
        audioScope.launch {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcmBuffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcmBuffer, 0, pcmBuffer.size)
                track.setVolume(vol)
                activeTracks.add(track)
                track.play()
                delay(durationMs + 40L)
            } catch (_: Throwable) {
            } finally {
                if (track != null) {
                    activeTracks.remove(track)
                    runCatching { track.stop() }
                    runCatching { track.release() }
                }
            }
        }
    }

    fun release() {
        audioScope.cancel()
        while (true) {
            val track = activeTracks.poll() ?: break
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}

typealias SimonAudioSynthesizer = GlowAudioSynthesizer

internal const val PREFS_NAME = "glowback_prefs"
internal const val KEY_BEST_SCORE = "best_high_score"
internal const val KEY_HAS_SEEN_EXPLAINER = "has_seen_explainer"
internal const val KEY_SHAPES_ENABLED = "colorblind_shapes_enabled"
internal const val KEY_AUDIO_VOLUME = "audio_volume"
internal const val KEY_GRID_SIZE = "selected_grid_size"
internal const val KEY_PACE_MODE = "selected_pace_mode"
internal const val KEY_GAME_MODE = "selected_game_mode"

internal fun playerWinsKey(playerIndex: Int): String = "wins_P${playerIndex + 1}"
internal fun readPlayerWins(prefs: SharedPreferences, playerIndex: Int): Int = prefs.getInt(playerWinsKey(playerIndex), 0)
internal fun incrementPlayerWin(prefs: SharedPreferences, playerIndex: Int) {
    val key = playerWinsKey(playerIndex)
    prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
}
internal fun resetMultiplayerWins(prefs: SharedPreferences) {
    val editor = prefs.edit()
    for (i in 0 until 4) editor.putInt(playerWinsKey(i), 0)
    editor.apply()
}

internal fun readGameMode(prefs: SharedPreferences): GameMode {
    val name = prefs.getString(KEY_GAME_MODE, GameMode.SOLO.name)
    return GameMode.entries.find { it.name == name } ?: GameMode.SOLO
}

internal fun writeGameMode(prefs: SharedPreferences, mode: GameMode) {
    prefs.edit().putString(KEY_GAME_MODE, mode.name).apply()
}

fun computeStandings(players: List<PlayerSlot>): List<PlayerStanding> {
    val sorted = players.sortedWith(compareByDescending<PlayerSlot> { it.roundsSurvived }.thenBy { it.index })
    if (sorted.isEmpty()) return emptyList()
    val maxScore = sorted.first().roundsSurvived
    var currentRank = 1
    return sorted.mapIndexed { idx, p ->
        if (idx > 0 && p.roundsSurvived < sorted[idx - 1].roundsSurvived) {
            currentRank = idx + 1
        }
        val isWin = p.roundsSurvived == maxScore
        val medal = when (currentRank) {
            1 -> if (sorted.count { it.roundsSurvived == maxScore } > 1) "🤝" else "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "${currentRank}th"
        }
        PlayerStanding(player = p, rank = currentRank, medal = medal, isWinner = isWin)
    }
}

internal fun bestScoreKeyFor(gridSize: GridSize, paceMode: PaceMode): String = "best_${gridSize.keyPrefix}_${paceMode.keySuffix}"

internal fun readMigratedBestScore(prefs: SharedPreferences, gridSize: GridSize, paceMode: PaceMode): Int {
    val key = bestScoreKeyFor(gridSize, paceMode)
    if (gridSize == GridSize.TWO_BY_TWO && paceMode == PaceMode.CLASSIC) {
        val resolved = max(prefs.getInt(KEY_BEST_SCORE, 0), prefs.getInt(key, 0))
        if (resolved != prefs.getInt(key, 0)) prefs.edit().putInt(key, resolved).apply()
        return resolved
    }
    return prefs.getInt(key, 0)
}

internal fun writeBestScore(prefs: SharedPreferences, gridSize: GridSize, paceMode: PaceMode, score: Int) {
    val editor = prefs.edit().putInt(bestScoreKeyFor(gridSize, paceMode), score)
    if ((gridSize == GridSize.TWO_BY_TWO && paceMode == PaceMode.CLASSIC) || score > prefs.getInt(KEY_BEST_SCORE, 0)) {
        editor.putInt(KEY_BEST_SCORE, score)
    }
    editor.apply()
}

private val GlowDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8), onPrimary = Color(0xFF0F172A),
    secondary = Color(0xFF10B981), onSecondary = Color(0xFF0F172A),
    tertiary = Color(0xFFF59E0B), background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC), surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC), surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1), error = Color(0xFFEF4444), onError = Color.White
)

private val SimonDarkColorScheme = GlowDarkColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = GlowDarkColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GlowbackGameScreen()
                }
            }
        }
    }
}

@Composable
fun SimonSaysGameScreen(modifier: Modifier = Modifier) = GlowbackGameScreen(modifier)

@Composable
fun GlowbackGameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var gameMode by remember { mutableStateOf(readGameMode(sharedPrefs)) }
    var playerWinsVersion by remember { mutableIntStateOf(0) }
    var gridSize by remember {
        val name = sharedPrefs.getString(KEY_GRID_SIZE, GridSize.TWO_BY_TWO.name)
        mutableStateOf(GridSize.entries.find { it.name == name } ?: GridSize.TWO_BY_TWO)
    }
    var paceMode by remember {
        val name = sharedPrefs.getString(KEY_PACE_MODE, PaceMode.CLASSIC.name)
        mutableStateOf(PaceMode.entries.find { it.name == name } ?: PaceMode.CLASSIC)
    }
    var feedbackEnabled by remember { mutableStateOf(true) }
    var audioVolume by remember { mutableFloatStateOf(sharedPrefs.getFloat(KEY_AUDIO_VOLUME, 0.85f).coerceIn(0f, 1f)) }
    var showColorblindShapes by remember { mutableStateOf(sharedPrefs.getBoolean(KEY_SHAPES_ENABLED, true)) }
    var hasSeenExplainer by remember { mutableStateOf(sharedPrefs.getBoolean(KEY_HAS_SEEN_EXPLAINER, false)) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Async Challenge State
    var activeChallenge by remember { mutableStateOf<ChallengeItem?>(null) }
    var challengeRng by remember { mutableStateOf<java.util.Random?>(null) }
    var showEnterChallengeDialog by remember { mutableStateOf(false) }
    var savedChallenges by remember { mutableStateOf(ChallengeStorage.loadChallenges(context)) }
    var challengeAttemptsLeft by remember { mutableIntStateOf(3) }
    var isChallengeWon by remember { mutableStateOf(false) }
    var isChallengeLost by remember { mutableStateOf(false) }

    val activePadPool = remember(gridSize) { padsForGridSize(gridSize) }
    val sequence = remember { mutableStateListOf<PadSpec>() }
    var playerStepIndex by remember { mutableIntStateOf(0) }
    var currentScore by remember { mutableIntStateOf(0) }
    var bestScore by remember(gridSize, paceMode) { mutableIntStateOf(readMigratedBestScore(sharedPrefs, gridSize, paceMode)) }

    // Multiplayer State
    val players = remember { mutableStateListOf<PlayerSlot>() }
    var activePlayerIndex by remember { mutableIntStateOf(0) }
    var isHandoffVisible by remember { mutableStateOf(false) }
    var isRedemptionTurnActive by remember { mutableStateOf(false) }
    var isMatchComplete by remember { mutableStateOf(false) }
    var matchStandings by remember { mutableStateOf<List<PlayerStanding>>(emptyList()) }
    var eliminatedBannerText by remember { mutableStateOf<String?>(null) }

    var activePadId by remember { mutableStateOf<Int?>(null) }
    var allPadsConfettiFlash by remember { mutableStateOf(false) }
    var clearedRoundNumber by remember { mutableIntStateOf(0) }
    var isRoundClearCelebration by remember { mutableStateOf(false) }

    var failedStepNumber by remember { mutableIntStateOf(0) }
    var failedExpectedPad by remember { mutableStateOf<PadSpec?>(null) }
    var failedTappedPad by remember { mutableStateOf<PadSpec?>(null) }
    var wrongShakePadId by remember { mutableStateOf<Int?>(null) }
    val wrongPadShakeOffset = remember { Animatable(0f) }

    var isReplayingLostRound by remember { mutableStateOf(false) }
    var ghostReplayStep by remember { mutableIntStateOf(0) }
    var ghostReplayJob by remember { mutableStateOf<Job?>(null) }

    var isLifecyclePaused by remember { mutableStateOf(false) }
    var wasPausedMidRound by remember { mutableStateOf(false) }
    var resumedBannerNote by remember { mutableStateOf<String?>(null) }
    var roundTrigger by remember { mutableIntStateOf(0) }
    var tapFlashJob by remember { mutableStateOf<Job?>(null) }

    val audioSynth = remember { GlowAudioSynthesizer() }
    DisposableEffect(Unit) { onDispose { audioSynth.release() } }

    val effectiveVolume = if (feedbackEnabled) audioVolume else 0f

    fun dismissExplainer() {
        if (!hasSeenExplainer) {
            hasSeenExplainer = true
            sharedPrefs.edit().putBoolean(KEY_HAS_SEEN_EXPLAINER, true).apply()
        }
    }

    fun toggleColorblindShapes() {
        showColorblindShapes = !showColorblindShapes
        sharedPrefs.edit().putBoolean(KEY_SHAPES_ENABLED, showColorblindShapes).apply()
    }

    fun updateVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        audioVolume = clamped
        sharedPrefs.edit().putFloat(KEY_AUDIO_VOLUME, clamped).apply()
        if (clamped > 0f && !feedbackEnabled) feedbackEnabled = true
    }

    fun updateBestScoreIfNeeded(newScore: Int) {
        if (newScore > bestScore) {
            bestScore = newScore
            writeBestScore(sharedPrefs, gridSize, paceMode, newScore)
        }
    }

    fun resetRunState() {
        tapFlashJob?.cancel()
        ghostReplayJob?.cancel()
        isReplayingLostRound = false
        isRoundClearCelebration = false
        allPadsConfettiFlash = false
        wrongShakePadId = null
        failedExpectedPad = null
        failedTappedPad = null
        failedStepNumber = 0
        resumedBannerNote = null
        wasPausedMidRound = false
        isLifecyclePaused = false
        activePadId = null
        currentScore = 0
        playerStepIndex = 0
        sequence.clear()
        isHandoffVisible = false
        isRedemptionTurnActive = false
        eliminatedBannerText = null
    }

    fun resetToIdleForConfigChange(newGridSize: GridSize, newPaceMode: PaceMode) {
        resetRunState()
        activeChallenge = null
        challengeRng = null
        isChallengeWon = false
        isChallengeLost = false
        gridSize = newGridSize
        paceMode = newPaceMode
        sharedPrefs.edit().putString(KEY_GRID_SIZE, newGridSize.name).putString(KEY_PACE_MODE, newPaceMode.name).apply()
        bestScore = readMigratedBestScore(sharedPrefs, newGridSize, newPaceMode)
        isMatchComplete = false
        gameState = GameState.IDLE
    }

    fun resetToIdleForMode(newMode: GameMode) {
        resetRunState()
        activeChallenge = null
        challengeRng = null
        isChallengeWon = false
        isChallengeLost = false
        gameMode = newMode
        writeGameMode(sharedPrefs, newMode)
        isMatchComplete = false
        matchStandings = emptyList()
        players.clear()
        if (newMode != GameMode.SOLO) {
            players.addAll(List(newMode.playerCount) { i -> PlayerSlot(i, "P${i + 1}", PLAYER_COLORS[i]) })
        }
        gameState = GameState.IDLE
    }

    val currentGameState by rememberUpdatedState(gameState)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (currentGameState == GameState.WATCHING || currentGameState == GameState.PLAYING) {
                    wasPausedMidRound = true
                    isLifecyclePaused = true
                    tapFlashJob?.cancel()
                    activePadId = null
                    allPadsConfettiFlash = false
                    isRoundClearCelebration = false
                }
                Lifecycle.Event.ON_RESUME -> if (wasPausedMidRound && sequence.isNotEmpty()) {
                    wasPausedMidRound = false
                    isLifecyclePaused = false
                    playerStepIndex = 0
                    activePadId = null
                    allPadsConfettiFlash = false
                    isRoundClearCelebration = false
                    resumedBannerNote = "Round replayed - you were away."
                    gameState = GameState.WATCHING
                    roundTrigger++
                } else {
                    isLifecyclePaused = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val flashDurationMs = remember(paceMode, sequence.size) {
        if (paceMode == PaceMode.CLASSIC) 400L else max(200L, 400L - (sequence.size - 1) * 18L)
    }
    val gapDurationMs = remember(paceMode, sequence.size) {
        if (paceMode == PaceMode.CLASSIC) 200L else max(100L, 200L - (sequence.size - 1) * 9L)
    }

    fun concludeMatch() {
        isMatchComplete = true
        gameState = GameState.GAME_OVER
        isHandoffVisible = false
        isRedemptionTurnActive = false
        eliminatedBannerText = null
        val standings = computeStandings(players)
        matchStandings = standings
        standings.filter { it.isWinner }.forEach { incrementPlayerWin(sharedPrefs, it.player.index) }
        playerWinsVersion++
        coroutineScope.launch {
            allPadsConfettiFlash = true
            if (feedbackEnabled && effectiveVolume > 0f) {
                audioSynth.playRoundClearArpeggio(effectiveVolume)
            }
            delay(320L)
            allPadsConfettiFlash = false
        }
    }

    fun onHandoffTapped() {
        if (!isHandoffVisible) return
        isHandoffVisible = false
        gameState = GameState.WATCHING
        roundTrigger++
    }

    fun startChallenge(item: ChallengeItem) {
        dismissExplainer()
        resetRunState()
        activeChallenge = item
        challengeAttemptsLeft = item.attemptsLeft
        isChallengeWon = item.isWon
        isChallengeLost = item.attemptsLeft <= 0 && !item.isWon
        gameMode = GameMode.SOLO

        val targetGrid = when (item.gridSizeTag.lowercase()) {
            "3x3" -> GridSize.THREE_BY_THREE
            "4x4" -> GridSize.FOUR_BY_FOUR
            else -> GridSize.TWO_BY_TWO
        }
        gridSize = targetGrid
        paceMode = PaceMode.CLASSIC

        val rng = java.util.Random(item.seed.toLong())
        challengeRng = rng
        val pads = padsForGridSize(targetGrid)
        sequence.add(pads[rng.nextInt(pads.size)])
        roundTrigger++
        gameState = GameState.WATCHING
    }

    fun startNewGame() {
        dismissExplainer()
        resetRunState()
        isMatchComplete = false
        matchStandings = emptyList()
        if (activeChallenge != null) {
            val challenge = activeChallenge!!
            val targetGrid = when (challenge.gridSizeTag.lowercase()) {
                "3x3" -> GridSize.THREE_BY_THREE
                "4x4" -> GridSize.FOUR_BY_FOUR
                else -> GridSize.TWO_BY_TWO
            }
            gridSize = targetGrid
            val rng = java.util.Random(challenge.seed.toLong())
            challengeRng = rng
            val pads = padsForGridSize(targetGrid)
            sequence.add(pads[rng.nextInt(pads.size)])
            roundTrigger++
            gameState = GameState.WATCHING
        } else if (gameMode == GameMode.SOLO) {
            sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
            roundTrigger++
            gameState = GameState.WATCHING
        } else {
            players.clear()
            players.addAll(List(gameMode.playerCount) { i -> PlayerSlot(i, "P${i + 1}", PLAYER_COLORS[i], isAlive = true, roundsSurvived = 0) })
            activePlayerIndex = 0
            isRedemptionTurnActive = false
            sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
            isHandoffVisible = true
            gameState = GameState.WATCHING
        }
    }

    fun startWatchMyRoundReplay() {
        if (gameState != GameState.GAME_OVER || sequence.isEmpty() || isReplayingLostRound) return
        ghostReplayJob?.cancel()
        ghostReplayJob = coroutineScope.launch {
            isReplayingLostRound = true
            wrongShakePadId = null
            activePadId = null
            ghostReplayStep = 0
            delay(300L)
            for ((index, pad) in sequence.withIndex()) {
                if (gameState != GameState.GAME_OVER) break
                ghostReplayStep = index + 1
                activePadId = pad.id
                if (feedbackEnabled && effectiveVolume > 0f) {
                    audioSynth.playPadSpecTone(pad, effectiveVolume)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                delay(flashDurationMs)
                activePadId = null
                delay(gapDurationMs)
            }
            activePadId = null
            isReplayingLostRound = false
        }
    }

    LaunchedEffect(gameState, roundTrigger, isLifecyclePaused, isRoundClearCelebration, isHandoffVisible) {
        if (gameState == GameState.WATCHING && sequence.isNotEmpty() && !isLifecyclePaused && !isRoundClearCelebration && !isHandoffVisible) {
            activePadId = null
            delay(450L)
            for (pad in sequence) {
                if (isLifecyclePaused || gameState != GameState.WATCHING || isHandoffVisible) return@LaunchedEffect
                activePadId = pad.id
                if (feedbackEnabled && effectiveVolume > 0f) {
                    audioSynth.playPadSpecTone(pad, effectiveVolume)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                delay(flashDurationMs)
                activePadId = null
                delay(gapDurationMs)
            }
            if (!isLifecyclePaused && gameState == GameState.WATCHING && !isHandoffVisible) {
                playerStepIndex = 0
                gameState = GameState.PLAYING
            }
        }
    }

    fun onPadTapped(tappedPad: PadSpec) {
        if (gameState != GameState.PLAYING || sequence.isEmpty() || isLifecyclePaused || isHandoffVisible) return
        resumedBannerNote = null
        val expectedPad = sequence.getOrNull(playerStepIndex) ?: return

        if (tappedPad.id == expectedPad.id) {
            tapFlashJob?.cancel()
            wrongShakePadId = null
            activePadId = tappedPad.id
            if (feedbackEnabled && effectiveVolume > 0f) {
                audioSynth.playPadSpecTone(tappedPad, effectiveVolume)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            val nextStep = playerStepIndex + 1
            playerStepIndex = nextStep
            if (nextStep >= sequence.size) {
                val completedRound = sequence.size
                if (gameMode == GameMode.SOLO) {
                    currentScore += 1
                    updateBestScoreIfNeeded(currentScore)
                    if (activeChallenge != null) {
                        val ch = activeChallenge!!
                        if (currentScore > ch.bestScore) {
                            ch.bestScore = currentScore
                            ChallengeStorage.updateChallenge(context, ch)
                            savedChallenges = ChallengeStorage.loadChallenges(context)
                        }
                        if (currentScore >= ch.targetScore) {
                            isChallengeWon = true
                        }
                    }
                    clearedRoundNumber = completedRound
                    isRoundClearCelebration = true
                    gameState = GameState.WATCHING
                    tapFlashJob = coroutineScope.launch {
                        delay(140L)
                        activePadId = null
                        if (feedbackEnabled && effectiveVolume > 0f) {
                            audioSynth.playRoundClearArpeggio(effectiveVolume)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        allPadsConfettiFlash = true
                        delay(260L)
                        allPadsConfettiFlash = false
                        delay(240L)
                        val nextPad = if (activeChallenge != null) {
                            val rng = challengeRng ?: java.util.Random(activeChallenge!!.seed.toLong()).also { challengeRng = it }
                            activePadPool[rng.nextInt(activePadPool.size)]
                        } else {
                            activePadPool[Random.nextInt(activePadPool.size)]
                        }
                        sequence.add(nextPad)
                        isRoundClearCelebration = false
                        roundTrigger++
                    }
                } else {
                    val activePlayer = players[activePlayerIndex]
                    players[activePlayerIndex] = activePlayer.copy(roundsSurvived = activePlayer.roundsSurvived + 1)
                    clearedRoundNumber = completedRound
                    isRoundClearCelebration = true
                    gameState = GameState.WATCHING
                    tapFlashJob = coroutineScope.launch {
                        delay(140L)
                        activePadId = null
                        if (feedbackEnabled && effectiveVolume > 0f) {
                            audioSynth.playRoundClearArpeggio(effectiveVolume)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        allPadsConfettiFlash = true
                        delay(260L)
                        allPadsConfettiFlash = false
                        delay(240L)
                        isRoundClearCelebration = false

                        if (isRedemptionTurnActive) {
                            concludeMatch()
                        } else {
                            sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
                            var nextIdx = (activePlayerIndex + 1) % players.size
                            while (!players[nextIdx].isAlive) {
                                nextIdx = (nextIdx + 1) % players.size
                            }
                            activePlayerIndex = nextIdx
                            playerStepIndex = 0
                            isHandoffVisible = true
                        }
                    }
                }
            } else {
                tapFlashJob = coroutineScope.launch {
                    delay(180L)
                    if (gameState == GameState.PLAYING) activePadId = null
                }
            }
        } else {
            tapFlashJob?.cancel()
            activePadId = null
            allPadsConfettiFlash = false
            failedStepNumber = playerStepIndex + 1
            failedExpectedPad = expectedPad
            failedTappedPad = tappedPad
            wrongShakePadId = tappedPad.id
            if (feedbackEnabled && effectiveVolume > 0f) {
                audioSynth.playErrorBuzz(effectiveVolume)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            tapFlashJob = coroutineScope.launch {
                wrongPadShakeOffset.snapTo(0f)
                for (target in listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)) {
                    wrongPadShakeOffset.animateTo(target, tween(45, easing = FastOutSlowInEasing))
                }
            }

            if (gameMode == GameMode.SOLO) {
                updateBestScoreIfNeeded(currentScore)
                if (activeChallenge != null) {
                    val ch = activeChallenge!!
                    val newTries = (challengeAttemptsLeft - 1).coerceAtLeast(0)
                    challengeAttemptsLeft = newTries
                    ch.attemptsLeft = newTries
                    if (currentScore > ch.bestScore) {
                        ch.bestScore = currentScore
                    }
                    ChallengeStorage.updateChallenge(context, ch)
                    savedChallenges = ChallengeStorage.loadChallenges(context)
                    if (currentScore >= ch.targetScore) {
                        isChallengeWon = true
                        isChallengeLost = false
                    } else if (newTries <= 0) {
                        isChallengeLost = true
                        isChallengeWon = false
                    }
                }
                gameState = GameState.GAME_OVER
            } else {
                val eliminatedPlayer = players[activePlayerIndex]
                players[activePlayerIndex] = eliminatedPlayer.copy(isAlive = false)
                eliminatedBannerText = "P${eliminatedPlayer.index + 1} MISSED - OUT!"

                tapFlashJob = coroutineScope.launch {
                    delay(900L)
                    eliminatedBannerText = null
                    val alive = players.filter { it.isAlive }
                    when {
                        alive.isEmpty() -> concludeMatch()
                        alive.size == 1 -> {
                            val survivor = alive.first()
                            val maxDeadRounds = players.filter { !it.isAlive }.maxOfOrNull { it.roundsSurvived } ?: 0
                            if (survivor.roundsSurvived > maxDeadRounds) {
                                concludeMatch()
                            } else {
                                isRedemptionTurnActive = true
                                activePlayerIndex = survivor.index
                                playerStepIndex = 0
                                isHandoffVisible = true
                                gameState = GameState.WATCHING
                            }
                        }
                        else -> {
                            var nextIdx = (activePlayerIndex + 1) % players.size
                            while (!players[nextIdx].isAlive) {
                                nextIdx = (nextIdx + 1) % players.size
                            }
                            activePlayerIndex = nextIdx
                            playerStepIndex = 0
                            isHandoffVisible = true
                            gameState = GameState.WATCHING
                        }
                    }
                }
            }
        }
    }

    val isLargeScreen = configuration.smallestScreenWidthDp > 500
    val maxContentWidth = if (isLargeScreen) 680.dp else 560.dp
    val gridCapDp = if (isLargeScreen) 480.dp else 360.dp
    val showConfigRow = gameState == GameState.IDLE || (gameState == GameState.GAME_OVER && gameMode == GameMode.SOLO)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF090D16)), radius = 1400f))
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = maxContentWidth).align(Alignment.Center).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TopTitleRow(
                        feedbackEnabled = feedbackEnabled && audioVolume > 0f,
                        onToggleFeedback = { feedbackEnabled = !feedbackEnabled },
                        onOpenSettings = { showSettingsSheet = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (activeChallenge != null && gameState != GameState.IDLE) {
                        InGameChallengeBanner(
                            rivalName = activeChallenge!!.rivalName,
                            targetScore = activeChallenge!!.targetScore,
                            triesLeft = challengeAttemptsLeft,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    if (gameMode == GameMode.SOLO) {
                        ScoreboardRow(currentScore = currentScore, bestScore = bestScore, gridSize = gridSize)
                    } else {
                        if (gameState == GameState.IDLE) {
                            val wins = remember(playerWinsVersion, gameMode) {
                                (0 until 4).map { readPlayerWins(sharedPrefs, it) }
                            }
                            MultiplayerWinsSummaryRow(gameMode = gameMode, wins = wins)
                        } else {
                            PlayerRail(
                                players = players,
                                activePlayerIndex = activePlayerIndex,
                                isRedemptionTurnActive = isRedemptionTurnActive
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = showConfigRow,
                        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            if (gameState == GameState.IDLE) {
                                ModeSelectorRow(selectedMode = gameMode, enabled = !isReplayingLostRound) {
                                    resetToIdleForMode(it)
                                }
                            }
                            ConfigSummaryPillsRow(gridSize, paceMode, !isReplayingLostRound) { showSettingsSheet = true }
                            if (gameState == GameState.IDLE && gameMode == GameMode.SOLO) {
                                ActiveChallengesRail(
                                    challenges = savedChallenges,
                                    onPlayChallenge = ::startChallenge,
                                    onDeleteChallenge = { code ->
                                        ChallengeStorage.deleteChallenge(context, code)
                                        savedChallenges = ChallengeStorage.loadChallenges(context)
                                    },
                                    onOpenEnterModal = { showEnterChallengeDialog = true }
                                )
                            }
                        }
                    }
                }

                if (gameMode != GameMode.SOLO && isMatchComplete) {
                    MatchCompleteCard(
                        standings = matchStandings,
                        sequenceSize = sequence.size,
                        isReplaying = isReplayingLostRound,
                        onRematch = ::startNewGame,
                        onSetup = {
                            resetRunState()
                            isMatchComplete = false
                            gameState = GameState.IDLE
                        },
                        onSoloMode = {
                            resetToIdleForMode(GameMode.SOLO)
                        },
                        onWatchFinalRound = ::startWatchMyRoundReplay
                    )
                } else if (gameState == GameState.IDLE && !hasSeenExplainer) {
                    FirstRunExplainerCard(onDismiss = ::dismissExplainer)
                } else {
                    StatusBanner(
                        gameState = gameState,
                        gameMode = gameMode,
                        gridSize = gridSize,
                        currentScore = currentScore,
                        playerStepIndex = playerStepIndex,
                        sequenceSize = sequence.size,
                        flashDurationMs = flashDurationMs,
                        isRoundClearCelebration = isRoundClearCelebration,
                        clearedRoundNumber = clearedRoundNumber,
                        resumedBannerNote = resumedBannerNote,
                        failedStepNumber = failedStepNumber,
                        failedExpectedPad = failedExpectedPad,
                        failedTappedPad = failedTappedPad,
                        isReplayingLostRound = isReplayingLostRound,
                        ghostReplayStep = ghostReplayStep,
                        activePlayer = if (gameMode != GameMode.SOLO && players.isNotEmpty()) players.getOrNull(activePlayerIndex) else null,
                        isRedemptionTurn = isRedemptionTurnActive,
                        eliminatedBannerText = eliminatedBannerText,
                        activeChallenge = activeChallenge,
                        challengeAttemptsLeft = challengeAttemptsLeft,
                        isChallengeWon = isChallengeWon,
                        isChallengeLost = isChallengeLost,
                        onWatchMyRound = ::startWatchMyRoundReplay
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(vertical = 6.dp).testTag("pad_grid_container"),
                    contentAlignment = Alignment.Center
                ) {
                    val animatedGridSide by animateDpAsState(
                        targetValue = minOf(maxWidth, maxHeight, gridCapDp),
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "animatedGridSide"
                    )
                    SimonPadGrid(
                        modifier = Modifier.size(animatedGridSide),
                        gridSize = gridSize,
                        pads = activePadPool,
                        activePadId = activePadId,
                        allPadsConfettiFlash = allPadsConfettiFlash,
                        wrongShakePadId = wrongShakePadId,
                        wrongShakeOffsetPx = wrongPadShakeOffset.value,
                        showColorblindShapes = showColorblindShapes,
                        inputEnabled = gameState == GameState.PLAYING && !isReplayingLostRound && !isLifecyclePaused && !isHandoffVisible,
                        onPadClick = ::onPadTapped
                    )
                }

                if (gameMode == GameMode.SOLO || !isMatchComplete) {
                    BottomActionSection(
                        gameState = gameState,
                        gameMode = gameMode,
                        activeChallenge = activeChallenge,
                        challengeAttemptsLeft = challengeAttemptsLeft,
                        isChallengeWon = isChallengeWon,
                        isChallengeLost = isChallengeLost,
                        onStartOrRetry = ::startNewGame,
                        onShareChallenge = {
                            val gridTag = when (gridSize) {
                                GridSize.TWO_BY_TWO -> "2x2"
                                GridSize.THREE_BY_THREE -> "3x3"
                                GridSize.FOUR_BY_FOUR -> "4x4"
                            }
                            val seed = ChallengeSeed.generateRandomSeed()
                            val scoreToShare = currentScore.coerceAtLeast(1)
                            val code = ChallengeSeed.formatChallengeCode(gridTag, scoreToShare, seed)
                            coroutineScope.launch {
                                ChallengeShareHelper.shareChallenge(
                                    context = context,
                                    code = code,
                                    gridSizeTag = gridTag,
                                    score = scoreToShare,
                                    attempts = 1
                                )
                            }
                        },
                        onReplyToRival = {
                            if (activeChallenge != null) {
                                val ch = activeChallenge!!
                                coroutineScope.launch {
                                    ChallengeShareHelper.shareReplyResult(
                                        context = context,
                                        code = ch.code,
                                        gridSizeTag = ch.gridSizeTag,
                                        myScore = currentScore,
                                        targetScore = ch.targetScore,
                                        rivalName = ch.rivalName,
                                        won = currentScore >= ch.targetScore
                                    )
                                }
                            }
                        },
                        onExitChallenge = {
                            activeChallenge = null
                            challengeRng = null
                            resetRunState()
                            gameState = GameState.IDLE
                        }
                    )
                }
            }

            if (showEnterChallengeDialog) {
                EnterChallengeDialog(
                    onDismiss = { showEnterChallengeDialog = false },
                    onSubmit = { newItem ->
                        ChallengeStorage.saveChallenge(context, newItem)
                        savedChallenges = ChallengeStorage.loadChallenges(context)
                        showEnterChallengeDialog = false
                        startChallenge(newItem)
                    }
                )
            }

            AnimatedVisibility(
                visible = isHandoffVisible && players.isNotEmpty(),
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 },
                exit = fadeOut(tween(200))
            ) {
                val activeP = players.getOrElse(activePlayerIndex) { PlayerSlot(0, "P1", PLAYER_COLORS[0]) }
                HandoffWaveOverlay(
                    player = activeP,
                    sequenceLength = sequence.size,
                    isRedemption = isRedemptionTurnActive,
                    onReady = ::onHandoffTapped
                )
            }

            if (showSettingsSheet) {
                SettingsSheetDialog(
                    currentGridSize = gridSize,
                    currentPaceMode = paceMode,
                    feedbackEnabled = feedbackEnabled,
                    volume = audioVolume,
                    shapesEnabled = showColorblindShapes,
                    isMidRun = gameState == GameState.WATCHING || gameState == GameState.PLAYING,
                    onSelectGridSize = { if (it != gridSize) resetToIdleForConfigChange(it, paceMode) },
                    onSelectPaceMode = { if (it != paceMode) resetToIdleForConfigChange(gridSize, it) },
                    onToggleSound = { feedbackEnabled = it },
                    onVolumeChange = ::updateVolume,
                    onToggleShapes = ::toggleColorblindShapes,
                    onResetMultiplayerWins = {
                        resetMultiplayerWins(sharedPrefs)
                        playerWinsVersion++
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }
        }
    }
}

@Composable
private fun HeaderIconBtn(icon: ImageVector, contentDesc: String, tint: Color, borderColor: Color, tag: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).background(Color(0xFF1E293B)).border(1.dp, borderColor, CircleShape).testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = tint)
    }
}

@Composable
private fun TopTitleRow(
    feedbackEnabled: Boolean,
    onToggleFeedback: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF10B981)))
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFEF4444)))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "GLOWBACK", color = Color(0xFFF8FAFC), fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIconBtn(
                icon = if (feedbackEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDesc = if (feedbackEnabled) "Mute sound and haptics" else "Unmute sound and haptics",
                tint = if (feedbackEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                borderColor = Color(0xFF334155),
                tag = "audio_toggle_button",
                onClick = onToggleFeedback
            )
            HeaderIconBtn(
                icon = Icons.Default.Settings,
                contentDesc = "Open Settings",
                tint = Color(0xFFCBD5E1),
                borderColor = Color(0xFF334155),
                tag = "settings_button",
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun RowScope.ScoreCard(title: String, value: Int, icon: ImageVector, accent: Color, valueColor: Color, tag: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = title, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value.toString(), color = valueColor, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun ScoreboardRow(currentScore: Int, bestScore: Int, gridSize: GridSize) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScoreCard("CURRENT SCORE", currentScore, Icons.Default.Bolt, Color(0xFF38BDF8), Color(0xFFF8FAFC), "current_score_text")
        ScoreCard("BEST (${gridSize.label})", bestScore, Icons.Default.EmojiEvents, Color(0xFFFBBF24), Color(0xFFFBBF24), "best_score_text")
    }
}

@Composable
private fun ConfigSummaryPillsRow(gridSize: GridSize, paceMode: PaceMode, enabled: Boolean, onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple("Grid ${gridSize.label}", Icons.Default.GridView, "config_pill_grid"),
            Triple("Pace ${paceMode.shortLabel}", Icons.Default.Speed, "config_pill_pace")
        ).forEach { (label, icon, tag) ->
            Surface(
                onClick = onOpenSettings,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                modifier = Modifier.heightIn(min = 44.dp).testTag(tag)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Text(text = label, color = Color(0xFFF8FAFC), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ModeSelectorRow(
    selectedMode: GameMode,
    enabled: Boolean,
    onSelectMode: (GameMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "MODE",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(end = 2.dp)
        )
        GameMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Surface(
                onClick = { onSelectMode(mode) },
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF1E293B),
                border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)),
                modifier = Modifier.weight(1f).height(38.dp).testTag(mode.tagId)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = mode.label,
                        color = if (isSelected) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiplayerWinsSummaryRow(
    gameMode: GameMode,
    wins: List<Int>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("wins_summary_bar"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until gameMode.playerCount) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PLAYER_COLORS[i]))
                    Text(text = "P${i + 1}:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "${wins.getOrElse(i) { 0 }}W", color = Color(0xFFF8FAFC), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                }
                if (i < gameMode.playerCount - 1) {
                    Text(text = "•", color = Color(0xFF475569), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PlayerRail(
    players: List<PlayerSlot>,
    activePlayerIndex: Int,
    isRedemptionTurnActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        players.forEach { p ->
            val isActive = p.index == activePlayerIndex && p.isAlive
            val isRedemption = isActive && isRedemptionTurnActive
            val tag = "player_rail_p${p.index + 1}"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag(tag),
                shape = RoundedCornerShape(14.dp),
                color = when {
                    !p.isAlive -> Color(0xFF1E293B).copy(alpha = 0.45f)
                    isActive -> p.color.copy(alpha = 0.25f)
                    else -> Color(0xFF1E293B)
                },
                border = BorderStroke(
                    width = if (isActive) 2.dp else 1.dp,
                    color = when {
                        !p.isAlive -> Color(0xFF334155)
                        isRedemption -> Color(0xFFF59E0B)
                        isActive -> p.color
                        else -> p.color.copy(alpha = 0.45f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (p.isAlive) p.color else Color(0xFFEF4444))
                        )
                        Text(
                            text = "P${p.index + 1}",
                            color = if (p.isAlive) Color(0xFFF8FAFC) else Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = when {
                            !p.isAlive -> "OUT"
                            isRedemption -> "MATCH"
                            else -> "${p.roundsSurvived} R"
                        },
                        color = when {
                            !p.isAlive -> Color(0xFFEF4444)
                            isRedemption -> Color(0xFFFBBF24)
                            isActive -> p.color
                            else -> Color(0xFFCBD5E1)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun HandoffWaveOverlay(
    player: PlayerSlot,
    sequenceLength: Int,
    isRedemption: Boolean,
    onReady: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "handoff_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btn_pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        player.color.copy(alpha = 0.38f),
                        Color(0xFF090D16).copy(alpha = 0.96f),
                        Color(0xFF030712)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onReady
            )
            .testTag("handoff_card"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative radial neon glow behind the player badge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height * 0.38f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(player.color.copy(alpha = glowAlpha), Color.Transparent),
                    center = centerOffset,
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = centerOffset
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Player Avatar Badge with glowing neon border
            Surface(
                modifier = Modifier.size(92.dp),
                shape = CircleShape,
                color = Color(0xFF0F172A),
                border = BorderStroke(3.dp, player.color),
                shadowElevation = 10.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "P${player.index + 1}",
                        color = player.color,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Typography: Large bold text saying "PASS TO PLAYER [N]"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "NEXT TURN",
                    color = player.color.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "PASS TO PLAYER ${player.index + 1}",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )
            }

            // Subtext: "Sequence Length: [X]"
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, player.color.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = player.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sequence Length: $sequenceLength",
                        color = Color(0xFFF8FAFC),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (isRedemption) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Text(
                        text = "⚡ REDEMPTION TURN • MATCH ON THE LINE",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large, pulsing "TAP WHEN READY" button at the bottom
            Button(
                onClick = onReady,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(58.dp)
                    .scale(pulseScale)
                    .testTag("handoff_ready_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = player.color,
                    contentColor = Color(0xFF090D16)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "TAP WHEN READY",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            Text(
                text = "Tap to begin watching the sequence",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MatchCompleteCard(
    standings: List<PlayerStanding>,
    sequenceSize: Int,
    isReplaying: Boolean,
    onRematch: () -> Unit,
    onSetup: () -> Unit,
    onSoloMode: () -> Unit,
    onWatchFinalRound: () -> Unit
) {
    val winners = standings.filter { it.isWinner }
    val isTie = winners.size > 1
    val winnerColor = if (isTie) Color(0xFF38BDF8) else winners.firstOrNull()?.player?.color ?: Color(0xFFF59E0B)
    val titleText = if (isTie) "TIE FOR 1ST PLACE!" else "${winners.firstOrNull()?.player?.name ?: "Player 1"} WINS!"

    val infiniteTransition = rememberInfiniteTransition(label = "podium_celebration")
    val championGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "champ_glow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_complete_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141E33)),
        border = BorderStroke(2.dp, if (isTie) Color(0xFF38BDF8) else Color(0xFFF59E0B).copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "MATCH COMPLETE",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Text(
                text = "${if (isTie) "🤝 " else "👑 "}$titleText",
                color = winnerColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("status_header_text")
            )

            Text(
                text = "Final sequence reached $sequenceSize steps",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("status_subtitle_text")
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 2D VICTORY PODIUM
            VictoryPodium(
                standings = standings,
                glowAlpha = championGlowAlpha
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Primary buttons: [REMATCH] [SETUP]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRematch,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color(0xFF0F172A)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("rematch_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Rematch", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = onSetup,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("setup_button")
                ) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Setup", color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Lower Action Row: [SOLO MODE] and [WATCH FINAL ROUND]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSoloMode,
                    modifier = Modifier.testTag("solo_mode_button")
                ) {
                    Text(
                        text = "← Solo Mode",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onWatchFinalRound,
                    enabled = !isReplaying,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isReplaying) Color(0xFF475569) else Color(0xFFF59E0B)),
                    modifier = Modifier.height(36.dp).testTag("watch_final_round_button")
                ) {
                    val c = if (isReplaying) Color(0xFF64748B) else Color(0xFFFBBF24)
                    Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = c, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isReplaying) "Replaying" else "Final Round",
                        color = c,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VictoryPodium(
    standings: List<PlayerStanding>,
    glowAlpha: Float
) {
    if (standings.isEmpty()) return

    val isTie = standings.filter { it.isWinner }.size > 1

    val podiumItems = when {
        standings.size == 2 && !isTie -> listOf(standings[1], standings[0])
        standings.size >= 3 && !isTie -> listOf(standings[1], standings[0], standings[2])
        standings.size >= 3 && isTie -> listOf(standings[0], standings[1], standings[2])
        else -> standings.take(3)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            podiumItems.forEach { standing ->
                val isChampion = standing.isWinner
                val accentColor = when (standing.rank) {
                    1 -> Color(0xFFF59E0B) // Gold
                    2 -> Color(0xFF94A3B8) // Silver
                    3 -> Color(0xFFD97706) // Bronze
                    else -> Color(0xFF64748B)
                }
                val pedestalHeight = when (standing.rank) {
                    1 -> 80.dp
                    2 -> 56.dp
                    3 -> 40.dp
                    else -> 32.dp
                }
                val medalIcon = when (standing.rank) {
                    1 -> if (isTie) "🤝" else "👑"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "${standing.rank}"
                }

                PodiumColumn(
                    standing = standing,
                    pedestalHeight = pedestalHeight,
                    accentColor = accentColor,
                    medalLabel = medalIcon,
                    pedestalNumber = "${standing.rank}",
                    isChampion = isChampion,
                    glowAlpha = glowAlpha,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4th-place runner-up row if 4 players participated
        val runnerUps = standings.drop(3)
        if (runnerUps.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                runnerUps.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                            .border(BorderStroke(1.dp, Color(0xFF334155)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(s.player.color))
                            Text(
                                text = "${s.rank}th • Player ${s.player.index + 1}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${s.player.roundsSurvived} Rounds",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    standing: PlayerStanding,
    pedestalHeight: Dp,
    accentColor: Color,
    medalLabel: String,
    pedestalNumber: String,
    isChampion: Boolean,
    glowAlpha: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Champion Badge or Medal Icon
        if (isChampion) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFF59E0B),
                contentColor = Color(0xFF0F172A)
            ) {
                Text(
                    text = "CHAMPION",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        } else {
            Text(text = medalLabel, fontSize = 14.sp)
        }

        // Circular Player Avatar
        Box(
            modifier = Modifier
                .size(if (isChampion) 36.dp else 30.dp)
                .clip(CircleShape)
                .background(standing.player.color)
                .border(2.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "P${standing.player.index + 1}",
                color = Color(0xFF090D16),
                fontSize = if (isChampion) 14.sp else 12.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Player ${standing.player.index + 1}",
            color = if (isChampion) Color.White else Color(0xFFCBD5E1),
            fontSize = if (isChampion) 12.sp else 11.sp,
            fontWeight = if (isChampion) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )

        Text(
            text = "${standing.player.roundsSurvived} R",
            color = accentColor,
            fontSize = if (isChampion) 13.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Pedestal Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = if (isChampion) 0.35f else 0.20f),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .border(
                    BorderStroke(1.5.dp, accentColor),
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pedestalNumber,
                color = accentColor,
                fontSize = if (isChampion) 22.sp else 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SettingsSheetDialog(
    currentGridSize: GridSize,
    currentPaceMode: PaceMode,
    feedbackEnabled: Boolean,
    volume: Float,
    shapesEnabled: Boolean,
    isMidRun: Boolean,
    onSelectGridSize: (GridSize) -> Unit,
    onSelectPaceMode: (PaceMode) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleShapes: () -> Unit,
    onResetMultiplayerWins: () -> Unit,
    onDismiss: () -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color(0xFF0F172A),
        labelColor = Color(0xFFCBD5E1),
        selectedContainerColor = Color(0xFF0284C7).copy(alpha = 0.32f),
        selectedLabelColor = Color(0xFF38BDF8)
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).widthIn(max = 460.dp).testTag("settings_sheet_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.5.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Settings", color = Color(0xFFF8FAFC), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_close_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Settings", tint = Color(0xFF94A3B8))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Grid size", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GridSize.entries.forEach { opt ->
                            val sel = opt == currentGridSize
                            FilterChip(
                                selected = sel,
                                onClick = { onSelectGridSize(opt) },
                                label = {
                                    Text(
                                        opt.label,
                                        fontSize = 14.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(true, sel, borderColor = Color(0xFF334155), selectedBorderColor = Color(0xFF38BDF8)),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag(opt.testTagId)
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Pace", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaceMode.entries.forEach { mode ->
                            val sel = mode == currentPaceMode
                            val tag = if (mode == PaceMode.CLASSIC) "pace_mode_classic" else "pace_mode_progressive"
                            FilterChip(
                                selected = sel,
                                onClick = { onSelectPaceMode(mode) },
                                label = {
                                    Text(
                                        mode.shortLabel,
                                        fontSize = 14.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(true, sel, borderColor = Color(0xFF334155), selectedBorderColor = Color(0xFF38BDF8)),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag(tag)
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = "Colorblind shape glyphs", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (currentGridSize == GridSize.TWO_BY_TWO) "Show distinct geometric shapes on 2×2 pads" else "Always active on 3×3 and 4×4 boards",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = if (currentGridSize == GridSize.TWO_BY_TWO) shapesEnabled else true,
                            onCheckedChange = { if (currentGridSize == GridSize.TWO_BY_TWO) onToggleShapes() },
                            enabled = currentGridSize == GridSize.TWO_BY_TWO,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF38BDF8),
                                checkedTrackColor = Color(0xFF0284C7).copy(alpha = 0.4f),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155),
                                disabledCheckedThumbColor = Color(0xFF38BDF8).copy(alpha = 0.6f),
                                disabledCheckedTrackColor = Color(0xFF0284C7).copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.testTag("shape_toggle_button")
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Sound & Volume", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { onToggleSound(true) },
                                shape = RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp),
                                color = if (feedbackEnabled) Color(0xFF0284C7).copy(alpha = 0.35f) else Color.Transparent,
                                modifier = Modifier
                                    .width(62.dp)
                                    .height(34.dp)
                                    .testTag("sound_on_chip")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "On",
                                        color = if (feedbackEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Surface(
                                onClick = { onToggleSound(false) },
                                shape = RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp),
                                color = if (!feedbackEnabled) Color(0xFF0284C7).copy(alpha = 0.35f) else Color.Transparent,
                                modifier = Modifier
                                    .width(62.dp)
                                    .height(34.dp)
                                    .testTag("sound_muted_chip")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Muted",
                                        color = if (!feedbackEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val displayVol = if (feedbackEnabled) volume else 0f
                        Icon(
                            imageVector = if (feedbackEnabled && displayVol > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            tint = if (feedbackEnabled && displayVol > 0f) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                        Slider(
                            value = displayVol,
                            onValueChange = onVolumeChange,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.weight(1f).testTag("volume_slider")
                        )
                        Text(
                            text = "${(displayVol * 100).roundToInt()}%",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Multiplayer records", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Reset all saved Pass & Play wins (P1-P4)", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onResetMultiplayerWins,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("reset_wins_button")
                        ) {
                            Text(text = "Reset Wins", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = if (isMidRun) "Changing grid size or pace resets the current run and returns to READY."
                    else "Changing grid size or pace updates your active board and best score slot.",
                    color = Color(0xFF94A3B8), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_done_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color(0xFF0F172A))
                ) {
                    Text(text = "Done", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun FirstRunExplainerCard(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onDismiss).testTag("first_run_explainer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
        border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.75f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ExplainerStepItem(Icons.Default.Visibility, "WATCH", Color(0xFFFBBF24))
                Text(text = "→", color = Color(0xFF64748B), fontSize = 16.sp, fontWeight = FontWeight.Black)
                ExplainerStepItem(Icons.Default.TouchApp, "REPEAT", Color(0xFF10B981))
                Text(text = "→", color = Color(0xFF64748B), fontSize = 16.sp, fontWeight = FontWeight.Black)
                ExplainerStepItem(Icons.Default.Favorite, "SURVIVE", Color(0xFF38BDF8))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Each pad plays a musical note • Tap card to dismiss",
                color = Color(0xFF94A3B8), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("status_header_text")
            )
        }
    }
}

@Composable
private fun ExplainerStepItem(icon: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = label, color = Color(0xFFF8FAFC), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun StatusBanner(
    gameState: GameState,
    gameMode: GameMode = GameMode.SOLO,
    gridSize: GridSize,
    currentScore: Int,
    playerStepIndex: Int,
    sequenceSize: Int,
    flashDurationMs: Long,
    isRoundClearCelebration: Boolean,
    clearedRoundNumber: Int,
    resumedBannerNote: String?,
    failedStepNumber: Int,
    failedExpectedPad: PadSpec?,
    failedTappedPad: PadSpec?,
    isReplayingLostRound: Boolean,
    ghostReplayStep: Int,
    activePlayer: PlayerSlot? = null,
    isRedemptionTurn: Boolean = false,
    eliminatedBannerText: String? = null,
    activeChallenge: ChallengeItem? = null,
    challengeAttemptsLeft: Int = 3,
    isChallengeWon: Boolean = false,
    isChallengeLost: Boolean = false,
    onWatchMyRound: () -> Unit
) {
    val isMulti = gameMode != GameMode.SOLO
    val bannerScale by animateFloatAsState(
        targetValue = if (isRoundClearCelebration) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bannerPulseScale"
    )
    val bannerAccentColor by animateColorAsState(
        targetValue = when {
            activeChallenge != null && isChallengeWon -> Color(0xFF00E676)
            activeChallenge != null && isChallengeLost -> Color(0xFFEF4444)
            activeChallenge != null && gameState == GameState.GAME_OVER -> Color(0xFFF59E0B)
            eliminatedBannerText != null -> Color(0xFFEF4444)
            isRedemptionTurn -> Color(0xFFF59E0B)
            isRoundClearCelebration -> Color(0xFF34D399)
            gameState == GameState.IDLE -> Color(0xFF38BDF8)
            gameState == GameState.WATCHING -> Color(0xFFFBBF24)
            gameState == GameState.PLAYING -> if (isMulti && activePlayer != null) activePlayer.color else Color(0xFF10B981)
            gameState == GameState.GAME_OVER -> Color(0xFFEF4444)
            else -> Color(0xFF38BDF8)
        },
        animationSpec = tween(220),
        label = "bannerAccentColor"
    )
    val statusTitle = when {
        activeChallenge != null && isChallengeWon -> "Challenge Won! 🎉"
        activeChallenge != null && isChallengeLost -> "Challenge Lost"
        activeChallenge != null && gameState == GameState.GAME_OVER -> "Try Failed!"
        eliminatedBannerText != null -> "Player Out!"
        isRedemptionTurn -> "Redemption Turn!"
        isRoundClearCelebration -> if (isMulti && activePlayer != null) "${activePlayer.name} Cleared Round!" else "Round $clearedRoundNumber cleared!"
        gameState == GameState.IDLE -> "Ready to Play?"
        gameState == GameState.WATCHING -> if (isMulti && activePlayer != null) "${activePlayer.name} • Watch" else "Watch Carefully!"
        gameState == GameState.PLAYING -> if (isMulti && activePlayer != null) "${activePlayer.name}'s Turn!" else "Your Turn!"
        else -> "Game Over!"
    }
    val statusSubtitle = when {
        activeChallenge != null && isChallengeWon -> "You beat ${activeChallenge.rivalName}'s target of ${activeChallenge.targetScore} with score $currentScore!"
        activeChallenge != null && isChallengeLost -> "Out of tries! Target was ${activeChallenge.targetScore} (Best: ${activeChallenge.bestScore})"
        activeChallenge != null && gameState == GameState.GAME_OVER -> "$challengeAttemptsLeft tries remaining to beat target of ${activeChallenge.targetScore}"
        eliminatedBannerText != null -> eliminatedBannerText
        isRedemptionTurn -> "${activePlayer?.name ?: "Survivor"} must repeat $sequenceSize steps to win outright!"
        isRoundClearCelebration -> if (isMulti) "Next player up..." else "Get ready for Round ${clearedRoundNumber + 1}..."
        resumedBannerNote != null && (gameState == GameState.WATCHING || gameState == GameState.PLAYING) -> resumedBannerNote
        gameState == GameState.IDLE -> if (isMulti) "Pass the phone between turns • Last player standing wins" else "Memorize the glowing neon sequence and repeat it."
        gameState == GameState.WATCHING -> "Round ${sequenceSize.coerceAtLeast(1)} • Pace ${flashDurationMs}ms • Input Locked"
        gameState == GameState.PLAYING -> "Step ${playerStepIndex + 1} of $sequenceSize • Tap the matching pads"
        isReplayingLostRound -> "Replaying Round $sequenceSize (Step $ghostReplayStep of $sequenceSize) • Input Locked"
        failedExpectedPad != null && failedTappedPad != null && failedStepNumber > 0 -> {
            val exp = if (gridSize == GridSize.TWO_BY_TWO) failedExpectedPad.colorName.uppercase() else failedExpectedPad.glyph.displayName.uppercase()
            val hit = if (gridSize == GridSize.TWO_BY_TWO) failedTappedPad.colorName.uppercase() else failedTappedPad.glyph.displayName.uppercase()
            "Missed step $failedStepNumber of $sequenceSize - expected $exp, you hit $hit"
        }
        else -> "Final Score: $currentScore • Tap Play Again to beat your record!"
    }

    Card(
        modifier = Modifier.fillMaxWidth().scale(bannerScale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.92f)),
        border = BorderStroke(1.5.dp, bannerAccentColor.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = statusTitle,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "statusHeaderTransition"
            ) { title ->
                Text(
                    text = title, color = bannerAccentColor, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = if (title == statusTitle) Modifier.testTag("status_header_text") else Modifier
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = statusSubtitle, color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("status_subtitle_text")
            )
            if (gameState == GameState.GAME_OVER && sequenceSize > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Final Score: $currentScore", color = Color(0xFF94A3B8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = onWatchMyRound,
                        enabled = !isReplayingLostRound,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isReplayingLostRound) Color(0xFF475569) else Color(0xFF38BDF8)),
                        modifier = Modifier.heightIn(min = 44.dp).testTag("watch_my_round_button")
                    ) {
                        val c = if (isReplayingLostRound) Color(0xFF64748B) else Color(0xFF38BDF8)
                        Icon(imageVector = Icons.Default.Replay, contentDescription = null, tint = c, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isReplayingLostRound) "Replaying..." else "Watch My Round", color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (sequenceSize > 0 && (gameState == GameState.PLAYING || gameState == GameState.WATCHING)) {
                Spacer(modifier = Modifier.height(6.dp))
                val completed = when {
                    isRoundClearCelebration -> sequenceSize
                    gameState == GameState.PLAYING -> playerStepIndex
                    else -> 0
                }
                SequenceStepIndicator(totalSteps = sequenceSize, completedSteps = completed, accentColor = bannerAccentColor)
            }
        }
    }
}

@Composable
private fun SequenceStepIndicator(totalSteps: Int, completedSteps: Int, accentColor: Color) {
    val visibleDots = totalSteps.coerceAtMost(12)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until visibleDots) {
            val done = i < completedSteps
            Box(modifier = Modifier.size(if (done) 10.dp else 8.dp).clip(CircleShape).background(if (done) accentColor else Color(0xFF475569)))
        }
        if (totalSteps > visibleDots) {
            Text(text = "+${totalSteps - visibleDots}", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GlowPadGrid(
    modifier: Modifier = Modifier,
    gridSize: GridSize,
    pads: List<PadSpec>,
    activePadId: Int?,
    allPadsConfettiFlash: Boolean,
    wrongShakePadId: Int?,
    wrongShakeOffsetPx: Float,
    showColorblindShapes: Boolean,
    inputEnabled: Boolean,
    onPadClick: (PadSpec) -> Unit
) {
    val dim = gridSize.dimension
    val spacing = gridSize.spacingDp.dp
    val cornerRadius = gridSize.cornerDp.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (row in 0 until dim) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                for (col in 0 until dim) {
                    val pad = pads[row * dim + col]
                    GlowPadButton(
                        pad = pad,
                        gridSize = gridSize,
                        cornerRadius = cornerRadius,
                        isActive = allPadsConfettiFlash || activePadId == pad.id,
                        isWrongShake = wrongShakePadId == pad.id,
                        shakeOffsetPx = if (wrongShakePadId == pad.id) wrongShakeOffsetPx else 0f,
                        showShapeGlyph = if (gridSize == GridSize.TWO_BY_TWO) showColorblindShapes else true,
                        inputEnabled = inputEnabled,
                        onClick = { onPadClick(pad) },
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SimonPadGrid(
    modifier: Modifier = Modifier,
    gridSize: GridSize,
    pads: List<PadSpec>,
    activePadId: Int?,
    allPadsConfettiFlash: Boolean,
    wrongShakePadId: Int?,
    wrongShakeOffsetPx: Float,
    showColorblindShapes: Boolean,
    inputEnabled: Boolean,
    onPadClick: (PadSpec) -> Unit
) = GlowPadGrid(modifier, gridSize, pads, activePadId, allPadsConfettiFlash, wrongShakePadId, wrongShakeOffsetPx, showColorblindShapes, inputEnabled, onPadClick)

@Composable
private fun GlowPadButton(
    pad: PadSpec,
    gridSize: GridSize,
    cornerRadius: Dp,
    isActive: Boolean,
    isWrongShake: Boolean,
    shakeOffsetPx: Float,
    showShapeGlyph: Boolean,
    inputEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val is2x2 = gridSize == GridSize.TWO_BY_TWO
    val isFlashing = isActive && !isWrongShake
    val scale by animateFloatAsState(
        targetValue = if (isFlashing) 1.045f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "${pad.testTagId}_scale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isFlashing) pad.brightColor else pad.dimColor,
        animationSpec = tween(85, easing = FastOutSlowInEasing),
        label = "${pad.testTagId}_color"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isWrongShake -> Color(0xFFEF4444)
            isFlashing -> Color.White
            else -> pad.brightColor.copy(alpha = 0.45f)
        },
        animationSpec = tween(85),
        label = "${pad.testTagId}_border"
    )
    val padShape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .offset { IntOffset(shakeOffsetPx.roundToInt(), 0) }
            .aspectRatio(1f)
            .scale(scale)
            .shadow(if (isFlashing) 22.dp else 6.dp, padShape, ambientColor = if (isFlashing) pad.glowColor else Color.Black, spotColor = if (isFlashing) pad.brightColor else Color.Black)
            .clip(padShape)
            .background(containerColor)
            .border(if (isFlashing || isWrongShake) 3.5.dp else 2.dp, borderColor, padShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = inputEnabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (is2x2) {
                    if (showShapeGlyph) "${pad.colorName} ${pad.glyph.displayName} game pad" else "${pad.colorName} game pad"
                } else {
                    "${pad.glyph.displayName} ${pad.colorName} game pad"
                }
            }
            .testTag(pad.testTagId),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.65f
            val glowColors = if (isFlashing) listOf(Color.White.copy(alpha = 0.58f), pad.glowColor.copy(alpha = 0.45f), Color.Transparent)
            else listOf(pad.brightColor.copy(alpha = 0.18f), Color.Transparent)
            drawCircle(brush = Brush.radialGradient(glowColors, center = centerOffset, radius = maxRadius), radius = maxRadius, center = centerOffset)
            if (showShapeGlyph) {
                val glyphRadius = size.minDimension * 0.28f
                drawPadGlyph(pad.glyph, centerOffset, glyphRadius, isFlashing)
            }
        }
    }
}

@Composable
private fun SimonPadButton(
    pad: PadSpec,
    gridSize: GridSize,
    cornerRadius: Dp,
    isActive: Boolean,
    isWrongShake: Boolean,
    shakeOffsetPx: Float,
    showShapeGlyph: Boolean,
    inputEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = GlowPadButton(pad, gridSize, cornerRadius, isActive, isWrongShake, shakeOffsetPx, showShapeGlyph, inputEnabled, onClick, modifier)

private fun Path.addPolygon(cx: Float, cy: Float, r: Float, pts: List<Pair<Float, Float>>) {
    pts.forEachIndexed { idx, (dx, dy) ->
        if (idx == 0) moveTo(cx + dx * r, cy + dy * r) else lineTo(cx + dx * r, cy + dy * r)
    }
    close()
}

private fun DrawScope.drawPadGlyph(glyph: PadGlyph, center: Offset, radius: Float, isFlashing: Boolean) {
    val strokeW = (radius * 0.22f).coerceAtLeast(3.5f)
    val keylineW = (strokeW * 1.45f).coerceAtLeast(5f)
    val path = Path()
    val cx = center.x
    val cy = center.y

    when (glyph) {
        PadGlyph.TRIANGLE -> path.addPolygon(cx, cy, radius, listOf(0f to -0.77f, -0.82f to 0.75f, 0.82f to 0.75f))
        PadGlyph.CIRCLE -> path.addOval(Rect(cx - radius * 0.72f, cy - radius * 0.72f, cx + radius * 0.72f, cy + radius * 0.72f))
        PadGlyph.SQUARE -> path.addRect(Rect(cx - radius * 0.68f, cy - radius * 0.68f, cx + radius * 0.68f, cy + radius * 0.68f))
        PadGlyph.DIAMOND -> path.addPolygon(cx, cy, radius * 0.85f, listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f))
        PadGlyph.STAR -> {
            val pts = (0 until 10).map { i ->
                val a = -PI / 2.0 + i * (PI / 5.0)
                val rad = if (i % 2 == 0) 0.90f else 0.40f
                (rad * cos(a)).toFloat() to (rad * sin(a)).toFloat()
            }
            path.addPolygon(cx, cy, radius, pts)
        }
        PadGlyph.PLUS -> {
            val a = radius * 0.80f
            val t = radius * 0.28f
            val h = Path().apply { addRect(Rect(cx - a, cy - t, cx + a, cy + t)) }
            val v = Path().apply { addRect(Rect(cx - t, cy - a, cx + t, cy + a)) }
            path.op(h, v, PathOperation.Union)
        }
        PadGlyph.CROSS -> {
            val d1 = Path().apply { addPolygon(cx, cy, radius, listOf(-0.48f to -0.72f, 0.72f to 0.48f, 0.48f to 0.72f, -0.72f to -0.48f)) }
            val d2 = Path().apply { addPolygon(cx, cy, radius, listOf(0.48f to -0.72f, -0.72f to 0.48f, -0.48f to 0.72f, 0.72f to -0.48f)) }
            path.op(d1, d2, PathOperation.Union)
        }
        PadGlyph.RING -> {
            val outer = Path().apply { addOval(Rect(cx - radius * 0.82f, cy - radius * 0.82f, cx + radius * 0.82f, cy + radius * 0.82f)) }
            val inner = Path().apply { addOval(Rect(cx - radius * 0.38f, cy - radius * 0.38f, cx + radius * 0.38f, cy + radius * 0.38f)) }
            path.op(outer, inner, PathOperation.Difference)
        }
        PadGlyph.HEART -> {
            val r = radius * 1.05f
            path.moveTo(cx, cy + r * 0.76f)
            path.cubicTo(cx - r * 1.15f, cy + r * 0.12f, cx - r * 0.95f, cy - r * 0.88f, cx, cy - r * 0.32f)
            path.cubicTo(cx + r * 0.95f, cy - r * 0.88f, cx + r * 1.15f, cy + r * 0.12f, cx, cy + r * 0.76f)
            path.close()
        }
        PadGlyph.BOLT -> path.addPolygon(cx, cy, radius * 0.88f, listOf(0.15f to -1f, -0.65f to 0.08f, -0.05f to 0.08f, -0.25f to 1f, 0.65f to -0.12f, 0.05f to -0.12f))
        PadGlyph.MOON -> {
            val outer = Path().apply { addOval(Rect(cx - radius * 0.80f, cy - radius * 0.80f, cx + radius * 0.80f, cy + radius * 0.80f)) }
            val cut = Path().apply { addOval(Rect(cx - radius * 0.32f, cy - radius * 0.80f, cx + radius * 1.04f, cy + radius * 0.56f)) }
            path.op(outer, cut, PathOperation.Difference)
        }
        PadGlyph.HEXAGON -> {
            val pts = (0 until 6).map { i ->
                val a = i * (PI / 3.0) - PI / 6.0
                (0.82f * cos(a)).toFloat() to (0.82f * sin(a)).toFloat()
            }
            path.addPolygon(cx, cy, radius, pts)
        }
        PadGlyph.UP_ARROW -> path.addPolygon(cx, cy, radius * 0.85f, listOf(0f to -1f, 0.78f to -0.05f, 0.32f to -0.05f, 0.32f to 0.85f, -0.32f to 0.85f, -0.32f to -0.05f, -0.78f to -0.05f))
        PadGlyph.HOURGLASS -> path.addPolygon(cx, cy, radius * 0.78f, listOf(-1f to -1f, 1f to -1f, 0.18f to 0f, 1f to 1f, -1f to 1f, -0.18f to 0f))
        PadGlyph.CROWN -> path.addPolygon(cx, cy, radius * 0.82f, listOf(-1f to 0.65f, -0.85f to -0.55f, -0.35f to 0.05f, 0f to -0.75f, 0.35f to 0.05f, 0.85f to -0.55f, 1f to 0.65f))
        PadGlyph.SHIELD -> path.addPolygon(cx, cy, radius * 0.84f, listOf(0f to -1f, 0.78f to -0.62f, 0.68f to 0.18f, 0f to 0.92f, -0.68f to 0.18f, -0.78f to -0.62f))
    }

    if (isFlashing) {
        drawPath(path, Color(0xFF0F172A).copy(alpha = 0.90f), style = Stroke(width = keylineW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Color.White, style = Fill)
    } else {
        drawPath(path, Color.White.copy(alpha = 0.14f), style = Fill)
        drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun BottomActionSection(
    gameState: GameState,
    gameMode: GameMode = GameMode.SOLO,
    activeChallenge: ChallengeItem? = null,
    challengeAttemptsLeft: Int = 3,
    isChallengeWon: Boolean = false,
    isChallengeLost: Boolean = false,
    onStartOrRetry: () -> Unit,
    onShareChallenge: () -> Unit = {},
    onReplyToRival: () -> Unit = {},
    onExitChallenge: () -> Unit = {}
) {
    val isMulti = gameMode != GameMode.SOLO

    if (activeChallenge != null && gameState == GameState.GAME_OVER) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (challengeAttemptsLeft > 0 && !isChallengeWon) {
                Button(
                    onClick = onStartOrRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("challenge_retry_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Retry Turn ($challengeAttemptsLeft tries left)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else if (isChallengeWon) {
                Button(
                    onClick = onReplyToRival,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("challenge_reply_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Send Victory to Rival!", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onReplyToRival,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("challenge_reply_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Send Result to Rival", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (challengeAttemptsLeft > 0 && !isChallengeWon) {
                    OutlinedButton(
                        onClick = onReplyToRival,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("challenge_reply_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8))
                    ) {
                        Text(text = "Share Score", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = onExitChallenge,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("exit_challenge_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B))
                ) {
                    Text(text = "Exit Challenge", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        val label = when (gameState) {
            GameState.IDLE -> if (isMulti) "Start Match" else "Start Game"
            GameState.GAME_OVER -> if (isMulti) "Rematch" else "Play Again"
            GameState.WATCHING, GameState.PLAYING -> if (isMulti) "Restart Match" else "Restart Game"
        }
        val containerColor = when (gameState) {
            GameState.IDLE -> Color(0xFF10B981)
            GameState.GAME_OVER -> Color(0xFF38BDF8)
            GameState.WATCHING, GameState.PLAYING -> Color(0xFF334155)
        }
        val contentColor = if (gameState == GameState.IDLE || gameState == GameState.GAME_OVER) Color(0xFF0F172A) else Color(0xFFF8FAFC)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartOrRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_game_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
            ) {
                Icon(imageVector = if (gameState == GameState.IDLE) Icons.Default.PlayArrow else Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
            }

            if (gameState == GameState.GAME_OVER && gameMode == GameMode.SOLO) {
                OutlinedButton(
                    onClick = onShareChallenge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("share_challenge_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Share As Challenge", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(name = "Compact Portrait 640dp", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun GlowbackCompact640Preview() {
    MaterialTheme(colorScheme = GlowDarkColorScheme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { GlowbackGameScreen() } }
}

@Preview(name = "Phone 412x892", showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun GlowbackGamePreview() {
    MaterialTheme(colorScheme = GlowDarkColorScheme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { GlowbackGameScreen() } }
}

@Preview(name = "Tablet 600dp Adaptive Cap (480dp Grid)", showBackground = true, widthDp = 600, heightDp = 960)
@Composable
fun GlowbackTablet600Preview() {
    MaterialTheme(colorScheme = GlowDarkColorScheme) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { GlowbackGameScreen() } }
}

@Composable
fun SimonSaysCompact640Preview() = GlowbackCompact640Preview()
@Composable
fun SimonSaysGamePreview() = GlowbackGamePreview()
@Composable
fun SimonSaysTablet600Preview() = GlowbackTablet600Preview()

