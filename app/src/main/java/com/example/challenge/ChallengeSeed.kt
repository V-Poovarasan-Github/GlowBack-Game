package com.example.challenge

import java.util.Random

/**
 * Deterministic seed generator, Base32 encoder/decoder, and checksum calculator for Glowback.
 * Format: [Grid]-[Target]-T[tries][Difficulty]-[5-char Seed]-[1-char Checksum]
 * Example: 4x4-1-T3H-2GPGX-C or 2x2-5-T1E-8X2AB-D
 */
object ChallengeSeed {
    // 32-character custom alphabet (excludes 0, O, 1, I to eliminate ambiguity)
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val MAX_SEED = (1 shl 25) - 1 // 33,554,431 (25-bit seed)

    // Backwards-compatible regex: T([135])([EH])? defaulting to E, with optional 1-char checksum
    private val CODE_REGEX = Regex(
        pattern = """^([234]x[234])-([1-9][0-9]?)-T([135])([EH])?-([2-9A-HJ-NP-Z]{4,5})(?:-([2-9A-HJ-NP-Z]))?$""",
        option = RegexOption.IGNORE_CASE
    )

    // Legacy format support without tries segment: e.g. "2x2-12-8X2A"
    private val LEGACY_REGEX = Regex(
        pattern = """^([234]x[234])-([1-9][0-9]?)-([2-9A-HJ-NP-Z]{4})$""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Computes a 1-character Base32 checksum over the raw core string (everything before the checksum hyphen).
     */
    fun computeChecksum(rawCore: String): Char {
        var sum = 0
        for ((idx, ch) in rawCore.uppercase().withIndex()) {
            val weight = (idx % 7) + 1
            sum = (sum + ch.code * weight) % 32
        }
        return ALPHABET[sum % ALPHABET.length]
    }

    fun verifyChecksum(rawCore: String, expectedChecksum: Char): Boolean {
        return computeChecksum(rawCore).equals(expectedChecksum, ignoreCase = true)
    }

    /**
     * Encodes a 25-bit integer seed into a 5-character Base32 string.
     */
    fun encodeSeed(seed: Int): String {
        val safeSeed = seed and MAX_SEED
        val c0 = ALPHABET[(safeSeed ushr 20) and 0x1F]
        val c1 = ALPHABET[(safeSeed ushr 15) and 0x1F]
        val c2 = ALPHABET[(safeSeed ushr 10) and 0x1F]
        val c3 = ALPHABET[(safeSeed ushr 5) and 0x1F]
        val c4 = ALPHABET[safeSeed and 0x1F]
        return "$c0$c1$c2$c3$c4"
    }

    /**
     * Decodes a 4- or 5-character Base32 string back to an integer seed.
     * Returns null if any character is invalid or length not in 4..5.
     */
    fun decodeSeed(codeStr: String): Int? {
        val clean = codeStr.trim().uppercase()
        if (clean.length !in 4..5) return null
        var seed = 0
        for (ch in clean) {
            val idx = ALPHABET.indexOf(ch)
            if (idx == -1) return null
            seed = (seed shl 5) or idx
        }
        return seed
    }

    /**
     * Formats a complete structured challenge code:
     * e.g. "4x4-1-T3H-2GPGX-C" or "2x2-5-T1E-8X2AB-D"
     */
    fun formatChallengeCode(
        gridTag: String,
        targetScore: Int,
        tries: Int = 3,
        difficulty: GameDifficulty = GameDifficulty.EASY,
        seed: Int
    ): String {
        val seedCode = encodeSeed(seed)
        val normalizedGrid = gridTag.lowercase()
        val safeTries = if (tries in listOf(1, 3, 5)) tries else 3
        val diffChar = difficulty.codeChar
        val rawCore = "$normalizedGrid-$targetScore-T$safeTries$diffChar-$seedCode"
        val checksum = computeChecksum(rawCore)
        return "$rawCore-$checksum"
    }

    /**
     * Parses a full challenge code.
     * Supports:
     * 1) New format: [Grid]-[Target]-T[tries][Difficulty]-[5-char Seed]-[1-char Checksum]
     * 2) Backwards-compatible: T([135])([EH])? defaulting to E, optional checksum
     * 3) Legacy format: [Grid]-[Target]-[4-char Seed]
     * Returns null if invalid format or failing checksum.
     */
    fun parseChallengeCode(rawCode: String): ParsedChallenge? {
        val clean = rawCode.trim().uppercase()

        val match = CODE_REGEX.matchEntire(clean)
        if (match != null) {
            val gridTag = match.groupValues[1].lowercase()
            val targetScore = match.groupValues[2].toIntOrNull() ?: return null
            val tries = match.groupValues[3].toIntOrNull() ?: 3
            val diffChar = match.groupValues[4].firstOrNull()
            val difficulty = GameDifficulty.fromChar(diffChar)
            val seedStr = match.groupValues[5].uppercase()
            val checksumStr = match.groupValues[6].takeIf { it.isNotEmpty() }
            val seedInt = decodeSeed(seedStr) ?: return null

            val rawCore = "$gridTag-$targetScore-T$tries${difficulty.codeChar}-$seedStr"
            if (checksumStr != null) {
                val expected = checksumStr.first()
                if (!verifyChecksum(rawCore, expected)) {
                    return null // Invalid Checksum!
                }
            }

            val finalChecksum = checksumStr?.firstOrNull() ?: computeChecksum(rawCore)
            val fullCode = "$rawCore-$finalChecksum"

            return ParsedChallenge(
                gridTag = gridTag,
                targetScore = targetScore,
                tries = tries,
                difficulty = difficulty,
                seedCode = seedStr,
                seedInt = seedInt,
                checksum = finalChecksum,
                fullCode = fullCode
            )
        }

        val legacyMatch = LEGACY_REGEX.matchEntire(clean)
        if (legacyMatch != null) {
            val gridTag = legacyMatch.groupValues[1].lowercase()
            val targetScore = legacyMatch.groupValues[2].toIntOrNull() ?: return null
            val seedStr = legacyMatch.groupValues[3].uppercase()
            val seedInt = decodeSeed(seedStr) ?: return null
            val rawCore = "$gridTag-$targetScore-T3E-$seedStr"
            val checksum = computeChecksum(rawCore)
            val fullCode = "$rawCore-$checksum"

            return ParsedChallenge(
                gridTag = gridTag,
                targetScore = targetScore,
                tries = 3,
                difficulty = GameDifficulty.EASY,
                seedCode = seedStr,
                seedInt = seedInt,
                checksum = checksum,
                fullCode = fullCode
            )
        }

        return null
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
     * Creates a new random 25-bit seed.
     */
    fun generateRandomSeed(): Int {
        return (Math.random() * (MAX_SEED + 1)).toInt() and MAX_SEED
    }
}

