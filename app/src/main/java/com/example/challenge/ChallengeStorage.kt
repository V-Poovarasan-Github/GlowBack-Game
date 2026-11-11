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
    private const val KEY_SENT_RECOMMENDATIONS = "sent_rival_recommendations_v1"

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
            if (awaiting.rivalName != null) {
                put("rivalName", awaiting.rivalName)
            }
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
            val rName = obj.optString("rivalName", "").trim().takeIf { it.isNotEmpty() }
            AwaitingChallenge(
                chainId = obj.getString("chainId"),
                code = obj.getString("code"),
                gridSizeTag = obj.optString("gridSizeTag", "2x2"),
                targetScore = obj.getInt("targetScore"),
                difficulty = difficulty,
                paceName = obj.optString("paceName", "Classic"),
                tries = obj.optInt("tries", 3),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                rivalName = rName
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
                            val sDiffName = sObj.optString("lastDifficulty", "EASY")
                            val sDiff = try { GameDifficulty.valueOf(sDiffName) } catch (_: Exception) { GameDifficulty.EASY }
                            parsedGridStats[k.lowercase()] = GridStat(
                                wins = sObj.optInt("wins", 0),
                                losses = sObj.optInt("losses", 0),
                                ties = sObj.optInt("ties", 0),
                                lastTarget = sObj.optInt("lastTarget", 0),
                                lastDifficulty = sDiff,
                                lastStatus = sObj.optString("lastStatus", ""),
                                lastPlayedAt = sObj.optLong("lastPlayedAt", 0L)
                            )
                        }
                    }
                }

                // PASS 5.33: Multi-pending outbound parsing with lenient migration fallback
                val parsedPendingOutbounds = mutableListOf<PendingOutboundChallenge>()
                val pendingsArr = obj.optJSONArray("pendingOutbounds")
                if (pendingsArr != null) {
                    for (j in 0 until pendingsArr.length()) {
                        val pObj = pendingsArr.optJSONObject(j) ?: continue
                        val pDiffName = pObj.optString("difficulty", "EASY")
                        val pDiff = try { GameDifficulty.valueOf(pDiffName) } catch (_: Exception) { GameDifficulty.EASY }
                        parsedPendingOutbounds.add(
                            PendingOutboundChallenge(
                                gridTag = pObj.optString("gridTag", "2x2"),
                                difficulty = pDiff,
                                target = pObj.optInt("target", 0),
                                tries = pObj.optInt("tries", 3),
                                code = pObj.optString("code", ""),
                                mintedAt = pObj.optLong("mintedAt", System.currentTimeMillis()),
                                rivalName = pObj.optString("rivalName", obj.optString("name", "HERO")),
                                seed = pObj.optInt("seed", 0),
                                chainId = pObj.optString("chainId", "")
                            )
                        )
                    }
                } else {
                    // Lenient fallback: legacy single pendingOutbound object
                    val singleP = obj.optJSONObject("pendingOutbound")
                    if (singleP != null) {
                        val pDiffName = singleP.optString("difficulty", "EASY")
                        val pDiff = try { GameDifficulty.valueOf(pDiffName) } catch (_: Exception) { GameDifficulty.EASY }
                        parsedPendingOutbounds.add(
                            PendingOutboundChallenge(
                                gridTag = singleP.optString("gridTag", "2x2"),
                                difficulty = pDiff,
                                target = singleP.optInt("target", 0),
                                tries = singleP.optInt("tries", 3),
                                code = singleP.optString("code", ""),
                                mintedAt = singleP.optLong("mintedAt", System.currentTimeMillis()),
                                rivalName = singleP.optString("rivalName", obj.optString("name", "HERO")),
                                seed = singleP.optInt("seed", 0),
                                chainId = singleP.optString("chainId", "")
                            )
                        )
                    }
                }

                // PASS 5.35 Fix 1: Per-grid lastReplies parsing with lenient fallback
                val parsedLastReplies = mutableMapOf<String, StoredReply>()
                val repliesObj = obj.optJSONObject("lastReplies")
                if (repliesObj != null) {
                    val keys = repliesObj.keys().asSequence().toList()
                    for (k in keys) {
                        val rObj = repliesObj.optJSONObject(k)
                        if (rObj != null) {
                            val code = rObj.optString("code", "").trim()
                            if (code.isNotEmpty()) {
                                val copiedAtVal = if (rObj.has("copiedAt") && !rObj.isNull("copiedAt")) {
                                    val v = rObj.optLong("copiedAt", 0L)
                                    if (v > 0L) v else null
                                } else null
                                parsedLastReplies[k.trim().lowercase()] = StoredReply(
                                    code = code,
                                    mintedAt = rObj.optLong("mintedAt", System.currentTimeMillis()),
                                    copiedAt = copiedAtVal
                                )
                            }
                        }
                    }
                } else {
                    // Improvisation #1: Migrate legacy unsent lastReplyCode when lastReplies key is absent
                    val legacyCode = obj.optString("lastReplyCode", "").trim()
                    val legacySent = obj.optBoolean("isReplySent", true)
                    val legacyGrid = obj.optString("lastGrid", "2x2").trim().lowercase()
                    if (!legacySent && legacyCode.isNotEmpty() && legacyGrid.isNotEmpty()) {
                        parsedLastReplies[legacyGrid] = StoredReply(
                            code = legacyCode,
                            mintedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
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
                        isReplySent = obj.optBoolean("isReplySent", true),
                        pendingOutbounds = parsedPendingOutbounds,
                        lastReplies = parsedLastReplies
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
                    put("lastTarget", stat.lastTarget)
                    put("lastDifficulty", stat.lastDifficulty.name)
                    put("lastStatus", stat.lastStatus)
                    put("lastPlayedAt", stat.lastPlayedAt)
                }
                gridObj.put(gridKey.lowercase(), sObj)
            }
            val pendingsArr = JSONArray()
            for (p in item.pendingOutbounds) {
                val pObj = JSONObject().apply {
                    put("gridTag", p.gridTag)
                    put("difficulty", p.difficulty.name)
                    put("target", p.target)
                    put("tries", p.tries)
                    put("code", p.code)
                    put("mintedAt", p.mintedAt)
                    put("rivalName", p.rivalName)
                    put("seed", p.seed)
                    put("chainId", p.chainId)
                }
                pendingsArr.put(pObj)
            }
            val repliesObj = JSONObject()
            for ((gridKey, reply) in item.lastReplies) {
                if (reply.code.isNotBlank()) {
                    val rObj = JSONObject().apply {
                        put("code", reply.code)
                        put("mintedAt", reply.mintedAt)
                        if (reply.copiedAt != null) {
                            put("copiedAt", reply.copiedAt)
                        }
                    }
                    repliesObj.put(gridKey.trim().lowercase(), rObj)
                }
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
                put("pendingOutbounds", pendingsArr)
                put("lastReplies", repliesObj)
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
        val targetRival = existing.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (targetRival != null) {
            // PASS 5.33: Ghost unlock - clear unreplied pending outbounds from ledger
            for (p in targetRival.pendingOutbounds) {
                removeSentRecommendation(context, targetRival.name, p.gridTag, p.difficulty, p.seed)
            }
        }
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
    fun markReplySent(context: Context, rivalName: String, replyCode: String? = null, copiedAt: Long = System.currentTimeMillis()): RivalRecord? {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        val existing = findRivalByName(context, cleanName) ?: return null
        val updatedReplies = existing.lastReplies.toMutableMap()
        if (!replyCode.isNullOrBlank()) {
            val parsedGrid = ChallengeSeed.parseChallengeCode(replyCode)?.gridTag?.lowercase()
                ?: updatedReplies.entries.firstOrNull { it.value.code.equals(replyCode.trim(), ignoreCase = true) }?.key
                ?: replyCode.split('-').firstOrNull()?.trim()?.lowercase()?.takeIf { it in listOf("2x2", "3x3", "4x4", "5x4") }
                ?: existing.lastGrid.trim().lowercase()
            if (parsedGrid.isNotEmpty()) {
                val prevMintedAt = updatedReplies[parsedGrid]?.mintedAt ?: System.currentTimeMillis()
                updatedReplies[parsedGrid] = StoredReply(code = replyCode.trim(), mintedAt = prevMintedAt, copiedAt = copiedAt)
            }
        } else {
            val grid = existing.lastGrid.trim().lowercase()
            if (grid.isNotEmpty() && updatedReplies.containsKey(grid)) {
                val cur = updatedReplies[grid]!!
                updatedReplies[grid] = cur.copy(copiedAt = cur.copiedAt ?: copiedAt)
            } else if (updatedReplies.size == 1) {
                val singleKey = updatedReplies.keys.first()
                val cur = updatedReplies[singleKey]!!
                updatedReplies[singleKey] = cur.copy(copiedAt = cur.copiedAt ?: copiedAt)
            }
        }
        val updated = existing.copy(
            lastReplyCode = if (!replyCode.isNullOrBlank()) replyCode else existing.lastReplyCode,
            isReplySent = true,
            lastReplies = updatedReplies
        )
        saveRival(context, updated)
        return updated
    }

    /**
     * PASS 5.35 Fix 1:
     * Overwrites ONLY the specified gridTag's latest reply code on a rival's lastReplies map.
     */
    fun saveLastReplyForGrid(
        context: Context,
        rivalName: String,
        gridTag: String,
        replyCode: String,
        now: Long = System.currentTimeMillis(),
        copiedAt: Long? = null
    ): RivalRecord? {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        val cleanGrid = gridTag.trim().lowercase()
        val cleanCode = replyCode.trim()
        if (cleanName.isEmpty() || cleanGrid.isEmpty() || cleanCode.isEmpty()) return null
        val existing = findRivalByName(context, cleanName) ?: return null
        val updatedReplies = existing.lastReplies.toMutableMap()
        val prevCopiedAt = updatedReplies[cleanGrid]?.takeIf { it.code == cleanCode }?.copiedAt ?: copiedAt
        updatedReplies[cleanGrid] = StoredReply(code = cleanCode, mintedAt = now, copiedAt = prevCopiedAt)
        val updated = existing.copy(
            lastReplyCode = cleanCode,
            lastReplies = updatedReplies
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
     * PASS 5.15 Item 1 & 2 + PASS 5.19 Stage A #1 & Invariant (d) + PASS 5.35 Fix 2:
     * Persists an active/in-progress challenge both in KEY_CHALLENGES and as an "IN_PROGRESS"
     * RivalRecord in the Rivalry tab. Never sets IN_PROGRESS for a consumed code, and replaces
     * any older unfinished challenge from the same rival.
     * When lastUserScoreOverride is provided (e.g., 0 on a forfeited tie try), RivalRecord.lastUserScore
     * uses lastUserScoreOverride for display while KEY_CHALLENGES preserves sanitizedItem.bestScore.
     */
    fun saveInProgressChallenge(
        context: Context,
        item: ChallengeItem,
        lastUserScoreOverride: Int? = null
    ): RivalRecord {
        val cleanName = ChallengeSeed.sanitizePlayerName(item.rivalName)
        val existing = findRivalByName(context, cleanName)
        if (item.code.isNotBlank() && isCodeConsumed(context, item.code)) {
            return existing ?: RivalRecord(name = cleanName)
        }
        // PASS 5.20 Caution 1: Free Tries Pre-Deduction Write Guard
        // When maxTries == 0 (isFreeTries), never persist negative or finite tries.
        val safeMaxTries = item.maxTries.coerceAtLeast(0)
        val safeTriesLeft = if (safeMaxTries == 0) 0 else item.attemptsLeft.coerceAtLeast(0)
        val sanitizedItem = item.copy(
            rivalName = cleanName,
            maxTries = safeMaxTries,
            attemptsLeft = safeTriesLeft
        )

        // Remove any older unfinished challenge from the same rival before saving the new one
        val currentChallenges = loadChallenges(context).toMutableList()
        currentChallenges.removeAll { ch ->
            ch.rivalName.equals(cleanName, ignoreCase = true) &&
                !ch.code.equals(sanitizedItem.code, ignoreCase = true)
        }
        persistChallenges(context, currentChallenges)

        saveChallenge(context, sanitizedItem)
        val record = RivalRecord(
            name = cleanName,
            wins = existing?.wins ?: 0,
            losses = existing?.losses ?: 0,
            ties = existing?.ties ?: 0,
            matches = existing?.matches ?: 0,
            lastGrid = sanitizedItem.gridSizeTag,
            lastDifficulty = sanitizedItem.difficulty,
            lastTarget = sanitizedItem.targetScore,
            lastUserScore = lastUserScoreOverride ?: sanitizedItem.bestScore,
            lastStatus = "IN_PROGRESS",
            lastChainId = sanitizedItem.chainId.ifEmpty { existing?.lastChainId ?: "" },
            updatedAt = System.currentTimeMillis(),
            inProgressCode = sanitizedItem.code,
            inProgressTriesLeft = safeTriesLeft,
            inProgressMaxTries = safeMaxTries,
            inProgressSeed = sanitizedItem.seed,
            gridStats = existing?.gridStats ?: emptyMap(),
            lastReplyCode = existing?.lastReplyCode ?: "",
            isReplySent = existing?.isReplySent ?: true,
            pendingOutbounds = existing?.pendingOutbounds ?: emptyList(),
            lastReplies = existing?.lastReplies ?: emptyMap()
        )
        saveRival(context, record)
        return record
    }

    /**
     * PASS 5.20 Rule 7 & Guard A:
     * Reconciles any orphaned IN_PROGRESS RivalRecord where the player was on their final finite try
     * (inProgressMaxTries > 0 && inProgressTriesLeft <= 0, persisted when the "Ready?" gate was dismissed)
     * and force-closed the app mid-round. Automatically finalizes the match outcome (WON, TIED, or LOST
     * based on lastUserScore vs lastTarget), marks the code consumed, and generates the unsent R-code
     * (isReplySent = false) so the player never sees "Resume (0 left)" in the Rivalry tab.
     */
    fun reconcileOrphanedInProgressChallenges(context: Context) {
        val rivals = loadRivals(context)
        for (r in rivals) {
            if (r.lastStatus == "IN_PROGRESS" && r.inProgressMaxTries > 0 && r.inProgressTriesLeft <= 0) {
                if (r.inProgressCode.isNotBlank()) {
                    markCodeConsumed(context, r.inProgressCode)
                }
                val resolvedSeed = if (r.inProgressSeed != 0) {
                    r.inProgressSeed
                } else {
                    ChallengeSeed.parseChallengeCode(r.inProgressCode)?.seedInt ?: ChallengeSeed.generateRandomSeed()
                }
                val savedItem = loadChallenges(context).firstOrNull { ch ->
                    (r.inProgressCode.isNotEmpty() && ch.code.equals(r.inProgressCode, ignoreCase = true)) ||
                    (r.inProgressSeed != 0 && ch.seed == r.inProgressSeed)
                }
                val effectiveUserScore = maxOf(r.lastUserScore, savedItem?.bestScore ?: 0)
                recordMatchOutcome(
                    context = context,
                    rivalName = r.name,
                    userScore = effectiveUserScore,
                    targetScore = r.lastTarget,
                    gridSize = r.lastGrid,
                    difficulty = r.lastDifficulty,
                    chainId = r.lastChainId,
                    maxTries = r.inProgressMaxTries,
                    seed = resolvedSeed
                )
            }
        }
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
        isTie: Boolean,
        rivalScoreOnThisGrid: Int = 0,
        difficulty: GameDifficulty = GameDifficulty.EASY,
        myStatusOnThisGrid: String = "",
        playedAt: Long = System.currentTimeMillis()
    ): Map<String, GridStat> {
        val key = gridSize.trim().lowercase()
        val mutable = existingStats.toMutableMap()
        val prev = mutable[key] ?: GridStat()
        val resolvedTarget = if (rivalScoreOnThisGrid > 0) rivalScoreOnThisGrid else prev.lastTarget
        val resolvedDiff = if (rivalScoreOnThisGrid > 0) difficulty else prev.lastDifficulty
        val resolvedStatus = if (myStatusOnThisGrid.isNotBlank()) myStatusOnThisGrid else prev.lastStatus
        mutable[key] = GridStat(
            wins = prev.wins + (if (isWon) 1 else 0),
            losses = prev.losses + (if (isLoss) 1 else 0),
            ties = prev.ties + (if (isTie) 1 else 0),
            lastTarget = resolvedTarget,
            lastDifficulty = resolvedDiff,
            lastStatus = resolvedStatus,
            lastPlayedAt = playedAt
        )
        return mutable
    }

    /**
     * PASS 5.22:
     * Resolves the rival's score, difficulty, and outcome status for a given grid tag (e.g. "2x2", "3x3", "5x4").
     * Returns Triple(rivalScore, difficulty, status):
     * - If matching rival.lastGrid and rival.lastTarget > 0, returns the most recent match info.
     * - Else returns that specific grid's GridStat if present and has a recorded rival score.
     * - Else returns Triple(0, GameDifficulty.EASY, "NEW").
     */
    fun getGridTargetInfo(rival: RivalRecord, gridTag: String): Triple<Int, GameDifficulty, String> {
        val cleanTag = gridTag.trim().lowercase()
        val cleanLast = rival.lastGrid.trim().lowercase()
        if (cleanTag == cleanLast && rival.lastTarget > 0) {
            return Triple(rival.lastTarget, rival.lastDifficulty, rival.lastStatus)
        }
        val stat = rival.gridStats[cleanTag]
        if (stat != null && stat.lastTarget > 0) {
            return Triple(stat.lastTarget, stat.lastDifficulty, stat.lastStatus)
        }
        return Triple(0, GameDifficulty.EASY, "NEW")
    }

    private fun buildRecommendationKey(
        gridTag: String,
        difficulty: GameDifficulty,
        seed: Int
    ): String = "${gridTag.trim().lowercase()}:${difficulty.name.uppercase()}:$seed"

    /**
     * Records that a specific stored board (keyed by gridTag + difficulty + seed) has been
     * sent to or played against a specific rival, preventing it from being recommended again
     * to that same rival.
     */
    fun recordSentRecommendation(
        context: Context,
        rivalName: String,
        gridTag: String,
        difficulty: GameDifficulty,
        seed: Int
    ) {
        if (seed <= 0) return
        val cleanRival = ChallengeSeed.sanitizePlayerName(rivalName)
        if (cleanRival.isEmpty()) return
        val cleanGrid = gridTag.trim().lowercase()
        if (cleanGrid.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SENT_RECOMMENDATIONS, null)
        val rootObj = try {
            if (raw != null) JSONObject(raw) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        val arr = rootObj.optJSONArray(cleanRival) ?: JSONArray()
        val boardKey = buildRecommendationKey(cleanGrid, difficulty, seed)
        var exists = false
        for (i in 0 until arr.length()) {
            if (arr.optString(i, "").equals(boardKey, ignoreCase = true)) {
                exists = true
                break
            }
        }
        if (!exists) {
            arr.put(boardKey)
            rootObj.put(cleanRival, arr)
            prefs.edit().putString(KEY_SENT_RECOMMENDATIONS, rootObj.toString()).apply()
        }
    }

    fun getSentRecommendationsForRival(context: Context, rivalName: String): Set<String> {
        val cleanRival = ChallengeSeed.sanitizePlayerName(rivalName)
        if (cleanRival.isEmpty()) return emptySet()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SENT_RECOMMENDATIONS, null) ?: return emptySet()
        val rootObj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return emptySet()
        }
        val arr = rootObj.optJSONArray(cleanRival) ?: return emptySet()
        val result = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val entry = arr.optString(i, "").trim().lowercase()
            if (entry.isNotEmpty()) result.add(entry)
        }
        return result
    }

    /**
     * PASS 5.33: Removes a specific stored board entry from the sent-recommendations ledger
     * (used by Ghost Unlock when a targeted pending share expires past 7 days without reply,
     * or when a rival card is deleted).
     */
    fun removeSentRecommendation(
        context: Context,
        rivalName: String,
        gridTag: String,
        difficulty: GameDifficulty,
        seed: Int
    ) {
        if (seed <= 0) return
        val cleanRival = ChallengeSeed.sanitizePlayerName(rivalName)
        if (cleanRival.isEmpty()) return
        val cleanGrid = gridTag.trim().lowercase()
        if (cleanGrid.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SENT_RECOMMENDATIONS, null) ?: return
        val rootObj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        val arr = rootObj.optJSONArray(cleanRival) ?: return
        val boardKey = buildRecommendationKey(cleanGrid, difficulty, seed)
        val newArr = JSONArray()
        var changed = false
        for (i in 0 until arr.length()) {
            val entry = arr.optString(i, "")
            if (entry.equals(boardKey, ignoreCase = true)) {
                changed = true
            } else {
                newArr.put(entry)
            }
        }
        if (changed) {
            if (newArr.length() > 0) {
                rootObj.put(cleanRival, newArr)
            } else {
                rootObj.remove(cleanRival)
            }
            prefs.edit().putString(KEY_SENT_RECOMMENDATIONS, rootObj.toString()).apply()
        }
    }

    const val GHOST_UNLOCK_TTL_MS = 7L * 24 * 60 * 60 * 1000L // 7 days (168 hours)

    /**
     * PASS 5.33: Adds or prepends a targeted pending challenge to a rival's pendingOutbounds list.
     */
    fun addRivalPendingOutbound(context: Context, rivalName: String, pending: PendingOutboundChallenge) {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        if (cleanName.isEmpty()) return
        val rivals = loadRivals(context).toMutableList()
        val index = rivals.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val record = if (index >= 0) {
            rivals[index]
        } else {
            RivalRecord(name = cleanName)
        }
        val updatedList = record.pendingOutbounds.filterNot {
            (it.seed != 0 && it.seed == pending.seed) ||
            (it.chainId.isNotEmpty() && it.chainId.equals(pending.chainId, ignoreCase = true))
        }.toMutableList()
        updatedList.add(0, pending)
        val updatedRecord = record.copy(pendingOutbounds = updatedList, updatedAt = System.currentTimeMillis())
        if (index >= 0) {
            rivals[index] = updatedRecord
        } else {
            rivals.add(0, updatedRecord)
        }
        persistRivals(context, rivals)
    }

    /**
     * PASS 5.33: Removes a specific pending challenge from a rival's pendingOutbounds list by chainId or seed.
     */
    fun removeRivalPendingOutbound(context: Context, rivalName: String, chainId: String = "", seed: Int = 0) {
        val cleanName = ChallengeSeed.sanitizePlayerName(rivalName)
        if (cleanName.isEmpty()) return
        val rivals = loadRivals(context).toMutableList()
        val index = rivals.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        if (index < 0) return
        val record = rivals[index]
        val updatedList = record.pendingOutbounds.filterNot {
            val matchesChain = chainId.isNotEmpty() && it.chainId.equals(chainId, ignoreCase = true)
            val matchesSeed = seed != 0 && it.seed == seed
            matchesChain || matchesSeed
        }
        if (updatedList.size != record.pendingOutbounds.size) {
            rivals[index] = record.copy(pendingOutbounds = updatedList)
            persistRivals(context, rivals)
        }
    }

    /**
     * PASS 5.33: Sweeps all rivals for pending outbound challenges older than 7 days (GHOST_UNLOCK_TTL_MS).
     * For expired entries: removes them from KEY_SENT_RECOMMENDATIONS so they can be recommended again,
     * and removes them from the rival's pendingOutbounds list.
     */
    fun sweepExpiredPendingOutbounds(context: Context, now: Long = System.currentTimeMillis()): Int {
        val rivals = loadRivals(context).toMutableList()
        var totalPurged = 0
        var rivalsChanged = false

        for (i in rivals.indices) {
            val rival = rivals[i]
            if (rival.pendingOutbounds.isEmpty()) continue

            val remaining = mutableListOf<PendingOutboundChallenge>()
            var rivalModified = false

            for (pending in rival.pendingOutbounds) {
                if (now - pending.mintedAt >= GHOST_UNLOCK_TTL_MS) {
                    // Expired with NO reply -> ghost unlock! Remove from ledger!
                    removeSentRecommendation(context, rival.name, pending.gridTag, pending.difficulty, pending.seed)
                    totalPurged++
                    rivalModified = true
                } else {
                    remaining.add(pending)
                }
            }

            if (rivalModified) {
                rivals[i] = rival.copy(pendingOutbounds = remaining)
                rivalsChanged = true
            }
        }

        if (rivalsChanged) {
            persistRivals(context, rivals)
        }
        return totalPurged
    }

    const val LAST_REPLY_TTL_MS = 30L * 24 * 60 * 60 * 1000L // 30 days

    /**
     * PASS 5.35 Fix 1: Sweeps all rivals for stored per-grid reply codes older than 30 days (LAST_REPLY_TTL_MS).
     */
    fun sweepExpiredLastReplies(context: Context, now: Long = System.currentTimeMillis()): Int {
        val rivals = loadRivals(context).toMutableList()
        var totalPurged = 0
        var rivalsChanged = false

        for (i in rivals.indices) {
            val rival = rivals[i]
            if (rival.lastReplies.isEmpty()) continue

            val remaining = mutableMapOf<String, StoredReply>()
            var rivalModified = false

            for ((gridKey, stored) in rival.lastReplies) {
                if (now - stored.mintedAt >= LAST_REPLY_TTL_MS) {
                    totalPurged++
                    rivalModified = true
                } else {
                    remaining[gridKey] = stored
                }
            }

            if (rivalModified) {
                rivals[i] = rival.copy(lastReplies = remaining)
                rivalsChanged = true
            }
        }

        if (rivalsChanged) {
            persistRivals(context, rivals)
        }
        return totalPurged
    }

    /**
     * PASS 5.28 & PASS 5.33:
     * Computes qualifying "Sleeping Scores" (Rival Score Recommendations) where the player already has
     * a stored Classic board score (with valid seed > 0) that strictly beats the rival's lastTarget
     * on the exact same gridTag and difficulty, excluding any board already sent to or played against this rival.
     */
    fun computeSleepingScoresForRival(
        context: Context?,
        rival: RivalRecord,
        allBoardScores: List<BoardScoreOption>
    ): List<SleepingScoreRecommendation> {
        val sentKeys = if (context != null) {
            sweepExpiredPendingOutbounds(context)
            getSentRecommendationsForRival(context, rival.name)
        } else {
            emptySet()
        }
        val recommendations = mutableListOf<SleepingScoreRecommendation>()
        for (board in allBoardScores) {
            if (!board.paceName.equals("Classic", ignoreCase = true)) continue
            if (board.score < 5 || board.seed <= 0) continue

            val boardKey = buildRecommendationKey(board.gridTag, board.difficulty, board.seed).lowercase()
            if (boardKey in sentKeys) continue

            val (rivalTarget, rivalDiff, rivalStatus) = getGridTargetInfo(rival, board.gridTag)
            if (rivalTarget <= 0 || rivalStatus == "NEW") continue
            if (board.difficulty != rivalDiff) continue
            if (board.score > rivalTarget) {
                recommendations.add(
                    SleepingScoreRecommendation(
                        boardOption = board,
                        rivalLastTarget = rivalTarget
                    )
                )
            }
        }
        return recommendations.sortedWith(
            compareByDescending<SleepingScoreRecommendation> { it.boardOption.score - it.rivalLastTarget }
                .thenByDescending { it.boardOption.gridTag.equals(rival.lastGrid, ignoreCase = true) }
                .thenByDescending { it.boardOption.score }
        )
    }

    fun computeSleepingScoresForRival(
        rival: RivalRecord,
        allBoardScores: List<BoardScoreOption>
    ): List<SleepingScoreRecommendation> =
        computeSleepingScoresForRival(context = null, rival = rival, allBoardScores = allBoardScores)

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

        val now = System.currentTimeMillis()
        val updatedWins = (existing?.wins ?: 0) + (if (isWon) 1 else 0)
        val updatedLosses = (existing?.losses ?: 0) + (if (isLoss) 1 else 0)
        val updatedTies = (existing?.ties ?: 0) + (if (isTie) 1 else 0)
        val updatedMatches = (existing?.matches ?: 0) + 1
        val updatedGridStats = updateGridStatsMap(
            existingStats = existing?.gridStats ?: emptyMap(),
            gridSize = gridSize,
            isWon = isWon,
            isLoss = isLoss,
            isTie = isTie,
            rivalScoreOnThisGrid = targetScore,
            difficulty = difficulty,
            myStatusOnThisGrid = status,
            playedAt = now
        )
        val resolvedChainId = chainId.ifEmpty { existing?.lastChainId ?: ChallengeSeed.generateChainId() }
        val resolvedSeed = if (seed != 0) {
            seed
        } else {
            existing?.inProgressSeed?.takeIf { it != 0 } ?: ChallengeSeed.generateRandomSeed()
        }
        if (seed > 0) {
            recordSentRecommendation(context, cleanName, gridSize, difficulty, seed)
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

        val updatedLastReplies = (existing?.lastReplies ?: emptyMap()).toMutableMap()
        val cleanGridKey = gridSize.trim().lowercase()
        if (cleanGridKey.isNotEmpty() && savedReplyCode.isNotBlank()) {
            updatedLastReplies[cleanGridKey] = StoredReply(
                code = savedReplyCode,
                mintedAt = now
            )
        }

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
            updatedAt = now,
            inProgressCode = "",
            inProgressTriesLeft = 0,
            inProgressMaxTries = 0,
            inProgressSeed = 0,
            gridStats = updatedGridStats,
            lastReplyCode = savedReplyCode,
            isReplySent = false,
            pendingOutbounds = existing?.pendingOutbounds ?: emptyList(),
            lastReplies = updatedLastReplies
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
        val now = System.currentTimeMillis()
        val updatedWins = (existing?.wins ?: 0) + (if (isWon) 1 else 0)
        val updatedLosses = (existing?.losses ?: 0) + (if (isLost) 1 else 0)
        val updatedTies = (existing?.ties ?: 0) + (if (isTie) 1 else 0)
        val updatedMatches = (existing?.matches ?: 0) + 1
        val updatedGridStats = updateGridStatsMap(
            existingStats = existing?.gridStats ?: emptyMap(),
            gridSize = gridSize,
            isWon = isWon,
            isLoss = isLost,
            isTie = isTie,
            rivalScoreOnThisGrid = responderScore,
            difficulty = difficulty,
            myStatusOnThisGrid = status,
            playedAt = now
        )
        val resolvedChain = chainId.ifEmpty { existing?.lastChainId ?: ChallengeSeed.generateChainId() }

        if (resolvedChain.isNotEmpty()) {
            recordChainReplyRival(context, resolvedChain, seed, cleanName)
        }
        if (seed > 0) {
            recordSentRecommendation(context, cleanName, gridSize, difficulty, seed)
        }

        // PASS 5.33: Multi-pending resolution - remove matching pending targeted challenge
        val remainingPendings = (existing?.pendingOutbounds ?: emptyList()).filterNot {
            val matchesChain = chainId.isNotEmpty() && it.chainId.equals(chainId, ignoreCase = true)
            val matchesSeed = seed != 0 && it.seed == seed
            matchesChain || matchesSeed
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
            updatedAt = now,
            inProgressCode = "",
            inProgressTriesLeft = 0,
            inProgressMaxTries = 0,
            inProgressSeed = 0,
            gridStats = updatedGridStats,
            lastReplyCode = "",
            isReplySent = true,
            pendingOutbounds = remainingPendings,
            lastReplies = existing?.lastReplies ?: emptyMap()
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
                val rivalName = obj.optString("rivalName", "").trim().takeIf { it.isNotEmpty() }
                list.add(
                    AwaitingChallenge(
                        chainId = obj.getString("chainId"),
                        code = obj.getString("code"),
                        gridSizeTag = obj.optString("gridSizeTag", "2x2"),
                        targetScore = obj.getInt("targetScore"),
                        difficulty = difficulty,
                        paceName = obj.optString("paceName", "Classic"),
                        tries = obj.optInt("tries", 3),
                        createdAt = createdAt,
                        rivalName = rivalName
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
                if (item.rivalName != null) {
                    put("rivalName", item.rivalName)
                }
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
