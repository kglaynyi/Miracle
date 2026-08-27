package com.miracle.kglaynyi.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class PlaybackHistoryUtils {
    private static final String PREFS = "miracle_playback_history";
    private static final String COMPLETED = "completed_";

    private PlaybackHistoryUtils() {}

    public static boolean isCompleted(Context context, String mediaKey) {
        if (context == null || mediaKey == null || mediaKey.trim().isEmpty()) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(COMPLETED + key(mediaKey), false);
    }

    public static void markCompleted(Context context, String mediaKey) {
        if (context == null || mediaKey == null || mediaKey.trim().isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(COMPLETED + key(mediaKey), true).apply();
    }

    public static void markInProgress(Context context, String mediaKey) {
        if (context == null || mediaKey == null || mediaKey.trim().isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(COMPLETED + key(mediaKey), false).apply();
    }

    private static String key(String mediaKey) {
        return Integer.toHexString(mediaKey.hashCode());
    }
}
