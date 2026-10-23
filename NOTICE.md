# Third-party notices

Tether is built with open-source Android and Kotlin libraries. The main components are listed below. Each project remains under its own license; this file does not relicense them.

| Component | Purpose | License |
| --- | --- | --- |
| AndroidX / Jetpack Compose | UI, lifecycle, activity integration | Apache License 2.0 |
| AndroidX Media3 (ExoPlayer, MediaSession) | Playback and media session | Apache License 2.0 |
| AndroidX Room | Local database | Apache License 2.0 |
| AndroidX DataStore | Preferences | Apache License 2.0 |
| AndroidX WorkManager | Background downloads | Apache License 2.0 |
| Kotlin & Kotlin Coroutines | Language and concurrency | Apache License 2.0 |
| OkHttp | HTTP client | Apache License 2.0 |
| Coil | Image loading | Apache License 2.0 |
| Palette | Artwork color extraction | Apache License 2.0 |
| Material Icons | UI icons | Apache License 2.0 |
| NewPipeExtractor | Resolving YouTube metadata and stream URLs | GNU General Public License v3.0 |
| nanojson | JSON parsing used by NewPipeExtractor | MIT License |
| Rhino | JavaScript engine used by NewPipeExtractor for player signatures | Mozilla Public License 2.0 |
| jsoup | HTML parsing used by NewPipeExtractor | MIT License |
| Poppins | Bundled UI typeface | SIL Open Font License 1.1 |

The full dependency set and exact versions are declared in `gradle/libs.versions.toml` and the module `build.gradle.kts` files. Transitive dependencies are distributed under their respective licenses; their license files are packaged with the APK where required. The full Poppins license text is included at `licenses/Poppins-OFL.txt`.

## Services

- **YouTube** — online music and video catalog, resolved through NewPipeExtractor against the public web player. Tether is not affiliated with, endorsed by, or sponsored by YouTube. Media remains the property of its rights holders. Streaming and downloads are the user's responsibility and are intended for personal use.
- **LRCLIB** — community lyrics database. Lyrics remain the property of their authors.

## Brand assets

The Tether feather logo, wordmark, launcher icons and TV banner are original project assets. The MIT License covers the source code; it does not grant trademark rights to the Tether name or logo.

No third-party application source code or assets from other music players are included in this repository.
