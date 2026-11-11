package com.openpostmobajl;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Best-effort pickup-point image loader: downloads the image once, caches it on
 * disk for 24 hours, and appends an {@link ImageView} to the given container
 * only after a bitmap was decoded successfully.
 *
 * Any failure (missing URL, network error, non-image response, decode error) is
 * silent: nothing is added and no error is shown.
 */
final class PickupImageLoader {

    private static final long TTL_MS = 24L * 60 * 60 * 1000;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int IMAGE_HEIGHT_DP = 180;

    private PickupImageLoader() {
    }

    /**
     * Loads {@code url} off the main thread and, on success, appends the image
     * to {@code parent}. Does nothing when {@code url} is empty or the load
     * fails.
     */
    static void loadInto(final Activity activity, final LinearLayout parent, final String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = fetch(activity, url);
                if (bmp == null) {
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }
                        ImageView image = new ImageView(activity);
                        image.setImageBitmap(bmp);
                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        image.setBackgroundColor(Color.WHITE);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, IMAGE_HEIGHT_DP));
                        lp.topMargin = dp(activity, 8);
                        parent.addView(image, lp);
                    }
                });
            }
        }, "pickup-image").start();
    }

    private static Bitmap fetch(Context ctx, String url) {
        try {
            File file = cacheFile(ctx, url);
            long age = System.currentTimeMillis() - file.lastModified();
            if (!file.exists() || age < 0 || age > TTL_MS) {
                if (!download(url, file) && !file.exists()) {
                    return null;
                }
            }
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean download(String url, File file) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                return false;
            }
            InputStream in = c.getInputStream();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    out.close();
                    in.close();
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    return false;
                }
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            return tmp.renameTo(file);
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static File cacheFile(Context ctx, String url) {
        return new File(ctx.getCacheDir(), "pickup_" + sha256(url) + ".img");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }
}
