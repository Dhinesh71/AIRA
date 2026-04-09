# Aira

Aira is an Android-native Kotlin prototype for an AI-powered mobile control assistant. The app captures voice commands, sends them to a Node.js intent backend, turns them into structured actions, and executes supported actions through an Android `AccessibilityService`.

This workspace includes:

- A Kotlin Android app using MVVM, Retrofit, Room, and a native `AccessibilityService`
- A Node.js backend with a `/interpret` endpoint
- A strict OpenAI Structured Outputs contract for natural language -> JSON intent parsing
- MVP execution paths for app launch, navigation, click, scroll, type, and basic messaging automation

## MVP scope

Phase 1 in this repo is focused on:

- Voice -> text with Android `SpeechRecognizer`
- Optional continuous listening with a basic `"Hey Aira"` wake phrase flow
- Text -> intent through `POST /interpret`
- Intent -> execution for:
  - open app
  - back/home/recents
  - click visible text
  - scroll containers
  - type into editable fields

Included but intentionally best-effort:

- Messaging automation through accessibility heuristics
- Tasker-style integration via a broadcast bridge that Tasker can listen to

Not fully implemented yet:

- True background always-on voice capture as a foreground service
- Predictive automation
- Rich context awareness across apps
- App-specific reliability tuning for complex multi-step flows

## Project structure

```text
AIRA/
├─ app/
│  ├─ build.gradle.kts
│  ├─ proguard-rules.pro
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/aira/app/
│     │  ├─ AiraApplication.kt
│     │  ├─ AppContainer.kt
│     │  ├─ MainActivity.kt
│     │  ├─ automation/
│     │  │  ├─ AiraAccessibilityService.kt
│     │  │  ├─ AiraActionExecutor.kt
│     │  │  ├─ AutomationValidator.kt
│     │  │  └─ TaskerBridge.kt
│     │  ├─ data/
│     │  │  ├─ local/
│     │  │  ├─ model/
│     │  │  ├─ remote/
│     │  │  └─ repository/
│     │  ├─ ui/
│     │  │  ├─ CommandHistoryAdapter.kt
│     │  │  ├─ MainUiState.kt
│     │  │  └─ MainViewModel.kt
│     │  └─ voice/
│     │     ├─ VoiceCommandManager.kt
│     │     └─ WakeWordDetector.kt
│     └─ res/
│        ├─ drawable/
│        ├─ layout/
│        ├─ values/
│        ├─ values-night/
│        └─ xml/
├─ backend/
│  ├─ .env.example
│  ├─ package.json
│  ├─ package-lock.json
│  └─ src/
│     ├─ intentSchema.js
│     └─ server.js
├─ gradle/
│  ├─ libs.versions.toml
│  └─ wrapper/gradle-wrapper.properties
├─ build.gradle.kts
├─ gradle.properties
└─ settings.gradle.kts
```

## Android architecture

- `MainActivity`
  - Hosts the dashboard UI
  - Requests microphone permission
  - Starts/stops `VoiceCommandManager`
  - Opens Accessibility / Overlay / Battery settings

- `MainViewModel`
  - Coordinates interpretation and execution
  - Tracks status, history, confirmation state, and shortcut suggestions
  - Stores command history through Room

- `VoiceCommandManager`
  - Uses `SpeechRecognizer`
  - Supports one-shot capture and continuous listening
  - Detects `"Hey Aira"` with a simple wake-word arming flow

- `AiraAccessibilityService`
  - Searches the active node tree
  - Performs clicks, scrolling, text entry, and global navigation

- `AiraActionExecutor`
  - Maps structured intents to device actions
  - Opens apps through `PackageManager`
  - Uses the accessibility service when screen interaction is required

- `AiraRepository`
  - Calls the backend with Retrofit
  - Persists recent commands with Room
  - Derives shortcut suggestions from frequent actions

## Backend architecture

- `POST /interpret`
  - Input: voice text + locale + recent commands
  - Output: strict JSON intent

- Uses OpenAI Responses API with Structured Outputs
  - `text.format.type = "json_schema"`
  - strict schema for intent extraction
  - local safety pass adds confirmation for risky commands

- Sensitive actions
  - `send_message`
  - `type_text`
  - `click_text`
  - low-confidence actions

These return or become `requiresConfirmation = true` before the app executes them.

## Sample intent mapping

Examples handled by the current contract:

```json
{
  "action": "open_app",
  "summary": "Open WhatsApp",
  "appName": "WhatsApp",
  "packageName": "com.whatsapp",
  "targetText": "",
  "inputText": "",
  "contact": "",
  "message": "",
  "direction": "",
  "navigationTarget": "",
  "taskName": "",
  "requiresConfirmation": false,
  "sensitive": false,
  "confidence": 0.94,
  "reason": "User explicitly requested app launch."
}
```

```json
{
  "action": "navigate",
  "summary": "Go back",
  "appName": "",
  "packageName": "",
  "targetText": "",
  "inputText": "",
  "contact": "",
  "message": "",
  "direction": "",
  "navigationTarget": "back",
  "taskName": "",
  "requiresConfirmation": false,
  "sensitive": false,
  "confidence": 0.98,
  "reason": "Simple global navigation command."
}
```

```json
{
  "action": "send_message",
  "summary": "Send a WhatsApp message to Arun",
  "appName": "WhatsApp",
  "packageName": "com.whatsapp",
  "targetText": "",
  "inputText": "",
  "contact": "Arun",
  "message": "",
  "direction": "",
  "navigationTarget": "",
  "taskName": "",
  "requiresConfirmation": true,
  "sensitive": true,
  "confidence": 0.82,
  "reason": "Contact is clear, but message content is missing."
}
```

## Setup

### 1. Start the backend

```bash
cd backend
Copy-Item .env.example .env
```

Fill in:

```env
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-4o-mini
PORT=3000
```

Then run:

```bash
npm install
npm run dev
```

Health check:

```bash
curl http://localhost:3000/health
```

### 2. Open the Android project

Open the repository root in Android Studio.

Important default:

- `BuildConfig.AIRA_API_BASE_URL` is set to `http://10.0.2.2:3000/`
- That works for the Android emulator only
- For a physical device, update `AIRA_API_BASE_URL` in `app/build.gradle.kts` to your machine’s LAN IP

### 3. Enable required permissions and services

After installing the APK:

1. Grant microphone permission.
2. Open Accessibility settings and enable `Aira Device Control`.
3. Optionally enable overlay permission.
4. Optionally ignore battery optimization so the app is less likely to be suspended during testing.

## Tasker integration

This repo uses a practical Tasker bridge without depending on Tasker SDK internals:

- Aira sends a broadcast action: `com.aira.app.TASKER_TRIGGER`
- Extras:
  - `task_name`
  - `summary`
  - `triggered_at`

In Tasker, create a profile that listens for this intent and route it to any task you want. That gives you a clean path for device- or context-specific automations without hardcoding them into the app.

## Notes on verification

Verified in this workspace:

- Backend dependencies install successfully
- Backend starts successfully with `node src/server.js`
- Backend source passes `node --check`

Not verified in this workspace:

- Android build, because Java/Gradle are not installed here
- On-device accessibility behavior, which still needs real-device testing and per-app tuning

## OpenAI API references

The backend design follows the current OpenAI docs for:

- Responses API: https://platform.openai.com/docs/api-reference/responses
- Structured Outputs: https://developers.openai.com/api/docs/guides/structured-outputs

Specifically, the backend uses the `Responses` API plus a strict `text.format` JSON schema so Android receives machine-safe intents instead of free-form text.
