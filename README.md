# SwiftNote - Smart Note Taking App

**SwiftNote** is a modern, intelligent, and offline-first note-taking Android application built with **Jetpack Compose**. It combines clean UI/UX with powerful AI-driven reminders, making note-taking not just easier but smarter. Whether you’re writing down quick thoughts, managing tasks, or setting time-sensitive reminders, SwiftNote ensures everything stays organized and accessible anytime, even without an internet connection.

---

## ✨ Features

### 📱 Modern UI/UX

* Built **entirely with Jetpack Compose**, offering declarative and reactive UI.
* Designed using **Material Design 3 principles**, ensuring a consistent, beautiful user experience.
* Smooth animations and intuitive transitions make the app feel lively and responsive.
* Optimized **responsive layouts** for mobile devices of different screen sizes.
* **Light & Dark theme support**, adapting seamlessly to user preferences.

### 📝 Powerful Note Management

* Create, edit, and delete notes with ease.
* **Rich text formatting support** for bold, italic, and underline styles.
* **Character counter with progress indicators** for focused writing.
* Built-in validation for title and content ensures structured note-taking.
* **Offline-first data persistence** ensures no data loss when connectivity drops.

### 🤖 AI-Powered Smart Reminders

* Integrated with **Google ML Kit** for on-device NLP (Natural Language Processing).
* Automatic **reminder detection** from text content — no need to manually add reminders.
* Supports both **English and Hinglish**, recognizing dates and times in natural language.
* Can detect **multiple reminders** within a single note.
* Suggests reminders contextually while writing notes.

### ⏰ Advanced Reminder Features

* Flexible scheduling options for reminders.
* Quick preset shortcuts (10min, 30min, 1hour, 1day) for convenience.
* **Custom date and time picker** for precise scheduling.
* Persistent **system notifications** for important tasks.
* **Snooze functionality** for delaying tasks when needed.
* Reminders work fully **offline**, syncing when internet returns.

### 🔄 Offline-First Architecture

* Notes are available offline at all times.
* **Background sync** keeps data consistent across devices.
* Smart **conflict resolution** ensures no overwriting when multiple updates happen.

### 📲 Device Sync via Device ID & QR Code

* Seamlessly **sync notes across multiple devices** without requiring an account.
* Each installation generates a unique **Device ID** that can be securely shared.
* Quickly link devices by **scanning a QR code**, enabling instant pairing.
* Works fully offline-first, with background sync resuming when internet connectivity is available.
* Ensures secure, private, and anonymous syncing between your personal devices.

---

## 🛠️ Technical Highlights

### 🏗️ Architecture

* **MVVM (Model-View-ViewModel)** for clear separation of concerns.
* **Repository pattern** for data handling and abstraction.
* **Coroutines** for asynchronous tasks with structured concurrency.
* **StateFlow** for real-time, reactive UI updates.
* Dependency Injection with **Hilt** (expandable for future scaling).
* **Device Sync Module** for managing unique Device IDs, QR code generation, and secure device-to-device linking.

### ⚙️ Technologies Used

* **Kotlin** — primary development language.
* **Jetpack Compose** — modern UI toolkit.
* **Room Database** — local persistence layer.
* **ML Kit** — natural language entity extraction for reminders.
* **WorkManager** — background processing and sync tasks.
* **AndroidX Libraries** — lifecycle-aware, robust components.
* **ZXing / ML Kit Barcode** — QR code generation and scanning for device linking.

### 💡 Smart AI Features

* On-device processing ensures **privacy and security** of user data.
* Recognizes natural date/time expressions like *“tomorrow at 5pm”* or *“next Monday”*.
* Handles **context-aware scenarios**, like differentiating *“Meeting at 3”* vs *“Finish report by 3”*.
* Intelligent formatting detection makes notes consistent without manual effort.

---

## 📂 Project Structure

```
SwiftNote/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/amvarpvtltd/selfnote/
│   │   │   │   ├── ai/
│   │   │   │   │   └── SmartReminderAI.kt        # AI-powered reminder detection
│   │   │   │   ├── components/
│   │   │   │   │   └── ReminderComponents.kt     # Reusable UI elements
│   │   │   │   ├── design/
│   │   │   │   │   └── add_Note.kt              # Note creation & editing screen
│   │   │   │   ├── notifications/
│   │   │   │   │   └── NotificationHelper.kt     # Notification manager
│   │   │   │   └── reminders/
│   │   │   │       └── ReminderManager.kt        # Reminder scheduling system
│   │   │   ├── res/                              # Resources (icons, fonts, values)
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/                          # Instrumentation tests
│   │   └── test/                                 # Unit tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 📱 Screenshots  

<table>
  <tr>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/f9b4800a-81d3-46fa-bc6d-2a4a97f2ec13" /></td>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/449e5270-8e63-4933-a4d5-dcdac85aeaf9" /></td>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/a773dedb-8159-4731-a1c4-973fa0e3ee82" /></td>
  </tr>
  <tr>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/9027efac-7541-4fec-bbc1-8bf634122703" /></td>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/e9db5c02-b1ea-4bea-a69a-d523cc1bdfc5" /></td>
    <td><img width="230" height="510" alt="5 1" src="https://github.com/user-attachments/assets/bb4ebcd7-a476-4d15-889e-8637331864b6" /></td>
  </tr>
  <tr>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/7cbf1a6a-8f77-414b-a8a4-0933e4fb93e7" /></td>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/41f2e952-3ba1-4f6d-9c99-125ad13a2472" /></td>
    <td><img width="230" height="510" src="https://github.com/user-attachments/assets/9e940054-0089-4ba7-9fbb-4970b150585f" /></td>
  </tr>
</table>

---

## 🚀 Getting Started

### Prerequisites

* Android Studio **Arctic Fox (or newer)**.
* **Minimum SDK:** 21 (Android 5.0 Lollipop).
* **Target SDK:** 34.
* Kotlin version: **1.9.x or newer**.

### Installation

1. Clone the repository:

   ```bash
    git clone https://github.com/ayushsingh-22/SwiftNote.git
   ```

   👉 [Click here to view the repository](https://github.com/ayushsingh-22/SwiftNote.git)
   
3. Open the project in **Android Studio**.
4. Sync Gradle dependencies.
5. Run the app on a physical device or emulator.

### Building from Source

* **From Android Studio:**

  * Select *Build > Make Project*.
  * Click ▶️ (Run) to launch.
* **From CLI:**

  ```bash
  ./gradlew assembleDebug    # Debug build
  ./gradlew assembleRelease  # Release build
  ```

### Testing

```bash
./gradlew test            # Unit tests
./gradlew connectedCheck  # Instrumented tests
```

---

## 📄 License

This project is licensed under the **\[MIT License]** — see the LICENSE file for details.

---

## 🤝 Contributing

Contributions are welcome! Here’s how you can help:

1. Fork the repository.
2. Create a new feature branch:

   ```bash
   git checkout -b feature/AmazingFeature
   ```
3. Commit your changes:

   ```bash
   git commit -m "Add AmazingFeature"
   ```
4. Push the branch:

   ```bash
   git push origin feature/AmazingFeature
   ```
5. Open a Pull Request.

**Contribution Guidelines:**

* Follow official **Kotlin coding conventions**.
* Use clear, meaningful names for variables and functions.
* Add comments for non-trivial logic.
* Ensure new features are covered with **unit tests**.

---

## 🙏 Acknowledgments

* **Google’s ML Kit** — natural language AI.
* **Jetpack Compose** — modern UI toolkit.
* **Material Design 3** — for visual consistency.
* **Android Architecture Components** — lifecycle-aware building blocks.

---

## 📞 Contact

* Author: [**Ayush Kumar**](https://www.linkedin.com/in/ayush-kumar-a2880a258/)
* Project Repository: [**Repository**](https://github.com/ayushsingh-22/Swift-Note)
* LinkedIn: [**Ayush Kumar**](https://www.linkedin.com/in/ayush-kumar-a2880a258/)

---

💡 *SwiftNote is built with love and care to make note-taking simpler, faster, and smarter.*

Made with ❤️ using **Jetpack Compose**.
