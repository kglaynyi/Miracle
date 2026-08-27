package com.miracle.kglaynyi.utils;

import static com.miracle.kglaynyi.utils.SendGetRequestTMDB.sendGet2;
import static com.miracle.kglaynyi.utils.SendGetRequestTMDB.sendGetTVShow;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans a user-selected Storage Access Framework tree. When Google Drive is
 * selected in Android's system folder picker, the persisted tree permission is
 * the login/authorization and no Google API key or OAuth client secret is needed.
 */
public final class GoogleDriveFolderClient {
    private static final int MAX_FOLDERS = 10000;

    private GoogleDriveFolderClient() {}

    private static final class CachedEntry {
        final boolean tv;
        final Date modified;

        CachedEntry(boolean tv, Date modified) {
            this.tv = tv;
            this.modified = modified;
        }
    }

    private static final class Stats {
        int folders;
        int files;
        int videos;
        int cached;
    }

    public static int scan(Context context, String treeUriString, int indexId,
                           GdiJsIndexClient.ProgressListener listener) throws Exception {
        if (context == null) throw new IOException("Context is unavailable");
        if (treeUriString == null || treeUriString.trim().isEmpty()) {
            throw new IOException("Google Drive folder permission is missing");
        }

        Uri treeUri = Uri.parse(treeUriString);
        ContentResolver resolver = context.getContentResolver();
        String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            throw new IOException("Selected Google Drive folder is no longer available", e);
        }

        emit(listener, GdiJsIndexClient.Progress.status(
                "Opening selected Google Drive folder…", -1, 0, 0, 0, 0, 0, 0));

        Map<String, CachedEntry> cache = buildCache(context, indexId);
        Set<String> seen = new HashSet<>();
        Stats stats = new Stats();

        String rootPath = "/" + getFolderDisplayName(context, treeUri);
        scanFolder(context, resolver, treeUri, rootId, rootPath, indexId,
                cache, seen, stats, listener);

        pruneMissing(context, cache, seen);
        emit(listener, GdiJsIndexClient.Progress.done(
                "Done • " + stats.videos + " videos • " + stats.cached + " reused from cache",
                stats.videos, stats.folders, stats.files));
        return stats.videos;
    }

    public static String getFolderDisplayName(Context context, Uri treeUri) {
        if (context == null || treeUri == null) return "Selected folder";
        Cursor cursor = null;
        try {
            String id = DocumentsContract.getTreeDocumentId(treeUri);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
            cursor = context.getContentResolver().query(docUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "Selected folder";
    }

    private static void scanFolder(Context context, ContentResolver resolver, Uri treeUri,
                                   String parentDocumentId, String relativePath, int indexId,
                                   Map<String, CachedEntry> cache, Set<String> seen,
                                   Stats stats, GdiJsIndexClient.ProgressListener listener)
            throws Exception {
        if (stats.folders >= MAX_FOLDERS) {
            throw new IOException("Selected Drive folder contains too many folders to scan safely");
        }
        stats.folders++;

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, parentDocumentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };

        Cursor cursor = null;
        try {
            cursor = resolver.query(childrenUri, projection, null, null, null);
            if (cursor == null) throw new IOException("Google Drive returned an empty folder result");

            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
            int modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED);

            while (cursor.moveToNext()) {
                String documentId = cursor.getString(idColumn);
                String name = cursor.getString(nameColumn);
                String mime = cursor.getString(mimeColumn);
                long size = cursor.isNull(sizeColumn) ? 0L : cursor.getLong(sizeColumn);
                long modifiedMs = cursor.isNull(modifiedColumn) ? 0L : cursor.getLong(modifiedColumn);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    String childPath = relativePath + "/" + (name == null ? "" : name);
                    scanFolder(context, resolver, treeUri, documentId, childPath, indexId,
                            cache, seen, stats, listener);
                    continue;
                }

                stats.files++;
                if (!isVideo(name, mime)) continue;
                stats.videos++;

                String stableId = "saf:" + documentId;
                seen.add(stableId);
                boolean tv = GdiJsIndexClient.shouldTreatAsTv(relativePath, name, false);
                String sizeString = String.valueOf(Math.max(0L, size));
                Date modified = modifiedMs > 0 ? new Date(modifiedMs) : null;

                if (name != null) {
                    if (tv) {
                        DatabaseClient.getInstance(context).getAppDatabase().episodeDao()
                                .deleteDuplicateSources(indexId, name, sizeString, stableId);
                    } else {
                        DatabaseClient.getInstance(context).getAppDatabase().movieDao()
                                .deleteDuplicateSources(indexId, name, sizeString, stableId);
                    }
                }

                CachedEntry old = cache.get(stableId);
                if (old != null && old.tv == tv && !isRemoteNewer(modified, old.modified)) {
                    stats.cached++;
                    emitProgress(listener, stats);
                    continue;
                }

                if (old != null) {
                    if (old.tv) {
                        DatabaseClient.getInstance(context).getAppDatabase().episodeDao()
                                .deleteByGdId(stableId);
                    } else {
                        DatabaseClient.getInstance(context).getAppDatabase().movieDao()
                                .deleteByGdId(stableId);
                    }
                } else if (tv) {
                    DatabaseClient.getInstance(context).getAppDatabase().movieDao()
                            .deleteByGdId(stableId);
                } else {
                    DatabaseClient.getInstance(context).getAppDatabase().episodeDao()
                            .deleteByGdId(stableId);
                }

                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                if (tv) {
                    Episode episode = new Episode();
                    episode.setFileName(name);
                    episode.setMimeType(mime);
                    episode.setModifiedTime(modified);
                    episode.setSize(sizeString);
                    episode.setUrlString(documentUri.toString());
                    episode.setGd_id(stableId);
                    episode.setIndex_id(indexId);
                    sendGetTVShow(episode);
                } else {
                    Movie movie = new Movie();
                    movie.setFileName(name);
                    movie.setMimeType(mime);
                    movie.setModifiedTime(modified);
                    movie.setSize(sizeString);
                    movie.setUrlString(documentUri.toString());
                    movie.setGd_id(stableId);
                    movie.setIndex_id(indexId);
                    if (GdiJsIndexClient.isTmdbConfigured()) {
                        sendGet2(movie);
                    } else {
                        String title = GdiJsIndexClient.fallbackMovieTitle(name);
                        movie.setTitle(title);
                        movie.setOriginal_title(title);
                        DatabaseClient.getInstance(context).getAppDatabase().movieDao().insert(movie);
                    }
                }

                cache.put(stableId, new CachedEntry(tv, modified));
                emitProgress(listener, stats);
            }
        } catch (SecurityException e) {
            throw new IOException("Google Drive permission expired. Re-add the folder source.", e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static void emitProgress(GdiJsIndexClient.ProgressListener listener, Stats stats) {
        emit(listener, GdiJsIndexClient.Progress.status(
                "Scanning Drive… " + stats.folders + " folders • "
                        + stats.files + " files • " + stats.videos + " videos • "
                        + stats.cached + " cached",
                -1, stats.videos, stats.videos, 0, stats.folders, stats.files, stats.videos));
    }

    private static Map<String, CachedEntry> buildCache(Context context, int indexId) {
        Map<String, CachedEntry> result = new HashMap<>();
        List<Movie> movies = DatabaseClient.getInstance(context).getAppDatabase()
                .movieDao().getAllFromIndex(indexId);
        if (movies != null) {
            for (Movie movie : movies) {
                if (movie == null || movie.getGd_id() == null || movie.getGd_id().trim().isEmpty()) continue;
                result.put(movie.getGd_id(), new CachedEntry(false, movie.getModifiedTime()));
            }
        }

        List<Episode> episodes = DatabaseClient.getInstance(context).getAppDatabase()
                .episodeDao().getAllFromIndex(indexId);
        if (episodes != null) {
            for (Episode episode : episodes) {
                if (episode == null || episode.getGd_id() == null || episode.getGd_id().trim().isEmpty()) continue;
                result.put(episode.getGd_id(), new CachedEntry(true, episode.getModifiedTime()));
            }
        }
        return result;
    }

    private static void pruneMissing(Context context, Map<String, CachedEntry> cache, Set<String> seen) {
        for (Map.Entry<String, CachedEntry> entry : cache.entrySet()) {
            String id = entry.getKey();
            if (id == null || !id.startsWith("saf:") || seen.contains(id)) continue;
            if (entry.getValue().tv) {
                DatabaseClient.getInstance(context).getAppDatabase().episodeDao().deleteByGdId(id);
            } else {
                DatabaseClient.getInstance(context).getAppDatabase().movieDao().deleteByGdId(id);
            }
        }
    }

    private static boolean isRemoteNewer(Date remote, Date cached) {
        if (remote == null || cached == null) return false;
        return remote.after(cached);
    }

    private static boolean isVideo(String name, String mime) {
        if (mime != null && mime.toLowerCase().startsWith("video/")) return true;
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".avi")
                || lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.endsWith(".webm")
                || lower.endsWith(".mpeg") || lower.endsWith(".mpg") || lower.endsWith(".ts")
                || lower.endsWith(".m2ts") || lower.endsWith(".3gp") || lower.endsWith(".wmv");
    }

    private static void emit(GdiJsIndexClient.ProgressListener listener,
                             GdiJsIndexClient.Progress progress) {
        if (listener != null) listener.onProgress(progress);
    }
}
