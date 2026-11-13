# GlowBack Release Hardening Notes (RELEASE-1)

## Standing v1.1 Reminders
- **Amendment A2 (R8 Deferred)**: R8 deferred at v1.0 freeze. Enable `isMinifyEnabled` + `isShrinkResources` with keep rules (WorkManager workers, FileProvider, any reflection-serialized models) and manually re-verify all release flows. Do not ship indefinitely without R8.

## Phase 1 — Privacy & Backup Integrity
- **1.1 Backup Exclusion (Option a chosen)**: Kept `android:allowBackup="true"` in `AndroidManifest.xml` and excluded `<exclude domain="sharedpref" path="." />` and `<exclude domain="file" path="." />` across `<full-backup-content>` in `app/src/main/res/xml/backup_rules.xml` and across both `<cloud-backup>` and `<device-transfer>` in `app/src/main/res/xml/data_extraction_rules.xml`. Verified via packaged debug resources (`app/build/intermediates/packaged_res/debug/packageDebugResources/xml/`) and merged release manifest (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`), aligning implementation with `PRIVACY.md` ("GlowBack does not upload your game data to cloud backups").
- **1.2 `metadata.json` Capability Cleanup**: Confirmed zero references to `gemini`, `generativeai`, or `GEMINI_API_KEY` across `app/src`. Updated `metadata.json` `"majorCapabilities"` from `["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]` to `[]`.
- **1.3 `RECEIVE_BOOT_COMPLETED` Verification (Amendment A1 — Evidence-First)**:
  - **Source grep (`app/src`)**: `app/src/main/AndroidManifest.xml:6: <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />` (zero custom `BroadcastReceiver` classes in `app/src`).
  - **Merged Release Manifest (`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`)**:
    - Line 13: `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`
    - Lines 137–147: `<receiver android:name="androidx.work.impl.background.systemalarm.RescheduleReceiver" ...><intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" />...</intent-filter></receiver>`
  - **Manifest Merger Blame Report (`app/build/outputs/logs/manifest-merger-release-report.txt`)**:
    - `uses-permission#android.permission.RECEIVE_BOOT_COMPLETED` merged from `app/src/main/AndroidManifest.xml:6:5-81` and `[androidx.work:work-runtime:2.10.0] AndroidManifest.xml:25:5-81`.
    - `receiver#androidx.work.impl.background.systemalarm.RescheduleReceiver` with `action#android.intent.action.BOOT_COMPLETED` added from `[androidx.work:work-runtime:2.10.0] AndroidManifest.xml:110:9-121:20`.
  - **Decision**: **KEEP**. `RECEIVE_BOOT_COMPLETED` is actively used by WorkManager's `RescheduleReceiver` (`androidx.work:work-runtime:2.10.0`) to reschedule periodic offline engagement notifications (`EngagementScheduler` / `EngagementWorker`) across device reboots.

## Phase 2 — Release Build Config
- **2.2 Release Signing Fail-Fast**: Added `verifyReleaseSigningConfig` task in `app/build.gradle.kts` wired as a dependency to all `Release` assemble/bundle/package/sign/validateSigning tasks. Throws a descriptive `GradleException` when `KEYSTORE_PATH` (or `${rootDir}/my-upload-key.jks`), `STORE_PASSWORD`, or `KEY_PASSWORD` are missing, preventing silent unsigned release builds while keeping `assembleDebug` and `testDebugUnitTest` unaffected.
- **2.3 Version Name Consistency**: Updated `versionName` in `app/build.gradle.kts` from `"1.0"` to `"1.0.0"` to match `README.md` (`v1.0.0`).
- **2.4 Theme Rename**: Renamed `Theme.MyApplication` to `Theme.GlowBack` in `app/src/main/res/values/themes.xml`, `app/src/main/res/values-v31/themes.xml`, and `app/src/main/AndroidManifest.xml` (`<application>` and `<activity>` tags).
- **2.5 Dead Dependency Cleanup**: Removed commented `// implementation(platform(libs.firebase.bom))` from `app/build.gradle.kts` and removed `firebaseBom` / `firebase-bom` from `gradle/libs.versions.toml`.
- **2.7 `applicationId` Finalization**: Set `applicationId = "com.rakesh.glowback"` in `app/build.gradle.kts`. ID finalized pre-launch; permanent after first Play upload; existing test installs reset. Confirmed `FileProvider` uses `android:authorities="${applicationId}.fileprovider"` in `AndroidManifest.xml` and `"${context.packageName}.fileprovider"` in `ChallengeShareHelper.kt`.
- **Deferred (Unchanged per D1/D2)**: Left R8 (`isMinifyEnabled = false`) and `composeBom = "2024.09.00"` unchanged for v1.0 freeze.

## Phase 2 Addendum — `applicationId` Correction
- **2.7 `applicationId` Corrected**: Updated `applicationId` in `app/build.gradle.kts` from `"com.rakesh.glowback"` to `"com.poovarasan.glowback"` per Confirmed Decision 3 in the approved plan.

## Phase 3 — Accessibility (TalkBack & Touch Targets)
- **3.1 Selection State Semantics**: Added `Role.RadioButton` / `Role.Tab`, `selected = ...`, and `stateDescription = if (isSelected) "Selected" else "Not selected"` to all interactive selectors: Home mode tabs (`mode_solo`..`mode_4p`), Home difficulty toggles (`difficulty_toggle_easy`, `difficulty_toggle_hard`), Settings sheet grid/pace `FilterChip`s, `ShareTriesSelectorDialog` board chips (`board_option_*`) and tries segmented options (`tries_option_*`), `RivalMatchSetupDialog` grid (`rival_setup_grid_*`) and difficulty (`rival_setup_diff_*`) buttons, `HallOfFameScreen` tabs (`tab_rivalry`, `tab_records`), `RivalCard` per-grid stat chips (`rival_grid_chip_*`), and `RecordsTabContent` mode (`records_mode_*`) and grid (`records_grid_*`) selectors.
- **3.2 Minimum Touch Targets ($\ge 48\text{dp}$)**: Applied `minimumInteractiveComponentSize()` and `heightIn(min = 48.dp)` across compact chips, icon buttons, and segmented controls while preserving the inner visual pill/chip styling (e.g. `26.dp` visual pill inside `48.dp` hit target on `rival_grid_chip_*`, `40.dp` visual track inside `48.dp` hit target on `difficulty_toggle_*`).
- **3.3 Actionable Rival Name**: Wrapped `rival_card_name_${rival.name}` in a `Box` with `minimumInteractiveComponentSize()`, `heightIn(min = 48.dp)`, `widthIn(min = 48.dp)`, and `clickable(role = Role.Button)`. Confirmed `RivalCard` does NOT set `mergeDescendants = true` (per D3 / Amendment A3).
- **3.4 Live Regions**: Added `LiveRegionMode.Polite` to `status_header_text` (during normal states), `status_subtitle_text`, and scoreboard values (`current_score_text`, `scoreboard_best_score_text`); added `LiveRegionMode.Assertive` to `status_header_text` on `GAME_OVER`/elimination/loss and to dialog error banners (`challenge_code_error_text`, `paste_rival_error_text`).
- **3.5 Pad Playback Semantics**: Added `stateDescription = if (isFlashing) "lit" else "idle"` to `GlowPadButton` in `MainActivity.kt`.
- **3.6 Semantic Headings**: Added `heading()` semantics to screen and dialog titles (`GLOWBACK` header, `leaderboard_screen_title`, `enter_challenge_dialog_title`, `paste_rival_dialog_title`, `share_challenge_header_title`, `rival_setup_dialog_title`).
- **3.8 Robolectric Accessibility Tests**: Added `release1_phase3_accessibilitySemanticsAndTouchTargets` in `ExampleRobolectricTest.kt` verifying `3.1`–`3.6` and `D3`.
