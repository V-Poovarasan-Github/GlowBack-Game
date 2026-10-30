# Simon Says: Neon Sequence Memory Game

A modern, high-contrast "Simon Says" memory challenge built entirely with Jetpack Compose, featuring zero external assets, tactile audio-haptic feedback, persistent high scores, and a hybrid pace mode selector.

## User Review & Critical Decisions

> [!IMPORTANT]
> Below is a summary of your confirmed preferences from our design alignment:

- **Persistent Best High Score**: Best scores are automatically persisted across app restarts using `SharedPreferences` (Separate high scores tracked for Classic Fixed and Progressive Speed modes, or unified best score display).
- **Hybrid Sequence Playback Pace**: Defaults to the classic fixed pace (`400ms` flash, `200ms` gap) with an interactive mode selector allowing players to switch between **Classic (400ms/200ms)** and **Progressive Speed** (starts at `400ms/200ms` and gradually tightens as rounds increase).
- **Audio & Tactile Feedback**: Combines distinct `android.media.ToneGenerator` DTMF tones per color pad (plus a victory chime on round completion and a distinct error tone on a wrong press) with `LocalHapticFeedback` vibration pulses, plus a quick sound/haptics mute toggle in the top header.
- **Self-Contained Architecture**: All game state, audio synthesis, persistence, and Compose UI components are delivered in a single, complete, zero-asset `MainActivity.kt` file.

---

## 1. Overview & Core Concept

- **What It Does**: Challenges players to watch, memorize, and repeat an ever-growing sequence of illuminated color pads (`Green`, `Red`, `Yellow`, `Blue`), testing working memory and reaction accuracy.
- **Target Audience / Persona**: Casual mobile gamers and puzzle enthusiasts looking for an instant-launch, zero-friction arcade memory challenge with crisp visual and audio-tactile feedback.
- **Key Value**: Delivers a responsive arcade experience using pure Compose canvas/shape rendering and native Android tone generation—requiring zero external drawables or sound files.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Idle / Ready State**: The player sees the current score (`0`), their persisted Best High Score, the Hybrid Pace selector (`Classic 400ms` vs. `Progressive`), a status banner reading *"Press Start to Play"*, the dimmed 2x2 neon pad grid, and a prominent **Start Game** button at the bottom.
  2. **Watching State (`WATCHING`)**: Status banner updates to *"Watch Carefully!"* with a step progress indicator. Input on the 2x2 grid is strictly locked. A coroutine steps through the sequence, illuminating each pad with a glowing radial bloom, scale spring animation, haptic tick, and its unique tone (`400ms` flash, `200ms` pause in Classic mode, or scaled in Progressive mode).
  3. **Playing State (`PLAYING`)**: Status banner updates to *"Your Turn!"* with a progress dots/counter bar showing how many steps of the current round have been entered. Tapping a pad flashes it immediately for visual/audio confirmation. Completing the full sequence increments the score, updates the Best Score if beaten, pauses briefly, and advances to the next round with 1 new random color appended.
  4. **Game Over State (`GAME_OVER`)**: Tapping an incorrect color immediately triggers a low error buzzer tone, heavy haptic feedback, and switches the status banner to *"Game Over!"* showing the final score achieved and a prominent **Try Again / Play Again** button at the bottom.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Retro-Futuristic Neon Arcade**—deep slate/charcoal surfaces contrasted with luminous neon color pads that bloom when active.
  - *Color Palette & Mood*:
    - `--background`: Deep Slate Charcoal (`#0F172A` to `#1E293B` radial vignette)
    - `--surface`: Elevated Slate Card (`#1E293B`) with subtle border highlights (`#334155`)
    - `GREEN` Pad: Dim `#064E3B` / Bright Neon `#10B981` (Glow `#34D399`)
    - `RED` Pad: Dim `#7F1D1D` / Bright Neon `#EF4444` (Glow `#F87171`)
    - `YELLOW` Pad: Dim `#78350F` / Bright Neon `#F59E0B` (Glow `#FBBF24`)
    - `BLUE` Pad: Dim `#1E3A8A` / Bright Neon `#3B82F6` (Glow `#60A5FA`)
  - *Typography & Hierarchy*: Bold geometric monospace/sans-serif numerals for Score and Best Score counters; high-contrast uppercase tracking for status headers (*"WATCH CAREFULLY!"*, *"YOUR TURN!"*, *"GAME OVER!"*).
  - *Component Styling & Layout*: Edge-to-edge layout respecting system bar safe areas (`WindowInsets.safeDrawing`), responsive `BoxWithConstraints` 2x2 square grid with generous `24.dp` rounded corners, inner specular highlights, and dynamic elevation/shadow glow when active.

- **Interactive Feedback & Motion**:
  - Animated scale (`animateFloatAsState` with `spring()`) and color transitions (`animateColorAsState`) when pads flash.
  - Instantaneous user tap flash (`180ms` coroutine pulse) during `PLAYING` state so fast consecutive taps feel snappy and responsive.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Hybrid Sequence Pace (Classic Fixed + Progressive Option)**
  - *Chosen Approach*: Default to the exact prompt-specified `400ms` flash / `200ms` gap timing (`Classic` mode), while offering a one-tap chip to switch to `Progressive` mode (which starts at `400ms` / `200ms` and accelerates by `15ms` per round down to a `220ms` / `110ms` floor).
  - *Why*: Guarantees strict compliance with the 400ms/200ms specification out of the box while fulfilling the hybrid speed progression preference for deeper replayability.
- **Decision 2: Native `ToneGenerator` Lifecycle Management**
  - *Chosen Approach*: Encapsulate `ToneGenerator(AudioManager.STREAM_MUSIC, 90)` inside a `DisposableEffect` helper with defensive `try/catch` blocks and clean `.release()` on disposal.
  - *Why*: Prevents native audio resource leaks across activity recreation or device rotation and gracefully handles devices where audio hardware is muted or unavailable.
- **Decision 3: Persistent High Score via `SharedPreferences`**
  - *Chosen Approach*: Store and load the `best_high_score` key using Android's built-in `Context.getSharedPreferences` directly inside the single file.
  - *Why*: Requires zero external database setup or extra files while ensuring the player's Best High Score survives app restarts.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                           MainActivity.kt                            │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │               SimonSaysGameScreen (State Holder)               │  │
│  │  • GameState: IDLE | WATCHING | PLAYING | GAME_OVER            │  │
│  │  • PaceMode: CLASSIC (400/200ms) | PROGRESSIVE (Dynamic)       │  │
│  │  • Sequence List<SimonColor>, PlayerIndex, Current & BestScore │  │
│  └───────────────┬────────────────────────────────┬───────────────┘  │
│                  │                                │                  │
│                  ▼                                ▼                  │
│  ┌───────────────────────────────┐  ┌─────────────────────────────┐  │
│  │   Effects & Native Services   │  │     Compose UI Hierarchy    │  │
│  │  • LaunchedEffect(gameState,  │  │  • Score & Pace Header Card │  │
│  │    round): Plays AI sequence  │  │  • Status Banner & Progress │  │
│  │    with 400ms/200ms delays    │  │  • 2x2 Neon Pad Grid        │  │
│  │  • ToneSoundPlayer (DTMF 1-4  │  │    (Input locked in         │  │
│  │    + Error tone, Disposable)  │  │     WATCHING/IDLE/GAME_OVER)│  │
│  │  • LocalHapticFeedback        │  │  • Start / Play Again CTA   │  │
│  │  • SharedPreferences (Score)  │  │                             │  │
│  └───────────────────────────────┘  └─────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- `enum class GameState { IDLE, WATCHING, PLAYING, GAME_OVER }`
- `enum class PaceMode { CLASSIC, PROGRESSIVE }`
- `enum class SimonColor(val label: String, val dimColor: Color, val brightColor: Color, val glowColor: Color, val toneType: Int)`:
  - `GREEN`: `ToneGenerator.TONE_DTMF_1`
  - `RED`: `ToneGenerator.TONE_DTMF_3`
  - `YELLOW`: `ToneGenerator.TONE_DTMF_7`
  - `BLUE`: `ToneGenerator.TONE_DTMF_9`
  - Error Tone: `ToneGenerator.TONE_SUP_ERROR` (or `TONE_CDMA_ABBR_ALERT`)

### Interactive Component & State Mapping

- **Start / Play Again Button (`start_game_button`)**:
  - Resets `currentScore = 0`, clears `sequence`, appends 1 random `SimonColor`, sets `playerStepIndex = 0`, and transitions `gameState` to `WATCHING`.
- **Sequence Playback Coroutine (`LaunchedEffect(gameState, sequence.size)`)**:
  - Runs only when `gameState == GameState.WATCHING`.
  - Waits `500ms` before starting the round, then iterates over `sequence`:
    - Sets `activeColor = color`, plays tone + haptic tick, delays `flashDurationMs` (`400ms` in Classic).
    - Sets `activeColor = null`, delays `gapDurationMs` (`200ms` in Classic).
  - Transitions `gameState` to `GameState.PLAYING` and resets `playerStepIndex = 0`.
- **Color Pad Tap Handler (`pad_green`, `pad_red`, `pad_yellow`, `pad_blue`)**:
  - Strictly enabled **only** when `gameState == GameState.PLAYING`.
  - Launches a short visual flash (`activeColor = tappedColor` for `180ms`) and triggers tone + haptic feedback.
  - Validates `tappedColor == sequence[playerStepIndex]`:
    - **Match**: Increments `playerStepIndex`. If `playerStepIndex == sequence.size`, increments `currentScore`, updates & saves `bestScore` in `SharedPreferences` if `currentScore > bestScore`, appends a new random `SimonColor` to `sequence`, and sets `gameState = GameState.WATCHING`.
    - **Mismatch**: Plays error tone (`350ms`), triggers long-press haptic feedback, sets `activeColor = null`, and transitions `gameState` immediately to `GameState.GAME_OVER`.
