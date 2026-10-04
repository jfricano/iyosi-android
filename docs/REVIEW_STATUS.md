# Android review status

Status as of October 4, 2026, for pull request #1.

## Where things stand

- The Kotlin and Jetpack Compose app replaces the earlier Java pilot. It matches the iOS app screen for screen and uses the landing page's city-guide design.
- GitHub Actions builds every push and runs four steps:
  - unit tests;
  - Robolectric screenshot renders;
  - debug and R8 release builds;
  - lint.
- CI is green on the pull request head.
- An independent review ran before merge, with three reviewers covering the API contract, the Android platform, and the Compose UI.
  - It found 2 blockers and 7 should-fix issues, and all of them are fixed.
  - The findings were reconciled with the iOS and API reviews. The shared notes are in the project's `reviews/android.md`, `reviews/ios.md`, and `reviews/api.md`.
- **Not yet exercised:** the app has not run on an emulator or device against a live API. The cloud environment cannot download the Android SDK, so it is verified only by CI. That makes the dev-mode test below the gate before merging.

## Merging

Merge with **Rebase and merge** once the dev-mode test below passes. The branch is a linear series of commits on top of `main`, with no merge commits, so they apply cleanly and keep their author.

## Works with which API

- The app works against the API's current `main`.
- iyosi-api pull request #2 only adds endpoints or loosens rules, and Android uses one of its additions:
  - **`GET /v1/places/{id}/reviews/me`:** the review form prefills your rating and offers "Delete my review". Without that endpoint the form starts blank, and nothing else changes.
  - **Account deletion:** the deletion dialog and privacy notice describe what deletion removes under the policy in API pull request #2. That is reviews removed and free text blanked; published places stay.

## Before a release build

- **Production API host.** No production HTTPS API host has been chosen.
  - Release builds compile in `-PiyosiApiUrl` only when it starts with `https://`.
  - Without one, people must enter a server under Account → API server settings.
- **Tile provider.** Choose a pilot-approved tile provider, or confirm that OpenStreetMap's tile usage policy covers the pilot.
- **Signing and store listing.** Signing keys and the store listing are not set up.

## Known open items (accepted for the pilot)

- If Android kills the app in the background, it reopens on the map: the open screen and any form drafts are lost. Rotation, theme, and font-size changes keep state.
- Map tiles that have scrolled off screen still finish downloading. That is wasted data, but harmless.

## Optional follow-ups

- **Look and feel closer to iOS:**
  - Bundle the Inter typeface; Android currently uses Roboto, while iOS uses Avenir Next.
  - Tint OpenStreetMap tiles toward the landing palette.
  - Use a diamond question-mark icon for "Establishment claim".
- **Server-side items** (rate limits per account, correction diffs, connection pooling) are tracked in `reviews/api.md`.

## Dev-mode test checklist

### Setup

1. Start the API's isolated demo stack from the iyosi-api repository (see its `docs/DEMO_DATA.md`). It serves fictional places on port 8001 with working address search.
   - To test review prefill and delete, run the API from its pull request #2 branch.
2. Install the debug build pointed at the demo API from the emulator:

   ```sh
   ./gradlew installDebug -PiyosiApiUrl=http://10.0.2.2:8001
   ```

   Alternatively, install the default debug build and set `http://10.0.2.2:8001` under Account → API server settings.
3. To check moderation results, an admin can use the API's moderation console at `http://localhost:8001/admin`. That needs an account promoted with `scripts/grant_admin.py`.

### Journeys

| Area | Check |
| --- | --- |
| Welcome | The adult prompt appears first. "I am under 18" exits. The privacy notice opens and returns. |
| Map | Four demo pins appear around Manila, Quezon City, Makati, and Taguig. Pinch, pan, and double-tap zoom work, and pins cluster when zoomed out. Tiles stay below the header. |
| Search | Typing `DEMO` lists suggestions, and choosing one moves the map. |
| Filters and legend | Type and status filters change the pins. Prohibited warnings are hidden until turned on. The legend sheet opens. |
| List | The List toggle shows the same places with status text. |
| Near me | Location permission is asked only on tap. Use the emulator's extended controls to set a Manila location. |
| Place card and detail | Tapping a pin shows the compact card; "View place details" (or "Read report details") opens the full page. Walking directions open Google Maps, or a notice if no maps app is installed. |
| Deep link | `adb shell am start -a android.intent.action.VIEW -d iyosi://places/PLACE_ID` opens that place. |
| Sign in | Contributing asks you to sign in. Register with a 12+ character password and the adult attestation. Account shows your email, and sign-out works. |
| Confirm | Confirm an observation (date within 29 days) and see the receipt. |
| Review | Rate a place. Reopen the form: with API pull request #2 it prefills, and "Delete my review" removes it. |
| Report | Report a place and see the receipt. |
| Add place | Tap the map to set the area pin, then switch to the entrance pin. A proposal within 50 m of a demo place is blocked in favour of a correction. A distant one submits and appears under Account → your proposals as pending. |
| Correction | "Suggest correction" on a place detail opens the form for that place. |
| Server change | Sign in, change the API server in settings, and confirm you are signed out. |
| Account deletion | The request dialog explains what deletion removes, and the request is acknowledged. |
| Rotation and dark mode | Rotate on a form with text entered: the screen and text stay. Switch to dark mode and the guide palette changes. |
| Release build | `./gradlew assembleRelease -PiyosiApiUrl=https://…` installs and talks only to HTTPS. |

## Verification commands

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

- The screenshot renders land in `app/build/outputs/roborazzi`.
- Reviewed copies are in [design](design).
- A pushed commit whose message contains `[update screenshots]` republishes the renders to the `design-screenshots` branch.
