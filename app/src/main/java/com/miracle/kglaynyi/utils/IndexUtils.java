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

public class IndexUtils {

    public static boolean refreshIndex(Context mContext, IndexLink indexLink) {
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
                if (saved == null) return;

                int id = saved.getId();
                boolean tvShows = "TVShows".equals(folderType);

                if ("GDI-JS".equals(indexType)) {
                    GdiJsIndexClient.scan(link, user, pass, tvShows, id);
                    return;
                }

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
            } catch (Exception e) {
                System.out.println("Index refresh failed: " + e);
            }
        });
        thread.start();
        return thread.isAlive();
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

    static int noOfMedia = 0;

    public static int getNoOfMedia(Context mContext, IndexLink t) {
        Thread thread = new Thread(() -> {
            noOfMedia = 0;
            if (t.getFolderType() != null && t.getFolderType().equals("Movies")) {
                noOfMedia = DatabaseClient.getInstance(mContext)
                        .getAppDatabase().movieDao().getNoOfMovies(t.getId());
            }
            if (t.getFolderType() != null && t.getFolderType().equals("TVShows")) {
                noOfMedia = DatabaseClient.getInstance(mContext)
                        .getAppDatabase().episodeDao().getNoOfShows(t.getId());
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return noOfMedia;
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
