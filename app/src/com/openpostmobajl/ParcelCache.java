package com.openpostmobajl;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Encrypted parcels.json cache.
 *
 * The file is an AES/GCM envelope whose key lives in the Android Keystore.
 * Only encrypted envelopes are read; plaintext JSON is not supported. The
 * cache is best-effort: a read/write failure never breaks the UI.
 */
final class ParcelCache {

    private static final String FILE = "parcels.json";

    private ParcelCache() {
    }

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE);
    }

    /** Returns null if there is no cache or it cannot be read/decrypted. */
    static ApiClient.ParcelResult load(Context ctx) {
        File f = file(ctx);
        if (!f.exists()) {
            return null;
        }
        try {
            byte[] raw = SecureStore.readFile(f);
            if (raw == null) {
                return null;
            }

            String json = new String(SecureStore.decrypt(raw), "UTF-8");
            JSONObject o = new JSONObject(json);
            JSONArray parcels = o.optJSONArray("parcels");
            if (parcels == null) {
                return null;
            }
            ApiClient.ParcelResult r = new ApiClient.ParcelResult();
            r.backend = o.optString("backend", "");
            r.parcels = parcels;

            return r;
        } catch (Exception e) {
            return null;
        }
    }

    static void save(Context ctx, ApiClient.ParcelResult res) {
        try {
            JSONObject o = new JSONObject();
            o.put("savedAt", nowIso());
            o.put("backend", res.backend);
            o.put("parcels", res.parcels);

            byte[] plain = o.toString(2).getBytes("UTF-8");
            SecureStore.writeFile(file(ctx), SecureStore.encrypt(plain));
        } catch (Exception ignored) {
            // Cache is best-effort; never break the UI because of it.
        }
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

    private static String nowIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
