package com.miracle.kglaynyi.utils;

import android.content.Context;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.IndexLink;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class IndexUtils {

    private static final Map<Integer, GdiJsIndexClient.Progress> SCAN_PROGRESS = new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTUP_REFRESH_STARTED = new AtomicBoolean(false);

    public static void refreshEnabledIndexesOnStartup(Context context) {
        if (context == null || !STARTUP_REFRESH_STARTED.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();

        new Thread(() -> {
            List<IndexLink> indexes = DatabaseClient.getInstance(appContext)
                    .getAppDatabase().indexLinksDao().getAllEnabled();
            if (indexes == null) return;

            for (IndexLink index : indexes) {
                if (index == null) continue;

                // Cached rows stay visible. The scanner compares gd_id + modifiedTime
                // and only reprocesses new or changed files.
                SCAN_PROGRESS.remove(index.getId());
                refreshIndex(appContext, index);

                long deadline = System.currentTimeMillis() + 2L * 60L * 60L * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    GdiJsIndexClient.Progress progress = SCAN_PROGRESS.get(index.getId());
                    if (progress != null && progress.finished) break;
                    try {
                        Thread.sleep(350L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "MiracleStartupRefresh").start();
    }

    public static GdiJsIndexClient.Progress getScanProgress(int indexId) {
        return SCAN_PROGRESS.get(indexId);
    }

    public static boolean isAnyScanRunning() {
        for (GdiJsIndexClient.Progress progress : SCAN_PROGRESS.values()) {
            if (progress != null && !progress.finished) return true;
        }
        return false;
    }

    private static void publishProgress(int indexId, GdiJsIndexClient.ProgressListener listener,
                                        GdiJsIndexClient.Progress progress) {
        SCAN_PROGRESS.put(indexId, progress);
        if (listener != null) listener.onProgress(progress);
    }

    public static boolean refreshIndex(Context mContext, IndexLink indexLink) {
        return refreshIndex(mContext, indexLink, null);
    }

    public static boolean refreshIndex(Context mContext, IndexLink indexLink,
                                       GdiJsIndexClient.ProgressListener listener) {
        Thread thread = new Thread(() -> {
            try {
                String folderType = indexLink.getFolderType();
                String indexType = indexLink.getIndexType();
                String link = indexLink.getLink();
                String user = indexLink.getUsername();
                String pass = indexLink.getPassword();

                IndexLink saved = DatabaseClient.getInstance(mContext)
                        .getAppDatabase().indexLinksDao().find(link);
                if (saved == null) {
                    DatabaseClient.getInstance(mContext)
                            .getAppDatabase().indexLinksDao().insert(indexLink);
                    saved = DatabaseClient.getInstance(mContext)
                            .getAppDatabase().indexLinksDao().find(link);
                }
                if (saved == null) {
                    notifyProgress(listener, GdiJsIndexClient.Progress.failed("Could not find saved index"));
                    return;
                }

                int id = saved.getId();

                // GDI-JS root indexes are mixed libraries in current Miracle.
                // Migrate older saved Movies/TVShows choices so one refresh handles both.
                if ("GDI-JS".equals(indexType) && !"Movies + TV Shows".equals(folderType)) {
                    folderType = "Movies + TV Shows";
                    DatabaseClient.getInstance(mContext).getAppDatabase()
                            .indexLinksDao().updateFolderType(id, folderType);
                    saved.setFolderType(folderType);
                }

                boolean tvShows = "TVShows".equals(folderType);

                if (!"GDI-JS".equals(indexType)) {
                    publishProgress(id, listener, GdiJsIndexClient.Progress.failed(
                            "Only GDI-JS indexes are supported."));
                    return;
                }

                List<String> selectedFolders =
                        IndexFolderSelectionUtils.parse(saved.getSelectedFoldersJson());

                // Existing pre-folder-selection indexes remain compatible and scan
                // the root once until the user explicitly chooses folders.
                if (selectedFolders == null) {
                    selectedFolders = java.util.Collections.singletonList("/");
                }

                if (selectedFolders.isEmpty()) {
                    publishProgress(id, listener, GdiJsIndexClient.Progress.failed(
                            "No folders selected. Open Manage Sources → Folders."));
                    return;
                }

                GdiJsIndexClient.scanSelectedFolders(
                        link, user, pass, tvShows, id, selectedFolders,
                        progress -> publishProgress(id, listener, progress));
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                publishProgress(indexLink.getId(), listener, GdiJsIndexClient.Progress.failed(
                        "Refresh failed • " + message));
                System.out.println("Index refresh failed: " + e);
            }
        });
        thread.start();
        return thread.isAlive();
    }

    private static void notifyProgress(GdiJsIndexClient.ProgressListener listener,
                                       GdiJsIndexClient.Progress progress) {
        if (listener != null) listener.onProgress(progress);
    }

    public static boolean deleteIndex(Context mContext, IndexLink indexLink) {
        Thread thread = new Thread(() -> {
            if (indexLink.getFolderType().equals("Movies") || indexLink.getFolderType().equals("Movies + TV Shows")) {
                DatabaseClient.getInstance(mContext)
                        .getAppDatabase()
                        .movieDao()
                        .deleteAllFromthisIndex(indexLink.getId());
            }
            if (indexLink.getFolderType().equals("TVShows") || indexLink.getFolderType().equals("Movies + TV Shows")) {
                DatabaseClient.getInstance(mContext)
                        .getAppDatabase()
                        .episodeDao()
                        .deleteAllFromThisIndex(indexLink.getId());

                List<TVShowSeasonDetails> seasonsList = DatabaseClient
                        .getInstance(mContext)
                        .getAppDatabase()
                        .tvShowSeasonDetailsDao()
                        .getAll();

                for (TVShowSeasonDetails season : seasonsList) {
                    List<Episode> episodeList = DatabaseClient
                            .getInstance(mContext)
                            .getAppDatabase()
                            .episodeDao()
                            .getFromSeasonOnly(season.getId());
                    if (episodeList == null || episodeList.size() == 0) {
                        DatabaseClient.getInstance(mContext).getAppDatabase()
                                .tvShowSeasonDetailsDao().deleteById(season.getId());
                    }
                }

                List<TVShow> tvShowList = DatabaseClient
                        .getInstance(mContext)
                        .getAppDatabase()
                        .tvShowDao().getAll();

                for (TVShow tvShow : tvShowList) {
                    List<TVShowSeasonDetails> seasonsInThisShow = DatabaseClient
                            .getInstance(mContext)
                            .getAppDatabase()
                            .tvShowSeasonDetailsDao()
                            .findByShowId(tvShow.getId());
                    if (seasonsInThisShow == null || seasonsInThisShow.size() == 0) {
                        DatabaseClient.getInstance(mContext).getAppDatabase()
                                .tvShowDao().deleteById(tvShow.getId());
                    }
                }
            }
            DatabaseClient.getInstance(mContext)
                    .getAppDatabase()
                    .indexLinksDao()
                    .deleteById(indexLink.getId());

        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return thread.isAlive();
    }

    public static void replaceSelectedFoldersAndRefresh(
            Context context, IndexLink indexLink, List<String> folders,
            GdiJsIndexClient.ProgressListener listener) {
        if (context == null || indexLink == null) return;

        new Thread(() -> {
            try {
                List<String> safeFolders = folders == null
                        ? new java.util.ArrayList<>()
                        : new java.util.ArrayList<>(folders);

                if (safeFolders.isEmpty()) {
                    publishProgress(indexLink.getId(), listener,
                            GdiJsIndexClient.Progress.failed(
                                    "Choose at least one folder before scanning."));
                    return;
                }

                String json = IndexFolderSelectionUtils.encode(safeFolders);

                // Folder membership is not stored on old media rows, so a changed
                // selection gets one clean rescan. Normal refreshes remain cached.
                GdiJsIndexClient.clearIndexMediaForRescan(indexLink.getId());
                DatabaseClient.getInstance(context).getAppDatabase().indexLinksDao()
                        .updateSelectedFolders(indexLink.getId(), json);
                indexLink.setSelectedFoldersJson(json);
                indexLink.setFolderType("Movies + TV Shows");
                DatabaseClient.getInstance(context).getAppDatabase().indexLinksDao()
                        .updateFolderType(indexLink.getId(), "Movies + TV Shows");

                GdiJsIndexClient.scanSelectedFolders(
                        indexLink.getLink(), indexLink.getUsername(), indexLink.getPassword(),
                        false, indexLink.getId(), safeFolders,
                        progress -> publishProgress(indexLink.getId(), listener, progress));
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                publishProgress(indexLink.getId(), listener,
                        GdiJsIndexClient.Progress.failed("Folder scan failed • " + message));
            }
        }, "MiracleFolderSelectionScan").start();
    }

    public static void purgeUnsupportedSources(Context context) {
        if (context == null) return;
        List<IndexLink> all = DatabaseClient.getInstance(context)
                .getAppDatabase().indexLinksDao().getAll();
        if (all == null) return;
        for (IndexLink source : new java.util.ArrayList<>(all)) {
            if (source == null || "GDI-JS".equals(source.getIndexType())) continue;
            deleteIndex(context, source);
        }
    }

    public static int getNoOfMedia(Context mContext, IndexLink t) {
        final int[] result = new int[]{0};
        Thread thread = new Thread(() -> {
            if (t.getFolderType() != null && (t.getFolderType().equals("Movies") || t.getFolderType().equals("Movies + TV Shows"))) {
                result[0] = DatabaseClient.getInstance(mContext)
                        .getAppDatabase().movieDao().getNoOfMovies(t.getId());
            }
            if (t.getFolderType() != null && t.getFolderType().equals("TVShows")) {
                result[0] = DatabaseClient.getInstance(mContext)
                        .getAppDatabase().episodeDao().getNoOfShows(t.getId());
            } else if (t.getFolderType() != null && t.getFolderType().equals("Movies + TV Shows")) {
                result[0] += DatabaseClient.getInstance(mContext)
                        .getAppDatabase().episodeDao().getNoOfShows(t.getId());
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    public static void disableIndex(Context mContext, IndexLink indexLink) {
        new Thread(() -> {
            if (indexLink.getFolderType().equals("Movies") || indexLink.getFolderType().equals("Movies + TV Shows")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .movieDao().disableFromThisIndex(indexLink.getId());
            }
            if (indexLink.getFolderType().equals("TVShows") || indexLink.getFolderType().equals("Movies + TV Shows")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .episodeDao().disableFromThisIndex(indexLink.getId());
            }
            DatabaseClient.getInstance(mContext).getAppDatabase()
                    .indexLinksDao().disableIndex(indexLink.getId());
        }).start();
    }

    public static void enableIndex(Context mContext, IndexLink indexLink) {
        new Thread(() -> {
            if (indexLink.getFolderType().equals("Movies") || indexLink.getFolderType().equals("Movies + TV Shows")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .movieDao().enableFromThisIndex(indexLink.getId());
            }
            if (indexLink.getFolderType().equals("TVShows") || indexLink.getFolderType().equals("Movies + TV Shows")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .episodeDao().enableFromThisIndex(indexLink.getId());
            }
            DatabaseClient.getInstance(mContext).getAppDatabase()
                    .indexLinksDao().enableIndex(indexLink.getId());
        }).start();
    }
}
