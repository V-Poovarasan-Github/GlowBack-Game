package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.notification.NotificationScheduler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.challenge.ActiveChallengesRail
import com.example.challenge.AwaitingChallenge
import com.example.challenge.BoardScoreOption
import com.example.challenge.ChallengeItem
import com.example.challenge.ChallengeSeed
import com.example.challenge.ChallengeShareHelper
import com.example.challenge.ChallengeStorage
import com.example.challenge.ChallengerNameSetupDialog
import com.example.challenge.DidYouShareDialog
import com.example.challenge.EnterChallengeDialog
import com.example.challenge.FirstTimeShareGateDialog
import com.example.challenge.GameDifficulty
import com.example.challenge.HallOfFameScreen
import com.example.challenge.InGameChallengeBanner
import com.example.challenge.RivalRecord
import com.example.challenge.RunStateStorage
import com.example.challenge.SerializedRunState
import com.example.challenge.ShareTriesSelectorDialog

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

enum class AppScreen { HOME, LEADERBOARD }
enum class GameState { IDLE, WATCHING, PLAYING, GAME_OVER }
enum class ActiveDialog {
    NONE,
    TRIES_SELECTOR,
    ENTER_CHALLENGE,
    FIRST_TIME_SHARE_GATE,
    NAME_SETUP,
    SETTINGS_SHEET,
    NOTIFICATION_PERM,
    DISCARD_CONFIRM,
    DID_YOU_SHARE
}

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
                } else if (freqHz < 300.0) {
                    // Upper harmonics added to low frequencies (<300Hz e.g. 196Hz G3, 220Hz A3, 261Hz C4)
                    // so fundamental pitch is clearly audible on mobile speakers via psychoacoustic harmonics
                    sin(i * w) * 0.50 + sin(i * w * 2.0) * 0.30 + sin(i * w * 3.0) * 0.15 + sin(i * w * 4.0) * 0.05
                } else if (freqHz < 500.0) {
                    sin(i * w) * 0.75 + sin(i * w * 2.0) * 0.20 + sin(i * w * 3.0) * 0.05
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
    private val staticTrackPool = ConcurrentHashMap<ShortArray, AudioTrack>()

    val frequencyPcmBuffers: Map<Double, ShortArray> = (CLASSIC_2X2_PADS + GRID_16_PADS)
        .map { it.frequencyHz }.distinct().associateWith { synthesizeWavePcm(it, 240, 0.72) }

    val padPcmBuffers: Map<GlowColor, ShortArray> = GlowColor.entries.associateWith {
        frequencyPcmBuffers[it.frequencyHz] ?: synthesizeWavePcm(it.frequencyHz, 240, 0.72)
    }

    val errorBuzzPcm: ShortArray = synthesizeDescendingErrorBuzzPcm(420)
    val clearArpeggioPcm: ShortArray = synthesizeRisingArpeggioPcm()

    init {
        // Pre-allocate AudioTrack pool on background dispatcher (Dispatchers.Default) to prevent UI thread ANRs
        audioScope.launch {
            for (pcm in frequencyPcmBuffers.values) {
                runCatching {
                    staticTrackPool[pcm] = createStaticTrack(pcm)
                }
            }
            runCatching { staticTrackPool[errorBuzzPcm] = createStaticTrack(errorBuzzPcm) }
            runCatching { staticTrackPool[clearArpeggioPcm] = createStaticTrack(clearArpeggioPcm) }
        }
    }

    private fun createStaticTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

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
            try {
                var track = staticTrackPool[pcmBuffer]
                if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                    track = createStaticTrack(pcmBuffer)
                    staticTrackPool[pcmBuffer] = track
                }
                synchronized(track) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.flush()
                    track.playbackHeadPosition = 0
                    track.setVolume(vol)
                    track.play()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun release() {
        audioScope.cancel()
        for ((_, track) in staticTrackPool) {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        }
        staticTrackPool.clear()
    }
}

typealias SimonAudioSynthesizer = GlowAudioSynthesizer

internal const val PREFS_NAME = "glowback_prefs"
internal const val KEY_BEST_SCORE = "best_high_score"
internal const val KEY_INSTRUMENT_INVITE_DISMISSED = "glowback_instrument_invite_dismissed_v1"
internal const val KEY_CHALLENGE_NUDGE_SEEN = "glowback_challenge_nudge_v1"
internal const val KEY_AUDIO_VOLUME = "audio_volume"
internal const val KEY_VIBRATE_ENABLED = "vibrate_enabled"
internal const val KEY_FEEDBACK_MODE = "feedback_mode"
internal const val KEY_DIFFICULTY_MODE = "difficulty_mode"

enum class FeedbackMode {
    SOUND,
    VIBRATE,
    MUTE
}
internal const val KEY_LAUNCH_COUNT = "glowback_launch_count"
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

internal const val KEY_SCORES_MIGRATED_V2 = "scores_migrated_v2"

internal fun bestScoreKeyFor(gridSize: GridSize, paceMode: PaceMode, difficulty: GameDifficulty = GameDifficulty.EASY): String =
    "best_${gridSize.keyPrefix}_${paceMode.keySuffix}_${difficulty.name}"

internal fun checkAndMigrateScoresV2(prefs: SharedPreferences) {
    if (prefs.getBoolean(KEY_SCORES_MIGRATED_V2, false)) return
    val editor = prefs.edit()
    for (grid in GridSize.entries) {
        for (pace in PaceMode.entries) {
            val oldKey = "best_${grid.keyPrefix}_${pace.keySuffix}"
            val newEasyKey = "best_${grid.keyPrefix}_${pace.keySuffix}_EASY"
            val oldScore = prefs.getInt(oldKey, 0)
            if (oldScore > 0 && prefs.getInt(newEasyKey, 0) == 0) {
                editor.putInt(newEasyKey, oldScore)
            }
        }
    }
    val legacyBest = prefs.getInt(KEY_BEST_SCORE, 0)
    val easy2x2ClassicKey = "best_2x2_classic_EASY"
    if (legacyBest > prefs.getInt(easy2x2ClassicKey, 0)) {
        editor.putInt(easy2x2ClassicKey, legacyBest)
    }
    editor.putBoolean(KEY_SCORES_MIGRATED_V2, true)
    editor.apply()
}

internal fun readMigratedBestScore(prefs: SharedPreferences, gridSize: GridSize, paceMode: PaceMode, difficulty: GameDifficulty = GameDifficulty.EASY): Int {
    checkAndMigrateScoresV2(prefs)
    val key = bestScoreKeyFor(gridSize, paceMode, difficulty)
    return prefs.getInt(key, 0)
}

internal fun writeBestScore(prefs: SharedPreferences, gridSize: GridSize, paceMode: PaceMode, difficulty: GameDifficulty = GameDifficulty.EASY, score: Int) {
    val editor = prefs.edit().putInt(bestScoreKeyFor(gridSize, paceMode, difficulty), score)
    val baseKey = "best_${gridSize.keyPrefix}_${paceMode.keySuffix}"
    if (score > prefs.getInt(baseKey, 0)) {
        editor.putInt(baseKey, score)
    }
    if (score > prefs.getInt(KEY_BEST_SCORE, 0)) {
        editor.putInt(KEY_BEST_SCORE, score)
    }
    editor.apply()
}

internal fun getAllBoardScores(prefs: SharedPreferences): List<BoardScoreOption> {
    checkAndMigrateScoresV2(prefs)
    val list = mutableListOf<BoardScoreOption>()
    for (grid in GridSize.entries) {
        for (pace in PaceMode.entries) {
            for (diff in GameDifficulty.entries) {
                val score = readMigratedBestScore(prefs, grid, pace, diff)
                if (score > 0) {
                    val gridTag = when (grid) {
                        GridSize.TWO_BY_TWO -> "2x2"
                        GridSize.THREE_BY_THREE -> "3x3"
                        GridSize.FOUR_BY_FOUR -> "4x4"
                    }
                    list.add(BoardScoreOption(gridTag, pace.shortLabel, diff, score))
                }
            }
        }
    }
    return list.sortedByDescending { it.score }
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()

        NotificationScheduler.createNotificationChannel(this)

        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = GlowDarkColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GlowbackGameScreen(launchCount = launchCount)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 1. Time-delta tracking: save current timestamp as last_opened_timestamp
        prefs.edit().putLong(NotificationScheduler.KEY_LAST_OPENED_TIMESTAMP, System.currentTimeMillis()).apply()

        // 2. Permission sync: if OS revoked permission, toggle OFF daily notifications & cancel WorkManager
        val isSystemAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val isAppEnabled = prefs.getBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, true)

        if (!isSystemAllowed && isAppEnabled) {
            prefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, false).apply()
            NotificationScheduler.cancelReminder(this)
        } else if (isSystemAllowed && isAppEnabled) {
            NotificationScheduler.scheduleDailyReminder(this)
        }
    }
}

@Composable
fun SimonSaysGameScreen(modifier: Modifier = Modifier) = GlowbackGameScreen(modifier = modifier, launchCount = 4)

@Composable
fun GlowbackGameScreen(modifier: Modifier = Modifier, launchCount: Int = 1) {
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
    var audioVolume by remember { mutableFloatStateOf(sharedPrefs.getFloat(KEY_AUDIO_VOLUME, 0.85f).coerceIn(0f, 1f)) }
    var feedbackMode by remember {
        val saved = sharedPrefs.getString(KEY_FEEDBACK_MODE, null)
        val initial = if (saved != null) {
            try { FeedbackMode.valueOf(saved) } catch (_: Exception) { FeedbackMode.SOUND }
        } else {
            val isMuted = !sharedPrefs.getBoolean("feedback_enabled", true)
            val isVib = sharedPrefs.getBoolean(KEY_VIBRATE_ENABLED, false)
            if (isMuted) {
                if (isVib) FeedbackMode.VIBRATE else FeedbackMode.MUTE
            } else {
                FeedbackMode.SOUND
            }
        }
        mutableStateOf(initial)
    }
    val feedbackEnabled = feedbackMode == FeedbackMode.SOUND
    val isVibrateEnabled = feedbackMode == FeedbackMode.VIBRATE
    var difficultyMode by remember {
        val raw = sharedPrefs.getString(KEY_DIFFICULTY_MODE, GameDifficulty.EASY.name)
        mutableStateOf(try { GameDifficulty.valueOf(raw ?: "EASY") } catch (_: Exception) { GameDifficulty.EASY })
    }
    var showOnboardingPopup by remember { mutableStateOf(launchCount <= 3) }

    val vibrator = remember(context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    val vibrationAudioAttrs = remember {
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build()
    }

    fun executeHapticFeedback() {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}

        try {
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        vibrator.vibrate(
                            android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK),
                            vibrationAudioAttrs
                        )
                    } catch (_: Exception) {
                        vibrator.vibrate(
                            android.os.VibrationEffect.createOneShot(45L, android.os.VibrationEffect.DEFAULT_AMPLITUDE),
                            vibrationAudioAttrs
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        android.os.VibrationEffect.createOneShot(45L, android.os.VibrationEffect.DEFAULT_AMPLITUDE),
                        vibrationAudioAttrs
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(45L)
                }
            }
        } catch (_: Exception) {}
    }

    fun triggerPadTapVibration() {
        if (!isVibrateEnabled) return
        executeHapticFeedback()
    }

    fun cycleFeedbackMode() {
        val nextMode = when (feedbackMode) {
            FeedbackMode.SOUND -> FeedbackMode.VIBRATE
            FeedbackMode.VIBRATE -> FeedbackMode.MUTE
            FeedbackMode.MUTE -> FeedbackMode.SOUND
        }
        feedbackMode = nextMode
        sharedPrefs.edit()
            .putString(KEY_FEEDBACK_MODE, nextMode.name)
            .putBoolean(KEY_VIBRATE_ENABLED, nextMode == FeedbackMode.VIBRATE)
            .putBoolean("feedback_enabled", nextMode == FeedbackMode.SOUND)
            .apply()

        if (nextMode == FeedbackMode.VIBRATE) {
            executeHapticFeedback()
        }
    }

    var hasDismissedInstrumentInvite by remember {
        mutableStateOf(sharedPrefs.getBoolean(KEY_INSTRUMENT_INVITE_DISMISSED, false))
    }
    var hasSeenChallengeNudge by remember {
        mutableStateOf(sharedPrefs.getBoolean(KEY_CHALLENGE_NUDGE_SEEN, false))
    }
    var showChallengeNudge by remember { mutableStateOf(false) }
    var activeRunState by remember { mutableStateOf(RunStateStorage.loadRunState(context)) }

    var dailyNotificationsEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, true))
    }

    var activeDialog by remember {
        mutableStateOf(if (ChallengeStorage.getPendingShare(context) != null) ActiveDialog.DID_YOU_SHARE else ActiveDialog.NONE)
    }
    var awaitingChallengeForDialog by remember {
        mutableStateOf(ChallengeStorage.getPendingShare(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isSystemAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (!isSystemAllowed) {
                    dailyNotificationsEnabled = false
                    sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, false).apply()
                    NotificationScheduler.cancelReminder(context)
                } else {
                    dailyNotificationsEnabled = sharedPrefs.getBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            dailyNotificationsEnabled = true
            sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, true).apply()
            NotificationScheduler.scheduleDailyReminder(context)
            Toast.makeText(context, "Daily reminders enabled!", Toast.LENGTH_SHORT).show()
        } else {
            dailyNotificationsEnabled = false
            sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, false).apply()
            NotificationScheduler.cancelReminder(context)
        }
    }

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var isPausedMidGame by remember { mutableStateOf(false) }

    // Async Challenge & Deterministic Solo Seed State
    var activeChallenge by remember { mutableStateOf<ChallengeItem?>(null) }
    var challengeRng by remember { mutableStateOf<java.util.Random?>(null) }
    var currentRunSeed by remember { mutableIntStateOf(ChallengeSeed.generateRandomSeed()) }
    var soloRng by remember { mutableStateOf<java.util.Random?>(null) }
    var selectedShareTries by remember { mutableIntStateOf(3) }
    var savedChallenges by remember { mutableStateOf(ChallengeStorage.loadChallenges(context)) }
    var challengeAttemptsLeft by remember { mutableIntStateOf(3) }
    var isChallengeWon by remember { mutableStateOf(false) }
    var isChallengeLost by remember { mutableStateOf(false) }

    val activePadPool = remember(gridSize) { padsForGridSize(gridSize) }
    val sequence = remember { mutableStateListOf<PadSpec>() }
    var playerStepIndex by remember { mutableIntStateOf(0) }
    var currentScore by remember { mutableIntStateOf(0) }
    var bestScore by remember(gridSize, paceMode, difficultyMode) { mutableIntStateOf(readMigratedBestScore(sharedPrefs, gridSize, paceMode, difficultyMode)) }

    fun requestShareChallenge() {
        val boardScores = getAllBoardScores(sharedPrefs)
        if (boardScores.isEmpty() && currentScore <= 0) {
            activeDialog = ActiveDialog.FIRST_TIME_SHARE_GATE
        } else {
            activeDialog = ActiveDialog.TRIES_SELECTOR
        }
    }

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
        showChallengeNudge = false
    }

    fun saveActiveRun() {
        if (gameState != GameState.WATCHING && gameState != GameState.PLAYING) return
        val currentAlive = if (players.isNotEmpty()) players.map { it.isAlive } else listOf(true)
        val currentRounds = if (players.isNotEmpty()) players.map { it.roundsSurvived } else listOf(currentScore)
        val state = SerializedRunState(
            modeName = gameMode.name,
            gridDimension = gridSize.dimension,
            paceSuffix = paceMode.keySuffix,
            sequencePadIds = sequence.map { it.id },
            round = sequence.size,
            activePlayerIndex = activePlayerIndex,
            alivePlayers = currentAlive,
            roundsSurvived = currentRounds,
            activeChallengeCode = activeChallenge?.code,
            challengeTriesLeft = if (activeChallenge != null) challengeAttemptsLeft else null,
            challengeBestScore = activeChallenge?.bestScore,
            soloRunSeed = if (gameMode == GameMode.SOLO) currentRunSeed else null,
            difficultyMode = difficultyMode.name
        )
        RunStateStorage.saveRunState(context, state)
        activeRunState = state
    }

    fun discardActiveRun() {
        RunStateStorage.clearRunState(context)
        activeRunState = null
    }

    fun resumeActiveRun(state: SerializedRunState) {
        resetRunState()
        val restoredMode = GameMode.entries.find { it.name == state.modeName } ?: GameMode.SOLO
        val restoredGrid = when (state.gridDimension) {
            3 -> GridSize.THREE_BY_THREE
            4 -> GridSize.FOUR_BY_FOUR
            else -> GridSize.TWO_BY_TWO
        }
        val restoredPace = when (state.paceSuffix) {
            "progressive" -> PaceMode.PROGRESSIVE
            else -> PaceMode.CLASSIC
        }

        gameMode = restoredMode
        gridSize = restoredGrid
        paceMode = restoredPace
        difficultyMode = try { GameDifficulty.valueOf(state.difficultyMode) } catch (_: Exception) { GameDifficulty.EASY }
        val padPool = padsForGridSize(restoredGrid)

        sequence.clear()
        for (padId in state.sequencePadIds) {
            val pad = padPool.find { it.id == padId } ?: padPool.first()
            sequence.add(pad)
        }

        if (restoredMode == GameMode.SOLO) {
            if (!state.activeChallengeCode.isNullOrBlank()) {
                val found = savedChallenges.find { it.code.equals(state.activeChallengeCode, ignoreCase = true) }
                    ?: ChallengeSeed.parseChallengeCode(state.activeChallengeCode)?.let {
                        ChallengeItem(it.fullCode, "Rival", it.gridTag, it.targetScore, it.tries, state.challengeTriesLeft ?: it.tries, state.challengeBestScore ?: 0, it.seedInt, it.difficulty)
                    }
                activeChallenge = found
                challengeAttemptsLeft = state.challengeTriesLeft ?: found?.attemptsLeft ?: 3
                if (found != null) {
                    difficultyMode = found.difficulty
                }
                val restoredSeed = found?.seed ?: state.soloRunSeed ?: ChallengeSeed.generateRandomSeed()
                currentRunSeed = restoredSeed
                challengeRng = java.util.Random(restoredSeed.toLong()).apply {
                    repeat(sequence.size) { nextInt(padPool.size) }
                }
                soloRng = challengeRng
            } else {
                activeChallenge = null
                challengeRng = null
                val restoredSeed = state.soloRunSeed ?: ChallengeSeed.generateRandomSeed()
                currentRunSeed = restoredSeed
                soloRng = java.util.Random(restoredSeed.toLong()).apply {
                    repeat(sequence.size) { nextInt(padPool.size) }
                }
            }
            currentScore = (sequence.size - 1).coerceAtLeast(0)
            isHandoffVisible = false
        } else {
            activeChallenge = null
            challengeRng = null
            soloRng = null
            players.clear()
            for (i in 0 until restoredMode.playerCount) {
                val isAlive = state.alivePlayers.getOrElse(i) { true }
                val rounds = state.roundsSurvived.getOrElse(i) { 0 }
                players.add(PlayerSlot(i, "P${i + 1}", PLAYER_COLORS[i], isAlive = isAlive, roundsSurvived = rounds))
            }
            activePlayerIndex = state.activePlayerIndex.coerceIn(0, players.lastIndex)
            isHandoffVisible = true
        }

        wasPausedMidRound = true
        resumedBannerNote = "Round replayed - you were away."
        gameState = GameState.WATCHING
        roundTrigger++
    }

    fun updateVolume(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        audioVolume = clamped
        sharedPrefs.edit().putFloat(KEY_AUDIO_VOLUME, clamped).apply()
        if (clamped > 0f && feedbackMode != FeedbackMode.SOUND) {
            feedbackMode = FeedbackMode.SOUND
            sharedPrefs.edit().putString(KEY_FEEDBACK_MODE, FeedbackMode.SOUND.name).apply()
        }
    }

    fun updateBestScoreIfNeeded(newScore: Int) {
        if (newScore > bestScore) {
            bestScore = newScore
            writeBestScore(sharedPrefs, gridSize, paceMode, difficultyMode, newScore)
        }
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
        bestScore = readMigratedBestScore(sharedPrefs, newGridSize, newPaceMode, difficultyMode)
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
                    saveActiveRun()
                    wasPausedMidRound = true
                    isLifecyclePaused = true
                    tapFlashJob?.cancel()
                    activePadId = null
                    allPadsConfettiFlash = false
                    isRoundClearCelebration = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    val pending = ChallengeStorage.getPendingShare(context)
                    if (pending != null) {
                        awaitingChallengeForDialog = pending
                        activeDialog = ActiveDialog.DID_YOU_SHARE
                    }
                    if (wasPausedMidRound && sequence.isNotEmpty()) {
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
        discardActiveRun()
        isMatchComplete = true
        gameState = GameState.GAME_OVER
        isHandoffVisible = false
        isRedemptionTurnActive = false
        eliminatedBannerText = null
        wrongShakePadId = null
        activePadId = null
        val standings = computeStandings(players)
        matchStandings = standings
        standings.filter { it.isWinner }.forEach { incrementPlayerWin(sharedPrefs, it.player.index) }
        playerWinsVersion++

        // Record multiplayer longest survival
        val maxSurvived = players.maxOfOrNull { it.roundsSurvived } ?: 0
        val longestKey = "longest_survival_${players.size}p"
        if (maxSurvived > sharedPrefs.getInt(longestKey, 0)) {
            sharedPrefs.edit().putInt(longestKey, maxSurvived).apply()
        }

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
        wrongShakePadId = null
        activePadId = null
        isHandoffVisible = false
        gameState = GameState.WATCHING
        roundTrigger++
        saveActiveRun()
    }

    fun startChallenge(item: ChallengeItem) {
        discardActiveRun()
        resetRunState()
        activeChallenge = item
        difficultyMode = item.difficulty
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
        currentRunSeed = item.seed
        challengeRng = rng
        soloRng = rng
        val pads = padsForGridSize(targetGrid)
        sequence.add(pads[rng.nextInt(pads.size)])
        roundTrigger++
        gameState = GameState.WATCHING
        saveActiveRun()
    }

    fun startRevengeGame(rival: RivalRecord) {
        discardActiveRun()
        resetRunState()
        val targetGrid = when (rival.lastGrid.lowercase()) {
            "3x3" -> GridSize.THREE_BY_THREE
            "4x4" -> GridSize.FOUR_BY_FOUR
            else -> GridSize.TWO_BY_TWO
        }
        gridSize = targetGrid
        difficultyMode = rival.lastDifficulty
        paceMode = PaceMode.CLASSIC
        val seed = ChallengeSeed.generateRandomSeed()
        val item = ChallengeItem(
            code = "",
            rivalName = rival.name,
            gridSizeTag = rival.lastGrid,
            targetScore = rival.lastTarget,
            maxTries = 3,
            attemptsLeft = 3,
            bestScore = 0,
            seed = seed,
            difficulty = rival.lastDifficulty,
            chainId = rival.lastChainId,
            isRevenge = true
        )
        activeChallenge = item
        challengeAttemptsLeft = 3
        isChallengeWon = false
        isChallengeLost = false
        gameMode = GameMode.SOLO
        currentRunSeed = seed
        val rng = java.util.Random(seed.toLong())
        challengeRng = rng
        soloRng = rng
        val pads = padsForGridSize(targetGrid)
        sequence.add(pads[rng.nextInt(pads.size)])
        roundTrigger++
        gameState = GameState.WATCHING
        saveActiveRun()
    }

    fun actuallyStartNewGame() {
        discardActiveRun()
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
            currentRunSeed = challenge.seed
            val rng = java.util.Random(challenge.seed.toLong())
            challengeRng = rng
            soloRng = rng
            val pads = padsForGridSize(targetGrid)
            sequence.add(pads[rng.nextInt(pads.size)])
            roundTrigger++
            gameState = GameState.WATCHING
        } else if (gameMode == GameMode.SOLO) {
            val newSeed = ChallengeSeed.generateRandomSeed()
            currentRunSeed = newSeed
            selectedShareTries = 3
            val rng = java.util.Random(newSeed.toLong())
            soloRng = rng
            sequence.add(activePadPool[rng.nextInt(activePadPool.size)])
            roundTrigger++
            gameState = GameState.WATCHING
        } else {
            soloRng = null
            players.clear()
            players.addAll(List(gameMode.playerCount) { i -> PlayerSlot(i, "P${i + 1}", PLAYER_COLORS[i], isAlive = true, roundsSurvived = 0) })
            activePlayerIndex = 0
            isRedemptionTurnActive = false
            sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
            isHandoffVisible = true
            gameState = GameState.WATCHING
        }
        saveActiveRun()
    }

    fun startNewGame() {
        if (activeRunState != null && activeRunState!!.modeName != GameMode.SOLO.name) {
            activeDialog = ActiveDialog.DISCARD_CONFIRM
        } else {
            actuallyStartNewGame()
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
            wrongShakePadId = null
            delay(450L)
            for (pad in sequence) {
                if (isLifecyclePaused || gameState != GameState.WATCHING || isHandoffVisible) return@LaunchedEffect
                activePadId = pad.id
                if (feedbackEnabled && effectiveVolume > 0f) {
                    audioSynth.playPadSpecTone(pad, effectiveVolume)
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
        if (gameState == GameState.IDLE) {
            if (isLifecyclePaused || isHandoffVisible) return
            if (!hasDismissedInstrumentInvite) {
                hasDismissedInstrumentInvite = true
                sharedPrefs.edit().putBoolean(KEY_INSTRUMENT_INVITE_DISMISSED, true).apply()
            }
            tapFlashJob?.cancel()
            activePadId = tappedPad.id
            if (feedbackEnabled && effectiveVolume > 0f) {
                audioSynth.playPadSpecTone(tappedPad, effectiveVolume)
            }
            triggerPadTapVibration()
            tapFlashJob = coroutineScope.launch {
                delay(240L)
                if (gameState == GameState.IDLE) activePadId = null
            }
            return
        }
        if (gameState != GameState.PLAYING || sequence.isEmpty() || isLifecyclePaused || isHandoffVisible) return
        resumedBannerNote = null
        val expectedPad = sequence.getOrNull(playerStepIndex) ?: return

        if (tappedPad.id == expectedPad.id) {
            tapFlashJob?.cancel()
            wrongShakePadId = null
            activePadId = tappedPad.id
            if (feedbackEnabled && effectiveVolume > 0f) {
                audioSynth.playPadSpecTone(tappedPad, effectiveVolume)
            }
            triggerPadTapVibration()
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
                            // Immediate Victory: stop sequence coroutine and transition to victory game over
                            isChallengeWon = true
                            tapFlashJob?.cancel()
                            activePadId = null
                            allPadsConfettiFlash = false
                            isRoundClearCelebration = false
                            clearedRoundNumber = completedRound
                            if (feedbackEnabled && effectiveVolume > 0f) {
                                audioSynth.playRoundClearArpeggio(effectiveVolume)
                            }
                            gameState = GameState.GAME_OVER
                            discardActiveRun()
                            return@onPadTapped
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
                        }
                        allPadsConfettiFlash = true
                        delay(260L)
                        allPadsConfettiFlash = false
                        delay(240L)
                        if (difficultyMode == GameDifficulty.HARD) {
                            sequence.clear()
                            val newTargetLen = completedRound + 1
                            val rng = if (activeChallenge != null) {
                                challengeRng ?: java.util.Random(activeChallenge!!.seed.toLong()).also { challengeRng = it }
                            } else {
                                soloRng ?: java.util.Random(currentRunSeed.toLong()).also { soloRng = it }
                            }
                            repeat(newTargetLen) {
                                sequence.add(activePadPool[rng.nextInt(activePadPool.size)])
                            }
                        } else {
                            val nextPad = if (activeChallenge != null) {
                                val rng = challengeRng ?: java.util.Random(activeChallenge!!.seed.toLong()).apply {
                                    repeat(sequence.size) { nextInt(activePadPool.size) }
                                }.also { challengeRng = it }
                                activePadPool[rng.nextInt(activePadPool.size)]
                            } else {
                                val rng = soloRng ?: java.util.Random(currentRunSeed.toLong()).apply {
                                    repeat(sequence.size) { nextInt(activePadPool.size) }
                                }.also { soloRng = it }
                                activePadPool[rng.nextInt(activePadPool.size)]
                            }
                            sequence.add(nextPad)
                        }
                        isRoundClearCelebration = false
                        roundTrigger++
                        saveActiveRun()
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
                        }
                        allPadsConfettiFlash = true
                        delay(260L)
                        allPadsConfettiFlash = false
                        delay(240L)
                        isRoundClearCelebration = false

                        if (isRedemptionTurnActive) {
                            concludeMatch()
                        } else {
                            var nextIdx = (activePlayerIndex + 1) % players.size
                            while (!players[nextIdx].isAlive) {
                                nextIdx = (nextIdx + 1) % players.size
                            }
                            activePlayerIndex = nextIdx
                            playerStepIndex = 0
                            if (difficultyMode == GameDifficulty.HARD) {
                                val targetLen = completedRound + 1
                                sequence.clear()
                                repeat(targetLen) {
                                    sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
                                }
                            } else {
                                sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
                            }
                            isHandoffVisible = true
                            saveActiveRun()
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
            }
            triggerPadTapVibration()
            if (gameMode == GameMode.SOLO) {
                tapFlashJob = coroutineScope.launch {
                    wrongPadShakeOffset.snapTo(0f)
                    for (target in listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)) {
                        wrongPadShakeOffset.animateTo(target, tween(45, easing = FastOutSlowInEasing))
                    }
                    wrongShakePadId = null
                }
                updateBestScoreIfNeeded(currentScore)
                if (activeChallenge != null) {
                    val ch = activeChallenge!!
                    val isFree = ch.maxTries == 0
                    val newTries = if (isFree) 0 else (challengeAttemptsLeft - 1).coerceAtLeast(0)
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
                    } else if (!isFree && newTries <= 0) {
                        isChallengeLost = true
                        isChallengeWon = false
                    }
                    if (isChallengeWon || (isChallengeLost && !isFree)) {
                        ChallengeStorage.recordMatchOutcome(
                            context = context,
                            rivalName = ch.rivalName,
                            userScore = currentScore,
                            targetScore = ch.targetScore,
                            gridSize = ch.gridSizeTag,
                            difficulty = ch.difficulty,
                            chainId = ch.chainId,
                            maxTries = ch.maxTries
                        )
                    }
                    showChallengeNudge = false
                } else if (!hasSeenChallengeNudge) {
                    showChallengeNudge = true
                    hasSeenChallengeNudge = true
                    sharedPrefs.edit().putBoolean(KEY_CHALLENGE_NUDGE_SEEN, true).apply()
                }
                gameState = GameState.GAME_OVER
                discardActiveRun()
            } else {
                val eliminatedPlayer = players[activePlayerIndex]
                players[activePlayerIndex] = eliminatedPlayer.copy(isAlive = false)
                eliminatedBannerText = "P${eliminatedPlayer.index + 1} missed the sequence"

                tapFlashJob = coroutineScope.launch {
                    wrongPadShakeOffset.snapTo(0f)
                    for (target in listOf(-18f, 18f, -14f, 14f, -8f, 8f, 0f)) {
                        wrongPadShakeOffset.animateTo(target, tween(45, easing = FastOutSlowInEasing))
                    }
                    wrongShakePadId = null
                    delay(585L)
                    eliminatedBannerText = null
                    wrongShakePadId = null
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
                                if (difficultyMode == GameDifficulty.HARD) {
                                    val curLen = sequence.size
                                    sequence.clear()
                                    repeat(curLen) {
                                        sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
                                    }
                                }
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
                            if (difficultyMode == GameDifficulty.HARD) {
                                val curLen = sequence.size
                                sequence.clear()
                                repeat(curLen) {
                                    sequence.add(activePadPool[Random.nextInt(activePadPool.size)])
                                }
                            }
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
    val showConfigRow = gameState == GameState.IDLE

    // PASS 2H Item 4: Back gesture routing
    // GAME_OVER and MATCH_COMPLETE -> Home (IDLE); WATCHING/PLAYING -> pause dialog; IDLE -> system exit.
    BackHandler(enabled = gameState != GameState.IDLE) {
        when (gameState) {
            GameState.WATCHING, GameState.PLAYING -> {
                activeDialog = ActiveDialog.SETTINGS_SHEET
            }
            GameState.GAME_OVER -> {
                discardActiveRun()
                activeChallenge = null
                challengeRng = null
                soloRng = null
                isChallengeWon = false
                isChallengeLost = false
                isMatchComplete = false
                matchStandings = emptyList()
                resetRunState()
                activeRunState = null
                gameState = GameState.IDLE
            }
            GameState.IDLE -> Unit
        }
    }

    BackHandler(enabled = currentScreen == AppScreen.LEADERBOARD) {
        currentScreen = AppScreen.HOME
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0B1120),
                contentColor = Color(0xFF00E5FF),
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.HOME,
                    onClick = { currentScreen = AppScreen.HOME },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontWeight = if (currentScreen == AppScreen.HOME) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.testTag("nav_home")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.LEADERBOARD,
                    onClick = {
                        if (gameState == GameState.PLAYING || gameState == GameState.WATCHING) {
                            isPausedMidGame = true
                        }
                        currentScreen = AppScreen.LEADERBOARD
                    },
                    icon = { Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = "Leaderboard") },
                    label = { Text("Leaderboard", fontWeight = if (currentScreen == AppScreen.LEADERBOARD) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5FF),
                        selectedTextColor = Color(0xFF00E5FF),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.testTag("nav_leaderboard")
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentScreen == AppScreen.LEADERBOARD) {
            HallOfFameScreen(
                onNavigateBack = { currentScreen = AppScreen.HOME },
                onStartRevenge = { rival ->
                    currentScreen = AppScreen.HOME
                    startRevengeGame(rival)
                },
                onPlayAwaiting = { _ ->
                    currentScreen = AppScreen.HOME
                },
                onShareCodeClicked = {
                    requestShareChallenge()
                },
                onPasteCodeClicked = {
                    activeDialog = ActiveDialog.ENTER_CHALLENGE
                },
                onAcceptChallenge = { item ->
                    currentScreen = AppScreen.HOME
                    startChallenge(item)
                },
                sharedPrefs = sharedPrefs,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF090D16)), radius = 1400f))
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = maxContentWidth)
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TopTitleRow(
                            feedbackMode = feedbackMode,
                            isMidRun = gameState == GameState.WATCHING || gameState == GameState.PLAYING,
                            onCycleFeedbackMode = ::cycleFeedbackMode,
                            onOpenSettings = { activeDialog = ActiveDialog.SETTINGS_SHEET }
                        )

                        if (gameState != GameState.IDLE) {
                            if (activeChallenge != null) {
                                InGameChallengeBanner(
                                    rivalName = activeChallenge!!.rivalName,
                                    targetScore = activeChallenge!!.targetScore,
                                    triesLeft = challengeAttemptsLeft,
                                    isRevenge = activeChallenge!!.isRevenge,
                                    maxTries = activeChallenge!!.maxTries
                                )
                            }
                            if (gameState == GameState.WATCHING || gameState == GameState.PLAYING) {
                                if (gameMode == GameMode.SOLO) {
                                    ScoreboardRow(
                                        currentScore = currentScore,
                                        bestScore = bestScore,
                                        gridSize = gridSize
                                    )
                                } else if (!isMatchComplete) {
                                    PlayerRail(
                                        players = players,
                                        activePlayerIndex = activePlayerIndex,
                                        isRedemptionTurnActive = isRedemptionTurnActive
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showConfigRow,
                        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (gameState == GameState.IDLE) {
                                // ROW A: Single segmented capsule [SOLO|2P|3P|4P] with sliding highlight thumb
                                SegmentedModeTrack(
                                    selectedMode = gameMode,
                                    enabled = !isReplayingLostRound,
                                    onSelectMode = { resetToIdleForMode(it) }
                                )

                                // ROW B: Left = ONE Board pill ("3×3 · Classic"), Right = Context slot (+ Challenge pill OR borderless dot tally)
                                val wins = remember(playerWinsVersion, gameMode) {
                                    (0 until 4).map { readPlayerWins(sharedPrefs, it) }
                                }
                                BoardAndContextRow(
                                    selectedMode = gameMode,
                                    gridSize = gridSize,
                                    paceMode = paceMode,
                                    activeChallengesCount = savedChallenges.size,
                                    wins = wins,
                                    enabled = !isReplayingLostRound,
                                    onOpenSettings = { activeDialog = ActiveDialog.SETTINGS_SHEET },
                                    onOpenEnterChallenge = { activeDialog = ActiveDialog.ENTER_CHALLENGE }
                                )

                                // CHALLENGE RAIL: Only when non-empty in SOLO mode
                                if (gameMode == GameMode.SOLO && savedChallenges.isNotEmpty()) {
                                    ActiveChallengesRail(
                                        challenges = savedChallenges,
                                        onPlayChallenge = ::startChallenge,
                                        onDeleteChallenge = { code ->
                                            ChallengeStorage.deleteChallenge(context, code)
                                            savedChallenges = ChallengeStorage.loadChallenges(context)
                                        },
                                        onOpenEnterModal = { activeDialog = ActiveDialog.ENTER_CHALLENGE }
                                    )
                                }
                            }
                        }
                    }

                val isPodiumOverlayActive = gameMode != GameMode.SOLO && isMatchComplete

                if (isPodiumOverlayActive && !isReplayingLostRound) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MatchCompleteCard(
                            standings = matchStandings,
                            sequenceSize = sequence.size,
                            isReplaying = isReplayingLostRound,
                            onRematch = {
                                discardActiveRun()
                                startNewGame()
                            },
                            onHome = {
                                discardActiveRun()
                                resetRunState()
                                activeChallenge = null
                                challengeRng = null
                                isChallengeWon = false
                                isChallengeLost = false
                                gameMode = GameMode.SOLO
                                writeGameMode(sharedPrefs, GameMode.SOLO)
                                isMatchComplete = false
                                matchStandings = emptyList()
                                players.clear()
                                activeRunState = null
                                gameState = GameState.IDLE
                            },
                            onWatchFinalRound = ::startWatchMyRoundReplay
                        )
                    }
                } else {
                    if (isPodiumOverlayActive) {
                        MatchCompleteCard(
                            standings = matchStandings,
                            sequenceSize = sequence.size,
                            isReplaying = isReplayingLostRound,
                            onRematch = {
                                discardActiveRun()
                                startNewGame()
                            },
                            onHome = {
                                discardActiveRun()
                                resetRunState()
                                activeChallenge = null
                                challengeRng = null
                                isChallengeWon = false
                                isChallengeLost = false
                                gameMode = GameMode.SOLO
                                writeGameMode(sharedPrefs, GameMode.SOLO)
                                isMatchComplete = false
                                matchStandings = emptyList()
                                players.clear()
                                activeRunState = null
                                gameState = GameState.IDLE
                            },
                            onWatchFinalRound = ::startWatchMyRoundReplay
                        )
                    } else if (gameState != GameState.IDLE) {
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

                    // HERO PAD GRID: Takes all remaining vertical weight
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("pad_grid_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Faint radial ambient glow behind the grid (alpha <= 6%, brand cyan/magenta)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val stageRadius = size.minDimension * 0.72f
                            if (stageRadius > 0f) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.06f),
                                            Color(0xFFD946EF).copy(alpha = 0.04f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = stageRadius
                                    ),
                                    radius = stageRadius,
                                    center = center
                                )
                            }
                        }

                        val perPadCap = if (gridSize == GridSize.TWO_BY_TWO) 180.dp else 150.dp
                        val maxBoardCap = (perPadCap * gridSize.dimension) + (gridSize.spacingDp.dp * (gridSize.dimension - 1))
                        val animatedGridSide by animateDpAsState(
                            targetValue = minOf(maxWidth, maxHeight, maxBoardCap),
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            label = "animatedGridSide"
                        )
                        val isInstrumentArmed = gameState == GameState.IDLE
                        SimonPadGrid(
                            modifier = Modifier.size(animatedGridSide),
                            gridSize = gridSize,
                            pads = activePadPool,
                            activePadId = activePadId,
                            allPadsConfettiFlash = allPadsConfettiFlash,
                            wrongShakePadId = wrongShakePadId,
                            wrongShakeOffsetPx = wrongPadShakeOffset.value,
                            inputEnabled = (gameState == GameState.PLAYING || isInstrumentArmed) && !isReplayingLostRound && !isLifecyclePaused && !isHandoffVisible,
                            onPadClick = ::onPadTapped
                        )

                        // One-time Instrument Invitation chip at the hero's bottom edge
                        if (gameState == GameState.IDLE && !hasDismissedInstrumentInvite) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF0F172A).copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("instrument_invite_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Tap pads to play their notes.",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // ONBOARDING POPUP: Displayed on Home screen for first 3 launches
                        if (gameState == GameState.IDLE && showOnboardingPopup) {
                            OnboardingPopupDialog(onDismiss = { showOnboardingPopup = false })
                        }
                    }
                }

                if (gameMode == GameMode.SOLO || !isMatchComplete) {
                    val myName = remember { ChallengeStorage.getMyName(context) }
                    val generatedCode = remember(currentScore, gridSize, currentRunSeed, selectedShareTries, difficultyMode, myName) {
                        val gridTag = when (gridSize) {
                            GridSize.TWO_BY_TWO -> "2x2"
                            GridSize.THREE_BY_THREE -> "3x3"
                            GridSize.FOUR_BY_FOUR -> "4x4"
                        }
                        val scoreToShare = currentScore.coerceAtLeast(1)
                        val chainId = activeChallenge?.chainId?.ifEmpty { ChallengeSeed.generateChainId() } ?: ChallengeSeed.generateChainId()
                        val code = ChallengeSeed.formatChallengeCode(
                            gridTag = gridTag,
                            targetScore = scoreToShare,
                            tries = selectedShareTries,
                            difficulty = difficultyMode,
                            seed = currentRunSeed,
                            senderName = myName,
                            chainId = chainId,
                            isRevenge = activeChallenge?.isRevenge == true
                        )
                        ChallengeStorage.recordCreatedCode(context, code)
                        code
                    }
                    BottomActionSection(
                        gameState = gameState,
                        gameMode = gameMode,
                        gridSize = gridSize,
                        activeRunState = if (gameState == GameState.IDLE) activeRunState else null,
                        activeChallenge = activeChallenge,
                        challengeAttemptsLeft = challengeAttemptsLeft,
                        isChallengeWon = isChallengeWon,
                        isChallengeLost = isChallengeLost,
                        showChallengeNudge = showChallengeNudge,
                        onResumeSavedRun = {
                            if (activeRunState != null) {
                                resumeActiveRun(activeRunState!!)
                            }
                        },
                        onStartOrRetry = ::startNewGame,
                        onShareChallenge = {
                            showChallengeNudge = false
                            requestShareChallenge()
                        },
                        onCopyCode = {
                            showChallengeNudge = false
                            ChallengeStorage.recordCreatedCode(context, generatedCode)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Glowback Challenge Code", generatedCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Challenge code copied: $generatedCode", Toast.LENGTH_SHORT).show()

                            val parsed = ChallengeSeed.parseChallengeCode(generatedCode)
                            val chainId = parsed?.chainId?.ifEmpty { ChallengeSeed.generateChainId() } ?: ChallengeSeed.generateChainId()
                            val gridTag = when (gridSize) {
                                GridSize.TWO_BY_TWO -> "2x2"
                                GridSize.THREE_BY_THREE -> "3x3"
                                GridSize.FOUR_BY_FOUR -> "4x4"
                            }
                            val awaiting = AwaitingChallenge(
                                chainId = chainId,
                                code = generatedCode,
                                gridSizeTag = gridTag,
                                targetScore = currentScore.coerceAtLeast(1),
                                difficulty = difficultyMode,
                                paceName = paceMode.shortLabel,
                                tries = selectedShareTries
                            )
                            ChallengeStorage.savePendingShare(context, awaiting)
                            awaitingChallengeForDialog = awaiting
                            activeDialog = ActiveDialog.DID_YOU_SHARE
                        },
                        onReturnHome = {
                            discardActiveRun()
                            activeChallenge = null
                            challengeRng = null
                            soloRng = null
                            isChallengeWon = false
                            isChallengeLost = false
                            isMatchComplete = false
                            matchStandings = emptyList()
                            resetRunState()
                            activeRunState = null
                            gameState = GameState.IDLE
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
                            soloRng = null
                            resetRunState()
                            gameState = GameState.IDLE
                        }
                    )
                }
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

            if (isPausedMidGame && (gameState == GameState.PLAYING || gameState == GameState.WATCHING)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { isPausedMidGame = false }
                        .testTag("midgame_paused_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .padding(24.dp)
                            .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "GAME PAUSED",
                                color = Color(0xFF00E5FF),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap anywhere to resume your sequence",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // ROOT DIALOG MUTUAL EXCLUSION: Rendered at root Scaffold level, exactly one active at a time
    when (activeDialog) {
        ActiveDialog.NONE -> Unit
        ActiveDialog.FIRST_TIME_SHARE_GATE -> {
            FirstTimeShareGateDialog(
                onDismiss = { activeDialog = ActiveDialog.NONE },
                onPlayNow = {
                    activeDialog = ActiveDialog.NONE
                    currentScreen = AppScreen.HOME
                    startNewGame()
                }
            )
        }
        ActiveDialog.NAME_SETUP -> {
            ChallengerNameSetupDialog(
                onDismiss = { activeDialog = ActiveDialog.NONE },
                onSaveName = { savedName ->
                    ChallengeStorage.setMyName(context, savedName)
                    activeDialog = ActiveDialog.TRIES_SELECTOR
                }
            )
        }
        ActiveDialog.TRIES_SELECTOR -> {
            ShareTriesSelectorDialog(
                initialTries = selectedShareTries,
                boardOptions = getAllBoardScores(sharedPrefs),
                currentScoreToShare = currentScore.coerceAtLeast(1),
                requireNameSetupFirst = !ChallengeStorage.hasConfiguredName(context),
                awaitingChallengeForDialog = awaitingChallengeForDialog,
                onDismiss = { activeDialog = ActiveDialog.NONE },
                onDismissDidYouShare = {
                    ChallengeStorage.clearPendingShare(context)
                    awaitingChallengeForDialog = null
                    activeDialog = ActiveDialog.NONE
                },
                onConfirmDidYouShare = { confirmedItem ->
                    ChallengeStorage.saveAwaitingChallenge(context, confirmedItem)
                    ChallengeStorage.clearPendingShare(context)
                    awaitingChallengeForDialog = null
                    activeDialog = ActiveDialog.NONE
                    Toast.makeText(context, "Challenge saved! Waiting for rival...", Toast.LENGTH_SHORT).show()
                },
                onGenerateCard = { chosenTries, senderName, chosenBoard ->
                    selectedShareTries = chosenTries
                    val gridTag = chosenBoard?.gridTag ?: when (gridSize) {
                        GridSize.TWO_BY_TWO -> "2x2"
                        GridSize.THREE_BY_THREE -> "3x3"
                        GridSize.FOUR_BY_FOUR -> "4x4"
                    }
                    val scoreToShare = chosenBoard?.score ?: currentScore.coerceAtLeast(1)
                    val chosenDiff = chosenBoard?.difficulty ?: difficultyMode
                    val chosenPace = chosenBoard?.paceName ?: paceMode.shortLabel
                    val chainId = activeChallenge?.chainId?.ifEmpty { ChallengeSeed.generateChainId() } ?: ChallengeSeed.generateChainId()
                    val code = ChallengeSeed.formatChallengeCode(
                        gridTag = gridTag,
                        targetScore = scoreToShare,
                        tries = chosenTries,
                        difficulty = chosenDiff,
                        seed = currentRunSeed,
                        senderName = senderName,
                        chainId = chainId,
                        isRevenge = activeChallenge?.isRevenge == true
                    )
                    ChallengeStorage.recordCreatedCode(context, code)
                    val awaiting = AwaitingChallenge(
                        chainId = chainId,
                        code = code,
                        gridSizeTag = gridTag,
                        targetScore = scoreToShare,
                        difficulty = chosenDiff,
                        paceName = chosenPace,
                        tries = chosenTries
                    )
                    ChallengeStorage.savePendingShare(context, awaiting)
                    awaitingChallengeForDialog = awaiting

                    coroutineScope.launch {
                        ChallengeShareHelper.shareChallenge(
                            context = context,
                            code = code,
                            gridSizeTag = gridTag,
                            score = scoreToShare,
                            attempts = 1,
                            tries = chosenTries,
                            paceName = chosenPace,
                            difficulty = chosenDiff
                        )
                    }
                }
            )
        }
        ActiveDialog.ENTER_CHALLENGE -> {
            EnterChallengeDialog(
                onDismiss = { activeDialog = ActiveDialog.NONE },
                onSubmit = { newItem ->
                    ChallengeStorage.saveChallenge(context, newItem)
                    savedChallenges = ChallengeStorage.loadChallenges(context)
                    activeDialog = ActiveDialog.NONE
                    currentScreen = AppScreen.HOME
                    startChallenge(newItem)
                }
            )
        }
        ActiveDialog.SETTINGS_SHEET -> {
            SettingsSheetDialog(
                currentGridSize = gridSize,
                currentDifficulty = difficultyMode,
                currentPaceMode = paceMode,
                isMidRun = gameState == GameState.WATCHING || gameState == GameState.PLAYING,
                onSelectGridSize = { if (it != gridSize) resetToIdleForConfigChange(it, paceMode) },
                onSelectDifficulty = { diff ->
                    difficultyMode = diff
                    sharedPrefs.edit().putString(KEY_DIFFICULTY_MODE, diff.name).apply()
                },
                onSelectPaceMode = { if (it != paceMode) resetToIdleForConfigChange(gridSize, it) },
                onResetMultiplayerWins = {
                    resetMultiplayerWins(sharedPrefs)
                    playerWinsVersion++
                    Toast.makeText(context, "Multiplayer records reset to 0", Toast.LENGTH_SHORT).show()
                },
                dailyNotificationsEnabled = dailyNotificationsEnabled,
                onToggleDailyNotifications = { wantEnabled ->
                    if (wantEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                dailyNotificationsEnabled = true
                                sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, true).apply()
                                NotificationScheduler.scheduleDailyReminder(context)
                                Toast.makeText(context, "Daily reminders enabled!", Toast.LENGTH_SHORT).show()
                            } else {
                                activeDialog = ActiveDialog.NOTIFICATION_PERM
                            }
                        } else {
                            val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                            if (allowed) {
                                dailyNotificationsEnabled = true
                                sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, true).apply()
                                NotificationScheduler.scheduleDailyReminder(context)
                                Toast.makeText(context, "Daily reminders enabled!", Toast.LENGTH_SHORT).show()
                            } else {
                                activeDialog = ActiveDialog.NOTIFICATION_PERM
                            }
                        }
                    } else {
                        dailyNotificationsEnabled = false
                        sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, false).apply()
                        NotificationScheduler.cancelReminder(context)
                        Toast.makeText(context, "Daily notifications turned off", Toast.LENGTH_SHORT).show()
                    }
                },
                onResumeRun = { activeDialog = ActiveDialog.NONE },
                onSaveAndExit = {
                    saveActiveRun()
                    resetRunState()
                    gameState = GameState.IDLE
                    activeDialog = ActiveDialog.NONE
                },
                onQuitToHome = {
                    discardActiveRun()
                    activeChallenge = null
                    challengeRng = null
                    soloRng = null
                    isChallengeWon = false
                    isChallengeLost = false
                    isMatchComplete = false
                    matchStandings = emptyList()
                    resetRunState()
                    activeRunState = null
                    gameState = GameState.IDLE
                    activeDialog = ActiveDialog.NONE
                },
                onRestartRun = {
                    discardActiveRun()
                    activeDialog = ActiveDialog.NONE
                    actuallyStartNewGame()
                },
                onDismiss = { activeDialog = ActiveDialog.NONE }
            )
        }
        ActiveDialog.NOTIFICATION_PERM -> {
            NotificationPermissionDialog(
                onAllow = {
                    activeDialog = ActiveDialog.NONE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                        if (allowed) {
                            dailyNotificationsEnabled = true
                            sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, true).apply()
                            NotificationScheduler.scheduleDailyReminder(context)
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                },
                onDismiss = {
                    activeDialog = ActiveDialog.NONE
                    dailyNotificationsEnabled = false
                    sharedPrefs.edit().putBoolean(NotificationScheduler.KEY_DAILY_NOTIFICATIONS_ENABLED, false).apply()
                }
            )
        }
        ActiveDialog.DISCARD_CONFIRM -> {
            if (activeRunState != null) {
                val modeLabel = when (activeRunState!!.modeName) {
                    "TWO_PLAYERS" -> "2P"
                    "THREE_PLAYERS" -> "3P"
                    "FOUR_PLAYERS" -> "4P"
                    else -> activeRunState!!.modeName
                }
                AlertDialog(
                    onDismissRequest = { activeDialog = ActiveDialog.NONE },
                    title = { Text(text = "Abandon Active Match?", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "You have an unfinished $modeLabel match (Round ${activeRunState!!.round}). Starting a new match will discard this progress.",
                            color = Color(0xFFCBD5E1)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                activeDialog = ActiveDialog.NONE
                                actuallyStartNewGame()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.testTag("confirm_discard_button")
                        ) {
                            Text("Discard & Start New", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { activeDialog = ActiveDialog.NONE },
                            modifier = Modifier.testTag("cancel_discard_button")
                        ) {
                            Text("Keep Match", color = Color(0xFF38BDF8))
                        }
                    },
                    containerColor = Color(0xFF1E293B),
                    shape = RoundedCornerShape(18.dp)
                )
            } else {
                activeDialog = ActiveDialog.NONE
            }
        }
        ActiveDialog.DID_YOU_SHARE -> {
            val awaiting = awaitingChallengeForDialog ?: ChallengeStorage.getPendingShare(context)
            if (awaiting != null) {
                DidYouShareDialog(
                    awaiting = awaiting,
                    onDismiss = {
                        ChallengeStorage.clearPendingShare(context)
                        awaitingChallengeForDialog = null
                        activeDialog = ActiveDialog.NONE
                    },
                    onConfirmShared = { confirmedItem ->
                        ChallengeStorage.saveAwaitingChallenge(context, confirmedItem)
                        ChallengeStorage.clearPendingShare(context)
                        awaitingChallengeForDialog = null
                        activeDialog = ActiveDialog.NONE
                        Toast.makeText(context, "Challenge saved! Waiting for rival...", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                activeDialog = ActiveDialog.NONE
            }
        }
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
    feedbackMode: FeedbackMode,
    isMidRun: Boolean = false,
    onCycleFeedbackMode: () -> Unit,
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
            val (modeIcon, modeDesc, modeTint) = when (feedbackMode) {
                FeedbackMode.SOUND -> Triple(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    "Sound ON. Tap for Vibrate.",
                    Color(0xFF38BDF8)
                )
                FeedbackMode.VIBRATE -> Triple(
                    Icons.Default.Vibration,
                    "Vibrate ON. Tap to Mute.",
                    Color(0xFF38BDF8)
                )
                FeedbackMode.MUTE -> Triple(
                    Icons.AutoMirrored.Filled.VolumeOff,
                    "Muted. Tap to Unmute.",
                    Color(0xFF94A3B8)
                )
            }
            HeaderIconBtn(
                icon = modeIcon,
                contentDesc = modeDesc,
                tint = modeTint,
                borderColor = if (feedbackMode == FeedbackMode.MUTE) Color(0xFF334155) else Color(0xFF38BDF8).copy(alpha = 0.5f),
                tag = "audio_toggle_button",
                onClick = onCycleFeedbackMode
            )
            HeaderIconBtn(
                icon = if (isMidRun) Icons.Default.Pause else Icons.Default.Settings,
                contentDesc = if (isMidRun) "Pause Game" else "Open Settings",
                tint = if (isMidRun) Color(0xFFFBBF24) else Color(0xFFCBD5E1),
                borderColor = if (isMidRun) Color(0xFFFBBF24).copy(alpha = 0.5f) else Color(0xFF334155),
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
        ScoreCard("BEST (${gridSize.label})", bestScore, Icons.Default.EmojiEvents, Color(0xFFFBBF24), Color(0xFFFBBF24), "scoreboard_best_score_text")
    }
}

@Composable
private fun SegmentedModeTrack(
    selectedMode: GameMode,
    enabled: Boolean,
    onSelectMode: (GameMode) -> Unit
) {
    val modes = GameMode.entries
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(22.dp))
            .padding(3.dp)
            .testTag("segmented_mode_track")
    ) {
        val segmentWidth = maxWidth / modes.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "modeThumbOffset"
        )

        // Sliding highlight thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xFF0284C7).copy(alpha = 0.42f))
                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(19.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { mode ->
                val isSelected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(19.dp))
                        .clickable(
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onSelectMode(mode) }
                        )
                        .testTag(mode.tagId),
                    contentAlignment = Alignment.Center
                ) {
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
private fun BoardAndContextRow(
    selectedMode: GameMode,
    gridSize: GridSize,
    paceMode: PaceMode,
    activeChallengesCount: Int,
    wins: List<Int>,
    enabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenEnterChallenge: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Guard for 4P + Progressive at <= 360dp screen width (<= 320dp content width):
        // Fallback 1: drop trophy icon from tally; Fallback 2: abbreviate "Progressive" to "Prog."
        val isMulti4P = selectedMode == GameMode.FOUR_PLAYERS
        val isProgressive = paceMode == PaceMode.PROGRESSIVE
        val hasMultiDigitWins = wins.take(selectedMode.playerCount).any { it >= 10 }
        val dropTrophy = isMulti4P && (maxWidth < 312.dp || (isProgressive && maxWidth <= 324.dp) || hasMultiDigitWins)
        val abbreviatePace = isMulti4P && isProgressive && (maxWidth < 300.dp || (hasMultiDigitWins && maxWidth <= 324.dp))
        val displayPaceLabel = if (abbreviatePace) "Prog." else paceMode.shortLabel

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: ONE Board pill ("3×3 · Classic") opening Settings
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable(enabled = enabled, role = Role.Button, onClick = onOpenSettings)
                            .padding(start = 12.dp, end = 4.dp)
                            .testTag("config_pill_grid"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = gridSize.label,
                            color = Color(0xFFF8FAFC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = "·",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable(enabled = enabled, role = Role.Button, onClick = onOpenSettings)
                            .padding(start = 4.dp, end = 12.dp)
                            .testTag("config_pill_pace"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayPaceLabel,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Right: Contextual slot
            if (selectedMode == GameMode.SOLO) {
                val pillLabel = if (activeChallengesCount > 0) {
                    "Challenge · $activeChallengesCount"
                } else {
                    "+ Challenge"
                }
                Surface(
                    onClick = onOpenEnterChallenge,
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.55f)),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("enter_challenge_code_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = pillLabel,
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                // Multiplayer: Borderless color-dot tally ([trophy] *0 *0 *0 *0) in 14sp, never wraps at 360dp
                MultiplayerWinsDotTally(
                    gameMode = selectedMode,
                    wins = wins,
                    showTrophy = !dropTrophy
                )
            }
        }
    }
}

@Composable
private fun MultiplayerWinsDotTally(
    gameMode: GameMode,
    wins: List<Int>,
    showTrophy: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .testTag("wins_summary_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showTrophy) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Multiplayer Wins",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(16.dp)
            )
        }
        for (i in 0 until gameMode.playerCount) {
            Row(
                modifier = Modifier.widthIn(min = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PLAYER_COLORS[i])
                )
                Text(
                    text = "${wins.getOrElse(i) { 0 }}",
                    color = Color(0xFFF8FAFC),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ContinueRunPill(
    state: SerializedRunState,
    onResume: () -> Unit
) {
    val modeLabel = when (state.modeName) {
        "SOLO" -> "Solo"
        "TWO_PLAYERS" -> "2P"
        "THREE_PLAYERS" -> "3P"
        "FOUR_PLAYERS" -> "4P"
        else -> state.modeName
    }
    val detail = if (state.modeName == "SOLO") {
        "Round ${state.round}"
    } else {
        "P${state.activePlayerIndex + 1} Turn • Round ${state.round}"
    }
    val dotColor = if (state.modeName == "SOLO") {
        Color(0xFF38BDF8)
    } else {
        PLAYER_COLORS.getOrElse(state.activePlayerIndex) { Color(0xFF38BDF8) }
    }
    val pillAccent = Color(0xFF38BDF8)

    Surface(
        onClick = onResume,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, pillAccent.copy(alpha = 0.45f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .testTag("continue_run_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = "Continue $modeLabel",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "($detail)",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Resume",
                    color = pillAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = pillAccent,
                    modifier = Modifier.size(16.dp)
                )
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        players.forEach { p ->
            val isActive = p.index == activePlayerIndex && p.isAlive
            val isRedemption = isActive && isRedemptionTurnActive
            val tag = "player_rail_p${p.index + 1}"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 56.dp)
                    .heightIn(min = 52.dp)
                    .alpha(if (p.isAlive) 1f else 0.40f)
                    .testTag(tag),
                shape = RoundedCornerShape(14.dp),
                color = when {
                    !p.isAlive -> Color(0xFF0F172A)
                    isActive -> p.color.copy(alpha = 0.25f)
                    else -> Color(0xFF1E293B)
                },
                border = BorderStroke(
                    width = if (isActive && p.isAlive) 2.dp else 1.dp,
                    color = when {
                        !p.isAlive -> Color(0xFF334155)
                        isRedemption -> Color(0xFFF59E0B)
                        isActive -> p.color
                        else -> Color(0xFF334155)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
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
                                .background(if (p.isAlive) p.color else Color(0xFF64748B))
                        )
                        Text(
                            text = "P${p.index + 1}",
                            color = if (p.isAlive) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            !p.isAlive -> "OUT"
                            isRedemption -> "MATCH"
                            else -> "${p.roundsSurvived} rd"
                        },
                        color = when {
                            !p.isAlive -> Color(0xFF94A3B8)
                            isRedemption -> Color(0xFFFBBF24)
                            isActive -> p.color
                            else -> Color(0xFFCBD5E1)
                        },
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
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
    onHome: () -> Unit,
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

            // Primary buttons: [REMATCH] [HOME]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Rematch", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }

                Button(
                    onClick = onHome,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFFE2E8F0)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("solo_mode_button")
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Home", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Lower Action Row: [FINAL ROUND]
            OutlinedButton(
                onClick = onWatchFinalRound,
                enabled = !isReplaying,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isReplaying) Color(0xFF475569) else Color(0xFFF59E0B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("watch_final_round_button")
            ) {
                val c = if (isReplaying) Color(0xFF64748B) else Color(0xFFFBBF24)
                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = c, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isReplaying) "Replaying..." else "Final Round",
                    color = c,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
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

    val isTie = standings.count { it.isWinner } > 1
    // Ordered strictly by rank left to right (1st -> 2nd -> 3rd -> 4th)
    val orderedStandings = standings.sortedWith(compareBy<PlayerStanding> { it.rank }.thenBy { it.player.index })
    val totalPlayers = orderedStandings.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("victory_podium_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        orderedStandings.forEachIndexed { idx, standing ->
            val isChampion = standing.isWinner
            val rankTieCount = orderedStandings.count { it.rank == standing.rank }
            val accentColor = when (standing.rank) {
                1 -> Color(0xFFF59E0B) // Gold
                2 -> Color(0xFF94A3B8) // Silver
                3 -> Color(0xFFD97706) // Bronze
                else -> Color(0xFF64748B)
            }
            // Step heights strictly keyed on rank (1st > 2nd > 3rd > 4th: 84 / 62 / 44 / 30)
            // Tied players share the exact same rank AND the exact same pedestal height!
            val pedestalHeight = when (standing.rank) {
                1 -> 84.dp
                2 -> 62.dp
                3 -> 44.dp
                else -> 30.dp
            }
            val medalIcon = when (standing.rank) {
                1 -> if (rankTieCount > 1) "🤝" else "👑"
                2 -> "🥈"
                3 -> "🥉"
                else -> "${standing.rank}th"
            }

            PodiumColumn(
                standing = standing,
                totalPlayers = totalPlayers,
                pedestalHeight = pedestalHeight,
                accentColor = accentColor,
                medalLabel = medalIcon,
                pedestalNumber = "${standing.rank}",
                isChampion = isChampion,
                glowAlpha = glowAlpha,
                modifier = Modifier
                    .weight(1f)
                    .testTag("podium_col_rank_${idx + 1}")
            )
        }
    }
}

@Composable
private fun PodiumColumn(
    standing: PlayerStanding,
    totalPlayers: Int,
    pedestalHeight: Dp,
    accentColor: Color,
    medalLabel: String,
    pedestalNumber: String,
    isChampion: Boolean,
    glowAlpha: Float,
    modifier: Modifier = Modifier
) {
    // 2P uses full "Player N"; 3P/4P uses slot labels "P1"-"P4" so columns never wrap at ~80dp
    val displayName = if (totalPlayers <= 2) {
        "Player ${standing.player.index + 1}"
    } else {
        "P${standing.player.index + 1}"
    }
    val rds = standing.player.roundsSurvived
    val roundsText = if (rds == 1) "1 round" else "$rds rounds"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shared Baseline Row 1: Badge / Medal (uniform 22.dp slot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isChampion) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF59E0B),
                    contentColor = Color(0xFF0F172A)
                ) {
                    Text(
                        text = if (totalPlayers >= 4) "CHAMP" else "CHAMPION",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            } else {
                Text(
                    text = medalLabel,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Shared Baseline Row 2: Avatar (uniform 36.dp slot & 34.dp circle across all columns)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .alpha(if (isChampion) 1f else 0.45f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isChampion) standing.player.color else Color(0xFF334155))
                    .border(if (isChampion) 2.5.dp else 1.dp, if (isChampion) accentColor else Color(0xFF475569), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "P${standing.player.index + 1}",
                    color = if (isChampion) Color(0xFF090D16) else Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Shared Baseline Row 3: Name (uniform 18.dp slot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName,
                color = if (isChampion) Color.White else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = if (isChampion) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1
            )
        }

        // Shared Baseline Row 4: Stats in full words ("2 rounds", uniform 18.dp slot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = roundsText,
                color = accentColor,
                fontSize = if (totalPlayers >= 4) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Shared Bottom-Aligned Pedestal Well (84.dp tall, bottom-aligned step block)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pedestalHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = if (isChampion) 0.35f + glowAlpha * 0.25f else 0.22f),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .border(
                        BorderStroke(if (isChampion) 2.dp else 1.5.dp, accentColor),
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pedestalNumber,
                    color = accentColor,
                    fontSize = if (isChampion) 20.sp else 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheetDialog(
    currentGridSize: GridSize,
    currentDifficulty: GameDifficulty,
    currentPaceMode: PaceMode,
    isMidRun: Boolean,
    onSelectGridSize: (GridSize) -> Unit,
    onSelectDifficulty: (GameDifficulty) -> Unit,
    onSelectPaceMode: (PaceMode) -> Unit,
    onResetMultiplayerWins: () -> Unit,
    dailyNotificationsEnabled: Boolean = false,
    onToggleDailyNotifications: (Boolean) -> Unit = {},
    onResumeRun: () -> Unit = {},
    onSaveAndExit: () -> Unit = {},
    onQuitToHome: () -> Unit = {},
    onRestartRun: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color(0xFF0F172A),
        labelColor = Color(0xFFCBD5E1),
        selectedContainerColor = Color(0xFF0284C7).copy(alpha = 0.32f),
        selectedLabelColor = Color(0xFF38BDF8)
    )
    val scrollState = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 460.dp)
                .testTag("settings_sheet_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.5.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isMidRun) "Game Paused" else "Settings", color = Color(0xFFF8FAFC), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_close_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = if (isMidRun) "Close Pause Menu" else "Close Settings", tint = Color(0xFF94A3B8))
                    }
                }

                if (isMidRun) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onResumeRun,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pause_resume_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color(0xFF090D16)
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }

                        Button(
                            onClick = onSaveAndExit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("pause_save_exit_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F172A),
                                contentColor = Color(0xFF38BDF8)
                            ),
                            border = BorderStroke(1.5.dp, Color(0xFF38BDF8))
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save & Exit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Button(
                            onClick = onQuitToHome,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("pause_quit_home_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F172A),
                                contentColor = Color(0xFFEF4444)
                            ),
                            border = BorderStroke(1.5.dp, Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Quit to Home", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Button(
                            onClick = onRestartRun,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("pause_restart_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F172A),
                                contentColor = Color(0xFFFBBF24)
                            ),
                            border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restart", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                } else {
                    // Full Settings Sheet strictly for the IDLE home screen
                    val context = LocalContext.current
                    var myNameInput by remember { mutableStateOf(ChallengeStorage.getMyName(context)) }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "My Name (for challenges)", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = myNameInput,
                            onValueChange = { input ->
                                val cleaned = input.replace(' ', '_').uppercase().filter { it.isLetterOrDigit() || it == '_' }
                                val truncated = if (cleaned.length > 10) cleaned.substring(0, 10) else cleaned
                                myNameInput = truncated
                                ChallengeStorage.setMyName(context, truncated)
                            },
                            placeholder = { Text("e.g. HERO", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("settings_my_name_input")
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(text = "Daily Notifications", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Brain teasers & streak reminders to keep your memory sharp", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                            Switch(
                                checked = dailyNotificationsEnabled,
                                onCheckedChange = onToggleDailyNotifications,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF0F172A),
                                    checkedTrackColor = Color(0xFF38BDF8),
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.testTag("daily_notifications_switch")
                            )
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
                        Text(text = "Difficulty", color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GameDifficulty.entries.forEach { diff ->
                                val sel = diff == currentDifficulty
                                val tag = if (diff == GameDifficulty.EASY) "difficulty_easy" else "difficulty_hard"
                                FilterChip(
                                    selected = sel,
                                    onClick = { onSelectDifficulty(diff) },
                                    label = {
                                        Text(
                                            diff.label,
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
                        Text(
                            text = if (currentDifficulty == GameDifficulty.EASY) "Cumulative sequence across rounds & players" else "Fresh unique sequence every round & turn",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
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

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        text = "Changing grid size, difficulty, or pace updates your active board.",
                        color = Color(0xFF94A3B8), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
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
}

@Composable
private fun NotificationPermissionDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 400.dp)
                .testTag("notification_permission_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Notifications",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Stay in the Flow",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Get a daily brain teaser and challenge reminders. Allow notifications?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onAllow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("notification_permission_allow_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Text("Allow", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("notification_permission_dismiss_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF94A3B8)
                    )
                ) {
                    Text("Not Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun OnboardingPopupDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 400.dp)
                .testTag("onboarding_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.85f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = "Welcome to Glowback!",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("onboarding_title_text")
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFBBF24).copy(alpha = 0.25f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("1", color = Color(0xFFFBBF24), fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                        Text(
                            text = "Watch the glowing pads.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.25f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("2", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                        Text(
                            text = "Repeat the sequence.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("3", color = Color(0xFF38BDF8), fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                        Text(
                            text = "Survive!",
                            color = Color(0xFF38BDF8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("onboarding_got_it_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Text("Got it", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = statusTitle,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "statusHeaderTransition"
            ) { title ->
                Text(
                    text = title, color = bannerAccentColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, modifier = if (title == statusTitle) Modifier.testTag("status_header_text") else Modifier
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusSubtitle, color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("status_subtitle_text")
            )
            if (gameState == GameState.GAME_OVER && sequenceSize > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Final Score: $currentScore", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = onWatchMyRound,
                        enabled = !isReplayingLostRound,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isReplayingLostRound) Color(0xFF475569) else Color(0xFF38BDF8)),
                        modifier = Modifier.heightIn(min = 38.dp).testTag("watch_my_round_button")
                    ) {
                        val c = if (isReplayingLostRound) Color(0xFF64748B) else Color(0xFF38BDF8)
                        Icon(imageVector = Icons.Default.Replay, contentDescription = null, tint = c, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = if (isReplayingLostRound) "Replaying..." else "Watch My Round", color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                        showShapeGlyph = true,
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
    inputEnabled: Boolean,
    onPadClick: (PadSpec) -> Unit
) = GlowPadGrid(modifier, gridSize, pads, activePadId, allPadsConfettiFlash, wrongShakePadId, wrongShakeOffsetPx, inputEnabled, onPadClick)

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
    val targetContainerColor = if (isFlashing) pad.brightColor else pad.dimColor
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(85, easing = FastOutSlowInEasing),
        label = "${pad.testTagId}_color"
    )
    val targetBorderColor = when {
        isWrongShake -> Color(0xFFEF4444)
        isFlashing -> Color.White
        else -> pad.brightColor.copy(alpha = 0.30f)
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
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
            val maxRadius = size.minDimension * 0.68f
            val glowColors = if (isFlashing) listOf(Color.White.copy(alpha = 0.58f), pad.glowColor.copy(alpha = 0.45f), Color.Transparent)
            else listOf(pad.brightColor.copy(alpha = 0.12f), Color.Transparent)
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
    gridSize: GridSize = GridSize.TWO_BY_TWO,
    activeRunState: SerializedRunState? = null,
    activeChallenge: ChallengeItem? = null,
    challengeAttemptsLeft: Int = 3,
    isChallengeWon: Boolean = false,
    isChallengeLost: Boolean = false,
    showChallengeNudge: Boolean = false,
    onStartOrRetry: () -> Unit,
    onResumeSavedRun: () -> Unit = {},
    onShareChallenge: () -> Unit = {},
    onCopyCode: () -> Unit = {},
    onReturnHome: () -> Unit = {},
    onReplyToRival: () -> Unit = {},
    onExitChallenge: () -> Unit = {}
) {
    if (gameState == GameState.WATCHING || gameState == GameState.PLAYING) {
        // Bottom slot remains empty during play; restart lives in pause dialog
        return
    }

    val isMulti = gameMode != GameMode.SOLO

    if (activeChallenge != null && gameState == GameState.GAME_OVER) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (challengeAttemptsLeft > 0 && !isChallengeWon) {
                Button(
                    onClick = onStartOrRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
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
                        .height(46.dp)
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
                        .height(46.dp)
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
                            .height(40.dp)
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
                        .height(40.dp)
                        .testTag("exit_challenge_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B))
                ) {
                    Text(text = "Exit Challenge", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else if (gameState == GameState.GAME_OVER && gameMode == GameMode.SOLO) {
        // PASS 2J Item 1: Solo Game Over 2x2 Grid with 12dp gaps
        // Top-Left: "Play Again" (Primary Green) | Top-Right: "Home" (Dark Slate)
        // Bottom-Left: "Share Challenge" (Dark Slate) | Bottom-Right: "Copy Challenge" (Dark Slate)
        val gridButtonShape = RoundedCornerShape(14.dp)
        val gridButtonHeight = 48.dp

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartOrRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(gridButtonHeight)
                        .testTag("start_game_button"),
                    shape = gridButtonShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color(0xFF0F172A)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Play Again", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }

                Button(
                    onClick = onReturnHome,
                    modifier = Modifier
                        .weight(1f)
                        .height(gridButtonHeight)
                        .testTag("game_over_home_button"),
                    shape = gridButtonShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFF475569))
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Home", color = Color(0xFFF8FAFC), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onShareChallenge,
                    modifier = Modifier
                        .weight(1f)
                        .height(gridButtonHeight)
                        .testTag("share_challenge_button"),
                    shape = gridButtonShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF00E5FF)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFF00E5FF))
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Share Challenge", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = onCopyCode,
                    modifier = Modifier
                        .weight(1f)
                        .height(gridButtonHeight)
                        .testTag("copy_challenge_code_button"),
                    shape = gridButtonShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF38BDF8)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8))
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Copy Challenge", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    } else {
        val label = when (gameState) {
            GameState.IDLE -> if (isMulti) "START ${gameMode.label} MATCH" else "START SOLO · ${gridSize.label}"
            GameState.GAME_OVER -> if (isMulti) "Rematch" else "Play Again"
            else -> if (isMulti) "START ${gameMode.label} MATCH" else "START SOLO · ${gridSize.label}"
        }
        val containerColor = when (gameState) {
            GameState.IDLE -> Color(0xFF10B981)
            GameState.GAME_OVER -> Color(0xFF38BDF8)
            else -> Color(0xFF10B981)
        }
        val contentColor = Color(0xFF0F172A)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (gameState == GameState.IDLE && activeRunState != null) {
                ContinueRunPill(
                    state = activeRunState,
                    onResume = onResumeSavedRun
                )
            }

            Button(
                onClick = onStartOrRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (gameState == GameState.GAME_OVER) 48.dp else 52.dp)
                    .testTag("start_game_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
            ) {
                Icon(imageVector = if (gameState == GameState.IDLE) Icons.Default.PlayArrow else Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
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

