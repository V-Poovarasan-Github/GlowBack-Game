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
}
