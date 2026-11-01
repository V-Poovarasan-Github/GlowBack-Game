package com.example.challenge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline persistent storage for Glowback challenges via SharedPreferences and JSON.
 */
object ChallengeStorage {
    private const val PREFS_NAME = "glowback_challenge_prefs"
    private const val KEY_CHALLENGES = "active_challenges_json"

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
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt data fallback
        }
        return list
    }

    private fun persistList(context: Context, list: List<ChallengeItem>) {
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
        persistList(context, existing)
    }

    fun updateChallenge(context: Context, item: ChallengeItem) {
        val existing = loadChallenges(context).toMutableList()
        val index = existing.indexOfFirst { it.code.equals(item.code, ignoreCase = true) }
        if (index >= 0) {
            existing[index] = item
            persistList(context, existing)
        }
    }

    fun deleteChallenge(context: Context, code: String) {
        val existing = loadChallenges(context).toMutableList()
        val removed = existing.removeAll { it.code.equals(code, ignoreCase = true) }
        if (removed) {
            persistList(context, existing)
        }
    }

    private const val KEY_MY_CODES = "my_created_challenge_codes"

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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_MY_CODES, emptySet()) ?: emptySet()
        val clean = code.trim().uppercase()
        if (current.any { it.equals(clean, ignoreCase = true) }) return true

        val parsed = ChallengeSeed.parseChallengeCode(clean)
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
