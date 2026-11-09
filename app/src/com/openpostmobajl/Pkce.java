package com.openpostmobajl;

import android.net.Uri;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * OAuth2/OIDC constants and PKCE helpers for the current InPost login flow.
 * Values mirror the Go POC (see inpost-mobile/poc1-phone-token/FLOW.md).
 */
final class Pkce {

    static final String ISSUER = "https://account.inpost-group.com";
    static final String AUTHORIZE = ISSUER + "/oauth2/authorize";
    static final String TOKEN = ISSUER + "/oauth2/token";
    static final String CLIENT_ID = "inpost-mobile";
    static final String REDIRECT_URI = ISSUER + "/callback";
    static final String SCOPE = "openid";

    // HTTP headers for the parcel API.
    static final String APP_ID = "pl.inpost.inmobile";
    static final String APP_NAME = "InPost-Mobile";
    static final String APP_VERSION = "4.22.1";
    static final int APP_VERSION_CODE = 42201000;

    private static final SecureRandom RNG = new SecureRandom();

    private Pkce() {
    }

    /** base64url without padding, like the POC's RawURLEncoding. */
    static String randomB64(int byteCount) {
        byte[] b = new byte[byteCount];
        RNG.nextBytes(b);
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static String challenge(String verifier) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(verifier.getBytes("UTF-8"));
        return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static String buildAuthorizeUrl(String state, String nonce, String verifier, String lang) throws Exception {
        return new Uri.Builder()
                .scheme("https")
                .authority("account.inpost-group.com")
                .appendPath("oauth2")
                .appendPath("authorize")
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPE)
                .appendQueryParameter("code_challenge", challenge(verifier))
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("response_mode", "query")
                .appendQueryParameter("lang", lang)
                .build()
                .toString();
    }
}
