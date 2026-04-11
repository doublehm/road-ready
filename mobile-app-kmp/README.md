# Road Ready — Kotlin Multiplatform

This is the Kotlin Multiplatform rewrite of the Road Ready mobile app, targeting both **Android** and **iOS** with shared business logic and UI via **Compose Multiplatform**.

## Tech Stack

| Layer | Library |
|-------|---------|
| UI | Compose Multiplatform |
| Networking | Ktor Client |
| DI | Koin |
| Navigation | Decompose |
| Serialization | kotlinx.serialization |
| Image Loading | Coil 3 |
| Local Storage | multiplatform-settings |
| Secure Storage | Platform-specific (Keychain / EncryptedSharedPreferences) |

## Project Structure

```
mobile-app-kmp/
├── shared/                    # Shared KMP module (commonMain, androidMain, iosMain)
│   └── src/commonMain/kotlin/com/roadready/
│       ├── data/              # Models, API client, repositories
│       ├── di/                # Koin dependency injection modules
│       ├── navigation/        # Decompose navigation components
│       └── ui/                # Compose Multiplatform screens & theme
│           ├── theme/         # Colors, typography, design system
│           ├── components/    # Reusable UI components
│           └── screens/       # Feature screens (auth, student, instructor)
├── androidApp/                # Android application entry point
└── iosApp/                    # iOS application entry point (SwiftUI wrapper)
```

## Building

### Prerequisites
- JDK 17+
- Android SDK (API 35)
- Xcode 15+ (for iOS)

### Android
```bash
./gradlew :androidApp:assembleDebug
```

### iOS
Open `iosApp/` in Xcode or build via:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## Backend API

The app connects to the Road Ready FastAPI backend at `http://{host}:8000/api/v1`. Set the base URL in `shared/.../di/AppModule.kt`.

## Migration Status

This is an incremental rewrite from React Native. Screens implemented:
- [x] Login
- [x] Register
- [x] Student Home Dashboard
- [ ] Instructor Home Dashboard (placeholder)
- [ ] Setup screens
- [ ] Diagnostic Ride flow
- [ ] Booking flow
- [ ] Messaging
- [ ] Education / Modules
- [ ] Grading screens
- [ ] Profile editing
- [ ] Notifications
