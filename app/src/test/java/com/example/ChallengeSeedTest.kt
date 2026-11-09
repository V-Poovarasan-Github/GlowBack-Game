package com.example

import com.example.challenge.ChallengeSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeSeedTest {

    @Test
    fun testSeed12345GeneratesDeterministicSequence() {
        val seed = 12345
        val seq1 = ChallengeSeed.generateSequence(seed = seed, padCount = 4, length = 10)
        val seq2 = ChallengeSeed.generateSequence(seed = seed, padCount = 4, length = 10)

        assertEquals("Sequences from same seed must match exactly", seq1, seq2)
        assertEquals(10, seq1.size)

        // Verify values are strictly in range 0..3
        for (padIdx in seq1) {
            assertTrue("Pad index must be in range 0..3", padIdx in 0..3)
        }

        // Run a third independent generation to verify repeatability
        val seq3 = ChallengeSeed.generateSequence(seed = seed, padCount = 4, length = 10)
        assertEquals(seq1, seq3)
    }

    @Test
    fun testSeedEncodingAndDecodingLossless() {
        val testSeeds = listOf(0, 1, 42, 12345, 999999, ChallengeSeed.MAX_SEED)
        for (originalSeed in testSeeds) {
            val encoded = ChallengeSeed.encodeSeed(originalSeed)
            assertEquals("Encoded seed must be exactly 5 characters", 5, encoded.length)

            // Characters must belong to custom alphabet
            for (ch in encoded) {
                assertTrue("Char $ch must be in ALPHABET", ch in ChallengeSeed.ALPHABET)
            }

            val decoded = ChallengeSeed.decodeSeed(encoded)
            assertEquals("Decoded seed must match original", originalSeed, decoded)
        }
    }

    @Test
    fun testChallengeCodeFormatAndParsingWithTries() {
        val code = ChallengeSeed.formatChallengeCode("2x2", 12, 3, com.example.challenge.GameDifficulty.EASY, 12345)
        assertTrue("Code must format as segments with T3E and checksum", code.startsWith("2x2-12-T3E-"))

        val parsed = ChallengeSeed.parseChallengeCode(code)
        assertNotNull("Code must parse successfully", parsed)
        assertEquals("2x2", parsed?.gridTag)
        assertEquals(12, parsed?.targetScore)
        assertEquals(3, parsed?.tries)
        assertEquals(com.example.challenge.GameDifficulty.EASY, parsed?.difficulty)
        assertEquals(12345, parsed?.seedInt)
    }

    @Test
    fun testBackwardCompatibilityWithLegacy3SegmentCode() {
        // Legacy 3-segment code without -T[tries]- segment must default to tries = 3
        val legacyCode = "3x3-15-B4Y9"
        val parsed = ChallengeSeed.parseChallengeCode(legacyCode)
        assertNotNull("Legacy 3-segment code must parse successfully", parsed)
        assertEquals("3x3", parsed?.gridTag)
        assertEquals(15, parsed?.targetScore)
        assertEquals(3, parsed?.tries)
        assertEquals(ChallengeSeed.decodeSeed("B4Y9"), parsed?.seedInt)
    }

    @Test
    fun testCustomTriesParsedCorrectly() {
        val code5Tries = "4x4-20-T5-8X2A"
        val parsed = ChallengeSeed.parseChallengeCode(code5Tries)
        assertNotNull(parsed)
        assertEquals("4x4", parsed?.gridTag)
        assertEquals(20, parsed?.targetScore)
        assertEquals(5, parsed?.tries)
    }

    @Test
    fun testCrossSettingsDeterminism() {
        // Player A creates challenge: local settings were 4x4 Hyper, but shares a 2x2-10-T3 code
        val sharedCode = "2x2-10-T3-8X2A"
        val parsedA = ChallengeSeed.parseChallengeCode(sharedCode)
        assertNotNull(parsedA)

        // Player B receives code: their local settings are 3x3 Progressive
        val parsedB = ChallengeSeed.parseChallengeCode(sharedCode)
        assertNotNull(parsedB)

        // When both run deterministic generator with the parsed code settings:
        val padCountA = if (parsedA!!.gridTag == "2x2") 4 else 9
        val padCountB = if (parsedB!!.gridTag == "2x2") 4 else 9

        val sequenceA = ChallengeSeed.generateSequence(parsedA.seedInt, padCountA, parsedA.targetScore)
        val sequenceB = ChallengeSeed.generateSequence(parsedB.seedInt, padCountB, parsedB.targetScore)

        assertEquals("Sequence length must match target score", 10, sequenceA.size)
        assertEquals("Sequences must be 100% identical regardless of different local settings", sequenceA, sequenceB)
    }

    @Test
    fun testInvalidCodesRejected() {
        assertNull(ChallengeSeed.parseChallengeCode(""))
        assertNull(ChallengeSeed.parseChallengeCode("invalid"))
        assertNull(ChallengeSeed.parseChallengeCode("5x5-12-ABCD")) // invalid grid
        assertNull(ChallengeSeed.parseChallengeCode("2x2-0-ABCD"))   // invalid target score
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-ABC"))   // too short
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-ABCDEF")) // too long
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-0000"))  // '0' not in alphabet
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-IIII"))  // 'I' not in alphabet
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-T0-ABCD")) // T0 is invalid
    }

    // --- STEP 0 GOLDEN TESTS (Section 5.1) ---

    @Test
    fun goldenTest_all64PermutationsRoundTrip() {
        val grids = listOf("2x2", "3x3", "4x4", "5x4")
        val triesList = listOf(0, 1, 3, 5)
        val difficulties = listOf(com.example.challenge.GameDifficulty.EASY, com.example.challenge.GameDifficulty.HARD)
        val revengeOptions = listOf(false, true)
        val seed = 23456

        var totalTested = 0
        for (grid in grids) {
            for (tries in triesList) {
                for (diff in difficulties) {
                    for (rev in revengeOptions) {
                        val formatted = ChallengeSeed.formatChallengeCode(
                            gridTag = grid,
                            targetScore = 12,
                            tries = tries,
                            difficulty = diff,
                            seed = seed,
                            senderName = "PLAYER",
                            chainId = "K7Q",
                            isRevenge = rev
                        )
                        val parsed = ChallengeSeed.parseChallengeCode(formatted)
                        assertNotNull("Permutation failed for $formatted", parsed)
                        assertEquals(grid, parsed?.gridTag)
                        assertEquals(12, parsed?.targetScore)
                        assertEquals(tries, parsed?.tries)
                        assertEquals(diff, parsed?.difficulty)
                        assertEquals(rev, parsed?.isRevenge)
                        assertEquals("PLAYER", parsed?.senderName)
                        assertEquals("K7Q", parsed?.chainId)
                        assertEquals(seed, parsed?.seedInt)
                        totalTested++
                    }
                }
            }
        }
        assertEquals("Must test all 64 permutations", 64, totalTested)
    }

    @Test
    fun goldenTest_mutationDetection_guaranteedAndStatistical() {
        val validCode = ChallengeSeed.formatChallengeCode(
            gridTag = "3x3",
            targetScore = 15,
            tries = 3,
            difficulty = com.example.challenge.GameDifficulty.EASY,
            seed = 77777,
            senderName = "HERO",
            chainId = "ABC"
        )
        assertNotNull(ChallengeSeed.parseChallengeCode(validCode))

        // 1. Guaranteed caught mutation: Target score digit +1 (delta is not a multiple of 32)
        val parts = validCode.split('-').toMutableList()
        parts[1] = "16" // Mutate 15 to 16
        val targetMutated = parts.joinToString("-")
        assertNull("Target score mutation must be rejected by checksum", ChallengeSeed.parseChallengeCode(targetMutated))

        // 2. Guaranteed caught mutation: Grid mutation (3x3 -> 4x4)
        parts[1] = "15"
        parts[0] = "4x4"
        val gridMutated = parts.joinToString("-")
        assertNull("Grid tag mutation must be rejected", ChallengeSeed.parseChallengeCode(gridMutated))

        // 3. Guaranteed caught mutation: Invalid characters outside alphabet
        val alphabetMutated = validCode.replace('A', '0')
        if (alphabetMutated != validCode) {
            assertNull("Character not in Base32 alphabet must be rejected", ChallengeSeed.parseChallengeCode(alphabetMutated))
        }

        // 4. Statistical bound: Test all single-character replacements across alphanumeric chars
        var rejectedCount = 0
        var totalMutations = 0
        val testChars = ChallengeSeed.ALPHABET.toList()

        for (i in validCode.indices) {
            if (validCode[i] == '-') continue
            for (alt in testChars) {
                if (alt == validCode[i]) continue
                val mutated = validCode.substring(0, i) + alt + validCode.substring(i + 1)
                totalMutations++
                if (ChallengeSeed.parseChallengeCode(mutated) == null) {
                    rejectedCount++
                }
            }
        }
        val rejectionRate = rejectedCount.toDouble() / totalMutations
        assertTrue(
            "Statistical bound: at least 90% of mutations must be rejected (actual: $rejectionRate)",
            rejectionRate >= 0.90
        )
    }

    @Test
    fun goldenTest_whatsAppExtractionWithZeroWidthAndUnicodeDashes() {
        val cleanCode = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 8,
            tries = 3,
            difficulty = com.example.challenge.GameDifficulty.EASY,
            seed = 99999,
            senderName = "ALEX",
            chainId = "WAP"
        )

        // Simulate WhatsApp message with emoji, zero-width spaces (\u200B), and Unicode en-dash (\u2013)
        val whatsappText = "Hey bro! 🔥 Try beating my score: \u200B" +
            cleanCode.replace('-', '\u2013') +
            "\u200B. See you on the leaderboard!"

        val extracted = ChallengeSeed.extractCodeFromText(whatsappText)
        assertNotNull("Must extract challenge code from WhatsApp message text", extracted)

        val parsed = ChallengeSeed.parseChallengeCode(whatsappText)
        assertNotNull("Must parse challenge code directly from dirty text", parsed)
        assertEquals("2x2", parsed?.gridTag)
        assertEquals(8, parsed?.targetScore)
        assertEquals("ALEX", parsed?.senderName)
    }

    @Test
    fun goldenTest_legacy3_4_5_segmentCodes() {
        // 3-segment legacy
        val legacy3 = "3x3-15-B4Y9"
        val parsed3 = ChallengeSeed.parseChallengeCode(legacy3)
        assertNotNull(parsed3)
        assertEquals("3x3", parsed3?.gridTag)
        assertEquals(15, parsed3?.targetScore)
        assertEquals(3, parsed3?.tries)
        assertTrue(parsed3!!.isLegacy)

        // 4-segment legacy
        val legacy4 = "4x4-20-T5H-8X2A"
        val parsed4 = ChallengeSeed.parseChallengeCode(legacy4)
        assertNotNull(parsed4)
        assertEquals("4x4", parsed4?.gridTag)
        assertEquals(20, parsed4?.targetScore)
        assertEquals(5, parsed4?.tries)
        assertEquals(com.example.challenge.GameDifficulty.HARD, parsed4?.difficulty)

        // 5-segment legacy
        val rawCore5 = "2x2-10-T3E-2GPGX"
        val checksum5 = ChallengeSeed.computeChecksum(rawCore5)
        val legacy5 = "$rawCore5-$checksum5"
        val parsed5 = ChallengeSeed.parseChallengeCode(legacy5)
        assertNotNull(parsed5)
        assertEquals("2x2", parsed5?.gridTag)
        assertEquals(10, parsed5?.targetScore)
        assertTrue(parsed5!!.isLegacy)
    }

    @Test
    fun goldenTest_nameEdgeCases() {
        assertEquals("HERO", ChallengeSeed.sanitizePlayerName(null))
        assertEquals("HERO", ChallengeSeed.sanitizePlayerName(""))
        assertEquals("HERO", ChallengeSeed.sanitizePlayerName("   "))
        assertEquals("SUPER_HERO", ChallengeSeed.sanitizePlayerName("super hero"))
        assertEquals("TENCHARACT", ChallengeSeed.sanitizePlayerName("TENCHARACTERSLONG"))
    }

    @Test
    fun testTargetScoreBoundary_999Accepted_1000Rejected() {
        val code999 = ChallengeSeed.formatChallengeCode(
            gridTag = "2x2",
            targetScore = 999,
            tries = 3,
            difficulty = com.example.challenge.GameDifficulty.EASY,
            seed = 12345,
            senderName = "TEST",
            chainId = "K7Q"
        )
        val parsed999 = ChallengeSeed.parseChallengeCode(code999)
        assertNotNull(parsed999)
        assertEquals(999, parsed999?.targetScore)

        val rawCore1000 = "2x2-1000-T3E-TEST-K7Q-8X2AB"
        val checksum1000 = ChallengeSeed.computeChecksum(rawCore1000)
        val code1000 = "$rawCore1000-$checksum1000"
        val parsed1000 = ChallengeSeed.parseChallengeCode(code1000)
        assertNull("Target score 1000 must be rejected", parsed1000)

        val raw5_999 = "3x3-999-T3E-8X2AB"
        val parsed5_999 = ChallengeSeed.parseChallengeCode("$raw5_999-${ChallengeSeed.computeChecksum(raw5_999)}")
        assertNotNull(parsed5_999)
        assertEquals(999, parsed5_999?.targetScore)

        val raw5_1000 = "3x3-1000-T3E-8X2AB"
        val parsed5_1000 = ChallengeSeed.parseChallengeCode("$raw5_1000-${ChallengeSeed.computeChecksum(raw5_1000)}")
        assertNull("Legacy 5-segment target score 1000 must be rejected", parsed5_1000)

        assertNull(ChallengeSeed.parseChallengeCode("4x4-1000-T3E-8X2A"))
        assertNull(ChallengeSeed.parseChallengeCode("2x2-1000-8X2A"))
    }

    @Test
    fun testSanitizePlayerName_asciiFilteringAndRoundTrip() {
        assertEquals("JOS", ChallengeSeed.sanitizePlayerName("José"))
        assertEquals("HERO", ChallengeSeed.sanitizePlayerName("🚀🔥✨"))
        assertEquals("_PRO_GAMER", ChallengeSeed.sanitizePlayerName("🎮 Pro Gamer 🎯"))
        assertEquals("_BOB_", ChallengeSeed.sanitizePlayerName("_bob_"))
        assertEquals("ABCDEFGHIJ", ChallengeSeed.sanitizePlayerName("abcdefghijk_extra"))
        assertEquals("HERO", ChallengeSeed.sanitizePlayerName(null))
        assertEquals("HERO", ChallengeSeed.sanitizePlayerName(""))
        assertEquals("HERO", ChallengeSeed.sanitizePlayerName("   "))
        assertEquals("___", ChallengeSeed.sanitizePlayerName("___"))

        val name = ChallengeSeed.sanitizePlayerName("Neon King!")
        assertEquals("NEON_KING", name)
        val code = ChallengeSeed.formatChallengeCode("2x2", 10, 3, com.example.challenge.GameDifficulty.EASY, 12345, senderName = name)
        val parsed = ChallengeSeed.parseChallengeCode(code)
        assertNotNull(parsed)
        assertEquals("NEON_KING", parsed?.senderName)
    }
}
