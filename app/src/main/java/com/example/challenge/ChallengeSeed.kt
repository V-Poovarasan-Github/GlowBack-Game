package com.example.challenge

import java.util.Random

private val ZERO_WIDTH_REGEX = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]")
private val UNICODE_DASH_REGEX = Regex("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212\\uFE58\\uFE63\\uFF0D]")
private val V2_EXTRACTION_REGEX = Regex("""\b((?:[234]x[234]|5x4)-\d+-[TR][135F][EH]-[A-Za-z0-9_]{1,10}-[A-Za-z0-9]{3,5}-[A-NP-Z0-9a-np-z]{4,5}-[A-Za-z0-9])\b""", RegexOption.IGNORE_CASE)
private val V1_EXTRACTION_REGEX = Regex("""\b((?:[234]x[234]|5x4)-\d+-[TR][135F][EH]-[A-NP-Z0-9a-np-z]{4,5}-[A-Za-z0-9])\b""", RegexOption.IGNORE_CASE)
private val V0_EXTRACTION_REGEX = Regex("""(?<![A-Za-z0-9_-])\b((?:[234]x[234]|5x4)-\d+-(?:(?:T[135F][EH]|R[135F][EH])-)?[A-NP-Z0-9a-np-z]{4,5}(?:-[A-Za-z0-9])?)\b(?!-[A-Za-z0-9_])""", RegexOption.IGNORE_CASE)
private val GRID_REGEX = Regex("^([234]x[234]|5x4)$")
private val TD_MATCH_REGEX = Regex("^([TR])([135F])([EH])$")
private val TD_LEGACY_MATCH_REGEX = Regex("^([TR])?([135F])?([EH])?$")

/**
 * Deterministic seed generator, Base32 encoder/decoder, and checksum calculator for Glowback.
 * Format v2 (7 segments): [Grid]-[Target]-[TD]-[NAME]-[CHAIN]-[5-char Seed]-[1-char Checksum]
 * Example: 4x4-12-T3H-NEON_KING-K7Q-8X2AB-C
 *
 * Legacy format v1 (5 segments): [Grid]-[Target]-T[tries][Diff]-[5-char Seed]-[1-char Checksum]
 * Example: 4x4-1-T3H-2GPGX-C
 */
object ChallengeSeed {
    // 32-character custom alphabet (excludes 0, O, 1, I to eliminate ambiguity)
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val MAX_SEED = (1 shl 25) - 1 // 33,554,431 (25-bit seed)

    val NAME_REGEX = Regex("^[A-Z0-9_]{1,10}$")

    /**
     * Sanitizes a player's display name:
     * - Replaces spaces with underscores
     * - Converts to uppercase
     * - Retains only letters, digits, and underscores (ASCII only)
     * - Limits to 10 characters
     * - Defaults to "HERO" if empty
     */
    fun sanitizePlayerName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "HERO"
        val cleaned = rawName.trim().replace(' ', '_').uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        val truncated = if (cleaned.length > 10) cleaned.substring(0, 10) else cleaned
        return truncated.ifEmpty { "HERO" }
    }

    /**
     * PASS 5.33: Generates a 4-character Base36/Base32 Chain ID for newly created rivalry threads.
     */
    fun generateChainId(): String {
        return (1..4).map { ALPHABET[(Math.random() * ALPHABET.length).toInt()] }.joinToString("")
    }

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
     * Format v2: [Grid]-[Target]-[TD]-[NAME]-[CHAIN]-[Seed]-[Checksum]
     * e.g. "4x4-12-T3H-NEON_KING-K7Q-8X2AB-C"
     */
    fun formatChallengeCode(
        gridTag: String,
        targetScore: Int,
        tries: Int = 3,
        difficulty: GameDifficulty = GameDifficulty.EASY,
        seed: Int,
        senderName: String = "HERO",
        chainId: String = generateChainId(),
        isRevenge: Boolean = false
    ): String {
        val seedCode = encodeSeed(seed)
        val normalizedGrid = gridTag.lowercase()
        val safeTries = when (tries) {
            1 -> "1"
            3 -> "3"
            5 -> "5"
            0 -> "F"
            else -> "3"
        }
        val typeChar = if (isRevenge) 'R' else 'T'
        val diffChar = difficulty.codeChar
        val tdSegment = "$typeChar$safeTries$diffChar"
        val cleanName = sanitizePlayerName(senderName)
        val cleanChain = chainId.trim().uppercase().take(5).ifEmpty { generateChainId() }

        val rawCore = "$normalizedGrid-$targetScore-$tdSegment-$cleanName-$cleanChain-$seedCode"
        val checksum = computeChecksum(rawCore)
        return "$rawCore-$checksum"
    }

    /**
     * Parses a full challenge code.
     * Supports:
     * 1) Format v2 (7 segments): [Grid]-[Target]-[TD]-[NAME]-[CHAIN]-[Seed]-[Checksum]
     * 2) Legacy format v1 (5 segments): [Grid]-[Target]-T[tries][Difficulty]-[Seed]-[Checksum]
     * 3) Legacy format v0 (3 or 4 segments): [Grid]-[Target]-[Seed] or [Grid]-[Target]-[Seed]-[Checksum]
     *
     * Returns null if invalid structure, invalid characters, or failed checksum.
     */
    /**
     * Strips invisible zero-width formatting characters (e.g. from WhatsApp/Telegram/iOS copy-paste)
     * and normalizes Unicode en-dashes/em-dashes/minus signs to standard ASCII '-'.
     */
    fun sanitizeCodeText(input: String): String {
        return input
            .replace(ZERO_WIDTH_REGEX, "")
            .replace(UNICODE_DASH_REGEX, "-")
            .trim()
    }

    /**
     * Extracts a challenge code token from freeform text if surrounded by words, colons, or quotes.
     * PASS 5.14: Fail fast if a 5/6/7-segment structure is detected but fails checksum verification.
     */
    fun extractCodeFromText(input: String): String? {
        val cleanedInput = sanitizeCodeText(input)
        if (parseInternal(cleanedInput) != null) return cleanedInput

        // If input is a bare 5/6/7+ segment hyphenated token whose checksum/parse failed, hard stop immediately
        if (!cleanedInput.contains(' ') && !cleanedInput.contains('\n') && cleanedInput.split('-').size >= 5) {
            return null
        }

        // Format v2 7-segment: [Grid]-[Target]-[TD]-[NAME]-[CHAIN]-[Seed]-[Checksum]
        V2_EXTRACTION_REGEX.find(cleanedInput)?.let { m ->
            val cand = m.value.trim()
            // Hard stop: if a 7-segment v2 code structure is present, return it ONLY if checksum passes, otherwise null (never fall back to v0)
            return if (parseInternal(cand) != null) cand else null
        }

        // 5-segment Legacy format: [Grid]-[Target]-[T/R][tries][Diff]-[Seed]-[Checksum]
        V1_EXTRACTION_REGEX.find(cleanedInput)?.let { m ->
            val cand = m.value.trim()
            // Hard stop: if a 5-segment v1 code structure is present, return it ONLY if checksum passes, otherwise null
            return if (parseInternal(cand) != null) cand else null
        }

        // 4-segment / 3-segment Legacy format — negative lookahead (?!-[A-Za-z0-9_]) ensures a 5/6/7-segment code can never have its first 4 segments sliced off
        V0_EXTRACTION_REGEX.find(cleanedInput)?.let { m ->
            val cand = m.value.trim()
            if (parseInternal(cand) != null) return cand
        }

        return null
    }

    fun parseChallengeCode(rawCode: String): ParsedChallenge? {
        val cleaned = sanitizeCodeText(rawCode)
        val direct = parseInternal(cleaned)
        if (direct != null) return direct

        // PASS 5.14: Fail fast if a bare 5/6/7+ segment code failed direct parsing (e.g. checksum mismatch)
        if (!cleaned.contains(' ') && !cleaned.contains('\n') && cleaned.split('-').size >= 5) {
            return null
        }

        val extracted = extractCodeFromText(cleaned)
        if (extracted != null && extracted != cleaned) {
            return parseInternal(extracted)
        }
        return null
    }

    private fun parseInternal(rawCode: String): ParsedChallenge? {
        val clean = sanitizeCodeText(rawCode).uppercase()
        val parts = clean.split('-')

        // 7-segment Format v2: [Grid]-[Target]-[TD]-[NAME]-[CHAIN]-[Seed]-[Checksum]
        if (parts.size == 7) {
            val gridTag = parts[0].lowercase()
            if (!GRID_REGEX.matches(gridTag)) return null

            // REPLY-CODE EXEMPTION: Inbound parser accepts targetScore in 1..999 so reply codes
            // (where a friend lost early at score 1..4) and legacy codes remain valid,
            // even though outbound initial challenge creation requires score >= 5.
            val targetScore = parts[1].toIntOrNull() ?: return null
            if (targetScore !in 1..999) return null

            val td = parts[2]
            val tdMatch = TD_MATCH_REGEX.matchEntire(td) ?: return null
            val typePrefix = tdMatch.groupValues[1].uppercase()
            val isRevenge = typePrefix == "R"
            val triesChar = tdMatch.groupValues[2]
            val tries = if (triesChar == "F") 0 else triesChar.toInt()
            val diffChar = tdMatch.groupValues[3].first()
            val difficulty = GameDifficulty.fromChar(diffChar)

            val name = parts[3]
            if (!NAME_REGEX.matches(name)) return null

            val chainId = parts[4]
            if (chainId.length !in 3..5) return null

            val seedStr = parts[5]
            val seedInt = decodeSeed(seedStr) ?: return null

            val checksumStr = parts[6]
            if (checksumStr.length != 1) return null
            val expectedChecksum = checksumStr.first()

            val rawCore = "$gridTag-$targetScore-$td-$name-$chainId-$seedStr"
            if (!verifyChecksum(rawCore, expectedChecksum)) {
                return null // Checksum mismatch
            }

            return ParsedChallenge(
                gridTag = gridTag,
                targetScore = targetScore,
                tries = tries,
                difficulty = difficulty,
                senderName = name,
                chainId = chainId,
                seedCode = seedStr,
                seedInt = seedInt,
                checksum = expectedChecksum,
                fullCode = clean,
                isLegacy = false,
                isRevenge = isRevenge
            )
        }

        // 5-segment Format v1 (Legacy): [Grid]-[Target]-[T/R][tries][Difficulty]-[Seed]-[Checksum]
        if (parts.size == 5) {
            val gridTag = parts[0].lowercase()
            if (!GRID_REGEX.matches(gridTag)) return null

            // REPLY-CODE EXEMPTION: Accept inbound targetScore in 1..999
            val targetScore = parts[1].toIntOrNull() ?: return null
            if (targetScore !in 1..999) return null

            val td = parts[2]
            val tdMatch = TD_LEGACY_MATCH_REGEX.matchEntire(td) ?: return null
            val typePrefix = tdMatch.groupValues[1].uppercase().ifEmpty { "T" }
            val isRevenge = typePrefix == "R"
            val triesChar = tdMatch.groupValues[2]
            val tries = if (triesChar == "F") 0 else triesChar.toIntOrNull() ?: 3
            val diffChar = tdMatch.groupValues[3].firstOrNull()
            val difficulty = GameDifficulty.fromChar(diffChar)

            val seedStr = parts[3]
            val seedInt = decodeSeed(seedStr) ?: return null

            val checksumStr = parts[4]
            if (checksumStr.length != 1) return null
            val expectedChecksum = checksumStr.first()

            val rawTriesChar = if (tries == 0) "F" else tries.toString()
            val rawCore = "$gridTag-$targetScore-$typePrefix$rawTriesChar${difficulty.codeChar}-$seedStr"
            if (!verifyChecksum(rawCore, expectedChecksum)) {
                return null // Checksum mismatch
            }

            return ParsedChallenge(
                gridTag = gridTag,
                targetScore = targetScore,
                tries = tries,
                difficulty = difficulty,
                senderName = "RIVAL",
                chainId = "",
                seedCode = seedStr,
                seedInt = seedInt,
                checksum = expectedChecksum,
                fullCode = clean,
                isLegacy = true,
                isRevenge = isRevenge
            )
        }

        // 4-segment format:
        // Option A: [Grid]-[Target]-[TD]-[Seed] (e.g. 4x4-20-T5-8X2A, 4x4-1-T3H-2GPGX)
        // Option B: [Grid]-[Target]-[Seed]-[Checksum] (e.g. 2x2-12-8X2A-C)
        if (parts.size == 4) {
            val gridTag = parts[0].lowercase()
            if (!GRID_REGEX.matches(gridTag)) return null

            val targetScore = parts[1].toIntOrNull() ?: return null
            if (targetScore !in 1..999) return null

            // Check if parts[2] is a TD spec: e.g. T5, T3, T3H, TFE, TFH, R3H
            val tdMatch = TD_LEGACY_MATCH_REGEX.matchEntire(parts[2])
            val seedFromPart3 = decodeSeed(parts[3])
            if (tdMatch != null && seedFromPart3 != null) {
                val typePrefix = tdMatch.groupValues[1].uppercase().ifEmpty { "T" }
                val isRevenge = typePrefix == "R"
                val triesChar = tdMatch.groupValues[2]
                val tries = if (triesChar == "F") 0 else triesChar.toIntOrNull() ?: 3
                val diffChar = tdMatch.groupValues[3].firstOrNull()
                val difficulty = GameDifficulty.fromChar(diffChar)
                val rawTriesChar = if (tries == 0) "F" else tries.toString()
                val rawCore = "$gridTag-$targetScore-$typePrefix$rawTriesChar${difficulty.codeChar}-${parts[3]}"
                val checksum = computeChecksum(rawCore)

                return ParsedChallenge(
                    gridTag = gridTag,
                    targetScore = targetScore,
                    tries = tries,
                    difficulty = difficulty,
                    senderName = "RIVAL",
                    chainId = "",
                    seedCode = parts[3],
                    seedInt = seedFromPart3,
                    checksum = checksum,
                    fullCode = "$rawCore-$checksum",
                    isLegacy = true,
                    isRevenge = isRevenge
                )
            }

            // Option B: [Grid]-[Target]-[Seed]-[Checksum]
            val seedStr = parts[2]
            val seedInt = decodeSeed(seedStr) ?: return null
            val rawCore = "$gridTag-$targetScore-T3E-$seedStr"
            val expectedChecksum = parts[3].firstOrNull() ?: return null
            if (!verifyChecksum(rawCore, expectedChecksum)) return null

            return ParsedChallenge(
                gridTag = gridTag,
                targetScore = targetScore,
                tries = 3,
                difficulty = GameDifficulty.EASY,
                senderName = "RIVAL",
                chainId = "",
                seedCode = seedStr,
                seedInt = seedInt,
                checksum = expectedChecksum,
                fullCode = "$rawCore-$expectedChecksum",
                isLegacy = true
            )
        }

        // 3-segment legacy format: [Grid]-[Target]-[Seed] (e.g. 3x3-15-B4Y9)
        if (parts.size == 3) {
            val gridTag = parts[0].lowercase()
            if (!GRID_REGEX.matches(gridTag)) return null

            val targetScore = parts[1].toIntOrNull() ?: return null
            if (targetScore !in 1..999) return null

            val seedStr = parts[2]
            val seedInt = decodeSeed(seedStr) ?: return null

            val rawCore = "$gridTag-$targetScore-T3E-$seedStr"
            val checksum = computeChecksum(rawCore)

            return ParsedChallenge(
                gridTag = gridTag,
                targetScore = targetScore,
                tries = 3,
                difficulty = GameDifficulty.EASY,
                senderName = "RIVAL",
                chainId = "",
                seedCode = seedStr,
                seedInt = seedInt,
                checksum = checksum,
                fullCode = "$rawCore-$checksum",
                isLegacy = true
            )
        }

        // Any other segment count is invalid
        return null
    }

    /**
     * Generates a deterministic sequence of pad indices of the requested [length]
     * for a grid containing [padCount] pads (e.g. 4, 9, 16, or 20).
     */
    fun generateSequence(seed: Int, padCount: Int, length: Int): List<Int> {
        if (padCount <= 0 || length <= 0) return emptyList()
        val rng = Random(seed.toLong())
        return List(length) { rng.nextInt(padCount) }
    }

    /**
     * Generates the exact 0-based pad index sequence for a 1-based [round] under [difficulty]:
     * - EASY: Cumulative 1 draw per round (Round R has R indices drawn sequentially from Random(seed)).
     * - HARD: Round R draws R fresh indices after prior rounds 1..(R-1) each consumed r draws.
     */
    fun generateSequenceForRound(
        seed: Int,
        padCount: Int,
        difficulty: GameDifficulty = GameDifficulty.EASY,
        round: Int
    ): List<Int> {
        if (padCount <= 0 || round <= 0) return emptyList()
        val rng = Random(seed.toLong())
        return if (difficulty == GameDifficulty.HARD) {
            for (r in 1 until round) {
                repeat(r) { rng.nextInt(padCount) }
            }
            List(round) { rng.nextInt(padCount) }
        } else {
            List(round) { rng.nextInt(padCount) }
        }
    }

    @Volatile
    var lastGeneratedRandomSeed: Int = 0
        private set

    /**
     * Creates a new random non-zero 25-bit seed (1..MAX_SEED).
     */
    fun generateRandomSeed(): Int {
        val raw = (Math.random() * MAX_SEED).toInt() + 1
        val generated = (raw and MAX_SEED).let { if (it <= 0) 1 else it }
        lastGeneratedRandomSeed = generated
        return generated
    }
}
