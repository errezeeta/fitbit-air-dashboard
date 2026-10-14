# Fitbit Air health dashboard (working title)

Private Android app for a Pixel 9. The initial build uses demo data only; it does not call Google Health or Hermes yet.

## Current scope

- Kotlin + Jetpack Compose + Material 3 dynamic colors.
- Home, Sleep, Activity, and AI tabs; 7/30-day trend selector.
- Sleep duration/stages and app-calculated sleep efficiency, steps with a configurable local goal, resting heart rate trend.
- AI screen is a non-networked UI stub until the secure Hermes connection is implemented.
- No persistent health-metric history in the app. The only local preference is the manual step-goal fallback.

## Build

Requires JDK 17 and Android SDK Platform 37 / Build Tools 36.0.0. `local.properties` is machine-specific and is intentionally ignored by Git.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The actual Google Health API OAuth and Hermes API connection are deferred to the integration phase. Never place OAuth client secrets or `API_SERVER_KEY` in this Android project.
