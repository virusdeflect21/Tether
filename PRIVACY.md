# Privacy

Tether is local-first.

- No account is required.
- There is no analytics, advertising SDK, advertising ID or crash telemetry.
- Library metadata, playlists, favorites, history, lyrics and settings are stored on the device.
- The app does not upload the local library, listening history or files.

## Network requests

Tether makes network requests only for features you use:

| Purpose | Destination |
| --- | --- |
| Online music and video search and streaming | `youtube.com`, its InnerTube endpoints, and the media URLs it publishes |
| Lyrics lookup | `lrclib.net` |
| Remote artwork | URLs published by the provider |
| User-requested downloads | The media URL published by the provider |
| Diagnostics | A connectivity check to the music and lyrics providers |

Search terms and track metadata are sent to the relevant provider when you search or request lyrics. As with any web request, those services receive your IP address and standard request information. Their own privacy policies apply.

## Files and permissions

- Audio access (`READ_MEDIA_AUDIO`, or `READ_EXTERNAL_STORAGE` on Android 12 and below) is used to scan audio already on the device.
- Folder access is granted through the Storage Access Framework and can be revoked in system settings.
- Imported files and downloads are read only after you choose them.
- Download files are written to the app-specific external files directory.

## Removing data

Use Settings → Clear caches for temporary files, or clear the app's storage in Android settings to remove the database, playlists, settings and app-managed downloads. Deleting the app does not modify your original music files.
