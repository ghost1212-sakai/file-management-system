# 📁 File Management System

An Android file manager app built with **Jetpack Compose** and **MVVM architecture**, developed as a project at **Jaypee University of Information Technology (JUIT)**.

> Prepared by: Omansh (241033008), Nilesh (241033045), Nitin (241033057)  
> Date: 09-04-2026

---

## 📱 Features

- 📂 **Browse** files and folders on device storage
- ➕ **Create** files and folders
- ✂️ **Cut**, **Copy**, and **Paste** files
- 🗑️ **Delete** files and folders
- ✏️ **Rename** files and folders
- 🗜️ **Compress** files into `.zip` archives
- 📦 **Decompress** / Extract `.zip` files
- 🔗 **Share** files with other apps
- 🔍 **Search** files by name
- 📊 **Sort** by name, date modified, or size
- ℹ️ **File information** — size, permissions, last modified, path

---

## 🏗️ Architecture

This project follows the **MVVM (Model-View-ViewModel)** pattern, separating the app into three distinct layers:

```
┌─────────────────────────────┐
│        MainActivity.kt       │  ← View layer (UI / Jetpack Compose)
├─────────────────────────────┤
│   FileManagerViewModel.kt   │  ← ViewModel layer (business logic)
├─────────────────────────────┤
│   FileManagerRepository.kt  │  ← Model layer (filesystem I/O)
└─────────────────────────────┘
```

| File | Layer | Responsibility |
|---|---|---|
| `MainActivity.kt` | View | All UI — screen, dialogs, bottom sheet, FAB |
| `FileManagerViewModel.kt` | ViewModel | State management, operation orchestration |
| `FileManagerRepository.kt` | Model | Raw file I/O, zip/unzip, directory listing |

---

## 🧩 UML Diagrams

The project includes a full set of UML diagrams documented in the project report:

| Diagram | Purpose |
|---|---|
| **Use Case Diagram** | Shows all 11 operations a user can perform |
| **Activity Diagram** | Step-by-step flow for each operation |
| **Class Diagram** | Structure of `User`, `FileManager`, `Folder`, `File` classes |
| **Sequence Diagram** | Message flow: User → FileManager → FileSystem |
| **Collaboration Diagram** | Numbered message passing between all system objects |
| **State Diagram** | System states: Idle → Selecting → Processing → Compressing / Sharing / Decompressing |
| **Component Diagram** | 6 components: UI, File Manager, File Operations, Compression Module, Sharing Module, Storage System |
| **ER Diagram** | Entities: User, File, Folder with relationships Performs, Creates, Contains, Shares |

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Async:** Kotlin Coroutines + StateFlow
- **File I/O:** Java `File` API + `ZipOutputStream` / `ZipInputStream`
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)

---

## 🚀 Getting Started

### Installation

1. Clone the repository:
```bash
git clone https://github.com/Nitinyadav2305/File_Manager_System.git
```

2. Open the project in **Android Studio**.

3. Build and run on a device or emulator.

4. On first launch, grant **All Files Access** permission when prompted (required for Android 11+).

---

## 🔐 Permissions

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
```

On Android 11 and above, the app requests `MANAGE_EXTERNAL_STORAGE` at runtime via the system settings screen.

---

## 📂 Project Structure

```
app/src/main/java/com/example/file_manager/
│
├── MainActivity.kt             # UI layer — all Composables and screen logic
├── FileManagerViewModel.kt     # ViewModel — state, clipboard, sort, search
└── FileManagerRepository.kt    # Repository — file I/O, zip/unzip, FileItem data class
```

---

## 📸 Screenshots

![WhatsApp Image 2026-04-07 at 01 14 46](https://github.com/user-attachments/assets/ffa46a5b-3b0c-410f-8023-31717a0cc10a)


![WhatsApp Image 2026-04-07 at 01 14 46](https://github.com/user-attachments/assets/576bb793-fe7e-4b86-ba14-759310c44666)



---

## 🎓 Academic Context

This project was developed as part of a software engineering course at **JUIT (Jaypee University of Information Technology)**. The full UML documentation — including all 8 diagrams — is included in the project report (`FILE_MANAGEMENT_SYSTEM.pdf`).

---

## 📄 License

This project is for academic purposes at JUIT.
