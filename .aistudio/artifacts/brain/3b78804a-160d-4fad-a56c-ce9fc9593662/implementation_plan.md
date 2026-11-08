# Glowback Final Production-Readiness Plan (Approved Revisions)

## Summary
Executes the final production-readiness hardening for Glowback (offline Android memory-arcade game built with Jetpack Compose and SharedPreferences). Enforces zero additional runtime dependencies, retains the 7-segment challenge code format byte-for-byte, and implements safety-first test verification prior to any refactoring.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Approved Adjustments & Invariants:**
> 1. **Checksum Mutation Verification (Golden Test)**:
>    - The golden test will **not** assert that 100% of single-character mutations are rejected, as the 5-bit checksum ($2^5 = 32$) mathematically collides in approximately 1 out of 32 cases (e.g., characters with delta 32 under odd-position weighting).
>    - The test will assert mutations that are **guaranteed to be caught** (such as target score digit $+1$ where delta is non-multiple of 32, grid tag shifts, or invalid alphabet characters) and test a statistical bound ($\ge 90\%$ rejection over all single-character mutations).
>    - `computeChecksum` and the 7-segment code format remain **100% untouched**.
> 2. **Field Filter Separation**:
>    - **Paste-CODE fields** (ChallengeUI lines ~875 and ~1790): Filter with an ASCII code filter keeping `A-Z`, `0-9`, `'-'`, and `'_'`.
>    - **Name fields** (ChallengeUI lines ~1203, ~1298, ~1333): Filter with the ASCII name filter keeping `A-Z`, `0-9`, and `'_'` (max 10 chars, matching `NAME_REGEX`).
> 3. **Target Score Range Check (1..999)**:
>    - Inside `parseInternal`, enforce `if (targetScore !in 1..999) return null` across **all four** segment-count branches (7, 5, 4, and 3 segments). Add tests verifying 999 is accepted and 1000 is rejected.
> 4. **Fresh-Clone Debug Signing & Assembly**:
>    - In `build.gradle.kts`, debug signing does not depend on a gitignored `debug.keystore`. In Step 6, run `gradle :app:assembleDebug` without any signing environment variables and verify it builds successfully.
> 5. **Decoupled Splash Testing**:
>    - Remove `Class.forName("org.robolectric.Robolectric")` and `skipSplashForTesting` from production release code, decoupling test splash handling cleanly.
> 6. **Short Window Pad Grid Safety**:
>    - Responsive geometry ensures pad grid and parent containers adapt in short or wide windows (split-screen, landscape tablet) without clipping or pushing buttons off-screen.
> 7. **Execution Transparency**:
>    - Every command executed will be reported with exact output and passing counts; any step not executed will be explicitly stated.

---

## 1. Overview & Core Concept

- **The Objective**: Eliminate production failure modes (rotation wipe/forfeits, concurrency crashes, cross-context seed leaks, and cache bloat) while hardening parser performance, accessibility font sizes, and build configuration for a clean Google Play release.
- **Scope of Changes**:
  - **Manifest & Window Safety**: `screenOrientation="portrait"` + full `configChanges` to prevent Activity recreation on phones, tablets, foldables, and split-screen.
  - **Responsive Grid Geometry**: Pad grid and containers adapt smoothly to short heights and wide aspect ratios without clipping.
  - **Playback Concurrency**: Immutable snapshotting (`val steps = sequence.toList()`) with active cancellation checks in playback effects.
  - **Solo Isolation**: `shouldUpdateSoloBest` predicate guarding all Solo personal bests and seeds from friend challenges and rival duels.
  - **Notification Respect**: `IMPORTANCE_DEFAULT` and `PRIORITY_DEFAULT` without heads-up popups.
  - **FileProvider Hardening**: Scope cache path strictly to `shared_challenges/` and prune old PNG cards (> 24h).
  - **Parser Optimization**: Hoist Regex instances, enforce ASCII-only player names and codes, and reject targets $> 999$.

---

## 2. User Experience & Visual Design

### Resilient Layout in Short & Wide Windows

```
   Tall Window / Portrait Phone                  Short Window / Split-Screen / Landscape
┌─────────────────────────────┐         ┌──────────────────────────────────────────────────┐
│  GLOWBACK         [🔊] [⚙]  │         │  GLOWBACK [🔊][⚙] ● RIVAL: FALCON  TARGET: 6    │
│  ● RIVAL: FALCON  TARGET: 6 │         │  (Compact Header 4dp spacing)                    │
│  2×2 CLASSIC  SCORE: 4      │         │                                                  │
│                             │   ──►   │           ┌─────┐   ┌─────┐                      │
│      ┌─────┐   ┌─────┐      │         │           │  ▲  │   │  ●  │                      │
│      │  ▲  │   │  ●  │      │         │           └─────┘   └─────┘                      │
│      └─────┘   └─────┘      │         │           ┌─────┐   ┌─────┐                      │
│      ┌─────┐   ┌─────┐      │         │           │  ■  │   │  ◆  │                      │
│      │  ■  │   │  ◆  │      │         │           └─────┘   └─────┘                      │
│      └─────┘   └─────┘      │         │       (Calculated side fits within maxHeight)    │
│                             │         │  [Play Again] [Home]      [Bottom Navigation]    │
│     [Bottom Navigation]     │         └──────────────────────────────────────────────────┘
└─────────────────────────────┘            Pads Never Clipped • Touch Targets >= 48dp
```

### Visual & Touch Safety
- **Adaptive Spacing**: When vertical height is constrained (split-screen or landscape where `maxHeight < 550.dp`), vertical arrangement spacing contracts from 10dp to 4dp.
- **Aspect Ratio Clamping**: In `BoxWithConstraints`, the pad grid side is clamped against available `maxHeight` so pads never push action buttons off-screen or get clipped.
- **Typography**: Four text labels previously styled at 9–10sp (MainActivity ~4522; ChallengeUI ~468, 513, 3683) are scaled to $\ge 11\text{sp}$ to ensure readability under large system font scaling.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Rationale | Rejected Alternative |
| :--- | :--- | :--- | :--- |
| **Activity Lifecycle on Rotation** | `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode|fontScale|density|layoutDirection|locale"` | Prevents Activity destruction on orientation/density/font change; prevents challenge forfeit | Migrating all game state to `rememberSaveable` (prone to partial serialization bugs) |
| **Solo Best Score Isolation** | Pure function `shouldUpdateSoloBest(...)` | Guarantees friend challenge seeds never leak into Solo records or Sleeping Scores | Manual ad-hoc `if` checks across call sites |
| **Playback Concurrency** | Immutable snapshot `sequence.toList()` + `ensureActive()` | Eliminates `ConcurrentModificationException` when player taps Restart mid-playback | Catching CME or blocking state clearing |
| **Name vs Code Filtering** | ASCII name filter for names; ASCII code filter for paste fields | Allows `-` in codes while preventing invalid symbols in names | Applying name filter to code paste fields (breaks paste!) |
| **Target Score Range** | `targetScore in 1..999` in all 4 `parseInternal` branches | Prevents integer overflow, formatting corruption, or absurd values across all code versions | Only checking in v2 branch |
| **Debug Keystore Fallback** | Fall back to standard AGP debug keystore if file missing | Guarantees fresh git clone builds without requiring a committed binary keystore | Hardcoding file path that fails build on clone |

---

## 4. Technical Architecture & Phased Execution

```
┌────────────────────────────────────────────────────────────────────────┐
│                   PHASED IMPLEMENTATION PIPELINE                       │
├────────────────────────────────────────────────────────────────────────┤
│  Step 0: Write Golden Parser Tests against UNCHANGED ChallengeSeed    │
│          └─► Run gradle :app:testDebugUnitTest (must pass 100%)       │
│                                                                        │
│  Step 1: Stability & Window Layout Fixes                               │
│          ├─ Manifest configChanges & portrait lock                     │
│          ├─ Pad grid short-window responsive constraints               │
│          ├─ Sequence playback snapshot (sequence.toList())            │
│          └─ shouldUpdateSoloBest guard on updateBestScoreIfNeeded      │
│          └─► Run test suite                                           │
│                                                                        │
│  Step 2: Notification Tuning                                           │
│          ├─ IMPORTANCE_DEFAULT & PRIORITY_DEFAULT                      │
│          ├─ Remove DEFAULT_ALL & remove triggerInstantTest            │
│          └─► Run test suite                                           │
│                                                                        │
│  Step 3: Sharing / FileProvider Hardening                              │
│          ├─ Remove cache_root from file_paths.xml                      │
│          ├─ 24h age-based sweep in ChallengeShareHelper               │
│          └─► Run test suite                                           │
│                                                                        │
│  Step 4: Cleanup & Hardening                                           │
│          ├─ Hoist all Regexes in ChallengeSeed                         │
│          ├─ Target score 1..999 check across all 4 branches           │
│          ├─ Separate ASCII code filter vs ASCII name filter            │
│          ├─ Decouple test splash handling (no Class.forName in release)│
│          ├─ Fresh-clone debug signing fallback & plugin cleanup        │
│          └─ Scale 9-10sp typography to >= 11sp                         │
│          └─► Run test suite                                           │
│                                                                        │
│  Step 5: Full Test Suite Expansion (5.1 - 5.6)                         │
│          └─► Run gradle :app:testDebugUnitTest                         │
│                                                                        │
│  Step 6: Verification & Final Report                                   │
│          ├─ gradle :app:assembleDebug (no signing env vars)            │
│          ├─ gradle :app:testDebugUnitTest                              │
│          ├─ gradle :app:lintRelease                                    │
│          ├─ Verify merged manifest (zero INTERNET permission)          │
│          └─ Produce comprehensive release readiness audit report        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Granular Implementation Steps

### Step 0: Golden Tests Safety Net
- In `ChallengeSeedTest.kt`:
  - Round-trip test: `formatChallengeCode` -> `parseChallengeCode` across:
    - 4 Grids: `2x2`, `3x3`, `4x4`, `5x4`
    - 4 Tries: `F` (0), `1`, `3`, `5`
    - 2 Difficulties: `EASY`, `HARD`
    - 2 Types: `T` (Outbound), `R` (Reply)
    - Total: $4 \times 4 \times 2 \times 2 = 64$ permutations.
  - Guaranteed mutation detection: Target score digit $+1$ returns `null`.
  - Statistical mutation bound: Over all single-character mutations, $\ge 90\%$ return `null`.
  - WhatsApp text extraction with zero-width characters and Unicode dashes.
  - Legacy 3-segment, 4-segment, and 5-segment code parsing.
  - Name edge cases (empty, whitespace, max length, underscores).
- **Gate**: Run `gradle :app:testDebugUnitTest` and verify all tests pass on unchanged code.

### Step 1: Stability & Window Layout Fixes
1. **`app/src/main/AndroidManifest.xml`**:
   - Add `android:screenOrientation="portrait"` to `MainActivity`.
   - Add `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode|fontScale|density|layoutDirection|locale"`.
2. **`MainActivity.kt` (Short Window Layout)**:
   - In `BoxWithConstraints` pad grid container:
     - Check available `maxHeight` vs `maxWidth`.
     - In short windows (`maxHeight < 550.dp`), reduce vertical arrangement spacing in parent `Column` from 10dp to 4dp and ensure pad grid height calculation clamps strictly to `maxHeight - safetyMargin`, preventing layout overflow or button clipping in split-screen/landscape tablet windows.
3. **`MainActivity.kt` (Playback Concurrency)**:
   - In sequence playback `LaunchedEffect`:
     ```kotlin
     val steps = sequence.toList()
     val currentTrigger = roundTrigger
     for (pad in steps) {
         currentCoroutineContext().ensureActive()
         if (isLifecyclePaused || gameState != GameState.WATCHING || roundTrigger != currentTrigger ||
             isHandoffVisible || isChallengeReadyGateVisible || isVictoryDecisionVisible) return@LaunchedEffect
         ...
     }
     ```
   - Apply the same snapshotting to `startWatchMyRoundReplay`.
4. **`MainActivity.kt` (Solo Isolation)**:
   - Extract `shouldUpdateSoloBest`:
     ```kotlin
     internal fun shouldUpdateSoloBest(
         activeChallenge: ChallengeItem?,
         isOutboundRivalRun: Boolean,
         isRevengeRun: Boolean,
         gameMode: GameMode
     ): Boolean = activeChallenge == null && !isOutboundRivalRun && !isRevengeRun && gameMode == GameMode.SOLO
     ```
   - Guard `updateBestScoreIfNeeded`:
     ```kotlin
     fun updateBestScoreIfNeeded(newScore: Int) {
         if (!shouldUpdateSoloBest(activeChallenge, isOutboundRivalRun, isRevengeRun, gameMode)) return
         ...
     }
     ```
   - Audit all call sites (around lines 1740, 1884, 2838, 3145, 3357) to ensure none bypass the guard.
- **Gate**: Run test suite.

### Step 2: Notifications
- In `NotificationScheduler.kt`:
  - `val importance = NotificationManager.IMPORTANCE_DEFAULT`
  - `.setPriority(NotificationCompat.PRIORITY_DEFAULT)`
  - Remove `.setDefaults(NotificationCompat.DEFAULT_ALL)`
  - Remove dead debug helper `triggerInstantTest`
- **Gate**: Run test suite.

### Step 3: Sharing / FileProvider
- In `app/src/main/res/xml/file_paths.xml`:
  - Remove `<cache-path name="cache_root" path="." />`
- In `ChallengeShareHelper.kt`:
  - Add `cleanOldSharedFiles(cacheFolder: File, maxAgeHours: Long = 24)` on `Dispatchers.IO` before writing a new PNG file.
  - Ensure the newly created bitmap file is never deleted.
- **Gate**: Run test suite.

### Step 4: Cleanup & Hardening
1. **`ChallengeSeed.kt`**:
   - Hoist all Regexes (`V2_EXTRACTION_REGEX`, `V1_EXTRACTION_REGEX`, `V0_EXTRACTION_REGEX`, `GRID_REGEX`, `TD_MATCH_REGEX`, `TD_REPLY_MATCH_REGEX`, `ZERO_WIDTH_REGEX`, `UNICODE_DASH_REGEX`) to private constants.
   - Enforce ASCII-only in `sanitizePlayerName`:
     ```kotlin
     val cleaned = rawName.trim().replace(' ', '_').uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' }
     ```
   - In `parseInternal`, add target score check to **all four** branches (7, 5, 4, and 3 segments):
     ```kotlin
     val targetScore = parts[1].toIntOrNull() ?: return null
     if (targetScore !in 1..999) return null
     ```
2. **`ChallengeUI.kt`**:
   - **Code fields** (lines ~875 and ~1790): Use ASCII code filter keeping `A-Z`, `0-9`, `'-'`, and `'_'`.
   - **Name fields** (lines ~1203, ~1298, ~1333): Use ASCII name filter keeping `A-Z`, `0-9`, and `'_'` (max 10 chars, matching `NAME_REGEX`).
3. **`MainActivity.kt` (Splash Decoupling)**:
   - Remove `Class.forName("org.robolectric.Robolectric")` and `skipSplashForTesting` from production code.
   - Support test splash skipping via Intent extra `intent.getBooleanExtra("EXTRA_SKIP_SPLASH", false)` or test configuration so release code has zero reflection/hacks while Compose UI tests run smoothly.
4. **`app/build.gradle.kts`**:
   - Remove unused plugins `alias(libs.plugins.secrets)` and `alias(libs.plugins.google.services)` and their configurations.
   - Configure debug signing to check if `debug.keystore` exists: if present, use it; otherwise fall back to standard built-in debug config so fresh clones build debug immediately.
   - Update `signingConfigs.release` to only fail on release-packaging tasks if credentials are missing.
5. **Typography scaling**:
   - Raise 9–10sp text elements in `MainActivity.kt` (~line 4522) and `ChallengeUI.kt` (~lines 468, 513, 3683) to $\ge 11\text{sp}$.
- **Gate**: Run test suite.

### Step 5: Test Suite Expansion
Add comprehensive Robolectric and unit tests:
1. Golden round-trip parser tests (64 permutations + target 999 accepted + target 1000 rejected).
2. `sanitizePlayerName` tests for Unicode stripping, name length, and round-trip preservation.
3. `ChallengeStorage` tests: `recordMatchOutcome` (free-tries loss returns null, tie, win), `recordReplyOutcome`, `isCodeConsumed`, `isSelfChallenge`, FIFO caps (200 / 500), 30-day chain-target expiry, 3-day awaiting expiry, and orphan reconciliation.
4. `shouldUpdateSoloBest` truth table test + integration test with active challenge seed 88888.
5. Manifest reflection test asserting all 10 `configChanges` flags.
6. Playback concurrency UI test: starting a game, tapping Restart mid-playback with `autoAdvance = false`, advancing clock, asserting no `ConcurrentModificationException`.

### Step 6: Verification & Audit Report
- Run `./gradlew :app:assembleDebug` without signing environment variables.
- Run `./gradlew :app:testDebugUnitTest`.
- Run `./gradlew :app:lintRelease`.
- Inspect merged release manifest to verify zero `INTERNET` permission.
- Produce comprehensive release audit report detailing lines changed, test counts, and non-modified items (`allowBackup`, opt-in defaults, localization).
