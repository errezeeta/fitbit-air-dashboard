# Fitbit Air health dashboard (working title)

Private Android app for a Pixel 9. The UI is finished on demo data; real Google Health data is served by the private Cloud Run backend in `backend/`.

## Current scope

- Kotlin + Jetpack Compose + Material 3 dynamic colors.
- Home, Sleep, Activity, and AI tabs; 7/30-day trend selector.
- Sleep duration/stages and app-calculated sleep efficiency, steps with a configurable local goal, resting heart rate trend.
- Adaptive launcher icon: sleep crescent + pulse waveform in a Material You-inspired palette.
- No persistent health-metric history on the device. Locally stored: the manual step-goal fallback and an encrypted app session token.

## How the real-data path works

1. The app starts Google OAuth with PKCE (`/auth/google/start`).
2. Google redirects to Cloud Run, which exchanges the code **server-side** and stores the refresh token in Secret Manager.
3. Cloud Run hands the app a short-lived ticket; the app exchanges it for an app session token. The Google refresh token, the Google client secret, and the Hermes key never reach the device.
4. The app calls `/api/dashboard` and `/api/assistant`; Cloud Run reads Google Health on demand and returns only what each screen needs.

Local debug builds stay on demo data unless a backend URL is supplied:

```sh
./gradlew :app:assembleDebug -PbackendBaseUrl=https://YOUR-SERVICE.run.app
```

## Build

Requires JDK 17 and Android SDK Platform 37 / Build Tools 36.0.0. `local.properties` is machine-specific and is intentionally ignored by Git.

```sh
./gradlew :core:test
./gradlew :app:assembleDebug
```

On an ARM64 host whose SDK build-tools are x86_64, run the Android build inside an x86_64 container (see `backend/README.md` for the pattern used here).

## Backend and deployment

See `backend/README.md` for the Cloud Run setup, the Workload Identity Federation script, and the manual deployment workflow. Never place the OAuth client secret, the app signing secret, or the Hermes `API_SERVER_KEY` in this Android project, in `config.yaml`, or in the repository.
