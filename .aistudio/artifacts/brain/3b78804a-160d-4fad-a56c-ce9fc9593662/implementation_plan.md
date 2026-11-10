# PASS 5.36: Read-Only Sender Name in Share Dialog

This update removes the editable "Your Display Name" text field from `ShareTriesSelectorDialog`, replacing it with a clean, single-line read-only row ("Sending as: [NAME]") synced directly with `ChallengeStorage.getMyName(context)`. It also removes legacy dead name-setup branching from the share flow, while preserving name editing exclusively within the Settings sheet.

## User Review & Critical Decisions

> [!NOTE]
> All specifications for PASS 5.36 are well-defined and follow PASS 5.32 (where player name setup was unified into onboarding and the Settings sheet).

- **Sender Name Display**: Replaces the editable `share_sender_name_input` text field with a non-interactive text row styled with a `slate-400` (`#94A3B8`) 12sp label and a `White` (`#FFFFFF`) bold 14sp name tag.
- **Single Source of Truth**: Player name configuration remains exclusively in the IDLE Settings sheet. `ShareTriesSelectorDialog` directly queries `ChallengeStorage.getMyName(context)` (with built-in fallback `"HERO"`).
- **Dead Code Cleanup**: `requireNameSetupFirst` and the nested `ChallengerNameSetupCardContent` fallback inside `ShareTriesSelectorDialog` are removed because players are guaranteed to have a configured name from onboarding before reaching challenge sharing (score $\ge 5$).

---

## 1. Overview & Core Concept

- **What It Does**: When generating or copying a challenge code in `ShareTriesSelectorDialog`, players see a clean read-only confirmation of their sender name (`Sending as: ALEX`) rather than an editable text input.
- **Why It Matters**: Prevents accidental name changes or mismatched sender handles right before sharing. Ensures the player's identity remains consistent across duels, with name updates managed deliberately in Settings.
- **Scope**:
  - `ShareTriesSelectorDialog` in `ChallengeUI.kt`: remove text input, render read-only row, pass `ChallengeStorage.getMyName(context)` directly to card generation and clipboard copy.
  - `MainActivity.kt`: remove unused `requireNameSetupFirst` parameter in share dialog invocation.
  - `ExampleRobolectricTest.kt`: add tests for read-only row verification, zero editable fields, and settings rename synchronization.

---

## 2. User Experience & Visual Design

### Share Dialog Header & Read-Only Row

```
┌────────────────────────────────────────────────────────┐
│  Share Challenge                                   [✕] │
│                                                        │
│  Sending as: ALEX                                      │
│                                                        │
│  [Classic 4x4 • Score: 8]                              │
│                                                        │
│  TRIES:  [ 1 ]  [ 3* ]  [ 5 ]  [ Free ]                │
│                                                        │
│  [  ⚡ Generate Card  ]                                │
│  [  📋 Copy Code Only  ]                               │
└────────────────────────────────────────────────────────┘
```

- **Visual Hierarchy**:
  - Label: `"Sending as: "` in slate-400 (`#94A3B8`), 12sp, medium weight.
  - Name: `"[NAME]"` in white (`#FFFFFF`), 14sp, bold weight.
  - Container: Single-line row (`share_sending_as_row`) positioned right under the header/banner.
  - Zero affordance: No chevron, edit pencil, clickable ripple, or helper microcopy.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Removal of Inline Name Editing in Share Dialog**
  - *Chosen Approach*: Name cannot be edited inside `ShareTriesSelectorDialog`.
  - *Rationale*: Name editing belongs in the Settings sheet. Offering an editable text field inside the quick share dialog was redundant, caused confusion about whether it was a one-time alias or a permanent profile change, and risked accidental edits.
- **Decision 2: Direct Storage Read & Automatic Fallback**
  - *Chosen Approach*: Call `ChallengeStorage.getMyName(context)` directly.
  - *Rationale*: `getMyName` already returns the sanitized player name or defaults to `"HERO"`. By reading storage directly upon dialog opening, any rename performed in Settings immediately reflects in the share dialog on next open without requiring complex state plumbing.
- **Decision 3: Pruning Dead Branching in Share Flow**
  - *Chosen Approach*: Eliminate `requireNameSetupFirst` and inline `ChallengerNameSetupCardContent` from `ShareTriesSelectorDialog`.
  - *Rationale*: Since PASS 5.32, all players must either set a name or skip (defaulting to `"HERO"`) during first startup. Moreover, sharing is gated behind a score of 5, making zero-name share dialogs completely unreachable in production.

---

## 4. Technical Architecture & Component Mapping

```
┌─────────────────────────────────────────────────────────────────┐
│                       SettingsSheetDialog                       │
│  - Edit Player Name (Single Source of Truth)                    │
│  - Calls ChallengeStorage.setMyName(context, newName)           │
└────────────────────────────────┬────────────────────────────────┘
                                 │ updates
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ChallengeStorage                         │
│  - getMyName(context): String (sanitized, defaults to "HERO")   │
└────────────────────────────────┬────────────────────────────────┘
                                 │ reads
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ShareTriesSelectorDialog                     │
│  - Text: "Sending as: " + getMyName(context)                    │
│    (slate-400 12sp + white bold 14sp)                           │
│  - No OutlinedTextField (share_sender_name_input REMOVED)       │
│  - Buttons invoke onGenerateCard / onCopyOnly with getMyName()  │
└─────────────────────────────────────────────────────────────────┘
```

### Component Changes

1. **`app/src/main/java/com/example/challenge/ChallengeUI.kt`**:
   - In `ShareTriesSelectorDialog`:
     - Remove `requireNameSetupFirst: Boolean` parameter and `isConfiguringName` state.
     - Remove `senderNameInput` state and the `OutlinedTextField` with `testTag("share_sender_name_input")`.
     - Remove `ChallengerNameSetupCardContent` conditional branch inside the dialog.
     - Add the read-only row:
       ```kotlin
       val senderName = remember(context) { ChallengeStorage.getMyName(context) }
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .semantics(mergeDescendants = true) {}
               .testTag("share_sending_as_row"),
           verticalAlignment = Alignment.CenterVertically
       ) {
           Text(
               text = "Sending as: ",
               color = Color(0xFF94A3B8),
               fontSize = 12.sp,
               fontWeight = FontWeight.Normal
           )
           Text(
               text = senderName,
               color = Color.White,
               fontSize = 14.sp,
               fontWeight = FontWeight.Bold,
               modifier = Modifier.testTag("share_sending_as_name")
           )
       }
       ```
     - In `onGenerateCard` and `onCopyOnly` button click handlers, use `senderName` directly instead of `senderNameInput`.
2. **`app/src/main/java/com/example/MainActivity.kt`**:
   - At line 3153, remove `requireNameSetupFirst = !ChallengeStorage.hasConfiguredName(context)`.

---

## 5. Robolectric Test Plan

Add tests in `ExampleRobolectricTest.kt`:

- **Test A (`pass5_36_shareDialog_showsReadOnlySenderName_zeroEditableFields`)**:
  - Configure player name to `"TESTER"` in storage.
  - Trigger challenge share dialog from Game Over or Hall of Fame.
  - Assert `tries_selector_sheet` is displayed.
  - Assert `share_sending_as_row` is displayed and contains `"Sending as: TESTER"`.
  - Assert `share_sender_name_input` does NOT exist (`assertDoesNotExist()`).
  - Assert no editable `TextField` exists in `tries_selector_sheet`.
- **Test B (`pass5_36_renamingInSettings_reflectsInShareDialogOnNextOpen`)**:
  - Start with name `"ORIGINAL"`.
  - Open Settings sheet, enter `"RENAMED"`, and save.
  - Open challenge share dialog.
  - Assert `share_sending_as_row` displays `"Sending as: RENAMED"`.
- **Regression Verification**:
  - Run `gradle :app:testDebugUnitTest` to verify all 101 tests pass (99 existing + 2 new).
