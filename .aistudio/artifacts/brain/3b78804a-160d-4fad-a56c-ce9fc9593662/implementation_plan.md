# PASS 5.19 — Async Challenge Robustness Pass & 4-Player Mesh Architecture

This plan hardens the offline peer-to-peer Async Challenge lifecycle against state bleed, duplicate/forged replies, group-broadcast collisions, and mid-game interruptions, while visually separating Challenge gameplay from Solo gameplay for maximum board space on `5×4` grids.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All Stage A, Stage B, Phase 1 interactive clarification choices, and the mandatory **Per-Rival Sub-Chain Forking (`chainId:seedInt`)** enhancement are locked in for execution.

- **Confirmed Decision 1 (`findInProgressChallenge` Matching)**: Match in-progress challenges strictly by **exact `code` string or `seedInt`**, never by `chainId`. Pasting a new T-code from a rival while an older challenge from that rival is still unfinished replaces the old in-progress state and displays: `"Replaced your unfinished challenge from [NAME]"`.
- **Confirmed Decision 2 (Single Share Flow, Two Outputs)**: Remove the standalone `"Copy Challenge"` button from the Solo Game Over screen. Tapping `"Share Challenge"` opens `ShareTriesSelectorDialog`; after generating the challenge, the result view presents both **"Share"** (PNG Trophy Card + 7-segment code caption) and **"Copy Code"** (raw text to clipboard).
- **Confirmed Decision 3 (Post-Challenge Exit Destination)**: Exiting a **finished** challenge (`WON`, `TIED`, or `LOST` with `0` tries remaining) navigates directly to **Leaderboard → Rivalry**, whereas exiting a normal Solo run stays on Home.
- **Confirmed Decision 4 (Group Broadcast 30-Day Chain Retention + Per-Rival Sub-Chain Forking)**: Keep outbound target entries in `KEY_CHAIN_TARGETS` for a **30-day hard TTL** (capped at 100 entries, FIFO), keyed by **`(chainId + ":" + seedInt)`** (with `chainId` fallback) so simultaneous revenge rallies with multiple group members on the same `chainId` never overwrite each other's target scores. Individual R-codes are consumed in `KEY_CONSUMED_CODES` (cap raised to 500, FIFO).
- **Confirmed Decision 5 (Same-Name Second Reply Disambiguation)**: When an inbound R-code matches an existing rival card's name **and** that rival already has a recorded outcome for the same challenge (`chainId:seedInt`), show an explicit two-choice prompt: `"Two different replies from [NAME] for the same challenge."`
  - **`[Same person]`**: Merge onto the existing rival card and record the match.
  - **`[Different person]`**: Require a pre-focused Custom Rival Name input and create a separate rival card before recording.
- **Confirmed Decision 6 (Tie on Try 1 with Tries Remaining)**: When a responder ties the target (`bestScore == targetScore`) with tries still remaining (`triesLeft > 0`), display:
  - Banner: `"Target tied (Best: N)! Retry to beat it for the WIN, or send the tie now."`
  - Primary Button: **`Retry Turn (X tries left)`**
  - Secondary Button: **`Send Tie to Rival`** (finalizes the match immediately as `TIED`, forfeits remaining tries, consumes the challenge, and generates/sends the R-code)
  - Tertiary Button: **`Exit Challenge`**

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates state leakage between Solo mode and Async Challenge mode, enforces strict state validation on 7-segment `T`-codes and `R`-codes across multi-player group chats using `(chainId:seedInt)` sub-chain keys, and introduces a unified, space-efficient Challenge HUD with a player-consent start gate.
- **Target Audience / Persona**: Competitive friends rallying back-and-forth or broadcasting challenges inside WhatsApp/Telegram group chats completely offline.
- **Key Value**: Zero lost replies, zero accidental re-triggers of completed codes, full vertical space for `5×4` boards, and deterministic head-to-head records across 1-on-1 rallies and 4-player group broadcasts.

---

## 2. User Experience & Visual Design

1. **Unified 2-Row Challenge Header (Space Optimization for `5×4`)**:
   - **Line 1**: `● RIVAL: ALEX   •   TARGET: 5   •   TRIES: 1`
   - **Line 2**: `5×4 · CLASSIC   •   ⚡ SCORE: 0   •   HARD`
   - **Dynamic Milestone Border & Accent**: Cyan (`#00E5FF`) when chasing, Amber/Gold (`#FFD600`) when tied, Neon Green (`#00E676`) when beaten.
2. **Round-vs-Score Clarity**:
   - Status subtitle explicitly states what clearing the current round accomplishes (`"Round 6 • Clear this round to TIE Target (6)"`, `"Round 7 • Clear this round to WIN (7 vs 6)"`).
3. **"Ready?" Consent Gate (Round 1 & Resume Only)**:
   - Starting Try 1, retrying a turn, starting a Revenge run, or resuming an interrupted challenge pauses in a `"READY vs [RIVAL]? • TAP WHEN READY"` state before flashing Pad 1.
4. **Context-Pure Game Over Actions**:
   - Failed try (`bestScore < target`, `triesLeft > 0`): `Retry Turn` + `Exit Challenge` (no `Share Score`).
   - Tied try (`bestScore == target`, `triesLeft > 0`): `Retry Turn` + `Send Tie to Rival` + `Exit Challenge`.
   - Finished challenge: `Send Victory / Tie / Result to Rival` (with Double-Send Guard) + `Exit Challenge` (navigates to Leaderboard → Rivalry).
   - Solo Game Over: `Play Again` | `Home` + single `Share Challenge` button with post-generation `Share` and `Copy Code` actions.
