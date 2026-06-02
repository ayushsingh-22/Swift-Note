# AGENTS.md

## Project Overview

SwiftNote is an offline-first Android note-taking app (package: `com.amvarpvtltd.swiftNote`, applicationId: `com.amvarpvtltd.selfnote`) built with Kotlin, Jetpack Compose, Room, Firebase Realtime Database, and ML Kit. It features E2E encryption, multi-device sync via passphrase/QR, and AI-powered reminder detection.

## Architecture

- **MVVM + Repository pattern** — UI (Compose) → ViewModel → Repository → Room (local) + Firebase (remote)
- **ViewModels**: Dedicated `viewmodel/` directory with `AddNoteViewModel`, `NotesViewModel`, `ViewNoteViewModel` (extend `AndroidViewModel`, use `StateFlow` + `SharedFlow` for UI events).
- **Offline-first**: All writes go to Room first (`synced=false`), then background-sync to Firebase. See `repository/NoteRepository.kt` and `offline/OfflineNoteManager.kt`.
- **Encryption**: Notes are encrypted/decrypted using device ID and passphrase keys. Core logic lives in `dataclass.kt` (`Note.getEncryptedTitle()`, `Note.fromEncryptedData()`).
- **Singletons**: `SmartReminderAI`, `ReminderManager`, `SyncManager` use object/singleton patterns.
- **Navigation**: Single-Activity architecture with Compose NavHost defined in `navbar.kt`. Routes: `onboarding`, `main`, `addscreen`, `addscreen/{noteId}`, `viewnote/{noteId}`, `syncSettings`, `offlineSyncScreen`, `aiSettings`, `archive`, `today`. All routes use slide+fade enter/exit transitions defined in `NavigationComponent`.
- **Theme**: `AppThemeState` (object in `theme/ThemeManager.kt`) is the global `StateFlow<ThemeMode>` for reactive theming. Initialize with `AppThemeState.initialize(context)` in `MyApp`; update with `AppThemeState.setTheme(context, mode)` from any screen. `ThemeMode` values: `LIGHT`, `DARK`, `SYSTEM`.

## Key Source Paths

```
app/src/main/java/com/amvarpvtltd/swiftNote/
├── ai/
│   ├── SmartReminderAI.kt          # ML Kit entity extraction + regex fallback
│   ├── GeminiReminderParser.kt     # Reminder parser — now routes through LlmService (Gemini or Groq)
│   ├── SmartEntityDetector.kt      # Unified entity detector: TextClassifier + regex, LRU-cached by noteId
│   ├── AITitleGenerator.kt         # Multi-model title generator — now routes through LlmService; rate-limited (3/min, 60/day); falls back to AutoTitleGenerator
│   ├── DetectedEntity.kt           # Sealed hierarchy: PhoneNumber, Email, Url, Address, DateTime, Amount, TrackingNumber
│   ├── LlmService.kt               # **NEW** Unified LLM service (single entry-point for all AI calls); routes to Gemini or Groq based on active key's provider; Gemini rate-limited (5/min, 80/day), Groq rate-limited (30/min, 250/day); 12s timeout; singleton via LlmService.getInstance(context)
│   ├── LlmProvider.kt              # **NEW** Enum: GEMINI, GROQ + LlmModels object (GEMINI_MODELS, GROQ_MODELS free-tier lists)
│   ├── GroqClient.kt               # **NEW** Groq REST client (OpenAI-compatible endpoint, java.net only, no extra HTTP lib); free tier: 30 req/min, 14,400 req/day; models: llama-3.3-70b-versatile, llama-3.1-8b-instant, gemma2-9b-it, mixtral-8x7b-32768
│   └── ReminderIntentAnalyzer.kt   # **NEW** Pre-LLM intent filter; scores sentences for explicit/action/temporal signals (English + Hinglish); use hasReminderIntent() before invoking LlmService to avoid wasted calls
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
│   ├── TodayScreen.kt              # Daily Review screen — today's reminders, recent notes, quick actions
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
│   ├── ReminderScheduler.kt        # AlarmManager-only scheduler (setExactAndAllowWhileIdle with inexact fallback)
│   ├── SystemNotificationHelper.kt # Posts system notifications, cancels by reminderId
│   └── NotificationActionReceiver.kt # BroadcastReceiver for notification action intents
├── reminders/
│   ├── ReminderManager.kt          # Scheduling entry point + broadcast extras constants
│   ├── ReminderRepository.kt       # CRUD for ReminderEntity + delegates to ReminderScheduler
│   ├── ReminderEntity.kt / ReminderDao.kt / ReminderData.kt
│   └── RecurrenceCalculator.kt     # Pure-function next-occurrence calculator (DAILY/WEEKLY/MONTHLY/YEARLY)
├── security/
│   ├── EncryptionUtil.kt           # AES note encryption
│   ├── GeminiKeyManager.kt         # Multi-key LLM API key storage (Gemini + Groq) in EncryptedSharedPreferences; GeminiApiKey now has a `provider` field (LlmProvider.name); use getActiveKeyObject() for provider-aware routing
│   └── HashUtils.kt                # PBKDF2-HMAC-SHA256 (120k iter) for passphrases; SHA-256 for checksums
├── categories/CategoryManager.kt   # Note categories with preset colors
├── checklist/ChecklistParser.kt    # Checklist item parsing
├── search/SearchAndSortManager.kt  # Search, filtering, and sort (8 modes)
├── settings/DataManagementScreen.kt # Data management settings UI
├── permissions/                    # PermissionUtils, PermissionManager
├── cleanup/DataCleanupManager.kt   # Complete app data cleanup
├── widget/                         # QuickNoteWidget, QuickNoteWidgetReceiver, WidgetUpdateWorker (Glance)
├── utils/                          # ValidationUtils, UIUtils, ShareUtils, QRUtils, PreferenceManager, NetworkManager, AutoSyncManager, AppContext, Constants, AutoTitleGenerator (rule-based 8-strategy title pipeline)
├── ui/
│   ├── theme/                      # Theme.kt, Colors.kt, Type.kt
│   └── base/BaseFullScreenActivity.kt  # Edge-to-edge + immersive sticky base activity
├── theme/ThemeManager.kt           # ThemeManager (SharedPrefs r/w) + AppThemeState (global StateFlow<ThemeMode> singleton; LIGHT/DARK/SYSTEM)
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
4. **Reminders pipeline**: `ReminderIntentAnalyzer.hasReminderIntent(body, title)` pre-screens note text (English + Hinglish signals) before invoking the LLM — skip LLM calls when it returns false. Then: `SmartReminderAI` (ML Kit regex) or `GeminiReminderParser` (routes through `LlmService`, supports Gemini + Groq) detects → `ReminderRepository.createReminder()` persists entity → `ReminderScheduler.scheduleReminder()` registers an AlarmManager exact alarm (`setExactAndAllowWhileIdle`, with `setAndAllowWhileIdle` fallback when exact-alarm permission is not granted) → `ReminderReceiver` (BroadcastReceiver) fires → `SystemNotificationHelper` posts the system notification. For recurring reminders, `ReminderReceiver` computes the next occurrence via `RecurrenceCalculator.getNextOccurrence()` and re-inserts a new `ReminderEntity`. `BootReceiver` re-arms all active reminders on `ACTION_BOOT_COMPLETED`. `ExactAlarmPermissionReceiver` (declared inline in `reminders/ReminderManager.kt`, registered in `AndroidManifest.xml` for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`) reschedules all active reminders when the Android 12+ exact-alarm permission is granted/revoked.
5. **Adding a ViewModel**: Create in `viewmodel/`, extend `AndroidViewModel`, expose state via `StateFlow`, emit one-shot events via `SharedFlow`.
6. **LLM/AI key management**: All AI calls now go through `LlmService.getInstance(context).generateText(prompt)` — it automatically routes to Gemini or Groq based on the active key's `provider` field. Keys (both Gemini `AIza...` and Groq `gsk_...`) live only on-device in `EncryptedSharedPreferences` via `GeminiKeyManager`. Call `LlmService.invalidate()` after the user adds/removes/changes keys. Rate limits are provider-specific: Gemini (5/min, 80/day), Groq (30/min, 250/day). `GeminiKeyManager.getActiveKeyObject(context)` returns `GeminiApiKey` with `.provider` for direct provider-aware routing. `LlmService.isAvailable()` returns false when no key is configured.
7. **Smart Action Chips**: `SmartEntityDetector.analyze(context, text, noteId)` returns `List<DetectedEntity>`; pass to `SmartActionChipRow` composable. Invalidate the note's cache entry with `SmartEntityDetector.invalidateCache(noteId)` on every save.
8. **Auto title generation**: Two-tier pipeline — `AITitleGenerator.getInstance(context).generate(description)` (suspend, routes through `LlmService` with multi-model rotation for both Gemini and Groq) falls back automatically to `AutoTitleGenerator.generate(description)` (sync, 8-strategy rule-based, offline-safe). Use `AutoTitleGenerator` directly for live suggestion chips (instant, no coroutine needed). `AITitleGenerator.isAvailable()` returns false when no LLM key is set.
9. **Theme switching**: Set theme reactively via `AppThemeState.setTheme(context, ThemeMode.DARK/LIGHT/SYSTEM)`; observe via `AppThemeState.themeMode.collectAsState()`. Never call `ThemeManager` directly from UI — always go through `AppThemeState`.

## External Services

- **Firebase**: Realtime Database (note sync), Auth (anonymous), Firestore, Crashlytics, Analytics
- **Google AI (Gemini)**: `generativeai:0.9.0`, models rotated from `LlmModels.GEMINI_MODELS` (gemini-2.5-flash, gemini-2.5-flash-lite, gemini-2.0-flash, etc.). No developer key bundled — user provides their own free key via AI Settings; stored via `GeminiKeyManager` in `EncryptedSharedPreferences`. Multi-key support with usage tracking and rotation.
- **Groq**: REST client (`GroqClient.kt`, no extra HTTP lib) using OpenAI-compatible endpoint. Free tier: 30 req/min, 14,400 req/day. User provides `gsk_...` key via AI Settings; stored alongside Gemini keys in `GeminiKeyManager`.
- **ML Kit**: `entity-extraction` for NLP reminder detection — note that `gradle/libs.versions.toml` pins `16.0.0-beta5` but `app/build.gradle.kts` hardcodes `16.0.0-beta6` (the hardcoded value wins; keep them in sync when bumping).
- **CameraX + ML Kit Barcode**: QR code scanning for device pairing
- **ZXing**: QR code generation
- **Glance**: App widget framework (`QuickNoteWidget`) for home screen quick capture
- **AndroidX PDF Viewer**: `pdf-viewer:1.0.0-alpha10`
- **AndroidX Browser**: Custom Tabs for Smart Action Chips (`browser:1.8.0`)
- **AndroidX Security Crypto**: `security-crypto:1.1.0-alpha06` for `EncryptedSharedPreferences` (Gemini key storage)
- **compose-rich-editor**: `richeditor-compose:1.0.0-rc14` (MohamedRejeb) for rich text editing/display
- **Jsoup**: `1.18.1` for HTML parsing/stripping in `RichTextBridge` and `RichTextSanitizer`


