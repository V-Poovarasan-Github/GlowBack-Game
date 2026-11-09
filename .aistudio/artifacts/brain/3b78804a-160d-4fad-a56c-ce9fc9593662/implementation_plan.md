# PASS 5.32 — First-Time Player Name Creation Dialog Plan [COMPLETED]

This plan implements a one-time "Create your name" dialog displayed the very first time a player starts any game mode (Solo, Pass-and-Play, Inbound Challenge, or Outbound Revenge) on a fresh install, ensuring the player's name is configured while avoiding the Back Button Trap and maintaining full synchronization across Settings, sharing, and tests.

---

## 1. Summary of Changes

1. **State & Dialog Enum**:
   - Added `FIRST_NAME_SETUP` to `ActiveDialog`.
   - Maintained `pendingStartAction: (() -> Unit)?` in `GlowbackGameScreen`.
   - Created `ensurePlayerNameConfigured(onReady: () -> Unit)` that checks `ChallengeStorage.hasConfiguredName(context)`. If configured, executes `onReady()`; otherwise stores `pendingStartAction = onReady` and presents `ActiveDialog.FIRST_NAME_SETUP`.
   - Wrapped `startNewGame()`, `startChallenge(item)`, and `startRevengeGame(...)` with `ensurePlayerNameConfigured { ... }`.

2. **Dialog Component (`FirstNameSetupDialog`)**:
   - `onDismissRequest` handles the Android Back Button Trap by triggering the Skip logic (saving `"HERO"`, clearing dialog, and invoking `pendingStartAction`), never trapping the user.
   - Text input auto-focused on launch, filtering with exact `ChallengeSeed.sanitizePlayerName()` rules (`input.replace(' ', '_').uppercase().filter { (it in 'A'..'Z') || (it in '0'..'9') || it == '_' }.take(10)`), displaying live counter `${nameInput.length}/10` and `ImeAction.Done`.
   - "Let's play" primary button (`first_name_confirm_button`) enabled only when `nameInput.isNotBlank()` and not submitting.
   - "Skip" text button (`first_name_skip_button`) saving `"HERO"`.

3. **Test Suite Verification**:
   - Added `ChallengeStorage.setMyName(context, "TESTER")` to `clearPrefs()` in `ExampleRobolectricTest.kt`.
   - Added 7 dedicated tests in `ExampleRobolectricTest.kt`:
     1. `testFirstNameDialog_freshInstall_showsDialogAndBlocksStart`
     2. `testFirstNameDialog_enteringNameAndConfirm_savesAndStartsGame`
     3. `testFirstNameDialog_skipSavesHeroAndStarts`
     4. `testFirstNameDialog_confirmButtonDisabledWhenEmpty`
     5. `testFirstNameDialog_preConfiguredNameNeverShows`
     6. `testFirstNameDialog_settingsFieldSync`
     7. `testFirstNameDialog_challengeAndRevenge_showsDialogThenContinues`
   - Verified that all 81 tests pass (66 Robolectric tests in `ExampleRobolectricTest`, 14 in `ChallengeSeedTest`, 1 in `ExampleUnitTest`).
