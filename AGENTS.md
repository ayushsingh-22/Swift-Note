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
├── ai/SmartReminderAI.kt          # ML Kit entity extraction + regex fallback
├── viewmodel/                      # AddNoteViewModel, NotesViewModel, ViewNoteViewModel
├── room/                           # Room DB: AppDatabase, NoteDao, NoteEntity, PendingDeletionDao
├── repository/NoteRepository.kt    # Offline-first CRUD, Firebase sync
├── offline/OfflineNoteManager.kt   # Local Room operations, StateFlow for pending notes
├── sync/SyncManager.kt             # Cross-device sync via passphrase decryption
├── design/                         # All Compose screens (add_Note.kt, note_screen.kt, ViewNoteScreen.kt)
├── components/                     # Reusable UI composables (ReminderComponents)
├── notifications/                  # System notification scheduling
├── reminders/                      # ReminderManager + ReminderEntity (with recurrence support)
├── security/                       # Encryption utilities
├── categories/CategoryManager.kt   # Note categories with preset colors
├── checklist/ChecklistParser.kt    # Checklist item parsing
├── search/SearchAndSortManager.kt  # Search, filtering, and sort (8 modes)
├── settings/DataManagementScreen.kt # Data management settings UI
├── permissions/                    # PermissionUtils, PermissionManager
├── cleanup/DataCleanupManager.kt   # Complete app data cleanup
├── utils/                          # ValidationUtils, UIUtils, ShareUtils, QRUtils, PreferenceManager, NetworkManager, AutoSyncManager, AppContext, Constants
├── ui/theme/                       # Theme.kt, Colors.kt, Type.kt
├── theme/ThemeManager.kt           # Theme state management
├── dataclass.kt                    # Note model with encryption methods
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
- Uses Kotlin 2.2.10 with Compose compiler plugin (not the old kotlinCompilerExtensionVersion approach)
- Room uses `kapt` (not KSP). Schema output: `app/schemas/`. Current DB version: **8**.
- Version catalog: `gradle/libs.versions.toml`

## Conventions

- **File naming**: Screens use snake_case (`add_Note.kt`, `note_screen.kt`); other files use PascalCase.
- **State management**: `StateFlow` + `collectAsState()` in Compose; no LiveData.
- **Coroutines**: Use `Dispatchers.IO` for DB/network via `viewModelScope` or direct coroutine builders.
- **Firebase path structure**: `users/{accountId}/notes/{deviceId}/{noteId}`
- **Room entities** track sync state with a `synced` boolean column; pending deletions use a separate `PendingDeletionEntity` table. `NoteEntity` also has `isPinned`, `isArchived`, `category`, `colorKey` columns (added in migration 7→8).
- **No Hilt/Dagger**: Dependencies are manually instantiated (singletons, constructor parameters). Don't add DI frameworks without explicit request.
- **Rich text**: Notes support HTML formatting (bold/italic/underline) via clipboard detection.
- **Reminders**: `ReminderEntity` supports recurrence fields (`recurrenceType`, `recurrenceInterval`, `recurrenceDaysOfWeek`, `recurrenceEndDate`, `parentReminderId`).

## Critical Patterns

1. **Adding a new screen**: Create composable in `design/`, add route in `navbar.kt` NavHost, pass dependencies manually.
2. **Adding a Room entity**: Create Entity + DAO in `room/`, update `AppDatabase` `@Database(entities=[...])`, increment DB version, add migration in `AppDatabase.kt`.
3. **Firebase sync**: Always encrypt before writing to Firebase, decrypt after reading. Use the Note model's built-in encryption methods.
4. **Reminders**: `SmartReminderAI` detects date/time from text → `ReminderManager` schedules via AlarmManager → `NotificationHelper` fires notification.
5. **Adding a ViewModel**: Create in `viewmodel/`, extend `AndroidViewModel`, expose state via `StateFlow`, emit one-shot events via `SharedFlow`.

## External Services

- **Firebase**: Realtime Database (note sync), Auth (anonymous), Firestore, Crashlytics
- **ML Kit**: `entity-extraction:16.0.0-beta6` for NLP reminder detection
- **CameraX + ML Kit Barcode**: QR code scanning for device pairing
- **ZXing**: QR code generation

