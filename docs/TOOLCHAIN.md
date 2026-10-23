# Android toolchain

The project pins a stable API 36 toolchain. Gradle downloads dependencies on the first build; later builds reuse its cache.

| Component | Pinned version |
| --- | --- |
| Android Gradle Plugin | 8.13.2 |
| Gradle wrapper | 8.13 |
| Kotlin and Kotlin Compose compiler plugin | 2.2.21 |
| Compose BOM | 2025.10.01 |
| Activity Compose | 1.11.0 |
| Lifecycle | 2.9.4 |
| Navigation Compose | 2.9.5 |
| Firebase Android BOM | 34.4.0 |
| Google Services Gradle plugin | 4.4.4 |
| Coroutines | 1.10.2 |
| Compile / target SDK | 36 |
| Minimum SDK | 26 (Android 8.0) |
| JVM bytecode target | 17 |

AGP 8.13 requires Gradle 8.13 and JDK 17 or newer and supports API 36.1. This workspace uses Microsoft OpenJDK 21.0.8, which is compatible with Gradle 8.13. Avoid independently upgrading only one of AGP, Gradle, Kotlin, and Compose.

Verified primary references:

- [AGP 8.13 compatibility and 8.13.2 release](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Gradle 8.13 release](https://docs.gradle.org/8.13/release-notes.html)
- [Kotlin release history](https://kotlinlang.org/docs/releases.html)
- [Compose BOM guidance](https://developer.android.com/develop/ui/compose/bom)
- [Activity 1.11.0](https://developer.android.com/jetpack/androidx/releases/activity#1.11.0)
- [Lifecycle 2.9.4](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.4)
- [Navigation 2.9.5](https://developer.android.com/jetpack/androidx/releases/navigation#2.9.5)
- [Firebase Android release notes](https://firebase.google.com/support/release-notes/android)
- [Firebase main modules and BoM setup](https://firebase.google.com/docs/android/setup)

## Windows build

Open the repository root in Android Studio, choose JDK 17 or 21 as its Gradle JDK, and install Android SDK Platform 36 and Build Tools 35.0.0 through SDK Manager. `local.properties` records the local SDK path and is ignored by Git.

From PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
```

This runs `assembleDebug`, `testDebugUnitTest`, and `lintDebug`. The helper discovers `JAVA_HOME`, Android Studio's bundled JDK, or the Microsoft JDK found on this workstation. To choose another JDK:

```powershell
.\scripts\build.ps1 -JavaHome 'C:\path\to\jdk-21'
```

The script changes environment variables only for its own process and restores them before returning. The standard `gradlew.bat` and `gradlew` wrappers also work directly.

Workstation paths discovered during implementation:

- JDK: `C:\Program Files\Android\openjdk\jdk-21.0.8`
- SDK: `C:\Android`
- SDK manager: `C:\Android\cmdline-tools\latest\bin\sdkmanager.bat`
- ADB: `C:\Android\platform-tools\adb.exe`

Build output: `app/build/outputs/apk/debug/app-debug.apk`. Unit-test report: `app/build/reports/tests/testDebugUnitTest/index.html`. Lint report: `app/build/reports/lint-results-debug.html`.

## Firebase configuration

Place the project's real `google-services.json` at `app/google-services.json`. It must contain an Android client for `com.darktemp.app` and the project's Realtime Database URL. The Google Services plugin is applied only when that file exists. An unconfigured build is intentionally installable and shows setup guidance rather than simulated sensor readings or a fabricated successful connection.

## Wrapper provenance

The wrapper scripts and JAR come from the official Gradle `v8.13.0` tag. The JAR was verified against Gradle's published SHA-256 checksum:

```text
81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
```

`gradle-wrapper.properties` pins and verifies the Gradle distribution checksum:

```text
20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
```

Source: [Gradle distribution checksums](https://gradle.org/release-checksums/).
