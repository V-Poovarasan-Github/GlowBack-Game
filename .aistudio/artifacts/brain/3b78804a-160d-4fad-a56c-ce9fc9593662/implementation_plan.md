# Glowback — `GlowAudioSynthesizer` Android 12+ Pad Tone Reliability Plan

This plan implements the exact 7 required changes in `GlowAudioSynthesizer` (`MainActivity.kt`), hooks `preWarmForGrid(gridSize)` into `GlowbackGameScreen`, and adds deterministic Robolectric tests verifying pool invariants, LRU eviction, track lifecycle, and grid switching.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Strict Scope Enforcement**
> Only `GlowAudioSynthesizer` in `MainActivity.kt`, its `LaunchedEffect(gridSize)` warm-up hook in `GlowbackGameScreen`, and Robolectric unit tests will be modified.
>
> **Untouched by design**:
> - `KEY_LAUNCH_COUNT` and the welcome card (`OnboardingPopupDialog`) logic
> - `backup_rules.xml` and `data_extraction_rules.xml`
> - `app/build.gradle.kts` and all signing configurations
> - Tone frequencies, durations, amplitudes, and volumes
> - All UI, layouts, and animations

> [!NOTE]
> **Approved Refinements Incorporated**
> 1. **Computed Distinct-Buffer Counts in Tests**: Grid warm-up and switching tests compute the expected pool size dynamically from the distinct tone buffers of the grid plus `errorBuzzPcm` and `clearArpeggioPcm` capped at `MAX_POOL_SIZE` (rather than hardcoding `6` or `11`).
> 2. **Safe Post-Release Queue Flush**: `flushAudioQueueForTesting()` checks `isReleased` and catches any executor/cancellation rejection so calling it after `release()` is a guaranteed safe no-op.
> 3. **Big-Grid On-Demand Creation Preserved**: Grids whose total distinct buffers exceed `MAX_POOL_SIZE = 12` (`4×4` and `5×4`) pre-warm up to the 12-track cap and create any remaining unpooled pad tones on demand via `getOrCreateTrack` with LRU eviction.

> [!NOTE]
> **Limitations of Self-Heal & JVM Testing**
> - The self-heal check only detects negative error codes and `playState != AudioTrack.PLAYSTATE_PLAYING` after `play()`; it cannot detect a hardware/HAL bug where an `AudioTrack` reports `PLAYSTATE_PLAYING` while outputting silence.
> - Robolectric runs on the JVM without audio hardware, so the audible fix can only be confirmed on a physical Android device.

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates cross-thread `AudioTrack` races, fixes `MODE_STATIC` buffer rewinding, caps simultaneous open `AudioTrack` instances at 12 with LRU eviction, queues per-track warm-ups for the active grid so taps interleave without blocking, and logs/self-heals track errors.
- **Target Audience / Persona**: Players on Android 12+ phones experiencing occasional silent pad taps.
- **Key Value**: Serialized, deterministic `AudioTrack` lifecycle management that stays within OEM `AudioFlinger` track limits while keeping active-grid tones pre-warmed.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Startup & Grid Switch Pre-Warm**: `LaunchedEffect(gridSize) { audioSynth.preWarmForGrid(gridSize) }` enqueues one audio-thread job per tone buffer so user taps can execute between warm-up steps without waiting for an entire batch.
  2. **Pad Tap & Sequence Playback**: Every tone request runs sequentially on the single audio thread, rewinds via `reloadStaticData()` (or `setPlaybackHeadPosition(0)` if negative), self-heals once on error or non-`PLAYING` state, and maintains at most 12 open tracks via LRU eviction.
- **Visual Identity & Theme**:
  - No UI, layout, color, or animation changes.

---

## 3. Required Technical Changes (Items 1–7)

### 1. Single Audio Thread
- Replace `Dispatchers.Default` with `private val audioExecutor = Executors.newSingleThreadExecutor()` and `private val audioDispatcher = audioExecutor.asCoroutineDispatcher()`, with `private val audioScope = CoroutineScope(SupervisorJob() + audioDispatcher)`.
- Track an `@Volatile private var isReleased = false` flag so calls after `release()` safely no-op without allocating tracks.
- In `release()`, set `isReleased = true`, cancel `audioScope`, stop and `release()` all tracks in the pool, clear the pool, and call `audioDispatcher.close()`.

### 2. Correct `MODE_STATIC` Rewind in `playStaticPcmInternal`
1. Obtain the track via `getOrCreateTrack(pcmBuffer, forceRecreate = false)`.
2. If `track.playState == AudioTrack.PLAYSTATE_PLAYING`, call `track.stop()`.
3. Call `val reloadRes = track.reloadStaticData()`. If `reloadRes < 0`, call `track.setPlaybackHeadPosition(0)`.
4. Call `track.setVolume(vol)`, then `track.play()`.
5. Remove `track.flush()`.

### 3. Self-Heal and Logging
- If `reloadStaticData()` failed **and** `setPlaybackHeadPosition(0) < 0`, or `setVolume(vol) < 0`, or `track.playState != AudioTrack.PLAYSTATE_PLAYING` after `play()`, recreate the track via `getOrCreateTrack(pcmBuffer, forceRecreate = true)`, call `setVolume(vol)`, and call `play()` once.
- Catch any `Throwable` with `Log.w("GlowAudio", "Audio playback failed", t)` so failures are visible in Logcat and never crash the app.

### 4. One Pool Entry Point (`getOrCreateTrack`)
- All `AudioTrack` creation goes exclusively through `private fun getOrCreateTrack(pcmBuffer: ShortArray, forceRecreate: Boolean = false): AudioTrack`, called only on the single audio thread.
- If an existing track in the pool has `state != AudioTrack.STATE_INITIALIZED` or `forceRecreate == true`, stop (if playing) and `release()` the old track before storing the newly created track.
- Guarantees at most one `AudioTrack` per `ShortArray` tone buffer.

### 5. Pool Cap with LRU Eviction (Max 12 Tracks)
- Maintain the pool in an access-ordered `LinkedHashMap<ShortArray, AudioTrack>(16, 0.75f, true)` (synchronized on `poolLock` for thread-safe inspection during `release()` and tests) capped at `MAX_POOL_SIZE = 12`.
- Accessing a track in `getOrCreateTrack` updates its recency order.
- Before inserting a new track when `pool.size >= MAX_POOL_SIZE`, evict the eldest (least-recently-used) entry, stopping and calling `release()` on the evicted `AudioTrack`.
- Tones not currently in the pool on larger grids (`4×4` or `5×4`) are created on demand via `getOrCreateTrack` with LRU eviction.

### 6. Per-Track Queued Warm-Up for the Active Grid (`preWarmForGrid`)
- Add `fun preWarmForGrid(gridSize: GridSize)`:
  - Compute the desired buffers for `gridSize`: `errorBuzzPcm`, `clearArpeggioPcm`, and `padsForGridSize(gridSize).map { getOrSynthesizeTone(it.frequencyHz) }.distinct()`, capped at `MAX_POOL_SIZE = 12`.
  - First, launch an audio-thread job that releases and removes any pooled track whose `ShortArray` buffer is not in the active grid's target set.
  - Next, for each buffer in the target warm-up set, launch **one separate `audioScope.launch` job per track** calling `getOrCreateTrack(pcmBuffer)`. Because each track warm-up is its own job on the single-threaded dispatcher queue, any user tap (`playPadSpecTone`) queued during warm-up runs immediately after the current single track creation rather than waiting for all tracks to finish.
- In `GlowbackGameScreen`, hook up `LaunchedEffect(gridSize) { audioSynth.preWarmForGrid(gridSize) }` right next to `val audioSynth = remember { GlowAudioSynthesizer() }` and `DisposableEffect(Unit) { onDispose { audioSynth.release() } }`.

### 7. Run-Path Verification Summary
Every code path that starts or resumes a run in `GlowbackGameScreen` interacts with `gridSize` as follows:
1. **Solo (`startNewGame()` -> `actuallyStartNewGame()` at line 1604)**: Uses the current `gridSize` state (`padsForGridSize(gridSize)`). `gridSize` was already pre-warmed at initial composition (`LaunchedEffect(gridSize)`) or when changed in Settings (`resetToIdleForConfigChange` at line 1342, which updates `gridSize = newGridSize`). If the user starts a second Solo run on the same grid, `gridSize` does not change because its tracks are already warm in the pool.
2. **Pass-and-Play 2P / 3P / 4P (`actuallyStartNewGame()` at line 1623 & `resumeActiveRun(state)` at line 1228)**:
   - New match (line 1623): Uses current `gridSize`, already pre-warmed by `LaunchedEffect(gridSize)`.
   - Resumed match (`resumeActiveRun`, line 1243): Sets `gridSize = restoredGrid`, triggering `LaunchedEffect(gridSize)` if the saved match's grid differs from the current grid.
3. **Outbound Rival Run (`startRevengeGame(...)` with `needsRevengeGate = false` at line 1516, and retry in `actuallyStartNewGame()` at line 1609)**:
   - `startRevengeGame` sets `gridSize = resolvedGrid` (line 1534), triggering `LaunchedEffect(gridSize)` if the grid changed.
   - Retry in `actuallyStartNewGame()` (line 1609) stays on the same `gridSize` whose tracks are already in the pool.
4. **Inbound Challenge (`startChallenge(item)` at line 1462, and retry in `actuallyStartNewGame()` at line 1581)**:
   - Both set `gridSize = targetGrid` (lines 1500 and 1593), updating the exact `gridSize` state observed by `LaunchedEffect(gridSize)`.
5. **Revenge (`startRevengeGame(...)` with `needsRevengeGate = true` at line 1516)**:
   - Sets `gridSize = resolvedGrid` (line 1534), updating the `gridSize` state observed by `LaunchedEffect(gridSize)`.
6. **Replay (`startWatchMyRoundReplay()` at line 1648)**:
   - Replays the `sequence` from the just-completed round in `GameState.GAME_OVER` without changing `gridSize`. This is safe because the active grid's tracks are already warm in the pool from the round that just ended, and any evicted tone on 16/20-pad grids falls back to `getOrCreateTrack(pcmBuffer)`.

---

## 4. Technical Architecture & Verification

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    GlowAudioSynthesizer (Single Thread)                 │
├─────────────────────────────────────────────────────────────────────────┤
│  LaunchedEffect(gridSize) ──► preWarmForGrid(gridSize)                  │
│    ├─► Job 0: Prune & release tracks not in active grid target set      │
│    ├─► Job 1..N: One job per buffer ──► getOrCreateTrack(pcm)           │
│    │     (User taps interleave between individual warm-up jobs)         │
│                                                                         │
│  playPadSpecTone / playErrorBuzz / playRoundClearArpeggio               │
│    └─► audioScope.launch (SingleThreadExecutor):                        │
│          └─► playStaticPcmInternal(pcmBuffer, vol)                      │
│                ├─► track = getOrCreateTrack(pcmBuffer) [LRU cap = 12]   │
│                ├─► if (PLAYSTATE_PLAYING) track.stop()                  │
│                ├─► if (reloadStaticData() < 0) setPlaybackHeadPosition(0)│
│                ├─► setVolume(vol) -> play()                             │
│                └─► if (error < 0 || playState != PLAYSTATE_PLAYING):    │
│                      └─► getOrCreateTrack(pcmBuffer, forceRecreate=true)│
│                            -> setVolume(vol) -> play()                  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Robolectric Test Suite
We will expose `internal` inspection helpers on `GlowAudioSynthesizer`:
- `internal fun poolSizeForTesting(): Int`
- `internal fun pooledTracksSnapshotForTesting(): Map<ShortArray, AudioTrack>`
- `internal fun flushAudioQueueForTesting()` — submits a no-op barrier task to `audioExecutor` and awaits completion, returning immediately as a safe no-op if `isReleased` is true or if the executor is shut down.

And verify:
1. Calling `playPadSpecTone` for every pad 50 times in quick succession does not throw.
2. Pool size never exceeds 12 (`MAX_POOL_SIZE`), even after playing all 20 pads of the `5×4` grid, and holds at most one `AudioTrack` per tone buffer.
3. A replaced track (via `forceRecreate` / self-heal or LRU eviction) has `release()` called (`track.state == AudioTrack.STATE_UNINITIALIZED`).
4. `release()` leaves the pool empty (`poolSizeForTesting() == 0`), and a subsequent `playPadSpecTone` + `flushAudioQueueForTesting()` call after `release()` is a safe no-op that creates no tracks (`poolSizeForTesting() == 0`) and does not throw.
5. Switching grids via `preWarmForGrid(GridSize.TWO_BY_TWO)` followed by `preWarmForGrid(GridSize.THREE_BY_THREE)` releases tracks belonging only to the old grid and leaves a pool size equal to the computed distinct-buffer count (`minOf(MAX_POOL_SIZE, distinctBuffersForGrid.size)`).
