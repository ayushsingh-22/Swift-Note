# AGENTS.md

## Project Overview

SwiftNote is an offline-first Android note-taking app (package: `com.amvarpvtltd.swiftNote`, applicationId: `com.amvarpvtltd.selfnote`) built with Kotlin, Jetpack Compose, Room, Firebase Realtime Database, and ML Kit. It features E2E encryption, multi-device sync via passphrase/QR, and AI-powered reminder detection.

## Architecture

- **MVVM + Repository pattern** — UI (Compose) → ViewModel → Repository → Room (local) + Firebase (remote)
- **ViewModels**: Dedicated `viewmodel/` directory with `AddNoteViewModel`, `NotesViewModel`, `ViewNoteViewModel` (extend `AndroidViewModel`, use `StateFlow` + `SharedFlow` for UI events).
- **Offline-first**: All writes go to Room first (`synced=false`), then background-sync to Firebase. See `repository/NoteRepository.kt` and `offline/OfflineNoteManager.kt`.
- **Encryption**: Notes are encrypted/decrypted using device ID and passphrase keys. Core logic lives in `dataclass.kt` (`Note.getEncryptedTitle()`, `Note.fromEncryptedData()`).
- **Singletons**: `SmartReminderAI`, `ReminderManager`, `SyncManager` use object/singleton patterns.
- **Navigation**: Single-Activity architecture with Compose NavHost defined in `navbar.kt`. Routes: `onboarding`, `main`, `addscreen`, `addscreen/{noteId}`, `viewnote/{noteId}`, `syncSettings`, `offlineSyncScreen`, `aiSettings`, `archive`.

## Key Source Paths

```
app/src/main/java/com/amvarpvtltd/swiftNote/
├── ai/
│   ├── SmartReminderAI.kt          # ML Kit entity extraction + regex fallback
│   ├── GeminiReminderParser.kt     # Gemini 2.0 Flash reminder parser (user API key, Hinglish, rate-limited)
│   ├── SmartEntityDetector.kt      # Unified entity detector: TextClassifier + regex, LRU-cached by noteId
│   └── DetectedEntity.kt           # Sealed hierarchy: PhoneNumber, Email, Url, Address, DateTime, Amount, TrackingNumber
├── auth/                           # DeviceIdManager, DeviceManager, PassphraseManager
│   └── SyncMode.kt                 # Enum: LOCAL_ONLY, CONTINUOUS, ONE_TIME_IMPORTED (persisted via PassphraseManager)
├── viewmodel/                      # AddNoteViewModel, NotesViewModel, ViewNoteViewModel
├── room/
│   ├── AppDatabase.kt              # Room DB: @Database, migrations
│   ├── NoteDao.kt / NoteEntity.kt  # Note table
│   ├── NoteEntityMapper.kt         # Maps dataclass ↔ NoteEntity (encrypts on toEntity, decrypts on toDomain)
│   └── PendingDeletionDao.kt / PendingDeletionEntity.kt
├── repository/NoteRepository.kt    # Offline-first CRUD, Firebase sync
├── offline/OfflineNoteManager.kt   # Local Room operations, StateFlow for pending notes
├── sync/SyncManager.kt             # Cross-device sync via passphrase decryption
├── share/                          # QuickCaptureSheet, SharedNoteData, ShareReceiverActivity
├── design/
│   ├── add_Note.kt / note_screen.kt / ViewNoteScreen.kt / ArchiveScreen.kt
│   ├── AISettingsScreen.kt         # Gemini API key management UI
│   ├── OnboardingScreen.kt         # First-run onboarding
│   ├── SyncSettingsScreen.kt / OfflineSyncScreen.kt
│   ├── QRScannerComponent.kt       # CameraX QR scanning composable
│   └── NoteTheme.kt                # In-app color palette singleton (mutableStateOf vars, not MaterialTheme)
├── components/
│   ├── ReminderComponents.kt       # Reminder UI composables
│   ├── SmartActionChipRow.kt       # Action chips for entities detected in note text (tap-to-call, maps, etc.)
│   ├── RichTextToolbar.kt          # Formatting toolbar (bold/italic/underline/headings/lists/links/code)
│   ├── RichTextDisplay.kt          # Read-only rich text renderer (handles HTML + Markdown content)
│   ├── DisconnectSyncDialog.kt     # Multi-step disconnect sync confirmation dialog
│   ├── SyncModeDialog.kt           # Sync mode selection dialog (Continuous vs One-Time Import)
│   ├── ChecklistComponents.kt / NoteComponents.kt / ScreenComponents.kt / ViewModeComponents.kt
│   ├── SearchComponents.kt / DialogComponents.kt / OfflineComponents.kt / OfflineEmptyStateCard.kt
│   ├── BackgroundProvider.kt / PermissionRationaleSheet.kt
│   └── NotificationComponent.kt    # In-app snackbar/banner via NotificationHelper singleton
├── richtext/
│   ├── RichTextBridge.kt           # Facade over compose-rich-editor; HTML stripping via Jsoup
│   ├── MarkdownToHtmlConverter.kt  # Best-effort Markdown→HTML for pasted content
│   └── RichTextSanitizer.kt        # Sanitizes clipboard/share HTML to supported tag subset (b,i,u,h1,h2,p,br,ul,ol,li,a,code,pre,input)
├── notifications/
│   ├── ReminderScheduler.kt        # Dual scheduling: AlarmManager (exact) + WorkManager (backup); contains ReminderWorker
│   ├── SystemNotificationHelper.kt # Posts system notifications, cancels by reminderId
│   └── NotificationActionReceiver.kt # BroadcastReceiver for notification action intents
├── reminders/
│   ├── ReminderManager.kt          # Scheduling entry point + broadcast extras constants
│   ├── ReminderRepository.kt       # CRUD for ReminderEntity + delegates to ReminderScheduler
│   ├── ReminderEntity.kt / ReminderDao.kt / ReminderData.kt
│   └── RecurrenceCalculator.kt     # Pure-function next-occurrence calculator (DAILY/WEEKLY/MONTHLY/YEARLY)
├── security/
│   ├── EncryptionUtil.kt           # AES note encryption
│   ├── GeminiKeyManager.kt         # Multi-key Gemini API key storage in EncryptedSharedPreferences
│   └── HashUtils.kt                # PBKDF2-HMAC-SHA256 (120k iter) for passphrases; SHA-256 for checksums
├── categories/CategoryManager.kt   # Note categories with preset colors
├── checklist/ChecklistParser.kt    # Checklist item parsing
├── search/SearchAndSortManager.kt  # Search, filtering, and sort (8 modes)
├── settings/DataManagementScreen.kt # Data management settings UI
├── permissions/                    # PermissionUtils, PermissionManager
├── cleanup/DataCleanupManager.kt   # Complete app data cleanup
├── widget/                         # QuickNoteWidget, QuickNoteWidgetReceiver, WidgetUpdateWorker (Glance)
├── utils/                          # ValidationUtils, UIUtils, ShareUtils, QRUtils, PreferenceManager, NetworkManager, AutoSyncManager, AppContext, Constants
├── ui/
│   ├── theme/                      # Theme.kt, Colors.kt, Type.kt
│   └── base/BaseFullScreenActivity.kt  # Edge-to-edge + immersive sticky base activity
├── theme/ThemeManager.kt           # Theme state management
├── test/SmartChipsTestDataSeeder.kt # Seeds test notes for Smart Action Chips dev/QA
├── Application.kt                  # Custom Application class
├── dataclass.kt                    # Note model with encryption methods
├── fetchUniqueDeveiceId.kt         # Device ID fetching utility
├── navbar.kt                       # NavHost + app initialization logic
└── MainActivity.kt                 # Entry point, permissions, TextClassifier init
```

## Build & Run

```powershell
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK (uses debug.keystore in app/)
./gradlew test                 # Unit tests
./gradlew connectedCheck       # Instrumented tests
```

- **compileSdk = 36**, **minSdk = 31**, **targetSdk = 36**
- Uses Kotlin 2.2.10 with Compose compiler plugin (not the old kotlinCompilerExtensionVersion approach). AGP 9.2.1.
- Room 2.7.1 uses `kapt` (not KSP). Schema output: `app/schemas/`. Current DB version: **8**.
- Version catalog: `gradle/libs.versions.toml`
- App version: `versionCode = 9`, `versionName = "2.0.1"`
- Unit test stack: `kotlinx-coroutines-test`, `mockito-core` + `mockito-kotlin`, `turbine` (Flow testing), `robolectric`

## Conventions

- **File naming**: Screens use snake_case (`add_Note.kt`, `note_screen.kt`); other files use PascalCase.
- **State management**: `StateFlow` + `collectAsState()` in Compose; no LiveData.
- **Coroutines**: Use `Dispatchers.IO` for DB/network via `viewModelScope` or direct coroutine builders.
- **Firebase path structure**: `users/{accountId}/notes/{deviceId}/{noteId}`
- **Room entities** track sync state with a `synced` boolean column; pending deletions use a separate `PendingDeletionEntity` table. `NoteEntity` also has `isPinned`, `isArchived`, `category`, `colorKey` columns (added in migration 7→8).
- **No Hilt/Dagger**: Dependencies are manually instantiated (singletons, constructor parameters). Don't add DI frameworks without explicit request.
- **Rich text**: Notes use `compose-rich-editor` (MohamedRejeb) for editing and display. HTML is the storage format. All HTML stripping goes through `RichTextBridge.stripHtmlToPlainText()` (Jsoup-backed). Pasted Markdown is auto-converted via `MarkdownToHtmlConverter`. Clipboard/share HTML is sanitized via `RichTextSanitizer` before insertion.
- **Reminders**: `ReminderEntity` supports recurrence fields (`recurrenceType`, `recurrenceInterval`, `recurrenceDaysOfWeek`, `recurrenceEndDate`, `parentReminderId`).
- **NoteEntityMapper**: Always use `NoteEntityMapper.toEntity()` / `NoteEntityMapper.toDomain()` when converting between `dataclass` and `NoteEntity` — handles encrypt/decrypt automatically.
- **In-app notifications**: Use `NotificationHelper.showSuccess/showWarning/showInfo()` (from `components/NotificationComponent.kt`) for transient UI feedback; do not show system notifications for in-app state.
- **Passphrase hashing**: Use `HashUtils.hashPassphrase()` / `HashUtils.verifyPassphrase()` (PBKDF2-HMAC-SHA256, 120k iterations). Plain `HashUtils.sha256()` is only for non-security uses.
- **Sync modes**: `SyncMode` enum (`LOCAL_ONLY`, `CONTINUOUS`, `ONE_TIME_IMPORTED`) tracks how device identity was established. Read/write via `PassphraseManager.getSyncMode()` / `setSyncMode()`.

## Critical Patterns

1. **Adding a new screen**: Create composable in `design/`, add route in `navbar.kt` NavHost, pass dependencies manually.
2. **Adding a Room entity**: Create Entity + DAO in `room/`, update `AppDatabase` `@Database(entities=[...])`, increment DB version, add migration in `AppDatabase.kt`.
3. **Firebase sync**: Always encrypt before writing to Firebase, decrypt after reading. Use the Note model's built-in encryption methods.
4. **Reminders pipeline**: `SmartReminderAI` (ML Kit regex) or `GeminiReminderParser` (Gemini 2.0 Flash, requires user API key) detects → `ReminderRepository.createReminder()` persists entity → `ReminderScheduler.scheduleReminder()` registers both an AlarmManager exact alarm AND a WorkManager `ReminderWorker` as backup → `SystemNotificationHelper` fires the system notification. For recurring reminders, `ReminderWorker` computes the next occurrence via `RecurrenceCalculator.getNextOccurrence()` and re-inserts a new `ReminderEntity`.
5. **Adding a ViewModel**: Create in `viewmodel/`, extend `AndroidViewModel`, expose state via `StateFlow`, emit one-shot events via `SharedFlow`.
6. **Gemini API key management**: Keys live only on-device in `EncryptedSharedPreferences` (no developer key bundled). Access via `GeminiKeyManager.getActiveApiKey(context)`. Call `GeminiReminderParser.invalidate()` after the user adds/removes keys. Respect the 15-calls/minute free-tier rate limit already enforced in `GeminiReminderParser`.
7. **Smart Action Chips**: `SmartEntityDetector.analyze(context, text, noteId)` returns `List<DetectedEntity>`; pass to `SmartActionChipRow` composable. Invalidate the note's cache entry with `SmartEntityDetector.invalidateCache(noteId)` on every save.

## External Services

- **Firebase**: Realtime Database (note sync), Auth (anonymous), Firestore, Crashlytics, Analytics
- **Google AI (Gemini)**: `generativeai:0.9.0`, model `gemini-2.0-flash`. No developer key bundled — user provides their own free key via AI Settings; stored via `GeminiKeyManager` in `EncryptedSharedPreferences`. Multi-key support with usage tracking and rotation.
- **ML Kit**: `entity-extraction:16.0.0-beta6` for NLP reminder detection
- **CameraX + ML Kit Barcode**: QR code scanning for device pairing
- **ZXing**: QR code generation
- **Glance**: App widget framework (`QuickNoteWidget`) for home screen quick capture
- **AndroidX PDF Viewer**: `pdf-viewer:1.0.0-alpha10`
- **AndroidX Browser**: Custom Tabs for Smart Action Chips (`browser:1.8.0`)
- **AndroidX Security Crypto**: `security-crypto:1.1.0-alpha06` for `EncryptedSharedPreferences` (Gemini key storage)
- **compose-rich-editor**: `richeditor-compose:1.0.0-rc14` (MohamedRejeb) for rich text editing/display
- **Jsoup**: `1.18.1` for HTML parsing/stripping in `RichTextBridge` and `RichTextSanitizer`
