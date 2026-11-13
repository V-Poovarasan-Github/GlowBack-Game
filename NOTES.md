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
