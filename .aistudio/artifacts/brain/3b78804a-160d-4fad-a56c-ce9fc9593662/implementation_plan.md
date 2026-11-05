# PASS 5.21 — Victory Decision Pause ("You won! Exit or continue?")

Pauses active asynchronous challenges and Revenge / Tie-Breaker runs **once per try** at the exact round-clear moment when `currentScore` first exceeds `targetScore` (or `revengeTargetScore`), allowing the player to either immediately lock in their winning score or continue pushing higher without interruption.

---

## User Review & Critical Decisions

- **Confirmed Decision 1 — Implement PASS 5.21 Victory Decision Pause:** Pause after the round-clear celebration arpeggio when `currentScore` first exceeds `targetScore` (inbound challenge) or `revengeTargetScore` (Revenge / Tie-Breaker run), showing the **Victory Decision** overlay once per try.
- **Confirmed Decision 2 — Inbound Challenge `[🔒 Lock & Exit]` Destination:** Tapping `[🔒 Lock & Exit]` on an inbound challenge immediately finalizes the match as **`WON`** with `effectiveBest = maxOf(currentScore, ch.bestScore)`, marks the challenge code consumed, generates the unsent R-code (`isReplySent = false`), clears `IN_PROGRESS` storage, and navigates directly to **Leaderboard $\rightarrow$ Rivalry** where `"Send Reply"` is ready.
- **Confirmed Micro-Caution — `effectiveBest` in Body Copy:** In `VictoryDecisionDialog` body copy, display `effectiveBest = maxOf(currentScore, ch.bestScore)` as `[N]` (not raw `currentScore`) so the copy stays accurate even in the resume edge case where a prior try's `bestScore` already exceeded the target.

---

## 1. Overview & Core Concept

- **What It Does:** Eliminates the need for players to intentionally miss a pad or race to hit Pause during the next sequence after beating a rival's target. The moment they clear the winning round (`currentScore == targetScore + 1`), the round-clear celebration plays, the next round's sequence is prepared, and the sequence playback pauses behind a celebratory **Victory Decision** overlay.
- **Target Audience / Persona:** Players competing in asynchronous T-code challenges or Revenge / Tie-Breaker rallies who want either an immediate, frictionless victory lock-in or an uninterrupted high-score push.
- **Key Value:** Gives players explicit control at the moment of victory while keeping normal Solo, 2P, 3P, and 4P modes completely untouched.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Inbound Challenge — First Time Beating Target (`currentScore == targetScore + 1`):**
   - Player taps the final pad of Round `T + 1`, bringing `currentScore` to `T + 1` (`> targetScore`).
   - `bestScore` updates immediately (`ch.bestScore = maxOf(currentScore, ch.bestScore)`) and is checkpointed to `SharedPreferences` via `saveInProgressChallenge`.
   - The round-clear arpeggio and emerald confetti flash play as usual.
   - Instead of immediately flashing Round `T + 2`, the game pauses (`isVictoryDecisionVisible = true`, `hasShownVictoryDecisionThisTry = true`) and presents the **Victory Decision** dialog:
     - **Title:** `"🏆 Target Beaten!"`
     - **Body:** `"You beat [RIVAL]'s target of [T] with score [N]! Lock this as your winning result, or keep playing to set an even higher score?"` where `[N] = effectiveBest = maxOf(currentScore, ch.bestScore)`.
     - **Buttons:** **`[🔒 Lock & Exit]`** and **`[▶ Keep Playing]`**
   - **If `[🔒 Lock & Exit]` is tapped:**
     - Finalizes the match as **`WON`** using `effectiveBest = maxOf(currentScore, ch.bestScore)`, marks the code consumed, generates the unsent R-code (`isReplySent = false`), clears `IN_PROGRESS` state, and navigates to **Leaderboard $\rightarrow$ Rivalry**.
   - **If `[▶ Keep Playing]` is tapped:**
     - Dismisses the overlay (`isVictoryDecisionVisible = false`) and immediately triggers the `WATCHING` playback coroutine for the next round (`roundTrigger++`).
     - Does **not** pause again on subsequent rounds (`T + 2`, `T + 3`, etc.) during this try.
     - If the player later misses a pad, the standard miss handler records **`WON`** with `bestScore`. If they later open Pause / Exit mid-round, the existing PASS 5.20 Scenario B `"Lock Score & Exit?"` dialog applies.

2. **Revenge / Tie-Breaker Run (`isRevengeRun == true && revengeTargetScore > 0`):**
   - When `currentScore` first exceeds `revengeTargetScore` (`currentScore == revengeTargetScore + 1`), pauses at the same round-clear boundary (`isVictoryDecisionVisible = true`, `hasShownVictoryDecisionThisTry = true`):
     - **Title:** `"🏆 Target Beaten!"`
     - **Body:** `"You beat [RIVAL]'s score of [T]! Lock this to unlock your Revenge share, or keep playing to set an even higher target?"`
     - **Buttons:** **`[🔒 Lock & Exit]`** and **`[▶ Keep Playing]`**
   - **If `[🔒 Lock & Exit]` is tapped:**
     - Dismisses the overlay and transitions directly to `GameState.GAME_OVER` (`failedStepNumber = 0`, `failedExpectedPad = null`, `failedTappedPad = null`) with `currentScore` preserved so the standard Game Over screen appears with **`"Share Challenge"` UNLOCKED** (`currentScore > revengeTargetScore`).
   - **If `[▶ Keep Playing]` is tapped:**
     - Dismisses the overlay and continues the run into the next round without further interruptions.

3. **Non-Trigger Conditions:**
   - **Tie (`currentScore == targetScore`):** Never pauses; preserves the existing `"Clear this round to WIN!"` live banner flow.
   - **Already Shown This Try (`hasShownVictoryDecisionThisTry == true`):** Never pauses again on `currentScore == targetScore + 2`, `targetScore + 3`, etc.
   - **Standard Solo (`activeChallenge == null && !isRevengeRun`) & Multiplayer (`2P`/`3P`/`4P`):** Never triggers.

### Visual Identity & Theme
- **Aesthetic Direction:** High-contrast neon arcade dark card (`#1E293B` surface on `#0F172A` backdrop) with an **Emerald Victory** (`#00E676`) border glow matching the live milestone header.
- **Component Styling & Accessibility:**
  - `testTag = "victory_decision_dialog"` on the dialog card
  - `testTag = "victory_decision_title"` and `testTag = "victory_decision_body"` on the header and body text
  - `testTag = "victory_decision_lock_exit_button"` (`[🔒 Lock & Exit]`, minimum `48.dp` touch target)
  - `testTag = "victory_decision_keep_playing_button"` (`[▶ Keep Playing]`, minimum `48.dp` touch target)
