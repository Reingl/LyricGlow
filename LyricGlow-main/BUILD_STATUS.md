# Build and verification status

- Source edits and archive structure checked locally.
- A local Android build was **not run**: this snapshot has no Gradle wrapper, and this environment has no installed `gradle` executable or configured Android SDK.
- The repository's GitHub Actions workflow installs Gradle 8.11.1 and Android SDK 36, then runs `gradle --no-daemon assembleDebug`.
- Please run the existing GitHub Actions workflow after pushing. Treat its compiler output as the authoritative build check before release.
