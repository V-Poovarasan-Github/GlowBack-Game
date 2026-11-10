package com.example.challenge

import com.example.PaceMode

enum class GameDifficulty(val label: String, val codeChar: Char) {
    EASY("Easy", 'E'),
    HARD("Hard", 'H');

    companion object {
        fun fromChar(c: Char?): GameDifficulty = when (c?.uppercaseChar()) {
            'H' -> HARD
            else -> EASY
        }
    }
}

/**
 * Data model for a peer-to-peer asynchronous challenge in Glowback.
 */
data class ChallengeItem(
    val code: String,          // e.g. "4x4-12-T3H-NEON_KING-K7Q-8X2AB-C"
    val rivalName: String,     // e.g. "ALEX" or "NEON_KING"
    val gridSizeTag: String,   // "2x2", "3x3", "4x4"
    val targetScore: Int,      // e.g. 12
    val maxTries: Int = 3,     // e.g. 3
    var attemptsLeft: Int,     // e.g. 3
    var bestScore: Int,        // e.g. 0
    val seed: Int,             // seed integer
    val difficulty: GameDifficulty = GameDifficulty.EASY,
    val chainId: String = "",  // 3-char chain identifier (e.g. "K7Q")
    val isRevenge: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isFreeTries: Boolean
        get() = maxTries == 0

    val isCompleted: Boolean
        get() = bestScore > targetScore || (!isFreeTries && attemptsLeft <= 0)

    val isWon: Boolean
        get() = bestScore > targetScore

    val isTied: Boolean
        get() = bestScore == targetScore && targetScore > 0
}

data class BoardScoreOption(
    val gridTag: String,
    val paceName: String,
    val difficulty: GameDifficulty,
    val score: Int,
    val seed: Int = 0
)

/**
 * PASS 5.28: Represents a stored Classic board score that already strictly beats
 * a rival's recorded lastTarget on the exact same gridTag and difficulty.
 */
data class SleepingScoreRecommendation(
    val boardOption: BoardScoreOption,
    val rivalLastTarget: Int
) {
    val gridDisplay: String
        get() = when (boardOption.gridTag.lowercase()) {
            "2x2" -> "2×2"
            "3x3" -> "3×3"
            "4x4" -> "4×4"
            "5x4" -> "5×4"
            else -> boardOption.gridTag
        }

    val chipLabel: String
        get() = "$gridDisplay ${boardOption.difficulty.label} · You ${boardOption.score} vs their $rivalLastTarget"
}

sealed class ShareSource {
    data class FromRunSnapshot(
        val gridTag: String,
        val gridLabel: String,
        val score: Int,
        val difficulty: GameDifficulty,
        val seed: Int,
        val pace: PaceMode = PaceMode.CLASSIC,
        val lockedRivalName: String? = null,
        val lockedChainId: String? = null
    ) : ShareSource()

    data object FromBoardSelector : ShareSource()

    data class FromRivalRecommendation(
        val rivalName: String,
        val chainId: String,
        val qualifyingBoards: List<BoardScoreOption>,
        val initialIndex: Int = 0
    ) : ShareSource()
}

data class ParsedChallenge(
    val gridTag: String,
    val targetScore: Int,
    val tries: Int = 3,
    val difficulty: GameDifficulty = GameDifficulty.EASY,
    val senderName: String = "HERO",
    val chainId: String = "",
    val seedCode: String,
    val seedInt: Int,
    val checksum: Char? = null,
    val fullCode: String,
    val isLegacy: Boolean = false,
    val isRevenge: Boolean = false
)

data class GridStat(
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val lastTarget: Int = 0,                                   // Rival's last score on this grid from my perspective
    val lastDifficulty: GameDifficulty = GameDifficulty.EASY,  // EASY or HARD only
    val lastStatus: String = ""                                // "WON", "LOST", "TIED"
)

/**
 * PASS 5.33: Data model for a pending targeted outbound challenge linked to a specific rival.
 */
data class PendingOutboundChallenge(
    val gridTag: String,                               // e.g. "4x4"
    val difficulty: GameDifficulty = GameDifficulty.EASY,
    val target: Int,                                   // Target score
    val tries: Int = 3,                                // Max tries (0 = Free)
    val code: String,                                  // Full 7-segment code
    val mintedAt: Long = System.currentTimeMillis(),   // Mint timestamp for 7-day ghost unlock
    val rivalName: String,                             // e.g. "ALEX"
    val seed: Int = 0,                                 // Board seed for ledger correlation
    val chainId: String = ""                           // Chain ID for revenge thread
)

/**
 * Record for head-to-head rivalry history with a named rival.
 */
data class RivalRecord(
    val name: String,                  // Uppercase sanitized name, e.g. "ALEX"
    val wins: Int = 0,                 // Times player won against this rival
    val losses: Int = 0,               // Times player lost to this rival
    val ties: Int = 0,                 // Times player tied
    val matches: Int = 0,              // Total matches played
    val lastGrid: String = "2x2",      // e.g. "4x4"
    val lastDifficulty: GameDifficulty = GameDifficulty.EASY,
    val lastTarget: Int = 0,           // Rival's score or target
    val lastUserScore: Int = 0,        // Player's score in last match (or bestScore when IN_PROGRESS)
    val lastStatus: String = "WON",    // "WON", "LOST", "TIED", or "IN_PROGRESS"
    val lastChainId: String = "",      // Chain ID for revenge/counter-challenges
    val updatedAt: Long = System.currentTimeMillis(),
    val inProgressCode: String = "",   // Full challenge code when lastStatus == "IN_PROGRESS"
    val inProgressTriesLeft: Int = 0,  // Remaining tries when lastStatus == "IN_PROGRESS"
    val inProgressMaxTries: Int = 3,   // Max tries (0 = Free tries) when lastStatus == "IN_PROGRESS"
    val inProgressSeed: Int = 0,       // Challenge seed when lastStatus == "IN_PROGRESS"
    val gridStats: Map<String, GridStat> = emptyMap(), // Per-grid breakdown (e.g. "2x2" -> GridStat(1, 1, 0))
    val lastReplyCode: String = "",    // Saved R-code from last completed incoming challenge
    val isReplySent: Boolean = true,   // False when an R-code was generated on Game Over but not yet copied/shared
    val pendingOutbounds: List<PendingOutboundChallenge> = emptyList() // PASS 5.33: Active targeted shares awaiting reply
)

/**
 * Record for an outbound challenge waiting for a challenger's response.
 */
data class AwaitingChallenge(
    val chainId: String,               // Chain ID, e.g. "K7Q" or "K7QZ"
    val code: String,                  // Full 7-segment code
    val gridSizeTag: String,           // "2x2", "3x3", "4x4"
    val targetScore: Int,              // Score to beat
    val difficulty: GameDifficulty = GameDifficulty.EASY,
    val paceName: String = "Classic",
    val tries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val rivalName: String? = null      // PASS 5.33: Null for broadcast shares; named rival for targeted shares
)
