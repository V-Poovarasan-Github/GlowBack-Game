package com.example.challenge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encapsulates the complete game run state for saving and resuming runs across
 * app exits, backgrounding, or manual menu returns.
 */
data class SerializedRunState(
    val modeName: String,           // "SOLO", "TWO_PLAYERS", "THREE_PLAYERS", "FOUR_PLAYERS"
    val gridDimension: Int,         // 2, 3, 4
    val paceSuffix: String,         // "classic" or "progressive"
    val sequencePadIds: List<Int>,  // e.g. [0, 2, 1, 3]
    val round: Int,                 // sequence length
    val activePlayerIndex: Int,     // 0..3
    val alivePlayers: List<Boolean>,
    val roundsSurvived: List<Int>,
    val activeChallengeCode: String? = null,
    val challengeTriesLeft: Int? = null,
    val challengeBestScore: Int? = null,
    val soloRunSeed: Int? = null,
    val difficultyMode: String = "EASY",
    val timestamp: Long = System.currentTimeMillis()
) {
    val mode: String get() = modeName
}

object RunStateStorage {
    private const val PREFS_NAME = "glowback_runstate_prefs"
    private const val KEY_ACTIVE_RUN = "active_run_json"

    fun saveRunState(context: Context, state: SerializedRunState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("modeName", state.modeName)
            put("gridDimension", state.gridDimension)
            put("paceSuffix", state.paceSuffix)
            put("sequencePadIds", JSONArray(state.sequencePadIds))
            put("round", state.round)
            put("activePlayerIndex", state.activePlayerIndex)
            put("alivePlayers", JSONArray(state.alivePlayers))
            put("roundsSurvived", JSONArray(state.roundsSurvived))
            put("activeChallengeCode", state.activeChallengeCode ?: "")
            put("challengeTriesLeft", state.challengeTriesLeft ?: -1)
            put("challengeBestScore", state.challengeBestScore ?: 0)
            put("soloRunSeed", state.soloRunSeed ?: -1)
            put("difficultyMode", state.difficultyMode)
            put("timestamp", state.timestamp)
        }
        prefs.edit().putString(KEY_ACTIVE_RUN, obj.toString()).apply()
    }

    fun loadRunState(context: Context): SerializedRunState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ACTIVE_RUN, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val seqArray = obj.getJSONArray("sequencePadIds")
            val seqList = (0 until seqArray.length()).map { seqArray.getInt(it) }
            val aliveArray = obj.getJSONArray("alivePlayers")
            val aliveList = (0 until aliveArray.length()).map { aliveArray.getBoolean(it) }
            val roundsArray = obj.getJSONArray("roundsSurvived")
            val roundsList = (0 until roundsArray.length()).map { roundsArray.getInt(it) }
            val chCode = obj.optString("activeChallengeCode").takeIf { it.isNotBlank() }
            val chTries = obj.optInt("challengeTriesLeft", -1).takeIf { it >= 0 }
            val chBest = obj.optInt("challengeBestScore", 0)
            val soloSeed = obj.optInt("soloRunSeed", -1).takeIf { it >= 0 }
            val diffMode = obj.optString("difficultyMode", "EASY")

            SerializedRunState(
                modeName = obj.getString("modeName"),
                gridDimension = obj.getInt("gridDimension"),
                paceSuffix = obj.getString("paceSuffix"),
                sequencePadIds = seqList,
                round = obj.getInt("round"),
                activePlayerIndex = obj.getInt("activePlayerIndex"),
                alivePlayers = aliveList,
                roundsSurvived = roundsList,
                activeChallengeCode = chCode,
                challengeTriesLeft = chTries,
                challengeBestScore = chBest,
                soloRunSeed = soloSeed,
                difficultyMode = diffMode,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearRunState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ACTIVE_RUN).apply()
    }
}
