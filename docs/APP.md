# OpenPostMobajl — lightweight Android app

A ~290 KB native Android app (no Gradle, no AndroidX, no Kotlin; only the
ZXing core jar, downloaded by `bootstrap.sh`, for local QR generation) that
implements the same flow as the original Go POC:

- **If `auth.json` does not exist:** renders the InPost OAuth login page in an
  in-app `WebView`. After the phone/SMS login, the redirect to
  `https://account.inpost-group.com/callback?code=...` is intercepted, the code
  is exchanged for tokens, the state is saved, and the parcel list is shown.
- **If `auth.json` exists:** uses the saved access token, refreshes it with the
  refresh token if needed, and lists parcels. No login page is shown.

`auth.json` and `parcels.json` are encrypted at rest with an AES-256-GCM key
held in the Android Keystore (alias `openpostmobajl_data_key`). The key is
non-exportable and hardware-backed when the device supports it.

The full protocol is documented in [`docs/FLOW.md`](FLOW.md).

---

## Build

```sh
cd ~/openpostmobajl
./bootstrap.sh          # first time only
./build-debug.sh        # debug APK
./build-release.sh      # signed release APK
```

Outputs:

```
out/openpostmobajl-debug.apk
out/openpostmobajl-release.apk
```

The build uses only the official SDK tools (`aapt2`, `javac`, `d8`, `jar`,
`zipalign`, `apksigner`) through the SDK's `tools/gcompat.sh` wrapper. The debug
APK is signed with the local debug keystore and is marked
`android:debuggable="true"` via `aapt2 link --debug-mode`. The release APK is
not debuggable and is signed with `tools/release.keystore`.

`minSdk` is 23, which is required for Android Keystore AES-GCM key generation.

## Install / run

```sh
tools/gcompat.sh adb install -r out/openpostmobajl-debug.apk
tools/gcompat.sh adb shell am start -n com.openpostmobajl/.MainActivity
```

First launch:

1. The app opens the InPost login page in a WebView.
2. Log in with your Polish phone number and the SMS code.
3. The app intercepts the callback, exchanges the code, saves `auth.json`, and
   shows the parcel list.

Later launches go straight to the parcel list. Use **Refresh** to re-fetch and
**Sign out** to delete `auth.json` and start the login again.

## Inspect the saved state

The app stores the same JSON shape as the Go POC at:

```
/data/data/com.openpostmobajl/files/auth.json
```

The file is an encrypted envelope, not plaintext JSON:

```json
{"v":1,"iv":"<base64>","data":"<base64 ciphertext>"}
```

The AES key lives in the Android Keystore and is not exportable, so `cat` only
shows the envelope. To reset without the UI:

```sh
tools/gcompat.sh adb shell run-as com.openpostmobajl rm files/auth.json
```

Only encrypted envelopes are read; plaintext JSON is not supported.

## Parcel list and detail view

The main list excludes delivered parcels. Delivered parcels are shown in the
**Delivered parcels** view, accessible from the sidebar.

Each list row shows, in order:

```
<last 6 of shipmentNumber>  ·  <statusGroup>  ·  <pickup point name>  ·  <sender name>  ›
```

Tapping a row opens `ParcelDetailActivity`, which shows:

- a locally generated QR code at the top for delivered parcels and for
  ready-to-pickup parcels (`statusGroup` `TO_PICKUP` / `status`
  `READY_TO_PICKUP`) whenever the JSON carries a non-empty `qrCode` field
  (content = that field); tap it to open fullscreen at maximum screen
  brightness,
- a short summary (shipment, status, pickup point, sender),
- the complete backend JSON.

QR generation uses the ZXing core jar installed by `bootstrap.sh` into
`libs/`; no network is involved.

## UI

- **Main view:** a top bar with a burger button (`≡`) on the left and the
  `OpenPostMobajl` title, then the status line and the parcel list.
- **Sidebar:** tap the burger to slide in a panel with **Refresh**,
  **Delivered parcels**, **Backend: auto/v4/global**, **Licenses**, and
  **Sign out**. Close it with the `×` button, by tapping the dimmed scrim, or
  with the system Back button.
- The status line and parcel list always stay in the main view.

## Cache

The last successful parcel list is saved to `parcels.json` in the app's files
directory. Like `auth.json`, it is an encrypted envelope:

```json
{"v":1,"iv":"<base64>","data":"<base64 ciphertext>"}
```

The decrypted payload is:

```json
{
  "savedAt": "2026-11-09T13:40:00Z",
  "backend": "v4",
  "parcels": [ ... ]
}
```

On launch:

1. If `auth.json` has credentials, `parcels.json` is read and rendered
   immediately (status shows `Cached v4 | parcels: N — updating...`).
2. The app then calls the backend. On success the list is re-rendered and
   `parcels.json` is overwritten.
3. If the network call fails, the cached list stays visible and the status
   shows the error.

The cache is deleted on **Sign out**. It is best-effort: a read/write failure
never breaks the UI. Writes go to `parcels.json.tmp` and are renamed into place.

`cat` shows only the encrypted envelope. Only encrypted envelopes are read;
plaintext JSON is not supported.

## Sorting

The main list is sorted by status group (stable, so the original order is kept
within each group):

1. **Ready to collect** — `TO_PICKUP`, `AWAITING_COLLECTION`, `READY_TO_COLLECT`
2. **In transit** — `IN_TRANSIT`, `IN_DELIVERY`, `OUT_FOR_DELIVERY`
3. **To send** — `TO_SEND`, `TO_SENT`, `CREATED`
4. **Other** — `UNDELIVERED`, `CLAIMED`, `OTHER`, anything unknown

Delivered parcels (`DELIVERED`) are excluded from the main list and shown in
the **Delivered parcels** view. The rank function also recognizes `DELIVERED`
for that view.

The rank is computed from `statusGroup` first, then `status` as a fallback.
This matches the app's own `ShipmentStatusGroup` enum
(`CREATED`, `TO_SENT`, `IN_TRANSIT`, `IN_DELIVERY`, `TO_PICKUP`, `DELIVERED`,
`UNDELIVERED`, `CLAIMED`, `OTHER`).

## Backends

The app has a **Backend** button that cycles `auto` → `v4` → `global`:

| Preference | Request | Notes |
|---|---|---|
| `auto` | `GET /v4/parcels/tracked`, then global | Default. |
| `v4` | `GET /v4/parcels/tracked` | Current PL endpoint; the official 4.22.1 app uses it. |
| `global` | `GET /global/cps/api/v1/parcels?role=receiver&size=100` | Newer CPS API. |

Tested with a live PL account (2026-11-09):

- `v4` → **200**, returns `parcels[]` (including `parcelId`, `eventLog`, `events`).
- `global` → **501** `FEATURE_DISABLED`:
  `"Feature 'findParcels' has not been implemented yet. Find parcels feature is disabled for market: PL"`.
  The same 501 is returned for `role=receiver`, `role=sender`, and the
  `/global/cps/api/v1/parcels/{parcelId}` detail endpoint.
- The `app-inmobile-pl.easypack24.net` host (used when the remote-config flag
  `isCfFqdnEnvironmentEnabled` is true) is currently `NXDOMAIN`; the app falls
  back to `api-inmobile-pl.easypack24.net`.

So for the PL market there is no newer working parcel-list backend with the
current auth data; `v4/parcels/tracked` is the current endpoint, not a
deprecated one. The global CPS catalogues (`/global/cps/api/v1/status-catalogue`,
`/global/cps/api/v1/event-catalogue`) do work, but they are metadata only.

## Source layout

```
app/AndroidManifest.xml                     INTERNET permission, MainActivity
app/res/values/strings.xml                  app name
app/src/com/example/inpost/MainActivity.java        UI, WebView, orchestration
app/src/com/example/inpost/ParcelDetailActivity.java full parcel view + QR code
app/src/com/example/inpost/ParcelCache.java          parcels.json cache
app/src/com/example/inpost/Pkce.java                 OAuth constants + PKCE
app/src/com/example/inpost/ApiClient.java            token exchange/refresh + parcels
app/src/com/example/inpost/AuthStore.java            encrypted auth.json read/write
app/src/com/example/inpost/AuthState.java            state fields
app/src/com/example/inpost/SecureStore.java          Android Keystore AES-GCM helper
libs/                                                ZXing core jar (downloaded by bootstrap)
```

The original "Hello World" sample is preserved in `app-hello/` (not built).

## How it maps to the POC

| POC step | Android implementation |
|---|---|
| Generate PKCE verifier/challenge, state, nonce | `Pkce.randomB64`, `Pkce.challenge` |
| Build authorize URL | `Pkce.buildAuthorizeUrl` |
| Open browser | `WebView.loadUrl` (in-app) |
| User logs in (phone + SMS + Turnstile) | WebView renders `account.inpost-group.com` |
| Capture `code` from redirect | `WebViewClient.shouldOverrideUrlLoading` + `Uri.getQueryParameter("code")` |
| Exchange code at `/oauth2/token` | `ApiClient.exchangeCode` (`HttpURLConnection`, form-urlencoded) |
| Store `Bearer <access_token>`, `GIP` | `ApiClient.applyTokens` + `AuthStore.save` |
| Refresh with `grant_type=refresh_token` | `ApiClient.refresh` |
| List parcels (legacy, then global) | `ApiClient.fetchParcels` |
| Render parcels | `MainActivity.render` |

## Notes / troubleshooting

- The login page is protected by Cloudflare Turnstile. The app uses a real
  `WebView` (JavaScript + DOM storage + third-party cookies enabled), which is
  the same approach as the official app. A device/emulator with an up-to-date
  Android System WebView is required.
- The app uses the **default WebView user-agent** (the official app's login
  WebView also uses the default; its `WebViewSettings.userAgentString` default
  is `null`).
- All network calls are HTTPS. The app has the `INTERNET` permission.
- The parcel API calls use the same headers as the POC: `X-App-Id`,
  `device-uid`, `User-Agent`, `Accept-Language`, `Authorization: Bearer ...`.
- If the login page does not load, check `adb logcat` and make sure the device
  has network access and a working WebView.
- `auth.json` and `parcels.json` are encrypted with a non-exportable Android
  Keystore key (AES-256-GCM). The key is removed when the app is uninstalled.
