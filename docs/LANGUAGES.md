# Languages and translations

The app ships with **English** and **Polski (Polish)**. The default language is
**Polish**.

Everything user-facing goes through Android string resources, so a translation
is just a text file. The selected language is applied per-app (it does not
change the system language) and takes effect immediately when you pick it in
**Settings**.

---

## Where the text lives

| Language | File |
|---|---|
| English (default resources) | `app/res/values/strings.xml` |
| Polish | `app/res/values-pl/strings.xml` |

The matching between the picker and the files is in
`app/src/com/openpostmobajl/LocaleHelper.java`:

```java
static final String DEFAULT_LANGUAGE = "pl";
static final String[] CODES  = {"en", "pl"};
static final String[] LABELS = {"English", "Polski"};
```

`CODES` are Android resource qualifiers (`values-en`, `values-pl`, …) and
`LABELS` are what the picker shows. The two arrays must stay in the same order.

The default `values/strings.xml` is the English set and is also the fallback:
if a key is missing from a translation, the default value is used.

---

## Changing an existing entry

1. Open the language file, e.g. `app/res/values-pl/strings.xml`.
2. Find the `<string name="…">` entry and edit only the text between the tags.
   **Never change the `name`** — that is the key the code looks up.
3. Keep every placeholder (`%1$s`, `%2$d`, …) and every entry
   (`<string>`/`<plurals>`) in the file. A missing entry falls back to the
   default language instead of crashing.
4. Rebuild:

   ```sh
   ./build-debug.sh     # or ./build-release.sh
   ```

Example — changing the "Refresh" button:

```xml
<!-- app/res/values-pl/strings.xml -->
<string name="common_refresh">Odśwież</string>
```

---

## Adding a new language

Adding a language does not require any Java logic — only a resource folder and
one line in the registry.

1. **Pick the ISO 639-1 code**, e.g. `de` (German), `fr` (French), `cs` (Czech).

2. **Create the resource folder** `app/res/values-<code>/` and add a
   `strings.xml` inside it. The easiest way is to copy the default file and
   translate the values:

   ```sh
   mkdir -p app/res/values-de
   cp app/res/values/strings.xml app/res/values-de/strings.xml
   # then translate the values in app/res/values-de/strings.xml
   ```

3. **Register it** in `LocaleHelper.java` (keep the order consistent):

   ```java
   static final String[] CODES  = {"en", "pl", "de"};
   static final String[] LABELS = {"English", "Polski", "Deutsch"};
   ```

4. **Rebuild** (`./build-debug.sh`). The new language appears in
   Settings → Language and can be selected.

That's it. `aapt2` picks up every `values-<code>/` folder automatically; there
is nothing to add to the build scripts or the manifest.

---

## Unicode and special characters

- Every `strings.xml` is **UTF-8**. Type accented letters directly:

  ```xml
  <string name="common_back">← Wstecz</string>
  <string name="delivered_empty">Brak odebranych paczek.</string>
  <string name="main_login_prompt">Zaloguj się numerem telefonu…</string>
  ```

- XML special characters must be escaped: `&amp;` for `&`, `&lt;` for `<`,
  `&gt;` for `>`.
- An **unescaped apostrophe** (`'`) breaks the build; write `\'` or wrap the
  value in double quotes, e.g. `"Don't"` → `Don\'t`.

---

## Placeholders and plurals

- Positional placeholders are kept as-is and in the same order:

  ```xml
  <string name="detail_status">Status: %1$s (%2$s)</string>
  ```

- Counts use `<plurals>`. Polish needs `one`/`few`/`many`/`other`; English
  needs `one`/`other`. Translate each quantity but keep `%d`:

  ```xml
  <plurals name="multi_parcels">
      <item quantity="one">%d paczka</item>
      <item quantity="few">%d paczki</item>
      <item quantity="many">%d paczek</item>
      <item quantity="other">%d paczki</item>
  </plurals>
  ```

---

## How the language is applied

- `LocaleHelper.getLanguage()` reads the choice from `SharedPreferences`
  (`openpostmobajl_settings`), defaulting to Polish.
- `BaseActivity.attachBaseContext()` calls `AppSettings.wrap()`, which applies
  the chosen `Locale` (and, in simplified mode, the larger font scale), so every
  `getString(R.string…)` returns the right language.
- `SettingsActivity`'s language `Spinner` saves the choice and calls
  `recreate()`, so the Settings screen updates at once. `BaseActivity.onResume()`
  recreates any other activity (e.g. `MainActivity`) the next time it comes to
  the foreground if the language changed while it was in the back stack.

All activities extend `BaseActivity`; if you add a new screen, extend it too so
it is translated and reacts to language changes.

---

## Key reference

Keys are grouped by screen in the resource files:

| Prefix | Screen |
|---|---|
| `common_*` | labels shared across screens (back, refresh, licenses…) |
| `main_*` | `MainActivity` (status line, login prompt) |
| `detail_*` | `ParcelDetailActivity` |
| `multi_*` | `MultiCompartmentActivity` and group rows |
| `expires_*` | expiry countdown (`ParcelUi.expiryText`) |
| `delivered_*` | `DeliveredParcelsActivity` |
| `licenses_*` | `LicensesActivity` |
| `status_*` | translated package statuses/groups for simplified mode |
| `settings_*` | `SettingsActivity` |
| `backend_*` | backend picker labels (`auto`/`v4`/`global`) |
