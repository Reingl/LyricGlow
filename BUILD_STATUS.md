# Build status for this LyricGlow snapshot

- Source archive integrity: checked.
- XML resource parsing: checked.
- Branding/application ID consistency: statically inspected.
- Android compilation: **not verified in this environment**. The Gradle wrapper attempted to download Gradle 9.3.1, but this environment could not resolve `services.gradle.org` (DNS/network unavailable).
- Recommended CI task: `./gradlew assembleUniversalFossDebug`.
- The upstream workflow currently builds `assembleUniversalGmsDebug`; that flavor may require the Google Cast/GMS configuration. The FOSS universal task is the default no-GMS build target.

Run the task in Android Studio or GitHub Actions with Android SDK 37 and JDK 21. Report the complete first build log before changing dependency versions.
