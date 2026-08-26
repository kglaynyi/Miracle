package com.miracle.kglaynyi.utils;

import static com.miracle.kglaynyi.Constants.CF_CACHE_TOKEN;

import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class IndexConnectionValidator {

    private IndexConnectionValidator() {}

    public static final class ValidationResult {
        public final boolean success;
        public final String message;
        public final String resolvedIndexType;

        private ValidationResult(boolean success, String message, String resolvedIndexType) {
            this.success = success;
            this.message = message;
            this.resolvedIndexType = resolvedIndexType;
        }

        public static ValidationResult ok(String resolvedIndexType) {
            return new ValidationResult(true, "Index connection verified", resolvedIndexType);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }
    }

    public static ValidationResult validate(String rawUrl, String user, String pass, String indexType) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return ValidationResult.error("Enter an index URL");
        }
        if (indexType == null || indexType.trim().isEmpty()) {
            return ValidationResult.error("Select an index type");
        }

        String urlString = rawUrl.trim();
        if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
            return ValidationResult.error("Index URL must start with http:// or https://");
        }
        if (!urlString.endsWith("/")) {
            urlString += "/";
        }

        String type = indexType.trim();

        if ("GDI-JS".equals(type)) {
            GdiJsIndexClient.Result result = GdiJsIndexClient.validate(urlString, user, pass);
            return result.success ? ValidationResult.ok("GDI-JS")
                    : ValidationResult.error(result.message);
        }

        HttpURLConnection conn = null;
        try {
            String authHeader = basicAuth(user == null ? "" : user, pass == null ? "" : pass);
            URL url;

            if ("MapleIndex".equals(type) || "Maple".equals(type)) {
                url = new URL(urlString + "?rootId=root");
                conn = (HttpURLConnection) url.openConnection();
                configure(conn, authHeader);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
            } else if ("GoIndex".equals(type)) {
                url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                configure(conn, authHeader);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                byte[] body = "{\"q\":\"\",\"password\":null,\"page_index\":0}".getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(body);
            } else if ("SimpleProgram".equals(type)) {
                url = new URL(urlString + "?page_token=&page_index=0&password=");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestMethod("GET");
                if (CF_CACHE_TOKEN != null && !CF_CACHE_TOKEN.isEmpty()) {
                    conn.setRequestProperty("cf_cache_token", CF_CACHE_TOKEN);
                }
            } else if ("GDIndex".equals(type)) {
                url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                configure(conn, authHeader);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                Map<String, Object> params = new LinkedHashMap<>();
                params.put("authorization", authHeader);
                params.put("page_token", "");
                params.put("page_index", 0);
                StringBuilder postData = new StringBuilder();
                for (Map.Entry<String, Object> param : params.entrySet()) {
                    if (postData.length() != 0) postData.append('&');
                    postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                    postData.append('=');
                    postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
                }
                conn.getOutputStream().write(postData.toString().getBytes(StandardCharsets.UTF_8));
            } else {
                return ValidationResult.error("Unsupported index type: " + type);
            }

            int code = conn.getResponseCode();
            String body = readBody(conn, code);

            // GDI-JS 2.x protects POST API calls with a session cookie and returns 401
            // until /login has been called. Automatically detect that when the user
            // selected the older GDIndex option.
            if ("GDIndex".equals(type)
                    && (code == HttpURLConnection.HTTP_UNAUTHORIZED
                    || code == HttpURLConnection.HTTP_FORBIDDEN)) {
                GdiJsIndexClient.Result gdiJs = GdiJsIndexClient.validate(urlString, user, pass);
                if (gdiJs.success) return ValidationResult.ok("GDI-JS");
                return ValidationResult.error(gdiJs.message);
            }

            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                return ValidationResult.error("Incorrect username/password or access denied (HTTP " + code + ")");
            }
            if (code < 200 || code >= 300) {
                return ValidationResult.error("Index returned HTTP " + code);
            }
            if (body == null || body.trim().isEmpty()) {
                return ValidationResult.error("Index returned an empty response");
            }

            String lower = body.toLowerCase(Locale.US);
            if (lower.contains("unauthorized") || lower.contains("invalid password") ||
                    lower.contains("incorrect password") || lower.contains("access denied") ||
                    lower.contains("forbidden") || lower.contains("\"code\":401") ||
                    lower.contains("\"code\":403")) {
                return ValidationResult.error("Incorrect username/password or index access denied");
            }

            if (!"GDIndex".equals(type) && lower.startsWith("<html") &&
                    !lower.contains("files") && !lower.contains("drive")) {
                return ValidationResult.error("URL opened a web page instead of an index API response");
            }

            return ValidationResult.ok(type);
        } catch (java.net.MalformedURLException e) {
            return ValidationResult.error("Invalid index URL");
        } catch (java.net.SocketTimeoutException e) {
            return ValidationResult.error("Index connection timed out");
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
            return ValidationResult.error("Cannot connect to index: " + message);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void configure(HttpURLConnection conn, String authHeader) {
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("authorization", authHeader);
    }

    private static String readBody(HttpURLConnection conn, int code) throws Exception {
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        return out.toString();
    }

    private static String basicAuth(String user, String pass) {
        byte[] bytes = (user + ":" + pass).getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return "Basic " + Base64.getEncoder().encodeToString(bytes);
        }
        return "Basic " + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }
}
