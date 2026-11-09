package com.openpostmobajl;

/**
 * The subset of the POC's {@code auth.json} that the app needs.
 * Field names match the Go POC so the files are interchangeable.
 */
final class AuthState {
    String authToken = "";       // "Bearer <access_token>"
    String accessToken = "";
    String refreshToken = "";
    String idToken = "";
    String tokenProvider = "";
    int expiresIn = 0;

    String deviceUID = "";
    String authBase = "";
    String authHeaderMode = "raw";
    String parcelBackend = "";
    String backendPreference = "auto"; // auto | v4 | global

    String oauthVerifier = "";
    String oauthState = "";
    String oauthNonce = "";
    String authorizeUrl = "";
    String tokenResponse = "";

    String createdAt = "";
    String updatedAt = "";
}
