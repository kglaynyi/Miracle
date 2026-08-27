package com.miracle.kglaynyi.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent GDI-JS scan checkpoint. Saved after every successfully processed
 * API page so an interrupted scan can continue from the unfinished folder/page.
 */
public final class ScanCheckpointStore {

    private static final String PREFS = "miracle_scan_checkpoints";
    private static final String KEY_PREFIX = "index_";
    private static final Gson GSON = new Gson();

    private ScanCheckpointStore() {}

    public static final class FolderCursor {
        public String folderUrl;
        public String pageToken;
        public int pageIndex;

        public FolderCursor() {}

        public FolderCursor(String folderUrl, String pageToken, int pageIndex) {
            this.folderUrl = folderUrl;
            this.pageToken = pageToken == null ? "" : pageToken;
            this.pageIndex = pageIndex;
        }
    }

    public static final class SessionState {
        public String selectionSignature;
        public int selectedRootIndex;
        public String selectedRootPath;
        public List<FolderCursor> queue = new ArrayList<>();
        public List<String> completedFolders = new ArrayList<>();
        public int folders;
        public int files;
        public int videos;
        public int handled;
        public int cached;
        public long updatedAt;
    }

    public static SessionState load(Context context, int indexId, String expectedSignature) {
        if (context == null || indexId <= 0) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_PREFIX + indexId, null);
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            SessionState state = GSON.fromJson(raw, SessionState.class);
            if (state == null) return null;
            if (expectedSignature != null
                    && !expectedSignature.equals(state.selectionSignature)) {
                clear(context, indexId);
                return null;
            }
            if (state.queue == null) state.queue = new ArrayList<>();
            if (state.completedFolders == null) state.completedFolders = new ArrayList<>();
            return state;
        } catch (Exception ignored) {
            clear(context, indexId);
            return null;
        }
    }

    public static void save(Context context, int indexId, SessionState state) {
        if (context == null || indexId <= 0 || state == null) return;
        state.updatedAt = System.currentTimeMillis();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PREFIX + indexId, GSON.toJson(state))
                .commit();
    }

    public static void clear(Context context, int indexId) {
        if (context == null || indexId <= 0) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PREFIX + indexId)
                .apply();
    }

    public static boolean hasCheckpoint(Context context, int indexId) {
        if (context == null || indexId <= 0) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(KEY_PREFIX + indexId);
    }
}
