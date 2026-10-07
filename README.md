# android-compose-template

A baseline Android app template built for **cold start speed** and **smooth frames**.

Single module, Kotlin, Jetpack Compose, MVVM with `StateFlow`, manual DI. No Hilt, no
annotation processors, no reflection-based DI — every layer you skip here is work you
otherwise pay for at startup.

## Start a new project from this

```bash
git clone https://github.com/cat-miaw/android-compose-template.git my-app
cd my-app
```

Then make it yours — one command does the whole rename:

```bash
# usage: ./setup.sh com.yourcompany.myapp My App Name
./setup.sh com.yourcompany.myapp "My App Name"
git rm -rf .git && git init    # drop the template's history
./gradlew :app:assembleDebug
```

The script rewrites `namespace`, `applicationId` and the benchmark package, moves the
source directories, and updates the app label. On Windows, run the steps manually — it is
only a package rename.

If you hit the `[androidx.baselineprofile] Configuration with name 'baselineProfile' not
found` error after renaming, you skipped setup.sh and the module wiring drifted; re-clone
and use the script.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 35 |
| Gradle | via the included wrapper (8.11.1) |
| minSdk / targetSdk | 24 / 35 |

## Build and run

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:installDebug           # install on a connected device
./gradlew :app:assembleRelease        # minified + resource-shrunk APK
./gradlew :app:testDebugUnitTest      # JVM tests
./gradlew :app:connectedAndroidTest   # instrumentation tests
```

If `local.properties` is missing, point it at your SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Layout

```
app/                     single module — the whole app
  src/main/java/com/example/template/
    App.kt               Application: intentionally does nothing at startup
    MainActivity.kt      single activity, splash screen, edge-to-edge
    di/                  manual dependency container + repository
    ui/
      TemplateNavHost.kt navigation graph
      home/              ViewModel, UI state, screen, route binding
      theme/             Material 3 color, type, theme
benchmark/               baseline profile generator (needs a physical device)
```

Rename `com.example.template` to your own package before you start — it appears in
`app/build.gradle.kts` (three times), `AndroidManifest.xml`, `benchmark/build.gradle.kts`,
and the package directories.

## Why it starts fast

| Lever | Where | Effect |
| --- | --- | --- |
| Empty `Application` | `App.kt` | No work before the first frame. Everything is `by lazy`. |
| Manual DI | `di/AppContainer.kt` | No annotation processing, no classpath scanning, no reflection at startup. |
| Splash screen | `MainActivity.kt`, `themes.xml` | The system draws the splash for free. XML-only theme: no AppCompat, no Material view library. |
| Baseline profiles | `benchmark/` | ART precompiles your code ahead of launch. Typically a 15–30% cold-start win. Generate it once and commit the file. |
| R8 full mode + shrinking | `app/build.gradle.kts` | Release APK is ~1 MB vs ~17 MB debug. |
| No blocking main thread | `HomeRepository` | I/O runs on `Dispatchers.IO`; the screen renders immediately with a loading state. |
| Language splits | `app/build.gradle.kts` | Only the needed locale resources ship. |

### Generate a baseline profile

On a **physical device** (emulators give misleading numbers):

```bash
./gradlew :app:generateBaselineProfile
```

Commit the generated `app/src/main/baseline-prof.txt`. Regenerate whenever you add a
significant new user journey — the profile only helps code it has actually seen.

## Why frames stay smooth

| Lever | Where | Effect |
| --- | --- | --- |
| Strong skipping | Kotlin 2.0.20+ default | The compiler skips composables whose parameters didn't change. Only works if types are stable and immutable — which is why state is a `data class` of vals. |
| Single immutable state object | `HomeUiState` | One source of truth, structural equality, no cascading invalidation. |
| `collectAsStateWithLifecycle` | `HomeRoute.kt` | Collection suspends while the screen is not visible, so hidden screens never recompose. |
| `LazyColumn` with keys | `HomeScreen.kt` | Composes visible rows only, and stable `key` values let Compose reuse nodes across reloads. |
| Derived state, not repeated computation | — | If you find yourself recomputing a list on every recomposition, hoist it with `remember` or `derivedStateOf`. |
| Typed typography | `theme/Type.kt` | Fewer text styles to compose on first frame. |

The `androidTest` and `benchmark` modules are where to catch regressions. The benchmark
module is also the right home for a `JankStats` test once you have real screens:

```kotlin
// Trace one interaction, fail the build if frames-per-second drops.
val jank = JankStats.createAndTrack(window) { frameData ->
    frameData.frameDurationUiNanos
}
```

## CI

`.github/workflows/ci.yml` runs lint, unit tests and a release assemble on every push.
Parallel execution and the Gradle build cache are on. The configuration cache is **off** —
AGP 8.7.x cannot serialize `aarMetadataArtifacts` on `CheckAarMetadataTask` and fails the
build rather than degrading gracefully. Revisit it when you bump AGP; the payoff is real.

## Things to know before you build on this

- **Release signing uses the debug key.** It builds out of the box; wire up a real
  `signingConfig` before you ship.
- **`HomeRepository.loadItems()` has a `delay(400)` in it.** That is a visible stand-in
  for a real I/O call so you can see the loading state. Delete it.
- **Baseline profiles do not apply to debug builds.** Always measure release.
- **A profile that compiles too much code makes startup slower**, not faster. If launch
  time regresses after adding journeys, the profile is too broad.

## Useful commands

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath  # what ships
./gradlew :app:lintRelease                                        # static checks
./gradlew :app:bundleRelease                                       # AAB for Play
./gradlew clean :app:assembleRelease                               # verify from scratch
```

Reference measurements come from Android vitals and Macrobenchmark on a physical device —
treat any number in a template as a direction, not a guarantee.