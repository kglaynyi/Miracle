package com.miracle.kglaynyi.utils;

import static com.miracle.kglaynyi.Constants.TMDB_API_KEY;
import static com.miracle.kglaynyi.MainActivity.context;
import static com.miracle.kglaynyi.utils.SendGetRequestTMDB.sendGet2;
import static com.miracle.kglaynyi.utils.SendGetRequestTMDB.sendGetTVShow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.File;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.ResFormat;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client for GDI-JS / GoogleDriveIndex 2.x installations that use the
 * username/password login form and a session cookie instead of HTTP Basic Auth.
 */
public final class GdiJsIndexClient {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_PAGES_PER_FOLDER = 1000;
    private static final int MAX_FOLDERS = 10000;
    private static final int NETWORK_RETRY_COUNT = 4;
    private static final long NETWORK_RETRY_BASE_DELAY_MS = 2000L;
    private static final String DEFAULT_DRIVE_PREFIX = "0:/";

    private GdiJsIndexClient() {}

    public interface ProgressListener {
        void onProgress(Progress progress);
    }

    public static final class Progress {
        public final String message;
        public final int percent;
        public final int processed;
        public final int total;
        public final int remaining;
        public final int folders;
        public final int files;
        public final int videos;
        public final boolean finished;
        public final boolean error;

        private Progress(String message, int percent, int processed, int total,
                         int remaining, int folders, int files, int videos,
                         boolean finished, boolean error) {
            this.message = message;
            this.percent = percent;
            this.processed = processed;
            this.total = total;
            this.remaining = remaining;
            this.folders = folders;
            this.files = files;
            this.videos = videos;
            this.finished = finished;
            this.error = error;
        }

        public static Progress status(String message, int percent, int processed,
                                      int total, int remaining, int folders,
                                      int files, int videos) {
            return new Progress(message, percent, processed, total, remaining,
                    folders, files, videos, false, false);
        }

        public static Progress done(String message, int total, int folders, int files) {
            return new Progress(message, 100, total, total, 0,
                    folders, files, total, true, false);
        }

        public static Progress failed(String message) {
            return new Progress(message, -1, 0, 0, 0,
                    0, 0, 0, true, true);
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static Result ok() {
            return new Result(true, "GDI-JS connection verified");
        }

        public static Result error(String message) {
            return new Result(false, message);
        }
    }

    private static final class Session {
        final boolean success;
        final String cookie;
        final String message;

        Session(boolean success, String cookie, String message) {
            this.success = success;
            this.cookie = cookie;
            this.message = message;
        }
    }

    private static final class CachedEntry {
        final boolean tv;
        final Date modifiedTime;

        CachedEntry(boolean tv, Date modifiedTime) {
            this.tv = tv;
            this.modifiedTime = modifiedTime;
        }
    }


    public static Result validate(String rawBaseUrl, String username, String password) {
        try {
            String baseUrl = normalizeBaseUrl(rawBaseUrl);
            Session session = login(baseUrl, username, password);
            if (!session.success) return Result.error(session.message);

            ResFormat root = fetchPage(getDefaultApiRoot(baseUrl), session.cookie, "", 0);
            if (root == null || root.getData() == null) {
                return Result.error("Login succeeded, but /0:/ returned an invalid file list");
            }
            return Result.ok();
        } catch (java.net.MalformedURLException e) {
            return Result.error("Invalid index URL");
        } catch (java.net.SocketTimeoutException e) {
            return Result.error("Index connection timed out");
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
            return Result.error("Cannot connect to GDI-JS index: " + message);
        }
    }

    public static int scan(String rawBaseUrl, String username, String password,
                           boolean tvShows, int indexId) throws Exception {
        return scan(rawBaseUrl, username, password, tvShows, indexId, null);
    }

    public static int scan(String rawBaseUrl, String username, String password,
                           boolean tvShows, int indexId, ProgressListener listener) throws Exception {
        emit(listener, Progress.status("Connecting to index…", -1,
                0, 0, 0, 0, 0, 0));

        String baseUrl = normalizeBaseUrl(rawBaseUrl);
        Session session = loginWithRetry(baseUrl, username, password, listener);
        if (!session.success) throw new IOException(session.message);

        emit(listener, Progress.status("Login verified. Scanning library…", -1,
                0, 0, 0, 0, 0, 0));

        String apiRoot = getDefaultApiRoot(baseUrl);
        Set<String> visitedFolders = new HashSet<>();
        Set<String> seenIds = new HashSet<>();
        Map<String, CachedEntry> cache = buildScanCache(indexId);
        int[] stats = new int[]{0, 0, 0, 0, 0}; // folders, files, videos, handled, cached

        emit(listener, Progress.status("Loaded " + cache.size()
                        + " cached media entries • scanning for changes…", -1,
                0, 0, 0, 0, 0, 0));

        discoverAndProcessFolder(apiRoot, baseUrl, session.cookie, visitedFolders,
                seenIds, cache, stats, indexId, tvShows, listener);

        pruneMissingCachedMedia(cache, seenIds);
        int total = stats[2];
        emit(listener, Progress.done("Done • " + total + " videos • "
                        + stats[4] + " reused from cache",
                total, stats[0], stats[1]));
        return total;
    }

    private static void discoverAndProcessFolder(String folderUrl, String rootUrl, String cookie,
                                                 Set<String> visitedFolders, Set<String> seenIds,
                                                 Map<String, CachedEntry> cache, int[] stats,
                                                 int indexId, boolean selectedTvShows,
                                                 ProgressListener listener) throws Exception {
        if (stats[0] >= MAX_FOLDERS) {
            throw new IOException("Index contains too many folders to scan safely");
        }
        if (!visitedFolders.add(folderUrl)) return;
        stats[0]++;

        String pageToken = "";
        int pageIndex = 0;
        for (int page = 0; page < MAX_PAGES_PER_FOLDER; page++) {
            ResFormat result = fetchPageWithRetry(folderUrl, cookie, pageToken, pageIndex,
                    stats, listener);
            if (result == null || result.getData() == null) return;

            List<File> files = result.getData().getFiles();
            if (files != null) {
                for (File file : files) {
                    if (file == null) continue;

                    if (isFolder(file)) {
                        String child = appendPath(folderUrl, file.getName(), true);
                        discoverAndProcessFolder(child, rootUrl, cookie, visitedFolders,
                                seenIds, cache, stats, indexId, selectedTvShows, listener);
                        continue;
                    }

                    stats[1]++;
                    if (!isVideoFile(file)) continue;

                    stats[2]++;
                    String gdId = file.getId();
                    if (gdId != null && !gdId.trim().isEmpty()) seenIds.add(gdId);
                    boolean reused = processVideo(rootUrl, folderUrl, file,
                            selectedTvShows, indexId, cache);
                    stats[3]++;
                    if (reused) stats[4]++;

                    String message = "Scanning… " + stats[0] + " folders • "
                            + stats[1] + " files • " + stats[2] + " videos • "
                            + stats[4] + " cached";
                    emit(listener, Progress.status(message, -1, stats[3], stats[2],
                            Math.max(0, stats[2] - stats[3]), stats[0], stats[1], stats[2]));
                }
            }

            String next = result.getNextPageToken();
            if (next == null || next.trim().isEmpty()) break;
            pageToken = next;
            pageIndex++;
        }
    }

    private static Session loginWithRetry(String baseUrl, String username, String password,
                                          ProgressListener listener) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= NETWORK_RETRY_COUNT; attempt++) {
            try {
                return login(baseUrl, username, password);
            } catch (Exception e) {
                if (!isTransientNetworkError(e) || attempt == NETWORK_RETRY_COUNT) throw e;
                last = e;
                long delay = NETWORK_RETRY_BASE_DELAY_MS * attempt;
                emit(listener, Progress.status("Network/DNS unavailable • retry "
                                + attempt + "/" + NETWORK_RETRY_COUNT + " in "
                                + (delay / 1000) + "s", -1,
                        0, 0, 0, 0, 0, 0));
                sleepRetry(delay);
            }
        }
        throw last == null ? new IOException("Unable to connect to index") : last;
    }

    private static ResFormat fetchPageWithRetry(String folderUrl, String cookie,
                                                String pageToken, int pageIndex,
                                                int[] stats, ProgressListener listener) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= NETWORK_RETRY_COUNT; attempt++) {
            try {
                return fetchPage(folderUrl, cookie, pageToken, pageIndex);
            } catch (Exception e) {
                if (!isTransientNetworkError(e) || attempt == NETWORK_RETRY_COUNT) {
                    if (isTransientNetworkError(e)) {
                        throw new IOException("Network/DNS unavailable after "
                                + NETWORK_RETRY_COUNT + " retries. "
                                + stats[3] + " videos were already processed and kept. "
                                + "Tap refresh to resume.", e);
                    }
                    throw e;
                }
                last = e;
                long delay = NETWORK_RETRY_BASE_DELAY_MS * attempt;
                emit(listener, Progress.status("Network/DNS issue • retry "
                                + attempt + "/" + NETWORK_RETRY_COUNT + " in "
                                + (delay / 1000) + "s • " + stats[3]
                                + " videos kept", -1,
                        stats[3], stats[2], Math.max(0, stats[2] - stats[3]),
                        stats[0], stats[1], stats[2]));
                sleepRetry(delay);
            }
        }
        throw last == null ? new IOException("Unable to read index") : last;
    }

    private static boolean isTransientNetworkError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void sleepRetry(long delayMs) throws IOException {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Scan interrupted", e);
        }
    }

    private static Session login(String baseUrl, String username, String password) throws Exception {
        URL url = new URL(baseUrl + "login");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String form = "username=" + URLEncoder.encode(username == null ? "" : username, "UTF-8")
                    + "&password=" + URLEncoder.encode(password == null ? "" : password, "UTF-8");
            byte[] body = form.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));
            conn.getOutputStream().write(body);

            int code = conn.getResponseCode();
            String response = readBody(conn, code);
            if (code < 200 || code >= 300) {
                return new Session(false, null, "Login endpoint returned HTTP " + code);
            }

            JsonObject json;
            try {
                json = JsonParser.parseString(response).getAsJsonObject();
            } catch (Exception e) {
                return new Session(false, null,
                        "This URL does not appear to use the GDI-JS login API");
            }

            boolean ok = json.has("ok") && json.get("ok").getAsBoolean();
            if (!ok) {
                String message = json.has("message") ? json.get("message").getAsString()
                        : "Invalid username or password";
                return new Session(false, null, message);
            }

            String cookie = extractSessionCookie(conn.getHeaderField("Set-Cookie"));
            if (cookie == null) {
                return new Session(false, null,
                        "Login succeeded but no session cookie was returned");
            }
            return new Session(true, cookie, "OK");
        } finally {
            conn.disconnect();
        }
    }

    private static ResFormat fetchPage(String folderUrl, String cookie,
                                       String pageToken, int pageIndex) throws Exception {
        String urlString = folderUrl.endsWith("/") ? folderUrl : folderUrl + "/";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cookie", cookie);
            conn.setDoOutput(true);

            JsonObject request = new JsonObject();
            request.addProperty("page_token", pageToken == null ? "" : pageToken);
            request.addProperty("page_index", pageIndex);
            request.addProperty("password", "");
            conn.getOutputStream().write(request.toString().getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            String response = readBody(conn, code);
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED
                    || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new IOException("GDI-JS session was rejected (HTTP " + code + ")");
            }
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                throw new IOException("GDI-JS API redirected to "
                        + (location == null ? "another page" : location));
            }
            if (code < 200 || code >= 300) {
                throw new IOException("Index returned HTTP " + code);
            }

            String trimmed = response == null ? "" : response.trim();
            if (trimmed.isEmpty()) {
                throw new IOException("Index returned an empty file-list response");
            }
            if (trimmed.startsWith("<") || trimmed.toLowerCase().startsWith("<!doctype")) {
                throw new IOException("Index returned HTML instead of JSON at " + urlString);
            }

            try {
                JsonObject envelope = JsonParser.parseString(trimmed).getAsJsonObject();
                if (envelope.has("ok") && !envelope.get("ok").getAsBoolean()
                        && envelope.has("message")) {
                    throw new IOException(envelope.get("message").getAsString());
                }
            } catch (IllegalStateException ignored) {
                // Normal file-list response is parsed below.
            }

            ResFormat result;
            try {
                result = new Gson().fromJson(trimmed, ResFormat.class);
            } catch (Exception e) {
                throw new IOException("GDI-JS returned malformed JSON at " + urlString, e);
            }
            if (result == null || result.getData() == null) {
                throw new IOException("Index returned an invalid file-list response at " + urlString);
            }
            return result;
        } finally {
            conn.disconnect();
        }
    }

    private static void clearIndexMediaForRescan(int indexId) {
        DatabaseClient db = DatabaseClient.getInstance(context);
        db.getAppDatabase().movieDao().deleteAllFromthisIndex(indexId);
        db.getAppDatabase().episodeDao().deleteAllFromThisIndex(indexId);

        // Season/show rows are shared metadata, so only remove them when no episode
        // from any enabled index still references them.
        List<TVShowSeasonDetails> seasons = db.getAppDatabase().tvShowSeasonDetailsDao().getAll();
        if (seasons != null) {
            for (TVShowSeasonDetails season : seasons) {
                List<Episode> episodes = db.getAppDatabase().episodeDao().getFromSeasonOnly(season.getId());
                if (episodes == null || episodes.isEmpty()) {
                    db.getAppDatabase().tvShowSeasonDetailsDao().deleteById(season.getId());
                }
            }
        }

        List<TVShow> shows = db.getAppDatabase().tvShowDao().getAll();
        if (shows != null) {
            for (TVShow show : shows) {
                List<TVShowSeasonDetails> showSeasons = db.getAppDatabase()
                        .tvShowSeasonDetailsDao().findByShowId(show.getId());
                if (showSeasons == null || showSeasons.isEmpty()) {
                    db.getAppDatabase().tvShowDao().deleteById(show.getId());
                }
            }
        }
    }

    private static void saveDiscoveredPlaceholder(String folderUrl, File file, int indexId) {
    if (file == null) return;
    String gdId = file.getId();
    Movie existing = (gdId == null || gdId.trim().isEmpty()) ? null :
            DatabaseClient.getInstance(context).getAppDatabase().movieDao().getByGdId(gdId);
    if (existing != null) return;

    Movie movie = new Movie();
    movie.setFileName(file.getName());
    movie.setMimeType(file.getMimeType());
    movie.setModifiedTime(file.getModifiedTime());
    movie.setSize(file.getSize());
    movie.setUrlString(resolveFileUrl(folderUrl, folderUrl, file));
    movie.setGd_id(gdId == null ? "" : gdId);
    movie.setIndex_id(indexId);
    String title = fallbackMovieTitle(file.getName());
    movie.setTitle(title);
    movie.setOriginal_title(title);
    DatabaseClient.getInstance(context).getAppDatabase().movieDao().insert(movie);
}

    private static boolean processVideo(String rootUrl, String folderUrl, File file,
                                        boolean tvShows, int indexId,
                                        Map<String, CachedEntry> cache) {
        String id = file.getId();
        boolean treatAsTv = shouldTreatAsTv(folderUrl, file.getName(), tvShows);

        if (id != null && !id.trim().isEmpty()) {
            // GDI-JS signed download URLs can change between scans. The stable Drive
            // id is the source identity; remove older rows for the same file that
            // were created before gd_id-based caching existed.
            if (file.getName() != null && file.getSize() != null) {
                if (treatAsTv) {
                    DatabaseClient.getInstance(context).getAppDatabase().episodeDao()
                            .deleteDuplicateSources(indexId, file.getName(), file.getSize(), id);
                } else {
                    DatabaseClient.getInstance(context).getAppDatabase().movieDao()
                            .deleteDuplicateSources(indexId, file.getName(), file.getSize(), id);
                }
            }

            CachedEntry cached = cache.get(id);
            if (cached != null && cached.tv == treatAsTv && !isRemoteNewer(file.getModifiedTime(), cached.modifiedTime)) {
                return true;
            }

            if (cached != null) {
                if (cached.tv) {
                    DatabaseClient.getInstance(context).getAppDatabase().episodeDao().deleteByGdId(id);
                } else {
                    DatabaseClient.getInstance(context).getAppDatabase().movieDao().deleteByGdId(id);
                }
                cache.remove(id);
            } else if (treatAsTv) {
                DatabaseClient.getInstance(context).getAppDatabase().movieDao().deleteByGdId(id);
            } else {
                DatabaseClient.getInstance(context).getAppDatabase().episodeDao().deleteByGdId(id);
            }
        }

        String streamUrl = resolveFileUrl(rootUrl, folderUrl, file);
        if (treatAsTv) {
            Episode episode = new Episode();
            episode.setFileName(file.getName());
            episode.setMimeType(file.getMimeType());
            episode.setModifiedTime(file.getModifiedTime());
            episode.setSize(file.getSize());
            episode.setUrlString(streamUrl);
            episode.setGd_id(id == null ? "" : id);
            episode.setIndex_id(indexId);
            sendGetTVShow(episode);
        } else {
            Movie movie = new Movie();
            movie.setFileName(file.getName());
            movie.setMimeType(file.getMimeType());
            movie.setModifiedTime(file.getModifiedTime());
            movie.setSize(file.getSize());
            movie.setUrlString(streamUrl);
            movie.setGd_id(id == null ? "" : id);
            movie.setIndex_id(indexId);

            if (isTmdbConfigured()) {
                sendGet2(movie);
            } else {
                String title = fallbackMovieTitle(file.getName());
                movie.setTitle(title);
                movie.setOriginal_title(title);
                DatabaseClient.getInstance(context).getAppDatabase().movieDao().insert(movie);
            }
        }

        if (id != null && !id.trim().isEmpty()) {
            cache.put(id, new CachedEntry(treatAsTv, file.getModifiedTime()));
        }
        return false;
    }

    private static Map<String, CachedEntry> buildScanCache(int indexId) {
        Map<String, CachedEntry> result = new HashMap<>();
        DatabaseClient db = DatabaseClient.getInstance(context);

        List<Movie> movies = db.getAppDatabase().movieDao().getAllFromIndex(indexId);
        if (movies != null) {
            for (Movie movie : movies) {
                if (movie == null || movie.getGd_id() == null || movie.getGd_id().trim().isEmpty()) continue;
                result.put(movie.getGd_id(), new CachedEntry(false, movie.getModifiedTime()));
            }
        }

        List<Episode> episodes = db.getAppDatabase().episodeDao().getAllFromIndex(indexId);
        if (episodes != null) {
            for (Episode episode : episodes) {
                if (episode == null || episode.getGd_id() == null || episode.getGd_id().trim().isEmpty()) continue;
                result.put(episode.getGd_id(), new CachedEntry(true, episode.getModifiedTime()));
            }
        }
        return result;
    }

    private static boolean isRemoteNewer(Date remote, Date cached) {
        if (remote == null || cached == null) return false;
        return remote.after(cached);
    }

    private static void pruneMissingCachedMedia(Map<String, CachedEntry> cache, Set<String> seenIds) {
        if (cache == null || cache.isEmpty()) return;
        DatabaseClient db = DatabaseClient.getInstance(context);
        for (Map.Entry<String, CachedEntry> entry : cache.entrySet()) {
            String id = entry.getKey();
            if (id == null || seenIds.contains(id)) continue;
            if (entry.getValue().tv) {
                db.getAppDatabase().episodeDao().deleteByGdId(id);
            } else {
                db.getAppDatabase().movieDao().deleteByGdId(id);
            }
        }
    }

    static boolean shouldTreatAsTv(String folderUrl, String fileName,
                                             boolean selectedTvShows) {
        String path = (folderUrl == null ? "" : folderUrl)
                .replace("%20", " ").toLowerCase();

        // Explicit folder names win for mixed-root GDI-JS libraries.
        if (path.contains("/movie/") || path.contains("/movies/")) return false;
        if (path.contains("/series/") || path.contains("/tvshows/")
                || path.contains("/tv shows/") || path.contains("/shows/")) return true;

        // Anime can contain both films and episodic series.
        if (path.contains("/anime/")) return looksLikeEpisode(folderUrl, fileName);

        if (looksLikeEpisode(folderUrl, fileName)) return true;
        return selectedTvShows;
    }

    private static boolean looksLikeEpisode(String folderUrl, String fileName) {
        if (fileName == null) return false;
        String clean = fileName.replace("Copy of ", "").trim();
        if (ShowUtils.parseShowName(clean) != null) return true;
        if (AnimeNameExtractor.getAnimeName(clean) != null) return true;

        String lowerPath = ((folderUrl == null ? "" : folderUrl) + "/" + clean).toLowerCase();
        boolean seriesFolder = lowerPath.contains("/tv shows/")
                || lowerPath.contains("/tvshows/")
                || lowerPath.contains("/series/")
                || lowerPath.contains("/shows/")
                || lowerPath.contains("/anime/");
        String base = clean.replaceFirst("\\.[^.]+$", "").trim();
        boolean numberOnlyEpisode = base.matches("(?i)^(?:e|ep|episode)?[ ._-]*\\d{1,3}(?:[ ._-].*)?$");
        boolean seasonFolder = lowerPath.matches(".*[/_-](?:season[ ._-]*|s)\\d{1,2}[/_-].*");
        return seriesFolder && (numberOnlyEpisode || seasonFolder);
    }

    static boolean isTmdbConfigured() {
        return TMDB_API_KEY != null && !TMDB_API_KEY.trim().isEmpty();
    }

    static String fallbackMovieTitle(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "Untitled Video";

        try {
            String[] extracted = MovieTitleExtractor2.getTitle2(fileName.replace("Copy of ", ""));
            if (extracted != null && extracted.length > 0
                    && extracted[0] != null && !extracted[0].trim().isEmpty()) {
                return extracted[0].trim();
            }
        } catch (Exception ignored) {}

        String name = fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replace('.', ' ').replace('_', ' ')
                .replaceAll("\\s+", " ").trim();
        return name.isEmpty() ? "Untitled Video" : name;
    }

    private static boolean isVideoFile(File file) {
        String mime = file.getMimeType();
        if (mime != null && mime.toLowerCase().startsWith("video/")) return true;
        String name = file.getName();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".avi")
                || lower.endsWith(".mov") || lower.endsWith(".m4v") || lower.endsWith(".webm")
                || lower.endsWith(".mpeg") || lower.endsWith(".mpg") || lower.endsWith(".ts")
                || lower.endsWith(".m2ts") || lower.endsWith(".3gp") || lower.endsWith(".wmv");
    }

    private static boolean isFolder(File file) {
        return "application/vnd.google-apps.folder".equals(file.getMimeType());
    }

    private static String resolveFileUrl(String rootUrl, String folderUrl, File file) {
        String link = file.getLink();
        if (link != null && !link.trim().isEmpty()) {
            try {
                return new URL(new URL(rootUrl), link).toString();
            } catch (Exception ignored) {}
        }
        return appendPath(folderUrl, file.getName(), false);
    }

    private static String appendPath(String base, String name, boolean folder) {
        if (!base.endsWith("/")) base += "/";
        if (name == null) name = "";
        try {
            String encoded = URLEncoder.encode(name, "UTF-8")
                    .replace("+", "%20").replace("%2F", "/");
            return base + encoded + (folder ? "/" : "");
        } catch (Exception e) {
            return base + name + (folder ? "/" : "");
        }
    }

    private static String getDefaultApiRoot(String baseUrl) {
        return baseUrl + DEFAULT_DRIVE_PREFIX;
    }

    private static String normalizeBaseUrl(String raw) throws java.net.MalformedURLException {
        if (raw == null || raw.trim().isEmpty()) throw new java.net.MalformedURLException();
        String value = raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new java.net.MalformedURLException();
        }
        if (!value.endsWith("/")) value += "/";
        new URL(value);
        return value;
    }

    private static String extractSessionCookie(String setCookie) {
        if (setCookie == null) return null;
        String[] parts = setCookie.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("session=")) return trimmed;
        }
        return null;
    }

    private static void emit(ProgressListener listener, Progress progress) {
        if (listener != null) listener.onProgress(progress);
    }

    private static String readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        return out.toString();
    }
}
