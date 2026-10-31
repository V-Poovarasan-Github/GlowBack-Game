package com.example.challenge

/**
 * Data model for a peer-to-peer asynchronous challenge in Glowback.
 */
data class ChallengeItem(
    val code: String,          // e.g. "2x2-12-8X2A"
    val rivalName: String,     // e.g. "Alex" or "Anonymous"
    val gridSizeTag: String,   // "2x2", "3x3", "4x4"
    val targetScore: Int,      // e.g. 12
    var attemptsLeft: Int,     // e.g. 3
    var bestScore: Int,        // e.g. 0
    val seed: Int,             // 20-bit seed integer (0..1,048,575)
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
    val seedCode: String,
    val seedInt: Int,
    val fullCode: String
)
