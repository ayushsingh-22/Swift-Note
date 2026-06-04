# [SwiftNote - Offline-First Smart Notes for Android](https://play.google.com/store/apps/details?id=com.amvarpvtltd.selfnote)

[![Get it on Google Play](https://img.shields.io/badge/Get%20it%20on-Google%20Play-green?logo=google-play)](https://play.google.com/store/apps/details?id=com.amvarpvtltd.selfnote)

**SwiftNote** is an offline-first Android note-taking app built with **Kotlin**, **Jetpack Compose**, **Room**, and **Firebase Realtime Database**. It combines private multi-device sync, encrypted note storage, AI-assisted reminder detection, rich text editing, recurring reminders, reactive theme switching, and fast local-first performance.


---

## ✨ What SwiftNote currently includes

### 📝 Notes, writing, and organization

- Create, edit, archive, pin, search, and delete notes.
- Rich text note editing and display using `compose-rich-editor` with HTML as the storage format.
- Checklist-friendly note flows and quick note creation.
- Categories and note color tagging.
- “Today” / Daily Review screen for reminders and recent activity.
- Light, dark, and system theme support through a reactive app-wide theme state.
- Responsive/adaptive layout helpers for compact, medium, and expanded screens.
- Share text into SwiftNote from other apps using `ShareReceiverActivity`.
- Home screen quick-capture widget built with Glance.

### 🤖 AI features

- **AI reminder detection** while writing notes.
- **Tiered reminder pipeline**:
  1. `ReminderIntentAnalyzer` pre-filters reminder intent in English and Hinglish before cloud calls
  2. User-provided **Gemini** or **Groq** key via unified `LlmService` routing
  3. On-device **ML Kit Entity Extraction**
  4. Regex fallback for English and Hinglish-style date/time phrases
- `LlmService` automatically routes to the active provider configured in encrypted key storage.
- Provider-aware app limits: Gemini is capped at `5/min` and `80/day`, while Groq is capped at `30/min` and `250/day` through `LlmService`.
- **AI title generation** with offline fallback when no AI key is configured.
- **Smart Action Chips** for detected entities like links, phone numbers, email addresses, dates, addresses, and amounts.

### ⏰ Reminder system

- One-time and recurring reminders.
- Daily, weekly, monthly, and yearly recurrence support.
- Exact alarm scheduling with Android 12+ permission handling.
- Boot-time reminder re-registration after device restart or app update.
- System notifications for reminders and note deep-linking.
- Works offline; alarms are scheduled locally on-device.

### 🔐 Privacy, sync, and offline-first behavior

- Room is the source of truth; writes happen locally first.
- Notes are encrypted for persistence and before remote sync.
- Multi-device sync uses a **passphrase-based identity model**, not a traditional email/password account system.
- QR-based device pairing and manual passphrase restore flows.
- Sync modes support:
  - `LOCAL_ONLY`
  - `CONTINUOUS`
  - `ONE_TIME_IMPORTED`
- Firebase Anonymous Auth is used only to satisfy backend rules; it is **not** the user identity model.

---

## 🏗️ Architecture

SwiftNote follows an **MVVM + Repository** approach:

- **UI:** Jetpack Compose screens and reusable components
- **State:** `StateFlow` + `SharedFlow`
- **ViewModels:** `AddNoteViewModel`, `NotesViewModel`, `ViewNoteViewModel`
- **Persistence:** Room (`AppDatabase`, DB version `8`)
- **Sync:** Firebase Realtime Database with offline-first sync behavior
- **Encryption:** note content is encrypted/decrypted through the `Note` model and mapper layer
- **Theme:** reactive `AppThemeState` singleton with `LIGHT`, `DARK`, and `SYSTEM` modes
- **Responsive UI:** screen-width-aware layout buckets via `WindowSize.kt` (`COMPACT`, `MEDIUM`, `EXPANDED`)
- **Dependency management:** manual instantiation / singletons; **no Hilt/Dagger in the current codebase**

Core data flow:

```text
Compose UI → ViewModel → Repository → Room (local first) → Firebase sync (background)
```

---

## 🧠 AI and reminder pipeline

SwiftNote’s reminder stack is more advanced than a single NLP call:

1. `ReminderIntentAnalyzer` checks whether the note actually looks like a reminder.
2. `SmartReminderAI` tries to extract reminder candidates.
3. If configured, `LlmService` routes requests to the active AI provider:
   - **Gemini**
   - **Groq**
   Routing is based on the active key’s provider metadata stored on-device.
4. If cloud AI is unavailable, the app falls back to **ML Kit** and then regex-based extraction.
5. Accepted reminders are persisted in Room and scheduled through AlarmManager.
6. Recurring reminders compute the next occurrence and re-schedule themselves.

AI keys are **user-supplied** and stored securely in `EncryptedSharedPreferences`. No Gemini or Groq key is bundled with the app. Groq runs through a custom OpenAI-compatible REST client, while Gemini uses the Google AI SDK.

---

## 📲 Navigation routes

The app uses a single-activity Compose navigation graph defined in `navbar.kt`.

| Route | Screen |
|---|---|
| `onboarding` | First-run onboarding / restore options |
| `main` | Notes list |
| `addscreen` | Create note |
| `addscreen/{noteId}` | Edit note |
| `viewnote/{noteId}` | View note |
| `syncSettings` | Sync and passphrase settings |
| `offlineSyncScreen` | Device pairing / sync status |
| `aiSettings` | Gemini/Groq key management |
| `archive` | Archived notes |
| `today` | Daily review / today’s reminders |

---

## ⚙️ Tech stack

| Area | Current setup |
|---|---|
| Language | Kotlin `2.2.10` |
| Android Gradle Plugin | `9.2.1` |
| UI | Jetpack Compose (BOM `2024.12.01`) |
| Material | Material 3 + Compose Material |
| Local database | Room `2.7.1` |
| Cloud sync | Firebase Realtime Database |
| Auth model | Firebase Anonymous Auth + passphrase identity |
| AI providers | Gemini (`0.9.0`) and Groq via unified `LlmService` routing |
| On-device NLP | ML Kit Entity Extraction `16.0.0-beta6` |
| Background work | WorkManager `2.10.0` |
| QR scanning | CameraX + ML Kit Barcode |
| Browser integration | AndroidX Browser `1.8.0` (Custom Tabs) |
| PDF support | AndroidX PDF Viewer `1.0.0-alpha10` |
| QR generation | ZXing |
| Secure storage | AndroidX Security Crypto |
| Widget | Glance `1.1.1` |
| Rich text | `richeditor-compose 1.0.0-rc14` |
| HTML processing | Jsoup `1.18.1` |
| Compose lifecycle helpers | `lifecycle-runtime-compose 2.8.7` |

---

## 🗂️ Project structure

```text
Swift-Note/
├── AGENTS.md
├── AUTHENTICATION_FLOW.md
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/amvarpvtltd/swiftNote/
│       │       ├── ai/                 # Reminder detection, LLM routing, title generation
│       │       ├── auth/               # Device ID, passphrase, sync modes
│       │       ├── categories/         # Category and color helpers
│       │       ├── components/         # Reusable Compose UI pieces
│       │       ├── design/             # App screens
│       │       ├── notifications/      # Notification posting/actions
│       │       ├── offline/            # Offline note state management
│       │       ├── reminders/          # Reminder entities, repository, recurrence logic
│       │       ├── repository/         # Note repository + sync orchestration
│       │       ├── richtext/           # HTML sanitizing and plain-text conversion
│       │       ├── room/               # Room entities, DAO, DB, mapper
│       │       ├── security/           # Encryption, key storage, hashing
│       │       ├── share/              # Share-to-app capture flow
│       │       ├── sync/               # Cross-device sync logic
│       │       ├── theme/              # ThemeManager + AppThemeState
│       │       ├── utils/              # Utilities including responsive window sizing
│       │       ├── viewmodel/          # MVVM view models
│       │       └── widget/             # Glance app widget
│       ├── test/                       # Unit tests
│       └── androidTest/                # Instrumented tests
├── gradle/
│   └── libs.versions.toml
└── build.gradle.kts
```

---

## 📱 Screenshots

<table>
  <tr>
    <td><img width="230" height="510" alt="SwiftNote screenshot 1" src="https://github.com/user-attachments/assets/f9b4800a-81d3-46fa-bc6d-2a4a97f2ec13" /></td>
    <td><img width="230" height="510" alt="SwiftNote screenshot 2" src="https://github.com/user-attachments/assets/449e5270-8e63-4933-a4d5-dcdac85aeaf9" /></td>
    <td><img width="230" height="510" alt="SwiftNote screenshot 3" src="https://github.com/user-attachments/assets/a773dedb-8159-4731-a1c4-973fa0e3ee82" /></td>
  </tr>
  <tr>
    <td><img width="230" height="510" alt="SwiftNote screenshot 4" src="https://github.com/user-attachments/assets/9027efac-7541-4fec-bbc1-8bf634122703" /></td>
    <td><img width="230" height="510" alt="SwiftNote screenshot 5" src="https://github.com/user-attachments/assets/e9db5c02-b1ea-4bea-a69a-d523cc1bdfc5" /></td>
    <td><img width="230" height="510" alt="SwiftNote screenshot" src="https://github.com/user-attachments/assets/bb4ebcd7-a476-4d15-889e-8637331864b6" /></td>
  </tr>
  <tr>
    <td><img width="230" height="510" alt="SwiftNote screenshot 7" src="https://github.com/user-attachments/assets/7cbf1a6a-8f77-414b-a8a4-0933e4fb93e7" /></td>
    <td><img width="230" height="510" alt="SwiftNote screenshot 8" src="https://github.com/user-attachments/assets/41f2e952-3ba1-4f6d-9c99-125ad13a2472" /></td>
    <td><img width="230" height="510" alt="SwiftNote screenshot 9" src="https://github.com/user-attachments/assets/9e940054-0089-4ba7-9fbb-4970b150585f" /></td>
  </tr>
</table>

---

## 🚀 Getting started

### Requirements

- Android Studio with recent Compose/AGP support
- JDK 11
- Android SDK `36`
- **minSdk:** `31`
- **targetSdk:** `36`
- Kotlin `2.2.10`

### Clone the repository

```bash
git clone https://github.com/ayushsingh-22/Swift-Note.git
cd Swift-Note
```

### Open and run

1. Open the project in Android Studio.
2. Let Gradle sync.
3. Run on an emulator or physical device with Android 12+.

### Build from the command line

**macOS / Linux**

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

**Windows PowerShell**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

### Run tests

**macOS / Linux**

```bash
./gradlew test
./gradlew connectedCheck
```

**Windows PowerShell**

```powershell
.\gradlew.bat test
.\gradlew.bat connectedCheck
```

---

## 🧪 Testing stack

The project includes unit tests around encryption, recurrence logic, search/sort behavior, sync-related utilities, rich text sanitization, and AI reminder intent handling.

Main test tooling in the project:

- JUnit 4
- Mockito + Mockito-Kotlin
- `kotlinx-coroutines-test`
- Turbine
- Robolectric

---

## 🔑 AI setup notes

- SwiftNote does **not** ship with a Gemini or Groq API key.
- Users can add their own keys from the in-app **AI Settings** screen.
- Keys are stored on-device using `EncryptedSharedPreferences`.
- If no AI key is configured, reminder detection still falls back to on-device and regex-based logic.

---

## 📚 Additional docs in this repository

- `AGENTS.md` — project architecture, conventions, and implementation notes
- `AUTHENTICATION_FLOW.md` — detailed passphrase-based identity and sync behavior

---

## 🤝 Contributing

Contributions are welcome. If you plan to change architecture or storage/sync behavior, review `AGENTS.md` and `AUTHENTICATION_FLOW.md` first so new changes stay aligned with the existing app model.

General guidelines:

- Follow Kotlin and Compose conventions already used in the project.
- Preserve the offline-first Room → Firebase flow.
- Keep note encryption intact when touching persistence or sync code.
- Add or update tests for non-trivial logic changes.

---

## 📌 Notes

- The repository currently does **not** include a `LICENSE` file, so no license is declared here.
- Some Firebase services are present in the build for app features and telemetry, including Realtime Database, Auth, Firestore, Crashlytics, and Analytics.

---

## 📞 Contact

- Author: [**Ayush Kumar**](https://www.linkedin.com/in/ayush-kumar-a2880a258/)
- Repository: [**Swift-Note**](https://github.com/ayushsingh-22/Swift-Note)
- Play Store: [**SwiftNote**](https://play.google.com/store/apps/details?id=com.amvarpvtltd.selfnote)

---

Made with ❤️ using **Jetpack Compose**.
