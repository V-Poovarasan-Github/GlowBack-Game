# Privacy Policy for Glowback

**Last Updated:** November 10, 2026

Welcome to **Glowback** ("the App"). We are committed to protecting your privacy. This Privacy Policy explains our practices regarding user data and information handling.

---

## 1. Zero Data Collection

**Glowback does not collect, share, or transmit any personal data. All gameplay, records, and codes remain 100% offline on your device. There are no accounts, no analytics, and no internet connection required.**

- **No Personal Information:** We do not collect names, email addresses, phone numbers, device IDs, IP addresses, or any other personally identifiable information.
- **No User Accounts:** The App operates entirely without registration, logins, or user profiles.
- **No Internet or Server Transmission:** The App does not connect to external servers, cloud databases, or remote APIs.

---

## 2. On-Device Storage Only & Backup Exclusion

All data created within the App—including game scores, leaderboard records, sound/haptic preferences, and challenge codes—is stored strictly locally on your device via standard Android application storage (`SharedPreferences`) and local cache files.

- **No Cloud Backup or Device Transfer:** Android Auto Backup (`full-backup-content` and `cloud-backup`) and device-to-device transfer (`device-transfer`) are explicitly excluded in `backup_rules.xml` and `data_extraction_rules.xml` for both `sharedpref` and `file` domains. Glowback does not upload your game data to cloud backups or transfer it during device migrations; no data ever leaves your device.
- You can permanently delete all stored game data at any time by clearing the App's storage via your Android device settings (**Settings > Apps > Glowback > Storage > Clear Data**) or by uninstalling the application.

---

## 3. Third-Party Services & Analytics

Glowback does not incorporate:
- Advertising SDKs or ad networks
- Analytics, telemetry, or crash-reporting trackers (e.g., Google Analytics, Firebase Analytics)
- Social network plugins or external trackers

No third party receives any data from your use of the App.

---

## 4. Device Permissions

Glowback only uses device capabilities essential for local gameplay:
- **Vibration / Haptic Feedback (`VIBRATE`):** Used exclusively to provide sensory feedback when tapping pads or clearing rounds.
- **Notifications (`POST_NOTIFICATIONS`, Optional):** If enabled, local notifications are scheduled and delivered entirely on-device (via Android WorkManager) to notify you of offline challenge reminders. No notification data is sent or received over the network.
- **Boot Completed (`RECEIVE_BOOT_COMPLETED`):** Used exclusively by Android Jetpack WorkManager (`RescheduleReceiver`) to keep your opt-in local notification schedule active after a device reboot.

---

## 5. Children's Privacy

Glowback is safe for users of all ages, including children. Because the App does not collect, store, or transmit any personal information, it complies with the Children's Online Privacy Protection Act (COPPA), the General Data Protection Regulation (GDPR), and Google Play Families Policy requirements.

---

## 6. Changes to This Policy

We may update this Privacy Policy from time to time to reflect operational or legal requirements. Any updates will be posted in this repository.

---

## 7. Contact Us

If you have questions or suggestions about this Privacy Policy, please open an issue on the GitHub repository:

- **Repository & Inquiries:** [https://github.com/V-Poovarasan-Github/GlowBack-Game/issues](https://github.com/V-Poovarasan-Github/GlowBack-Game/issues)
