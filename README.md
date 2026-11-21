# LyricGlow

**An ad-free, highly customisable YouTube Music client for Android.**

LyricGlow is a branded derivative of [Mochy Music](https://github.com/realidkroo/Mochy-Music). It keeps Mochy's mature Android playback, catalogue, provider, library, cache, lyrics, and customisation architecture while applying LyricGlow's own visual identity: deep black surfaces, crisp white typography, and vivid green accents.

## Product direction

- YouTube Music-first catalogue discovery and search.
- Background playback, queue management, album and playlist playback.
- Persistent local library, history, downloads, and media cache.
- Lyrics with provider integrations and immersive player options.
- Extensive player, layout, theme, and effect customisation.
- Optional account and sync integrations; listening locally does not require a LyricGlow account.
- No LyricGlow advertising SDK or in-app ad placements.

Availability of third-party catalogue and stream endpoints depends on upstream services and may change. LyricGlow is an independent client and is not affiliated with or endorsed by YouTube or Google. It does not provide paid YouTube Premium entitlements or bypass access restrictions.

## Build

This project uses the Gradle wrapper and Android Gradle Plugin configuration supplied by the upstream project.

```bash
./gradlew :app:assembleUniversalFossDebug
```

If your environment does not have Android SDK 37 installed, install the SDK platform and matching build tools required by the Gradle build.

## Fork and licensing

LyricGlow is derived from Mochy Music. The upstream project is licensed under GPL-3.0; the original `LICENSE` file and upstream notices are retained. Changes in this derivative are also distributed under GPL-3.0. See [UPSTREAM.md](UPSTREAM.md) for attribution and change notes.

Do not remove upstream copyright notices. If you distribute binaries, provide the corresponding source under the GPL terms.

## Configuration

Optional third-party integrations may require their own developer credentials. Keep credentials in `local.properties` or CI secrets; never commit private keys, OAuth secrets, or personal account tokens.
