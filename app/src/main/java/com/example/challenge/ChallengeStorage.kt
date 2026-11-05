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
    private const val KEY_MY_CODES_FIFO = "my_created_challenge_codes_fifo_v1"
    private const val KEY_CONSUMED_CODES = "consumed_challenge_codes_v1"
    private const val KEY_CONSUMED_CODES_FIFO = "consumed_challenge_codes_fifo_v1"
    private const val KEY_CHAIN_TARGETS = "outbound_chain_targets_v1"
    private const val KEY_LAST_SHARED_RIVAL = "last_shared_rival_name"
    private const val KEY_MY_NAME = "glowback_player_name"
    private const val KEY_PENDING_SHARE = "pending_share_challenge_json"

    const val AWAITING_TTL_MS = 3 * 24 * 60 * 60 * 1000L // 3 days (72 hours) UI nudge TTL
    const val CHAIN_TARGET_TTL_MS = 30L * 24 * 60 * 60 * 1000L // 30 days hard expiry for outbound chain targets
    const val MAX_CHAIN_TARGETS_CAP = 100
    const val MAX_RIVALS_CAP = 50
    const val MAX_CODES_CAP = 200
    const val MAX_CONSUMED_CODES_CAP = 500

    enum class ChainLookupStatus {
        VALID,
        EXPIRED,
        NOT_FOUND
    }

    data class ChainLookupResult(
        val status: ChainLookupStatus,
        val targetScore: Int = 0,
        val repliedRivals: List<String> = emptyList()
    )

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
    // SMART SHARE OPTIMIZATION (lastSharedRivalName)
    // ==========================================

    fun getLastSharedRivalName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LAST_SHARED_RIVAL, null)?.trim()?.uppercase()
        return if (raw.isNullOrEmpty()) null else raw
    }

    fun setLastSharedRivalName(context: Context, rivalName: String?) {
        val cleaned = rivalName?.trim()?.uppercase() ?: ""
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SHARED_RIVAL, cleaned)
            .apply()
    }

    /**
     * Always share full PNG trophy card + 7-segment code caption on ALL challenge shares
     * to provide visual proof of the target score and prevent text-editing forgery.
     */
    fun shouldShareTextOnly(context: Context, currentRivalName: String?): Boolean {
        return false
    }

    // ==========================================
    // OUTBOUND CHAIN -> TARGET SCORE MAP (30d Hard Expiry, 100 FIFO, chainId:seedInt Sub-Chain Forking)
    // ==========================================

    private fun buildChainTargetKey(chainId: String, seedInt: Int): String {
        val cleanChain = chainId.trim().uppercase()
        return if (seedInt != 0) "$cleanChain:$seedInt" else cleanChain
    }

    private fun resolveChainEntryKey(mapObj: JSONObject, cleanChain: String, seedInt: Int): String? {
        if (cleanChain.isEmpty()) return null
        if (seedInt != 0) {
            val compositeKey = "$cleanChain:$seedInt"
            if (mapObj.has(compositeKey)) return compositeKey
        }
        if (mapObj.has(cleanChain)) return cleanChain
        // Fallback: if caller did not specify seedInt (e.g. seedInt == 0), locate newest sub-chain entry for cleanChain
        val prefix = "$cleanChain:"
        var bestKey: String? = null
        var bestTime = Long.MIN_VALUE
        val keys = mapObj.keys().asSequence().toList()
        for (k in keys) {
            if (k.startsWith(prefix, ignoreCase = true)) {
                val t = mapObj.optJSONObject(k)?.optLong("createdAt", 0L) ?: 0L
                if (t >= bestTime) {
                    bestTime = t
                    bestKey = k
                }
            }
        }
        return bestKey
    }

    /**
     * PASS 5.19 Stage B #3 & Mandatory Sub-Chain Forking:
     * Saves a 30-day background mapping of (chainId:seedInt) -> targetScore (capped at 100 entries, FIFO)
     * whenever a T-code is generated. Supports 1-to-many group broadcasts and simultaneous per-rival revenge rallies.
     */
    fun saveOutboundChainTarget(
        context: Context,
        chainId: String,
        targetScore: Int,
        createdAt: Long = System.currentTimeMillis(),
        seedInt: Int = 0
    ) {
        val cleanChain = chainId.trim().uppercase()
        if (cleanChain.isEmpty() || targetScore <= 0) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CHAIN_TARGETS, null)
        val mapObj = try {
            if (raw != null) JSONObject(raw) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }

        val entryKey = buildChainTargetKey(cleanChain, seedInt)
        val existingEntry = mapObj.optJSONObject(entryKey)
        val existingReplied = existingEntry?.optJSONArray("repliedRivals") ?: JSONArray()

        val newEntry = JSONObject().apply {
            put("targetScore", targetScore)
            put("createdAt", createdAt)
            put("chainId", cleanChain)
            put("seedInt", seedInt)
            put("repliedRivals", existingReplied)
        }
        mapObj.put(entryKey, newEntry)

        // Enforce 100-entry FIFO cap by createdAt
        val allKeys = mapObj.keys().asSequence().toList()
        if (allKeys.size > MAX_CHAIN_TARGETS_CAP) {
            val sortedByAge = allKeys.sortedBy { k ->
                mapObj.optJSONObject(k)?.optLong("createdAt", 0L) ?: 0L
            }
            val toEvict = sortedByAge.take(allKeys.size - MAX_CHAIN_TARGETS_CAP)
            for (k in toEvict) {
                mapObj.remove(k)
            }
        }

        prefs.edit().putString(KEY_CHAIN_TARGETS, mapObj.toString()).apply()
    }

    /**
     * Inspects the outbound chain target map for (chainId, seedInt), distinguishing between:
     * - VALID (<= 30 days)
     * - EXPIRED (> 30 days -> "This reply has expired.")
     * - NOT_FOUND (never issued by this device -> "This reply is for a challenge you didn't issue.")
     */
    fun inspectOutboundChainTarget(
        context: Context,
        chainId: String,
        seedInt: Int = 0,
        now: Long = System.currentTimeMillis()
    ): ChainLookupResult {
        val cleanChain = chainId.trim().uppercase()
        if (cleanChain.isEmpty()) return ChainLookupResult(ChainLookupStatus.NOT_FOUND)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CHAIN_TARGETS, null) ?: return ChainLookupResult(ChainLookupStatus.NOT_FOUND)
        val mapObj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return ChainLookupResult(ChainLookupStatus.NOT_FOUND)
        }

        val matchedKey = resolveChainEntryKey(mapObj, cleanChain, seedInt)
            ?: return ChainLookupResult(ChainLookupStatus.NOT_FOUND)
        val matched = mapObj.optJSONObject(matchedKey)
            ?: return ChainLookupResult(ChainLookupStatus.NOT_FOUND)

        val entryTime = matched.optLong("createdAt", 0L)
        if (now - entryTime > CHAIN_TARGET_TTL_MS) {
            return ChainLookupResult(ChainLookupStatus.EXPIRED)
        }

        val score = matched.optInt("targetScore", -1)
        if (score <= 0) return ChainLookupResult(ChainLookupStatus.NOT_FOUND)

        val repliedList = mutableListOf<String>()
        val arr = matched.optJSONArray("repliedRivals")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val r = arr.optString(i, "").trim().uppercase()
                if (r.isNotEmpty()) repliedList.add(r)
            }
        }
        return ChainLookupResult(
            status = ChainLookupStatus.VALID,
            targetScore = score,
            repliedRivals = repliedList
        )
    }

    /**
     * Looks up the original targetScore for a given chainId (and optional seedInt) if within the 30-day TTL window.
     */
    fun lookupOutboundChainTarget(
        context: Context,
        chainId: String,
        now: Long = System.currentTimeMillis(),
        seedInt: Int = 0
    ): Int? {
        val res = inspectOutboundChainTarget(context, chainId, seedInt, now)
        return if (res.status == ChainLookupStatus.VALID && res.targetScore > 0) res.targetScore else null
    }

    /**
     * Records that a rival name has replied to a specific outbound (chainId, seedInt) challenge.
     * Used to detect when two group members with the same display name reply to the same challenge.
     */
    fun recordChainReplyRival(
        context: Context,
        chainId: String,
        seedInt: Int,
        rivalName: String
    ) {
        val cleanChain = chainId.trim().uppercase()
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        if (cleanChain.isEmpty() || cleanName.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CHAIN_TARGETS, null) ?: return
        val mapObj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        val matchedKey = resolveChainEntryKey(mapObj, cleanChain, seedInt) ?: return
        val matched = mapObj.optJSONObject(matchedKey) ?: return
        val arr = matched.optJSONArray("repliedRivals") ?: JSONArray()
        var exists = false
        for (i in 0 until arr.length()) {
            if (arr.optString(i, "").equals(cleanName, ignoreCase = true)) {
                exists = true
                break
            }
        }
        if (!exists) {
            arr.put(cleanName)
            matched.put("repliedRivals", arr)
            mapObj.put(matchedKey, matched)
            prefs.edit().putString(KEY_CHAIN_TARGETS, mapObj.toString()).apply()
        }
    }

    /**
     * Returns true if an inbound R-code's senderName matches an existing RivalRecord AND that rival
     * already has a recorded outcome for this exact challenge (chainId + seedInt).
     */
    fun hasSameNameSecondReplyForChallenge(
        context: Context,
        chainId: String,
        seedInt: Int,
        senderName: String
    ): Boolean {
        val cleanName = ChallengeSeed.sanitizePlayerName(senderName)
        val existingRival = findRivalByName(context, cleanName) ?: return false
        val inspect = inspectOutboundChainTarget(context, chainId, seedInt)
        if (inspect.status == ChainLookupStatus.VALID &&
            inspect.repliedRivals.any { it.equals(cleanName, ignoreCase = true) }
        ) {
            return true
        }
        return false
    }

    // ==========================================
    // PENDING SHARE (Process Death Survival)
    // ==========================================

    fun savePendingShare(context: Context, awaiting: AwaitingChallenge) {
        val parsed = ChallengeSeed.parseChallengeCode(awaiting.code)
        saveOutboundChainTarget(
            context = context,
            chainId = awaiting.chainId,
            targetScore = awaiting.targetScore,
            createdAt = awaiting.createdAt,
            seedInt = parsed?.seedInt ?: 0
        )
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
    // RIVALRIES & HEAD-TO-HEAD HISTORY (Capped at 50)
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
                val parsedGridStats = mutableMapOf<String, GridStat>()
                val gridObj = obj.optJSONObject("gridStats")
                if (gridObj != null) {
                    val keys = gridObj.keys().asSequence().toList()
                    for (k in keys) {
                        val sObj = gridObj.optJSONObject(k)
                        if (sObj != null) {
                            parsedGridStats[k.lowercase()] = GridStat(
                                wins = sObj.optInt("wins", 0),
                                losses = sObj.optInt("losses", 0),
                                ties = sObj.optInt("ties", 0)
                            )
                        }
                    }
                }
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
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        inProgressCode = obj.optString("inProgressCode", ""),
                        inProgressTriesLeft = obj.optInt("inProgressTriesLeft", 0),
                        inProgressMaxTries = obj.optInt("inProgressMaxTries", 3),
                        inProgressSeed = obj.optInt("inProgressSeed", 0),
                        gridStats = parsedGridStats,
                        lastReplyCode = obj.optString("lastReplyCode", ""),
                        isReplySent = obj.optBoolean("isReplySent", true)
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt data fallback
        }
        return list.sortedByDescending { it.updatedAt }.take(MAX_RIVALS_CAP)
    }

    private fun persistRivals(context: Context, list: List<RivalRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val capped = list.sortedByDescending { it.updatedAt }.take(MAX_RIVALS_CAP)
        val jsonArray = JSONArray()
        for (item in capped) {
            val gridObj = JSONObject()
            for ((gridKey, stat) in item.gridStats) {
                val sObj = JSONObject().apply {
                    put("wins", stat.wins)
                    put("losses", stat.losses)
                    put("ties", stat.ties)
                }
                gridObj.put(gridKey.lowercase(), sObj)
            }
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
                put("inProgressCode", item.inProgressCode)
                put("inProgressTriesLeft", item.inProgressTriesLeft)
                put("inProgressMaxTries", item.inProgressMaxTries)
                put("inProgressSeed", item.inProgressSeed)
                put("gridStats", gridObj)
                put("lastReplyCode", item.lastReplyCode)
                put("isReplySent", item.isReplySent)
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
        val challenges = loadChallenges(context).toMutableList()
        if (challenges.removeAll { it.rivalName.equals(name, ignoreCase = true) }) {
            persistChallenges(context, challenges)
        }
    }

    fun findRivalByName(context: Context, name: String): RivalRecord? {
        return loadRivals(context).firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * PASS 5.18 Part 1:
     * Marks a rival's saved R-code reply as sent (isReplySent = true) once the user copies or shares it
     * from either the Game Over screen or the Rivalry tab's "Send Reply" button.
     */
    fun markReplySent(context: Context, rivalName: String, replyCode: String? = null): RivalRecord? {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        val existing = findRivalByName(context, cleanName) ?: return null
        val updated = existing.copy(
            lastReplyCode = if (!replyCode.isNullOrBlank()) replyCode else existing.lastReplyCode,
            isReplySent = true
        )
        saveRival(context, updated)
        return updated
    }

    /**
     * Returns true if the given rival currently has an unfinished (IN_PROGRESS) challenge
     * whose code and seed differ from the newly pasted T-code.
     */
    fun hasUnfinishedChallengeToReplace(
        context: Context,
        rivalName: String,
        newCode: String,
        newSeed: Int
    ): Boolean {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        val cleanCode = ChallengeSeed.sanitizeCodeText(newCode).uppercase()
        val existingRival = findRivalByName(context, cleanName)
        if (existingRival != null && existingRival.lastStatus == "IN_PROGRESS") {
            val sameCode = cleanCode.isNotEmpty() && existingRival.inProgressCode.equals(cleanCode, ignoreCase = true)
            val sameSeed = newSeed != 0 && existingRival.inProgressSeed == newSeed
            if (!sameCode && !sameSeed) {
                return true
            }
        }
        val saved = loadChallenges(context)
        return saved.any { ch ->
            !ch.isCompleted &&
                ch.rivalName.equals(cleanName, ignoreCase = true) &&
                !(cleanCode.isNotEmpty() && ch.code.equals(cleanCode, ignoreCase = true)) &&
                !(newSeed != 0 && ch.seed == newSeed)
        }
    }

    /**
     * PASS 5.15 Item 1 & 2 + PASS 5.19 Stage A #1 & Invariant (d):
     * Persists an active/in-progress challenge both in KEY_CHALLENGES and as an "IN_PROGRESS"
     * RivalRecord in the Rivalry tab. Never sets IN_PROGRESS for a consumed code, and replaces
     * any older unfinished challenge from the same rival.
     */
    fun saveInProgressChallenge(context: Context, item: ChallengeItem): RivalRecord {
        val cleanName = ChallengeSeed.sanitizePlayerName(item.rivalName)
        val existing = findRivalByName(context, cleanName)
        if (item.code.isNotBlank() && isCodeConsumed(context, item.code)) {
            return existing ?: RivalRecord(name = cleanName)
        }
        // Remove any older unfinished challenge from the same rival before saving the new one
        val currentChallenges = loadChallenges(context).toMutableList()
        currentChallenges.removeAll { ch ->
            ch.rivalName.equals(cleanName, ignoreCase = true) &&
                !ch.code.equals(item.code, ignoreCase = true)
        }
        persistChallenges(context, currentChallenges)

        saveChallenge(context, item.copy(rivalName = cleanName))
        val record = RivalRecord(
            name = cleanName,
            wins = existing?.wins ?: 0,
            losses = existing?.losses ?: 0,
            ties = existing?.ties ?: 0,
            matches = existing?.matches ?: 0,
            lastGrid = item.gridSizeTag,
            lastDifficulty = item.difficulty,
            lastTarget = item.targetScore,
            lastUserScore = item.bestScore,
            lastStatus = "IN_PROGRESS",
            lastChainId = item.chainId.ifEmpty { existing?.lastChainId ?: "" },
            updatedAt = System.currentTimeMillis(),
            inProgressCode = item.code,
            inProgressTriesLeft = item.attemptsLeft,
            inProgressMaxTries = item.maxTries,
            inProgressSeed = item.seed,
            gridStats = existing?.gridStats ?: emptyMap(),
            lastReplyCode = existing?.lastReplyCode ?: "",
            isReplySent = existing?.isReplySent ?: true
        )
        saveRival(context, record)
        return record
    }

    /**
     * PASS 5.19 Stage A #1:
     * Looks up an existing "IN_PROGRESS" challenge strictly by exact code string or seedInt ONLY,
     * NEVER by chainId (since chainId is permanent across all matches with a rival).
     */
    fun findInProgressChallenge(
        context: Context,
        code: String,
        chainId: String = "",
        rivalName: String? = null,
        seedInt: Int = 0
    ): ChallengeItem? {
        val cleanCode = ChallengeSeed.sanitizeCodeText(code).uppercase()
        if (cleanCode.isNotEmpty() && isCodeConsumed(context, cleanCode)) return null
        val parsed = if (cleanCode.isNotEmpty()) ChallengeSeed.parseChallengeCode(cleanCode) else null
        val targetSeed = if (seedInt != 0) seedInt else (parsed?.seedInt ?: 0)

        // 1. Check RivalRecords with lastStatus == "IN_PROGRESS" by exact code or seedInt ONLY
        val rivals = loadRivals(context)
        val matchedRival = rivals.firstOrNull { r ->
            r.lastStatus == "IN_PROGRESS" && (
                (cleanCode.isNotEmpty() && r.inProgressCode.equals(cleanCode, ignoreCase = true)) ||
                (targetSeed != 0 && r.inProgressSeed == targetSeed)
            )
        }
        if (matchedRival != null) {
            return getInProgressChallengeForRival(context, matchedRival)
        }

        // 2. Check saved ChallengeItems in KEY_CHALLENGES by exact code or seedInt ONLY
        val saved = loadChallenges(context)
        val matchedSaved = saved.firstOrNull { ch ->
            !ch.isCompleted && (
                (cleanCode.isNotEmpty() && ch.code.equals(cleanCode, ignoreCase = true)) ||
                (targetSeed != 0 && ch.seed == targetSeed)
            )
        }
        if (matchedSaved != null) {
            val resolvedName = if (!rivalName.isNullOrBlank()) ChallengeSeed.sanitizePlayerName(rivalName) else matchedSaved.rivalName
            return matchedSaved.copy(rivalName = resolvedName)
        }

        return null
    }

    /**
     * Reconstructs the persisted ChallengeItem for a RivalRecord whose lastStatus == "IN_PROGRESS".
     */
    fun getInProgressChallengeForRival(context: Context, rival: RivalRecord): ChallengeItem? {
        if (rival.lastStatus != "IN_PROGRESS") return null
        if (rival.inProgressCode.isNotBlank() && isCodeConsumed(context, rival.inProgressCode)) return null
        val saved = loadChallenges(context).firstOrNull { ch ->
            (rival.inProgressCode.isNotEmpty() && ch.code.equals(rival.inProgressCode, ignoreCase = true)) ||
            (rival.inProgressSeed != 0 && ch.seed == rival.inProgressSeed)
        }
        if (saved != null) {
            return saved.copy(
                rivalName = rival.name,
                attemptsLeft = rival.inProgressTriesLeft,
                bestScore = maxOf(saved.bestScore, rival.lastUserScore)
            )
        }
        val parsed = if (rival.inProgressCode.isNotEmpty()) ChallengeSeed.parseChallengeCode(rival.inProgressCode) else null
        val seedToUse = if (rival.inProgressSeed != 0) rival.inProgressSeed else (parsed?.seedInt ?: 0)
        return ChallengeItem(
            code = rival.inProgressCode,
            rivalName = rival.name,
            gridSizeTag = rival.lastGrid,
            targetScore = rival.lastTarget,
            maxTries = rival.inProgressMaxTries,
            attemptsLeft = rival.inProgressTriesLeft,
            bestScore = rival.lastUserScore,
            seed = seedToUse,
            difficulty = rival.lastDifficulty,
            chainId = rival.lastChainId,
            isRevenge = parsed?.isRevenge ?: false
        )
    }

    private fun clearInProgressChallengeStorage(context: Context, rivalName: String, chainId: String) {
        val existing = loadChallenges(context).toMutableList()
        val removed = existing.removeAll { ch ->
            (chainId.isNotBlank() && ch.chainId.equals(chainId, ignoreCase = true)) ||
            ch.rivalName.equals(rivalName, ignoreCase = true)
        }
        if (removed) {
            persistChallenges(context, existing)
        }
    }

    private fun updateGridStatsMap(
        existingStats: Map<String, GridStat>,
        gridSize: String,
        isWon: Boolean,
        isLoss: Boolean,
        isTie: Boolean
    ): Map<String, GridStat> {
        val key = gridSize.trim().lowercase()
        val mutable = existingStats.toMutableMap()
        val prev = mutable[key] ?: GridStat()
        mutable[key] = GridStat(
            wins = prev.wins + (if (isWon) 1 else 0),
            losses = prev.losses + (if (isLoss) 1 else 0),
            ties = prev.ties + (if (isTie) 1 else 0)
        )
        return mutable
    }

    fun recordMatchOutcome(
        context: Context,
        rivalName: String,
        userScore: Int,
        targetScore: Int,
        gridSize: String,
        difficulty: GameDifficulty,
        chainId: String,
        maxTries: Int = 3,
        allowTie: Boolean = true,
        seed: Int = 0,
        replyCode: String = ""
    ): RivalRecord? {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        val existing = findRivalByName(context, cleanName)

        // PASS 5.13 Item 1: Strict <, ==, > comparisons for responder playing T-code:
        // userScore > target = WON, userScore == target = TIED, userScore < target = LOST.
        val isWon = if (allowTie) userScore > targetScore else userScore >= targetScore
        val isTie = allowTie && userScore == targetScore
        val isLoss = userScore < targetScore

        // Confirmation 3: With Free tries (maxTries == 0), a Loss is impossible.
        // Beat/tie target = Win/Tie, quit without reaching target = no record (match stays open/pending).
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
        val updatedGridStats = updateGridStatsMap(existing?.gridStats ?: emptyMap(), gridSize, isWon, isLoss, isTie)
        val resolvedChainId = chainId.ifEmpty { existing?.lastChainId ?: ChallengeSeed.generateChainId() }
        val resolvedSeed = if (seed != 0) {
            seed
        } else {
            existing?.inProgressSeed?.takeIf { it != 0 } ?: ChallengeSeed.generateRandomSeed()
        }
        val mySenderName = ChallengeSeed.sanitizePlayerName(getMyName(context))
        val savedReplyCode = replyCode.ifBlank {
            ChallengeSeed.formatChallengeCode(
                gridTag = gridSize,
                targetScore = userScore.coerceAtLeast(1),
                tries = maxTries,
                difficulty = difficulty,
                seed = resolvedSeed,
                senderName = mySenderName,
                chainId = resolvedChainId,
                isRevenge = true
            )
        }
        recordCreatedCode(context, savedReplyCode)

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
            lastChainId = resolvedChainId,
            updatedAt = System.currentTimeMillis(),
            inProgressCode = "",
            inProgressTriesLeft = 0,
            inProgressMaxTries = 0,
            inProgressSeed = 0,
            gridStats = updatedGridStats,
            lastReplyCode = savedReplyCode,
            isReplySent = false
        )

        clearInProgressChallengeStorage(context, cleanName, chainId)
        saveRival(context, record)
        return record
    }

    /**
     * PASS 5.13 Item 1: Records the outcome when pasting an inbound Reply Code (R-code)
     * using strict <, ==, > comparisons:
     * - If responderScore < myOriginalTarget: I WON (I Defended)
     * - If responderScore == myOriginalTarget: TIED
     * - If responderScore > myOriginalTarget: I LOST (Rival Won / Conquered)
     */
    fun recordReplyOutcome(
        context: Context,
        rivalName: String,
        myOriginalTarget: Int,
        responderScore: Int,
        gridSize: String,
        difficulty: GameDifficulty,
        chainId: String,
        maxTries: Int = 3,
        seed: Int = 0
    ): RivalRecord {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        val existing = findRivalByName(context, cleanName)

        val isWon = responderScore < myOriginalTarget
        val isTie = responderScore == myOriginalTarget
        val isLost = responderScore > myOriginalTarget

        val status = when {
            isWon -> "WON"
            isTie -> "TIED"
            else -> "LOST"
        }
        val updatedWins = (existing?.wins ?: 0) + (if (isWon) 1 else 0)
        val updatedLosses = (existing?.losses ?: 0) + (if (isLost) 1 else 0)
        val updatedTies = (existing?.ties ?: 0) + (if (isTie) 1 else 0)
        val updatedMatches = (existing?.matches ?: 0) + 1
        val updatedGridStats = updateGridStatsMap(existing?.gridStats ?: emptyMap(), gridSize, isWon, isLost, isTie)
        val resolvedChain = chainId.ifEmpty { existing?.lastChainId ?: ChallengeSeed.generateChainId() }

        if (resolvedChain.isNotEmpty()) {
            recordChainReplyRival(context, resolvedChain, seed, cleanName)
        }

        val record = RivalRecord(
            name = cleanName,
            wins = updatedWins,
            losses = updatedLosses,
            ties = updatedTies,
            matches = updatedMatches,
            lastGrid = gridSize,
            lastDifficulty = difficulty,
            lastTarget = responderScore,
            lastUserScore = myOriginalTarget,
            lastStatus = status,
            lastChainId = resolvedChain,
            updatedAt = System.currentTimeMillis(),
            inProgressCode = "",
            inProgressTriesLeft = 0,
            inProgressMaxTries = 0,
            inProgressSeed = 0,
            gridStats = updatedGridStats,
            lastReplyCode = "",
            isReplySent = true
        )

        clearInProgressChallengeStorage(context, cleanName, chainId)
        saveRival(context, record)
        return record
    }

    // ==========================================
    // AWAITING CHALLENGES (Outbound with 3-day TTL)
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
                // 3-day (72-hour) TTL expiration check
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
        val parsed = ChallengeSeed.parseChallengeCode(awaiting.code)
        saveOutboundChainTarget(
            context = context,
            chainId = awaiting.chainId,
            targetScore = awaiting.targetScore,
            createdAt = awaiting.createdAt,
            seedInt = parsed?.seedInt ?: 0
        )
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

    /**
     * INBOUND MERGE TUPLE:
     * Matches awaiting cards using (chainId + gridTag) ONLY.
     * targetScore is intentionally excluded because a reply code's targetScore is the responder's
     * score, which differs from the original awaiting card's targetScore.
     */
    fun findAwaitingMatch(context: Context, chainId: String, gridTag: String): AwaitingChallenge? {
        if (chainId.isBlank()) return null
        return loadAwaitingChallenges(context).firstOrNull {
            it.chainId.equals(chainId, ignoreCase = true) &&
            it.gridSizeTag.equals(gridTag, ignoreCase = true)
        }
    }

    /**
     * INBOUND MERGE TUPLE:
     * Deletes matching awaiting card by (chainId + gridTag) ONLY.
     */
    fun deleteAwaitingMatch(context: Context, chainId: String, gridTag: String): Boolean {
        if (chainId.isBlank()) return false
        val existing = loadAwaitingChallenges(context).toMutableList()
        val removed = existing.removeAll {
            it.chainId.equals(chainId, ignoreCase = true) &&
            it.gridSizeTag.equals(gridTag, ignoreCase = true)
        }
        if (removed) {
            persistAwaitingChallenges(context, existing)
        }
        return removed
    }

    // ==========================================
    // FIFO CODE SETS (MY_CODES = 200 CAP, CONSUMED_CODES = 500 CAP)
    // ==========================================

    private fun loadFifoCodes(
        context: Context,
        fifoKey: String,
        setKey: String,
        maxCap: Int = MAX_CODES_CAP
    ): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawFifo = prefs.getString(fifoKey, null)
        val result = mutableListOf<String>()
        if (rawFifo != null) {
            try {
                val arr = JSONArray(rawFifo)
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i, "").trim().uppercase()
                    if (item.isNotEmpty()) result.add(item)
                }
            } catch (_: Exception) {}
        }
        val legacySet = prefs.getStringSet(setKey, emptySet()) ?: emptySet()
        for (s in legacySet) {
            val clean = s.trim().uppercase()
            if (clean.isNotEmpty() && result.none { it.equals(clean, ignoreCase = true) }) {
                result.add(clean)
            }
        }
        return if (result.size > maxCap) {
            result.takeLast(maxCap).toMutableList()
        } else {
            result
        }
    }

    private fun saveFifoCodes(
        context: Context,
        fifoKey: String,
        setKey: String,
        codes: List<String>,
        maxCap: Int = MAX_CODES_CAP
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val capped = if (codes.size > maxCap) codes.takeLast(maxCap) else codes
        val arr = JSONArray()
        for (c in capped) {
            arr.put(c)
        }
        prefs.edit()
            .putString(fifoKey, arr.toString())
            .putStringSet(setKey, capped.toSet())
            .apply()
    }

    fun getMyCreatedCodes(context: Context): List<String> =
        loadFifoCodes(context, KEY_MY_CODES_FIFO, KEY_MY_CODES, MAX_CODES_CAP)

    fun getConsumedCodes(context: Context): List<String> =
        loadFifoCodes(context, KEY_CONSUMED_CODES_FIFO, KEY_CONSUMED_CODES, MAX_CONSUMED_CODES_CAP)

    fun recordCreatedCode(context: Context, code: String) {
        val clean = ChallengeSeed.sanitizeCodeText(code).uppercase()
        if (clean.isEmpty()) return
        val list = loadFifoCodes(context, KEY_MY_CODES_FIFO, KEY_MY_CODES, MAX_CODES_CAP)
        list.removeAll { it.equals(clean, ignoreCase = true) }
        list.add(clean)
        val parsed = ChallengeSeed.parseChallengeCode(clean)
        if (parsed != null) {
            val fullUpper = parsed.fullCode.uppercase()
            list.removeAll { it.equals(fullUpper, ignoreCase = true) }
            list.add(fullUpper)
            if (!parsed.isRevenge && parsed.chainId.isNotEmpty()) {
                saveOutboundChainTarget(
                    context = context,
                    chainId = parsed.chainId,
                    targetScore = parsed.targetScore,
                    seedInt = parsed.seedInt
                )
            }
        }
        saveFifoCodes(context, KEY_MY_CODES_FIFO, KEY_MY_CODES, list, MAX_CODES_CAP)
    }

    fun markCodeConsumed(context: Context, code: String) {
        val clean = ChallengeSeed.sanitizeCodeText(code).uppercase()
        if (clean.isEmpty()) return
        val list = loadFifoCodes(context, KEY_CONSUMED_CODES_FIFO, KEY_CONSUMED_CODES, MAX_CONSUMED_CODES_CAP)
        list.removeAll { it.equals(clean, ignoreCase = true) }
        list.add(clean)
        val parsed = ChallengeSeed.parseChallengeCode(clean)
        if (parsed != null) {
            val fullUpper = parsed.fullCode.uppercase()
            list.removeAll { it.equals(fullUpper, ignoreCase = true) }
            list.add(fullUpper)
        }
        saveFifoCodes(context, KEY_CONSUMED_CODES_FIFO, KEY_CONSUMED_CODES, list, MAX_CONSUMED_CODES_CAP)
    }

    /**
     * PASS 5.19 Run-Signature Consumption Check:
     * Checks both the exact 7-segment code string AND, for playable T-codes, the canonical
     * run signature (senderName + gridTag + targetScore + seedInt). Even if the same run is
     * re-shared with different tries or a different chainId, it is recognized as already completed.
     */
    fun isCodeConsumed(context: Context, code: String): Boolean {
        val clean = ChallengeSeed.sanitizeCodeText(code).uppercase()
        if (clean.isEmpty()) return false
        val consumed = loadFifoCodes(context, KEY_CONSUMED_CODES_FIFO, KEY_CONSUMED_CODES, MAX_CONSUMED_CODES_CAP)
        if (consumed.any { it.equals(clean, ignoreCase = true) }) return true
        val parsed = ChallengeSeed.parseChallengeCode(clean) ?: return false
        if (consumed.any { it.equals(parsed.fullCode, ignoreCase = true) }) {
            return true
        }
        if (!parsed.isRevenge && !parsed.isLegacy && parsed.seedInt != 0) {
            for (stored in consumed) {
                val cParsed = ChallengeSeed.parseChallengeCode(stored)
                if (cParsed != null &&
                    !cParsed.isRevenge &&
                    !cParsed.isLegacy &&
                    cParsed.seedInt == parsed.seedInt &&
                    cParsed.targetScore == parsed.targetScore &&
                    cParsed.gridTag.equals(parsed.gridTag, ignoreCase = true) &&
                    cParsed.senderName.equals(parsed.senderName, ignoreCase = true)
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * PASS 5.11 Item 2 & 3:
     * Tier 1 (Hard Block):
     * 1. Check the EXACT code string against KEY_MY_CODES FIRST. If it matches, HARD BLOCK.
     * 2. Only AFTER the exact string check passes, apply the R-token bypass for the seed/chain check.
     * 3. For T-codes, check seed + targetScore + gridTag against locally created T-codes.
     * Note: Same-name collision (senderName == myName) is now handled via Tier 2 Soft Block Dialog.
     */
    fun isSelfChallenge(context: Context, code: String): Boolean {
        val clean = ChallengeSeed.sanitizeCodeText(code).uppercase()
        if (clean.isEmpty()) return false
        val parsed = ChallengeSeed.parseChallengeCode(clean)
        val current = loadFifoCodes(context, KEY_MY_CODES_FIFO, KEY_MY_CODES)

        // Step 1: Check EXACT code string against KEY_MY_CODES FIRST (blocks self-reply & own T-code)
        if (current.any { it.equals(clean, ignoreCase = true) }) return true
        if (parsed != null && current.any { it.equals(parsed.fullCode, ignoreCase = true) }) return true

        // Step 2: Only AFTER exact string check passes, apply R-token bypass for seed/chain check
        val tdToken = clean.split('-').getOrNull(2)?.uppercase() ?: ""
        if (parsed?.isRevenge == true || tdToken.startsWith("R")) {
            return false
        }

        // Step 3: Check seed + targetScore + gridTag against locally generated T-codes
        if (parsed != null) {
            for (stored in current) {
                val storedParsed = ChallengeSeed.parseChallengeCode(stored)
                if (storedParsed != null &&
                    !storedParsed.isRevenge &&
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

    fun isSelfChallenge(context: Context, parsed: ParsedChallenge): Boolean =
        isSelfChallenge(context, parsed.fullCode)

    /**
     * Tier 2 (Soft Block): Returns true if the code's senderName matches local "My Name"
     * (after Tier 1 device-history check has already passed).
     */
    fun isSameNameCollision(context: Context, parsed: ParsedChallenge?): Boolean {
        if (parsed == null || parsed.isLegacy || parsed.senderName.isBlank()) return false
        val myName = getMyName(context)
        return parsed.senderName.equals(myName, ignoreCase = true)
    }
}
