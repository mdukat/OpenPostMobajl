package com.openpostmobajl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * OpenPostMobajl for Android.
 *
 * If auth.json does not exist, it renders the OAuth authorize page in a WebView.
 * The WebView's redirect to https://account.inpost-group.com/callback?code=... is
 * intercepted, the code is exchanged for tokens, the state is saved, and the
 * parcel list is shown. On later launches the saved token is used (and refreshed
 * if needed) without showing the login page.
 */
public class MainActivity extends BaseActivity {

    private FrameLayout root;
    private LinearLayout panel;
    private TextView status;
    private LinearLayout list;
    private WebView webView;
    private LinearLayout sidebar;
    private View scrim;
    private boolean sidebarOpen = false;
    private boolean simplified = false;
    private AuthState state;
    private boolean codeHandled = false;

    // Live countdown to expiryDate for the rendered ready-to-pickup rows.
    private final List<TextView> rowViews = new ArrayList<TextView>();
    private final List<String> rowBase = new ArrayList<String>();
    private final List<Long> rowExpiry = new ArrayList<Long>();
    private boolean hasCountdown = false;
    private boolean resumed = false;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            updateCountdowns();
            countdownHandler.postDelayed(this, 1000);
        }
    };

    private static final int SIDEBAR_WIDTH_DP = 280;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            state = AuthStore.load(this);
        } catch (Exception e) {
            state = new AuthState();
        }
        if (state.deviceUID == null || state.deviceUID.length() == 0) {
            state.deviceUID = UUID.randomUUID().toString();
        }
        if (state.createdAt == null || state.createdAt.length() == 0) {
            state.createdAt = nowIso();
        }

        simplified = AppSettings.isSimplified(this);
        setContentView(buildPanel());
        if (Build.VERSION.SDK_INT >= 33) {
            BackApi33.register(this, new Runnable() {
                @Override
                public void run() {
                    handleBack();
                }
            });
        }

        boolean haveCredentials = (state.authToken != null && state.authToken.length() > 0)
                || (state.refreshToken != null && state.refreshToken.length() > 0);
        if (haveCredentials) {
            ApiClient.ParcelResult cached = ParcelCache.load(this);
            if (cached != null) {
                render(cached, true);
            }
            loadParcels();
        } else {
            startLogin();
        }
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private View buildPanel() {
        root = new FrameLayout(this);

        // ---- main view: burger + status + parcel list ----
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button burger = new Button(this);
        if (simplified) {
            burger.setText(R.string.common_menu);
            burger.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            burger.setPadding(dp(14), dp(12), dp(14), dp(12));
        } else {
            burger.setText("\u2261"); // ≡ (safe burger glyph)
            burger.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            burger.setPadding(dp(8), 0, dp(8), 0);
        }
        burger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSidebar();
            }
        });
        topBar.addView(burger, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (simplified) {
            // Older users may not find Refresh in the sidebar, so keep it in the
            // top bar next to "Menu".
            Button quickRefresh = new Button(this);
            quickRefresh.setText(R.string.common_refresh);
            quickRefresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            quickRefresh.setPadding(dp(14), dp(12), dp(14), dp(12));
            quickRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadParcels();
                }
            });
            topBar.addView(quickRefresh, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        panel.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        status.setText(R.string.main_status_ready);
        if (simplified) {
            status.setVisibility(View.GONE);
        }
        panel.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- scrim behind the sidebar ----
        scrim = new View(this);
        scrim.setBackgroundColor(0x66000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
            }
        });
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- sidebar ----
        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(Color.WHITE);
        sidebar.setPadding(dp(12), dp(12), dp(12), dp(12));
        sidebar.setElevation(dp(8));

        LinearLayout sideHeader = new LinearLayout(this);
        sideHeader.setOrientation(LinearLayout.HORIZONTAL);
        sideHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView menuTitle = new TextView(this);
        menuTitle.setText(R.string.common_menu);
        menuTitle.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        menuTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        sideHeader.addView(menuTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button close = new Button(this);
        close.setText("\u00d7"); // ×
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
            }
        });
        sideHeader.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sidebar.addView(sideHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button refresh = new Button(this);
        refresh.setText(R.string.common_refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
                loadParcels();
            }
        });
        sidebar.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button delivered = new Button(this);
        delivered.setText(R.string.common_delivered_parcels);
        delivered.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
                startActivity(new Intent(MainActivity.this, DeliveredParcelsActivity.class));
            }
        });
        sidebar.addView(delivered, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button settings = new Button(this);
        settings.setText(R.string.common_settings);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        sidebar.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button licenses = new Button(this);
        licenses.setText(R.string.common_licenses);
        licenses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
                startActivity(new Intent(MainActivity.this, LicensesActivity.class));
            }
        });
        sidebar.addView(licenses, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!simplified) {
            Button signOut = new Button(this);
            signOut.setText(R.string.common_sign_out);
            signOut.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeSidebar();
                    signOut();
                }
            });
            sidebar.addView(signOut, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        FrameLayout.LayoutParams sideLp = new FrameLayout.LayoutParams(
                dp(SIDEBAR_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT);
        sideLp.gravity = Gravity.START;
        root.addView(sidebar, sideLp);
        sidebar.setTranslationX(-dp(SIDEBAR_WIDTH_DP));

        return root;
    }

    private void openSidebar() {
        if (sidebarOpen) {
            return;
        }
        sidebarOpen = true;
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        sidebar.animate().translationX(0f).setDuration(200).start();
    }

    private void closeSidebar() {
        if (!sidebarOpen) {
            return;
        }
        sidebarOpen = false;
        sidebar.animate().translationX(-dp(SIDEBAR_WIDTH_DP)).setDuration(200).start();
        scrim.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!sidebarOpen) {
                    scrim.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    private void startLogin() {
        try {
            codeHandled = false;
            String verifier = Pkce.randomB64(32);
            String oauthState = Pkce.randomB64(16);
            String nonce = Pkce.randomB64(16);
            state.oauthVerifier = verifier;
            state.oauthState = oauthState;
            state.oauthNonce = nonce;

            String url = Pkce.buildAuthorizeUrl(oauthState, nonce, verifier, ApiClient.languageTag());
            state.authorizeUrl = url;

            status.setText(R.string.main_login_prompt);
            showWebView(url);
        } catch (Exception e) {
            showError(e);
        }
    }

    private void showWebView(String url) {
        if (webView != null) {
            webView.stopLoading();
            root.removeView(webView);
            webView.destroy();
        }
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                // Fallback for WebView versions that do not call
                // shouldOverrideUrlLoading for server-side redirects.
                handleUrl(url);
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.loadUrl(url);
    }

    private boolean handleUrl(String u) {
        if (u == null) {
            return false;
        }
        if (u.startsWith(Pkce.REDIRECT_URI)) {
            if (codeHandled) {
                return true;
            }
            Uri uri = Uri.parse(u);
            String code = uri.getQueryParameter("code");
            String gotState = uri.getQueryParameter("state");

            if (gotState != null && gotState.length() > 0
                    && state.oauthState != null && state.oauthState.length() > 0
                    && !gotState.equals(state.oauthState)) {
                showError(new Exception("OAuth state mismatch"));
                return true;
            }
            if (code != null && code.length() > 0) {
                codeHandled = true;
                status.setText(R.string.main_exchanging_code);
                if (webView != null) {
                    webView.stopLoading();
                    root.removeView(webView);
                    webView.destroy();
                    webView = null;
                }
                exchangeCode(code);
                return true;
            }
        }
        return false;
    }

    private void exchangeCode(final String code) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiClient.exchangeCode(state, code);
                    AuthStore.save(MainActivity.this, state);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadParcels();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError(e);
                        }
                    });
                }
            }
        }).start();
    }

    private void loadParcels() {
        status.setText(list.getChildCount() > 0
                ? getString(R.string.main_status_updating)
                : getString(R.string.main_status_loading));
        status.setVisibility(simplified ? View.GONE : View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ApiClient.ParcelResult res = ApiClient.ensureAuth(state);
                    AuthStore.save(MainActivity.this, state);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            render(res, false);
                        }
                    });
                    // Persist after posting the render so the UI is not delayed.
                    ParcelCache.save(MainActivity.this, res);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError(e);
                        }
                    });
                }
            }
        }).start();
    }

    private void render(ApiClient.ParcelResult res, boolean cached) {
        list.removeAllViews();
        status.setVisibility(simplified ? View.GONE : View.VISIBLE);
        rowViews.clear();
        rowBase.clear();
        rowExpiry.clear();
        hasCountdown = false;

        List<JSONObject> sorted = ParcelUi.sorted(res.parcels, false);
        List<ParcelEntry> items = ParcelUi.grouped(sorted);

        int n = sorted.size();
        if (cached) {
            status.setText(getString(R.string.main_status_cached, res.backend, n));
        } else {
            status.setText(getString(R.string.main_status_backend, res.backend,
                    state.backendPreference, n));
        }

        if (items.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.main_no_parcels);
            tv.setPadding(0, dp(8), 0, dp(8));
            list.addView(tv);
            syncCountdownTimer();
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            final ParcelEntry entry = items.get(i);
            String base = simplified
                    ? ParcelUi.simplifiedRowText(this, entry.first)
                    : (entry.isMulti()
                    ? ParcelUi.multiCompartmentRowText(this, entry)
                    : ParcelUi.rowText(entry.first));
            final long expiry = entry.expiryMillis();
            TextView tv = new TextView(this);
            tv.setText(expiry > 0 ? base + "\n" + ParcelUi.expiryText(this, expiry) : base);
            tv.setTextColor(Color.rgb(0x11, 0x11, 0x11));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            if (simplified) {
                Ui.applyBorderedRow(this, tv);
            } else {
                tv.setPadding(0, dp(12), 0, dp(12));
            }
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (entry.isMulti()) {
                        JSONArray arr = new JSONArray();
                        for (int j = 0; j < entry.parcels.size(); j++) {
                            arr.put(entry.parcels.get(j));
                        }
                        Intent intent = new Intent(MainActivity.this,
                                MultiCompartmentActivity.class);
                        intent.putExtra("parcels", arr.toString());
                        intent.putExtra("uuid", entry.multiUuid);
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(MainActivity.this,
                                ParcelDetailActivity.class);
                        intent.putExtra("parcel", entry.first.toString());
                        startActivity(intent);
                    }
                }
            });
            list.addView(tv);

            rowViews.add(tv);
            rowBase.add(base);
            rowExpiry.add(Long.valueOf(expiry));
            if (expiry > 0) {
                hasCountdown = true;
            }

            if (!simplified) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.LTGRAY);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }

        syncCountdownTimer();
    }

    private void updateCountdowns() {
        for (int i = 0; i < rowViews.size(); i++) {
            long expiry = rowExpiry.get(i).longValue();
            if (expiry > 0) {
                rowViews.get(i).setText(
                        rowBase.get(i) + "\n" + ParcelUi.expiryText(this, expiry));
            }
        }
    }

    private void syncCountdownTimer() {
        countdownHandler.removeCallbacks(countdownTick);
        if (resumed && hasCountdown) {
            countdownHandler.post(countdownTick);
        }
    }

    private void signOut() {
        closeSidebar();
        AuthStore.delete(this);
        ParcelCache.delete(this);
        if (webView != null) {
            webView.stopLoading();
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
            root.removeView(webView);
            webView.destroy();
            webView = null;
        }
        // Drop everything the login WebView may have persisted, not just cookies.
        WebStorage.getInstance().deleteAllData();
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
        codeHandled = false;
        state = new AuthState();
        state.deviceUID = UUID.randomUUID().toString();
        state.createdAt = nowIso();
        list.removeAllViews();
        startLogin();
    }

    private void showError(Exception e) {
        status.setText(getString(R.string.common_error, e.getMessage()));
        // Simplified mode hides the status line, but errors must still be visible.
        status.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        syncCountdownTimer();
        reloadBackendPreference();
    }

    /**
     * The backend is chosen in Settings, which writes it to auth.json; pick it
     * up here and refresh when it changed.
     */
    private void reloadBackendPreference() {
        try {
            String pref = AuthStore.load(this).backendPreference;
            if (pref == null) {
                pref = "auto";
            }
            String current = state.backendPreference == null ? "auto" : state.backendPreference;
            if (!pref.equals(current)) {
                state.backendPreference = pref;
                if (state.authToken != null && state.authToken.length() > 0) {
                    loadParcels();
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        countdownHandler.removeCallbacks(countdownTick);
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Pre-API 33 path; on 33+ BackApi33 registers a predictive-back callback.
        handleBack();
    }

    private void handleBack() {
        if (sidebarOpen) {
            closeSidebar();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        finish();
    }

    /**
     * Predictive-back registration (API 33+), isolated in its own class so
     * devices below API 33 never load the newer types.
     */
    private static final class BackApi33 {
        static void register(Activity activity, final Runnable onBack) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            onBack.run();
                        }
                    });
        }
    }

    @Override
    protected void onDestroy() {
        countdownHandler.removeCallbacks(countdownTick);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    static String nowIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
