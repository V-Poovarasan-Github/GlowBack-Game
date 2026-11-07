# PASS 5.26 (REVISED) — Unified Rival Run State Machine, Multiplayer-Only Save/Continue & Anti-Exploit Hardening

This revised plan incorporates all review catches: an explicit `isOutboundRivalRun` launch flag (with strict teardown guarantees), fresh-seed + Ready-gate enforcement on `MIN_FIVE` restarts, a qualifying-score confirmation guard on both mid-run `Restart` and unshared `Game Over` exits, `2P`/`3P`/`4P`-only `Save & Exit` / `Continue`, and workspace asset cleanup.

---

## User Review & Confirmed Decisions

> [!IMPORTANT]
> All Phase 1 decisions and all Pass 5.26 review catches are locked into this specification:

- **Confirmed Decision 1 (Save & Exit / Continue Scope)**: `Save & Exit` and the Home `Continue` banner are restricted strictly to **2P, 3P, and 4P Pass & Play matches**. Normal Solo, Outbound Rival runs, and Inbound Challenges never write to or display the `Continue` banner.
- **Confirmed Decision 2 (Victory Decision Popup on `MIN: 5` Rival Runs)**: When a player launches a match from a Rival card on a new or previously won grid (`MIN: 5` gate) and clears Round 5 (reaching Score 5), the **Victory Decision Pause** pops up immediately (`Lock & Exit` vs `Keep Playing`), matching the `BEAT: X` flow.
- **Confirmed Decision 3 (Mid-Run Exit on Outbound Rival Runs)**: When pausing or pressing Back mid-run during any Outbound Rival run, if the target is already met (`score >= effectiveGate`), the player is offered **Exit & Lock Score**; if not yet met, a confirmation dialog warns that exiting will **discard the run**.
- **Review Catch 1 (Restart Fresh-Seed Rule & Qualifying Score Restart Guard)**:
  - Restarting any `isOutboundRivalRun` (both `BEAT_SCORE` and `MIN_FIVE`) mints a **brand-new random seed**, enforces `PaceMode.CLASSIC`, and re-arms the `"Tap When Ready"` gate so players cannot memorize sequences via restart-loops.
  - **Qualifying Score Restart Safeguard**: If `currentScore >= effectiveRivalGateScore` when `Restart` is tapped in the Pause menu, a confirmation prompt warns `"Restart discards your qualifying score of [N]"` (`[Keep Playing]` vs `[Restart Anyway]`) so an accidental tap never vaporizes a share-qualifying run.
- **Review Catch 2 (Explicit `isOutboundRivalRun` State Flag & Strict Teardown)**:
  - Instead of deriving rival-run status from `currentRivalNameForShare`, use an explicit `var isOutboundRivalRun by remember { mutableStateOf(false) }` state variable set **only** in `startRevengeGame(...)`.
  - `clearRevengeState()` resets `isOutboundRivalRun = false`, `isRevengeRun = false`, `revengeTargetScore = 0`, `currentRivalNameForShare = null`, `currentRivalChainId = null`, and `hasSharedCurrentRivalRun = false`, and is called whenever any Solo or `2P`/`3P`/`4P` run starts from `IDLE`, whenever an Inbound Challenge starts, and whenever a run exits/discards to Home or Rivalry.
- **Polish 1 & 2 (Unshared Qualifying Score Guard at Game Over & Safe Lock Landing)**:
  - **Lock & Exit Landing**: Tapping `🔒 Lock & Exit` (from `VictoryDecisionDialog` or from the mid-run `Exit & Lock Score` dialog) lands cleanly on the **Game Over** screen with `currentScore` locked in and Share Challenge unlocked (consistent with PASS 5.21). If the player opens and dismisses `ShareTriesSelectorDialog`, they remain safely on `Game Over` with Share still unlocked.
  - **Unshared Qualifying Score Guard on Game Over Exit**: If the player is on `Game Over` during an `isOutboundRivalRun` with `currentScore >= effectiveRivalGateScore` and has **not** yet shared or copied their code (`!hasSharedCurrentRivalRun`), tapping `Exit to Rivalry`, Back, or Leaderboard shows a confirmation prompt: `"Score [N] qualifies — share before leaving?"` (`[Share Challenge]` vs `[Leave Without Sharing]`).

---

## 1. Overview & Core Concept

- **Root Cause & Architectural Fix**:
  Previously, `isRevengeRun` conflated two distinct concepts:
  1. *"Is this run an Outbound Rival Duel launched from a Rival card?"*
  2. *"Does this grid use a `BEAT: X` target gate rather than a `MIN: 5` gate?"*
  By introducing an explicit `isOutboundRivalRun: Boolean` state flag for the run context while keeping `isRevengeRun: Boolean` + `revengeTargetScore: Int` strictly for the `BEAT: X` vs `MIN: 5` target rule, every screen, dialog, pause menu, restart handler, and lifecycle observer behaves identically across all Rival grid variations.

---

## 2. User Experience, Flowcharts & Anti-Exploit Matrix

### Flow 1: Complete Outbound Rival Run Lifecycle (`BEAT: X` and `MIN: 5`)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                 RIVAL CARD ACTION ("Get Revenge" / "Challenge")              │
└───────────────────────────────────────┬──────────────────────────────────────┘
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                        RIVAL MATCH SETUP DIALOG                              │
│  • Option A: Continue Last Mode (e.g. 2×2 Easy • Beat rival's score of 6)   │
│  • Option B: Pick Grid & Difficulty (e.g. 3×3 Easy • First duel: Score 5+)   │
└───────────────────┬───────────────────────────────────────┬──────────────────┘
                    │ (Loss/Tie on chosen grid)             │ (Won/Unplayed grid)
                    ▼                                       ▼
      ┌───────────────────────────┐           ┌───────────────────────────┐
      │  RIVAL RUN: BEAT_SCORE    │           │   RIVAL RUN: MIN_FIVE     │
      │  • isOutboundRival = true │           │   • isOutboundRival = true│
      │  • isRevengeRun = true    │           │   • isRevengeRun = false  │
      │  • effectiveGate = X + 1  │           │   • effectiveGate = 5     │
      │  • HUD: "BEAT: X"         │           │   • HUD: "MIN: 5"         │
      └─────────────┬─────────────┘           └─────────────┬─────────────┘
                    └───────────────────┬───────────────────┘
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                    IN-GAME PLAYBACK & ROUND PROGRESSION                      │
│  • "Tap When Ready" Gate protects Round 1 start & OS background resume       │
│  • Never writes to Solo/Multiplayer RunStateStorage                          │
└──────┬────────────────────────────────┬───────────────────────────────┬──────┘
       │                                │                               │
[Reaches effectiveGate]        [Taps Pause -> Restart]         [Taps Pause -> Exit / Back]
(score == X+1 OR score == 5)            │                               │
       │                                ▼                               ▼
       ▼                    ┌───────────────────────┐       ┌───────────────────────┐
┌──────────────────────┐    │ RESTART GUARD         │       │ RIVAL EXIT CONFIRM    │
│ VICTORY DECISION     │    │ • Score < Gate:       │       │ • At Ready Gate:      │
│ • "🏆 Target Beaten!"│    │   Instant fresh seed  │       │   Instant clean exit  │
│   or "🏆 Target      │    │   + Ready Gate        │       │ • Score < Gate:       │
│   Reached!"          │    │ • Score >= Gate:      │       │   Warn & Discard Run  │
│ • [🔒 Lock & Exit]   │    │   Confirm: "Restart   │       │ • Score >= Gate:      │
│   -> Lands on Game   │    │   discards qualifying │       │   Offer [Exit & Lock  │
│   Over (Share Ready) │    │   score of [N]"       │       │   Score] -> Lands on  │
│ • [▶ Keep Playing]   │    └───────────────────────┘       │   Game Over (Share)   │
└──────────┬───────────┘                                    └───────────┬───────────┘
           └────────────────────────────┬───────────────────────────────┘
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     GAME OVER SCREEN (OUTBOUND RIVAL RUN)                    │
│  • Share Challenge / Copy Code unlocked when currentScore >= effectiveGate   │
│  • If unshared & currentScore >= effectiveGate on Exit:                      │
│    Confirm: "Score [N] qualifies — share before leaving?"                    │
│    -> [Share Challenge] (opens Share Tries dialog) vs [Leave Without Sharing]│
└──────────────────────────────────────────────────────────────────────────────┘
```

---

### Flow 2: All 4 Game Modes — Pause Menu & Exit Behavior Comparison

```
┌─────────────────────────┬─────────────────────────┬─────────────────────────┬─────────────────────────┐
│     1. NORMAL SOLO      │   2. PASS & PLAY (2-4P) │   3. OUTBOUND RIVAL     │  4. INBOUND CHALLENGE   │
├─────────────────────────┼─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ • [Resume]              │ • [Resume]              │ • [Resume]              │ • [Resume]              │
│ • [Quit to Home]        │ • [Save & Exit]         │ • [Exit to Rivalry]     │ • [Exit Challenge]      │
│ • [Restart]             │ • [Quit to Home]        │ • [Restart]             │                         │
│                         │ • [Restart]             │                         │                         │
│ • No Save & Exit        │ • Saves 2P-4P state     │ • No Save & Exit        │ • Cheat-Proof Forfeit   │
│ • No Continue Banner    │ • Shows Continue Pill   │ • Restart = Fresh Seed  │   or Lock Score Dialog  │
│ • Clears Rival State    │ • Clears Rival State    │   + Qualifying Guard    │ • Clears Rival State    │
└─────────────────────────┴─────────────────────────┴─────────────────────────┴─────────────────────────┘
```

---

### Flow 3: Anti-Exploit & Boundary Matrix

| Vector / Boundary Scenario | System Protection |
| :--- | :--- |
| **Exploit 1: Restart-Loop Sequence Memorization on `MIN: 5` Rival Grid** | Keying `actuallyStartNewGame()` on `isOutboundRivalRun` ensures every Restart on a `MIN: 5` grid mints a fresh `ChallengeSeed.generateRandomSeed()`, forces `PaceMode.CLASSIC`, and re-arms the `"Tap When Ready"` gate. |
| **Exploit 2: Stale `currentRivalNameForShare` Leaking into Normal Solo** | Explicit `isOutboundRivalRun` boolean is only set in `startRevengeGame()` and is wiped in `clearRevengeState()` on every Solo/Multiplayer start, mode switch, config change, inbound challenge start, and exit. |
| **Exploit 3: Saving a Rival Run into "Continue Solo"** | `Save & Exit` is hidden whenever `gameMode == GameMode.SOLO` (both Normal Solo and Outbound Rival runs). `saveActiveRun()` and `RunStateStorage.loadRunState()` strictly reject `SOLO` states, allowing only `TWO_PLAYERS`, `THREE_PLAYERS`, and `FOUR_PLAYERS`. |
| **Hazard 1: Accidental Mid-Run Restart After Qualifying (`score >= gate`)** | Tapping `Restart` when `isOutboundRivalRun && currentScore >= effectiveRivalGateScore` opens a confirmation dialog (`"Restart discards your qualifying score of [N]"` with `[Keep Playing]` vs `[Restart Anyway]`). |
| **Hazard 2: Accidental Exit from Game Over Before Sharing Qualifying Score** | Tapping `Exit to Rivalry` or Back on `GAME_OVER` when `isOutboundRivalRun && currentScore >= effectiveRivalGateScore && !hasSharedCurrentRivalRun` opens `"Score [N] qualifies — share before leaving?"` (`[Share Challenge]` vs `[Leave Without Sharing]`). |
| **Hazard 3: Dismissing `ShareTriesSelectorDialog` After Locking Score** | `Lock & Exit` (from Victory Decision Pause or Mid-Run Exit Lock) transitions to `GAME_OVER` with `currentScore` preserved; dismissing `ShareTriesSelectorDialog` leaves the player safely on `GAME_OVER` where `[Share Challenge]` and `[Copy Code]` remain unlocked. |

---

## 3. Technical Architecture & State Transitions

### State Flags & Derived Helpers

1. **Explicit Outbound Rival State**:
   - `var isOutboundRivalRun by remember { mutableStateOf(false) }`
   - `var hasSharedCurrentRivalRun by remember { mutableStateOf(false) }`
   - `val effectiveRivalGateScore = if (isOutboundRivalRun) (if (isRevengeRun && revengeTargetScore > 0) revengeTargetScore + 1 else 5) else 0`
   - `clearRevengeState()` resets:
     - `isOutboundRivalRun = false`
     - `isRevengeRun = false`
     - `revengeTargetScore = 0`
     - `currentRivalNameForShare = null`
     - `currentRivalChainId = null`
     - `hasSharedCurrentRivalRun = false`

2. **Victory Decision Pause (`isVictoryDecisionVisible`)**:
   - Triggered once per try (`!hasShownVictoryDecisionThisTry`) at the end of a round when:
     - Inbound Challenge: `activeChallenge != null && currentScore > activeChallenge!!.targetScore`
     - Outbound Rival (`BEAT_SCORE` or `MIN_FIVE`): `isOutboundRivalRun && currentScore >= effectiveRivalGateScore`
   - Copy in `VictoryDecisionDialog`:
     - Inbound Challenge: Title `"🏆 Target Beaten!"`, Body `"You beat ${rivalName}'s target of $targetScore with score $effectiveBestScore! Lock this as your winning result, or keep playing to set an even higher score?"`
     - Outbound Rival (`BEAT_SCORE`, `isRevengeRun && revengeTargetScore > 0`): Title `"🏆 Target Beaten!"`, Body `"You beat ${rivalName}'s score of $targetScore! Lock this to unlock your Revenge share, or keep playing to set an even higher target?"`
     - Outbound Rival (`MIN_FIVE`, `isOutboundRivalRun && !isRevengeRun`): Title `"🏆 Target Reached!"`, Body `"You reached score $effectiveBestScore against $rivalName! Lock this to unlock your challenge share, or keep playing to set an even higher target?"`
   - On `🔒 Lock & Exit` for `isOutboundRivalRun`:
     - Cancels `tapFlashJob`, clears round celebration flags, updates best score if needed, and sets `gameState = GameState.GAME_OVER` so the player lands on the Game Over summary with Share Challenge unlocked.

3. **Dialogs for Outbound Rival Exit, Restart, and Unshared Game Over**:
   - **Mid-Run Exit (`ActiveDialog.EXIT_CHALLENGE_CONFIRM`)**:
     - Works for both `activeChallenge != null` (Inbound Challenge) and `isOutboundRivalRun` (Outbound Rival Run).
     - For `isOutboundRivalRun` mid-round:
       - If `currentScore < effectiveRivalGateScore`: Title `"Exit Rival Match?"`, warns that leaving discards the current run (`[Keep Playing]` vs `[Exit & Discard]`).
       - If `currentScore >= effectiveRivalGateScore`: Title `"Lock Score & Exit?"`, offers to lock `currentScore` and go to the Game Over share screen (`[Keep Playing]` vs `[Exit & Lock Score]`).
   - **Mid-Run Qualifying Score Restart Confirm (`ActiveDialog.RIVAL_RESTART_CONFIRM`)**:
     - When `onRestartRun` is clicked in `SettingsSheetDialog` during `isOutboundRivalRun`:
       - If `currentScore >= effectiveRivalGateScore`: opens `RivalRestartConfirmDialog` (`"Restart discards your qualifying score of $currentScore."` with `[Keep Playing]` and `[Restart Anyway]`).
       - If `currentScore < effectiveRivalGateScore`: immediately restarts with a fresh seed and `"Tap When Ready"` gate.
   - **Unshared Qualifying Score Game Over Exit Confirm (`ActiveDialog.UNSHARED_RIVAL_EXIT_CONFIRM`)**:
     - When `requestExitChallenge()` is invoked at `GameState.GAME_OVER` during `isOutboundRivalRun`:
       - If `currentScore >= effectiveRivalGateScore && !hasSharedCurrentRivalRun`: opens `UnsharedRivalExitConfirmDialog` (`"Score $currentScore qualifies — share before leaving?"` with `[Share Challenge]` and `[Leave Without Sharing]`).
       - Otherwise: exits cleanly to the Rivalry tab (`exitChallengeAndNavigateToRivalry()`).
     - Note: Setting `hasSharedCurrentRivalRun = true` occurs whenever the player copies the code (`onCopyCode`) or initiates share (`onGenerateCard` / `onCopyOnly` in `ShareTriesSelectorDialog`).

4. **Strict 2P/3P/4P-Only `Save & Exit` and `Continue` Banner**:
   - `SettingsSheetDialog`: `Save & Exit` button is rendered **only** when `!isChallengeMode && !isRevengeMode && currentGameMode != GameMode.SOLO`.
   - `saveActiveRun()`: Returns early if `gameMode == GameMode.SOLO` (after handling `activeChallenge != null`), so only `2P`, `3P`, and `4P` runs are written to `RunStateStorage`.
   - `RunStateStorage.loadRunState(context)`: Filters out any legacy `"SOLO"` state so `activeRunState` is `null` unless a `2P`, `3P`, or `4P` match was saved.

5. **Unused Media & Asset Cleanup**:
   - Remove the 9 unused generated `.jpg` files in `/app/src/main/res/drawable/` (`glowback_grid_5x4_*.jpg`, `glowback_hard_mode_*.jpg`, `glowback_home_idle_*.jpg`, `glowback_midflash_*.jpg`, `glowback_splash_*.jpg`, `glowback_your_turn_*.jpg`).
   - Remove temporary uploaded/copied screenshots and videos in `/public/assets/aistudio/` and temporary showcase markdown artifacts.

---

## 4. Boundary & Regression Verification Suite

1. **Normal Solo Pause & No Continue**: Verify `pause_save_exit_button` does not exist in Solo, and `continue_run_button` never appears after Solo pause/quit/background.
2. **Pass & Play (2P/3P/4P) Save & Resume**: Verify `pause_save_exit_button` is visible in `2P`, saves state, displays `continue_run_button` on Home, and resumes into the player handoff card.
3. **Outbound Rival `MIN: 5` Grid Variation (`3×3`)**:
   - Verify `isOutboundRivalRun == true`, `Save & Exit` is hidden, and `Exit to Rivalry` is shown.
   - Verify clearing Round 5 (`score == 5`) triggers `VictoryDecisionDialog` (`"🏆 Target Reached!"`).
   - Verify `🔒 Lock & Exit` lands on `Game Over` with Share Challenge unlocked; dismissing `ShareTriesSelectorDialog` keeps the player on `Game Over` with Share unlocked.
4. **Outbound Rival Restart Fresh-Seed & Qualifying Guard**:
   - At `score < 5`: Restart mints a new seed and re-arms `"Tap When Ready"`.
   - At `score >= 5` (after `Keep Playing`): Tapping `Restart` opens `RivalRestartConfirmDialog` warning `"Restart discards your qualifying score of 5"`.
5. **Outbound Rival Mid-Run Exit & Unshared Game Over Guard**:
   - Mid-run at `score < 5`: `Exit to Rivalry` warns and discards.
   - Mid-run at `score >= 5`: `Exit to Rivalry` offers `Exit & Lock Score` $\rightarrow$ lands on `Game Over`.
   - At `Game Over` with unshared `score >= 5`: Tapping `Exit to Rivalry` prompts `"Score 5 qualifies — share before leaving?"`.
