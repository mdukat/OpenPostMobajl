package com.openpostmobajl;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;

/**
 * Encrypted auth.json persistence.
 *
 * The file is an AES/GCM envelope whose key lives in the Android Keystore.
 * Only encrypted envelopes are read; plaintext JSON is not supported.
 */
final class AuthStore {

    private static final String FILE = "auth.json";

    private AuthStore() {
    }

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE);
    }

    static boolean exists(Context ctx) {
        return file(ctx).exists();
    }

    static AuthState load(Context ctx) throws Exception {
        AuthState st = new AuthState();
        File f = file(ctx);
        if (!f.exists()) {
            return st;
        }
        byte[] raw = SecureStore.readFile(f);
        if (raw == null) {
            return st;
        }

        String json = new String(SecureStore.decrypt(raw, FILE), "UTF-8");
        JSONObject o = new JSONObject(json);
        st.authToken = o.optString("authToken", "");
        st.accessToken = o.optString("accessToken", "");
        st.refreshToken = o.optString("refreshToken", "");
        st.idToken = o.optString("idToken", "");
        st.tokenProvider = o.optString("tokenProvider", "");
        st.expiresIn = o.optInt("expiresIn", 0);
        st.deviceUID = o.optString("deviceUID", "");
        st.authBase = o.optString("authBase", "");
        st.authHeaderMode = o.optString("authHeaderMode", "raw");
        st.parcelBackend = o.optString("parcelBackend", "");
        st.backendPreference = o.optString("backendPreference", "auto");
        st.oauthVerifier = o.optString("oauthVerifier", "");
        st.oauthState = o.optString("oauthState", "");
        st.oauthNonce = o.optString("oauthNonce", "");
        st.authorizeUrl = o.optString("authorizeUrl", "");
        st.tokenResponse = o.optString("tokenResponse", "");
        st.createdAt = o.optString("createdAt", "");
        st.updatedAt = o.optString("updatedAt", "");

        return st;
    }

    static void save(Context ctx, AuthState st) throws Exception {
        JSONObject o = new JSONObject();
        o.put("authToken", st.authToken);
        o.put("accessToken", st.accessToken);
        o.put("refreshToken", st.refreshToken);
        o.put("idToken", st.idToken);
        o.put("tokenProvider", st.tokenProvider);
        o.put("expiresIn", st.expiresIn);
        o.put("deviceUID", st.deviceUID);
        o.put("authBase", st.authBase);
        o.put("authHeaderMode", st.authHeaderMode);
        o.put("parcelBackend", st.parcelBackend);
        o.put("backendPreference", st.backendPreference);
        o.put("oauthVerifier", st.oauthVerifier);
        o.put("oauthState", st.oauthState);
        o.put("oauthNonce", st.oauthNonce);
        o.put("authorizeUrl", st.authorizeUrl);
        o.put("tokenResponse", st.tokenResponse);
        o.put("createdAt", st.createdAt);
        o.put("updatedAt", st.updatedAt);

        byte[] plain = o.toString(2).getBytes("UTF-8");
        SecureStore.writeFile(file(ctx), SecureStore.encrypt(plain, FILE));
    }

    static void delete(Context ctx) {
        File f = file(ctx);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        File tmp = new File(ctx.getFilesDir(), FILE + ".tmp");
        if (tmp.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
