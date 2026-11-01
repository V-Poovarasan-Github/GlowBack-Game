package com.example.challenge

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
    val code: String,          // e.g. "4x4-1-T3H-2GPGX-C"
    val rivalName: String,     // e.g. "Alex" or "Anonymous"
    val gridSizeTag: String,   // "2x2", "3x3", "4x4"
    val targetScore: Int,      // e.g. 12
    val maxTries: Int = 3,     // e.g. 3
    var attemptsLeft: Int,     // e.g. 3
    var bestScore: Int,        // e.g. 0
    val seed: Int,             // seed integer
    val difficulty: GameDifficulty = GameDifficulty.EASY,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isCompleted: Boolean
        get() = bestScore >= targetScore || attemptsLeft <= 0

    val isWon: Boolean
        get() = bestScore >= targetScore
}

data class ParsedChallenge(
    val gridTag: String,
    val targetScore: Int,
    val tries: Int = 3,
    val difficulty: GameDifficulty = GameDifficulty.EASY,
    val seedCode: String,
    val seedInt: Int,
    val checksum: Char? = null,
    val fullCode: String
)


