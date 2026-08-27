package com.miracle.kglaynyi.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.exoplayer2.C;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ResumeUtils {

    public static final String PREFS_NAME = "miracle_video_resume";
    private static final String POSITION_PREFIX = "resume_";
    private static final String URL_PREFIX = "resume_url_";
    private static final String UPDATED_PREFIX = "resume_updated_";

    private ResumeUtils() {}

    public static final class Entry {
        public final String url;
        public final long positionMs;
        public final long updatedAt;

        Entry(String url, long positionMs, long updatedAt) {
            this.url = url;
            this.positionMs = positionMs;
            this.updatedAt = updatedAt;
        }
    }

    public static long getPosition(Context context, String url) {
        if (context == null || url == null || url.trim().isEmpty()) return C.TIME_UNSET;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(positionKey(url), C.TIME_UNSET);
    }

    public static void save(Context context, String url, long positionMs) {
        if (context == null || url == null || url.trim().isEmpty()) return;
        String suffix = suffix(url);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(POSITION_PREFIX + suffix, positionMs)
                .putString(URL_PREFIX + suffix, url)
                .putLong(UPDATED_PREFIX + suffix, System.currentTimeMillis())
                .apply();
    }

    public static void remove(Context context, String url) {
        if (context == null || url == null || url.trim().isEmpty()) return;
        String suffix = suffix(url);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(POSITION_PREFIX + suffix)
                .remove(URL_PREFIX + suffix)
                .remove(UPDATED_PREFIX + suffix)
                .apply();
    }

    public static List<Entry> getEntries(Context context) {
        List<Entry> result = new ArrayList<>();
        if (context == null) return result;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> item : all.entrySet()) {
            String key = item.getKey();
            if (key == null || !key.startsWith(URL_PREFIX)) continue;

            Object rawUrl = item.getValue();
            if (!(rawUrl instanceof String)) continue;
            String url = (String) rawUrl;
            if (url.trim().isEmpty()) continue;

            String suffix = key.substring(URL_PREFIX.length());
            long position = prefs.getLong(POSITION_PREFIX + suffix, C.TIME_UNSET);
            if (position == C.TIME_UNSET || position <= 0) continue;

            long updated = prefs.getLong(UPDATED_PREFIX + suffix, 0L);
            result.add(new Entry(url, position, updated));
        }

        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return result;
    }

    private static String positionKey(String url) {
        return POSITION_PREFIX + suffix(url);
    }

    private static String suffix(String url) {
        return Integer.toHexString(url.hashCode());
    }
}
