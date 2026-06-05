package com.whu.software.athena.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Token management utilities.
 */
public class TokenManager {
    private static final String TAG = "TokenManager";

    /**
     * Read token from local database.
     */
    public static String getToken(Context context) {
        if (context == null) return "";

        UserDao userDao = new UserDao(context);
        String token = "";
        try {
            userDao.open();
            String[] loginUser = userDao.getCurrentLoginUser();
            if (loginUser != null && loginUser.length >= 3) {
                token = loginUser[2];
            }
        } catch (Exception e) {
            Log.w(TAG, "getToken failed", e);
        } finally {
            userDao.close();
        }
        return token != null ? token : "";
    }

    /**
     * Read userId from local database first, then fallback to parsing the JWT payload itself.
     */
    public static String getUserId(Context context) {
        if (context == null) return "";

        UserDao userDao = new UserDao(context);
        String userId = "";
        String token = "";
        String phone = "";
        try {
            userDao.open();
            String[] loginUser = userDao.getCurrentLoginUser();
            if (loginUser != null) {
                if (loginUser.length >= 4) {
                    userId = loginUser[3];
                }
                if (loginUser.length >= 3) {
                    token = loginUser[2];
                }
                if (loginUser.length >= 2) {
                    phone = loginUser[1];
                }
            }

            if (TextUtils.isEmpty(userId)) {
                userId = parseUserIdFromToken(token);
                if (!TextUtils.isEmpty(userId) && !TextUtils.isEmpty(phone)) {
                    userDao.insertOrUpdateUser(1, phone, token, userId);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getUserId failed", e);
        } finally {
            userDao.close();
        }
        return userId != null ? userId : "";
    }

    /**
     * Update token in local database. When possible, parse userId from the new token and persist it together.
     */
    public static boolean updateToken(Context context, String phone, String newToken) {
        if (context == null || phone == null || newToken == null) return false;

        UserDao userDao = new UserDao(context);
        try {
            userDao.open();
            String[] loginUser = userDao.getCurrentLoginUser();
            if (loginUser != null && loginUser.length >= 2) {
                phone = loginUser[1];
            }

            String parsedUserId = parseUserIdFromToken(newToken);
            long result = userDao.insertOrUpdateUser(1, phone, newToken, parsedUserId);
            return result > 0;
        } catch (Exception e) {
            Log.w(TAG, "updateToken failed", e);
            return false;
        } finally {
            userDao.close();
        }
    }

    private static String parseUserIdFromToken(String token) {
        if (TextUtils.isEmpty(token)) {
            return "";
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return "";
            }

            String payload = parts[1];
            int mod = payload.length() % 4;
            if (mod != 0) {
                payload += "====".substring(mod);
            }

            byte[] decoded = Base64.decode(
                    payload,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );
            String json = new String(decoded, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");

            String userId = firstNonEmpty(
                    root.optString("userId", ""),
                    root.optString("id", ""),
                    root.optString("user_id", ""),
                    root.optString("uid", ""),
                    root.optString("userID", ""),
                    root.optString("sub", "")
            );
            if (!TextUtils.isEmpty(userId)) {
                return userId;
            }

            if (data != null) {
                return firstNonEmpty(
                        data.optString("userId", ""),
                        data.optString("id", ""),
                        data.optString("user_id", ""),
                        data.optString("uid", ""),
                        data.optString("userID", ""),
                        data.optString("sub", "")
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "parseUserIdFromToken failed", e);
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed)) {
                    return trimmed;
                }
            }
        }
        return "";
    }
}
