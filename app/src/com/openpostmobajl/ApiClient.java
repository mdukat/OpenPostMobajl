package com.openpostmobajl;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP layer: OAuth code exchange, refresh, and parcel listing.
 * Mirrors the Go POC's ApiClient.
 */
final class ApiClient {

    static final String API_BASE = "https://api-inmobile-pl.easypack24.net";
    static final String GLOBAL_BASE = API_BASE + "/global";

    static final class ApiException extends Exception {
        final int status;
        final String body;

        ApiException(int status, String body) {
            super("HTTP " + status + (body == null || body.length() == 0 ? "" : ": " + trim(body)));
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    static final class ParcelResult {
        String backend = "";
        JSONArray parcels = new JSONArray();
        String raw = "";
    }

    private ApiClient() {
    }

    static String userAgent() {
        String lang = Locale.getDefault().getLanguage();
        return String.format(Locale.US, "%s/%s(%d) (Android %s; %s; %s %s; %s)",
                Pkce.APP_NAME, Pkce.APP_VERSION, Pkce.APP_VERSION_CODE,
                Build.VERSION.RELEASE, Build.MODEL, Build.BRAND, Build.DEVICE, lang);
    }

    static String languageTag() {
        String tag = Locale.getDefault().toLanguageTag();
        if (tag == null || tag.length() == 0 || "und".equals(tag)) {
            return "pl-PL";
        }
        return tag;
    }

    private static String trim(String s) {
        s = s.replace('\n', ' ').trim();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static HttpURLConnection open(String endpoint, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    private static JSONObject readJson(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String body = readAll(in);
        if (code < 200 || code >= 300) {
            throw new ApiException(code, body);
        }
        return new JSONObject(body);
    }

    static JSONObject postForm(String endpoint, Map<String, String> form) throws Exception {
        HttpURLConnection c = open(endpoint, "POST");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", userAgent());
        c.setRequestProperty("Accept-Language", languageTag());
        c.setDoOutput(true);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8"));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        OutputStream os = c.getOutputStream();
        os.write(sb.toString().getBytes("UTF-8"));
        os.close();
        return readJson(c);
    }

    static JSONObject getJson(String endpoint, Map<String, String> headers) throws Exception {
        HttpURLConnection c = open(endpoint, "GET");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", userAgent());
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        return readJson(c);
    }

    // ------------------------------------------------------------------
    // OAuth
    // ------------------------------------------------------------------

    static void exchangeCode(AuthState st, String code) throws Exception {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", Pkce.CLIENT_ID);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", Pkce.REDIRECT_URI);
        form.put("code_verifier", st.oauthVerifier);
        form.put("code", code);
        applyTokens(st, postForm(Pkce.TOKEN, form));
    }

    static void refresh(AuthState st) throws Exception {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("client_id", Pkce.CLIENT_ID);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", st.refreshToken);
        applyTokens(st, postForm(Pkce.TOKEN, form));
    }

    private static void applyTokens(AuthState st, JSONObject resp) {
        st.accessToken = resp.optString("access_token", "");
        st.authToken = "Bearer " + st.accessToken;
        String rt = resp.optString("refresh_token", "");
        if (rt.length() > 0) {
            st.refreshToken = rt;
        }
        st.idToken = resp.optString("id_token", "");
        st.expiresIn = resp.optInt("expires_in", 0);
        st.tokenProvider = "GIP";
        st.authHeaderMode = "raw";
        st.authBase = API_BASE;
        st.tokenResponse = resp.toString();
        st.updatedAt = MainActivity.nowIso();
    }

    // ------------------------------------------------------------------
    // Parcels
    // ------------------------------------------------------------------

    private static String authHeader(AuthState st) {
        String t = st.authToken == null ? "" : st.authToken.trim();
        boolean bearer = t.toLowerCase(Locale.US).startsWith("bearer ");
        if ("strip-bearer".equals(st.authHeaderMode) && bearer) {
            return t.substring("bearer ".length()).trim();
        }
        if ("bearer".equals(st.authHeaderMode) && !bearer) {
            return "Bearer " + t;
        }
        return t;
    }

    private static Map<String, String> parcelHeaders(AuthState st) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Content-Type", "application/json");
        h.put("Accept", "application/json");
        h.put("X-App-Id", Pkce.APP_ID);
        h.put("device-uid", st.deviceUID);
        h.put("User-Agent", userAgent());
        h.put("Accept-Language", languageTag());
        h.put("Authorization", authHeader(st));
        return h;
    }

    static ParcelResult ensureAuth(AuthState st) throws Exception {
        if (st.authToken != null && st.authToken.length() > 0) {
            try {
                return fetchParcels(st);
            } catch (ApiException e) {
                if (e.status != 401 && e.status != 403) {
                    throw e;
                }
                // expired/invalid token: fall through to refresh
            }
        }
        if (st.refreshToken == null || st.refreshToken.length() == 0) {
            throw new Exception("no refresh token");
        }
        refresh(st);
        return fetchParcels(st);
    }

    static ParcelResult fetchParcels(AuthState st) throws Exception {
        boolean hasBearer = st.authToken != null
                && st.authToken.toLowerCase(Locale.US).startsWith("bearer ");
        String[] modes = hasBearer
                ? new String[]{"raw", "strip-bearer"}
                : new String[]{"raw", "bearer"};

        String pref = st.backendPreference == null ? "auto" : st.backendPreference;
        Exception last = null;
        ApiException auth = null;
        for (int i = 0; i < modes.length; i++) {
            st.authHeaderMode = modes[i];
            if (!"global".equals(pref)) {
                try {
                    return fetchV4(st);
                } catch (Exception e) {
                    last = e;
                    if (e instanceof ApiException) {
                        ApiException ae = (ApiException) e;
                        if (ae.status == 401 || ae.status == 403) {
                            auth = ae;
                        }
                    }
                }
            }
            if (!"v4".equals(pref)) {
                try {
                    return fetchGlobal(st);
                } catch (Exception e) {
                    last = e;
                    if (e instanceof ApiException) {
                        ApiException ae = (ApiException) e;
                        if (ae.status == 401 || ae.status == 403) {
                            auth = ae;
                        }
                    }
                }
            }
        }
        if (auth != null) {
            throw auth;
        }
        if (last != null) {
            throw last;
        }
        throw new Exception("no parcel backend available");
    }

    private static ParcelResult fetchV4(AuthState st) throws Exception {
        JSONObject o = getJson(API_BASE + "/v4/parcels/tracked", parcelHeaders(st));
        st.parcelBackend = "v4";
        st.authBase = API_BASE;
        ParcelResult r = new ParcelResult();
        r.backend = "v4";
        JSONArray a = o.optJSONArray("parcels");
        r.parcels = a == null ? new JSONArray() : a;
        r.raw = o.toString();
        return r;
    }

    private static ParcelResult fetchGlobal(AuthState st) throws Exception {
        JSONArray all = new JSONArray();
        String pagingState = "";
        String lastRaw = "";
        for (int page = 0; page < 100; page++) {
            String url = GLOBAL_BASE + "/cps/api/v1/parcels?role=receiver&size=100";
            if (pagingState.length() > 0) {
                url += "&pagingState=" + URLEncoder.encode(pagingState, "UTF-8");
            }
            JSONObject o = getJson(url, parcelHeaders(st));
            lastRaw = o.toString();
            JSONArray a = o.optJSONArray("parcels");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    all.put(a.get(i));
                }
            }
            JSONObject np = o.optJSONObject("nextPage");
            String ps = np == null ? "" : np.optString("pagingState", "");
            if (ps.length() == 0) {
                break;
            }
            pagingState = ps;
        }
        st.parcelBackend = "global";
        st.authBase = API_BASE;
        ParcelResult r = new ParcelResult();
        r.backend = "global";
        r.parcels = all;
        r.raw = lastRaw;
        return r;
    }
}
