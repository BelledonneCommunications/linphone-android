# CLAUDE.md

Dimensions Technologies' fork of Belledonne's **linphone-android** (GPLv3). It's a SIP softphone
customised as a UC client for the Xarios cloud platform, and white-labelled for several resellers.
The upstream `README.md` and `CHANGELOG.md` describe Linphone, not this fork.

## Build

- JDK 17, AGP 8.7, compile/target SDK 36, min SDK 23. Single module: `:app`.
- `./gradlew assembleDebug`: debug APK. The application id gets a `.debug` suffix.
- `./gradlew bundleRelease`: the Dimensions-branded AAB. Reseller builds use `bundleBryteCall`,
  `bundleEcx`, `bundleVoyager` and `bundleVpbx`.
- Every build needs a root `keystore.properties` because `app/build.gradle` loads it
  unconditionally. CI generates one; the debug pipeline writes dummy values.
- `preBuild` runs **`ktlintFormat`**, so a build rewrites Kotlin files in place. Expect formatting
  diffs after building. ktlint failures don't break the build (`ignoreFailures = true`).
- The linphone SDK comes from download.linphone.org Maven (`org.linphone:linphone-sdk-android`).
  Set `LinphoneSdkBuildDir` in `~/.gradle/gradle.properties` to use a local SDK build instead.
- JVM unit tests live in `app/src/test/`: type adapters, gateway API contract tests
  (MockWebServer with fixtures in `src/test/resources/gateway/`, built from the model classes), and
  brand configuration checks. Run them with `./gradlew testDebugUnitTest`, and lint with
  `./gradlew lintDebug`. Lint uses `app/lint-baseline.xml`, so only new issues fail. Regenerate it
  with `./gradlew updateLintBaselineDebug`, and only on purpose.
- Tests marked `@Ignore("Known bug: …")` describe real bugs that haven't been fixed yet. Remove the
  `@Ignore` as part of the fix. `knownOverrideIssues` in `BrandConfigurationTest` works the same
  way for brand override JSON.
- There are no instrumented or UI tests. UI and call behaviour is tested manually, by eye on a
  device or emulator. Don't claim a change is verified just because it builds or the unit tests
  pass. Say what needs checking by hand.
- Dependency upgrades are welcome if they have no breaking changes. Check the release notes first,
  and flag any that need code changes instead of upgrading.

## Brands / reseller variants

Each brand is a **build type** created with `initWith release`, not a product flavour. Each one has
its own source set under `app/src/<brand>/`, holding the manifest, `google-services.json`, logos,
colours, strings, styles and `res/raw/environment_overrides.json`.

| Build type | Source set | Pipeline | Package id (set by CI) |
|---|---|---|---|
| `release` | `main` | `build-release.yml`, `build-ci.yml` | `cloud.xarios.dimensions` |
| `bryteCall` | `bryteCall` | `build-release-brytecall.yml` | `com.brytecall.mobile` |
| `ecx` | `ecx` | `build-release-ipintegration.yml` | `com.ipintegration.elasticcx.uc` |
| `voyager` | `voyager` | `build-release-voyager.yml` | `com.voyager.connectmobile` |
| `vpbx` | `vpbx` | `build-release-vpbx.yml` | `com.virtualpbx.mobile` |

When adding a branded asset, add it to **every** brand source set that overrides it, or that brand
silently falls back to the `main` version. `BrandConfigurationTest` checks this for the core set
of brand files (update `requiredBrandFiles` when you add one). It also checks each brand's pipeline,
build type, splash theme, `google-services.json` package name and `environment_overrides.json`.

## Strings

- Add and edit strings in `app/src/main/res/values/strings.xml` and in brand `strings.xml`
  overrides. The other `values-<locale>/` translations are upstream Weblate output frozen at 5.2.5
  and aren't maintained. Don't add to them.
- The exception is `values-en-rGB`, `-en-rIE` and `-en-rNZ`, which hold regional `E911_*`
  wording. Keep them in sync when changing the E911 strings.
- Never hardcode a brand name in user-facing text. Use `@~#resellerName#~@`, which CI replaces
  per brand.

## CI (Azure DevOps, `*.yml` in repo root)

The pipelines **edit source files with `sed` and token replacement before building**. Don't change
these without updating every pipeline:

- the `cloud.xarios.dimensions` literal in `app/build.gradle` (replaced with the brand package id)
- the exact lines `versionCode appVersionCode` and `versionName "${project.version}"`
- `@~#{key}#~@` placeholders are filled at release time, for example `@~#resellerName#~@` in
  `strings.xml` and `@~#DIAGBLOB_UK#~@` in `environment/Environments.kt`. The build YAMLs replace
  the `strings.xml` tokens, and the release environments handle the rest outside this repo. An
  unreplaced token in the repo is expected. Never commit real values in place of a token.

Pipeline roles:
- `build-ci.yml` is the release pipeline for the main app. It builds `bundleRelease` and pushes it
  to the Play Store internal track. It first runs `testDebugUnitTest lintDebug`, controlled by the
  `runTests` checkbox when queueing (on by default), so a failure stops the upload.
- `build-release*.yml` build the main app and each brand, and publish Azure artifacts only.
- `build-debug.yml` builds a debug APK artifact.

`.gitlab-ci.yml` and `.gitlab-ci-files/` are upstream leftovers and aren't used.

## Code layout (`app/src/main/java/org/linphone/`)

Upstream Linphone code: `activities`, `compatibility`, `contact`, `core` (`CoreContext`), `notifications`,
`telecom`, `utils`, `views`.

Dimensions additions:
- `authentication/`: OAuth/OIDC via AppAuth (`AuthStateManager`, `DimensionsAccountsManager`).
- `environment/` + `models/DimensionsEnvironment`: regional Xarios environments (NA, AU, EU, UK,
  staging) with identity, gateway and realtime URIs. Brands narrow or override them through
  `environment_overrides.json`.
- `services/`: API and business services. `APIClientService` builds Retrofit clients against the
  gateway, `Realtime*Service` uses SignalR, plus presence, call history, directories, recordings,
  transfer, push tokens (`DimensionsFirebaseMessaging`), diagnostics and branding.
- `interfaces/CTGatewayService.kt`: Retrofit interface for the UC gateway API.
- `middleware/`: OkHttp `AuthAuthenticator`, Timber `FileTree` for file logging, and
  `SentryEventProcessor`.
- `models/`, `typeadapters/`: DTOs and Gson adapters.

Stack: Kotlin and Java mix, Data Binding, Navigation, ViewModels and LiveData, coroutines, RxJava3,
Retrofit/OkHttp/Gson, and Timber for logging (use Timber, not `android.util.Log`).

## Crash reporting

Sentry is in scope for **crashes and errors only**. Keep tracing, profiling, replay and
bytecode instrumentation off. It's configured in `app/build.gradle` (`sentry {}`) and initialised in
`LinphoneApplication.onCreate`. ProGuard mapping and native symbol upload only happen when the
`SENTRY_AUTH_TOKEN` env var is set. It comes from the `Sentry-Symbol-Upload` variable group in CI,
and debug builds are skipped. Firebase Crashlytics wiring from upstream still exists but is
effectively off (`crashlytics_enabled = false` in release).

## Git workflow

- `master` is the main branch. Release lines are `release/x.y` (currently `release/5.2`).
- Branch names are `fix/<desc>` or `dev/<work-item>`. Commit messages start with the Azure DevOps
  work item, e.g. `WI #34661 - Fixed splash screen logo.`
- Work reaches branches through GitHub PRs on `Dimensions-Technologies/linphone-android`.
  **PRs target `release/5.2`**, not `master`.
- **Upstream Linphone is never merged in.** This fork split off at 5.2, and upstream has changed
  too much since then. Don't suggest pulling or cherry-picking upstream changes, and don't assume
  current upstream docs or code match this codebase. Treat the upstream code here as ours.
- `appVersionName` and `appVersionCode` in `app/build.gradle` are changed by hand when a release
  is cut. Don't bump them as part of normal changes.

## Off-limits

Never modify, move, delete or regenerate these, including the per-brand copies:
- `google-services.json`: `app/` and each `app/src/<brand>/`
- keystores and signing config: `*.keystore`, `keystore.properties`
