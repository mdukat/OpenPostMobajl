package com.openpostmobajl;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * "Extend collection time" (avizo) with BLIK payment.
 *
 * Endpoints (validated against InPost Mobile 4.22.1):
 *   GET  /v3/prices/parcels?sourceCountryCode=PL  -> expandAvizo {price, time}
 *   POST /v1/orders/expand-avizo                  -> {paymentRequired, regularPrice}
 *   POST /v1/payments/transactions/create/blik    -> {transactionId}
 *   poll GET /v4/parcels/tracked until avizoTransactionStatus is COMPLETED/REJECTED
 */
public class AvizoActivity extends BaseActivity {

    private static final long POLL_INTERVAL_MS = 3000;
    private static final int POLL_MAX_TRIES = 40; // ~2 minutes

    private AuthState state;
    private String shipmentNumber = "";

    private TextView status;
    private TextView priceView;
    private TextView agreement;
    private EditText blikCode;
    private Button payButton;
    private Button cancelButton;

    private String amount = "";
    private String currency = "";
    private long timeMinutes = 0;
    private String transactionId = "";
    private volatile boolean cancelled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            state = AuthStore.load(this);
        } catch (Exception e) {
            state = new AuthState();
        }

        JSONObject parcel = null;
        try {
            parcel = new JSONObject(getIntent().getStringExtra("parcel"));
        } catch (Exception ignored) {
        }
        if (parcel != null) {
            shipmentNumber = parcel.optString("shipmentNumber",
                    parcel.optString("primaryParcelNumber", ""));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        // ---- top bar ----
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new Button(this);
        back.setText(R.string.common_back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.avizo_title);
        title.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setPadding(dp(8), 0, 0, 0);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView parcelLabel = new TextView(this);
        parcelLabel.setText(parcel == null
                ? "" : getString(R.string.avizo_parcel, ParcelUi.last6(parcel)));
        parcelLabel.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        parcelLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        parcelLabel.setPadding(0, dp(12), 0, dp(4));
        content.addView(parcelLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(Color.rgb(0x55, 0x55, 0x55));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        status.setText(R.string.avizo_preparing);
        status.setPadding(0, dp(8), 0, dp(8));
        content.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        priceView = new TextView(this);
        priceView.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        priceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        priceView.setVisibility(View.GONE);
        priceView.setPadding(0, dp(8), 0, dp(8));
        content.addView(priceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        blikCode = new EditText(this);
        blikCode.setHint(R.string.avizo_blik_hint);
        blikCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        blikCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        blikCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        blikCode.setVisibility(View.GONE);
        content.addView(blikCode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        payButton = new Button(this);
        payButton.setText(R.string.avizo_pay);
        payButton.setVisibility(View.GONE);
        payButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pay();
            }
        });
        content.addView(payButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        agreement = new TextView(this);
        agreement.setText(R.string.avizo_agreement);
        agreement.setTextColor(Color.rgb(0x55, 0x55, 0x55));
        agreement.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        agreement.setVisibility(View.GONE);
        agreement.setPadding(0, dp(8), 0, dp(8));
        content.addView(agreement, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cancelButton = new Button(this);
        cancelButton.setText(R.string.avizo_cancel);
        cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelPayment();
            }
        });
        content.addView(cancelButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        if (parcel == null || shipmentNumber.length() == 0) {
            showResult(getString(R.string.avizo_not_available));
        } else {
            prepare();
        }
    }

    // ------------------------------------------------------------------
    // Flow
    // ------------------------------------------------------------------

    private void prepare() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiClient.ensureAuth(state);
                    try {
                        AuthStore.save(AvizoActivity.this, state);
                    } catch (Exception ignored) {
                    }
                    final ApiClient.AvizoPrice price = ApiClient.fetchAvizoPrice(state);
                    final ApiClient.AvizoOrder order = ApiClient.createExpandAvizoOrder(
                            state, shipmentNumber, price.amount, price.timeMinutes, null);
                    amount = order.price.length() > 0 ? order.price : price.amount;
                    currency = price.currency;
                    timeMinutes = price.timeMinutes;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (order.paymentRequired) {
                                showReady();
                            } else {
                                showResult(getString(R.string.avizo_free_done));
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            prepareFailed(e);
                        }
                    });
                }
            }
        }, "avizo-prepare").start();
    }

    private void prepareFailed(Exception e) {
        status.setText(getString(R.string.avizo_error, e.getMessage()));
        status.setVisibility(View.VISIBLE);
        payButton.setVisibility(View.GONE);
        blikCode.setVisibility(View.GONE);
        agreement.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
    }

    private void pay() {
        final String code = blikCode.getText().toString().trim();
        if (code.length() != 6) {
            status.setText(R.string.avizo_invalid_code);
            status.setVisibility(View.VISIBLE);
            return;
        }
        payButton.setEnabled(false);
        blikCode.setEnabled(false);
        status.setText(R.string.avizo_in_progress);
        status.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    transactionId = ApiClient.createBlikTransaction(
                            state, shipmentNumber, amount, currency, code, timeMinutes);
                    pollStatus();
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            paymentFailed(e);
                        }
                    });
                }
            }
        }, "avizo-pay").start();
    }

    private void pollStatus() {
        for (int i = 0; i < POLL_MAX_TRIES && !cancelled; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
            String s;
            try {
                s = ApiClient.fetchAvizoStatus(state, shipmentNumber);
            } catch (Exception e) {
                continue; // transient error: keep polling
            }
            if ("COMPLETED".equalsIgnoreCase(s)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showResult(getString(R.string.avizo_success));
                    }
                });
                return;
            }
            if ("REJECTED".equalsIgnoreCase(s)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        paymentFailed(new Exception(getString(R.string.avizo_rejected)));
                    }
                });
                return;
            }
        }
        if (!cancelled) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showResult(getString(R.string.avizo_timeout));
                }
            });
        }
    }

    private void cancelPayment() {
        cancelled = true;
        final String tx = transactionId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (tx != null && tx.length() > 0) {
                        ApiClient.cancelTransaction(state, tx);
                    }
                } catch (Exception ignored) {
                }
            }
        }, "avizo-cancel").start();
        showResult(getString(R.string.avizo_cancelled));
    }

    // ------------------------------------------------------------------
    // UI state
    // ------------------------------------------------------------------

    private void showReady() {
        status.setText("");
        status.setVisibility(View.GONE);
        priceView.setText(getString(R.string.avizo_price, amount, currency)
                + "\n" + extensionText());
        priceView.setVisibility(View.VISIBLE);
        blikCode.setVisibility(View.VISIBLE);
        payButton.setEnabled(true);
        payButton.setVisibility(View.VISIBLE);
        agreement.setVisibility(View.VISIBLE);
    }

    private String extensionText() {
        if (timeMinutes <= 0) {
            return "";
        }
        if (timeMinutes % 60 == 0) {
            return getString(R.string.avizo_extension_hours, timeMinutes / 60);
        }
        return getString(R.string.avizo_extension_minutes, timeMinutes);
    }

    private void paymentFailed(Exception e) {
        payButton.setEnabled(true);
        blikCode.setEnabled(true);
        payButton.setVisibility(View.VISIBLE);
        blikCode.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.GONE);
        status.setText(getString(R.string.avizo_error, e.getMessage()));
        status.setVisibility(View.VISIBLE);
    }

    private void showResult(String message) {
        cancelled = true;
        payButton.setVisibility(View.GONE);
        blikCode.setVisibility(View.GONE);
        agreement.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
