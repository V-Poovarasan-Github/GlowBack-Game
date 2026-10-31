package com.example.challenge

import java.util.Random

/**
 * Deterministic seed generator and Base32 encoder/decoder for Glowback.
 * Uses a 20-bit seed (0..1,048,575) to produce a 4-character code.
 */
object ChallengeSeed {
    // 32-character custom alphabet (excludes 0, O, 1, I to eliminate ambiguity)
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val MAX_SEED = (1 shl 20) - 1 // 1,048,575

    private val CODE_REGEX = Regex(
        pattern = """^([234]x[234])-([1-9][0-9]?)-([2-9A-HJ-NP-Z]{4})$""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Encodes a 20-bit integer seed (0..1,048,575) into a 4-character Base32 string.
     */
    fun encodeSeed(seed: Int): String {
        val safeSeed = seed and MAX_SEED
        val c0 = ALPHABET[(safeSeed ushr 15) and 0x1F]
        val c1 = ALPHABET[(safeSeed ushr 10) and 0x1F]
        val c2 = ALPHABET[(safeSeed ushr 5) and 0x1F]
        val c3 = ALPHABET[safeSeed and 0x1F]
        return "$c0$c1$c2$c3"
    }

    /**
     * Decodes a 4-character Base32 string back to a 20-bit integer seed.
     * Returns null if any character is invalid or length != 4.
     */
    fun decodeSeed(code4: String): Int? {
        val clean = code4.trim().uppercase()
        if (clean.length != 4) return null
        var seed = 0
        for (ch in clean) {
            val idx = ALPHABET.indexOf(ch)
            if (idx == -1) return null
            seed = (seed shl 5) or idx
        }
        return seed
    }

    /**
     * Formats a complete structured challenge code: e.g. "2x2-12-8X2A"
     */
    fun formatChallengeCode(gridTag: String, targetScore: Int, seed: Int): String {
        val seedCode = encodeSeed(seed)
        val normalizedGrid = gridTag.lowercase()
        return "$normalizedGrid-$targetScore-$seedCode"
    }

    /**
     * Parses a full challenge code (e.g. "2x2-12-8X2A").
     * Returns a [ParsedChallenge] if valid, or null if invalid format.
     */
    fun parseChallengeCode(rawCode: String): ParsedChallenge? {
        val clean = rawCode.trim().uppercase()
        val match = CODE_REGEX.matchEntire(clean) ?: return null

        val gridTag = match.groupValues[1].lowercase()
        val targetScore = match.groupValues[2].toIntOrNull() ?: return null
        val seedStr = match.groupValues[3].uppercase()
        val seedInt = decodeSeed(seedStr) ?: return null

        return ParsedChallenge(
            gridTag = gridTag,
            targetScore = targetScore,
            seedCode = seedStr,
            seedInt = seedInt,
            fullCode = "$gridTag-$targetScore-$seedStr"
        )
    }

    /**
     * Generates a deterministic sequence of pad indices of the requested [length]
     * for a grid containing [padCount] pads (e.g. 4, 9, or 16).
     */
    fun generateSequence(seed: Int, padCount: Int, length: Int): List<Int> {
        if (padCount <= 0 || length <= 0) return emptyList()
        val rng = Random(seed.toLong())
        return List(length) { rng.nextInt(padCount) }
    }

    /**
     * Creates a new random 20-bit seed (0..1,048,575).
     */
    fun generateRandomSeed(): Int {
        return (Math.random() * (MAX_SEED + 1)).toInt() and MAX_SEED
    }
}
