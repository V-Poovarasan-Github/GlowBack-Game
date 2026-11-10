# PASS 5.33: Final Multiplayer Robustness & Rivalry UX Pass (Multi-Pending Support)

Architectural implementation plan for PASS 5.33, incorporating multi-pending outbound support per rival, lenient backward-compatible JSON migration, DidYouShare flow integration, comprehensive `{3,5}` regex relaxation, and strict recommendation sort preservation.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions and refinements are confirmed:
> - **CATCH 1 - Multi-Pending Support**:
>   - `RivalRecord.pendingOutbounds: List<PendingOutboundChallenge> = emptyList()` replaces single `pendingOutbound`.
>   - **Lenient Migration**: Parser handles absent (empty list), legacy single object `pendingOutbound` (one-element list), and `pendingOutbounds` JSON array.
>   - Multiple simultaneous unreplied targeted shares to the same rival (e.g. across different grids or difficulties) are retained without overwriting.
>   - **Card Banner Display**: Shows the MOST RECENT pending entry (sorted by `mintedAt` descending), with a `"+N"` count badge when additional pending challenges exist (e.g. `"+1 more"`).
>   - **Per-Entry Expiry Sweeping**: `sweepExpiredPendingOutbounds` evaluates each entry in `pendingOutbounds`, purging only entries older than 7 days from `mintedAt` (removing both the list entry and its `(rival, grid, difficulty, seed)` ledger entry).
>   - **Reply Matching**: Incoming reply resolution matches by `seed` and `chainId`, removing only the completed pending entry from the list and leaving other pending entries intact.
>   - **SIM5 Extension**: Test covers two simultaneous pending targeted shares to the same rival; advances clock +7 days past the first only; asserts first ledger entry is purged and re-recommended while the second pending banner remains active.
> - **Migration Safety**: `AwaitingChallenge.rivalName` parses with `optString("rivalName", null)`. Legacy awaiting challenges continue as broadcast and expire via the 3-day TTL.
> - **DidYouShare Flow**: Targeted mints tag `rivalName` on `AwaitingChallenge` so the global section hides them while the rival card tracks them. Broadcast mints set `rivalName = null`.
> - **Sort + Regex Scope**: Recommendation comparator (`win-margin desc -> rival.lastGrid -> score desc`) is strictly preserved. Every chain segment regex in the codebase is relaxed to `{3,5}`.
> - **SIM4 Chain Target 31-Day Expiry Outcome**: Graceful dialog explaining the challenge expired over 30 days ago, with no crashes or corruption.
> - **Idempotent Re-copy**: Re-copying code copies to clipboard with toast notification without duplicating ledger writes.

---

## 1. Overview & Core Concept

PASS 5.33 establishes the final production polish for Glowback's asynchronous peer-to-peer multiplayer rivalry system:
- **Ghost Unlock**: Unlocks boards in `KEY_SENT_RECOMMENDATIONS` when targeted pending shares pass 7 days without a reply or when a rival card is deleted. Broadcast mints do not write at send time. Completed matches retain suppression permanently.
- **Multi-Pending Outbound Tracking**: Allows multiple active targeted challenges to the same rival. Displays the newest on the card with a `+N` badge, and resolves or expires each entry independently.
- **4-Character Chain ID Hardening**: Upgrades `ChallengeSeed.generateChainId()` to output 4 characters, while maintaining full support for 3-character legacy chains and `{3,5}` regex extraction across all regex patterns.
- **Share Clarity**: Renames Rivalry top action to "Share to Everyone" and presents a rival-targeted explanatory banner inside targeted share dialogs.
- **Adaptive Step Indicator**: Dot indicator for `totalSteps <= 8`; compact numeric pill `[ completedSteps / totalSteps ]` for `totalSteps >= 9` (slate for watching, green for playing).
- **Two-Line Recommendation Chips**: Full-bleed horizontal `LazyRow` inside dialogs with bold grid/difficulty, `"vs Y"` badge, `"You X"` score, and trailing share glyph.
- **Robolectric Simulation Tests (SIM1–SIM5)**: Comprehensive test suite validating full duel lifecycles, force-kills, broadcast completion hooks, 31-day chain target expirations, and the multi-pending 7-day ghost sweep flow.

---

## 2. User Experience & Visual Design

### A. Rival Card With Multi-Pending Outbound Banner

```
CARD WHILE PENDING OUTBOUND CHALLENGES (e.g. 2 active):
┌───────────────────────────────────────────────────────────┐
│ ⚔ ALEX                                              🗑    │
│ 1W • 1L • 1T  (3 matches)                                 │
│ [3×3: 1W]  [4×4: 1T]                                      │
│ ┌───────────────────────────────────────────────────────┐ │
│ │ ⏳ AWAITING REPLY • sent 2h ago             [+1 more] │ │
│ │ 4×4 Easy • Target 6 • 3 tries                         │ │
│ └───────────────────────────────────────────────────────┘ │
│ [ 📋 Paste Reply Code ]            [ 🔁 Re-copy Code ]    │
└───────────────────────────────────────────────────────────┘

CARD AFTER ALL REPLIES RESOLVED:
┌───────────────────────────────────────────────────────────┐
│ ⚔ ALEX                                              🗑    │
│ 2W • 1L • 1T  (4 matches)                                 │
│ Last: 4×4 Easy   Target 6 vs You 4 [WON]                  │
│ [ ⚡ Get Revenge ]                   [ 📋 Paste Code ]      │
└───────────────────────────────────────────────────────────┘
```

### B. Global Awaiting List (Broadcast Only)

```
AWAITING CHALLENGES (1)
┌───────────────────────────────────────────────────────────┐
│ ❓ Unknown challenger                                      │
│ 3×3 Easy • Target 9 • sent 1d ago                         │
└───────────────────────────────────────────────────────────┘
(Only entries where awaiting.rivalName == null appear here)
```

### C. Share Clarity & Targeted Dialog Note

```
RIVALRY TOP ACTION BAR:
[ 🌍 Share to Everyone ]             [ 📋 Paste Code ]

TARGETED SHARE DIALOG NOTE:
┌───────────────────────────────────────────────────────────┐
│ Share Challenge to ALEX                                ✕  │
│ ┌───────────────────────────────────────────────────────┐ │
│ │ ℹ This code only works for ALEX.                      │ │
│ │   New challenger? Use 🌍 Share to Everyone.           │ │
│ └───────────────────────────────────────────────────────┘ │
│ Target Score: [ 4×4 Easy · You 9 ]                        │
│ Tries: [1]  [3]  [5]  [Free]                              │
│ [ 🎴 Generate Card ]                 [ 📋 Copy Code ]     │
└───────────────────────────────────────────────────────────┘
```

### D. Progress Indicator (Dots vs Pill)

- **Total Steps <= 8 (e.g. Round 4, 2 correct)**:
  `● ● ○ ○`
- **Total Steps >= 9 (e.g. Round 12, watching sequence)**:
  `[ 0 / 12 ]` (Slate pill: `#1E293B` container, `#334155` border, `#94A3B8` text)
- **Total Steps >= 9 (e.g. Round 12, player turn, 5 correct)**:
  `[ 5 / 12 ]` (Green pill: `#064E3B` container, `#10B981` border, `#34D399` text)

### E. Recommendation Chip Redesign (Two-Line Full Bleed)

```
┌───────────────────────────────────────────────────────────┐
│ 🎯 RECOMMENDATION                                         │
│ You already beat ALEX here - send it now:                 │
│ ┌───────────────────┐ ┌───────────────────┐               │
│ │ 4×4 Easy     [vs5]│ │ 3×3 Hard     [vs9]│  ───►         │
│ │ You 6           ↗ │ │ You 14          ↗ │  scroll       │
│ └───────────────────┘ └───────────────────┘               │
└───────────────────────────────────────────────────────────┘
```
- Full-bleed horizontal `LazyRow` inside the dialog.
- Two-line chips, min width `150.dp`.
- Line 1: `Row` with bold grid + difficulty, and top-right `"vs Y"` badge.
- Line 2: `Row` with bold `"You X"` score and trailing share glyph.
- Preserves the recommendation comparator: margin desc -> `rival.lastGrid` matching -> score desc.

---

## 3. Technical Architecture & Implementation Steps

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MULTI-PENDING ARCHITECTURE                      │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   Targeted Share Mint                     Broadcast Share Mint         │
│   (lockedRivalName != null)               (lockedRivalName == null)    │
│          │                                         │                   │
│          ▼                                         ▼                   │
│   ┌──────────────────────────────┐        ┌───────────────────────┐    │
│   │ RivalRecord.pendingOutbounds │        │ AwaitingChallenge     │    │
│   │ List<PendingOutboundChallenge│        │ {rivalName = null}    │    │
│   │ (Appends new challenge)      │        └───────────┬───────────┘    │
│   └──────────────┬───────────────┘                    │                │
│                  │                                    ▼                │
│   Writes to:     │                         Global Awaiting Section     │
│   KEY_SENT_REC   │                         (rivalName == null only)    │
│   (Suppresses)   │                                                     │
│                  ▼                                                     │
│         RivalCard Display                                              │
│         [Most Recent Banner + Badge]                                   │
│         [Paste Reply] [Re-copy]                                        │
│                  │                                                     │
│      ┌───────────┴───────────────┐                                     │
│      │                           │                                     │
│  7-Day Expiry (Per Entry)   Reply Resolved (Matched by seed/chain)     │
│      ▼                           ▼                                     │
│  Ghost Unlock              Match Completed                             │
│  - Remove expired entry    - Remove matched entry                      │
│  - Evict KEY_SENT_REC      - Keep other pendings                       │
│  - Re-recommend board      - Keep KEY_SENT_REC, record Win/Loss/Tie    │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

### Component & File Scope

1. **`ChallengeModels.kt`**:
   - `data class PendingOutboundChallenge(val gridTag: String, val difficulty: GameDifficulty = GameDifficulty.EASY, val target: Int, val tries: Int = 3, val code: String, val mintedAt: Long = System.currentTimeMillis(), val rivalName: String, val seed: Int = 0, val chainId: String = "")`
   - Update `RivalRecord`: replace `pendingOutbound: PendingOutboundChallenge?` with `val pendingOutbounds: List<PendingOutboundChallenge> = emptyList()`.
   - Update `AwaitingChallenge`: add `val rivalName: String? = null`.
2. **`ChallengeStorage.kt`**:
   - Lenient JSON parser for `RivalRecord`: parses `pendingOutbounds` array if present; falls back to legacy single object `pendingOutbound`; defaults to empty list.
   - Serializer writes `pendingOutbounds` JSON array.
   - Add `addRivalPendingOutbound(context, rivalName, pending)`.
   - Add `removeRivalPendingOutbound(context, rivalName, chainId, seed)`.
   - Add `removeSentRecommendation(context, rivalName, gridTag, difficulty, seed)`.
   - Update `sweepExpiredPendingOutbounds(context, now)`: iterates all rivals, filters each rival's `pendingOutbounds`, evicts entries where `now - mintedAt >= 7 * 24 * 60 * 60 * 1000L` from both the list and `KEY_SENT_RECOMMENDATIONS`.
   - Update `deleteRival(context, rivalName)`: purges all unreplied pending entries in that rival's `pendingOutbounds` from `KEY_SENT_RECOMMENDATIONS`.
   - On reply match in `recordMatchResult` / chain target lookup: remove the matched pending challenge from `pendingOutbounds`.
   - Guard send-time writes: broadcast shares (`rivalName == null`) do NOT write to `KEY_SENT_RECOMMENDATIONS`.
   - Graceful 30-day chain target expiration handling.
3. **`ChallengeSeed.kt`**:
   - Update `generateChainId()` to output 4 characters: `(1..4).map { ALPHABET[...] }`.
   - Relax all chain segment regexes (e.g. `V2_EXTRACTION_REGEX`, etc.) to `{3,5}`.
4. **`ChallengeUI.kt`**:
   - In `RivalCard`: When `rival.pendingOutbounds.isNotEmpty()`, pick the most recent entry (`maxByOrNull { it.mintedAt }`). Display awaiting banner with age, target, tries, and if `rival.pendingOutbounds.size > 1`, display `"+${rival.pendingOutbounds.size - 1} more"` badge. Action buttons: `[Paste Reply Code]` and `[Re-copy]`.
   - In `RivalryTabContent`: Rename top button to `"Share to Everyone"`. Filter global awaiting challenges to `it.rivalName == null`.
   - In `RivalMatchSetupDialog`: Two-line horizontal `LazyRow` recommendation chips preserving comparator order.
   - Targeted share dialog: Display the rival-targeting note banner (`"ℹ This code only works for {RIVAL}. New challenger? Use 🌍 Share to Everyone."`).
5. **`MainActivity.kt`**:
   - Update `SequenceStepIndicator`: dots for `<= 8`, compact pill for `>= 9`.
   - Implement `[Re-copy]` action handling (copies most recent code to clipboard with toast, no duplicate ledger write).
   - Trigger `sweepExpiredPendingOutbounds` on Rivalry tab load and sleeping score checks.
6. **`ExampleRobolectricTest.kt`**:
   - Implement SIM1 (full duel lifecycle), SIM2 (force-kill loss reconciliation), SIM3 (broadcast completion hook), SIM4 (31-day chain target expiration), SIM5 (7-day ghost unlock flow + SIM5 extension with two pending targeted shares).
   - Verify `gradle :app:testDebugUnitTest`.
