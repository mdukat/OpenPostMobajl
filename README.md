# OpenPostMobajl

## Info od człowieka

Apka jest wyklankerowana chińskim modelem `deepseek-v4.1-flash` z openroutera. Przeczytaj kod zanim zbudujesz/zainstalujesz. To Twoje urządzenia i ty za nie odpowiadasz. Piesek na ikonce to moja psina, jako alternatywa dla tego jednego VonDojcza przez którego istnienie powstał ten projekt. Kodzik i miniaturka w tym repo jest na licencji MIT, więc jeśli chcesz jakąś nową funkcjonalność, [to wiesz co robić](https://www.youtube.com/watch?v=aHtEm9sxzYg).

TLDR: logujesz sie do apki przez webview z oficjalnej aplikacji, token jest trzymany w androidowym keystore, klikasz na paczuche i odbierasz. Koniec funkcjonalności apki.

Jeśli chcesz zbudować od zera: postaw se VMke z Alpine Linuxem, pobierz to repo, odpal `bootstrap.sh` a następnie `build-release.sh`. W katalogu `out` zobaczysz gotowy pliczek APK do wgrania na telefon.

Jeśli pewnej firmie od dostarczania paczek nie podoba się, że to repozytorium istnieje - dajcie znaka (najlepiej na maila). Usune w przeciągu 14 dni kalendarzowych od otrzymaniu takiej informacji. Wiecie jak mnie znaleźć.

Apka by-default posiada tryb uproszczonego widoku. Specjalnie klankerowałem go z myślą o osobach starszych w mojej rodzinie. Zawiera powiększone czcionki, prosty język, tyle informacji, ile jest osobie potrzebne do odebrania przesyłki. Jeśli jesteś poweruserem, możesz go wyłączyć w ustawieniach i obserwować status paczek czytając surowy JSON zwracany przez endpoint.

Funkcjonalność powiadomień jest dosyć trudna i klanker sobie z nią dobrze nie radził. Jego wypociny znajdziesz w branchu `func/notifications`, ale z moich testów generalnie nie działa to dobrze i wymaga więcej pracy albo osoby która ogarnia ekosystem androida (wspominałem już, że nie potrafie w androida?).

Poniżej kontynuacja README od klankera.

---

A lightweight, native Android client for the InPost Mobile parcel flow.

It logs in through the real InPost OAuth page in an in-app `WebView`, lists
your parcels, caches the last known state, and shows a full parcel detail view
with a locally generated QR code for delivered parcels.

The app is built without Gradle, AndroidX, or Kotlin. The only third-party
library is ZXing core, downloaded by `bootstrap.sh` into `libs/` and used for
local QR generation.

> This is a personal/educational POC. It is not affiliated with InPost. Use it
> only with your own account. Tokens are live credentials.

---

## Features

- OAuth2/OIDC + PKCE login in a `WebView` (phone + SMS, Cloudflare Turnstile
  handled by the real login page).
- Automatic token refresh.
- Parcel list from the current PL endpoint (`/v4/parcels/tracked`).
- Compact list rows: last 6 of `shipmentNumber`, `statusGroup`, pickup point,
  sender.
- Main list sorting: ready to collect → in transit → to send → other.
  Delivered parcels are shown in a separate **Delivered parcels** view.
- Last-known-state cache (`parcels.json`) shown immediately on launch, then
  refreshed from the backend.
- Full parcel detail view with the complete backend JSON.
- Locally generated QR code for delivered parcels (content = `qrCode`).
  Tap it to open fullscreen at maximum screen brightness.
- Sliding sidebar with Refresh, Delivered parcels, Backend selector, Licenses,
  and Sign out.
- Encrypted at rest: `auth.json` and `parcels.json` are AES-256-GCM envelopes
  whose key lives in the Android Keystore (non-exportable, hardware-backed when
  available).
- `minSdk` 23, `targetSdk` 34.

## Requirements

- Alpine Linux (musl) x86_64, or a compatible environment.
- No root required.
- `curl` or `wget` for the online bootstrap.
- Internet access for the first bootstrap, or a local package mirror.

## Bootstrap

The bootstrap script provisions a complete, root-free Android build
environment in the current directory:

```sh
cd ~/openpostmobajl
./bootstrap.sh
```

It downloads Alpine packages, the Android SDK, and the ZXing core jar into
`./mirror/`, unpacks a musl-native OpenJDK 17, the Android SDK (build-tools 34,
platform android-34, platform-tools), and the gcompat layer used to run the
glibc-linked SDK tools. The ZXing jar is installed into `libs/`.

### Offline / local mirror

The online run creates a self-describing `./mirror/` directory. Copy it
anywhere (USB, CD, another machine) and use it offline:

```sh
./bootstrap.sh --mirror /path/to/mirror
```

`./mirror/` is intentionally **not** committed to git. The bootstrap script
downloads everything it needs, or uses the mirror you point it at.

Other options:

```sh
./bootstrap.sh --force     # re-provision the toolchain
./bootstrap.sh --help
```

## Build

### Debug APK

```sh
./build-debug.sh
# or the convenience wrapper:
./build.sh
```

Output:

```
out/openpostmobajl-debug.apk
```

The debug build is signed with `tools/debug.keystore` (generated on first use)
and is marked `android:debuggable="true"` via `aapt2 link --debug-mode`.

### Release APK

```sh
./build-release.sh
```

Output:

```
out/openpostmobajl-release.apk
```

The release build is **not** debuggable and is signed with
`tools/release.keystore`. If that keystore does not exist, the script generates
one on first use with these defaults:

```
store password: openpostmobajl
key alias:      openpostmobajl
key password:   openpostmobajl
```

Override them with environment variables:

```sh
RELEASE_STORE_PASSWORD='...' \
RELEASE_KEY_PASSWORD='...' \
RELEASE_KEY_ALIAS='...' \
./build-release.sh
```

For a real production release, replace `tools/release.keystore` with your own
keystore and keep it out of version control. `tools/release.keystore` is
git-ignored.

Version overrides:

```sh
VERSION_CODE=3 VERSION_NAME=1.2 ./build-release.sh
```

## Install / run

```sh
tools/gcompat.sh adb install -r out/openpostmobajl-debug.apk
tools/gcompat.sh adb shell am start -n com.openpostmobajl/.MainActivity
```

First launch opens the InPost login page. After the phone/SMS login, the app
intercepts the OAuth redirect, exchanges the code, saves the encrypted auth
state, and shows the parcel list.

## Security

- `auth.json` and `parcels.json` are encrypted with AES-256-GCM.
- The key is generated in the Android Keystore under the alias
  `openpostmobajl_data_key`; it is non-exportable and hardware-backed when the
  device supports it.
- Each write uses a fresh random IV and a 128-bit GCM tag.
- Only encrypted envelopes are read; plaintext JSON is not supported.
- If the keystore key is invalidated, the app falls back to a fresh login /
  empty cache instead of crashing.
- `android:allowBackup="false"` prevents the encrypted files from being backed
  up.
- The app is a POC: the debug APK is debuggable, and the default release
  keystore password is public. Replace the release keystore for production.

## Project layout

```
app/                                  Android app sources, manifest, resources
  src/com/openpostmobajl/             Java sources
libs/                                 ZXing core jar (downloaded by bootstrap)
tools/gcompat.sh                      run glibc SDK tools on musl
bootstrap.sh                          provision the toolchain / mirror
build-debug.sh                        debug APK
build-release.sh                      signed release APK
build.sh                              debug wrapper
docs/APP.md                           app details and usage
docs/VIEWS.md                         screen/view reference for contributors
docs/LANGUAGES.md                     translations and adding languages
docs/PARCEL-SCHEMA.md                 parcel response schema
docs/FLOW.md                          auth + parcel protocol reference
```

Generated and ignored: `out/`, `libs/`, `tools/root/`, `tools/android-sdk/`,
`tools/nsroot/`, `tools/debug.keystore`, `tools/release.keystore`, `mirror/`.

## Documentation

- [`docs/APP.md`](docs/APP.md) — app behavior, UI, cache, security, and
  troubleshooting.
- [`docs/VIEWS.md`](docs/VIEWS.md) — every screen, what it shows, where it
  lives in code, navigation, and how to add a new view.
- [`docs/LANGUAGES.md`](docs/LANGUAGES.md) — how to edit translations and how
  to add a new language.
- [`docs/PARCEL-SCHEMA.md`](docs/PARCEL-SCHEMA.md) — the v4 parcel response
  schema and status groups.
- [`docs/FLOW.md`](docs/FLOW.md) — the language-agnostic OAuth + parcel flow
  recovered from the InPost Mobile 4.22.1 APK.

## License

The project code, build scripts, documentation, and launcher icon are licensed
under the MIT License. See [LICENSE.md](LICENSE.md).

Third-party components and build tools are listed in
[THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).

The APK bundles both files as assets and shows them from the sidebar's
**Licenses** button.

## Notes

- The current PL parcel endpoint is `GET /v4/parcels/tracked`. The newer global
  CPS endpoint is disabled for the PL market (HTTP 501 `FEATURE_DISABLED`).
- The login page is protected by Cloudflare Turnstile; the app uses a real
  `WebView`, the same approach as the official app.
- The app uses the default WebView user-agent and the official app's HTTP
  headers (`X-App-Id`, `device-uid`, `User-Agent`, `Accept-Language`).
- Do not commit `auth.json`, `parcels.json`, `mirror/`, or keystores.
