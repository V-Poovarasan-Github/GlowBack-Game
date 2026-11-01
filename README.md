# 🧠 GlowBack

**Watch the glow. Send it back.**

GlowBack is a polished, 100% offline, multiplayer memory game with a viral async challenge system. Test your memory, challenge your friends, and see who can survive the longest sequence.

## ✨ Features

- **100% Offline & Private:** No accounts, no ads, no internet required, and zero data collection. Your data stays on your device.
- **Async Multiplayer Challenges:** Generate a unique, checksum-verified challenge code at Game Over and send it to a friend. They play your exact sequence.
- **Dynamic Difficulty:** 
  - *Easy Mode:* Shared cumulative sequences (classic pass & play).
  - *Hard Mode:* Completely unique, fresh sequences every round for every player.
- **Smart Retention:** Opt-in daily local notifications with dynamic, personalized reminders based on your high score and play history.
- **Premium Audio & Haptics:** Custom synthesized audio engine with psychoacoustic harmonics for crystal-clear sound on any device, paired with crisp haptic feedback.
- **Clean UI:** Built with Jetpack Compose featuring a beautiful dark-mode neon aesthetic.

## 📥 How to Install (APK)

1. Go to the **[Releases](https://github.com/V-Poovarsan-Github/GlowBack-Game/releases)** section of this repository.
2. Download the latest `app-release.apk` file.
3. On your Android device, allow "Install from Unknown Sources" when prompted.
4. Tap the downloaded file to install and start playing!

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM, SharedPreferences (Offline Storage)
- **Background Tasks:** WorkManager (for smart local notifications)
- **Audio:** Custom `AudioTrack` PCM synthesis with pre-warmed static pools

## 📜 License

This project is open source and available under the [MIT License](LICENSE).
