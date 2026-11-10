package com.openpostmobajl;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES/GCM encryption backed by a non-exportable Android Keystore key.
 *
 * The on-disk envelope is JSON:
 *   {"v":1,"iv":"<base64>","data":"<base64 ciphertext>"}
 *
 * The key never leaves the keystore. If the key is invalidated (for example by
 * a lock-screen change), it is recreated and the caller falls back to a fresh
 * login / empty cache.
 */
final class SecureStore {

    private static final String ALIAS = "openpostmobajl_data_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int VERSION = 1;

    private SecureStore() {
    }

    static byte[] encrypt(byte[] plaintext) throws Exception {
        try {
            return encryptWithKey(plaintext);
        } catch (KeyPermanentlyInvalidatedException e) {
            // The key was invalidated (for example by a lock-screen change).
            // Recreate it once and retry; do not delete key material for any
            // other error, otherwise a transient failure would wipe the data.
            deleteKey();
            return encryptWithKey(plaintext);
        } catch (UnrecoverableKeyException e) {
            // The key exists but its material cannot be read. Recreate once.
            deleteKey();
            return encryptWithKey(plaintext);
        }
    }

    static byte[] decrypt(byte[] envelope) throws Exception {
        JSONObject o = new JSONObject(new String(envelope, "UTF-8"));
        if (o.optInt("v", 0) != VERSION) {
            throw new Exception("unsupported envelope version");
        }
        byte[] iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP);
        byte[] data = Base64.decode(o.getString("data"), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(data);
    }

    static byte[] readFile(File f) throws Exception {
        if (!f.exists()) {
            return null;
        }
        return readAll(new FileInputStream(f));
    }

    static void writeFile(File f, byte[] bytes) throws Exception {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        out.write(bytes);
        out.close();
        if (!tmp.renameTo(f)) {
            FileOutputStream out2 = new FileOutputStream(f);
            out2.write(bytes);
            out2.close();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    static void deleteKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) {
                ks.deleteEntry(ALIAS);
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] encryptWithKey(byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] data = cipher.doFinal(plaintext);
        JSONObject o = new JSONObject();
        o.put("v", VERSION);
        o.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        o.put("data", Base64.encodeToString(data, Base64.NO_WRAP));
        return o.toString().getBytes("UTF-8");
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            try {
                return (SecretKey) ks.getKey(ALIAS, null);
            } catch (UnrecoverableKeyException e) {
                ks.deleteEntry(ALIAS);
            }
        }
        try {
            return generateKey(256);
        } catch (Exception e) {
            deleteKey();
            return generateKey(128);
        }
    }

    private static SecretKey generateKey(int size) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(size)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return kg.generateKey();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }
}
