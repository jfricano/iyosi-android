# iYosi Android pilot

This is the Android visitor and contributor client for the separate iYosi `/v1` API. It is an informational guide to designated and community-reported smoking areas in Metro Manila, supporting smoke-free rules and shared air. It is written in Kotlin with Jetpack Compose and follows the iOS app's screens and the landing page's city-guide design. It has no third-party map or account SDK; the map is a small built-in OpenStreetMap raster renderer. The operator moderation UI remains a separate project.

## Build and run

Install JDK 17, Android SDK platform 35, and an Android emulator or device. Open this repository in Android Studio or run:

```sh
./gradlew assembleDebug
```

The wrapper downloads Gradle 8.9 on first use. By default, debug builds call `http://10.0.2.2:8000`, the host machine as seen by the Android emulator. Start the separate iYosi API using its README. For a physical device or deployed HTTPS API, pass a URL accessible to that device:

```sh
./gradlew assembleDebug -PiyosiApiUrl=https://api.example.com
```

Release builds block cleartext HTTP. The base URL can also be changed in the app under **Account → API server settings**; it must be HTTPS, or HTTP for `localhost`, `10.0.2.2`, or a `.local` host. Changing servers signs the account out. Do not put API secrets in the app: the client only uses public endpoints and per-account bearer tokens.

The map defaults to OpenStreetMap standard raster tiles. Set `-PiyosiTileUrl=https://your-provider.example/{z}/{x}/{y}.png` and `-PiyosiTileAttribution='Your provider attribution'` to use a pilot-approved tile provider. The tile client identifies itself, shows attribution, requests only visible tiles, and keeps tiles in its cache for seven days. OpenStreetMap's [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) applies to the default. Choose a provider and publish operator contact details before a larger rollout.

Address search needs the API's `GEOCODER_URL`. Without it, the server returns `503`; the map and place list continue working. The app does not need location permission for browsing or search. A user grants foreground location only by tapping **Near me**.

## Pilot behavior

- Adult prompt and pilot privacy notice appear before browsing. Underage selection exits.
- Search an area or landmark, then browse a map or list. The map requests bounded viewport pages (up to five pages of 100), clusters pins when zoomed out, and uses independent type and status filters. Prohibited warnings are off by default. Status always pairs text, symbol, and color; the List view is the screen-reader-friendly alternative to the map.
- Tapping a pin opens a compact place card that fetches current details (status, last confirmation, access note) before offering full details.
- Place detail distinguishes verification review from community confirmation and ratings. Directions use the public access point when supplied and are disabled for prohibited, closed, or merged records.
- Sign-in or registration is requested only for contributions. The bearer token is encrypted with Android Keystore at rest. Proposals remain pending, and the app never sends a verification or advisory status in contribution requests.
- **Add place** opens a proposal form with its own map: tap to set the reported area pin, or switch to set a public entrance pin. Nearby published records within 200 m are listed, and a record within 50 m blocks a new proposal in favor of a correction.
- Confirmations, reviews, reports, account summary, proposal history, and deletion requests use the API resources defined in the API repository's `docs/openapi.json`.
- `iyosi://places/PLACE_ID` opens a place directly, including the API's closed and merged states.

## Validation

`./gradlew testDebugUnitTest` runs the JVM unit tests (API client encoding and errors, viewport and clustering logic, idempotency keys, session state). GitHub Actions runs those tests plus debug and release builds and lint on every pull request. The Android build needs JDK 17, Android SDK 35, and network access for Gradle artifacts. The API repository's Python contract tests validate the API independently. Before a pilot, exercise the Android journeys on an emulator against a seeded Compose API and configure a geocoder. Public release still needs the age-assurance, privacy, legal, and provider decisions in the API repository's `docs/LAUNCH_NOTES.md`.

## License

Copyright © 2026 Orca Solutions. All rights reserved. See [LICENSE.md](LICENSE.md). Public visibility does not make this app open source.
