# iYosi Android pilot

This is the Android visitor and contributor client for the separate iYosi `/v1` API. It is an informational guide to designated and community-reported smoking areas in Metro Manila, supporting smoke-free rules and shared air. It uses only Android platform APIs and Java 17 source, with no third-party map or account SDK. The operator moderation UI remains a separate project.

## Build and run

Install JDK 17, Android SDK platform 35, and an Android emulator or device. Open this repository in Android Studio or run:

```sh
./gradlew assembleDebug
```

The first wrapper invocation downloads Gradle 8.9 and checks the official wrapper JAR checksum. By default, debug builds call `http://10.0.2.2:8000`, the host machine as seen by the Android emulator. Start the separate iYosi API using its README. For a physical device or deployed HTTPS API, pass a URL accessible to that device:

```sh
./gradlew assembleDebug -PiyosiApiUrl=https://api.example.com
```

Release builds block cleartext HTTP. The URL must not end in `/`. Do not put API secrets in the app: the client only uses public endpoints and per-account bearer tokens.

The map defaults to OpenStreetMap standard raster tiles. Set `-PiyosiTileUrl=https://your-provider.example/{z}/{x}/{y}.png` and `-PiyosiTileAttribution='Your provider attribution'` to use a pilot-approved tile provider. The tile client identifies itself, shows attribution, requests only visible tiles, and keeps tiles in its cache for seven days. OpenStreetMap's [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) applies to the default. Choose a provider and publish operator contact details before a larger rollout.

Address search needs the API's `GEOCODER_URL`. Without it, the server returns `503`; the map and place list continue working. The app does not need location permission for browsing or search. A user grants foreground location only by tapping **Near me**.

## Pilot behavior

- Adult prompt and pilot privacy notice appear before browsing. Underage selection exits.
- Map requests bounded viewport pages and uses independent kind and advisory status filters. Prohibited warnings are off by default. Shapes and text accompany pin color; the list below the map is available to screen readers.
- Place detail distinguishes verification review from community confirmation and ratings. Directions use the public access point when supplied and are disabled for prohibited, closed, or merged records.
- Sign-in or registration is requested only for contributions. The bearer token is encrypted with Android Keystore at rest. Proposals remain pending, and the app never sends a verification or advisory status in contribution requests.
- Long-press the map to choose an area pin, then tap **Add place**. Nearby published records are shown before submission. Public entrance coordinates may be entered separately.
- Confirmations, reviews, reports, account summary, proposal history, and deletion requests use the API resources defined in the API repository's `docs/openapi.json`.
- `iyosi://places/PLACE_ID` opens a place directly, including the API's closed and merged states.

## Validation

The Android build needs JDK 17, Android SDK 35, and network access for Gradle artifacts. The API repository's Python contract tests validate the API independently. Before a pilot, exercise the Android journeys on an emulator against a seeded Compose API and configure a geocoder. Public release still needs the age-assurance, privacy, legal, and provider decisions in the API repository's `docs/LAUNCH_NOTES.md`.

## License

Copyright © 2026 Orca Solutions. All rights reserved. See [LICENSE.md](LICENSE.md). Public visibility does not make this app open source.
