package com.miracle.kglaynyi.utils;

import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestGDIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestGoIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestMapleIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestSimpleProgramIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.resetPagingState;

import android.content.Context;

import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.IndexLink;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IndexUtils {

    private static final Map<Integer, GdiJsIndexClient.Progress> SCAN_PROGRESS = new ConcurrentHashMap<>();

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
                boolean tvShows = "TVShows".equals(folderType);

                if ("GDI-JS".equals(indexType)) {
                    GdiJsIndexClient.scan(link, user, pass, tvShows, id, progress -> publishProgress(id, listener, progress));
                    return;
                }

                publishProgress(id, listener, GdiJsIndexClient.Progress.status(
                        "Refreshing index…", -1, 0, 0, 0, 0, 0, 0));

                resetPagingState();
                if ("GDIndex".equals(indexType)) {
                    postRequestGDIndex(link, user, pass, tvShows, id);
                } else if ("GoIndex".equals(indexType)) {
                    postRequestGoIndex(link, user, pass, tvShows, id);
                } else if ("MapleIndex".equals(indexType) || "Maple".equals(indexType)) {
                    postRequestMapleIndex(link, user, pass, tvShows, id);
                } else if ("SimpleProgram".equals(indexType)) {
                    postRequestSimpleProgramIndex(link, user, pass, tvShows, id);
                }

                int count = getNoOfMedia(mContext, saved);
                publishProgress(id, listener, GdiJsIndexClient.Progress.done(
                        "Refresh complete • " + count + " items", count, 0, count));
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
            if (indexLink.getFolderType().equals("Movies")) {
                DatabaseClient.getInstance(mContext)
                        .getAppDatabase()
                        .movieDao()
                        .deleteAllFromthisIndex(indexLink.getId());
            }
            if (indexLink.getFolderType().equals("TVShows")) {
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

    public static int getNoOfMedia(Context mContext, IndexLink t) {
        final int[] result = new int[]{0};
        Thread thread = new Thread(() -> {
            if (t.getFolderType() != null && t.getFolderType().equals("Movies")) {
                result[0] = DatabaseClient.getInstance(mContext)
                        .getAppDatabase().movieDao().getNoOfMovies(t.getId());
            }
            if (t.getFolderType() != null && t.getFolderType().equals("TVShows")) {
                result[0] = DatabaseClient.getInstance(mContext)
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
            if (indexLink.getFolderType().equals("Movies")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .movieDao().disableFromThisIndex(indexLink.getId());
            }
            if (indexLink.getFolderType().equals("TVShows")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .episodeDao().disableFromThisIndex(indexLink.getId());
            }
            DatabaseClient.getInstance(mContext).getAppDatabase()
                    .indexLinksDao().disableIndex(indexLink.getId());
        }).start();
    }

    public static void enableIndex(Context mContext, IndexLink indexLink) {
        new Thread(() -> {
            if (indexLink.getFolderType().equals("Movies")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .movieDao().enableFromThisIndex(indexLink.getId());
            }
            if (indexLink.getFolderType().equals("TVShows")) {
                DatabaseClient.getInstance(mContext).getAppDatabase()
                        .episodeDao().enableFromThisIndex(indexLink.getId());
            }
            DatabaseClient.getInstance(mContext).getAppDatabase()
                    .indexLinksDao().enableIndex(indexLink.getId());
        }).start();
    }
}
