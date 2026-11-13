# 🧠 GlowBack

**Watch the glow. Send it back.**

GlowBack is a neon sequence memory game for Android featuring a 100% offline, zero-backend asynchronous multiplayer rivalry engine. Memorize expanding neon sequences across multiple grid sizes, challenge friends via compact deterministic codes, track head-to-head rivalries, and master progressive speeds with low-latency audio-haptic feedback.

---

## ✨ Features

### 🔲 Multi-Grid Neon Sequence Engine
- **4 Grid Layouts:** Play on **2×2** (4 pads), **3×3** (9 pads), **4×4** (16 pads), or **5×4** (20 pads) configurations.
- **Visual & Audio Uniqueness:** Every pad features a unique geometric neon symbol (circles, diamonds, hexagons, crescents, stars) and a dedicated musical frequency tuned for psychoacoustic clarity.
- **Dual Paces:** 
  - *Classic:* Steady, rhythmic 400ms interval pace.
  - *Progressive Speed:* Sequence playback dynamically accelerates as round lengths increase.
- **Dual Difficulties:**
  - *Easy Mode:* Classic cumulative memory—each round appends one new step to the previous sequence.
  - *Hard Mode:* Generates a completely new, unpredictable sequence of target length on every round.
- **Local 2-Player Versus:** Pass-and-play multiplayer mode on a single device with round-by-round scorekeeping.

### ⚔️ Asynchronous Peer-to-Peer Rivalry System (Zero Server / Zero Cloud)
- **Deterministic Challenge Codes (T-Codes):** Generate compact, checksum-protected codes (e.g., `4x4-6-T3E-ALEX-...`) at Game Over or from any qualifying high score. Challenge a friend to beat your exact sequence on the identical random seed.
- **Direct Reply Codes (R-Codes):** Challengers play the seed and mint an authenticated reply code (e.g., `4x4-7-R3E-HERO-...`) encoding their score back to the original challenger.
- **Comprehensive Rivalry Hub (Hall of Fame):**
  - Track lifetime head-to-head records against each friend: **Wins, Losses, Ties, and Total Matches**.
  - **Scrollable Per-Grid Stat Chips:** View your performance breakdown across all grids (`2×2: 1W - 0L`, `3×3: 0W - 1L`, etc.) in a single-line horizontal chip selector.
  - **State-Aware Reply Presentation:** Active replies display a prominent *"Send Reply to [Rival]"* button; once copied, it collapses into an elegant *"✓ Reply copied X ago · copy again"* indicator.
  - **Per-Grid Reply Storage:** Stores the latest reply code independently per grid, automatically switching codes as you select different grid chips.
  - **Quick Actions:** Initiate **Tie-Breaker** rematches, **Revenge** games, or new challenges directly from the rival card.

### 🎯 Try-Preserving Challenge Mechanics
- **Configurable Attempts:** Challenges can be configured for 1, 3, 5, or Free (unlimited) tries.
- **Ready Gate:** Re-arms before each attempt with a 3-second countdown preview of the exact sequence seed.
- **Try-Preserving Exit on Tie:** Tying a rival's target score with tries remaining presents 3 choices:
  - *Keep Playing:* Push for the outright win.
  - *Lock Tie & Exit:* Finalize the match immediately as a tie.
  - *Forfeit Try & Exit:* Forfeit the current try (recording 0 for that attempt) while preserving remaining attempts to resume later via the rival's *"Resume Challenge"* card.
- **Best-Score Invariant:** Final match outcomes always evaluate the highest score achieved across all non-forfeited attempts.

### 🔒 100% Offline, Private & Zero-Permission
- **Zero Internet Required:** Completely serverless. No user accounts, logins, telemetry, ads, or background network calls.
- **Local Storage Only (No Cloud Backup or Device Transfer):** Game records, rival profiles, and settings reside strictly on-device in Android `SharedPreferences`, with both Android Auto Backup (`cloud-backup` / `full-backup-content`) and device-to-device transfer (`device-transfer`) excluded so no data ever leaves the device.
- **Automatic Hygiene Sweeps:** 30-day lifecycle purges obsolete reply codes without bloating device storage.

### 🔊 Psychoacoustic Audio & Haptic Synthesis
- **Low-Latency Synthesis:** Pure PCM sine tone synthesis generated in real time using Android `AudioTrack` with psychoacoustic odd harmonics for rich tone even through device speakers.
- **Tactile Haptics:** Custom `VibrationEffect` waveforms mapped to pad taps, streak milestones, game-over buzzes, and victory pulses.
- **Quick Mute:** Dedicated header toggle to instantly silence sound and vibration.

### 🔔 Smart Offline Notifications
- Opt-in daily reminders scheduled locally via Android `WorkManager`.
- Context-aware notification copy highlighting unplayed challenges or personal best streaks without draining battery.

---

## 🔐 Challenge Code Specification

Challenges and replies operate entirely offline using deterministic string tokens:

| Type | Format Pattern | Example | Purpose |
| :--- | :--- | :--- | :--- |
| **Challenge (T-Code)** | `[GRID]-[TARGET]-T[TRIES][DIFF]-[SENDER]-[CHAIN]-[SEED]-[CKSUM]` | `4x4-6-T3E-ALEX-DG9A-Q579D-B` | Sent to a friend to challenge them to beat score 6 on seed Q579D |
| **Reply (R-Code)** | `[GRID]-[SCORE]-R[TRIES][DIFF]-[SENDER]-[CHAIN]-[SEED]-[CKSUM]` | `4x4-5-R3E-HERO-DG9A-Q579D-B` | Returned to the sender with the challenger's final score |

- **Deterministic Pseudo-RNG:** The encoded seed recreates the exact pad sequence on the opponent's device.
- **Tamper Proof:** Reversible Base32/Base36 encoding coupled with a custom 7-segment checksum validates code integrity and detects corrupted or manually altered codes with 0ms local validation.

---

## 📥 How to Install (APK)

1. Go to the **[Releases](https://github.com/V-Poovarasan-Github/GlowBack-Game/releases/tag/v1.0.0)** section of this repository.
2. Download the latest `app-release.apk` file.
3. On your Android device, allow "Install from Unknown Sources" when prompted.
4. Tap the downloaded file to install and start playing!

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin 100% (Modern coroutines & Flow)
- **UI Framework:** Jetpack Compose with Material Design 3 (M3)
  - Dark-mode neon palette (`#00E5FF` Cyan, `#10B981` Emerald, `#F59E0B` Amber, `#EF4444` Crimson)
  - Edge-to-edge window insets support
  - Adaptive components: `LazyRow` horizontal selectors, custom modal dialogs, and animated canvas pads
- **Architecture:** Clean MVVM with Unidirectional Data Flow (UDF)
- **State & Local Persistence:** SharedPreferences with strict JSON serialization, migration fallbacks, and automatic sweeps
- **Audio & Haptics:**
  - Android `AudioTrack` direct PCM synthesis with static tone pool pre-warming
  - Android `Vibrator` / `VibrationEffect` with fallback for legacy API levels
- **Background Scheduling:** Android Jetpack `WorkManager` for battery-efficient offline local reminders

---

## 🧪 Testing & Quality Assurance

GlowBack maintains a local test suite with **107 passing unit and Robolectric tests** executed on local JVM:

```bash
gradle :app:testDebugUnitTest
```

- **92 Robolectric UI & Integration Tests (`ExampleRobolectricTest`):** Covers all user journeys from first install onboarding, sequence play, audio engine, Game Over, rival creation, multi-try challenge lifecycle, 3-button tie forfeit states, chip selectors, accessibility/TalkBack semantics, and settings synchronization.
- **14 Deterministic Seed & Code Tests (`ChallengeSeedTest`):** Tests seed generation, bidirectional code formatting/parsing, checksum validation, tampering detection, and roundtrip serialization.
- **1 Unit Test (`ExampleUnitTest`):** Core configuration validation.

---

## 📜 Privacy & License

- **Privacy Policy:** See [PRIVACY.md](PRIVACY.md). GlowBack collects zero personal data, includes zero analytics trackers, and requires no network permissions.
- **License:** Open source and available under the [MIT License](LICENSE).
