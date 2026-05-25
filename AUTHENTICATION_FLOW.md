# SwiftNote — Authentication & Login Flow

> **Package:** `com.amvarpvtltd.swiftNote` | **App ID:** `com.amvarpvtltd.selfnote`
> **Last Updated:** May 25, 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Identity Concepts](#2-core-identity-concepts)
3. [Key Components & Files](#3-key-components--files)
4. [Master Flow Diagram](#4-master-flow-diagram)
5. [Scenario Walkthroughs](#5-scenario-walkthroughs)
   - [5.1 Fresh Install — No Firebase Data](#51-scenario-1--fresh-install-no-firebase-data)
   - [5.2 Returning User — Passphrase Exists](#52-scenario-2--returning-user-passphrase-exists)
   - [5.3 Reinstall — Same Device, Firebase Has Data](#53-scenario-3--reinstall-same-device-firebase-has-data)
   - [5.4 Restore via QR Code](#54-scenario-4--restore-via-qr-code-new-device)
   - [5.5 Restore via Manual Passphrase](#55-scenario-5--restore-via-manual-passphrase)
   - [5.6 Start Fresh — Old Local Data Present](#56-scenario-6--start-fresh-old-local-data-present)
   - [5.7 Offline First Launch](#57-scenario-7--offline-first-launch)
6. [Firebase Authentication Sub-Flow](#6-firebase-authentication-sub-flow)
7. [Onboarding Screen Options](#7-onboarding-screen-options)
8. [SyncManager — Note Import Flow](#8-syncmanager--note-import-flow)
9. [Passphrase Storage & Encryption](#9-passphrase-storage--encryption)
10. [State Machine Summary](#10-state-machine-summary)
11. [Navigation Routes Reference](#11-navigation-routes-reference)
12. [Security Notes](#12-security-notes)

---

## 1. Overview

SwiftNote uses a **passphrase-based identity system** — not traditional email/password login. There is no user account creation form. Instead:

- Every device generates a **unique passphrase** that acts as its "account key".
- The passphrase determines the **Firebase Realtime Database path** where notes are stored: `users/{passphrase}/notes/{deviceId}`.
- Firebase Anonymous Auth is used solely to satisfy Firebase security rules (`auth != null`). It does **not** represent the user's identity.
- The app is **offline-first**: all reads/writes go to Room (SQLite) first; Firebase is a background sync layer.

```
Identity Model:
┌──────────────────────────────────────────────────────────┐
│  passphrase  ──►  Firebase path root  ──►  user's notes  │
│  (UUID or adjective-noun-NNN format)                     │
│                                                          │
│  deviceId    ──►  Firebase sub-path   ──►  this device   │
│  (hardware-based stable ID)                              │
│                                                          │
│  Firebase UID ──►  auth only, NOT identity               │
│  (anonymous, ephemeral per install)                      │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Core Identity Concepts

| Concept | Storage Location | Format | Lifetime | Purpose |
|---|---|---|---|---|
| **deviceId** | `device_id_prefs` SharedPrefs | `hex-string` (hardware-based) or UUID fallback | Stable per install | Firebase sub-path `notes/{deviceId}` |
| **passphrase** | `passphrase_prefs` SharedPrefs (Keystore-encrypted) | UUID string (Start Fresh) or `adjective-noun-NNN` (QR/sync) | Until app reset / reinstall | Firebase root path `users/{passphrase}` |
| **Firebase UID** | Firebase SDK in-memory | Firebase anonymous UID | Per install (ephemeral) | Satisfies `auth != null` in DB rules |
| **Firebase path** | Firebase RTDB | `users/{passphrase}/notes/{deviceId}/{noteId}` | Persistent in cloud | Remote note storage |

---

## 3. Key Components & Files

| File | Role in Auth/Login |
|---|---|
| `navbar.kt` → `MyApp()` | Entry point. Reads stored passphrase, checks Firebase for reinstall data, determines `startDestination` |
| `design/OnboardingScreen.kt` | Shows the 3 login options (Start Fresh, QR, Passphrase) |
| `auth/PassphraseManager.kt` | Stores/retrieves passphrase (Keystore encryption), Firebase anonymous auth |
| `auth/DeviceManager.kt` | Thin wrapper over `DeviceIdManager` |
| `auth/DeviceIdManager.kt` | Generates/stores stable `deviceId` in SharedPrefs |
| `sync/SyncManager.kt` | Downloads + decrypts notes from a source Firebase passphrase path |
| `cleanup/DataCleanupManager.kt` | Wipes local Room notes; best-effort Firebase deletion |
| `repository/NoteRepository.kt` | Resolves Firebase path using stored passphrase; syncs notes |
| `room/AppDatabase.kt` | Local Room DB; all notes written here first |

---

## 4. Master Flow Diagram

```
╔═══════════════════════════════════════════════════════════════╗
║                       APP LAUNCH                              ║
║   MainActivity.onCreate() → MyApp() composable               ║
║   isInitializing = true  →  [ LOADING SCREEN shown ]         ║
╚═══════════════════════════════════════════════════════════════╝
                              │
                              ▼
              ┌───────────────────────────────┐
              │   LaunchedEffect (once)       │
              │   Read stored passphrase      │
              │   from Keystore-encrypted     │
              │   SharedPreferences           │
              └───────────────┬───────────────┘
                              │
             ┌────────────────▼────────────────┐
             │   storedPassphrase != null ?     │
             └────────────┬────────────┬────────┘
                         YES           NO
                          │             │
                          ▼             ▼
               ┌──────────────┐  ┌──────────────────────────────┐
               │ Set identity │  │  getOrCreateDeviceId()       │
               │ from stored  │  │  Read hardware ID or gen UUID│
               │ passphrase   │  └──────────────┬───────────────┘
               │              │                 │
               │ startDest=   │                 ▼
               │  "main"      │  ┌──────────────────────────────┐
               └──────┬───────┘  │  Firebase: GET               │
                      │          │   users/{deviceId}           │
                      │          │  [5-second timeout]          │
                      │          └──────────────┬───────────────┘
                      │                         │
                      │          ┌──────────────▼──────────────┐
                      │          │  Firebase node exists?       │
                      │          └────────┬────────────┬────────┘
                      │                 YES            NO or Timeout
                      │                  │              │
                      │                  ▼              ▼
                      │     ┌─────────────────┐  ┌────────────────┐
                      │     │ SyncManager     │  │ startDest=     │
                      │     │ .syncDataFrom   │  │ "onboarding"   │
                      │     │  Passphrase(    │  └───────┬────────┘
                      │     │  deviceId,      │          │
                      │     │  deviceId)      │          │
                      │     └────────┬────────┘          │
                      │              │                    │
                      │     ┌────────▼────────┐          │
                      │     │  Sync success?  │          │
                      │     └──────┬────┬─────┘          │
                      │           YES   NO                │
                      │            │    │                 │
                      │            ▼    ▼                 │
                      │    ┌──────────┐ ┌──────────────┐  │
                      │    │ start=   │ │  start=      │  │
                      │    │ "main"   │ │ "onboarding" │  │
                      │    └────┬─────┘ └──────┬───────┘  │
                      │         │              │           │
                      └────┬────┘              └─────┬─────┘
                           │                         │
                           ▼                         ▼
                  isInitializing=false      isInitializing=false
                           │                         │
                           ▼                         ▼
                 ┌──────────────────┐     ┌──────────────────────┐
                 │   MAIN SCREEN    │     │  ONBOARDING SCREEN   │
                 │  (NotesScreen)   │     │  3 options displayed │
                 └──────────────────┘     └──────────┬───────────┘
                                                     │
                          ┌──────────────────────────┼──────────────────────────┐
                          │                          │                          │
                          ▼                          ▼                          ▼
              ┌───────────────────┐      ┌────────────────────┐    ┌─────────────────────┐
              │  "Start Fresh"    │      │ "Restore via QR"   │    │ "Enter Passphrase   │
              │                   │      │                    │    │  Manually"          │
              └─────────┬─────────┘      └─────────┬──────────┘    └──────────┬──────────┘
                        │                          │                           │
                        ▼                          ▼                           ▼
           ┌────────────────────────┐  ┌─────────────────────┐  ┌──────────────────────────┐
           │ 1. Check Room DB (IO)  │  │ Open camera scanner │  │ Show dialog: type        │
           │ 2. If notes → clear    │  │ Scan QR code        │  │ passphrase string        │
           │    local notes         │  └──────────┬──────────┘  └──────────────┬───────────┘
           │ 3. Generate fresh UUID │             │                             │
           │    as passphrase       │             └──────────────┬──────────────┘
           │ 4. storePassphrase()   │                            ▼
           │ 5. Navigate → main     │          ┌───────────────────────────────────────┐
           └────────────────────────┘          │  extractPassphraseFromQR() / input    │
                                               │  → sourcePassphrase extracted         │
                                               └────────────────┬──────────────────────┘
                                                                │
                                                                ▼
                                               ┌───────────────────────────────────────┐
                                               │  1. storePassphrase(deviceId)         │
                                               │  2. clearLocalNotesOnly() [IO]        │
                                               │  3. SyncManager.syncDataFrom          │
                                               │     Passphrase(source, deviceId)      │
                                               │     ├─ Firebase auth (anonymous)      │
                                               │     ├─ GET users/{source}/notes       │
                                               │     ├─ Decrypt each note              │
                                               │     ├─ Insert into Room DB            │
                                               │     └─ Re-upload to users/{deviceId}  │
                                               │  4. Navigate → main                   │
                                               └───────────────────────────────────────┘
```

---

## 5. Scenario Walkthroughs

---

### 5.1 Scenario 1 — Fresh Install, No Firebase Data

**Trigger:** User installs the app for the first time on a device that has no previous SwiftNote data anywhere.

**Step-by-step:**

```
LAUNCH
  │
  ├─ 1. getStoredPassphrase() → null  (SharedPrefs empty)
  │
  ├─ 2. getOrCreateDeviceId()
  │       → generateUniqueDeviceId() using hardware identifiers
  │       → Stored in device_id_prefs
  │
  ├─ 3. Firebase GET users/{deviceId} [5s timeout]
  │       → snapshot.exists() = FALSE
  │
  ├─ 4. startDestination = "onboarding"
  │
  └─ 5. ONBOARDING SCREEN shown
           │
           └─ User taps "Start Fresh"
                 │
                 ├─ a. Room DB has 0 notes → skip cleanup
                 ├─ b. freshPassphrase = UUID.randomUUID().toString()
                 ├─ c. storePassphrase(context, freshPassphrase)
                 │       → Keystore-encrypts passphrase
                 │       → Writes to passphrase_prefs
                 │       → Firebase: SET users/{freshPassphrase}
                 │         { createdAt, deviceType, lastActiveAt }
                 └─ d. navigate("main") → popUpTo("onboarding") inclusive=true
```

**Result:** User lands on a blank `"main"` screen. Notes are stored under `users/{freshUUID}/notes/{deviceId}`.

---

### 5.2 Scenario 2 — Returning User (Passphrase Exists)

**Trigger:** User re-opens the app after having previously set it up.

**Step-by-step:**

```
LAUNCH
  │
  ├─ 1. getStoredPassphrase()
  │       → Read passphrase_prefs KEY_PASSPHRASE_ENCRYPTED
  │       → EncryptionUtil.decryptWithKeystore(encryptedValue)
  │       → Returns stored passphrase string  ✓
  │
  ├─ 2. DeviceIdentity.set(storedPassphrase, "NavHost.storedPassphrase")
  │
  ├─ 3. startDestination = "main"  — no Firebase query needed
  │
  ├─ 4. isInitializing = false
  │
  └─ 5. MAIN SCREEN shown immediately
           │
           └─ NoteRepository.fetchNotes()
                 ├─ Load from Room (instant, offline-first)
                 └─ Background: syncFromCloudInBackground()
                       → resolveNotesRef() builds path from stored passphrase
                       → Downloads any new/updated notes from Firebase
                       → Conflict resolution: local wins if newer timestamp
```

**Result:** Notes appear instantly from Room. Any cloud changes sync quietly in background. Total "login" time ≈ 0ms — purely a SharedPrefs read.

---

### 5.3 Scenario 3 — Reinstall (Same Device, Firebase Has Data)

**Trigger:** User uninstalls and reinstalls the app. SharedPrefs are wiped but hardware deviceId is regenerated identically. Old notes still exist in Firebase under `users/{deviceId}`.

> **Note:** This only works if the original passphrase happened to equal the deviceId (i.e., the very first version of the app used deviceId as the passphrase). If the user used "Start Fresh" (UUID passphrase), the UUID is lost on reinstall and auto-recovery is NOT possible — they must use QR or manual passphrase.

**Step-by-step:**

```
LAUNCH
  │
  ├─ 1. getStoredPassphrase() → null  (SharedPrefs wiped by uninstall)
  │
  ├─ 2. getOrCreateDeviceId()
  │       → generateUniqueDeviceId() → returns SAME ID as before (hardware-stable)
  │
  ├─ 3. Firebase GET users/{deviceId} [5s timeout]
  │       → snapshot.exists() = TRUE  (old notes are here)
  │
  ├─ 4. SyncManager.syncDataFromPassphrase(context, deviceId, deviceId)
  │       ├─ ensureAuthenticated() → Firebase anonymous sign-in
  │       ├─ GET users/{deviceId}/notes  → fetch all note nodes
  │       ├─ For each note: tryDecryptWithCandidates([deviceId, deviceId])
  │       ├─ Insert decrypted notes into Room
  │       ├─ uploadLocalDataToFirebase() → re-upload notes to same path
  │       └─ Returns Result.success(SyncResult)
  │
  ├─ 5. DeviceIdentity.set(deviceId, "NavHost.remoteImport")
  │
  ├─ 6. startDestination = "main"
  │
  └─ 7. MAIN SCREEN — user sees all their old notes restored  ✓
```

**Result:** Seamless auto-recovery. User sees their notes without any manual action.

---

### 5.4 Scenario 4 — Restore via QR Code (New Device)

**Trigger:** User has SwiftNote on Device A and wants to migrate/sync to Device B by scanning the QR code shown on Device A.

**QR Code format:** `SwiftNote://sync?passphrase={sourcePassphrase}`

**Step-by-step:**

```
Device B — ONBOARDING SCREEN
  │
  └─ User taps "Restore via QR Code"
        │
        ├─ 1. QRScannerSection opens camera
        │
        ├─ 2. QR scanned → raw content string received
        │
        ├─ 3. extractPassphraseFromQR(qrContent)
        │       Tries in order:
        │       a. URL-decode up to 3 times
        │       b. Find "SwiftNote://sync?passphrase=" prefix → extract value
        │       c. JSON parse for "passphrase" key
        │       d. URI query param parse
        │       e. Direct token / UUID heuristics
        │       → Returns sourcePassphrase  ✓
        │
        ├─ 4. Check: sourcePassphrase != null ?
        │       NO → Toast "Invalid QR code format" → abort
        │
        ├─ 5. currentPassphrase = getOrCreateDeviceId(context)
        │       (Device B's deviceId becomes its passphrase)
        │
        ├─ 6. storePassphrase(context, currentPassphrase)
        │       → Keystore-encrypt, write to passphrase_prefs
        │       → Firebase: SET users/{currentPassphrase} metadata
        │
        ├─ 7. clearLocalNotesOnly(context)
        │       → DELETE FROM notes     [IO thread]
        │       → DELETE FROM pending_deletions
        │
        ├─ 8. SyncManager.syncDataFromPassphrase(
        │         context,
        │         sourcePassphrase,    ← Device A's passphrase
        │         currentPassphrase    ← Device B's passphrase
        │     )
        │       ├─ ensureAuthenticated()
        │       ├─ GET users/{sourcePassphrase}/notes  (Device A's Firebase data)
        │       ├─ For each note:
        │       │     tryDecryptWithCandidates([currentPassphrase,
        │       │       noteData.deviceId, sourcePassphrase, globalDeviceId])
        │       │     → Insert decrypted note into Device B's Room DB
        │       └─ uploadLocalDataToFirebase(context, currentPassphrase)
        │             → Push all notes to users/{currentPassphrase}/notes/{deviceB_id}
        │
        └─ 9. navigate("main") → Device B shows all notes from Device A  ✓
```

**Result:** Device B has a full copy of Device A's notes. Both devices are now independent (same content, different Firebase paths).

---

### 5.5 Scenario 5 — Restore via Manual Passphrase

**Trigger:** User knows their passphrase string from a previous device/install and types it directly.

**Flow is identical to Scenario 4** except:
- Step 2–3 are replaced by the user typing text in `EnhancedSyncFromDeviceDialog`.
- The typed string **is** the `sourcePassphrase` directly (no QR parsing).
- `inputPassphrase.isBlank()` check → `errorMessage = "Please enter a passphrase"` guard.

```
ONBOARDING SCREEN
  │
  └─ User taps "Enter Passphrase Manually"
        │
        ├─ EnhancedSyncFromDeviceDialog shown
        │
        ├─ User types passphrase → taps Sync/Restore
        │
        ├─ isBlank() check → error if empty
        │
        └─ Same as steps 5–9 of Scenario 4
               sourcePassphrase = inputPassphrase (typed text)
               currentPassphrase = getOrCreateDeviceId(context)
```

---

### 5.6 Scenario 6 — "Start Fresh" (Old Local Data Present)

**Trigger:** User opens onboarding (perhaps after an in-app reset, or if the passphrase was somehow cleared) and there are already notes in the local Room DB.

**Step-by-step:**

```
ONBOARDING SCREEN
  │
  └─ User taps "Start Fresh"
        │
        ├─ 1. [IO thread] Room query: getAllNotesIncludingArchived()
        │       → Returns list size > 0  → hasLocalData = true
        │
        ├─ 2. DataCleanupManager.clearLocalNotesOnly(context)
        │       → [IO thread]
        │       → noteDao.deleteAllNotes()      (DELETE FROM notes)
        │       → pendingDeletionDao.clearAll() (DELETE FROM pending_deletions)
        │       → Returns Result.success()
        │
        ├─ 3. freshPassphrase = UUID.randomUUID().toString()
        │       WHY UUID and not deviceId?
        │       → If deviceId were used, NoteRepository.syncFromCloudInBackground()
        │          would immediately re-download old notes from Firebase
        │          (since Firebase still has them under users/{deviceId})
        │       → A fresh UUID creates a zero-history Firebase path → nothing to sync
        │
        ├─ 4. storePassphrase(context, freshPassphrase)
        │       → Keystore-encrypt → write to passphrase_prefs
        │       → Firebase: SET users/{freshUUID} metadata
        │
        └─ 5. navigate("main") → blank notes screen  ✓
                  │
                  └─ syncFromCloudInBackground() runs but
                     users/{freshUUID}/notes/{deviceId} doesn't exist
                     → downloads nothing → stays clean  ✓
```

**Key insight — Why we don't delete from Firebase:**
A previous implementation tried to `removeValue()` from Firebase before starting fresh, but this failed with `Permission denied` because:
- Firebase security rules enforce that a user can only write to their own path
- A new anonymous auth session has a different UID than the session that originally wrote the data
- The solution is to simply **abandon the old path** by creating a new UUID identity

---

### 5.7 Scenario 7 — Offline First Launch

**Trigger:** App launched with no internet connection.

**Step-by-step:**

```
LAUNCH (no network)
  │
  ├─ Case A: Has stored passphrase
  │     → Read from SharedPrefs (offline-capable) ✓
  │     → startDestination = "main"
  │     → Notes shown from Room immediately
  │     → syncFromCloudInBackground() fails silently → no crash
  │
  └─ Case B: No stored passphrase (first install offline)
        │
        ├─ getOrCreateDeviceId() → works offline (hardware/UUID)
        │
        ├─ Firebase GET users/{deviceId}
        │     → withTimeoutOrNull(5_000L) → TIMES OUT after 5 seconds
        │     → returns null → treated as false
        │
        ├─ startDestination = "onboarding"
        │
        └─ User taps "Start Fresh"
                │
                ├─ clearLocalNotesOnly() → works offline (Room only)
                ├─ freshPassphrase = UUID.randomUUID() → works offline
                ├─ storePassphrase() LOCAL part → works offline
                │     (Keystore encrypt + SharedPrefs write = no network)
                ├─ storePassphrase() FIREBASE part → FAILS silently
                │     (Firebase SET users/{freshUUID} metadata → no network)
                │     → caught in try/catch → logged → does not crash
                └─ navigate("main") ✓
                      → Notes written to Room offline
                      → Firebase sync happens when connection restored
                      → AutoSyncManager / SyncWorker (WorkManager) picks it up
```

---

## 6. Firebase Authentication Sub-Flow

```
                   Any Firebase operation attempted
                              │
                              ▼
                PassphraseManager.ensureAuthenticated()
                              │
                   ┌──────────▼──────────┐
                   │  FirebaseAuth       │
                   │  .currentUser       │
                   │  != null ?          │
                   └──────┬──────┬───────┘
                         YES     NO
                          │       │
                          ▼       ▼
                       ✓ skip  FirebaseAuth.signInAnonymously().await()
                                   │
                          ┌────────▼────────┐
                          │   user != null? │
                          └──────┬────┬─────┘
                                YES   NO
                                 │     │
                                 ▼     ▼
                         ✓ success  Result.failure()
                                       │
                                       ▼
                                  Caller handles:
                                  • SyncManager → returns Result.failure()
                                  • DataCleanupManager → logs warning, continues
                                  • App works in local-only mode
```

**Important:** The Firebase anonymous UID is **not** the user's account identity. It is discarded conceptually after auth. The `passphrase` is the actual account identifier used for all data paths.

---

## 7. Onboarding Screen Options

```
┌──────────────────────────────────────────────────────────────────┐
│                     ONBOARDING SCREEN                            │
│                 "How would you like to start?"                   │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ✨  Start Fresh                                           │  │
│  │      Begin with a clean slate. Notes stay private.        │  │
│  │      → Generates brand-new UUID passphrase                │  │
│  │      → No Firebase deletion (abandons old path)           │  │
│  │      → Creates fresh identity                             │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  📷  Restore via QR Code                                  │  │
│  │      Scan QR from another device to import notes.         │  │
│  │      → Opens camera scanner                               │  │
│  │      → Parses SwiftNote://sync?passphrase=XXX             │  │
│  │      → Clears local → Syncs from source passphrase        │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  🔑  Enter Passphrase Manually                            │  │
│  │      Already have a passphrase? Enter it directly.        │  │
│  │      → Type passphrase string                             │  │
│  │      → Same sync flow as QR restore                       │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  [Loading indicator shown while any operation is in-progress]   │
└──────────────────────────────────────────────────────────────────┘
```

### Decision Guide

| User situation | Recommended option |
|---|---|
| Brand new user | **Start Fresh** |
| Phone reset / reinstall, has QR code from old device | **Restore via QR** |
| Phone reset / reinstall, remembers passphrase string | **Enter Passphrase Manually** |
| Using a second phone alongside existing phone | **Restore via QR** or **Passphrase** |
| Reinstall on same hardware | *Automatic* — no onboarding shown (Scenario 3) |

---

## 8. SyncManager — Note Import Flow

```
SyncManager.syncDataFromPassphrase(
    context,
    sourcePassphrase,   ← where to fetch FROM
    currentPassphrase   ← where this device writes TO
)
         │
         ▼
  ensureAuthenticated()
         │
         ▼
  FirebaseDatabase.getInstance()
    .getReference("users")
    .child(sourcePassphrase)
    .child("notes")
    .get().await()                   ← Reads ALL note nodes
         │
         ▼
  For each note node in snapshot:
  ┌────────────────────────────────────────────────┐
  │  tryDecryptWithCandidates(noteData, keys)      │
  │                                                │
  │  candidateKeys = [                             │
  │    currentPassphrase,                          │
  │    noteData.mymobiledeviceid,                  │
  │    sourcePassphrase,                           │
  │    globalDeviceId                              │
  │  ]                                             │
  │                                                │
  │  For each key + variants (lower, upper,        │
  │  strip-dashes, substring-16/20/32, base64):    │
  │    EncryptionUtil.decrypt(title, key)          │
  │    EncryptionUtil.decrypt(description, key)    │
  │    → if both non-null → decryption success ✓   │
  └────────────────────────────────────────────────┘
         │
         ▼
  Preserve local metadata if note already in Room:
    isPinned, isArchived, category, colorKey
         │
         ▼
  noteDao.insert(entity)    ← upsert into Room
         │
         ▼
  uploadLocalDataToFirebase(context, currentPassphrase)
    → Push all local notes to users/{currentPassphrase}/notes/{deviceId}
    → Push reminders to users/{currentPassphrase}/reminders
    → SET lastSyncAt, totalNotes metadata
         │
         ▼
  Return Result.success(SyncResult(syncedNotesCount, source, target))
```

---

## 9. Passphrase Storage & Encryption

```
storePassphrase(context, passphrase)
         │
         ▼
  EncryptionUtil.encryptWithKeystore(passphrase)
         │         Android Keystore — AES/GCM/NoPadding
         │         Key alias: "swiftnote_keystore_key" (or similar)
         ▼
  context.getSharedPreferences("passphrase_prefs")
    .edit()
    .putString(KEY_PASSPHRASE_ENCRYPTED, encryptedValue)
    .remove(KEY_PASSPHRASE)    ← remove any legacy plaintext
    .putBoolean(KEY_MIGRATED, true)
    .apply()


getStoredPassphrase(context)
         │
         ├─ 1. Try KEY_PASSPHRASE_ENCRYPTED → decryptWithKeystore() → return
         │
         ├─ 2. Try legacy KEY_PASSPHRASE (plaintext) → migrate to encrypted → return
         │
         └─ 3. Nothing found → return null


clearStoredPassphrase(context)
         │
         └─ Remove KEY_PASSPHRASE, KEY_PASSPHRASE_ENCRYPTED, KEY_MIGRATED
```

**Security properties:**
- Passphrase never stored in plaintext (post-migration).
- Android Keystore key is hardware-backed on supported devices.
- Passphrase is not transmitted over the wire — only used as a Firebase path segment.
- Notes themselves are also AES-encrypted before leaving the device for Firebase.

---

## 10. State Machine Summary

```
                        ┌────────────┐
                        │   LAUNCH   │
                        └─────┬──────┘
                              │
                    ┌─────────▼──────────┐
                    │  INITIALIZING      │
                    │  (Loading Screen)  │
                    └─────────┬──────────┘
                              │
                 ┌────────────▼─────────────┐
                 │   passphrase stored?      │
                 └────────┬─────────┬────────┘
                         YES         NO
                          │           │
                          │    ┌──────▼──────────────┐
                          │    │ Firebase check       │
                          │    │ users/{deviceId}     │
                          │    │ [5s timeout]         │
                          │    └──────┬─────────┬─────┘
                          │        found        not found
                          │           │              │
                          │    ┌──────▼────┐  ┌──────▼──────┐
                          │    │ Auto-sync │  │  ONBOARDING │◄─────────────────────┐
                          │    │ import    │  │   SCREEN    │                      │
                          │    └──────┬────┘  └──────┬───────┘                     │
                          │        success            │                             │
                          │           │        ┌──────┴──────────────────────┐      │
                          │           │        │                             │      │
                          └─────┬─────┘   ┌───▼────────┐              ┌─────▼─────┐│
                                │         │ Start Fresh │              │  Restore  ││
                                ▼         └─────┬───────┘              │ QR/Manual ││
                         ┌────────────┐         │                      └─────┬─────┘│
                         │   MAIN     │◄────────┘                            │      │
                         │   SCREEN   │◄────────────────────────────────────┘      │
                         └────────────┘                                             │
                              │                                                     │
                    [Notes load from Room]                                          │
                    [Background Firebase sync]                                      │
                              │                                                     │
               [User resets / clears passphrase] ──────────────────────────────────┘
```

---

## 11. Navigation Routes Reference

| Route | Screen | Auth state required |
|---|---|---|
| `onboarding` | `OnboardingScreen` | No passphrase stored |
| `main` | `NotesScreen` | Passphrase stored |
| `addscreen` | `AddScreen` (new note) | Must be on main |
| `addscreen/{noteId}` | `AddScreen` (edit note) | Must be on main |
| `viewnote/{noteId}` | `ViewNoteScreen` | Must be on main |
| `syncSettings` | `SyncSettingsScreen` | Must be on main |
| `offlineSyncScreen` | `OfflineSyncScreen` | Must be on main |
| `aiSettings` | `AISettingsScreen` | Must be on main |
| `archive` | `ArchiveScreen` | Must be on main |

---

## 12. Security Notes

| Concern | How it's handled |
|---|---|
| **Passphrase at rest** | AES-GCM encrypted via Android Keystore in SharedPreferences |
| **Notes at rest (local)** | Room DB — unencrypted locally (device-level protection assumed) |
| **Notes in transit to Firebase** | AES-encrypted with passphrase as key before `setValue()` |
| **Firebase path as "password"** | The passphrase IS the Firebase path — anyone who knows it can read the data if Firebase rules allow. Keep passphrase/QR secret. |
| **Firebase auth** | Anonymous sign-in (`signInAnonymously`) — satisfies `auth != null` rules. Not linked to a real identity. |
| **Reinstall recovery** | Only automatic if passphrase == deviceId (hardware-stable). UUID passphrases (Start Fresh) are lost on reinstall — document the QR/passphrase as the backup. |
| **"Start Fresh" data leakage** | Old Firebase data is abandoned (not deleted). It remains on Firebase servers under the old path but is unreachable without the old passphrase. |
| **No traditional passwords** | There is no server-side user account, email, or password. Loss of passphrase = loss of cloud data access. |

---

*Generated from source analysis of `navbar.kt`, `OnboardingScreen.kt`, `PassphraseManager.kt`, `DeviceIdManager.kt`, `SyncManager.kt`, `DataCleanupManager.kt`, and `NoteRepository.kt`.*

