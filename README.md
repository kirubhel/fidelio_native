# kokeb fidel Native (Android)

This is a **Mobile Native (Android)** implementation of the kokeb fidel educational application, migrated from Flutter to comply with the **RESPECT Launcher Ecosystem** guidelines.

## Features
- **Kotlin & Jetpack Compose**: Built with modern Android development standards.
- **RESPECT Integrated**: Ready for single sign-on (SSO) and deep-linking from the RESPECT Launcher app.
- **Offline-First**: Designed to work without an internet connection using the RESPECT local proxy.

## Integration
- **Deep Linking**: The app responds to `kokebfidel://topic` and `https://learningcloud.et/kokebfidel` URLs to launch specific learning units.
- **Progress Tracking**: Uses **xAPI** to report learner performance back to the launcher.

## Getting Started
1. Open this project folder in **Android Studio**.
2. Sync with Gradle.
3. Deploy to an Android device or emulator that has the **RESPECT Launcher** installed.
