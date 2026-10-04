# Vivvyo Translator

> **100% On-Device • Zero Data Collection • True Privacy**

**Vivvyo Translator** is a lightweight, privacy-hardened Android application that performs text, voice-to-voice, and image OCR translation completely on-device without internet access.

---

## 🔒 Privacy Commitment

*“Vivvyo Translator processes everything on-device. Your voice, text, and images never leave your device.”*

- **Zero Egress**: After the initial optional language pack setup, Vivvyo makes strictly 0 network requests. All network calls are blocked by a runtime `NetworkGate`.
- **Encrypted Local Storage**: Translation history is encrypted using Android Keystore hardware-backed AES-256-GCM.
- **Memory Hygiene**: In-memory speech, OCR, and text buffers are zero-wiped immediately after processing.
- **Cleartext Blocked**: Hardened network security config prevents all cleartext traffic.
- **No Telemetry**: Absolutely no analytics, tracking SDKs, or crash reporting.

---

## 🚀 Key Features

1. **Offline Text Translation**:
   - Primary translation powered by ML Kit On-Device Translation.
   - Intelligent fallback chain to ONNX Runtime Mobile (quantized NLLB-200 and OPUS-MT) for rare and indigenous dialects.
   - On-device language identification across 100+ languages.
   - 10 default core language packs pre-configured.

2. **Offline Voice-to-Voice Translation**:
   - Android 12+ on-device speech recognition via `SpeechRecognizer.createOnDeviceSpeechRecognizer()`.
   - Voice Activity Detection (Silero VAD via ONNX Runtime Mobile) for natural speech segmentation under 2-second boundaries.
   - Push-to-talk and Continuous Hands-Free conversation modes.
   - **Mirror Mode**: Split-screen interface where the top half flips 180° for face-to-face table conversations.
   - Offline TextToSpeech voice output.

3. **Clipboard Image OCR & Google Lens Overlay**:
   - ML Kit Text Recognition v2 supporting Latin, Devanagari, Chinese, Japanese, and Korean offline.
   - Detects images in clipboard with one-tap translation from notification, floating bubble, or image tab.
   - Editable OCR preview to correct any character misreads.
   - Real-time Google Lens style text overlay drawn directly on top of the image.
   - Export and share translated images as PNG.

4. **Floating Bubble & System Integration**:
   - Draggable floating overlay bubble (`SYSTEM_ALERT_WINDOW`) for translation anywhere on screen.
   - Persistent foreground notification with quick actions (*Translate Clip*, *OCR Image*, *Voice*, *Settings*).
   - Quick Settings Tile: **Vivvyo Translate**.
   - Hardware volume-key long-press trigger for instant clipboard translation.

5. **Cross-App Translation via Accessibility Service**:
   - `VivvyoAccessibilityService`: translates selected text on screen in any third-party app.
   - Optional *Replace in place* for messaging input fields.
   - Per-app targeting: auto-translates incoming text in chosen chat/messaging apps.

---

## 📱 Installation Instructions

To install **Vivvyo Translator** on any Android phone, tablet, or foldable:

1. **Download APK**:
   Get `vivvyo-translator.apk` from the project root.
2. **Allow Unknown Sources**:
   On your Android device, go to **Settings → Security & Privacy → Install unknown apps** (or tap the downloaded file and enable permission for your browser/file manager).
3. **Install**:
   Tap `vivvyo-translator.apk` and select **Install**.
4. **Permissions**:
   Grant Microphone (for Voice translation), Notification (for persistent service), Overlay (for floating bubble), and Accessibility (for cross-app translation) when prompted.

---

## 🛠️ Build & Development Commands

Build with standard Gradle:

- **Build Debug APK (Installs on any device)**:
  ```bash
  gradle assembleDebug
  ```
- **Build Release APK**:
  ```bash
  gradle assembleRelease
  ```
- **Build Android App Bundle (AAB for Google Play Store)**:
  ```bash
  gradle bundleRelease
  ```
- **Run Unit Tests (Robolectric JVM)**:
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## ⚙️ Compatibility Specifications

- **Application ID**: `com.vivvyo.translator`
- **Version**: `1.0.0` (Version Code `1`)
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Architectures (ABIs)**: `arm64-v8a`, `armeabi-v7a`, `x86_64`
