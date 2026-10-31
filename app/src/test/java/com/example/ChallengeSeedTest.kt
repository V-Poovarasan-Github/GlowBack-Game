package com.example

import com.example.challenge.ChallengeItem
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
            assertEquals("Encoded seed must be exactly 4 characters", 4, encoded.length)

            // Characters must belong to custom alphabet
            for (ch in encoded) {
                assertTrue("Char $ch must be in ALPHABET", ch in ChallengeSeed.ALPHABET)
            }

            val decoded = ChallengeSeed.decodeSeed(encoded)
            assertEquals("Decoded seed must match original", originalSeed, decoded)
        }
    }

    @Test
    fun testChallengeCodeFormatAndParsing() {
        val code = ChallengeSeed.formatChallengeCode("2x2", 12, 12345)
        assertTrue("Code must start with grid", code.startsWith("2x2-12-"))

        val parsed = ChallengeSeed.parseChallengeCode(code)
        assertNotNull("Code must parse successfully", parsed)
        assertEquals("2x2", parsed?.gridTag)
        assertEquals(12, parsed?.targetScore)
        assertEquals(12345, parsed?.seedInt)
    }

    @Test
    fun testInvalidCodesRejected() {
        assertNull(ChallengeSeed.parseChallengeCode(""))
        assertNull(ChallengeSeed.parseChallengeCode("invalid"))
        assertNull(ChallengeSeed.parseChallengeCode("5x5-12-ABCD")) // invalid grid
        assertNull(ChallengeSeed.parseChallengeCode("2x2-0-ABCD"))   // invalid target score
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-ABC"))   // too short
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-ABCDE")) // too long
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-0000"))  // '0' not in alphabet
        assertNull(ChallengeSeed.parseChallengeCode("2x2-12-IIII"))  // 'I' not in alphabet
    }
}
