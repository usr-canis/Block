# Android Auto data feasibility

Research checked against the requested repositories and Android documentation on 2026-11-12. "Defined in a schema" is deliberately separated from "observed in a vehicle": neither protobuf definitions nor another project's test setup proves a production head unit advertises a field.

## What a regular, non-rooted phone app can see during Google Android Auto

It can read its own Android sensors and, after a runtime permission grant, its own phone location. Android exposes no public broadcast, service, or `CarHardwareManager` connection for a normal phone app to inspect Google's active Android Auto session. There is no evidence in the inspected projects that a third-party app can passively intercept that session. Its app sandbox and the session's authenticated TLS transport prevent this app from reading AAP channels, subscriptions, or payloads.

The official `androidx.car.app.hardware.CarHardwareManager` type exists. Its `getCarInfo()`, `getCarSensors()`, and experimental `getCarClimate()` methods are reached through `CarContext` in an Android for Cars app hosted by Android Auto or Android Automotive OS. It is not a general phone-side vehicle API. Android Auto and Android Automotive OS are different hosts; hardware support and returned values are host/vehicle dependent. This project is a normal phone app and does not claim those APIs are active here.

The protocol repositories describe a different arrangement. AAP is a multiplexed phone-to-head-unit session with service discovery, channels, protobuf messages, and TLS 1.2 authentication. A phone-side replacement can negotiate its own session with a compatible head unit. The head unit chooses the services and sensor types it offers. A normal app cannot join Google's already-running session. Replacing the phone side, controlling the head unit, or obtaining an authorized capture is an advanced lab setup; it is not a passive collector and may prevent the official Android Auto app from running.

## Feasibility and implementation matrix

| Data | Source/API | Permission | Root | Needs AA/head-unit control | Expected availability | Evidence | App status |
|---|---|---|---|---|---|---|---|
| Accelerometer axes | Android `SensorManager`, `TYPE_ACCELEROMETER` | None | No | No | Device dependent; phone motion only | [Android sensor overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview) | Implemented; live while app is foreground |
| Gyroscope axes | `SensorManager`, `TYPE_GYROSCOPE` | None | No | No | Device dependent; phone motion only | Android sensor overview above | Implemented when hardware exists |
| Compass/orientation | `TYPE_ROTATION_VECTOR`; gravity + magnetic fallback | None | No | No | Device dependent; calibration/interference apply | Android sensor overview above | Implemented when hardware exists |
| Phone latitude/longitude/altitude | Android `LocationManager` GPS provider | Coarse or fine location at runtime | No | No | Permission, GPS provider, and sky view dependent | [Location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions) | Implemented; explicitly phone GPS |
| Phone GPS speed/bearing/accuracy/fix time | Android `Location` fix | Coarse or fine location at runtime | No | No | Optional fields on actual location fix | [Location](https://developer.android.com/reference/android/location/Location) | Implemented when provided by the phone |
| Vehicle/dashboard speed, RPM, fuel, gear, odometer | AAP sensor channel in a session the collector owns; or car-host hardware APIs | Car-host permissions/policy vary | Not inherently, but privileged head-unit setup may be needed | Yes, unless a supported Car App host grants access | Varies by HU/vehicle; never inferred from protocol definitions | [AAP sensor protocol notes](https://github.com/mretallack/AndroidAuto/blob/master/docs/protocol.md#5-sensors--and-the-correlation-hazard), [open schemas](https://github.com/mrmees/open-android-auto/tree/main/oaa/sensor), [CarHardwareManager](https://developer.android.com/reference/androidx/car/app/hardware/CarHardwareManager) | Not available to this ordinary phone app |
| HVAC, doors, lights, tire pressure, passenger/trailer | Car-host APIs or vehicle/vendor/head-unit extensions | Host/vendor specific | Sometimes | Yes | Highly vehicle-specific; not guaranteed | [Car App hardware APIs](https://developer.android.com/training/cars/apps/library) and open protocol definitions | Not implemented; availability unknown without vehicle access |
| EV level/range/energy and charge status | Car host or AAP sensor/service | Host/vendor specific | Sometimes | Yes | Vehicle and head-unit dependent; schema presence is not observation | [AAP sensor definitions](https://github.com/mrmees/open-android-auto/tree/main/oaa/sensor), AARE README | Not implemented; availability unknown without vehicle access |
| AA connection state / USB vs wireless transport | No public ordinary-app API identified | N/A | N/A | System/host diagnostics | Not observable reliably by this app | [Android for Cars overview](https://developer.android.com/training/cars) | Explicitly shown as unknown/not observable |
| AAP channels, sensor adverts/subscriptions, raw protobuf, unknown fields | Owned protocol implementation or authorized capture | Capture/tool-specific | Often not for a lab HU, but could require modified/privileged setup | Yes | HU only advertises a subset; encrypted session | AARE and [protocol reference](https://github.com/mretallack/AndroidAuto/blob/master/docs/protocol.md) | Not intercepted; raw bytes unavailable |
| OBD-II / manufacturer data | Future separate Bluetooth/USB adapter or OEM interface | Adapter/OEM specific | No in general | No for external adapter | Vehicle/adapter dependent | Android does not define generic AA OBD access | Not implemented; kept separate from native AA |

Permission states shown by the app apply to location. Sensor APIs require no runtime permission. Android Auto protocol fields remain "Requires advanced access" or "Unknown"; the app never substitutes phone GPS for vehicle speed.

## Source verification notes

- [`mrmees/open-android-auto`](https://github.com/mrmees/open-android-auto) exists and describes reverse-engineered protobuf definitions from Android Auto firmware and captured traffic. Its README distinguishes confidence tiers and documents sensor schemas. It is a schema/reference repository, not proof that a given car exposes those sensors.
- [`prototux/AARE`](https://github.com/prototux/AARE) exists and identifies itself as a phone-side Android Auto reimplementation. Its README says its protocol implementation has been tested in a simulated head unit, its own desktop HU, and only a small number of real HUs; some car-hardware sensor work is explicitly described as tested against fake car data. This is not passive access to Google's session.
- [`mretallack/AndroidAuto`](https://github.com/mretallack/AndroidAuto) exists and contains a phone-side WIP implementation and a protocol reference. Its reference describes 4-byte channel framing, TLS 1.2 with the phone as server, service discovery, and sensor start request/response/event messages. It also warns that sensor requests and returned event batches have correlation hazards. This demonstrates protocol research, not a stable third-party hook into Google's app.
- The requested official [`CarHardwareManager` reference](https://developer.android.com/reference/androidx/car/app/hardware/CarHardwareManager) was fetched and confirms `getCarInfo()`, `getCarSensors()`, and experimental `getCarClimate()`. The requested [Android for Cars training page](https://developer.android.com/training/cars) distinguishes Android Auto from Android Automotive OS and directs apps to host-specific Car App Library APIs.

## Compatibility and real-device procedure

No head unit or Android handset was attached to this build environment. Hardware compatibility is therefore unverified; do not interpret the matrix's live values as vehicle values.

1. Install the release APK on a non-rooted Android phone. Open Roadscope with no car attached. Confirm the AA state stays "Session not observable" and vehicle values remain unavailable.
2. Deny location. Confirm motion sensors can still update and location is marked permission denied. Grant approximate location; confirm fields remain phone-sourced and note the reported accuracy. Grant precise location only if appropriate.
3. Connect the phone by USB Android Auto and then, separately, wireless Android Auto. Confirm Roadscope does not claim either transport is detected and does not disrupt the AA session.
4. While parked, record a short session with the phone stationary, then move it carefully by hand. Confirm acceleration/gyroscope values change with the phone and GPS speed is separately labeled. Stop and export JSONL and CSV; inspect event source, timestamps, units, and drop count.
5. Repeat with GPS unavailable, location disabled, unsupported phone sensors, rapid sensor updates, app background/foreground, and screen off. The current build intentionally pauses collection when the app leaves the foreground. Test process termination during recording; Room should retain already-flushed events while queued recent events can be lost.
6. To test actual vehicle telemetry or AAP raw events, use a compatible head-unit emulator or authorized head unit that the tester controls, with a separate phone-side protocol implementation/capture. Record whether the HU advertises each sensor and the subscription response and event. Do not attach this inspector as a passive tap to the Google session.

## Current release boundaries

Implemented: foreground phone sensor and GPS collection, searchable live field list, per-field local history, timestamped bounded live event queue, Room persistence, event filtering/pause, JSONL/CSV file export, and a per-category availability view.

Not implemented: Android Auto protocol session, passive USB/wireless interception, raw protobuf capture/import, unknown-field preservation from AAP, background foreground-service recording, external OBD-II adapters, or official Car App host APIs. The build does not fabricate fields or send telemetry off-device.
