# OpenPostMobajl — Auth + Parcel Flow (Reimplementation Spec)

This document describes the **working** flow implemented by the original Go
POC (`main.go`) and now by the OpenPostMobajl Android app. It is
language-agnostic: everything needed to recreate the flow in another language
is here (exact URLs, parameters, headers, state, fallbacks, and response
shapes).

It was recovered from:
- the release APK `InPost Mobile 4.22.1` (`base.apk`, version code `42201000`),
- the live `account.inpost-group.com` login SPA,
- live requests to `api-inmobile-pl.easypack24.net`.

> Use only with your own InPost account. Tokens are live credentials.

---

## 0. TL;DR

The current app login is **OAuth2/OIDC + PKCE** against
`https://account.inpost-group.com`. The phone/SMS step runs in a real browser
because the login page is protected by **Cloudflare Turnstile**. The program:

1. generates PKCE verifier/challenge + `state` + `nonce`,
2. builds and opens `https://account.inpost-group.com/oauth2/authorize?...`,
3. the user logs in with phone + SMS in the browser,
4. the user pastes the redirect URL (or just the `code`),
5. the program exchanges the code at `POST https://account.inpost-group.com/oauth2/token`,
6. it stores `authorizationData` and uses it for parcel requests,
7. on later runs it uses the saved access token, then refreshes with
   `grant_type=refresh_token`.

The legacy `POST /v1/account` + `/v1/account/verification` SMS API is **dead**
(404 on LTE, other networks, and VPN). Do not use it.

---

## 1. Constants

| Name | Value |
|---|---|
| OAuth issuer | `https://account.inpost-group.com` |
| Authorize endpoint | `https://account.inpost-group.com/oauth2/authorize` |
| Token endpoint | `https://account.inpost-group.com/oauth2/token` |
| Client ID | `inpost-mobile` |
| Redirect URI | `https://account.inpost-group.com/callback` |
| Scope | `openid` |
| Response type | `code` |
| Response mode | `query` |
| PKCE method | `S256` |
| App ID header | `pl.inpost.inmobile` |
| App name (UA) | `InPost-Mobile` |
| App version (UA) | `4.22.1` |
| App version code (UA) | `42201000` |
| Default language | `pl-PL` |
| Legacy API base | `https://api-inmobile-pl.easypack24.net` |
| Global API base | `https://api-inmobile-pl.easypack24.net/global` |

**Important empirical finding:** the working public token endpoint is
`https://account.inpost-group.com/oauth2/token`. The app's Retrofit annotation
mentions `global/oauth2/token`, but `https://account.inpost-group.com/global/oauth2/token`
returns the SPA's 404 page. Use `/oauth2/token`.

---

## 2. Why a browser is required (read before implementing)

The authorize URL redirects to a Next.js SPA at
`https://account.inpost-group.com/auth?lang=...&client_id=inpost-mobile`.

The SPA's login API (`/api/auth/onboarding/...`) is protected by:
- a Cloudflare bot challenge (403 for plain HTTP clients),
- **Cloudflare Turnstile** with site key `0x4AAAAAACMfDV9xlIDeRadM`.

A pure HTTP client cannot complete the phone/SMS step. The POC therefore uses
the browser for that step and only automates the standard OAuth code exchange
and everything after it.

Acceptable alternatives if a fully headless flow is required:
- drive a real Chromium (Playwright / chromedp) and capture the callback URL,
- use a captcha-solving service,
- do not attempt to fake Turnstile from plain HTTP.

The SPA's internal endpoints are documented in the appendix for reference, but
the POC does **not** call them.

---

## 3. Login flow (first run)

### 3.1 Generate PKCE values, state, and nonce

All values are **base64url without padding** (`RawURLEncoding`).

| Value | Generation | Example length |
|---|---|---|
| `code_verifier` | 32 cryptographically random bytes, base64url | 43 chars |
| `code_challenge` | `base64url(SHA-256(ASCII(code_verifier)))` | 43 chars |
| `state` | 16 random bytes, base64url | 22 chars |
| `nonce` | 16 random bytes, base64url | 22 chars |

Pseudocode:

```text
verifier  = b64url(random(32))
challenge = b64url(sha256(ascii(verifier)))
state     = b64url(random(16))
nonce     = b64url(random(16))
```

Keep `verifier`, `state`, and `nonce` in memory until the code exchange
completes.

### 3.2 Build the authorize URL

`GET https://account.inpost-group.com/oauth2/authorize` with these query
parameters (order does not matter; encode values):

| Parameter | Value |
|---|---|
| `response_type` | `code` |
| `client_id` | `inpost-mobile` |
| `redirect_uri` | `https://account.inpost-group.com/callback` |
| `scope` | `openid` |
| `code_challenge` | from 3.1 |
| `code_challenge_method` | `S256` |
| `state` | from 3.1 |
| `nonce` | from 3.1 |
| `response_mode` | `query` |
| `lang` | e.g. `pl-PL` (optional but sent by the app) |

Example (values shortened):

```text
https://account.inpost-group.com/oauth2/authorize
  ?client_id=inpost-mobile
  &code_challenge=WWpvdLDc3qAWjST7VezMtsKlJTBprucnoRaN6HyGkFU
  &code_challenge_method=S256
  &lang=pl-PL
  &nonce=OuXuKxqHa_Obv-ATqf-IsA
  &redirect_uri=https%3A%2F%2Faccount.inpost-group.com%2Fcallback
  &response_mode=query
  &response_type=code
  &scope=openid
  &state=4M6nDbyDkSSu0Huwz03Utg
```

The POC prints this URL and optionally opens it with the platform browser
(`xdg-open` / `open` / `rundll32`).

### 3.3 User logs in

The browser page asks for the phone number and the SMS code. After success it
redirects to:

```text
https://account.inpost-group.com/callback?code=<AUTH_CODE>&state=<STATE>
```

The user copies the **full redirect URL** (or just the `code`) and pastes it
into the program.

Parsing rules used by the POC:
- if the input contains `?` / `://` / starts with `/`, parse it as a URL and
  take `code` and `state` query parameters;
- otherwise treat the input as the bare code;
- if both `state` values are present and differ, abort;
- if no `code`, abort.

### 3.4 Exchange the code for tokens

`POST https://account.inpost-group.com/oauth2/token`

Headers:

| Header | Value |
|---|---|
| `Content-Type` | `application/x-www-form-urlencoded` |
| `Accept` | `application/json` |
| `User-Agent` | see §5.1 |
| `Accept-Language` | e.g. `pl-PL` |

Body (form-urlencoded, exact keys):

| Key | Value |
|---|---|
| `client_id` | `inpost-mobile` |
| `grant_type` | `authorization_code` |
| `redirect_uri` | `https://account.inpost-group.com/callback` |
| `code_verifier` | the verifier from 3.1 |
| `code` | the authorization code from 3.3 |

No `X-App-Id`, no `device-uid`, no `Authorization` header, no client secret.

Successful response is standard OIDC JSON:

```json
{
  "access_token": "<JWT or opaque>",
  "refresh_token": "<opaque>",
  "id_token": "<JWT>",
  "expires_in": 3600
}
```

A fake/expired code returns:

```json
{"error":"invalid_grant"}
```

with HTTP 400.

### 3.5 Build and store the auth state

The app's `LogInPostTokensMapper` does:

```text
authToken      = "Bearer " + access_token
refreshToken  = response.refresh_token
idToken       = response.id_token
expiresIn     = response.expires_in
tokenProvider = "GIP"
```

Also decode the access token's JWT payload if it is a JWT (optional but useful;
the app reads `jti`, `sub`, `phone`, `phone_prefix`, `exp`, `iat`, `market`).

Persist the state (§6) **before** making parcel calls, so a later failure does
not lose the tokens.

---

## 4. Token refresh (later runs)

If the saved access token is rejected (HTTP 401/403), call the same token
endpoint with the refresh grant.

`POST https://account.inpost-group.com/oauth2/token`

Headers: same as §3.4.

Body:

| Key | Value |
|---|---|
| `client_id` | `inpost-mobile` |
| `grant_type` | `refresh_token` |
| `refresh_token` | saved refresh token |

Response: same shape as §3.4.

Apply the response exactly like §3.5. If the response contains a non-empty
`refresh_token`, replace the stored one; otherwise keep the old one.

If refresh fails with 400/401, the refresh token is dead. Do **not** ask for the
phone number again if a state file exists; instruct the user to delete the file
and redo the browser login (this matches the app's re-auth behavior and the
POC's requirement).

---

## 5. Parcel listing

### 5.1 Common headers

The app's OkHttp interceptors add these to every parcel request:

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |
| `X-App-Id` | `pl.inpost.inmobile` |
| `device-uid` | a stable random UUID v4, generated once and persisted |
| `User-Agent` | see formula below |
| `Accept-Language` | e.g. `pl-PL` |
| `Authorization` | `Bearer <access_token>` |

`device-uid` generation (matches `DeviceUIDProvider`): generate
`UUID.randomUUID().toString()` once, persist it, reuse it forever.

`User-Agent` formula (matches `UserAgentFactory`):

```text
<appName>/<versionName>(<versionCode>) (Android <Build.VERSION.RELEASE>; <Build.MODEL>; <Build.BRAND> <Build.DEVICE>; <language>)
```

Example (a plausible device):

```text
InPost-Mobile/4.22.1(42201000) (Android 15; Pixel 7; google panther; pl)
```

Notes:
- `language` is the **language only** (`pl`), while `Accept-Language` is the
  full tag (`pl-PL`).
- `Build.MODEL` / `Build.BRAND` / `Build.DEVICE` / OS version come from the
  device in the real app. Any plausible Android values work for the POC.

### 5.2 Authorization header format

The stored `authToken` already includes `Bearer ` (from §3.5). Send it as-is:

```text
Authorization: Bearer <access_token>
```

If an implementation stores only the raw `access_token`, prefix it with
`Bearer `.

The POC tries, in order:
1. `raw` — send the stored token as-is,
2. `strip-bearer` — remove a leading `Bearer ` and send the raw token.

This is a safety net; with the flow above, `raw` is correct.

### 5.3 Backend selection

The app has two parcel backends behind a remote flag. The POC tries both, with
the **legacy** endpoint first.

#### Legacy endpoint

```text
GET https://api-inmobile-pl.easypack24.net/v4/parcels/tracked
```

Response (`TrackedParcelsResponse`):

```json
{
  "parcels": [
    {
      "shipmentNumber": "...",
      "shipmentType": "...",
      "status": "...",
      "statusGroup": "...",
      "events": [],
      "eventLog": [],
      "pickUpPoint": {"name": "..."},
      "...": "..."
    }
  ],
  "removedParcelList": ["..."],
  "updatedUntil": "...",
  "more": false
}
```

#### Global endpoint

```text
GET https://api-inmobile-pl.easypack24.net/global/cps/api/v1/parcels
    ?role=receiver
    &size=100
```

`role` is `receiver` for received parcels or `sender` for sent parcels.
`size=100` matches the app.

Response (`ParcelsResponse`):

```json
{
  "parcels": [
    {
      "parcelId": "...",
      "primaryParcelNumber": "...",
      "ownershipStatus": "OWN",
      "shortParcelNumber": "...",
      "pickUp": {"name": "..."},
      "...": "..."
    }
  ],
  "size": 100,
  "nextPage": {"pagingState": "..."}
}
```

If `nextPage.pagingState` is non-empty, repeat with:

```text
&pagingState=<url-encoded pagingState>
```

Stop when `nextPage` is null/empty. The app caps this at 100 pages.

### 5.4 Fallback and validation logic

For each auth-header mode (`raw`, then `strip-bearer`):

1. Try the legacy endpoint.
   - 2xx → success.
   - 404 or 5xx → try the next backend.
   - 401/403 → remember as an auth error and continue to the next mode.
2. Try the global endpoint (paginated).
   - 2xx → success.
   - 404 or 5xx → continue.
   - 401/403 → remember as an auth error.
3. If no mode/backend succeeded and an auth error was seen → treat the token as
   invalid and refresh.
4. Otherwise return the last error.

After a successful call, remember the working backend
(`legacy` / `global`) and header mode in the state file for faster later runs.

> `app-inmobile-pl.easypack24.net` is the app's Cloudflare-FQDN alternate host.
> It has no public A record in testing and is not usable; the POC keeps it as a
> harmless fallback. In practice `api-inmobile-pl.easypack24.net` is used.

---

## 6. State file (`auth.json`)

Plaintext, permissions `0600`. Fields used by the POC:

```json
{
  "authToken": "Bearer <access_token>",
  "accessToken": "<access_token>",
  "refreshToken": "<refresh_token>",
  "idToken": "<id_token>",
  "tokenProvider": "GIP",
  "expiresIn": 3600,

  "deviceUID": "11111111-1111-4111-8111-111111111111",
  "authBase": "https://api-inmobile-pl.easypack24.net",
  "authHeaderMode": "raw",
  "parcelBackend": "legacy",

  "jwtClaims": {
    "jti": "...",
    "sub": "...",
    "phone": "...",
    "phone_prefix": "+48",
    "exp": 0,
    "iat": 0,
    "market": "PL"
  },

  "oauthIssuer": "https://account.inpost-group.com",
  "oauthClientId": "inpost-mobile",
  "oauthRedirectUri": "https://account.inpost-group.com/callback",
  "oauthVerifier": "<kept for reference>",
  "oauthState": "<kept for reference>",
  "oauthNonce": "<kept for reference>",
  "authorizeUrl": "https://account.inpost-group.com/oauth2/authorize?...",
  "tokenResponse": {"access_token": "...", "refresh_token": "...", "id_token": "...", "expires_in": 3600},

  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

Minimum fields needed for operation:
- `authToken` (or `accessToken`) and `refreshToken`,
- `deviceUID`,
- optionally `authHeaderMode` / `parcelBackend`.

The POC also writes `parcels.json`:

```json
{
  "backend": "legacy",
  "fetchedAt": "2024-01-01T00:00:00Z",
  "count": 3,
  "response": { "...raw server response..." }
}
```

---

## 7. Run-state machine

```text
START
  |
  +-- does auth.json exist?
        |
        no --> browser OAuth login (section 3)
        |      -> save auth.json
        |      -> fetch parcels (section 5)
        |
        yes --> try saved authToken on parcel endpoints
                |
                +-- success --> list parcels
                |
                +-- 401/403 --> POST /oauth2/token (refresh_token)
                                |
                                +-- success --> save auth.json
                                |              --> fetch parcels
                                |
                                +-- failure --> exit; tell user to delete auth.json
```

Important: if `auth.json` exists, never ask for the phone number. Only a
refresh failure should require a new browser login (by deleting the file).

---

## 8. Language-agnostic pseudocode

```text
function main():
    state = load_or_init(auth.json)
    ensure state.deviceUID else state.deviceUID = uuid4()

    if file_exists(auth.json):
        result = ensure_auth(state)
        if result is error:
            fail("delete auth.json and do a new browser login")
    else:
        oauth_login(state)
        save(auth.json, state)
        result = fetch_parcels(state)

    save(auth.json, state)
    save(parcels.json, result)
    print_parcels(result)


function oauth_login(state):
    verifier  = b64url(random(32))
    challenge = b64url(sha256(ascii(verifier)))
    stateVal  = b64url(random(16))
    nonce     = b64url(random(16))

    state.oauthVerifier = verifier
    state.oauthState    = stateVal
    state.oauthNonce    = nonce

    url = "https://account.inpost-group.com/oauth2/authorize"
        + "?response_type=code"
        + "&client_id=inpost-mobile"
        + "&redirect_uri=" + urlencode("https://account.inpost-group.com/callback")
        + "&scope=openid"
        + "&code_challenge=" + challenge
        + "&code_challenge_method=S256"
        + "&state=" + stateVal
        + "&nonce=" + nonce
        + "&response_mode=query"
        + "&lang=" + urlencode(lang)

    print(url)
    open_browser_if_possible(url)

    input = prompt("Redirect URL or code:")
    code = parse_code(input)
    if state parameter present and != stateVal: fail
    if code empty: fail

    response = http_post_form(
        "https://account.inpost-group.com/oauth2/token",
        headers = {
            "Content-Type":  "application/x-www-form-urlencoded",
            "Accept":        "application/json",
            "User-Agent":    user_agent(),
            "Accept-Language": lang,
        },
        form = {
            "client_id":     "inpost-mobile",
            "grant_type":    "authorization_code",
            "redirect_uri":  "https://account.inpost-group.com/callback",
            "code_verifier": verifier,
            "code":          code,
        },
    )
    apply_tokens(state, response)


function apply_tokens(state, response):
    state.accessToken   = response.access_token
    state.authToken     = "Bearer " + response.access_token
    if response.refresh_token: state.refreshToken = response.refresh_token
    state.idToken       = response.id_token
    state.expiresIn     = response.expires_in
    state.tokenProvider = "GIP"
    state.authHeaderMode = "raw"
    state.jwtClaims     = decode_jwt(response.access_token)   # optional
    state.updatedAt     = now()


function ensure_auth(state):
    if state.authToken != "":
        if parcels_request(state) succeeds: return result
    if state.refreshToken == "": return error
    refresh_response = http_post_form(
        "https://account.inpost-group.com/oauth2/token",
        headers = {same as above},
        form = {
            "client_id":     "inpost-mobile",
            "grant_type":    "refresh_token",
            "refresh_token": state.refreshToken,
        },
    )
    apply_tokens(state, refresh_response)
    save(auth.json, state)
    return parcels_request(state)


function parcel_headers(state):
    return {
        "Content-Type":      "application/json",
        "Accept":            "application/json",
        "X-App-Id":          "pl.inpost.inmobile",
        "device-uid":        state.deviceUID,
        "User-Agent":        user_agent(),
        "Accept-Language":   lang,
        "Authorization":     state.authToken,   # already "Bearer ..."
    }


function fetch_parcels(state):
    for mode in ["raw", "strip-bearer"]:
        header = auth_header(state.authToken, mode)
        for backend in ["legacy", "global"]:
            try:
                if backend == "legacy":
                    return GET("https://api-inmobile-pl.easypack24.net/v4/parcels/tracked", headers=parcel_headers_with(header))
                else:
                    return GET_global_pages(state, header)
            except e:
                if e.status in (404, 5xx): continue
                if e.status in (401, 403): remember auth error; continue
                return e
    if auth_error: return AUTH_ERROR
    return last_error
```

`GET_global_pages`:

```text
pagingState = ""
all = []
for page in 1..100:
    url = "https://api-inmobile-pl.easypack24.net/global/cps/api/v1/parcels"
        + "?role=receiver"
        + "&size=100"
        + (pagingState ? "&pagingState=" + urlencode(pagingState) : "")
    resp = GET(url, headers=parcel_headers_with(header))
    all += resp.parcels
    if resp.nextPage is null or resp.nextPage.pagingState == "": break
    pagingState = resp.nextPage.pagingState
return all
```

---

## 9. Error handling summary

| Situation | Behavior |
|---|---|
| `auth.json` absent | Run browser OAuth login |
| `auth.json` present, token works | List parcels, no prompt |
| `auth.json` present, 401/403 | Refresh via `grant_type=refresh_token` |
| Refresh succeeds | Save new tokens, list parcels |
| Refresh fails (400/401) | Exit; tell user to delete `auth.json` |
| Legacy 404/5xx | Try global backend |
| Global 404/5xx | Try next header mode |
| All 401/403 | Refresh |
| Browser login cancelled | Exit; no file written |
| `state` mismatch | Abort the exchange |

Never log the full tokens in production. The POC saves them only because it is
a local proof of concept; the file is created with mode `0600`.

---

## 10. Reference examples

Authorize URL (shortened values):

```text
https://account.inpost-group.com/oauth2/authorize
  ?client_id=inpost-mobile
  &code_challenge=WWpvdLDc3qAWjST7VezMtsKlJTBprucnoRaN6HyGkFU
  &code_challenge_method=S256
  &lang=pl-PL
  &nonce=OuXuKxqHa_Obv-ATqf-IsA
  &redirect_uri=https%3A%2F%2Faccount.inpost-group.com%2Fcallback
  &response_mode=query
  &response_type=code
  &scope=openid
  &state=4M6nDbyDkSSu0Huwz03Utg
```

Code exchange request:

```http
POST /oauth2/token HTTP/2
Host: account.inpost-group.com
Content-Type: application/x-www-form-urlencoded
Accept: application/json
User-Agent: InPost-Mobile/4.22.1(42201000) (Android 15; Pixel 7; google panther; pl)
Accept-Language: pl-PL

client_id=inpost-mobile
&grant_type=authorization_code
&redirect_uri=https%3A%2F%2Faccount.inpost-group.com%2Fcallback
&code_verifier=<43-char-verifier>
&code=<authorization-code>
```

Successful token response:

```json
{"access_token":"...","refresh_token":"...","id_token":"...","expires_in":3600}
```

Legacy parcel request:

```http
GET /v4/parcels/tracked HTTP/2
Host: api-inmobile-pl.easypack24.net
Content-Type: application/json
Accept: application/json
X-App-Id: pl.inpost.inmobile
device-uid: 11111111-1111-4111-8111-111111111111
User-Agent: InPost-Mobile/4.22.1(42201000) (Android 15; Pixel 7; google panther; pl)
Accept-Language: pl-PL
Authorization: Bearer <access_token>
```

Global parcel request:

```http
GET /global/cps/api/v1/parcels?role=receiver&size=100 HTTP/2
Host: api-inmobile-pl.easypack24.net
Content-Type: application/json
Accept: application/json
X-App-Id: pl.inpost.inmobile
device-uid: 11111111-1111-4111-8111-111111111111
User-Agent: InPost-Mobile/4.22.1(42201000) (Android 15; Pixel 7; google panther; pl)
Accept-Language: pl-PL
Authorization: Bearer <access_token>
```

---

## 11. Verification checklist for a reimplementation

1. Generate a verifier and challenge; the challenge must be 43-char base64url.
2. Open the authorize URL in a browser; you should get the InPost login page,
   not an error.
3. After login, the redirect contains `code` and the original `state`.
4. Exchange the code; you must get JSON with `access_token` (not an HTML page).
5. `GET /v4/parcels/tracked` with `Authorization: Bearer <access_token>` must
   return 2xx and a `parcels` array (empty is fine).
6. If it returns 401, refresh with `grant_type=refresh_token` and retry.
7. On the second run, no phone prompt; the saved token or refresh must work.
8. The global endpoint must be tried only after the legacy one fails, and its
   `nextPage.pagingState` must be followed.

---

## Appendix A — SPA login API (observed, not used by the POC)

These were recovered from the `account.inpost-group.com` Next.js chunks. They
are listed for completeness; using them requires solving Cloudflare Turnstile
and the bot challenge, which is why the POC uses a real browser instead.

Base: `https://account.inpost-group.com/api/auth/onboarding`

Browser-like headers are required (plain clients get a Cloudflare 403):
`User-Agent` (Chrome), `Accept: application/json`, `Content-Type: application/json`,
`X-Requested-With: XMLHttpRequest`, `Origin`, `Referer`, `Sec-Fetch-*`.
CSRF:
- `GET /api/auth/csrf` → 204, sets an `XSRF-TOKEN` cookie.
- Send `X-XSRF-TOKEN: <url-decoded cookie>` on every POST.

Observed steps:

| Step | Method/path | Body |
|---|---|---|
| Current step | `GET /steps` | — |
| Start / sign-in identity | `POST /steps/startOnboarding` | `{"identity":"+48512345678","openEmailButtonVisible":true,"sourceApp":"inpost","captchaToken":"<turnstile>"}` |
| Choose phone | `POST /steps/chooseAlternative` | `{"alternative":"PHONE_NUMBER"}` |
| Submit phone | `POST /steps/phoneNumber` | `{"phoneNumber":"+48512345678"}` + `Accept-Language` |
| Submit SMS code | `POST /steps/phoneVerificationCode` | `{"code":"123456"}` |
| Resend code | `POST /steps/resend` | optional body |
| Back | `POST /steps/back` | — |

The `steps` response includes `step`, `prefixes`, `allowedOptions`,
`onboardingFlow`, `nonce`, and (when done) `redirectUrl` / `personId`.
`pending` steps include `PROVIDE_START_INFORMATION_FOR_ONBOARD`,
`PROVIDE_PHONE_NUMBER_FOR_LOGIN`, `PROVIDE_PHONE_CODE`, etc. The final
`redirectUrl` is the OAuth callback URL containing `code` and `state`.

Turnstile:
- site key: `0x4AAAAAACMfDV9xlIDeRadM`
- the SPA refuses to submit without a token when the key is configured.

---

## Appendix B — Why the legacy SMS API is not used

The APK still contains `RegistrationApi` (`POST /v1/account`,
`/v1/account/verification`) and `RefreshTokenApi`
(`/v1/sendSMSCode`, `/v1/reconfirmSMSCode`), but these routes 404 on the live
`api-inmobile-pl.easypack24.net` host from LTE, other networks, and VPN. They
are a retired flow. The current app uses the OAuth page described above.

The old `global/oauth2/token` path from the app's Retrofit interface also 404s
on the public host; use `/oauth2/token`.
