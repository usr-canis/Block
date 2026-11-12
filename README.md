# Roadscope

**Android Auto Ultimate Data Inspector** — a small, local-first Android dashboard for real phone sensors and a clear report of what the phone cannot observe about Android Auto.

> Roadscope does not intercept or join Google's Android Auto session. It reads the phone's own motion sensors and, with permission, its GPS. Phone GPS speed is labelled as phone GPS and is never presented as vehicle/dashboard speed.

## What works in this first release

- Live accelerometer, gyroscope, rotation-vector compass, and GPS location fields when the device provides them.
- Optional phone barometer, light, ambient temperature/humidity, proximity, gravity, magnetic field, and GNSS satellite counts when Android and the device provide them.
- Dark phone dashboard, searchable live field explorer, event pause/filter/JSON inspection, field history and update-rate summary.
- Local Room event recording with timestamps, source, unit, category, and a bounded write queue/drop counter.
- JSON, JSONL, and CSV export through Android's document picker. No telemetry is sent to a server.
- Availability matrix that separates live phone readings from Android Auto data that is unknown or needs a head-unit/protocol lab setup.
- Collection pauses when the app leaves the foreground; already flushed Room events remain on the device.

See [`docs/FEASIBILITY.md`](docs/FEASIBILITY.md) for source-code research, the field-by-field feasibility matrix, current limitations, and the real-head-unit test procedure.

## Build

Open this directory in Android Studio (JDK 17 or newer; Android SDK platform 35), or run:

```sh
./gradlew :app:assembleDebug
```

Install the debug APK at `app/build/outputs/apk/debug/app-debug.apk`. The release variant uses a dedicated signing key provided through environment variables or the ignored local `.signing/` directory; never commit a signing key or password.

## Install and test on a phone

Download the [Roadscope 1.0.0 APK](release/v1.0.0/Roadscope-1.0.0.apk) and verify it with [`SHA256SUMS.txt`](release/v1.0.0/SHA256SUMS.txt). Grant location only if you want phone GPS fields. Denying location does not prevent motion sensors from working. Leave the app open during collection; the current build intentionally stops sampling when it is backgrounded. Use the matrix to see what was actually observed on the phone.

For Android Auto checks, first test with no head unit, then USB and wireless while parked. Roadscope should continue to report the Android Auto session as not observable and should not interfere with it. A protocol experiment requires control of a compatible head unit or an authorized capture and is outside this phone-only release.

## Release signing

Release builds use these environment variables: `ROADSCOPE_KEYSTORE`, `ROADSCOPE_KEY_ALIAS`, `ROADSCOPE_KEY_PASSWORD`, and `ROADSCOPE_STORE_PASSWORD`. The GitHub Actions release workflow publishes a signed APK for a `v*` tag when its repository signing secrets are configured; without them it uploads a debug build artifact instead. Keep the keystore and passwords private; losing the keystore prevents signing compatible future updates. See [`docs/RELEASE.md`](docs/RELEASE.md).
