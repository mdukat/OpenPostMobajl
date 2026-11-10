# Views and UI

Reference for the app's screen layer: every view, what it shows, where it lives
in code, how screens navigate, and the conventions to follow when changing an
existing view or adding a new one.

This app has **no XML layouts, no AndroidX, no Compose and no Kotlin**. Every
screen is built programmatically in an `android.app.Activity` subclass using
plain `android.widget` views. Read [Conventions](#conventions) and
[Adding a new view](#adding-a-new-view) before writing code.

Line numbers below are approximate and will drift; method names are the stable
reference. Version noted here: **1.1** (`versionCode` 2).

---

## Screen map

```
MainActivity
├─ (no credentials) login WebView overlay, replaces the list until callback
└─ (credentials) main parcel list + sidebar
   ├─ list row (single)   ──► ParcelDetailActivity ──► QrFullscreenActivity
   ├─ list row (group)    ──► MultiCompartmentActivity
   │                              ├─ QR tap        ──► QrFullscreenActivity
   │                              └─ contained row ──► ParcelDetailActivity
   ├─ sidebar: Delivered parcels ──► DeliveredParcelsActivity ──► ParcelDetailActivity
   ├─ sidebar: Licenses          ──► LicensesActivity
   └─ sidebar: Refresh / Backend / Sign out (stay in MainActivity)
```

There is no separate login activity: `MainActivity` hosts the OAuth `WebView`
as an overlay on top of the list.

---

## Activity inventory

| Activity | File | Purpose | Intent extras |
|---|---|---|---|
| `MainActivity` | `MainActivity.java` | Launcher. Login WebView + parcel list + sidebar. | — |
| `ParcelDetailActivity` | `ParcelDetailActivity.java` | One parcel: QR, summary, full JSON. | `parcel` = single JSON object string |
| `MultiCompartmentActivity` | `MultiCompartmentActivity.java` | A "MULTISKRYTKA" group: one QR + member list. | `parcels` = JSON array string, `uuid` = group uuid |
| `DeliveredParcelsActivity` | `DeliveredParcelsActivity.java` | Delivered parcels from the cache only (no network). | — |
| `QrFullscreenActivity` | `QrFullscreenActivity.java` | Fullscreen QR at max brightness; tap to close. | `qr` = payload string |
| `LicensesActivity` | `LicensesActivity.java` | Bundled `LICENSE.md` + `THIRD-PARTY-LICENSES.md`. | — |

All are registered in `app/AndroidManifest.xml` with
`android:exported="false"` except `MainActivity` (launcher), and all set
`android:configChanges="orientation|screenSize|keyboardHidden"`.
`QrFullscreenActivity` additionally uses
`@android:style/Theme.Material.Light.NoActionBar`.

---

## Data entering the view layer

Views never fetch data themselves except `MainActivity` and
`DeliveredParcelsActivity`; they communicate by passing JSON **strings** as
Intent extras.

- **Network → `MainActivity`.** `ApiClient.ensureAuth(state)` returns an
  `ApiClient.ParcelResult` (`backend`, `parcels` `JSONArray`, `raw`).
  `MainActivity.loadParcels()` runs it on a background thread and posts
  `render(res, false)` to the UI thread. On success the result is written to
  the encrypted cache via `ParcelCache.save`.
- **Cache → UI.** `ParcelCache.load(ctx)` returns the last
  `ApiClient.ParcelResult` (or `null`). `MainActivity.onCreate` renders it
  immediately with `render(cached, true)`, then refreshes from the network.
  `DeliveredParcelsActivity` reads the cache only.
- **List → detail.** A list row serializes its parcel with
  `JSONObject.toString()` into the `parcel` extra; a group serializes its
  member array into the `parcels` extra.
- **QR.** Only the `qrCode` field payload is passed (`qr` extra). The bitmap is
  always generated locally by `QrCode.generate` (ZXing core); never fetched.

---

## Screens

### `MainActivity`

File: `app/src/com/openpostmobajl/MainActivity.java`

Central activity. Responsibilities:

- Loads `AuthState` from `AuthStore` and ensures `deviceUID` / `createdAt`.
- If credentials exist → loads the cached list, renders it, then refreshes.
  If not → `startLogin()`.
- Hosts the sidebar and the login `WebView`.

Key methods:

| Method | Role |
|---|---|
| `onCreate` | loads auth, builds the panel, chooses login vs. list |
| `buildPanel` | builds **all** chrome: main panel, list, scrim, sidebar |
| `openSidebar` / `closeSidebar` | animate the sidebar + scrim; `onBackPressed` closes it first |
| `startLogin` / `showWebView` / `handleUrl` / `exchangeCode` | OAuth flow; intercepts the redirect, exchanges the code, saves auth, then loads parcels |
| `loadParcels` | background fetch + refresh, posts to `render`, saves cache |
| `render` | **the list renderer** (see below) |
| `updateCountdowns` / `syncCountdownTimer` / `onResume` / `onPause` | live expiry countdown: a 1 s `Handler` tick while the main view is resumed |
| `cycleBackend` | cycles `auto → v4 → global`, saves, reloads |
| `signOut` | deletes auth + cache, clears cookies, restarts login |
| `showError` | writes `Error: …` into the status line |
| `nowIso` | static ISO-8601 UTC helper reused by `ApiClient`/`ParcelCache` |
| `dp` | local dp→px helper (duplicated in every activity) |

Main panel layout (top to bottom): burger button, `status` `TextView`, a
`ScrollView` containing the `list` `LinearLayout`. Sidebar buttons: **Refresh**,
**Delivered parcels**, **Backend: …**, **Licenses**, **Sign out**.

`render(res, cached)` is the single place that turns data into rows:

1. `ParcelUi.sorted(res.parcels, false)` — non-delivered parcels in rank order.
2. `ParcelUi.grouped(sorted)` — collapses ready-to-pickup multi-compartment
   parcels into `ParcelEntry` groups.
3. The `status` line reports the **physical** parcel count (`sorted.size()`).
4. For each `ParcelEntry`: group rows use `ParcelUi.multiCompartmentRowText`
   and open `MultiCompartmentActivity`; single rows use `ParcelUi.rowText` and
   open `ParcelDetailActivity`.
5. Ready-to-pickup rows (single or group) append a second line with the expiry
   countdown from `ParcelUi.expiryText`. The row's base text, view and expiry
   are kept in parallel lists so `updateCountdowns` can refresh only the
   countdown line every second while the activity is resumed.

When there are no parcels it renders a single "No parcels found." row. Rows are
`TextView`s with a 1 px `View` divider.

### `ParcelDetailActivity`

File: `app/src/com/openpostmobajl/ParcelDetailActivity.java`

Shows one parcel. In `onCreate`:

- Top bar: `← Back` + `Parcel <last6>`.
- QR block: rendered when `statusGroup` is `DELIVERED`, or the parcel is
  ready-to-pickup (`statusGroup` `TO_PICKUP` / `status` `READY_TO_PICKUP`),
  **and** `qrCode` is non-empty. Tapping opens `QrFullscreenActivity`.
- Summary: shipment number, status + status group, pickup point, sender.
- Full JSON: `parcel.toString(2)` in a monospace, selectable `TextView`.

Parses the `parcel` extra with `new JSONObject(...)`; malformed input shows
"Could not read parcel data." instead of crashing.

### `MultiCompartmentActivity`

File: `app/src/com/openpostmobajl/MultiCompartmentActivity.java`

Shows a logical multi-compartment group. Parses `parcels` (JSON array, already
in scan order) and `uuid`. In `onCreate`:

- Top bar: `← Back` + `ParcelUi.multiCompartmentName(uuid)`
  (`MULTISKRYTKA <uuid-section>`).
- One QR code taken from the **first** parcel in the array (`optJSONObject(0)`),
  rendered only if that parcel has a non-empty `qrCode`; tap opens
  `QrFullscreenActivity`.
- A "N parcels" label.
- Each contained parcel as a `ParcelUi.rowText` row; tapping opens
  `ParcelDetailActivity` with that parcel's JSON.

Grouping rules live in `ParcelUi` (see below), not here. This activity only
renders whatever group it is given.

### `DeliveredParcelsActivity`

File: `app/src/com/openpostmobajl/DeliveredParcelsActivity.java`

Reads `ParcelCache.load(this)` **synchronously on the UI thread** (no network),
filters with `ParcelUi.sorted(res.parcels, true)` (which sorts delivered
parcels by `storedDate`, newest first), and renders `rowText` rows that open
`ParcelDetailActivity`. Shows "No delivered parcels." when empty.
It does **not** group multi-compartment parcels.

If you add heavier work here, move the cache load off the UI thread — the
decrypt is synchronous today.

### `QrFullscreenActivity`

File: `app/src/com/openpostmobajl/QrFullscreenActivity.java`

Renders the `qr` extra at 1024 px, sets window brightness to `1.0f`, keeps the
screen on, hides the system bars (immersive sticky), and finishes on any tap.

### `LicensesActivity`

File: `app/src/com/openpostmobajl/LicensesActivity.java`

Reads the `LICENSE.md` and `THIRD-PARTY-LICENSES.md` assets (copied into the APK
by the build scripts) with `readAsset` and shows them in one monospace,
selectable `TextView`.

---

## Shared helpers

### `ParcelUi` — `app/src/com/openpostmobajl/ParcelUi.java`

All list/formatting logic is centralized here so it can be reused by every
view. Static, package-private, no state.

| Method | Purpose |
|---|---|
| `rowText(p)` | single-parcel row: `last6 · statusGroup · point · sender ›` |
| `multiCompartmentRowText(e)` | group row: `MULTISKRYTKA <section> · N parcels · point · sender ›` |
| `last6(p)` | last 6 chars of `shipmentNumber` / `primaryParcelNumber` |
| `pointName(p)` / `senderName(p)` | tolerate missing `pickUpPoint`/`pickUp`/`sender` |
| `isDelivered(p)` | `DELIVERED` in `statusGroup` **or** `status` |
| `isReadyToPickup(p)` | `TO_PICKUP`/`AWAITING_COLLECTION`/`READY_TO_COLLECT` groups, or `READY_TO_PICKUP`/etc. statuses |
| `multiCompartmentUuid(p)` | uuid when ready-to-pickup **and** `multiCompartment.presentation == true`, else `null` |
| `multiCompartmentName(uuid)` | `MULTISKRYTKA <part before first '-'>` |
| `grouped(sorted)` | collapses duplicate presentable uuids into `ParcelEntry` groups |
| `sorted(parcels, deliveredOnly)` | filters delivered/undelivered; non-delivered by `rank`, delivered by `storedDate` newest first (missing last) |
| `rank(p)` | 0 ready-to-collect, 1 in transit, 2 to send, 3 delivered, 4 other |
| `expiryMillis(p)` | epoch millis of `expiryDate` for a ready-to-pickup parcel, else `0` |
| `storedMillis(p)` | epoch millis of `storedDate`, else `0` |
| `parseIsoMillis(s)` | tolerant ISO-8601 parser (`Z` or `±HH:MM`, optional millis) |
| `expiryText(millis)` | `Expires <local time> · in <Xd Yh…>` or `· expired` |

`grouped` only groups a uuid seen on **more than one** parcel, preserves scan
order, and makes the first member the group representative.

### `ParcelEntry` — `app/src/com/openpostmobajl/ParcelEntry.java`

One list row: `first` (representative parcel), `parcels` (all members),
`multiUuid` (`null` for a normal row), `isMulti()`, and `expiryMillis()` (the
earliest valid expiry among members, or `0`). Constructed via
`ParcelEntry.single(parcel)` or `ParcelEntry.group(members, uuid)`.

### `QrCode` — `app/src/com/openpostmobajl/QrCode.java`

`QrCode.generate(content, size)` builds an ARGB bitmap with ZXing core, UTF-8
and margin 2. Returns `null` on failure; callers must null-check.

---

## Conventions

- **Programmatic UI only.** Build a `LinearLayout` root in `onCreate`, add
  children with `LinearLayout.LayoutParams`, and call `setContentView(root)`.
  There are no layout XML files to inflate.
- **No new dependencies.** Only the ZXing core jar is linked. Do not introduce
  Gradle, AndroidX, Compose, Kotlin, or extra jars without updating the build
  scripts and `bootstrap.sh`.
- **Top bar pattern.** Each child screen starts with a horizontal row holding a
  `← Back` `Button` (`finish()` on click) and a title `TextView` with weight 1.
- **Text/colors.** Colors are literal `Color.rgb(...)`; sizes use
  `TypedValue.COMPLEX_UNIT_SP`; spacing uses each class's private `dp()` helper.
- **Threading.** Blocking work runs on a bare `new Thread(...)` and results are
  posted back with `runOnUiThread(...)`. There is no `AsyncTask`, no
  `ViewModel`, no reactive state.
- **Java 8, anonymous inner classes.** The code uses `new View.OnClickListener()`
  style and `final` locals captured by listeners; match that style.
- **Config changes.** Activities declare `configChanges` for
  orientation/screen-size/keyboard, so `onCreate` is not re-run on rotation and
  there is no re-render path. Rebuild state in `onCreate` only.
- **Live updates.** The only periodic UI update is the expiry countdown in
  `MainActivity` (a `Handler` posting every 1 s). Start/stop it with
  `onResume`/`onPause`; never leave timers running in the background.
- **Defensive parsing.** Views are built from JSON that may be missing fields;
  use `optString`/`optJSONObject`/`optBoolean` and null-check before use.

---

## Adding a new view

1. Create `app/src/com/openpostmobajl/XxxActivity.java` extending
   `android.app.Activity`.
2. Build the UI in `onCreate` (root `LinearLayout`, optional `ScrollView`,
   `setContentView`). Add a private `dp()` helper and follow the top-bar
   pattern above.
3. Read any input from `getIntent().getStringExtra(...)`. If you pass a parcel,
   use `JSONObject.toString()` / `new JSONArray(...)`, matching the existing
   extras (`parcel`, `parcels`, `uuid`, `qr`).
4. Register it in `app/AndroidManifest.xml`:
   ```xml
   <activity
       android:name=".XxxActivity"
       android:exported="false"
       android:configChanges="orientation|screenSize|keyboardHidden" />
   ```
5. Add a way to reach it:
   - sidebar entry → add a `Button` in `MainActivity.buildPanel()`;
   - list row → branch in `MainActivity.render()` (or another list activity)
     when constructing the row's `OnClickListener`.
6. Reuse `ParcelUi` for text/sorting/grouping and `QrCode.generate` for QR
   codes instead of re-implementing them.
7. Build and verify:
   ```sh
   ./build-debug.sh
   ./build-release.sh
   ```
   There is no Gradle step. The build compiles resources, Java, dexes, aligns,
   and signs.

### Where to change an existing behavior

| If you want to change… | Edit |
|---|---|
| which parcels appear in the main list | `ParcelUi.sorted` / `ParcelUi.isDelivered` |
| list ordering | `ParcelUi.rank` |
| single-row text | `ParcelUi.rowText` |
| multi-compartment grouping or name | `ParcelUi.grouped` / `multiCompartmentUuid` / `multiCompartmentName` / `multiCompartmentRowText` |
| expiry countdown format or parsing | `ParcelUi.expiryMillis` / `parseIsoMillis` / `expiryText` |
| countdown refresh / lifecycle | `MainActivity` `countdownTick` / `updateCountdowns` / `onResume` / `onPause` |
| what a detail screen shows | `ParcelDetailActivity.onCreate` |
| what a group screen shows | `MultiCompartmentActivity.onCreate` |
| when a QR is shown | `ParcelDetailActivity` / `MultiCompartmentActivity` QR blocks |
| delivered list contents | `DeliveredParcelsActivity.onCreate` |
| sidebar entries | `MainActivity.buildPanel` |
| list rendering / navigation | `MainActivity.render` |
| app version | `app/AndroidManifest.xml` + `VERSION_CODE`/`VERSION_NAME` in both build scripts (currently 1.1 / 2) |
| theme of a screen | its `<activity>` in `app/AndroidManifest.xml` |

---

## Gotchas

- **`Pkce.APP_VERSION` is not the app version.** `4.22.1` / `42201000` are the
  InPost-Mobile User-Agent identity used for API requests. Never bump them when
  releasing our app; bump the manifest and build scripts instead.
- **Delivered parcels are excluded from the main list** (`sorted(..., false)`).
  They appear only in `DeliveredParcelsActivity`, which reads the cache.
- **Grouping is main-list only** and ready-to-pickup only. Delivered and
  in-transit parcels are never grouped.
- **`expiryDate` is optional.** Only ready-to-pickup entries with a valid date
  get a countdown; a missing or unparseable date is silently ignored.
- **QR payloads never come from the network bitmap**; only the `qrCode` string
  is used and the image is generated locally.
- **Intent extra names are part of the contract** between screens:
  `parcel`, `parcels`, `uuid`, `qr`. Reusing a name for different content will
  break existing callers.
- **`DeliveredParcelsActivity` decrypts on the UI thread.** Keep it cheap or
  move it to a background thread if you extend it.
- **No strings resource for UI labels.** User-facing text is hard-coded in
  Java; `app/res/values/strings.xml` only holds the app name.
- **`minSdk` is 23, target 34.** Avoid APIs newer than 23 without a guard.

See also [`APP.md`](APP.md) for the build, cache, and backend behavior, and
[`PARCEL-SCHEMA.md`](PARCEL-SCHEMA.md) for the JSON fields these views read.
