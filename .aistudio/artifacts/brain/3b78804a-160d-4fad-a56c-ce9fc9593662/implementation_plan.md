# Implementation Plan - PASS 5.22: Rival Match Variation Selector & Per-Grid Target Tracking (Approved)

## Approved Decisions & User Fixes
1. **Easy & Hard Only:** Glowback uses only `GameDifficulty.EASY` ('E') and `GameDifficulty.HARD` ('H'). No Medium exists.
2. **Precise `GridStat.lastTarget` Definition:**
   - `lastTarget` strictly represents the **rival's last score on this grid from my perspective**:
     - In `recordMatchOutcome` (responder playing rival's T-code): rival's score is incoming `targetScore`.
     - In `recordReplyOutcome` (originator receiving rival's R-code): rival's score is incoming `responderScore` (NOT my original target).
   - `lastStatus` represents **my outcome** on that grid (`"WON"`, `"LOST"`, `"TIED"`).
3. **Chain & Gate-Type Plumbing:**
   - Always preserves `rival.lastChainId` so all matches continue to stack on the same rival card.
   - Gate type:
     - For grids with previous **LOST** or **TIED** status (`targetScore > 0`): `isRevengeRun = true`, `revengeTargetScore = targetScore` (BEAT_SCORE gate: must exceed rival's score to unlock Revenge share; banner shows `BEAT: X`).
     - For grids with previous **WON** status or unplayed grids: `isRevengeRun = false`, `revengeTargetScore = 0` (MIN_FIVE gate: score ≥ 5 unlocks challenge share; banner shows `MIN: 5` or standard banner).

---

## Technical Implementation

### 1. `ChallengeModels.kt`
- Update `GridStat`:
  ```kotlin
  data class GridStat(
      val wins: Int = 0,
      val losses: Int = 0,
      val ties: Int = 0,
      val lastTarget: Int = 0,                                   // Rival's last score on this grid
      val lastDifficulty: GameDifficulty = GameDifficulty.EASY,  // EASY or HARD only
      val lastStatus: String = ""                                // "WON", "LOST", "TIED"
  )
  ```

### 2. `ChallengeStorage.kt`
- Update `updateGridStatsMap`:
  ```kotlin
  private fun updateGridStatsMap(
      existingStats: Map<String, GridStat>,
      gridSize: String,
      isWon: Boolean,
      isLoss: Boolean,
      isTie: Boolean,
      rivalScoreOnThisGrid: Int,
      difficulty: GameDifficulty,
      myStatusOnThisGrid: String
  ): Map<String, GridStat>
  ```
- In `recordMatchOutcome`:
  - Pass `rivalScoreOnThisGrid = targetScore` (the sender's score).
  - Pass `myStatusOnThisGrid = status`.
- In `recordReplyOutcome`:
  - Pass `rivalScoreOnThisGrid = responderScore` (the rival's score).
  - Pass `myStatusOnThisGrid = status`.
- In JSON serialization and deserialization:
  - Serialize `lastTarget`, `lastDifficulty.name`, `lastStatus`.
  - Deserialization with fallback (`optInt("lastTarget", 0)`, `optString("lastDifficulty", "EASY")`, `optString("lastStatus", "")`).
- Add helper `getGridTargetInfo(rival: RivalRecord, gridTag: String): Triple<Int, GameDifficulty, String>`:
  - Returns `(rivalScore, difficulty, status)`.
  - If `gridTag.equals(rival.lastGrid, ignoreCase = true)`, returns `(rival.lastTarget, rival.lastDifficulty, rival.lastStatus)`.
  - Else checks `rival.gridStats[gridTag.lowercase()]`.
  - If not found or empty, returns `(0, GameDifficulty.EASY, "NEW")`.

### 3. `ChallengeUI.kt`
- Build `RivalMatchSetupDialog`:
  - Title:
    - `"⚡ Revenge Setup: ${rival.name}"` (if `rival.lastStatus == "LOST"`)
    - `"🤝 Tie-Breaker Setup: ${rival.name}"` (if `rival.lastStatus == "TIED"`)
    - `"👑 Challenge Setup: ${rival.name}"` (if `rival.lastStatus == "WON"`)
  - **Quick Action ("Continue Last Mode"):**
    - Card displaying: `${rival.lastGrid.uppercase()} • ${if (rival.lastDifficulty == GameDifficulty.HARD) "Hard" else "Easy"} • Classic`
    - Target label:
      - If `rival.lastStatus == "LOST"` or `"TIED"`: `"Target: Beat ${rival.lastTarget}"`
      - Else: `"Target: Score 5+"`
    - Button: `[▶ Continue ${rival.lastGrid.uppercase()}]` (`testTag = "rival_setup_continue_last_button"`).
  - **Custom Action ("Choose Variation"):**
    - Grid chips: `2×2`, `3×3`, `4×4`, `5×4` (`testTag = "rival_setup_grid_..."`).
    - Difficulty chips: `Easy` and `Hard` ONLY (`testTag = "rival_setup_diff_easy"`, `"rival_setup_diff_hard"`).
    - Dynamic Target Banner (`testTag = "rival_setup_target_banner"`):
      - If selected grid has a prior loss/tie: `"⚡ Revenge Mode: Beat ${rival.name}'s previous score of ${target} to share"`
      - If selected grid has a prior win: `"👑 Challenge Mode: Score 5+ to send challenge"`
      - If selected grid is unplayed: `"⚔️ First match on this grid: Score 5+ to send challenge"`
    - Button: `[Start Match]` (`testTag = "rival_setup_start_custom_button"`).
  - **Pace Mode:** Displays `"Pace: Classic (Async Standard)"` (no picker, locked to Classic).
  - Test tags on all interactive elements.

### 4. `MainActivity.kt`
- Add `var rivalForMatchSetup by remember { mutableStateOf<RivalRecord?>(null) }`.
- When clicking `onDynamicAction` in `RivalCard`:
  - If `rival.lastStatus == "IN_PROGRESS"`, call `onStartRevenge(rival)` directly (resumes active run).
  - If `rival.lastStatus != "IN_PROGRESS"`, open setup dialog: `rivalForMatchSetup = rival`.
- In `startRevengeGame(rival: RivalRecord, targetGrid: GridSize, targetDifficulty: GameDifficulty)`:
  - Preserve `currentRivalChainId = rival.lastChainId.ifEmpty { ChallengeSeed.generateChainId() }`.
  - Preserve `currentRivalNameForShare = rival.name`.
  - Retrieve target and status for `targetGrid.tag` via `ChallengeStorage.getGridTargetInfo(rival, targetGrid.tag)`.
  - If `(status == "LOST" || status == "TIED") && targetScore > 0`:
    - `isRevengeRun = true`, `revengeTargetScore = targetScore` (BEAT_SCORE gate).
  - Else:
    - `isRevengeRun = false`, `revengeTargetScore = 0` (MIN_FIVE gate).
  - Set `gridSize = targetGrid`, `difficultyMode = targetDifficulty`, `paceMode = PaceMode.CLASSIC`.
  - Update banner display if `isRevengeRun` (shows `BEAT: ${revengeTargetScore}`) vs non-revenge (shows `MIN: 5`).

### 5. `ExampleRobolectricTest.kt`
- Add test `pass5_22_rivalMatchVariationSelector_and_perGridTargets()`:
  - Tests quick continue vs custom variation.
  - Verifies Easy/Hard chips (no Medium).
  - Verifies per-grid target scores and gate-type behavior.
