# PASS 5.30 — Solo Voluntary Game Over ("End Run & Keep Score")

## Summary
Enables Normal Solo players who are in a hurry to voluntarily conclude their run and bank qualifying scores ($\ge 5$) directly from the Pause menu (`SettingsSheetDialog`). The run transitions immediately to the standard `GAME_OVER` screen, updating personal best records and saving the exact run sequence seed in `SharedPreferences` (leveraging PASS 5.27/5.29). It preserves competitive integrity by strictly forbidding mid-game resumption (`RunStateStorage` is never written to), and leaves Pass & Play, Inbound Duels, and Outbound Rival runs 100% untouched.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Key Architecture Decisions Approved & Enforced:**
> 1. **Zero Resumption in Solo**: No save-and-resume capability is added to Solo. Tapping `[End Run & Keep Score]` permanently concludes the run and advances directly to `GAME_OVER`. No "Continue Solo" pill will ever appear on the Home screen.
> 2. **Score Threshold ($\ge 5$)**: 
>    - When `currentScore < 5`: The pause menu retains the single `[Quit to Home]` button (`pause_quit_home_button`), as sub-5 scores cannot be banked or shared.
>    - When `currentScore >= 5`: The pause menu shows two distinct options: `[End Run & Keep Score]` (primary emerald button) and `[Quit & Discard]` (secondary outline/red button).
> 3. **Complete Flow Isolation**:
>    - **2P / 3P / 4P Pass & Play**: Unchanged (`[Save & Exit]` and `[Quit to Home]`).
>    - **Inbound Challenges (`activeChallenge != null`)**: Unchanged (`[Exit Challenge]`).
>    - **Outbound Rival Runs (`isOutboundRivalRun == true`)**: Unchanged (`[Exit to Rivalry]`).

---

## 1. Overview & Core Concept

- **What It Does**: Allows players with real-life interruptions (phone calls, arriving transit, meeting start) to cash out and bank their current Solo score without having to deliberately tap the wrong pad to trigger a Game Over.
- **Target Persona**: Dedicated Solo players aiming for high scores and challenge creation who need to exit the app immediately without sacrificing their hard-earned score of 5+.
- **Key Value**: Eliminates the "intentional suicide" anti-pattern in Solo play, converting mid-game exits into valid, banked high-score records with seed persistence.

---

## 2. User Experience & Visual Design

### Pause Menu State Transitions (Solo Mode)

```
┌────────────────────────────────────────────────────────┐
│                   PAUSE / SETTINGS                     │
│                                                        │
│  [ ▶ Resume ]                                          │
│                                                        │
│  WHEN Solo & currentScore >= 5:                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 🏆 End Run & Keep Score                          │  │  --> Updates record/seed,
│  │ (Emerald Green 0xFF10B981, testTag:              │  │      transitions to GAME_OVER
│  │  "pause_end_run_keep_score_button")              │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ✕ Quit & Discard                                 │  │  --> Discards run,
│  │ (Dark Slate/Red 0xFFEF4444, testTag:             │  │      transitions to IDLE (Home)
│  │  "pause_quit_discard_button")                    │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  WHEN Solo & currentScore < 5:                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ⌂ Quit to Home                                   │  │  --> Standard discard
│  │ (testTag: "pause_quit_home_button")              │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  [ ↺ Restart ]                                         │
└────────────────────────────────────────────────────────┘
```

### Visual & Semantic Specification
- **`[End Run & Keep Score]` Button**:
  - Container: Emerald Accent `Color(0xFF10B981)`.
  - Content: Dark background `Color(0xFF090D16)`.
  - Icon: `Icons.Default.CheckCircle`.
  - Tag: `pause_end_run_keep_score_button`.
  - Touch Target: 48dp height minimum.
- **`[Quit & Discard]` Button**:
  - Container: Deep slate `Color(0xFF0F172A)`.
  - Border & Text: Red `Color(0xFFEF4444)`.
  - Icon: `Icons.Default.Close`.
  - Tag: `pause_quit_discard_button`.
  - Touch Target: 48dp height minimum.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Why | Rejected Alternative |
| :--- | :--- | :--- | :--- |
| **Voluntary End vs. Save & Resume** | Conclude run directly into `GameState.GAME_OVER` | Eliminates memory-sequence cheat vector (studying pattern while app is closed) | Allowing "Save & Resume" in Solo, which compromises leaderboard fairness |
| **Score Threshold** | Gated strictly at $\ge 5$ | Scores $< 5$ cannot be shared as challenges and are fast to replay | Showing two buttons at score 0 or 1, cluttering the UI for trivial runs |
| **Destination Screen** | Lands on `GameState.GAME_OVER` | Immediately gives the player access to the Game Over summary, records, and "Share Challenge" button | Silently dumping the player on the Home screen without confirmation of score banking |

---

## 4. Technical Architecture & Data Strategy

### System Flow Diagram

```
                 [ User Taps Settings / Pause ]
                               │
            ┌──────────────────┴──────────────────┐
     [ Is Normal Solo? ]                   [ Other Mode? ]
            │                                     │
   ┌────────┴────────┐                   ┌────────┴────────┐
[ Score >= 5 ]  [ Score < 5 ]        [ 2P/3P/4P ]   [ Rival / Inbound ]
   │               │                     │                 │
[ End Run &     [ Quit to             [ Save & Exit     [ Exit Challenge /
 Keep Score ]     Home ]               Quit to Home ]    Exit to Rivalry ]
   │
   ├─► 1. writeBestScore(sharedPrefs, gridSize, paceMode, difficultyMode, currentScore, currentRunSeed)
   ├─► 2. discardActiveRun() -> RunStateStorage.clearRunState(context), activeRunState = null
   ├─► 3. activeDialog = ActiveDialog.NONE
   └─► 4. gameState = GameState.GAME_OVER (Shows summary, Share Challenge enabled for Classic)
```

### Modified Components & File Scopes
1. **`app/src/main/java/com/example/MainActivity.kt`**:
   - `SettingsSheetDialog`:
     - Add parameters: `currentScore: Int = 0`, `onEndRunAndKeepScore: () -> Unit = {}`, `onQuitAndDiscard: () -> Unit = {}`.
     - In the Solo branch, condition button rendering on `currentScore >= 5`.
   - Call site of `SettingsSheetDialog`:
     - Provide `currentScore = currentScore`.
     - Implement `onEndRunAndKeepScore`:
       ```kotlin
       writeBestScore(sharedPrefs, gridSize, paceMode, difficultyMode, currentScore, currentRunSeed)
       tapFlashJob?.cancel()
       activePadId = null
       allPadsConfettiFlash = false
       isRoundClearCelebration = false
       discardActiveRun()
       gameState = GameState.GAME_OVER
       activeDialog = ActiveDialog.NONE
       ```
     - Wire `onQuitAndDiscard` to the standard `onQuitToHome` handler.

---

## 5. Verification Plan

### Automated Robolectric Test Suite
1. **`pass5_30_soloVoluntaryGameOver_bankScoreAndSeed`**:
   - Start 2x2 Classic Solo run.
   - Play to score 8.
   - Open Pause (`settings_button`).
   - Assert `pause_end_run_keep_score_button` and `pause_quit_discard_button` are displayed.
   - Assert `pause_save_exit_button` does NOT exist.
   - Tap `pause_end_run_keep_score_button`.
   - Assert `gameState == GameState.GAME_OVER` and Game Over screen is displayed with score 8.
   - Assert `prefs.getInt("best_2x2_classic_EASY", 0) == 8` and `prefs.getInt("best_seed_2x2_classic_EASY", 0) == currentRunSeed`.
   - Assert `continue_run_button` does NOT exist on Home (no `RunStateStorage` pollution).
2. **`pass5_30_soloLowScore_retainsQuitToHome`**:
   - Start Solo run at score 0/1 (< 5).
   - Open Pause.
   - Assert `pause_quit_home_button` is displayed with "Quit to Home".
   - Assert `pause_end_run_keep_score_button` does NOT exist.
3. **`pass5_30_passAndPlayAndRivals_isolationRegressions`**:
   - Verify 2P Pass & Play still has `pause_save_exit_button` and `pause_quit_home_button`.
   - Verify Outbound Rival runs still show `Exit to Rivalry` and trigger `exit_challenge_confirm_dialog`.
   - Run full test suite: `gradle :app:testDebugUnitTest` (target: 52+ tests passing, 0 failures).
