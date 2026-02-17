# SideShelf

SideShelf is a system-wide Android side tray overlay that provides fast clipboard access for text and images — accessible from anywhere on the device.

It is designed as a lightweight productivity layer that sits above apps and improves copy–paste workflows.

---

## Features (Mobile v1)

- Clipboard history for copied text
- Image clipboard support
- Delete individual entries
- Clear all clipboard items
- System-wide side tray overlay (draws over other apps)
- Instant access from any screen

---

## Architecture Overview

SideShelf runs as a background service using:

- `SYSTEM_ALERT_WINDOW` permission for overlay
- `ClipboardManager` for monitoring clipboard changes
- Local storage for persistent clipboard history
- Custom View-based UI (XML + programmatic logic)

The app listens for clipboard updates, stores entries, and renders them inside a right-edge sliding tray interface.

---

## Tech Stack

- Kotlin
- Android SDK
- WindowManager API
- ClipboardManager
- XML-based layouts
- Background Service architecture

---

## Design Goals

- System-level utility, not just an in-app feature
- Fast and minimal UI
- Lightweight and low overhead
- Works without requiring internet access

---

## Planned Features

- 🔗 Cross-device clipboard sync (Linux companion)
- 🔎 Search inside clipboard history
- 🧠 Smart categorization (URLs, OTPs, code snippets)
- 📌 Pin frequently used items

---

## Why This Project?

SideShelf explores:

- OS-level Android capabilities
- Overlay permissions and system UI layering
- Background services and lifecycle handling
- Practical productivity tooling beyond typical app patterns

---

## Status

Mobile version complete for personal use.  
Desktop companion (Bluetooth-based sync) planned.
