# GymBuddy

A workout tracker with the feature set of Strong, built as two native apps:

- `android/` — Kotlin, Jetpack Compose, Room, Hilt
- `ios/` — Swift, SwiftUI, SwiftData (project generated with [XcodeGen](https://github.com/yonaskolb/XcodeGen))
- `shared/` — data both apps use: the built-in exercise library (`exercises.json`, English + Vietnamese) and cross-platform test vectors
- `supabase/` — backend schema (Postgres + Row Level Security) as SQL migrations

Plan and roadmap: [GymBuddy plan](https://claude.ai/artifact/B8nMc2oNurDNNxptmehYJ9).

## Android

Requires Android Studio (or JDK 17 + Android SDK 35).

```sh
cd android
./gradlew testDebugUnitTest assembleDebug
```

## iOS

Requires Xcode 16 and XcodeGen (`brew install xcodegen`).

```sh
cd ios
xcodegen generate
open GymBuddy.xcodeproj
```

## Backend

The apps keep a local database for offline use and sync with Supabase. Each synced row has a client-generated UUID,
`updated_at` (newer wins) and `deleted_at` (soft delete). To apply the schema to a Supabase project:

```sh
supabase link --project-ref <your-project-ref>
supabase db push
```

## Shared test vectors

Calculations that must agree on both platforms (estimated 1RM, unit conversion) are checked against
`shared/test-vectors/*.json` by the unit tests of each app.
