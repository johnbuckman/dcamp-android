# dcamp — native Android app

A **fully native Kotlin + Jetpack Compose** client for [dcamp](https://decentespresso.com/support/dcamp/),
the Decent Espresso "Diaspora" community forum. A feature clone of the SwiftUI
[`dcamp-ios`](https://github.com/johnbuckman/dcamp-ios) app, working identically.

- **This repo**: the Android app.
- **Server**: dcamp is NaviServer/Tcl + MySQL + a vanilla-JS SPA under `/d` (John's web root).
  The app talks to ONE endpoint: `POST {host}/support/dcamp/api.adp` with `action=<name>` form
  params (~150 actions), bearer-token auth (`lib/dcamp_native.tcl`), envelope `{"ok":…}`.
- Live server = `decentespresso.com`; Test = local NaviServer (emulator reaches the host at
  `10.0.2.2:8000`). Runtime-switchable from **My settings → Server** (admin).

## Architecture (mirrors dcamp-ios)

| iOS (SwiftUI) | Android (Kotlin) |
|---|---|
| `DcampAPI` actor | `api/DcampApi.kt` (OkHttp, one `call<T>`/`rawData`, bearer) + `DcampApiFeatures.kt` |
| `Models.swift` Codable | `api/Models.kt` `@Serializable` (snake_case naming strategy) |
| `@Observable` stores | `SessionStore` (StateFlow), `ui/Router`, `ui/UIStrings` |
| `DcampTheme` | `ui/theme/` (paper light + warm-dark palette, `ThemeState` reactive) |
| Native Trix renderer | `ui/trix/` (`TrixParser`/`TrixInline`/`TrixContentView`) |
| Embedded-Trix composer | `ui/views/Composer.kt` (WebView + `assets/editor/editor.html` + JS bridge) |
| Keychain | `TokenStore` (EncryptedSharedPreferences) |
| ASWebAuthenticationSession | `OAuthManager` (PKCE + Chrome Custom Tabs, redirect `dcamp://auth`) |

OAuth reuses the server's built-in first-party client `dcamp-ios` → `dcamp://auth` (no server
registration needed).

## Features

Auth (OAuth2/PKCE + bearer) · forums home card grid · thread list (category pills, machine/DECENT
badges, Problems status, pagination) · thread detail (native Trix: paragraphs/headings/lists/quotes/
tables/images-with-zoom/YouTube/links/@mentions; reactions + emoji picker; ⋯ menu copy-link/status/
pin/delete; prev-next; since-last-visit; 3s live poll) · rich composer (embedded Trix, @mention
autocomplete, toolbar) · chat room · direct messages (bubbles, groups, unread) · search (facets,
Ask-Derek, people/results, highlighting) · person cards · notifications inbox · settings (profile,
appearance/theme, language, notifications, muted, Basecamp, account) · server-driven i18n.

**Deliberately not done** (mirrors iOS): Adminland (blocked by the emailed `adminpw` cookie a bearer
can't hold) · `client=native` structured content · push notifications (deferred).

## Build & run

Requires Android Studio (bundles JDK 21) + SDK 35. Gradle 8.13 / AGP 8.9.1 / Kotlin 2.0.21 /
Compose BOM 2024.12 / minSdk 26.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release signing reads `local.keystore.properties` (gitignored): `storeFile`, `storePassword`,
`keyAlias`, `keyPassword`. Then `./gradlew :app:assembleRelease`.

## Gotchas carried over from iOS

- **Composer `ComposerModel` must be owned via `remember`** by the hosting view — a fresh model per
  recomposition has a null WebView, so Send reads empty and silently no-ops (chat 3s / DM 6s polls).
- **Send fetches HTML on demand** (`dcampGetHTML()`), never gated on change events.
- Booleans arrive as 0/1 ints; `has_more` is an Int. Decode as Int, compare.
- Category-pill in a thread row must not vertical-wrap (long names) — `maxLines=1, softWrap=false`.
