package com.orcasolutions.iyosi;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ApiClient {
    interface Callback { void complete(JSONObject data, ApiError error); }

    static final class ApiError extends Exception {
        final int status;
        final String code;
        final JSONObject response;
        ApiError(int status, String code, String message, JSONObject response) {
            super(message); this.status = status; this.code = code; this.response = response;
        }
    }

    private static final String KEY_ALIAS = "iyosi-access-token";
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, String> pendingKeys = new ConcurrentHashMap<>();
    private String token;

    ApiClient(Context context) {
        this.context = context.getApplicationContext();
        token = restoreToken();
    }

    boolean signedIn() { return token != null && !token.isEmpty(); }
    void signOut() {
        token = null;
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply();
    }
    void setToken(String value) {
        token = value;
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
                generator.generateKey();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, (SecretKey) ks.getKey(KEY_ALIAS, null));
            byte[] bytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
                    .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString("token", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {
            // The current session still works; a future launch asks for sign-in again.
            context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply();
        }
    }
    private String restoreToken() {
        try {
            String iv = context.getSharedPreferences("auth", Context.MODE_PRIVATE).getString("iv", null);
            String saved = context.getSharedPreferences("auth", Context.MODE_PRIVATE).getString("token", null);
            if (iv == null || saved == null) return null;
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, (SecretKey) ks.getKey(KEY_ALIAS, null),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(saved, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply();
            return null;
        }
    }

    static String encode(String value) { return Uri.encode(value); }
    static String now() { return Instant.now().toString(); }
    static String newKey() { return UUID.randomUUID().toString(); }

    void get(String path, Callback callback) { request("GET", path, null, true, false, callback); }
    void post(String path, JSONObject body, boolean authenticated, boolean idempotent, Callback callback) {
        request("POST", path, body, authenticated, idempotent, callback);
    }
    void put(String path, JSONObject body, Callback callback) { request("PUT", path, body, true, false, callback); }
    void delete(String path, Callback callback) { request("DELETE", path, null, true, false, callback); }

    private void request(String method, String path, JSONObject body, boolean authenticated, boolean idempotent, Callback callback) {
        final String auth = token;
        final String identity = idempotent ? method + path + (body == null ? "" : body.toString()) : null;
        final String key = identity == null ? null : pendingKeys.computeIfAbsent(identity, ignored -> newKey());
        executor.execute(() -> {
            JSONObject data = null; ApiError error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BuildConfig.API_BASE_URL + path).openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                connection.setRequestMethod(method);
                connection.setRequestProperty("Accept", "application/json");
                if (authenticated && auth != null) connection.setRequestProperty("Authorization", "Bearer " + auth);
                if (key != null) connection.setRequestProperty("Idempotency-Key", key);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    try (java.io.OutputStream output = connection.getOutputStream()) {
                        output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                int status = connection.getResponseCode();
                byte[] bytes = read(status < 400 ? connection.getInputStream() : connection.getErrorStream());
                if (bytes.length > 0) data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                else data = new JSONObject();
                if (status >= 200 && status < 300 && identity != null) pendingKeys.remove(identity);
                if (status >= 400) {
                    error = new ApiError(status, data.optString("code", "http_error"),
                            data.optString("message", "Request failed (" + status + ")"), data);
                }
            } catch (Exception exception) {
                error = new ApiError(0, "network_error", "Cannot reach the API. Check your connection and try again.", null);
            } finally { if (connection != null) connection.disconnect(); }
            JSONObject result = data; ApiError failure = error;
            main.post(() -> callback.complete(result, failure));
        });
    }

    private static byte[] read(java.io.InputStream stream) throws Exception {
        if (stream == null) return new byte[0];
        try (java.io.InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) >= 0) output.write(buffer, 0, n);
            return output.toByteArray();
        }
    }

    static JSONObject object(Object... parts) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < parts.length; i += 2) result.put((String) parts[i], parts[i + 1]); }
        catch (JSONException exception) { throw new IllegalArgumentException(exception); }
        return result;
    }
    static JSONArray array(JSONObject value, String name) { return value == null ? new JSONArray() : value.optJSONArray(name) == null ? new JSONArray() : value.optJSONArray(name); }
}
