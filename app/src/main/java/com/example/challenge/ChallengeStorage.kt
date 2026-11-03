package com.example.challenge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline persistent storage for Glowback challenges, rivalries, and awaiting shares
 * via SharedPreferences and JSON.
 */
object ChallengeStorage {
    private const val PREFS_NAME = "glowback_challenge_prefs"
    private const val KEY_CHALLENGES = "active_challenges_json"
    private const val KEY_RIVALS = "glowback_rivalries_v1"
    private const val KEY_AWAITING = "glowback_awaiting_challenges_v1"
    private const val KEY_MY_CODES = "my_created_challenge_codes"
    private const val KEY_MY_NAME = "glowback_player_name"
    private const val KEY_PENDING_SHARE = "pending_share_challenge_json"

    private const val AWAITING_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    // ==========================================
    // PLAYER NAME ("My Name")
    // ==========================================

    fun getMyName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_MY_NAME, "HERO") ?: "HERO"
        return ChallengeSeed.sanitizePlayerName(name)
    }

    fun hasConfiguredName(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_MY_NAME) && !prefs.getString(KEY_MY_NAME, "").isNullOrBlank()
    }

    fun setMyName(context: Context, rawName: String) {
        val sanitized = ChallengeSeed.sanitizePlayerName(rawName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MY_NAME, sanitized)
            .apply()
    }

    // ==========================================
    // PENDING SHARE (Process Death Survival)
    // ==========================================

    fun savePendingShare(context: Context, awaiting: AwaitingChallenge) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("chainId", awaiting.chainId)
            put("code", awaiting.code)
            put("gridSizeTag", awaiting.gridSizeTag)
            put("targetScore", awaiting.targetScore)
            put("difficulty", awaiting.difficulty.name)
            put("paceName", awaiting.paceName)
            put("tries", awaiting.tries)
            put("createdAt", awaiting.createdAt)
        }
        prefs.edit().putString(KEY_PENDING_SHARE, obj.toString()).apply()
    }

    fun getPendingShare(context: Context): AwaitingChallenge? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_SHARE, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val diffName = obj.optString("difficulty", "EASY")
            val difficulty = try { GameDifficulty.valueOf(diffName) } catch (_: Exception) { GameDifficulty.EASY }
            AwaitingChallenge(
                chainId = obj.getString("chainId"),
                code = obj.getString("code"),
                gridSizeTag = obj.optString("gridSizeTag", "2x2"),
                targetScore = obj.getInt("targetScore"),
                difficulty = difficulty,
                paceName = obj.optString("paceName", "Classic"),
                tries = obj.optInt("tries", 3),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearPendingShare(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_SHARE)
            .apply()
    }

    // ==========================================
    // ACTIVE CHALLENGES (Incoming)
    // ==========================================

    fun loadChallenges(context: Context): List<ChallengeItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_CHALLENGES, null) ?: return emptyList()
        val list = mutableListOf<ChallengeItem>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val maxTries = obj.optInt("maxTries", 3)
                val diffName = obj.optString("difficulty", "EASY")
                val difficulty = try { GameDifficulty.valueOf(diffName) } catch (_: Exception) { GameDifficulty.EASY }
                list.add(
                    ChallengeItem(
                        code = obj.getString("code"),
                        rivalName = obj.optString("rivalName", "Anonymous"),
                        gridSizeTag = obj.optString("gridSizeTag", "2x2"),
                        targetScore = obj.getInt("targetScore"),
                        maxTries = maxTries,
                        attemptsLeft = obj.optInt("attemptsLeft", maxTries),
                        bestScore = obj.optInt("bestScore", 0),
                        seed = obj.getInt("seed"),
                        difficulty = difficulty,
                        chainId = obj.optString("chainId", ""),
                        isRevenge = obj.optBoolean("isRevenge", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt data fallback
        }
        return list
    }

    private fun persistChallenges(context: Context, list: List<ChallengeItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("code", item.code)
                put("rivalName", item.rivalName)
                put("gridSizeTag", item.gridSizeTag)
                put("targetScore", item.targetScore)
                put("maxTries", item.maxTries)
                put("attemptsLeft", item.attemptsLeft)
                put("bestScore", item.bestScore)
                put("seed", item.seed)
                put("difficulty", item.difficulty.name)
                put("chainId", item.chainId)
                put("isRevenge", item.isRevenge)
                put("createdAt", item.createdAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CHALLENGES, jsonArray.toString()).apply()
    }

    fun saveChallenge(context: Context, item: ChallengeItem) {
        val existing = loadChallenges(context).toMutableList()
        val index = existing.indexOfFirst { it.code.equals(item.code, ignoreCase = true) }
        if (index >= 0) {
            existing[index] = item
        } else {
            existing.add(0, item) // newest first
        }
        persistChallenges(context, existing)
    }

    fun updateChallenge(context: Context, item: ChallengeItem) {
        val existing = loadChallenges(context).toMutableList()
        val index = existing.indexOfFirst { it.code.equals(item.code, ignoreCase = true) }
        if (index >= 0) {
            existing[index] = item
            persistChallenges(context, existing)
        }
    }

    fun deleteChallenge(context: Context, code: String) {
        val existing = loadChallenges(context).toMutableList()
        val removed = existing.removeAll { it.code.equals(code, ignoreCase = true) }
        if (removed) {
            persistChallenges(context, existing)
        }
    }

    // ==========================================
    // RIVALRIES & HEAD-TO-HEAD HISTORY
    // ==========================================

    fun loadRivals(context: Context): List<RivalRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_RIVALS, null) ?: return emptyList()
        val list = mutableListOf<RivalRecord>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val diffName = obj.optString("lastDifficulty", "EASY")
                val difficulty = try { GameDifficulty.valueOf(diffName) } catch (_: Exception) { GameDifficulty.EASY }
                list.add(
                    RivalRecord(
                        name = obj.getString("name"),
                        wins = obj.optInt("wins", 0),
                        losses = obj.optInt("losses", 0),
                        ties = obj.optInt("ties", 0),
                        matches = obj.optInt("matches", 0),
                        lastGrid = obj.optString("lastGrid", "2x2"),
                        lastDifficulty = difficulty,
                        lastTarget = obj.optInt("lastTarget", 0),
                        lastUserScore = obj.optInt("lastUserScore", 0),
                        lastStatus = obj.optString("lastStatus", "WON"),
                        lastChainId = obj.optString("lastChainId", ""),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt data fallback
        }
        return list.sortedByDescending { it.updatedAt }
    }

    private fun persistRivals(context: Context, list: List<RivalRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("name", item.name)
                put("wins", item.wins)
                put("losses", item.losses)
                put("ties", item.ties)
                put("matches", item.matches)
                put("lastGrid", item.lastGrid)
                put("lastDifficulty", item.lastDifficulty.name)
                put("lastTarget", item.lastTarget)
                put("lastUserScore", item.lastUserScore)
                put("lastStatus", item.lastStatus)
                put("lastChainId", item.lastChainId)
                put("updatedAt", item.updatedAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_RIVALS, jsonArray.toString()).apply()
    }

    fun saveRival(context: Context, record: RivalRecord) {
        val existing = loadRivals(context).toMutableList()
        val index = existing.indexOfFirst { it.name.equals(record.name, ignoreCase = true) }
        if (index >= 0) {
            existing[index] = record
        } else {
            existing.add(0, record)
        }
        persistRivals(context, existing)
    }

    fun deleteRival(context: Context, name: String) {
        val existing = loadRivals(context).toMutableList()
        val removed = existing.removeAll { it.name.equals(name, ignoreCase = true) }
        if (removed) {
            persistRivals(context, existing)
        }
    }

    fun findRivalByName(context: Context, name: String): RivalRecord? {
        return loadRivals(context).firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun recordMatchOutcome(
        context: Context,
        rivalName: String,
        userScore: Int,
        targetScore: Int,
        gridSize: String,
        difficulty: GameDifficulty,
        chainId: String,
        maxTries: Int = 3
    ): RivalRecord? {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        val existing = findRivalByName(context, cleanName)

        val isWon = userScore > targetScore
        val isTie = userScore == targetScore
        val isLoss = userScore < targetScore

        // Confirmation 3: With Free tries (maxTries == 0), a Loss is impossible.
        // Beat target = Win, exact match = Tie, quit without beating = no record (match stays open/pending).
        // Losses are only recorded when 1/3/5 tries are exhausted.
        if (maxTries == 0 && isLoss) {
            return null
        }

        val status = when {
            isWon -> "WON"
            isTie -> "TIED"
            else -> "LOST"
        }

        val updatedWins = (existing?.wins ?: 0) + (if (isWon) 1 else 0)
        val updatedLosses = (existing?.losses ?: 0) + (if (isLoss) 1 else 0)
        val updatedTies = (existing?.ties ?: 0) + (if (isTie) 1 else 0)
        val updatedMatches = (existing?.matches ?: 0) + 1

        val record = RivalRecord(
            name = cleanName,
            wins = updatedWins,
            losses = updatedLosses,
            ties = updatedTies,
            matches = updatedMatches,
            lastGrid = gridSize,
            lastDifficulty = difficulty,
            lastTarget = targetScore,
            lastUserScore = userScore,
            lastStatus = status,
            lastChainId = chainId.ifEmpty { existing?.lastChainId ?: ChallengeSeed.generateChainId() },
            updatedAt = System.currentTimeMillis()
        )

        saveRival(context, record)
        return record
    }

    // ==========================================
    // AWAITING CHALLENGES (Outbound with 7-day TTL)
    // ==========================================

    fun loadAwaitingChallenges(context: Context): List<AwaitingChallenge> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_AWAITING, null) ?: return emptyList()
        val list = mutableListOf<AwaitingChallenge>()
        val now = System.currentTimeMillis()
        var hadExpired = false

        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val createdAt = obj.optLong("createdAt", now)
                // 7-day TTL expiration check
                if (now - createdAt > AWAITING_TTL_MS) {
                    hadExpired = true
                    continue
                }
                val diffName = obj.optString("difficulty", "EASY")
                val difficulty = try { GameDifficulty.valueOf(diffName) } catch (_: Exception) { GameDifficulty.EASY }
                list.add(
                    AwaitingChallenge(
                        chainId = obj.getString("chainId"),
                        code = obj.getString("code"),
                        gridSizeTag = obj.optString("gridSizeTag", "2x2"),
                        targetScore = obj.getInt("targetScore"),
                        difficulty = difficulty,
                        paceName = obj.optString("paceName", "Classic"),
                        tries = obj.optInt("tries", 3),
                        createdAt = createdAt
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt data fallback
        }

        if (hadExpired) {
            persistAwaitingChallenges(context, list)
        }

        return list.sortedByDescending { it.createdAt }
    }

    private fun persistAwaitingChallenges(context: Context, list: List<AwaitingChallenge>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("chainId", item.chainId)
                put("code", item.code)
                put("gridSizeTag", item.gridSizeTag)
                put("targetScore", item.targetScore)
                put("difficulty", item.difficulty.name)
                put("paceName", item.paceName)
                put("tries", item.tries)
                put("createdAt", item.createdAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_AWAITING, jsonArray.toString()).apply()
    }

    fun saveAwaitingChallenge(context: Context, awaiting: AwaitingChallenge) {
        val existing = loadAwaitingChallenges(context).toMutableList()
        val index = existing.indexOfFirst { it.chainId.equals(awaiting.chainId, ignoreCase = true) }
        if (index >= 0) {
            existing[index] = awaiting
        } else {
            existing.add(0, awaiting)
        }
        persistAwaitingChallenges(context, existing)
    }

    fun deleteAwaitingChallenge(context: Context, chainId: String) {
        val existing = loadAwaitingChallenges(context).toMutableList()
        val removed = existing.removeAll { it.chainId.equals(chainId, ignoreCase = true) }
        if (removed) {
            persistAwaitingChallenges(context, existing)
        }
    }

    fun findAwaitingByChain(context: Context, chainId: String): AwaitingChallenge? {
        if (chainId.isBlank()) return null
        return loadAwaitingChallenges(context).firstOrNull { it.chainId.equals(chainId, ignoreCase = true) }
    }

    // ==========================================
    // SELF-CHALLENGE PREVENTIONS
    // ==========================================

    fun recordCreatedCode(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_MY_CODES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val clean = code.trim().uppercase()
        current.add(clean)
        val parsed = ChallengeSeed.parseChallengeCode(clean)
        if (parsed != null) {
            current.add(parsed.fullCode.uppercase())
        }
        prefs.edit().putStringSet(KEY_MY_CODES, current).apply()
    }

    fun isSelfChallenge(context: Context, code: String): Boolean {
        val clean = code.trim().uppercase()
        val parsed = ChallengeSeed.parseChallengeCode(clean)

        // Rule 1: Check against local "My Name"
        val myName = getMyName(context)
        if (parsed != null && parsed.senderName.isNotEmpty() && parsed.senderName.equals(myName, ignoreCase = true)) {
            return true
        }

        // Rule 2: Check against locally generated code hashes
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_MY_CODES, emptySet()) ?: emptySet()
        if (current.any { it.equals(clean, ignoreCase = true) }) return true

        if (parsed != null) {
            if (current.any { it.equals(parsed.fullCode, ignoreCase = true) }) return true
            for (stored in current) {
                val storedParsed = ChallengeSeed.parseChallengeCode(stored)
                if (storedParsed != null &&
                    storedParsed.seedInt == parsed.seedInt &&
                    storedParsed.targetScore == parsed.targetScore &&
                    storedParsed.gridTag.equals(parsed.gridTag, ignoreCase = true)
                ) {
                    return true
                }
            }
        }
        return false
    }
}
